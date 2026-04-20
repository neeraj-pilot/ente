package io.ente.entegram.ui.components

import android.util.Log
import android.util.LruCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dagger.hilt.android.EntryPointAccessors
import io.ente.entegram.R
import io.ente.entegram.app.EnteGramTheme
import io.ente.entegram.ui.design.AvatarPalette

/** In-memory LRU cache for decoded avatar bytes, keyed by slug. */
private val avatarCache = LruCache<String, ByteArray>(32)

/** Invalidate cached avatar for a slug (e.g. after upload/remove). */
fun invalidateAvatarCache(slug: String) {
    avatarCache.remove(slug)
}

@Composable
fun AvatarView(
    slug: String,
    displayName: String?,
    avatarObjectKey: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    val gradient = remember(slug) { AvatarPalette.forSlug(slug) }
    val initial = remember(slug, displayName) {
        (displayName?.firstOrNull() ?: slug.firstOrNull() ?: '?').uppercaseChar().toString()
    }
    val fontSize = remember(size) { (size.value * 0.42f).sp }

    val name = displayName ?: "@$slug"
    val avatarDescription = stringResource(R.string.avatar_description, name)

    val context = LocalContext.current
    val wallClient = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            WallClientEntryPoint::class.java,
        ).wallClient()
    }

    val cachedBytes = remember(slug) { avatarCache.get(slug) }
    val avatarBytes by produceState(initialValue = cachedBytes, slug) {
        if (value != null) return@produceState // already have cached bytes
        if (slug.isBlank()) {
            value = null
            return@produceState
        }
        value = runCatching {
            val wall = wallClient.fetchWall(slug)
            val key = wall.avatarObjectKey
            if (key.isNullOrBlank()) null
            else wallClient.loadAvatarBytes(wall.id, key)
        }
            .onFailure { e -> Log.d("AvatarView", "avatar load failed for $slug", e) }
            .getOrNull()
            ?.also { bytes -> avatarCache.put(slug, bytes) }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(gradient.top, gradient.bottom),
                ),
            )
            .semantics { contentDescription = avatarDescription },
        contentAlignment = Alignment.Center,
    ) {
        // Initials fallback
        Text(
            text = initial,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
            fontSize = fontSize,
            lineHeight = fontSize * 1.2f,
        )

        // Real avatar with crossfade
        AnimatedVisibility(
            visible = avatarBytes != null,
            enter = fadeIn(),
        ) {
            avatarBytes?.let { bytes ->
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(bytes)
                        .build(),
                    contentDescription = avatarDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun AvatarViewPreview() {
    EnteGramTheme {
        AvatarView(slug = "lena", displayName = "Lena Marchetti")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0E0E10)
@Composable
private fun AvatarViewLargePreview() {
    EnteGramTheme {
        AvatarView(slug = "sora-kitchen", displayName = "Sora's Kitchen", size = 56.dp)
    }
}
