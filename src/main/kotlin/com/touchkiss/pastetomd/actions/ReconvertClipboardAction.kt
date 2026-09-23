package com.touchkiss.pastetomd.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.touchkiss.pastetomd.paste.BackgroundMarkdownPaste

class ReconvertClipboardAction : AnAction() {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null && event.getData(CommonDataKeys.EDITOR) != null
    }
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        BackgroundMarkdownPaste.paste(project, editor, event.getData(CommonDataKeys.VIRTUAL_FILE))
    }
}
