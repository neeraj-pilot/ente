package io.ente.entegram.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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

@Composable
fun LoadingStateView(
    modifier: Modifier = Modifier,
    cardCount: Int = 3,
) {
    Column(modifier = modifier.padding(horizontal = Space.md)) {
        repeat(cardCount) { index ->
            SkeletonCard()
            if (index < cardCount - 1) {
                Spacer(modifier = Modifier.height(Space.lg))
            }
        }
    }
}

@Composable
private fun SkeletonCard() {
    Column {
        // Header: avatar circle + two text lines
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = Space.sm),
        ) {
            ShimmerBox(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape),
                cornerRadius = 18.dp,
            )
            Spacer(modifier = Modifier.width(Space.sm))
            Column {
                ShimmerBox(
                    modifier = Modifier
                        .width(120.dp)
                        .height(12.dp),
                )
                Spacer(modifier = Modifier.height(Space.xs))
                ShimmerBox(
                    modifier = Modifier
                        .width(60.dp)
                        .height(10.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(Space.xs))

        // Image placeholder — 4:3 aspect
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
            cornerRadius = Radius.lg,
        )
        Spacer(modifier = Modifier.height(Space.sm))

        // Caption placeholder
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(14.dp),
        )
        Spacer(modifier = Modifier.height(Space.xs))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(14.dp),
        )
        Spacer(modifier = Modifier.height(Space.md))

        // Footer row placeholder
        Row {
            ShimmerBox(
                modifier = Modifier
                    .width(48.dp)
                    .height(12.dp),
            )
            Spacer(modifier = Modifier.width(Space.md))
            ShimmerBox(
                modifier = Modifier
                    .width(48.dp)
                    .height(12.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun LoadingStateViewPreview() {
    EnteGramTheme {
        Box(modifier = Modifier.padding(top = Space.md)) {
            LoadingStateView()
        }
    }
}
