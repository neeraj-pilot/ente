package io.ente.entegram.features.compose

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ente.entegram.core.services.FeedRepository
import io.ente.entegram.core.services.CreatePostInput
import io.ente.entegram.core.services.WallClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SelectedPhoto(
    val uri: Uri,
    val fullData: ByteArray,
    val thumbnailData: ByteArray,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val blurHash: String = "",
) {
    val exceedsLimit: Boolean get() = sizeBytes > MAX_PHOTO_BYTES

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SelectedPhoto) return false
        return uri == other.uri
    }

    override fun hashCode(): Int = uri.hashCode()

    companion object {
        const val MAX_PHOTO_BYTES = 2L * 1024 * 1024
    }
}

sealed interface ComposerUiState {
    data object Empty : ComposerUiState
    data class Drafting(
        val photos: List<SelectedPhoto>,
        val activeIndex: Int = 0,
        val caption: String = "",
        val isCaptionFocused: Boolean = false,
    ) : ComposerUiState {
        val canPost: Boolean
            get() = photos.isNotEmpty() &&
                photos.all { !it.exceedsLimit } &&
                photos.size <= MAX_PHOTOS

        val hasOversize: Boolean
            get() = photos.any { it.exceedsLimit }

        val oversizeIndices: Set<Int>
            get() = photos.indices.filter { photos[it].exceedsLimit }.toSet()
    }

    data class Uploading(
        val photos: List<SelectedPhoto>,
        val activeIndex: Int,
        val caption: String,
    ) : ComposerUiState

    data class Success(val postId: Long) : ComposerUiState

    data class Error(
        val photos: List<SelectedPhoto>,
        val activeIndex: Int,
        val caption: String,
        val message: String,
    ) : ComposerUiState
}

const val MAX_PHOTOS = 10

@HiltViewModel
class PostComposerViewModel @Inject constructor(
    private val wallClient: WallClient,
    private val feedRepository: FeedRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ComposerUiState>(ComposerUiState.Empty)
    val uiState: StateFlow<ComposerUiState> = _uiState.asStateFlow()

    fun addPhotos(photos: List<SelectedPhoto>) {
        val current = _uiState.value
        val existingPhotos = when (current) {
            is ComposerUiState.Empty -> emptyList()
            is ComposerUiState.Drafting -> current.photos
            is ComposerUiState.Error -> current.photos
            else -> return
        }
        val caption = when (current) {
            is ComposerUiState.Drafting -> current.caption
            is ComposerUiState.Error -> current.caption
            else -> ""
        }
        val combined = (existingPhotos + photos).take(MAX_PHOTOS)
        _uiState.value = ComposerUiState.Drafting(
            photos = combined,
            activeIndex = existingPhotos.size.coerceAtMost(combined.size - 1),
            caption = caption,
        )
    }

    fun removePhoto(index: Int) {
        val current = _uiState.value
        if (current !is ComposerUiState.Drafting) return
        val newPhotos = current.photos.toMutableList().apply { removeAt(index) }
        if (newPhotos.isEmpty()) {
            _uiState.value = if (current.caption.isBlank()) {
                ComposerUiState.Empty
            } else {
                ComposerUiState.Drafting(
                    photos = emptyList(),
                    activeIndex = 0,
                    caption = current.caption,
                )
            }
        } else {
            _uiState.value = current.copy(
                photos = newPhotos,
                activeIndex = current.activeIndex.coerceAtMost(newPhotos.size - 1),
            )
        }
    }

    fun selectPhoto(index: Int) {
        _uiState.update { state ->
            if (state is ComposerUiState.Drafting) {
                state.copy(activeIndex = index.coerceIn(0, state.photos.size - 1))
            } else state
        }
    }

    fun movePhoto(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (state is ComposerUiState.Drafting) {
                val photos = state.photos.toMutableList()
                if (fromIndex in photos.indices && toIndex in photos.indices) {
                    val item = photos.removeAt(fromIndex)
                    photos.add(toIndex, item)
                    val newActive = when (state.activeIndex) {
                        fromIndex -> toIndex
                        in minOf(fromIndex, toIndex)..maxOf(fromIndex, toIndex) -> {
                            if (fromIndex < toIndex) state.activeIndex - 1
                            else state.activeIndex + 1
                        }
                        else -> state.activeIndex
                    }
                    state.copy(photos = photos, activeIndex = newActive)
                } else state
            } else state
        }
    }

    fun updateCaption(text: String) {
        _uiState.update { state ->
            when (state) {
                is ComposerUiState.Empty -> ComposerUiState.Drafting(
                    photos = emptyList(),
                    caption = text,
                )
                is ComposerUiState.Drafting -> state.copy(caption = text)
                else -> state
            }
        }
    }

    fun setCaptionFocus(focused: Boolean) {
        _uiState.update { state ->
            if (state is ComposerUiState.Drafting) state.copy(isCaptionFocused = focused)
            else state
        }
    }

    fun share() {
        val current = _uiState.value
        if (current !is ComposerUiState.Drafting || !current.canPost) return

        _uiState.value = ComposerUiState.Uploading(
            photos = current.photos,
            activeIndex = current.activeIndex,
            caption = current.caption,
        )

        viewModelScope.launch {
            try {
                val walls = wallClient.listOwnedWalls()
                val wallId = walls.firstOrNull()?.id ?: return@launch

                val images = current.photos.map { photo ->
                    CreatePostInput.Image(
                        fullData = photo.fullData,
                        thumbnailData = photo.thumbnailData,
                        width = photo.width,
                        height = photo.height,
                        blurHash = photo.blurHash,
                    )
                }

                val post = wallClient.createPost(
                    CreatePostInput(
                        wallId = wallId,
                        caption = current.caption,
                        images = images,
                    ),
                )
                feedRepository.cachePost(post)

                _uiState.value = ComposerUiState.Success(postId = post.id)
            } catch (e: Exception) {
                _uiState.value = ComposerUiState.Error(
                    photos = current.photos,
                    activeIndex = current.activeIndex,
                    caption = current.caption,
                    message = e.message ?: "Failed to share post",
                )
            }
        }
    }

    fun retry() {
        val current = _uiState.value
        if (current !is ComposerUiState.Error) return
        _uiState.value = ComposerUiState.Drafting(
            photos = current.photos,
            activeIndex = current.activeIndex,
            caption = current.caption,
        )
        share()
    }

    fun dismissError() {
        val current = _uiState.value
        if (current !is ComposerUiState.Error) return
        _uiState.value = ComposerUiState.Drafting(
            photos = current.photos,
            activeIndex = current.activeIndex,
            caption = current.caption,
        )
    }
}
