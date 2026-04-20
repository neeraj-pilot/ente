package io.ente.entegram.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo
import java.time.Instant
import java.time.temporal.ChronoUnit

@Composable
fun UserRow(
    slug: String,
    displayName: String?,
    modifier: Modifier = Modifier,
    subtitle: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AvatarView(slug = slug, displayName = displayName)
        Spacer(modifier = Modifier.width(Space.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName ?: "@$slug",
                style = Typo.bodyEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            if (subtitle != null) {
                subtitle()
            } else {
                Text(
                    text = "@$slug",
                    style = Typo.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(Space.sm))
            trailing()
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun UserRowPreview() {
    EnteGramTheme {
        UserRow(
            slug = "lena",
            displayName = "Lena Marchetti",
            trailing = {
                RelativeTimeLabel(instant = Instant.now().minus(3, ChronoUnit.HOURS))
            },
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun UserRowNoDisplayNamePreview() {
    EnteGramTheme {
        UserRow(
            slug = "mapmaker",
            displayName = null,
        )
    }
}
