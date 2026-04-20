package io.ente.entegram.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Dark palette ──────────────────────────────────────────────
private val DarkSurfaceRoot = Color(0xFF000000)
private val DarkSurface = Color(0xFF0E0E10)
private val DarkSurfaceVariant = Color(0xFF17171B)
private val DarkSurfaceElevated = Color(0xFF1F1F26)

private val DarkPrimary = Color(0xFF7C5CFF)
private val DarkSecondary = Color(0xFF4ADE80)
private val DarkError = Color(0xFFF87171)

private val DarkOnSurface = Color(0xFFF5F5F7)
private val DarkOnSurfaceVariant = Color(0xFFA1A1AA)

// ── Light palette ─────────────────────────────────────────────
private val LightSurfaceRoot = Color(0xFFFAFAFC)
private val LightSurface = Color(0xFFF4F4F6)
private val LightSurfaceVariant = Color(0xFFEBEBF0)
private val LightSurfaceElevated = Color(0xFFFFFFFF)

private val LightPrimary = Color(0xFF6344E0)   // slightly deeper violet for contrast on white
private val LightSecondary = Color(0xFF2D9D5A) // darker mint for readability
private val LightError = Color(0xFFDC4444)     // deeper salmon for readability

private val LightOnSurface = Color(0xFF1A1A1F)
private val LightOnSurfaceVariant = Color(0xFF6B6B78)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkSurfaceRoot,
    primaryContainer = DarkPrimary.copy(alpha = 0.18f),
    onPrimaryContainer = DarkPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkSurfaceRoot,
    secondaryContainer = DarkSecondary.copy(alpha = 0.18f),
    onSecondaryContainer = DarkSecondary,
    error = DarkError,
    onError = DarkSurfaceRoot,
    errorContainer = DarkError.copy(alpha = 0.18f),
    onErrorContainer = DarkError,
    background = DarkSurfaceRoot,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOnSurfaceVariant.copy(alpha = 0.35f),
    outlineVariant = DarkOnSurfaceVariant.copy(alpha = 0.18f),
    surfaceContainerLowest = DarkSurfaceRoot,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceVariant,
    surfaceContainerHigh = DarkSurfaceElevated,
    surfaceContainerHighest = DarkSurfaceElevated,
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimary.copy(alpha = 0.12f),
    onPrimaryContainer = LightPrimary,
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondary.copy(alpha = 0.12f),
    onSecondaryContainer = LightSecondary,
    error = LightError,
    onError = Color.White,
    errorContainer = LightError.copy(alpha = 0.10f),
    onErrorContainer = LightError,
    background = LightSurfaceRoot,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOnSurfaceVariant.copy(alpha = 0.40f),
    outlineVariant = LightOnSurfaceVariant.copy(alpha = 0.20f),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = LightSurfaceRoot,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    surfaceContainerHighest = LightSurfaceElevated,
)

@Composable
fun EnteGramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = EnteGramTypography,
        content = content,
    )
}
