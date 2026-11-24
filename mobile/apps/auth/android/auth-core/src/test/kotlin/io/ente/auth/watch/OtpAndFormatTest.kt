package io.ente.auth.watch

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class OtpAndFormatTest {
    private fun loadVectors(): com.google.gson.JsonObject {
        val candidates = listOf(
            File("../../test/fixtures/watch_otp_vectors.json"),
            File("../test/fixtures/watch_otp_vectors.json"),
            File("mobile/apps/auth/test/fixtures/watch_otp_vectors.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: error("Could not locate watch_otp_vectors.json from ${File(".").absolutePath}")
        return JsonParser.parseString(file.readText(StandardCharsets.UTF_8)).asJsonObject
    }

    @Test
    fun sharedVectorsAndFormatting() {
        val vectors = loadVectors()
        for (item in vectors.getAsJsonArray("otp")) {
            val v = item.asJsonObject
            val name = v.get("name").asString
            val entry = Entry(
                id = name,
                issuer = name,
                account = "alice@ente.io",
                secret = v.get("secretBase64").asString,
                generator = v.get("generator").asString,
                algorithm = v.get("algorithm").asString,
                digits = v.get("digits").asInt,
                period = v.get("period").asInt,
            )
            assertEquals(name, v.get("expected").asString, entry.code(v.get("timestamp").asLong))
        }
        for (item in vectors.getAsJsonArray("format")) {
            val v = item.asJsonObject
            assertEquals(v.get("expected").asString, formatCode(v.get("input").asString))
        }
    }

    @Test
    fun invalidEntriesRejected() {
        val validSecret = "12345678901234567890".toByteArray(StandardCharsets.UTF_8).base64()
        val invalid = listOf(
            Entry("", "A", "B", validSecret, "totp", "sha1", 6, 30),
            Entry("1", "A", "B", "", "totp", "sha1", 6, 30),
            Entry("2", "A", "B", validSecret, "totp", "sha1", 0, 30),
            Entry("3", "A", "B", validSecret, "totp", "sha1", 11, 30),
            Entry("4", "A", "B", validSecret, "totp", "sha1", 6, 0),
            Entry("5", "Steam", "B", validSecret, "steam", "sha256", 5, 30),
            Entry("6", "Steam", "B", validSecret, "steam", "sha1", 6, 30),
            Entry("7", "Hotp", "B", validSecret, "hotp", "sha1", 6, 30),
        )
        for (entry in invalid) {
            assertThrows(entry.id, WatchException::class.java) { entry.validate() }
        }
    }
}
