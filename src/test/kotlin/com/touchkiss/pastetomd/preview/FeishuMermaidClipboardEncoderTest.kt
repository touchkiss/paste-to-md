package com.touchkiss.pastetomd.preview

import com.fasterxml.jackson.databind.ObjectMapper
import com.touchkiss.pastetomd.converter.RichTextToMarkdownConverter
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class FeishuMermaidClipboardEncoderTest {
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

    private fun placeholder(token: String) = "<span data-paste-to-md-mermaid=\"$token\"></span>"

    @Test
    fun `supported diagram types get their Feishu view and fixed block type`() {
        val views = mapOf("flowchart" to "chart", "graph" to "chart", "sequenceDiagram" to "chart",
            "classDiagram" to "codeChart", "erDiagram" to "codeChart", "gantt" to "codeChart",
            "pie" to "codeChart", "markmap" to "codeChart")
        for ((type, view) in views) {
            val source = "$type\n示例源码"
            val output = FeishuMermaidClipboardEncoder().encode(placeholder("a"), listOf(MermaidClipboardBlock("a", source)))
            val document = Jsoup.parseBodyFragment(output)
            val record = mapper.readTree(document.selectFirst("[data-lark-record-data]")!!.attr("data-lark-record-data"))
            val id = record.path("recordMap").fieldNames().next()
            val snapshot = record.path("recordMap").path(id).path("snapshot")
            assertEquals("blk_631fefbbae02400430b8f9f4", snapshot.path("block_type_id").asText())
            assertEquals(view, snapshot.path("data").path("view").asText())
            assertEquals(source, snapshot.path("data").path("data").asText())
        }
    }

    @Test
    fun `type detection skips front matter comments and multiline init directives`() {
        assertEquals("chart", FeishuMermaidClipboardEncoder.viewForSource("\uFEFF\n---\ntitle: example\n---\n%% comment\nflowchart LR\nA-->B"))
        assertEquals("codeChart", FeishuMermaidClipboardEncoder.viewForSource("%%{init: {\n 'theme': 'default'\n}}%%\n%% comment\npie showData\n\"a\": 1"))
        assertEquals(null, FeishuMermaidClipboardEncoder.viewForSource("---\nunclosed"))
        assertEquals(null, FeishuMermaidClipboardEncoder.viewForSource("flowchartUnsupported\nA-->B"))
    }

    @Test
    fun `unsupported types become source code with a line ID but no drawing records`() {
        for (type in listOf("mindmap", "stateDiagram-v2", "journey", "gitGraph", "unknown")) {
            val source = "$type\n  <中文 & \"字面内容\">\n"
            val document = Jsoup.parseBodyFragment(FeishuMermaidClipboardEncoder().encode(
                placeholder("a"), listOf(MermaidClipboardBlock("a", source))))
            assertEquals(listOf("pre", "span"), content(document).children().map { it.tagName() })
            assertEquals("language-mermaid", document.selectFirst("pre > code")!!.className())
            assertEquals(source, document.selectFirst("code")!!.wholeText())
            assertTrue(document.select(".block-placeholder,[data-paste-to-md-mermaid]").isEmpty())
        }
    }

    @Test
    fun `mixed supported and unsupported types preserve order with only supported records`() {
        val blocks = listOf(MermaidClipboardBlock("a", "flowchart TD; A-->B"),
            MermaidClipboardBlock("b", "mindmap\n  root"), MermaidClipboardBlock("c", "classDiagram\n  class A"))
        val html = "<p>before</p>" + placeholder("a") + placeholder("b") + placeholder("c") + "<p>after</p>"
        val document = Jsoup.parseBodyFragment(FeishuMermaidClipboardEncoder().encode(html, blocks))
        assertEquals(listOf("div", "span", "pre", "span", "div", "span"), content(document).children().map { it.tagName() })
        assertEquals(blocks[1].source, document.selectFirst("code")!!.wholeText())
        val record = mapper.readTree(document.selectFirst("[data-lark-record-data]")!!.attr("data-lark-record-data"))
        val ids = record.path("recordMap").fields().asSequence().filter { it.value.has("snapshot") }.map { it.key }.toList()
        assertEquals(2, ids.size)
        assertEquals(listOf("chart", "codeChart"), ids.map { record.path("recordMap").path(it).path("snapshot").path("data").path("view").asText() })
        assertEquals(2, record.path("payloadMap").size())
    }

    @Test
    fun `acceptance samples preserve source and mixed content`() {
        val sample = mapper.readTree(Files.readString(Path.of("res/json-min.json")))
        val sampleId = sample.path("recordIds")[0].asText()
        val source = sample.path("recordMap").path(sampleId).path("snapshot").path("data").path("data").asText()
        val encoder = FeishuMermaidClipboardEncoder()
        val outputDir = Path.of("build/feishu-copy-acceptance")
        Files.createDirectories(outputDir)
        val samples = mapOf(
            "single" to (placeholder("a") to listOf(MermaidClipboardBlock("a", source))),
            "mixed" to ("<p><strong>前文</strong></p><table class=\"ace-table\"><tbody><tr><td>项目</td><td>值</td></tr></tbody></table>" +
                placeholder("a") + "<p>两图之间</p>" + placeholder("b") + "<p>后文</p>" to listOf(
                MermaidClipboardBlock("a", source), MermaidClipboardBlock("b", "sequenceDiagram\nA->>B: 请求\nB-->>A: 响应"),
            )),
        )
        for ((name, input) in samples) {
            val html = encoder.encode(input.first, input.second)
            val document = Jsoup.parseBodyFragment(html)
            val json = document.selectFirst("[data-lark-record-data]")!!.attr("data-lark-record-data")
            val record = mapper.readTree(json)
            assertEquals(input.second.size, document.select(".block-placeholder").size)
            assertEquals(source, record.path("recordMap").elements().asSequence().first { it.has("snapshot") }.path("snapshot").path("data").path("data").asText())
            if (name == "mixed") {
                assertEquals(listOf("前文", "两图之间", "后文"), document.select("div.ace-line").filter { it.parent() == content(document) }.map { it.text() })
                assertEquals("ace-table", document.selectFirst("table")!!.className())
            }
            Files.writeString(outputDir.resolve("$name.html"), html)
            Files.writeString(outputDir.resolve("$name.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(record))
        }
    }

    @Test
    fun `source and complete minimal snapshot survive JSON and HTML encoding`() {
        val sample = mapper.readTree(Files.readString(Path.of("res/json-min.json")))
        val sampleId = sample.path("recordIds")[0].asText()
        val source = "flowchart TD\n  A[中文 😀 &<>\"' &quot; \\] --> B\n"
        val encoder = FeishuMermaidClipboardEncoder()
        val output = encoder.encode(placeholder("a"), listOf(MermaidClipboardBlock("a", source)))
        val document = Jsoup.parseBodyFragment(output)
        assertEquals(listOf("span", "span"), content(document).children().map { it.tagName() })
        assertEquals(1, document.select("[data-page-id]").size)
        content(document)
        val metadata = document.select("[data-lark-record-data]").single()
        val record = mapper.readTree(metadata.attr("data-lark-record-data"))
        val id = record.path("recordMap").fieldNames().next()
        assertTrue(Regex("[A-Za-z0-9]{27}").matches(id))
        assertFalse(id == sampleId)
        assertEquals("docx/record", metadata.attr("data-lark-record-format"))
        assertEquals("lark-record-clipboard", metadata.className())
        assertEquals(metadata, content(document).children().last())
        assertEquals("block-id-$id block-type-ISV_BLOCK block-placeholder", content(document).child(0).className())
        assertEquals(0, content(document).child(0).childNodeSize())
        assertEquals(false, record.path("isCut").asBoolean())
        assertEquals(source, record.path("recordMap").path(id).path("snapshot").path("data").path("data").asText())
        val expectedSnapshot = sample.path("recordMap").path(sampleId).path("snapshot").deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>()
        expectedSnapshot.withObject("/data").put("data", source)
        assertEquals(expectedSnapshot, record.path("recordMap").path(id).path("snapshot"))
        assertEquals(id, record.path("recordMap").path(id).path("id").asText())
        assertEquals(0, record.path("payloadMap").path(id).size())
        assertEquals(setOf("isCut", "recordIds", "recordMap", "payloadMap"), record.fieldNames().asSequence().toSet())
        assertFalse(output.contains("data-paste-to-md-mermaid"))
        assertTrue(output.contains("&quot;"))

        // Use the actual output root rather than adding a test-only wrapper.
        val restored = RichTextToMarkdownConverter().convertHtml(output)
        assertEquals("```mermaid\n${source.trim()}\n```", restored)
    }

    @Test
    fun `multiple records follow DOM order retry collision and preserve mixed content`() {
        val candidates = ArrayDeque(listOf("A".repeat(27), "A".repeat(27), "B".repeat(27)) + ('C'..'Z').map { it.toString().repeat(27) })
        val encoder = FeishuMermaidClipboardEncoder(nextId = { candidates.removeFirst() })
        val html = "<p><em>before</em></p><table class=\"ace-table existing\"><tbody><tr><td>cell</td></tr></tbody></table>" +
            placeholder("b") + "<p>middle</p>" + placeholder("a") + "<p>after</p>"
        val blocks = listOf(MermaidClipboardBlock("a", "flowchart TD; A-->B"), MermaidClipboardBlock("b", "flowchart TD; A-->B"))
        val document = Jsoup.parseBodyFragment(encoder.encode(html, blocks))
        assertEquals(1, document.select("[data-page-id]").size)
        content(document)
        val record = mapper.readTree(document.selectFirst("[data-lark-record-data]")!!.attr("data-lark-record-data"))
        assertEquals(listOf("A".repeat(27), "B".repeat(27)), record.path("recordMap").fields().asSequence().filter { it.value.has("snapshot") }.map { it.key }.toList())
        assertEquals(6, record.path("recordMap").size())
        assertEquals(2, record.path("payloadMap").size())
        assertEquals(listOf("div", "table", "span", "div", "span", "div", "span"), content(document).children().map { it.tagName() })
        assertEquals("ace-table existing", document.selectFirst("table")!!.className())
        assertEquals("before", document.selectFirst("em")!!.text())
        val second = Jsoup.parseBodyFragment(encoder.encode(html, blocks))
        assertFalse(document.selectFirst(".block-placeholder")!!.className() == second.selectFirst(".block-placeholder")!!.className())
    }

    @Test
    fun `source association uses token rather than list index`() {
        val encoder = FeishuMermaidClipboardEncoder()
        val document = Jsoup.parseBodyFragment(encoder.encode(placeholder("b") + placeholder("a"), listOf(
            MermaidClipboardBlock("a", "flowchart TD; first"), MermaidClipboardBlock("b", "flowchart TD; second"),
        )))
        val record = mapper.readTree(document.selectFirst("[data-lark-record-data]")!!.attr("data-lark-record-data"))
        val sources = record.path("recordMap").elements().asSequence().map { it.path("snapshot").path("data").path("data").asText() }.toList()
        assertEquals(listOf("flowchart TD; second", "flowchart TD; first"), sources)
    }

    @Test
    fun `ordinary paragraphs and table cell content also get record IDs`() {
        val html = "<p class='existing'> &quot; </p>\n<table class='ace-table extra'><tr><td>text</td></tr></table>"
        val document = Jsoup.parseBodyFragment(FeishuMermaidClipboardEncoder().encode(html, emptyList()))
        assertEquals("existing ace-line", content(document).child(0).className().substringBefore(" old-record-id-"))
        assertEquals("\"", content(document).child(0).text())
        assertEquals("text", document.selectFirst("td > div.ace-line")!!.text())
        assertTrue(document.selectFirst("table")!!.hasClass("extra"))
        val record = mapper.readTree(document.selectFirst("[data-lark-record-data]")!!.attr("data-lark-record-data"))
        assertEquals(2, record.path("recordIds").size())
        assertEquals(2, record.path("recordMap").size())
        assertTrue(record.path("recordMap").all { it.size() == 1 && it.has("id") })
    }

    @Test
    fun `missing duplicate extra or nonempty placeholders and sources are rejected`() {
        val encoder = FeishuMermaidClipboardEncoder()
        val a = MermaidClipboardBlock("a", "source")
        val failures = listOf(
            "<p>ordinary</p>" to listOf(a),
            placeholder("a") to emptyList(),
            placeholder("b") to listOf(a),
            placeholder("a") + placeholder("a") to listOf(a, MermaidClipboardBlock("b", "source")),
            placeholder("a") + placeholder("b") to listOf(a, a),
            placeholder("a") to listOf(a.copy(source = " \n")),
            "<span data-paste-to-md-mermaid='a'>unexpected</span>" to listOf(a),
            "<div data-paste-to-md-mermaid='a'></div>" to listOf(a),
            "<div class='mermaid'><svg></svg></div>" to emptyList(),
            placeholder("a") + "<span data-lark-record-data='{}'></span>" to listOf(a),
        )
        for ((html, blocks) in failures) assertFailsWith<IllegalArgumentException> { encoder.encode(html, blocks) }
    }

    @Test
    fun `invalid and permanently colliding ID generator fail without hanging`() {
        val blocks = listOf(MermaidClipboardBlock("a", "flowchart TD; A-->B"), MermaidClipboardBlock("b", "flowchart TD; A-->B"))
        assertFailsWith<IllegalArgumentException> {
            FeishuMermaidClipboardEncoder(nextId = { "invalid" }).encode(placeholder("a"), blocks.take(1))
        }
        assertFailsWith<IllegalStateException> {
            FeishuMermaidClipboardEncoder(nextId = { "A".repeat(27) }).encode(placeholder("a") + placeholder("b"), blocks)
        }
    }

    @Test
    fun `bridge accepts absent blocks for older non diagram scripts and rejects malformed data`() {
        val encoder = FeishuMermaidClipboardEncoder()
        assertEquals(emptyList(), encoder.blocksFromJson(mapper.readTree("{}").path("mermaidBlocks")))
        for (json in listOf("null", "{}", "[{}]", "[{\"token\":1,\"source\":\"a\"}]", "[{\"token\":\"a\",\"source\":null}]")) {
            assertFailsWith<IllegalArgumentException> { encoder.blocksFromJson(mapper.readTree(json)) }
        }
        assertEquals(listOf(MermaidClipboardBlock("a", "source")), encoder.blocksFromJson(mapper.readTree("[{\"token\":\"a\",\"source\":\"source\"}]")))
    }
}
