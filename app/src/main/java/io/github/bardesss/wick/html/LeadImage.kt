package io.github.bardesss.wick.html

import org.jsoup.Jsoup

private val tinySides = setOf(0, 1, 2)
private val hidden = Regex("display\\s*:\\s*none", RegexOption.IGNORE_CASE)

/**
 * The address of the first image in feed HTML that is worth showing, or null. Relative addresses
 * are resolved against the article's [link]. Only http and https count; an http address is
 * returned as https so the image loads on a secure connection. An image that says it is up to two
 * pixels wide or high, or is hidden by its style (a tracking pixel), is passed over.
 */
fun leadImageUrl(html: String?, link: String?): String? {
    if (html.isNullOrBlank()) return null
    val body = Jsoup.parseBodyFragment(html.take(MAX_HTML_INPUT), link?.trim().orEmpty()).body()
    for (img in body.select("img[src]")) {
        if (side(img.attr("width")) in tinySides || side(img.attr("height")) in tinySides) continue
        if (hidden.containsMatchIn(img.attr("style"))) continue
        val url = img.absUrl("src").trim()
        if (url.startsWith("https://", ignoreCase = true)) return url
        if (url.startsWith("http://", ignoreCase = true)) return "https://" + url.substring("http://".length)
    }
    return null
}

// "1", "1px" and " 2 " all count
private fun side(attr: String): Int? = attr.trim().takeWhile { it.isDigit() }.toIntOrNull()
