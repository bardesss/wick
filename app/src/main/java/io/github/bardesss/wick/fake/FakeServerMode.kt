package io.github.bardesss.wick.fake

import io.github.bardesss.wick.AppContainer
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.api.Feed
import io.github.bardesss.wick.api.Item
import io.github.bardesss.wick.api.Page
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.api.UnreadCount
import io.github.bardesss.wick.db.CountEntity
import io.github.bardesss.wick.db.FeedEntity
import io.github.bardesss.wick.db.ItemEntity
import java.util.Random

/**
 * Deterministic offline data for benchmarks. Only reachable when BuildConfig.FAKE_ENABLED is
 * true, which is every build type except release, so R8 drops this class from release builds.
 */
object FakeServerMode {
    const val EXTRA = "wick.fake"

    @Volatile
    var active = false
        private set

    private val words = (
        "rust kernel release notes patch compiler budget election climate battery chip sensor " +
            "memory language review benchmark cluster outage security update browser privacy open " +
            "source design layout font phone camera screen storage network protocol server cache " +
            "queue index parser engine record study paper result market launch delay merger"
        ).split(' ')

    private val feeds = listOf(
        Feed("feed/1", "Hacker Dispatch", "Tech", null, null),
        Feed("feed/2", "Kernel Weekly", "Tech", null, null),
        Feed("feed/3", "The Long Read", "News", null, null),
        Feed("feed/4", "Daily Wire Service", "News", null, null),
        Feed("feed/5", "Pixel Notes", null, null, null),
        Feed("feed/6", "Field Recordings", null, null, null),
    )

    private val items by lazy { build(500) }

    private fun build(n: Int): List<Item> {
        val rnd = Random(42)
        val now = System.currentTimeMillis() / 1000
        return List(n) { i ->
            val len = 3 + rnd.nextInt(14)
            val title = List(len) { words[rnd.nextInt(words.size)] }.joinToString(" ")
                .replaceFirstChar { it.uppercase() }
            Item(
                id = "fake/$i",
                feedId = feeds[rnd.nextInt(feeds.size)].id,
                title = title,
                url = "https://example.invalid/post/$i",
                publishedSec = now - i * 900L,
                html = "<p>${title}. " + List(20 + rnd.nextInt(60)) { words[rnd.nextInt(words.size)] }.joinToString(" ") + ".</p>",
            )
        }
    }

    /** Replaces the stored data with [count] fake items, a stored snapshot and a signed in account. */
    suspend fun enable(container: AppContainer, count: Int) {
        active = true
        val db = container.db
        db.clearAllTables()
        container.creds.save(Auth("https://fake.invalid", "fake"))
        val list = items.take(count)
        container.feedDao.replaceAll(feeds.map { FeedEntity(it.id, it.title, it.folder, it.htmlUrl, it.iconUrl) })
        container.feedDao.replaceCounts(countsOf(list))
        container.itemDao.upsertKeepingLocal(
            list.mapIndexed { i, it ->
                ItemEntity(it.id, it.feedId, it.title, it.url, it.publishedSec, it.html, read = i % 7 == 3)
            },
        )
        container.snapshotDao.replaceSnapshot(list.map { it.id }, System.currentTimeMillis())
    }

    private fun countsOf(list: List<Item>): List<CountEntity> {
        val perFeed = list.groupingBy { it.feedId }.eachCount()
        return perFeed.map { CountEntity(it.key, it.value) } + CountEntity(StreamId.All.raw, list.size)
    }

    /** Serves the same items the store was seeded with, so a refresh changes nothing. */
    class Api : ReaderApi {
        override suspend fun login(server: String, user: String, apiKey: String) = "fake"
        override suspend fun feeds() = feeds
        override suspend fun unreadCounts(): List<UnreadCount> =
            items.groupingBy { it.feedId }.eachCount().map { UnreadCount(it.key, it.value) }

        override suspend fun unread(stream: StreamId, oldestFirst: Boolean, max: Int, continuation: String?) =
            Page(items, null)

        override suspend fun setRead(itemIds: List<String>, read: Boolean) = Unit
        override suspend fun markAllRead(stream: StreamId, olderThanUsec: Long) = Unit
    }
}
