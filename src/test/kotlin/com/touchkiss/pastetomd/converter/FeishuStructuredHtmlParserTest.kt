package com.touchkiss.pastetomd.converter

import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import java.io.File
import kotlin.test.*

class FeishuStructuredHtmlParserTest {

    private val converter = RichTextToMarkdownConverter()

    @Test
    fun `restores nested diagrams in source order from trailing escaped metadata`() {
        val document = Jsoup.parseBodyFragment("""
            <div data-lark-html-role="root"><ol><li>流程
                <div><p>之前</p><span class="block-id-first block-type-ISV_BLOCK">占位提示</span>
                <p>中间</p><span class="block-id-second block-type-ISV_BLOCK">占位提示</span><p>之后</p></div>
            </li></ol></div>
        """.trimIndent())
        val first = "flowchart TD\n    A[\"&quot; & <tag>\"] --> B"
        val second = "sequenceDiagram\n    X->>Y: hi"
        // Deliberately reverse metadata order: output must follow the HTML positions.
        document.body().appendElement("span").attr("data-lark-record-data", ObjectMapper().writeValueAsString(mapOf(
            "recordMap" to linkedMapOf("second" to second, "first" to first).mapValues { (_, source) ->
                mapOf("snapshot" to mapOf("type" to "isv", "data" to mapOf("data" to source)))
            },
        )))

        val markdown = converter.convertHtml(document.body().html())
        assertEquals(2, Regex("```mermaid").findAll(markdown).count())
        assertContains(markdown, "A[\"&quot; & <tag>\"] --> B")
        assertContains(markdown, "X->>Y: hi")
        val positions = listOf("之前", "flowchart TD", "中间", "sequenceDiagram", "之后").map(markdown::indexOf)
        assertTrue(positions.all { it >= 0 } && positions.zipWithNext().all { (a, b) -> a < b }, markdown)
        assertFalse(markdown.contains("占位提示"))
    }

    @Test
    fun `converts mermaid isv block and skips whiteboard`() {
        // Keep the regression fixture stable: res/part.html is a user-provided table sample.
        val document = Jsoup.parseBodyFragment("""
            <div data-lark-html-role="root">
                <h2>demo流程图</h2>
                <span class="block-id-diagram block-type-ISV_BLOCK block-placeholder"></span>
                <div data-type="whiteboard">暂时无法在飞书文档外展示此内容</div>
                <h2>demo原理</h2>
            </div>
        """.trimIndent())
        document.body().appendElement("span").attr("data-lark-record-data", ObjectMapper().writeValueAsString(mapOf(
            "recordMap" to mapOf("diagram" to mapOf("snapshot" to mapOf(
                "type" to "isv", "data" to mapOf("data" to "sequenceDiagram\nA->>B: hi"),
            ))),
        )))
        val html = document.body().html()

        val markdown = converter.convertHtml(html)

        assertContains(markdown, "## demo流程图")
        assertContains(markdown, "```mermaid")
        assertContains(markdown, "sequenceDiagram")
        assertContains(markdown, "## demo原理")
        assertFalse(markdown.contains("暂时无法在飞书文档外展示此内容"))
    }

    @Test
    fun `converts current table sample`() {
        val markdown = converter.convertHtml(File("res/part.html").readText())
        assertContains(markdown, "| 项目 | 值 |")
        assertContains(markdown, "| 监控看板 |")
    }

    @Test
    fun `converts full html rich elements`() {
        val html = File("res/full.html").readText()

        val markdown = converter.convertHtml(html)

        println(markdown)
    }
}
