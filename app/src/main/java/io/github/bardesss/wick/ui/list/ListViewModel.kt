package io.github.bardesss.wick.ui.list

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.bardesss.wick.AppContainer
import io.github.bardesss.wick.api.ApiError
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.data.CredentialStore
import io.github.bardesss.wick.data.LinkTarget
import io.github.bardesss.wick.data.SettingsStore
import io.github.bardesss.wick.db.FeedDao
import io.github.bardesss.wick.db.FeedEntity
import io.github.bardesss.wick.db.ItemDao
import io.github.bardesss.wick.db.LocalState
import io.github.bardesss.wick.db.SnapshotDao
import io.github.bardesss.wick.db.SnapshotRow
import io.github.bardesss.wick.html.htmlToAnnotated
import io.github.bardesss.wick.html.leadImageUrl
import io.github.bardesss.wick.sync.MarkQueue
import io.github.bardesss.wick.sync.SyncEngine
import io.github.bardesss.wick.ui.picker.PickerEntry
import io.github.bardesss.wick.ui.picker.buildPicker
import io.github.bardesss.wick.ui.picker.foldPicker
import io.github.bardesss.wick.ui.picker.hideEmpty
import io.github.bardesss.wick.ui.Note
import io.github.bardesss.wick.ui.note
import io.github.bardesss.wick.ui.resolveIcon
import io.github.bardesss.wick.ui.sheet.SheetBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a row shows apart from its read state, which changes on its own (see [ListViewModel.read]). */
@Immutable
data class RowModel(
    val id: String,
    val title: String,
    val url: String?,
    val source: String,
    val iconUrl: String?,
    val publishedSec: Long,
)

private const val TAG = "Wick"
private const val KEY_VIEW = "view"
private const val STALE_MS = 30 * 60_000L

