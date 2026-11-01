package io.github.bardesss.wick.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.bardesss.wick.sync.storedItems
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ItemDaoTest {
    private lateinit var db: WickDb
    private lateinit var items: ItemDao
    private lateinit var feeds: FeedDao
    private lateinit var queue: QueueDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WickDb::class.java)
            .allowMainThreadQueries()
            .build()
        items = db.itemDao()
        feeds = db.feedDao()
        queue = db.queueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun item(
        id: String,
        feedId: String = "f1",
        publishedSec: Long = 100,
        read: Boolean = false,
        manualUnread: Boolean = false,
        title: String = "Title $id",
    ) = ItemEntity(
        id = id,
        feedId = feedId,
        title = title,
        url = null,
        publishedSec = publishedSec,
        html = null,
        read = read,
        manualUnread = manualUnread,
    )

    private fun row(id: String) = db.storedItems(listOf(id)).single()

    @Test
    fun upsert_preserves_pending_local_state() = runTest {
        items.upsertKeepingLocal(listOf(item("a"), item("b")))
        items.setRead("a", read = true, manualUnread = false)
        items.setRead("b", read = false, manualUnread = true)
        queue.enqueue(PendingMarkEntity("a", read = true, queuedAt = 1))
        queue.enqueue(PendingMarkEntity("b", read = false, queuedAt = 2))

        items.upsertKeepingLocal(
            listOf(
                item("a", read = false, title = "New A"),
                item("b", read = true, title = "New B"),
            ),
        )

        val a = row("a")
        assertTrue(a.read)
        assertEquals("New A", a.title)
        val b = row("b")
        assertFalse(b.read)
        assertTrue(b.manualUnread)
        assertEquals("New B", b.title)
    }

    @Test
    fun upsert_keeps_items_covered_by_a_pending_mark_all_read() = runTest {
        feeds.replaceAll(
            listOf(
                FeedEntity("f1", "One", "News", null),
                FeedEntity("f2", "Two", null, null),
            ),
        )
        items.upsertKeepingLocal(listOf(item("a", publishedSec = 100), item("late", publishedSec = 300), item("other", feedId = "f2")))
        // mark all of the folder up to 200, as MarkQueue does it, and the server never hears of it
        items.markReadWhere(listOf("f1"), 200)
        queue.enqueueAll(PendingMarkAllEntity(streamRaw = "user/-/label/News", olderThanUsec = 201 * 1_000_000L))

        items.upsertKeepingLocal(
            listOf(
                item("a", publishedSec = 100),
                item("new", publishedSec = 150),
                item("edge", publishedSec = 200),
                item("late", publishedSec = 300),
                item("other", feedId = "f2", publishedSec = 100),
            ),
        )

        assertTrue(row("a").read)
        assertTrue(row("new").read)
        assertTrue(row("edge").read)
        assertFalse(row("late").read)
        assertFalse(row("other").read)
    }

    @Test
    fun a_pending_item_mark_after_a_mark_all_wins_on_upsert() = runTest {
        items.upsertKeepingLocal(listOf(item("a", publishedSec = 100)))
        items.markReadWhere(null, 200)
        queue.enqueueAll(PendingMarkAllEntity(streamRaw = "user/-/state/com.google/reading-list", olderThanUsec = 201 * 1_000_000L))
        items.setRead("a", read = false, manualUnread = true)
        queue.enqueue(PendingMarkEntity("a", read = false, queuedAt = 1))

        items.upsertKeepingLocal(listOf(item("a", publishedSec = 100)))

        assertFalse(row("a").read)
        assertTrue(row("a").manualUnread)
    }

    @Test
    fun unreadIds_order_respects_oldestFirst() = runTest {
        items.upsertKeepingLocal(
            listOf(
                item("c", publishedSec = 300),
                item("a", publishedSec = 100),
                item("b", publishedSec = 200),
                item("d", publishedSec = 200),
                item("r", publishedSec = 50, read = true),
            ),
        )
        assertEquals(listOf("a", "b", "d", "c"), items.unreadIds(null, oldestFirst = true))
        assertEquals(listOf("c", "b", "d", "a"), items.unreadIds(null, oldestFirst = false))
        assertEquals(listOf("a", "b"), items.unreadIds(listOf("f1"), oldestFirst = true).take(2))
    }

    @Test
    fun markReadWhere_respects_cutoff_and_feeds() = runTest {
        items.upsertKeepingLocal(
            listOf(
                item("old1", feedId = "f1", publishedSec = 100),
                item("new1", feedId = "f1", publishedSec = 300),
                item("old2", feedId = "f2", publishedSec = 100, manualUnread = true),
            ),
        )

        items.markReadWhere(listOf("f1"), 200)
        assertEquals(listOf("new1", "old2"), items.unreadIds(null, oldestFirst = false).sortedBy { it })

        items.markReadWhere(null, 200)
        assertEquals(listOf("new1"), items.unreadIds(null, oldestFirst = true))
        assertFalse(row("old2").manualUnread)
    }

    @Test
    fun queue_last_action_wins() = runTest {
        queue.enqueue(PendingMarkEntity("a", read = true, queuedAt = 1))
        queue.enqueue(PendingMarkEntity("a", read = false, queuedAt = 2))
        queue.enqueue(PendingMarkEntity("b", read = true, queuedAt = 3))

        val pending = queue.pending().sortedBy { it.itemId }
        assertEquals(listOf("a", "b"), pending.map { it.itemId })
        assertFalse(pending[0].read)
        assertEquals(2L, pending[0].queuedAt)

        queue.enqueueAll(PendingMarkAllEntity(streamRaw = "user/-/state/com.google/reading-list", olderThanUsec = 5))
        val all = queue.pendingAll()
        assertEquals(1, all.size)
        queue.deleteAll(all.single().id)
        assertTrue(queue.pendingAll().isEmpty())
    }

    @Test
    fun prune_keeps_snapshot_rows() = runTest {
        items.upsertKeepingLocal(
            listOf(
                item("keepRead", read = true),
                item("dropRead", read = true),
                item("pendingRead", read = true),
                item("unread"),
            ),
        )
        queue.enqueue(PendingMarkEntity("pendingRead", read = true, queuedAt = 1))

        items.pruneReadNotIn(setOf("keepRead"))

        val left = db.storedItems(listOf("keepRead", "dropRead", "pendingRead", "unread")).map { it.id }
        assertEquals(listOf("keepRead", "pendingRead", "unread"), left)
    }

    @Test
    fun readAmong_handles_more_than_sqlite_variable_limit() = runTest {
        val ids = (1..1500).map { "id$it" }
        items.upsertKeepingLocal(ids.mapIndexed { n, id -> item(id, read = n % 2 == 0) })
        assertEquals(ids.filterIndexed { n, _ -> n % 2 == 0 }.toSet(), items.readAmong(ids))
    }

    @Test
    fun counts_are_keyed_by_stream_id() = runTest {
        feeds.replaceCounts(listOf(CountEntity("feed/1", 3), CountEntity("feed/2", 0)))
        feeds.replaceCounts(listOf(CountEntity("feed/1", 4)))
        assertEquals(mapOf("feed/1" to 4), feeds.counts().first())
    }
}
