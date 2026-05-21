package com.touchkiss.pastetomd.paste

import com.touchkiss.pastetomd.clipboard.ClipboardPayload
import com.touchkiss.pastetomd.settings.PasteToMarkdownSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PasteToMarkdownServiceTest {

    private val service = PasteToMarkdownService()

    @Test
    fun `returns plain text directly when clipboard has no html or rtf`() {
        val payload = ClipboardPayload(
            html = null,
            rtf = null,
            plainText = "原始文本\n第二行",
        )

        val markdown = service.convertPayload(payload, defaultSettings())

        assertEquals("原始文本\n第二行", markdown)
    }

    @Test
    fun `still prefers html conversion when html exists`() {
        val payload = ClipboardPayload(
            html = "<p><strong>加粗</strong></p>",
            rtf = null,
            plainText = "加粗",
        )

        val markdown = service.convertPayload(payload, defaultSettings())

        assertEquals("**加粗**", markdown)
    }

    @Test
    fun `prefers plain text when html only wraps markdown in pre block`() {
        val payload = ClipboardPayload(
            html = "<pre><code># 标题\n\n- 列表项</code></pre>",
            rtf = null,
            plainText = "# 标题\n\n- 列表项",
        )

        val markdown = service.convertPayload(payload, defaultSettings())

        assertEquals("# 标题\n\n- 列表项", markdown)
    }

    @Test
    fun `prefers plain text when html wraps markdown in div and pre code`() {
        val payload = ClipboardPayload(
            html = "<div class=\"markdown-body\"><pre><code>## 二级标题\n\n1. 第一步\n2. 第二步</code></pre></div>",
            rtf = null,
            plainText = "## 二级标题\n\n1. 第一步\n2. 第二步",
        )

        val markdown = service.convertPayload(payload, defaultSettings())

        assertEquals("## 二级标题\n\n1. 第一步\n2. 第二步", markdown)
    }

    @Test
    fun `keeps html conversion for ordinary code block content that is not markdown document`() {
        val payload = ClipboardPayload(
            html = "<pre><code>if (ok) {\n  return;\n}</code></pre>",
            rtf = null,
            plainText = "if (ok) {\n  return;\n}",
        )

        val markdown = service.convertPayload(payload, defaultSettings())

        assertEquals(
            """
            ```
            if (ok) {
              return;
            }
            ```
            """.trimIndent(),
            markdown
        )
    }

    private fun defaultSettings(): PasteToMarkdownSettings.SettingsState {
        return PasteToMarkdownSettings.SettingsState().apply {
            enabled = true
            convertHtml = true
            convertRtf = true
            preserveImageLinks = true
            debugLogging = false
        }
    }
}
