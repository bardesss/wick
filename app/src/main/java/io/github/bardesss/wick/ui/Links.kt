package io.github.bardesss.wick.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import io.github.bardesss.wick.data.LinkTarget
import io.github.bardesss.wick.ui.theme.WickColors

/** True for http and https links, the only ones a feed may open. */
fun isWebUrl(url: String): Boolean =
    url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

/** The intent that opens [url] for [target], or null for anything but http and https. */
fun linkIntent(url: String, target: LinkTarget): Intent? {
    if (!isWebUrl(url)) return null
    val uri = url.toUri()
    return when (target) {
        LinkTarget.DefaultApp -> Intent(Intent.ACTION_VIEW, uri)
        LinkTarget.InApp -> {
            val colors = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(WickColors.Night.toArgb())
                .setNavigationBarColor(WickColors.Night.toArgb())
                .build()
            CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colors)
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
                .setShowTitle(true)
                .build()
                .intent
                .setData(uri)
        }
    }
}

/**
 * Opens [url] the way [target] says. A Custom Tab falls back to a plain view intent when no
 * browser takes it. Other schemes are ignored.
 */
fun openLink(context: Context, url: String, target: LinkTarget) {
    val intent = linkIntent(url, target) ?: return
    if (start(context, intent) || target != LinkTarget.InApp) return
    start(context, Intent(Intent.ACTION_VIEW, url.toUri()))
}

// false when nothing on the device can open it
private fun start(context: Context, intent: Intent): Boolean =
    try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
