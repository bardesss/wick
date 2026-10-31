package io.github.bardesss.wick.ui.list

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import io.github.bardesss.wick.ui.theme.WickColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

enum class SwipeOutcome { OpenSheet, ToggleRead, None }

enum class SwipeAxis { Horizontal, Vertical, Undecided }

/** Distance at which a released swipe does something. */
val SwipeThreshold = 80.dp

// the row follows the finger 1:1 up to 110dp at the 80dp threshold, then resists
private const val LINEAR_FACTOR = 110f / 80f
private const val RESIST = 0.55f

fun swipeOutcome(dxPx: Float, thresholdPx: Float): SwipeOutcome = when {
    dxPx >= thresholdPx -> SwipeOutcome.OpenSheet
    dxPx <= -thresholdPx -> SwipeOutcome.ToggleRead
    else -> SwipeOutcome.None
}

fun rubberBand(dxPx: Float, thresholdPx: Float): Float {
    val linear = thresholdPx * LINEAR_FACTOR
    val d = abs(dxPx)
    return if (d <= linear) dxPx else sign(dxPx) * (linear + (d - linear) * RESIST)
}

/** The finger travel that [rubberBand] turns into [offsetPx]. */
fun unRubberBand(offsetPx: Float, thresholdPx: Float): Float {
    val linear = thresholdPx * LINEAR_FACTOR
    val d = abs(offsetPx)
    return if (d <= linear) offsetPx else sign(offsetPx) * (linear + (d - linear) / RESIST)
}

/** Which way a touch is going. A row only claims it once it is past [slop] and mostly sideways. */
fun swipeAxis(dx: Float, dy: Float, slop: Float): SwipeAxis = when {
    abs(dx) > slop && abs(dx) > abs(dy) -> SwipeAxis.Horizontal
    abs(dy) > slop && abs(dy) >= abs(dx) -> SwipeAxis.Vertical
    else -> SwipeAxis.Undecided
}

/** Lets one row at a time follow a finger. Not state: nothing draws from it. */
class SwipeLock {
    private var owner: String? = null

    fun claim(id: String): Boolean {
        if (owner != null && owner != id) return false
        owner = id
        return true
    }

    fun release(id: String) {
        if (owner == id) owner = null
    }
}

private val IconSize = 20.dp
private val IconInset = 24.dp
private val FullOpacityAt = 70.dp
private val SettleSpring = spring<Float>(dampingRatio = 0.8f)

// 24 unit viewbox, the same strokes as the prototype icons; lazy so the pure functions above load without graphics
private val LinesIcon by lazy {
    Path().apply {
        moveTo(5f, 6f); lineTo(19f, 6f)
        moveTo(5f, 11f); lineTo(19f, 11f)
        moveTo(5f, 16f); lineTo(14f, 16f)
    }
}
private val CheckIcon by lazy {
    Path().apply { moveTo(5f, 12.5f); lineTo(9.5f, 17f); lineTo(19f, 7.5f) }
}
private val IconStroke = Stroke(width = 1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)

/**
 * A list row that can be dragged sideways. The offset is read only while laying out and
 * drawing, so a drag never recomposes the row or the list.
 */
@Composable
fun SwipeRow(
    id: String,
    lock: SwipeLock,
    reducedMotion: Boolean,
    onThreshold: () -> Unit,
    onSwipe: (SwipeOutcome) -> Unit,
    modifier: Modifier = Modifier,
    startOffsetPx: Float = 0f,
    content: @Composable (Modifier) -> Unit,
) {
    val offset = remember { mutableFloatStateOf(startOffsetPx) }
    val scope = rememberCoroutineScope()
    val tick by rememberUpdatedState(onThreshold)
    val done by rememberUpdatedState(onSwipe)
    val still by rememberUpdatedState(reducedMotion)
    val settle = remember { arrayOfNulls<Job>(1) }

    Box(
        modifier
            .drawBehind { drawUnder(offset.floatValue) }
            .pointerInput(id, lock) {
                val threshold = SwipeThreshold.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var dx = 0f
                    var dy = 0f
                    // watch until the touch shows its direction; a scroll that claims it first wins
                    while (true) {
                        val change = currentEvent(down.id) ?: return@awaitEachGesture
                        if (!change.pressed || change.isConsumed) return@awaitEachGesture
                        val d = change.positionChange()
                        dx += d.x
                        dy += d.y
                        when (swipeAxis(dx, dy, viewConfiguration.touchSlop)) {
                            SwipeAxis.Vertical -> return@awaitEachGesture
                            SwipeAxis.Horizontal -> {
                                if (!lock.claim(id)) return@awaitEachGesture
                                change.consume()
                                break
                            }
                            SwipeAxis.Undecided -> Unit
                        }
                    }
                    // pick up from wherever a spring back left the row, and count travel from the slop on
                    settle[0]?.cancel()
                    dx = unRubberBand(offset.floatValue, threshold) + dx - sign(dx) * viewConfiguration.touchSlop
                    var past = abs(dx) >= threshold
                    if (past) tick()
                    offset.floatValue = rubberBand(dx, threshold)
                    var released = false
                    try {
                        while (true) {
                            val change = currentEvent(down.id) ?: break
                            if (!change.pressed) {
                                released = true
                                change.consume()
                                break
                            }
                            dx += change.positionChange().x
                            change.consume()
                            offset.floatValue = rubberBand(dx, threshold)
                            val nowPast = abs(dx) >= threshold
                            if (nowPast && !past) tick()
                            past = nowPast
                        }
                    } finally {
                        lock.release(id)
                        val from = offset.floatValue
                        settle[0] = scope.launch {
                            if (still) {
                                offset.floatValue = 0f
                            } else {
                                animate(from, 0f, animationSpec = SettleSpring) { v, _ -> offset.floatValue = v }
                            }
                        }
                    }
                    if (released) {
                        val outcome = swipeOutcome(dx, threshold)
                        if (outcome != SwipeOutcome.None) done(outcome)
                    }
                }
            },
    ) {
        content(Modifier.graphicsLayer { translationX = offset.floatValue }.background(WickColors.Night))
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.currentEvent(
    pointer: androidx.compose.ui.input.pointer.PointerId,
) = awaitPointerEvent().changes.firstOrNull { it.id == pointer }

private fun DrawScope.drawUnder(x: Float) {
    if (x == 0f) return
    val alpha = (abs(x) / FullOpacityAt.toPx()).coerceAtMost(1f)
    drawRect(if (x > 0f) WickColors.Dusk else WickColors.Sage, alpha = alpha)
    val icon = IconSize.toPx()
    val left = if (x > 0f) IconInset.toPx() else size.width - IconInset.toPx() - icon
    translate(left, (size.height - icon) / 2f) {
        withTransform({ scale(icon / 24f, icon / 24f, pivot = Offset.Zero) }) {
            drawPath(if (x > 0f) LinesIcon else CheckIcon, WickColors.Paper, alpha = alpha, style = IconStroke)
        }
    }
}
