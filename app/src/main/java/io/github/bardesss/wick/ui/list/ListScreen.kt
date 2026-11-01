package io.github.bardesss.wick.ui.list

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.bardesss.wick.R
import io.github.bardesss.wick.data.LinkTarget
import io.github.bardesss.wick.ui.isWebUrl
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CountStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 14.sp, fontFeatureSettings = "tnum")
private val NoteStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 13.sp)
private val ToastStyle = TextStyle(fontFamily = InstrumentSans, fontSize = 13.5.sp)
private val ToastShape = RoundedCornerShape(12.dp)
private val FlameSize = 28.dp
private val TapTarget = 48.dp
private val MarkAllHeight = 32.dp
private val MarkAllShape = RoundedCornerShape(18.dp)
private val ArmedStyle = TextStyle(fontFamily = InstrumentSans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
private val FOOTER_SLACK = 16.dp
private const val ARM_TIMEOUT_MS = 4000L
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
    val canMarkAll by vm.canMarkAll.collectAsStateWithLifecycle()
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
    // an armed "Mark all read" is dropped after a pause, and when the list moves
    LaunchedEffect(arm.armed) {
        if (arm.armed) {
            delay(ARM_TIMEOUT_MS)
            arm.disarm()
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (it) arm.disarm() }
    }
    val snackbar = remember { SnackbarHostState() }
    val toast = remember { arrayOfNulls<Job>(1) }
    val markedRead = stringResource(R.string.list_marked_read)
    val markedUnread = stringResource(R.string.list_marked_unread)
    var sheetId by rememberSaveable { mutableStateOf<String?>(null) }
    val sheetKept = remember { arrayOfNulls<RowModel>(1) }

    val linkTarget by vm.linkTarget.collectAsStateWithLifecycle(LinkTarget.InApp)
    val open = remember(vm, context, linkTarget) {
        { row: RowModel ->
            row.url?.takeIf(::isWebUrl)?.let { url ->
                openLink(context, url, linkTarget)
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
        markEnabled = canMarkAll,
        armed = arm.armed,
        animate = !reducedMotion,
        onSelect = { v ->
            // a view switch must not leave an armed mark all to confirm against the new view
            arm.disarm()
            vm.select(v)
            scope.launch { drawerState.show(open = false, animate = !reducedMotion) }
        },
        onMarkAll = {
            if (arm.tap(canMarkAll && count > 0)) {
                haptics.tick()
                vm.markAllRead()
                scope.launch { drawerState.show(open = false, animate = !reducedMotion) }
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
        canMarkAll = canMarkAll,
        markArmed = arm.armed,
        onMarkAll = {
            if (arm.tap(canMarkAll && count > 0)) {
                haptics.tick()
                vm.markAllRead()
            }
        },
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
            linkTarget = linkTarget,
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
    canMarkAll: Boolean = true,
    markArmed: Boolean = false,
    onMarkAll: () -> Unit = {},
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
            Header(viewName, count, animate = !reducedMotion, canMarkAll, markArmed, onMarkAll, onOpenPicker)
            if (message != null) {
                Text(
                    message,
                    style = NoteStyle,
                    color = WickColors.Meta,
                    modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 8.dp),
                )
            }
            val pull = rememberPullToRefreshState()
            val rowCount by rememberUpdatedState(rows?.size ?: -1)
            val refreshingNow by rememberUpdatedState(refreshing)
            val refreshNow by rememberUpdatedState(onRefresh)
            val pullScope = rememberCoroutineScope()
            val density = LocalDensity.current
            val footerPull = remember(listState, pull) {
                val slackPx = with(density) { FOOTER_SLACK.toPx() }
                FooterPull(
                    state = pull,
                    scope = pullScope,
                    thresholdPx = with(density) { PullToRefreshDefaults.PositionalThreshold.toPx() },
                    // the footer is the first item on screen and has hardly moved up
                    active = {
                        listState.firstVisibleItemIndex == rowCount &&
                            listState.firstVisibleItemScrollOffset <= slackPx
                    },
                    refreshing = { refreshingNow },
                    onRefresh = { refreshNow() },
                )
            }
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
                    LazyColumn(state = listState, modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(footerPull)
                            // a gesture that ends without a fling, or is cancelled, still ends the pull
                            .pointerInput(footerPull) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    try {
                                        do {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                        } while (event.changes.any { it.pressed })
                                        footerPull.release()
                                    } catch (e: CancellationException) {
                                        footerPull.cancel()
                                        throw e
                                    }
                                }
                            }
                            .testTag("list"),
                    ) {
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
                                    modifier = moved.testTag("row").semantics {
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
private fun Header(
    name: String,
    count: Int,
    animate: Boolean,
    canMarkAll: Boolean,
    markArmed: Boolean,
    onMarkAll: () -> Unit,
    onClick: () -> Unit,
) {
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
            .testTag("header")
            .clickable(interactionSource = null, indication = null, onClickLabel = chooseView, role = Role.Button, onClick = onClick)
            .statusBarsPadding()
            .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 6.dp)
            // the mark all tap target sets the height, so it does not change when that control hides
            .heightIn(min = TapTarget),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f)) {
            Image(
                painter = painterResource(R.drawable.ic_flame),
                contentDescription = null,
                // the flame stands 10% above the bottom of its box, so that edge goes on the title's baseline
                modifier = Modifier.size(FlameSize).alignBy { (it.measuredHeight * 0.9f).roundToInt() },
            )
            Spacer(Modifier.width(6.dp))
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
        if (count > 0 && canMarkAll) {
            Spacer(Modifier.width(4.dp))
            MarkAll(count, markArmed, animate, onMarkAll)
        }
    }
}

/** The check icon that turns into "Tap again to mark N read" once armed. */
@Composable
private fun MarkAll(count: Int, armed: Boolean, animate: Boolean, onClick: () -> Unit) {
    val fillTarget = if (armed) WickColors.Amber else Color.Transparent
    val inkTarget = if (armed) WickColors.Night else WickColors.Amber
    val fill by animateColorAsState(fillTarget, tween(200), label = "fill")
    val ink by animateColorAsState(inkTarget, tween(200), label = "ink")
    val label = if (armed) stringResource(R.string.picker_mark_all_armed, count) else stringResource(R.string.picker_mark_all)
    // the pill is smaller than a finger, so the tap target around it is 48dp
    Box(
        Modifier
            .heightIn(min = TapTarget)
            .widthIn(min = TapTarget)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick)
            .testTag("mark_all"),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .height(MarkAllHeight)
                .clip(MarkAllShape)
                .background(if (animate) fill else fillTarget)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_done_all),
                contentDescription = null,
                tint = if (animate) ink else inkTarget,
                modifier = Modifier.size(20.dp),
            )
            if (armed) {
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.list_mark_all_armed, count),
                    style = ArmedStyle,
                    color = if (animate) ink else inkTarget,
                    maxLines = 1,
                )
            }
        }
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
