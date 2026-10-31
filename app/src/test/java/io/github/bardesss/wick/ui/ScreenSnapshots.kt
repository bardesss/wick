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
import org.robolectric.shadows.ShadowDialog
import java.io.File
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalDensity
import io.github.bardesss.wick.html.htmlToAnnotated
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.ui.picker.MarkAllArm
import io.github.bardesss.wick.ui.picker.PickerDrawer
import io.github.bardesss.wick.ui.picker.PickerEntry
import io.github.bardesss.wick.ui.settings.SettingsScreen
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.rememberDrawerState
import io.github.bardesss.wick.ui.sheet.SheetBody
import io.github.bardesss.wick.ui.sheet.SummarySheet

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
        val canvas = android.graphics.Canvas(bmp)
        view.draw(canvas)
        // a sheet lives in a dialog window of its own; lay it over the screen behind it
        ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView?.let { sheet ->
            canvas.save()
            canvas.translate(0f, (view.height - sheet.height).toFloat())
            sheet.draw(canvas)
            canvas.restore()
        }
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
    fun list_clean() = snap("list_clean") {
        ListContent(
            viewName = "All unread",
            count = 87,
            message = null,
            rows = sample,
            read = remember { mutableStateOf(setOf("1", "4")) },
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
    fun list_swipes() = snap("list_swipes") {
        // row 2 dragged right past the threshold, row 5 dragged left past it
        val px = with(LocalDensity.current) { 100.dp.toPx() }
        ListContent(
            viewName = "All unread",
            count = 87,
            message = null,
            rows = sample,
            read = remember { mutableStateOf(setOf("1", "4", "6")) },
            nowSec = now,
            refreshing = false,
            listState = rememberLazyListState(),
            onRefresh = {},
            onOpenPicker = {},
            onOpen = {},
            onFrame = { false },
            swipePreview = mapOf("2" to px, "5" to -px),
        )
    }

    private val article = """
        <p>Ten years ago nearly every site carried a small orange icon. Most of those sites still
        publish feeds today; they just stopped telling anyone about them.</p>
        <p>The <a href="https://e.x/survey">survey</a> looked at 4,000 sites &amp; found three patterns:</p>
        <ul><li>feeds still work but are <em>hidden</em></li><li>feeds were dropped in a redesign</li>
        <li>feeds only carry a summary</li></ul>
        <p>Whatever text the feed includes shows up here. <strong>Long text scrolls</strong> inside the sheet.</p>
        <script>track()</script><img src="hero.jpg">
    """.trimIndent()

    @OptIn(ExperimentalMaterial3Api::class)
    private fun sheet(name: String, html: String?) = snap(name) {
        ListContent(
            viewName = "All unread",
            count = 87,
            message = null,
            rows = sample,
            read = remember { mutableStateOf(setOf("1", "4", "6")) },
            nowSec = now,
            refreshing = false,
            listState = rememberLazyListState(),
            onRefresh = {},
            onOpenPicker = {},
            onOpen = {},
            onFrame = { false },
        )
        SummarySheet(
            row = sample[4],
            body = SheetBody.Ready(htmlToAnnotated(html)),
            nowSec = now,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onOpen = {},
            onDismiss = {},
        )
    }

    @Test
    fun sheet_article() = sheet("sheet_article", article)

    @Test
    fun sheet_empty() = sheet("sheet_empty", null)

    @Test
    fun list_caught_up() = snap("list_caught_up") {
        ListContent(
            viewName = "Hospitality",
            count = 0,
            message = null,
            rows = emptyList(),
            read = remember { mutableStateOf(emptySet()) },
            nowSec = now,
            refreshing = false,
            listState = rememberLazyListState(),
            onRefresh = {},
            onOpenPicker = {},
            onOpen = {},
            onFrame = { false },
        )
    }

    private val pickerEntries = listOf(
        PickerEntry.All(87),
        PickerEntry.Folder("Food", 0),
        PickerEntry.Feed("feed/6", "Cooking Weekly", null, 0, indented = true),
        PickerEntry.Folder("Tech", 71),
        PickerEntry.Feed("feed/3", "Android Developers", null, 12, indented = true),
        PickerEntry.Feed("feed/2", "Hacker News", null, 52, indented = true),
        PickerEntry.Feed("feed/7", "Simon Willison", null, 7, indented = true),
        PickerEntry.Folder("Travel", 16),
        PickerEntry.Feed("feed/4", "Rail News", null, 16, indented = true),
        PickerEntry.Feed("feed/5", "Alpha notes", null, 0, indented = false),
        PickerEntry.Feed("feed/1", "Personal blog", null, 0, indented = false),
    )

    private fun drawer(name: String, armed: Boolean) = snap(name) {
        val arm = remember { MarkAllArm().also { if (armed) it.arm() } }
        PickerDrawer(
            state = rememberDrawerState(DrawerValue.Open),
            entries = pickerEntries,
            selected = StreamId.Feed("feed/2"),
            markCount = 52,
            markEnabled = true,
            armed = arm.armed,
            animate = false,
            onSelect = {},
            onMarkAll = {},
            onSettings = {},
        ) {
            ListContent(
                viewName = "Hacker News",
                count = 52,
                message = null,
                rows = sample,
                read = remember { mutableStateOf(setOf("1", "4", "6")) },
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

    @Test
    fun drawer_open() = drawer("drawer_open", armed = false)

    @Test
    fun drawer_armed() = drawer("drawer_armed", armed = true)

    @Test
    fun settings() = snap("settings") {
        SettingsScreen(
            oldestFirst = false,
            haptics = true,
            server = "https://feeds.example.com",
            onOldestFirst = {},
            onHaptics = {},
            onSignOut = {},
            version = "0.1.0",
        )
    }
}
