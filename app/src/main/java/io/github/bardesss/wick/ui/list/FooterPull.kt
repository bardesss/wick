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
        if (source != NestedScrollSource.UserInput || refreshing()) return Offset.Zero
        if (!pulling && !(available.y > 0f && active())) return Offset.Zero
        pulling = true
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
        if (reached) onRefresh() else state.animateToHidden()
        return available
    }

    private companion object {
        // the same damping the pull to refresh box applies
        const val DRAG = 0.5f
    }
}
