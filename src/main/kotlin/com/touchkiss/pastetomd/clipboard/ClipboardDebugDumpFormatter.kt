package com.touchkiss.pastetomd.clipboard

internal class ClipboardDebugDumpFormatter {

    fun shouldInlinePreview(dump: ClipboardDebugDump): Boolean = estimateSize(dump) <= MAX_INLINE_CHARS

    fun buildDumpText(dump: ClipboardDebugDump): String {
        return buildString {
            appendLine("=== Data Flavors ===")
            if (dump.flavors.isEmpty()) {
                appendLine("(none)")
            } else {
                dump.flavors.forEach { appendLine(it) }
            }

            appendLine()
            appendLine("=== HTML ===")
            appendLine(dump.payload.html ?: "(none)")

            appendLine()
            appendLine("=== RTF ===")
            appendLine(dump.payload.rtf ?: "(none)")

            appendLine()
            appendLine("=== Plain Text ===")
            appendLine(dump.payload.plainText ?: "(none)")
        }
    }

    fun buildSummary(dump: ClipboardDebugDump): String {
        return buildString {
            appendLine("剪贴板内容较大，已改为导出到临时文件以避免卡住 IDEA。")
            appendLine()
            appendLine("DataFlavor 数量: ${dump.flavors.size}")
            appendLine("HTML 长度: ${dump.payload.html?.length ?: 0}")
            appendLine("RTF 长度: ${dump.payload.rtf?.length ?: 0}")
            appendLine("Plain Text 长度: ${dump.payload.plainText?.length ?: 0}")
        }
    }

    private fun estimateSize(dump: ClipboardDebugDump): Int {
        return dump.flavors.sumOf { it.length } +
            (dump.payload.html?.length ?: 0) +
            (dump.payload.rtf?.length ?: 0) +
            (dump.payload.plainText?.length ?: 0)
    }

    companion object {
        const val MAX_INLINE_CHARS: Int = 200_000
    }
}

