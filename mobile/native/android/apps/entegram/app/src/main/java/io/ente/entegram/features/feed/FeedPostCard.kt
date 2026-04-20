package io.ente.entegram.features.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.R
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.PostAsset
import io.ente.entegram.core.models.PostAssetVariant
import io.ente.entegram.core.services.SampleData
import io.ente.entegram.ui.components.AvatarView
import io.ente.entegram.ui.components.EncryptedAssetImage
import io.ente.entegram.ui.components.HeartBurst
import io.ente.entegram.ui.components.LikeCountText
import io.ente.entegram.ui.components.RelativeTimeLabel
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

private val PhotoCornerRadius = 18.dp
private val PhotoHorizontalPadding = 8.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedPostCard(
    post: Post,
    onTap: () -> Unit,
    onLikeTap: () -> Unit,
    onCommentTap: () -> Unit,
    modifier: Modifier = Modifier,
    onAuthorTap: (() -> Unit)? = null,
    isViewerPost: Boolean = false,
    onDeleteTap: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current

    // HeartBurst state
    var burstTrigger by remember { mutableIntStateOf(0) }
    var heartIconCenter by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    // Detect when viewerLiked flips to true -> fire a burst
    var previousLiked by remember { mutableStateOf(post.viewerLiked) }
    LaunchedEffect(post.viewerLiked) {
        if (post.viewerLiked && !previousLiked) {
            burstTrigger++
        }
        previousLiked = post.viewerLiked
    }

    // Track root Box position for heart coordinate calculation
    var boxPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    // Caption expand state
    var captionExpanded by remember { mutableStateOf(false) }

    // Overflow menu state
    var showMenu by remember { mutableStateOf(false) }

    // Full-variant assets only
    val fullAssets = remember(post.assets) {
        post.assets.filter { it.variant == PostAssetVariant.Full }.sortedBy { it.position }
    }
    val authorDisplayName = post.authorDisplayName?.trim()?.takeIf { it.isNotEmpty() }

    // Overlay scope: Box wraps the entire card so HeartBurst can fill it
    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                boxPositionInRoot = coords.positionInRoot()
            },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Author row ──────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = Space.md, vertical = Space.sm)
                    .then(
                        if (onAuthorTap != null) {
                            Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAuthorTap()
                            }
                        } else {
                            Modifier
                        },
                    ),
            ) {
                AvatarView(
                    slug = post.authorSlug,
                    displayName = post.authorDisplayName,
                    size = 36.dp,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = authorDisplayName ?: "@${post.authorSlug}",
                        style = Typo.bodyEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (authorDisplayName != null) {
                            Text(
                                text = "@${post.authorSlug}",
                                style = Typo.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        RelativeTimeLabel(
                            instant = post.createdAt,
                            style = Typo.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (isViewerPost && onDeleteTap != null) {
                    Box {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showMenu = true
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.MoreHoriz,
                                contentDescription = stringResource(R.string.post_more_options),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(R.string.post_menu_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onDeleteTap()
                                },
                            )
                        }
                    }
                }
            }

            // ── Photo area ──────────────────────────────────────
            if (fullAssets.isNotEmpty()) {
                val firstAspect = fullAssets.first().aspect.ratio.coerceIn(0.8f, 1f)

                if (fullAssets.size == 1) {
                    // Single photo
                    SinglePhotoArea(
                        asset = fullAssets.first(),
                        aspectRatio = firstAspect,
                        onTap = onTap,
                        onDoubleTap = {
                            if (!post.viewerLiked) {
                                onLikeTap()
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        contentDescription = if (post.caption.text.isNotBlank()) {
                            stringResource(R.string.post_photo_by_caption, post.authorSlug, post.caption.text.take(80))
                        } else {
                            stringResource(R.string.post_photo_by, post.authorSlug)
                        },
                    )
                } else {
                    // Multi-photo pager
                    MultiPhotoPager(
                        assets = fullAssets,
                        aspectRatio = firstAspect,
                        onTap = onTap,
                        onDoubleTap = {
                            if (!post.viewerLiked) {
                                onLikeTap()
                            }
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                    )
                }
            }

            // ── Action row ──────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.md),
                modifier = Modifier.padding(start = Space.md, end = Space.md, top = 12.dp),
            ) {
                // Like group
                val likeScale by animateFloatAsState(
                    targetValue = if (post.viewerLiked) 1.1f else 1f,
                    animationSpec = Motion.snap(),
                    label = "likeScale",
                )
                val heartPink = Color(0xFFFF6B7A)
                val likeColor by animateColorAsState(
                    targetValue = if (post.viewerLiked) heartPink else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = Motion.quickFade(),
                    label = "likeColor",
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLikeTap()
                    },
                ) {
                    Icon(
                        imageVector = if (post.viewerLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = stringResource(if (post.viewerLiked) R.string.post_unlike else R.string.post_like),
                        tint = likeColor,
                        modifier = Modifier
                            .size(22.dp)
                            .scale(likeScale)
                            .onGloballyPositioned { coords ->
                                val posInRoot = coords.positionInRoot()
                                val iconSizePx = with(density) { 22.dp.toPx() }
                                heartIconCenter = Offset(
                                    x = posInRoot.x - boxPositionInRoot.x + iconSizePx / 2f,
                                    y = posInRoot.y - boxPositionInRoot.y + iconSizePx / 2f,
                                )
                            },
                    )
                    LikeCountText(count = post.likeCount)
                }

                // Comment group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onCommentTap()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = stringResource(R.string.post_comments),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        text = "${post.commentCount}",
                        style = Typo.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── Caption row (handle-prefixed) ───────────────────
            if (post.caption.text.isNotBlank()) {
                val captionText = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                        append("@${post.authorSlug} ")
                    }
                    append(post.caption.text)
                }
                Text(
                    text = captionText,
                    style = Typo.body,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = if (captionExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = Space.md, end = Space.md, top = 6.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            captionExpanded = !captionExpanded
                        },
                )
            }

            Spacer(modifier = Modifier.height(Space.lg))
        }

        // HeartBurst overlay — covers the entire card
        HeartBurst(
            trigger = burstTrigger,
            emissionOrigin = heartIconCenter,
        )
    }
}

