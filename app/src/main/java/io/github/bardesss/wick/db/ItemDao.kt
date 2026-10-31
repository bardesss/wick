package io.github.bardesss.wick.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

data class LocalState(val id: String, val read: Boolean, val manualUnread: Boolean)

private const val CHUNK = 500

@Dao
abstract class ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    protected abstract suspend fun insertAll(items: List<ItemEntity>)

    @Query("SELECT id, read, manualUnread FROM ItemEntity WHERE id IN (:ids)")
    protected abstract suspend fun localState(ids: List<String>): List<LocalState>

    @Query("SELECT itemId FROM PendingMarkEntity WHERE itemId IN (:ids)")
    protected abstract suspend fun pendingAmong(ids: List<String>): List<String>

    @Query(
        "SELECT i.*, f.title AS feedTitle, f.iconUrl AS feedIconUrl FROM ItemEntity i " +
            "LEFT JOIN FeedEntity f ON f.id = i.feedId WHERE i.id IN (:ids)",
    )
    protected abstract fun rowsFor(ids: List<String>): Flow<List<ItemRow>>

    @Query("UPDATE ItemEntity SET read = :read, manualUnread = :manualUnread WHERE id = :id")
    abstract suspend fun setRead(id: String, read: Boolean, manualUnread: Boolean)

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

    @Query(
        "SELECT id FROM ItemEntity WHERE read = 1 " +
            "AND id NOT IN (SELECT itemId FROM PendingMarkEntity)",
    )
    protected abstract suspend fun prunable(): List<String>

    @Query("DELETE FROM ItemEntity WHERE id IN (:ids)")
    protected abstract suspend fun deleteByIds(ids: List<String>)

    @Transaction
    open suspend fun upsertKeepingLocal(items: List<ItemEntity>) {
        val ids = items.map { it.id }
        val existing = ids.chunked(CHUNK).flatMap { localState(it) }.associateBy { it.id }
        val pending = ids.chunked(CHUNK).flatMap { pendingAmong(it) }.toSet()
        val merged = items.map { incoming ->
            val local = existing[incoming.id] ?: return@map incoming
            if (incoming.id in pending) {
                incoming.copy(read = local.read, manualUnread = local.manualUnread)
            } else {
                incoming.copy(manualUnread = local.manualUnread)
            }
        }
        merged.chunked(CHUNK).forEach { insertAll(it) }
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

    open suspend fun unreadIds(feedIds: List<String>?, oldestFirst: Boolean): List<String> =
        if (feedIds == null) unreadAll(oldestFirst) else unreadIn(feedIds, oldestFirst)
}
