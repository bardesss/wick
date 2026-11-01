package io.github.bardesss.wick.ui.settings

import android.util.Log
import io.github.bardesss.wick.LOG_TAG
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.sync.MarkQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sends what is queued, if the server answers within [flushTimeoutMs], then wipes the account
 * and forgets it, and clears the credentials last.
 */
suspend fun signOut(
    queue: MarkQueue,
    creds: CredentialStore,
    wipe: suspend () -> Unit,
    forgetAccount: suspend () -> Unit = {},
    flushTimeoutMs: Long = 2_000,
) {
    try {
        withTimeoutOrNull(flushTimeoutMs) {
            queue.awaitApplied()
            queue.flush()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(LOG_TAG, "could not send the queue before signing out", e)
    }
    try {
        wipe()
        forgetAccount()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(LOG_TAG, "could not clear the local data while signing out", e)
    } finally {
        // the credentials go even when the rest failed, and even when this was cancelled
        withContext(NonCancellable) { creds.clear() }
    }
}
