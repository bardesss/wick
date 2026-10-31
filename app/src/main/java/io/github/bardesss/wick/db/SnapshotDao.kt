package io.github.bardesss.wick.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** What a list row shows. Read state is queried on its own, see [SnapshotDao.states]. */
data class SnapshotRow(
    val id: String,
    val title: String,
    val url: String?,
    val publishedSec: Long,
    val feedTitle: String?,
    val feedIconUrl: String?,
)

/** The item ids the list shows, in order. Only an explicit refresh replaces them. */
@Dao
abstract class SnapshotDao {
    @Query("DELETE FROM SnapshotEntity")
    protected abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insert(rows: List<SnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun setMeta(meta: SnapshotMetaEntity)

    @Query("SELECT itemId FROM SnapshotEntity ORDER BY pos")
    abstract fun snapshotIds(): Flow<List<String>>

    /** When the snapshot was last replaced with server data, or null if never. */
    @Query("SELECT writtenAtMs FROM SnapshotMetaEntity WHERE id = 0")
    abstract suspend fun writtenAt(): Long?

    @Query(
        "SELECT i.id, i.title, i.url, i.publishedSec, f.title AS feedTitle, f.iconUrl AS feedIconUrl " +
            "FROM SnapshotEntity s JOIN ItemEntity i ON i.id = s.itemId " +
            "LEFT JOIN FeedEntity f ON f.id = i.feedId ORDER BY s.pos",
    )
    abstract suspend fun rowsNow(): List<SnapshotRow>

    @Query("SELECT i.id, i.read, i.manualUnread FROM SnapshotEntity s JOIN ItemEntity i ON i.id = s.itemId")
    abstract fun states(): Flow<List<LocalState>>

    /** Replaces the ids and records [writtenAtMs]; 0 marks a list that did not come from the server. */
    @Transaction
    open suspend fun replaceSnapshot(ids: List<String>, writtenAtMs: Long) {
        clear()
        ids.mapIndexed { pos, id -> SnapshotEntity(pos, id) }.chunked(500).forEach { insert(it) }
        setMeta(SnapshotMetaEntity(writtenAtMs = writtenAtMs))
    }
}
