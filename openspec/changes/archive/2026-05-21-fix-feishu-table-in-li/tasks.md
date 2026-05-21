## 1. 修复核心渲染逻辑

- [x] 1.1 在 `RichTextToMarkdownConverter.renderList()` 中，将子元素过滤从 `.filter { it.tagName() == "li" }` 改为遍历所有子元素：`<li>` 走原有列表项逻辑，非 `<li>` 调用 `renderBlockNode(child, indentLevel)` 并追加到结果
- [x] 1.2 将有序列表的序号计数器从基于 `forEachIndexed` 改为独立的 `liIndex` 变量，仅在处理 `<li>` 时递增，确保非 `<li>` 元素不影响序号

## 2. 补充测试用例

- [x] 2.1 在 `FeishuStructuredHtmlParserTest`（或新建的 `RichTextToMarkdownConverterTest`）中，添加测试：输入包含 `<ul><li>文字</li></ul>` + 内嵌 `<ul><div><table>...</table></div></ul>` 结构的 HTML，断言输出包含 Markdown 表格
- [x] 2.2 添加测试：列表文字与嵌套表格同时存在时，两者均出现在输出中
- [x] 2.3 添加测试：有序列表中含非 `<li>` 子元素时，序号仍从 1 连续递增
- [x] 2.4 添加回归测试：纯标准 `<li>` 列表的渲染结果与修改前一致

## 3. 验证

- [x] 3.1 运行全量测试套件（`./gradlew test`），确保无回归
- [x] 3.2 用 `full.html` 中的片段手动验证：粘贴后输出包含"数据 | 计算公式 | 边界条件"表格
