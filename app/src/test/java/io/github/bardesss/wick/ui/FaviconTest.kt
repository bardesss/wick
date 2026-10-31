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