// ── Single photo ─────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SinglePhotoArea(
    asset: PostAsset,
    aspectRatio: Float,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    contentDescription: String? = null,
) {
    EncryptedAssetImage(
        objectKey = asset.objectKey,
        blurHash = asset.blurHash,
        contentDescription = contentDescription,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PhotoHorizontalPadding)
            .aspectRatio(aspectRatio)
            .clip(RoundedCornerShape(PhotoCornerRadius))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onDoubleClick = onDoubleTap,
            ),
        contentScale = ContentScale.Crop,
    )
}

// ── Multi-photo pager ────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MultiPhotoPager(
    assets: List<PostAsset>,
    aspectRatio: Float,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val pagerState = rememberPagerState(pageCount = { assets.size })

    // Haptic on page change
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PhotoHorizontalPadding),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(PhotoCornerRadius)),
        ) { page ->
            val asset = assets[page]
            EncryptedAssetImage(
                objectKey = asset.objectKey,
                blurHash = asset.blurHash,
                contentDescription = stringResource(R.string.post_photo_n_of_total, page + 1, assets.size),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTap,
                        onDoubleClick = onDoubleTap,
                    ),
                contentScale = ContentScale.Crop,
            )
        }

        // Page indicator pill
        if (assets.size > 1) {
            PageIndicator(
                pageCount = assets.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

// ── Page indicator ───────────────────────────────────────────────

@Composable
private fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.35f),
                shape = RoundedCornerShape(50),
            )
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val dotWidth by animateDpAsState(
                targetValue = if (isActive) 18.dp else 6.dp,
                animationSpec = spring(stiffness = 400f, dampingRatio = 0.8f),
                label = "dotWidth",
            )
            val dotAlpha by animateFloatAsState(
                targetValue = if (isActive) 0.95f else 0.45f,
                animationSpec = Motion.quickFade(),
                label = "dotAlpha",
            )
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(dotWidth)
                    .background(
                        color = Color.White.copy(alpha = dotAlpha),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

// ── Previews ─────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedPostCardSinglePhotoPreview() {
    EnteGramTheme {
        FeedPostCard(
            post = SampleData.posts[0], // Lena single photo, short caption, 1 comment
            onTap = {},
            onLikeTap = {},
            onCommentTap = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedPostCardMultiPhoto2Preview() {
    EnteGramTheme {
        FeedPostCard(
            post = SampleData.posts[1], // Sora 2 photos, liked
            onTap = {},
            onLikeTap = {},
            onCommentTap = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedPostCardMultiPhoto3Preview() {
    EnteGramTheme {
        FeedPostCard(
            post = SampleData.posts[4], // Lena tram-28, 3 photos, liked
            onTap = {},
            onLikeTap = {},
            onCommentTap = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedPostCardLongCaptionPreview() {
    EnteGramTheme {
        FeedPostCard(
            post = SampleData.posts[2], // mapmaker long caption, 0 comments
            onTap = {},
            onLikeTap = {},
            onCommentTap = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedPostCardHighLikeCountPreview() {
    EnteGramTheme {
        FeedPostCard(
            post = SampleData.posts[6], // mapmaker 89 likes
            onTap = {},
            onLikeTap = {},
            onCommentTap = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedPostCardNoCaptionNoCommentsPreview() {
    EnteGramTheme {
        FeedPostCard(
            post = SampleData.posts[8].copy(caption = SampleData.posts[8].caption.copy(text = ""), commentCount = 0),
            onTap = {},
            onLikeTap = {},
            onCommentTap = {},
        )
    }
}
