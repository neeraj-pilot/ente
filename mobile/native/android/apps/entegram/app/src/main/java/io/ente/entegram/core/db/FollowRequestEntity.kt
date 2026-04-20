package io.ente.entegram.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.ente.entegram.core.models.FollowRequest
import java.time.Instant

@Entity(
    tableName = "follow_requests",
    indices = [
        Index("direction"),
        Index("wall_id"),
    ],
)
data class FollowRequestEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "from_user_id") val fromUserId: Long,
    @ColumnInfo(name = "from_slug") val fromSlug: String,
    @ColumnInfo(name = "from_display_name") val fromDisplayName: String?,
    @ColumnInfo(name = "wall_id") val wallId: String,
    @ColumnInfo(name = "created_at") val createdAt: Instant,
    val direction: FollowRequest.Direction,
    @ColumnInfo(name = "cached_at") val cachedAt: Instant = Instant.now(),
) {
    fun toModel(): FollowRequest = FollowRequest(
        id = id,
        fromUserId = fromUserId,
        fromSlug = fromSlug,
        fromDisplayName = fromDisplayName,
        wallId = wallId,
        createdAt = createdAt,
        direction = direction,
    )

    companion object {
        fun from(req: FollowRequest): FollowRequestEntity = FollowRequestEntity(
            id = req.id,
            fromUserId = req.fromUserId,
            fromSlug = req.fromSlug,
            fromDisplayName = req.fromDisplayName,
            wallId = req.wallId,
            createdAt = req.createdAt,
            direction = req.direction,
        )
    }
}
