package io.ente.entegram.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Space

/**
 * A divider that starts after the avatar column, matching the UserRow layout.
 * Avatar (36.dp) + spacer (8.dp) = 44.dp default start inset.
 */
@Composable
fun DividerInset(
    modifier: Modifier = Modifier,
    startInset: Dp = 44.dp + Space.md,
) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun DividerInsetPreview() {
    EnteGramTheme {
        DividerInset()
    }
}
