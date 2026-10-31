package io.github.bardesss.wick.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
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

    /** Who the stored data belongs to (server and user, not secret), or null when there is none. */
    suspend fun account(): String? = store.data.first()[accountKey]

    suspend fun setAccount(id: String?) {
        store.edit { if (id == null) it.remove(accountKey) else it[accountKey] = id }
    }

    /** The raw stream id of the view shown last, so a start can draw the right header at once. */
    suspend fun lastView(): String? = store.data.first()[lastViewKey]

    suspend fun setLastView(raw: String) {
        store.edit { it[lastViewKey] = raw }
    }

    companion object {
        fun create(context: Context) = SettingsStore(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
        )
    }
}
