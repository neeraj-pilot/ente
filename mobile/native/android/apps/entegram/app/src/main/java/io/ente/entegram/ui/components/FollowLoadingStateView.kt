package io.ente.entegram.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Radius
import io.ente.entegram.ui.design.Space

/**
 * Shimmer loading state shaped like follow request rows:
 * avatar circle + two text lines + trailing action button.
 */
@Composable
fun FollowLoadingStateView(
    modifier: Modifier = Modifier,
    rowCount: Int = 4,
) {
    // Tab bar shimmer
    Column(modifier = modifier) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            cornerRadius = Radius.md,
        )
        Spacer(modifier = Modifier.height(Space.lg))

        repeat(rowCount) { index ->
            FollowSkeletonRow()
            if (index < rowCount - 1) {
                Spacer(modifier = Modifier.height(Space.md))
            }
        }
    }
}

@Composable
private fun FollowSkeletonRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Avatar
        ShimmerBox(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            cornerRadius = 18.dp,
        )
        Spacer(modifier = Modifier.width(Space.sm))

        // Name + subtitle
        Column(modifier = Modifier.weight(1f)) {
            ShimmerBox(
                modifier = Modifier
                    .width(100.dp)
                    .height(12.dp),
            )
            Spacer(modifier = Modifier.height(Space.xs))
            ShimmerBox(
                modifier = Modifier
                    .width(60.dp)
                    .height(10.dp),
            )
        }

        // Action button placeholder
        ShimmerBox(
            modifier = Modifier
                .width(80.dp)
                .height(32.dp),
            cornerRadius = Radius.pill,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FollowLoadingStateViewPreview() {
    EnteGramTheme {
        FollowLoadingStateView(
            modifier = Modifier.padding(Space.md),
        )
    }
}
