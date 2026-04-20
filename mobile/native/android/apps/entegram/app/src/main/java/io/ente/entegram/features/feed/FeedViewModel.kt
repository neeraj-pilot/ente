package io.ente.entegram.features.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ente.entegram.core.logging.AppLogger
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.Wall
import io.ente.entegram.core.services.ConnectivityObserver
import io.ente.entegram.core.services.FeedRepository
import io.ente.entegram.core.services.WallClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface FeedUiState {
    data object Loading : FeedUiState
    data object Empty : FeedUiState
    data class Ready(
        val posts: List<Post>,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val hasMore: Boolean = true,
        val isOffline: Boolean = false,
    ) : FeedUiState
    data class Error(val message: String) : FeedUiState
}

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val feedRepository: FeedRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val wallClient: WallClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeedUiState>(FeedUiState.Loading)
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private val _viewerWall = MutableStateFlow<Wall?>(null)
    val viewerWall: StateFlow<Wall?> = _viewerWall.asStateFlow()

    private var nextCursor: String? = null

    init {
        observeCache()
        observeConnectivity()
        loadFeed()
        loadViewerWall()
    }

    private fun loadViewerWall() {
        viewModelScope.launch {
            runCatching {
                wallClient.listOwnedWalls().firstOrNull()
            }.onSuccess { wall ->
                _viewerWall.value = wall
            }.onFailure { error ->
                AppLogger.w("Feed", "failed to load viewer wall", error)
            }
        }
    }

    fun refresh() {
        val current = _uiState.value
        if (current is FeedUiState.Ready && current.isRefreshing) return

        if (current is FeedUiState.Ready) {
            _uiState.update { (it as FeedUiState.Ready).copy(isRefreshing = true) }
        }

        viewModelScope.launch {
            try {
                nextCursor = feedRepository.revalidate()
                AppLogger.i("Feed", "feed refreshed")
                // The Room Flow observer will emit the fresh posts automatically.
                // We only need to clear the refreshing flag here.
                _uiState.update { state ->
                    when (state) {
                        is FeedUiState.Ready -> state.copy(
                            isRefreshing = false,
                            hasMore = nextCursor != null,
                        )
                        // If we were in Loading/Error and refresh succeeded, the cache
                        // observer will push the state to Ready (or Empty if no posts).
                        else -> state
                    }
                }
            } catch (e: Exception) {
                AppLogger.w("Feed", "feed refresh failed", e)
                val current2 = _uiState.value
                if (current2 is FeedUiState.Ready) {
                    // Keep showing stale data, just clear refreshing
                    _uiState.update { (it as FeedUiState.Ready).copy(isRefreshing = false) }
                } else {
                    _uiState.value = FeedUiState.Error(
                        e.message ?: "Something went wrong",
                    )
                }
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current !is FeedUiState.Ready) return
        if (current.isLoadingMore || !current.hasMore) return
        val cursor = nextCursor ?: return

        _uiState.update { (it as FeedUiState.Ready).copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                val result = feedRepository.loadMore(cursor)
                nextCursor = result.nextCursor
                AppLogger.i("Feed", "loaded more feed posts")
                // The Room Flow observer will emit updated posts.
                // We just clear the loading flag and update hasMore.
                _uiState.update { state ->
                    if (state is FeedUiState.Ready) {
                        state.copy(
                            isLoadingMore = false,
                            hasMore = result.nextCursor != null,
                        )
                    } else state
                }
            } catch (e: Exception) {
                AppLogger.w("Feed", "load more failed", e)
                _uiState.update {
                    if (it is FeedUiState.Ready) it.copy(isLoadingMore = false) else it
                }
            }
        }
    }

    fun toggleLike(postId: Long) {
        viewModelScope.launch {
            feedRepository.toggleLike(postId)
            // Room Flow observer picks up the change automatically
        }
    }

    fun deletePost(postId: Long) {
        viewModelScope.launch {
            runCatching {
                feedRepository.deletePost(postId)
            }.onSuccess {
                AppLogger.i("Feed", "deleted post")
            }.onFailure { error ->
                AppLogger.w("Feed", "failed to delete post", error)
            }
        }
    }

    /**
     * Observe the Room cache. This is the single source of truth for the post list.
     * Network fetches write into Room, and this Flow emits the updated list.
     */
    private fun observeCache() {
        viewModelScope.launch {
            feedRepository.cachedFeed().collect { posts ->
                val current = _uiState.value
                when {
                    posts.isEmpty() && current is FeedUiState.Loading -> {
                        // Still loading from network — don't flip to Empty yet
                    }
                    posts.isEmpty() && current !is FeedUiState.Loading -> {
                        _uiState.value = FeedUiState.Empty
                    }
                    else -> {
                        _uiState.update { state ->
                            when (state) {
                                is FeedUiState.Ready -> state.copy(posts = posts)
                                else -> FeedUiState.Ready(
                                    posts = posts,
                                    hasMore = nextCursor != null,
                                    isOffline = !connectivityObserver.isOnline,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Mirror connectivity state into the Ready state so the UI can show an offline banner.
     * When connectivity returns, trigger a background revalidation.
     */
    private fun observeConnectivity() {
        viewModelScope.launch {
            connectivityObserver.observe().collect { online ->
                _uiState.update { state ->
                    if (state is FeedUiState.Ready) {
                        state.copy(isOffline = !online)
                    } else state
                }
                // Auto-refresh when connectivity returns
                if (online) {
                    val current = _uiState.value
                    if (current is FeedUiState.Ready && !current.isRefreshing) {
                        refresh()
                    }
                }
            }
        }
    }

    /**
     * Initial load: serve cached posts immediately (via the Flow observer),
     * then revalidate from the network in the background.
     */
    private fun loadFeed() {
        viewModelScope.launch {
            try {
                // If we have cached data, the Flow observer already emitted it.
                // If not, we stay in Loading state until the network responds.
                val hadCache = feedRepository.hasCachedPosts()

                nextCursor = feedRepository.revalidate()
                AppLogger.i("Feed", "initial feed load completed")

                // If cache was empty before revalidation, and still empty after,
                // the Flow observer won't emit (no change) — so set Empty explicitly.
                if (!hadCache && !feedRepository.hasCachedPosts()) {
                    _uiState.value = FeedUiState.Empty
                }
            } catch (e: Exception) {
                AppLogger.w("Feed", "initial feed load failed", e)
                // If we have cached data, the Flow observer already showed it.
                // Only show error if there's nothing cached.
                if (!feedRepository.hasCachedPosts()) {
                    _uiState.value = FeedUiState.Error(
                        e.message ?: "Something went wrong",
                    )
                }
                // Otherwise: stale cache is visible, network failed silently.
            }
        }
    }

    companion object {
        private const val PAGE_SIZE = 20
    }
}
