package io.github.bardesss.wick.ui.list

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
import io.github.bardesss.wick.data.SettingsStore
import io.github.bardesss.wick.db.FeedDao
import io.github.bardesss.wick.db.ItemDao
import io.github.bardesss.wick.db.ItemRow
import io.github.bardesss.wick.db.SnapshotDao
import io.github.bardesss.wick.sync.MarkQueue
import io.github.bardesss.wick.sync.SyncEngine
import io.github.bardesss.wick.ui.login.LoginMessages
import io.github.bardesss.wick.ui.resolveIcon
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

private const val KEY_VIEW = "view"
private const val STALE_MS = 30 * 60_000L

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
    private val clock: () -> Long = System::currentTimeMillis,
    private val work: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // A saved view means the process was rebuilt under the user, not opened fresh.
    private val restored = savedState.contains(KEY_VIEW)

    private val _view = MutableStateFlow(StreamId.parse(savedState[KEY_VIEW] ?: StreamId.All.raw))
    val view: StateFlow<StreamId> = _view.asStateFlow()

    private val tracker = SeenTracker()
    private var lastFrameIndex = HashMap<String, Int>()
    private var busy = false
    private var again = false
    private var backgroundedAt: Long? = null

    private val snapshot = snapshotDao.snapshotIds()
        .onEach { resetTracking() }
        .flatMapLatest { itemDao.byIds(it) }
        .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val rows: StateFlow<List<RowModel>> = snapshot
        .map { list ->
            val server = creds.auth()?.server.orEmpty()
            list.map { it.toModel(server) }
        }
        .flowOn(work)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** False until the stored snapshot has been read once. */
    val loaded: StateFlow<Boolean> = snapshot.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Ids of the snapshot rows that are read. */
    val read: StateFlow<Set<String>> = snapshot
        .map { list -> idsWhere(list) { it.read } }
        .flowOn(work)
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // Rows the user marked unread by hand are left alone by scrolling until the next refresh.
    private val exempt: StateFlow<Set<String>> = snapshot
        .map { list -> idsWhere(list) { it.manualUnread } }
        .flowOn(work)
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

    private val _refreshing = MutableStateFlow(false)

    /** True while the pull indicator should show. */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)

    /** A quiet note under the header after a refresh failed. */
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        savedState[KEY_VIEW] = _view.value.raw
        if (!restored) {
            viewModelScope.launch {
                val stored = snapshotDao.snapshotIds().first()
                startRefresh(indicator = stored.isEmpty())
            }
        }
    }

    fun refresh() = startRefresh(indicator = true)

    fun select(view: StreamId) {
        if (view == _view.value) return
        _view.value = view
        savedState[KEY_VIEW] = view.raw
        viewModelScope.launch {
            // show what is stored for the view at once; the refresh then brings it up to date
            val feeds = feedDao.feedsNow()
            val feedIds = when (view) {
                StreamId.All -> null
                is StreamId.Folder -> feeds.filter { it.folder == view.name }.map { it.id }
                is StreamId.Feed -> listOf(view.id)
            }
            snapshotDao.replaceSnapshot(itemDao.unreadIds(feedIds, settings.oldestFirst.first()))
            startRefresh(indicator = true)
        }
    }

    /** Feeds one laid out frame of the list. Returns true while it wants the next frame too. */
    fun onVisible(frame: ListFrame): Boolean {
        if (busy) return false
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

    fun onBackground() {
        backgroundedAt = clock()
        viewModelScope.launch {
            try {
                queue.flush()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // the queue keeps the marks and sends them later
            }
        }
    }

    fun onForeground() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (clock() - since > STALE_MS) startRefresh(indicator = true)
    }

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
                        sync.refresh(v, settings.oldestFirst.first())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                    result.fold(
                        onSuccess = { ids ->
                            if (v == _view.value) snapshotDao.replaceSnapshot(ids) else again = true
                            _message.value = null
                        },
                        onFailure = { e ->
                            if (e == ApiError.BadCredentials) {
                                creds.clear()
                            } else {
                                _message.value = if (e is ApiError) LoginMessages.of(e) else LoginMessages.UNREACHABLE
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

    private fun resetTracking() {
        tracker.reset()
        lastFrameIndex = HashMap()
    }

    private fun idsWhere(list: List<ItemRow>, test: (ItemRow) -> Boolean): Set<String> =
        list.filter(test).mapTo(HashSet()) { it.id }

    private fun ItemRow.toModel(server: String) = RowModel(
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
                )
            }
        }
    }
}
