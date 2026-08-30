package io.ente.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ActionKind { Primary, Secondary, Neutral, Destructive, DestructiveText, Link }

enum class ActionSize { Regular, Compact }

enum class IconActionKind { Primary, Critical, Unfilled, Secondary, Accent, Circular }

enum class FloatingActionKind { Primary, Secondary, Destructive }

@Composable
fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ActionKind = ActionKind.Primary,
    size: ActionSize = ActionSize.Regular,
    enabled: Boolean = true,
    fullWidth: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    val palette = LocalEntePalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = actionColors(kind, palette, enabled, pressed)
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = tween(EnteMotion.quick),
        label = "actionScale",
    )
    val verticalPadding = if (size == ActionSize.Regular) 14.dp else 12.dp
    val actionModifier = if (fullWidth) modifier.fillMaxWidth() else modifier

    Surface(
        color = colors.background,
        contentColor = colors.foreground,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(EnteRadius.button),
        modifier = actionModifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 24.dp)
                .padding(horizontal = EnteSpacing.xl, vertical = verticalPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) { leading() }
                androidx.compose.foundation.layout.Spacer(Modifier.width(EnteSpacing.sm))
            }
            Text(
                text = label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = if (size == ActionSize.Regular) EnteTypography.bodyBold else EnteTypography.body,
                textDecoration = if (kind == ActionKind.DestructiveText || kind == ActionKind.Link) TextDecoration.Underline else null,
            )
        }
    }
}

@Composable
fun FloatingAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: FloatingActionKind = FloatingActionKind.Primary,
    content: @Composable () -> Unit,
) {
    val palette = LocalEntePalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(EnteMotion.quick),
        label = "floatingActionScale",
    )
    val colors = floatingActionColors(kind, palette, pressed)
    Surface(
        color = colors.background,
        contentColor = colors.foreground,
        shape = androidx.compose.foundation.shape.CircleShape,
        modifier = modifier
            .size(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun IconAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: IconActionKind = IconActionKind.Secondary,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    val palette = LocalEntePalette.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colors = actionColors(kind, palette, enabled, pressed)
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.98f else 1f,
        animationSpec = tween(EnteMotion.quick),
        label = "iconActionScale",
    )

    Surface(
        color = colors.background,
        contentColor = colors.foreground,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(if (kind == IconActionKind.Circular) size / 2 else EnteRadius.medium),
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

private data class ActionColors(val background: Color, val foreground: Color)

private fun actionColors(kind: ActionKind, palette: Palette, enabled: Boolean, pressed: Boolean): ActionColors {
    if (!enabled) {
        return ActionColors(
            if (kind == ActionKind.DestructiveText || kind == ActionKind.Link) Color.Transparent else palette.fill,
            palette.disabledText,
        )
    }
    return when (kind) {
        ActionKind.Primary -> ActionColors(if (pressed) palette.primaryDarker else palette.primary, Color.White)
        ActionKind.Secondary -> ActionColors(if (pressed) palette.fillDarkest else palette.fill, palette.text)
        ActionKind.Neutral -> ActionColors(palette.text, palette.reverseText)
        ActionKind.Destructive -> ActionColors(if (pressed) palette.dangerDarker else palette.danger, Color.White)
        ActionKind.DestructiveText -> ActionColors(Color.Transparent, if (pressed) palette.dangerDarker else palette.danger)
        ActionKind.Link -> ActionColors(Color.Transparent, if (pressed) palette.primaryDarker else palette.primary)
    }
}

private fun actionColors(kind: IconActionKind, palette: Palette, enabled: Boolean, pressed: Boolean): ActionColors {
    if (!enabled) {
        return ActionColors(
            if (kind == IconActionKind.Unfilled || kind == IconActionKind.Secondary) Color.Transparent else palette.fill,
            palette.hintText,
        )
    }
    return when (kind) {
        IconActionKind.Primary, IconActionKind.Circular -> ActionColors(if (pressed) palette.fillDarker else palette.surface, palette.text)
        IconActionKind.Critical -> ActionColors(if (pressed) palette.fillDarkest else palette.fill, palette.text)
        IconActionKind.Unfilled, IconActionKind.Secondary -> ActionColors(
            Color.Transparent,
            if (palette.background.luminance() < 0.5f) palette.text else palette.text.copy(alpha = 0.75f),
        )
        IconActionKind.Accent -> ActionColors(if (pressed) palette.primaryDarker else palette.primary, Color.White)
    }
}

private fun floatingActionColors(kind: FloatingActionKind, palette: Palette, pressed: Boolean): ActionColors = when (kind) {
    FloatingActionKind.Primary -> ActionColors(if (pressed) palette.primaryDark else palette.primary, Color.White)
    FloatingActionKind.Secondary -> ActionColors(if (pressed) palette.fillDarker else palette.fill, palette.primary)
    FloatingActionKind.Destructive -> ActionColors(if (pressed) palette.dangerDark else palette.danger, Color.White)
}
