package io.ente.entegram.features.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.core.media.AvatarTranscoder
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.PostAssetVariant
import io.ente.entegram.core.models.Wall
import io.ente.entegram.core.services.SampleData
import io.ente.entegram.ui.components.AppTopBar
import io.ente.entegram.ui.components.EncryptedAssetImage
import io.ente.entegram.ui.components.ErrorStateView
import io.ente.entegram.ui.components.LoadingStateView
import io.ente.entegram.ui.components.ProfileStat
import io.ente.entegram.ui.components.ProfileSummaryHeader
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onPostTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val profileEditState by viewModel.profileEditState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = uiState,
        modifier = modifier,
        transitionSpec = {
            fadeIn(Motion.longFade()) togetherWith fadeOut(Motion.longFade())
        },
        contentKey = { state ->
            when (state) {
                is SettingsUiState.Loading -> "loading"
                is SettingsUiState.Ready -> "ready"
                is SettingsUiState.Error -> "error"
                is SettingsUiState.Empty -> "empty"
            }
        },
        label = "settingsState",
    ) { state ->
        when (state) {
            is SettingsUiState.Loading -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
                    LoadingStateView(modifier = Modifier.padding(top = Space.xxl))
                }
            }

            is SettingsUiState.Empty -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
                    LoadingStateView(modifier = Modifier.padding(top = Space.xxl))
                }
            }

            is SettingsUiState.Error -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    AppTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.retry() },
                    )
                }
            }

            is SettingsUiState.Ready -> {
                SettingsContent(
                    wall = state.wall,
                    ownPosts = state.ownPosts,
                    profileEditState = profileEditState,
                    onBack = onBack,
                    onPostTap = onPostTap,
                    onSaveProfile = viewModel::saveProfile,
                    onDismissProfileEditError = viewModel::clearProfileEditError,
                )
            }
        }
    }
}

@Composable
private fun SettingsContent(
    wall: Wall,
    ownPosts: List<Post>,
    profileEditState: SettingsProfileEditUiState,
    onBack: (() -> Unit)?,
    onPostTap: (Long) -> Unit,
    onSaveProfile: (
        displayName: String?,
        bio: String?,
        avatarJpeg: ByteArray?,
        removeAvatar: Boolean,
        onSuccess: () -> Unit,
    ) -> Unit,
    onDismissProfileEditError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showEditProfileDialog by remember(wall.id) { mutableStateOf(false) }
    var editDisplayName by remember(wall.id, wall.displayName) {
        mutableStateOf(wall.displayName.orEmpty())
    }
    var editBio by remember(wall.id, wall.bio) { mutableStateOf(wall.bio.orEmpty()) }
    var selectedAvatarJpeg by remember(wall.id) { mutableStateOf<ByteArray?>(null) }
    var selectedAvatarPreviewUri by remember(wall.id) { mutableStateOf<Uri?>(null) }
    var removeAvatarRequested by remember(wall.id) { mutableStateOf(false) }

    fun resetEditor() {
        editDisplayName = wall.displayName.orEmpty()
        editBio = wall.bio.orEmpty()
        selectedAvatarJpeg = null
        selectedAvatarPreviewUri = null
        removeAvatarRequested = false
        onDismissProfileEditError()
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    AvatarTranscoder.transcodeToJpeg(context, uri)
                }.onSuccess { jpegBytes ->
                    selectedAvatarJpeg = jpegBytes
                    selectedAvatarPreviewUri = uri
                    removeAvatarRequested = false
                    onDismissProfileEditError()
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.settings_title),
            onBack = onBack,
            trailing = {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        resetEditor()
                        showEditProfileDialog = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.settings_edit_profile),
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            // Profile header
            ProfileHeader(wall = wall)

            // Own posts grid
            if (ownPosts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Space.md))
                Text(
                    text = stringResource(R.string.settings_own_posts),
                    style = Typo.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xs),
                )
                OwnPostsGrid(
                    posts = ownPosts,
                    onPostTap = onPostTap,
                )
            }

            Spacer(modifier = Modifier.height(Space.xxl))
        }
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            wall = wall,
            displayName = editDisplayName,
            bio = editBio,
            selectedAvatarPreviewUri = selectedAvatarPreviewUri,
            removeAvatarRequested = removeAvatarRequested,
            state = profileEditState,
            onDisplayNameChange = {
                editDisplayName = it
                onDismissProfileEditError()
            },
            onBioChange = {
                editBio = it
                onDismissProfileEditError()
            },
            onPickAvatar = {
                avatarPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemoveAvatar = {
                selectedAvatarJpeg = null
                selectedAvatarPreviewUri = null
                removeAvatarRequested = true
                onDismissProfileEditError()
            },
            onDismiss = {
                showEditProfileDialog = false
                resetEditor()
            },
            onSave = {
                onSaveProfile(
                    editDisplayName,
                    editBio,
                    selectedAvatarJpeg,
                    removeAvatarRequested,
                ) {
                    showEditProfileDialog = false
                    resetEditor()
                }
            },
        )
    }
}

// ── Profile header ──────────────────────────────────────────────

@Composable
private fun ProfileHeader(
    wall: Wall,
    modifier: Modifier = Modifier,
) {
    ProfileSummaryHeader(
        wall = wall.copy(displayName = wall.displayName ?: stringResource(R.string.settings_you_fallback)),
        modifier = modifier,
        avatarSize = 64.dp,
        stats = listOf(
            ProfileStat(wall.postCount, stringResource(R.string.posts)),
            ProfileStat(wall.followerCount, stringResource(R.string.followers)),
            ProfileStat(wall.followingCount, stringResource(R.string.following)),
        ),
    )
}

// ── Own posts grid ──────────────────────────────────────────────

@Composable
private fun OwnPostsGrid(
    posts: List<Post>,
    onPostTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    // Fixed-height grid so it doesn't conflict with the scroll
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.md)
            .height((((posts.size + 2) / 3) * 120).dp),
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
        userScrollEnabled = false,
    ) {
        items(posts, key = { it.id }) { post ->
            val imageAsset = post.assets.firstOrNull { it.variant == PostAssetVariant.Thumbnail }
                ?: post.assets.firstOrNull()
            val tileDescription = buildString {
                append("Post")
                val caption = post.caption?.text?.take(40)
                if (!caption.isNullOrBlank()) {
                    append(": $caption")
                }
                if (post.assets.size > 1) {
                    append(", ${post.assets.size} photos")
                }
            }
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPostTap(post.id)
                    }
                    .semantics { contentDescription = tileDescription },
            ) {
                if (imageAsset != null) {
                    EncryptedAssetImage(
                        objectKey = imageAsset.objectKey,
                        blurHash = imageAsset.blurHash,
                        contentDescription = post.caption?.text?.take(40),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                // Multi-photo badge
                if (post.assets.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(Space.xs)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                RoundedCornerShape(Radius.xs),
                            )
                            .padding(horizontal = Space.xs, vertical = Space.xxs),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Collections,
                            contentDescription = "${post.assets.size} photos",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}

// ── Previews ────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SettingsReadyPreview() {
    EnteGramTheme {
        SettingsContent(
            wall = SampleData.viewerWall,
            ownPosts = SampleData.posts.filter { it.wallId == SampleData.viewerWall.id },
            profileEditState = SettingsProfileEditUiState(),
            onBack = {},
            onPostTap = {},
            onSaveProfile = { _, _, _, _, onSuccess -> onSuccess() },
            onDismissProfileEditError = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun SettingsProfileHeaderPreview() {
    EnteGramTheme {
        ProfileHeader(wall = SampleData.lena)
    }
}
