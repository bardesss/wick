package io.github.bardesss.wick.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ItemEntity::class,
        FeedEntity::class,
        CountEntity::class,
        PendingMarkEntity::class,
        PendingMarkAllEntity::class,
        SnapshotEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class WickDb : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun feedDao(): FeedDao
    abstract fun queueDao(): QueueDao
    abstract fun snapshotDao(): SnapshotDao
}
