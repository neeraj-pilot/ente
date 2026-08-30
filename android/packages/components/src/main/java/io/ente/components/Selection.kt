package io.ente.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun Checkmark(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalEntePalette.current
    val enabled = onCheckedChange != null
    val fill by animateColorAsState(
        if (checked) if (enabled) palette.primary else palette.fillDarkest else Color.Transparent,
        tween(EnteMotion.quick),
        label = "checkmarkFill",
    )
    val stroke = if (checked) fill else if (enabled) palette.mutedText else palette.faintBorder
    Box(
        modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .border(1.dp, stroke, RoundedCornerShape(4.dp))
            .toggleable(checked, enabled, Role.Checkbox) { onCheckedChange?.invoke(it) },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Canvas(Modifier.size(12.dp)) {
                drawPath(
                    Path().apply {
                        moveTo(size.width * 0.2f, size.height * 0.52f)
                        lineTo(size.width * 0.42f, size.height * 0.74f)
                        lineTo(size.width * 0.82f, size.height * 0.3f)
                    },
                    Color.White,
                    style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
        }
    }
}

@Composable
fun Radio(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalEntePalette.current
    val enabled = onClick != null
    val active by animateColorAsState(if (enabled) palette.primary else palette.fillDarkest, tween(EnteMotion.quick), label = "radioFill")
    Box(
        modifier
            .size(16.dp)
            .border(if (selected) 2.dp else 1.dp, if (selected) active else palette.border, androidx.compose.foundation.shape.CircleShape)
            .then(if (selected) Modifier.drawBehind {
                drawCircle(active, radius = size.minDimension / 2 - 3.dp.toPx())
            } else Modifier)
            .toggleable(selected, enabled, Role.RadioButton) { onClick?.invoke() },
    )
}

@Composable
fun Toggle(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val palette = LocalEntePalette.current
    val enabled = onCheckedChange != null
    val shape = RoundedCornerShape(12.dp)
    val track by animateColorAsState(if (checked) palette.primary else palette.fill, tween(EnteMotion.quick), label = "toggleTrack")
    val thumb by animateColorAsState(if (checked) Color.White else palette.primary, tween(EnteMotion.quick), label = "toggleThumb")
    val thumbOffset by animateDpAsState(if (checked) 19.dp else 3.dp, tween(EnteMotion.quick), label = "toggleOffset")
    Box(
        modifier = modifier
            .size(40.dp, 24.dp)
            .clip(shape)
            .background(track)
            .border(1.dp, if (enabled) palette.primary else palette.faintBorder, shape)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = { onCheckedChange?.invoke(it) }),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .padding(start = thumbOffset)
                .size(18.dp)
                .background(thumb, androidx.compose.foundation.shape.CircleShape),
        )
    }
}

@Composable
fun LabeledControl(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    val palette = LocalEntePalette.current
    val rowModifier = if (onClick == null) modifier else {
        modifier.clickable(enabled = enabled, onClick = onClick)
    }
    Row(
        modifier = rowModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        control()
        Spacer(Modifier.width(EnteSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(label, style = EnteTypography.body, color = if (enabled) palette.text else palette.disabledText)
            subtitle?.let { Text(it, style = EnteTypography.mini, color = palette.mutedText) }
        }
    }
}
