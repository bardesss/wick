package io.github.bardesss.wick.ui.list

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeLogicTest {
    private val threshold = 80f
    private val slop = 8f

    @Test
    fun right_past_80dp_opens_sheet() {
        assertEquals(SwipeOutcome.OpenSheet, swipeOutcome(81f, threshold))
        assertEquals(SwipeOutcome.OpenSheet, swipeOutcome(300f, threshold))
    }

    @Test
    fun left_past_80dp_toggles() {
        assertEquals(SwipeOutcome.ToggleRead, swipeOutcome(-81f, threshold))
        assertEquals(SwipeOutcome.ToggleRead, swipeOutcome(-300f, threshold))
    }

    @Test
    fun under_threshold_none() {
        assertEquals(SwipeOutcome.None, swipeOutcome(0f, threshold))
        assertEquals(SwipeOutcome.None, swipeOutcome(79f, threshold))
        assertEquals(SwipeOutcome.None, swipeOutcome(-79f, threshold))
    }

    @Test
    fun rubber_band_beyond_threshold() {
        // linear up to 110dp at an 80dp threshold, then 0.55 of the extra travel
        assertEquals(50f, rubberBand(50f, threshold), 0.001f)
        assertEquals(100f, rubberBand(100f, threshold), 0.001f)
        assertEquals(110f, rubberBand(110f, threshold), 0.001f)
        assertEquals(165f, rubberBand(210f, threshold), 0.001f)
        assertEquals(-165f, rubberBand(-210f, threshold), 0.001f)
        // scales with the threshold, so it holds at any density
        assertEquals(330f, rubberBand(420f, threshold * 2), 0.001f)
    }

    @Test
    fun rubber_band_inverts() {
        for (dx in listOf(-300f, -110f, -20f, 0f, 50f, 110f, 210f, 400f)) {
            assertEquals(dx, unRubberBand(rubberBand(dx, threshold), threshold), 0.001f)
        }
    }

    @Test
    fun swipe_vertical_dominant_is_ignored() {
        assertEquals(SwipeAxis.Vertical, swipeAxis(dx = 20f, dy = 30f, slop = slop))
        assertEquals(SwipeAxis.Vertical, swipeAxis(dx = 0f, dy = -9f, slop = slop))
        assertEquals(SwipeAxis.Vertical, swipeAxis(dx = 12f, dy = 12f, slop = slop))
        assertEquals(SwipeAxis.Undecided, swipeAxis(dx = 5f, dy = 2f, slop = slop))
        assertEquals(SwipeAxis.Undecided, swipeAxis(dx = 8f, dy = 0f, slop = slop))
        assertEquals(SwipeAxis.Horizontal, swipeAxis(dx = 20f, dy = 5f, slop = slop))
        assertEquals(SwipeAxis.Horizontal, swipeAxis(dx = -20f, dy = 15f, slop = slop))
    }
}
