package com.touchkiss.pastetomd.paste

import com.intellij.codeInsight.editorActions.CopyPastePreProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.RawText
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.touchkiss.pastetomd.settings.PasteToMarkdownSettings

class MarkdownPastePreProcessor : CopyPastePreProcessor {

    private val converter = PasteToMarkdownService()

    override fun preprocessOnCopy(
        file: PsiFile?,
        startOffsets: IntArray?,
        endOffsets: IntArray?,
        text: String?,
    ): String? = null

    override fun preprocessOnPaste(
        project: Project?,
        file: PsiFile?,
        editor: Editor?,
        text: String?,
        rawText: RawText?,
    ): String {
        if (PasteBypass.consume()) {
            return text.orEmpty()
        }
        if (project == null || editor == null || !isMarkdownFile(file)) {
            return text.orEmpty()
        }

        val settings = PasteToMarkdownSettings.instance(project).state
        if (!settings.enabled) {
            return text.orEmpty()
        }

        val converted = converter.convertClipboard(settings) ?: return text.orEmpty()
        val original = text.orEmpty().trim()
        return converted.takeIf { it.trim().isNotEmpty() && it.trim() != original } ?: text.orEmpty()
    }

    private fun isMarkdownFile(file: PsiFile?): Boolean {
        if (file == null) {
            return false
        }
        val extension = file.virtualFile?.extension?.lowercase()
        return extension in setOf("md", "markdown", "mdown") ||
            file.fileType.name.equals("Markdown", ignoreCase = true) ||
            file.language.id.equals("Markdown", ignoreCase = true)
    }
}
