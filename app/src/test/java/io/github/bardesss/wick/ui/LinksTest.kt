package io.github.bardesss.wick.ui

import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import io.github.bardesss.wick.data.LinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LinksTest {
    @Test
    fun default_app_is_a_plain_view_intent() {
        val intent = linkIntent("https://example.com/a", LinkTarget.DefaultApp)!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com/a", intent.dataString)
        assertFalse(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
    }

    @Test
    fun in_app_is_a_custom_tab_intent() {
        val intent = linkIntent("https://example.com/a", LinkTarget.InApp)!!
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com/a", intent.dataString)
        assertTrue(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
    }

    @Test
    fun other_schemes_get_no_intent_for_either_choice() {
        for (target in LinkTarget.entries) {
            assertNull(linkIntent("market://details?id=x", target))
            assertNull(linkIntent("javascript:alert(1)", target))
        }
    }

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
