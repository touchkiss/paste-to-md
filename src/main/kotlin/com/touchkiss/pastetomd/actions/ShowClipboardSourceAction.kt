package com.touchkiss.pastetomd.actions

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.touchkiss.pastetomd.clipboard.ClipboardDebugDumpFormatter
import com.touchkiss.pastetomd.clipboard.ClipboardPayloadExtractor
import java.awt.Dimension
import java.io.File

class ShowClipboardSourceAction : AnAction() {

    private val extractor = ClipboardPayloadExtractor()
    private val formatter = ClipboardDebugDumpFormatter()

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = CopyPasteManager.getInstance().contents != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Read Clipboard Rich Text Source", false) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Reading clipboard payload"
                val dump = extractor.dumpFromSystemClipboard()
                if (dump == null) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(project, "当前剪贴板为空，或者 IDEA 还没读到内容。", "Clipboard Rich Text Source")
                    }
                    return
                }

                if (formatter.shouldInlinePreview(dump)) {
                    val text = formatter.buildDumpText(dump)
                    ApplicationManager.getApplication().invokeLater {
                        showPreviewDialog(project, text)
                    }
                    return
                }

                indicator.text = "Writing clipboard dump to temp file"
                val dumpText = formatter.buildDumpText(dump)
                val tempFile = File.createTempFile("paste-to-md-clipboard-", ".txt").apply {
                    writeText(dumpText)
                    deleteOnExit()
                }
                ApplicationManager.getApplication().invokeLater {
                    openDumpFile(project, tempFile, formatter.buildSummary(dump))
                }
            }
        })
    }

    private fun showPreviewDialog(project: com.intellij.openapi.project.Project?, text: String) {
        val textArea = JBTextArea(text).apply {
            lineWrap = false
            wrapStyleWord = false
            isEditable = false
            caretPosition = 0
        }

        DialogBuilder(project).apply {
            title("Clipboard Rich Text Source")
            setCenterPanel(JBScrollPane(textArea).apply {
                preferredSize = Dimension(960, 640)
            })
            addOkAction()
            show()
        }
    }

    private fun openDumpFile(project: com.intellij.openapi.project.Project?, tempFile: File, summary: String) {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile)
        if (project != null && virtualFile != null) {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
            Messages.showInfoMessage(
                project,
                "$summary\n\n完整内容已导出并打开：\n${tempFile.absolutePath}",
                "Clipboard Rich Text Source",
            )
            return
        }

        Messages.showInfoMessage(
            project,
            "$summary\n\n完整内容已导出到：\n${tempFile.absolutePath}",
            "Clipboard Rich Text Source",
        )
    }
}
