package io.ente.entegram.core.services

import io.ente.entegram.BuildConfig
import io.ente.entegram.core.crypto.EnteAuthCore
import io.ente.entegram.core.models.AuthenticatedUser
import io.ente.entegram.core.models.LoginResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.ente_ffi.FfiAccountLoginFlow
import uniffi.ente_ffi.FfiAccountLoginResult
import uniffi.ente_ffi.FfiException

@Singleton
class RustAccountAuthClient @Inject constructor(
    private val enteAuthCore: EnteAuthCore,
) : AuthClient {

    // Native Android does not own auth HTTP. It only forwards the backend
    // base URL into the shared ente3/rust/accounts surface.
    private val defaultBaseUrl: String = BuildConfig.ENTEGRAM_API_BASE_URL
    private var baseUrlOverride: String? = null

    internal constructor(
        enteAuthCore: EnteAuthCore,
        baseUrl: String,
    ) : this(enteAuthCore) {
        baseUrlOverride = baseUrl
    }

    override suspend fun sendOtt(email: String, purpose: AuthOttPurpose) {
        val normalizedEmail = normalizeEmail(email)
        requireField(normalizedEmail.isNotEmpty(), "Email is required.")
        runRustCall {
            enteAuthCore.sendOtt(resolvedBaseUrl, normalizedEmail, purpose.wireValue)
        }
    }

    override suspend fun loginPreflight(email: String): LoginFlowDecision {
        val normalizedEmail = normalizeEmail(email)
        requireField(normalizedEmail.isNotEmpty(), "Email is required.")
        return runRustCall {
            when (enteAuthCore.loginPreflight(resolvedBaseUrl, normalizedEmail).flow) {
                FfiAccountLoginFlow.PASSWORD_ONLY -> LoginFlowDecision.PasswordOnly
                FfiAccountLoginFlow.EMAIL_OTT_AND_PASSWORD -> LoginFlowDecision.EmailOttAndPassword
                FfiAccountLoginFlow.SIGNUP -> LoginFlowDecision.Signup
            }
        }
    }

    override suspend fun signup(
        email: String,
        password: String,
        code: String,
    ): LoginResult {
        val normalizedEmail = normalizeEmail(email)
        val normalizedCode = code.trim()
        requireField(normalizedEmail.isNotEmpty(), "Email is required.")
        requireField(password.isNotEmpty(), "Password is required.")
        requireField(normalizedCode.isNotEmpty(), "Verification code is required.")
        return runRustCall {
            enteAuthCore.signup(
                baseUrl = resolvedBaseUrl,
                email = normalizedEmail,
                ott = normalizedCode,
                password = password,
            ).toLoginResult()
        }
    }

    override suspend fun login(email: String, password: String, code: String?): LoginResult {
        val normalizedEmail = normalizeEmail(email)
        val normalizedCode = code?.trim().orEmpty()
        requireField(normalizedEmail.isNotEmpty(), "Email is required.")
        requireField(password.isNotEmpty(), "Password is required.")
        return runRustCall {
            if (normalizedCode.isNotEmpty()) {
                enteAuthCore.loginWithOtt(
                    baseUrl = resolvedBaseUrl,
                    email = normalizedEmail,
                    ott = normalizedCode,
                    password = password,
                ).toLoginResult()
            } else {
                enteAuthCore.login(
                    baseUrl = resolvedBaseUrl,
                    email = normalizedEmail,
                    password = password,
                ).toLoginResult()
            }
        }
    }

    override suspend fun recoverAccount(
        email: String,
        code: String,
        recoveryKey: String,
        newPassword: String,
    ): LoginResult {
        val normalizedEmail = normalizeEmail(email)
        val normalizedCode = code.trim()
        val normalizedRecoveryKey = recoveryKey.trim()
        requireField(normalizedEmail.isNotEmpty(), "Email is required.")
        requireField(normalizedCode.isNotEmpty(), "Verification code is required.")
        requireField(normalizedRecoveryKey.isNotEmpty(), "Recovery key is required.")
        requireField(newPassword.isNotEmpty(), "New password is required.")
        return runRustCall {
            enteAuthCore.recover(
                baseUrl = resolvedBaseUrl,
                email = normalizedEmail,
                ott = normalizedCode,
                recoveryKey = normalizedRecoveryKey,
                newPassword = newPassword,
            ).toLoginResult()
        }
    }

    override suspend fun signOut(sessionToken: String?) {
        val normalizedToken = sessionToken?.trim().orEmpty()
        if (normalizedToken.isEmpty()) {
            return
        }
        runRustCall {
            enteAuthCore.logout(
                baseUrl = resolvedBaseUrl,
                authToken = normalizedToken,
            )
        }
    }

    private suspend fun <T> runRustCall(block: () -> T): T {
        return withContext(Dispatchers.IO) {
            try {
                block()
            } catch (error: Throwable) {
                throw mapError(error)
            }
        }
    }

    private fun mapError(error: Throwable): Throwable {
        return when (error) {
            is IllegalArgumentException -> error
            is FfiException -> IllegalStateException(mapFfiMessage(error.message))
            else -> error
        }
    }

    private fun mapFfiMessage(message: String?): String {
        val rawMessage = message ?: return "Authentication failed."
        return when {
            rawMessage.isKnownError("USER_ALREADY_REGISTERED", "User is already registered") ->
                "This email already has an account. Sign in instead."
            rawMessage.isKnownError("USER_NOT_REGISTERED", "User is not registered") ->
                "No account found for this email. Create a new account instead."
            rawMessage.isKnownError("USER_SIGNUP_INCOMPLETE", "User signup is incomplete") ->
                "Account setup is incomplete. Create the account again to finish setup."
            rawMessage.contains("incorrect OTT", ignoreCase = true) ||
                rawMessage.contains("no active OTT", ignoreCase = true) ->
                "That verification code is incorrect or expired. Request a new code."
            rawMessage.contains("incorrect password", ignoreCase = true) ||
                rawMessage.contains("invalid password", ignoreCase = true) ->
                "Incorrect password."
            rawMessage.contains("incorrect recovery key", ignoreCase = true) ->
                "Incorrect recovery key."
            rawMessage.contains("too many", ignoreCase = true) ||
                rawMessage.contains("HTTP 429", ignoreCase = true) ->
                "Too many attempts. Wait a bit and try again."
            rawMessage.contains("Network error:", ignoreCase = true) ->
                "Couldn't reach the server. Check your connection and try again."
            rawMessage.contains("master_key_encrypted_with_recovery_key", ignoreCase = true) ->
                "Account recovery is unavailable for this account on the current backend. Missing recovery-key metadata."
            rawMessage.contains("HTTP 5", ignoreCase = true) ->
                "The server is having trouble. Try again shortly."
            rawMessage.contains("HTTP ", ignoreCase = true) ->
                "Authentication failed. Please try again."
            else -> rawMessage.removeRequestContext()
        }
    }

    private fun String.isKnownError(code: String, message: String): Boolean {
        return contains(code, ignoreCase = true) || contains(message, ignoreCase = true)
    }

    private fun String.removeRequestContext(): String {
        return replace(Regex("\\s*\\[request:.*]"), "").trim()
    }

    private fun requireField(condition: Boolean, message: String) {
        if (!condition) {
            throw IllegalArgumentException(message)
        }
    }

    private fun normalizeEmail(email: String): String {
        return email.trim().lowercase()
    }

    private val resolvedBaseUrl: String
        get() = baseUrlOverride ?: defaultBaseUrl

    private fun FfiAccountLoginResult.toLoginResult(): LoginResult {
        return LoginResult(
            user = AuthenticatedUser(
                id = userId,
                email = email,
                username = null,
                sessionToken = authToken,
            ),
            masterKey = masterKey,
            secretKey = secretKey,
            publicKey = publicKey,
            recoveryKey = recoveryKey,
        )
    }

}

private val AuthOttPurpose.wireValue: String
    get() = when (this) {
        AuthOttPurpose.Signup -> "signup"
        AuthOttPurpose.Login -> "login"
        AuthOttPurpose.Recovery -> "recovery"
    }
