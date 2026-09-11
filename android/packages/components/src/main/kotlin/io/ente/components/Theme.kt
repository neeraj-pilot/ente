package io.ente.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

public enum class EnteApp { Photos, Auth, Locker }

@Immutable
public class EntePalette internal constructor(
    public val isDark: Boolean,
    public val primary: Color,
    public val primaryDark: Color,
    public val primaryDarker: Color,
    public val primarySurface: Color,
    public val text: Color,
    public val mutedText: Color,
    public val hintText: Color,
    public val disabledText: Color,
    public val reverseText: Color,
    public val background: Color,
    public val surface: Color,
    public val fill: Color,
    public val fillDarker: Color,
    public val fillDarkest: Color,
    public val border: Color,
    public val faintBorder: Color,
    public val danger: Color,
    public val dangerDark: Color,
    public val dangerDarker: Color,
    public val caution: Color,
    public val information: Color,
)

public val LocalEntePalette: ProvidableCompositionLocal<EntePalette> = staticCompositionLocalOf {
    palette(EnteApp.Photos, dark = false)
}

public object EnteSpacing {
    public val xs: Dp = 4.dp
    public val sm: Dp = 8.dp
    public val md: Dp = 12.dp
    public val lg: Dp = 16.dp
    public val xl: Dp = 20.dp
    public val xxl: Dp = 24.dp
}

public object EnteRadius {
    public val small: Dp = 8.dp
    public val medium: Dp = 12.dp
    public val large: Dp = 16.dp
    public val button: Dp = 20.dp
    public val sheet: Dp = 24.dp
}

public object EnteIconSize {
    public val micro: Dp = 8.dp
    public val tiny: Dp = 12.dp
    public val small: Dp = 18.dp
    public val medium: Dp = 24.dp
    public val large: Dp = 36.dp
}

public object EnteMotion {
    public const val quick: Int = 120
    public const val standard: Int = 180
    public const val slow: Int = 260
}

public object EnteTypography {
    private val inter: FontFamily = FontFamily(
        Font(R.font.ente_inter_regular, FontWeight.Normal),
        Font(R.font.ente_inter_medium, FontWeight.Medium),
        Font(R.font.ente_inter_semibold, FontWeight.SemiBold),
        Font(R.font.ente_inter_bold, FontWeight.Bold),
    )
    private val outfit: FontFamily = FontFamily(
        Font(R.font.ente_outfit_semibold, FontWeight.SemiBold),
    )

    public val display1: TextStyle = style(outfit, 32.sp, FontWeight.SemiBold, 40.sp)
    public val display2: TextStyle = style(outfit, 24.sp, FontWeight.SemiBold, 32.sp)
    public val heading1: TextStyle = style(inter, 20.sp, FontWeight.Bold, 28.sp)
    public val heading2: TextStyle = style(inter, 18.sp, FontWeight.SemiBold, 24.sp)
    public val large: TextStyle = style(inter, 16.sp, FontWeight.SemiBold, 20.sp)
    public val body: TextStyle = style(inter, 14.sp, FontWeight.Medium, 20.sp)
    public val bodyBold: TextStyle = style(inter, 14.sp, FontWeight.SemiBold, 20.sp)
    public val mini: TextStyle = style(inter, 12.sp, FontWeight.Medium, 16.sp)
    public val tiny: TextStyle = style(inter, 10.sp, FontWeight.Medium, 12.sp)
    public val avatarExtraSmall: TextStyle = style(inter, 8.sp, FontWeight.Medium, 15.sp)
    public val avatarSmall: TextStyle = style(inter, 10.sp, FontWeight.Medium, 15.sp)

    internal val material: Typography = Typography(
        headlineLarge = display1,
        headlineMedium = display2,
        titleLarge = heading1,
        titleMedium = heading2,
        bodyLarge = body,
        bodyMedium = body,
        labelLarge = bodyBold,
        labelMedium = mini,
        labelSmall = tiny,
    )

