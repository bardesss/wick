package io.github.bardesss.wick.html

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import io.github.bardesss.wick.ui.theme.WickColors
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private val Bold = SpanStyle(fontWeight = FontWeight.Bold)
private val Italic = SpanStyle(fontStyle = FontStyle.Italic)
private val Mono = SpanStyle(fontFamily = FontFamily.Monospace)
private val LinkStyles = TextLinkStyles(SpanStyle(color = WickColors.Amber, textDecoration = TextDecoration.Underline))

private val dropped = setOf(
    "script", "style", "img", "picture", "source", "iframe", "noscript", "svg", "video", "audio",
    "object", "embed", "canvas", "form", "input", "button", "select", "textarea", "head", "template",
)
private val blocks = setOf(
    "p", "div", "section", "article", "header", "footer", "aside", "main", "nav", "figure", "figcaption",
    "ul", "ol", "dl", "dt", "dd", "table", "tr", "hr", "address", "details", "summary",
    "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre",
)
private val headings = setOf("h1", "h2", "h3", "h4", "h5", "h6")
private val linkSchemes = listOf("http://", "https://")
/** How much of an item's HTML is parsed; the rest is cut, the sheet only shows the start anyway. */
internal const val MAX_HTML_INPUT = 200_000

/**
 * Turns feed HTML into styled text for the sheet. Returns null when nothing readable is left.
 * Pure, so it can run on any thread.
 */
fun htmlToAnnotated(html: String?): AnnotatedString? {
    if (html.isNullOrBlank()) return null
    val out = TextWriter()
    // very long feeds are cut rather than parsed whole; the sheet only shows the start anyway
    out.children(Jsoup.parseBodyFragment(html.take(MAX_HTML_INPUT)).body())
    return out.build()
}

private class Range(val style: Any, val start: Int, val end: Int)

private class TextWriter {
    private val text = StringBuilder()
    private val ranges = ArrayList<Range>()

    // breaks and spaces wait until the next visible character, so nothing trails a block
    private var breaks = 0
    private var space = false
    private var pre = 0

    fun children(el: Element) {
        for (child in el.childNodes()) node(child)
    }

    private fun node(n: Node) {
        when (n) {
            is TextNode -> text(n.wholeText)
            is Element -> element(n)
        }
    }

    private fun element(el: Element) {
        val tag = el.normalName()
        if (tag in dropped) return
        when (tag) {
            "br" -> {
                breaks = (breaks + 1).coerceAtMost(2)
                space = false
                return
            }
            "li" -> {
                lineBreak(1)
                put("• ")
                children(el)
                lineBreak(1)
                return
            }
        }
        val block = tag in blocks
        if (block) lineBreak(2)
        if (tag == "pre") pre++
        val start = text.length
        children(el)
        if (tag == "pre") pre--
        val style: Any? = when {
            tag in headings || tag == "b" || tag == "strong" -> Bold
            tag == "i" || tag == "em" || tag == "cite" || tag == "blockquote" -> Italic
            tag == "pre" || tag == "code" || tag == "kbd" || tag == "samp" || tag == "tt" -> Mono
            tag == "a" -> el.attr("href").trim().takeIf { href -> linkSchemes.any { href.startsWith(it, ignoreCase = true) } }
                ?.let { LinkAnnotation.Url(it, LinkStyles) }
            else -> null
        }
        if (style != null) mark(style, start)
        if (block) lineBreak(2)
    }

    private fun lineBreak(n: Int) {
        if (n > breaks) breaks = n
        space = false
    }

    private fun text(raw: String) {
        if (pre > 0) {
            if (raw.isNotEmpty()) put(raw)
            return
        }
        // each run of visible characters goes out in one piece
        var i = 0
        while (i < raw.length) {
            if (isGap(raw[i])) {
                space = true
                i++
                continue
            }
            val start = i
            while (i < raw.length && !isGap(raw[i])) i++
            put(raw.substring(start, i))
        }
    }

    private fun isGap(c: Char) = c.isWhitespace() || c == '\u00A0'

    private fun put(s: String) {
        if (text.isNotEmpty()) {
            if (breaks > 0) {
                repeat(breaks) { text.append('\n') }
            } else if (space) {
                text.append(' ')
            }
        }
        breaks = 0
        space = false
        text.append(s)
    }

    // the range starts after any break or space that was written ahead of its first character
    private fun mark(style: Any, from: Int) {
        var start = from
        while (start < text.length && (text[start] == '\n' || text[start] == ' ')) start++
        if (start < text.length) ranges += Range(style, start, text.length)
    }

    fun build(): AnnotatedString? {
        if (text.isBlank()) return null
        val builder = AnnotatedString.Builder(text.toString())
        for (r in ranges) {
            when (val s = r.style) {
                is SpanStyle -> builder.addStyle(s, r.start, r.end)
                is LinkAnnotation.Url -> builder.addLink(s, r.start, r.end)
            }
        }
        return builder.toAnnotatedString()
    }
}
