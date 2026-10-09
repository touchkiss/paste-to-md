package com.touchkiss.pastetomd.preview

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** Registers content lines, preserving tables, lists and opaque code/diagram blocks. */
internal class FeishuHtmlRecordIds(private val nextId: () -> String) {
    private val lineIds = mutableSetOf<String>()

    fun annotate(body: Element, diagramIds: Set<String>): List<String> {
        processChildren(body, insideLine = false)
        // DOM preorder includes parent list/quote lines before their nested item lines.
        return body.getAllElements().mapNotNull { element ->
            element.classNames().firstNotNullOfOrNull { token ->
                when {
                    token.startsWith("old-record-id-") -> token.removePrefix("old-record-id-").takeIf { it in lineIds }
                    token.startsWith("block-id-") -> token.removePrefix("block-id-").takeIf { it in diagramIds }
                    else -> null
                }
            }
        }
    }

    private fun decorate(element: Element) {
        element.classNames(element.classNames().filterNot { it.startsWith("old-record-id-") }.toSet())
        val id = nextId()
        lineIds.add(id)
        element.addClass("ace-line").addClass("old-record-id-$id")
    }

    private fun wrap(nodes: List<Node>): Element {
        val wrapper = Element("div")
        decorate(wrapper)
        nodes.first().before(wrapper)
        nodes.forEach(wrapper::appendChild)
        return wrapper
    }

    private fun processChildren(parent: Element, insideLine: Boolean) {
        val inlineRun = mutableListOf<Node>()
        fun flush() {
            if (inlineRun.any { it !is TextNode || !it.isBlank }) wrap(inlineRun)
            inlineRun.clear()
        }
        for (node in parent.childNodes().toList()) {
            if (node !is Element) {
                if (!insideLine && node is TextNode) inlineRun.add(node)
                continue
            }
            val tag = node.normalName()
            when {
                node.hasClass("block-type-ISV_BLOCK") -> flush() // Mermaid stays a bare span.
                tag == "br" -> {
                    flush()
                    wrap(listOf(node))
                }
                tag in DIRECT_LINES -> {
                    flush()
                    decorate(node)
                    if (tag != "pre") processChildren(node, insideLine = true)
                }
                tag == "td" || tag == "th" -> {
                    flush()
                    val wrapper = Element("div")
                    decorate(wrapper)
                    node.childNodes().toList().forEach(wrapper::appendChild)
                    node.appendChild(wrapper)
                    processChildren(wrapper, insideLine = true)
                }
                tag in STRUCTURAL -> {
                    flush()
                    if (tag == "table") node.addClass("ace-table")
                    processChildren(node, insideLine)
                }
                tag == "p" || tag == "div" -> {
                    flush()
                    // A div line avoids invalid div-inside-p markup when wrapping <br>.
                    if (tag == "p") node.tagName("div")
                    val isLine = tag == "p" || node.children().none {
                        it.tag().isBlock || it.hasClass("block-type-ISV_BLOCK")
                    }
                    if (!insideLine && isLine) decorate(node)
                    processChildren(node, insideLine || isLine)
                }
                node.tag().isBlock -> {
                    flush()
                    if (!insideLine) wrap(listOf(node))
                    processChildren(node, insideLine = true)
                }
                else -> {
                    if (!insideLine) inlineRun.add(node)
                    processChildren(node, insideLine = true)
                }
            }
        }
        flush()
    }

    companion object {
        private val DIRECT_LINES = setOf("h1", "h2", "h3", "h4", "h5", "h6", "ol", "li", "pre", "blockquote")
        private val STRUCTURAL = setOf("table", "thead", "tbody", "tfoot", "tr", "ul", "section", "article", "main", "aside", "header", "footer", "nav")
    }
}
