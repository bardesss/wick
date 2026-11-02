package io.github.bardesss.wick.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.toBitmap
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.api.FaviconAuth
import io.github.bardesss.wick.buildImageLoader
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// ImageDecoder does not run under Robolectric on Windows; below API 28 Coil uses BitmapFactory
@Config(sdk = [27])
class FaviconCacheTest {
    private val server = MockWebServer()

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun caches_use_the_plain_url_while_the_request_carries_the_key() {
        val png = ByteArrayOutputStream().also {
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(png)))
        server.start()
        val auth = Auth(server.url("/").toString().trimEnd('/'), "alice/k3y")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "favicon-test").apply { deleteRecursively() }
        val loader = buildImageLoader(context, OkHttpClient.Builder().addInterceptor(FaviconAuth { auth }).build(), dir)
        val url = server.url("/rest/feed/favicon/3").toString()

        // Coil hops to the main thread, which only runs while this test thread idles its looper
        var result: ImageResult? = null
        val worker = thread { result = runBlocking { loader.execute(ImageRequest.Builder(context).data(url).build()) } }
        val deadline = System.currentTimeMillis() + 20_000
        while (worker.isAlive && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }

        assertEquals("k3y", server.takeRequest().requestUrl!!.queryParameter("apiKey"))
        assertTrue("load failed: $result", result is SuccessResult)
        loader.diskCache!!.openSnapshot(url).use { assertNotNull(it) }
        val memoryKeys = loader.memoryCache!!.keys.map { it.key }
        assertTrue(memoryKeys.toString(), url in memoryKeys)
        assertTrue(memoryKeys.none { "apiKey" in it })
    }

    @Test
    fun an_svg_icon_loads() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"><circle cx="8" cy="8" r="7" fill="#d33"/></svg>"""
        assertTrue(inkedPixels(loadSvg(svg)) > 0)
    }

    @Test
    fun an_svg_icon_with_wide_gamut_colours_still_draws() {
        // the shape design tools export: a plain colour, then a display-p3 one in the same style
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="27" height="36" fill="none" viewBox="0 0 27 36">""" +
            """<g clip-path="url(#c)"><path fill="#fb4c01" d="M2 2h23v32H2z" style="fill:#fb4c01;fill:color(display-p3 .98 .3 0);fill-opacity:1"/></g>""" +
            """<defs><clipPath id="c"><path fill="#fff" d="M0 0h27v36H0z" style="fill:#fff;fill-opacity:1"/></clipPath></defs></svg>"""
        assertTrue(inkedPixels(loadSvg(svg)) > 0)
    }

    private fun loadSvg(svg: String): Bitmap {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/svg+xml").setBody(svg))
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "favicon-svg-test").apply { deleteRecursively() }
        val loader = buildImageLoader(context, OkHttpClient(), dir)
        val url = server.url("/rest/feed/favicon/4").toString()

        var result: ImageResult? = null
        val worker = thread {
            result = runBlocking { loader.execute(ImageRequest.Builder(context).data(url).size(40).build()) }
        }
        val deadline = System.currentTimeMillis() + 20_000
        while (worker.isAlive && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }

        val success = result as? SuccessResult ?: throw AssertionError("load failed: $result")
        return success.image.toBitmap()
    }

    private fun inkedPixels(bitmap: Bitmap): Int {
        var n = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) if (bitmap.getPixel(x, y) ushr 24 != 0) n++
        return n
    }
}
