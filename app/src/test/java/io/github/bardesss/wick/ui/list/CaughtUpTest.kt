package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
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
class CaughtUpTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tapping_the_button_refreshes() {
        var refreshed = 0
        rule.setContent { WickTheme { CaughtUp(onRefresh = { refreshed++ }, refreshing = false) } }

        rule.onNodeWithTag("check_new").performClick()

        assertEquals(1, refreshed)
    }

    @Test
    fun the_button_is_disabled_while_refreshing() {
        var refreshed = 0
        rule.setContent { WickTheme { CaughtUp(onRefresh = { refreshed++ }, refreshing = true) } }

        rule.onNodeWithTag("check_new").assertIsNotEnabled()
        rule.onNodeWithTag("check_new").performClick()

        assertEquals(0, refreshed)
    }
}
