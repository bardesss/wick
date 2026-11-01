package io.github.bardesss.wick.ui.list

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pull to refresh from the "All caught up" footer. Pull to refresh only starts at the top of the
 * list, but once the footer fills the screen a downward drag would scroll back up through the read
 * items. While [active] says the footer fills the screen, this takes that drag and feeds it to
 * [state], the same state the pull to refresh box draws its indicator from.
 *
 * Only this connection moves the indicator while the footer is pulled: it takes the whole drag, so
 * the box's own connection sees nothing, and it is the only one that calls [onRefresh]. A gesture
 * ends in [release], from the pointer going up or being cancelled, and from the fling.
 */
@OptIn(ExperimentalMaterial3Api::class)
class FooterPull(
    private val state: PullToRefreshState,
    private val scope: CoroutineScope,
    private val thresholdPx: Float,
    private val active: () -> Boolean,
    private val refreshing: () -> Boolean,
    private val onRefresh: () -> Unit,
) : NestedScrollConnection {
    private var pulling = false
    private var distance = 0f
    // the fling that follows a pull must not carry the list back up
    private var flingPending = false

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        flingPending = false
        if (!pulling) {
            if (refreshing() || available.y <= 0f || !active()) return Offset.Zero
            pulling = true
        }
        // dragging back up shortens the pull; at zero the drag goes back to the list
        val next = (distance + available.y * DRAG).coerceAtLeast(0f)
        val used = (next - distance) / DRAG
        distance = next
        if (next == 0f) pulling = false
        val fraction = next / thresholdPx
        scope.launch { state.snapTo(fraction) }
        return Offset(0f, used)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        release()
        if (!flingPending) return Velocity.Zero
        flingPending = false
        return available
    }

    /** The finger is up or the gesture was cancelled: refresh when the pull got far enough, else hide the indicator. */
    fun release() {
        if (!pulling) return
        val reached = distance >= thresholdPx
        pulling = false
        flingPending = true
        distance = 0f
        // when a refresh is already running the box owns the indicator
        if (refreshing()) return
        if (reached) onRefresh() else scope.launch { state.animateToHidden() }
    }

    /** The gesture was cancelled: hide the indicator without refreshing. */
    fun cancel() {
        if (!pulling) return
        pulling = false
        distance = 0f
        scope.launch { state.animateToHidden() }
    }

    private companion object {
        // the same damping the pull to refresh box applies
        const val DRAG = 0.5f
    }
}
