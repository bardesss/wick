package io.github.bardesss.wick.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionScopeTest {
    private class Probe : ViewModel() {
        var cleared = false
        override fun onCleared() {
            cleared = true
        }
    }

    private val factory = viewModelFactory { initializer { Probe() } }

    @Test
    fun end_clears_the_session_view_models() {
        val handle = SavedStateHandle()
        val session = SessionScope(handle)
        val first = ViewModelProvider.create(session, factory)[Probe::class]
        assertFalse(first.cleared)

        session.end()

        assertTrue(first.cleared)
        val second = ViewModelProvider.create(session, factory)[Probe::class]
        assertNotSame(first, second)
        assertEquals(1, SessionScope(handle).generation)
    }
}