    private fun style(
        fontFamily: FontFamily,
        fontSize: TextUnit,
        fontWeight: FontWeight,
        lineHeight: TextUnit,
    ): TextStyle = TextStyle(
        fontFamily = fontFamily,
        fontSize = fontSize,
        fontWeight = fontWeight,
        lineHeight = lineHeight,
    )
}

@Composable
public fun EnteTheme(
    app: EnteApp = EnteApp.Photos,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = palette(app, darkTheme)
    androidx.compose.runtime.CompositionLocalProvider(LocalEntePalette provides palette) {
        MaterialTheme(
            colorScheme = palette.colorScheme(),
            typography = EnteTypography.material,
            content = content,
        )
    }
}

private fun EntePalette.colorScheme(): ColorScheme = if (isDark) {
    darkColorScheme(
        primary = primary,
        onPrimary = reverseText,
        secondary = primarySurface,
        onSecondary = primary,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = fill,
        onSurfaceVariant = mutedText,
        outline = border,
        error = danger,
    )
} else {
    lightColorScheme(
        primary = primary,
        onPrimary = reverseText,
        secondary = primarySurface,
        onSecondary = primary,
        background = background,
        onBackground = text,
        surface = surface,
        onSurface = text,
        surfaceVariant = fill,
        onSurfaceVariant = mutedText,
        outline = border,
        error = danger,
    )
}

private fun palette(app: EnteApp, dark: Boolean): EntePalette {
    val primary = when (app) {
        EnteApp.Photos -> Color(0xFF08C225)
        EnteApp.Auth -> Color(0xFF9610D6)
        EnteApp.Locker -> Color(0xFF1071FF)
    }
    val primaryDark = when (app) {
        EnteApp.Photos -> Color(0xFF069D1E)
        EnteApp.Auth -> Color(0xFF7A0CAE)
        EnteApp.Locker -> Color(0xFF0E5FD9)
    }
    val primaryDarker = when (app) {
        EnteApp.Photos -> Color(0xFF057C18)
        EnteApp.Auth -> Color(0xFF5D0884)
        EnteApp.Locker -> Color(0xFF0B4CAD)
    }
    val primarySurface = when (app) {
        EnteApp.Photos -> if (dark) Color(0xFF292929) else Color(0xFFDDEEDF)
        EnteApp.Auth -> if (dark) Color(0xFF271C32) else Color(0xFFF4E7FC)
        EnteApp.Locker -> if (dark) Color(0xFF292929) else Color(0xFFE7EFFA)
    }

    return EntePalette(
        isDark = dark,
        primary = primary,
        primaryDark = primaryDark,
        primaryDarker = primaryDarker,
        primarySurface = primarySurface,
        text = if (dark) Color.White else Color.Black,
        mutedText = Color(if (dark) 0xFF999999 else 0xFF666666),
        hintText = Color(0xFF969696),
        disabledText = Color(if (dark) 0xFF414141 else 0xFFD6D6D6),
        reverseText = if (dark) Color.Black else Color.White,
        background = Color(if (dark) 0xFF161616 else 0xFFF4F4F4),
        surface = if (dark) Color(0xFF212121) else Color.White,
        fill = Color(if (dark) 0xFF0A0A0A else 0xFFEAEAEA),
        fillDarker = Color(if (dark) 0xFF141414 else 0xFFDEDEDE),
        fillDarkest = Color(if (dark) 0xFF292929 else 0xFFD2D2D2),
        border = Color(if (dark) 0xFF3E3E3E else 0xFFE0E0E0),
        faintBorder = Color(if (dark) 0xFF2A2A2A else 0xFFEBEBEB),
        danger = Color(0xFFF63A3A),
        dangerDark = Color(0xFFDD3434),
        dangerDarker = Color(0xFFC52E2E),
        caution = Color(0xFFF08A1E),
        information = Color(0xFF1071FF),
    )
}
