package io.ente.entegram.core.services

import io.ente.entegram.core.models.LoginResult

/**
 * Authentication service interface.
 * Android delegates this surface to `ente3/rust/accounts` and persists successful
 * sessions separately via [PersistingAuthClient].
 */
enum class AuthOttPurpose {
    Signup,
    Login,
    Recovery,
}

enum class LoginFlowDecision {
    PasswordOnly,
    EmailOttAndPassword,
    Signup,
}

interface AuthClient {
    suspend fun sendOtt(email: String, purpose: AuthOttPurpose)
    suspend fun loginPreflight(email: String): LoginFlowDecision
    suspend fun signup(email: String, password: String, code: String): LoginResult
    suspend fun login(email: String, password: String, code: String?): LoginResult
    suspend fun recoverAccount(
        email: String,
        code: String,
        recoveryKey: String,
        newPassword: String,
    ): LoginResult
    suspend fun signOut(sessionToken: String?)
}
