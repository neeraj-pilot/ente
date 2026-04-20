package io.ente.entegram.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CommentDao {

    @Query("SELECT * FROM comments WHERE post_id = :postId ORDER BY created_at ASC")
    fun forPostFlow(postId: Long): Flow<List<CommentEntity>>

    @Query("SELECT * FROM comments WHERE post_id = :postId ORDER BY created_at ASC")
    suspend fun forPostSnapshot(postId: Long): List<CommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(comments: List<CommentEntity>)

    @Query("DELETE FROM comments WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM comments WHERE post_id = :postId")
    suspend fun deleteByPostId(postId: Long)

    @Query("DELETE FROM comments")
    suspend fun deleteAll()
}
