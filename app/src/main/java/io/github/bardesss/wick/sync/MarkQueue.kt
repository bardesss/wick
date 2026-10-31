package io.github.bardesss.wick.sync

import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.db.ItemDao
import io.github.bardesss.wick.db.PendingMarkAllEntity
import io.github.bardesss.wick.db.PendingMarkEntity
import io.github.bardesss.wick.db.QueueDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val BATCH = 100
private const val DEBOUNCE_MS = 3_000L

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
) {
    private val ops = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val flushLock = Mutex()
    private var debounce: Job? = null
    private var lastStamp = 0L

    init {
        scope.launch {
            for (op in ops) {
                op()
                restartTimer()
            }
        }
    }

    fun mark(id: String, read: Boolean, manual: Boolean) {
        ops.trySend {
            itemDao.setRead(id, read, manualUnread = manual && !read)
            queueDao.enqueueOrCancel(PendingMarkEntity(id, read, nextStamp()))
        }
    }

    fun markAll(stream: StreamId, feedIds: List<String>?, newestLoadedSec: Long) {
        ops.trySend {
            itemDao.markReadWhere(feedIds, newestLoadedSec)
            queueDao.dropUnreadMarksForReadItems()
            queueDao.enqueueAll(PendingMarkAllEntity(streamRaw = stream.raw, olderThanUsec = newestLoadedSec * 1_000_000))
        }
    }

    /** Sends what is queued. Returns true when nothing is left in the queue. */
    suspend fun flush(): Boolean = flushLock.withLock {
        var clean = true

        // Mark-all first, so an item mark made after it still wins on the server.
        for (m in queueDao.pendingAll()) {
            try {
                api.markAllRead(StreamId.parse(m.streamRaw), m.olderThanUsec)
                queueDao.deleteAll(m.id)
            } catch (e: ApiError) {
                if (e.stopsFlush()) return false
                clean = false
            }
        }

        val marks = queueDao.pending()
        for (read in listOf(true, false)) {
            for (batch in marks.filter { it.read == read }.chunked(BATCH)) {
                try {
                    api.setRead(batch.map { it.itemId }, read)
                    queueDao.deleteUnchanged(batch)
                } catch (e: ApiError) {
                    if (e.stopsFlush()) return false
                    clean = false
                }
            }
        }
        clean && queueDao.pending().isEmpty() && queueDao.pendingAll().isEmpty()
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
            withContext(NonCancellable) { flush() }
        }
    }
}
