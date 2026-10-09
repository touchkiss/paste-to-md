package com.touchkiss.pastetomd.preview

import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeishuHtmlRecordIdsTest {
    private val mapper = ObjectMapper()
    private fun content(document: Document): Element {
        val root = document.body().children().single()
        assertEquals("div", root.normalName())
        assertEquals("root", root.attr("data-lark-html-role"))
        assertEquals("false", root.attr("data-docx-has-block-data"))
        assertTrue(Regex("[A-Za-z0-9]{27}").matches(root.attr("data-page-id")))
        assertFalse(root.hasClass("ace-line"))
        return root
    }


    private fun encode(html: String, blocks: List<MermaidClipboardBlock> = emptyList()): Document =
        Jsoup.parseBodyFragment(FeishuMermaidClipboardEncoder().encode(html, blocks))

    private fun assertIds(document: Document): List<String> {
        val metadata = document.select("[data-lark-record-data]").single()
        val record = mapper.readTree(metadata.attr("data-lark-record-data"))
        val ids = record.path("recordIds").map { it.asText() }
        val htmlIds = document.body().getAllElements().mapNotNull { element ->
            element.classNames().firstOrNull { it.startsWith("old-record-id-") || it.startsWith("block-id-") }
                ?.removePrefix("old-record-id-")?.removePrefix("block-id-")
        }
        assertEquals(htmlIds, ids)
        assertEquals(ids.size, ids.toSet().size)
        assertFalse(ids.contains(content(document).attr("data-page-id")))
        assertEquals(ids, record.path("recordMap").fieldNames().asSequence().toList())
        for (id in ids) {
            val entry = record.path("recordMap").path(id)
            assertEquals(id, entry.path("id").asText())
            if (!entry.has("snapshot")) assertEquals(setOf("id"), entry.fieldNames().asSequence().toSet())
        }
        assertTrue(ids.all { Regex("[A-Za-z0-9]{27}").matches(it) })
        assertEquals(metadata, content(document).children().last())
        assertEquals(1, document.select("[data-page-id]").size)
        content(document)
        return ids
    }

    @Test
    fun `direct tags retain attributes and classes while ul stays structural`() {
        val document = encode("""
            <h1 class="title" id="heading">标题</h1><h2>二级</h2><h3>三级</h3><h4>四级</h4><h5>五级</h5><h6>六级</h6>
            <ol start="3" class="ordered"><li>one<ul class="unordered"><li>nested</li></ul></li></ol>
            <blockquote cite="source"><p>quote</p><pre><code>  &lt;tag&gt;\n原始代码</code></pre></blockquote>
        """.trimIndent())
        val direct = document.select("h1,h2,h3,h4,h5,h6,ol,li,pre,blockquote")
        assertTrue(direct.all { it.hasClass("ace-line") && it.classNames().count { token -> token.startsWith("old-record-id-") } == 1 })
        assertFalse(document.selectFirst("ul")!!.hasClass("ace-line"))
        assertEquals("3", document.selectFirst("ol")!!.attr("start"))
        assertEquals("heading", document.selectFirst("h1")!!.id())
        assertTrue(document.selectFirst("h1")!!.hasClass("title"))
        assertEquals("source", document.selectFirst("blockquote")!!.attr("cite"))
        assertEquals(0, document.select("pre .ace-line").size)
        assertEquals(direct.size, assertIds(document).size)
    }

    @Test
    fun `cell contents are wrapped once and br has its own div line`() {
        val document = encode("""
            <table class="existing"><thead><tr><th>header</th></tr></thead><tbody><tr>
            <td colspan="2"><p><strong>value</strong><br>next</p></td><td></td>
            </tr></tbody></table><p class="body">before<br>after</p><br>
        """.trimIndent())
        assertTrue(document.selectFirst("table")!!.hasClass("ace-table"))
        assertEquals(3, document.select("td > div.ace-line,th > div.ace-line").size)
        assertEquals("2", document.selectFirst("td")!!.attr("colspan"))
        assertEquals("value", document.selectFirst("strong")!!.text())
        assertTrue(document.select("br").all { it.parent()!!.normalName() == "div" && it.parent()!!.hasClass("ace-line") })
        assertEquals(0, document.select("table.ace-line,tr.ace-line,td.ace-line,th.ace-line").size)
        assertEquals(7, assertIds(document).size) // Three cells, body paragraph and three <br>.
    }

    @Test
    fun `bare inline content is grouped without losing formatting or escaping`() {
        val document = encode("hello <strong>A &amp; B</strong><a href='https://example.com'>link</a><br>tail")
        assertEquals(listOf("div", "div", "div", "span"), content(document).children().map { it.normalName() })
        assertEquals("A & B", document.selectFirst("strong")!!.text())
        assertEquals("https://example.com", document.selectFirst("a")!!.attr("href"))
        assertEquals("tail", content(document).child(2).text())
        assertEquals(3, assertIds(document).size)
    }

    @Test
    fun `recordIds and recordMap include all lines while only drawings have snapshots`() {
        val blocks = listOf(MermaidClipboardBlock("a", "flowchart TD; A-->B"), MermaidClipboardBlock("b", "mindmap\n  root"))
        val document = encode("<h2>heading</h2><p>before</p><span data-paste-to-md-mermaid='a'></span>" +
            "<ul><li>item</li></ul><span data-paste-to-md-mermaid='b'></span><table><tr><td>cell</td></tr></table>", blocks)
        val ids = assertIds(document)
        assertEquals(6, ids.size)
        val record = mapper.readTree(document.selectFirst("[data-lark-record-data]")!!.attr("data-lark-record-data"))
        assertEquals(6, record.path("recordMap").size())
        assertTrue(record.path("recordMap").has(ids[2]))
        assertEquals(1, record.path("recordMap").count { it.has("snapshot") })
        assertEquals(1, record.path("payloadMap").size())
        assertEquals("mindmap\n  root", document.selectFirst("pre code")!!.wholeText())
        assertEquals(0, document.select(".block-placeholder.ace-line").size)
    }

    @Test
    fun `line IDs refresh and never collide with drawing IDs`() {
        var attempts = 0
        val encoder = FeishuMermaidClipboardEncoder(nextId = {
            attempts++
            if (attempts <= 2) "A".repeat(27) else attempts.toString().padStart(27, '0')
        })
        val input = "<p class='old ace-line old-record-id-${"Z".repeat(27)}'>text</p><span data-paste-to-md-mermaid='a'></span>"
        val document = Jsoup.parseBodyFragment(encoder.encode(input, listOf(MermaidClipboardBlock("a", "flowchart TD; A-->B"))))
        val ids = assertIds(document)
        assertEquals(2, ids.size)
        assertEquals("A".repeat(27), ids[1])
        assertFalse(ids.contains("Z".repeat(27)))
        assertTrue(document.selectFirst("div.ace-line")!!.hasClass("old"))
        assertEquals(1, document.selectFirst("div.ace-line")!!.classNames().count { it.startsWith("old-record-id-") })
    }
}
