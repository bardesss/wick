package io.github.bardesss.wick.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class QueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun enqueue(mark: PendingMarkEntity)

    @Query("SELECT * FROM PendingMarkEntity ORDER BY queuedAt, itemId")
    abstract suspend fun pending(): List<PendingMarkEntity>

    @Query("DELETE FROM PendingMarkEntity WHERE itemId IN (:ids)")
    protected abstract suspend fun deleteChunk(ids: List<String>)

    @Insert
    abstract suspend fun enqueueAll(m: PendingMarkAllEntity)

    @Query("SELECT * FROM PendingMarkAllEntity ORDER BY id")
    abstract suspend fun pendingAll(): List<PendingMarkAllEntity>

    @Query("DELETE FROM PendingMarkAllEntity WHERE id = :id")
    abstract suspend fun deleteAll(id: Long)

    @Query("SELECT * FROM PendingMarkEntity")
    abstract fun pendingFlow(): Flow<List<PendingMarkEntity>>

    @Query("SELECT * FROM PendingMarkAllEntity ORDER BY id")
    abstract fun pendingAllFlow(): Flow<List<PendingMarkAllEntity>>

    @Query("SELECT * FROM PendingMarkEntity WHERE itemId = :id")
    protected abstract suspend fun find(id: String): PendingMarkEntity?

    @Query("DELETE FROM PendingMarkEntity WHERE itemId = :id")
    protected abstract suspend fun deleteOne(id: String)

    @Query("DELETE FROM PendingMarkEntity WHERE itemId = :id AND queuedAt = :queuedAt")
    protected abstract suspend fun deleteIfQueuedAt(id: String, queuedAt: Long)

    /** Drops pending unread marks for items that are now read locally (a mark-all covered them). */
    @Query(
        "DELETE FROM PendingMarkEntity WHERE read = 0 " +
            "AND itemId IN (SELECT id FROM ItemEntity WHERE read = 1)",
    )
    abstract suspend fun dropUnreadMarksForReadItems()

    /** A mark that reverses a pending one cancels it, otherwise the newest mark replaces the old one. */
    @Transaction
    open suspend fun enqueueOrCancel(mark: PendingMarkEntity) {
        val existing = find(mark.itemId)
        if (existing != null && existing.read != mark.read) deleteOne(mark.itemId) else enqueue(mark)
    }

    /** Deletes the given marks unless they were re-queued since they were read. */
    @Transaction
    open suspend fun deleteUnchanged(marks: List<PendingMarkEntity>) {
        marks.forEach { deleteIfQueuedAt(it.itemId, it.queuedAt) }
    }

    @Transaction
    open suspend fun delete(ids: List<String>) {
        ids.chunked(500).forEach { deleteChunk(it) }
    }
}
