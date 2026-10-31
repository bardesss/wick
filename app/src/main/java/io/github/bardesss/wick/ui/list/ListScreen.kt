package io.github.bardesss.wick.ui.list

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bardesss.wick.R
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.WickColors
import io.github.bardesss.wick.ui.theme.WickText
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val CountStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 14.sp, fontFeatureSettings = "tnum")
private val NoteStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 13.sp)
private const val FOOTER_KEY = 0

@Composable
fun ListScreen(vm: ListViewModel, onOpenPicker: () -> Unit) {
    val context = LocalContext.current
    val rows by vm.rows.collectAsStateWithLifecycle()
    val read = vm.read.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val name by vm.viewName.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onBackground() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onForeground() }

    // ages only move once a minute
    val nowSec by produceState(System.currentTimeMillis() / 1000) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis() / 1000
        }
    }

    // a new snapshot starts at the top; the first one keeps any restored position
    val ids = remember(rows) { rows.map { it.id } }
    var shownIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(ids) {
        if (shownIds != null && shownIds != ids) listState.scrollToItem(0)
        shownIds = ids
    }

    ListContent(
        viewName = name ?: stringResource(R.string.list_all_unread),
        count = count,
        message = message,
        rows = rows,
        read = read,
        loaded = loaded,
        nowSec = nowSec,
        refreshing = refreshing,
        listState = listState,
        onRefresh = vm::refresh,
        onOpenPicker = onOpenPicker,
        onOpen = { row ->
            row.url?.let { url ->
                openLink(context, url)
                vm.open(row.id)
            }
        },
        onFrame = vm::onVisible,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListContent(
    viewName: String,
    count: Int,
    message: String?,
    rows: List<RowModel>,
    read: State<Set<String>>,
    loaded: Boolean,
    nowSec: Long,
    refreshing: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpen: (RowModel) -> Unit,
    onFrame: (ListFrame) -> Boolean,
) {
    val frameSink by rememberUpdatedState(onFrame)
    LaunchedEffect(listState) {
        // every layout change, then every frame while a row on screen still needs dwell time
        snapshotFlow { listState.layoutInfo }.collectLatest {
            do {
                val more = withFrameMillis { t -> frameSink(listState.layoutInfo.toFrame(t)) }
            } while (more)
        }
    }

    Column(Modifier.fillMaxSize().background(WickColors.Night)) {
        Header(viewName, count, onOpenPicker)
        if (message != null) {
            Text(
                message,
                style = NoteStyle,
                color = WickColors.Meta,
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
            )
        }
        val pull = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            state = pull,
            modifier = Modifier.fillMaxSize(),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pull,
                    isRefreshing = refreshing,
                    containerColor = WickColors.Raised,
                    color = WickColors.Amber,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val footerHeight = maxHeight
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(rows, key = { _, row -> row.id }, contentType = { _, _ -> "item" }) { index, row ->
                        val isRead by remember(row.id) { derivedStateOf { row.id in read.value } }
                        ItemRow(
                            row = row,
                            read = isRead,
                            nowSec = nowSec,
                            divider = index > 0,
                            onClick = { onOpen(row) },
                        )
                    }
                    if (loaded) {
                        item(key = FOOTER_KEY, contentType = "caught_up") {
                            CaughtUp(Modifier.height(footerHeight))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(name: String, count: Int, onClick: () -> Unit) {
    var tick by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count != shown) {
            shown = count
            tick = true
            delay(350)
            tick = false
        }
    }
    val countColor by animateColorAsState(
        if (tick) WickColors.Amber else WickColors.Meta,
        tween(300),
        label = "count",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 10.dp),
    ) {
        Text(
            name,
            style = WickText.Header.copy(letterSpacing = (-0.4).sp),
            color = WickColors.Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alignByBaseline().weight(1f, fill = false),
        )
        Spacer(Modifier.width(10.dp))
        Text(count.toString(), style = CountStyle, color = countColor, modifier = Modifier.alignByBaseline())
    }
}

private fun LazyListLayoutInfo.toFrame(nowMs: Long): ListFrame {
    val items = ArrayList<FrameItem>(visibleItemsInfo.size)
    for (info in visibleItemsInfo) {
        val id = info.key as? String ?: continue
        items += FrameItem(id, info.index, info.offset, info.size)
    }
    return ListFrame(nowMs, viewportStartOffset, viewportEndOffset, items)
}

private fun openLink(context: Context, url: String) {
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
