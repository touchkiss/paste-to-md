package com.touchkiss.pastetomd.preview

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Only table classes and Mermaid fences differ from the native clipboard HTML. */
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
        return document.body().html()
    }
}
