package io.ente.entegram.core.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PostEntity::class,
        CommentEntity::class,
        FollowRequestEntity::class,
        WallKeyEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun postDao(): PostDao
    abstract fun commentDao(): CommentDao
    abstract fun followRequestDao(): FollowRequestDao
    abstract fun wallKeyDao(): WallKeyDao
}
