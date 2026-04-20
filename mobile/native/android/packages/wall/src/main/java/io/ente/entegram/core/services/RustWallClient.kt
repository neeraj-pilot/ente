package io.ente.entegram.core.services

import io.ente.entegram.BuildConfig
import io.ente.entegram.core.crypto.EnteWallCore
import io.ente.entegram.core.models.AspectPreset
import io.ente.entegram.core.models.CaptionPayload
import io.ente.entegram.core.models.Comment
import io.ente.entegram.core.models.CommunityResult
import io.ente.entegram.core.models.FollowRequest
import io.ente.entegram.core.models.Post
import io.ente.entegram.core.models.PostAsset
import io.ente.entegram.core.models.PostAssetVariant
import io.ente.entegram.core.models.Wall
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uniffi.ente_ffi.FfiCreatePostImage
import uniffi.ente_ffi.FfiException
import uniffi.ente_ffi.FfiWallSession

@Singleton
class RustWallClient @Inject constructor(
    private val authSessionStore: AuthSessionStore,
    private val enteWallCore: EnteWallCore,
) : WallClient {
    private val defaultBaseUrl: String = BuildConfig.ENTEGRAM_API_BASE_URL
    private val postKeys = ConcurrentHashMap<Long, String>()
    private val assetKeys = ConcurrentHashMap<String, AssetKey>()
    private val commentPosts = ConcurrentHashMap<Long, Long>()

    override suspend fun listOwnedWalls(): List<Wall> =
        runDecode<List<WireWall>> {
            enteWallCore.listOwnedWallsJson(requireWallSession())
        }.map { it.model }

    override suspend fun wall(bySlug: String): Wall? {
        val payload = runRustCall {
            enteWallCore.lookupBySlugJson(requireWallSession(), bySlug)
        }
        if (payload == "null" || payload.isBlank()) {
            return null
        }
        return json.decodeFromString<WireWall>(payload).model
    }

    override suspend fun createWall(slug: String, displayName: String?, bio: String?): Wall =
        runDecode<WireWall> {
            enteWallCore.createWallJson(
                session = requireWallSession(),
                wallSlug = slug,
                displayName = displayName,
                bio = bio,
            )
        }.model

    override suspend fun updateWallProfile(
        wallId: String,
        displayName: String?,
        bio: String?,
    ): Wall = runDecode<WireWall> {
        enteWallCore.updateWallProfileJson(
            session = requireWallSession(),
            wallId = wallId,
            displayName = displayName,
            bio = bio,
        )
    }.model

    override suspend fun uploadAvatar(wallId: String, jpegData: ByteArray): Wall =
        runDecode<WireWall> {
            enteWallCore.uploadAvatarJson(
                session = requireWallSession(),
                wallId = wallId,
                jpegData = jpegData,
            )
        }.model

    override suspend fun removeAvatar(wallId: String): Wall =
        runDecode<WireWall> {
            enteWallCore.removeAvatarJson(
                session = requireWallSession(),
                wallId = wallId,
            )
        }.model

    override suspend fun fetchWall(slug: String): Wall {
        return wall(slug) ?: throw WallClientException.NotFound()
    }

    override suspend fun listWallPosts(wallId: String, cursor: String?, limit: Int): Page<Post> {
        val page = runDecode<WirePage<WirePost>> {
            enteWallCore.listWallPostsJson(
                session = requireWallSession(),
                wallId = wallId,
                cursor = cursor,
                limit = limit,
            )
        }
        return remember(page)
    }

    override suspend fun listFeed(cursor: String?, limit: Int): Page<Post> {
        val page = runDecode<WirePage<WirePost>> {
            enteWallCore.listFeedJson(
                session = requireWallSession(),
                cursor = cursor,
                limit = limit,
            )
        }
        return remember(page)
    }

    override suspend fun fetchPost(id: Long): Post {
        val post = runDecode<WirePost> {
            enteWallCore.fetchPostJson(requireWallSession(), id)
        }
        return remember(post)
    }

    override suspend fun createPost(input: CreatePostInput): Post {
        val images = input.images.flatMapIndexed { index, image ->
            listOf(
                FfiCreatePostImage(
                    data = image.fullData,
                    position = index,
                    variant = "full",
                    width = image.width,
                    height = image.height,
                    blurHash = image.blurHash,
                    contentType = "image/jpeg",
                ),
                FfiCreatePostImage(
                    data = image.thumbnailData,
                    position = index,
                    variant = "thumbnail",
                    width = image.width,
                    height = image.height,
                    blurHash = image.blurHash,
                    contentType = "image/jpeg",
                ),
            )
        }
        val post = runDecode<WirePost> {
            enteWallCore.createPostJson(
                session = requireWallSession(),
                wallId = input.wallId,
                caption = input.caption,
                images = images,
            )
        }
        return remember(post)
    }

    override suspend fun deletePost(id: Long) {
        runRustCall {
            enteWallCore.deletePost(requireWallSession(), id)
        }
        postKeys.remove(id)
        commentPosts.entries.removeIf { it.value == id }
    }

    override suspend fun likePost(id: Long) {
        runRustCall {
            enteWallCore.setPostLike(requireWallSession(), id, true)
        }
    }

    override suspend fun unlikePost(id: Long) {
        runRustCall {
            enteWallCore.setPostLike(requireWallSession(), id, false)
        }
    }

    override suspend fun listComments(postId: Long, cursor: String?, limit: Int): Page<Comment> {
        val key = postKey(postId)
        val page = runDecode<WirePage<WireComment>> {
            enteWallCore.listCommentsJson(
                session = requireWallSession(),
                postId = postId,
                postKeyB64 = key,
                cursor = cursor,
                limit = limit,
            )
        }
        return rememberComments(page)
    }

    override suspend fun createComment(postId: Long, parentId: Long?, text: String): Comment {
        val key = postKey(postId)
        val comment = runDecode<WireComment> {
            enteWallCore.createCommentJson(
                session = requireWallSession(),
                postId = postId,
                postKeyB64 = key,
                text = text,
                parentCommentId = parentId,
            )
        }
        commentPosts[comment.id] = comment.postId
        return comment.model
    }

    override suspend fun deleteComment(id: Long) {
        val postId = commentPosts[id] ?: throw WallClientException.NotFound()
        runRustCall {
            enteWallCore.deleteComment(
                session = requireWallSession(),
                postId = postId,
                commentId = id,
            )
        }
    }

    override suspend fun searchCommunity(query: String, limit: Int): List<CommunityResult> =
        runDecode<List<WireCommunityResult>> {
            enteWallCore.searchCommunityJson(
                session = requireWallSession(),
                query = query,
                cursor = null,
                limit = limit,
            )
        }.map { it.model }

    override suspend fun listFollowRequests(direction: FollowRequest.Direction): List<FollowRequest> =
        runDecode<List<WireFollowRequest>> {
            enteWallCore.listFollowRequestsJson(
                session = requireWallSession(),
                direction = direction.wireValue,
            )
        }.map { it.model }

    override suspend fun listFollowers(wallId: String): List<FollowRequest> =
        runDecode<List<WireFollowRequest>> {
            enteWallCore.listFollowersJson(
                session = requireWallSession(),
                wallId = wallId,
            )
        }.map { it.model }

    override suspend fun listFollowing(): List<FollowRequest> =
        runDecode<List<WireFollowRequest>> {
            enteWallCore.listFollowingJson(
                session = requireWallSession(),
            )
        }.map { it.model }

    override suspend fun requestFollow(wallId: String) {
        runRustCall {
            enteWallCore.requestFollow(requireWallSession(), wallId)
        }
    }

    override suspend fun approveFollowRequest(id: Long) {
        runRustCall {
            enteWallCore.approveFollowRequest(requireWallSession(), id)
        }
    }

    override suspend fun rejectFollowRequest(id: Long) {
        runRustCall {
            enteWallCore.rejectFollowRequest(requireWallSession(), id)
        }
    }

    override suspend fun cancelFollowRequest(id: Long) {
        runRustCall {
            enteWallCore.cancelFollowRequest(requireWallSession(), id)
        }
    }

    override suspend fun unfollow(wallId: String) {
        runRustCall {
            enteWallCore.unfollow(requireWallSession(), wallId)
        }
    }

    override suspend fun loadAssetBytes(objectKey: String): ByteArray {
        val key = assetKeys[objectKey] ?: throw WallClientException.NotFound()
        return runRustCall {
            enteWallCore.loadAssetBytes(
                session = requireWallSession(),
                wallId = key.wallId,
                objectKey = objectKey,
                postKeyB64 = key.postKeyB64,
            )
        }
    }

    override suspend fun loadAvatarBytes(wallId: String, objectKey: String): ByteArray =
        runRustCall {
            enteWallCore.loadAvatarBytes(
                session = requireWallSession(),
                wallId = wallId,
                objectKey = objectKey,
            )
        }

    private suspend fun requireWallSession(): FfiWallSession {
        val persisted = authSessionStore.read() ?: throw WallClientException.NotAuthenticated()
        val login = persisted.toLoginResult()
        if (login.masterKey.isEmpty() || login.secretKey.isEmpty() || login.publicKey.isEmpty()) {
            throw WallClientException.NotAuthenticated()
        }
        return FfiWallSession(
            baseUrl = defaultBaseUrl,
            authToken = persisted.sessionToken,
            masterKey = login.masterKey,
            publicKey = login.publicKey,
            privateKey = login.secretKey,
            userId = persisted.userId,
        )
    }

    private suspend fun postKey(postId: Long): String {
        postKeys[postId]?.let { return it }
        fetchPost(postId)
        return postKeys[postId] ?: throw WallClientException.DecryptionFailed()
    }

    private fun remember(page: WirePage<WirePost>): Page<Post> {
        return Page(
            items = page.items.map { remember(it) },
            nextCursor = page.nextCursor,
        )
    }

    private fun remember(post: WirePost): Post {
        postKeys[post.id] = post.postKeyB64
        post.assets.forEach { asset ->
            assetKeys[asset.objectKey] = AssetKey(
                wallId = post.wallId,
                postKeyB64 = post.postKeyB64,
            )
        }
        return post.model
    }

    private fun rememberComments(page: WirePage<WireComment>): Page<Comment> {
        page.items.forEach { comment ->
            commentPosts[comment.id] = comment.postId
        }
        return Page(
            items = page.items.map { it.model },
            nextCursor = page.nextCursor,
        )
    }

    private suspend inline fun <reified T> runDecode(
        noinline work: suspend () -> String,
    ): T {
        val payload = try {
            work()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            throw mapError(error)
        }
        return try {
            json.decodeFromString(payload)
        } catch (error: SerializationException) {
            throw WallClientException.ServerError("Invalid wall payload: ${error.message ?: error}")
        }
    }

    private suspend fun <T> runRustCall(
        work: suspend () -> T,
    ): T = withContext(Dispatchers.IO) {
        try {
            work()
        } catch (error: Throwable) {
            throw mapError(error)
        }
    }

    private fun mapError(error: Throwable): Throwable {
        return when (error) {
            is WallClientException -> error
            is FfiException -> {
                val message = error.message.orEmpty()
                when {
                    "404" in message -> WallClientException.NotFound()
                    "401" in message || message.contains("unauthorized", ignoreCase = true) ->
                        WallClientException.NotAuthenticated()
                    "409" in message || message.contains("conflict", ignoreCase = true) ->
                        WallClientException.Conflict(message)
                    isNetworkMessage(message) ->
                        WallClientException.Network("Network error. Check that the server is reachable and try again.")
                    else -> WallClientException.ServerError(message.ifBlank { "Wall request failed" })
                }
            }
            is IllegalStateException -> WallClientException.ServerError(error.message ?: "Wall request failed")
            else -> error
        }
    }

    private fun isNetworkMessage(message: String): Boolean {
        return message.contains("network error", ignoreCase = true) ||
            message.contains("error sending request", ignoreCase = true) ||
            message.contains("connection refused", ignoreCase = true) ||
            message.contains("failed to connect", ignoreCase = true) ||
            message.contains("timed out", ignoreCase = true)
    }

    private data class AssetKey(
        val wallId: String,
        val postKeyB64: String,
    )

    @Serializable
    private data class WireWall(
        val id: String,
        val slug: String,
        val displayName: String? = null,
        val bio: String? = null,
        val avatarObjectKey: String? = null,
        val keyVersion: Int,
        val createdAt: String? = null,
        val followerCount: Int,
        val followingCount: Int = 0,
        val postCount: Int,
    ) {
        val model: Wall
            get() = Wall(
                id = id,
                slug = slug,
                displayName = displayName,
                bio = bio,
                avatarObjectKey = avatarObjectKey,
                keyVersion = keyVersion,
                createdAt = createdAt?.let(Instant::parse),
                followerCount = followerCount,
                followingCount = followingCount,
                postCount = postCount,
            )
    }

    @Serializable
    private data class WireAsset(
        val position: Int,
        val variant: String,
        val objectKey: String,
        val blurHash: String,
        val aspect: String,
    ) {
        val model: PostAsset
            get() = PostAsset(
                position = position,
                variant = when (variant.lowercase()) {
                    "thumbnail" -> PostAssetVariant.Thumbnail
                    else -> PostAssetVariant.Full
                },
                objectKey = objectKey,
                blurHash = blurHash,
                aspect = when (aspect.lowercase()) {
                    "portrait" -> AspectPreset.Portrait
                    "square" -> AspectPreset.Square
                    else -> AspectPreset.Landscape
                },
            )
    }

    @Serializable
    private data class WirePost(
        val id: Long,
        val wallId: String,
        val authorSlug: String,
        val authorDisplayName: String? = null,
        val createdAt: String,
        val caption: String,
        val assets: List<WireAsset>,
        val likeCount: Int,
        val commentCount: Int,
        val viewerLiked: Boolean,
        val postKeyB64: String,
    ) {
        val model: Post
            get() {
                val assetModels = assets.map { it.model }
                return Post(
                    id = id,
                    wallId = wallId,
                    authorSlug = authorSlug,
                    authorDisplayName = authorDisplayName,
                    createdAt = Instant.parse(createdAt),
                    caption = CaptionPayload(
                        text = caption,
                        images = assetModels
                            .filter { it.variant == PostAssetVariant.Full }
                            .map { asset ->
                                CaptionPayload.Entry(
                                    position = asset.position,
                                    blurHash = asset.blurHash,
                                    aspect = asset.aspect,
                                )
                            },
                    ),
                    assets = assetModels,
                    likeCount = likeCount,
                    commentCount = commentCount,
                    viewerLiked = viewerLiked,
                )
            }
    }

    @Serializable
    private data class WireComment(
        val id: Long,
        val postId: Long,
        val parentId: Long? = null,
        val authorSlug: String,
        val authorDisplayName: String? = null,
        val text: String,
        val createdAt: String,
        val replyCount: Int,
    ) {
        val model: Comment
            get() = Comment(
                id = id,
                postId = postId,
                parentId = parentId,
                authorSlug = authorSlug,
                authorDisplayName = authorDisplayName,
                text = text,
                createdAt = Instant.parse(createdAt),
                replyCount = replyCount,
            )
    }

    @Serializable
    private data class WirePage<T>(
        val items: List<T>,
        val nextCursor: String? = null,
    )

    @Serializable
    private data class WireFollowRequest(
        val id: Long,
        val fromUserId: Long,
        val fromSlug: String,
        val fromDisplayName: String? = null,
        val wallId: String,
        val createdAt: String,
        val direction: String,
    ) {
        val model: FollowRequest
            get() = FollowRequest(
                id = id,
                fromUserId = fromUserId,
                fromSlug = fromSlug,
                fromDisplayName = fromDisplayName,
                wallId = wallId,
                createdAt = Instant.parse(createdAt),
                direction = when (direction.lowercase()) {
                    "outgoing" -> FollowRequest.Direction.Outgoing
                    else -> FollowRequest.Direction.Incoming
                },
            )
    }

    @Serializable
    private data class WireCommunityResult(
        val id: String,
        val slug: String,
        val displayName: String? = null,
        val followerCount: Int,
        val postCount: Int = 0,
        val relationship: String? = null,
        val bio: String? = null,
    ) {
        val model: CommunityResult
            get() = CommunityResult(
                id = id,
                slug = slug,
                displayName = displayName,
                followerCount = followerCount,
                postCount = postCount,
                relationship = relationship,
                bio = bio,
            )
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

private val FollowRequest.Direction.wireValue: String
    get() = when (this) {
        FollowRequest.Direction.Incoming -> "incoming"
        FollowRequest.Direction.Outgoing -> "outgoing"
    }
