package io.ente.entegram.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object Motion {
    fun <T> snap() = spring<T>(
        stiffness = 500f,
        dampingRatio = 0.8f,
    )

    fun <T> soft() = spring<T>(
        stiffness = 220f,
        dampingRatio = 0.9f,
    )

    fun <T> settle() = spring<T>(
        stiffness = 110f,
        dampingRatio = Spring.DampingRatioNoBouncy,
    )

    fun <T> quickFade() = tween<T>(
        durationMillis = 180,
        easing = FastOutSlowInEasing,
    )

    fun <T> longFade() = tween<T>(
        durationMillis = 420,
        easing = FastOutSlowInEasing,
    )

    const val SHIMMER_DURATION_MS = 1200
}
