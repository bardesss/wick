package io.github.bardesss.wick.ui.login

import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.Auth
import io.github.bardesss.wick.api.Feed
import io.github.bardesss.wick.api.Page
import io.github.bardesss.wick.api.ReaderApi
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.api.UnreadCount
import io.github.bardesss.wick.data.CredentialStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private class StubApi : ReaderApi {
        var failure: ApiError? = null
        var gate: CompletableDeferred<Unit>? = null
        val logins = mutableListOf<Triple<String, String, String>>()

        override suspend fun login(server: String, user: String, apiKey: String): String {
            logins += Triple(server, user, apiKey)
            gate?.await()
            failure?.let { throw it }
            return "tok"
        }

        override suspend fun feeds(): List<Feed> = error("unused")
        override suspend fun unreadCounts(): List<UnreadCount> = error("unused")
        override suspend fun unread(stream: StreamId, oldestFirst: Boolean, max: Int, continuation: String?): Page =
            error("unused")
        override suspend fun setRead(itemIds: List<String>, read: Boolean) = error("unused")
        override suspend fun markAllRead(stream: StreamId, olderThanUsec: Long) = error("unused")
    }

    private class MemoryCreds : CredentialStore {
        private val flow = MutableStateFlow<Auth?>(null)
        override fun auth(): Auth? = flow.value
        override val authFlow: StateFlow<Auth?> get() = flow
        override suspend fun save(auth: Auth) { flow.value = auth }
        override suspend fun clear() { flow.value = null }
    }

    private val api = StubApi()
    private val creds = MemoryCreds()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun failWith(error: ApiError): String? {
        api.failure = error
        val vm = LoginViewModel(api, creds)
        vm.signIn("example.com", "me", "key")
        return vm.state.value.error
    }

    @Test
    fun maps_unreachable_to_vpn_message() {
        assertEquals("Can't reach the server. Is your VPN on?", failWith(ApiError.Unreachable))
    }

    @Test
    fun maps_bad_credentials() {
        assertEquals("Username or API key is wrong", failWith(ApiError.BadCredentials))
    }

    @Test
    fun maps_no_api() {
        assertEquals("No Google Reader API at this address", failWith(ApiError.NoGReaderApi))
        assertEquals("No Google Reader API at this address", failWith(ApiError.Malformed))
    }

    @Test
    fun maps_http_code() {
        assertEquals("The server answered with error 502", failWith(ApiError.Http(502)))
    }

    @Test
    fun normalises_server_url() {
        assertEquals("https://example.com", normaliseServer(" example.com/ "))
        assertEquals("http://host:8082", normaliseServer("http://host:8082/"))
        assertEquals("https://x.y", normaliseServer("https://x.y/rest/googlereader/"))
    }

    @Test
    fun success_saves_normalised_server_and_token() {
        val vm = LoginViewModel(api, creds)
        vm.signIn(" example.com/ ", "me", "key")
        assertEquals(Auth("https://example.com", "tok"), creds.auth())
        assertEquals("https://example.com", api.logins.single().first)
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.error)
    }

    @Test
    fun storage_failure_shows_message_and_clears_busy() {
        val throwing = object : CredentialStore by MemoryCreds() {
            override suspend fun save(auth: Auth) = throw java.security.KeyStoreException("no key")
        }
        val vm = LoginViewModel(api, throwing)
        vm.signIn("example.com", "me", "key")
        assertEquals("Couldn't store the sign in on this device", vm.state.value.error)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun auth_to_string_hides_token() {
        val s = Auth("https://a.b", "secret-token").toString()
        assertEquals("Auth(server=https://a.b, token=***)", s)
    }

    @Test
    fun ignores_second_sign_in_while_busy() {
        api.gate = CompletableDeferred()
        val vm = LoginViewModel(api, creds)
        vm.signIn("example.com", "me", "key")
        assertTrue(vm.state.value.busy)
        vm.signIn("example.com", "me", "key")
        assertEquals(1, api.logins.size)
        api.gate!!.complete(Unit)
        assertFalse(vm.state.value.busy)
    }
}
