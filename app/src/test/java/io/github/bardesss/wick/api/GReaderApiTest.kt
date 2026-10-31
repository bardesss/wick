package io.github.bardesss.wick.api

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class GReaderApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: GReaderApi
    private val base = "/rest/googlereader"
    private val read = "user/-/state/com.google/read"

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = GReaderApi(OkHttpClient()) { Auth(server.url("/").toString(), "u/k") }
    }

    @After
    fun tearDown() {
        runCatching { server.shutdown() }
    }

    private fun serverUrl() = server.url("/").toString()

    private fun expectError(expected: ApiError, block: suspend () -> Unit) {
        try {
            runBlocking { block() }
            fail("expected $expected")
        } catch (e: ApiError) {
            assertEquals(expected, e)
        }
    }

    @Test
    fun login_posts_email_and_apikey_and_returns_auth_line() {
        server.enqueue(MockResponse().setBody("SID=u/k\nLSID=u/k\nAuth=u/k\n"))
        val token = runBlocking { api.login(serverUrl(), "u", "k") }
        assertEquals("u/k", token)
        val req = server.takeRequest()
        assertEquals("$base/accounts/ClientLogin", req.path)
        val body = req.body.readUtf8()
        assertTrue(body, body.contains("Email=u"))
        assertTrue(body, body.contains("Passwd=k"))
    }

    @Test
    fun login_403_is_BadCredentials() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("Error=BadAuthentication"))
        expectError(ApiError.BadCredentials) { api.login(serverUrl(), "u", "bad") }
    }

    @Test
    fun login_404_is_NoGReaderApi() {
        server.enqueue(MockResponse().setResponseCode(404))
        expectError(ApiError.NoGReaderApi) { api.login(serverUrl(), "u", "k") }
    }

    @Test
    fun login_unreachable_is_Unreachable() {
        val url = serverUrl()
        server.shutdown()
        expectError(ApiError.Unreachable) { api.login(url, "u", "k") }
    }

    @Test
    fun requests_send_googlelogin_header() {
        server.enqueue(MockResponse().setBody("""{"subscriptions":[]}"""))
        runBlocking { api.feeds() }
        assertEquals("GoogleLogin auth=u/k", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun feeds_parse_folder_and_icon() {
        server.enqueue(
            MockResponse().setBody(
                """{"subscriptions":[{"id":"feed/3","title":"Blog &amp; Co","categories":[{"id":"user/-/label/News","label":"News"}],
                "url":"https://b.example/feed","htmlUrl":"https://b.example","iconUrl":"https://b.example/i.png"},
                {"id":"feed/4","title":"Loose","categories":[]}]}""",
            ),
        )
        val feeds = runBlocking { api.feeds() }
        assertEquals(
            listOf(
                Feed("feed/3", "Blog & Co", "News", "https://b.example", "https://b.example/i.png"),
                Feed("feed/4", "Loose", null, null, null),
            ),
            feeds,
        )
        assertEquals("$base/reader/api/0/subscription/list?output=json", server.takeRequest().path)
    }

    @Test
    fun unreadCounts_parse() {
        server.enqueue(
            MockResponse().setBody(
                """{"max":1000,"unreadcounts":[{"id":"feed/3","count":7,"newestItemTimestampUsec":"1"},
                {"id":"user/-/state/com.google/reading-list","count":9}]}""",
            ),
        )
        val counts = runBlocking { api.unreadCounts() }
        assertEquals(
            listOf(UnreadCount("feed/3", 7), UnreadCount("user/-/state/com.google/reading-list", 9)),
            counts,
        )
        assertEquals("$base/reader/api/0/unread-count?output=json", server.takeRequest().path)
    }

    @Test
    fun unread_requests_exclude_read_and_cap_n() {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        runBlocking {
            api.unread(StreamId.All, false, 1000, null)
            api.unread(StreamId.All, true, 1000, "tok2")
        }
        val first = server.takeRequest()
        assertEquals(
            "$base/reader/api/0/stream/contents/user/-/state/com.google/reading-list",
            first.requestUrl!!.encodedPath,
        )
        assertEquals(read, first.requestUrl!!.queryParameter("xt"))
        assertEquals("1000", first.requestUrl!!.queryParameter("n"))
        assertNull(first.requestUrl!!.queryParameter("r"))
        assertNull(first.requestUrl!!.queryParameter("c"))
        val second = server.takeRequest()
        assertEquals("o", second.requestUrl!!.queryParameter("r"))
        assertEquals("tok2", second.requestUrl!!.queryParameter("c"))
    }

    @Test
    fun folder_stream_id_is_path_encoded() {
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        runBlocking { api.unread(StreamId.Folder("Tech & Co/Ä"), false, 50, null) }
        assertEquals(
            "$base/reader/api/0/stream/contents/user/-/label/Tech%20%26%20Co%2F%C3%84",
            server.takeRequest().requestUrl!!.encodedPath,
        )
    }

    @Test
    fun item_parsing() {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"user/-/state/com.google/reading-list","continuation":"next1","items":[
                {"id":"tag:google.com,2005:reader/item/000000000000002a","published":1730000000,"title":"A &amp; B",
                 "canonical":[{"href":"https://x.example/a"}],"summary":{"content":"<p>hi</p>","direction":"ltr"},
                 "origin":{"streamId":"feed/3","title":"Blog","htmlUrl":"https://x.example"}},
                {"id":"tag:google.com,2005:reader/item/000000000000002b","published":1730000001,
                 "summary":{"content":"x"},"origin":{"streamId":"feed/4","title":"Fallback Feed"}},
                {"id":"tag:google.com,2005:reader/item/000000000000002c","published":1730000002,
                 "canonical":[{"href":"https://host.example/p"}],"origin":{"streamId":"feed/5"}}
                ]}""",
            ),
        )
        val page = runBlocking { api.unread(StreamId.All, false, 10, null) }
        assertEquals("next1", page.continuation)
        val a = page.items[0]
        assertEquals("tag:google.com,2005:reader/item/000000000000002a", a.id)
        assertEquals("feed/3", a.feedId)
        assertEquals("https://x.example/a", a.url)
        assertEquals("<p>hi</p>", a.html)
        assertEquals("A & B", a.title)
        assertEquals(1730000000L, a.publishedSec)
        val b = page.items[1]
        assertEquals("Fallback Feed", b.title)
        assertNull(b.url)
        assertEquals("host.example", page.items[2].title)
    }

    @Test
    fun setRead_fetches_token_then_posts_edit_tag() {
        server.enqueue(MockResponse().setBody("tok"))
        server.enqueue(MockResponse().setBody("OK"))
        server.enqueue(MockResponse().setBody("OK"))
        runBlocking {
            api.setRead(listOf("id1", "id2"), true)
            api.setRead(listOf("id3"), false)
        }
        val tokenReq = server.takeRequest()
        assertEquals("GET", tokenReq.method)
        assertEquals("$base/reader/api/0/token", tokenReq.path)
        val post = server.takeRequest()
        assertEquals("$base/reader/api/0/edit-tag", post.path)
        val body = post.body.readUtf8()
        assertEquals(listOf("id1", "id2"), Regex("(?:^|&)i=([^&]*)").findAll(body).map { it.groupValues[1] }.toList())
        assertTrue(body, body.contains("a=user%2F-%2Fstate%2Fcom.google%2Fread"))
        assertTrue(body, body.contains("T=tok"))
        val unread = server.takeRequest().body.readUtf8()
        assertTrue(unread, unread.contains("r=user%2F-%2Fstate%2Fcom.google%2Fread"))
        assertEquals(3, server.requestCount)
    }

    @Test
    fun setRead_refetches_token_once_on_401() {
        server.enqueue(MockResponse().setBody("old"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("new"))
        server.enqueue(MockResponse().setBody("OK"))
        runBlocking { api.setRead(listOf("id1"), true) }
        repeat(3) { server.takeRequest() }
        assertTrue(server.takeRequest().body.readUtf8().contains("T=new"))
    }

    @Test
    fun non_json_200_body_is_Malformed() {
        server.enqueue(MockResponse().setBody("<html>proxy login</html>"))
        expectError(ApiError.Malformed) { api.feeds() }
        server.enqueue(MockResponse().setBody("not json"))
        expectError(ApiError.Malformed) { api.unread(StreamId.All, false, 10, null) }
    }

    @Test
    fun unauthorized_on_authenticated_calls_is_BadCredentials() {
        server.enqueue(MockResponse().setResponseCode(401))
        expectError(ApiError.BadCredentials) { api.feeds() }
        // token fetch
        server.enqueue(MockResponse().setResponseCode(401))
        expectError(ApiError.BadCredentials) { api.setRead(listOf("a"), true) }
        // POST rejected again after the one token retry
        server.enqueue(MockResponse().setBody("t1"))
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(MockResponse().setBody("t2"))
        server.enqueue(MockResponse().setResponseCode(401))
        expectError(ApiError.BadCredentials) { api.setRead(listOf("a"), true) }
    }

    @Test
    fun markAllRead_sends_stream_and_ts_usec() {
        server.enqueue(MockResponse().setBody("tok"))
        server.enqueue(MockResponse().setBody("OK"))
        runBlocking { api.markAllRead(StreamId.Folder("News"), 1730000000000000L) }
        server.takeRequest()
        val post = server.takeRequest()
        assertEquals("$base/reader/api/0/mark-all-as-read", post.path)
        val body = post.body.readUtf8()
        assertTrue(body, body.contains("s=user%2F-%2Flabel%2FNews"))
        assertTrue(body, body.contains("ts=1730000000000000"))
        assertTrue(body, body.contains("T=tok"))
    }
}
