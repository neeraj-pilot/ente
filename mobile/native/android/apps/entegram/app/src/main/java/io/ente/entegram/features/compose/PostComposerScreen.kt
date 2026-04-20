package io.ente.entegram.features.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.core.media.PostImageTranscoder
import io.ente.entegram.ui.components.DestructiveTextButton
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo
import kotlinx.coroutines.launch

@Composable
fun PostComposerScreen(
    onBack: () -> Unit,
    onPostCreated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PostComposerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(
            maxItems = MAX_PHOTOS,
        ),
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                val photos = uris.mapNotNull { uri ->
                    try {
                        val prepared = PostImageTranscoder.preparePostImage(context, uri)

                        SelectedPhoto(
                            uri = uri,
                            fullData = prepared.fullData,
                            thumbnailData = prepared.thumbnailData,
                            width = prepared.width,
                            height = prepared.height,
                            sizeBytes = prepared.sizeBytes,
                            blurHash = prepared.blurHash,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                viewModel.addPhotos(photos)
            }
        }
    }

    // Success dismiss animation: scale 0.96 → 1.0 → 0.0 + alpha 1 → 0
    val haptic = LocalHapticFeedback.current
    val dismissScale = remember { Animatable(1f) }
    val dismissAlpha = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState) {
        if (uiState is ComposerUiState.Success) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            // Animate dismiss: scale 0.96 → 1.0 → 0.0 over 260ms, alpha 1 → 0
            launch {
                dismissScale.animateTo(
                    targetValue = 0f,
                    animationSpec = keyframes {
                        durationMillis = 260
                        0.96f at 0 using FastOutSlowInEasing
                        1.0f at 80 using FastOutSlowInEasing
                        0f at 260
                    },
                )
            }
            launch {
                dismissAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(260, easing = FastOutSlowInEasing),
                )
            }
            kotlinx.coroutines.delay(280)
            onPostCreated()
        }
    }

    val pickPhotos = {
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .scale(dismissScale.value)
            .alpha(dismissAlpha.value),
    ) {
        when (val state = uiState) {
            is ComposerUiState.Empty -> {
                ComposerContent(
                    photos = emptyList(),
                    activeIndex = 0,
                    caption = "",
                    isCaptionFocused = false,
                    canPost = false,
                    hasOversize = false,
                    isUploading = false,
                    errorMessage = null,
                    onBack = onBack,
                    onCaptionChange = { viewModel.updateCaption(it) },
                    onCaptionFocusChange = { viewModel.setCaptionFocus(it) },
                    onShare = {},
                    onPickPhotos = pickPhotos,
                    onSelectPhoto = {},
                    onRemovePhoto = {},
                    onMovePhoto = { _, _ -> },
                    onRetry = {},
                    onDismissError = {},
                )
            }

            is ComposerUiState.Drafting -> {
                ComposerContent(
                    photos = state.photos,
                    activeIndex = state.activeIndex,
                    caption = state.caption,
                    isCaptionFocused = state.isCaptionFocused,
                    canPost = state.canPost,
                    hasOversize = state.hasOversize,
                    isUploading = false,
                    errorMessage = null,
                    onBack = onBack,
                    onCaptionChange = { viewModel.updateCaption(it) },
                    onCaptionFocusChange = { viewModel.setCaptionFocus(it) },
                    onShare = { viewModel.share() },
                    onPickPhotos = pickPhotos,
                    onSelectPhoto = { viewModel.selectPhoto(it) },
                    onRemovePhoto = { viewModel.removePhoto(it) },
                    onMovePhoto = { from, to -> viewModel.movePhoto(from, to) },
                    onRetry = {},
                    onDismissError = {},
                )
            }

            is ComposerUiState.Uploading -> {
                ComposerContent(
                    photos = state.photos,
                    activeIndex = state.activeIndex,
                    caption = state.caption,
                    isCaptionFocused = false,
                    canPost = false,
                    hasOversize = false,
                    isUploading = true,
                    errorMessage = null,
                    onBack = onBack,
                    onCaptionChange = {},
                    onCaptionFocusChange = {},
                    onShare = {},
                    onPickPhotos = {},
                    onSelectPhoto = {},
                    onRemovePhoto = {},
                    onMovePhoto = { _, _ -> },
                    onRetry = {},
                    onDismissError = {},
                )
            }

            is ComposerUiState.Success -> {
                // Dismiss animation plays; content holds last visual state
                Box(modifier = Modifier.fillMaxSize())
            }

            is ComposerUiState.Error -> {
                ComposerContent(
                    photos = state.photos,
                    activeIndex = state.activeIndex,
                    caption = state.caption,
                    isCaptionFocused = false,
                    canPost = true,
                    hasOversize = false,
                    isUploading = false,
                    errorMessage = state.message,
                    onBack = onBack,
                    onCaptionChange = { viewModel.updateCaption(it) },
                    onCaptionFocusChange = { viewModel.setCaptionFocus(it) },
                    onShare = { viewModel.share() },
                    onPickPhotos = pickPhotos,
                    onSelectPhoto = { viewModel.selectPhoto(it) },
                    onRemovePhoto = { viewModel.removePhoto(it) },
                    onMovePhoto = { from, to -> viewModel.movePhoto(from, to) },
                    onRetry = { viewModel.retry() },
                    onDismissError = { viewModel.dismissError() },
                )
            }
        }
    }
}

