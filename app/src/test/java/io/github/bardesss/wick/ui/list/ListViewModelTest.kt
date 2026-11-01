package io.github.bardesss.wick.ui.list

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.data.MemoryPreferences
import io.github.bardesss.wick.data.SettingsStore
import io.github.bardesss.wick.db.CountEntity
import io.github.bardesss.wick.db.FeedEntity
import io.github.bardesss.wick.db.ItemEntity
import io.github.bardesss.wick.db.WickDb
import io.github.bardesss.wick.sync.FakeReaderApi
import io.github.bardesss.wick.sync.MarkQueue
import io.github.bardesss.wick.sync.SyncEngine
import io.github.bardesss.wick.sync.inMemoryDb
import io.github.bardesss.wick.sync.newQueue
import io.github.bardesss.wick.ui.login.LoginMessages
import io.github.bardesss.wick.ui.picker.PickerEntry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ListViewModelTest {
    private class MemoryCreds(start: Auth?) : CredentialStore {
        private val flow = MutableStateFlow(start)
        override fun auth() = flow.value
        override val authFlow: StateFlow<Auth?> = flow
        override suspend fun save(auth: Auth) { flow.value = auth }
        override suspend fun clear() { flow.value = null }
    }

    private lateinit var db: WickDb
    private val api = FakeReaderApi()
    private val main = UnconfinedTestDispatcher()
    private val creds = MemoryCreds(Auth("https://feeds.example.com", "tok"))
    private val settings = SettingsStore(MemoryPreferences())
    private val stores = mutableListOf<ViewModelStore>()
    private var now = 1_000_000_000L
    private var fetches = 0
    private lateinit var queue: MarkQueue
    private lateinit var sync: SyncEngine

    @Before
    fun setUp() {
        Dispatchers.setMain(main)
        db = inMemoryDb()
        api.addFeed("feed/1", "News")
        api.addFeed("feed/2", null)
        api.beforeUnread = { fetches++ }
    }

    @After
    fun tearDown() {
        stores.forEach { it.clear() }
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.engine() {
        queue = db.newQueue(api, backgroundScope)
        sync = SyncEngine(api, db, queue)
    }

    private fun TestScope.vm(handle: SavedStateHandle = SavedStateHandle()): ListViewModel {
        val store = ViewModelStore().also { stores += it }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ListViewModel(
                savedState = handle,
                itemDao = db.itemDao(),
                feedDao = db.feedDao(),
                snapshotDao = db.snapshotDao(),
                sync = sync,
                queue = queue,
                settings = settings,
                creds = creds,
                appScope = backgroundScope,
                clock = { now },
                work = main,
            ) as T
        }
        return ViewModelProvider(store, factory)[ListViewModel::class.java]
    }

    /** Room delivers some changes on its own threads, so poll for a short while in real time. */
    private suspend fun <T> TestScope.settle(flow: StateFlow<T>, ok: (T) -> Boolean): T {
        repeat(150) {
            runCurrent()
            if (ok(flow.value)) return flow.value
            withContext(Dispatchers.IO) { Thread.sleep(10) }
        }
        return flow.value
    }

    private suspend fun TestScope.ids(vm: ListViewModel, expected: List<String>): List<String> =
        settle(vm.rows) { rows -> rows?.map { it.id } == expected }.orEmpty().map { it.id }

    /** Polls until [n] fetches have started, in real time like [settle]. */
    private suspend fun TestScope.awaitFetches(n: Int): Int {
        repeat(150) {
            runCurrent()
            if (fetches >= n) return fetches
            withContext(Dispatchers.IO) { Thread.sleep(10) }
        }
        return fetches
    }

    private fun seedServer() {
        api.addItem("a", "feed/1", 100)
        api.addItem("b", "feed/1", 200)
    }

    @Test
    fun refresh_replaces_snapshot() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        assertEquals(listOf("b", "a"), ids(vm, listOf("b", "a")))

        api.addItem("c", "feed/2", 300)
        vm.refresh()

        assertEquals(listOf("c", "b", "a"), ids(vm, listOf("c", "b", "a")))
        assertFalse(settle(vm.refreshing) { !it })
    }

    @Test
    fun snapshot_unchanged_by_background_sync() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))
        assertEquals(2, settle(vm.count) { it == 2 })

        api.addItem("c", "feed/2", 300)
        sync.backgroundSync().getOrThrow()

        assertEquals(3, settle(vm.count) { it == 3 })
        assertEquals(listOf("b", "a"), vm.rows.value?.map { it.id })
    }

    @Test
    fun state_survives_recreation() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        api.addItem("c", "feed/2", 300)
        val handle = SavedStateHandle()
        val first = vm(handle)
        ids(first, listOf("c", "b", "a"))
        first.select(StreamId.Folder("News"))
        ids(first, listOf("b", "a"))
        first.toggleRead("b")
        first.toggleRead("b")
        settle(first.read) { "b" !in it }
        val fetchesBefore = fetches

        val second = vm(handle)

        assertEquals(StreamId.Folder("News"), second.view.value)
        assertEquals(listOf("b", "a"), ids(second, listOf("b", "a")))
        assertTrue(db.itemDao().byIds(listOf("b")).first().single().manualUnread)
        assertEquals(fetchesBefore, fetches)
    }

    @Test
    fun cold_start_shows_stored_snapshot() = runTest(main, timeout = 30.seconds) {
        engine()
        db.itemDao().upsertKeepingLocal(
            listOf(
                ItemEntity("a", "feed/1", "T a", null, 100, null, read = false),
                ItemEntity("b", "feed/1", "T b", null, 200, null, read = false),
            ),
        )
        db.snapshotDao().replaceSnapshot(listOf("b", "a"), writtenAtMs = now)
        val gate = CompletableDeferred<Unit>()
        api.beforeUnread = { fetches++; gate.await() }

        val vm = vm()

        assertEquals(listOf("b", "a"), ids(vm, listOf("b", "a")))
        assertFalse(vm.refreshing.value)
        assertEquals(1, fetches)

        // the server has nothing unread, so the refresh empties the list
        gate.complete(Unit)
        assertEquals(emptyList<String>(), ids(vm, emptyList()))
        assertEquals(1, fetches)
    }

    @Test
    fun snapshot_survives_prune() = runTest(main, timeout = 30.seconds) {
        engine()
        db.itemDao().upsertKeepingLocal(listOf(ItemEntity("r", "feed/1", "T r", null, 100, null, read = true)))
        db.snapshotDao().replaceSnapshot(listOf("r"), writtenAtMs = now)

        sync.refresh(StreamId.All, oldestFirst = false).getOrThrow()

        assertEquals(listOf("r"), db.itemDao().byIds(listOf("r")).first().map { it.id })
    }

    @Test
    fun refresh_failure_keeps_list_and_shows_message() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))

        api.addItem("c", "feed/2", 300)
        api.failNext(1)
        vm.refresh()

        assertEquals(LoginMessages.UNREACHABLE, settle(vm.message) { it != null })
        assertEquals(listOf("b", "a"), vm.rows.value?.map { it.id })

        vm.refresh()
        ids(vm, listOf("c", "b", "a"))
        assertNull(settle(vm.message) { it == null })
    }

    @Test
    fun bad_credentials_sign_out() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))

        api.failNext(1, ApiError.BadCredentials)
        vm.refresh()

        settle(creds.authFlow) { it == null }
        assertNull(creds.auth())
    }

    @Test
    fun resume_after_30_minutes_refreshes() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))
        api.addItem("c", "feed/2", 300)

        vm.onBackground()
        now += 10 * 60_000L
        vm.onForeground()
        runCurrent()
        assertEquals(listOf("b", "a"), vm.rows.value?.map { it.id })

        vm.onBackground()
        now += 31 * 60_000L
        vm.onForeground()
        assertEquals(listOf("c", "b", "a"), ids(vm, listOf("c", "b", "a")))
    }

    private fun frame(t: Long, vararg rows: Pair<String, Int>, firstIndex: Int = 0): ListFrame =
        ListFrame(
            nowMs = t,
            viewportStart = 0,
            viewportEnd = 800,
            items = rows.mapIndexed { n, (id, offset) -> FrameItem(id, firstIndex + n, offset, 100) },
        )

    @Test
    fun scrolled_past_rows_are_marked_read() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))
        settle(vm.refreshing) { !it }

        for (t in 0L..400L step 16) vm.onVisible(frame(t, "b" to 0, "a" to 100))
        // b has left the top; a is still on screen
        vm.onVisible(frame(420, "a" to 0, firstIndex = 1))

        assertEquals(setOf("b"), settle(vm.read) { it == setOf("b") })
    }

    @Test
    fun rows_marked_unread_by_hand_are_not_marked_by_scroll() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))
        settle(vm.refreshing) { !it }
        vm.toggleRead("b")
        settle(vm.read) { "b" in it }
        vm.toggleRead("b")
        settle(vm.read) { "b" !in it }

        for (t in 0L..400L step 16) vm.onVisible(frame(t, "b" to 0, "a" to 100))
        vm.onVisible(frame(420, "a" to 0, firstIndex = 1))
        runCurrent()

        assertEquals(emptySet<String>(), settle(vm.read) { it.isNotEmpty() })
    }

    @Test
    fun open_marks_read() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))

        vm.open("a")

        assertEquals(setOf("a"), settle(vm.read) { it == setOf("a") })
    }

    @Test
    fun only_time_away_counts_on_return() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))
        val before = awaitFetches(1)
        api.addItem("c", "feed/2", 300)

        // the list has been read for 40 minutes, then the user leaves for 10 seconds
        now += 40 * 60_000L
        vm.onBackground()
        now += 10_000L
        vm.onForeground()
        runCurrent()
        assertEquals(before, fetches)
        assertEquals(listOf("b", "a"), vm.rows.value?.map { it.id })

        vm.onBackground()
        now += 31 * 60_000L
        vm.onForeground()
        assertEquals(listOf("c", "b", "a"), ids(vm, listOf("c", "b", "a")))
        assertEquals(before + 1, fetches)
    }

    @Test
    fun restored_stale_list_refreshes() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val handle = SavedStateHandle()
        ids(vm(handle), listOf("b", "a"))
        val before = awaitFetches(1)
        api.addItem("c", "feed/2", 300)

        // the process died and comes back after 31 minutes
        now += 31 * 60_000L
        val restored = vm(handle)

        assertEquals(listOf("c", "b", "a"), ids(restored, listOf("c", "b", "a")))
        assertEquals(before + 1, fetches)
    }

    @Test
    fun restored_fresh_list_does_not_refresh() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val handle = SavedStateHandle()
        ids(vm(handle), listOf("b", "a"))
        val before = awaitFetches(1)
        api.addItem("c", "feed/2", 300)

        now += 5 * 60_000L
        val restored = vm(handle)

        assertEquals(listOf("b", "a"), ids(restored, listOf("b", "a")))
        runCurrent()
        assertEquals(before, fetches)
    }

    @Test
    fun rows_scrolled_past_during_a_refresh_are_marked() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))
        settle(vm.refreshing) { !it }
        val gate = CompletableDeferred<Unit>()
        api.beforeUnread = { fetches++; gate.await() }
        vm.refresh()
        assertTrue(vm.refreshing.value)

        for (t in 0L..400L step 16) vm.onVisible(frame(t, "b" to 0, "a" to 100))
        vm.onVisible(frame(420, "a" to 0, firstIndex = 1))

        assertEquals(setOf("b"), settle(vm.read) { it == setOf("b") })
        gate.complete(Unit)
        settle(vm.refreshing) { !it }
    }

    @Test
    fun mark_all_uses_view_feeds_and_newest_loaded() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        api.addItem("c", "feed/2", 300)
        val vm = vm()
        ids(vm, listOf("c", "b", "a"))
        vm.select(StreamId.Folder("News"))
        ids(vm, listOf("b", "a"))
        // arrives after the list was loaded, so mark all must leave it alone
        api.addItem("d", "feed/1", 250)

        vm.markAllRead()

        assertEquals(listOf("d"), ids(vm, listOf("d")))
        assertEquals(listOf(StreamId.Folder("News").raw to 201_000_000L), api.markAllCalls)
        assertTrue(api.isRead("a") && api.isRead("b"))
        assertFalse(api.isRead("c") || api.isRead("d"))
        val c = db.itemDao().byIds(listOf("c")).first().single()
        assertFalse(c.read)
    }

    @Test
    fun cold_start_restores_view_header() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        db.itemDao().upsertKeepingLocal(
            listOf(
                ItemEntity("a", "feed/1", "T a", null, 100, null, read = false),
                ItemEntity("b", "feed/1", "T b", null, 200, null, read = false),
            ),
        )
        db.snapshotDao().replaceSnapshot(listOf("b", "a"), writtenAtMs = now, viewRaw = StreamId.Folder("News").raw)

        val vm = vm()

        assertEquals(StreamId.Folder("News"), settle(vm.view) { it == StreamId.Folder("News") })
        assertEquals("News", settle(vm.viewName) { it == "News" })
        assertEquals(listOf("b", "a"), ids(vm, listOf("b", "a")))
    }

    @Test
    fun picker_lists_folders_with_counts() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))

        val entries = settle(vm.picker) { it.size > 1 }

        assertEquals(PickerEntry.All(2), entries.first())
        assertEquals(PickerEntry.Folder("News", 2), entries[1])
    }

    @Test
    fun a_folded_folder_loses_its_feeds_in_the_picker_and_stays_folded() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))
        settle(vm.picker) { it.size > 1 }

        vm.toggleFolder("News")
        runCurrent()
        val folded = settle(vm.picker) { list -> list.none { it is PickerEntry.Feed } }

        assertEquals(PickerEntry.Folder("News", 2, collapsed = true), folded[1])
        assertEquals(setOf("News"), settings.collapsedFolders.first())
    }

    @Test
    fun mark_all_with_nothing_loaded_does_nothing() = runTest(main, timeout = 30.seconds) {
        engine()
        val vm = vm()
        ids(vm, emptyList())
        settle(vm.refreshing) { !it }
        api.addItem("a", "feed/1", 100)

        vm.markAllRead()
        runCurrent()

        assertTrue(api.markAllCalls.isEmpty())
        assertFalse(api.isRead("a"))
    }

    @Test
    fun mark_all_works_from_the_count_when_the_list_has_no_rows() = runTest(main, timeout = 30.seconds) {
        engine()
        val vm = vm()
        ids(vm, emptyList())
        settle(vm.refreshing) { !it }
        // sync stored an item the snapshot does not hold, so the header counts it
        db.feedDao().replaceAll(listOf(FeedEntity("feed/1", "News", null, null, null)))
        db.feedDao().replaceCounts(listOf(CountEntity("feed/1", 1)))
        db.itemDao().upsertKeepingLocal(listOf(ItemEntity("x", "feed/1", "T x", null, 150, null, read = false)))
        assertEquals(1, settle(vm.count) { it == 1 })
        assertTrue(vm.rows.value.isNullOrEmpty())

        vm.markAllRead()
        repeat(150) {
            runCurrent()
            if (api.markAllCalls.isNotEmpty()) return@repeat
            withContext(Dispatchers.IO) { Thread.sleep(10) }
        }

        assertEquals(listOf(StreamId.All.raw to 151_000_000L), api.markAllCalls)
    }

    @Test
    fun the_sheet_has_an_image_only_while_the_setting_is_on() = runTest(main, timeout = 30.seconds) {
        engine()
        api.addItem("a", "feed/1", 100, html = "<p>Text</p><img src=\"/pic.jpg\">", url = "https://e.x/post")
        val vm = vm()
        ids(vm, listOf("a"))

        assertEquals("https://e.x/pic.jpg", vm.sheetBody("a", "https://e.x/post").imageUrl)

        settings.setLeadImages(false)
        val off = vm.sheetBody("a", "https://e.x/post")

        assertNull(off.imageUrl)
        assertEquals("Text", off.text?.text)
    }

    @Test
    fun selecting_the_current_view_refreshes() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = vm()
        ids(vm, listOf("b", "a"))
        settle(vm.refreshing) { !it }
        api.addItem("c", "feed/2", 300)

        vm.select(StreamId.All)

        assertEquals(listOf("c", "b", "a"), ids(vm, listOf("c", "b", "a")))
    }

    @Test
    fun initial_view_seeds_the_header_before_anything_loads() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = ListViewModel(
            savedState = SavedStateHandle(), itemDao = db.itemDao(), feedDao = db.feedDao(), snapshotDao = db.snapshotDao(),
            sync = sync, queue = queue, settings = settings, creds = creds, appScope = backgroundScope,
            initialView = StreamId.Folder("News").raw, clock = { now }, work = main,
        )
        assertEquals(StreamId.Folder("News"), vm.view.value)
    }

    @Test
    fun stale_view_from_another_account_falls_back_to_all() = runTest(main, timeout = 30.seconds) {
        engine()
        seedServer()
        val vm = ListViewModel(
            savedState = SavedStateHandle(), itemDao = db.itemDao(), feedDao = db.feedDao(), snapshotDao = db.snapshotDao(),
            sync = sync, queue = queue, settings = settings, creds = creds, appScope = backgroundScope,
            initialView = StreamId.Folder("Other account folder").raw, clock = { now }, work = main,
        )

        assertEquals(StreamId.All, settle(vm.view) { it == StreamId.All })
        assertEquals(listOf("b", "a"), ids(vm, listOf("b", "a")))
    }
}
