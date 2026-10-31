package io.github.bardesss.wick.format

import org.junit.Assert.assertEquals
import org.junit.Test

class AgeTest {
    private val then = 1_000_000L

    @Test
    fun under_a_minute_is_now() {
        assertEquals("now", formatAge(then, then))
        assertEquals("now", formatAge(then + 59, then))
    }

    @Test
    fun minutes() {
        assertEquals("1m", formatAge(then + 60, then))
        assertEquals("12m", formatAge(then + 12 * 60 + 30, then))
        assertEquals("59m", formatAge(then + 3599, then))
    }

    @Test
    fun hours() {
        assertEquals("1h", formatAge(then + 3600, then))
        assertEquals("3h", formatAge(then + 3 * 3600 + 59 * 60, then))
        assertEquals("23h", formatAge(then + 86_399, then))
    }

    @Test
    fun days() {
        assertEquals("1d", formatAge(then + 86_400, then))
        assertEquals("2d", formatAge(then + 2 * 86_400 + 5, then))
    }

    @Test
    fun future_is_now() {
        assertEquals("now", formatAge(then, then + 500))
    }
}
