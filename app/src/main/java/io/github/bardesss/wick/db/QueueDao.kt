package io.github.bardesss.wick.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.github.bardesss.wick.api.StreamId
import kotlinx.coroutines.flow.Flow

@Dao
abstract class QueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun enqueue(mark: PendingMarkEntity)

    @Query("SELECT * FROM PendingMarkEntity ORDER BY queuedAt, itemId")
    abstract suspend fun pending(): List<PendingMarkEntity>

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
    protected abstract suspend fun deleteIfQueuedAt(id: String, queuedAt: Long): Int

    @Query(
        "UPDATE CountEntity SET count = MAX(0, count + :delta) " +
            "WHERE streamId = (SELECT feedId FROM ItemEntity WHERE id = :itemId)",
    )
    protected abstract suspend fun adjustFeedCountOf(itemId: String, delta: Int)

    @Query("UPDATE CountEntity SET count = 0 WHERE streamId IN (SELECT id FROM FeedEntity)")
    protected abstract suspend fun zeroAllFeedCounts()

    @Query("UPDATE CountEntity SET count = 0 WHERE streamId IN (SELECT id FROM FeedEntity WHERE folder = :folder)")
    protected abstract suspend fun zeroFolderFeedCounts(folder: String)

    @Query("UPDATE CountEntity SET count = 0 WHERE streamId = :feedId")
    protected abstract suspend fun zeroFeedCount(feedId: String)

    /** Drops pending unread marks for items that are now read locally (a mark-all covered them). */
    @Query(
        "DELETE FROM PendingMarkEntity WHERE read = 0 " +
            "AND itemId IN (SELECT id FROM ItemEntity WHERE read = 1)",
    )
    abstract suspend fun dropUnreadMarksForReadItems()

    @Query(
        "DELETE FROM PendingMarkEntity WHERE read = 1 AND itemId IN " +
            "(SELECT id FROM ItemEntity WHERE publishedSec <= :publishedAtOrBeforeSec)",
    )
    protected abstract suspend fun dropReadMarksUpTo(publishedAtOrBeforeSec: Long)

    @Query(
        "DELETE FROM PendingMarkEntity WHERE read = 1 AND itemId IN " +
            "(SELECT id FROM ItemEntity WHERE feedId IN (:feedIds) AND publishedSec <= :publishedAtOrBeforeSec)",
    )
    protected abstract suspend fun dropReadMarksOfFeedsUpTo(feedIds: List<String>, publishedAtOrBeforeSec: Long)

    /**
     * Drops pending read marks for the items a mark-all covers (same scope and cutoff as
     * [ItemDao.markReadWhere]), so a later unread mark on one of them is queued, not cancelled.
     */
    @Transaction
    open suspend fun dropReadMarksCoveredBy(feedIds: List<String>?, publishedAtOrBeforeSec: Long) {
        if (feedIds == null) {
            dropReadMarksUpTo(publishedAtOrBeforeSec)
        } else {
            feedIds.chunked(CHUNK).forEach { dropReadMarksOfFeedsUpTo(it, publishedAtOrBeforeSec) }
        }
    }

    /**
     * A mark that reverses a pending one cancels it, otherwise the newest mark replaces the old
     * one. A pending mark that is being sent right now ([inFlight]) or was sent before (attempted)
     * is never cancelled, because the server may already have it.
     */
    @Transaction
    open suspend fun enqueueOrCancel(mark: PendingMarkEntity, inFlight: Boolean) {
        val existing = find(mark.itemId)
        if (existing != null && existing.read != mark.read && !existing.attempted && !inFlight) {
            deleteOne(mark.itemId)
        } else {
            enqueue(mark.copy(attempted = existing?.attempted ?: false))
        }
    }

    /** Flags marks whose send is about to start. */
    @Transaction
    open suspend fun markAttempted(ids: List<String>) {
        ids.chunked(CHUNK).forEach { markAttemptedChunk(it) }
    }

    @Query("UPDATE PendingMarkEntity SET attempted = 1 WHERE itemId IN (:ids)")
    protected abstract suspend fun markAttemptedChunk(ids: List<String>)

    /**
     * Records that the server accepted [marks]: removes the rows that were not re-queued since,
     * and moves the stored feed counts by the same amount so the displayed count stays put.
     */
    @Transaction
    open suspend fun completeSent(marks: List<PendingMarkEntity>) {
        for (m in marks) {
            val removed = deleteIfQueuedAt(m.itemId, m.queuedAt) > 0
            val reversedSince = !removed && find(m.itemId)?.let { it.read != m.read } == true
            if (removed || reversedSince) adjustFeedCountOf(m.itemId, if (m.read) -1 else 1)
        }
    }

    /** Removes a sent mark-all and zeroes the stored counts of the feeds it covered. */
    @Transaction
    open suspend fun completeMarkAll(m: PendingMarkAllEntity) {
        deleteAll(m.id)
        when (val s = StreamId.parse(m.streamRaw)) {
            StreamId.All -> zeroAllFeedCounts()
            StreamId.Read -> Unit
            is StreamId.Folder -> zeroFolderFeedCounts(s.name)
            is StreamId.Feed -> zeroFeedCount(s.id)
        }
    }
}
