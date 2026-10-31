package io.github.bardesss.wick.sync

import android.util.Log
import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.db.ItemDao
import io.github.bardesss.wick.db.PendingMarkAllEntity
import io.github.bardesss.wick.db.PendingMarkEntity
import io.github.bardesss.wick.db.QueueDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "Wick"
private const val BATCH = 100
private const val DEBOUNCE_MS = 3_000L
private const val OP_ATTEMPTS = 3
private const val OP_RETRY_MS = 1_000L

/**
 * Writes read state to Room at once and sends it to the server later. Calls to [mark] and
 * [markAll] are applied in call order and restart a 3 s timer that triggers [flush].
 */
class MarkQueue(
    private val api: ReaderApi,
    private val queueDao: QueueDao,
    private val itemDao: ItemDao,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    /** Runs the block in one database transaction (the app passes `db.withTransaction`). */
    private val inTransaction: suspend (suspend () -> Unit) -> Unit = { it() },
) {
    private val ops = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val flushLock = Mutex()
    private var debounce: Job? = null
    private var lastStamp = 0L

    // Ids in the snapshot of the flush that is running. stateLock makes "read the queue and
    // register it" and "check the registry and write a mark" atomic against each other.
    private val stateLock = Mutex()
    private val inFlight = HashSet<String>()

    init {
        scope.launch {
            for (op in ops) {
                var attempt = 0
                while (true) {
                    try {
                        op()
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // keep the loop alive; retry the same op a few times before dropping it
                        if (++attempt >= OP_ATTEMPTS) {
                            Log.e(TAG, "dropping a read mark after $OP_ATTEMPTS failed attempts", e)
                            break
                        }
                        delay(OP_RETRY_MS)
                    }
                }
                restartTimer()
            }
        }
    }

    fun mark(id: String, read: Boolean, manual: Boolean) {
        ops.trySend {
            stateLock.withLock {
                inTransaction {
                    itemDao.setRead(id, read, manualUnread = manual && !read)
                    queueDao.enqueueOrCancel(PendingMarkEntity(id, read, nextStamp()), inFlight = id in inFlight)
                }
            }
        }
    }

    fun markAll(stream: StreamId, feedIds: List<String>?, newestLoadedSec: Long) {
        ops.trySend {
            inTransaction {
                itemDao.markReadWhere(feedIds, newestLoadedSec)
                queueDao.dropUnreadMarksForReadItems()
                queueDao.enqueueAll(
                    PendingMarkAllEntity(streamRaw = stream.raw, olderThanUsec = newestLoadedSec * 1_000_000),
                )
            }
        }
    }

    /** Returns once every [mark] and [markAll] made before this call has been applied to Room. */
    suspend fun awaitApplied() {
        val done = CompletableDeferred<Unit>()
        ops.trySend { done.complete(Unit) }
        done.await()
    }

    /** Sends what is queued. Returns true when nothing is left in the queue. */
    suspend fun flush(): Boolean = flushLock.withLock { flushLocked() }

    /**
     * Flushes, then runs [block] while no other flush can start. Used to keep a fetch and its
     * database write from interleaving with a flush that would change what the fetch saw.
     */
    suspend fun <T> exclusive(block: suspend () -> T): T = flushLock.withLock {
        flushLocked()
        block()
    }

    /** Runs [block] while no flush or fetch can run, without flushing first. */
    suspend fun <T> quiesced(block: suspend () -> T): T = flushLock.withLock { block() }

    private suspend fun flushLocked(): Boolean {
        var clean = true

        // Mark-all first, so an item mark made after it still wins on the server.
        for (m in queueDao.pendingAll()) {
            try {
                api.markAllRead(StreamId.parse(m.streamRaw), m.olderThanUsec)
                queueDao.completeMarkAll(m)
            } catch (e: ApiError) {
                if (e.stopsFlush()) return false
                clean = false
            }
        }
        // A mark-all that is still queued would be resent later and undo newer item marks.
        if (!clean) return false

        val marks = stateLock.withLock {
            queueDao.pending().also { snapshot -> inFlight.addAll(snapshot.map { it.itemId }) }
        }
        try {
            for (read in listOf(true, false)) {
                for (batch in marks.filter { it.read == read }.chunked(BATCH)) {
                    try {
                        api.setRead(batch.map { it.itemId }, read)
                        stateLock.withLock { queueDao.completeSent(batch) }
                    } catch (e: ApiError) {
                        if (e.stopsFlush()) return false
                        clean = false
                    }
                }
            }
        } finally {
            stateLock.withLock { inFlight.clear() }
        }
        return clean && queueDao.pending().isEmpty() && queueDao.pendingAll().isEmpty()
    }

    private fun ApiError.stopsFlush() = this == ApiError.Unreachable || this == ApiError.BadCredentials

    private fun nextStamp(): Long {
        lastStamp = maxOf(clock(), lastStamp + 1)
        return lastStamp
    }

    private fun restartTimer() {
        debounce?.cancel()
        debounce = scope.launch {
            delay(DEBOUNCE_MS)
            // a new mark must not cancel a flush that is already talking to the server
            withContext(NonCancellable) {
                try {
                    flush()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "background flush failed", e)
                }
            }
        }
    }
}
