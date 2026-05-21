package com.touchkiss.pastetomd.converter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class FeishuStructuredHtmlParserTest {

    private val converter = RichTextToMarkdownConverter()

    @Test
    fun `converts part html with mermaid isv block and skips whiteboard`() {
        val html = File("part.html").readText()

        val markdown = converter.convertHtml(html)

        assertContains(markdown, "## demo流程图")
        assertContains(markdown, "```mermaid")
        assertContains(markdown, "sequenceDiagram")
        assertContains(markdown, "## demo原理")
        assertFalse(markdown.contains("暂时无法在飞书文档外展示此内容"))
    }

    @Test
    fun `converts full html rich elements`() {
        val html = File("full.html").readText()

        val markdown = converter.convertHtml(html)

        println(markdown)
    }
}