@Composable
private fun ComposerContent(
    photos: List<SelectedPhoto>,
    activeIndex: Int,
    caption: String,
    isCaptionFocused: Boolean,
    canPost: Boolean,
    hasOversize: Boolean,
    isUploading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onCaptionChange: (String) -> Unit,
    onCaptionFocusChange: (Boolean) -> Unit,
    onShare: () -> Unit,
    onPickPhotos: () -> Unit,
    onSelectPhoto: (Int) -> Unit,
    onRemovePhoto: (Int) -> Unit,
    onMovePhoto: (Int, Int) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Scroll-aware divider
    val showDivider = scrollState.value > 0

    // Auto-scroll to caption when focused (keeps it visible above keyboard)
    LaunchedEffect(isCaptionFocused) {
        if (isCaptionFocused) {
            coroutineScope.launch {
                scrollState.animateScrollTo(
                    scrollState.maxValue,
                    animationSpec = Motion.soft(),
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding(),
    ) {
        // Header
        Box {
            ComposerTopBar(
                canPost = canPost,
                isUploading = isUploading,
                onBack = onBack,
                onShare = onShare,
            )
        }

        // Divider on scroll
        AnimatedVisibility(
            visible = showDivider,
            enter = fadeIn(Motion.quickFade()),
            exit = fadeOut(Motion.quickFade()),
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { focusManager.clearFocus() },
                )
                .padding(horizontal = Space.md),
        ) {
            Spacer(modifier = Modifier.height(Space.sm))

            // Hero area with crossfade on thumbnail switch
            Box {
                if (photos.isEmpty()) {
                    HeroAddPhotoTile(
                        onClick = onPickPhotos,
                    )
                } else {
                    val safeIndex = activeIndex.coerceIn(photos.indices)
                    val activePhoto = photos[safeIndex]
                    val aspect = if (activePhoto.width > 0 && activePhoto.height > 0) {
                        (activePhoto.width.toFloat() / activePhoto.height.toFloat())
                            .coerceIn(0.8f, 1f)
                    } else {
                        0.8f
                    }

                    // Upload parallax: subtle lift-and-settle (0 → -4dp → 0 over 900ms)
                    val parallaxOffsetY = if (isUploading) {
                        val infiniteTransition = rememberInfiniteTransition(label = "upload-parallax")
                        val offsetPx by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = keyframes {
                                    durationMillis = 900
                                    0f at 0 using FastOutSlowInEasing
                                    -4f at 450 using FastOutSlowInEasing
                                    0f at 900
                                },
                                repeatMode = RepeatMode.Restart,
                            ),
                            label = "parallax-y",
                        )
                        offsetPx
                    } else {
                        0f
                    }

                    val density = androidx.compose.ui.platform.LocalDensity.current
                    Box(
                        modifier = Modifier.offset {
                            IntOffset(0, with(density) { parallaxOffsetY.dp.roundToPx() })
                        },
                    ) {
                        // AnimatedContent crossfade between active photos (Motion.soft + 180ms overlap)
                        AnimatedContent(
                            targetState = safeIndex,
                            transitionSpec = {
                                fadeIn(tween(180, easing = FastOutSlowInEasing)) togetherWith
                                    fadeOut(tween(180, easing = FastOutSlowInEasing))
                            },
                            label = "hero-crossfade",
                        ) { targetIndex ->
                            val targetPhoto = photos.getOrElse(targetIndex) { activePhoto }
                            val targetAspect = if (targetPhoto.width > 0 && targetPhoto.height > 0) {
                                (targetPhoto.width.toFloat() / targetPhoto.height.toFloat())
                                    .coerceIn(0.8f, 1f)
                            } else {
                                0.8f
                            }
                            HeroPhoto(
                                uri = targetPhoto.uri,
                                aspectRatio = targetAspect,
                                onRemove = { onRemovePhoto(targetIndex) },
                                photoIndex = targetIndex,
                                totalPhotos = photos.size,
                                modifier = Modifier.animateContentSize(Motion.soft()),
                            )
                        }

                        // Upload linear progress bar at bottom of hero
                        if (isUploading) {
                            val progressTransition = rememberInfiniteTransition(label = "progress-crawl")
                            val progress by progressTransition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = keyframes {
                                        durationMillis = 2400
                                        0f at 0 using FastOutSlowInEasing
                                        0.6f at 1200 using FastOutSlowInEasing
                                        1f at 2400
                                    },
                                    repeatMode = RepeatMode.Restart,
                                ),
                                label = "progress-value",
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedCornerShape(bottomStart = Radius.lg, bottomEnd = Radius.lg)),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                trackColor = Color.Transparent,
                                strokeCap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }

            // Thumbnail strip
            if (photos.isNotEmpty()) {
                Spacer(modifier = Modifier.height(Space.sm))
                Box {
                    ThumbnailStrip(
                        photos = photos,
                        activeIndex = activeIndex,
                        onSelect = onSelectPhoto,
                        onAdd = onPickPhotos,
                        onMove = onMovePhoto,
                        canAdd = photos.size < MAX_PHOTOS && !isUploading,
                        modifier = Modifier.padding(horizontal = 0.dp),
                    )
                }
            }

            // Caption field
            val captionDescription = stringResource(R.string.composer_caption_description)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (photos.isEmpty()) Space.lg else Space.md),
            ) {
                Text(
                    text = stringResource(R.string.composer_caption_label),
                    style = Typo.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(start = Space.xs, bottom = Space.xs),
                )
                OutlinedTextField(
                    value = caption,
                    onValueChange = onCaptionChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
                            RoundedCornerShape(Radius.lg),
                        )
                        .onFocusChanged { onCaptionFocusChange(it.isFocused) }
                        .semantics { contentDescription = captionDescription },
                    enabled = !isUploading,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.composer_caption_placeholder),
                            style = Typo.composer,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                        )
                    },
                    textStyle = Typo.composer.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    minLines = 3,
                    maxLines = 8,
                    shape = RoundedCornerShape(Radius.lg),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.72f),
                        cursorColor = MaterialTheme.colorScheme.primary,
                        disabledBorderColor = Color.Transparent,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    ),
                )
            }

            // Bottom breathing room
            Spacer(modifier = Modifier.height(Space.md))
        }

        // Error bar at bottom
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = slideInVertically(
                animationSpec = Motion.settle(),
                initialOffsetY = { it },
            ) + fadeIn(Motion.quickFade()),
            exit = slideOutVertically(
                animationSpec = Motion.settle(),
                targetOffsetY = { it },
            ) + fadeOut(Motion.quickFade()),
        ) {
            if (errorMessage != null) {
                InlineErrorBar(
                    message = errorMessage,
                    onRetry = onRetry,
                    onDismiss = onDismissError,
                    modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                )
            }
        }
    }
}

