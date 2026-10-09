package com.touchkiss.pastetomd.preview

import com.fasterxml.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/** Temporary local diagnostic: reread the user's samples on every copy. */
internal object FeishuClipboardDebugSample {
    const val ENABLED = false
    private val htmlPath = Path.of("/Users/liushiyou/Documents/java/paste-to-md/res/mermaid-from-feishu.html")
    private val jsonPath = Path.of("/Users/liushiyou/Documents/java/paste-to-md/res/json-source.json")
    private val recordAttribute = Regex("(?i)\\bdata-lark-record-data\\s*=\\s*\"([^\"]*)\"")

    fun load(): Pair<String, String> {
        val html = Files.readString(htmlPath)
        val json = Files.readString(jsonPath)
        val record = ObjectMapper().readTree(json)
        require(record.isObject)
        val attribute = recordAttribute.findAll(html).lastOrNull() ?: error("Missing record attribute")
        val value = requireNotNull(attribute.groups[1])
        // Replace only the attribute value. Do not parse/rewrite the template DOM or regenerate IDs.
        val escaped = json.replace("&", "&amp;").replace("\"", "&quot;")
            .replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;")
        val output = html.replaceRange(value.range, escaped)
        val id = record.path("recordIds").firstOrNull()?.asText().orEmpty()
        val text = record.path("recordMap").path(id).path("snapshot").path("data").path("data").asText("")
        return output to text
    }
}
