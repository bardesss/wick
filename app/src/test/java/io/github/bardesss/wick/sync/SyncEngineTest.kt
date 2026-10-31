package io.github.bardesss.wick.sync

import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.db.ItemEntity
import io.github.bardesss.wick.db.PendingMarkAllEntity
import io.github.bardesss.wick.db.PendingMarkEntity
import io.github.bardesss.wick.db.WickDb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SyncEngineTest {
    private lateinit var db: WickDb
    private val api = FakeReaderApi()

    @Before
    fun setUp() {
        db = inMemoryDb()
        api.addFeed("feed/1", "News")
        api.addFeed("feed/2", "News")
        api.addFeed("feed/3", null)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun TestScope.engine(): SyncEngine {
        val q = MarkQueue(api, db.queueDao(), db.itemDao(), backgroundScope)
        return SyncEngine(api, db, q)
    }

    private fun local(id: String, feed: String, sec: Long, read: Boolean = false) =
        ItemEntity(id, feed, "T $id", null, sec, null, read)

    private suspend fun row(id: String) = db.itemDao().byIds(listOf(id)).first().singleOrNull()

    @Test
    fun refresh_server_wins_without_pending_change() = runTest {
        api.addItem("a", "feed/1", 100)
        db.itemDao().upsertKeepingLocal(listOf(local("a", "feed/1", 100, read = true)))
        val engine = engine()

        val ids = engine.refresh(StreamId.All, oldestFirst = false).getOrThrow()

        assertEquals(listOf("a"), ids)
        assertFalse(row("a")!!.read)
    }

    @Test
    fun refresh_keeps_pending_local_state() = runTest {
        api.addItem("a", "feed/1", 100)
        api.addItem("b", "feed/1", 200)
        db.itemDao().upsertKeepingLocal(listOf(local("a", "feed/1", 100, read = true)))
        db.queueDao().enqueue(PendingMarkEntity("a", read = true, queuedAt = 1))
        val engine = engine()
        // the flush cannot reach the server, so the mark stays pending
        api.failNext(1)

        val ids = engine.refresh(StreamId.All, oldestFirst = false).getOrThrow()

        assertEquals(listOf("b", "a"), ids)
        assertTrue(row("a")!!.read)
        assertEquals(1, db.queueDao().pending().size)
    }

    @Test
    fun refresh_returns_snapshot_in_requested_order() = runTest {
        api.addItem("a", "feed/1", 100)
        api.addItem("b", "feed/2", 300)
        api.addItem("c", "feed/3", 200)
        val engine = engine()

        assertEquals(listOf("b", "c", "a"), engine.refresh(StreamId.All, oldestFirst = false).getOrThrow())
        assertEquals(listOf("a", "c", "b"), engine.refresh(StreamId.All, oldestFirst = true).getOrThrow())
        assertEquals(listOf("b", "a"), engine.refresh(StreamId.Folder("News"), oldestFirst = false).getOrThrow())
    }

    @Test
    fun displayed_counts_adjust_for_pending() = runTest {
        api.addItem("a", "feed/1", 100)
        api.addItem("b", "feed/1", 110)
        api.addItem("c", "feed/2", 120)
        api.addItem("d", "feed/3", 130)
        api.addItem("e", "feed/3", 140, read = true)
        val engine = engine()
        engine.refresh(StreamId.All, oldestFirst = false).getOrThrow()

        // read a (feed/1), unread e (feed/3)
        db.itemDao().setRead("a", read = true, manualUnread = false)
        db.queueDao().enqueue(PendingMarkEntity("a", read = true, queuedAt = 1))
        db.itemDao().upsertKeepingLocal(listOf(local("e", "feed/3", 140)))
        db.queueDao().enqueue(PendingMarkEntity("e", read = false, queuedAt = 2))

        val counts = engine.displayedCounts().first()

        assertEquals(1, counts["feed/1"])
        assertEquals(1, counts["feed/2"])
        assertEquals(2, counts["feed/3"])
        assertEquals(2, counts[StreamId.Folder("News").raw])
        assertEquals(4, counts[StreamId.All.raw])

        db.queueDao().enqueueAll(PendingMarkAllEntity(streamRaw = StreamId.Folder("News").raw, olderThanUsec = 1))
        val covered = engine.displayedCounts().first()
        assertEquals(0, covered["feed/1"])
        assertEquals(0, covered["feed/2"])
        assertEquals(0, covered[StreamId.Folder("News").raw])
        assertEquals(2, covered[StreamId.All.raw])
    }

    @Test
    fun count_stays_stable_after_flush() = runTest {
        api.addItem("a", "feed/1", 100)
        api.addItem("b", "feed/1", 110)
        api.addItem("c", "feed/2", 120)
        api.addItem("d", "feed/3", 130)
        api.addItem("e", "feed/3", 140, read = true)
        val q = MarkQueue(api, db.queueDao(), db.itemDao(), backgroundScope)
        val engine = SyncEngine(api, db, q)
        engine.refresh(StreamId.All, oldestFirst = false).getOrThrow()

        q.mark("a", read = true, manual = false)
        q.mark("d", read = true, manual = false)
        runCurrent()
        val before = engine.displayedCounts().first()
        assertEquals(1, before["feed/1"])
        assertEquals(0, before["feed/3"])

        assertTrue(q.flush())
        assertEquals(before, engine.displayedCounts().first())
        assertEquals(1, db.feedDao().counts().first()["feed/1"])

        // the server agrees with the adjusted counts
        engine.refresh(StreamId.All, oldestFirst = false).getOrThrow()
        assertEquals(before, engine.displayedCounts().first())

        q.markAll(StreamId.Folder("News"), listOf("feed/1", "feed/2"), newestLoadedSec = 1_000)
        runCurrent()
        assertTrue(q.flush())
        val after = engine.displayedCounts().first()
        assertEquals(0, after["feed/1"])
        assertEquals(0, after["feed/2"])
        assertEquals(0, db.feedDao().counts().first()["feed/2"])
        assertEquals(0, after[StreamId.All.raw])
    }

    @Test
    fun refresh_marks_missing_items_read_when_fetch_completes() = runTest {
        api.addItem("gone", "feed/1", 100, read = true)
        api.addItem("kept", "feed/1", 200)
        api.addItem("other", "feed/3", 50, read = true)
        db.itemDao().upsertKeepingLocal(
            listOf(
                local("gone", "feed/1", 100),
                local("kept", "feed/1", 200),
                local("pend", "feed/1", 150),
                local("other", "feed/3", 50),
            ),
        )
        db.queueDao().enqueue(PendingMarkEntity("pend", read = false, queuedAt = 1))
        val engine = engine()
        api.failNext(1)

        val ids = engine.refresh(StreamId.Folder("News"), oldestFirst = false).getOrThrow()

        assertEquals(listOf("kept"), ids)
        assertTrue(row("gone") == null)
        assertFalse(row("kept")!!.read)
        assertFalse(row("pend")!!.read)
        // feed/3 is outside the refreshed view
        assertFalse(row("other")!!.read)
    }

    @Test
    fun refresh_failure_leaves_db_untouched() = runTest {
        api.addItem("a", "feed/1", 100)
        val engine = engine()
        api.failNext(3)

        val result = engine.refresh(StreamId.All, oldestFirst = false)

        assertTrue(result.exceptionOrNull() is ApiError.Unreachable)
        assertTrue(db.feedDao().all().first().isEmpty())
        assertTrue(row("a") == null)
    }

    @Test
    fun backgroundSync_upserts_without_pruning_or_clearing_manual_unread() = runTest {
        api.addItem("a", "feed/1", 100)
        db.itemDao().upsertKeepingLocal(
            listOf(
                local("old", "feed/1", 50, read = true),
                local("a", "feed/1", 100).copy(manualUnread = true),
            ),
        )
        val engine = engine()

        engine.backgroundSync().getOrThrow()

        assertTrue(row("old") != null)
        assertTrue(row("a")!!.manualUnread)
        assertEquals(3, db.feedDao().all().first().size)
    }
}
