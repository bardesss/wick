package io.github.bardesss.wick.ui.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.bardesss.wick.R
import io.github.bardesss.wick.format.ageOf
import io.github.bardesss.wick.ui.Favicon
import io.github.bardesss.wick.ui.short
import io.github.bardesss.wick.ui.spoken
import io.github.bardesss.wick.ui.theme.WickColors
import io.github.bardesss.wick.ui.theme.WickText

private val TitleUnread = WickText.Title.copy(lineHeight = 1.3.em, letterSpacing = (-0.1).sp)
private val TitleRead = TitleUnread.copy(fontWeight = FontWeight.Normal)
private val DividerStart = 56.dp
private val FadeSpec = tween<androidx.compose.ui.graphics.Color>(450)

@Composable
fun ItemRow(
    row: RowModel,
    read: Boolean,
    nowSec: Long,
    divider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val titleColor by animateColorAsState(if (read) WickColors.Faded else WickColors.Paper, FadeSpec, label = "title")
    val metaColor by animateColorAsState(if (read) WickColors.MetaDim else WickColors.Meta, FadeSpec, label = "meta")
    // the colour alone tells read from unread, so say it too
    val state = stringResource(if (read) R.string.row_state_read else R.string.row_state_unread)
    val age = ageOf(nowSec, row.publishedSec)
    val spokenAge = age.spoken()
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (divider) {
                    Modifier.drawBehind {
                        drawLine(
                            color = WickColors.Line,
                            start = Offset(DividerStart.toPx(), 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .semantics { stateDescription = state }
            .clickable(onClick = onClick)
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 15.dp),
    ) {
        Favicon(row.iconUrl, row.source, faded = read, modifier = Modifier.padding(top = 3.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = if (read) TitleRead else TitleUnread,
                color = titleColor,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 5.dp)) {
                Text(
                    text = row.source,
                    style = WickText.Meta,
                    color = metaColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = age.short(),
                    style = WickText.Meta,
                    color = metaColor,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp).semantics { contentDescription = spokenAge },
                )
            }
        }
    }
}
