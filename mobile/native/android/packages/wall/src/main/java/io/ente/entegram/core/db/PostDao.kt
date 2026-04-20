package io.ente.entegram.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {

    @Query("SELECT * FROM posts ORDER BY created_at DESC LIMIT :limit")
    fun feedFlow(limit: Int = 50): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts ORDER BY created_at DESC LIMIT :limit")
    suspend fun feedSnapshot(limit: Int = 50): List<PostEntity>

    @Query("SELECT * FROM posts WHERE wall_id = :wallId ORDER BY created_at DESC")
    fun wallPostsFlow(wallId: String): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE id = :postId")
    suspend fun getById(postId: Long): PostEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(post: PostEntity)

    @Query("UPDATE posts SET like_count = :likeCount, viewer_liked = :viewerLiked WHERE id = :postId")
    suspend fun updateLikeState(postId: Long, likeCount: Int, viewerLiked: Boolean)

    @Query("UPDATE posts SET comment_count = :commentCount WHERE id = :postId")
    suspend fun updateCommentCount(postId: Long, commentCount: Int)

    @Query("DELETE FROM posts WHERE id = :postId")
    suspend fun deleteById(postId: Long)

    @Query("DELETE FROM posts")
    suspend fun deleteAll()

    @Query("SELECT MIN(cached_at) FROM posts")
    suspend fun oldestCacheTimestamp(): Long?
}
