package io.github.bardesss.wick.sync

import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.db.ItemEntity
import io.github.bardesss.wick.db.WickDb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
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
class MarkQueueTest {
    private lateinit var db: WickDb
    private val api = FakeReaderApi()

    @Before
    fun setUp() {
        db = inMemoryDb()
        api.addFeed("feed/1", "News")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun TestScope.queue() = MarkQueue(api, db.queueDao(), db.itemDao(), backgroundScope)

    private suspend fun seed(vararg ids: String, publishedSec: Long = 100) {
        db.itemDao().upsertKeepingLocal(
            ids.map { ItemEntity(it, "feed/1", "T $it", null, publishedSec, null, read = false) },
        )
        ids.forEach { api.addItem(it, "feed/1", publishedSec) }
    }

    private suspend fun row(id: String) = db.itemDao().byIds(listOf(id)).first().single()

    @Test
    fun mark_is_local_immediately_and_sent_after_flush() = runTest {
        seed("a", "b")
        val q = queue()

        q.mark("a", read = true, manual = false)
        q.mark("b", read = true, manual = false)
        runCurrent()

        assertTrue(row("a").read)
        assertTrue(api.setReadCalls.isEmpty())
        assertEquals(2, db.queueDao().pending().size)

        advanceTimeBy(2_900)
        assertTrue(api.setReadCalls.isEmpty())
        advanceTimeBy(200)
        runCurrent()

        assertEquals(setOf("a", "b"), api.readSet())
        assertEquals(1, api.setReadCalls.size)
        assertTrue(db.queueDao().pending().isEmpty())
    }

    @Test
    fun unreachable_keeps_queue_and_retries_later() = runTest {
        seed("a", "b")
        val q = queue()
        q.mark("a", read = true, manual = false)
        q.mark("b", read = true, manual = false)
        runCurrent()

        api.failNext(1)
        assertFalse(q.flush())
        assertEquals(2, db.queueDao().pending().size)
        assertTrue(api.readSet().isEmpty())
        assertTrue(row("a").read)

        assertTrue(q.flush())
        assertEquals(setOf("a", "b"), api.readSet())
        assertEquals(1, api.setReadCalls.size)
        assertEquals(listOf("a", "b"), api.setReadCalls.single().first.sorted())
        assertTrue(db.queueDao().pending().isEmpty())
    }

    @Test
    fun markAll_offline_applies_locally_and_sends_ts_cutoff() = runTest {
        seed("old", publishedSec = 100)
        seed("new", publishedSec = 500)
        val q = queue()

        api.failNext(1)
        q.markAll(StreamId.All, null, newestLoadedSec = 200)
        runCurrent()

        assertTrue(row("old").read)
        assertFalse(row("new").read)
        assertFalse(q.flush())
        assertEquals(1, db.queueDao().pendingAll().size)

        assertTrue(q.flush())
        assertEquals(listOf(StreamId.All.raw to 200_000_000L), api.markAllCalls)
        assertTrue(api.isRead("old"))
        assertFalse(api.isRead("new"))
        assertTrue(db.queueDao().pendingAll().isEmpty())
    }

    @Test
    fun reversal_cancels_pending_mark() = runTest {
        seed("a")
        val q = queue()

        q.mark("a", read = true, manual = false)
        q.mark("a", read = false, manual = true)
        runCurrent()

        assertTrue(db.queueDao().pending().isEmpty())
        val r = row("a")
        assertFalse(r.read)
        assertTrue(r.manualUnread)
        assertTrue(q.flush())
        assertTrue(api.setReadCalls.isEmpty())
    }

    @Test
    fun flush_batches_by_100() = runTest {
        val ids = (1..250).map { "i%03d".format(it) }
        seed(*ids.toTypedArray())
        val q = queue()
        ids.forEach { q.mark(it, read = true, manual = false) }
        runCurrent()

        assertTrue(q.flush())
        assertEquals(listOf(100, 100, 50), api.setReadCalls.map { it.first.size })
        assertEquals(ids.toSet(), api.readSet())
        assertTrue(db.queueDao().pending().isEmpty())
    }

    @Test
    fun bad_credentials_keeps_everything_and_returns_false() = runTest {
        seed("a")
        val q = queue()
        q.mark("a", read = true, manual = false)
        runCurrent()
        api.failNext(1, ApiError.BadCredentials)
        assertFalse(q.flush())
        assertEquals(1, db.queueDao().pending().size)
    }
}
