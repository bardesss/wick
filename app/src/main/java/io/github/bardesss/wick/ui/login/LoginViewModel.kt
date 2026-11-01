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
    const val BAD_ADDRESS = "That address doesn't look right"
    fun http(code: Int) = "The server answered with error $code"

    fun of(e: ApiError) = when (e) {
        ApiError.Unreachable -> UNREACHABLE
        ApiError.BadCredentials -> BAD_CREDENTIALS
        ApiError.NoGReaderApi, ApiError.Malformed -> NO_API
        is ApiError.Http -> http(e.code)
    }
}

private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

/** Trims, defaults to https and drops trailing slashes. The path stays, it is part of the API URL. */
fun normaliseApiUrl(raw: String): String {
    val s = raw.trim()
    return (if (scheme.containsMatchIn(s)) s else "https://$s").trimEnd('/')
}

private val fallbackPaths = listOf("/rest/googlereader", "/api/greader.php")

/** The URL as entered, then the known API paths appended to it unless it already ends with one. */
fun loginCandidates(apiUrl: String): List<String> =
    listOf(apiUrl) + fallbackPaths.filterNot { apiUrl.endsWith(it, ignoreCase = true) }.map { apiUrl + it }

/** Names the account a sign in is for, to tell a new key for the same account from another account. */
fun accountId(server: String, user: String): String = normaliseApiUrl(server).lowercase() + "\n" + user.trim()

class LoginViewModel(
    private val api: ReaderApi,
    private val creds: CredentialStore,
    /** Runs before the credentials are saved; the app uses it to drop another account's data. */
    private val onAccount: suspend (String) -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun signIn(server: String, user: String, key: String) {
        if (_state.value.busy) return
        if (server.isBlank() || user.isBlank() || key.isBlank()) return
        _state.value = LoginState(busy = true)
        val entered = normaliseApiUrl(server)
        viewModelScope.launch {
            var base = entered
            val token = try {
                login(entered, user.trim(), key.trim()) { base = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                _state.value = LoginState(error = LoginMessages.of(e))
                return@launch
            } catch (e: Exception) {
                _state.value = LoginState(error = LoginMessages.BAD_ADDRESS)
                return@launch
            }
            try {
                onAccount(accountId(base, user))
                creds.save(Auth(base, token))
                _state.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LoginState(error = LoginMessages.STORAGE)
            }
        }
    }

    /** Signs in at [apiUrl], and where that answers 404 at the same URL with a known API path added. */
    private suspend fun login(apiUrl: String, user: String, key: String, worked: (String) -> Unit): String {
        for (candidate in loginCandidates(apiUrl)) {
            try {
                val token = api.login(candidate, user, key)
                worked(candidate)
                return token
            } catch (e: ApiError.NoGReaderApi) {
                // only a 404 moves on to the next path
            }
        }
        throw ApiError.NoGReaderApi
    }

    companion object {
        fun factory(api: ReaderApi, creds: CredentialStore, onAccount: suspend (String) -> Unit = {}) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LoginViewModel(api, creds, onAccount) as T
            }
    }
}
