package io.ente.entegram.core.services

import io.ente.entegram.core.models.LoginResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PersistingAuthClientTest {

    @Test
    fun `login persists returned session`() = runTest {
        val delegate = FakeAuthClient(loginResult = SampleData.viewerLoginResult)
        val store = FakeAuthSessionStore()
        val client = PersistingAuthClient(delegate, store)

        val result = client.login(
            email = "me@example.com",
            password = "hunter2",
            code = "123456",
        )

        assertEquals(SampleData.viewerLoginResult, result)
        val persisted = requireNotNull(store.persisted)
        assertEquals(SampleData.viewer.id, persisted.userId)
        assertEquals(SampleData.viewer.email, persisted.email)
        assertEquals(SampleData.viewer.sessionToken, persisted.sessionToken)
        assertArrayEquals(SampleData.viewerLoginResult.masterKey, persisted.toLoginResult().masterKey)
        assertArrayEquals(SampleData.viewerLoginResult.secretKey, persisted.toLoginResult().secretKey)
    }

    @Test
    fun `failed login does not overwrite stored session`() = runTest {
        val existing = PersistedAuthSession.from(SampleData.viewerLoginResult)
        val delegate = FakeAuthClient(loginFailure = IllegalStateException("boom"))
        val store = FakeAuthSessionStore(existing)
        val client = PersistingAuthClient(delegate, store)

        runCatching {
            client.login(
                email = "me@example.com",
                password = "wrong",
                code = null,
            )
        }

        assertEquals(existing, store.persisted)
    }

    @Test
    fun `sign out clears stored session after delegate succeeds`() = runTest {
        val delegate = FakeAuthClient(loginResult = SampleData.viewerLoginResult)
        val store = FakeAuthSessionStore(PersistedAuthSession.from(SampleData.viewerLoginResult))
        val client = PersistingAuthClient(delegate, store)

        client.signOut(SampleData.viewer.sessionToken)

        assertNull(store.persisted)
    }
}

private class FakeAuthSessionStore(
    initial: PersistedAuthSession? = null,
) : AuthSessionStore {
    var persisted: PersistedAuthSession? = initial

    override suspend fun read(): PersistedAuthSession? = persisted

    override suspend fun write(result: LoginResult) {
        persisted = PersistedAuthSession.from(result)
    }

    override suspend fun clear() {
        persisted = null
    }
}

private class FakeAuthClient(
    private val loginResult: LoginResult? = null,
    private val loginFailure: Throwable? = null,
) : AuthClient {
    override suspend fun sendOtt(email: String, purpose: AuthOttPurpose) = Unit

    override suspend fun loginPreflight(email: String): LoginFlowDecision = LoginFlowDecision.PasswordOnly

    override suspend fun signup(
        email: String,
        password: String,
        code: String,
    ): LoginResult {
        return requireNotNull(loginResult)
    }

    override suspend fun login(email: String, password: String, code: String?): LoginResult {
        loginFailure?.let { throw it }
        return requireNotNull(loginResult)
    }

    override suspend fun recoverAccount(
        email: String,
        code: String,
        recoveryKey: String,
        newPassword: String,
    ): LoginResult {
        return requireNotNull(loginResult)
    }

    override suspend fun signOut(sessionToken: String?) = Unit
}
