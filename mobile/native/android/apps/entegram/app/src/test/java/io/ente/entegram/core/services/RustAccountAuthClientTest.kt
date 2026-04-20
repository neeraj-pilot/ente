package io.ente.entegram.core.services

import io.ente.entegram.core.crypto.EnteAuthCore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import uniffi.ente_ffi.FfiAccountLoginFlow
import uniffi.ente_ffi.FfiAccountLoginPreflight
import uniffi.ente_ffi.FfiAccountLoginResult
import uniffi.ente_ffi.FfiException

class RustAccountAuthClientTest {

    private val testBaseUrl = "http://127.0.0.1:8080"

    @Test
    fun `signup returns recovery key from rust result`() = runTest {
        val authCore = FakeEnteAuthCore(
            signupResult = FfiAccountLoginResult(
                userId = 7,
                email = "new@example.com",
                username = "acct_123",
                authToken = "session-token",
                masterKey = byteArrayOf(1, 2, 3),
                secretKey = byteArrayOf(4, 5, 6),
                publicKey = byteArrayOf(7, 8, 9),
                recoveryKey = "real-recovery-key",
            ),
        )
        val client = RustAccountAuthClient(authCore, testBaseUrl)

        val result = client.signup(
            email = " New@Example.com ",
            password = "hunter2",
            code = " 123456 ",
        )

        assertEquals("new@example.com", result.user.email)
        assertEquals("real-recovery-key", result.recoveryKey)
        assertEquals(testBaseUrl, authCore.lastBaseUrl)
        assertEquals("123456", authCore.lastOtt)
    }

    @Test
    fun `login chooses ott rust call when login code is present`() = runTest {
        val authCore = FakeEnteAuthCore(
            loginResult = sampleFfiLoginResult(),
        )
        val client = RustAccountAuthClient(authCore, testBaseUrl)

        client.login(
            email = "viewer@example.com",
            password = "hunter2",
            code = "654321",
        )

        assertEquals(0, authCore.passwordLoginCalls)
        assertEquals(1, authCore.ottLoginCalls)
        assertEquals("654321", authCore.lastOtt)
    }

    @Test
    fun `preflight maps rust flow enum`() = runTest {
        val authCore = FakeEnteAuthCore(
            preflight = FfiAccountLoginPreflight(FfiAccountLoginFlow.EMAIL_OTT_AND_PASSWORD),
        )
        val client = RustAccountAuthClient(authCore, testBaseUrl)

        val result = client.loginPreflight("viewer@example.com")

        assertEquals(LoginFlowDecision.EmailOttAndPassword, result)
    }

    @Test
    fun `ffi errors surface as state errors`() = runTest {
        val authCore = FakeEnteAuthCore(
            loginFailure = FfiException.Auth("boom"),
        )
        val client = RustAccountAuthClient(authCore, testBaseUrl)

        val error = runCatching {
            client.login("viewer@example.com", "hunter2", null)
        }.exceptionOrNull()

        check(error is IllegalStateException)
        assertEquals("boom", error.message)
    }

    @Test
    fun `registered signup ott error maps to user-facing copy`() = runTest {
        val authCore = FakeEnteAuthCore(
            sendOttFailure = FfiException.Auth(
                "HTTP 409: User is already registered [request: POST /users/ott]",
            ),
        )
        val client = RustAccountAuthClient(authCore, testBaseUrl)

        val error = runCatching {
            client.sendOtt("viewer@example.com", AuthOttPurpose.Signup)
        }.exceptionOrNull()

        check(error is IllegalStateException)
        assertEquals("This email already has an account. Sign in instead.", error.message)
    }

    @Test
    fun `missing account ott error maps to user-facing copy`() = runTest {
        val authCore = FakeEnteAuthCore(
            sendOttFailure = FfiException.Auth(
                "HTTP 404: User is not registered [request: POST /users/ott]",
            ),
        )
        val client = RustAccountAuthClient(authCore, testBaseUrl)

        val error = runCatching {
            client.sendOtt("viewer@example.com", AuthOttPurpose.Login)
        }.exceptionOrNull()

        check(error is IllegalStateException)
        assertEquals("No account found for this email. Create a new account instead.", error.message)
    }

