package io.github.bardesss.wick.ui.picker

import androidx.activity.ComponentActivity
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.bardesss.wick.api.StreamId
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
class PickerDrawerFoldTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val selected = mutableListOf<StreamId>()
    private val toggled = mutableListOf<String>()

    private fun show(entries: List<PickerEntry>) {
        rule.setContent {
            WickTheme {
                PickerDrawer(
                    state = rememberDrawerState(DrawerValue.Open),
                    entries = entries,
                    selected = StreamId.All,
                    markCount = 3,
                    armed = false,
                    animate = false,
                    onSelect = { selected += it },
                    onMarkAll = {},
                    onSettings = {},
                    onToggleFolder = { toggled += it },
                ) { Text("list") }
            }
        }
    }

    @Test
    fun the_chevron_folds_and_the_name_still_selects() {
        show(listOf(PickerEntry.All(3), PickerEntry.Folder("Tech", 3)))

        rule.onNodeWithContentDescription("Collapse Tech").performClick()
        rule.onNodeWithText("Tech").performClick()

        assertEquals(listOf("Tech"), toggled)
        assertEquals(listOf<StreamId>(StreamId.Folder("Tech")), selected)
    }

    @Test
    fun a_folded_folder_offers_to_expand() {
        show(listOf(PickerEntry.All(3), PickerEntry.Folder("Tech", 3, collapsed = true)))

        rule.onNodeWithContentDescription("Expand Tech").performClick()

        assertEquals(listOf("Tech"), toggled)
    }

    @Test
    fun the_wordmark_is_one_name_and_not_a_control() {
        show(listOf(PickerEntry.All(3)))

        rule.onNodeWithContentDescription("Wick").assertHasNoClickAction()
        rule.onAllNodesWithText("wıck").assertCountEquals(0)
    }
}
