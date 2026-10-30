package com.touchkiss.pastetomd.preview

import com.touchkiss.pastetomd.clipboard.PreviewHtmlTransferable
import org.jsoup.Jsoup
import java.awt.datatransfer.DataFlavor
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.util.*
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewImageEmbeddingTest {
    @Test fun `preview localhost image is embedded with original pixels and no srcset`() {
        val dir = Files.createTempDirectory("preview 图片+")
        val file = dir.resolve("image.png")
        try {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", file.toFile())
            val url = "http://localhost:63342/markdownPreview/123/" + file.toUri().rawPath
            val output = PreviewClipboardHtmlNormalizer().normalize("<p>before</p><img src='$url' srcset='$url 2x'><p>after</p>", emptyList())
            val img = Jsoup.parseBodyFragment(output).selectFirst("img")!!
            assertTrue(img.attr("src").startsWith("data:image/png;base64,"))
            assertFalse(img.hasAttr("srcset"))
            val pixels = ImageIO.read(Base64.getDecoder().decode(img.attr("src").substringAfter(",")).inputStream())
            assertEquals(3, pixels.width)
            assertEquals(2, pixels.height)
            val single = PreviewHtmlTransferable(img.outerHtml(), "")
            assertTrue(single.isDataFlavorSupported(DataFlavor.imageFlavor))
            assertEquals(3, (single.getTransferData(DataFlavor.imageFlavor) as BufferedImage).width)
            assertFalse(PreviewHtmlTransferable(output, "before after").isDataFlavorSupported(DataFlavor.imageFlavor))
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(dir) }
    }
    @Test fun `copy works without ImageIO stream providers for file HTML image and RTF`() {
        val file = Files.createTempFile("preview-stream-provider-", ".png")
        val registry = javax.imageio.spi.IIORegistry.getDefaultInstance()
        val inputs = registry.getServiceProviders(javax.imageio.spi.ImageInputStreamSpi::class.java, false).asSequence().toList()
        val outputs = registry.getServiceProviders(javax.imageio.spi.ImageOutputStreamSpi::class.java, false).asSequence().toList()
        try {
            ImageIO.write(BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", file.toFile())
            inputs.forEach { registry.deregisterServiceProvider(it) }
            outputs.forEach { registry.deregisterServiceProvider(it) }
            val html = "<img src='${file.toUri()}'>"
            val embedded = PreviewClipboardHtmlNormalizer().normalize(html, emptyList())
            val payload = PreviewHtmlTransferable(embedded, "")
            assertEquals(3, (payload.getTransferData(DataFlavor.imageFlavor) as BufferedImage).width)
            val flavor = DataFlavor("text/rtf;class=java.io.InputStream")
            val rtf = (payload.getTransferData(flavor) as java.io.InputStream).reader().readText()
            assertTrue(rtf.contains("\\pngblip\\picw3\\pich2"))
            val mixed = PreviewHtmlTransferable("<p>before</p>$embedded<p>after</p>", "before after")
            assertTrue(mixed.isDataFlavorSupported(flavor))
        } finally {
            inputs.forEach { registry.registerServiceProvider(it) }
            outputs.forEach { registry.registerServiceProvider(it) }
            Files.deleteIfExists(file)
        }
    }

}
