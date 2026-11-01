package io.github.bardesss.wick.sync

import io.github.bardesss.wick.data.SettingsStore
import io.github.bardesss.wick.db.WickDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Empties the local copy of an account: every table and the cached images. Runs with no sync
 * or flush in progress, after the queue has applied what it was given. [beforeRelease] runs
 * last, still under that lock.
 */
suspend fun wipeAccount(
    queue: MarkQueue,
    db: WickDb,
    settings: SettingsStore,
    beforeRelease: suspend () -> Unit = {},
    clearImages: () -> Unit,
) {
    queue.quiesced {
        queue.awaitApplied()
        withContext(Dispatchers.IO) {
            db.clearAllTables()
            clearImages()
        }
        // the next account must not open on this one's folder or feed, or find its folders folded
        settings.setLastView(null)
        settings.clearCollapsed()
        // sync must not find credentials once it can run again
        beforeRelease()
    }
    // an operation that was still on its way must not land in the fresh database
    queue.awaitApplied()
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
