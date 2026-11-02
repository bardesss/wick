package io.github.bardesss.wick.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Where a link opens: a Custom Tab inside the app, or whatever app Android picks for it. */
enum class LinkTarget { InApp, DefaultApp }

class SettingsStore(private val store: DataStore<Preferences>) {

    private val oldestFirstKey = booleanPreferencesKey("oldest_first")
    private val hapticsKey = booleanPreferencesKey("haptics")

    private val leadImagesKey = booleanPreferencesKey("lead_images")
    private val hideEmptyKey = booleanPreferencesKey("hide_empty")

    val oldestFirst: Flow<Boolean> = store.data.map { it[oldestFirstKey] ?: false }

    /** Whether the summary sheet loads the article's first image. */
    val leadImages: Flow<Boolean> = store.data.map { it[leadImagesKey] ?: true }

    /** Whether the drawer leaves out feeds and folders with nothing unread. */
    val hideEmpty: Flow<Boolean> = store.data.map { it[hideEmptyKey] ?: false }
    val haptics: Flow<Boolean> = store.data.map { it[hapticsKey] ?: true }

    private val linkTargetKey = stringPreferencesKey("link_target")

    val linkTarget: Flow<LinkTarget> = store.data.map {
        if (it[linkTargetKey] == DEFAULT_APP) LinkTarget.DefaultApp else LinkTarget.InApp
    }

    suspend fun setLinkTarget(value: LinkTarget) {
        store.edit { it[linkTargetKey] = if (value == LinkTarget.DefaultApp) DEFAULT_APP else IN_APP }
    }

    suspend fun setOldestFirst(value: Boolean) {
        store.edit { it[oldestFirstKey] = value }
    }

    suspend fun setLeadImages(value: Boolean) {
        store.edit { it[leadImagesKey] = value }
    }

    suspend fun setHideEmpty(value: Boolean) {
        store.edit { it[hideEmptyKey] = value }
    }

    suspend fun setHaptics(value: Boolean) {
        store.edit { it[hapticsKey] = value }
    }

    private val syncMinutesKey = intPreferencesKey("sync_minutes")
    private val recentCountKey = intPreferencesKey("recent_count")

    /** Minutes between background syncs, or 0 for none. A value from elsewhere falls back to the default. */
    val syncMinutes: Flow<Int> = store.data.map { p -> p[syncMinutesKey]?.takeIf { it in SYNC_CHOICES } ?: SYNC_DEFAULT }

    /** How many of the items read last the "Recently read" view shows. */
    val recentCount: Flow<Int> = store.data.map { p -> p[recentCountKey]?.takeIf { it in RECENT_CHOICES } ?: RECENT_DEFAULT }

    suspend fun setSyncMinutes(value: Int) {
        store.edit { it[syncMinutesKey] = value }
    }

    suspend fun setRecentCount(value: Int) {
        store.edit { it[recentCountKey] = value }
    }

    private val collapsedKey = stringSetPreferencesKey("collapsed_folders")

    /** Names of the drawer folders whose feeds are folded away. */
    val collapsedFolders: Flow<Set<String>> = store.data.map { it[collapsedKey] ?: emptySet() }

    suspend fun toggleCollapsed(folder: String) {
        store.edit {
            val now = it[collapsedKey] ?: emptySet()
            it[collapsedKey] = if (folder in now) now - folder else now + folder
        }
    }

    suspend fun clearCollapsed() {
        store.edit { it.remove(collapsedKey) }
    }

    private val accountKey = stringPreferencesKey("account")
    private val lastViewKey = stringPreferencesKey("last_view")

    /** Absent in v0.1.0, whose account id held a bare server address instead of the API URL. */
    private val accountFormatKey = intPreferencesKey("account_format")

    /** Who the stored data belongs to (API URL and user, not secret), or null when there is none. */
    suspend fun account(): String? {
        val prefs = store.data.first()
        val id = prefs[accountKey] ?: return null
        if (prefs[accountFormatKey] == STORE_FORMAT) return id
        val split = id.indexOf('\n').let { if (it < 0) id.length else it }
        val migrated = legacyApiUrl(id.substring(0, split)) + id.substring(split)
        setAccount(migrated)
        return migrated
    }

    suspend fun setAccount(id: String?) {
        store.edit {
            if (id == null) {
                it.remove(accountKey)
                it.remove(accountFormatKey)
            } else {
                it[accountKey] = id
                it[accountFormatKey] = STORE_FORMAT
            }
        }
    }

    /** The raw stream id of the view shown last, so a start can draw the right header at once. */
    suspend fun lastView(): String? = store.data.first()[lastViewKey]

    suspend fun setLastView(raw: String?) {
        store.edit { if (raw == null) it.remove(lastViewKey) else it[lastViewKey] = raw }
    }

    companion object {
        private const val IN_APP = "in_app"
        private const val DEFAULT_APP = "default_app"

        /** WorkManager runs periodic work at most every 15 minutes. */
        val SYNC_CHOICES = listOf(15, 30, 60, 180, 0)
        const val SYNC_DEFAULT = 60
        val RECENT_CHOICES = listOf(50, 100, 200)
        const val RECENT_DEFAULT = 100

        fun create(context: Context) = SettingsStore(
            PreferenceDataStoreFactory.create(corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }) {
                context.preferencesDataStoreFile("settings")
            },
        )
    }
}
