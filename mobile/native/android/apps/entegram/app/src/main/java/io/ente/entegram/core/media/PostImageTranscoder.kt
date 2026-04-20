package io.ente.entegram.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PostImageTranscoder {
    private const val FULL_MAX_DIMENSION = 1600
    private const val THUMBNAIL_MAX_DIMENSION = 480
    private const val FULL_BYTE_BUDGET = 1_700_000
    private const val THUMBNAIL_BYTE_BUDGET = 140_000

    data class PreparedImage(
        val fullData: ByteArray,
        val thumbnailData: ByteArray,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
        val blurHash: String = "",
    )

    suspend fun preparePostImage(context: Context, uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        val sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Couldn't read the selected image.")
        val sourceBitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: error("Couldn't decode the selected image.")
        val orientedBitmap = BitmapOrientation.applyExifOrientation(sourceBitmap, sourceBytes)
        if (orientedBitmap !== sourceBitmap) {
            sourceBitmap.recycle()
        }

        val flattened = flatten(orientedBitmap)
        if (flattened !== orientedBitmap) {
            orientedBitmap.recycle()
        }

        try {
            val full = encodeJpeg(
                flattened,
                maxDimension = FULL_MAX_DIMENSION,
                targetBytes = FULL_BYTE_BUDGET,
                qualities = intArrayOf(82, 76, 70, 64),
                dimensionFallbacks = intArrayOf(1600, 1440, 1280, 1080, 960),
            )
            val thumbnail = encodeJpeg(
                flattened,
                maxDimension = THUMBNAIL_MAX_DIMENSION,
                targetBytes = THUMBNAIL_BYTE_BUDGET,
                qualities = intArrayOf(66, 60, 54, 48),
                dimensionFallbacks = intArrayOf(480, 384, 320, 256),
            )
            PreparedImage(
                fullData = full.data,
                thumbnailData = thumbnail.data,
                width = full.bitmap.width,
                height = full.bitmap.height,
                sizeBytes = full.data.size.toLong(),
            )
        } finally {
            flattened.recycle()
        }
    }

    private data class EncodedJpeg(
        val data: ByteArray,
        val bitmap: Bitmap,
    )

    private fun encodeJpeg(
        source: Bitmap,
        maxDimension: Int,
        targetBytes: Int,
        qualities: IntArray,
        dimensionFallbacks: IntArray,
    ): EncodedJpeg {
        var bestAttempt: EncodedJpeg? = null
        for (candidateDimension in dimensionFallbacks) {
            if (candidateDimension > maxDimension) continue
            val resized = scaleDownIfNeeded(source, candidateDimension)
            for (quality in qualities) {
                val output = ByteArrayOutputStream()
                check(resized.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    "Couldn't prepare the selected images."
                }
                val bytes = output.toByteArray()
                bestAttempt = EncodedJpeg(bytes, resized)
                if (bytes.size <= targetBytes) {
                    return EncodedJpeg(bytes, resized)
                }
            }
        }
        val attempt = bestAttempt ?: error("Couldn't prepare the selected images.")
        if (attempt.data.size > targetBytes) {
            error("One of the selected images is still too large after compression.")
        }
        return attempt
    }

    private fun flatten(bitmap: Bitmap): Bitmap {
        if (!bitmap.hasAlpha()) return bitmap
        val flattened = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flattened)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return flattened
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longestEdge = max(bitmap.width, bitmap.height)
        if (longestEdge <= maxDimension) {
            return bitmap
        }
        val scale = maxDimension.toFloat() / longestEdge.toFloat()
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
