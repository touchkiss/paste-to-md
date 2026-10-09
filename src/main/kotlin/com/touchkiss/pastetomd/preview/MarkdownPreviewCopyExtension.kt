package com.touchkiss.pastetomd.preview

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import com.touchkiss.pastetomd.clipboard.ClipboardPayloadExtractor
import com.touchkiss.pastetomd.clipboard.PreviewHtmlTransferable
import org.intellij.plugins.markdown.extensions.MarkdownBrowserPreviewExtension
import org.intellij.plugins.markdown.ui.preview.BrowserPipe
import org.intellij.plugins.markdown.ui.preview.MarkdownHtmlPanel
import org.intellij.plugins.markdown.ui.preview.ResourceProvider
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class MarkdownPreviewCopyExtension(private val panel: MarkdownHtmlPanel) :
    MarkdownBrowserPreviewExtension, ResourceProvider {
    internal val project: Project? = panel.project
    internal val component get() = panel.component
    private val pipe = panel.browserPipe!!
    private val mapper = ObjectMapper()
    private val normalizer = PreviewClipboardHtmlNormalizer()
    private val clipboard = ClipboardPayloadExtractor()
    private val requests = PreviewCopyRequest()
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    @Volatile private var disposed = false
    private val service = MarkdownPreviewCopyService.getInstance()

    private val readyHandler = object : BrowserPipe.Handler {
        override fun processMessageReceived(data: String): Boolean {
            onEdt { sendShortcuts() }
            return true
        }
    }
    private val shortcutHandler = object : BrowserPipe.Handler {
        override fun processMessageReceived(data: String): Boolean {
            onEdt { if (isActive()) copy() }
            return true
        }
    }
    private val resultHandler = object : BrowserPipe.Handler {
        override fun processMessageReceived(data: String): Boolean {
            onEdt { receive(data) }
            return true
        }
    }

    init {
        service.register(this)
        pipe.subscribe(READY, readyHandler)
        pipe.subscribe(SHORTCUT, shortcutHandler)
        pipe.subscribe(RESULT, resultHandler)
        ApplicationManager.getApplication().messageBus.connect(this).subscribe(
            KeymapManagerListener.TOPIC,
            object : KeymapManagerListener {
                override fun activeKeymapChanged(keymap: Keymap?) = onEdt { sendShortcuts() }
                override fun shortcutsChanged(keymap: Keymap, actionIds: Collection<String>, fromSettings: Boolean) {
                    if (ACTION_ID in actionIds) onEdt { sendShortcuts() }
                }
                override fun shortcutChanged(keymap: Keymap, actionId: String) {
                    if (actionId == ACTION_ID) onEdt { sendShortcuts() }
                }
            },
        )
    }

    override val scripts = listOf(SCRIPT)
    override val resourceProvider: ResourceProvider get() = this
    override fun canProvide(resourceName: String): Boolean = resourceName == SCRIPT
    override fun loadResource(resourceName: String): ResourceProvider.Resource? {
        if (!canProvide(resourceName)) return null
        val bytes = javaClass.getResourceAsStream("/preview/$SCRIPT")?.use { it.readBytes() } ?: return null
        return ResourceProvider.Resource(bytes, "text/javascript")
    }

    internal fun isActive(): Boolean {
        val currentProject = project ?: return false
        return !disposed && !currentProject.isDisposed && component.isShowing &&
            panel.virtualFile in FileEditorManager.getInstance(currentProject).selectedFiles
    }

    internal fun copy() {
        if (!isActive()) return
        val id = requests.start() ?: return
        alarm.cancelAllRequests()
        alarm.addRequest({
            if (requests.expire(id) && isActive()) notify(project, "Markdown 预览复制超时，请稍后重试。")
        }, 3000)
        try {
            pipe.send(REQUEST, id.toString())
        } catch (_: Exception) {
            requests.cancel()
            alarm.cancelAllRequests()
            notify(project, "Markdown 预览尚未就绪，无法复制。")
        }
    }

    private fun receive(data: String) {
        var responseId: Long? = null
        try {
            val result = mapper.readTree(data)
            val id = result.path("requestId").asLong(-1)
            responseId = id
            val valid = isActive() && project?.let { service.focused(it) === this } == true
            if (!requests.isPending(id) || !valid) return
            val error = result.path("error").asText("")
            if (error.isNotEmpty()) {
                if (!requests.finish(id, valid)) return
                alarm.cancelAllRequests()
                notify(project, when (error) {
                    "empty-selection" -> "请先在 Markdown 预览中选择要复制的内容。"
                    "multiple-ranges" -> "暂不支持不连续的预览选区。"
                    "not-focused" -> "请在 Markdown 预览中选择内容后复制。"
                    "mermaid-source-missing" -> "所选 Mermaid 缺少绘图源码，剪贴板保持不变。"
                    "mermaid-source-invalid" -> "所选 Mermaid 源码编码无效，剪贴板保持不变。"
                    "native-copy-failed" -> "预览原生复制失败，请重新选择后重试。"
                    else -> "无法读取 Markdown 预览选区，请重新选择后重试。"
                })
                return
            }
            require(result.path("nativeCopy").asBoolean(false))
            val sourcesNode = result.path("sources")
            require(sourcesNode.isArray && sourcesNode.all { it.isTextual })
            val sources = sourcesNode.map { it.asText() }
            require(sources.isEmpty() || result.path("text").isTextual)
            // The browser has performed native copy. Read its HTML, including
            // Chromium's selected structural context, before applying our edits.
            val native = clipboard.readFromSystemClipboard()
            val html = native?.html
            if (html.isNullOrEmpty()) {
                requests.finish(id, valid)
                alarm.cancelAllRequests()
                notify(project, "原生复制未提供 HTML，已保留原生剪贴板内容。")
                return
            }
            val text = if (sources.isEmpty()) native?.plainText.orEmpty() else result.path("text").asText()
            // Normalize off the EDT; stale requests must not overwrite newer clipboard content.
            ApplicationManager.getApplication().executeOnPooledThread {
                val normalized = runCatching { normalizer.normalize(html, sources) }
                onEdt {
                    val current = isActive() && project?.let { service.focused(it) === this } == true &&
                        runCatching { clipboard.readFromSystemClipboard() == native }.getOrDefault(false)
                    if (requests.finish(id, current)) {
                        alarm.cancelAllRequests()
                        normalized.fold(
                            onSuccess = { output ->
                                try {
                                    CopyPasteManager.getInstance().setContents(PreviewHtmlTransferable(output, text))
                                } catch (_: Exception) {
                                    notify(project, "处理预览复制失败，已保留当前剪贴板内容。")
                                }
                            },
                            onFailure = { notify(project, "Mermaid 源码与原生 HTML 不匹配，已保留原生复制内容。") },
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // A malformed or old response must not cancel a newer request.
            val id = responseId ?: return
            if (!requests.isPending(id)) return
            requests.cancel()
            alarm.cancelAllRequests()
            if (isActive()) notify(project, "处理预览复制失败，已保留当前剪贴板内容。")
        }
    }

    private fun sendShortcuts() {
        if (disposed) return
        val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(ACTION_ID)
            .filterIsInstance<KeyboardShortcut>().map {
                mapOf("first" to stroke(it.firstKeyStroke), "second" to it.secondKeyStroke?.let(::stroke))
            }
        pipe.send(CONFIG, mapper.writeValueAsString(shortcuts))
    }

    private fun stroke(key: KeyStroke): Map<String, Any> = mapOf(
        "key" to browserKey(key.keyCode),
        "ctrl" to (key.modifiers and InputEvent.CTRL_DOWN_MASK != 0),
        "meta" to (key.modifiers and InputEvent.META_DOWN_MASK != 0),
        "alt" to (key.modifiers and InputEvent.ALT_DOWN_MASK != 0),
        "shift" to (key.modifiers and InputEvent.SHIFT_DOWN_MASK != 0),
    )

    private fun onEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater { if (!disposed) action() }
    }

    override fun dispose() {
        disposed = true
        requests.cancel()
        alarm.cancelAllRequests()
        service.unregister(this)
        pipe.removeSubscription(READY, readyHandler)
        pipe.removeSubscription(SHORTCUT, shortcutHandler)
        pipe.removeSubscription(RESULT, resultHandler)
    }

    class Provider : MarkdownBrowserPreviewExtension.Provider {
        override fun createBrowserExtension(panel: MarkdownHtmlPanel): MarkdownBrowserPreviewExtension? =
            if (panel.browserPipe != null) MarkdownPreviewCopyExtension(panel) else null
    }

    companion object {
        const val ACTION_ID = "PasteToMarkdown.CopyPreviewForFeishu"
        private const val SCRIPT = "paste-to-md-preview-copy.js"
        private const val READY = "paste-to-md/copy-ready"
        private const val SHORTCUT = "paste-to-md/copy-shortcut"
        private const val RESULT = "paste-to-md/copy-result"
        private const val REQUEST = "paste-to-md/copy-request"
        private const val CONFIG = "paste-to-md/copy-config"

        internal fun browserKey(code: Int): String = when (code) {
            in KeyEvent.VK_A..KeyEvent.VK_Z -> ('a'.code + code - KeyEvent.VK_A).toChar().toString()
            in KeyEvent.VK_0..KeyEvent.VK_9 -> (code - KeyEvent.VK_0).toString()
            in KeyEvent.VK_F1..KeyEvent.VK_F12 -> "F${code - KeyEvent.VK_F1 + 1}"
            in KeyEvent.VK_F13..KeyEvent.VK_F24 -> "F${code - KeyEvent.VK_F13 + 13}"
            KeyEvent.VK_ENTER -> "Enter"
            KeyEvent.VK_ESCAPE -> "Escape"
            KeyEvent.VK_SPACE -> " "
            KeyEvent.VK_TAB -> "Tab"
            KeyEvent.VK_BACK_SPACE -> "Backspace"
            KeyEvent.VK_DELETE -> "Delete"
            KeyEvent.VK_UP -> "ArrowUp"
            KeyEvent.VK_DOWN -> "ArrowDown"
            KeyEvent.VK_LEFT -> "ArrowLeft"
            KeyEvent.VK_RIGHT -> "ArrowRight"
            KeyEvent.VK_HOME -> "Home"
            KeyEvent.VK_END -> "End"
            KeyEvent.VK_PAGE_UP -> "PageUp"
            KeyEvent.VK_PAGE_DOWN -> "PageDown"
            KeyEvent.VK_INSERT -> "Insert"
            KeyEvent.VK_COMMA -> ","
            KeyEvent.VK_PERIOD -> "."
            KeyEvent.VK_SLASH -> "/"
            KeyEvent.VK_SEMICOLON -> ";"
            KeyEvent.VK_QUOTE -> "'"
            KeyEvent.VK_OPEN_BRACKET -> "["
            KeyEvent.VK_CLOSE_BRACKET -> "]"
            KeyEvent.VK_BACK_SLASH -> "\\"
            KeyEvent.VK_BACK_QUOTE -> "`"
            KeyEvent.VK_MINUS -> "-"
            KeyEvent.VK_EQUALS -> "="
            else -> KeyEvent.getKeyText(code).lowercase()
        }

        internal fun notify(project: Project?, message: String) {
            NotificationGroupManager.getInstance().getNotificationGroup("Paste to Markdown")
                .createNotification(message, NotificationType.INFORMATION).notify(project)
        }
    }
}
