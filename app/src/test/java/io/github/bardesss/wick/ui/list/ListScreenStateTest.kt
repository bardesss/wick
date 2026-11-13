package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.data.MemoryPreferences
import io.github.bardesss.wick.data.SettingsStore
import io.github.bardesss.wick.db.ItemEntity
import io.github.bardesss.wick.db.WickDb
import io.github.bardesss.wick.sync.FakeReaderApi
import io.github.bardesss.wick.sync.SyncEngine
import io.github.bardesss.wick.sync.inMemoryDb
import io.github.bardesss.wick.sync.newQueue
import io.github.bardesss.wick.ui.theme.WickTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The screen's sheet and search state across recreation. One view model instance is kept
 * across the emulated recreation, which is what survives a rotation in the app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ListScreenStateTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private class MemoryCreds : CredentialStore {
        private val flow = MutableStateFlow<Auth?>(Auth("https://feeds.example.com", "tok"))
        override fun auth() = flow.value
        override val authFlow: StateFlow<Auth?> = flow
        override suspend fun save(auth: Auth) { flow.value = auth }
        override suspend fun clear() { flow.value = null }
    }

    private lateinit var db: WickDb
    private lateinit var scope: CoroutineScope
    private lateinit var vm: ListViewModel
    private val api = FakeReaderApi()

    @Before
    fun setUp() {
        db = inMemoryDb()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        // the first refresh never finishes, so the stored list stays as the test left it
        val gate = CompletableDeferred<Unit>()
        api.beforeUnread = { gate.await() }
        val queue = db.newQueue(api, scope)
        vm = ListViewModel(
            savedState = SavedStateHandle(),
            itemDao = db.itemDao(),
            feedDao = db.feedDao(),
            snapshotDao = db.snapshotDao(),
            sync = SyncEngine(api, db, queue),
            queue = queue,
            settings = SettingsStore(MemoryPreferences()),
            creds = MemoryCreds(),
            appScope = scope,
            work = Dispatchers.Default,
        )
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun store(ids: List<String>) = runBlocking {
        db.itemDao().upsertKeepingLocal(
            ids.mapIndexed { i, id -> ItemEntity(id, "feed/1", "Title $id", null, 100L + i, null, read = false) },
        )
    }

    private fun snapshot(ids: List<String>) = runBlocking {
        db.snapshotDao().replaceSnapshot(ids, writtenAtMs = System.currentTimeMillis())
    }

    private fun rowCount() = rule.onAllNodesWithTag("row").fetchSemanticsNodes().size

    private fun showSheetOf(index: Int) {
        rule.waitUntil(5_000) { rowCount() > index }
        val action = rule.onAllNodesWithTag("row")[index].fetchSemanticsNode().config
            .getOrNull(SemanticsActions.CustomActions)!!.first()
        rule.runOnUiThread { action.action() }
        rule.waitUntil(5_000) { sheetShowing() }
    }

    private fun sheetShowing() = rule.onAllNodesWithTag("sheet").fetchSemanticsNodes().isNotEmpty()

    @Test
    fun sheet_for_a_dropped_row_survives_recreation() {
        store(listOf("a", "b"))
        snapshot(listOf("b", "a"))
        val tester = StateRestorationTester(rule)
        tester.setContent { WickTheme { ListScreen(vm, onOpenSettings = {}) } }
        showSheetOf(0)

        // a refresh drops the row the sheet shows
        snapshot(listOf("a"))
        rule.waitUntil(5_000) { rowCount() == 1 }
        assertTrue(sheetShowing())

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertTrue(sheetShowing())
    }

    @Test
    fun sheet_id_without_a_row_is_cleared() {
        store(listOf("a", "b"))
        snapshot(listOf("b", "a"))
        val tester = StateRestorationTester(rule)
        tester.setContent { WickTheme { ListScreen(vm, onOpenSettings = {}) } }
        showSheetOf(0)

        // the process was rebuilt: nothing is kept and the row is gone from the list
        vm.sheetKept = null
        snapshot(listOf("a"))
        tester.emulateSavedInstanceStateRestore()
        rule.waitUntil(5_000) { !sheetShowing() }

        // and it does not come back by itself if the row returns later
        snapshot(listOf("b", "a"))
        rule.waitUntil(5_000) { rowCount() == 2 }
        assertFalse(sheetShowing())
    }

    @Test
    fun a_row_still_listed_keeps_its_sheet_and_the_kept_row() {
        store(listOf("a", "b"))
        snapshot(listOf("b", "a"))
        rule.setContent { WickTheme { ListScreen(vm, onOpenSettings = {}) } }
        showSheetOf(0)
        rule.waitForIdle()

        assertTrue(sheetShowing())
        assertEquals("b", vm.sheetKept?.id)
    }

    @Test
    fun restored_search_keeps_its_scroll_position() {
        val rows = (1..40).map { RowModel("n$it", "Title $it", null, "News", null, 100L + it) }
        val tester = StateRestorationTester(rule)
        tester.setContent {
            WickTheme {
                ListContent(
                    viewName = "Recently read",
                    history = true,
                    count = 0,
                    message = null,
                    rows = rows,
                    read = remember { mutableStateOf(rows.map { it.id }.toSet()) },
                    nowSec = 0,
                    refreshing = false,
                    listState = rememberLazyListState(),
                    onRefresh = {},
                    onOpenPicker = {},
                    onOpen = {},
                    onFrame = { false },
                )
            }
        }

        rule.onNodeWithTag("search").performClick()
        rule.onNodeWithTag("search_field").performTextInput("Title")
        rule.waitForIdle()
        rule.onNodeWithTag("list").performScrollToIndex(20)
        rule.waitForIdle()
        val before = scrollValue()
        assertTrue(before > 0f)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        assertEquals(before, scrollValue(), 0.5f)
    }

    private fun scrollValue(): Float = rule.onNodeWithTag("list").fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.VerticalScrollAxisRange)!!.value()
}
