package io.github.bardesss.wick.format

import org.junit.Assert.assertEquals
import org.junit.Test

class AgeTest {
    private val then = 1_000_000L

    @Test
    fun under_a_minute_is_now() {
        assertEquals(Age.Now, ageOf(then, then))
        assertEquals(Age.Now, ageOf(then + 59, then))
    }

    @Test
    fun minutes() {
        assertEquals(Age.Minutes(1), ageOf(then + 60, then))
        assertEquals(Age.Minutes(12), ageOf(then + 12 * 60 + 30, then))
        assertEquals(Age.Minutes(59), ageOf(then + 3599, then))
    }

    @Test
    fun hours() {
        assertEquals(Age.Hours(1), ageOf(then + 3600, then))
        assertEquals(Age.Hours(3), ageOf(then + 3 * 3600 + 59 * 60, then))
        assertEquals(Age.Hours(23), ageOf(then + 86_399, then))
    }

    @Test
    fun days() {
        assertEquals(Age.Days(1), ageOf(then + 86_400, then))
        assertEquals(Age.Days(2), ageOf(then + 2 * 86_400 + 5, then))
    }

    @Test
    fun future_is_now() {
        assertEquals(Age.Now, ageOf(then, then + 500))
    }
}
