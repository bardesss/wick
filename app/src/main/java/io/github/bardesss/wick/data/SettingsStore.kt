package io.github.bardesss.wick.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
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

    companion object {
        fun create(context: Context) = SettingsStore(
            PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") },
        )
    }
}
