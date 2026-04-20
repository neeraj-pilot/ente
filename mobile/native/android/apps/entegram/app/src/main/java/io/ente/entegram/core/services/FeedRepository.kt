package io.ente.entegram.core.services

import io.ente.entegram.core.db.PostDao
import io.ente.entegram.core.db.PostEntity
import io.ente.entegram.core.models.Post
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stale-while-revalidate feed cache.
 *
 * - [cachedFeed] emits the local Room cache as a Flow (always up-to-date with DB writes).
 * - [revalidate] fetches from the network, writes into Room, and returns the page metadata.
 * - [loadMore] fetches the next page from the network and appends to Room.
 * - [toggleLike] does an optimistic local update + fire-and-forget network call with rollback.
 *
 * The ViewModel observes [cachedFeed] for display and calls [revalidate]/[loadMore] for freshness.
 */
@Singleton
class FeedRepository @Inject constructor(
    private val wallClient: WallClient,
    private val postDao: PostDao,
) {
    /** Observable feed from Room, ordered by created_at DESC. */
    fun cachedFeed(limit: Int = CACHE_LIMIT): Flow<List<Post>> =
        postDao.feedFlow(limit).map { entities -> entities.map { it.toModel() } }

    /** True if there are any cached posts at all. Cheap one-shot check. */
    suspend fun hasCachedPosts(): Boolean =
        postDao.feedSnapshot(limit = 1).isNotEmpty()

    /**
     * Fetch the first page from the network, replace the cache, and return the next cursor.
     * Returns null cursor if there are no more pages.
     */
    suspend fun revalidate(pageSize: Int = PAGE_SIZE): String? {
        val page = wallClient.listFeed(cursor = null, limit = pageSize)
        // Replace cache with fresh first-page data.
        // We clear old posts and insert the fresh batch so stale entries don't linger.
        postDao.deleteAll()
        postDao.upsertAll(page.items.map { PostEntity.from(it) })
        return page.nextCursor
    }

    /**
     * Fetch the next page from the network and append to the cache.
     * Returns the next cursor (null = no more pages).
     */
    suspend fun loadMore(cursor: String, pageSize: Int = PAGE_SIZE): PageResult {
        val page = wallClient.listFeed(cursor = cursor, limit = pageSize)
        postDao.upsertAll(page.items.map { PostEntity.from(it) })
        return PageResult(
            newItems = page.items,
            nextCursor = page.nextCursor,
        )
    }

    /** Insert a newly-created post immediately so the feed updates when returning from composer. */
    suspend fun cachePost(post: Post) {
        postDao.upsertAll(listOf(PostEntity.from(post)))
    }

    suspend fun deletePost(postId: Long) {
        wallClient.deletePost(postId)
        postDao.deleteById(postId)
    }

    /**
     * Optimistic like/unlike: update Room immediately, fire network call, rollback on failure.
     */
    suspend fun toggleLike(postId: Long): LikeResult {
        val cached = postDao.getById(postId) ?: return LikeResult.NotFound
        val nowLiked = !cached.viewerLiked
        val newCount = if (nowLiked) cached.likeCount + 1 else (cached.likeCount - 1).coerceAtLeast(0)

        // Optimistic local update
        postDao.updateLikeState(postId, newCount, nowLiked)

        return try {
            if (nowLiked) wallClient.likePost(postId) else wallClient.unlikePost(postId)
            LikeResult.Success(nowLiked)
        } catch (_: Exception) {
            // Rollback
            postDao.updateLikeState(postId, cached.likeCount, cached.viewerLiked)
            LikeResult.RolledBack
        }
    }

    data class PageResult(
        val newItems: List<Post>,
        val nextCursor: String?,
    )

    sealed interface LikeResult {
        data class Success(val liked: Boolean) : LikeResult
        data object NotFound : LikeResult
        data object RolledBack : LikeResult
    }

    companion object {
        const val PAGE_SIZE = 20
        const val CACHE_LIMIT = 100
    }
}
