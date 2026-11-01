package io.github.bardesss.wick.ui.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.unit.sp
import io.github.bardesss.wick.R
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.Newsreader
import io.github.bardesss.wick.ui.theme.TapTarget
import io.github.bardesss.wick.ui.theme.WickColors

private val HeadStyle = TextStyle(
    fontFamily = Newsreader,
    fontWeight = FontWeight.Medium,
    fontSize = 22.sp,
    color = WickColors.Paper,
    textAlign = TextAlign.Center,
)
private val ButtonStyle = TextStyle(
    fontFamily = InstrumentSans,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    textAlign = TextAlign.Center,
)

/**
 * The end of the list, and the whole list when it is empty. The caller gives it the viewport height.
 * The button runs the same refresh as pulling at the top.
 */
@Composable
fun CaughtUp(onRefresh: () -> Unit, refreshing: Boolean, modifier: Modifier = Modifier) {
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
        Box(
            Modifier
                .heightIn(min = TapTarget)
                .widthIn(min = TapTarget)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = !refreshing, role = Role.Button, onClick = onRefresh)
                .padding(horizontal = 16.dp)
                .testTag("check_new"),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.caught_up_check),
                    style = ButtonStyle,
                    color = WickColors.Amber,
                    modifier = Modifier.alpha(if (refreshing) 0.5f else 1f),
                )
                if (refreshing) {
                    val checking = stringResource(R.string.caught_up_checking)
                    CircularProgressIndicator(
                        color = WickColors.Amber,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .size(16.dp)
                            .semantics { contentDescription = checking },
                    )
                }
            }
        }
    }
}
