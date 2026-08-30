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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class EnteApp { Photos, Auth, Locker }

@Immutable
data class Palette(
    val primary: Color,
    val primaryDark: Color,
    val primaryDarker: Color,
    val primarySurface: Color,
    val text: Color,
    val mutedText: Color,
    val hintText: Color,
    val disabledText: Color,
    val reverseText: Color,
    val background: Color,
    val surface: Color,
    val fill: Color,
    val fillDarker: Color,
    val fillDarkest: Color,
    val border: Color,
    val faintBorder: Color,
    val danger: Color,
    val dangerDark: Color,
    val dangerDarker: Color,
    val caution: Color,
    val information: Color,
)

val LocalEntePalette: ProvidableCompositionLocal<Palette> = staticCompositionLocalOf {
    palette(EnteApp.Photos, dark = false)
}

object EnteSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
}

object EnteRadius {
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val button = 20.dp
    val sheet = 24.dp
}

object EnteIconSize {
    val micro = 8.dp
    val tiny = 12.dp
    val small = 18.dp
    val medium = 24.dp
    val large = 36.dp
}

object EnteMotion {
    const val quick = 120
    const val standard = 180
    const val slow = 260
}

object EnteTypography {
    private val inter = FontFamily(
        Font(R.font.inter_regular, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_semibold, FontWeight.SemiBold),
        Font(R.font.inter_bold, FontWeight.Bold),
    )
    private val outfit = FontFamily(Font(R.font.outfit_semibold, FontWeight.SemiBold))

    val display1 = TextStyle(fontFamily = outfit, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, lineHeight = 40.sp)
    val display2 = TextStyle(fontFamily = outfit, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 32.sp)
    val heading1 = TextStyle(fontFamily = inter, fontSize = 20.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp)
    val heading2 = TextStyle(fontFamily = inter, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
    val large = TextStyle(fontFamily = inter, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    val body = TextStyle(fontFamily = inter, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
    val bodyBold = TextStyle(fontFamily = inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    val mini = TextStyle(fontFamily = inter, fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
    val tiny = TextStyle(fontFamily = inter, fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 12.sp)
    val avatarExtraSmall = TextStyle(fontFamily = inter, fontSize = 8.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp)
    val avatarSmall = TextStyle(fontFamily = inter, fontSize = 10.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp)

    val material = Typography(
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
}

@Composable
fun EnteTheme(
    app: EnteApp = EnteApp.Photos,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = palette(app, darkTheme)
    androidx.compose.runtime.CompositionLocalProvider(LocalEntePalette provides palette) {
        MaterialTheme(
            colorScheme = palette.colorScheme(darkTheme),
            typography = EnteTypography.material,
            content = content,
        )
    }
}

private fun Palette.colorScheme(dark: Boolean): ColorScheme = if (dark) {
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

private fun palette(app: EnteApp, dark: Boolean): Palette {
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

    return Palette(
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
