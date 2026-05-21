## Why

表格单元格内的 `<ul>`/`<ol>` 当前使用自定义 `renderTableList()` 函数输出 Markdown 风格的纯文本列表，通过 `&#160;&#160;` 等 workaround 模拟缩进，无法完美表达多层嵌套结构，且在不同 Markdown 渲染器中显示效果不一致。改为输出原生 HTML `<ul>`/`<ol>` 标签，可利用 HTML 本身的树状结构完整表达任意层级嵌套，且 GitHub Flavored Markdown 和 IntelliJ 均支持表格内嵌 HTML。

## What Changes

- **修改** `renderTableCellNode()` 中 `ul`/`ol` 的处理分支：不再调用 `renderTableList()`，改为调用新增的 `renderTableCellListHtml()` 函数，直接输出 `<ul>/<ol>` HTML 标签及嵌套 `<li>` 结构
- **新增** `renderTableCellListHtml(list, ordered)` 函数：递归地将 `<ul>`/`<ol>` 转换为 HTML `<ul><li>...</li></ul>` 字符串，嵌套列表保持原有树状结构
- **移除** `renderTableList()` 及其 `indent`/`&#160;` workaround 逻辑（或保留但不再从 table cell 路径调用）

## Capabilities

### New Capabilities
- `table-cell-html-list`: 表格单元格内的列表使用 HTML 原生 `<ul>`/`<ol>` 渲染，完整支持任意层级嵌套

### Modified Capabilities

## Impact

- **文件**: `src/main/kotlin/com/touchkiss/pastetomd/converter/RichTextToMarkdownConverter.kt`（`renderTableCellNode()`、`renderTableList()`，约第 460-525 行）
- **测试**: `src/test/kotlin/com/touchkiss/pastetomd/converter/RichTextToMarkdownConverterTest.kt`（更新现有 table list 测试，断言从 `&#160;&#160;-` 改为 `<li>` 结构）
- **范围**: 仅影响表格单元格内的列表渲染，普通段落列表路径（`renderList`）不受影响
