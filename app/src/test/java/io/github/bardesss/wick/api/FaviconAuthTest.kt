package io.github.bardesss.wick.api

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FaviconAuthTest {
    private val server = MockWebServer()
    private val other = MockWebServer()
    private var auth: Auth? = null
    private val client = OkHttpClient.Builder().addInterceptor(FaviconAuth { auth }).build()

    @Before
    fun setUp() {
        server.start()
        other.start()
        auth = Auth(server.url("/").toString().trimEnd('/'), "alice/k3y")
    }

    @After
    fun tearDown() {
        server.shutdown()
        other.shutdown()
    }

    /** Requests [path] on [on] and returns the URL the server saw. */
    private fun fetch(on: MockWebServer, path: String): HttpUrl {
        client.newCall(Request.Builder().url(on.url(path)).build()).execute().close()
        return on.takeRequest().requestUrl!!
    }

    @Test
    fun favicon_on_signed_in_server_gets_the_key() {
        server.enqueue(MockResponse())
        val url = fetch(server, "/rest/feed/favicon/3")
        assertEquals("k3y", url.queryParameter("apiKey"))
        assertEquals("/rest/feed/favicon/3", url.encodedPath)
    }

    @Test
    fun favicon_under_a_sub_path_gets_the_key() {
        auth = Auth(server.url("/reader").toString(), "alice/k3y")
        server.enqueue(MockResponse())
        assertEquals("k3y", fetch(server, "/reader/rest/feed/favicon/3").queryParameter("apiKey"))
    }

    @Test
    fun plain_http_on_the_same_host_and_port_gets_no_key() {
        val u = server.url("/")
        auth = Auth("https://" + u.host + ":" + u.port, "alice/k3y")
        server.enqueue(MockResponse())
        assertNull(fetch(server, "/rest/feed/favicon/3").queryParameter("apiKey"))
    }

    @Test
    fun other_server_gets_no_key() {
        other.enqueue(MockResponse())
        assertNull(fetch(other, "/rest/feed/favicon/3").queryParameter("apiKey"))
    }

    @Test
    fun other_path_gets_no_key() {
        server.enqueue(MockResponse())
        server.enqueue(MockResponse())
        assertNull(fetch(server, "/rest/feed/entries").queryParameter("apiKey"))
        assertNull(fetch(server, "/favicon.ico").queryParameter("apiKey"))
    }

    @Test
    fun signed_out_gets_no_key() {
        auth = null
        server.enqueue(MockResponse())
        assertNull(fetch(server, "/rest/feed/favicon/3").queryParameter("apiKey"))
    }
}
