package com.touchkiss.pastetomd.clipboard

import org.jsoup.Jsoup
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException

class PreviewHtmlTransferable(html: String, private val text: String, private val preserveHtml: Boolean = false) : Transferable {
    private val document = Jsoup.parseBodyFragment(html).apply {
        outputSettings().prettyPrint(false)
        select("table").forEach { it.addClass("ace-table") }
    }
    private val fragment = if (preserveHtml) html else document.body().html()

    override fun getTransferDataFlavors(): Array<DataFlavor> = FLAVORS.clone()

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in FLAVORS

    override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
        DataFlavor.stringFlavor -> text
        DataFlavor.allHtmlFlavor -> if (preserveHtml) fragment else document.outerHtml()
        DataFlavor.fragmentHtmlFlavor, DataFlavor.selectionHtmlFlavor -> fragment
        else -> throw UnsupportedFlavorException(flavor)
    }

    companion object {
        private val FLAVORS = arrayOf(
            DataFlavor.fragmentHtmlFlavor, DataFlavor.selectionHtmlFlavor,
            DataFlavor.allHtmlFlavor, DataFlavor.stringFlavor,
        )
    }
}
