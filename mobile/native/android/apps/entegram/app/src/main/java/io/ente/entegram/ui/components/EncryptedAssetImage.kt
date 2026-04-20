package io.ente.entegram.ui.components

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.ente.entegram.core.services.WallClient
import kotlinx.coroutines.CancellationException

@Composable
fun EncryptedAssetImage(
    objectKey: String?,
    blurHash: String? = null,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallback: @Composable BoxScope.() -> Unit = {
        BlurHashFallback(
            blurHash = blurHash,
            contentDescription = contentDescription,
            contentScale = contentScale,
        )
    },
) {
    val tag = "EncryptedAssetImage"
    val context = LocalContext.current
    val wallClient = rememberWallClient(context)
    val imageBytes by produceState<ByteArray?>(initialValue = null, objectKey) {
        value = if (objectKey.isNullOrBlank()) {
            null
        } else {
            try {
                wallClient.loadAssetBytes(objectKey)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.e(tag, "failed to load asset objectKey=$objectKey", error)
                null
            }
        }
    }

    Box(modifier = modifier) {
        if (imageBytes != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageBytes)
                    .build(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            fallback()
        }
    }
}

@Composable
private fun BlurHashFallback(
    blurHash: String?,
    contentDescription: String?,
    contentScale: ContentScale,
) {
    val bitmap = remember(blurHash) { decodeBlurHashBitmap(blurHash)?.asImageBitmap() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
        )
    } else {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .fillMaxSize(),
        )
    }
}

@Composable
private fun rememberWallClient(context: Context): WallClient {
    return remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WallClientEntryPoint::class.java,
        ).wallClient()
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WallClientEntryPoint {
    fun wallClient(): WallClient
}
