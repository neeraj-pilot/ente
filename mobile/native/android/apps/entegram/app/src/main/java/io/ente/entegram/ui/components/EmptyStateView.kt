package io.ente.entegram.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Space
import io.ente.entegram.ui.design.Typo

@Composable
fun EmptyStateView(
    icon: ImageVector,
    headline: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(modifier = Modifier.height(Space.md))
        Text(
            text = headline,
            style = Typo.title,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Space.sm))
        Text(
            text = body,
            style = Typo.body,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            val haptic = LocalHapticFeedback.current
            Spacer(modifier = Modifier.height(Space.lg))
            TextButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onAction()
            }) {
                Text(
                    text = actionLabel,
                    style = Typo.bodyEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun EmptyStateViewPreview() {
    EnteGramTheme {
        EmptyStateView(
            icon = Icons.Outlined.AutoAwesome,
            headline = "Your feed is quiet",
            body = "Find people to follow and their posts will show up here.",
            actionLabel = "Find people",
            onAction = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun EmptyStateNoActionPreview() {
    EnteGramTheme {
        EmptyStateView(
            icon = Icons.Outlined.AutoAwesome,
            headline = "No comments yet",
            body = "Be the first to share a thought.",
        )
    }
}
