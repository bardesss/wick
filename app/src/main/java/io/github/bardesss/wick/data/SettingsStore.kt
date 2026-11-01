package io.github.bardesss.wick.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import io.github.bardesss.wick.api.LEGACY_API_PATH
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsStore(private val store: DataStore<Preferences>) {

    private val oldestFirstKey = booleanPreferencesKey("oldest_first")
    private val hapticsKey = booleanPreferencesKey("haptics")

    val oldestFirst: Flow<Boolean> = store.data.map { it[oldestFirstKey] ?: false }
    val haptics: Flow<Boolean> = store.data.map { it[hapticsKey] ?: true }

    suspend fun setOldestFirst(value: Boolean) {
        store.edit { it[oldestFirstKey] = value }
    }

    suspend fun setHaptics(value: Boolean) {
        store.edit { it[hapticsKey] = value }
    }

    private val accountKey = stringPreferencesKey("account")
    private val lastViewKey = stringPreferencesKey("last_view")

    /** Absent in v0.1.0, whose account id held a bare server address instead of the API URL. */
    private val accountFormatKey = intPreferencesKey("account_format")

    /** Who the stored data belongs to (API URL and user, not secret), or null when there is none. */
    suspend fun account(): String? {
        val prefs = store.data.first()
        val id = prefs[accountKey] ?: return null
        if (prefs[accountFormatKey] == ACCOUNT_FORMAT) return id
        val split = id.indexOf('\n').let { if (it < 0) id.length else it }
        val migrated = id.substring(0, split).trimEnd('/') + LEGACY_API_PATH + id.substring(split)
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
                it[accountFormatKey] = ACCOUNT_FORMAT
            }
        }
    }

    /** The raw stream id of the view shown last, so a start can draw the right header at once. */
    suspend fun lastView(): String? = store.data.first()[lastViewKey]

    suspend fun setLastView(raw: String?) {
        store.edit { if (raw == null) it.remove(lastViewKey) else it[lastViewKey] = raw }
    }

    companion object {
        private const val ACCOUNT_FORMAT = 2

        fun create(context: Context) = SettingsStore(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
        )
    }
}
