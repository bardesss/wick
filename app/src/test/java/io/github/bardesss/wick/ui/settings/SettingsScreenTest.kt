package io.github.bardesss.wick.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.bardesss.wick.data.LinkTarget
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
class SettingsScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val changes = mutableListOf<Pair<String, Any>>()

    private fun show() {
        rule.setContent {
            WickTheme {
                SettingsScreen(
                    oldestFirst = false,
                    hideEmpty = true,
                    leadImages = false,
                    haptics = true,
                    linkTarget = LinkTarget.InApp,
                    server = "https://feeds.example.com",
                    onOldestFirst = { changes += "oldestFirst" to it },
                    onHideEmpty = { changes += "hideEmpty" to it },
                    onLeadImages = { changes += "leadImages" to it },
                    onHaptics = { changes += "haptics" to it },
                    onLinkTarget = { changes += "linkTarget" to it },
                    onSignOut = {},
                    version = "0.1.0",
                )
            }
        }
    }

    private fun switchRow(label: String) =
        rule.onNode(hasText(label) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch), useUnmergedTree = false)

    @Test
    fun a_switch_row_says_its_state_and_toggles_as_a_whole() {
        show()

        switchRow("Hide feeds with no unread").assertIsOn()
        switchRow("Images in summary").assertIsOff()
        rule.onNodeWithText("Images in summary").performClick()
        rule.onNodeWithText("Haptics").performClick()

        assertEquals(listOf<Pair<String, Any>>("leadImages" to true, "haptics" to false), changes)
    }

    @Test
    fun segments_say_which_is_selected_and_pick_the_other() {
        show()

        rule.onNodeWithText("Newest").assertIsSelected()
        rule.onNodeWithText("Oldest").assertIsNotSelected()
        rule.onNodeWithText("In app").assertIsSelected()
        rule.onNodeWithText("Oldest").performClick()
        rule.onNodeWithText("Default app").performClick()

        assertEquals(listOf<Pair<String, Any>>("oldestFirst" to true, "linkTarget" to LinkTarget.DefaultApp), changes)
    }
}
