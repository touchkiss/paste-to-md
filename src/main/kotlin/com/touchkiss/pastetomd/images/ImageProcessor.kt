package com.touchkiss.pastetomd.images

import java.net.URI
import java.net.HttpURLConnection
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal enum class ImageMode { KEEP, LOCAL, BASE64 }

internal data class DownloadedImage(val bytes: ByteArray, val mime: String) {
    val extension: String get() = when (mime) {
        "image/jpeg" -> "jpg"
        "image/svg+xml" -> "svg"
        "image/x-icon", "image/vnd.microsoft.icon" -> "ico"
        else -> mime.substringAfter('/').takeIf { it.matches(Regex("[a-z0-9]+")) } ?: "img"
    }
}

internal class ImageProcessor(
    private val download: (String) -> DownloadedImage = ::downloadImage,
) {
    data class Result(val markdown: String, val failed: Int)

    fun process(
        markdown: String,
        mode: ImageMode,
        directory: Path?,
        documentDirectory: Path?,
        checkCanceled: () -> Unit = {},
    ): Result {
        if (mode == ImageMode.KEEP) return Result(markdown, 0)
        val targets = ImageTargets.find(markdown)
        if (targets.isEmpty()) return Result(markdown, 0)
        val store = if (mode == ImageMode.LOCAL) ImageStore(requireNotNull(directory)) else null
        val executor = Executors.newFixedThreadPool(4) { runnable ->
            Thread(runnable, "paste-to-md-images").apply { isDaemon = true }
        }
        try {
            val jobs = targets.map { it.url }.distinct().associateWith { url ->
                executor.submit(Callable {
                    val image = download(url)
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    when (mode) {
                        ImageMode.BASE64 -> "data:${image.mime};base64,${Base64.getEncoder().encodeToString(image.bytes)}"
                        ImageMode.LOCAL -> {
                            val file = store!!.save(image.bytes, image.extension)
                            val relative = documentDirectory?.let { base ->
                                runCatching { base.toRealPath().relativize(file).toString() }.getOrNull()
                            }
                            if (relative == null) file.toUri().toASCIIString()
                            else URI(null, null, relative.replace('\\', '/'), null).toASCIIString()
                                .replace("(", "%28").replace(")", "%29")
                        }
                        ImageMode.KEEP -> url
                    }
                })
            }
            val replacements = mutableMapOf<String, String>()
            var failures = 0
            for ((url, future) in jobs) {
                while (!future.isDone) {
                    checkCanceled()
                    try { future.get(100, TimeUnit.MILLISECONDS) } catch (_: java.util.concurrent.TimeoutException) {
                        continue
                    } catch (_: java.util.concurrent.ExecutionException) { break }
                }
                checkCanceled()
                try { replacements[url] = future.get() } catch (_: java.util.concurrent.ExecutionException) { failures++ }
            }
            val result = StringBuilder(markdown)
            targets.asReversed().forEach { target ->
                replacements[target.url]?.let { result.replace(target.start, target.end, it) }
            }
            return Result(result.toString(), failures)
        } finally { executor.shutdownNow() }
    }

    companion object {
        private const val MAX_BYTES = 32 * 1024 * 1024
        private fun downloadImage(url: String): DownloadedImage {
            var current = URI(url)
            repeat(6) {
                require(current.scheme.lowercase() in setOf("http", "https")) { "不支持的图片协议" }
                val connection = current.toURL().openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.instanceFollowRedirects = false
                connection.setRequestProperty("User-Agent", "PasteToMarkdown")
                try {
                    val status = connection.responseCode
                    if (status in setOf(301, 302, 303, 307, 308)) {
                        current = current.resolve(requireNotNull(connection.getHeaderField("Location")))
                    } else {
                        require(status in 200..299) { "HTTP $status" }
                        require(connection.contentLengthLong <= MAX_BYTES) { "图片超过 32 MiB" }
                        val mime = connection.contentType?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                        require(mime.startsWith("image/") && mime.matches(Regex("image/[a-z0-9.+-]+"))) { "响应不是图片" }
                        val bytes = connection.inputStream.use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                if (Thread.currentThread().isInterrupted) throw InterruptedException()
                                val count = input.read(buffer)
                                if (count < 0) break
                                require(output.size() + count <= MAX_BYTES) { "图片超过 32 MiB" }
                                output.write(buffer, 0, count)
                            }
                            output.toByteArray()
                        }
                        require(bytes.isNotEmpty()) { "图片内容为空" }
                        return DownloadedImage(bytes, mime)
                    }
                } finally { connection.disconnect() }
            }
            error("图片重定向次数过多")
        }
    }
}
