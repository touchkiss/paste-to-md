package com.touchkiss.pastetomd.paste

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.touchkiss.pastetomd.clipboard.ClipboardPayloadExtractor
import com.touchkiss.pastetomd.images.ImageMode
import com.touchkiss.pastetomd.images.ImageProcessor
import com.touchkiss.pastetomd.images.ImageStore
import com.touchkiss.pastetomd.settings.PasteToMarkdownSettings
import java.nio.file.Path

internal object BackgroundMarkdownPaste {
    fun paste(project: Project, editor: Editor, file: VirtualFile? = null) {
        val payload = ClipboardPayloadExtractor().readFromSystemClipboard() ?: return
        val state = PasteToMarkdownSettings.instance(project).state
        val settings = PasteToMarkdownSettings.SettingsState().apply {
            enabled = state.enabled; convertHtml = state.convertHtml; convertRtf = state.convertRtf
            preserveImageLinks = true; debugLogging = state.debugLogging
            imageMode = state.imageMode; imageDirectory = state.imageDirectory
        }
        val mode = runCatching { ImageMode.valueOf(settings.imageMode.orEmpty()) }.getOrDefault(ImageMode.KEEP)
        val document = editor.document
        val stamp = document.modificationStamp
        val caret = editor.caretModel.offset
        val selectionStart = editor.selectionModel.selectionStart
        val selectionEnd = editor.selectionModel.selectionEnd
        val source = file ?: FileDocumentManager.getInstance().getFile(document)
        val parent = source?.takeIf { it.isInLocalFileSystem }?.path?.let { Path.of(it).parent }
        val projectPath = project.basePath
        object : Task.Backgroundable(project, "转换 Markdown 图片", true) {
            var result: ImageProcessor.Result? = null
            override fun run(indicator: ProgressIndicator) {
                indicator.checkCanceled()
                val markdown = PasteToMarkdownService().convertPayload(payload, settings) ?: payload.plainText ?: return
                val directory = if (mode == ImageMode.LOCAL) ImageStore.resolveDirectory(settings.imageDirectory.orEmpty(), projectPath) else null
                result = ImageProcessor().process(markdown, mode, directory, parent) { indicator.checkCanceled() }
                indicator.checkCanceled()
            }
            override fun onSuccess() {
                if (project.isDisposed || editor.isDisposed) return
                val converted = result ?: return
                if (document.modificationStamp != stamp || editor.caretModel.offset != caret ||
                    editor.selectionModel.selectionStart != selectionStart || editor.selectionModel.selectionEnd != selectionEnd) {
                    Messages.showWarningDialog(project, "下载期间文档或插入位置已变化，本次未插入，请重新粘贴。已下载的图片可复用。", "Paste to Markdown")
                    return
                }
                WriteCommandAction.runWriteCommandAction(project, "Paste as Markdown", null, Runnable {
                    EditorModificationUtil.insertStringAtCaret(editor, converted.markdown, false, true)
                })
                if (converted.failed > 0) Messages.showWarningDialog(project, "${converted.failed} 张图片下载失败，已保留原链接。", "Paste to Markdown")
            }
            override fun onThrowable(error: Throwable) {
                if (!project.isDisposed) Messages.showErrorDialog(project, error.message ?: "图片处理失败", "Paste to Markdown")
            }
        }.queue()
    }
}
