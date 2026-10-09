package com.touchkiss.pastetomd.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.touchkiss.pastetomd.preview.MarkdownPreviewCopyService

class CopyMarkdownPreviewForFeishuAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.let { MarkdownPreviewCopyService.getInstance().copy(it) }
    }
}
