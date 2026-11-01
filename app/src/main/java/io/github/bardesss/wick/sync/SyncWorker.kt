package io.github.bardesss.wick.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.bardesss.wick.LOG_TAG
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.db.QueueDao
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

private const val KEY_FLUSH_ONLY = "flushOnly"

/**
 * Sends the queued marks and, unless [SyncWorker.input] asked for the flush alone, brings the
 * database up to date. It always reports success: a failed run is retried by the next
 * periodic one, so a dead server cannot cause a retry storm.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val auth: () -> Auth?,
    private val queue: () -> MarkQueue,
    private val engine: () -> SyncEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (auth() == null) return Result.success()
        try {
            if (inputData.getBoolean(KEY_FLUSH_ONLY, false)) {
                queue().flush()
            } else {
                // a failure here is an ApiError that the next run tries again
                engine().backgroundSync()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(LOG_TAG, "background sync failed", e)
        }
        return Result.success()
    }

    companion object {
        fun input(flushOnly: Boolean): Data = workDataOf(KEY_FLUSH_ONLY to flushOnly)
    }
}

/** Builds [SyncWorker]s from the app's objects; the objects are looked up only when a run starts. */
class SyncWorkerFactory(
    private val auth: () -> Auth?,
    private val queue: () -> MarkQueue,
    private val engine: () -> SyncEngine,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ) = if (workerClassName == SyncWorker::class.java.name) {
        SyncWorker(appContext, workerParameters, auth, queue, engine)
    } else {
        null
    }
}

object SyncWork {
    internal const val SYNC = "sync"
    internal const val FLUSH = "flush"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Runs the full sync every 60 minutes while there is a network. Calling it again keeps the existing job. */
    fun schedule(wm: WorkManager) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(60, TimeUnit.MINUTES)
            .setConstraints(online)
            .setInputData(SyncWorker.input(flushOnly = false))
            .build()
        wm.enqueueUniquePeriodicWork(SYNC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Queues a flush that outlives the process, but only when marks are waiting to be sent. */
    suspend fun enqueueFlushIfPending(wm: WorkManager, queueDao: QueueDao) {
        if (queueDao.pending().isEmpty() && queueDao.pendingAll().isEmpty()) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(online)
            .setInputData(SyncWorker.input(flushOnly = true))
            .build()
        wm.enqueueUniqueWork(FLUSH, ExistingWorkPolicy.REPLACE, request)
    }
}
