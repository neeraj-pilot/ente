package io.ente.entegram.features.community

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.core.models.CommunityResult
import io.ente.entegram.ui.components.AppTopBar
import io.ente.entegram.ui.components.CommunityLoadingStateView
import io.ente.entegram.ui.components.DividerInset
import io.ente.entegram.ui.components.EmptyStateView
import io.ente.entegram.ui.components.ErrorStateView
import io.ente.entegram.ui.components.UserRow
import io.ente.entegram.ui.design.Motion
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

@Composable
fun CommunityScreen(
    onBack: (() -> Unit)? = null,
    onWallTap: (String) -> Unit = {},
    viewModel: CommunityViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val query by viewModel.query.collectAsState()

    CommunityContent(
        uiState = uiState,
        query = query,
        onBack = onBack,
        onQueryChange = viewModel::updateQuery,
        onRetry = viewModel::retry,
        onWallTap = onWallTap,
    )
}

@Composable
private fun CommunityContent(
    uiState: CommunityUiState,
    query: String,
    onBack: (() -> Unit)?,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onWallTap: (String) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current

    // Hoisted for use inside non-composable semantics lambda
    val searchWallsDesc = stringResource(R.string.community_search_walls)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        AppTopBar(
            title = stringResource(R.string.community_title),
            onBack = onBack,
        )

        // Search field
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.md, vertical = Space.sm)
                .focusRequester(focusRequester)
                .semantics { contentDescription = searchWallsDesc },
            placeholder = {
                Text(
                    text = stringResource(R.string.community_search_placeholder),
                    style = Typo.body,
                )
            },
            leadingIcon = {
                // Swap search icon for a spinner while loading (no stale results showing)
                if (uiState is CommunityUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = stringResource(R.string.community_search_icon),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onQueryChange("")
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.community_clear_search),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else null,
            textStyle = Typo.body.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = true,
            shape = RoundedCornerShape(Radius.md),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                cursorColor = MaterialTheme.colorScheme.primary,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    keyboardController?.hide()
                },
            ),
        )

        // Crossfade between states (matching Feed's AnimatedContent pattern)
        AnimatedContent(
            targetState = uiState,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(Motion.longFade()) togetherWith fadeOut(Motion.longFade())
            },
            contentKey = { state ->
                when (state) {
                    is CommunityUiState.Idle -> "idle"
                    is CommunityUiState.Loading -> "loading"
                    is CommunityUiState.Empty -> "empty"
                    is CommunityUiState.Error -> "error"
                    is CommunityUiState.Ready -> "ready"
                }
            },
            label = "community-state",
        ) { state ->
            when (state) {
                is CommunityUiState.Idle -> {
                    IdleContent()
                }

                is CommunityUiState.Loading -> {
                    CommunityLoadingStateView(
                        modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                        rowCount = 4,
                    )
                }

                is CommunityUiState.Empty -> {
                    EmptyStateView(
                        icon = Icons.Outlined.SearchOff,
                        headline = stringResource(R.string.community_no_results_headline),
                        body = stringResource(R.string.community_no_results_body),
                    )
                }

                is CommunityUiState.Error -> {
                    ErrorStateView(
                        message = state.message,
                        onRetry = onRetry,
                    )
                }

                is CommunityUiState.Ready -> {
                    ResultsList(
                        results = state.results,
                        refreshing = state.refreshing,
                        onWallTap = onWallTap,
                    )
                }
            }
        }
    }
}

@Composable
private fun IdleContent() {
    EmptyStateView(
        icon = Icons.Outlined.Explore,
        headline = stringResource(R.string.community_idle_headline),
        body = stringResource(R.string.community_idle_body),
    )
}

@Composable
private fun ResultsList(
    results: List<CommunityResult>,
    refreshing: Boolean,
    onWallTap: (String) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val resultsAlpha by animateFloatAsState(
        targetValue = if (refreshing) 0.6f else 1f,
        animationSpec = Motion.quickFade(),
        label = "results-alpha",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .alpha(resultsAlpha),
        ) {
            // Subtle linear progress at the top when refreshing with stale results
            if (refreshing) {
                item(key = "refresh-indicator") {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    )
                }
            }

            itemsIndexed(
                items = results,
                key = { _, result -> result.id },
            ) { index, result ->
                val visibleState = remember(result.id) {
                    MutableTransitionState(false).apply { targetState = true }
                }
                AnimatedVisibility(
                    visibleState = visibleState,
                    enter = fadeIn(Motion.quickFade()) + slideInVertically(
                        animationSpec = Motion.soft(),
                        initialOffsetY = { it / 4 },
                    ),
                    exit = fadeOut(Motion.quickFade()) + shrinkVertically(Motion.settle()),
                ) {
                    // Hoisted for use inside non-composable semantics lambda
                    val viewWallDesc = stringResource(
                        R.string.community_view_wall,
                        result.displayName ?: "@${result.slug}",
                    )
                    Column(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onWallTap(result.slug)
                            }
                            .semantics {
                                contentDescription = viewWallDesc
                            },
                    ) {
                        UserRow(
                            slug = result.slug,
                            displayName = result.displayName,
                            modifier = Modifier.padding(horizontal = Space.md),
                            subtitle = {
                                Text(
                                    text = "@${result.slug}",
                                    style = Typo.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            },
                            trailing = {
                                val badge = result.relationship
                                if (!badge.isNullOrBlank()) {
                                    RelationshipBadge(relationship = badge)
                                } else {
                                    FollowerCountBadge(count = result.followerCount)
                                }
                            },
                        )
                        if (index < results.lastIndex) {
                            DividerInset()
                        }
                    }
                }
            }

            item(key = "bottom-spacer") {
                Spacer(modifier = Modifier.height(Space.xxl))
            }
        }
    }
}

