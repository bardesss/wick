package io.github.bardesss.wick.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import io.github.bardesss.wick.api.StreamId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class LocalState(val id: String, val read: Boolean, val manualUnread: Boolean)

data class ItemFeed(val id: String, val feedId: String)

private const val CHUNK = 500

/** The items a pending mark-all marks read: of [feeds] (all when null) and published by [newestSec]. */
private class MarkAllCover(val feeds: Set<String>?, val newestSec: Long) {
    fun covers(item: ItemEntity) = (feeds == null || item.feedId in feeds) && item.publishedSec <= newestSec
}

@Dao
abstract class ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(items: List<ItemEntity>)

    @Query("SELECT id, read, manualUnread FROM ItemEntity WHERE id IN (:ids)")
    protected abstract suspend fun localState(ids: List<String>): List<LocalState>

    @Query("SELECT itemId FROM PendingMarkEntity WHERE itemId IN (:ids)")
    protected abstract suspend fun pendingAmong(ids: List<String>): List<String>

    @Query("SELECT id FROM ItemEntity WHERE read = 1 AND id IN (:ids)")
    protected abstract suspend fun readChunk(ids: List<String>): List<String>

    @Query("SELECT * FROM PendingMarkAllEntity")
    protected abstract suspend fun pendingMarkAlls(): List<PendingMarkAllEntity>

    @Query("SELECT id FROM FeedEntity WHERE folder = :folder")
    protected abstract suspend fun feedsInFolder(folder: String): List<String>

    @Query(
        "SELECT i.*, f.title AS feedTitle, f.iconUrl AS feedIconUrl FROM ItemEntity i " +
            "LEFT JOIN FeedEntity f ON f.id = i.feedId WHERE i.id IN (:ids)",
    )
    protected abstract fun rowsFor(ids: List<String>): Flow<List<ItemRow>>

    @Query("UPDATE ItemEntity SET read = :read, manualUnread = :manualUnread WHERE id = :id")
    abstract suspend fun setRead(id: String, read: Boolean, manualUnread: Boolean)

    @Query("SELECT MAX(publishedSec) FROM ItemEntity WHERE read = 0")
    protected abstract suspend fun newestUnreadAll(): Long?

    @Query("SELECT MAX(publishedSec) FROM ItemEntity WHERE read = 0 AND feedId IN (:feedIds)")
    protected abstract suspend fun newestUnreadIn(feedIds: List<String>): Long?

    @Query("UPDATE ItemEntity SET read = 1, manualUnread = 0 WHERE publishedSec <= :cutoff")
    protected abstract suspend fun markAllReadUpTo(cutoff: Long)

    @Query(
        "UPDATE ItemEntity SET read = 1, manualUnread = 0 " +
            "WHERE publishedSec <= :cutoff AND feedId IN (:feedIds)",
    )
    protected abstract suspend fun markFeedsReadUpTo(feedIds: List<String>, cutoff: Long)

    @Query(
        "SELECT id FROM ItemEntity WHERE read = 0 ORDER BY " +
            "CASE WHEN :oldestFirst = 1 THEN publishedSec END ASC, " +
            "CASE WHEN :oldestFirst = 0 THEN publishedSec END DESC, id ASC",
    )
    protected abstract suspend fun unreadAll(oldestFirst: Boolean): List<String>

    @Query(
        "SELECT id FROM ItemEntity WHERE read = 0 AND feedId IN (:feedIds) ORDER BY " +
            "CASE WHEN :oldestFirst = 1 THEN publishedSec END ASC, " +
            "CASE WHEN :oldestFirst = 0 THEN publishedSec END DESC, id ASC",
    )
    protected abstract suspend fun unreadIn(feedIds: List<String>, oldestFirst: Boolean): List<String>

    // rows in the list snapshot stay until the snapshot is replaced
    @Query(
        "SELECT id FROM ItemEntity WHERE read = 1 " +
            "AND id NOT IN (SELECT itemId FROM PendingMarkEntity) " +
            "AND id NOT IN (SELECT itemId FROM SnapshotEntity)",
    )
    protected abstract suspend fun prunable(): List<String>

    @Query("DELETE FROM ItemEntity WHERE id IN (:ids)")
    protected abstract suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT id, feedId FROM ItemEntity WHERE id IN (:ids)")
    protected abstract suspend fun feedIdsChunk(ids: List<String>): List<ItemFeed>

    @Query("UPDATE ItemEntity SET read = 1, manualUnread = 0 WHERE id IN (:ids)")
    protected abstract suspend fun markReadByIds(ids: List<String>)

    @Query("UPDATE ItemEntity SET manualUnread = 0 WHERE manualUnread = 1")
    abstract suspend fun clearManualUnread()

    @Query("SELECT html FROM ItemEntity WHERE id = :id")
    abstract suspend fun htmlOf(id: String): String?

    /** Which of [ids] are read locally. */
    suspend fun readAmong(ids: List<String>): Set<String> = ids.chunked(CHUNK).flatMapTo(HashSet()) { readChunk(it) }

    suspend fun feedIdsOf(ids: List<String>): Map<String, String> =
        ids.chunked(CHUNK).flatMap { feedIdsChunk(it) }.associate { it.id to it.feedId }

    /** Marks local unread rows of [feedIds] (all feeds when null) read unless they are in [keep] or have a pending mark. */
    @Transaction
    open suspend fun markMissingRead(feedIds: List<String>?, keep: Set<String>) {
        val candidates = unreadIds(feedIds, oldestFirst = false).filter { it !in keep }
        val pending = candidates.chunked(CHUNK).flatMap { pendingAmong(it) }.toSet()
        candidates.filter { it !in pending }.chunked(CHUNK).forEach { markReadByIds(it) }
    }

    /**
     * Writes [items] from the server but keeps what the server has not been told yet: an item with
     * a pending mark keeps its local state, and one that a pending mark-all covers stays read.
     */
    @Transaction
    open suspend fun upsertKeepingLocal(items: List<ItemEntity>) {
        val ids = items.map { it.id }
        val existing = ids.chunked(CHUNK).flatMap { localState(it) }.associateBy { it.id }
        val pending = ids.chunked(CHUNK).flatMap { pendingAmong(it) }.toSet()
        val covers = markAllCovers()
        val merged = items.map { incoming ->
            val local = existing[incoming.id]
            when {
                incoming.id in pending && local != null ->
                    incoming.copy(read = local.read, manualUnread = local.manualUnread)
                // later item marks are pending ones, handled above, so the mark-all decides the rest
                covers.any { it.covers(incoming) } -> incoming.copy(read = true, manualUnread = false)
                local != null -> incoming.copy(manualUnread = local.manualUnread)
                else -> incoming
            }
        }
        merged.chunked(CHUNK).forEach { insertAll(it) }
    }

    // the same rule markReadWhere applied when the mark-all was made: in scope and published at
    // or before the newest second it covered, which is the cutoff minus one second
    private suspend fun markAllCovers(): List<MarkAllCover> = pendingMarkAlls().map { m ->
        val feeds = when (val s = StreamId.parse(m.streamRaw)) {
            StreamId.All -> null
            is StreamId.Folder -> feedsInFolder(s.name).toSet()
            is StreamId.Feed -> setOf(s.id)
        }
        MarkAllCover(feeds, m.olderThanUsec / 1_000_000 - 1)
    }

    fun byIds(ids: List<String>): Flow<List<ItemRow>> {
        if (ids.isEmpty()) return flowOf(emptyList())
        val chunks = ids.chunked(CHUNK).map { rowsFor(it) }
        return combine(chunks) { parts ->
            val byId = parts.flatMap { it }.associateBy { it.id }
            ids.mapNotNull { byId[it] }
        }
    }

    @Transaction
    open suspend fun markReadWhere(feedIds: List<String>?, publishedAtOrBeforeSec: Long) {
        if (feedIds == null) {
            markAllReadUpTo(publishedAtOrBeforeSec)
        } else {
            feedIds.chunked(CHUNK).forEach { markFeedsReadUpTo(it, publishedAtOrBeforeSec) }
        }
    }

    @Transaction
    open suspend fun pruneReadNotIn(keep: Set<String>) {
        prunable().filter { it !in keep }.chunked(CHUNK).forEach { deleteByIds(it) }
    }

    /** The publish time of the newest unread item of [feedIds] (all feeds when null), or null when none. */
    open suspend fun newestUnreadSec(feedIds: List<String>?): Long? =
        if (feedIds == null) newestUnreadAll() else feedIds.chunked(CHUNK).mapNotNull { newestUnreadIn(it) }.maxOrNull()

    open suspend fun unreadIds(feedIds: List<String>?, oldestFirst: Boolean): List<String> =
        if (feedIds == null) unreadAll(oldestFirst) else unreadIn(feedIds, oldestFirst)
}
