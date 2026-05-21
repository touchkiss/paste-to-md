## Why

飞书文档复制到剪贴板时，列表项（`<li>`）内嵌套的表格会被完全丢弃，导致粘贴后表格内容缺失。根本原因是 `renderList()` 方法仅过滤 `<li>` 子元素，而飞书生成的 HTML 会将 `<table>` 包裹在 `<div>` 中直接放入 `<ul>`（而非放入 `<li>`），违反了标准 HTML 规范但飞书确实如此输出。

## What Changes

- **修复** `RichTextToMarkdownConverter.renderList()` 方法：在处理列表元素时，不再跳过非 `<li>` 的直接子元素，而是将其作为块级内容渲染并追加到列表输出中
- 添加对应的测试用例，覆盖"列表项内含表格"的场景

## Capabilities

### New Capabilities
- `feishu-table-in-list`: 支持将飞书列表（`ul`/`ol`）中直接包含的块级元素（尤其是 `<div><table>` 结构）正确渲染为 Markdown 表格

### Modified Capabilities
<!-- 无现有 spec 文件需要变更 -->

## Impact

- **文件**: `src/main/kotlin/com/touchkiss/pastetomd/converter/RichTextToMarkdownConverter.kt`（`renderList()` 方法，约第 170-198 行）
- **测试**: `src/test/kotlin/com/touchkiss/pastetomd/converter/FeishuStructuredHtmlParserTest.kt` 或新增测试文件
- **范围**: 仅影响通用 HTML 渲染路径（非飞书结构化路径），对其他来源内容无副作用
