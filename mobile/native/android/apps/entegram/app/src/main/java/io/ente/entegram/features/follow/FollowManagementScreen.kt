package io.ente.entegram.features.follow

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Outbox
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.core.models.FollowRequest
import io.ente.entegram.ui.components.AppTopBar
import io.ente.entegram.ui.components.DividerInset
import io.ente.entegram.ui.components.EmptyStateView
import io.ente.entegram.ui.components.ErrorStateView
import io.ente.entegram.ui.components.FollowLoadingStateView
import io.ente.entegram.ui.components.RelativeTimeLabel
import io.ente.entegram.ui.components.SegmentedTabBar
import io.ente.entegram.ui.components.SubtleActionButton
import io.ente.entegram.ui.components.UserRow
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun FollowManagementScreen(
    onBack: () -> Unit,
    onWallTap: (String) -> Unit = {},
    viewModel: FollowManagementViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    FollowManagementContent(
        uiState = uiState,
        onBack = onBack,
        onTabSelected = viewModel::selectTab,
        onApprove = viewModel::approve,
        onReject = viewModel::reject,
        onCancelSent = viewModel::cancelSent,
        onRetry = viewModel::refresh,
        onWallTap = onWallTap,
    )
}

@Composable
private fun FollowManagementContent(
    uiState: FollowUiState,
    onBack: () -> Unit,
    onTabSelected: (FollowTab) -> Unit,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onCancelSent: (Long) -> Unit,
    onRetry: () -> Unit,
    onWallTap: (String) -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppTopBar(
            title = stringResource(R.string.follow_title),
            onBack = onBack,
        )

        when (uiState) {
            is FollowUiState.Loading -> {
                Spacer(modifier = Modifier.height(Space.md))
                FollowLoadingStateView(
                    modifier = Modifier.padding(horizontal = Space.md),
                )
            }

            is FollowUiState.Empty -> {
                EmptyStateView(
                    icon = Icons.Outlined.People,
                    headline = stringResource(R.string.follow_no_activity_headline),
                    body = stringResource(R.string.follow_no_activity_body),
                )
            }

            is FollowUiState.Error -> {
                ErrorStateView(
                    message = uiState.message,
                    onRetry = onRetry,
                )
            }

            is FollowUiState.Ready -> {
                ReadyContent(
                    state = uiState,
                    onTabSelected = onTabSelected,
                    onApprove = onApprove,
                    onReject = onReject,
                    onCancelSent = onCancelSent,
                    onWallTap = onWallTap,
                )
            }
        }
    }
}

@Composable
private fun ReadyContent(
    state: FollowUiState.Ready,
    onTabSelected: (FollowTab) -> Unit,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onCancelSent: (Long) -> Unit,
    onWallTap: (String) -> Unit = {},
) {
    val tabs = FollowTab.entries
    val currentList = when (state.selectedTab) {
        FollowTab.Followers -> state.followers
        FollowTab.Following -> state.following
        FollowTab.Received -> state.incoming
        FollowTab.Sent -> state.outgoing
    }

    val followersLabel = stringResource(R.string.follow_tab_followers)
    val followingLabel = stringResource(R.string.follow_tab_following)
    val receivedLabel = stringResource(R.string.follow_tab_received)
    val sentLabel = stringResource(R.string.follow_tab_sent)

    Column {
        SegmentedTabBar(
            items = tabs,
            selectedItem = state.selectedTab,
            onItemSelected = onTabSelected,
            label = { tab ->
                when (tab) {
                    FollowTab.Followers -> followersLabel + badgeText(state.followers.size)
                    FollowTab.Following -> followingLabel + badgeText(state.following.size)
                    FollowTab.Received -> receivedLabel + badgeText(state.incoming.size)
                    FollowTab.Sent -> sentLabel + badgeText(state.outgoing.size)
                }
            },
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
        )

        if (currentList.isEmpty()) {
            TabEmptyState(tab = state.selectedTab)
        } else {
            FollowList(
                requests = currentList,
                tab = state.selectedTab,
                processingIds = state.processingIds,
                dismissingIds = state.dismissingIds,
                onApprove = onApprove,
                onReject = onReject,
                onCancelSent = onCancelSent,
                onWallTap = onWallTap,
            )
        }
    }
}

