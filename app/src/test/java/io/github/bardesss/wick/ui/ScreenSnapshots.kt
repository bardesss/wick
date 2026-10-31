package io.github.bardesss.wick.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import io.github.bardesss.wick.ui.list.ListContent
import io.github.bardesss.wick.ui.list.RowModel
import io.github.bardesss.wick.ui.login.LoginContent
import io.github.bardesss.wick.ui.login.LoginMessages
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

    private val now = 1_790_000_000L

    private val sample = listOf(
        RowModel("1", "Restaurant margins in 2026: what actually changed", "https://e.x/1", "Hospitality Today", null, now - 2 * 3600),
        RowModel("2", "Why the small kitchen supplier you have never heard of keeps winning tenders across the whole of the Benelux, and what the big wholesalers are doing about it this year", "https://e.x/2", "Trade Weekly", null, now - 12 * 60),
        RowModel("3", "Kotlin 2.4 ships with faster builds", "https://e.x/3", "Android Developers", null, now - 40),
        RowModel("4", "A short one", "https://e.x/4", "Lobsters", null, now - 3 * 86_400),
        RowModel("5", "Notes on reading feeds on a phone without the noise, part three of a series that has gone on far longer than planned", "https://e.x/5", "Personal blog", null, now - 5 * 3600),
        RowModel("6", "The night train is back", "https://e.x/6", "Rail News", null, now - 26 * 3600),
        RowModel("7", "Espresso machines, ranked by how often they break", null, "Coffee Review", null, now - 7 * 86_400),
    )

    @Test
    fun list_mixed() = snap("list_mixed") {
        ListContent(
            viewName = "All unread",
            count = 87,
            message = LoginMessages.UNREACHABLE,
            rows = sample,
            read = remember { mutableStateOf(setOf("1", "4", "6")) },
            loaded = true,
            nowSec = now,
            refreshing = false,
            listState = rememberLazyListState(),
            onRefresh = {},
            onOpenPicker = {},
            onOpen = {},
            onFrame = { false },
        )
    }

    @Test
    fun list_caught_up() = snap("list_caught_up") {
        ListContent(
            viewName = "Hospitality",
            count = 0,
            message = null,
            rows = emptyList(),
            read = remember { mutableStateOf(emptySet()) },
            loaded = true,
            nowSec = now,
            refreshing = false,
            listState = rememberLazyListState(),
            onRefresh = {},
            onOpenPicker = {},
            onOpen = {},
            onFrame = { false },
        )
    }
}