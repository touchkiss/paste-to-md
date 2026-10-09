package com.touchkiss.pastetomd.preview

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.security.SecureRandom

internal data class MermaidClipboardBlock(val token: String, val source: String)

/** The sampled Feishu record format is isolated here; it is not a public API. */
internal class FeishuMermaidClipboardEncoder(
    private val mapper: ObjectMapper = ObjectMapper(),
    private val nextId: () -> String = ::randomId,
) {
    fun blocksFromJson(node: JsonNode): List<MermaidClipboardBlock> {
        if (node.isMissingNode) return emptyList()
        require(node.isArray)
        return node.map {
            require(it.path("token").isTextual && it.path("source").isTextual)
            MermaidClipboardBlock(it.path("token").asText(), it.path("source").asText())
        }
    }

    fun encode(html: String, blocks: List<MermaidClipboardBlock>): String {
        val document = Jsoup.parseBodyFragment(html)
        document.outputSettings().prettyPrint(false)
        val placeholders = document.select("[data-paste-to-md-mermaid]")
        require(placeholders.size == blocks.size)
        require(document.select(".mermaid").isEmpty())
        require(document.select("[data-lark-record-data],.lark-record-clipboard,.block-type-ISV_BLOCK").isEmpty())
        val byToken = blocks.associateBy { it.token }
        require(byToken.size == blocks.size && blocks.all { it.token.isNotBlank() && it.source.isNotBlank() })
        val tokens = placeholders.map { it.attr("data-paste-to-md-mermaid") }
        require(tokens.toSet().size == tokens.size && tokens.toSet() == byToken.keys)
        val ids = mutableSetOf<String>()
        fun allocateId(): String = (1..100).firstNotNullOfOrNull {
            nextId().also { candidate -> require(ID_PATTERN.matches(candidate)) }.takeIf(ids::add)
        } ?: error("Cannot allocate unique clipboard record ID")
        fun wrapRoot(): String {
            val root = Element("div")
                .attr("data-page-id", allocateId())
                .attr("data-lark-html-role", "root")
                .attr("data-docx-has-block-data", "false")
            // Include every output element, including the final metadata span.
            document.body().childNodes().toList().forEach(root::appendChild)
            document.body().appendChild(root)
            return document.body().html()
        }
        val records = linkedMapOf<String, Any>()
        val payloads = linkedMapOf<String, Any>()
        for (placeholder in placeholders) {
            require(placeholder.tagName() == "span" && placeholder.childNodeSize() == 0)
            val block = byToken.getValue(placeholder.attr("data-paste-to-md-mermaid"))
            val view = viewForSource(block.source)
            if (view == null) {
                val code = Element("code").attr("class", "language-mermaid").text(block.source)
                placeholder.replaceWith(Element("pre").appendChild(code))
                continue
            }
            // Bound retries so a faulty generator cannot hang a clipboard request.
            val id = allocateId()
            records[id] = mapOf(
                "id" to id,
                "snapshot" to mapOf(
                    "type" to "isv",
                    "children" to emptyList<String>(),
                    "comments" to emptyList<String>(),
                    "revisions" to emptyList<String>(),
                    "data" to mapOf("data" to block.source, "theme" to "default", "view" to view),
                    "app_block_id" to "",
                    "block_type_id" to "blk_631fefbbae02400430b8f9f4",
                    "manifest" to mapOf("view_type" to "block_h5", "app_version" to "0.0.118"),
                ),
            )
            payloads[id] = emptyMap<String, Any>()
            placeholder.replaceWith(Element("span").attr("class", "block-id-$id block-type-ISV_BLOCK block-placeholder"))
        }
        val orderedIds = FeishuHtmlRecordIds(::allocateId).annotate(document.body(), records.keys)
        if (orderedIds.isEmpty()) return wrapRoot()
        // Ordinary HTML lines need a record entry too, but no diagram snapshot.
        val orderedRecords = orderedIds.associateWith { id -> records[id] ?: mapOf("id" to id) }
        val json = mapper.writeValueAsString(mapOf(
            "isCut" to false, "recordIds" to orderedIds, "recordMap" to orderedRecords, "payloadMap" to payloads,
        ))
        document.body().appendChild(Element("span")
            .attr("data-lark-record-data", json)
            .attr("data-lark-record-format", "docx/record")
            .attr("class", "lark-record-clipboard"))
        return wrapRoot()
    }

    companion object {
        internal fun viewForSource(source: String): String? {
            // Mermaid front matter, comments and init directives can precede the type.
            val lines = source.trimStart('\uFEFF').replace(Regex("(?s)%%\\{.*?}%%"), "")
                .lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
            var start = 0
            if (lines.firstOrNull() == "---") {
                val end = lines.drop(1).indexOfFirst { it == "---" }
                if (end < 0) return null
                start = end + 2
            }
            val declaration = lines.drop(start).firstOrNull { !it.startsWith("%%") } ?: return null
            val type = Regex("^([A-Za-z][A-Za-z0-9]*)\\b").find(declaration)?.groupValues?.get(1)
            return when (type) {
                "flowchart", "graph", "sequenceDiagram" -> "chart"
                "classDiagram", "erDiagram", "gantt", "pie", "markmap" -> "codeChart"
                else -> null
            }
        }

        private val random = SecureRandom()
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        private val ID_PATTERN = Regex("[A-Za-z0-9]{27}")
        private fun randomId(): String = buildString { repeat(27) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
    }
}
