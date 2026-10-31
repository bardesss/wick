package io.github.bardesss.wick.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bardesss.wick.ui.login.LoginContent
import io.github.bardesss.wick.ui.login.LoginState
import io.github.bardesss.wick.ui.theme.WickColors
import io.github.bardesss.wick.ui.theme.WickTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Viewing aid: writes PNGs to app/build/snapshots. It only checks the image is not empty. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenSnapshots {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun snap(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent { WickTheme { content() } }
        rule.waitForIdle()
        // captureToImage waits on a draw callback that never fires under Robolectric on this host, so draw the view directly.
        val view = rule.activity.window.decorView
        val bmp = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(bmp))
        assertTrue(bmp.width > 0 && bmp.height > 0)
        val dir = File("build/snapshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun login_empty() = snap("login_empty") { LoginContent(LoginState()) { _, _, _ -> } }

    @Test
    fun login_error() = snap("login_error") {
        LoginContent(LoginState(error = "Username or API key is wrong")) { _, _, _ -> }
    }

    @Test
    fun wordmark() = snap("wordmark") {
        Column(Modifier.background(WickColors.Night).padding(24.dp)) {
            Wordmark()
            Wordmark(Modifier.padding(top = 24.dp), size = 112.sp)
        }
    }
}
