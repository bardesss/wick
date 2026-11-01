package io.github.bardesss.wick.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaviconTest {
    @Test
    fun relative_icon_resolved_against_server() {
        assertEquals(
            "https://feeds.example.com/rest/feed/favicon?id=3",
            resolveIcon("https://feeds.example.com", "rest/feed/favicon?id=3"),
        )
        assertEquals(
            "https://example.com/reader/rest/feed/favicon?id=3",
            resolveIcon("https://example.com/reader", "rest/feed/favicon?id=3"),
        )
        assertEquals(
            "https://feeds.example.com/rest/feed/favicon?id=3",
            resolveIcon("https://feeds.example.com/", "/rest/feed/favicon?id=3"),
        )
    }

    @Test
    fun site_root_drops_a_known_api_path() {
        assertEquals("https://h.example", siteRoot("https://h.example/rest/googlereader"))
        assertEquals("https://h.example", siteRoot("https://h.example/api/greader.php"))
        assertEquals("https://h.example", siteRoot("https://h.example"))
        assertEquals("https://h.example/sub", siteRoot("https://h.example/sub/REST/GoogleReader/"))
        assertEquals("https://h.example/sub", siteRoot("https://h.example/sub/API/greader.php"))
        assertEquals("https://h.example/miniflux", siteRoot("https://h.example/miniflux"))
    }

    @Test
    fun relative_icon_resolves_against_the_site_of_the_api_url() {
        val icon = "rest/feed/favicon/3"
        val expected = "https://h.example/rest/feed/favicon/3"
        assertEquals(expected, resolveIcon("https://h.example/rest/googlereader", icon))
        assertEquals(expected, resolveIcon("https://h.example/api/greader.php", icon))
        assertEquals(expected, resolveIcon("https://h.example", icon))
    }

    @Test
    fun relative_icon_with_url_in_query_is_still_relative() {
        assertEquals(
            "https://feeds.example.com/rest/feed/favicon/3?from=https://site.example.org/",
            resolveIcon("https://feeds.example.com", "rest/feed/favicon/3?from=https://site.example.org/"),
        )
    }

    @Test
    fun absolute_icon_returned_as_is() {
        assertEquals("https://cdn.example.org/i.png", resolveIcon("https://feeds.example.com", "https://cdn.example.org/i.png"))
        assertEquals("http://x.example.org/f.ico", resolveIcon("https://feeds.example.com", "http://x.example.org/f.ico"))
    }

    @Test
    fun protocol_relative_icon_takes_server_scheme() {
        assertEquals("https://cdn.example.org/i.png", resolveIcon("https://feeds.example.com", "//cdn.example.org/i.png"))
    }

    @Test
    fun blank_icon_is_null() {
        assertNull(resolveIcon("https://feeds.example.com", null))
        assertNull(resolveIcon("https://feeds.example.com", ""))
        assertNull(resolveIcon("https://feeds.example.com", "   "))
    }
}
