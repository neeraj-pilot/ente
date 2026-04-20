package io.ente.entegram.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AvatarTranscoder {
    suspend fun transcodeToJpeg(
        context: Context,
        uri: Uri,
        maxDimension: Int = 1024,
        quality: Int = 88,
    ): ByteArray = withContext(Dispatchers.IO) {
        val sourceBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Couldn't read the selected image.")
        val sourceBitmap = BitmapFactory.decodeByteArray(sourceBytes, 0, sourceBytes.size)
            ?: error("Couldn't decode the selected image.")
        val orientedBitmap = BitmapOrientation.applyExifOrientation(sourceBitmap, sourceBytes)
        if (orientedBitmap !== sourceBitmap) {
            sourceBitmap.recycle()
        }

        val scaledBitmap = scaleDownIfNeeded(orientedBitmap, maxDimension)
        val output = ByteArrayOutputStream()
        check(scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            "Couldn't prepare the avatar image."
        }
        if (scaledBitmap !== orientedBitmap) {
            scaledBitmap.recycle()
        }
        orientedBitmap.recycle()
        output.toByteArray()
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
