package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.bardesss.wick.sync.MAX_ITEMS
import io.github.bardesss.wick.ui.theme.WickTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NewItemsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun the_count_beyond_the_snapshot_is_new() {
        assertEquals(1, newItems(count = 1, unreadRowsInSnapshot = 0))
        assertEquals(3, newItems(count = 10, unreadRowsInSnapshot = 7))
    }

    @Test
    fun nothing_is_new_when_the_snapshot_covers_the_count() {
        assertEquals(0, newItems(count = 5, unreadRowsInSnapshot = 5))
        assertEquals(0, newItems(count = 0, unreadRowsInSnapshot = 0))
        assertEquals(0, newItems(count = 2, unreadRowsInSnapshot = 9))
    }

    @Test
    fun a_full_snapshot_has_no_room_for_more() {
        assertEquals(0, newItems(count = 1200, unreadRowsInSnapshot = 1000, snapshotRows = MAX_ITEMS))
        assertEquals(5, newItems(count = 1004, unreadRowsInSnapshot = 999, snapshotRows = MAX_ITEMS - 1))
    }

    @Test
    fun a_suppressed_count_shows_nothing_until_it_moves() {
        assertEquals(0, newItems(count = 8, unreadRowsInSnapshot = 5, suppressedAt = 8))
        assertEquals(4, newItems(count = 9, unreadRowsInSnapshot = 5, suppressedAt = 8))
    }

    @Test
    fun a_refresh_that_did_not_shrink_the_gap_gained_nothing() {
        assertTrue(refreshGainedNothing(gapBefore = 3, gapAfter = 3))
        assertTrue(refreshGainedNothing(gapBefore = 3, gapAfter = 5))
        assertFalse(refreshGainedNothing(gapBefore = 3, gapAfter = 1))
        assertFalse(refreshGainedNothing(gapBefore = 0, gapAfter = 0))
    }

    private var refreshing by mutableStateOf(false)
    private var refreshed = 0

    private fun show(count: Int, rows: List<RowModel> = emptyList()) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            WickTheme {
                ListContent(
                    viewName = "All unread",
                    count = count,
                    message = null,
                    rows = rows,
                    read = remember { mutableStateOf(emptySet()) },
                    nowSec = 0,
                    refreshing = refreshing,
                    listState = rememberLazyListState(),
                    onRefresh = { refreshed++ },
                    onOpenPicker = {},
                    onOpen = {},
                    onFrame = { false },
                )
            }
        }
    }

    private fun advance(ms: Long) = rule.mainClock.advanceTimeBy(ms)

    @Test
    fun the_pill_waits_for_the_gap_to_hold_then_a_tap_refreshes() {
        show(count = 3)

        advance(PILL_DELAY_MS - 100)
        rule.onNodeWithTag("new_items").assertDoesNotExist()

        advance(400)
        rule.onNode(hasText("3 new")).assertExists()
        rule.onNodeWithTag("new_items").performClick()
        assertEquals(1, refreshed)
    }

    @Test
    fun the_pill_is_inert_while_refreshing() {
        show(count = 3)
        advance(PILL_DELAY_MS + 400)

        rule.runOnUiThread { refreshing = true }
        rule.waitForIdle()
        advance(50)

        rule.onNodeWithTag("new_items").assertIsNotEnabled()
        assertEquals(0, refreshed)
    }

    @Test
    fun no_pill_starts_while_refreshing() {
        refreshing = true
        show(count = 3)

        advance(PILL_DELAY_MS * 3)

        rule.onNodeWithTag("new_items").assertDoesNotExist()
    }

    @Test
    fun a_refresh_that_leaves_the_gap_hides_the_pill_for_good() {
        refreshing = true
        show(count = 3)
        advance(100)

        refreshing = false
        advance(PILL_DELAY_MS * 4)

        rule.onNodeWithTag("new_items").assertDoesNotExist()
    }

    @Test
    fun a_full_snapshot_gets_no_pill() {
        val rows = List(MAX_ITEMS) { RowModel("id$it", "T $it", null, "S", null, 0) }
        show(count = MAX_ITEMS + 5, rows = rows)

        advance(PILL_DELAY_MS * 3)

        rule.onNodeWithTag("new_items").assertDoesNotExist()
    }

    @Test
    fun the_pill_is_hidden_when_nothing_is_new() {
        show(count = 0)

        advance(PILL_DELAY_MS * 3)

        rule.onNodeWithTag("new_items").assertDoesNotExist()
    }
}
