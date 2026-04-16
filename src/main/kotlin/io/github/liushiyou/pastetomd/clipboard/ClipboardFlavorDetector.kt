package io.github.liushiyou.pastetomd.clipboard

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable

internal class ClipboardFlavorDetector {

    fun hasHtml(transferable: Transferable): Boolean = findHtmlFlavor(transferable) != null

    fun hasRtf(transferable: Transferable): Boolean = findRtfFlavor(transferable) != null

    fun findHtmlFlavor(transferable: Transferable): DataFlavor? =
        transferable.transferDataFlavors.firstOrNull {
            it.primaryType.equals("text", true) && it.subType.equals("html", true)
        }

    fun findRtfFlavor(transferable: Transferable): DataFlavor? =
        transferable.transferDataFlavors.firstOrNull {
            it.primaryType.equals("text", true) && it.subType.equals("rtf", true)
        }
}

