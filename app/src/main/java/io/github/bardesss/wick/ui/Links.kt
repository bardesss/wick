package io.github.bardesss.wick.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import io.github.bardesss.wick.ui.theme.WickColors

/** Opens [url] in a Custom Tab of the default browser, or any app that takes it. */
fun openLink(context: Context, url: String) {
    val uri = url.toUri()
    val colors = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(WickColors.Night.toArgb())
        .setNavigationBarColor(WickColors.Night.toArgb())
        .build()
    try {
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(colors)
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK)
            .setShowTitle(true)
            .build()
            .launchUrl(context, uri)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: ActivityNotFoundException) {
            // nothing on the device can open it
        }
    }
}
