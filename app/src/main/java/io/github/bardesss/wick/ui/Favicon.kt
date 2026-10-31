package io.github.bardesss.wick.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.WickColors
import java.net.URI

/** Absolute URLs as they are, relative ones against [server], null when there is no icon. */
fun resolveIcon(server: String, iconUrl: String?): String? {
    val icon = iconUrl?.trim()
    if (icon.isNullOrEmpty()) return null
    return try {
        val uri = URI(icon)
        if (uri.isAbsolute) icon else URI(server.trimEnd('/') + "/").resolve(uri).toString()
    } catch (e: Exception) {
        null
    }
}

private val TileShape = RoundedCornerShape(6.dp)
private val Greyscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val LetterStyle = TextStyle(
    fontFamily = InstrumentSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 11.sp,
    color = WickColors.Meta,
)

/** The feed icon, or the first letter of the feed on a tile while it loads or when there is none. */
@Composable
fun Favicon(url: String?, title: String, faded: Boolean, modifier: Modifier = Modifier) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(
        modifier
            .size(20.dp)
            .alpha(if (faded) FADED_ALPHA else 1f)
            .clip(TileShape),
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            Box(Modifier.size(20.dp).background(WickColors.Raised), contentAlignment = Alignment.Center) {
                Text(title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "", style = LetterStyle)
            }
        }
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = if (faded) Greyscale else null,
                onState = { loaded = it is AsyncImagePainter.State.Success },
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private const val FADED_ALPHA = 0.3f
