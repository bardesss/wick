package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import io.github.bardesss.wick.ui.picker.MarkAllArm
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
class HeaderMarkAllTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(count: Int, canMark: Boolean = true, onMark: () -> Unit) {
        val arm = MarkAllArm()
        rule.setContent {
            WickTheme {
                ListContent(
                    viewName = "All unread",
                    count = count,
                    message = null,
                    rows = emptyList(),
                    read = remember { mutableStateOf(emptySet()) },
                    nowSec = 0,
                    refreshing = false,
                    listState = rememberLazyListState(),
                    onRefresh = {},
                    onOpenPicker = {},
                    onOpen = {},
                    onFrame = { false },
                    canMarkAll = canMark,
                    markArmed = arm.armed,
                    onMarkAll = { if (arm.tap(count > 0)) onMark() },
                )
            }
        }
    }

    @Test
    fun first_tap_arms_with_the_count_and_second_marks() {
        var marked = 0
        show(12) { marked++ }

        rule.onNodeWithTag("mark_all").performClick()
        rule.onNode(hasText("Mark 12 read")).assertExists()
        assertEquals(0, marked)

        rule.onNodeWithTag("mark_all").performClick()
        rule.onNode(hasText("Mark 12 read")).assertDoesNotExist()
        assertEquals(1, marked)
    }

    @Test
    fun hidden_when_the_view_cannot_be_marked() {
        show(5, canMark = false) {}
        rule.onNodeWithTag("mark_all").assertDoesNotExist()
    }

    @Test
    fun hidden_when_nothing_is_unread() {
        show(0) {}
        rule.onNodeWithTag("mark_all").assertDoesNotExist()
    }
}
