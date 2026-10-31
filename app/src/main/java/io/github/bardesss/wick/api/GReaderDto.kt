package io.github.bardesss.wick.api

import kotlinx.serialization.Serializable

@Serializable
internal data class SubscriptionListDto(val subscriptions: List<SubscriptionDto> = emptyList())

@Serializable
internal data class SubscriptionDto(
    val id: String,
    val title: String = "",
    val categories: List<CategoryDto> = emptyList(),
    val htmlUrl: String? = null,
    val iconUrl: String? = null,
)

@Serializable
internal data class CategoryDto(val id: String = "", val label: String = "")

@Serializable
internal data class UnreadCountsDto(val unreadcounts: List<UnreadCountDto> = emptyList())

@Serializable
internal data class UnreadCountDto(val id: String, val count: Long = 0)

@Serializable
internal data class StreamContentsDto(
    val items: List<StreamItemDto> = emptyList(),
    val continuation: String? = null,
)

@Serializable
internal data class StreamItemDto(
    val id: String,
    val published: Long = 0,
    val title: String? = null,
    val canonical: List<HrefDto> = emptyList(),
    val summary: ContentDto? = null,
    val origin: OriginDto? = null,
)

@Serializable
internal data class HrefDto(val href: String? = null)

@Serializable
internal data class ContentDto(val content: String? = null)

@Serializable
internal data class OriginDto(
    val streamId: String? = null,
    val title: String? = null,
    val htmlUrl: String? = null,
)

internal fun StreamItemDto.toItem(): Item {
    val link = canonical.firstOrNull()?.href?.takeIf { it.isNotBlank() }
    val resolved = title?.takeIf { it.isNotBlank() }?.let { decodeEntities(it) }
        ?: origin?.title?.takeIf { it.isNotBlank() }?.let { decodeEntities(it) }
        ?: link?.let { hostOf(it) }
        ?: ""
    return Item(
        id = id,
        feedId = origin?.streamId.orEmpty(),
        title = resolved,
        url = link,
        publishedSec = published,
        html = summary?.content,
    )
}

private fun hostOf(url: String): String? =
    runCatching { java.net.URI(url).host }.getOrNull()?.takeIf { it.isNotBlank() }

private val namedEntities: Map<String, Int> = mapOf(
    "amp" to 0x26, "lt" to 0x3C, "gt" to 0x3E, "quot" to 0x22, "apos" to 0x27, "nbsp" to 0xA0,
    "hellip" to 0x2026, "mdash" to 0x2014, "ndash" to 0x2013,
    "lsquo" to 0x2018, "rsquo" to 0x2019, "ldquo" to 0x201C, "rdquo" to 0x201D,
    "laquo" to 0xAB, "raquo" to 0xBB, "middot" to 0xB7, "bull" to 0x2022,
    "copy" to 0xA9, "reg" to 0xAE, "trade" to 0x2122, "euro" to 0x20AC,
)

private val entityRegex = Regex("&(?:#([0-9]{1,7})|#[xX]([0-9a-fA-F]{1,6})|([A-Za-z][A-Za-z0-9]{1,8}));")

/** Decodes named and numeric HTML entities. Unknown entities are left as written. */
internal fun decodeEntities(s: String): String {
    if ('&' !in s) return s
    return entityRegex.replace(s) { m ->
        val dec = m.groupValues[1]
        val hex = m.groupValues[2]
        val name = m.groupValues[3]
        val cp = when {
            dec.isNotEmpty() -> dec.toInt()
            hex.isNotEmpty() -> hex.toInt(16)
            else -> return@replace namedEntities[name]?.let { String(Character.toChars(it)) } ?: m.value
        }
        if (cp in 1..0x10FFFF && cp !in 0xD800..0xDFFF) String(Character.toChars(cp)) else m.value
    }
}
