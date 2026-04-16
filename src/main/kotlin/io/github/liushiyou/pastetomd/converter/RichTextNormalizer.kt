package io.github.liushiyou.pastetomd.converter

import org.jsoup.Jsoup
import org.jsoup.nodes.Comment
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

class RichTextNormalizer {

    fun normalizeHtml(html: String): Document {
        val document = Jsoup.parse(html)
        document.outputSettings().prettyPrint(false)
        document.select("meta, style, script, link").remove()
        document.select("o\\:p").remove()

        cleanup(document.body())
        return document
    }

    private fun cleanup(node: Node?) {
        if (node == null) {
            return
        }

        val children = node.childNodes().toList()
        children.forEach(::cleanup)

        when (node) {
            is Comment -> node.remove()
            is Element -> {
                promoteSemanticHeading(node)

                if (node.tagName().equals("span", ignoreCase = true) && shouldUnwrapSpan(node)) {
                    node.unwrap()
                }

                if (node.tagName().equals("font", ignoreCase = true)) {
                    node.unwrap()
                }

                if (!node.tagName().equals("code", ignoreCase = true)) {
                    node.removeAttr("class")
                }
                node.removeAttr("lang")
                node.removeAttr("width")
                node.removeAttr("height")
                node.removeAttr("align")
                node.removeAttr("valign")
                node.removeAttr("role")
                node.removeAttr("aria-level")
            }
        }
    }

    private fun promoteSemanticHeading(element: Element) {
        val headingLevel = detectHeadingLevel(element) ?: return
        element.tagName("h$headingLevel")
    }

    private fun detectHeadingLevel(element: Element): Int? {
        val tagName = element.tagName().lowercase()
        if (tagName.matches(Regex("h[1-6]"))) {
            return tagName.removePrefix("h").toInt()
        }

        val blockType = element.attr("data-block-type").lowercase()
        Regex("""heading([1-6])""").find(blockType)?.groupValues?.get(1)?.toIntOrNull()?.let {
            return it
        }

        element.classNames().forEach { className ->
            Regex("""(?:^|[-_])h([1-6])(?:$|[-_])""").find(className.lowercase())?.groupValues?.get(1)?.toIntOrNull()?.let {
                return it
            }
            Regex("""heading[-_]h([1-6])""").find(className.lowercase())?.groupValues?.get(1)?.toIntOrNull()?.let {
                return it
            }
            Regex("""heading([1-6])""").find(className.lowercase())?.groupValues?.get(1)?.toIntOrNull()?.let {
                return it
            }
        }

        if (element.attr("role").equals("heading", ignoreCase = true)) {
            return element.attr("aria-level").toIntOrNull()?.coerceIn(1, 6)
        }

        return null
    }

    private fun shouldUnwrapSpan(element: Element): Boolean {
        val style = element.attr("style").lowercase()
        return style.isBlank() || (!style.contains("font-weight") && !style.contains("font-style") && !style.contains("text-decoration"))
    }
}
