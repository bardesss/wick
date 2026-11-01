package io.github.bardesss.wick.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.bardesss.wick.R
import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.api.KnownApiPaths
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.ui.Note
import io.github.bardesss.wick.ui.note
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginState(val busy: Boolean = false, val error: Note? = null)

private val scheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")

/** Trims, defaults to https and drops trailing slashes. The path stays, it is part of the API URL. */
fun normaliseApiUrl(raw: String): String {
    val s = raw.trim()
    return (if (scheme.containsMatchIn(s)) s else "https://$s").trimEnd('/')
}

/** The URL as entered, then the known API paths appended to it unless it already ends with one. */
fun loginCandidates(apiUrl: String): List<String> =
    listOf(apiUrl) + KnownApiPaths.all.filterNot { apiUrl.endsWith(it, ignoreCase = true) }.map { apiUrl + it }

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
        val entered = normaliseApiUrl(server)
        // the platform refuses plain http, and so does Wick, with a reason
        if (entered.startsWith("http://", ignoreCase = true)) {
            _state.value = LoginState(error = Note(R.string.error_needs_https))
            return
        }
        _state.value = LoginState(busy = true)
        viewModelScope.launch {
            var base = entered
            val token = try {
                login(entered, user.trim(), key.trim()) { base = it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                _state.value = LoginState(error = e.note())
                return@launch
            } catch (e: Exception) {
                _state.value = LoginState(error = Note(R.string.error_bad_address))
                return@launch
            }
            try {
                onAccount(accountId(base, user))
                creds.save(Auth(base, token))
                _state.update { it.copy(busy = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LoginState(error = Note(R.string.error_storage))
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
