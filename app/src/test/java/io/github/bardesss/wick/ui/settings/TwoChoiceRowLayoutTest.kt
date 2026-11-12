package io.github.bardesss.wick.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.bardesss.wick.data.LinkTarget
import io.github.bardesss.wick.ui.theme.WickTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class TwoChoiceRowLayoutTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun show(fontScale: Float) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale)) {
                WickTheme {
                    SettingsScreen(
                        oldestFirst = false,
                        hideEmpty = true,
                        leadImages = false,
                        haptics = true,
                        linkTarget = LinkTarget.InApp,
                        server = "https://feeds.example.com",
                        onOldestFirst = {},
                        onHideEmpty = {},
                        onLeadImages = {},
                        onHaptics = {},
                        onLinkTarget = {},
                        onSignOut = {},
                        onClose = {},
                        version = "0.1.0",
                    )
                }
            }
        }
    }

    private fun lines(text: String): Int {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(text).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.first().lineCount
    }

    @Test
    fun at_a_large_font_the_segments_move_below_a_label_of_at_most_two_lines() {
        show(fontScale = 1.5f)

        for ((label, segment) in listOf("Open links in" to "In app", "Sort order" to "Newest")) {
            val labelBounds = rule.onNodeWithText(label).getUnclippedBoundsInRoot()
            val segmentBounds = rule.onNodeWithText(segment).getUnclippedBoundsInRoot()
            assertTrue("$label wraps into ${lines(label)} lines", lines(label) <= 2)
            assertTrue("$segment sits below $label", segmentBounds.top >= labelBounds.bottom)
        }
    }

    @Test
    fun at_the_normal_font_the_segments_sit_beside_the_label() {
        show(fontScale = 1f)

        val label = rule.onNodeWithText("Open links in").getUnclippedBoundsInRoot()
        val segment = rule.onNodeWithText("In app").getUnclippedBoundsInRoot()
        assertEquals(1, lines("Open links in"))
        assertTrue(segment.left >= label.right)
        assertTrue(segment.top < label.bottom && label.top < segment.bottom)
        assertTrue(label.bottom - label.top <= 60.dp)
    }
}
