package io.github.liushiyou.pastetomd.converter

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
        val root = document.selectFirst("[data-lark-html-role=root]") ?: return null
        val recordMap = parseRecordMap(document)
        val children = root.children().toList()

        return ParallelRenderSupport.mapOrdered(children) { child ->
                when {
                    child.attr("data-type").equals("whiteboard", ignoreCase = true) -> emptyList()
                    child.classNames().any { it.equals("block-type-ISV_BLOCK", ignoreCase = true) } -> renderIsvBlock(child, recordMap)
                    else -> renderElement(child, 0)
                }
            }
            .flatten()
            .map(String::trimEnd)
            .filter(String::isNotBlank)
    }

    private fun renderIsvBlock(element: Element, recordMap: JsonNode?): List<String> {
        val blockId = element.classNames()
            .firstOrNull { it.startsWith("block-id-") }
            ?.removePrefix("block-id-")
            ?.trim()
            ?: return emptyList()

        val snapshot = recordMap?.path(blockId)?.path("snapshot")
        if (snapshot == null || snapshot.isMissingNode) {
            return emptyList()
        }

        val type = snapshot.path("type").asText("")
        if (type.equals("whiteboard", ignoreCase = true)) {
            return emptyList()
        }
        if (!type.equals("isv", ignoreCase = true)) {
            return emptyList()
        }

        val mermaid = snapshot.path("data").path("data").asText("").trim()
        if (mermaid.isBlank()) {
            return emptyList()
        }

        return listOf(
            buildString {
                append("```mermaid\n")
                append(mermaid)
                append("\n```")
            }
        )
    }

    private fun parseRecordMap(document: Document): JsonNode? {
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