private class Marks(val read: Set<String>, val exempt: Set<String>)

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModel(
    private val savedState: SavedStateHandle,
    private val itemDao: ItemDao,
    private val feedDao: FeedDao,
    private val snapshotDao: SnapshotDao,
    private val sync: SyncEngine,
    private val queue: MarkQueue,
    private val settings: SettingsStore,
    private val creds: CredentialStore,
    /** Outlives the view model, for work that must finish when the app goes to the background. */
    private val appScope: CoroutineScope,
    /** The view shown last time, for the first frame; the stored list decides if it differs. */
    initialView: String? = null,
    private val clock: () -> Long = System::currentTimeMillis,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // A saved view means the process was rebuilt under the user, not opened fresh.
    private val restored = savedState.contains(KEY_VIEW)

    private val _view = MutableStateFlow(StreamId.parse(savedState[KEY_VIEW] ?: initialView ?: StreamId.All.raw))
    val view: StateFlow<StreamId> = _view.asStateFlow()

    private val tracker = SeenTracker()
    private var lastFrameIndex = HashMap<String, Int>()
    private var busy = false
    private var again = false
    private var applying = false
    private var backgroundedAt: Long? = null

    /**
     * The snapshot rows, or null until they have been read once. Rebuilt only when the snapshot
     * or the feeds change, so marking a row does not touch it.
     */
    val rows: StateFlow<List<RowModel>?> = combine(snapshotDao.snapshotIds(), feedDao.all()) { _, _ -> }
        .mapLatest {
            val server = creds.auth()?.server.orEmpty()
            snapshotDao.rowsNow().map { it.toModel(server) }
        }
        .flowOn(work)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val marks = snapshotDao.states()
        .map { states -> Marks(idsWhere(states) { it.read }, idsWhere(states) { it.manualUnread }) }
        .flowOn(work)
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    /** Ids of the snapshot rows that are read. */
    val read: StateFlow<Set<String>> = marks.map { it.read }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Rows the user marked unread by hand are left alone by scrolling until the next refresh.
    private val exempt: StateFlow<Set<String>> = marks.map { it.exempt }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val count: StateFlow<Int> = combine(_view, sync.displayedCounts()) { v, counts -> counts[v.raw] ?: 0 }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** The folder or feed name, or null for the whole account. */
    val viewName: StateFlow<String?> = combine(_view, feedDao.all()) { v, feeds ->
        when (v) {
            StreamId.All -> null
            is StreamId.Folder -> v.name
            is StreamId.Feed -> feeds.firstOrNull { it.id == v.id }?.title.orEmpty()
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The drawer list with the icons resolved against the server. */
    val picker: StateFlow<List<PickerEntry>> = combine(
        feedDao.all(),
        sync.displayedCounts(),
        settings.collapsedFolders,
        settings.hideEmpty,
        _view,
    ) { feeds, counts, collapsed, hide, selected ->
        val server = creds.auth()?.server.orEmpty()
        val entries = buildPicker(feeds, counts).map { e ->
            if (e is PickerEntry.Feed) e.copy(iconUrl = resolveIcon(server, e.iconUrl)) else e
        }
        foldPicker(if (hide) hideEmpty(entries, selected) else entries, collapsed, selected)
    }
        .flowOn(work)
        .stateIn(viewModelScope, SharingStarted.Eagerly, listOf(PickerEntry.All(0)))

    private val _refreshing = MutableStateFlow(false)

    /** True while the pull indicator should show. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<Note?>(null)

    /** A quiet note under the header after a refresh failed. */
    val message: StateFlow<Note?> = _message.asStateFlow()

    init {
        savedState[KEY_VIEW] = _view.value.raw
        // a view that is not in the account (a stale cached one) falls back to All once feeds are known
        viewModelScope.launch {
            feedDao.all().filter { it.isNotEmpty() }.collect { feeds ->
                val v = _view.value
                val missing = when (v) {
                    StreamId.All -> false
                    is StreamId.Folder -> feeds.none { it.folder == v.name }
                    is StreamId.Feed -> feeds.none { it.id == v.id }
                }
                if (missing) select(StreamId.All)
            }
        }
        viewModelScope.launch {
            // a fresh start shows the view the stored list belongs to
            if (!restored) {
                snapshotDao.viewRaw()?.let { raw ->
                    _view.value = StreamId.parse(raw)
                    savedState[KEY_VIEW] = raw
                }
            }
            val stored = snapshotDao.snapshotIds().first()
            // a fresh start always refreshes; a restored one only when the list is old
            if (!restored || isStale()) startRefresh(indicator = stored.isEmpty())
        }
    }

    fun refresh() = startRefresh(indicator = true)

    /** Folds or unfolds a drawer folder; it stays that way across starts. */
    fun toggleFolder(name: String) {
        appScope.launch { settings.toggleCollapsed(name) }
    }

    fun select(view: StreamId) {
        if (view == _view.value) {
            startRefresh(indicator = true)
            return
        }
        _view.value = view
        savedState[KEY_VIEW] = view.raw
        appScope.launch { settings.setLastView(view.raw) }
        viewModelScope.launch {
            // show what is stored for the view at once; the refresh then brings it up to date
            val feedIds = feedIdsOf(view, feedDao.feedsNow())
            replaceSnapshot(itemDao.unreadIds(feedIds, settings.oldestFirst.first()), writtenAtMs = 0, view)
            startRefresh(indicator = true)
        }
    }

    /**
     * Marks the whole view read on the server. The cutoff is the newest of the rows loaded and the
     * unread items stored for the view, so it covers what the header counts, whether or not the
     * list has caught up. With neither, it is now. Items that arrive later stay unread.
     */
    fun markAllRead() {
        val v = _view.value
        val loaded = rows.value
        if (loaded.isNullOrEmpty() && count.value <= 0) return
        viewModelScope.launch {
            val feedIds = feedIdsOf(v, feedDao.feedsNow())
            val newest = listOfNotNull(loaded?.maxOfOrNull { it.publishedSec }, itemDao.newestUnreadSec(feedIds)).maxOrNull()
                ?: (clock() / 1000)
            queue.markAll(v, feedIds, newest)
            // the refresh must see the mark-all in the queue
            queue.awaitApplied()
            startRefresh(indicator = true)
        }
    }

    /** Feeds one laid out frame of the list. Returns true while it wants the next frame too. */
    fun onVisible(frame: ListFrame): Boolean {
        // the snapshot is being swapped; keep the frames coming so tracking resumes right after
        if (applying) return true
        val readNow = read.value
        val current = HashMap<String, Int>(frame.items.size * 2)
        val visible = ArrayList<Visibility>(frame.items.size + 4)
        var firstIndex = Int.MAX_VALUE
        for (item in frame.items) {
            current[item.id] = item.index
            if (item.index < firstIndex) firstIndex = item.index
            if (item.id in readNow || item.size <= 0) continue
            val top = maxOf(item.offset, frame.viewportStart)
            val bottom = minOf(item.offset + item.size, frame.viewportEnd)
            val fraction = (bottom - top).coerceAtLeast(0).toFloat() / item.size
            visible += Visibility(item.id, fraction, offTop = item.offset + item.size <= frame.viewportStart)
        }
        // rows the list dropped since the last frame, above the first one still laid out, left by the top
        for ((id, index) in lastFrameIndex) {
            if (id !in current && index < firstIndex && id !in readNow) visible += Visibility(id, 0f, offTop = true)
        }
        lastFrameIndex = current
        for (id in tracker.onFrame(frame.nowMs, visible, exempt.value)) queue.mark(id, read = true, manual = false)
        return tracker.needsFrames()
    }

    fun open(id: String) {
        if (id !in read.value) queue.mark(id, read = true, manual = false)
    }

    fun toggleRead(id: String) {
        if (id in read.value) queue.mark(id, read = false, manual = true) else queue.mark(id, read = true, manual = false)
    }

    /** Whether ticks are on; the screen follows it in its [io.github.bardesss.wick.ui.Haptics]. */
    val haptics: Flow<Boolean> get() = settings.haptics

    /** Where links open. */
    val linkTarget: Flow<LinkTarget> get() = settings.linkTarget

    /**
     * What the sheet shows for an item, read and converted off the main thread. The text is null
     * when the feed sent none; there is no image address when images are off.
     */
    suspend fun sheetBody(id: String, link: String?): SheetBody.Ready = withContext(work) {
        val html = itemDao.htmlOf(id)
        val image = if (settings.leadImages.first()) leadImageUrl(html, link) else null
        SheetBody.Ready(htmlToAnnotated(html), image)
    }

    /** Queued marks are sent by the flush job the app enqueues when it stops. */
    fun onBackground() {
        backgroundedAt = clock()
    }

    fun onForeground() {
        // the first start of this view model is handled in init
        val since = backgroundedAt ?: return
        backgroundedAt = null
        // only time spent away counts; a long read in the foreground keeps its list
        if (!busy && clock() - since > STALE_MS) startRefresh(indicator = true)
    }

    // After a restore the time away is unknown, so the age of the snapshot stands in for it.
    private suspend fun isStale(): Boolean = clock() - (snapshotDao.writtenAt() ?: 0L) > STALE_MS

    private fun startRefresh(indicator: Boolean) {
        if (indicator) _refreshing.value = true
        if (busy) {
            again = true
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                do {
                    again = false
                    val v = _view.value
                    val result = try {
                        // marks made just before, say right before a view switch, must be in Room first
                        queue.awaitApplied()
                        sync.refresh(v, settings.oldestFirst.first())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    result.fold(
                        onSuccess = { ids ->
                            if (v == _view.value) replaceSnapshot(ids, clock(), v) else again = true
                            _message.value = null
                        },
                        onFailure = { e ->
                            if (e == ApiError.BadCredentials) {
                                creds.clear()
                            } else {
                                _message.value = (e as? ApiError ?: ApiError.Unreachable).note()
                            }
                        },
                    )
                } while (again)
            } finally {
                busy = false
                _refreshing.value = false
            }
        }
    }

    /** Swaps the snapshot with scroll marking paused. On failure the old list stays. */
    private suspend fun replaceSnapshot(ids: List<String>, writtenAtMs: Long, view: StreamId) {
        applying = true
        try {
            snapshotDao.replaceSnapshot(ids, writtenAtMs, view.raw)
            tracker.reset()
            lastFrameIndex = HashMap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "could not store the new list", e)
        } finally {
            applying = false
        }
    }

    private fun feedIdsOf(view: StreamId, feeds: List<FeedEntity>): List<String>? = when (view) {
        StreamId.All -> null
        is StreamId.Folder -> feeds.filter { it.folder == view.name }.map { it.id }
        is StreamId.Feed -> listOf(view.id)
    }

    private fun idsWhere(list: List<LocalState>, test: (LocalState) -> Boolean): Set<String> =
        list.filter(test).mapTo(HashSet()) { it.id }

    private fun SnapshotRow.toModel(server: String) = RowModel(
        id = id,
        title = title,
        url = url,
        source = feedTitle.orEmpty(),
        iconUrl = resolveIcon(server, feedIconUrl),
        publishedSec = publishedSec,
    )

    companion object {
        fun factory(c: AppContainer) = viewModelFactory {
            initializer {
                ListViewModel(
                    savedState = createSavedStateHandle(),
                    itemDao = c.itemDao,
                    feedDao = c.feedDao,
                    snapshotDao = c.snapshotDao,
                    sync = c.syncEngine,
                    queue = c.markQueue,
                    settings = c.settings,
                    creds = c.creds,
                    appScope = c.appScope,
                    initialView = c.startView(),
                )
            }
        }
    }
}
