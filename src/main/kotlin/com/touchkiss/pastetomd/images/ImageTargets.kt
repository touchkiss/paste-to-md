package com.touchkiss.pastetomd.images

import org.jsoup.parser.Parser

/** Stores source offsets so replacement leaves all other Markdown formatting intact. */
internal object ImageTargets {
    data class Target(val start: Int, val end: Int, val url: String)

    fun find(markdown: String): List<Target> {
        val excluded = BooleanArray(markdown.length)
        var fence: String? = null
        var offset = 0
        markdown.splitToSequence('\n').forEach { line ->
            val marker = Regex("^ {0,3}(`{3,}|~{3,})").find(line)?.groupValues?.get(1)
            val active = fence
            if (active != null || marker != null || line.startsWith("    ") || line.startsWith('\t')) {
                for (i in offset until minOf(offset + line.length + 1, markdown.length)) excluded[i] = true
            }
            if (active == null && marker != null) fence = marker
            else if (active != null && marker != null && marker[0] == active[0] && marker.length >= active.length &&
                line.substringAfter(marker).isBlank()) fence = null
            offset += line.length + 1
        }
        Regex("(`+)([\\s\\S]*?)\\1(?!`)").findAll(markdown).forEach { match ->
            if (!excluded[match.range.first]) match.range.forEach { excluded[it] = true }
        }
        Regex("<!--[\\s\\S]*?-->").findAll(markdown).forEach { it.range.forEach { i -> excluded[i] = true } }
        val results = mutableListOf<Target>()
        // HTML images are used by the converter inside complex table cells.
        Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(markdown).forEach { tag ->
            if (!excluded[tag.range.first]) {
                val src = Regex("""\bsrc\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
                    .find(tag.value)
                val group = src?.groups?.drop(1)?.firstOrNull { it != null }
                if (group != null) {
                    val url = Parser.unescapeEntities(group.value, true)
                    if (remote(url)) results += Target(tag.range.first + group.range.first, tag.range.first + group.range.last + 1, url)
                }
            }
        }
        var i = 0
        while (i < markdown.length - 1) {
            if (excluded[i] || escaped(markdown, i) || !markdown.startsWith("![", i)) { i++; continue }
            var cursor = i + 2
            var depth = 1
            while (cursor < markdown.length && depth > 0) {
                if (!escaped(markdown, cursor)) {
                    if (markdown[cursor] == '[') depth++
                    if (markdown[cursor] == ']') depth--
                }
                cursor++
            }
            if (depth != 0 || cursor >= markdown.length || markdown[cursor] != '(') { i++; continue }
            cursor++
            while (cursor < markdown.length && markdown[cursor].isWhitespace()) cursor++
            val angled = cursor < markdown.length && markdown[cursor] == '<'
            if (angled) cursor++
            val start = cursor
            var parentheses = 0
            while (cursor < markdown.length) {
                val c = markdown[cursor]
                if (!escaped(markdown, cursor)) {
                    if (angled && c == '>') break
                    if (!angled) {
                        if (c == '(') parentheses++
                        if (c == ')') { if (parentheses == 0) break; parentheses-- }
                        if (c.isWhitespace() && parentheses == 0) break
                    }
                }
                cursor++
            }
            val raw = markdown.substring(start, cursor)
            val url = Parser.unescapeEntities(raw.replace(Regex("\\\\([\\p{Punct}])"), "$1"), false)
            if (remote(url)) results += Target(start, cursor, url)
            i = maxOf(cursor, i + 1)
        }
        return results.distinctBy { it.start }.sortedBy { it.start }
    }

    private fun remote(url: String) = url.startsWith("https://", true) || url.startsWith("http://", true)
    private fun escaped(text: String, offset: Int): Boolean {
        var i = offset - 1
        while (i >= 0 && text[i] == '\\') i--
        return (offset - i - 1) % 2 == 1
    }
}
