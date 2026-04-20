package io.ente.entegram.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.ente.entegram.core.models.FollowRequest
import kotlinx.coroutines.flow.Flow

@Dao
interface FollowRequestDao {

    @Query("SELECT * FROM follow_requests WHERE direction = :direction ORDER BY created_at DESC")
    fun byDirectionFlow(direction: FollowRequest.Direction): Flow<List<FollowRequestEntity>>

    @Query("SELECT * FROM follow_requests WHERE direction = :direction ORDER BY created_at DESC")
    suspend fun byDirectionSnapshot(direction: FollowRequest.Direction): List<FollowRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(requests: List<FollowRequestEntity>)

    @Query("DELETE FROM follow_requests WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM follow_requests WHERE direction = :direction")
    suspend fun deleteByDirection(direction: FollowRequest.Direction)

    @Query("DELETE FROM follow_requests")
    suspend fun deleteAll()
}
