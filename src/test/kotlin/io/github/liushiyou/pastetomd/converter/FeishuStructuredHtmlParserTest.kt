package io.github.liushiyou.pastetomd.converter

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

        assertContains(markdown, "## **现状分析**")
        assertContains(markdown, "| 组件 | 架构1 | 架构2 |")
        assertContains(markdown, "### **现状启动时序（有问题的）**")
        assertContains(markdown, "> [!WARNING]")
        assertContains(markdown, "```")
        assertContains(markdown, "---")
        assertContains(markdown, "1. **持续压测 bff 接口**")
        assertContains(markdown, "https://gitlab.bee.to/aladdin/rd/jaco-grpc-authority-starter/-/tree/warmup-lb-jdk8-nacos")
    }
}
