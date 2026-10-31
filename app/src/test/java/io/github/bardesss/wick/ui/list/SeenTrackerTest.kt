package io.github.bardesss.wick.ui.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeenTrackerTest {
    private val rowPx = 100
    private val viewportPx = 300
    private val frameMs = 16L

    /** Every row at scroll position [scroll]: rows that left the top report offTop. */
    private fun layout(scroll: Double, rows: Int): List<Visibility> = (0 until rows).map { i ->
        val top = i * rowPx - scroll
        val bottom = top + rowPx
        val shown = (minOf(bottom, viewportPx.toDouble()) - maxOf(top, 0.0)).coerceAtLeast(0.0)
        Visibility(id = "r$i", fraction = (shown / rowPx).toFloat(), offTop = bottom <= 0)
    }

    /** Scrolls from 0 until every row is off the top at [pxPerSec], then returns the marked ids in order. */
    private fun scrollAll(rows: Int, pxPerSec: Double, tracker: SeenTracker = SeenTracker()): List<String> {
        val marked = mutableListOf<String>()
        var t = 0L
        var scroll = 0.0
        val end = (rows * rowPx).toDouble()
        while (true) {
            marked += tracker.onFrame(t, layout(scroll, rows), emptySet())
            if (scroll >= end) break
            t += frameMs
            scroll = minOf(end, scroll + pxPerSec * frameMs / 1000.0)
        }
        // one more frame after stopping
        marked += tracker.onFrame(t + frameMs, layout(end, rows), emptySet())
        return marked
    }

    private fun hold(tracker: SeenTracker, id: String, fraction: Float, fromMs: Long, forMs: Long, exempt: Set<String> = emptySet()): List<String> {
        val out = mutableListOf<String>()
        var t = fromMs
        while (t <= fromMs + forMs) {
            out += tracker.onFrame(t, listOf(Visibility(id, fraction, offTop = false)), exempt)
            t += frameMs
        }
        return out
    }

    @Test
    fun slow_scroll_marks_each_item() {
        val marked = scrollAll(rows = 6, pxPerSec = 150.0)
        assertEquals((0 until 6).map { "r$it" }, marked)
    }

    @Test
    fun fling_under_250ms_marks_nothing() {
        val marked = scrollAll(rows = 80, pxPerSec = 20_000.0)
        assertEquals(emptyList<String>(), marked)
    }

    @Test
    fun half_visible_threshold() {
        val tracker = SeenTracker()
        assertTrue(hold(tracker, "under", 0.49f, fromMs = 0, forMs = 1000).isEmpty())
        assertEquals(emptyList<String>(), tracker.onFrame(1100, listOf(Visibility("under", 0f, offTop = true)), emptySet()))

        assertTrue(hold(tracker, "half", 0.5f, fromMs = 2000, forMs = 300).isEmpty())
        assertEquals(listOf("half"), tracker.onFrame(2400, listOf(Visibility("half", 0f, offTop = true)), emptySet()))
    }

    @Test
    fun visible_time_adds_up_over_separate_visits() {
        val tracker = SeenTracker()
        hold(tracker, "a", 1f, fromMs = 0, forMs = 150)
        tracker.onFrame(500, emptyList(), emptySet())
        hold(tracker, "a", 1f, fromMs = 1000, forMs = 150)
        assertEquals(listOf("a"), tracker.onFrame(1200, listOf(Visibility("a", 0f, offTop = true)), emptySet()))
    }

    @Test
    fun exempt_ids_never_marked() {
        val tracker = SeenTracker()
        val exempt = setOf("a")
        assertTrue(hold(tracker, "a", 1f, fromMs = 0, forMs = 500, exempt = exempt).isEmpty())
        repeat(10) { n ->
            assertEquals(
                emptyList<String>(),
                tracker.onFrame(600L + n * frameMs, listOf(Visibility("a", 0f, offTop = true)), exempt),
            )
        }
    }

    @Test
    fun returns_each_id_once() {
        val tracker = SeenTracker()
        hold(tracker, "a", 1f, fromMs = 0, forMs = 500)
        val out = mutableListOf<String>()
        repeat(10) { n -> out += tracker.onFrame(600L + n * frameMs, listOf(Visibility("a", 0f, offTop = true)), emptySet()) }
        assertEquals(listOf("a"), out)
    }

    @Test
    fun stops_asking_for_frames_once_visible_rows_have_dwelt() {
        val tracker = SeenTracker()
        tracker.onFrame(0, listOf(Visibility("a", 1f, offTop = false)), emptySet())
        assertTrue(tracker.needsFrames())
        hold(tracker, "a", 1f, fromMs = 16, forMs = 300)
        assertFalse(tracker.needsFrames())
    }
}
