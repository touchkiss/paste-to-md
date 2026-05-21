package com.touchkiss.pastetomd.actions

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.touchkiss.pastetomd.paste.PasteBypass

class PasteOriginalAction : AnAction() {

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.getData(CommonDataKeys.EDITOR) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val pasteAction = ActionManager.getInstance().getAction(IdeActions.ACTION_PASTE) ?: return
        PasteBypass.enableForCurrentPaste()
        pasteAction.actionPerformed(event)
    }
}

