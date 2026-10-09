package com.touchkiss.pastetomd.clipboard

import org.jsoup.Jsoup
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.UnsupportedFlavorException
import kotlin.test.*

class PreviewHtmlTransferableTest {
    @Test
    fun `adds table class while preserving structure attributes and existing classes`() {
        val payload = PreviewHtmlTransferable(
            "<p>Before</p><table class='existing' data-id='a'><tbody><tr><td colspan='2'><strong>Cell</strong></td></tr></tbody></table>" +
                "<table class='ace-table other'><tr><td>Second</td></tr></table><p>After</p>",
            "Before Cell Second After",
        )
        val html = payload.getTransferData(DataFlavor.fragmentHtmlFlavor) as String
        val document = Jsoup.parseBodyFragment(html)
        val tables = document.select("table")
        assertEquals(2, tables.size)
        assertEquals(setOf("existing", "ace-table"), tables[0].classNames())
        assertEquals(setOf("ace-table", "other"), tables[1].classNames())
        assertEquals("a", tables[0].attr("data-id"))
        assertEquals("2", tables[0].selectFirst("td")!!.attr("colspan"))
        assertEquals("Cell", tables[0].selectFirst("strong")!!.text())
        assertEquals(listOf("p", "table", "table", "p"), document.body().children().map { it.tagName() })
        val repeated = PreviewHtmlTransferable(html, "text").getTransferData(DataFlavor.fragmentHtmlFlavor)
        assertEquals(html, repeated)
    }

    @Test
    fun `exposes HTML fragments complete document and unchanged plain text`() {
        val payload = PreviewHtmlTransferable("<table><tr><td>&lt;A&gt;</td></tr></table>", "<A>\n")
        for (flavor in payload.transferDataFlavors) assertTrue(payload.isDataFlavorSupported(flavor))
        assertEquals("<A>\n", payload.getTransferData(DataFlavor.stringFlavor))
        assertEquals(payload.getTransferData(DataFlavor.fragmentHtmlFlavor), payload.getTransferData(DataFlavor.selectionHtmlFlavor))
        assertTrue((payload.getTransferData(DataFlavor.allHtmlFlavor) as String).contains("<html>"))
        assertTrue((payload.getTransferData(DataFlavor.fragmentHtmlFlavor) as String).contains("ace-table"))
        assertFalse(payload.isDataFlavorSupported(DataFlavor.imageFlavor))
        assertFailsWith<UnsupportedFlavorException> { payload.getTransferData(DataFlavor.imageFlavor) }
    }

    @Test
    fun `does not introduce tables into ordinary formatted content`() {
        val payload = PreviewHtmlTransferable("<p>Hello <strong>world</strong></p>", "Hello world")
        assertEquals("<p>Hello <strong>world</strong></p>", payload.getTransferData(DataFlavor.fragmentHtmlFlavor))
    }
}
