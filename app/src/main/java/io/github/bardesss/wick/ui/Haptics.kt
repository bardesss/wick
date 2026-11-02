package io.github.bardesss.wick.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.Flow

/** Feedback for gestures and committed actions, off when the haptics setting is off. */
class Haptics(private val view: View, private val enabled: Flow<Boolean>) {
    // stays off until the setting has been read once
    @Volatile
    private var on = false

    /** Follows the setting until the calling coroutine is cancelled. */
    suspend fun follow() {
        enabled.collect { on = it }
    }

    /** A swipe crossed the point where letting go acts. */
    fun threshold() = perform(
        when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        },
    )

    /** An action went through, like marking a whole view read. */
    fun confirm() = perform(
        when {
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.LONG_PRESS
        },
    )

    private fun perform(type: Int) {
        if (on) view.performHapticFeedback(type)
    }
}

/** One [Haptics] per screen, following [enabled] while the screen is shown. */
@Composable
fun rememberHaptics(enabled: Flow<Boolean>): Haptics {
    val view = LocalView.current
    val haptics = remember(view, enabled) { Haptics(view, enabled) }
    LaunchedEffect(haptics) { haptics.follow() }
    return haptics
}
