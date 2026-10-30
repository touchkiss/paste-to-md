package com.touchkiss.pastetomd.preview

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

/** Prepare a self-contained selection for rich-text clipboard consumers. */
internal class PreviewClipboardHtmlNormalizer {
    fun normalize(html: String, sources: List<String>): String {
        val document = Jsoup.parseBodyFragment(html)
        document.outputSettings().prettyPrint(false)
        val selector = if (sources.isEmpty()) ".mermaid" else ".mermaid, code.language-mermaid"
        val roots = document.select(selector).map { element ->
            val code = if (element.`is`("code.language-mermaid")) element else
                element.closest("code.language-mermaid")
            code?.closest("pre") ?: code ?: element
        }.distinct()
        require(roots.size == sources.size) { "Native Mermaid/source count mismatch" }
        roots.zip(sources).forEach { (root, source) ->
            require(source.isNotBlank()) { "Empty Mermaid source" }
            val pre = Element("pre")
            pre.appendChild(Element("code").addClass("language-mermaid").text(source))
            root.replaceWith(pre)
        }
        document.select("table").forEach { it.addClass("ace-table") }
        document.select("img[src]").forEach { image ->
            val uri = URI(image.attr("src"))
            val path = when {
                uri.scheme == "file" -> Path.of(uri)
                uri.scheme in setOf("http", "https") && uri.host in setOf("localhost", "127.0.0.1", "::1") &&
                    uri.path.startsWith("/markdownPreview/") -> {
                    val local = uri.path.removePrefix("/markdownPreview/").substringAfter('/', "")
                    require(local.startsWith('/')) { "Invalid preview image path" }
                    Path.of(local)
                }
                else -> null
            }
            if (path != null) {
                require(Files.size(path) <= 32L * 1024 * 1024) { "Preview image exceeds 32 MiB" }
                val pixels = PreviewImageCodec.read(path)
                val bytes = PreviewImageCodec.png(pixels)
                image.attr("src", "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes))
                image.removeAttr("srcset")
            }
        }
        return document.body().html()
    }
}
