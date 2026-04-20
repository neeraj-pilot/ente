package io.ente.entegram.core.models

import java.time.Instant

data class Post(
    val id: Long,
    val wallId: String,
    val authorSlug: String,
    val authorDisplayName: String?,
    val createdAt: Instant,
    val caption: CaptionPayload,
    val assets: List<PostAsset>,
    val likeCount: Int,
    val commentCount: Int,
    val viewerLiked: Boolean,
)

data class PostAsset(
    val position: Int,
    val variant: PostAssetVariant,
    val objectKey: String,
    val blurHash: String,
    val aspect: AspectPreset,
)

enum class PostAssetVariant { Full, Thumbnail }

enum class AspectPreset(val ratio: Float) {
    Landscape(4f / 3f),
    Portrait(3f / 4f),
    Square(1f),
}
