package io.github.liushiyou.pastetomd.paste

import com.intellij.openapi.diagnostic.Logger
import io.github.liushiyou.pastetomd.clipboard.ClipboardPayload
import io.github.liushiyou.pastetomd.clipboard.ClipboardPayloadExtractor
import io.github.liushiyou.pastetomd.converter.RichTextToMarkdownConverter
import io.github.liushiyou.pastetomd.settings.PasteToMarkdownSettings

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
            when {
                settings.convertHtml && !payload.html.isNullOrBlank() -> converter.convertHtml(payload.html)
                settings.convertRtf && !payload.rtf.isNullOrBlank() -> converter.convertRtf(payload.rtf)
                else -> null
            }?.let { markdown ->
                if (settings.preserveImageLinks) markdown else stripRemoteImages(markdown)
            }?.takeIf { it.isNotBlank() }
        }.onFailure {
            if (settings.debugLogging) {
                logger.warn("Failed to convert clipboard content into Markdown", it)
            }
        }.getOrNull()
    }

    private fun stripRemoteImages(markdown: String): String {
        return markdown.replace(Regex("""!\[(.*?)]\((https?://[^)]+)\)""")) { match ->
            match.groupValues[1].ifBlank { "image" }
        }
    }
}
