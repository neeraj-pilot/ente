package io.ente.entegram.app

import io.ente.entegram.core.models.LoginResult
import io.ente.entegram.core.services.AssetCache
import io.ente.entegram.core.services.AuthClient
import io.ente.entegram.core.services.AuthOttPurpose
import io.ente.entegram.core.services.AuthSessionStore
import io.ente.entegram.core.services.LoginFlowDecision
import io.ente.entegram.core.services.PersistedAuthSession
import io.ente.entegram.core.services.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSessionViewModelTest {

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init restores persisted session into authenticated state`() = runTest {
        val persisted = PersistedAuthSession.from(SampleData.viewerLoginResult)

        val viewModel = AppSessionViewModel(
            authSessionStore = FakeAuthSessionStore(persisted),
            authClient = FakeAuthClient(),
            assetCache = FakeAssetCache(),
        )
        advanceUntilIdle()

        assertEquals(
            AppSessionUiState.Authenticated(persisted),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `init without persisted session stays signed out`() = runTest {
        val viewModel = AppSessionViewModel(
            authSessionStore = FakeAuthSessionStore(),
            authClient = FakeAuthClient(),
            assetCache = FakeAssetCache(),
        )
        advanceUntilIdle()

        assertEquals(AppSessionUiState.SignedOut, viewModel.uiState.value)
    }

    @Test
    fun `sign out clears local session when remote logout fails`() = runTest {
        val store = FakeAuthSessionStore(PersistedAuthSession.from(SampleData.viewerLoginResult))
        val assetCache = FakeAssetCache()
        val viewModel = AppSessionViewModel(
            authSessionStore = store,
            authClient = FakeAuthClient(signOutFailure = IllegalStateException("boom")),
            assetCache = assetCache,
        )
        advanceUntilIdle()

        viewModel.signOut()
        advanceUntilIdle()

        assertNull(store.persisted)
        assertEquals(true, assetCache.cleared)
        assertEquals(AppSessionUiState.SignedOut, viewModel.uiState.value)
    }
}

private class FakeAssetCache : AssetCache {
    var cleared = false

    override suspend fun read(cacheKey: String): ByteArray? = null

    override suspend fun write(cacheKey: String, bytes: ByteArray) = Unit

    override suspend fun clear() {
        cleared = true
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
    private val signOutFailure: Throwable? = null,
) : AuthClient {
    override suspend fun sendOtt(email: String, purpose: AuthOttPurpose) = Unit

    override suspend fun loginPreflight(email: String): LoginFlowDecision = LoginFlowDecision.PasswordOnly

    override suspend fun signup(
        email: String,
        password: String,
        code: String,
    ): LoginResult = SampleData.viewerLoginResult

    override suspend fun login(email: String, password: String, code: String?): LoginResult {
        return SampleData.viewerLoginResult
    }

    override suspend fun recoverAccount(
        email: String,
        code: String,
        recoveryKey: String,
        newPassword: String,
    ): LoginResult = SampleData.viewerLoginResult

    override suspend fun signOut(sessionToken: String?) {
        signOutFailure?.let { throw it }
    }
}
