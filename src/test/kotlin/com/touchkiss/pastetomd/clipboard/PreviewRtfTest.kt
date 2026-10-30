package com.touchkiss.pastetomd.clipboard

import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertTrue

class PreviewRtfTest {
    @Test fun `mixed image and table include portable RTF with actual PNG bytes`() {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        val html = "<p>中文 {before}</p><table><tr><td><b>cell</b></td><td><img src='data:image/png;base64,${Base64.getEncoder().encodeToString(bytes)}'></td></tr></table><p>after</p>"
        val payload = PreviewHtmlTransferable(html, "中文 before cell after")
        val flavor = DataFlavor("text/rtf;class=java.io.InputStream")
        assertTrue(payload.isDataFlavorSupported(flavor))
        val rtf = (payload.getTransferData(flavor) as InputStream).reader(Charsets.US_ASCII).readText()
        assertTrue(rtf.startsWith("{\\rtf1"))
        assertTrue(rtf.contains("\\pngblip\\picw3\\pich2"))
        assertTrue(rtf.contains(bytes.joinToString("") { "%02x".format(it) }))
        assertTrue(rtf.contains("\\trowd") && rtf.contains("\\cell") && rtf.contains("\\row"))
        assertTrue(rtf.indexOf("before") < rtf.indexOf("\\pngblip") && rtf.indexOf("\\pngblip") < rtf.indexOf("after"))
        assertTrue(rtf.contains("\\u20013?"))
        assertTrue(rtf.contains("\\{before\\}"))
        val kit = javax.swing.text.rtf.RTFEditorKit()
        val doc = kit.createDefaultDocument()
        kit.read(rtf.byteInputStream(Charsets.US_ASCII), doc, 0)
        val text = doc.getText(0, doc.length)
        assertTrue(text.contains("中文 {before}") && text.contains("cell") && text.contains("after"))
    }
}
