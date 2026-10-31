package io.github.bardesss.wick.ui.list

/** How much of a row is on screen, and whether its bottom edge has passed the top of the list. */
data class Visibility(val id: String, val fraction: Float, val offTop: Boolean)

/** A row as the list laid it out in one frame. Offsets are in the list's viewport coordinates. */
data class FrameItem(val id: String, val index: Int, val offset: Int, val size: Int)

data class ListFrame(val nowMs: Long, val viewportStart: Int, val viewportEnd: Int, val items: List<FrameItem>)

/**
 * Decides which rows were seen. A row earns visible time while at least [minVisibleFraction]
 * of it is on screen, and is returned once it leaves the top with at least [dwellMs] earned.
 * The time between two frames goes to the rows that were showing in the earlier one.
 */
class SeenTracker(private val dwellMs: Long = 250, private val minVisibleFraction: Float = 0.5f) {
    private val seenMs = HashMap<String, Long>()
    private val returned = HashSet<String>()
    private var shown: List<String> = emptyList()
    private var lastMs = -1L

    fun onFrame(nowMs: Long, visible: List<Visibility>, exempt: Set<String>): List<String> {
        if (lastMs >= 0) {
            // a stalled frame clock (app paused) must not count as time on screen
            val dt = (nowMs - lastMs).coerceIn(0, MAX_FRAME_GAP_MS)
            for (id in shown) seenMs[id] = (seenMs[id] ?: 0) + dt
        }
        lastMs = nowMs

        var out: MutableList<String>? = null
        val nowShown = ArrayList<String>(visible.size)
        for (v in visible) {
            if (v.id in returned) continue
            if (v.offTop) {
                if (v.id !in exempt && (seenMs[v.id] ?: 0) >= dwellMs) {
                    returned += v.id
                    (out ?: ArrayList<String>().also { out = it }) += v.id
                }
            } else if (v.fraction >= minVisibleFraction) {
                nowShown += v.id
            }
        }
        shown = nowShown
        return out ?: emptyList()
    }

    /** True while a row on screen has not earned its dwell time yet. */
    fun needsFrames(): Boolean = shown.any { (seenMs[it] ?: 0) < dwellMs }

    fun reset() {
        seenMs.clear()
        returned.clear()
        shown = emptyList()
        lastMs = -1
    }

    private companion object {
        const val MAX_FRAME_GAP_MS = 100L
    }
}
