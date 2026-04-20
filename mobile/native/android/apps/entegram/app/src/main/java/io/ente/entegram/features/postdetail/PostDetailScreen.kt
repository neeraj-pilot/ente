package io.ente.entegram.features.postdetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.core.models.Comment
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.PostAsset
import io.ente.entegram.core.models.PostAssetVariant
import io.ente.entegram.core.services.SampleData
import io.ente.entegram.ui.components.AvatarView
import io.ente.entegram.ui.components.EncryptedAssetImage
import io.ente.entegram.ui.components.EmptyStateView
import io.ente.entegram.ui.components.ErrorStateView
import io.ente.entegram.ui.components.HeartBurst
import io.ente.entegram.ui.components.LikeCountText
import io.ente.entegram.ui.components.LoadingStateView
import io.ente.entegram.ui.components.RelativeTimeLabel
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo
import kotlinx.coroutines.launch

@Composable
fun PostDetailScreen(
    onBack: () -> Unit,
    onWallTap: (String) -> Unit = {},
    onDeleted: () -> Unit = onBack,
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is PostDetailUiState.Loading -> {
            PostDetailLoading(onBack = onBack, modifier = modifier)
        }

        is PostDetailUiState.Empty -> {
            PostDetailEmpty(onBack = onBack, modifier = modifier)
        }

        is PostDetailUiState.Error -> {
            PostDetailError(
                message = state.message,
                onBack = onBack,
                onRetry = { viewModel.retry() },
                modifier = modifier,
            )
        }

        is PostDetailUiState.Ready -> {
            PostDetailContent(
                state = state,
                onBack = onBack,
                onLikeTap = { viewModel.toggleLike() },
                onLoadMoreComments = { viewModel.loadMoreComments() },
                onSendComment = { viewModel.sendComment(it) },
                onDeleteComment = { viewModel.deleteComment(it) },
                onDeletePost = { viewModel.deletePost(onDeleted) },
                onWallTap = onWallTap,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PostDetailLoading(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        DetailTopBar(onBack = onBack)
        LoadingStateView(
            modifier = Modifier.padding(top = Space.md),
            cardCount = 1,
        )
    }
}

@Composable
private fun PostDetailEmpty(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        DetailTopBar(onBack = onBack)
        EmptyStateView(
            icon = Icons.Outlined.ChatBubbleOutline,
            headline = stringResource(R.string.post_detail_not_found_headline),
            body = stringResource(R.string.post_detail_not_found_body),
        )
    }
}

@Composable
private fun PostDetailError(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        DetailTopBar(onBack = onBack)
        ErrorStateView(
            message = message,
            onRetry = onRetry,
        )
    }
}

