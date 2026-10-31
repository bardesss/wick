package io.github.bardesss.wick.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.db.ItemEntity
import io.github.bardesss.wick.db.PendingMarkAllEntity
import io.github.bardesss.wick.db.WickDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    private lateinit var context: Context
    private lateinit var db: WickDb
    private val api = FakeReaderApi()
    private var auth: Auth? = Auth("https://rss.example", "token")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = inMemoryDb()
        api.addFeed("feed/1", "News")
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun runWorker(flushOnly: Boolean = false): ListenableWorker.Result {
        val queue = db.newQueue(api, CoroutineScope(Job()))
        val factory = SyncWorkerFactory(
            auth = { auth },
            queue = { queue },
            engine = { SyncEngine(api, db, queue) },
        )
        val worker = TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(SyncWorker.input(flushOnly))
            .build()
        return worker.doWork()
    }

    private suspend fun seedPendingRead() {
        db.itemDao().upsertKeepingLocal(
            listOf(ItemEntity("a", "feed/1", "T a", null, 100, null, read = false)),
        )
        api.addItem("a", "feed/1", 100)
        val q = db.newQueue(api, CoroutineScope(Job()))
        q.mark("a", read = true, manual = false)
        q.awaitApplied()
    }

    @Test
    fun unreachable_returns_success_without_retry_storm() = runTest {
        api.failNext(10, ApiError.Unreachable)

        assertEquals(ListenableWorker.Result.success(), runWorker())
    }

    @Test
    fun other_api_errors_also_return_success() = runTest {
        for (e in listOf(ApiError.BadCredentials, ApiError.Malformed, ApiError.Http(500))) {
            api.failNext(10, e)
            assertEquals(ListenableWorker.Result.success(), runWorker())
        }
    }

    @Test
    fun flushes_then_syncs() = runTest {
        seedPendingRead()
        api.addItem("b", "feed/1", 200)
        assertTrue(api.readSet().isEmpty())

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertEquals(setOf("a"), api.readSet())
        assertTrue(db.queueDao().pending().isEmpty())
        val stored = db.itemDao().byIds(listOf("a", "b")).first().map { it.id }.toSet()
        assertEquals(setOf("a", "b"), stored)
    }

    @Test
    fun signed_out_does_nothing() = runTest {
        seedPendingRead()
        auth = null
        api.addItem("b", "feed/1", 200)

        assertEquals(ListenableWorker.Result.success(), runWorker())

        assertTrue(api.readSet().isEmpty())
        assertEquals(1, db.queueDao().pending().size)
        assertTrue(db.itemDao().byIds(listOf("b")).first().isEmpty())
    }

    @Test
    fun flush_only_sends_marks_without_fetching() = runTest {
        seedPendingRead()
        api.addItem("b", "feed/1", 200)

        assertEquals(ListenableWorker.Result.success(), runWorker(flushOnly = true))

        assertEquals(setOf("a"), api.readSet())
        assertTrue(db.itemDao().byIds(listOf("b")).first().isEmpty())
    }

    @Test
    fun flush_job_enqueued_only_with_pending_marks() = runTest {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val wm = WorkManager.getInstance(context)

        SyncWork.enqueueFlushIfPending(wm, db.queueDao())
        assertTrue(wm.getWorkInfosForUniqueWork(SyncWork.FLUSH).get().isEmpty())

        seedPendingRead()
        SyncWork.enqueueFlushIfPending(wm, db.queueDao())
        val infos = wm.getWorkInfosForUniqueWork(SyncWork.FLUSH).get()
        assertEquals(1, infos.size)
        assertEquals(WorkInfo.State.ENQUEUED, infos.single().state)
    }

    @Test
    fun flush_job_enqueued_for_pending_mark_all() = runTest {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val wm = WorkManager.getInstance(context)
        db.queueDao().enqueueAll(PendingMarkAllEntity(streamRaw = "user/-/state/com.google/reading-list", olderThanUsec = 1))

        SyncWork.enqueueFlushIfPending(wm, db.queueDao())

        assertEquals(1, wm.getWorkInfosForUniqueWork(SyncWork.FLUSH).get().size)
    }

    @Test
    fun periodic_sync_is_unique_hourly_and_needs_network() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().build())
        val wm = WorkManager.getInstance(context)

        SyncWork.schedule(wm)
        SyncWork.schedule(wm)

        val infos = wm.getWorkInfosForUniqueWork(SyncWork.SYNC).get()
        assertEquals(1, infos.size)
        val c = infos.single().constraints
        assertEquals(androidx.work.NetworkType.CONNECTED, c.requiredNetworkType)
    }
}
