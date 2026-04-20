package io.ente.entegram.core.services

import io.ente.entegram.core.models.AspectPreset
import io.ente.entegram.core.models.CaptionPayload
import io.ente.entegram.core.models.Comment
import io.ente.entegram.core.models.CommunityResult
import io.ente.entegram.core.models.FollowRequest
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.PostAsset
import io.ente.entegram.core.models.PostAssetVariant
import io.ente.entegram.core.models.Wall
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * In-memory mock implementation of [WallClient].
 * Backed by [SampleData] fixtures with mutable state for likes, comments, follows.
 * Simulates 120-400ms latency so loading/empty/error states are testable.
 */
class MockWallClient : WallClient {

    private val posts = SampleData.posts.toMutableList()
    private val comments = SampleData.comments.toMutableList()
    private val followRequests = SampleData.followRequests.toMutableList()
    private val walls = SampleData.walls.toMutableList()
    private val ownedWallIds = mutableSetOf<String>().apply { add(SampleData.viewerWall.id) }

    private val nextPostId = AtomicLong(3000)
    private val nextCommentId = AtomicLong(6000)
    private val nextFollowId = AtomicLong(9100)

    private val seedIndex = SampleData.photographicSeeds.iterator()

    private suspend fun simulateLatency() {
        delay((120L..400L).random())
    }

    // ── Wall ────────────────────────────────────────────────────

    override suspend fun listOwnedWalls(): List<Wall> {
        simulateLatency()
        return walls.filter { it.id in ownedWallIds }
    }

    override suspend fun wall(bySlug: String): Wall? {
        simulateLatency()
        return walls.find { it.slug == bySlug }
    }

    override suspend fun createWall(slug: String, displayName: String?, bio: String?): Wall {
        simulateLatency()
        if (walls.any { it.slug == slug }) {
            throw WallClientException.Conflict("Slug '$slug' is taken")
        }
        val wall = Wall(
            id = "wall-$slug",
            slug = slug,
            displayName = displayName,
            bio = bio,
            avatarObjectKey = null,
            keyVersion = 1,
            createdAt = Instant.now(),
            followerCount = 0,
            followingCount = 0,
            postCount = 0,
        )
        walls.add(wall)
        ownedWallIds += wall.id
        return wall
    }

    override suspend fun updateWallProfile(wallId: String, displayName: String?, bio: String?): Wall {
        simulateLatency()
        val index = walls.indexOfFirst { it.id == wallId }
        if (index < 0) throw WallClientException.NotFound()
        val updated = walls[index].copy(displayName = displayName, bio = bio)
        walls[index] = updated
        return updated
    }

    override suspend fun uploadAvatar(wallId: String, jpegData: ByteArray): Wall {
        simulateLatency()
        val index = walls.indexOfFirst { it.id == wallId }
        if (index < 0) throw WallClientException.NotFound()
        if (jpegData.isEmpty()) throw WallClientException.ServerError("Avatar payload is empty")
        val updated = walls[index].copy(avatarObjectKey = "avatars/$wallId-mock")
        walls[index] = updated
        return updated
    }

    override suspend fun removeAvatar(wallId: String): Wall {
        simulateLatency()
        val index = walls.indexOfFirst { it.id == wallId }
        if (index < 0) throw WallClientException.NotFound()
        val updated = walls[index].copy(avatarObjectKey = null)
        walls[index] = updated
        return updated
    }

    override suspend fun fetchWall(slug: String): Wall {
        simulateLatency()
        return walls.find { it.slug == slug }
            ?: throw WallClientException.NotFound()
    }

    override suspend fun listWallPosts(wallId: String, cursor: String?, limit: Int): Page<Post> {
        simulateLatency()
        val wallPosts = posts.filter { it.wallId == wallId }
            .sortedByDescending { it.createdAt }
        return paginate(wallPosts, cursor, limit)
    }

    // ── Feed ────────────────────────────────────────────────────

    override suspend fun listFeed(cursor: String?, limit: Int): Page<Post> {
        simulateLatency()
        val sorted = posts.sortedByDescending { it.createdAt }
        return paginate(sorted, cursor, limit)
    }

    // ── Posts ────────────────────────────────────────────────────

    override suspend fun fetchPost(id: Long): Post {
        simulateLatency()
        return posts.find { it.id == id }
            ?: throw WallClientException.NotFound()
    }

    override suspend fun createPost(input: CreatePostInput): Post {
        simulateLatency()
        val id = nextPostId.getAndIncrement()
        val viewer = SampleData.viewer
        val viewerWall = SampleData.viewerWall
        val assets = input.images.mapIndexed { i, img ->
            val seed = if (seedIndex.hasNext()) seedIndex.next()
            else "user-upload-$id-$i"
            PostAsset(
                position = i,
                variant = PostAssetVariant.Full,
                objectKey = "mock/$seed",
                blurHash = img.blurHash,
                aspect = when {
                    img.width > img.height -> AspectPreset.Landscape
                    img.width < img.height -> AspectPreset.Portrait
                    else -> AspectPreset.Square
                },
            )
        }
        val post = Post(
            id = id,
            wallId = input.wallId,
            authorSlug = viewerWall.slug,
            authorDisplayName = viewerWall.displayName,
            createdAt = Instant.now(),
            caption = CaptionPayload(
                text = input.caption,
                images = assets.map { a ->
                    CaptionPayload.Entry(a.position, a.blurHash, a.aspect)
                },
            ),
            assets = assets,
            likeCount = 0,
            commentCount = 0,
            viewerLiked = false,
        )
        posts.add(0, post)
        return post
    }

