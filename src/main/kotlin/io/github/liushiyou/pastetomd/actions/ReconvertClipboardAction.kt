package io.github.liushiyou.pastetomd.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorModificationUtil
import io.github.liushiyou.pastetomd.paste.PasteToMarkdownService
import io.github.liushiyou.pastetomd.settings.PasteToMarkdownSettings

class ReconvertClipboardAction : AnAction() {

    private val converter = PasteToMarkdownService()

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val markdown = converter.convertClipboard(PasteToMarkdownSettings.instance().state) ?: return

        WriteCommandAction.runWriteCommandAction(project) {
            EditorModificationUtil.insertStringAtCaret(editor, markdown, false, true)
        }
    }
}
