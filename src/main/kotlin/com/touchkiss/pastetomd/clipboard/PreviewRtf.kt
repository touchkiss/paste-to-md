package com.touchkiss.pastetomd.clipboard

import com.touchkiss.pastetomd.preview.PreviewImageCodec
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import java.util.*

/** Portable fallback for Word: real PNG payloads, paragraphs and basic tables. */
internal object PreviewRtf {
    fun encode(body: Element): String = buildString {
        append("{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Arial;}{\\f1 Courier New;}}\\uc1\\f0\\fs22 ")
        body.childNodes().forEach { render(it, this) }
        append('}')
    }

    private fun escape(value: String, out: StringBuilder) {
        for (char in value) when (char) {
            '\\', '{', '}' -> out.append('\\').append(char)
            '\n' -> out.append("\\line ")
            '\r' -> Unit
            '\t' -> out.append("\\tab ")
            else -> if (char.code in 32..126) out.append(char)
                else out.append("\\u").append(char.code.toShort()).append('?')
        }
    }

    private fun render(node: Node, out: StringBuilder) {
        if (node is TextNode) { escape(node.wholeText, out); return }
        if (node !is Element) return
        fun children() = node.childNodes().forEach { render(it, out) }
        when (node.normalName()) {
            "script", "style", "button" -> Unit
            "img" -> {
                val source = node.attr("src")
                if (!source.startsWith("data:image/png;base64,")) {
                    escape(node.attr("alt"), out)
                    return
                }
                val bytes = Base64.getDecoder().decode(source.substringAfter(','))
                val pixels = PreviewImageCodec.read(bytes)
                val width = minOf(pixels.width * 15L, 9000L)
                val height = width * pixels.height / pixels.width
                out.append("{\\pict\\pngblip\\picw").append(pixels.width).append("\\pich").append(pixels.height)
                    .append("\\picwgoal").append(width).append("\\pichgoal").append(height).append('\n')
                val digits = "0123456789abcdef"
                for (byte in bytes) { val n = byte.toInt() and 255; out.append(digits[n shr 4]).append(digits[n and 15]) }
                out.append('}')
            }
            "table" -> { children(); out.append("\\pard ") }
            "tr" -> {
                val cells = node.children().filter { it.normalName() in setOf("td", "th") }
                out.append("\\trowd\\trgaph108 ")
                cells.indices.forEach { out.append("\\cellx").append((it + 1) * 9000 / cells.size) }
                out.append(' ')
                cells.forEach { cell ->
                    out.append("\\pard\\intbl ")
                    cell.childNodes().forEach { render(it, out) }
                    out.append("\\cell ")
                }
                out.append("\\row ")
            }
            "strong", "b" -> { out.append("{\\b "); children(); out.append('}') }
            "em", "i" -> { out.append("{\\i "); children(); out.append('}') }
            "code", "pre" -> { out.append("{\\f1 "); children(); out.append('}'); if (node.normalName() == "pre") out.append("\\par ") }
            "br" -> out.append("\\line ")
            "h1", "h2", "h3", "h4", "h5", "h6" -> { out.append("{\\b "); children(); out.append("}\\par ") }
            "p", "div", "blockquote", "li" -> { children(); out.append("\\par ") }
            else -> children()
        }
    }
}
