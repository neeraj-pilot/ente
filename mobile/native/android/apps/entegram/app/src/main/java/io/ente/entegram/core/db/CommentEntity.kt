package io.ente.entegram.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.ente.entegram.core.models.Comment
import java.time.Instant

@Entity(
    tableName = "comments",
    indices = [
        Index("post_id"),
        Index("created_at"),
    ],
)
data class CommentEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "post_id") val postId: Long,
    @ColumnInfo(name = "parent_id") val parentId: Long?,
    @ColumnInfo(name = "author_slug") val authorSlug: String,
    @ColumnInfo(name = "author_display_name") val authorDisplayName: String?,
    val text: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    @ColumnInfo(name = "reply_count") val replyCount: Int,
    @ColumnInfo(name = "cached_at") val cachedAt: Instant = Instant.now(),
) {
    fun toModel(): Comment = Comment(
        id = id,
        postId = postId,
        parentId = parentId,
        authorSlug = authorSlug,
        authorDisplayName = authorDisplayName,
        text = text,
        createdAt = createdAt,
        replyCount = replyCount,
    )

    companion object {
        fun from(comment: Comment): CommentEntity = CommentEntity(
            id = comment.id,
            postId = comment.postId,
            parentId = comment.parentId,
            authorSlug = comment.authorSlug,
            authorDisplayName = comment.authorDisplayName,
            text = comment.text,
            createdAt = comment.createdAt,
            replyCount = comment.replyCount,
        )
    }
}
