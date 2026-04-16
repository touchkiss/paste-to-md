package io.github.liushiyou.pastetomd.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "PasteToMarkdownSettings", storages = [Storage("PasteToMarkdown.xml")])
class PasteToMarkdownSettings : SimplePersistentStateComponent<PasteToMarkdownSettings.SettingsState>(SettingsState()) {

    class SettingsState : BaseState() {
        var enabled by property(true)
        var convertHtml by property(true)
        var convertRtf by property(true)
        var preserveImageLinks by property(true)
        var debugLogging by property(false)
    }

    companion object {
        fun instance(): PasteToMarkdownSettings =
            ApplicationManager.getApplication().getService(PasteToMarkdownSettings::class.java)
    }
}
