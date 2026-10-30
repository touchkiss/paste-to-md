package com.touchkiss.pastetomd.preview

/** State is confined to the EDT. Expired or superseded responses cannot write the clipboard. */
internal class PreviewCopyRequest(private val now: () -> Long = System::nanoTime) {
    private var sequence = 0L
    private var pending: Long? = null
    private var deadline = 0L
    private var lastStart: Long? = null

    fun start(): Long? {
        val time = now()
        // JCEF and the IDE can both dispatch the same physical keystroke.
        if (lastStart?.let { time - it < 100_000_000L } == true) return null
        lastStart = time
        deadline = time + TIMEOUT_MS * 1_000_000L
        return (++sequence).also { pending = it }
    }

    fun finish(id: Long, contextValid: Boolean): Boolean {
        if (id != pending) return false
        // Keep expired work pending until the timeout handler can report it.
        if (now() >= deadline) return false
        pending = null
        return contextValid
    }

    fun isPending(id: Long): Boolean = id == pending && now() < deadline

    fun expire(id: Long): Boolean {
        if (id != pending) return false
        pending = null
        return true
    }

    fun cancel() { pending = null }

    companion object { const val TIMEOUT_MS = 30_000 }
}
