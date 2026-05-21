package com.touchkiss.pastetomd.converter

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

class RichTextToMarkdownConverter(
    val normalizer: RichTextNormalizer = RichTextNormalizer(),
    val postProcessor: MarkdownPostProcessor = MarkdownPostProcessor(),
    val feishuParser: FeishuStructuredHtmlParser = FeishuStructuredHtmlParser(),
) {

    private data class ListItemHeading(
        val level: Int,
        val text: String,
        val element: Element,
    )

    fun convertHtml(html: String): String {
        if (html.isBlank()) {
            return ""
        }

        val rawDocument = Jsoup.parse(html).apply {
            outputSettings().prettyPrint(false)
        }
        val feishuBlocks = feishuParser.parse(rawDocument, ::renderElement)
        val blocks = if (feishuBlocks != null) {
            feishuBlocks
        } else {
            val document = normalizer.normalizeHtml(html)
            renderBlocks(document)
        }
        return postProcessor.process(blocks.joinToString("\n\n") { it.trimEnd() })
    }

    fun convertRtf(rtf: String): String {
        if (rtf.isBlank()) {
            return ""
        }

        return postProcessor.process(parseRtfToText(rtf))
    }

    private fun renderBlocks(document: Document): List<String> {
        val body = document.body()
        if (body.childrenSize() == 0) {
            val inline = renderInlineChildren(body).trim()
            return if (inline.isEmpty()) emptyList() else listOf(inline)
        }

        val childNodes = body.childNodes().toList()
        return ParallelRenderSupport.mapOrdered(childNodes) { renderBlockNode(it, 0) }
            .flatten()
            .map(String::trimEnd)
            .filter(String::isNotBlank)
    }

    private fun renderBlockNode(node: Node, indentLevel: Int): List<String> {
        return when (node) {
            is TextNode -> {
                val text = node.text().trim()
                if (text.isEmpty()) emptyList() else listOf(text)
            }

            is Element -> renderElement(node, indentLevel)
            else -> emptyList()
        }
    }

    private fun renderElement(element: Element, indentLevel: Int): List<String> {
        val blockType = element.attr("data-block-type").lowercase()
        val dataType = element.attr("data-type").lowercase()

        when (blockType) {
            "isv" -> return emptyList()
            "divider" -> return listOf("---")
            "quote_container" -> return renderFeishuQuote(element, indentLevel)
            "bullet" -> return renderFeishuListItem(element, ordered = false, indentLevel = indentLevel)
            "ordered" -> return renderFeishuListItem(element, ordered = true, indentLevel = indentLevel)
            "code" -> return listOf(renderFeishuCodeBlock(element))
            "text" -> {
                val text = renderEditableBlockText(element).trim()
                return if (text.isBlank()) emptyList() else listOf(text)
            }
        }

        Regex("""heading([1-6])""").find(blockType)?.groupValues?.get(1)?.toIntOrNull()?.let { level ->
            val text = normalizeWhitespace(element.text()).trim()
            return if (text.isBlank()) emptyList() else listOf("${"#".repeat(level)} $text")
        }

        when (dataType) {
            "whiteboard" -> return emptyList()
            "divider" -> return listOf("---")
            "sheet" -> {
                val table = element.selectFirst("table")
                return if (table != null) {
                    listOf(renderTable(table))
                } else {
                    element.childNodes().flatMap { renderBlockNode(it, indentLevel) }
                }
            }
        }

        if (element.attr("role").equals("heading", true)) {
            val level = element.attr("aria-level").toIntOrNull()?.coerceIn(1, 6) ?: 1
            val text = renderInlineChildren(element).replace("**", "").replace("*", "").trim()
            return if (text.isBlank()) emptyList() else listOf("${"#".repeat(level)} $text")
        }

        return when (element.tagName().lowercase()) {
            "p" -> {
                val text = renderInlineChildren(element).trim()
                if (text.isBlank()) emptyList() else listOf(text)
            }

            "div", "section", "article" -> {
                if (hasBlockLikeChildren(element)) {
                    element.childNodes().flatMap { renderBlockNode(it, indentLevel) }
                } else {
                    val text = renderInlineChildren(element).trim()
                    if (text.isBlank()) emptyList() else listOf(text)
                }
            }

            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val level = element.tagName().substring(1).toInt()
                val text = renderInlineChildren(element).trim()
                if (text.isBlank()) emptyList() else listOf("${"#".repeat(level)} $text")
            }

            "blockquote" -> {
                val nested = element.childNodes().flatMap { renderBlockNode(it, indentLevel) }
                if (nested.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        nested.joinToString("\n") { line ->
                            line.lines().joinToString("\n") { inner -> "> ${inner.trimEnd()}" }
                        }
                    )
                }
            }

            "ul" -> renderList(element, false, indentLevel)
            "ol" -> renderList(element, true, indentLevel)
            "pre" -> listOf(renderPreformatted(element))
            "table" -> listOf(renderTable(element))
            "hr" -> listOf("---")
            "img" -> {
                val image = renderImage(element)
                if (image.isBlank()) emptyList() else listOf(image)
            }

            "br" -> emptyList()
            else -> {
                if (element.childrenSize() == 0) {
                    val text = renderInlineChildren(element).trim()
                    if (text.isBlank()) emptyList() else listOf(text)
                } else {
                    element.childNodes().flatMap { renderBlockNode(it, indentLevel) }
                }
            }
        }
    }

    private fun renderList(listElement: Element, ordered: Boolean, indentLevel: Int): List<String> {
        val result = mutableListOf<String>()
        val itemIndent = "   ".repeat(indentLevel)
        val start = if (ordered) listElement.attr("start").toIntOrNull()?.coerceAtLeast(1) ?: 1 else 1
        var liIndex = 0

        listElement.children().forEach { child ->
            if (child.tagName().equals("li", ignoreCase = true)) {
                val prefix = if (ordered) "${start + liIndex}. " else "- "
                liIndex++
                val heading = renderListItemHeading(child)
                if (heading != null) {
                    result += renderListItemWithHeading(child, heading, prefix, indentLevel)
                    return@forEach
                } else {
                    val line = renderListItemLine(child)
                    if (line.isNotBlank()) {
                        result += itemIndent + prefix + line
                    }
                }

                child.children().forEach { subChild ->
                    val subTag = subChild.tagName().lowercase()
                    when {
                        subTag == "ul" || subTag == "ol" ->
                            result += renderList(subChild, subTag == "ol", indentLevel + 1)
                        isLiBlockChild(subChild) ->
                            result += renderBlockNode(subChild, indentLevel)
                    }
                }
            } else {
                result += renderBlockNode(child, indentLevel)
            }
        }

        return if (result.isEmpty()) emptyList() else listOf(result.joinToString("\n"))
    }

    private fun renderListItemHeading(item: Element): ListItemHeading? {
        val firstElement = item.children().firstOrNull { child ->
            !child.tagName().equals("ul", true) && !child.tagName().equals("ol", true)
        } ?: return null
        if (!firstElement.tagName().matches(Regex("h[1-6]", RegexOption.IGNORE_CASE))) {
            return null
        }
        val text = renderInlineChildren(firstElement).trim()
        if (text.isBlank()) {
            return null
        }
        val level = firstElement.tagName().lowercase().removePrefix("h").toIntOrNull() ?: return null
        return ListItemHeading(level, text, firstElement)
    }

    private fun renderListItemWithHeading(
        item: Element,
        heading: ListItemHeading,
        prefix: String,
        indentLevel: Int,
    ): List<String> {
        val rendered = mutableListOf<String>()

        item.children().forEach { child ->
            when {
                child === heading.element -> {
                    rendered += "${"#".repeat(heading.level)} $prefix${heading.text}"
                }

                child.tagName().equals("ul", true) || child.tagName().equals("ol", true) -> {
                    rendered += renderList(child, child.tagName().equals("ol", true), indentLevel + 1)
                }

                else -> {
                    rendered += renderElement(child, indentLevel + 1)
                }
            }
        }

        return rendered
    }

    private fun renderFeishuListItem(element: Element, ordered: Boolean, indentLevel: Int): List<String> {
        val text = renderEditableBlockText(element).trim()
        if (text.isBlank()) {
            return emptyList()
        }

        val itemIndent = "   ".repeat(indentLevel)
        val prefix = if (ordered) {
            element.selectFirst("button.order")?.text()?.trim()?.ifBlank { "1." } ?: "1."
        } else {
            "-"
        }
        return listOf("$itemIndent$prefix $text")
    }

    private fun renderFeishuQuote(element: Element, indentLevel: Int): List<String> {
        val nestedBlocks = element.select("> .quote-container-block > .quote-container-block-children > .render-unit-wrapper > .block")
            .flatMap { renderElement(it, indentLevel + 1) }
            .filter(String::isNotBlank)

        if (nestedBlocks.isEmpty()) {
            val fallback = renderEditableBlockText(element).trim()
            if (fallback.isBlank()) {
                return emptyList()
            }
            return listOf(fallback.lines().joinToString("\n") { "> $it" })
        }

        return listOf(
            nestedBlocks.joinToString("\n") { block ->
                block.lines().joinToString("\n") { line -> "> ${line.trimEnd()}" }
            }
        )
    }

    private fun renderFeishuCodeBlock(element: Element): String {
        val codeLines = element.select(".code-line-wrapper").map { wrapper ->
            normalizeWhitespace(wrapper.text()).trimEnd()
        }
        val content = codeLines.joinToString("\n").trimEnd()
        val language = element.selectFirst(".code-block-header-btn > span")?.text()?.trim()
            ?.takeUnless { it.isBlank() || it.equals("plain text", true) || it.equals("diff", true) }
            ?.lowercase()
            ?: ""
        val fence = if (language.isBlank()) "```" else "```$language"
        return buildString {
            append(fence)
            if (content.isNotEmpty()) {
                append("\n")
                append(content)
            }
            append("\n```")
        }
    }

    private fun renderEditableBlockText(element: Element): String {
        val zones = element.select(".zone-container[data-slate-editor=true], .zone-container[data-slate-editor]")
        if (zones.isEmpty()) {
            return renderInlineChildren(element)
        }

        val lines = zones.flatMap { zone ->
            zone.select("> .ace-line, .code-line-wrapper")
                .map { line -> normalizeWhitespace(renderInlineChildren(line)).trim() }
                .filter(String::isNotBlank)
        }

        return if (lines.isEmpty()) {
            normalizeWhitespace(zones.joinToString("\n") { renderInlineChildren(it) })
        } else {
            lines.joinToString("\n")
        }
    }

    private fun isLiBlockChild(element: Element): Boolean {
        val tag = element.tagName().lowercase()
        return tag == "table" || tag == "blockquote" || tag == "pre" || tag == "hr" ||
            ((tag == "div" || tag == "p" || tag == "section" || tag == "article") && hasBlockLikeChildren(element))
    }

    private fun renderListItemLine(item: Element): String {
        val inlineParts = mutableListOf<String>()
        item.childNodes().forEach { child ->
            when (child) {
                is TextNode -> inlineParts += child.text()
                is Element -> {
                    if (child.tagName().equals("ul", true) || child.tagName().equals("ol", true)) {
                        return@forEach
                    }
                    if (isLiBlockChild(child)) {
                        return@forEach
                    }
                    inlineParts += if (child.tagName().equals("p", true) || child.tagName().equals("div", true)) {
                        renderInlineChildren(child)
                    } else {
                        renderInlineNode(child)
                    }
                }
            }
        }
        return normalizeWhitespace(inlineParts.joinToString("")).trim()
    }

    private fun renderPreformatted(element: Element): String {
        val codeElement = element.selectFirst("code")
        val codeText = codeElement?.wholeText() ?: element.wholeText()
        val languageClass = codeElement?.classNames()?.firstOrNull { it.startsWith("language-") }
        val language = languageClass?.removePrefix("language-") ?: ""
        val fence = if (language.isBlank()) "```" else "```$language"
        return buildString {
            append(fence)
            append("\n")
            append(codeText.trim('\n'))
            append("\n```")
        }
    }

    private fun renderTable(table: Element): String {
        val rows = table.select("tr")
        if (rows.isEmpty()) {
            return renderInlineChildren(table).trim()
        }

        val matrix = expandTableMatrix(rows)

        if (matrix.isEmpty()) {
            return ""
        }

        val header = matrix.first()
        val body = if (matrix.size > 1) matrix.drop(1) else emptyList()
        val headerLine = "| ${header.joinToString(" | ")} |"
        val divider = "| ${header.joinToString(" | ") { "---" }} |"
        val bodyLines = body.map { "| ${it.joinToString(" | ")} |" }
        return listOf(headerLine, divider, *bodyLines.toTypedArray()).joinToString("\n")
    }

    private fun expandTableMatrix(rows: List<Element>): List<List<String>> {
        val matrix = mutableListOf<MutableList<String>>()
        val rowspanState = mutableMapOf<Int, Pair<Int, String>>()

        rows.forEach { row ->
            val currentRow = mutableListOf<String>()
            var columnIndex = 0

            fun fillRowspanSlots() {
                while (true) {
                    val span = rowspanState[columnIndex] ?: break
                    currentRow += span.second
                    if (span.first <= 1) {
                        rowspanState.remove(columnIndex)
                    } else {
                        rowspanState[columnIndex] = span.first - 1 to span.second
                    }
                    columnIndex++
                }
            }

            fillRowspanSlots()

            val cells = row.children().asSequence()
                .filter { it.tagName().equals("th", true) || it.tagName().equals("td", true) }
                .toList()

            cells.forEach { cell ->
                fillRowspanSlots()

                val text = renderTableCell(cell)
                val colspan = cell.attr("colspan").toIntOrNull()?.coerceAtLeast(1) ?: 1
                val rowspan = cell.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 1

                repeat(colspan) {
                    currentRow += text
                    if (rowspan > 1) {
                        rowspanState[columnIndex] = rowspan - 1 to text
                    }
                    columnIndex++
                }
            }

            while (rowspanState.containsKey(columnIndex)) {
                val span = rowspanState[columnIndex] ?: break
                currentRow += span.second
                if (span.first <= 1) {
                    rowspanState.remove(columnIndex)
                } else {
                    rowspanState[columnIndex] = span.first - 1 to span.second
                }
                columnIndex++
            }

            matrix += currentRow
        }

        val maxColumns = matrix.maxOfOrNull { it.size } ?: 0
        return matrix.map { row ->
            if (row.size == maxColumns) row else row + MutableList(maxColumns - row.size) { "" }
        }.filter { it.isNotEmpty() }
    }

    private fun renderTableCell(cell: Element): String {
        val rendered = cell.childNodes().joinToString("") { renderTableCellNode(it) }
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
        return rendered.replace("\n", "<br/>")
    }

    private fun renderTableCellNode(node: Node): String {
        return when (node) {
            is TextNode -> escapeHtml(normalizeWhitespace(node.text()))
            is Element -> when (node.tagName().lowercase()) {
                "strong", "b" -> wrapHtml("strong", node.childNodes().joinToString("") { renderTableCellNode(it) })
                "em", "i" -> wrapHtml("em", node.childNodes().joinToString("") { renderTableCellNode(it) })
                "s", "strike", "del" -> wrapHtml("del", node.childNodes().joinToString("") { renderTableCellNode(it) })
                "a" -> renderTableCellLink(node)
                "code" -> {
                    val text = normalizeWhitespace(node.text()).trim()
                    if (text.isBlank()) "" else "<code>${escapeHtml(text)}</code>"
                }
                "img" -> renderTableCellImage(node)
                "br" -> "\n"
                "pre" -> renderTableCodeBlock(node)
                "ul" -> renderTableCellListHtml(node, ordered = false)
                "ol" -> renderTableCellListHtml(node, ordered = true)
                "p", "div", "section", "article" -> {
                    val inner = node.childNodes().joinToString("") { renderTableCellNode(it) }.trim()
                    if (inner.isBlank()) "" else "$inner\n"
                }
                else -> {
                    if (hasBlockLikeChildren(node)) {
                        node.childNodes().joinToString("") { renderTableCellNode(it) } + "\n"
                    } else {
                        node.childNodes().joinToString("") { renderTableCellNode(it) }
                    }
                }
            }
            else -> ""
        }
    }

    private fun renderTableCellListHtml(list: Element, ordered: Boolean): String {
        val tag = if (ordered) "ol" else "ul"
        val items = StringBuilder()
        list.children().asSequence()
            .filter { it.tagName().equals("li", true) }
            .forEach { item ->
                val textContent = item.childNodes().asSequence()
                    .filter { node -> !(node is Element && (node.tagName().equals("ul", true) || node.tagName().equals("ol", true))) }
                    .joinToString("") { renderTableCellNode(it) }
                    .trim()
                val nestedLists = item.children().asSequence()
                    .filter { it.tagName().equals("ul", true) || it.tagName().equals("ol", true) }
                    .joinToString("") { renderTableCellListHtml(it, it.tagName().equals("ol", true)) }
                items.append("<li>$textContent$nestedLists</li>")
            }
        return "<$tag>$items</$tag>"
    }

    private fun renderTableCodeBlock(pre: Element): String {
        val codeElement = pre.selectFirst("code")
        val codeText = codeElement?.wholeText() ?: pre.wholeText()
        val languageClass = codeElement?.classNames()?.firstOrNull { it.startsWith("language-") }
        val language = languageClass?.removePrefix("language-")?.trim().orEmpty()
        val classAttr = if (language.isBlank()) "" else " class=\"language-${escapeHtmlAttribute(language)}\""
        val codeHtml = escapeHtml(codeText)
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("\n", "<br/>")
        return "<pre><code$classAttr>$codeHtml</code></pre>"
    }

    private fun renderTableCellLink(link: Element): String {
        val text = link.childNodes().joinToString("") { renderTableCellNode(it) }.ifBlank { escapeHtml(link.attr("href")) }
        val href = link.attr("href").trim()
        return if (href.isBlank()) text else "<a href=\"${escapeHtmlAttribute(href)}\">$text</a>"
    }

    private fun renderTableCellImage(image: Element): String {
        val src = image.attr("src").trim()
        if (src.isBlank()) {
            return ""
        }
        val alt = escapeHtmlAttribute(image.attr("alt").trim())
        val altAttr = if (alt.isBlank()) "" else " alt=\"$alt\""
        return "<img src=\"${escapeHtmlAttribute(src)}\"$altAttr />"
    }

    private fun wrapHtml(tag: String, content: String): String {
        return "<$tag>$content</$tag>"
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun escapeHtmlAttribute(text: String): String {
        return escapeHtml(text).replace("\"", "&quot;")
    }

    private fun renderInlineChildren(element: Element): String {
        return normalizeWhitespace(element.childNodes().joinToString("") { renderInlineNode(it) })
    }

    private fun renderInlineNode(node: Node): String {
        return when (node) {
            is TextNode -> node.text()
            is Element -> {
                when (node.tagName().lowercase()) {
                    "strong", "b" -> wrap("**", renderInlineChildren(node))
                    "em", "i" -> wrap("*", renderInlineChildren(node))
                    "s", "strike", "del" -> wrap("~~", renderInlineChildren(node))
                    "span" -> {
                        if (node.classNames().any { it.startsWith("inline-code") }) {
                            val text = normalizeWhitespace(node.text()).trim()
                            if (text.isBlank()) "" else "`$text`"
                        } else {
                            renderStyledSpan(node)
                        }
                    }
                    "a" -> {
                        val text = renderInlineChildren(node).trim()
                        val href = node.attr("href").trim()
                        if (href.isBlank()) text else "[$text]($href)"
                    }
                    "code" -> {
                        val text = normalizeWhitespace(node.text()).trim()
                        if (text.isBlank()) "" else "`$text`"
                    }
                    "img" -> renderImage(node)
                    "br" -> "\n"
                    "button" -> renderInlineChildren(node)
                    else -> {
                        if (hasStyle(node, "font-weight", "700") || hasStyle(node, "font-weight", "bold")) {
                            wrap("**", renderInlineChildren(node))
                        } else if (hasStyle(node, "font-style", "italic")) {
                            wrap("*", renderInlineChildren(node))
                        } else if (hasStyle(node, "text-decoration", "line-through")) {
                            wrap("~~", renderInlineChildren(node))
                        } else {
                            renderInlineChildren(node)
                        }
                    }
                }
            }
            else -> ""
        }
    }

    private fun renderStyledSpan(element: Element): String {
        val content = renderInlineChildren(element)
        return when {
            hasStyle(element, "font-weight", "700") || hasStyle(element, "font-weight", "bold") -> wrap("**", content)
            hasStyle(element, "font-style", "italic") -> wrap("*", content)
            hasStyle(element, "text-decoration", "line-through") -> wrap("~~", content)
            else -> content
        }
    }

    private fun renderImage(element: Element): String {
        val src = element.attr("src").trim()
        val alt = element.attr("alt").trim().ifBlank { "image" }
        return when {
            src.startsWith("http://") || src.startsWith("https://") -> "![$alt]($src)"
            element.attr("alt").isNotBlank() -> element.attr("alt").trim()
            else -> ""
        }
    }

    private fun wrap(marker: String, content: String): String {
        val trimmed = cleanInlineContent(content)
        return if (trimmed.isBlank()) "" else "$marker$trimmed$marker"
    }

    private fun normalizeWhitespace(text: String): String {
        return text
            .replace('\u00A0', ' ')
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace("\uFEFF", "")
            .replace(Regex("[ \t]+"), " ")
            .replace(Regex(" *\n *"), "\n")
            .replace(Regex("\\s+([，。！？；：、])"), "$1")
            .replace(Regex("(?<=[\\p{IsHan}])\\s+(?=[\\p{IsHan}])"), "")
    }

    private fun cleanInlineContent(text: String): String {
        return normalizeWhitespace(text).trim()
    }

    private fun hasBlockLikeChildren(element: Element): Boolean {
        return element.children().any { child ->
            child.tagName().lowercase() in setOf(
                "table", "ul", "ol", "blockquote", "pre", "hr", "h1", "h2", "h3", "h4", "h5", "h6", "div", "section", "article"
            ) || child.attr("data-block-type").isNotBlank() || child.attr("data-type").isNotBlank()
        }
    }

    private fun parseRtfToText(rtf: String): String {
        val output = StringBuilder()
        var index = 0

        while (index < rtf.length) {
            when (val current = rtf[index]) {
                '\\' -> {
                    index++
                    if (index >= rtf.length) {
                        break
                    }

                    when (val escaped = rtf[index]) {
                        '\\', '{', '}' -> {
                            output.append(escaped)
                            index++
                        }

                        '\n', '\r' -> index++
                        '\'' -> {
                            if (index + 2 < rtf.length) {
                                val hex = rtf.substring(index + 1, index + 3)
                                hex.toIntOrNull(16)?.let { output.append(it.toChar()) }
                                index += 3
                            } else {
                                index++
                            }
                        }

                        else -> {
                            val start = index
                            while (index < rtf.length && rtf[index].isLetter()) {
                                index++
                            }
                            val control = rtf.substring(start, index)

                            while (index < rtf.length && (rtf[index].isDigit() || rtf[index] == '-')) {
                                index++
                            }

                            when (control) {
                                "line" -> output.append('\n')
                                "par" -> output.append("\n\n")
                                "tab" -> output.append('\t')
                                "emdash" -> output.append("—")
                                "endash" -> output.append("–")
                                "bullet" -> output.append("•")
                            }

                            if (index < rtf.length && rtf[index] == ' ') {
                                index++
                            }
                        }
                    }
                }

                '{', '}' -> index++
                else -> {
                    output.append(current)
                    index++
                }
            }
        }

        return output.toString().trim()
    }

    private fun hasStyle(element: Element, name: String, value: String): Boolean {
        val style = element.attr("style").lowercase()
        return style.contains("$name:$value") || style.contains("$name: $value")
    }
}