@Composable
private fun PostDetailContent(
    state: PostDetailUiState.Ready,
    onBack: () -> Unit,
    onLikeTap: () -> Unit,
    onLoadMoreComments: () -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onDeletePost: () -> Unit,
    onWallTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var commentText by rememberSaveable { mutableStateOf("") }
    var pendingDeleteComment by remember { mutableStateOf<Comment?>(null) }
    var showDeletePostDialog by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Auto-load more comments near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.comments.size // offset by header items
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMoreComments && !state.isLoadingMoreComments) {
            onLoadMoreComments()
        }
    }

    // Scroll to bottom when a new comment is sent
    val commentCount = state.comments.size
    var previousCommentCount by remember { mutableIntStateOf(commentCount) }
    LaunchedEffect(commentCount) {
        if (commentCount > previousCommentCount) {
            // New comment was added — scroll to it smoothly
            // header(1) + divider(1) + comments
            listState.animateScrollToItem(index = commentCount + 1)
        }
        previousCommentCount = commentCount
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .background(MaterialTheme.colorScheme.background),
    ) {
        DetailTopBar(onBack = onBack)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = Space.md),
        ) {
            // Post content as header
            item(key = "post-header") {
                PostHeader(
                    post = state.post,
                    onLikeTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLikeTap()
                    },
                    canDelete = state.post.authorSlug in state.viewerWallSlugs,
                    onDeletePost = { showDeletePostDialog = true },
                    onWallTap = onWallTap,
                )
            }

            // Comments section divider
            item(key = "comments-divider") {
                CommentsSectionDivider(count = state.post.commentCount)
            }

            if (state.comments.isEmpty() && !state.isLoadingComments) {
                item(key = "no-comments") {
                    EmptyStateView(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        headline = stringResource(R.string.post_detail_no_comments_headline),
                        body = stringResource(R.string.post_detail_no_comments_body),
                        modifier = Modifier.height(240.dp),
                    )
                }
            }

            items(
                items = state.comments,
                key = { it.id },
            ) { comment ->
                AnimatedVisibility(
                    visible = true,
                    enter = slideInVertically(
                        animationSpec = Motion.soft(),
                        initialOffsetY = { it / 2 },
                    ) + fadeIn(Motion.quickFade()),
                ) {
                    CommentRow(
                        comment = comment,
                        viewerWallSlugs = state.viewerWallSlugs,
                        isDeleting = state.deletingCommentId == comment.id,
                        onDeleteRequest = { pendingDeleteComment = it },
                        onWallTap = onWallTap,
                    )
                }
            }

            if (state.isLoadingMoreComments) {
                item(key = "loading-more-comments") {
                    val loadingMoreDesc = stringResource(R.string.post_detail_loading_more_comments)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Space.lg)
                            .semantics { contentDescription = loadingMoreDesc },
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }
        }

        if (pendingDeleteComment != null) {
            AlertDialog(
                onDismissRequest = { pendingDeleteComment = null },
                title = { Text(text = stringResource(R.string.post_detail_delete_comment_title)) },
                text = { Text(text = stringResource(R.string.post_detail_delete_comment_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val commentId = pendingDeleteComment?.id ?: return@TextButton
                            pendingDeleteComment = null
                            onDeleteComment(commentId)
                        },
                    ) {
                        Text(text = stringResource(R.string.post_detail_delete_comment_action))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteComment = null }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                },
            )
        }

        if (showDeletePostDialog) {
            AlertDialog(
                onDismissRequest = { showDeletePostDialog = false },
                title = { Text(text = stringResource(R.string.post_delete_title)) },
                text = { Text(text = stringResource(R.string.post_delete_body)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeletePostDialog = false
                            onDeletePost()
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.post_menu_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeletePostDialog = false }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                },
            )
        }

        // Comment input bar
        CommentInputBar(
            text = commentText,
            onTextChange = { commentText = it },
            onSend = {
                if (commentText.isNotBlank()) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSendComment(commentText)
                    commentText = ""
                }
            },
            isSending = state.isSendingComment,
            viewerSlug = state.viewerSlug,
            focusRequester = focusRequester,
            onFocused = {
                // When input gains focus, scroll to bottom so user sees latest comments
                scope.launch {
                    if (state.comments.isNotEmpty()) {
                        listState.animateScrollToItem(index = commentCount + 1)
                    }
                }
            },
        )
    }
}

// ── Top bar ─────────────────────────────────────────────────────

