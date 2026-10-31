package io.github.bardesss.wick.ui.settings

import android.util.Log
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.data.SettingsStore
import io.github.bardesss.wick.db.WickDb
import io.github.bardesss.wick.sync.MarkQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Wick"

/**
 * Empties the local copy of an account: every table and the cached images. Runs with no sync
 * or flush in progress, after the queue has applied what it was given.
 */
suspend fun wipeAccount(queue: MarkQueue, db: WickDb, clearImages: () -> Unit) {
    queue.quiesced {
        queue.awaitApplied()
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            clearImages()
        }
    }
    // an operation that was still on its way must not land in the fresh database
    queue.awaitApplied()
}

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
        Log.w(TAG, "could not send the queue before signing out", e)
    }
    wipe()
    forgetAccount()
    creds.clear()
}

/**
 * Tells a new key for the account that is already here from a different account. Another
 * account's data and queued marks are wiped before its credentials are saved; the same account
 * keeps everything, so marks made before a rejected key are sent with the new one.
 */
class AccountSwitch(
    private val settings: SettingsStore,
    private val wipe: suspend () -> Unit,
) {
    suspend fun enter(id: String) {
        val previous = settings.account()
        if (previous != null && previous != id) wipe()
        settings.setAccount(id)
    }
}
