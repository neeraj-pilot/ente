package io.ente.entegram.features.wall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.PostAssetVariant
import io.ente.entegram.core.models.Wall
import io.ente.entegram.core.services.SampleData
import io.ente.entegram.ui.components.AppTopBar
import io.ente.entegram.ui.components.EncryptedAssetImage
import io.ente.entegram.ui.components.EmptyStateView
import io.ente.entegram.ui.components.ErrorStateView
import io.ente.entegram.ui.components.ProfileStat
import io.ente.entegram.ui.components.ProfileSummaryHeader
import io.ente.entegram.ui.components.ShimmerBox
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

@Composable
fun WallScreen(
    onBack: () -> Unit,
    onPostTap: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        AppTopBar(
            title = stringResource(R.string.wall_title),
            onBack = onBack,
        )

        when (val state = uiState) {
            is WallUiState.Loading -> {
                WallLoadingStateView()
            }

            is WallUiState.Empty -> {
                EmptyStateView(
                    icon = Icons.Outlined.CameraAlt,
                    headline = stringResource(R.string.wall_empty_headline),
                    body = stringResource(R.string.wall_empty_body),
                )
            }

            is WallUiState.Error -> {
                ErrorStateView(
                    message = state.message,
                    onRetry = { viewModel.refresh() },
                )
            }

            is WallUiState.Ready -> {
                WallContent(
                    state = state,
                    onRefresh = { viewModel.refresh() },
                    onLoadMore = { viewModel.loadMore() },
                    onPostTap = onPostTap,
                    onRequestFollow = viewModel::requestFollow,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WallContent(
    state: WallUiState.Ready,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPostTap: (Long) -> Unit,
    onRequestFollow: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val appearedItems = remember { mutableStateMapOf<Long, Boolean>() }

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // Header takes item 0, so offset by 1
            lastVisible >= state.posts.size - 2
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && state.hasMore && !state.isLoadingMore) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Profile header — full-width span
            item(
                key = "wall-header",
                span = { GridItemSpan(3) },
            ) {
                WallProfileHeader(
                    wall = state.wall,
                    relationship = state.relationship,
                    accessDenied = state.accessDenied,
                    isFollowActionLoading = state.isFollowActionLoading,
                    onRequestFollow = onRequestFollow,
                )
            }

            if (state.accessDenied) {
                item(
                    key = "locked-wall",
                    span = { GridItemSpan(3) },
                ) {
                    LockedWallHint(
                        relationship = state.relationship,
                        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md),
                    )
                }
            } else {
                // Post grid
                itemsIndexed(
                    items = state.posts,
                    key = { _, post -> post.id },
                ) { _, post ->
                    LaunchedEffect(post.id) {
                        appearedItems[post.id] = true
                    }

                    AnimatedVisibility(
                        visible = appearedItems[post.id] == true,
                        enter = fadeIn(Motion.quickFade()) + slideInVertically(
                            animationSpec = Motion.soft(),
                            initialOffsetY = { 24 },
                        ),
                    ) {
                        WallPostTile(
                            post = post,
                            onTap = { onPostTap(post.id) },
                        )
                    }
                }
            }

            // Loading-more indicator
            if (state.isLoadingMore) {
                item(
                    key = "loading-more",
                    span = { GridItemSpan(3) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Space.lg),
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

            // Bottom spacer
            item(
                key = "bottom-spacer",
                span = { GridItemSpan(3) },
            ) {
                Spacer(modifier = Modifier.height(Space.xxl))
            }
        }
    }
}

@Composable
private fun WallProfileHeader(
    wall: Wall,
    relationship: String,
    accessDenied: Boolean,
    isFollowActionLoading: Boolean,
    onRequestFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Space.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileSummaryHeader(
            wall = wall,
            stats = listOf(
                ProfileStat(wall.postCount, stringResource(R.string.posts)),
                ProfileStat(wall.followerCount, stringResource(R.string.followers)),
                ProfileStat(wall.followingCount, stringResource(R.string.following)),
            ),
        ) {
            when (relationship) {
                "idle" -> {
                    Button(
                        onClick = onRequestFollow,
                        enabled = !isFollowActionLoading,
                    ) {
                        Text(
                            text = if (isFollowActionLoading) {
                                stringResource(R.string.wall_follow_loading)
                            } else {
                                stringResource(R.string.wall_follow)
                            },
                        )
                    }
                }
                "pending" -> {
                    Text(
                        text = stringResource(R.string.wall_follow_pending),
                        style = Typo.bodyEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                "following" -> {
                    Text(
                        text = stringResource(R.string.wall_following),
                        style = Typo.bodyEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (!accessDenied) {
            // Divider-ish section header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.GridView,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(Space.sm))
                Text(
                    text = stringResource(R.string.posts),
                    style = Typo.bodyEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(modifier = Modifier.height(Space.md))
        }
    }
}

@Composable
private fun LockedWallHint(
    relationship: String,
    modifier: Modifier = Modifier,
) {
    val message = when (relationship) {
        "pending" -> stringResource(R.string.wall_locked_pending)
        "following" -> stringResource(R.string.wall_locked_following)
        else -> stringResource(R.string.wall_locked_idle)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(Space.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = Typo.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WallPostTile(
    post: Post,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val asset = post.assets.firstOrNull { it.variant == PostAssetVariant.Thumbnail }
        ?: post.assets.firstOrNull { it.variant == PostAssetVariant.Full }
    val photoCount = remember(post.assets) {
        post.assets.count { it.variant == PostAssetVariant.Full }
    }
    val description = remember(post.caption.text, photoCount) {
        val captionSnippet = post.caption.text.take(80)
        if (photoCount > 1) "$captionSnippet, $photoCount photos" else captionSnippet
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(Radius.xs))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTap()
            }
            .semantics { contentDescription = description },
    ) {
        if (asset != null) {
            EncryptedAssetImage(
                objectKey = asset.objectKey,
                blurHash = asset.blurHash,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }

        // Multi-photo indicator badge
        if (photoCount > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Space.xs)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(Radius.xs),
                    )
                    .padding(Space.xxs),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

// ── Loading state ──────────────────────────────────────────────

@Composable
private fun WallLoadingStateView(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(Space.md))

        // Avatar skeleton
        ShimmerBox(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape),
            cornerRadius = 36.dp,
        )
        Spacer(modifier = Modifier.height(Space.md))

        // Name skeleton
        ShimmerBox(
            modifier = Modifier
                .width(140.dp)
                .height(18.dp),
        )
        Spacer(modifier = Modifier.height(Space.sm))

        // Slug skeleton
        ShimmerBox(
            modifier = Modifier
                .width(100.dp)
                .height(14.dp),
        )
        Spacer(modifier = Modifier.height(Space.lg))

        // Stats skeleton
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.xxl),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ShimmerBox(modifier = Modifier.width(40.dp).height(18.dp))
                Spacer(modifier = Modifier.height(Space.xxs))
                ShimmerBox(modifier = Modifier.width(32.dp).height(12.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ShimmerBox(modifier = Modifier.width(40.dp).height(18.dp))
                Spacer(modifier = Modifier.height(Space.xxs))
                ShimmerBox(modifier = Modifier.width(48.dp).height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(Space.lg))

        // Grid skeleton — 3x3
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(3) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(3) {
                        ShimmerBox(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f),
                            cornerRadius = Radius.xs,
                        )
                    }
                }
            }
        }
    }
}

// ── Previews ───────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WallProfileHeaderPreview() {
    EnteGramTheme {
        WallProfileHeader(
            wall = SampleData.lena,
            relationship = "idle",
            accessDenied = false,
            isFollowActionLoading = false,
            onRequestFollow = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WallPostTilePreview() {
    EnteGramTheme {
        WallPostTile(
            post = SampleData.posts.first(),
            onTap = {},
            modifier = Modifier.size(120.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WallPostTileMultiPhotoPreview() {
    EnteGramTheme {
        WallPostTile(
            post = SampleData.posts[1], // multi-photo post
            onTap = {},
            modifier = Modifier.size(120.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WallLoadingPreview() {
    EnteGramTheme {
        WallLoadingStateView()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WallEmptyPreview() {
    EnteGramTheme {
        EmptyStateView(
            icon = Icons.Outlined.CameraAlt,
            headline = "No posts yet",
            body = "Share your first moment and it will appear here.",
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun WallScreenErrorPreview() {
    EnteGramTheme {
        ErrorStateView(
            message = "Could not load wall.",
            onRetry = {},
        )
    }
}