@Composable
private fun DetailTopBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onBack()
        }) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(modifier = Modifier.width(Space.sm))
        Text(
            text = stringResource(R.string.post_label),
            style = Typo.bodyEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Post header (image + caption + engagement) ──────────────────

@Composable
private fun PostHeader(
    post: Post,
    onLikeTap: () -> Unit,
    canDelete: Boolean,
    onDeletePost: () -> Unit,
    onWallTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // HeartBurst state
    var burstTrigger by remember { mutableIntStateOf(0) }
    var heartIconCenter by remember { mutableStateOf(Offset.Zero) }
    var previousLiked by remember { mutableStateOf(post.viewerLiked) }
    var showMenu by remember { mutableStateOf(false) }
    LaunchedEffect(post.viewerLiked) {
        if (post.viewerLiked && !previousLiked) {
            burstTrigger++
        }
        previousLiked = post.viewerLiked
    }

    var boxPositionInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                boxPositionInRoot = coords.positionInRoot()
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md),
        ) {
            // Author row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(vertical = Space.sm)
                    .clickable { onWallTap(post.authorSlug) },
            ) {
                AvatarView(
                    slug = post.authorSlug,
                    displayName = post.authorDisplayName,
                    size = 40.dp,
                )
                Spacer(modifier = Modifier.width(Space.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = post.authorDisplayName ?: "@${post.authorSlug}",
                        style = Typo.bodyEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "@${post.authorSlug}",
                        style = Typo.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.width(Space.sm))
                RelativeTimeLabel(instant = post.createdAt)
                if (canDelete) {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
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
                                    onDeletePost()
                                },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Space.xs))

            val fullAssets = remember(post.assets) {
                post.assets.filter { it.variant == PostAssetVariant.Full }.sortedBy { it.position }
            }
            if (fullAssets.isNotEmpty()) {
                PostImagePager(
                    assets = fullAssets,
                    contentDescription = post.caption.text.take(80),
                )
            }

            // Caption — full text in detail view
            if (post.caption.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(Space.md))
                Text(
                    text = post.caption.text,
                    style = Typo.body,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                )
            }

            Spacer(modifier = Modifier.height(Space.md))

            // Engagement row
            EngagementRow(
                post = post,
                onLikeTap = onLikeTap,
                onHeartPositioned = { rootOffset ->
                    heartIconCenter = Offset(
                        x = rootOffset.x - boxPositionInRoot.x,
                        y = rootOffset.y - boxPositionInRoot.y,
                    )
                },
            )

            Spacer(modifier = Modifier.height(Space.sm))
        }

        // HeartBurst overlay — covers the entire post header
        HeartBurst(
            trigger = burstTrigger,
            emissionOrigin = heartIconCenter,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PostImagePager(
    assets: List<PostAsset>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val pagerState = rememberPagerState(pageCount = { assets.size })
    val aspectRatio = assets.first().aspect.ratio.coerceIn(0.8f, 1f)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect {
            if (assets.size > 1) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(RoundedCornerShape(Radius.lg)),
        ) { page ->
            EncryptedAssetImage(
                objectKey = assets[page].objectKey,
                blurHash = assets[page].blurHash,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        if (assets.size > 1) {
            DetailPageIndicator(
                pageCount = assets.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun DetailPageIndicator(
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
            val width = if (index == currentPage) 18.dp else 6.dp
            val alpha = if (index == currentPage) 0.95f else 0.45f
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .background(
                        color = Color.White.copy(alpha = alpha),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

@Composable
private fun EngagementRow(
    post: Post,
    onLikeTap: () -> Unit,
    onHeartPositioned: (Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val heartPink = Color(0xFFFF6B7A)
    val likeScale by animateFloatAsState(
        targetValue = if (post.viewerLiked) 1.1f else 1f,
        animationSpec = Motion.snap(),
        label = "likeScale",
    )
    val likeColor by animateColorAsState(
        targetValue = if (post.viewerLiked) {
            heartPink
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = Motion.quickFade(),
        label = "likeColor",
    )

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Like
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onLikeTap,
            ),
        ) {
            Icon(
                imageVector = if (post.viewerLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(if (post.viewerLiked) R.string.post_unlike else R.string.post_like),
                tint = likeColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(likeScale)
                    .onGloballyPositioned { coords ->
                        val posInRoot = coords.positionInRoot()
                        val iconSizePx = with(density) { 24.dp.toPx() }
                        onHeartPositioned(
                            Offset(
                                x = posInRoot.x + iconSizePx / 2f,
                                y = posInRoot.y + iconSizePx / 2f,
                            ),
                        )
                    },
            )
            Spacer(modifier = Modifier.width(Space.xs))
            LikeCountText(
                count = post.likeCount,
                style = Typo.bodyEmphasized,
            )
        }

        Spacer(modifier = Modifier.width(Space.lg))

        // Comments
        Icon(
            imageVector = Icons.Outlined.ChatBubbleOutline,
            contentDescription = stringResource(R.string.post_comments),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(Space.xs))
        Text(
            text = "${post.commentCount}",
            style = Typo.bodyEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Comments section ────────────────────────────────────────────

@Composable
private fun CommentsSectionDivider(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = Space.md),
        )
        Spacer(modifier = Modifier.height(Space.md))
        Text(
            text = stringResource(if (count == 1) R.string.post_detail_comment_count_singular else R.string.post_detail_comment_count_plural, count),
            style = Typo.bodyEmphasized,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = Space.md),
        )
        Spacer(modifier = Modifier.height(Space.sm))
    }
}

@Composable
private fun CommentRow(
    comment: Comment,
    viewerWallSlugs: Set<String>,
    isDeleting: Boolean,
    onDeleteRequest: (Comment) -> Unit,
    onWallTap: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isReply = comment.parentId != null
    val canDelete = comment.authorSlug in viewerWallSlugs
    var showMenu by remember(comment.id) { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onWallTap(comment.authorSlug) }
            .padding(
                start = if (isReply) Space.xl + Space.md else Space.md,
                end = Space.md,
                top = Space.sm,
                bottom = Space.sm,
            ),
        horizontalArrangement = Arrangement.Start,
    ) {
        AvatarView(
            slug = comment.authorSlug,
            displayName = comment.authorDisplayName,
            size = if (isReply) 28.dp else 32.dp,
        )
        Spacer(modifier = Modifier.width(Space.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = comment.authorDisplayName ?: "@${comment.authorSlug}",
                    style = Typo.caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.width(Space.sm))
                RelativeTimeLabel(instant = comment.createdAt)
            }
            Spacer(modifier = Modifier.height(Space.xxs))
            Text(
                text = comment.text,
                style = Typo.body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
        }
        if (canDelete) {
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    enabled = !isDeleting,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = stringResource(R.string.post_detail_comment_more_options),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.post_detail_delete_comment_action)) },
                        onClick = {
                            showMenu = false
                            onDeleteRequest(comment)
                        },
                    )
                }
            }
        }
    }
}

