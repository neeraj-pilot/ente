package io.ente.entegram.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Typo

/**
 * Animated like count that rolls digits vertically when the value changes.
 * New value slides in from the top when incrementing, from the bottom when
 * decrementing.
 */
@Composable
fun LikeCountText(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = Typo.caption,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    AnimatedContent(
        targetState = count,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState > initialState) -1 else 1
            (slideInVertically { height -> direction * height } + fadeIn())
                .togetherWith(slideOutVertically { height -> -direction * height } + fadeOut())
                .using(SizeTransform(clip = false))
        },
        label = "likeCount",
    ) { targetCount ->
        Text(
            text = "$targetCount",
            style = style,
            color = color,
        )
    }
}

// ── Previews ──────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LikeCountTextPreview() {
    EnteGramTheme {
        LikeCountText(count = 42)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LikeCountTextZeroPreview() {
    EnteGramTheme {
        LikeCountText(count = 0)
    }
}
