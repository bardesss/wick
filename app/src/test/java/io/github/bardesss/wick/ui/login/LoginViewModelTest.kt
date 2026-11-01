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
        var notFoundAt: Set<String> = emptySet()
        var gate: CompletableDeferred<Unit>? = null
        val logins = mutableListOf<Triple<String, String, String>>()

        override suspend fun login(server: String, user: String, apiKey: String): String {
            logins += Triple(server, user, apiKey)
            gate?.await()
            if (server in notFoundAt) throw ApiError.NoGReaderApi
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
    fun normalises_api_url_and_keeps_its_path() {
        assertEquals("https://example.com", normaliseApiUrl(" example.com/ "))
        assertEquals("http://host:8082", normaliseApiUrl("http://host:8082/"))
        assertEquals("https://x.y/rest/googlereader", normaliseApiUrl("https://x.y/rest/googlereader/"))
        assertEquals("https://x.y/api/greader.php", normaliseApiUrl("x.y/api/greader.php"))
    }

    @Test
    fun login_candidates_add_known_paths_unless_present() {
        assertEquals(
            listOf("https://h", "https://h/rest/googlereader", "https://h/api/greader.php"),
            loginCandidates("https://h"),
        )
        assertEquals(
            listOf("https://h/rest/googlereader", "https://h/rest/googlereader/api/greader.php"),
            loginCandidates("https://h/rest/googlereader"),
        )
        assertEquals(
            listOf("https://h/API/greader.php", "https://h/API/greader.php/rest/googlereader"),
            loginCandidates("https://h/API/greader.php"),
        )
    }

    @Test
    fun a_404_falls_back_in_order_and_stores_the_url_that_worked() {
        api.notFoundAt = setOf("https://example.com", "https://example.com/rest/googlereader")
        val seen = mutableListOf<Pair<String, Auth?>>()
        val vm = LoginViewModel(api, creds) { id -> seen += id to creds.auth() }
        vm.signIn("example.com", "me", "key")
        assertEquals(
            listOf("https://example.com", "https://example.com/rest/googlereader", "https://example.com/api/greader.php"),
            api.logins.map { it.first },
        )
        assertEquals(Auth("https://example.com/api/greader.php", "tok"), creds.auth())
        assertEquals("https://example.com/api/greader.php\nme", seen.single().first)
    }

    @Test
    fun all_404s_report_no_api() {
        api.failure = ApiError.NoGReaderApi
        val vm = LoginViewModel(api, creds)
        vm.signIn("example.com", "me", "key")
        assertEquals(3, api.logins.size)
        assertEquals("No Google Reader API at this address", vm.state.value.error)
        assertNull(creds.auth())
    }

    @Test
    fun other_errors_do_not_fall_back() {
        for (error in listOf(ApiError.BadCredentials, ApiError.Unreachable, ApiError.Http(500))) {
            api.logins.clear()
            api.failure = error
            LoginViewModel(api, creds).signIn("example.com", "me", "key")
            assertEquals(error.toString(), 1, api.logins.size)
        }
    }

    @Test
    fun success_saves_normalised_api_url_and_token() {
        val vm = LoginViewModel(api, creds)
        vm.signIn(" example.com/ ", "me", "key")
        assertEquals(Auth("https://example.com", "tok"), creds.auth())
        assertEquals("https://example.com", api.logins.single().first)
        assertFalse(vm.state.value.busy)
        assertNull(vm.state.value.error)
    }

    @Test
    fun entered_path_is_kept() {
        val vm = LoginViewModel(api, creds)
        vm.signIn("https://h.example/rest/googlereader/", "me", "key")
        assertEquals(Auth("https://h.example/rest/googlereader", "tok"), creds.auth())
        assertEquals("https://h.example/rest/googlereader", api.logins.single().first)
    }

    @Test
    fun sign_in_reports_account_before_saving() {
        val seen = mutableListOf<Pair<String, Auth?>>()
        val vm = LoginViewModel(api, creds) { id -> seen += id to creds.auth() }
        vm.signIn(" example.com/ ", " me ", "key")
        assertEquals(listOf<Pair<String, Auth?>>("https://example.com\nme" to null), seen)
        assertEquals(Auth("https://example.com", "tok"), creds.auth())
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
    fun unusable_address_has_its_own_message() {
        val badUrl = object : ReaderApi by api {
            override suspend fun login(server: String, user: String, apiKey: String): String =
                throw IllegalArgumentException("bad url")
        }
        val vm = LoginViewModel(badUrl, creds)
        vm.signIn("exa mple.com", "me", "key")
        assertEquals("That address doesn't look right", vm.state.value.error)
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
