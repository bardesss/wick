package io.github.bardesss.wick.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.data.CredentialStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(val busy: Boolean = false, val error: String? = null)

object LoginMessages {
    const val UNREACHABLE = "Can't reach the server. Is your VPN on?"
    const val BAD_CREDENTIALS = "Username or API key is wrong"
    const val NO_API = "No Google Reader API at this address"
    const val STORAGE = "Couldn't store the sign in on this device"
    fun http(code: Int) = "The server answered with error $code"
}

private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

/** Trims, defaults to https, drops trailing slashes and a pasted API path. */
fun normaliseServer(raw: String): String {
    var s = raw.trim()
    if (!scheme.containsMatchIn(s)) s = "https://$s"
    s = s.trimEnd('/')
    if (s.endsWith("/rest/googlereader", ignoreCase = true)) {
        s = s.dropLast("/rest/googlereader".length).trimEnd('/')
    }
    return s
}

class LoginViewModel(
    private val api: ReaderApi,
    private val creds: CredentialStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun signIn(server: String, user: String, key: String) {
        if (_state.value.busy) return
        if (server.isBlank() || user.isBlank() || key.isBlank()) return
        _state.value = LoginState(busy = true)
        val base = normaliseServer(server)
        viewModelScope.launch {
            try {
                val token = api.login(base, user.trim(), key.trim())
                creds.save(Auth(base, token))
                _state.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                _state.value = LoginState(error = message(e))
            } catch (e: Exception) {
                _state.value = LoginState(error = LoginMessages.STORAGE)
            }
        }
    }

    private fun message(e: ApiError) = when (e) {
        ApiError.Unreachable -> LoginMessages.UNREACHABLE
        ApiError.BadCredentials -> LoginMessages.BAD_CREDENTIALS
        ApiError.NoGReaderApi, ApiError.Malformed -> LoginMessages.NO_API
        is ApiError.Http -> LoginMessages.http(e.code)
    }

    companion object {
        fun factory(api: ReaderApi, creds: CredentialStore) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = LoginViewModel(api, creds) as T
        }
    }
}
