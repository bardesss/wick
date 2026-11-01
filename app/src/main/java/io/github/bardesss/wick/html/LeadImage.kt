package io.github.bardesss.wick.html

import org.jsoup.Jsoup

private const val MAX_INPUT = 200_000
private val tinySides = setOf(1, 2)

/**
 * The address of the first image in feed HTML that is worth showing, or null. Relative addresses
 * are resolved against the article's [link]. Only http and https count, and an image that says it
 * is one or two pixels wide or high (a tracking pixel) is passed over.
 */
fun leadImageUrl(html: String?, link: String?): String? {
    if (html.isNullOrBlank()) return null
    val body = Jsoup.parseBodyFragment(html.take(MAX_INPUT), link?.trim().orEmpty()).body()
    for (img in body.select("img[src]")) {
        if (side(img.attr("width")) in tinySides || side(img.attr("height")) in tinySides) continue
        val url = img.absUrl("src").trim()
        if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) return url
    }
    return null
}

// "1", "1px" and " 2 " all count
private fun side(attr: String): Int? = attr.trim().takeWhile { it.isDigit() }.toIntOrNull()
