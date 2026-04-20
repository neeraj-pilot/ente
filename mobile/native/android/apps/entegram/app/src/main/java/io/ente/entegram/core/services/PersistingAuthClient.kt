package io.ente.entegram.core.services

import io.ente.entegram.core.models.LoginResult

class PersistingAuthClient(
    private val delegate: AuthClient,
    private val authSessionStore: AuthSessionStore,
) : AuthClient {

    override suspend fun sendOtt(email: String, purpose: AuthOttPurpose) {
        delegate.sendOtt(email, purpose)
    }

    override suspend fun loginPreflight(email: String): LoginFlowDecision {
        return delegate.loginPreflight(email)
    }

    override suspend fun signup(
        email: String,
        password: String,
        code: String,
    ): LoginResult {
        return delegate.signup(email, password, code).alsoPersist()
    }

    override suspend fun login(email: String, password: String, code: String?): LoginResult {
        return delegate.login(email, password, code).alsoPersist()
    }

    override suspend fun recoverAccount(
        email: String,
        code: String,
        recoveryKey: String,
        newPassword: String,
    ): LoginResult {
        return delegate.recoverAccount(email, code, recoveryKey, newPassword).alsoPersist()
    }

    override suspend fun signOut(sessionToken: String?) {
        delegate.signOut(sessionToken)
        authSessionStore.clear()
    }

    private suspend fun LoginResult.alsoPersist(): LoginResult {
        authSessionStore.write(this)
        return this
    }
}
