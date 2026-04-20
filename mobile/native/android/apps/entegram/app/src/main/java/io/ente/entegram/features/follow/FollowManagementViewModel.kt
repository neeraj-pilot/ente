package io.ente.entegram.features.follow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ente.entegram.core.models.FollowRequest
import io.ente.entegram.core.services.WallClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FollowTab { Followers, Following, Received, Sent }

enum class DismissReason { Approved, Rejected, Cancelled }

/**
 * Two-phase row dismissal: tinting (flash color visible, row still in place)
 * then sliding (exit animation triggers, row shrinks away).
 */
data class DismissState(
    val reason: DismissReason,
    val sliding: Boolean = false,
)

sealed interface FollowUiState {
    data object Loading : FollowUiState
    data object Empty : FollowUiState
    data class Ready(
        val incoming: List<FollowRequest>,
        val outgoing: List<FollowRequest>,
        val followers: List<FollowRequest>,
        val following: List<FollowRequest>,
        val selectedTab: FollowTab = FollowTab.Followers,
        val processingIds: Set<Long> = emptySet(),
        val dismissingIds: Map<Long, DismissState> = emptyMap(),
    ) : FollowUiState
    data class Error(val message: String) : FollowUiState
}

@HiltViewModel
class FollowManagementViewModel @Inject constructor(
    private val wallClient: WallClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FollowUiState>(FollowUiState.Loading)
    val uiState: StateFlow<FollowUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun selectTab(tab: FollowTab) {
        _uiState.update { state ->
            if (state is FollowUiState.Ready) state.copy(selectedTab = tab) else state
        }
    }

    fun refresh() {
        loadAll()
    }

    fun approve(requestId: Long) {
        val current = _uiState.value as? FollowUiState.Ready ?: return
        if (requestId in current.processingIds) return

        _uiState.update { (it as FollowUiState.Ready).copy(processingIds = it.processingIds + requestId) }

        viewModelScope.launch {
            try {
                wallClient.approveFollowRequest(requestId)
                // Phase 1: tint flash — row stays in place, background tints
                _uiState.update { state ->
                    if (state !is FollowUiState.Ready) return@update state
                    state.copy(
                        processingIds = state.processingIds - requestId,
                        dismissingIds = state.dismissingIds + (requestId to DismissState(DismissReason.Approved)),
                    )
                }
                delay(TINT_FLASH_MS)
                // Phase 2: trigger exit animation (slide + shrink)
                _uiState.update { state ->
                    if (state !is FollowUiState.Ready) return@update state
                    val ds = state.dismissingIds[requestId] ?: return@update state
                    state.copy(
                        dismissingIds = state.dismissingIds + (requestId to ds.copy(sliding = true)),
                    )
                }
                delay(ROW_EXIT_MS)
                // Remove from list
                _uiState.update { state ->
                    if (state !is FollowUiState.Ready) return@update state
                    val approved = state.incoming.find { it.id == requestId }
                    val newIncoming = state.incoming.filter { it.id != requestId }
                    val newFollowers = if (approved != null) {
                        state.followers + approved
                    } else {
                        state.followers
                    }
                    state.copy(
                        incoming = newIncoming,
                        followers = newFollowers,
                        dismissingIds = state.dismissingIds - requestId,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { state ->
                    if (state is FollowUiState.Ready) {
                        state.copy(
                            processingIds = state.processingIds - requestId,
                            dismissingIds = state.dismissingIds - requestId,
                        )
                    } else state
                }
            }
        }
    }

    fun reject(requestId: Long) {
        val current = _uiState.value as? FollowUiState.Ready ?: return
        if (requestId in current.processingIds) return

        _uiState.update { (it as FollowUiState.Ready).copy(processingIds = it.processingIds + requestId) }

        viewModelScope.launch {
            try {
                wallClient.rejectFollowRequest(requestId)
                // Phase 1: tint flash
                _uiState.update { state ->
                    if (state !is FollowUiState.Ready) return@update state
                    state.copy(
                        processingIds = state.processingIds - requestId,
                        dismissingIds = state.dismissingIds + (requestId to DismissState(DismissReason.Rejected)),
                    )
                }
                delay(TINT_FLASH_MS)
                // Phase 2: slide out
                _uiState.update { state ->
                    if (state !is FollowUiState.Ready) return@update state
                    val ds = state.dismissingIds[requestId] ?: return@update state
                    state.copy(
                        dismissingIds = state.dismissingIds + (requestId to ds.copy(sliding = true)),
                    )
                }
                delay(ROW_EXIT_MS)
                _uiState.update { state ->
                    if (state !is FollowUiState.Ready) return@update state
                    state.copy(
                        incoming = state.incoming.filter { it.id != requestId },
                        dismissingIds = state.dismissingIds - requestId,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { state ->
                    if (state is FollowUiState.Ready) {
                        state.copy(
                            processingIds = state.processingIds - requestId,
                            dismissingIds = state.dismissingIds - requestId,
                        )
                    } else state
                }
            }
        }
    }

    fun cancelSent(requestId: Long) {
        val current = _uiState.value as? FollowUiState.Ready ?: return
        if (requestId in current.processingIds) return

        _uiState.update { (it as FollowUiState.Ready).copy(processingIds = it.processingIds + requestId) }

        viewModelScope.launch {
            try {
                wallClient.cancelFollowRequest(requestId)
                // Phase 1: tint flash
                _uiState.update { state ->
                    if (state !is FollowUiState.Ready) return@update state
                    state.copy(
                        processingIds = state.processingIds - requestId,
                        dismissingIds = state.dismissingIds + (requestId to DismissState(DismissReason.Cancelled)),
                    )
                }
                delay(TINT_FLASH_MS)
                // Phase 2: slide out
                _uiState.update { state ->
                    if (state !is FollowUiState.Ready) return@update state
                    val ds = state.dismissingIds[requestId] ?: return@update state
                    state.copy(
                        dismissingIds = state.dismissingIds + (requestId to ds.copy(sliding = true)),
                    )
                }
                delay(ROW_EXIT_MS)
                _uiState.update { state ->
                    if (state !is FollowUiState.Ready) return@update state
                    state.copy(
                        outgoing = state.outgoing.filter { it.id != requestId },
                        dismissingIds = state.dismissingIds - requestId,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { state ->
                    if (state is FollowUiState.Ready) {
                        state.copy(
                            processingIds = state.processingIds - requestId,
                            dismissingIds = state.dismissingIds - requestId,
                        )
                    } else state
                }
            }
        }
    }

    companion object {
        /** Delay (ms) for the tint flash to register visually before exit animation starts. */
        const val TINT_FLASH_MS = 220L
        /** Delay (ms) for the exit slide/shrink animation to complete after tint flash. */
        const val ROW_EXIT_MS = 400L
    }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.value = FollowUiState.Loading
            try {
                val incoming = wallClient.listFollowRequests(FollowRequest.Direction.Incoming)
                val outgoing = wallClient.listFollowRequests(FollowRequest.Direction.Outgoing)
                val ownedWallId = wallClient.listOwnedWalls().firstOrNull()?.id
                val followers = if (ownedWallId != null) {
                    wallClient.listFollowers(ownedWallId)
                } else {
                    emptyList()
                }
                val following = wallClient.listFollowing()

                if (incoming.isEmpty() && outgoing.isEmpty() && followers.isEmpty() && following.isEmpty()) {
                    _uiState.value = FollowUiState.Empty
                } else {
                    _uiState.value = FollowUiState.Ready(
                        incoming = incoming,
                        outgoing = outgoing,
                        followers = followers,
                        following = following,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = FollowUiState.Error(
                    e.message ?: "Something went wrong",
                )
            }
        }
    }
}
