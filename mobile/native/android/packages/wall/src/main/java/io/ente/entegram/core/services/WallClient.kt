package io.ente.entegram.core.services

import io.ente.entegram.core.models.Comment
import io.ente.entegram.core.models.CommunityResult
import io.ente.entegram.core.models.FollowRequest
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.Wall

data class Page<T>(
    val items: List<T>,
    val nextCursor: String?,
)

data class CreatePostInput(
    val wallId: String,
    val caption: String,
    val images: List<Image>,
) {
    data class Image(
        val fullData: ByteArray,
        val thumbnailData: ByteArray,
        val width: Int,
        val height: Int,
        val blurHash: String,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Image) return false
            return fullData.contentEquals(other.fullData) &&
                thumbnailData.contentEquals(other.thumbnailData) &&
                width == other.width &&
                height == other.height &&
                blurHash == other.blurHash
        }

        override fun hashCode(): Int {
            var result = fullData.contentHashCode()
            result = 31 * result + thumbnailData.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + blurHash.hashCode()
            return result
        }
    }
}

sealed class WallClientException(message: String) : Exception(message) {
    class NotAuthenticated : WallClientException("Not authenticated")
    class NotFound : WallClientException("Not found")
    class Conflict(detail: String) : WallClientException(detail)
    class InvalidCredentials : WallClientException("Invalid credentials")
    class InvalidOtt : WallClientException("Invalid OTT")
    class RateLimited : WallClientException("Rate limited")
    class Network(detail: String) : WallClientException(detail)
    class ServerError(detail: String) : WallClientException(detail)
    class DecryptionFailed : WallClientException("Decryption failed")
}

interface WallClient {

    // Wall (profile) surface
    suspend fun listOwnedWalls(): List<Wall>
    suspend fun wall(bySlug: String): Wall?
    suspend fun createWall(slug: String, displayName: String?, bio: String?): Wall
    suspend fun updateWallProfile(wallId: String, displayName: String?, bio: String?): Wall
    suspend fun uploadAvatar(wallId: String, jpegData: ByteArray): Wall
    suspend fun removeAvatar(wallId: String): Wall
    suspend fun fetchWall(slug: String): Wall
    suspend fun listWallPosts(wallId: String, cursor: String?, limit: Int): Page<Post>

    // Feed
    suspend fun listFeed(cursor: String?, limit: Int): Page<Post>

    // Posts
    suspend fun fetchPost(id: Long): Post
    suspend fun createPost(input: CreatePostInput): Post
    suspend fun deletePost(id: Long)
    suspend fun likePost(id: Long)
    suspend fun unlikePost(id: Long)

    // Comments
    suspend fun listComments(postId: Long, cursor: String?, limit: Int): Page<Comment>
    suspend fun createComment(postId: Long, parentId: Long?, text: String): Comment
    suspend fun deleteComment(id: Long)

    // Community / discovery
    suspend fun searchCommunity(query: String, limit: Int): List<CommunityResult>

    // Follow graph
    suspend fun listFollowRequests(direction: FollowRequest.Direction): List<FollowRequest>
    suspend fun listFollowers(wallId: String): List<FollowRequest>
    suspend fun listFollowing(): List<FollowRequest>
    suspend fun requestFollow(wallId: String)
    suspend fun approveFollowRequest(id: Long)
    suspend fun rejectFollowRequest(id: Long)
    suspend fun cancelFollowRequest(id: Long)
    suspend fun unfollow(wallId: String)

    // Asset bytes
    suspend fun loadAssetBytes(objectKey: String): ByteArray

    // Avatar bytes (decrypted with wall key, not post key)
    suspend fun loadAvatarBytes(wallId: String, objectKey: String): ByteArray
}