@Composable
private fun FollowList(
    requests: List<FollowRequest>,
    tab: FollowTab,
    processingIds: Set<Long>,
    dismissingIds: Map<Long, DismissState>,
    onApprove: (Long) -> Unit,
    onReject: (Long) -> Unit,
    onCancelSent: (Long) -> Unit,
    onWallTap: (String) -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = requests,
            key = { _, req -> "${tab.name}:${req.id}:${req.wallId}:${req.fromSlug}" },
        ) { index, request ->
            val dismissState = dismissingIds[request.id]
            val isTinting = dismissState != null
            val isSliding = dismissState?.sliding == true
            val visibleState = remember(request.id) {
                MutableTransitionState(false).apply { targetState = true }
            }
            // Only trigger exit animation when phase 2 (sliding) begins
            visibleState.targetState = !isSliding

            // Tint flash: green for approved, red-ish for rejected, neutral for cancelled
            val tintColor = when (dismissState?.reason) {
                DismissReason.Approved -> MaterialTheme.colorScheme.secondaryContainer
                DismissReason.Rejected -> MaterialTheme.colorScheme.errorContainer
                DismissReason.Cancelled -> MaterialTheme.colorScheme.surfaceVariant
                null -> MaterialTheme.colorScheme.background
            }
            val tintAlpha by animateFloatAsState(
                targetValue = if (isTinting) 1f else 0f,
                animationSpec = Motion.quickFade(),
                label = "row-tint-${ request.id }",
            )

            AnimatedVisibility(
                visibleState = visibleState,
                enter = fadeIn(Motion.quickFade()) + slideInVertically(
                    animationSpec = Motion.soft(),
                    initialOffsetY = { it / 4 },
                ),
                exit = fadeOut(Motion.quickFade()) + slideOutHorizontally(
                    animationSpec = Motion.soft(),
                    targetOffsetX = { -it / 5 },
                ) + shrinkVertically(Motion.settle()),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(tintColor.copy(alpha = tintAlpha * 0.85f)),
                ) {
                    UserRow(
                        slug = request.fromSlug,
                        displayName = request.fromDisplayName,
                        modifier = Modifier.padding(horizontal = Space.md),
                        onClick = { onWallTap(request.fromSlug) },
                        subtitle = if (request.createdAt == Instant.EPOCH) {
                            null
                        } else {
                            { RelativeTimeLabel(instant = request.createdAt) }
                        },
                        trailing = {
                            when (tab) {
                                FollowTab.Received -> {
                                    val isProcessing = request.id in processingIds
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        TextButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                onReject(request.id)
                                            },
                                            enabled = !isProcessing && !isTinting,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        ) {
                                            Text(
                                                text = stringResource(R.string.follow_reject),
                                                style = Typo.caption,
                                                color = if (!isProcessing && !isTinting) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                },
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(Space.xxs))
                                        ApproveButton(
                                            enabled = !isProcessing && !isTinting,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                onApprove(request.id)
                                            },
                                        )
                                    }
                                }

                                FollowTab.Sent -> {
                                    SubtleActionButton(
                                        text = stringResource(R.string.cancel),
                                        enabled = request.id !in processingIds && !isTinting,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            onCancelSent(request.id)
                                        },
                                    )
                                }

                                FollowTab.Followers -> {
                                    // Followers tab: just the timestamp shown in subtitle, no action
                                }

                                FollowTab.Following -> {
                                    // Following tab: no action needed
                                }
                            }
                        },
                    )
                    if (index < requests.lastIndex) {
                        DividerInset()
                    }
                }
            }
        }
    }
}

@Composable
private fun TabEmptyState(tab: FollowTab) {
    when (tab) {
        FollowTab.Received -> EmptyStateView(
            icon = Icons.Outlined.GroupAdd,
            headline = stringResource(R.string.follow_requests_empty_headline),
            body = stringResource(R.string.follow_requests_empty_body),
        )

        FollowTab.Sent -> EmptyStateView(
            icon = Icons.Outlined.Outbox,
            headline = stringResource(R.string.follow_sent_empty_headline),
            body = stringResource(R.string.follow_sent_empty_body),
        )

        FollowTab.Followers -> EmptyStateView(
            icon = Icons.Outlined.People,
            headline = stringResource(R.string.follow_followers_empty_headline),
            body = stringResource(R.string.follow_followers_empty_body),
        )

        FollowTab.Following -> EmptyStateView(
            icon = Icons.Outlined.PersonAdd,
            headline = stringResource(R.string.follow_following_empty_headline),
            body = stringResource(R.string.follow_following_empty_body),
        )
    }
}

