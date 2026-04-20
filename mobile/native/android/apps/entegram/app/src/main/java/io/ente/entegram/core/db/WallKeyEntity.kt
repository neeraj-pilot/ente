package io.ente.entegram.core.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * Cached wall encryption key metadata. The actual key bytes are stored in
 * EncryptedSharedPreferences (hardware-backed); this table tracks which
 * walls we hold keys for and at which version, so the app can decide
 * whether to re-fetch a key from the server without hitting the network.
 */
@Entity(
    tableName = "wall_keys",
    indices = [Index("wall_id", unique = true)],
)
data class WallKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "wall_id") val wallId: String,
    @ColumnInfo(name = "key_version") val keyVersion: Int,
    @ColumnInfo(name = "cached_at") val cachedAt: Instant = Instant.now(),
)
