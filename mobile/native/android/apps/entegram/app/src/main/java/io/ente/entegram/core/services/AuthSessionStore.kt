package io.ente.entegram.core.services

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ente.entegram.core.models.AuthenticatedUser
import io.ente.entegram.core.models.LoginResult
import java.util.Base64
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface AuthSessionStore {
    suspend fun read(): PersistedAuthSession?
    suspend fun write(result: LoginResult)
    suspend fun clear()
}

@Serializable
data class PersistedAuthSession(
    @SerialName("user_id") val userId: Long,
    val email: String,
    @SerialName("session_token") val sessionToken: String,
    @SerialName("master_key") val masterKey: String,
    @SerialName("secret_key") val secretKey: String,
    @SerialName("public_key") val publicKey: String = "",
) {
    fun toLoginResult(): LoginResult = LoginResult(
        user = AuthenticatedUser(
            id = userId,
            email = email,
            username = null,
            sessionToken = sessionToken,
        ),
        masterKey = masterKey.decodeBase64(),
        secretKey = secretKey.decodeBase64(),
        publicKey = publicKey.decodeBase64OrEmpty(),
    )

    companion object {
        fun from(result: LoginResult): PersistedAuthSession = PersistedAuthSession(
            userId = result.user.id,
            email = result.user.email,
            sessionToken = result.user.sessionToken,
            masterKey = result.masterKey.encodeBase64(),
            secretKey = result.secretKey.encodeBase64(),
            publicKey = result.publicKey.encodeBase64(),
        )
    }
}

class EncryptedAuthSessionStore(
    context: Context,
    private val json: Json = Json,
) : AuthSessionStore {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun read(): PersistedAuthSession? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_AUTH_SESSION, null)?.let {
            json.decodeFromString(PersistedAuthSession.serializer(), it)
        }
    }

    override suspend fun write(result: LoginResult) {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString(
                    KEY_AUTH_SESSION,
                    json.encodeToString(
                        PersistedAuthSession.serializer(),
                        PersistedAuthSession.from(result),
                    ),
                )
                .apply()
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            prefs.edit()
                .remove(KEY_AUTH_SESSION)
                .apply()
        }
    }

    private companion object {
        const val PREFS_FILE = "entegram.auth.session"
        const val KEY_AUTH_SESSION = "auth_session"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AuthSessionModule {

    @Provides
    @Singleton
    fun provideAuthSessionStore(
        @ApplicationContext context: Context,
    ): AuthSessionStore = EncryptedAuthSessionStore(context)
}

private fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)

private fun String.decodeBase64(): ByteArray = Base64.getDecoder().decode(this)

private fun String.decodeBase64OrEmpty(): ByteArray =
    if (isBlank()) byteArrayOf() else Base64.getDecoder().decode(this)
