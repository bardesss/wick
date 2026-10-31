package io.github.bardesss.wick.ui.settings

import android.util.Log
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.db.WickDb
import io.github.bardesss.wick.sync.MarkQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Wick"

/**
 * Sends what is queued, if the server answers within [flushTimeoutMs], then forgets the account:
 * credentials, every table and the cached images.
 */
suspend fun signOut(
    queue: MarkQueue,
    creds: CredentialStore,
    db: WickDb,
    clearImages: () -> Unit,
    flushTimeoutMs: Long = 2_000,
) {
    try {
        withTimeoutOrNull(flushTimeoutMs) { queue.flush() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "could not send the queue before signing out", e)
    }
    creds.clear()
    withContext(Dispatchers.IO) {
        db.clearAllTables()
        clearImages()
    }
}
