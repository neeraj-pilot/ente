package io.ente.entegram.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.ente.entegram.core.models.AspectPreset
import io.ente.entegram.core.models.CaptionPayload
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.PostAsset
import java.time.Instant

@Entity(
    tableName = "posts",
    indices = [
        Index("wall_id"),
        Index("created_at"),
    ],
)
data class PostEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "wall_id") val wallId: String,
    @ColumnInfo(name = "author_slug") val authorSlug: String,
    @ColumnInfo(name = "author_display_name") val authorDisplayName: String?,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "caption_text") val captionText: String,
    @ColumnInfo(name = "caption_images") val captionImages: List<CaptionPayload.Entry>,
    val assets: List<PostAsset>,
    @ColumnInfo(name = "like_count") val likeCount: Int,
    @ColumnInfo(name = "comment_count") val commentCount: Int,
    @ColumnInfo(name = "viewer_liked") val viewerLiked: Boolean,
    @ColumnInfo(name = "cached_at") val cachedAt: Instant = Instant.now(),
) {
    fun toModel(): Post = Post(
        id = id,
        wallId = wallId,
        authorSlug = authorSlug,
        authorDisplayName = authorDisplayName,
        createdAt = createdAt,
        caption = CaptionPayload(text = captionText, images = captionImages),
        assets = assets,
        likeCount = likeCount,
        commentCount = commentCount,
        viewerLiked = viewerLiked,
    )

    companion object {
        fun from(post: Post): PostEntity = PostEntity(
            id = post.id,
            wallId = post.wallId,
            authorSlug = post.authorSlug,
            authorDisplayName = post.authorDisplayName,
            createdAt = post.createdAt,
            captionText = post.caption.text,
            captionImages = post.caption.images,
            assets = post.assets,
            likeCount = post.likeCount,
            commentCount = post.commentCount,
            viewerLiked = post.viewerLiked,
        )
    }
}
