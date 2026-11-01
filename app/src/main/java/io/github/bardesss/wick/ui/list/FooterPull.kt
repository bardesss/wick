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
 * items. While [active] says the footer fills the screen, this takes that drag.
 *
 * It sits on a parent of the pull to refresh box, so it sees every drag and fling first, and while
 * it pulls it takes the whole vertical drag: the box and the list see nothing. The pull has a
 * [state] of its own, apart from the box's, so the box never reacts to it.
 *
 * Compose ends every drag with a fling, also one with no speed and one that was cancelled. That
 * is where a pull ends, so a system cancel past the threshold counts as a release.
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

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        if (!pulling) {
            if (refreshing() || available.y <= 0f || !active()) return Offset.Zero
            pulling = true
        }
        // dragging back up shortens the pull; at zero the rest of the drag goes back to the list
        val next = (distance + available.y * DRAG).coerceAtLeast(0f)
        val used = (next - distance) / DRAG
        distance = next
        if (next == 0f) pulling = false
        val fraction = next / thresholdPx
        scope.launch { state.snapTo(fraction) }
        return Offset(0f, used)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (!pulling) return Velocity.Zero
        val reached = distance >= thresholdPx
        pulling = false
        distance = 0f
        // a refresh that started elsewhere has its own indicator, so this one goes away
        if (reached && !refreshing()) onRefresh() else scope.launch { state.animateToHidden() }
        return available
    }

    private companion object {
        // the same damping the pull to refresh box applies
        const val DRAG = 0.5f
    }
}
