package io.ente.entegram.core.models

import java.time.Instant

data class Comment(
    val id: Long,
    val postId: Long,
    val parentId: Long?,
    val authorSlug: String,
    val authorDisplayName: String?,
    val text: String,
    val createdAt: Instant,
    val replyCount: Int,
)