@Composable
private fun RelationshipBadge(relationship: String) {
    val label = when (relationship.lowercase()) {
        "following" -> stringResource(R.string.wall_following)
        "pending" -> stringResource(R.string.wall_follow_pending)
        "self" -> stringResource(R.string.settings_you_fallback)
        else -> relationship.replaceFirstChar { it.uppercase() }
    }
    val color = when (relationship.lowercase()) {
        "following" -> MaterialTheme.colorScheme.primary
        "pending" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = label,
        style = Typo.caption,
        color = color.copy(alpha = 0.85f),
        modifier = Modifier
            .background(
                color.copy(alpha = 0.12f),
                RoundedCornerShape(Radius.pill),
            )
            .padding(horizontal = Space.sm, vertical = Space.xxs),
    )
}

@Composable
private fun FollowerCountBadge(count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatFollowerCount(count),
            style = Typo.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(Space.xxs))
        Text(
            text = if (count == 1) stringResource(R.string.follow_follower_singular)
                else stringResource(R.string.follow_follower_plural),
            style = Typo.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
    }
}

private fun formatFollowerCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}M"
    count >= 10_000 -> "${count / 1_000}K"
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    else -> count.toString()
}

// ── Previews ────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun CommunityIdlePreview() {
    EnteGramTheme {
        CommunityContent(
            uiState = CommunityUiState.Idle,
            query = "",
            onBack = {},
            onQueryChange = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun CommunityLoadingPreview() {
    EnteGramTheme {
        CommunityContent(
            uiState = CommunityUiState.Loading,
            query = "len",
            onBack = {},
            onQueryChange = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun CommunityEmptyPreview() {
    EnteGramTheme {
        CommunityContent(
            uiState = CommunityUiState.Empty,
            query = "zzzzz",
            onBack = {},
            onQueryChange = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun CommunityErrorPreview() {
    EnteGramTheme {
        CommunityContent(
            uiState = CommunityUiState.Error("Could not reach the server."),
            query = "lena",
            onBack = {},
            onQueryChange = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun CommunityReadyPreview() {
    EnteGramTheme {
        CommunityContent(
            uiState = CommunityUiState.Ready(
                results = listOf(
                    CommunityResult(
                        id = "wall-lena",
                        slug = "lena",
                        displayName = "Lena Marchetti",
                        followerCount = 184,
                        relationship = null,
                    ),
                    CommunityResult(
                        id = "wall-sora",
                        slug = "sora-kitchen",
                        displayName = "Sora's Kitchen",
                        followerCount = 92,
                        relationship = null,
                    ),
                    CommunityResult(
                        id = "wall-mapmaker",
                        slug = "mapmaker",
                        displayName = "Henrik",
                        followerCount = 1_203,
                        relationship = null,
                    ),
                    CommunityResult(
                        id = "wall-ivory",
                        slug = "ivory-archive",
                        displayName = "The Ivory Archive",
                        followerCount = 41,
                        relationship = null,
                    ),
                ),
                query = "a",
            ),
            query = "a",
            onBack = {},
            onQueryChange = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun CommunityReadySingleResultPreview() {
    EnteGramTheme {
        CommunityContent(
            uiState = CommunityUiState.Ready(
                results = listOf(
                    CommunityResult(
                        id = "wall-lena",
                        slug = "lena",
                        displayName = "Lena Marchetti",
                        followerCount = 184,
                        relationship = null,
                    ),
                ),
                query = "lena",
            ),
            query = "lena",
            onBack = {},
            onQueryChange = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, heightDp = 700)
@Composable
private fun CommunityRefreshingPreview() {
    EnteGramTheme {
        CommunityContent(
            uiState = CommunityUiState.Ready(
                results = listOf(
                    CommunityResult(
                        id = "wall-lena",
                        slug = "lena",
                        displayName = "Lena Marchetti",
                        followerCount = 184,
                        relationship = null,
                    ),
                    CommunityResult(
                        id = "wall-sora",
                        slug = "sora-kitchen",
                        displayName = "Sora's Kitchen",
                        followerCount = 92,
                        relationship = null,
                    ),
                ),
                query = "le",
                refreshing = true,
            ),
            query = "len",
            onBack = {},
            onQueryChange = {},
            onRetry = {},
        )
    }
}
