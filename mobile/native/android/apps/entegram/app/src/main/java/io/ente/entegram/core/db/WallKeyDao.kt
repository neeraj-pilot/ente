package io.ente.entegram.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WallKeyDao {

    @Query("SELECT * FROM wall_keys WHERE wall_id = :wallId")
    suspend fun getByWallId(wallId: String): WallKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: WallKeyEntity)

    @Query("DELETE FROM wall_keys WHERE wall_id = :wallId")
    suspend fun deleteByWallId(wallId: String)

    @Query("DELETE FROM wall_keys")
    suspend fun deleteAll()
}
