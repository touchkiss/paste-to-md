package com.touchkiss.pastetomd.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.touchkiss.pastetomd.images.ImageMode
import com.touchkiss.pastetomd.images.ImageStore
import java.awt.BorderLayout
import javax.swing.*

class PasteToMarkdownConfigurable(private val project: Project) : Configurable {
    private val enabledCheckBox = JCheckBox("在 Markdown 文件中启用智能富文本粘贴")
    private val htmlCheckBox = JCheckBox("优先转换 HTML 剪贴板内容")
    private val rtfCheckBox = JCheckBox("当没有 HTML 时尝试转换 RTF")
    private val debugCheckBox = JCheckBox("开启调试日志")
    private val keep = JRadioButton("保留原图片链接", true)
    private val local = JRadioButton("下载图片到本地，并链接到本地文件")
    private val base64 = JRadioButton("下载图片，以 Base64 插入 Markdown")
    private val directory = JTextField(40)
    private val rebuild = JButton("生成 / 更新全量映射")
    private var rebuilding = false
    private fun mode() = when { local.isSelected -> ImageMode.LOCAL; base64.isSelected -> ImageMode.BASE64; else -> ImageMode.KEEP }
    private fun updateControls() {
        directory.isEnabled = local.isSelected
        rebuild.isEnabled = local.isSelected && !rebuilding
    }
    init {
        ButtonGroup().apply { add(keep); add(local); add(base64) }
        listOf(keep, local, base64).forEach { it.addActionListener { updateControls() } }
        rebuild.addActionListener { rebuildMap() }
    }
    override fun getDisplayName(): String = "Paste to Markdown"
    override fun createComponent(): JComponent = JPanel(BorderLayout()).apply {
        add(JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            listOf<JComponent>(enabledCheckBox, htmlCheckBox, rtfCheckBox, keep, local,
                JPanel(BorderLayout(8, 0)).apply { add(JLabel("存放目录："), BorderLayout.WEST); add(directory) },
                JLabel("支持绝对路径、项目相对路径及 \${ProjectPath}；图片链接相对于 Markdown 文件。"),
                rebuild, base64, debugCheckBox).forEach { component ->
                component.alignmentX = 0f
                add(component)
            }
        }, BorderLayout.NORTH)
        reset()
    }
    override fun isModified(): Boolean {
        val state = PasteToMarkdownSettings.instance(project).state
        return enabledCheckBox.isSelected != state.enabled || htmlCheckBox.isSelected != state.convertHtml ||
            rtfCheckBox.isSelected != state.convertRtf || debugCheckBox.isSelected != state.debugLogging ||
            mode().name != state.imageMode || directory.text != state.imageDirectory
    }
    override fun apply() {
        if (local.isSelected) try { ImageStore.resolveDirectory(directory.text, project.basePath) }
        catch (e: Exception) { throw ConfigurationException(e.message ?: "目录无效") }
        PasteToMarkdownSettings.instance(project).state.apply {
            enabled = enabledCheckBox.isSelected
            convertHtml = htmlCheckBox.isSelected
            convertRtf = rtfCheckBox.isSelected
            debugLogging = debugCheckBox.isSelected
            preserveImageLinks = true
            imageMode = mode().name
            imageDirectory = directory.text
        }
    }
    override fun reset() {
        val state = PasteToMarkdownSettings.instance(project).state
        enabledCheckBox.isSelected = state.enabled
        htmlCheckBox.isSelected = state.convertHtml
        rtfCheckBox.isSelected = state.convertRtf
        debugCheckBox.isSelected = state.debugLogging
        when (state.imageMode) { "LOCAL" -> local.isSelected = true; "BASE64" -> base64.isSelected = true; else -> keep.isSelected = true }
        directory.text = state.imageDirectory.orEmpty()
        updateControls()
    }
    private fun rebuildMap() {
        val path = try { ImageStore.resolveDirectory(directory.text, project.basePath) } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: "目录无效", displayName)
            return
        }
        rebuilding = true
        updateControls()
        object : Task.Backgroundable(project, "重建图片指纹映射", true) {
            var count = 0
            override fun run(indicator: ProgressIndicator) { count = ImageStore(path).rebuild { indicator.checkCanceled() } }
            override fun onSuccess() { Messages.showInfoMessage(project, "映射已更新，共 $count 个不同文件。", displayName) }
            override fun onThrowable(error: Throwable) { Messages.showErrorDialog(project, error.message ?: "重建失败", displayName) }
            override fun onFinished() { rebuilding = false; updateControls() }
        }.queue()
    }
}