@Composable
private fun ComposerTopBar(
    canPost: Boolean,
    isUploading: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = Space.xs, vertical = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading: Cancel
        DestructiveTextButton(
            text = stringResource(R.string.cancel),
            onClick = onBack,
            enabled = true,
        )

        // Centered title
        Text(
            text = stringResource(R.string.composer_title),
            style = Typo.body.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )

        // Trailing: Share or spinner
        if (isUploading) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onShare()
                },
                modifier = Modifier
                    .height(36.dp)
                    .padding(end = Space.sm),
                enabled = canPost,
                shape = RoundedCornerShape(Radius.pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = Color.Transparent,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
            ) {
                Text(
                    text = stringResource(R.string.composer_share),
                    style = Typo.bodyEmphasized,
                )
            }
        }
    }
}

// ── Preview helpers ─────────────────────────────────────────────────────

private fun previewPhoto(
    width: Int = 800,
    height: Int = 1000,
    sizeBytes: Long = 1_200_000L,
): SelectedPhoto = SelectedPhoto(
    uri = Uri.EMPTY,
    fullData = ByteArray(0),
    thumbnailData = ByteArray(0),
    width = width,
    height = height,
    sizeBytes = sizeBytes,
)

// ── Previews (6 per PLAN §14) ──────────────────────────────────────────

