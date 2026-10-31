package io.github.bardesss.wick.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

private const val KEY_GENERATION = "session_generation"

/**
 * Owns the view models of one signed in session. [end] clears them, so their work stops at
 * sign out, and moves [generation] on so the next session gets fresh saved state keys.
 */
class SessionScope(private val handle: SavedStateHandle) : ViewModel(), ViewModelStoreOwner {
    override var viewModelStore = ViewModelStore()
        private set

    val generation: Int get() = handle[KEY_GENERATION] ?: 0

    fun end() {
        viewModelStore.clear()
        viewModelStore = ViewModelStore()
        handle[KEY_GENERATION] = generation + 1
    }

    override fun onCleared() {
        viewModelStore.clear()
    }
}
