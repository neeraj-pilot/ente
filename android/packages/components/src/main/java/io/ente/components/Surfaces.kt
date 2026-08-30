package io.ente.components

import java.util.Locale
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class BannerKind { Failure, Information, Success, Warning, Neutral }

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette = LocalEntePalette.current
    val background = if (selected) {
        if (palette.background.luminance() < 0.5f) Color(0xFFF4F4F4) else Color(0xFF161616)
    } else {
        palette.surface
    }
    val foreground = when {
        selected -> if (palette.background.luminance() < 0.5f) Color.Black else Color.White
        enabled -> palette.mutedText
        else -> palette.disabledText
    }
    val hasAutoTrailing = selected && trailing == null
    val startPadding = when {
        leading != null && trailing == null -> EnteSpacing.md
        trailing != null -> EnteSpacing.lg
        else -> 18.dp
    }
    val endPadding = when {
        leading != null && trailing == null -> if (selected) EnteSpacing.md else EnteSpacing.lg
        trailing != null || selected -> EnteSpacing.md
        else -> 18.dp
    }

    Surface(
        color = background,
        contentColor = foreground,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .heightIn(min = 40.dp)
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox) { onClick() },
    ) {
        Row(
            modifier = Modifier.padding(start = startPadding, end = endPadding, top = EnteSpacing.md, bottom = EnteSpacing.md),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { it() }
                Spacer(Modifier.width(EnteSpacing.sm))
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = EnteTypography.mini)
            if (trailing != null || hasAutoTrailing) {
                Spacer(Modifier.width(EnteSpacing.sm))
                if (trailing != null) {
                    Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) { trailing() }
                } else {
                    RemoveIcon(foreground)
                }
            }
        }
    }
}

@Composable
fun Tag(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette = LocalEntePalette.current
    Surface(
        color = if (selected) palette.primary else palette.surface,
        contentColor = if (selected) Color.White else if (enabled) palette.mutedText else palette.disabledText,
        shape = RoundedCornerShape(EnteRadius.large),
        modifier = modifier
            .heightIn(min = 44.dp)
            .semantics { this.selected = selected }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (leading != null) EnteSpacing.md else EnteSpacing.xl,
                end = if (trailing != null) EnteSpacing.md else EnteSpacing.xl,
                top = EnteSpacing.md,
                bottom = EnteSpacing.md,
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let {
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { it() }
                Spacer(Modifier.width(EnteSpacing.xs))
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = EnteTypography.body)
            trailing?.let {
                Spacer(Modifier.width(EnteSpacing.xs))
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { it() }
            }
        }
    }
}

@Composable
fun MenuRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette = LocalEntePalette.current
    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(EnteRadius.button),
        border = if (selected) androidx.compose.foundation.BorderStroke(1.dp, palette.border) else null,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (leading == null) EnteSpacing.lg else EnteSpacing.md,
                end = EnteSpacing.md,
                top = 9.dp,
                bottom = 9.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { it() }
                Spacer(Modifier.width(EnteSpacing.md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    maxLines = if (subtitle == null) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                    style = EnteTypography.body,
                    color = if (enabled) palette.text else palette.disabledText,
                )
                subtitle?.let {
                    Spacer(Modifier.size(EnteSpacing.xs))
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = EnteTypography.mini, color = palette.mutedText)
                }
            }
            trailing?.let {
                Spacer(Modifier.width(EnteSpacing.md))
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { it() }
            }
        }
    }
}

@Composable
fun MenuGroup(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        color = LocalEntePalette.current.surface,
        shape = RoundedCornerShape(EnteRadius.button),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = { content() })
    }
}

@Composable
fun Banner(
    title: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    kind: BannerKind = BannerKind.Neutral,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette = LocalEntePalette.current
    val accent = when (kind) {
        BannerKind.Failure -> palette.danger
        BannerKind.Information -> palette.information
        BannerKind.Success, BannerKind.Neutral -> palette.primaryDark
        BannerKind.Warning -> palette.caution
    }
    val titleColor = if (kind == BannerKind.Neutral) palette.text else accent
    val interaction = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)

    Surface(
        color = palette.surface,
        shape = RoundedCornerShape(EnteRadius.button),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 66.dp)
            .then(interaction),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = EnteSpacing.lg, vertical = EnteSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.let {
                Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { it() }
                Spacer(Modifier.width(EnteSpacing.lg))
            }
            Column(Modifier.weight(1f)) {
                Text(title, maxLines = if (subtitle == null) 2 else 1, overflow = TextOverflow.Ellipsis, style = EnteTypography.bodyBold, color = titleColor)
                subtitle?.let {
                    Spacer(Modifier.size(EnteSpacing.xs))
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = EnteTypography.mini, color = palette.mutedText)
                }
            }
            trailing?.let {
                Spacer(Modifier.width(EnteSpacing.md))
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) { it() }
            }
        }
    }
}

@Composable
fun SettingsRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val palette = LocalEntePalette.current
    val textColor = if (destructive) palette.danger else palette.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = EnteSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { it() }
            Spacer(Modifier.width(EnteSpacing.lg))
        }
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = EnteTypography.body, color = textColor)
            subtitle?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = EnteTypography.mini, color = palette.mutedText) }
        }
        trailing?.let {
            Spacer(Modifier.width(EnteSpacing.md))
            Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { it() }
        }
    }
}

@Composable
fun EnteDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = LocalEntePalette.current.faintBorder)
}

@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    identity: String = name,
    size: AvatarSize = AvatarSize.Regular,
) {
    val palette = LocalEntePalette.current
    val color = avatarColors(palette)[avatarIndex(identity)]
    Box(
        modifier = modifier
            .size(size.dimension)
            .clip(CircleShape)
            .background(color)
            .border(size.border, palette.background, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(avatarInitials(name), style = size.textStyle, color = Color.White)
    }
}

enum class AvatarSize(val dimension: androidx.compose.ui.unit.Dp, val border: androidx.compose.ui.unit.Dp, val textStyle: androidx.compose.ui.text.TextStyle) {
    ExtraSmall(16.dp, 1.dp, EnteTypography.avatarExtraSmall),
    Small(20.dp, 1.dp, EnteTypography.avatarSmall),
    Regular(24.dp, 1.dp, EnteTypography.mini),
    Medium(28.dp, 1.dp, EnteTypography.mini),
    Large(32.dp, 2.dp, EnteTypography.mini),
    Contact(56.dp, 2.dp, EnteTypography.heading2),
}

private fun avatarColors(palette: Palette) = listOf(
    palette.caution, palette.primary, Color(0xFFF24822), Color(0xFFDF61BB),
    Color(0xFF9610D6), Color(0xFF1071FF), Color(0xFF00B8D4),
)

@Composable
private fun RemoveIcon(color: Color) {
    Canvas(Modifier.size(14.dp)) {
        val inset = size.width * 0.24f
        drawLine(color, Offset(inset, inset), Offset(size.width - inset, size.height - inset), 1.5.dp.toPx(), StrokeCap.Round)
        drawLine(color, Offset(size.width - inset, inset), Offset(inset, size.height - inset), 1.5.dp.toPx(), StrokeCap.Round)
    }
}

private fun avatarIndex(identity: String): Int {
    var hash = 0x811c9dc5L
    identity.trim().lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8).forEach { byte ->
        hash = (hash xor (byte.toLong() and 0xff)) * 0x01000193L and 0xffffffffL
    }
    return (hash % 7).toInt()
}

private fun avatarInitials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
    if (words.isEmpty()) return "?"
    return (words.first().first().uppercase() + words.drop(1).lastOrNull()?.first()?.uppercase().orEmpty())
}
