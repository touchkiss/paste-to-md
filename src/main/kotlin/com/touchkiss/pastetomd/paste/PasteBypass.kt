package com.touchkiss.pastetomd.paste

internal object PasteBypass {
    private val bypass = ThreadLocal.withInitial { false }

    fun enableForCurrentPaste() {
        bypass.set(true)
    }

    fun consume(): Boolean {
        val value = bypass.get()
        bypass.set(false)
        return value
    }
}