/** 1. Empty — no photos, no caption */
@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 700)
@Composable
private fun ComposerEmptyPreview() {
    EnteGramTheme {
        ComposerContent(
            photos = emptyList(),
            activeIndex = 0,
            caption = "",
            isCaptionFocused = false,
            canPost = false,
            hasOversize = false,
            isUploading = false,
            errorMessage = null,
            onBack = {},
            onCaptionChange = {},
            onCaptionFocusChange = {},
            onShare = {},
            onPickPhotos = {},
            onSelectPhoto = {},
            onRemovePhoto = {},
            onMovePhoto = { _, _ -> },
            onRetry = {},
            onDismissError = {},
        )
    }
}

/** 2. One photo + empty caption */
@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 700)
@Composable
private fun ComposerSinglePhotoPreview() {
    EnteGramTheme {
        ComposerContent(
            photos = listOf(previewPhoto()),
            activeIndex = 0,
            caption = "",
            isCaptionFocused = false,
            canPost = true,
            hasOversize = false,
            isUploading = false,
            errorMessage = null,
            onBack = {},
            onCaptionChange = {},
            onCaptionFocusChange = {},
            onShare = {},
            onPickPhotos = {},
            onSelectPhoto = {},
            onRemovePhoto = {},
            onMovePhoto = { _, _ -> },
            onRetry = {},
            onDismissError = {},
        )
    }
}

/** 3. Three photos + drafting caption */
@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 700)
@Composable
private fun ComposerDraftingPreview() {
    EnteGramTheme {
        ComposerContent(
            photos = listOf(
                previewPhoto(),
                previewPhoto(width = 1000, height = 1000),
                previewPhoto(width = 1200, height = 800),
            ),
            activeIndex = 0,
            caption = "Rooftop light, 6:47pm. The city hums below.",
            isCaptionFocused = false,
            canPost = true,
            hasOversize = false,
            isUploading = false,
            errorMessage = null,
            onBack = {},
            onCaptionChange = {},
            onCaptionFocusChange = {},
            onShare = {},
            onPickPhotos = {},
            onSelectPhoto = {},
            onRemovePhoto = {},
            onMovePhoto = { _, _ -> },
            onRetry = {},
            onDismissError = {},
        )
    }
}

/** 4. Photo over 2 MB — budget hint in error tint */
@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 700)
@Composable
private fun ComposerOverLimitPreview() {
    EnteGramTheme {
        ComposerContent(
            photos = listOf(
                previewPhoto(sizeBytes = 1_500_000L),
                previewPhoto(sizeBytes = 3_500_000L), // exceeds 2 MB
            ),
            activeIndex = 0,
            caption = "Golden hour over the harbor",
            isCaptionFocused = false,
            canPost = false,
            hasOversize = true,
            isUploading = false,
            errorMessage = null,
            onBack = {},
            onCaptionChange = {},
            onCaptionFocusChange = {},
            onShare = {},
            onPickPhotos = {},
            onSelectPhoto = {},
            onRemovePhoto = {},
            onMovePhoto = { _, _ -> },
            onRetry = {},
            onDismissError = {},
        )
    }
}

/** 5. Uploading — parallax + linear progress on hero */
@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 700)
@Composable
private fun ComposerUploadingPreview() {
    EnteGramTheme {
        ComposerContent(
            photos = listOf(previewPhoto(), previewPhoto(width = 1000, height = 1000)),
            activeIndex = 0,
            caption = "Rooftop light, 6:47pm.",
            isCaptionFocused = false,
            canPost = false,
            hasOversize = false,
            isUploading = true,
            errorMessage = null,
            onBack = {},
            onCaptionChange = {},
            onCaptionFocusChange = {},
            onShare = {},
            onPickPhotos = {},
            onSelectPhoto = {},
            onRemovePhoto = {},
            onMovePhoto = { _, _ -> },
            onRetry = {},
            onDismissError = {},
        )
    }
}

/** 6. Error bar visible */
@Preview(showBackground = true, backgroundColor = 0xFF0E0E10, heightDp = 700)
@Composable
private fun ComposerErrorPreview() {
    EnteGramTheme {
        ComposerContent(
            photos = listOf(previewPhoto()),
            activeIndex = 0,
            caption = "Golden hour over the harbor",
            isCaptionFocused = false,
            canPost = true,
            hasOversize = false,
            isUploading = false,
            errorMessage = "Failed to upload photos",
            onBack = {},
            onCaptionChange = {},
            onCaptionFocusChange = {},
            onShare = {},
            onPickPhotos = {},
            onSelectPhoto = {},
            onRemovePhoto = {},
            onMovePhoto = { _, _ -> },
            onRetry = {},
            onDismissError = {},
        )
    }
}
