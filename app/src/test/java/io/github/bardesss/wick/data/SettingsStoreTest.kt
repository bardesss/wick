package io.github.bardesss.wick.data

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsStoreTest {
    private fun store() = SettingsStore(MemoryPreferences())

    @Test
    fun settings_defaults() = runBlocking {
        val s = store()
        assertFalse(s.oldestFirst.first())
        assertTrue(s.haptics.first())
        assertFalse(s.hideEmpty.first())
        assertTrue(s.leadImages.first())
        assertEquals(LinkTarget.InApp, s.linkTarget.first())
    }

    @Test
    fun folder_folding_toggles_and_persists() = runBlocking {
        val ds = MemoryPreferences()
        val s = SettingsStore(ds)
        assertEquals(emptySet<String>(), s.collapsedFolders.first())

        s.toggleCollapsed("Tech")
        s.toggleCollapsed("Food")
        assertEquals(setOf("Tech", "Food"), SettingsStore(ds).collapsedFolders.first())

        s.toggleCollapsed("Tech")
        assertEquals(setOf("Food"), SettingsStore(ds).collapsedFolders.first())

        s.clearCollapsed()
        assertEquals(emptySet<String>(), s.collapsedFolders.first())
    }

    @Test
    fun v010_account_gets_the_api_path_once() = runBlocking {
        val ds = MemoryPreferences()
        ds.edit { it[stringPreferencesKey("account")] = "https://feeds.example.com\nme" }
        val s = SettingsStore(ds)
        assertEquals("https://feeds.example.com/rest/googlereader\nme", s.account())
        assertEquals("https://feeds.example.com/rest/googlereader\nme", SettingsStore(ds).account())
        s.setAccount(null)
        assertNull(s.account())
    }

    @Test
    fun settings_persist() = runBlocking {
        val s = store()
        s.setLinkTarget(LinkTarget.DefaultApp)
        assertEquals(LinkTarget.DefaultApp, s.linkTarget.first())
        s.setLinkTarget(LinkTarget.InApp)
        assertEquals(LinkTarget.InApp, s.linkTarget.first())
        s.setOldestFirst(true)
        s.setHaptics(false)
        assertTrue(s.oldestFirst.first())
        assertFalse(s.haptics.first())
    }
}
