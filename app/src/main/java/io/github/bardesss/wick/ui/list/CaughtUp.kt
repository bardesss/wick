package io.github.bardesss.wick.ui.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.bardesss.wick.R
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.Newsreader
import io.github.bardesss.wick.ui.theme.WickColors

private val HeadStyle = TextStyle(
    fontFamily = Newsreader,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    color = WickColors.Paper,
    textAlign = TextAlign.Center,
)
private val HintStyle = TextStyle(
    fontFamily = InstrumentSans,
    fontSize = 14.sp,
    lineHeight = 1.6.em,
    color = WickColors.Meta,
    textAlign = TextAlign.Center,
)

/** The end of the list, and the whole list when it is empty. The caller gives it the viewport height. */
@Composable
fun CaughtUp(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 40.dp, top = 120.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_flame),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Text(
            stringResource(R.string.caught_up_title),
            style = HeadStyle,
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        )
        Text(stringResource(R.string.caught_up_hint), style = HintStyle)
    }
}
