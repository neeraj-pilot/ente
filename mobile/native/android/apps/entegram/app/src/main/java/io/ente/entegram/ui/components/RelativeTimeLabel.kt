package io.ente.entegram.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.Typo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun RelativeTimeLabel(
    instant: Instant,
    modifier: Modifier = Modifier,
    style: TextStyle = Typo.caption,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val text = remember(instant) { formatRelativeTime(instant) }
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier,
    )
}

private val shortDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d").withZone(ZoneId.systemDefault())

private fun formatRelativeTime(instant: Instant): String {
    val now = Instant.now()
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    val hours = ChronoUnit.HOURS.between(instant, now)
    val days = ChronoUnit.DAYS.between(instant, now)

    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        days < 7 -> "${days}d"
        else -> shortDateFormatter.format(instant)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun RelativeTimeLabelPreview() {
    EnteGramTheme {
        RelativeTimeLabel(instant = Instant.now().minus(3, ChronoUnit.HOURS))
    }
}
