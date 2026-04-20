package io.ente.entegram.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ente.entegram.core.logging.AppLogger
import io.ente.entegram.core.services.AssetCache
import io.ente.entegram.core.services.AuthClient
import io.ente.entegram.core.services.AuthSessionStore
import io.ente.entegram.core.services.PersistedAuthSession
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppSessionUiState {
    data object Loading : AppSessionUiState
    data object SignedOut : AppSessionUiState
    data class Authenticated(val session: PersistedAuthSession) : AppSessionUiState
}

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val authSessionStore: AuthSessionStore,
    private val authClient: AuthClient,
    private val assetCache: AssetCache,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppSessionUiState>(AppSessionUiState.Loading)
    val uiState: StateFlow<AppSessionUiState> = _uiState.asStateFlow()

    init {
        refreshSession()
    }

    fun refreshSession() {
        viewModelScope.launch {
            _uiState.value = AppSessionUiState.Loading
            val session = authSessionStore.read()
            _uiState.value = session?.let(AppSessionUiState::Authenticated)
                ?: AppSessionUiState.SignedOut
            AppLogger.i(
                "Session",
                if (session != null) "restored persisted session" else "no persisted session",
            )
        }
    }

    fun signOut() {
        val current = _uiState.value as? AppSessionUiState.Authenticated ?: return
        viewModelScope.launch {
            _uiState.value = AppSessionUiState.Loading
            AppLogger.i("Session", "sign out started")
            runCatching {
                authClient.signOut(current.session.sessionToken)
            }.onSuccess {
                AppLogger.i("Session", "sign out completed")
            }.getOrElse { error ->
                // If remote logout fails, still clear the local session so relaunch
                // does not silently re-enter the signed-in surface.
                AppLogger.w("Session", "remote sign out failed; clearing local session", error)
                authSessionStore.clear()
            }
            assetCache.clear()
            _uiState.value = AppSessionUiState.SignedOut
        }
    }
}
