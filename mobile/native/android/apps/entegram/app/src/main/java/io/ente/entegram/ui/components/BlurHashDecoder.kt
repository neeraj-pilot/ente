package io.ente.entegram.ui.components

import android.graphics.Bitmap
import android.util.LruCache
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow

private const val BASE83_ALPHABET =
    "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#$%*+,-.:;=?@[]^_{|}~"
private const val DEFAULT_PUNCH = 1f
private val blurHashCache = LruCache<String, Bitmap>(64)

fun decodeBlurHashBitmap(
    blurHash: String?,
    width: Int = 32,
    height: Int = 32,
): Bitmap? {
    val hash = blurHash?.trim()?.takeIf { it.length >= 6 } ?: return null
    val cacheKey = "$hash:$width:$height"
    blurHashCache.get(cacheKey)?.let { return it }

    return runCatching {
        decodeBlurHash(hash, width, height).also { blurHashCache.put(cacheKey, it) }
    }.getOrNull()
}

private fun decodeBlurHash(hash: String, width: Int, height: Int): Bitmap {
    val sizeFlag = decode83(hash, 0, 1)
    val numY = sizeFlag / 9 + 1
    val numX = sizeFlag % 9 + 1
    val expectedLength = 4 + 2 * numX * numY
    require(hash.length >= expectedLength) { "BlurHash is too short" }

    val quantizedMaximumValue = decode83(hash, 1, 2)
    val maximumValue = (quantizedMaximumValue + 1) / 166f
    val colors = Array(numX * numY) { FloatArray(3) }
    colors[0] = decodeDc(decode83(hash, 2, 6))

    for (i in 1 until colors.size) {
        colors[i] = decodeAc(
            value = decode83(hash, 4 + i * 2, 6 + i * 2),
            maximumValue = maximumValue * DEFAULT_PUNCH,
        )
    }

    val pixels = IntArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            var r = 0f
            var g = 0f
            var b = 0f

            for (j in 0 until numY) {
                for (i in 0 until numX) {
                    val basis = (cos(PI * x * i / width) * cos(PI * y * j / height)).toFloat()
                    val color = colors[i + j * numX]
                    r += color[0] * basis
                    g += color[1] * basis
                    b += color[2] * basis
                }
            }

            pixels[x + y * width] = 0xFF000000.toInt() or
                (linearToSrgb(r).shl(16)) or
                (linearToSrgb(g).shl(8)) or
                linearToSrgb(b)
        }
    }

    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

private fun decode83(value: String, start: Int, end: Int): Int {
    var result = 0
    for (i in start until end) {
        val digit = BASE83_ALPHABET.indexOf(value[i])
        require(digit >= 0) { "Invalid BlurHash character" }
        result = result * 83 + digit
    }
    return result
}

private fun decodeDc(value: Int): FloatArray {
    val r = value shr 16
    val g = (value shr 8) and 255
    val b = value and 255
    return floatArrayOf(srgbToLinear(r), srgbToLinear(g), srgbToLinear(b))
}

private fun decodeAc(value: Int, maximumValue: Float): FloatArray {
    val quantR = value / (19 * 19)
    val quantG = (value / 19) % 19
    val quantB = value % 19
    return floatArrayOf(
        signedPow((quantR - 9) / 9f, 2f) * maximumValue,
        signedPow((quantG - 9) / 9f, 2f) * maximumValue,
        signedPow((quantB - 9) / 9f, 2f) * maximumValue,
    )
}

private fun srgbToLinear(value: Int): Float {
    val v = value / 255f
    return if (v <= 0.04045f) {
        v / 12.92f
    } else {
        ((v + 0.055f) / 1.055f).pow(2.4f)
    }
}

private fun linearToSrgb(value: Float): Int {
    val v = value.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) {
        (v * 12.92f * 255f + 0.5f).toInt()
    } else {
        ((1.055f * v.pow(1f / 2.4f) - 0.055f) * 255f + 0.5f).toInt()
    }.coerceIn(0, 255)
}

private fun signedPow(value: Float, exp: Float): Float =
    value.sign() * kotlin.math.abs(value).pow(exp)

private fun Float.sign(): Float = if (this < 0f) -1f else 1f
