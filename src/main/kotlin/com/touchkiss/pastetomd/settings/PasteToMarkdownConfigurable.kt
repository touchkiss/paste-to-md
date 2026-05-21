package com.touchkiss.pastetomd.settings

import com.intellij.openapi.options.Configurable
import java.awt.GridLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class PasteToMarkdownConfigurable : Configurable {
    private val enabledCheckBox = JCheckBox("在 Markdown 文件中启用智能富文本粘贴")
    private val htmlCheckBox = JCheckBox("优先转换 HTML 剪贴板内容")
    private val rtfCheckBox = JCheckBox("当没有 HTML 时尝试转换 RTF")
    private val imageCheckBox = JCheckBox("保留网络图片链接")
    private val debugCheckBox = JCheckBox("开启调试日志")

    override fun getDisplayName(): String = "Paste to Markdown"

    override fun createComponent(): JComponent {
        return JPanel(GridLayout(0, 1)).apply {
            add(enabledCheckBox)
            add(htmlCheckBox)
            add(rtfCheckBox)
            add(imageCheckBox)
            add(debugCheckBox)
        }
    }

    override fun isModified(): Boolean {
        val state = PasteToMarkdownSettings.instance().state
        return enabledCheckBox.isSelected != state.enabled ||
            htmlCheckBox.isSelected != state.convertHtml ||
            rtfCheckBox.isSelected != state.convertRtf ||
            imageCheckBox.isSelected != state.preserveImageLinks ||
            debugCheckBox.isSelected != state.debugLogging
    }

    override fun apply() {
        val state = PasteToMarkdownSettings.instance().state
        state.enabled = enabledCheckBox.isSelected
        state.convertHtml = htmlCheckBox.isSelected
        state.convertRtf = rtfCheckBox.isSelected
        state.preserveImageLinks = imageCheckBox.isSelected
        state.debugLogging = debugCheckBox.isSelected
    }

    override fun reset() {
        val state = PasteToMarkdownSettings.instance().state
        enabledCheckBox.isSelected = state.enabled
        htmlCheckBox.isSelected = state.convertHtml
        rtfCheckBox.isSelected = state.convertRtf
        imageCheckBox.isSelected = state.preserveImageLinks
        debugCheckBox.isSelected = state.debugLogging
    }
}
