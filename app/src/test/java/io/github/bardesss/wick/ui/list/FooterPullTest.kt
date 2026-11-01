package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.bardesss.wick.ui.theme.WickTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The footer pull inside the real list, with the real pull to refresh box around it. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FooterPullTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val rows = List(20) { RowModel("$it", "Item $it", "https://e.x/$it", "Source", null, 0) }
    private var refreshed = 0
    private lateinit var list: LazyListState
    private lateinit var footer: PullToRefreshState

    private fun show() {
        rule.setContent {
            WickTheme {
                list = rememberLazyListState()
                footer = rememberPullToRefreshState()
                ListContent(
                    viewName = "All unread",
                    count = 0,
                    message = null,
                    rows = rows,
                    read = remember { mutableStateOf(rows.map { it.id }.toSet()) },
                    nowSec = 0,
                    refreshing = false,
                    listState = list,
                    onRefresh = { refreshed++ },
                    onOpenPicker = {},
                    onOpen = {},
                    onFrame = { false },
                    footerPullState = footer,
                )
            }
        }
    }

    private fun toFooter() {
        rule.onNodeWithTag("list").performScrollToIndex(rows.size)
        rule.waitForIdle()
    }

    /** Down by each of [stepsDp] in turn (negative is up), then lift the finger. */
    private fun drag(vararg stepsDp: Float) {
        rule.onNodeWithTag("list").performTouchInput {
            down(Offset(width / 2f, 200f))
            for (s in stepsDp) repeat(10) { moveBy(Offset(0f, s.dp.toPx() / 10f)) }
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun a_long_pull_down_on_the_footer_refreshes_once() {
        show()
        toFooter()

        drag(400f)
        assertEquals(1, refreshed)
    }

    @Test
    fun pulling_past_the_threshold_and_back_under_it_does_not_refresh_and_resets_the_indicator() {
        show()
        toFooter()

        // 400dp down is 200dp of pull, 300dp back up leaves 50dp, under the 80dp threshold
        drag(400f, -300f)
        assertEquals(0, refreshed)
        assertEquals(0f, footer.distanceFraction, 0.01f)
    }

    @Test
    fun a_short_pull_on_the_footer_does_not_refresh() {
        show()
        toFooter()

        drag(60f)
        assertEquals(0, refreshed)
        assertEquals(0f, footer.distanceFraction, 0.01f)
    }

    @Test
    fun a_pull_down_at_the_top_of_the_list_still_refreshes_once() {
        show()

        drag(400f)
        assertEquals(1, refreshed)
        // the footer indicator was never involved
        assertEquals(0f, footer.distanceFraction, 0.01f)
    }

    @Test
    fun a_pull_down_in_the_middle_of_the_list_just_scrolls() {
        show()
        rule.onNodeWithTag("list").performScrollToIndex(15)
        rule.waitForIdle()
        val before = list.firstVisibleItemIndex

        drag(200f)
        assertEquals(0, refreshed)
        assertTrue(list.firstVisibleItemIndex < before)
    }

    @Test
    fun an_upward_scroll_still_scrolls_the_list() {
        show()

        drag(-300f)
        assertEquals(0, refreshed)
        assertTrue(list.firstVisibleItemIndex > 0)
        assertEquals(0f, footer.distanceFraction, 0.01f)
    }
}
