package io.github.bardesss.wick.ui.picker

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.db.FeedEntity

sealed interface PickerEntry {
    val count: Int

    data class All(override val count: Int) : PickerEntry

    data class Folder(val name: String, override val count: Int, val collapsed: Boolean = false) : PickerEntry

    data class Feed(
        val id: String,
        val title: String,
        val iconUrl: String?,
        override val count: Int,
        val indented: Boolean,
    ) : PickerEntry
}

private val ByName = compareBy<String, String>(String.CASE_INSENSITIVE_ORDER) { it }

/**
 * The drawer list: All, then each folder with its feeds under it, then the feeds that have no
 * folder. Folders and feeds are in alphabetical order. [counts] is keyed by raw stream id.
 */
fun buildPicker(feeds: List<FeedEntity>, counts: Map<String, Int>): List<PickerEntry> {
    val result = ArrayList<PickerEntry>(feeds.size + 8)
    result += PickerEntry.All(counts[StreamId.All.raw] ?: 0)
    val byTitle = compareBy<FeedEntity, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
    val grouped = feeds.groupBy { it.folder }
    for (folder in grouped.keys.filterNotNull().sortedWith(ByName)) {
        result += PickerEntry.Folder(folder, counts[StreamId.Folder(folder).raw] ?: 0)
        for (f in grouped.getValue(folder).sortedWith(byTitle)) {
            result += PickerEntry.Feed(f.id, f.title, f.iconUrl, counts[f.id] ?: 0, indented = true)
        }
    }
    for (f in grouped[null].orEmpty().sortedWith(byTitle)) {
        result += PickerEntry.Feed(f.id, f.title, f.iconUrl, counts[f.id] ?: 0, indented = false)
    }
    return result
}

/** Marks the folders in [collapsed] and leaves out their feeds. */
fun foldPicker(entries: List<PickerEntry>, collapsed: Set<String>): List<PickerEntry> {
    if (collapsed.isEmpty()) return entries
    val result = ArrayList<PickerEntry>(entries.size)
    var folded = false
    for (e in entries) {
        when {
            e is PickerEntry.Folder -> {
                folded = e.name in collapsed
                result += if (folded) e.copy(collapsed = true) else e
            }
            e is PickerEntry.Feed && e.indented && folded -> Unit
            else -> result += e
        }
    }
    return result
}

/** The two taps of "Mark all read": the first arms it, the second confirms. */
@Stable
class MarkAllArm {
    var armed by mutableStateOf(false)
        private set

    fun arm() {
        armed = true
    }

    /** True when it was armed, which is when the second tap should act. Disarms either way. */
    fun confirm(): Boolean {
        val was = armed
        armed = false
        return was
    }

    /** One tap: arms when idle and [canMark], and returns true when this tap should mark. */
    fun tap(canMark: Boolean): Boolean {
        if (armed) return confirm()
        if (canMark) arm()
        return false
    }

    fun disarm() {
        armed = false
    }
}
