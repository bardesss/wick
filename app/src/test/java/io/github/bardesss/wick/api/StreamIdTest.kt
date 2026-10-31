package io.github.bardesss.wick.api

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamIdTest {
    @Test
    fun raw_values() {
        assertEquals("user/-/state/com.google/reading-list", StreamId.All.raw)
        assertEquals("user/-/label/News", StreamId.Folder("News").raw)
        assertEquals("feed/12", StreamId.Feed("feed/12").raw)
    }

    @Test
    fun folder_name_is_percent_encoded_as_one_segment() {
        assertEquals(
            "user/-/label/Tech%20%26%20Co%2F%C3%84",
            StreamId.Folder("Tech & Co/Ä").encodedPath,
        )
    }

    @Test
    fun feed_and_all_paths_keep_their_slashes() {
        assertEquals("feed/12", StreamId.Feed("feed/12").encodedPath)
        assertEquals("user/-/state/com.google/reading-list", StreamId.All.encodedPath)
    }

    @Test
    fun parse_round_trips_raw() {
        val all = listOf(StreamId.All, StreamId.Folder("Tech & Co/Ä"), StreamId.Folder("a"), StreamId.Feed("feed/12"))
        all.forEach { assertEquals(it, StreamId.parse(it.raw)) }
    }

    @Test
    fun entities_decode() {
        assertEquals("A & B", decodeEntities("A &amp; B"))
        assertEquals("it's \"x\" <y>", decodeEntities("it&#39;s &quot;x&quot; &lt;y&gt;"))
        assertEquals("Ä Ä", decodeEntities("&#xC4; &#196;"))
        assertEquals("&bogus; &#99999999;", decodeEntities("&bogus; &#99999999;"))
    }
}
