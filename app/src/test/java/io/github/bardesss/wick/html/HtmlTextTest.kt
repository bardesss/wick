package io.github.bardesss.wick.html

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlTextTest {
    @Test
    fun html_paragraphs_and_links() {
        val out = htmlToAnnotated(
            "<p>Read  the\n <a href=\"https://e.x/post\">full post</a> here.</p>" +
                "<p>Second <b>bold</b> and <em>soft</em>.<br>New line</p>",
        )
        assertNotNull(out)
        assertEquals("Read the full post here.\n\nSecond bold and soft.\nNew line", out!!.text)

        val links = out.getLinkAnnotations(0, out.length)
        assertEquals(1, links.size)
        val link = links.single()
        assertEquals("https://e.x/post", (link.item as LinkAnnotation.Url).url)
        assertEquals("full post", out.text.substring(link.start, link.end))

        val spans = out.spanStyles
        assertTrue(spans.any { it.item.fontWeight == FontWeight.Bold && out.text.substring(it.start, it.end) == "bold" })
        assertTrue(spans.any { it.item.fontStyle == FontStyle.Italic && out.text.substring(it.start, it.end) == "soft" })
    }

    @Test
    fun html_drops_scripts_and_images() {
        val out = htmlToAnnotated(
            "<p>Hello<script>alert('x')</script><img src=\"a.png\" alt=\"pic\">" +
                "<style>p { color: red }</style> there</p><iframe src=\"https://v.x\">frame</iframe>",
        )
        assertEquals("Hello there", out?.text)
    }

    @Test
    fun html_blank_is_null() {
        assertNull(htmlToAnnotated(null))
        assertNull(htmlToAnnotated(""))
        assertNull(htmlToAnnotated("   \n "))
        assertNull(htmlToAnnotated("<p> </p><img src=\"a.png\"><script>x()</script><p>&nbsp;</p>"))
    }

    @Test
    fun html_decodes_entities() {
        val out = htmlToAnnotated("<p>Fish &amp; chips, caf&eacute; &#8216;open&#x2019; &lt;now&gt;&nbsp;ok</p>")
        assertEquals("Fish & chips, café ‘open’ <now> ok", out?.text)
    }

    @Test
    fun html_lists_and_headings() {
        val out = htmlToAnnotated(
            "<h2>Heading</h2><ul><li>One</li><li>Two <i>items</i></li></ul>" +
                "<blockquote>Quoted</blockquote><pre>a  b\n  c</pre><p>After</p>",
        )
        assertNotNull(out)
        assertEquals("Heading\n\n• One\n• Two items\n\nQuoted\n\na  b\n  c\n\nAfter", out!!.text)
        val spans = out.spanStyles
        assertTrue(spans.any { it.item.fontWeight == FontWeight.Bold && out.text.substring(it.start, it.end) == "Heading" })
        assertTrue(spans.any { it.item.fontStyle == FontStyle.Italic && out.text.substring(it.start, it.end) == "Quoted" })
        assertTrue(spans.any { it.item.fontFamily != null && out.text.substring(it.start, it.end) == "a  b\n  c" })
    }

    @Test
    fun html_collapses_blank_blocks() {
        val out = htmlToAnnotated("<div><p>One</p><br><br><br><div></div><p>Two</p></div>")
        assertEquals("One\n\nTwo", out?.text)
    }
}
