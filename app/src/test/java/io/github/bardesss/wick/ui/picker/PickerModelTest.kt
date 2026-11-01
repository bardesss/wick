package io.github.bardesss.wick.ui.picker

import io.github.bardesss.wick.api.StreamId
import io.github.bardesss.wick.db.FeedEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerModelTest {
    private fun feed(id: String, title: String, folder: String?, icon: String? = null) =
        FeedEntity(id, title, folder, null, icon)

    private val feeds = listOf(
        feed("feed/1", "zeta blog", null),
        feed("feed/2", "Hacker News", "Tech"),
        feed("feed/3", "android Developers", "Tech"),
        feed("feed/4", "Rail News", "travel"),
        feed("feed/5", "Alpha notes", null),
        feed("feed/6", "Cooking", "Food", "/icon/6"),
    )

    @Test
    fun order_all_folders_then_loose_feeds() {
        val counts = mapOf(
            StreamId.All.raw to 12,
            StreamId.Folder("Tech").raw to 7,
            StreamId.Folder("Food").raw to 1,
            "feed/2" to 3,
            "feed/3" to 4,
        )

        val entries = buildPicker(feeds, counts)

        assertEquals(
            listOf(
                PickerEntry.All(12),
                PickerEntry.Folder("Food", 1),
                PickerEntry.Feed("feed/6", "Cooking", "/icon/6", 0, indented = true),
                PickerEntry.Folder("Tech", 7),
                PickerEntry.Feed("feed/3", "android Developers", null, 4, indented = true),
                PickerEntry.Feed("feed/2", "Hacker News", null, 3, indented = true),
                PickerEntry.Folder("travel", 0),
                PickerEntry.Feed("feed/4", "Rail News", null, 0, indented = true),
                PickerEntry.Feed("feed/5", "Alpha notes", null, 0, indented = false),
                PickerEntry.Feed("feed/1", "zeta blog", null, 0, indented = false),
            ),
            entries,
        )
    }

    @Test
    fun zero_counts_kept() {
        val entries = buildPicker(feeds, emptyMap())

        assertEquals(1 + 3 + feeds.size, entries.size)
        assertEquals(PickerEntry.All(0), entries.first())
        assertTrue(entries.filterIsInstance<PickerEntry.Feed>().all { it.count == 0 })
        assertTrue(entries.filterIsInstance<PickerEntry.Folder>().all { it.count == 0 })
    }

    @Test
    fun no_feeds_still_lists_all() {
        assertEquals(listOf<PickerEntry>(PickerEntry.All(5)), buildPicker(emptyList(), mapOf(StreamId.All.raw to 5)))
    }

    @Test
    fun collapsed_folders_keep_their_row_and_lose_their_feeds() {
        val entries = buildPicker(feeds, mapOf("feed/2" to 3, StreamId.Folder("Tech").raw to 3))

        val folded = foldPicker(entries, setOf("Tech"))

        assertTrue(PickerEntry.Folder("Tech", 3, collapsed = true) in folded)
        assertTrue(folded.filterIsInstance<PickerEntry.Feed>().none { it.id == "feed/2" || it.id == "feed/3" })
        // other folders and the loose feeds stay
        assertTrue(folded.any { it is PickerEntry.Feed && it.id == "feed/4" })
        assertTrue(folded.any { it is PickerEntry.Feed && it.id == "feed/1" && !it.indented })
        assertEquals(entries.size - 2, folded.size)
    }

    private val withCounts = buildPicker(
        feeds,
        mapOf(
            StreamId.All.raw to 8,
            StreamId.Folder("Tech").raw to 8,
            "feed/2" to 8,
            "feed/5" to 0,
        ),
    )

    private fun names(list: List<PickerEntry>) = list.map {
        when (it) {
            is PickerEntry.All -> "all"
            is PickerEntry.Folder -> "folder:" + it.name
            is PickerEntry.Feed -> it.id
        }
    }

    @Test
    fun hiding_drops_feeds_and_folders_with_nothing_unread() {
        val shown = hideEmpty(withCounts, StreamId.All)

        assertEquals(listOf("all", "folder:Tech", "feed/2"), names(shown))
    }

    @Test
    fun hiding_keeps_the_selected_feed() {
        val shown = hideEmpty(withCounts, StreamId.Feed("feed/5"))

        assertEquals(listOf("all", "folder:Tech", "feed/2", "feed/5"), names(shown))
    }

    @Test
    fun hiding_keeps_the_selected_folder_and_the_folder_of_a_selected_feed() {
        assertEquals(
            listOf("all", "folder:Food", "folder:Tech", "feed/2"),
            names(hideEmpty(withCounts, StreamId.Folder("Food"))),
        )
        assertEquals(
            listOf("all", "folder:Food", "feed/6", "folder:Tech", "feed/2"),
            names(hideEmpty(withCounts, StreamId.Feed("feed/6"))),
        )
    }

    @Test
    fun hiding_keeps_all_unread_when_nothing_is_unread() {
        assertEquals(listOf("all"), names(hideEmpty(buildPicker(feeds, emptyMap()), StreamId.All)))
    }

    @Test
    fun the_folder_of_the_selected_feed_stays_open() {
        val entries = buildPicker(feeds, mapOf("feed/2" to 3, StreamId.Folder("Tech").raw to 3))

        val folded = foldPicker(entries, setOf("Tech", "Food"), StreamId.Feed("feed/2"))

        assertTrue(PickerEntry.Folder("Tech", 3) in folded)
        assertTrue(folded.any { it is PickerEntry.Feed && it.id == "feed/2" })
        // another folder stays folded, and so does Tech when the folder itself is the view
        assertTrue(PickerEntry.Folder("Food", 0, collapsed = true) in folded)
        assertTrue(PickerEntry.Folder("Tech", 3, collapsed = true) in foldPicker(entries, setOf("Tech"), StreamId.Folder("Tech")))
    }

    @Test
    fun nothing_collapsed_leaves_the_list_alone() {
        val entries = buildPicker(feeds, emptyMap())

        assertEquals(entries, foldPicker(entries, emptySet()))
    }

    @Test
    fun tap_arms_first_and_marks_on_the_second() {
        val arm = MarkAllArm()
        assertFalse(arm.tap(canMark = true))
        assertTrue(arm.armed)
        assertTrue(arm.tap(canMark = true))
        assertFalse(arm.armed)
    }

    @Test
    fun tap_does_not_arm_with_nothing_to_mark() {
        val arm = MarkAllArm()
        assertFalse(arm.tap(canMark = false))
        assertFalse(arm.armed)
    }

    @Test
    fun mark_all_needs_two_taps_and_disarms_on_close() {
        val arm = MarkAllArm()
        assertFalse(arm.armed)
        assertFalse(arm.confirm())

        arm.arm()
        assertTrue(arm.armed)
        assertTrue(arm.confirm())
        assertFalse(arm.armed)

        arm.arm()
        arm.disarm()
        assertFalse(arm.armed)
        assertFalse(arm.confirm())
    }
}
