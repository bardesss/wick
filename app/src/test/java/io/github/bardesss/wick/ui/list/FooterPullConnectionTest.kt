package io.github.bardesss.wick.ui.list

import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives [FooterPull] directly, with the real pull to refresh state it feeds. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FooterPullConnectionTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var state: PullToRefreshState
    private lateinit var pull: FooterPull
    private var refreshed = 0
    private var refreshing = false
    private var active = true

    private fun setUp() {
        rule.setContent {
            state = rememberPullToRefreshState()
            val scope = rememberCoroutineScope()
            pull = FooterPull(
                state = state,
                scope = scope,
                thresholdPx = 100f,
                active = { active },
                refreshing = { refreshing },
                onRefresh = { refreshed++ },
            )
        }
    }

    private fun drag(dy: Float): Offset {
        val used = pull.onPreScroll(Offset(0f, dy), NestedScrollSource.UserInput)
        rule.waitForIdle()
        return used
    }

    private fun fling() {
        runBlocking { pull.onPreFling(Velocity(0f, 500f)) }
        rule.waitForIdle()
    }

    @Test
    fun pull_past_the_threshold_and_release_refreshes_once() {
        setUp()
        assertEquals(300f, drag(300f).y, 0.01f)
        assertTrue(state.distanceFraction >= 1f)

        fling()
        fling()
        assertEquals(1, refreshed)
    }

    @Test
    fun pulling_back_below_the_threshold_before_release_does_not_refresh_and_hides_the_indicator() {
        setUp()
        drag(300f)
        assertTrue(state.distanceFraction >= 1f)

        drag(-150f)
        assertEquals(0.75f, state.distanceFraction, 0.01f)

        fling()
        assertEquals(0, refreshed)
        assertEquals(0f, state.distanceFraction, 0.01f)
    }

    @Test
    fun dragging_back_to_zero_hands_the_drag_back_to_the_list() {
        setUp()
        drag(100f)

        // 200 of upward drag would undo 100 of pull; the rest is not ours
        val used = drag(-300f)
        assertEquals(-100f, used.y, 0.01f)
        assertEquals(0f, state.distanceFraction, 0.01f)
        assertEquals(Offset.Zero, drag(-50f))
    }

    @Test
    fun a_stop_with_no_speed_resets_a_short_pull() {
        setUp()
        drag(60f)
        assertTrue(state.distanceFraction > 0f)

        runBlocking { pull.onPreFling(Velocity.Zero) }
        rule.waitForIdle()
        assertEquals(0, refreshed)
        assertEquals(0f, state.distanceFraction, 0.01f)
    }

    @Test
    fun the_fling_after_a_pull_is_taken_whole() {
        setUp()
        drag(300f)
        assertEquals(Velocity(0f, 500f), runBlocking { pull.onPreFling(Velocity(0f, 500f)) })
        assertEquals(Velocity.Zero, runBlocking { pull.onPreFling(Velocity(0f, 500f)) })
    }

    @Test
    fun a_release_while_refreshing_does_not_refresh_again() {
        setUp()
        drag(300f)
        refreshing = true

        fling()
        assertEquals(0, refreshed)
    }

    @Test
    fun nothing_starts_unless_the_footer_fills_the_screen_or_when_refreshing() {
        setUp()
        active = false
        assertEquals(Offset.Zero, drag(300f))
        active = true
        refreshing = true
        assertEquals(Offset.Zero, drag(300f))
        refreshing = false
        assertEquals(Offset.Zero, drag(-300f))
        assertEquals(0f, state.distanceFraction, 0.01f)
    }
}
