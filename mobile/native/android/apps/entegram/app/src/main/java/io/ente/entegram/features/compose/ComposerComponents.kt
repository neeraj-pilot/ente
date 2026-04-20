package io.ente.entegram.features.compose

import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import androidx.compose.ui.res.stringResource
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

// ── AddPhotoTile ────────────────────────────────────────────────────────

@Composable
fun AddPhotoTile(
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val addAnotherPhotoDesc = stringResource(R.string.composer_add_another_photo)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 100.dp) Radius.lg else Radius.md))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { contentDescription = addAnotherPhotoDesc },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            modifier = Modifier.size(if (size > 100.dp) 40.dp else 24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// ── HeroAddPhotoTile ────────────────────────────────────────────────────

@Composable
fun HeroAddPhotoTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val addPhotosDesc = stringResource(R.string.composer_add_photos)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(Radius.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
                RoundedCornerShape(Radius.lg),
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .semantics { contentDescription = addPhotosDesc },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                modifier = Modifier.size(46.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            )
            Text(
                text = stringResource(R.string.composer_add_photos),
                style = Typo.bodyEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.composer_add_photos_hint),
                style = Typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
            )
        }
    }
}

// ── HeroPhoto ───────────────────────────────────────────────────────────

@Composable
fun HeroPhoto(
    uri: Uri,
    aspectRatio: Float,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    photoIndex: Int = 0,
    totalPhotos: Int = 1,
) {
    val haptic = LocalHapticFeedback.current
    val selectedPhotoDesc = stringResource(R.string.composer_selected_photo, photoIndex + 1, totalPhotos)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspectRatio.coerceIn(0.8f, 1f))
            .clip(RoundedCornerShape(Radius.lg))
            .semantics {
                contentDescription = selectedPhotoDesc
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Remove button overlay
        IconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onRemove()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Space.sm)
                .size(36.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(R.string.composer_remove_photo),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── ThumbnailStrip ──────────────────────────────────────────────────────

@Composable
fun ThumbnailStrip(
    photos: List<SelectedPhoto>,
    activeIndex: Int,
    onSelect: (Int) -> Unit,
    onAdd: () -> Unit,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    canAdd: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    // Track which item is being dragged and its horizontal offset
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val itemWidth = 64.dp + Space.md // thumbnail size + gap

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Space.md),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        itemsIndexed(photos, key = { _, photo -> photo.uri.toString() }) { index, photo ->
            val isActive = index == activeIndex
            val isDragging = index == draggingIndex
            val borderWidth by animateDpAsState(
                targetValue = if (isActive) 2.dp else 0.dp,
                animationSpec = Motion.snap(),
                label = "thumb-border",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isActive) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                } else {
                    Color.Transparent
                },
                animationSpec = Motion.snap(),
                label = "thumb-border-color",
            )
            val scale by animateFloatAsState(
                targetValue = if (isDragging) 1.12f else 1f,
                animationSpec = Motion.snap(),
                label = "thumb-drag-scale",
            )

            val density = androidx.compose.ui.platform.LocalDensity.current
            val itemWidthPx = with(density) { itemWidth.toPx() }

            Box(
                modifier = Modifier
                    .zIndex(if (isDragging) 1f else 0f)
                    .offset {
                        IntOffset(
                            x = if (isDragging) dragOffsetX.roundToInt() else 0,
                            y = 0,
                        )
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .size(64.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .border(borderWidth, borderColor, RoundedCornerShape(Radius.md))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(index)
                    }
                    .pointerInput(index, photos.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                draggingIndex = index
                                dragOffsetX = 0f
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetX += dragAmount.x
                                // Check if dragged past half an item width to swap
                                val swapThreshold = itemWidthPx * 0.5f
                                if (dragOffsetX > swapThreshold && draggingIndex < photos.size - 1) {
                                    onMove(draggingIndex, draggingIndex + 1)
                                    draggingIndex += 1
                                    dragOffsetX -= itemWidthPx
                                } else if (dragOffsetX < -swapThreshold && draggingIndex > 0) {
                                    onMove(draggingIndex, draggingIndex - 1)
                                    draggingIndex -= 1
                                    dragOffsetX += itemWidthPx
                                }
                            },
                            onDragEnd = {
                                draggingIndex = -1
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                draggingIndex = -1
                                dragOffsetX = 0f
                            },
                        )
                    },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(photo.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = stringResource(R.string.composer_photo_n, index + 1),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Oversize error chip
                if (photo.exceedsLimit) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(16.dp)
                            .background(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = stringResource(R.string.composer_exceeds_size_limit),
                            modifier = Modifier.size(10.dp),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        if (canAdd) {
            item {
                AddPhotoTile(
                    size = 64.dp,
                    onClick = onAdd,
                )
            }
        }
    }
}

// ── InlineBudgetHint ────────────────────────────────────────────────────

@Composable
fun InlineBudgetHint(
    count: Int,
    hasOversize: Boolean,
    modifier: Modifier = Modifier,
    limit: Int = MAX_PHOTOS,
) {
    val textColor by animateColorAsState(
        targetValue = if (hasOversize) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = Motion.quickFade(),
        label = "budget-color",
    )

    val budgetOversizeDesc = if (hasOversize) {
        stringResource(R.string.composer_budget_oversize_description, count, limit)
    } else {
        null
    }

    Text(
        text = stringResource(R.string.composer_budget_hint, count, limit),
        style = Typo.caption,
        color = textColor,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm)
            .semantics {
                if (budgetOversizeDesc != null) {
                    contentDescription = budgetOversizeDesc
                }
            },
    )
}

// ── InlineErrorBar ──────────────────────────────────────────────────────

@Composable
fun InlineErrorBar(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .background(
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                RoundedCornerShape(Radius.md),
            )
            .padding(horizontal = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = Typo.caption,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        TextButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onRetry()
        }) {
            Text(
                text = stringResource(R.string.retry),
                style = Typo.bodyEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onDismiss()
        }) {
            Text(
                text = stringResource(R.string.dismiss),
                style = Typo.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Previews ────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun AddPhotoTileSmallPreview() {
    EnteGramTheme {
        Row(modifier = Modifier.padding(Space.md)) {
            AddPhotoTile(size = 64.dp, onClick = {})
            Spacer(modifier = Modifier.width(Space.md))
            AddPhotoTile(size = 200.dp, onClick = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun InlineBudgetHintPreview() {
    EnteGramTheme {
        Column(modifier = Modifier.padding(Space.md)) {
            InlineBudgetHint(count = 3, hasOversize = false)
            Spacer(modifier = Modifier.height(Space.sm))
            InlineBudgetHint(count = 2, hasOversize = true)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun InlineErrorBarPreview() {
    EnteGramTheme {
        InlineErrorBar(
            message = "Failed to upload photos",
            onRetry = {},
            onDismiss = {},
            modifier = Modifier.padding(Space.md),
        )
    }
}
