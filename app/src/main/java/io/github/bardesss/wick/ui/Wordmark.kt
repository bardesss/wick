package io.github.bardesss.wick.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import io.github.bardesss.wick.R
import io.github.bardesss.wick.ui.theme.Newsreader
import io.github.bardesss.wick.ui.theme.WickColors
import kotlin.math.roundToInt

/** "wick" with a dotless i, and the flame standing where the dot would be. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, size: TextUnit = 56.sp) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val flameSide = with(LocalDensity.current) { (sizePx * FLAME_EM).toDp() }

    val name = stringResource(R.string.wordmark)
    // the dotless i would be read out as an odd word, so the whole mark is one name
    Box(modifier.clearAndSetSemantics { contentDescription = name }) {
        Text(
            text = "wıck",
            style = TextStyle(
                fontFamily = Newsreader,
                fontWeight = FontWeight.Medium,
                fontSize = size,
                color = WickColors.Paper,
                letterSpacing = (-0.5).sp,
            ),
            onTextLayout = { layout = it },
        )
        layout?.let { l ->
            val dotless = l.getBoundingBox(1)
            val baseline = l.getLineBaseline(0)
            val side = sizePx * FLAME_EM
            // The drawable leaves a little room under the flame, so lift the box by that.
            val bottom = baseline - sizePx * DOT_ABOVE_BASELINE_EM + side * 0.1f
            Image(
                painter = painterResource(R.drawable.ic_flame),
                contentDescription = null,
                modifier = Modifier
                    .size(flameSide)
                    .offset {
                        IntOffset(
                            (dotless.center.x - side / 2f).roundToInt(),
                            (bottom - side).roundToInt(),
                        )
                    },
            )
        }
    }
}

private const val FLAME_EM = 0.55f
private const val DOT_ABOVE_BASELINE_EM = 0.52f

@Preview(showBackground = true, backgroundColor = 0xFF12161B)
@Composable
private fun WordmarkPreview() {
    Wordmark()
}
