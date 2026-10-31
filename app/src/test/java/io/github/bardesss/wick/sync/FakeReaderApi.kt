package io.github.bardesss.wick.sync

import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.Feed
import io.github.bardesss.wick.api.Item
import io.github.bardesss.wick.api.Page
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.api.UnreadCount

/** In-memory server with a read flag per item. Failures are injected with [failNext]. */
class FakeReaderApi : ReaderApi {
    val feedList = mutableListOf<Feed>()
    private val serverItems = linkedMapOf<String, Item>()
    private val readIds = mutableSetOf<String>()

    private var failures = 0
    private var failWith: ApiError = ApiError.Unreachable

    val setReadCalls = mutableListOf<Pair<List<String>, Boolean>>()
    val markAllCalls = mutableListOf<Pair<String, Long>>()

    /** Runs inside setRead before the change is applied, so a test can pause a request. */
    var beforeSetRead: (suspend () -> Unit)? = null

    /** Runs inside unread before the listing is built, so a test can pause a fetch. */
    var beforeUnread: (suspend () -> Unit)? = null

    fun failNext(n: Int, error: ApiError = ApiError.Unreachable) {
        failures = n
        failWith = error
    }

    fun addFeed(id: String, folder: String? = null) {
        feedList += Feed(id, "Feed $id", folder, null, null)
    }

    fun addItem(id: String, feedId: String, publishedSec: Long, read: Boolean = false) {
        serverItems[id] = Item(id, feedId, "Title $id", null, publishedSec, null)
        if (read) readIds += id else readIds -= id
    }

    fun isRead(id: String) = id in readIds

    fun readSet(): Set<String> = readIds.toSet()

    private fun check() {
        if (failures > 0) {
            failures--
            throw failWith
        }
    }

    private fun inStream(item: Item, stream: StreamId): Boolean = when (stream) {
        StreamId.All -> true
        is StreamId.Folder -> feedList.firstOrNull { it.id == item.feedId }?.folder == stream.name
        is StreamId.Feed -> item.feedId == stream.id
    }

    override suspend fun login(server: String, user: String, apiKey: String): String = "token"

    override suspend fun feeds(): List<Feed> {
        check()
        return feedList.toList()
    }

    override suspend fun unreadCounts(): List<UnreadCount> {
        check()
        val unread = serverItems.values.filter { it.id !in readIds }
        val perFeed = feedList.map { f -> UnreadCount(f.id, unread.count { it.feedId == f.id }) }
        val folders = feedList.mapNotNull { it.folder }.distinct().map { name ->
            UnreadCount(StreamId.Folder(name).raw, unread.count { inStream(it, StreamId.Folder(name)) })
        }
        return perFeed + folders + UnreadCount(StreamId.All.raw, unread.size)
    }

    override suspend fun unread(stream: StreamId, oldestFirst: Boolean, max: Int, continuation: String?): Page {
        check()
        beforeUnread?.invoke()
        val sorted = serverItems.values
            .filter { it.id !in readIds && inStream(it, stream) }
            .sortedWith(
                if (oldestFirst) {
                    compareBy<Item>({ it.publishedSec }, { it.id })
                } else {
                    compareByDescending<Item> { it.publishedSec }.thenBy { it.id }
                },
            )
        val start = continuation?.toInt() ?: 0
        val end = minOf(start + max, sorted.size)
        return Page(sorted.subList(start, end), if (end < sorted.size) end.toString() else null)
    }

    override suspend fun setRead(itemIds: List<String>, read: Boolean) {
        check()
        beforeSetRead?.invoke()
        setReadCalls += itemIds.toList() to read
        if (read) readIds += itemIds else readIds -= itemIds.toSet()
    }

    override suspend fun markAllRead(stream: StreamId, olderThanUsec: Long) {
        check()
        markAllCalls += stream.raw to olderThanUsec
        serverItems.values
            .filter { inStream(it, stream) && it.publishedSec * 1_000_000 <= olderThanUsec }
            .forEach { readIds += it.id }
    }
}
