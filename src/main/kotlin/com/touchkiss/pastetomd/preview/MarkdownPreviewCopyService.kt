package com.touchkiss.pastetomd.preview

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.awt.KeyboardFocusManager
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

@Service(Service.Level.APP)
class MarkdownPreviewCopyService : Disposable {
    private val previews = CopyOnWriteArrayList<MarkdownPreviewCopyExtension>()

    internal fun register(extension: MarkdownPreviewCopyExtension) { previews.add(extension) }
    internal fun unregister(extension: MarkdownPreviewCopyExtension) { previews.remove(extension) }

    internal fun focused(project: Project): MarkdownPreviewCopyExtension? {
        val owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner ?: return null
        return previews.firstOrNull {
            it.project == project && it.isActive() && SwingUtilities.isDescendingFrom(owner, it.component)
        }
    }

    fun copy(project: Project) {
        val preview = focused(project)
        if (preview == null) {
            MarkdownPreviewCopyExtension.notify(project, "请在已加载的 JCEF Markdown 预览中选择内容后复制。")
        } else {
            preview.copy()
        }
    }

    override fun dispose() { previews.clear() }

    companion object {
        fun getInstance(): MarkdownPreviewCopyService = service()
    }
}
