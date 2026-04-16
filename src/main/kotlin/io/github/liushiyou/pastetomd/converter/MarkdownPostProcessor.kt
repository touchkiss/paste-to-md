package io.github.liushiyou.pastetomd.converter

class MarkdownPostProcessor {

    fun process(markdown: String): String {
        if (markdown.isBlank()) {
            return ""
        }

        return markdown
            .replace("\r\n", "\n")
            .replace(Regex("[ \t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .trim()
    }
}

