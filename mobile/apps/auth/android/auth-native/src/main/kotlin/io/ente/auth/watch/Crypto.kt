package io.ente.auth.watch

import com.google.crypto.tink.HybridDecrypt
import com.google.crypto.tink.HybridEncrypt
import com.google.crypto.tink.InsecureSecretKeyAccess
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.TinkProtoKeysetFormat
import com.google.crypto.tink.hybrid.HpkeParameters
import com.google.crypto.tink.hybrid.HybridConfig
import com.google.gson.JsonObject

enum class CipherSuite(val wire: String, private val kem: HpkeParameters.KemId) {
    XWING("xwing-sha256-aes256", HpkeParameters.KemId.X_WING),
    X25519("x25519-sha256-aes256", HpkeParameters.KemId.DHKEM_X25519_HKDF_SHA256);

    internal fun parameters(): HpkeParameters = HpkeParameters.builder()
        .setVariant(HpkeParameters.Variant.NO_PREFIX).setKemId(kem)
        .setKdfId(HpkeParameters.KdfId.HKDF_SHA256).setAeadId(HpkeParameters.AeadId.AES_256_GCM).build()

    companion object {
        internal fun parse(value: String): CipherSuite = entries.firstOrNull { it.wire == value }
            ?: throw WatchException("unsupportedSuite")

        // XWing needs a compatible device provider. Probe public APIs, with no provider installation.
        val supported: List<CipherSuite> by lazy {
            HybridConfig.register()
            entries.filter { suite ->
                try {
                    val key = PrivateKey.generate(suite)
                    val probe = byteArrayOf(1, 2, 3)
                    key.decrypt(key.enrollment.encrypt(probe, probe), probe).contentEquals(probe)
                } catch (_: Exception) { false } catch (_: LinkageError) { false }
            }
        }
    }
}

data class Enrollment(val suite: CipherSuite, val publicKey: String) {
    val id: String get() = (suite.wire.toByteArray() + publicKey.bytes()).digest()

    private fun handle(): KeysetHandle {
        HybridConfig.register()
        val handle = TinkProtoKeysetFormat.parseKeysetWithoutSecret(publicKey.bytes())
        requireWatch(handle.size() == 1 && handle.primary.key.parameters == suite.parameters(), "invalidEnvelope")
        return handle
    }

    fun validate() { handle().getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java) }
    internal fun encrypt(data: ByteArray, context: ByteArray): ByteArray = handle()
        .getPrimitive(RegistryConfiguration.get(), HybridEncrypt::class.java).encrypt(data, context)
    internal fun encode() = JsonObject().apply {
        addProperty("suite", suite.wire); addProperty("publicKey", publicKey)
    }

    companion object {
        internal fun decode(value: JsonObject) = Enrollment(CipherSuite.parse(value.string("suite")),
            value.string("publicKey")).also { it.validate() }
    }
}

class PrivateKey private constructor(val suite: CipherSuite, private val handle: KeysetHandle) {
    val enrollment: Enrollment get() = Enrollment(suite,
        TinkProtoKeysetFormat.serializeKeysetWithoutSecret(handle.publicKeysetHandle).base64())
    internal fun decrypt(data: ByteArray, context: ByteArray): ByteArray = handle
        .getPrimitive(RegistryConfiguration.get(), HybridDecrypt::class.java).decrypt(data, context)
    internal fun encode(): ByteArray = JsonObject().apply {
        addProperty("suite", suite.wire)
        addProperty("keyset", TinkProtoKeysetFormat.serializeKeyset(handle, InsecureSecretKeyAccess.get(),
            RegistryConfiguration.get()).base64())
    }.encode()

    companion object {
        fun generate(suite: CipherSuite): PrivateKey {
            HybridConfig.register()
            return PrivateKey(suite, KeysetHandle.generateNew(suite.parameters()))
        }

        internal fun decode(data: ByteArray): PrivateKey {
            HybridConfig.register()
            val value = json(data)
            val suite = CipherSuite.parse(value.string("suite"))
            val handle = TinkProtoKeysetFormat.parseKeyset(value.string("keyset").bytes(),
                InsecureSecretKeyAccess.get(), RegistryConfiguration.get())
            requireWatch(handle.size() == 1 && handle.primary.key.parameters == suite.parameters(), "invalidEnvelope")
            return PrivateKey(suite, handle)
        }
    }
}

class Envelope private constructor(
    val data: ByteArray,
    val enrollmentID: String,
    val suite: CipherSuite,
    val revision: Long,
    val retire: Boolean,
    private val header: ByteArray,
    private val ciphertext: ByteArray,
) {
    val receipt: Receipt get() = Receipt(enrollmentID, revision, data.digest())

    fun open(key: PrivateKey): Snapshot {
        requireWatch(enrollmentID == key.enrollment.id && suite == key.suite, "wrongEnrollment")
        val snapshot = Snapshot.decode(key.decrypt(ciphertext, context(header)))
        requireWatch(!retire || snapshot.entries.isEmpty(), "invalidEnvelope")
        return snapshot
    }

    companion object {
        const val MAXIMUM_BYTES = 48 * 1024
        private fun context(header: ByteArray) = "io.ente.auth.watch/v1/".toByteArray() + header

        fun seal(snapshot: Snapshot, enrollment: Enrollment, revision: Long, retire: Boolean = false): Envelope {
            requireWatch(!retire || snapshot.entries.isEmpty(), "invalidEnvelope")
            val header = JsonObject().apply {
                addProperty("version", 1); addProperty("enrollment", enrollment.id)
                addProperty("suite", enrollment.suite.wire); addProperty("revision", revision)
                addProperty("kind", if (retire) "retire" else "snapshot")
            }.encode()
            val ciphertext = enrollment.encrypt(snapshot.encode(), context(header))
            return decode(JsonObject().apply {
                addProperty("header", header.base64()); addProperty("ciphertext", ciphertext.base64())
            }.encode())
        }

        fun decode(data: ByteArray): Envelope {
            val value = json(data)
            val header = value.string("header").bytes()
            val fields = json(header, 1024)
            requireWatch(fields.number("version") == 1L, "unsupportedVersion")
            val enrollment = fields.string("enrollment")
            val revision = fields.number("revision")
            val kind = fields.string("kind")
            requireWatch(enrollment.matches(Regex("[0-9a-f]{64}")) && revision > 0 &&
                kind in listOf("snapshot", "retire"), "invalidEnvelope")
            return Envelope(data.copyOf(), enrollment, CipherSuite.parse(fields.string("suite")), revision,
                kind == "retire", header, value.string("ciphertext").bytes())
        }
    }
}

data class Receipt(val enrollment: String, val revision: Long, val digest: String) {
    fun needsReplacement(envelope: Envelope): Boolean {
        requireWatch(enrollment == envelope.enrollmentID, "wrongEnrollment")
        if (revision == envelope.revision) {
            requireWatch(this == envelope.receipt, "conflictingRevision")
            return false
        }
        return envelope.revision > revision
    }

    internal fun encode() = JsonObject().apply {
        addProperty("enrollment", enrollment); addProperty("revision", revision); addProperty("digest", digest)
    }
    companion object {
        internal fun decode(value: JsonObject): Receipt = Receipt(value.string("enrollment"),
            value.number("revision"), value.string("digest")).also {
            requireWatch(it.enrollment.matches(Regex("[0-9a-f]{64}")) && it.revision > 0 &&
                it.digest.matches(Regex("[0-9a-f]{64}")), "invalidEnvelope")
        }
    }
}
