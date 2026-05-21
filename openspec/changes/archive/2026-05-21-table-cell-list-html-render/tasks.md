## 1. 核心实现

- [x] 1.1 在 `RichTextToMarkdownConverter.kt` 中新增 `renderTableCellListHtml(list: Element, ordered: Boolean): String` 函数：递归地将 `<ul>`/`<ol>` 转换为紧凑 HTML 字符串（`<ul><li>内容<ul>嵌套</ul></li></ul>`），`<li>` 内的文本节点和非列表子元素通过 `renderTableCellNode()` 渲染
- [x] 1.2 修改 `renderTableCellNode()` 中 `ul`/`ol` 的处理分支：不再调用 `renderTableList()`，改为调用 `renderTableCellListHtml(element, ordered)`
- [x] 1.3 删除 `renderTableList()` 函数及其所有调用点

## 2. 测试

- [x] 2.1 更新 `RichTextToMarkdownConverterTest` 中现有的 `renders nested list in table cell with indentation using nbsp` 测试：修改断言，从 `&#160;&#160;- 子项1` 改为断言 `<ul><li>` 嵌套结构
- [x] 2.2 新增测试：单层无序列表在表格单元格内输出 `<ul><li>...</li></ul>`
- [x] 2.3 新增测试：三层嵌套无序列表在表格单元格内保持正确嵌套层级
- [x] 2.4 新增测试：有序列表在表格单元格内输出 `<ol><li>...</li></ol>`
- [x] 2.5 新增测试：`<li>` 内含 `<strong>` 时，输出的 `<li>` 内容包含 `**bold**`

## 3. 验证

- [x] 3.1 运行全量测试套件（`./gradlew test`），确保无回归