    override suspend fun deletePost(id: Long) {
        simulateLatency()
        val removed = posts.removeAll { it.id == id }
        if (!removed) throw WallClientException.NotFound()
        comments.removeAll { it.postId == id }
    }

    override suspend fun likePost(id: Long) {
        simulateLatency()
        replacePost(id) { it.copy(likeCount = it.likeCount + 1, viewerLiked = true) }
    }

    override suspend fun unlikePost(id: Long) {
        simulateLatency()
        replacePost(id) { it.copy(likeCount = (it.likeCount - 1).coerceAtLeast(0), viewerLiked = false) }
    }

    // ── Comments ────────────────────────────────────────────────

    override suspend fun listComments(postId: Long, cursor: String?, limit: Int): Page<Comment> {
        simulateLatency()
        val postComments = comments
            .filter { it.postId == postId }
            .sortedBy { it.createdAt }
        return paginate(postComments, cursor, limit)
    }

    override suspend fun createComment(postId: Long, parentId: Long?, text: String): Comment {
        simulateLatency()
        val id = nextCommentId.getAndIncrement()
        val viewer = SampleData.viewer
        val comment = Comment(
            id = id,
            postId = postId,
            parentId = parentId,
            authorSlug = SampleData.viewerWall.slug,
            authorDisplayName = "Me",
            text = text,
            createdAt = Instant.now(),
            replyCount = 0,
        )
        comments.add(comment)
        replacePost(postId) { it.copy(commentCount = it.commentCount + 1) }
        if (parentId != null) {
            replaceComment(parentId) { it.copy(replyCount = it.replyCount + 1) }
        }
        return comment
    }

    override suspend fun deleteComment(id: Long) {
        simulateLatency()
        val comment = comments.find { it.id == id }
            ?: throw WallClientException.NotFound()
        comments.removeAll { it.id == id }
        replacePost(comment.postId) {
            it.copy(commentCount = (it.commentCount - 1).coerceAtLeast(0))
        }
    }

    // ── Community ───────────────────────────────────────────────

    override suspend fun searchCommunity(query: String, limit: Int): List<CommunityResult> {
        simulateLatency()
        val q = query.lowercase()
        return SampleData.communityResults
            .filter {
                it.slug.contains(q) ||
                    (it.displayName?.lowercase()?.contains(q) == true)
            }
            .take(limit)
    }

    // ── Follow graph ────────────────────────────────────────────

    override suspend fun listFollowRequests(direction: FollowRequest.Direction): List<FollowRequest> {
        simulateLatency()
        return followRequests.filter { it.direction == direction }
    }

    override suspend fun listFollowers(wallId: String): List<FollowRequest> {
        simulateLatency()
        return followRequests.filter {
            it.wallId == wallId && it.direction == FollowRequest.Direction.Incoming
        }
    }

    override suspend fun listFollowing(): List<FollowRequest> {
        simulateLatency()
        return emptyList()
    }

    override suspend fun requestFollow(wallId: String) {
        simulateLatency()
        val id = nextFollowId.getAndIncrement()
        followRequests.add(
            FollowRequest(
                id = id,
                fromUserId = SampleData.viewer.id,
                fromSlug = SampleData.viewerWall.slug,
                fromDisplayName = "Me",
                wallId = wallId,
                createdAt = Instant.now(),
                direction = FollowRequest.Direction.Outgoing,
            ),
        )
    }

    override suspend fun approveFollowRequest(id: Long) {
        simulateLatency()
        followRequests.removeAll { it.id == id }
    }

    override suspend fun rejectFollowRequest(id: Long) {
        simulateLatency()
        followRequests.removeAll { it.id == id }
    }

    override suspend fun cancelFollowRequest(id: Long) {
        simulateLatency()
        followRequests.removeAll { it.id == id }
    }

    override suspend fun unfollow(wallId: String) {
        simulateLatency()
        followRequests.removeAll { it.wallId == wallId }
    }

    // ── Assets ──────────────────────────────────────────────────

    override suspend fun loadAssetBytes(objectKey: String): ByteArray {
        return byteArrayOf()
    }

    override suspend fun loadAvatarBytes(wallId: String, objectKey: String): ByteArray {
        return byteArrayOf()
    }

    // ── Pagination helper ───────────────────────────────────────

    @OptIn(ExperimentalEncodingApi::class)
    private fun <T> paginate(
        items: List<T>,
        cursor: String?,
        limit: Int,
    ): Page<T> {
        val offset = if (cursor != null) {
            try {
                String(Base64.decode(cursor)).toInt()
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }
        val slice = items.drop(offset).take(limit)
        val nextOffset = offset + slice.size
        val nextCursor = if (nextOffset < items.size) {
            Base64.encode(nextOffset.toString().toByteArray())
        } else {
            null
        }
        return Page(items = slice, nextCursor = nextCursor)
    }

    // ── Mutation helpers ────────────────────────────────────────

    private fun replacePost(id: Long, transform: (Post) -> Post) {
        val idx = posts.indexOfFirst { it.id == id }
        if (idx >= 0) posts[idx] = transform(posts[idx])
    }

    private fun replaceComment(id: Long, transform: (Comment) -> Comment) {
        val idx = comments.indexOfFirst { it.id == id }
        if (idx >= 0) comments[idx] = transform(comments[idx])
    }
}
