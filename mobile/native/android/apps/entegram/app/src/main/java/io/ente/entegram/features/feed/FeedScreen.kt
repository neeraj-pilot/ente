package io.ente.entegram.features.feed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.core.logging.AppLogger
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.Wall
import io.ente.entegram.ui.components.AvatarView
import io.ente.entegram.ui.components.EmptyStateView
import io.ente.entegram.ui.components.ErrorStateView
import io.ente.entegram.ui.components.LoadingStateView
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onPostTap: (Long) -> Unit,
    onCommunityTap: () -> Unit,
    onWallTap: (String) -> Unit,
    onComposeTap: () -> Unit,
    onProfileTap: () -> Unit,
    onConnectionsTap: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val viewerWall by viewModel.viewerWall.collectAsStateWithLifecycle()
    var showProfileSheet by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showLogsDialog by remember { mutableStateOf(false) }
    var logSnapshot by remember { mutableStateOf("") }
    var pendingDeletePost by remember { mutableStateOf<Post?>(null) }
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier) {
        // Crossfade between states using AnimatedContent with longFade
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(Motion.longFade()) togetherWith fadeOut(Motion.longFade())
            },
            contentKey = { state ->
                when (state) {
                    is FeedUiState.Loading -> "loading"
                    is FeedUiState.Empty -> "empty"
                    is FeedUiState.Error -> "error"
                    is FeedUiState.Ready -> "ready"
                }
            },
            label = "feedState",
        ) { state ->
            when (state) {
                is FeedUiState.Loading -> {
                    LoadingStateView(
                        modifier = Modifier.padding(top = Space.xxl),
                    )
                }

                is FeedUiState.Empty -> {
                    FeedEmptyState(
                        viewerWall = viewerWall,
                        onAvatarTap = { showProfileSheet = true },
                        onCommunityTap = onCommunityTap,
                        onComposeTap = onComposeTap,
                    )
                }

                is FeedUiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = { viewModel.refresh() },
                    )
                }

                is FeedUiState.Ready -> {
                    FeedContent(
                        state = state,
                        viewerWall = viewerWall,
                        onRefresh = { viewModel.refresh() },
                        onLoadMore = { viewModel.loadMore() },
                        onPostTap = onPostTap,
                        onCommunityTap = onCommunityTap,
                        onWallTap = onWallTap,
                        onComposeTap = onComposeTap,
                        onAvatarTap = { showProfileSheet = true },
                        onLikeTap = { viewModel.toggleLike(it) },
                        onDeletePost = { pendingDeletePost = it },
                    )
                }
            }
        }
    }

    if (pendingDeletePost != null) {
        AlertDialog(
            onDismissRequest = { pendingDeletePost = null },
            title = { Text(text = stringResource(R.string.post_delete_title)) },
            text = { Text(text = stringResource(R.string.post_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val postId = pendingDeletePost?.id ?: return@TextButton
                        pendingDeletePost = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.deletePost(postId)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.post_menu_delete),
                        color = MaterialTheme.colorScheme.error,
                        style = Typo.bodyEmphasized,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePost = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }

    // Profile bottom sheet
    if (showProfileSheet) {
        ProfileBottomSheet(
            viewerWall = viewerWall,
            onDismiss = { showProfileSheet = false },
            onProfileTap = {
                showProfileSheet = false
                onProfileTap()
            },
            onConnectionsTap = {
                showProfileSheet = false
                onConnectionsTap()
            },
            onDiscoverTap = {
                showProfileSheet = false
                onCommunityTap()
            },
            onViewLogs = {
                showProfileSheet = false
                logSnapshot = AppLogger.snapshot()
                showLogsDialog = true
            },
            onSignOut = {
                showProfileSheet = false
                showSignOutDialog = true
            },
        )
    }

    if (showLogsDialog) {
        LogsDialog(
            logs = logSnapshot,
            onRefresh = {
                logSnapshot = AppLogger.snapshot()
            },
            onDismiss = {
                showLogsDialog = false
            },
        )
    }

    // Sign out confirmation dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.settings_sign_out_dialog_title),
                    style = Typo.title,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.settings_sign_out_dialog_body),
                    style = Typo.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSignOut()
                    },
                ) {
                    Text(
                        text = stringResource(R.string.settings_sign_out),
                        color = MaterialTheme.colorScheme.error,
                        style = Typo.bodyEmphasized,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) {
                    Text(
                        text = stringResource(R.string.cancel),
                        style = Typo.body,
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LogsDialog(
    logs: String,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val emptyLogsText = stringResource(R.string.settings_logs_empty)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_logs_title),
                style = Typo.title,
            )
        },
        text = {
            SelectionContainer {
                Text(
                    text = logs.ifBlank { emptyLogsText },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(scrollState),
                    style = Typo.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.dismiss),
                    style = Typo.body,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onRefresh) {
                Text(
                    text = stringResource(R.string.settings_logs_refresh),
                    style = Typo.body,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun FeedEmptyState(
    viewerWall: Wall?,
    onAvatarTap: () -> Unit,
    onCommunityTap: () -> Unit,
    onComposeTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar with title + avatar
        FeedTopBar(viewerWall = viewerWall, onAvatarTap = onAvatarTap)

        // Empty state content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(Space.md))
            Text(
                text = stringResource(R.string.feed_empty_headline),
                style = Typo.title,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Space.sm))
            Text(
                text = stringResource(R.string.feed_empty_body),
                style = Typo.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(Space.xl))
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onComposeTap()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(Radius.lg),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Space.sm),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.feed_empty_create_post),
                        style = Typo.bodyEmphasized,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Space.md))
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCommunityTap()
            }) {
                Text(
                    text = stringResource(R.string.feed_empty_action),
                    style = Typo.bodyEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedContent(
    state: FeedUiState.Ready,
    viewerWall: Wall?,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPostTap: (Long) -> Unit,
    onCommunityTap: () -> Unit,
    onWallTap: (String) -> Unit,
    onComposeTap: () -> Unit,
    onAvatarTap: () -> Unit,
    onLikeTap: (Long) -> Unit,
    onDeletePost: (Post) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val listState = rememberLazyListState()

    // Trigger load-more when scrolled near the bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.posts.size - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore) {
            onLoadMore()
        }
    }

    // Track which items have appeared for stagger animation
    val appearedItems = remember { mutableStateMapOf<Long, Boolean>() }

    Box(modifier = modifier) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                // Feed title with avatar
                item(key = "feed-title") {
                    FeedTopBar(viewerWall = viewerWall, onAvatarTap = onAvatarTap)
                }

                // Offline banner
                item(key = "offline-banner") {
                    AnimatedVisibility(
                        visible = state.isOffline,
                        enter = expandVertically(Motion.settle()) + fadeIn(Motion.quickFade()),
                        exit = shrinkVertically(Motion.settle()) + fadeOut(Motion.quickFade()),
                    ) {
                        val offlineDescription = stringResource(R.string.feed_offline_description)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = Space.md, vertical = Space.sm)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                )
                                .padding(horizontal = Space.md, vertical = Space.sm)
                                .semantics {
                                    contentDescription = offlineDescription
                                    liveRegion = LiveRegionMode.Polite
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(Space.sm))
                            Text(
                                text = stringResource(R.string.feed_offline_banner),
                                style = Typo.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Start,
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = state.posts,
                    key = { _, post -> post.id },
                ) { _, post ->
                    LaunchedEffect(post.id) {
                        appearedItems[post.id] = true
                    }

                    AnimatedVisibility(
                        visible = appearedItems[post.id] == true,
                        enter = fadeIn(Motion.longFade()) + slideInVertically(
                            animationSpec = Motion.soft(),
                            initialOffsetY = { 48 },
                        ),
                    ) {
                        FeedPostCard(
                            post = post,
                            onTap = { onPostTap(post.id) },
                            onLikeTap = { onLikeTap(post.id) },
                            onCommentTap = { onPostTap(post.id) },
                            onAuthorTap = { onWallTap(post.authorSlug) },
                            isViewerPost = post.authorSlug == viewerWall?.slug,
                            onDeleteTap = { onDeletePost(post) },
                        )
                    }
                }

                // Loading-more indicator at the bottom
                if (state.isLoadingMore) {
                    item(key = "loading-more") {
                        val loadingMoreDesc = stringResource(R.string.feed_loading_more)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Space.lg)
                                .semantics { contentDescription = loadingMoreDesc },
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                }

                // Bottom spacer for FAB clearance
                item(key = "bottom-spacer") {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // Compose FAB
        FloatingActionButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onComposeTap()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(Space.md),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = RoundedCornerShape(Radius.lg),
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.feed_new_post),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

// ── Feed top bar ──────────────────────────────────────────────

@Composable
private fun FeedTopBar(
    viewerWall: Wall?,
    onAvatarTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = Space.md)
            .padding(top = Space.lg, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = Typo.titleXL,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onAvatarTap),
        ) {
            AvatarView(
                slug = viewerWall?.slug ?: "",
                displayName = viewerWall?.displayName,
                avatarObjectKey = viewerWall?.avatarObjectKey,
                size = 32.dp,
            )
        }
    }
}

