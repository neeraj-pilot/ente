package io.ente.entegram.core.models

data class CommunityResult(
    val id: String,
    val slug: String,
    val displayName: String?,
    val followerCount: Int,
    val postCount: Int = 0,
    val relationship: String?,
    val bio: String? = null,
)
