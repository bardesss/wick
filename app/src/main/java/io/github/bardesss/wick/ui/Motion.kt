package io.github.bardesss.wick.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/** True when the system "remove animations" setting is on. */
fun animationsOff(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** [animationsOff], read again each time the screen resumes. */
@Composable
fun rememberReducedMotion(): State<Boolean> {
    val context = LocalContext.current
    val off = remember { mutableStateOf(animationsOff(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { off.value = animationsOff(context) }
    return off
}
