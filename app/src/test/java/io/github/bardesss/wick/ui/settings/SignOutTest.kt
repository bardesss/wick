package io.github.bardesss.wick.ui.settings

import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.data.MemoryPreferences
import io.github.bardesss.wick.data.SettingsStore
import io.github.bardesss.wick.db.CountEntity
import io.github.bardesss.wick.db.FeedEntity
import io.github.bardesss.wick.db.ItemEntity
import io.github.bardesss.wick.db.WickDb
import io.github.bardesss.wick.sync.FakeReaderApi
import io.github.bardesss.wick.sync.inMemoryDb
import io.github.bardesss.wick.sync.newQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SignOutTest {
    private class MemoryCreds(start: Auth?) : CredentialStore {
        private val flow = MutableStateFlow(start)
        override fun auth() = flow.value
        override val authFlow: StateFlow<Auth?> = flow
        override suspend fun save(auth: Auth) { flow.value = auth }
        override suspend fun clear() { flow.value = null }
    }

    private lateinit var db: WickDb
    private val settings = SettingsStore(MemoryPreferences())
    private val api = FakeReaderApi()
    private val creds = MemoryCreds(Auth("https://feeds.example.com", "tok"))

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun seed() {
        db.feedDao().replaceAll(listOf(FeedEntity("feed/1", "News", null, null, null)))
        db.feedDao().replaceCounts(listOf(CountEntity("feed/1", 1)))
        db.itemDao().upsertKeepingLocal(listOf(ItemEntity("a", "feed/1", "T a", null, 100, null, read = false)))
        db.snapshotDao().replaceSnapshot(listOf("a"), writtenAtMs = 5)
    }

    @Test
    fun sign_out_clears_everything() = runTest(timeout = 30.seconds) {
        seed()
        val queue = db.newQueue(api, backgroundScope)
        queue.mark("a", read = true, manual = false)
        queue.awaitApplied()
        var imagesCleared = false

        signOut(queue, creds, wipe = { wipeAccount(queue, db, settings) { imagesCleared = true } })

        // the queued mark was sent before anything was wiped
        assertEquals(listOf(listOf("a") to true), api.setReadCalls)
        assertNull(creds.auth())
        assertTrue(db.feedDao().feedsNow().isEmpty())
        assertTrue(db.feedDao().counts().first().isEmpty())
        assertTrue(db.snapshotDao().snapshotIds().first().isEmpty())
        assertNull(db.snapshotDao().writtenAt())
        assertTrue(db.itemDao().byIds(listOf("a")).first().isEmpty())
        assertTrue(db.queueDao().pending().isEmpty())
        assertTrue(imagesCleared)
    }

    @Test
    fun sign_out_does_not_wait_for_a_stuck_flush() = runTest(timeout = 30.seconds) {
        seed()
        val queue = db.newQueue(api, backgroundScope)
        queue.mark("a", read = true, manual = false)
        queue.awaitApplied()
        api.beforeSetRead = { awaitCancellation() }
        var imagesCleared = false

        signOut(queue, creds, wipe = { wipeAccount(queue, db, settings) { imagesCleared = true } })

        assertNull(creds.auth())
        assertTrue(db.feedDao().feedsNow().isEmpty())
        assertTrue(imagesCleared)
    }

    @Test
    fun same_account_after_401_keeps_pending_marks() = runTest(timeout = 30.seconds) {
        seed()
        val queue = db.newQueue(api, backgroundScope)
        val switch = AccountSwitch(settings) { wipeAccount(queue, db, settings) {} }
        switch.enter("https://feeds.example.com\nme")
        queue.mark("a", read = true, manual = false)
        queue.awaitApplied()

        // the server rejected the key; the user signs in again with a new one
        creds.clear()
        switch.enter("https://feeds.example.com\nme")

        assertEquals(listOf("a"), db.queueDao().pending().map { it.itemId })
        assertEquals(1, db.feedDao().feedsNow().size)
    }

    @Test
    fun different_account_wipes_previous_data() = runTest(timeout = 30.seconds) {
        seed()
        val queue = db.newQueue(api, backgroundScope)
        var imagesCleared = false
        val switch = AccountSwitch(settings) { wipeAccount(queue, db, settings) { imagesCleared = true } }
        switch.enter("https://feeds.example.com\nme")
        queue.mark("a", read = true, manual = false)
        queue.awaitApplied()

        switch.enter("https://feeds.example.com\nsomeone")

        assertTrue(db.queueDao().pending().isEmpty())
        assertTrue(db.feedDao().feedsNow().isEmpty())
        assertTrue(db.itemDao().byIds(listOf("a")).first().isEmpty())
        assertTrue(imagesCleared)
        assertEquals("https://feeds.example.com\nsomeone", settings.account())
    }

    @Test
    fun wipe_resets_the_last_view_for_the_next_account() = runTest(timeout = 30.seconds) {
        seed()
        val queue = db.newQueue(api, backgroundScope)
        settings.setLastView("feed/1")

        wipeAccount(queue, db, settings) {}

        assertNull(settings.lastView())
    }

    @Test
    fun sign_out_clears_credentials_even_when_the_wipe_fails() = runTest(timeout = 30.seconds) {
        val queue = db.newQueue(api, backgroundScope)

        signOut(queue, creds, wipe = { error("disk full") })

        assertNull(creds.auth())
    }
}
