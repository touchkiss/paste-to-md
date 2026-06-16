# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the plugin in a sandboxed IDE instance
./gradlew runIde

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "com.touchkiss.pastetomd.converter.RichTextToMarkdownConverterTest"

# Build the plugin distribution
./gradlew buildPlugin

# Verify plugin compatibility
./gradlew verifyPlugin
```

## Architecture

This is an IntelliJ Platform plugin (Kotlin, targeting 2024.1+) that intercepts paste in Markdown files and converts rich text to Markdown. The plugin depends on the bundled `org.intellij.plugins.markdown` plugin.

### Paste Processing Pipeline

The core flow: `MarkdownPastePreProcessor` (IntelliJ `CopyPastePreProcessor` extension point) → `PasteToMarkdownService` → `RichTextToMarkdownConverter`.

1. **`MarkdownPastePreProcessor`** — intercepts `Shift+Cmd/Ctrl+V` in `.md` files, delegates to `PasteToMarkdownService`, and inserts the result or falls back to standard paste
2. **`PasteToMarkdownService`** — reads clipboard via `ClipboardPayloadExtractor`, decides between html/rtf/plain-text based on settings and heuristics (`shouldPreferPlainText`, `looksLikeMarkdownDocument`), then calls the converter
3. **`RichTextToMarkdownConverter`** — the main converter; uses Jsoup to parse HTML, then routes to either `FeishuStructuredHtmlParser` (if Feishu markers are detected) or the generic `renderBlocks` path. Output goes through `MarkdownPostProcessor` for cleanup.

### Two Conversion Paths inside `RichTextToMarkdownConverter`

- **Feishu path**: `FeishuStructuredHtmlParser.parse()` detects `[data-lark-html-role=root]` or `data-block-type`/`data-type` attributes. It reads `data-lark-record-data` JSON embedded in the HTML to reconstruct ISV blocks (e.g. Mermaid diagrams). Whiteboards and unrecognised ISV blocks are silently dropped.
- **Generic path**: `RichTextNormalizer` pre-cleans the HTML, then `renderBlocks`/`renderElement` walk the Jsoup DOM recursively. Lists inside table cells use HTML `<ul>`/`<ol>` output (not Markdown) to correctly preserve nesting.

### Clipboard Layer

`ClipboardFlavorDetector` finds `text/html` and `text/rtf` MIME types from Java's `Transferable`. `ClipboardPayloadExtractor` reads all three formats (html, rtf, plainText) into a `ClipboardPayload` data class.

### Actions

Three registered actions (in `plugin.xml`):
- `PasteOriginalAction` — paste raw without conversion
- `ReconvertClipboardAction` — force re-convert current clipboard (`Ctrl+Meta+V`)
- `ShowClipboardSourceAction` — debug dialog showing raw html/rtf/plain + DataFlavors

### Settings

`PasteToMarkdownSettings` (application-level persistent state) controls: enabled, convertHtml, convertRtf, preserveImageLinks, debugLogging. Configurable under **Settings → Editor → Paste to Markdown**.

## Test Fixtures

Real clipboard samples for regression testing are in the project root:
- `full.html` — comprehensive Feishu document covering headings, tables, nested lists, code blocks, links
- `data-lark-record-data.json` — extracted `data-lark-record-data` JSON from a Feishu clipboard payload

Tests in `converter/` and `paste/` are plain JUnit 5 and do not require an IDE runtime.