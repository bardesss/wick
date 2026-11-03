package io.github.bardesss.wick.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import coil3.request.SuccessResult
import io.github.bardesss.wick.ui.theme.WickTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val RED = 0xFFE0401F.toInt()

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FaviconFadeTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @OptIn(DelicateCoilApi::class)
    @Before
    fun redIcons() {
        val red = Bitmap.createBitmap(60, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(RED) }
        val loader = ImageLoader.Builder(rule.activity)
            .components {
                add(
                    object : Interceptor {
                        override suspend fun intercept(chain: Interceptor.Chain): ImageResult =
                            SuccessResult(red.asImage(), chain.request, DataSource.MEMORY)
                    },
                )
            }
            .build()
        SingletonImageLoader.setUnsafe(loader)
    }

    private fun pixel(i: Int): Int {
        val img = rule.onAllNodesWithTag("icon")[i].captureToImage().asAndroidBitmap()
        return img.getPixel(img.width / 2, img.height / 2)
    }

    private fun show(rows: () -> List<Pair<String, Boolean>>) = rule.setContent {
        WickTheme {
            LazyColumn {
                items(rows(), key = { it.first }, contentType = { "item" }) { (_, read) ->
                    Favicon("https://e.x/icon.png", "Ars", faded = read, modifier = Modifier.testTag("icon"))
                }
            }
        }
    }

    @Test
    fun a_row_marked_unread_again_is_not_faded() {
        var read by mutableStateOf(true)
        show { (1..3).map { "a$it" to read } }
        rule.waitForIdle()
        // read: grey, so red no longer stands out from the other channels
        val faded = pixel(0)
        val (r, g, b) = Triple(faded shr 16 and 0xFF, faded shr 8 and 0xFF, faded and 0xFF)
        assertTrue("faded ${Integer.toHexString(faded)}", maxOf(r, g, b) - minOf(r, g, b) < 0x10)
        read = false
        rule.waitForIdle()

        for (i in 0 until 3) assertEquals("row $i", Integer.toHexString(RED), Integer.toHexString(pixel(i)))
    }

    @Test
    fun unread_rows_scrolled_in_after_read_ones_are_not_faded() {
        // read rows first, as after scrolling marks them; the unread ones below reuse their slots
        val rows = (0 until 60).map { "r$it" to (it % 3 != 2) }
        lateinit var state: LazyListState
        rule.setContent {
            WickTheme {
                state = rememberLazyListState()
                LazyColumn(state = state) {
                    items(rows, key = { it.first }, contentType = { "item" }) { (_, read) ->
                        Favicon("https://e.x/icon.png", "Ars", faded = read, modifier = Modifier.testTag(if (read) "read" else "icon"))
                    }
                }
            }
        }
        for (step in 1..40) {
            rule.runOnIdle { runBlocking { state.scrollBy(37f) } }
        }
        rule.waitForIdle()
        val n = rule.onAllNodesWithTag("icon").fetchSemanticsNodes().size
        for (i in 0 until n) assertEquals("row $i", Integer.toHexString(RED), Integer.toHexString(pixel(i)))
    }

    @Test
    fun an_unread_row_reusing_a_read_one_is_not_faded() {
        // a list of read rows, then a new list under other keys, as when another feed opens
        var rows by mutableStateOf((1..6).map { "a$it" to true })
        show { rows }
        rule.waitForIdle()
        rows = (1..6).map { "b$it" to false }
        rule.waitForIdle()

        for (i in 0 until 6) assertEquals("row $i", Integer.toHexString(RED), Integer.toHexString(pixel(i)))
    }
}
