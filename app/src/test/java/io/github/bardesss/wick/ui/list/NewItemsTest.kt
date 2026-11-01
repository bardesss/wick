package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.bardesss.wick.ui.theme.WickTheme
import org.junit.Assert.assertEquals
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

    private fun show(count: Int, refreshing: Boolean = false, onRefresh: () -> Unit = {}) {
        rule.setContent {
            WickTheme {
                ListContent(
                    viewName = "All unread",
                    count = count,
                    message = null,
                    rows = emptyList(),
                    read = remember { mutableStateOf(emptySet()) },
                    nowSec = 0,
                    refreshing = refreshing,
                    listState = rememberLazyListState(),
                    onRefresh = onRefresh,
                    onOpenPicker = {},
                    onOpen = {},
                    onFrame = { false },
                )
            }
        }
    }

    @Test
    fun the_pill_shows_the_difference_and_a_tap_refreshes() {
        var refreshed = 0
        show(count = 3) { refreshed++ }

        rule.onNode(hasText("3 new")).assertExists()
        rule.onNodeWithTag("new_items").performClick()

        assertEquals(1, refreshed)
    }

    @Test
    fun the_pill_is_inert_while_refreshing() {
        var refreshed = 0
        show(count = 3, refreshing = true) { refreshed++ }
        rule.onNodeWithTag("new_items").assertIsNotEnabled()
        assertEquals(0, refreshed)
    }

    @Test
    fun the_pill_is_hidden_when_nothing_is_new() {
        show(count = 0)
        rule.onNodeWithTag("new_items").assertDoesNotExist()
    }
}
