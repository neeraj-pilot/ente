package io.ente.entegram.features.wall

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.Wall
import io.ente.entegram.core.models.FollowRequest
import io.ente.entegram.core.services.WallClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface WallUiState {
    data object Loading : WallUiState
    data object Empty : WallUiState
    data class Ready(
        val wall: Wall,
        val posts: List<Post>,
        val relationship: String = "idle",
        val accessDenied: Boolean = false,
        val isFollowActionLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
    ) : WallUiState

    data class Error(val message: String) : WallUiState
}

@HiltViewModel
class WallViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wallClient: WallClient,
) : ViewModel() {

    private val slug: String = checkNotNull(savedStateHandle["slug"])

    private val _uiState = MutableStateFlow<WallUiState>(WallUiState.Loading)
    val uiState: StateFlow<WallUiState> = _uiState.asStateFlow()

    private var nextCursor: String? = null

    init {
        loadWall()
    }

    fun refresh() {
        val current = _uiState.value
        if (current is WallUiState.Ready && current.isRefreshing) return

        if (current is WallUiState.Ready) {
            _uiState.update { (it as WallUiState.Ready).copy(isRefreshing = true) }
        }

        viewModelScope.launch {
            try {
                val loaded = loadWallState()
                nextCursor = loaded.nextCursor
                if (loaded.posts.isEmpty() && !loaded.accessDenied) {
                    _uiState.value = WallUiState.Empty
                } else {
                    _uiState.value = WallUiState.Ready(
                        wall = loaded.wall,
                        posts = loaded.posts,
                        relationship = loaded.relationship,
                        accessDenied = loaded.accessDenied,
                        hasMore = loaded.nextCursor != null,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WallUiState.Error(
                    e.message ?: "Something went wrong",
                )
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current !is WallUiState.Ready) return
        if (current.isLoadingMore || !current.hasMore || current.accessDenied) return

        _uiState.update { (it as WallUiState.Ready).copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                val page = wallClient.listWallPosts(
                    current.wall.id,
                    cursor = nextCursor,
                    limit = PAGE_SIZE,
                )
                nextCursor = page.nextCursor
                _uiState.update {
                    (it as WallUiState.Ready).copy(
                        posts = current.posts + page.items,
                        isLoadingMore = false,
                        hasMore = page.nextCursor != null,
                    )
                }
            } catch (_: Exception) {
                _uiState.update { (it as WallUiState.Ready).copy(isLoadingMore = false) }
            }
        }
    }

    private fun loadWall() {
        viewModelScope.launch {
            try {
                val loaded = loadWallState()
                nextCursor = loaded.nextCursor
                if (loaded.posts.isEmpty() && !loaded.accessDenied) {
                    _uiState.value = WallUiState.Empty
                } else {
                    _uiState.value = WallUiState.Ready(
                        wall = loaded.wall,
                        posts = loaded.posts,
                        relationship = loaded.relationship,
                        accessDenied = loaded.accessDenied,
                        hasMore = loaded.nextCursor != null,
                    )
                }
            } catch (e: Exception) {
                _uiState.value = WallUiState.Error(
                    e.message ?: "Something went wrong",
                )
            }
        }
    }

    fun requestFollow() {
        val current = _uiState.value as? WallUiState.Ready ?: return
        if (current.isFollowActionLoading || current.relationship != "idle") return

        _uiState.update {
            (it as WallUiState.Ready).copy(isFollowActionLoading = true)
        }
        viewModelScope.launch {
            try {
                wallClient.requestFollow(current.wall.id)
                _uiState.update {
                    (it as WallUiState.Ready).copy(
                        relationship = "pending",
                        isFollowActionLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    (it as WallUiState.Ready).copy(isFollowActionLoading = false)
                }
                _uiState.value = WallUiState.Error(
                    e.message ?: "Couldn't send follow request",
                )
            }
        }
    }

    private suspend fun loadWallState(): LoadedWallState {
        val wall = wallClient.wall(slug) ?: throw IllegalStateException("Wall not found")
        return try {
            val fullWall = wallClient.fetchWall(slug)
            val page = wallClient.listWallPosts(fullWall.id, cursor = null, limit = PAGE_SIZE)
            val relationship = resolveAccessibleRelationship(fullWall)
            LoadedWallState(
                wall = fullWall,
                posts = page.items,
                relationship = relationship,
                accessDenied = false,
                nextCursor = page.nextCursor,
            )
        } catch (error: Exception) {
            if (error.message?.contains("wall access denied", ignoreCase = true) == true ||
                error.message?.contains("403") == true
            ) {
                val relationship = resolveLockedRelationship(wall)
                LoadedWallState(
                    wall = wall,
                    posts = emptyList(),
                    relationship = relationship,
                    accessDenied = true,
                    nextCursor = null,
                )
            } else {
                throw error
            }
        }
    }

    private suspend fun resolveAccessibleRelationship(wall: Wall): String {
        if (wallClient.listOwnedWalls().any { it.id == wall.id }) {
            return "self"
        }
        return "following"
    }

    private suspend fun resolveLockedRelationship(wall: Wall): String {
        if (wallClient.listOwnedWalls().any { it.id == wall.id }) {
            return "self"
        }
        return wallClient.listFollowRequests(FollowRequest.Direction.Outgoing)
            .firstOrNull { it.wallId == wall.id }
            ?.let { "pending" }
            ?: "idle"
    }

    private data class LoadedWallState(
        val wall: Wall,
        val posts: List<Post>,
        val relationship: String,
        val accessDenied: Boolean,
        val nextCursor: String?,
    )

    companion object {
        private const val PAGE_SIZE = 30
    }
}