/**
 * Softened mint approve button — secondary @ 0.18α fill per PLAN.md.
 * Not extracted to ui/components/ because it's single-use (follow requests only).
 */
@Composable
private fun ApproveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = Motion.snap(),
        label = "approve-btn-scale",
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp, minWidth = 1.dp)
            .scale(scale),
        enabled = enabled,
        shape = RoundedCornerShape(Radius.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
            disabledContentColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        interactionSource = interactionSource,
    ) {
        Text(
            text = stringResource(R.string.follow_approve),
            style = Typo.caption,
        )
    }
}

private fun badgeText(count: Int): String =
    if (count > 0) " ($count)" else ""

// ── Previews ────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FollowLoadingPreview() {
    EnteGramTheme {
        FollowManagementContent(
            uiState = FollowUiState.Loading,
            onBack = {},
            onTabSelected = {},
            onApprove = {},
            onReject = {},
            onCancelSent = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FollowEmptyPreview() {
    EnteGramTheme {
        FollowManagementContent(
            uiState = FollowUiState.Empty,
            onBack = {},
            onTabSelected = {},
            onApprove = {},
            onReject = {},
            onCancelSent = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FollowErrorPreview() {
    EnteGramTheme {
        FollowManagementContent(
            uiState = FollowUiState.Error("Could not load follow requests."),
            onBack = {},
            onTabSelected = {},
            onApprove = {},
            onReject = {},
            onCancelSent = {},
            onRetry = {},
        )
    }
}

private val now = Instant.now()

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun FollowRequestsTabPreview() {
    EnteGramTheme {
        FollowManagementContent(
            uiState = FollowUiState.Ready(
                incoming = listOf(
                    FollowRequest(
                        id = 1,
                        fromUserId = 100,
                        fromSlug = "pascal",
                        fromDisplayName = "Pascal",
                        wallId = "wall-you",
                        createdAt = now.minus(8, ChronoUnit.HOURS),
                        direction = FollowRequest.Direction.Incoming,
                    ),
                    FollowRequest(
                        id = 2,
                        fromUserId = 101,
                        fromSlug = "junebird",
                        fromDisplayName = "June",
                        wallId = "wall-you",
                        createdAt = now.minus(1, ChronoUnit.DAYS),
                        direction = FollowRequest.Direction.Incoming,
                    ),
                ),
                outgoing = listOf(
                    FollowRequest(
                        id = 3,
                        fromUserId = 42,
                        fromSlug = "teo",
                        fromDisplayName = "Teo Ogawa",
                        wallId = "wall-teo",
                        createdAt = now.minus(2, ChronoUnit.DAYS),
                        direction = FollowRequest.Direction.Outgoing,
                    ),
                ),
                followers = emptyList(),
                following = emptyList(),
                selectedTab = FollowTab.Received,
            ),
            onBack = {},
            onTabSelected = {},
            onApprove = {},
            onReject = {},
            onCancelSent = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun FollowSentTabPreview() {
    EnteGramTheme {
        FollowManagementContent(
            uiState = FollowUiState.Ready(
                incoming = emptyList(),
                outgoing = listOf(
                    FollowRequest(
                        id = 3,
                        fromUserId = 42,
                        fromSlug = "teo",
                        fromDisplayName = "Teo Ogawa",
                        wallId = "wall-teo",
                        createdAt = now.minus(2, ChronoUnit.DAYS),
                        direction = FollowRequest.Direction.Outgoing,
                    ),
                ),
                followers = emptyList(),
                following = emptyList(),
                selectedTab = FollowTab.Sent,
            ),
            onBack = {},
            onTabSelected = {},
            onApprove = {},
            onReject = {},
            onCancelSent = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun FollowFollowersEmptyPreview() {
    EnteGramTheme {
        FollowManagementContent(
            uiState = FollowUiState.Ready(
                incoming = listOf(
                    FollowRequest(
                        id = 1,
                        fromUserId = 100,
                        fromSlug = "pascal",
                        fromDisplayName = "Pascal",
                        wallId = "wall-you",
                        createdAt = now.minus(8, ChronoUnit.HOURS),
                        direction = FollowRequest.Direction.Incoming,
                    ),
                ),
                outgoing = emptyList(),
                followers = emptyList(),
                following = emptyList(),
                selectedTab = FollowTab.Followers,
            ),
            onBack = {},
            onTabSelected = {},
            onApprove = {},
            onReject = {},
            onCancelSent = {},
            onRetry = {},
        )
    }
}
