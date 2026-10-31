package io.github.bardesss.wick.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    private fun store() = SettingsStore(MemoryPreferences())

    @Test
    fun settings_defaults() = runBlocking {
        val s = store()
        assertFalse(s.oldestFirst.first())
        assertTrue(s.haptics.first())
    }

    @Test
    fun settings_persist() = runBlocking {
        val s = store()
        s.setOldestFirst(true)
        s.setHaptics(false)
        assertTrue(s.oldestFirst.first())
        assertFalse(s.haptics.first())
    }
}
