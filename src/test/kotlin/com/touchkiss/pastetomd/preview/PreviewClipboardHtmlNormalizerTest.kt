package com.touchkiss.pastetomd.preview

import org.jsoup.Jsoup
import kotlin.test.*

class PreviewClipboardHtmlNormalizerTest {
    private val normalizer = PreviewClipboardHtmlNormalizer()

    @Test
    fun `mixed native HTML retains ordinary elements but replaces entire Mermaid fences`() {
        val source = "flowchart TD\n  A[中文 😀 &<>\" &quot;] --> B"
        val html = "<h2>Title</h2><p style='color:red'><strong>Before</strong></p>" +
            "<table class='existing'><tr><td colspan='2'>Cell</td></tr></table>" +
            "<pre class='code-fence'><div class='code-fence-highlighter-copy-button'><img src='icon'>" +
            "<span class='tooltiptext'>Copy to clipboard</span></div><code class='language-mermaid'>" +
            "<div class='mermaid'><svg><text>label</text></svg></div></code></pre>" +
            "<pre><code class='language-kotlin'>val x = 1</code></pre><blockquote>After</blockquote>"
        val output = normalizer.normalize(html, listOf(source))
        val doc = Jsoup.parseBodyFragment(output)
        assertEquals(listOf("h2", "p", "table", "pre", "pre", "blockquote"), doc.body().children().map { it.tagName() })
        assertEquals("color:red", doc.selectFirst("p")!!.attr("style"))
        assertEquals("Before", doc.selectFirst("strong")!!.text())
        assertEquals(setOf("existing", "ace-table"), doc.selectFirst("table")!!.classNames())
        assertEquals("2", doc.selectFirst("td")!!.attr("colspan"))
        val code = doc.selectFirst("code.language-mermaid")!!
        assertEquals(source, code.wholeText())
        assertEquals("<pre>" + code.outerHtml() + "</pre>", code.parent()!!.outerHtml())
        assertEquals("val x = 1", doc.selectFirst("code.language-kotlin")!!.text())
        assertTrue(doc.select("svg,img,.tooltiptext,.code-fence-highlighter-copy-button,.mermaid").isEmpty())
        assertTrue(doc.select("[data-page-id],[data-lark-record-data],.ace-line,[class*=old-record-id-]").isEmpty())
        assertFalse(output.contains("recordIds"))
        assertEquals(output, normalizer.normalize(output, emptyList()))
    }

    @Test
    fun `all diagram types and repeated sources become separate code blocks in order`() {
        val sources = listOf("flowchart TD; A-->B", "sequenceDiagram\nA->>B: hi", "unsupportedDiagram", "flowchart TD; A-->B")
        val html = sources.joinToString("") { "<div class='mermaid'><svg></svg></div>" }
        val doc = Jsoup.parseBodyFragment(normalizer.normalize(html, sources))
        assertEquals(sources, doc.select("pre > code.language-mermaid").map { it.wholeText() })
        assertEquals(4, doc.body().childrenSize())
    }

    @Test
    fun `no Mermaid retains lists line breaks code ordinary SVG and adds table class idempotently`() {
        val html = "<ul><li>item<br>next</li></ul><svg><text>ordinary</text></svg>" +
            "<pre><code>abc</code></pre><table class='ace-table extra'><tr><td>cell</td></tr></table>"
        val output = normalizer.normalize(html, emptyList())
        val doc = Jsoup.parseBodyFragment(output)
        assertEquals(listOf("ul", "svg", "pre", "table"), doc.body().children().map { it.tagName() })
        assertEquals(1, doc.select("li > br").size)
        assertEquals(setOf("ace-table", "extra"), doc.selectFirst("table")!!.classNames())
        assertEquals(output, normalizer.normalize(output, emptyList()))
    }

    @Test
    fun `missing native fence extra native fence and empty source reject conversion`() {
        assertFailsWith<IllegalArgumentException> { normalizer.normalize("<p>unrelated clipboard</p>", listOf("flowchart TD")) }
        assertFailsWith<IllegalArgumentException> { normalizer.normalize("<div class='mermaid'></div>", emptyList()) }
        assertFailsWith<IllegalArgumentException> { normalizer.normalize("<div class='mermaid'></div>", listOf(" ")) }
    }
}
