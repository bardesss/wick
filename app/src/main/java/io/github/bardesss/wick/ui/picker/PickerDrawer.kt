package io.github.bardesss.wick.ui.picker

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bardesss.wick.R
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.ui.Favicon
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.Newsreader
import io.github.bardesss.wick.ui.theme.WickColors

private const val DRAWER_WIDTH = 0.84f
private val Scrim = Color(0x99040608)
private val FeedText = Color(0xFFB7BEC6)
private val IdleBorder = Color(0xFF3A3226)
private val ButtonShape = RoundedCornerShape(14.dp)

private val AllStyle = TextStyle(fontFamily = Newsreader, fontWeight = FontWeight.Normal, fontSize = 20.sp)
private val FolderStyle = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 15.sp)
private val FeedStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 15.sp)
private val CountStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 13.sp, fontFeatureSettings = "tnum")
private val ButtonStyle = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 15.sp)
private val LinkStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 13.sp)

/** Opens or closes the drawer, without the slide when [animate] is off. */
suspend fun DrawerState.show(open: Boolean, animate: Boolean) {
    when {
        !animate -> snapTo(if (open) DrawerValue.Open else DrawerValue.Closed)
        open -> open()
        else -> close()
    }
}

/**
 * The view picker as a left drawer over [content]. Edge swipes do not open it, so they never
 * compete with row swipes; it opens from the header and a swipe or back closes it.
 */
@Composable
fun PickerDrawer(
    state: DrawerState,
    entries: List<PickerEntry>,
    selected: StreamId,
    markCount: Int,
    markEnabled: Boolean,
    armed: Boolean,
    animate: Boolean,
    onSelect: (StreamId) -> Unit,
    onMarkAll: () -> Unit,
    onSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val width = LocalConfiguration.current.screenWidthDp.dp * DRAWER_WIDTH
    ModalNavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(width),
                drawerShape = RectangleShape,
                drawerContainerColor = WickColors.Raised,
                drawerContentColor = WickColors.Paper,
                drawerTonalElevation = 0.dp,
            ) {
                DrawerBody(entries, selected, markCount, markEnabled, armed, animate, onSelect, onMarkAll, onSettings)
            }
        },
        drawerState = state,
        gesturesEnabled = state.isOpen,
        scrimColor = Scrim,
        content = content,
    )
}

@Composable
private fun DrawerBody(
    entries: List<PickerEntry>,
    selected: StreamId,
    markCount: Int,
    markEnabled: Boolean,
    armed: Boolean,
    animate: Boolean,
    onSelect: (StreamId) -> Unit,
    onMarkAll: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 28.dp, bottom = 8.dp)) {
            items(entries, key = { it.key() }, contentType = { it::class }) { entry ->
                EntryRow(entry, entry.isSelected(selected)) { onSelect(entry.stream()) }
            }
        }
        Column(Modifier.padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 12.dp)) {
            MarkAllButton(markCount, markEnabled && markCount > 0, armed, animate, onMarkAll)
            Text(
                stringResource(R.string.settings),
                style = LinkStyle,
                color = WickColors.Meta,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onSettings)
                    .padding(top = 14.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EntryRow(entry: PickerEntry, selected: Boolean, onClick: () -> Unit) {
    val countColor = when {
        selected -> WickColors.Amber
        entry.count == 0 -> WickColors.Faded
        else -> WickColors.Meta
    }
    val start = if (entry is PickerEntry.Feed && entry.indented) 40.dp else 22.dp
    val top = if (entry is PickerEntry.Folder) 10.dp else 0.dp
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = top)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(start = start, end = 22.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (label, style, color) = when (entry) {
            is PickerEntry.All -> Triple(stringResource(R.string.list_all_unread), AllStyle, WickColors.Paper)
            is PickerEntry.Folder -> Triple(entry.name, FolderStyle, WickColors.Paper)
            is PickerEntry.Feed -> Triple(entry.title, FeedStyle, FeedText)
        }
        if (entry is PickerEntry.Feed) {
            Favicon(entry.iconUrl, entry.title, faded = false, tile = WickColors.Night)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            label,
            style = style,
            color = if (selected) WickColors.Amber else color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(entry.count.toString(), style = CountStyle, color = countColor)
    }
}

@Composable
private fun MarkAllButton(count: Int, enabled: Boolean, armed: Boolean, animate: Boolean, onClick: () -> Unit) {
    val fillTarget = if (armed) WickColors.Amber else Color.Transparent
    val textTarget = if (armed) WickColors.Night else WickColors.Amber
    val borderTarget = if (armed) WickColors.Amber else IdleBorder
    val fill by animateColorAsState(fillTarget, tween(200), label = "fill")
    val text by animateColorAsState(textTarget, tween(200), label = "text")
    val border by animateColorAsState(borderTarget, tween(200), label = "border")
    Box(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .clip(ButtonShape)
            .background(if (animate) fill else fillTarget)
            .border(1.dp, if (animate) border else borderTarget, ButtonShape)
            .semantics { role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
    ) {
        Text(
            if (armed) stringResource(R.string.picker_mark_all_armed, count) else stringResource(R.string.picker_mark_all),
            style = ButtonStyle,
            color = if (animate) text else textTarget,
        )
    }
}

private fun PickerEntry.key(): String = when (this) {
    is PickerEntry.All -> "all"
    is PickerEntry.Folder -> "folder:$name"
    is PickerEntry.Feed -> "feed:$id"
}

private fun PickerEntry.stream(): StreamId = when (this) {
    is PickerEntry.All -> StreamId.All
    is PickerEntry.Folder -> StreamId.Folder(name)
    is PickerEntry.Feed -> StreamId.Feed(id)
}

private fun PickerEntry.isSelected(view: StreamId): Boolean = stream() == view
