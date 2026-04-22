package io.github.liushiyou.pastetomd.converter

class MarkdownPostProcessor {

    private val tableDividerRegex = Regex("^\\|(?:\\s*:?-{3,}:?\\s*\\|)+\\s*$")

    fun process(markdown: String): String {
        if (markdown.isBlank()) {
            return ""
        }

        val normalized = markdown
            .replace("\r\n", "\n")
            .replace("&nbsp;", " ")
            .replace('\u00A0', ' ')
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")

        return ensureBlankLineBeforeTables(normalized)
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()
    }

    private fun ensureBlankLineBeforeTables(markdown: String): String {
        val lines = markdown.lines()
        val result = mutableListOf<String>()
        var inFence = false

        lines.forEachIndexed { index, line ->
            if (line.trimStart().startsWith("```")) {
                inFence = !inFence
            }
            if (!inFence && isTableHeader(lines, index)) {
                if (result.isNotEmpty() && result.last().isNotBlank()) {
                    result += ""
                }
            }
            result += line
        }

        return result.joinToString("\n")
    }

    private fun isTableHeader(lines: List<String>, index: Int): Boolean {
        if (index + 1 >= lines.size) {
            return false
        }
        val current = lines[index].trim()
        val next = lines[index + 1].trim()
        return current.startsWith("|") && current.endsWith("|") && tableDividerRegex.matches(next)
    }
}

