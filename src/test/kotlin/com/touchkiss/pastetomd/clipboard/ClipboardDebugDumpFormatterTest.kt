package com.touchkiss.pastetomd.clipboard

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClipboardDebugDumpFormatterTest {

    private val formatter = ClipboardDebugDumpFormatter()

    @Test
    fun `uses inline preview for small clipboard dump`() {
        val dump = ClipboardDebugDump(
            flavors = listOf("text/html"),
            payload = ClipboardPayload(
                html = "<p>hello</p>",
                rtf = null,
                plainText = "hello",
            ),
        )

        assertTrue(formatter.shouldInlinePreview(dump))
    }

    @Test
    fun `falls back to file export for large clipboard dump`() {
        val largeHtml = "x".repeat(ClipboardDebugDumpFormatter.MAX_INLINE_CHARS + 1)
        val dump = ClipboardDebugDump(
            flavors = listOf("text/html"),
            payload = ClipboardPayload(
                html = largeHtml,
                rtf = null,
                plainText = "hello",
            ),
        )

        assertFalse(formatter.shouldInlinePreview(dump))
    }
}
