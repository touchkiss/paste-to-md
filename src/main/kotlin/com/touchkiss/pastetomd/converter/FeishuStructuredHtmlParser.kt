package com.touchkiss.pastetomd.converter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class FeishuStructuredHtmlParser(
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {

    fun parse(
        document: Document,
        renderElement: (Element, Int) -> List<String>,
    ): List<String>? {
        val root = document.selectFirst("[data-lark-html-role=root]")
        val recordMap = parseRecordMap(document)
        // Resolve placeholders before rendering containers: diagrams can be nested in lists/quotes.
        // Mutate the tree before parallel rendering starts so all renderers see ordinary code blocks.
        document.getAllElements().filter { element ->
            element.classNames().any { it.equals("block-type-ISV_BLOCK", ignoreCase = true) }
        }.forEach { element ->
            val source = mermaidSource(element, recordMap)
            if (source == null) {
                element.remove()
            } else {
                val pre = Element("pre")
                pre.appendChild(Element("code").addClass("language-mermaid").text(source))
                element.replaceWith(pre)
            }
        }
        val children = if (root != null) {
            root.children().toList()
        } else {
            val bodyChildren = document.body().children().toList()
            val hasFeishuMarkers = bodyChildren.any {
                it.attr("data-block-type").isNotBlank() || it.attr("data-type").isNotBlank()
            }
            if (hasFeishuMarkers) bodyChildren else emptyList()
        }
        if (children.isEmpty()) {
            return null
        }

        return ParallelRenderSupport.mapOrdered(children) { child ->
                when {
                    child.attr("data-type").equals("whiteboard", ignoreCase = true) -> emptyList()
                    else -> renderElement(child, 0)
                }
            }
            .flatten()
            .map(String::trimEnd)
            .filter(String::isNotBlank)
    }

    private fun mermaidSource(element: Element, recordMap: JsonNode?): String? {
        val blockId = element.classNames()
            .firstOrNull { it.startsWith("block-id-") }
            ?.removePrefix("block-id-")
            ?.trim()
            ?: return null

        val snapshot = recordMap?.path(blockId)?.path("snapshot")
        if (snapshot == null || snapshot.isMissingNode) {
            return null
        }

        val type = snapshot.path("type").asText("")
        if (type.equals("whiteboard", ignoreCase = true)) {
            return null
        }
        if (!type.equals("isv", ignoreCase = true)) {
            return null
        }

        return snapshot.path("data").path("data").asText("").trim().takeIf(String::isNotBlank)
    }

    private fun parseRecordMap(document: Document): JsonNode? {
        // Jsoup has already decoded HTML entities in attribute values; do not unescape again.
        val raw = document.selectFirst("[data-lark-record-data]")?.attr("data-lark-record-data")?.trim()
            ?: return null
        if (raw.isBlank()) {
            return null
        }

        return runCatching {
            objectMapper.readTree(raw).path("recordMap")
        }.getOrNull()
    }
}