// ── Profile bottom sheet ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileBottomSheet(
    viewerWall: Wall?,
    onDismiss: () -> Unit,
    onProfileTap: () -> Unit,
    onConnectionsTap: () -> Unit,
    onDiscoverTap: () -> Unit,
    onViewLogs: () -> Unit,
    onSignOut: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val haptic = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Space.xl),
        ) {
            // Profile peek — tappable row navigates to full profile
            if (viewerWall != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onProfileTap()
                        }
                        .padding(horizontal = Space.lg, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AvatarView(
                        slug = viewerWall.slug,
                        displayName = viewerWall.displayName,
                        avatarObjectKey = viewerWall.avatarObjectKey,
                        size = 48.dp,
                    )
                    Spacer(modifier = Modifier.width(Space.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = viewerWall.displayName
                                ?: stringResource(R.string.settings_you_fallback),
                            style = Typo.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "@${viewerWall.slug}",
                            style = Typo.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
                )
            }

            // Menu items
            ProfileSheetRow(
                icon = Icons.Outlined.People,
                label = stringResource(R.string.profile_sheet_connections),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onConnectionsTap()
                },
            )
            ProfileSheetRow(
                icon = Icons.Outlined.Search,
                label = stringResource(R.string.profile_sheet_discover),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDiscoverTap()
                },
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.xs),
            )

            ProfileSheetRow(
                icon = Icons.Outlined.Shield,
                label = stringResource(R.string.settings_privacy_policy),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    uriHandler.openUri("https://ente.io/privacy")
                },
            )
            ProfileSheetRow(
                icon = Icons.AutoMirrored.Outlined.Article,
                label = stringResource(R.string.settings_view_logs),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onViewLogs()
                },
            )
            ProfileSheetRow(
                icon = Icons.AutoMirrored.Outlined.ExitToApp,
                label = stringResource(R.string.settings_sign_out),
                isDestructive = true,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSignOut()
                },
            )

            // Version
            Spacer(modifier = Modifier.height(Space.md))
            Text(
                text = "0.1.0",
                style = Typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.lg),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProfileSheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
) {
    val tint = if (isDestructive) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val labelColor = if (isDestructive) {
        tint
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = tint,
        )
        Spacer(modifier = Modifier.width(Space.md))
        Text(
            text = label,
            style = Typo.body,
            color = labelColor,
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Previews ────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedScreenLoadingPreview() {
    EnteGramTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            LoadingStateView(modifier = Modifier.padding(top = Space.xxl))
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedScreenEmptyPreview() {
    EnteGramTheme {
        FeedEmptyState(
            viewerWall = null,
            onAvatarTap = {},
            onCommunityTap = {},
            onComposeTap = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun OfflineBannerPreview() {
    EnteGramTheme {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.sm)
                .clip(RoundedCornerShape(Radius.sm))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                )
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(Space.sm))
            Text(
                text = "Offline \u00b7 showing cached posts",
                style = Typo.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FeedTopBarPreview() {
    EnteGramTheme {
        FeedTopBar(viewerWall = null, onAvatarTap = {})
    }
}
