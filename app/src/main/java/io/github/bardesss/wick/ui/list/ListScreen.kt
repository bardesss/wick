package io.github.bardesss.wick.ui.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bardesss.wick.R
import io.github.bardesss.wick.ui.openLink
import io.github.bardesss.wick.ui.picker.MarkAllArm
import io.github.bardesss.wick.ui.picker.PickerDrawer
import io.github.bardesss.wick.ui.picker.show
import io.github.bardesss.wick.ui.rememberHaptics
import io.github.bardesss.wick.ui.rememberReducedMotion
import io.github.bardesss.wick.ui.sheet.SheetBody
import io.github.bardesss.wick.ui.sheet.SummarySheet
import io.github.bardesss.wick.ui.theme.InstrumentSans
import io.github.bardesss.wick.ui.theme.WickColors
import io.github.bardesss.wick.ui.theme.WickText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private val CountStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 14.sp, fontFeatureSettings = "tnum")
private val NoteStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 13.sp)
private val ToastStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 13.5.sp)
private val ToastShape = RoundedCornerShape(12.dp)
private const val FOOTER_KEY = 0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(vm: ListViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val rows by vm.rows.collectAsStateWithLifecycle()
    val read = vm.read.collectAsStateWithLifecycle()
    val count by vm.count.collectAsStateWithLifecycle()
    val name by vm.viewName.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val view by vm.view.collectAsStateWithLifecycle()
    val picker by vm.picker.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val arm = remember { MarkAllArm() }

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
    val ids = remember(rows) { rows?.map { it.id } }
    var shownIds by remember { mutableStateOf<List<String>?>(null) }
    LaunchedEffect(ids) {
        if (ids == null) return@LaunchedEffect
        if (!shownIds.isNullOrEmpty() && shownIds != ids) listState.scrollToItem(0)
        shownIds = ids
    }

    val haptics = rememberHaptics(vm.haptics)
    val reducedMotion by rememberReducedMotion()
    val scope = rememberCoroutineScope()
    // closing the drawer, however it happens, disarms "Mark all read"
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue }.collect { if (it == DrawerValue.Closed) arm.disarm() }
    }
    val snackbar = remember { SnackbarHostState() }
    val toast = remember { arrayOfNulls<Job>(1) }
    val markedRead = stringResource(R.string.list_marked_read)
    val markedUnread = stringResource(R.string.list_marked_unread)
    var sheetId by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetKept = remember { arrayOfNulls<RowModel>(1) }

    val open = remember(vm, context) {
        { row: RowModel ->
            row.url?.let { url ->
                openLink(context, url)
                vm.open(row.id)
            }
            Unit
        }
    }

    PickerDrawer(
        state = drawerState,
        entries = picker,
        selected = view,
        markCount = count,
        armed = arm.armed,
        animate = !reducedMotion,
        onSelect = { v ->
            vm.select(v)
            scope.launch { drawerState.show(open = false, animate = !reducedMotion) }
        },
        onMarkAll = {
            if (arm.armed) {
                if (arm.confirm()) {
                    haptics.tick()
                    vm.markAllRead()
                    scope.launch { drawerState.show(open = false, animate = !reducedMotion) }
                }
            } else if (count > 0) {
                arm.arm()
            }
        },
        onSettings = {
            scope.launch { drawerState.show(open = false, animate = false) }
            onOpenSettings()
        },
    ) {
    ListContent(
        viewName = name ?: stringResource(R.string.list_all_unread),
        count = count,
        message = message,
        rows = rows,
        read = read,
        nowSec = nowSec,
        refreshing = refreshing,
        listState = listState,
        onRefresh = vm::refresh,
        onOpenPicker = { scope.launch { drawerState.show(open = true, animate = !reducedMotion) } },
        onOpen = open,
        onFrame = vm::onVisible,
        onShowText = { row -> sheetId = row.id },
        onToggle = { row ->
            val nowRead = row.id !in read.value
            vm.toggleRead(row.id)
            // a new note replaces the one showing at once
            toast[0]?.cancel()
            toast[0] = scope.launch { snackbar.showSnackbar(if (nowRead) markedRead else markedUnread) }
        },
        onSwipeTick = haptics::tick,
        reducedMotion = reducedMotion,
        snackbar = snackbar,
    )
    }

    // a refresh may drop the row; the open sheet keeps showing it until the user closes it
    val sheetRow = sheetId?.let { id -> rows?.firstOrNull { it.id == id } ?: sheetKept[0]?.takeIf { it.id == id } }
    sheetKept[0] = sheetRow
    if (sheetRow != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val body by produceState<SheetBody>(SheetBody.Loading, sheetRow.id) {
            value = SheetBody.Ready(vm.sheetText(sheetRow.id))
        }
        SummarySheet(
            row = sheetRow,
            body = body,
            nowSec = nowSec,
            sheetState = sheetState,
            onOpen = {
                open(sheetRow)
                scope.launch { sheetState.hide() }.invokeOnCompletion { sheetId = null }
            },
            onDismiss = { sheetId = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListContent(
    viewName: String,
    count: Int,
    message: String?,
    /** Null until the stored list has been read; nothing is laid out until then. */
    rows: List<RowModel>?,
    read: State<Set<String>>,
    nowSec: Long,
    refreshing: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onOpenPicker: () -> Unit,
    onOpen: (RowModel) -> Unit,
    onFrame: (ListFrame) -> Boolean,
    onShowText: (RowModel) -> Unit = {},
    onToggle: (RowModel) -> Unit = {},
    onSwipeTick: () -> Unit = {},
    reducedMotion: Boolean = false,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    /** Fixed sideways offsets in px by row id, for previews. */
    swipePreview: Map<String, Float> = emptyMap(),
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
    val lock = remember { SwipeLock() }
    val showTextLabel = stringResource(R.string.row_show_text)
    val markReadLabel = stringResource(R.string.row_mark_read)
    val markUnreadLabel = stringResource(R.string.row_mark_unread)

    Box(Modifier.fillMaxSize().background(WickColors.Night)) {
        Column(Modifier.fillMaxSize()) {
            Header(viewName, count, animate = !reducedMotion, onOpenPicker)
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
                // laying out an empty list before the rows arrive would reset a restored scroll position
                if (rows != null) BoxWithConstraints(Modifier.fillMaxSize()) {
                    val footerHeight = maxHeight
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(rows, key = { _, row -> row.id }, contentType = { _, _ -> "item" }) { index, row ->
                            val isRead by remember(row.id) { derivedStateOf { row.id in read.value } }
                            SwipeRow(
                                id = row.id,
                                lock = lock,
                                reducedMotion = reducedMotion,
                                onThreshold = onSwipeTick,
                                onSwipe = { outcome ->
                                    when (outcome) {
                                        SwipeOutcome.OpenSheet -> onShowText(row)
                                        SwipeOutcome.ToggleRead -> onToggle(row)
                                        SwipeOutcome.None -> Unit
                                    }
                                },
                                startOffsetPx = swipePreview[row.id] ?: 0f,
                            ) { moved ->
                                ItemRow(
                                    row = row,
                                    read = isRead,
                                    nowSec = nowSec,
                                    divider = index > 0,
                                    onClick = { onOpen(row) },
                                    modifier = moved.semantics {
                                        customActions = listOf(
                                            CustomAccessibilityAction(showTextLabel) { onShowText(row); true },
                                            CustomAccessibilityAction(if (isRead) markUnreadLabel else markReadLabel) {
                                                onToggle(row)
                                                true
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        item(key = FOOTER_KEY, contentType = "caught_up") {
                            CaughtUp(Modifier.height(footerHeight))
                        }
                    }
                }
            }
        }
        SnackbarHost(
            snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
        ) { data ->
            Text(
                data.visuals.message,
                style = ToastStyle,
                color = WickColors.Paper,
                modifier = Modifier
                    .background(WickColors.Raised, ToastShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Header(name: String, count: Int, animate: Boolean, onClick: () -> Unit) {
    val chooseView = stringResource(R.string.list_choose_view)
    var tick by remember { mutableStateOf(false) }
    var shown by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count != shown) {
            shown = count
            // no flash when the system has animations off
            if (!animate) return@LaunchedEffect
            tick = true
            delay(350)
            tick = false
        }
    }
    val target = if (tick) WickColors.Amber else WickColors.Meta
    val animated by animateColorAsState(target, tween(300), label = "count")
    val countColor = if (animate) animated else target
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(interactionSource = null, indication = null, onClickLabel = chooseView, onClick = onClick)
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