    @Test
    fun `unknown auth http errors do not leak request internals`() = runTest {
        val authCore = FakeEnteAuthCore(
            sendOttFailure = FfiException.Auth(
                "HTTP 400: invalid request [request: POST /users/ott]",
            ),
        )
        val client = RustAccountAuthClient(authCore, testBaseUrl)

        val error = runCatching {
            client.sendOtt("viewer@example.com", AuthOttPurpose.Signup)
        }.exceptionOrNull()

        check(error is IllegalStateException)
        assertEquals("Authentication failed. Please try again.", error.message)
    }

    @Test
    fun `recovery metadata ffi errors surface as recovery blocker copy`() = runTest {
        val authCore = FakeEnteAuthCore(
            recoverFailure = FfiException.Auth(
                "Auth error: crypto error: Missing required field: master_key_encrypted_with_recovery_key",
            ),
        )
        val client = RustAccountAuthClient(authCore, testBaseUrl)

        val error = runCatching {
            client.recoverAccount(
                email = "viewer@example.com",
                code = "123456",
                recoveryKey = "recovery-key",
                newPassword = "NewPassword123!",
            )
        }.exceptionOrNull()

        check(error is IllegalStateException)
        assertEquals(
            "Account recovery is unavailable for this account on the current backend. Missing recovery-key metadata.",
            error.message,
        )
    }
}

private class FakeEnteAuthCore(
    private val preflight: FfiAccountLoginPreflight = FfiAccountLoginPreflight(FfiAccountLoginFlow.PASSWORD_ONLY),
    private val signupResult: FfiAccountLoginResult = sampleFfiLoginResult(),
    private val loginResult: FfiAccountLoginResult = sampleFfiLoginResult(),
    private val sendOttFailure: Throwable? = null,
    private val loginFailure: Throwable? = null,
    private val recoverFailure: Throwable? = null,
) : EnteAuthCore {
    var lastBaseUrl: String? = null
    var lastOtt: String? = null
    var passwordLoginCalls: Int = 0
    var ottLoginCalls: Int = 0

    override fun sendOtt(baseUrl: String, email: String, purpose: String) {
        sendOttFailure?.let { throw it }
        lastBaseUrl = baseUrl
    }

    override fun loginPreflight(baseUrl: String, email: String): FfiAccountLoginPreflight {
        lastBaseUrl = baseUrl
        return preflight
    }

    override fun signup(
        baseUrl: String,
        email: String,
        ott: String,
        password: String,
    ): FfiAccountLoginResult {
        lastBaseUrl = baseUrl
        lastOtt = ott
        return signupResult
    }

    override fun login(baseUrl: String, email: String, password: String): FfiAccountLoginResult {
        loginFailure?.let { throw it }
        lastBaseUrl = baseUrl
        passwordLoginCalls += 1
        return loginResult
    }

    override fun loginWithOtt(
        baseUrl: String,
        email: String,
        ott: String,
        password: String,
    ): FfiAccountLoginResult {
        loginFailure?.let { throw it }
        lastBaseUrl = baseUrl
        lastOtt = ott
        ottLoginCalls += 1
        return loginResult
    }

    override fun recover(
        baseUrl: String,
        email: String,
        ott: String,
        recoveryKey: String,
        newPassword: String,
    ): FfiAccountLoginResult {
        recoverFailure?.let { throw it }
        lastBaseUrl = baseUrl
        lastOtt = ott
        return loginResult
    }

    override fun logout(baseUrl: String, authToken: String) = Unit
}

private fun sampleFfiLoginResult(): FfiAccountLoginResult = FfiAccountLoginResult(
    userId = 1,
    email = "viewer@example.com",
    username = "viewer",
    authToken = "token",
    masterKey = byteArrayOf(1, 2),
    secretKey = byteArrayOf(3, 4),
    publicKey = byteArrayOf(5, 6),
    recoveryKey = null,
)
