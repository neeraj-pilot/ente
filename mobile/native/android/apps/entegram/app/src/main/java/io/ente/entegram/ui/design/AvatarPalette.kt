package io.ente.entegram.ui.design

import androidx.compose.ui.graphics.Color
import kotlin.math.abs

data class GradientPair(val top: Color, val bottom: Color)

object AvatarPalette {
    private val gradients = listOf(
        GradientPair(Color(0xFF7C5CFF), Color(0xFF4A3AE0)),
        GradientPair(Color(0xFF4ADE80), Color(0xFF22C55E)),
        GradientPair(Color(0xFFF59E0B), Color(0xFFD97706)),
        GradientPair(Color(0xFFF87171), Color(0xFFDC2626)),
        GradientPair(Color(0xFF38BDF8), Color(0xFF0284C7)),
        GradientPair(Color(0xFFA78BFA), Color(0xFF7C3AED)),
        GradientPair(Color(0xFFFB923C), Color(0xFFEA580C)),
        GradientPair(Color(0xFF2DD4BF), Color(0xFF0D9488)),
    )

    fun forSlug(slug: String): GradientPair {
        val hash = abs(slug.hashCode())
        return gradients[hash % gradients.size]
    }
}
