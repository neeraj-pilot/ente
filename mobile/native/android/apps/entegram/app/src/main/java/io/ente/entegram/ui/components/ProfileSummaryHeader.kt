package io.ente.entegram.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.ente.entegram.core.models.Wall
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

@Composable
fun ProfileSummaryHeader(
    wall: Wall,
    modifier: Modifier = Modifier,
    avatarSize: androidx.compose.ui.unit.Dp = 72.dp,
    stats: List<ProfileStat> = defaultProfileStats(wall),
    centered: Boolean = true,
    actions: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val alignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
        horizontalAlignment = alignment,
    ) {
        AvatarView(
            slug = wall.slug,
            displayName = wall.displayName,
            avatarObjectKey = wall.avatarObjectKey,
            size = avatarSize,
        )

        Spacer(modifier = Modifier.height(Space.md))

        if (!wall.displayName.isNullOrBlank()) {
            Text(
                text = wall.displayName,
                style = Typo.title,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(Space.xxs))
        }

        Text(
            text = "@${wall.slug}",
            style = Typo.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (!wall.bio.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Space.sm))
            Text(
                text = wall.bio,
                style = Typo.body,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = if (centered) Modifier.padding(horizontal = Space.lg) else Modifier,
            )
        }

        Spacer(modifier = Modifier.height(Space.lg))

        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.xxl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stats.forEach { stat ->
                ProfileStatColumn(
                    value = stat.value,
                    label = stat.label,
                    onClick = stat.onClick,
                )
            }
        }

        if (actions != null) {
            Spacer(modifier = Modifier.height(Space.lg))
            actions()
        }
    }
}

@Stable
data class ProfileStat(
    val value: Int,
    val label: String,
    val onClick: (() -> Unit)? = null,
)

fun defaultProfileStats(wall: Wall): List<ProfileStat> = listOf(
    ProfileStat(value = wall.postCount, label = "Posts"),
    ProfileStat(value = wall.followerCount, label = "Followers"),
    ProfileStat(value = wall.followingCount, label = "Following"),
)

@Composable
fun ProfileStatColumn(
    value: Int,
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = formatProfileCount(value),
            style = Typo.title,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(Space.xxs))
        Text(
            text = label,
            style = Typo.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun formatProfileCount(count: Int): String = when {
    count >= 1_000_000 -> "${count / 1_000_000}.${(count % 1_000_000) / 100_000}M"
    count >= 10_000 -> "${count / 1_000}K"
    count >= 1_000 -> "${count / 1_000}.${(count % 1_000) / 100}K"
    else -> count.toString()
}
