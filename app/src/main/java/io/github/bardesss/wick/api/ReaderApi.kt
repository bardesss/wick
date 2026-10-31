package io.github.bardesss.wick.api

data class Item(
    val id: String,
    val feedId: String,
    val title: String,
    val url: String?,
    val publishedSec: Long,
    val html: String?,
)

data class Feed(
    val id: String,
    val title: String,
    val folder: String?,
    val htmlUrl: String?,
    val iconUrl: String?,
)

data class UnreadCount(val streamId: String, val count: Int)

data class Page(val items: List<Item>, val continuation: String?)

interface ReaderApi {
    /** Returns the auth token. */
    suspend fun login(server: String, user: String, apiKey: String): String
    suspend fun feeds(): List<Feed>
    suspend fun unreadCounts(): List<UnreadCount>
    suspend fun unread(stream: StreamId, oldestFirst: Boolean, max: Int, continuation: String?): Page
    suspend fun setRead(itemIds: List<String>, read: Boolean)
    suspend fun markAllRead(stream: StreamId, olderThanUsec: Long)
}
