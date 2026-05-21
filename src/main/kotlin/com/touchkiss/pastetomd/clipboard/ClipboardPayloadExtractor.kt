package com.touchkiss.pastetomd.clipboard

import com.intellij.openapi.ide.CopyPasteManager
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.InputStream
import java.io.Reader
import java.nio.charset.Charset

internal data class ClipboardPayload(
    val html: String? = null,
    val rtf: String? = null,
    val plainText: String? = null,
)

internal data class ClipboardDebugDump(
    val flavors: List<String>,
    val payload: ClipboardPayload,
)

internal class ClipboardPayloadExtractor(
    private val flavorDetector: ClipboardFlavorDetector = ClipboardFlavorDetector(),
    private val systemClipboardProvider: () -> Transferable? = {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.getContents(null) }.getOrNull()
    },
    private val copyPasteManagerProvider: () -> Transferable? = {
        CopyPasteManager.getInstance().contents
    },
) {

    fun readFromSystemClipboard(): ClipboardPayload? {
        val transferable = latestTransferable() ?: return null
        return extract(transferable)
    }

    fun dumpFromSystemClipboard(): ClipboardDebugDump? {
        val transferable = latestTransferable() ?: return null
        return ClipboardDebugDump(
            flavors = transferable.transferDataFlavors.map { flavor ->
                buildString {
                    append(flavor.mimeType)
                    flavor.humanPresentableName?.takeIf { it.isNotBlank() }?.let {
                        append(" | ")
                        append(it)
                    }
                }
            },
            payload = extract(transferable),
        )
    }

    private fun latestTransferable(): Transferable? {
        return systemClipboardProvider() ?: copyPasteManagerProvider()
    }

    fun extract(transferable: Transferable): ClipboardPayload {
        val html = flavorDetector.findHtmlFlavor(transferable)?.let { readFlavor(transferable, it) }
        val rtf = flavorDetector.findRtfFlavor(transferable)?.let { readFlavor(transferable, it) }
        val plainText = if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            transferable.getTransferData(DataFlavor.stringFlavor)?.toString()
        } else {
            null
        }

        return ClipboardPayload(html = html, rtf = rtf, plainText = plainText)
    }

    private fun readFlavor(transferable: Transferable, flavor: DataFlavor): String? {
        val data = transferable.getTransferData(flavor) ?: return null
        return when (data) {
            is String -> data
            is Reader -> data.readText()
            is InputStream -> {
                val charset = flavor.getParameter("charset")?.let(Charset::forName) ?: Charsets.UTF_8
                data.reader(charset).readText()
            }
            else -> data.toString()
        }
    }
}
