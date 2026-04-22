package io.github.liushiyou.pastetomd.clipboard

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ClipboardPayloadExtractorTest {

    @Test
    fun `prefers latest system clipboard content over ide snapshot`() {
        val extractor = ClipboardPayloadExtractor(
            systemClipboardProvider = { StringSelection("system-latest") },
            copyPasteManagerProvider = { StringSelection("idea-stale") },
        )

        val payload = extractor.readFromSystemClipboard()

        assertNotNull(payload)
        assertEquals("system-latest", payload.plainText)
    }

    @Test
    fun `falls back to ide snapshot when system clipboard is unavailable`() {
        val extractor = ClipboardPayloadExtractor(
            systemClipboardProvider = { null },
            copyPasteManagerProvider = { StringSelection("idea-fallback") },
        )

        val payload = extractor.readFromSystemClipboard()

        assertNotNull(payload)
        assertEquals("idea-fallback", payload.plainText)
    }

    @Test
    fun `returns null when neither clipboard source is available`() {
        val extractor = ClipboardPayloadExtractor(
            systemClipboardProvider = { null },
            copyPasteManagerProvider = { null },
        )

        assertNull(extractor.readFromSystemClipboard())
    }

    @Test
    fun `dump uses system clipboard flavors and payload when available`() {
        val transferable = HtmlTransferable(
            html = "<p>fresh</p>",
            plainText = "fresh",
        )
        val extractor = ClipboardPayloadExtractor(
            systemClipboardProvider = { transferable },
            copyPasteManagerProvider = { StringSelection("idea-stale") },
        )

        val dump = extractor.dumpFromSystemClipboard()

        assertNotNull(dump)
        assertEquals("<p>fresh</p>", dump.payload.html)
        assertEquals("fresh", dump.payload.plainText)
        assertEquals(2, dump.flavors.size)
    }

    private class HtmlTransferable(
        private val html: String,
        private val plainText: String,
    ) : Transferable {
        private val htmlFlavor = DataFlavor("text/html;class=java.lang.String")
        private val flavors = arrayOf(htmlFlavor, DataFlavor.stringFlavor)

        override fun getTransferDataFlavors(): Array<DataFlavor> = flavors

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavors.any { it == flavor }

        override fun getTransferData(flavor: DataFlavor): Any? = when (flavor) {
            htmlFlavor -> html
            DataFlavor.stringFlavor -> plainText
            else -> null
        }
    }
}
