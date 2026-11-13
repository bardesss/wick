package io.github.bardesss.wick.db

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.DeleteColumn
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec

@Database(
    entities = [
        ItemEntity::class,
        FeedEntity::class,
        CountEntity::class,
        PendingMarkEntity::class,
        PendingMarkAllEntity::class,
        SnapshotEntity::class,
        SnapshotMetaEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // the queue of read marks lives here too, so every change migrates instead of starting over
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = WickDb.DropFeedSite::class),
        // version 3 adds when an item was read here, for the recently read view
        AutoMigration(from = 2, to = 3),
        // version 4 notes which queued marks a send has started, so a reversal is never cancelled after that
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class WickDb : RoomDatabase() {
    /** Version 2 drops the feed's site address, which nothing read. */
    @DeleteColumn(tableName = "FeedEntity", columnName = "htmlUrl")
    class DropFeedSite : AutoMigrationSpec

    abstract fun itemDao(): ItemDao
    abstract fun feedDao(): FeedDao
    abstract fun queueDao(): QueueDao
    abstract fun snapshotDao(): SnapshotDao
}
