package com.touchkiss.pastetomd.clipboard

import com.touchkiss.pastetomd.preview.PreviewImageCodec
import org.jsoup.Jsoup
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.util.*

class PreviewHtmlTransferable(html: String, private val text: String, private val preserveHtml: Boolean = false) : Transferable {
    private val document = Jsoup.parseBodyFragment(html).apply {
        outputSettings().prettyPrint(false)
        select("table").forEach { it.addClass("ace-table") }
    }
    private val fragment = if (preserveHtml) html else document.body().html()

    private val image = document.select("img").singleOrNull()?.takeIf {
        document.body().text().isBlank() && it.attr("src").startsWith("data:image/png;base64,")
    }?.let { PreviewImageCodec.read(Base64.getDecoder().decode(it.attr("src").substringAfter(','))) }
    private val rtf = if (document.select("img").any { it.attr("src").startsWith("data:image/png;base64,") })
        PreviewRtf.encode(document.body()).toByteArray(Charsets.US_ASCII) else null
    private val flavors = FLAVORS + (if (rtf != null) arrayOf(RTF_FLAVOR) else emptyArray()) +
        (if (image != null) arrayOf(DataFlavor.imageFlavor) else emptyArray())

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.clone()

    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors

    override fun getTransferData(flavor: DataFlavor): Any = when (flavor) {
        RTF_FLAVOR -> rtf?.inputStream() ?: throw UnsupportedFlavorException(flavor)
        DataFlavor.imageFlavor -> image ?: throw UnsupportedFlavorException(flavor)
        DataFlavor.stringFlavor -> text
        DataFlavor.allHtmlFlavor -> if (preserveHtml) fragment else document.outerHtml()
        DataFlavor.fragmentHtmlFlavor, DataFlavor.selectionHtmlFlavor -> fragment
        else -> throw UnsupportedFlavorException(flavor)
    }

    companion object {
        private val RTF_FLAVOR = DataFlavor("text/rtf;class=java.io.InputStream")
        private val FLAVORS = arrayOf(
            DataFlavor.fragmentHtmlFlavor, DataFlavor.selectionHtmlFlavor,
            DataFlavor.allHtmlFlavor, DataFlavor.stringFlavor,
        )
    }
}
