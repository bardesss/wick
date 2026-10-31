package io.github.bardesss.wick.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    @Test
    fun only_http_and_https_are_web_urls() {
        assertTrue(isWebUrl("https://example.com/a"))
        assertTrue(isWebUrl("http://example.com/a"))
        assertTrue(isWebUrl("HTTPS://example.com/a"))
        assertFalse(isWebUrl("intent://scan/#Intent;scheme=zxing;end"))
        assertFalse(isWebUrl("market://details?id=x"))
        assertFalse(isWebUrl("file:///sdcard/a"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl(""))
    }
}
