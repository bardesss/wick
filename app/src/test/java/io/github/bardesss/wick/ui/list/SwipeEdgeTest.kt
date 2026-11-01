package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.ui.picker.PickerDrawer
import io.github.bardesss.wick.ui.picker.PickerEntry
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
class SwipeEdgeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val rows = listOf(
        RowModel("1", "First", "https://e.x/1", "Source", null, 0),
        RowModel("2", "Second", "https://e.x/2", "Source", null, 0),
    )

    private fun dragRight(startXDp: Float, yPx: Float, tag: String? = null, sign: Float = 1f) {
        val node = if (tag == null) rule.onRoot() else rule.onNodeWithTag(tag)
        node.performTouchInput {
            val x = startXDp.dp.toPx()
            down(Offset(x, yPx))
            repeat(10) { moveBy(Offset(sign * 30.dp.toPx(), 0f)) }
            up()
        }
        rule.waitForIdle()
    }

    private fun showRow(outcomes: MutableList<SwipeOutcome>, insetPx: Int? = null) {
        rule.setContent {
            WickTheme {
                Box(Modifier.fillMaxWidth().height(80.dp).testTag("row")) {
                    val lock = remember { SwipeLock() }
                    val inset = insetPx ?: 0
                    SwipeRow("1", lock, reducedMotion = true, onThreshold = {}, onSwipe = { outcomes += it }, gestureInsetPx = inset) {
                        Box(it.fillMaxSize())
                    }
                }
            }
        }
    }

    @Test
    fun a_leftward_swipe_from_the_left_edge_still_toggles() {
        val outcomes = mutableListOf<SwipeOutcome>()
        // a wide inset makes the zone 174dp, so there is room to travel left from inside it
        showRow(outcomes, insetPx = 450)

        dragRight(170f, 40f, "row", sign = -1f)
        assertEquals(listOf(SwipeOutcome.ToggleRead), outcomes)
        outcomes.clear()

        dragRight(170f, 40f, "row")
        assertEquals(emptyList<SwipeOutcome>(), outcomes)
    }

    @Test
    fun the_edge_zone_grows_with_the_system_gesture_inset() {
        val outcomes = mutableListOf<SwipeOutcome>()
        // 60px of back gesture at 3px per dp: the zone is 20dp + 24dp = 44dp wide
        showRow(outcomes, insetPx = 60)

        dragRight(40f, 40f, "row")
        assertEquals(emptyList<SwipeOutcome>(), outcomes)

        dragRight(60f, 40f, "row")
        assertEquals(listOf(SwipeOutcome.OpenSheet), outcomes)
    }

    @Test
    fun a_row_ignores_a_drag_from_the_left_edge_but_not_from_the_middle() {
        val outcomes = mutableListOf<SwipeOutcome>()
        showRow(outcomes)

        dragRight(8f, 40f, "row")
        assertEquals(emptyList<SwipeOutcome>(), outcomes)

        dragRight(150f, 40f, "row")
        assertEquals(listOf(SwipeOutcome.OpenSheet), outcomes)
    }

    @Test
    fun the_drawer_opens_from_the_left_edge_over_a_row_but_a_mid_row_swipe_leaves_it_closed() {
        lateinit var drawer: DrawerState
        val outcomes = mutableListOf<SwipeOutcome>()
        rule.setContent {
            WickTheme {
                drawer = rememberDrawerState(DrawerValue.Closed)
                PickerDrawer(
                    state = drawer,
                    entries = listOf(PickerEntry.All(2)),
                    selected = StreamId.All,
                    markCount = 2,
                    armed = false,
                    animate = false,
                    onSelect = {},
                    onMarkAll = {},
                    onSettings = {},
                ) {
                    ListContent(
                        viewName = "All unread",
                        count = 2,
                        message = null,
                        rows = rows,
                        read = remember { mutableStateOf(emptySet()) },
                        nowSec = 0,
                        refreshing = false,
                        listState = rememberLazyListState(),
                        onRefresh = {},
                        onOpenPicker = {},
                        onOpen = {},
                        onFrame = { false },
                        onShowText = { outcomes += SwipeOutcome.OpenSheet },
                    )
                }
            }
        }
        val rowY = 100.dp.value * 3f

        dragRight(150f, rowY)
        assertEquals(DrawerValue.Closed, drawer.currentValue)
        assertEquals(listOf(SwipeOutcome.OpenSheet), outcomes)

        dragRight(8f, rowY)
        assertEquals(DrawerValue.Open, drawer.currentValue)
        assertEquals(1, outcomes.size)
    }
}
