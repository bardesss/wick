package io.github.bardesss.wick.sync

import androidx.room.withTransaction
import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.Feed
import io.github.bardesss.wick.api.Item
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.api.UnreadCount
import io.github.bardesss.wick.db.CountEntity
import io.github.bardesss.wick.db.FeedEntity
import io.github.bardesss.wick.db.ItemEntity
import io.github.bardesss.wick.db.WickDb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

private const val PAGE_SIZE = 1000
/** The most rows a snapshot holds. */
const val MAX_ITEMS = 1000

private class Fetched(
    val feeds: List<Feed>,
    val counts: List<UnreadCount>,
    val items: List<Item>,
    /** False when the item cap cut the listing short. */
    val complete: Boolean,
)

class SyncEngine(
    private val api: ReaderApi,
    private val db: WickDb,
    private val queue: MarkQueue,
) {
    /**
     * Flushes the queue, fetches [view] and replaces the local copy. Returns the ids of the
     * new snapshot in server order. On failure the database is left as it was.
     */
    suspend fun refresh(view: StreamId, oldestFirst: Boolean): Result<List<String>> =
        queue.exclusive {
            val fetched = try {
                fetch(view, oldestFirst)
            } catch (e: ApiError) {
                return@exclusive Result.failure(e)
            }
            val ids = fetched.items.map { it.id }
            db.withTransaction {
                store(fetched)
                if (fetched.complete) {
                    db.itemDao().markMissingRead(feedIdsOf(view, fetched.feeds), ids.toSet())
                }
                db.itemDao().clearManualUnread()
                db.itemDao().pruneReadNotIn(ids.toSet())
            }
            Result.success(ids)
        }

    /** Like [refresh] for the whole account, but it only adds and updates rows. */
    suspend fun backgroundSync(): Result<Unit> =
        queue.exclusive {
            val fetched = try {
                fetch(StreamId.All, oldestFirst = false)
            } catch (e: ApiError) {
                return@exclusive Result.failure(e)
            }
            db.withTransaction { store(fetched) }
            Result.success(Unit)
        }

    /**
     * Unread counts by raw stream id. A feed shows the server count minus pending read marks
     * plus pending unread marks, or 0 when a pending mark-all covers it. Folders and the
     * reading list are the sums of their feeds.
     */
    fun displayedCounts(): Flow<Map<String, Int>> =
        combine(
            db.feedDao().all(),
            db.feedDao().counts(),
            db.queueDao().pendingFlow(),
            db.queueDao().pendingAllFlow(),
        ) { feeds, counts, marks, markAlls ->
            val feedOf = db.itemDao().feedIdsOf(marks.map { it.itemId })
            val delta = HashMap<String, Int>()
            for (m in marks) {
                val feed = feedOf[m.itemId] ?: continue
                delta[feed] = (delta[feed] ?: 0) + if (m.read) -1 else 1
            }
            val covered = HashSet<String>()
            for (m in markAlls) {
                when (val s = StreamId.parse(m.streamRaw)) {
                    StreamId.All -> feeds.forEach { covered += it.id }
                    is StreamId.Folder -> feeds.filter { it.folder == s.name }.forEach { covered += it.id }
                    is StreamId.Feed -> covered += s.id
                }
            }
            val perFeed = feeds.associate { f ->
                f.id to if (f.id in covered) 0 else maxOf(0, (counts[f.id] ?: 0) + (delta[f.id] ?: 0))
            }
            val result = HashMap<String, Int>(perFeed)
            feeds.mapNotNull { it.folder }.distinct().forEach { name ->
                result[StreamId.Folder(name).raw] = feeds.filter { it.folder == name }.sumOf { perFeed.getValue(it.id) }
            }
            result[StreamId.All.raw] = perFeed.values.sum()
            result
        }

    private suspend fun fetch(view: StreamId, oldestFirst: Boolean): Fetched {
        val feeds = api.feeds()
        val counts = api.unreadCounts()
        val items = LinkedHashMap<String, Item>()
        var continuation: String? = null
        do {
            val page = api.unread(view, oldestFirst, PAGE_SIZE, continuation)
            page.items.forEach { items.putIfAbsent(it.id, it) }
            continuation = page.continuation
        } while (continuation != null && items.size < MAX_ITEMS)
        val list = items.values.toList().take(MAX_ITEMS)
        return Fetched(feeds, counts, list, complete = continuation == null)
    }

    private suspend fun store(f: Fetched) {
        db.feedDao().replaceAll(f.feeds.map { FeedEntity(it.id, it.title, it.folder, it.htmlUrl, it.iconUrl) })
        db.feedDao().replaceCounts(f.counts.map { CountEntity(it.streamId, it.count) })
        db.itemDao().upsertKeepingLocal(
            f.items.map {
                ItemEntity(
                    id = it.id,
                    feedId = it.feedId,
                    title = it.title,
                    url = it.url,
                    publishedSec = it.publishedSec,
                    html = it.html,
                    read = false,
                )
            },
        )
    }

    private fun feedIdsOf(view: StreamId, feeds: List<Feed>): List<String>? = when (view) {
        StreamId.All -> null
        is StreamId.Folder -> feeds.filter { it.folder == view.name }.map { it.id }
        is StreamId.Feed -> listOf(view.id)
    }
}
