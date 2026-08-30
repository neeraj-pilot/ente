package io.ente.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnteModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val palette = LocalEntePalette.current
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        containerColor = palette.background,
        contentColor = palette.text,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = EnteRadius.button, topEnd = EnteRadius.button),
        dragHandle = null,
        content = { content() },
    )
}

@Composable
fun SheetContent(
    title: String?,
    modifier: Modifier = Modifier,
    message: String? = null,
    close: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val palette = LocalEntePalette.current
    Column(modifier.padding(EnteSpacing.xl)) {
        if (title != null || close != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != null) {
                    Text(title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = EnteTypography.heading2)
                } else {
                    Spacer(Modifier.weight(1f))
                }
                close?.let { it() }
            }
            Spacer(Modifier.height(EnteSpacing.lg))
        }
        if (message != null) {
            Text(message, style = EnteTypography.body, color = palette.mutedText)
            Spacer(Modifier.height(EnteSpacing.lg))
        }
        content()
    }
}
