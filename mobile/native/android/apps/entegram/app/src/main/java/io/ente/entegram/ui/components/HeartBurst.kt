package io.ente.entegram.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.ente.entegram.app.EnteGramTheme
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Floating-hearts particle burst, triggered each time [trigger] increments.
 *
 * Place this as an overlay in a Box that covers the card area. Hearts emit
 * from [emissionOrigin] (local coords) and float upward with gentle sway,
 * fading to zero over ~1.1s.
 *
 * Port of iOS `HeartBurstView.swift` — same particle math, same tuning.
 */
@Composable
fun HeartBurst(
    trigger: Int,
    emissionOrigin: Offset,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.error,
    durationMs: Int = 1100,
    count: Int = 3,
) {
    val bursts = remember { mutableStateListOf<Burst>() }
    val currentTint by rememberUpdatedState(tint)
    val density = LocalDensity.current

    // Spawn a new burst each time trigger increments
    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        val particles = List(count) { Particle.random() }
        val progress = Animatable(0f)
        val burst = Burst(particles = particles, progress = progress)
        bursts.add(burst)

        // Drive the animation
        launch {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = durationMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            // Reap after animation completes
            bursts.remove(burst)
        }
    }

    if (bursts.isEmpty()) return

    Canvas(modifier = modifier.fillMaxSize()) {
        for (burst in bursts) {
            val tGlobal = burst.progress.value
            for (particle in burst.particles) {
                drawParticle(
                    particle = particle,
                    tGlobal = tGlobal,
                    origin = emissionOrigin,
                    tint = currentTint,
                    density = density.density,
                )
            }
        }
    }
}

private fun DrawScope.drawParticle(
    particle: Particle,
    tGlobal: Float,
    origin: Offset,
    tint: Color,
    density: Float,
) {
    // Per-particle delay offset
    val local = ((tGlobal - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
    if (local <= 0f) return

    // Rise: ease-out (fast at first, slow at top)
    val rise = 1f - (1f - local).pow(2.3f)

    // Horizontal sway: sine wave for gentle S-curve
    val sway = sin(particle.swayPhase + local * Math.PI.toFloat() * 1.6f)

    val dx = (particle.horizontalBase + sway * particle.swayAmplitude) * density
    val dy = -rise * particle.travel * density

    // Scale: pop in to 1.05, settle to 1.0
    val scaleValue = when {
        local < 0.18f -> {
            val p = local / 0.18f
            1.05f * (1f - (1f - p).pow(2f))
        }
        local < 0.28f -> {
            val p = (local - 0.18f) / 0.10f
            1.05f - 0.05f * p
        }
        else -> 1.0f
    }

    // Opacity: fade in 0–0.1, hold 0.1–0.7, fade out 0.7–1.0
    val opacity = when {
        local < 0.1f -> local / 0.1f
        local > 0.7f -> 1f - (local - 0.7f) / 0.3f
        else -> 1.0f
    }

    // Lazy rotation: max ~12 degrees
    val rotation = particle.rotationDirection * local * 12f

    // Hue jitter applied to tint
    val heartColor = tint.hueShift(particle.hueJitter).copy(alpha = opacity)

    val heartSize = 10f * particle.size * scaleValue * density

    translate(left = origin.x + dx, top = origin.y + dy) {
        rotate(degrees = rotation, pivot = Offset.Zero) {
            scale(scale = 1f, pivot = Offset.Zero) {
                drawHeart(heartColor, heartSize)
            }
        }
    }
}

/**
 * Draw a heart shape centered at the current origin.
 */
private fun DrawScope.drawHeart(color: Color, size: Float) {
    val path = Path().apply {
        // Heart drawn in a coordinate system where size is the total width
        val w = size
        val h = size * 0.9f

        // Start at the bottom point
        moveTo(0f, h * 0.35f)

        // Left curve
        cubicTo(
            x1 = -w * 0.02f, y1 = h * 0.1f,
            x2 = -w * 0.45f, y2 = -h * 0.1f,
            x3 = -w * 0.25f, y3 = -h * 0.4f,
        )
        // Left top
        cubicTo(
            x1 = -w * 0.1f, y1 = -h * 0.6f,
            x2 = w * 0.1f, y2 = -h * 0.6f,
            x3 = 0f, y3 = -h * 0.35f,
        )
        // Right top (mirror)
        cubicTo(
            x1 = -w * 0.1f + w * 0.2f, y1 = -h * 0.6f,
            x2 = w * 0.1f + w * 0.2f, y2 = -h * 0.6f,
            x3 = w * 0.25f, y3 = -h * 0.4f,
        )
        // Right curve
        cubicTo(
            x1 = w * 0.45f, y1 = -h * 0.1f,
            x2 = w * 0.02f, y2 = h * 0.1f,
            x3 = 0f, y3 = h * 0.35f,
        )
        close()
    }
    drawPath(path, color = color)
}

// ── Data classes ───────────────────────────────────────────────

private class Burst(
    val particles: List<Particle>,
    val progress: Animatable<Float, *>,
)

private data class Particle(
    val horizontalBase: Float,       // initial x offset in dp
    val travel: Float,               // total upward distance in dp
    val size: Float,                 // scale multiplier on 10dp symbol
    val delay: Float,                // fraction of duration before starting
    val swayPhase: Float,            // radians
    val swayAmplitude: Float,        // dp
    val rotationDirection: Float,    // -1 or 1
    val hueJitter: Float,            // fraction in 0..1 hue space
) {
    companion object {
        fun random(rng: Random = Random): Particle = Particle(
            horizontalBase = rng.nextFloat() * 12f - 6f,       // -6..6
            travel = rng.nextFloat() * 20f + 38f,              // 38..58
            size = rng.nextFloat() * 0.45f + 0.85f,            // 0.85..1.3
            delay = rng.nextFloat() * 0.08f,                   // 0..0.08
            swayPhase = rng.nextFloat() * 2f * Math.PI.toFloat(),
            swayAmplitude = rng.nextFloat() * 12f + 14f,       // 14..26
            rotationDirection = if (rng.nextBoolean()) -1f else 1f,
            hueJitter = (rng.nextFloat() * 8f - 4f) / 360f,   // ±4 degrees
        )
    }
}

// ── Color helper ──────────────────────────────────────────────

/**
 * Shift this color's hue by [delta] (in 0..1 hue space).
 * Uses Android's Color conversion through HSV.
 */
private fun Color.hueShift(delta: Float): Color {
    val argb = android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    hsv[0] = (hsv[0] + delta * 360f).mod(360f)
    val shifted = android.graphics.Color.HSVToColor((alpha * 255).toInt(), hsv)
    return Color(shifted)
}

// ── Previews ──────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun HeartBurstPreview() {
    EnteGramTheme {
        Box(modifier = Modifier.size(200.dp)) {
            HeartBurst(
                trigger = 1,
                emissionOrigin = with(LocalDensity.current) {
                    Offset(100.dp.toPx(), 150.dp.toPx())
                },
            )
        }
    }
}
