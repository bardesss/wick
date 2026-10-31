package io.github.bardesss.wick.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(indices = [Index("feedId"), Index("publishedSec")])
data class ItemEntity(
    @PrimaryKey val id: String,
    val feedId: String,
    val title: String,
    val url: String?,
    val publishedSec: Long,
    val html: String?,
    val read: Boolean,
    val manualUnread: Boolean = false,
)

@Entity
data class FeedEntity(
    @PrimaryKey val id: String,
    val title: String,
    val folder: String?,
    val htmlUrl: String?,
    val iconUrl: String?,
)

@Entity
data class CountEntity(
    @PrimaryKey val streamId: String,
    val count: Int,
)

@Entity
data class PendingMarkEntity(
    @PrimaryKey val itemId: String,
    val read: Boolean,
    val queuedAt: Long,
)

@Entity
data class PendingMarkAllEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val streamRaw: String,
    val olderThanUsec: Long,
)

/** One row of the list snapshot: the item at position [pos]. */
@Entity
data class SnapshotEntity(
    @PrimaryKey val pos: Int,
    val itemId: String,
)

/** A single row (id 0) holding when the snapshot was last written and which view it belongs to. */
@Entity
data class SnapshotMetaEntity(
    @PrimaryKey val id: Int = 0,
    val writtenAtMs: Long,
    val viewRaw: String = "user/-/state/com.google/reading-list",
)

data class ItemRow(
    val id: String,
    val feedId: String,
    val title: String,
    val url: String?,
    val publishedSec: Long,
    val html: String?,
    val read: Boolean,
    val manualUnread: Boolean,
    val feedTitle: String?,
    val feedIconUrl: String?,
)
