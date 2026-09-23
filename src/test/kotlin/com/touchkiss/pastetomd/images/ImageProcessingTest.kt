package com.touchkiss.pastetomd.images

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class ImageProcessingTest {
    @TempDir lateinit var root: Path

    @Test fun `resolves project placeholder relative and absolute paths`() {
        assertEquals(root.resolve("assets"), ImageStore.resolveDirectory("assets", root.toString()))
        assertEquals(root.resolve("assets"), ImageStore.resolveDirectory("\${ProjectPath}/assets", root.toString()))
        assertEquals(root, ImageStore.resolveDirectory(root.toString(), null))
        assertFailsWith<IllegalArgumentException> { ImageStore.resolveDirectory("assets", null) }
    }

    @Test fun `deduplicates bytes and rebuild reuses manually named nested files`() {
        val bytes = "image content".toByteArray()
        val nested = Files.createDirectories(root.resolve("nested")).resolve("original.png")
        Files.write(nested, bytes)
        val store = ImageStore(root)
        assertEquals(1, store.rebuild())
        assertEquals(nested.toRealPath(), store.save(bytes, "png"))
        assertEquals(nested.toRealPath(), store.save(bytes, "jpg"))
        Files.delete(nested)
        val replacement = store.save(bytes, "png")
        assertTrue(Files.exists(replacement))
        assertEquals(1, store.rebuild())
        val map = ObjectMapper().readTree(root.resolve(ImageStore.MAP_FILE).toFile())
        assertEquals(replacement.fileName.toString(), map[ImageStore.fingerprint(bytes)].asText())
    }

    @Test fun `concurrent identical downloads create one file and valid map`() {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        try {
            val futures = (1..20).map { pool.submit<Path> { ImageStore(root).save(byteArrayOf(1, 2), "png") } }
            assertEquals(1, futures.map { it.get() }.toSet().size)
            assertEquals(1, ObjectMapper().readTree(root.resolve(ImageStore.MAP_FILE).toFile()).size())
        } finally { pool.shutdownNow() }
    }

    @Test fun `downloads unique urls concurrently and preserves failure links`() {
        val arrivals = CountDownLatch(2)
        val calls = AtomicInteger()
        val processor = ImageProcessor { url ->
            calls.incrementAndGet()
            if (url.endsWith("bad")) error("unavailable")
            arrivals.countDown()
            check(arrivals.await(3, TimeUnit.SECONDS)) { "downloads were serialized" }
            DownloadedImage(byteArrayOf(1, 2, 3), "image/png")
        }
        val markdown = "![a](https://example.com/a) ![again](https://example.com/a) <img src=\"https://example.com/b\" /> ![bad](https://example.com/bad)"
        val result = processor.process(markdown, ImageMode.BASE64, null, null)
        assertEquals(3, calls.get())
        assertEquals(1, result.failed)
        assertEquals(3, Regex("data:image/png;base64,AQID").findAll(result.markdown).count())
        assertTrue(result.markdown.endsWith("![bad](https://example.com/bad)"))
    }

    @Test fun `local links are relative to document and escape spaces`() {
        val folder = root.resolve("asset files")
        val document = Files.createDirectories(root.resolve("docs"))
        val result = ImageProcessor { DownloadedImage(byteArrayOf(1), "image/png") }
            .process("![a](https://example.com/a)", ImageMode.LOCAL, folder, document)
        assertTrue(result.markdown.startsWith("![a](../asset%20files/"))
        assertEquals(0, result.failed)
    }

    @Test fun `ignores code examples and finds balanced urls and html entities`() {
        val markdown = """
            `![code](https://example.com/code)`
            ```md
            ![fence](https://example.com/fence)
            ```
            ![real](https://example.com/a(b).png "title")
            <img src='https://example.com/a?x=1&amp;y=2' />
        """.trimIndent()
        assertEquals(listOf("https://example.com/a(b).png", "https://example.com/a?x=1&y=2"), ImageTargets.find(markdown).map { it.url })
    }

    @Test fun `http download follows redirect and rejects non images`() {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/image") { exchange ->
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, 3)
            exchange.responseBody.use { it.write(byteArrayOf(1, 2, 3)) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.add("Location", "/image")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/bad") { exchange ->
            exchange.responseHeaders.add("Content-Type", "text/html")
            exchange.sendResponseHeaders(200, 1)
            exchange.responseBody.use { it.write(byteArrayOf(1)) }
        }
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val result = ImageProcessor().process("![a]($base/redirect) ![bad]($base/bad)", ImageMode.BASE64, null, null)
            assertEquals("![a](data:image/png;base64,AQID) ![bad]($base/bad)", result.markdown)
            assertEquals(1, result.failed)
        } finally { server.stop(0) }
    }

    @Test fun `cancel does not replace existing map`() {
        val store = ImageStore(root)
        store.save(byteArrayOf(1), "png")
        val before = Files.readString(root.resolve(ImageStore.MAP_FILE))
        assertFailsWith<IllegalStateException> { store.rebuild { error("cancelled") } }
        assertEquals(before, Files.readString(root.resolve(ImageStore.MAP_FILE)))
    }
}
