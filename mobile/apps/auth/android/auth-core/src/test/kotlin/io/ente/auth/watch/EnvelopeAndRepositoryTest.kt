package io.ente.auth.watch

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.Deflater

class EnvelopeAndRepositoryTest {
    @Test
    fun envelopeRoundTripCompressionAndTamperRejection() {
        val secret = "12345678901234567890".toByteArray(StandardCharsets.UTF_8).base64()
        val entries = (0 until 300).map { idx ->
            Entry(
                id = "entry-$idx",
                issuer = "Service $idx",
                account = "alice+$idx@example.com",
                secret = secret,
                generator = "totp",
                algorithm = "sha1",
                digits = 6,
                period = 30,
            )
        }
        val snapshot = Snapshot(entries)
        assertTrue(CipherSuite.supported.isNotEmpty())

        for (suite in CipherSuite.supported) {
            val key = PrivateKey.generate(suite)
            val enrollment = key.enrollment
            val envelope = Envelope.seal(snapshot, enrollment, 1)
            assertTrue(envelope.data.size < Envelope.MAXIMUM_BYTES)

            val decoded = Envelope.decode(envelope.data)
            val opened = decoded.open(key)
            assertEquals(300, opened.entries.size)
            assertEquals("entry-0", opened.entries.first().id)
            assertEquals("entry-299", opened.entries.last().id)

            val wrongKey = PrivateKey.generate(suite)
            assertThrows(Exception::class.java) { decoded.open(wrongKey) }

            val root = JsonParser.parseString(envelope.data.toString(Charsets.UTF_8)).asJsonObject
            val ct = root.get("ciphertext").asString.bytes()
            ct[0] = (ct[0].toInt() xor 0xff).toByte()
            root.addProperty("ciphertext", ct.base64())
            val tampered = Envelope.decode(root.encode())
            assertThrows(Exception::class.java) { tampered.open(key) }
        }
    }

    @Test
    fun decompressionBombRejectedBeforeFullAllocation() {
        val key = PrivateKey.generate(CipherSuite.X25519)
        val enrollment = key.enrollment
        val huge = ByteArray(Envelope.MAX_PLAINTEXT_BYTES + 1024) { 0x41 }
        val compressed = compressRawDeflate(huge)
        val header = JsonObject().apply {
            addProperty("version", 1)
            addProperty("enrollment", enrollment.id)
            addProperty("suite", enrollment.suite.wire)
            addProperty("revision", 1)
            addProperty("kind", "snapshot")
            addProperty("encoding", "deflate")
        }.encode()
        val context = "io.ente.auth.watch/v1/".toByteArray() + header
        val ciphertext = enrollment.encrypt(compressed, context)
        val wire = JsonObject().apply {
            addProperty("header", header.base64())
            addProperty("ciphertext", ciphertext.base64())
        }.encode()

        val envelope = Envelope.decode(wire)
        val err = assertThrows(WatchException::class.java) { envelope.open(key) }
        assertEquals("payloadTooLarge", err.code)
    }

    @Test
    fun phoneRepositoryAndWatchSelectionLifecycle() {
        val dir = Files.createTempDirectory("auth-core-repo").toFile()
        try {
            val epoch = VaultEpoch(dir.resolve("epoch"))
            val initialEpoch = epoch.current()
            val repo = PhoneRepository(dir.resolve("phone-state"), initialEpoch)

            val watchKey = PrivateKey.generate(CipherSuite.X25519)
            val enrollment = watchKey.enrollment
            val approved = repo.approve(enrollment, null)
            assertEquals(1L, approved.envelope.revision)
            assertFalse(approved.delivered)

            val ack1 = approved.envelope.receipt
            repo.acknowledge(ack1)
            assertTrue(repo.read()!!.delivered)

            val entry = Entry(
                id = "1",
                issuer = "GitHub",
                account = "alice",
                secret = "12345678901234567890".toByteArray().base64(),
                generator = "totp",
                algorithm = "sha1",
                digits = 6,
                period = 30,
            )
            val published = repo.publish(Snapshot(listOf(entry)))
            assertEquals(2L, published.envelope.revision)
            assertFalse(published.delivered)

            // Same desired digest should be a no-op.
            assertEquals(2L, repo.publish(Snapshot(listOf(entry))).envelope.revision)

            assertTrue(ack1.needsReplacement(published.envelope))
            assertFalse(published.envelope.receipt.needsReplacement(published.envelope))
            val conflict = Receipt(enrollment.id, published.envelope.revision, "0".repeat(64))
            val conflictErr = assertThrows(WatchException::class.java) {
                conflict.needsReplacement(published.envelope)
            }
            assertEquals("conflictingRevision", conflictErr.code)

            // Advancing epoch resets existing snapshot.
            val nextEpoch = epoch.advance()
            val nextRepo = PhoneRepository(dir.resolve("phone-state"), nextEpoch)
            val reset = nextRepo.read()!!
            assertTrue(reset.envelope.open(watchKey).entries.isEmpty())
            assertTrue(reset.envelope.revision > 2L)

            // WatchSelection cases: active connected watch, replacement, ambiguous.
            val otherKey = PrivateKey.generate(CipherSuite.X25519)
            val activeStatus = WatchStatus(enrollment, ack1)
            val otherStatus = WatchStatus(otherKey.enrollment, null)

            val selectedActive = WatchSelection.choose(published, listOf(activeStatus), listOf(activeStatus))
            assertFalse(selectedActive.ambiguous)
            assertEquals(enrollment.id, selectedActive.request?.enrollment?.id)

            val replacement = WatchSelection.choose(published, listOf(otherStatus), listOf(activeStatus, otherStatus))
            assertFalse(replacement.ambiguous)
            assertEquals(otherKey.enrollment.id, replacement.request?.enrollment?.id)

            val ambiguous = WatchSelection.choose(null, listOf(activeStatus, otherStatus), emptyList())
            assertTrue(ambiguous.ambiguous)
            assertNull(ambiguous.request)

            val cached = WatchSelection.choose(published, emptyList(), listOf(activeStatus))
            assertNotNull(cached.request)
            assertFalse(cached.ambiguous)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun snapshotMetadataRoundTripAndBackwardCompatibility() {
        val secret = "12345678901234567890".toByteArray(StandardCharsets.UTF_8).base64()
        val entry = Entry(
            id = "42",
            issuer = "Ente",
            account = "alice@ente.io",
            secret = secret,
            generator = "totp",
            algorithm = "sha1",
            digits = 6,
            period = 30,
            pinned = true,
            tags = listOf("Daily", "Work"),
        )
        val snapshot = Snapshot(listOf(entry), hideCodes = true)
        val decoded = Snapshot.decode(snapshot.encode())
        assertTrue(decoded.hideCodes)
        assertTrue(decoded.entries.first().pinned)
        assertEquals(listOf("Daily", "Work"), decoded.entries.first().tags)

        val legacyJson =
            """{"entries":[{"id":"7","issuer":"Legacy","account":"a@b.c","secret":"$secret","generator":"totp","algorithm":"sha1","digits":6,"period":30}]}"""
                .toByteArray(StandardCharsets.UTF_8)
        val legacy = Snapshot.decode(legacyJson)
        assertFalse(legacy.hideCodes)
        assertFalse(legacy.entries.first().pinned)
        assertTrue(legacy.entries.first().tags.isEmpty())
    }

    private fun compressRawDeflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        return try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream(data.size)
            val buf = ByteArray(4096)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                if (count == 0 && deflater.needsInput()) break
                out.write(buf, 0, count)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }
}
