package io.github.bardesss.wick.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeadImageTest {
    @Test
    fun the_first_image_wins() {
        val html = "<p>Hi</p><img src=\"https://e.x/a.jpg\"><img src=\"https://e.x/b.jpg\">"

        assertEquals("https://e.x/a.jpg", leadImageUrl(html, "https://e.x/post"))
    }

    @Test
    fun relative_addresses_resolve_against_the_article_link() {
        assertEquals("https://e.x/blog/img/a.jpg", leadImageUrl("<img src=\"img/a.jpg\">", "https://e.x/blog/post"))
        assertEquals("https://e.x/img/a.jpg", leadImageUrl("<img src=\"/img/a.jpg\">", "https://e.x/blog/post"))
        assertEquals("https://cdn.e.x/a.jpg", leadImageUrl("<img src=\"//cdn.e.x/a.jpg\">", "https://e.x/blog/post"))
    }

    @Test
    fun a_relative_address_without_a_link_is_dropped() {
        assertNull(leadImageUrl("<img src=\"img/a.jpg\">", null))
    }

    @Test
    fun tracking_pixels_are_skipped() {
        val html = "<img src=\"https://t.x/p.gif\" width=\"1\" height=\"1\">" +
            "<img src=\"https://t.x/q.gif\" width=\"2px\">" +
            "<img src=\"https://t.x/r.gif\" height=\"1\">" +
            "<img src=\"https://e.x/real.jpg\" width=\"640\">"

        assertEquals("https://e.x/real.jpg", leadImageUrl(html, null))
    }

    @Test
    fun anything_but_http_is_ignored() {
        val html = "<img src=\"data:image/png;base64,AAAA\"><img src=\"ftp://e.x/a.jpg\">" +
            "<img src=\"file:///sdcard/a.jpg\"><img src=\"http://e.x/ok.jpg\">"

        assertEquals("http://e.x/ok.jpg", leadImageUrl(html, "https://e.x/post"))
    }

    @Test
    fun no_image_gives_null() {
        assertNull(leadImageUrl("<p>Just text</p>", "https://e.x/post"))
        assertNull(leadImageUrl("<img alt=\"no source\">", "https://e.x/post"))
        assertNull(leadImageUrl(null, null))
        assertNull(leadImageUrl("  ", null))
    }
}
