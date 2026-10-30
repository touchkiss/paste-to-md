package com.touchkiss.pastetomd.preview

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageInputStream
import javax.imageio.stream.ImageInputStream
import javax.imageio.stream.MemoryCacheImageInputStream
import javax.imageio.stream.MemoryCacheImageOutputStream

/** Avoid ImageIO stream-provider discovery and global disk-cache settings in IDE worker threads. */
internal object PreviewImageCodec {
    fun read(path: Path): BufferedImage = FileImageInputStream(path.toFile()).use(::decode)

    fun read(bytes: ByteArray): BufferedImage = bytes.inputStream().use { input ->
        MemoryCacheImageInputStream(input).use(::decode)
    }

    private fun decode(input: ImageInputStream): BufferedImage {
        val readers = ImageIO.getImageReaders(input)
        require(readers.hasNext()) { "Unsupported preview image format" }
        val reader = readers.next()
        try {
            reader.input = input
            return reader.read(0) ?: error("Cannot decode preview image")
        } finally {
            reader.dispose()
        }
    }

    fun png(pixels: BufferedImage): ByteArray = ByteArrayOutputStream().use { bytes ->
        MemoryCacheImageOutputStream(bytes).use { output ->
            check(ImageIO.write(pixels, "png", output)) { "PNG encoder unavailable" }
        }
        bytes.toByteArray()
    }
}
