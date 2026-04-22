package io.github.liushiyou.pastetomd.paste

import com.intellij.openapi.diagnostic.Logger
import io.github.liushiyou.pastetomd.clipboard.ClipboardPayload
import io.github.liushiyou.pastetomd.clipboard.ClipboardPayloadExtractor
import io.github.liushiyou.pastetomd.converter.RichTextToMarkdownConverter
import io.github.liushiyou.pastetomd.settings.PasteToMarkdownSettings
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal class PasteToMarkdownService(
    private val extractor: ClipboardPayloadExtractor = ClipboardPayloadExtractor(),
    private val converter: RichTextToMarkdownConverter = RichTextToMarkdownConverter(),
) {

    private val logger = Logger.getInstance(PasteToMarkdownService::class.java)

    fun convertClipboard(settings: PasteToMarkdownSettings.SettingsState): String? {
        val payload = extractor.readFromSystemClipboard() ?: return null
        return convertPayload(payload, settings)
    }

    fun convertPayload(payload: ClipboardPayload, settings: PasteToMarkdownSettings.SettingsState): String? {
        return runCatching {
            val markdown = when {
                shouldPreferPlainText(payload) -> payload.plainText
                settings.convertHtml && !payload.html.isNullOrBlank() -> converter.convertHtml(payload.html)
                settings.convertRtf && !payload.rtf.isNullOrBlank() -> converter.convertRtf(payload.rtf)
                payload.html.isNullOrBlank() && payload.rtf.isNullOrBlank() -> payload.plainText
                else -> null
            }

            markdown?.let {
                if (settings.preserveImageLinks) markdown else stripRemoteImages(markdown)
            }?.takeIf { it.isNotBlank() }
        }.onFailure {
            if (settings.debugLogging) {
                logger.warn("Failed to convert clipboard content into Markdown", it)
            }
        }.getOrNull()
    }

    private fun shouldPreferPlainText(payload: ClipboardPayload): Boolean {
        val html = payload.html?.trim().orEmpty()
        val plainText = payload.plainText?.trimEnd().orEmpty()
        if (html.isBlank() || plainText.isBlank()) {
            return false
        }
        if (!looksLikeMarkdownDocument(plainText)) {
            return false
        }

        val body = Jsoup.parse(html).body()
        val meaningfulChildren = body.children().filterNot { it.tagName().equals("meta", true) }
        if (meaningfulChildren.size != 1) {
            return false
        }

        val codeWrapper = unwrapPlainTextMarkdownWrapper(meaningfulChildren.first()) ?: return false
        val wrapperText = extractWrapperText(codeWrapper)

        return normalizeLineEndings(wrapperText) == normalizeLineEndings(plainText)
    }

    private fun looksLikeMarkdownDocument(text: String): Boolean {
        return text.lineSequence().any { line ->
            val trimmed = line.trimStart()
            trimmed.startsWith("#") ||
                trimmed.startsWith("- ") ||
                trimmed.startsWith("* ") ||
                trimmed.startsWith("> ") ||
                trimmed.startsWith("|") ||
                trimmed.startsWith("```") ||
                Regex("""\d+\.\s+""").containsMatchIn(trimmed)
        }
    }

    private fun unwrapPlainTextMarkdownWrapper(element: Element): Element? {
        return when {
            element.tagName().equals("pre", true) || element.tagName().equals("code", true) -> element
            element.tagName().equals("div", true) ||
                element.tagName().equals("section", true) ||
                element.tagName().equals("article", true) -> {
                val meaningfulChildren = element.children()
                    .filterNot { it.tagName().equals("meta", true) }
                if (meaningfulChildren.size != 1) {
                    null
                } else {
                    unwrapPlainTextMarkdownWrapper(meaningfulChildren.first())
                }
            }
            else -> null
        }
    }

    private fun extractWrapperText(wrapper: Element): String {
        val source = wrapper.selectFirst("code")?.wholeText()?.takeIf { it.isNotBlank() }
            ?: wrapper.wholeText()
        return source
            .replace('\u00A0', ' ')
            .replace("&nbsp;", " ")
            .trimEnd()
    }

    private fun normalizeLineEndings(text: String): String {
        return text.replace("\r\n", "\n").replace("\r", "\n")
    }

    private fun stripRemoteImages(markdown: String): String {
        return markdown.replace(Regex("""!\[(.*?)]\((https?://[^)]+)\)""")) { match ->
            match.groupValues[1].ifBlank { "image" }
        }
    }
}
