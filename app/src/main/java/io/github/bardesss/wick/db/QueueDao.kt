package io.github.bardesss.wick.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

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

    @Transaction
    open suspend fun delete(ids: List<String>) {
        ids.chunked(500).forEach { deleteChunk(it) }
    }
}
