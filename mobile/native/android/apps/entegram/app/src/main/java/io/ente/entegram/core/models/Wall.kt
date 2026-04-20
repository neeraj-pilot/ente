package io.ente.entegram.core.models

import java.time.Instant

data class Wall(
    val id: String,
    val slug: String,
    val displayName: String?,
    val bio: String?,
    val avatarObjectKey: String?,
    val keyVersion: Int,
    val createdAt: Instant?,
    val followerCount: Int,
    val followingCount: Int,
    val postCount: Int,
)
