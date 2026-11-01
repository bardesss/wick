package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FooterPullTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val rows = List(20) { RowModel("$it", "Item $it", "https://e.x/$it", "Source", null, 0) }
    private var refreshed = 0

    private fun show() {
        rule.setContent {
            WickTheme {
                ListContent(
                    viewName = "All unread",
                    count = 0,
                    message = null,
                    rows = rows,
                    read = remember { mutableStateOf(rows.map { it.id }.toSet()) },
                    nowSec = 0,
                    refreshing = false,
                    listState = rememberLazyListState(),
                    onRefresh = { refreshed++ },
                    onOpenPicker = {},
                    onOpen = {},
                    onFrame = { false },
                )
            }
        }
    }

    private fun dragDown(distanceDp: Float) {
        rule.onNodeWithTag("list").performTouchInput {
            val x = width / 2f
            down(Offset(x, 200f))
            repeat(10) { moveBy(Offset(0f, distanceDp.dp.toPx() / 10f)) }
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun a_long_pull_down_on_the_caught_up_footer_refreshes() {
        show()
        rule.onNodeWithTag("list").performScrollToIndex(rows.size)
        rule.waitForIdle()

        dragDown(400f)
        assertEquals(1, refreshed)
    }

    @Test
    fun a_short_pull_down_on_the_footer_does_not_refresh() {
        show()
        rule.onNodeWithTag("list").performScrollToIndex(rows.size)
        rule.waitForIdle()

        dragDown(60f)
        assertEquals(0, refreshed)
    }

    @Test
    fun a_pull_down_in_the_middle_of_the_list_just_scrolls() {
        show()
        rule.onNodeWithTag("list").performScrollToIndex(15)
        rule.waitForIdle()

        dragDown(200f)
        assertEquals(0, refreshed)
    }
}
