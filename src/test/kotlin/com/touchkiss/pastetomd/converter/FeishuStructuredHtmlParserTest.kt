package com.touchkiss.pastetomd.converter

import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class FeishuStructuredHtmlParserTest {

    private val converter = RichTextToMarkdownConverter()

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
