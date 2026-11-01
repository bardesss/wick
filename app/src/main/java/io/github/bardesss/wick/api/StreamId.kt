package io.github.bardesss.wick.api

sealed interface StreamId {
    val raw: String

    /** The raw id with the variable part percent-encoded, for use as URL path segments. */
    val encodedPath: String

    companion object {
        private const val LABEL_PREFIX = "user/-/label/"

        fun parse(raw: String): StreamId = when {
            raw == All.raw -> All
            raw.startsWith(LABEL_PREFIX) -> Folder(raw.removePrefix(LABEL_PREFIX))
            else -> Feed(raw)
        }
    }

    data object All : StreamId {
        override val raw = "user/-/state/com.google/reading-list"
        override val encodedPath = raw
    }

    data class Folder(val name: String) : StreamId {
        override val raw get() = "user/-/label/$name"
        override val encodedPath get() = "user/-/label/" + percentEncode(name)
    }

    data class Feed(val id: String) : StreamId {
        override val raw get() = id
        override val encodedPath get() = id.split('/').joinToString("/") { percentEncode(it) }
    }
}

/** A feed and the folder it sits in, as the server lists it and as it is stored. */
interface FeedInFolder {
    val id: String
    val folder: String?
}

/** The ids of the [feeds] this view covers, or null for the whole account. */
fun StreamId.feedIds(feeds: List<FeedInFolder>): List<String>? = when (this) {
    StreamId.All -> null
    is StreamId.Folder -> feeds.filter { it.folder == name }.map { it.id }
    is StreamId.Feed -> listOf(id)
}

internal fun percentEncode(s: String): String {
    val sb = StringBuilder()
    for (b in s.toByteArray(Charsets.UTF_8)) {
        val c = b.toInt() and 0xff
        val unreserved = (c >= 'A'.code && c <= 'Z'.code) || (c >= 'a'.code && c <= 'z'.code) ||
            (c >= '0'.code && c <= '9'.code) || c == '-'.code || c == '.'.code || c == '_'.code || c == '~'.code
        if (unreserved) sb.append(c.toChar()) else sb.append('%').append("%02X".format(c))
    }
    return sb.toString()
}
