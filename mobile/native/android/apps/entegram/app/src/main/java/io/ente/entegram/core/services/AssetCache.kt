package io.ente.entegram.core.services

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface AssetCache {
    suspend fun read(cacheKey: String): ByteArray?
    suspend fun write(cacheKey: String, bytes: ByteArray)
    suspend fun clear()
}

@Singleton
class EncryptedAssetDiskCache @Inject constructor(
    @ApplicationContext context: Context,
) : AssetCache {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.filesDir, CACHE_DIR)
    private val random = SecureRandom()
    private val secretLock = Any()
    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        PREFS_FILE,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    @Volatile
    private var secret: ByteArray? = null

    override suspend fun read(cacheKey: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(cacheKey)
        if (!file.exists()) {
            return@withContext null
        }
        runCatching {
            val payload = file.readBytes()
            require(payload.size > HEADER_SIZE && payload[0] == VERSION)
            val iv = payload.copyOfRange(1, HEADER_SIZE)
            val encrypted = payload.copyOfRange(HEADER_SIZE, payload.size)
            decrypt(cacheKey, iv, encrypted)
        }.getOrElse {
            file.delete()
            null
        }
    }

    override suspend fun write(cacheKey: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val iv = ByteArray(IV_SIZE).also(random::nextBytes)
            val encrypted = encrypt(cacheKey, iv, bytes)
            val file = fileFor(cacheKey)
            val tmp = File(cacheDir, "${file.name}.tmp")
            tmp.outputStream().use { output ->
                output.write(VERSION.toInt())
                output.write(iv)
                output.write(encrypted)
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
                tmp.delete()
            }
            pruneIfNeeded()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        }
    }

    private fun encrypt(cacheKey: String, iv: ByteArray, bytes: ByteArray): ByteArray {
        val cipher = cipher(Cipher.ENCRYPT_MODE, iv)
        cipher.updateAAD(cacheKey.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes)
    }

    private fun decrypt(cacheKey: String, iv: ByteArray, bytes: ByteArray): ByteArray {
        val cipher = cipher(Cipher.DECRYPT_MODE, iv)
        cipher.updateAAD(cacheKey.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(bytes)
    }

    private fun cipher(mode: Int, iv: ByteArray): Cipher =
        Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, SecretKeySpec(cacheSecret(), "AES"), GCMParameterSpec(TAG_BITS, iv))
        }

    private fun cacheSecret(): ByteArray {
        secret?.let { return it }
        return synchronized(secretLock) {
            secret?.let { return@synchronized it }
            val existing = prefs.getString(KEY_CACHE_SECRET, null)
            if (existing != null) {
                return@synchronized Base64.getDecoder().decode(existing).also {
                    secret = it
                }
            }
            val generated = ByteArray(32).also(random::nextBytes)
            prefs.edit()
                .putString(KEY_CACHE_SECRET, Base64.getEncoder().encodeToString(generated))
                .apply()
            secret = generated
            generated
        }
    }

    private fun fileFor(cacheKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(cacheKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(cacheDir, "$digest.asset")
    }

    private fun pruneIfNeeded() {
        val files = cacheDir.listFiles()?.filter { it.isFile } ?: return
        val totalBytes = files.sumOf { it.length() }
        if (totalBytes <= MAX_CACHE_BYTES) {
            return
        }
        var remaining = totalBytes
        for (file in files.sortedBy { it.lastModified() }) {
            if (remaining <= TARGET_CACHE_BYTES) {
                break
            }
            remaining -= file.length()
            file.delete()
        }
    }

    private companion object {
        const val CACHE_DIR = "asset-cache"
        const val PREFS_FILE = "entegram.asset.cache"
        const val KEY_CACHE_SECRET = "cache_secret"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
        const val HEADER_SIZE = 1 + IV_SIZE
        const val VERSION: Byte = 1
        const val MAX_CACHE_BYTES = 256L * 1024 * 1024
        const val TARGET_CACHE_BYTES = 224L * 1024 * 1024
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AssetCacheModule {
    @Binds
    @Singleton
    abstract fun bindAssetCache(
        impl: EncryptedAssetDiskCache,
    ): AssetCache
}
