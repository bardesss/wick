package io.github.bardesss.wick.ui.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.bardesss.wick.R
import io.github.bardesss.wick.format.formatAge
import io.github.bardesss.wick.ui.Favicon
import io.github.bardesss.wick.ui.list.RowModel
import io.github.bardesss.wick.ui.openLink
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.Newsreader
import io.github.bardesss.wick.ui.theme.WickColors
import io.github.bardesss.wick.ui.theme.WickText

/** The sheet body: not read yet, or read with [text] null when the feed sent none. */
sealed interface SheetBody {
    data object Loading : SheetBody
    data class Ready(val text: AnnotatedString?) : SheetBody
}

private const val MAX_HEIGHT = 0.78f
private val SheetShape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
private val Scrim = Color(0x99040608)
private val SourceStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 13.sp)
private val TitleStyle = TextStyle(
    fontFamily = Newsreader,
    fontWeight = FontWeight.Medium,
    fontSize = 23.sp,
    lineHeight = 1.25.em,
    letterSpacing = (-0.3).sp,
)
private val EmptyStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 14.sp, lineHeight = 1.5.em)
private val ButtonStyle = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 15.sp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummarySheet(
    row: RowModel,
    body: SheetBody,
    nowSec: Long,
    sheetState: SheetState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = WickColors.Raised,
        contentColor = WickColors.Paper,
        tonalElevation = 0.dp,
        scrimColor = Scrim,
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp, color = WickColors.Handle) },
    ) {
        // the sheet is its own window, so it needs its own resource id switch
        Box(Modifier.semantics { testTagsAsResourceId = true }.testTag("sheet")) {
            SheetContent(row, body, nowSec, onOpen)
        }
    }
}

@Composable
private fun SheetContent(row: RowModel, body: SheetBody, nowSec: Long, onOpen: () -> Unit) {
    val context = LocalContext.current
    val maxHeight = with(LocalDensity.current) { (LocalWindowInfo.current.containerSize.height * MAX_HEIGHT).toDp() }
    // links in the text open the same way as the list does
    val links = remember(context) {
        object : UriHandler {
            override fun openUri(uri: String) = openLink(context, uri)
        }
    }
    // the cap sits on the content; a height limit on the sheet itself throws off its anchors
    Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // the letter tile would vanish on the raised sheet
                Favicon(row.iconUrl, row.source, faded = false, tile = WickColors.Night)
                Spacer(Modifier.width(10.dp))
                Text(
                    row.source,
                    style = SourceStyle,
                    color = WickColors.Meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val age = formatAge(nowSec, row.publishedSec)
                Text(
                    if (age == "now") age else stringResource(R.string.sheet_age, age),
                    style = SourceStyle,
                    color = WickColors.Meta,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(row.title, style = TitleStyle, color = WickColors.Paper)
            Spacer(Modifier.height(14.dp))
            when (body) {
                SheetBody.Loading -> Unit
                is SheetBody.Ready -> if (body.text == null) {
                    Text(stringResource(R.string.sheet_no_text), style = EmptyStyle, color = WickColors.Meta)
                } else {
                    CompositionLocalProvider(LocalUriHandler provides links) {
                        Text(body.text, style = WickText.SheetBody, color = WickColors.Paper)
                    }
                }
            }
        }
        if (row.url != null) Button(
            onClick = onOpen,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WickColors.Amber, contentColor = WickColors.Night),
            contentPadding = PaddingValues(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 26.dp),
        ) {
            Text(stringResource(R.string.sheet_open_browser), style = ButtonStyle)
        }
    }
}
