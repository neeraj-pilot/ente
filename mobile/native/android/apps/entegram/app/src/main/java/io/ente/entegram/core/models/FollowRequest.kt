package io.ente.entegram.core.models

import java.time.Instant

data class FollowRequest(
    val id: Long,
    val fromUserId: Long,
    val fromSlug: String,
    val fromDisplayName: String?,
    val wallId: String,
    val createdAt: Instant,
    val direction: Direction,
) {
    enum class Direction { Incoming, Outgoing }
}
