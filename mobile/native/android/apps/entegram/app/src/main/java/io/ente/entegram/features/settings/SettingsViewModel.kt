package io.ente.entegram.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ente.entegram.core.logging.AppLogger
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.Wall
import io.ente.entegram.core.services.WallClient
import io.ente.entegram.core.services.WallClientException
import io.ente.entegram.ui.components.invalidateAvatarCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsUiState {
    data object Loading : SettingsUiState
    data class Ready(
        val wall: Wall,
        val ownPosts: List<Post>,
        val appVersion: String,
    ) : SettingsUiState
    data class Error(val message: String) : SettingsUiState
    data object Empty : SettingsUiState
}

data class SettingsProfileEditUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val wallClient: WallClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<SettingsUiState>(SettingsUiState.Loading)
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _profileEditState = MutableStateFlow(SettingsProfileEditUiState())
    val profileEditState: StateFlow<SettingsProfileEditUiState> = _profileEditState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = SettingsUiState.Loading
            try {
                val walls = wallClient.listOwnedWalls()
                val viewerWall = walls.firstOrNull()
                if (viewerWall == null) {
                    _uiState.value = SettingsUiState.Empty
                    return@launch
                }
                val page = wallClient.listWallPosts(
                    wallId = viewerWall.id,
                    cursor = null,
                    limit = 9,
                )
                _uiState.value = SettingsUiState.Ready(
                    wall = viewerWall,
                    ownPosts = page.items,
                    appVersion = "0.1.0 (1)",
                )
                AppLogger.i("Settings", "settings loaded")
            } catch (e: Exception) {
                AppLogger.w("Settings", "failed to load settings", e)
                _uiState.value = SettingsUiState.Error(
                    e.message ?: "Failed to load settings",
                )
            }
        }
    }

    fun retry() {
        loadSettings()
    }

    fun clearProfileEditError() {
        _profileEditState.update { it.copy(errorMessage = null) }
    }

    fun saveProfile(
        displayName: String?,
        bio: String?,
        avatarJpeg: ByteArray?,
        removeAvatar: Boolean,
        onSuccess: () -> Unit,
    ) {
        if (_profileEditState.value.isSaving) {
            return
        }
        val currentState = _uiState.value as? SettingsUiState.Ready ?: return
        val normalizedDisplayName = displayName?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedBio = bio?.trim()?.takeIf { it.isNotEmpty() }
        val currentWall = currentState.wall

        val hasChanges =
            normalizedDisplayName != currentWall.displayName ||
                normalizedBio != currentWall.bio ||
                avatarJpeg != null ||
                (removeAvatar && currentWall.avatarObjectKey != null)
        if (!hasChanges) {
            onSuccess()
            return
        }

        viewModelScope.launch {
            _profileEditState.value = SettingsProfileEditUiState(isSaving = true)
            runCatching {
                var updatedWall = currentWall
                if (
                    normalizedDisplayName != updatedWall.displayName ||
                    normalizedBio != updatedWall.bio
                ) {
                    updatedWall = wallClient.updateWallProfile(
                        wallId = updatedWall.id,
                        displayName = normalizedDisplayName,
                        bio = normalizedBio,
                    )
                }
                if (removeAvatar && updatedWall.avatarObjectKey != null) {
                    updatedWall = wallClient.removeAvatar(updatedWall.id)
                } else if (avatarJpeg != null) {
                    updatedWall = wallClient.uploadAvatar(updatedWall.id, avatarJpeg)
                }
                updatedWall
            }.onSuccess { updatedWall ->
                if (avatarJpeg != null || removeAvatar) {
                    invalidateAvatarCache(updatedWall.slug)
                }
                _uiState.value = currentState.copy(wall = updatedWall)
                _profileEditState.value = SettingsProfileEditUiState()
                AppLogger.i("Settings", "profile updated")
                onSuccess()
            }.onFailure { error ->
                AppLogger.w("Settings", "profile update failed", error)
                _profileEditState.value = SettingsProfileEditUiState(
                    errorMessage = profileEditMessage(error),
                )
            }
        }
    }

    private fun profileEditMessage(error: Throwable): String {
        return when (error) {
            is WallClientException.Conflict -> error.message ?: "That handle is already taken."
            else -> error.message ?: "Couldn't update your profile."
        }
    }
}