// ── Comment input bar ───────────────────────────────────────────

@Composable
private fun CommentInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean,
    viewerSlug: String? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    onFocused: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val canSend = text.isNotBlank() && !isSending

    // Animated background color for the send button
    val sendButtonBg by animateColorAsState(
        targetValue = if (canSend) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = Motion.quickFade(),
        label = "sendBtnBg",
    )
    val sendIconTint by animateColorAsState(
        targetValue = if (canSend) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        },
        animationSpec = Motion.quickFade(),
        label = "sendIconTint",
    )

    Column(modifier = modifier) {
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Space.sm + Space.xs, vertical = Space.sm),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (viewerSlug != null) {
                AvatarView(
                    slug = viewerSlug,
                    displayName = null,
                    size = 32.dp,
                )
                Spacer(modifier = Modifier.width(Space.sm))
            }
            val commentFieldDesc = stringResource(R.string.post_detail_comment_description)
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics { contentDescription = commentFieldDesc },
                placeholder = {
                    Text(
                        text = stringResource(R.string.post_detail_comment_placeholder),
                        style = Typo.body,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                },
                textStyle = Typo.body.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (canSend) onSend()
                    },
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
                shape = RoundedCornerShape(Radius.lg),
            )
            Spacer(modifier = Modifier.width(Space.sm))

            // Send button with crossfade between sending spinner and icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(sendButtonBg)
                    .clickable(enabled = canSend) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSend()
                    },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSending,
                    enter = scaleIn(Motion.snap()) + fadeIn(Motion.quickFade()),
                    exit = scaleOut(Motion.snap()) + fadeOut(Motion.quickFade()),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = !isSending,
                    enter = scaleIn(Motion.snap()) + fadeIn(Motion.quickFade()),
                    exit = scaleOut(Motion.snap()) + fadeOut(Motion.quickFade()),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(if (canSend) R.string.post_detail_send_comment else R.string.post_detail_type_to_send),
                        tint = sendIconTint,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

// ── Previews ────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PostDetailLoadingPreview() {
    EnteGramTheme {
        PostDetailLoading(onBack = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PostDetailEmptyPreview() {
    EnteGramTheme {
        PostDetailEmpty(onBack = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PostDetailErrorPreview() {
    EnteGramTheme {
        PostDetailError(
            message = "Something went wrong loading this post.",
            onBack = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PostDetailContentPreview() {
    val post = SampleData.posts[1] // miso post — has 3 comments
    val comments = SampleData.comments.filter { it.postId == post.id }
    EnteGramTheme {
        PostDetailContent(
            state = PostDetailUiState.Ready(
                post = post,
                comments = comments,
                viewerWallSlugs = setOf(SampleData.viewerWall.slug),
                hasMoreComments = false,
            ),
            onBack = {},
            onLikeTap = {},
            onLoadMoreComments = {},
            onSendComment = {},
            onDeleteComment = {},
            onDeletePost = {},
            onWallTap = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PostDetailNoCommentsPreview() {
    val post = SampleData.posts[2] // mapmaker post — 0 comments
    EnteGramTheme {
        PostDetailContent(
            state = PostDetailUiState.Ready(
                post = post,
                comments = emptyList(),
                viewerWallSlugs = setOf(SampleData.viewerWall.slug),
                hasMoreComments = false,
            ),
            onBack = {},
            onLikeTap = {},
            onLoadMoreComments = {},
            onSendComment = {},
            onDeleteComment = {},
            onDeletePost = {},
            onWallTap = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CommentRowPreview() {
    EnteGramTheme {
        CommentRow(
            comment = SampleData.comments.first(),
            viewerWallSlugs = setOf(SampleData.viewerWall.slug),
            isDeleting = false,
            onDeleteRequest = {},
            onWallTap = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CommentRowReplyPreview() {
    EnteGramTheme {
        CommentRow(
            comment = SampleData.comments[1],
            viewerWallSlugs = emptySet(),
            isDeleting = false,
            onDeleteRequest = {},
            onWallTap = {},
        ) // reply
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CommentInputBarPreview() {
    EnteGramTheme {
        CommentInputBar(
            text = "",
            onTextChange = {},
            onSend = {},
            isSending = false,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun CommentInputBarWithTextPreview() {
    EnteGramTheme {
        CommentInputBar(
            text = "This is amazing!",
            onTextChange = {},
            onSend = {},
            isSending = false,
        )
    }
}
