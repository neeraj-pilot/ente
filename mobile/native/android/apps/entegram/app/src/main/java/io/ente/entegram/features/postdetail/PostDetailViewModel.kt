package io.ente.entegram.features.postdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ente.entegram.app.PostDetailRoute
import io.ente.entegram.core.models.Comment
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.services.FeedRepository
import io.ente.entegram.core.services.WallClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PostDetailUiState {
    data object Loading : PostDetailUiState
    data object Empty : PostDetailUiState
    data class Ready(
        val post: Post,
        val comments: List<Comment>,
        val viewerWallSlugs: Set<String> = emptySet(),
        val viewerSlug: String? = null,
        val isLoadingComments: Boolean = false,
        val isLoadingMoreComments: Boolean = false,
        val hasMoreComments: Boolean = true,
        val isSendingComment: Boolean = false,
        val deletingCommentId: Long? = null,
    ) : PostDetailUiState
    data class Error(val message: String) : PostDetailUiState
}

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val wallClient: WallClient,
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val postId: Long = savedStateHandle.toRoute<PostDetailRoute>().postId

    private val _uiState = MutableStateFlow<PostDetailUiState>(PostDetailUiState.Loading)
    val uiState: StateFlow<PostDetailUiState> = _uiState.asStateFlow()

    private var commentsCursor: String? = null

    init {
        loadPost()
    }

    fun retry() {
        _uiState.value = PostDetailUiState.Loading
        loadPost()
    }

    fun toggleLike() {
        val current = _uiState.value
        if (current !is PostDetailUiState.Ready) return

        val post = current.post
        val nowLiked = !post.viewerLiked
        val newCount = if (nowLiked) post.likeCount + 1 else post.likeCount - 1

        val updatedPost = post.copy(
            viewerLiked = nowLiked,
            likeCount = newCount.coerceAtLeast(0),
        )
        _uiState.update { (it as PostDetailUiState.Ready).copy(post = updatedPost) }

        viewModelScope.launch {
            try {
                if (nowLiked) wallClient.likePost(postId) else wallClient.unlikePost(postId)
            } catch (_: Exception) {
                _uiState.update { state ->
                    if (state is PostDetailUiState.Ready) {
                        state.copy(post = post)
                    } else state
                }
            }
        }
    }

    fun loadMoreComments() {
        val current = _uiState.value
        if (current !is PostDetailUiState.Ready) return
        if (current.isLoadingMoreComments || !current.hasMoreComments) return

        _uiState.update { (it as PostDetailUiState.Ready).copy(isLoadingMoreComments = true) }

        viewModelScope.launch {
            try {
                val page = wallClient.listComments(postId, commentsCursor, COMMENT_PAGE_SIZE)
                commentsCursor = page.nextCursor
                _uiState.update {
                    (it as PostDetailUiState.Ready).copy(
                        comments = current.comments + page.items,
                        isLoadingMoreComments = false,
                        hasMoreComments = page.nextCursor != null,
                    )
                }
            } catch (_: Exception) {
                _uiState.update {
                    (it as PostDetailUiState.Ready).copy(isLoadingMoreComments = false)
                }
            }
        }
    }

    fun sendComment(text: String) {
        val current = _uiState.value
        if (current !is PostDetailUiState.Ready) return
        if (text.isBlank() || current.isSendingComment) return

        _uiState.update { (it as PostDetailUiState.Ready).copy(isSendingComment = true) }

        viewModelScope.launch {
            try {
                val comment = wallClient.createComment(postId, parentId = null, text = text.trim())
                _uiState.update { state ->
                    if (state is PostDetailUiState.Ready) {
                        state.copy(
                            post = state.post.copy(commentCount = state.post.commentCount + 1),
                            comments = state.comments + comment,
                            isSendingComment = false,
                        )
                    } else state
                }
            } catch (_: Exception) {
                _uiState.update { state ->
                    if (state is PostDetailUiState.Ready) {
                        state.copy(isSendingComment = false)
                    } else state
                }
            }
        }
    }

    fun deleteComment(commentId: Long) {
        val current = _uiState.value
        if (current !is PostDetailUiState.Ready) return
        if (current.deletingCommentId != null) return

        _uiState.update { (it as PostDetailUiState.Ready).copy(deletingCommentId = commentId) }

        viewModelScope.launch {
            try {
                wallClient.deleteComment(commentId)
                val post = wallClient.fetchPost(postId)
                val commentsPage = wallClient.listComments(postId, cursor = null, limit = COMMENT_PAGE_SIZE)
                commentsCursor = commentsPage.nextCursor
                _uiState.update { state ->
                    if (state is PostDetailUiState.Ready) {
                        state.copy(
                            post = post,
                            comments = commentsPage.items,
                            hasMoreComments = commentsPage.nextCursor != null,
                            deletingCommentId = null,
                        )
                    } else state
                }
            } catch (_: Exception) {
                _uiState.update { state ->
                    if (state is PostDetailUiState.Ready) {
                        state.copy(deletingCommentId = null)
                    } else state
                }
            }
        }
    }

    fun deletePost(onDeleted: () -> Unit) {
        viewModelScope.launch {
            runCatching {
                feedRepository.deletePost(postId)
            }.onSuccess {
                onDeleted()
            }
        }
    }

    private fun loadPost() {
        viewModelScope.launch {
            try {
                val ownedWalls = wallClient.listOwnedWalls()
                val viewerWallSlugs = ownedWalls.map { it.slug }.toSet()
                val viewerSlug = ownedWalls.firstOrNull()?.slug
                val post = wallClient.fetchPost(postId)
                val commentsPage = wallClient.listComments(postId, cursor = null, limit = COMMENT_PAGE_SIZE)
                commentsCursor = commentsPage.nextCursor
                _uiState.value = PostDetailUiState.Ready(
                    post = post,
                    comments = commentsPage.items,
                    viewerWallSlugs = viewerWallSlugs,
                    viewerSlug = viewerSlug,
                    hasMoreComments = commentsPage.nextCursor != null,
                )
            } catch (e: Exception) {
                _uiState.value = PostDetailUiState.Error(
                    e.message ?: "Couldn't load this post",
                )
            }
        }
    }

    companion object {
        private const val COMMENT_PAGE_SIZE = 30
    }
}
