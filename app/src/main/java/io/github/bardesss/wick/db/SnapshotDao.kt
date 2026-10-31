package io.github.bardesss.wick.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The item ids the list shows, in order. Only an explicit refresh replaces them. */
@Dao
abstract class SnapshotDao {
    @Query("DELETE FROM SnapshotEntity")
    protected abstract suspend fun clear()

    @Insert
    protected abstract suspend fun insert(rows: List<SnapshotEntity>)

    @Query("SELECT itemId FROM SnapshotEntity ORDER BY pos")
    abstract fun snapshotIds(): Flow<List<String>>

    @Transaction
    open suspend fun replaceSnapshot(ids: List<String>) {
        clear()
        ids.mapIndexed { pos, id -> SnapshotEntity(pos, id) }.chunked(500).forEach { insert(it) }
    }
}
