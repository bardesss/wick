package io.github.bardesss.wick.ui

import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.system.measureTimeMillis

class SvgIconsTest {
    @Test
    fun wide_gamut_fill_is_dropped() {
        val svg = """<path style="fill:#fb4c01;fill:color(display-p3 .98 .3 0);fill-opacity:1"/>"""
        val out = withoutWideGamutColours(svg)
        assertTrue(out.contains("fill:#fb4c01"))
        assertFalse(out.contains("color("))
        assertTrue(out.contains("fill-opacity:1"))
    }

    @Test
    fun wide_gamut_is_dropped_at_the_start_of_an_attribute_or_after_a_space() {
        assertEquals("""<p style=""/>""", withoutWideGamutColours("""<p style="fill:color(display-p3 1 0 0)"/>"""))
        assertEquals(
            """<p style="a:1; "/>""",
            withoutWideGamutColours("""<p style="a:1; stroke : color(display-p3 1 0 0);"/>"""),
        )
    }

    @Test
    fun long_run_of_letters_is_quick() {
        val text = "a".repeat(200_000)
        val ms = measureTimeMillis { withoutWideGamutColours(text) }
        assertTrue("took $ms ms", ms < 1000)
    }

    @Test(expected = IOException::class)
    fun oversized_svg_is_refused() {
        readSvgText(Buffer().write(ByteArray((MAX_SVG_BYTES + 1).toInt())))
    }

    @Test
    fun svg_of_exactly_the_limit_is_read() {
        assertEquals(10, readSvgText(Buffer().writeUtf8("a".repeat(10)), maxBytes = 10).length)
    }
}
