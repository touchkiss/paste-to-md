## Why

用户在 IDEA Markdown 预览中复制表格并粘贴到飞书文档时，观察到表格被识别为类似 Excel 的表格。新增专用复制入口，为预览 HTML 表格追加 `ace-table` 标记，按用户确认的方案实现即可；验收以表格添加该 class 并正确复制为准。

## What Changes

- 新增“Copy Markdown Preview for Feishu”动作，macOS 默认快捷键为 `Control+Command+C`，允许通过 IDEA Keymap 修改。
- 在 Markdown 预览面板具有焦点且存在选区时，给当前预览 DOM 中的 `table` 追加 `ace-table` class，再复制当前选区的 HTML 与纯文本。
- 保留表格已有 class、单元格结构及选区中的非表格内容；重复调用不重复添加 class。
- 无选区、预览尚未加载或不支持当前预览引擎时，保留剪贴板并提示原因。
- 不修改 Markdown 源文件，不接管普通复制，也不自动扩展为复制整篇文档。

## Capabilities

### New Capabilities

- `markdown-preview-feishu-copy`: Markdown 预览选区复制、表格 class 增强、快捷键入口、富文本剪贴板及失败处理。

### Modified Capabilities

无。既有飞书富文本转 Markdown 和表格内列表渲染规范保持不变。

## Impact

- 在 `src/main/kotlin/com/touchkiss/pastetomd/actions/` 添加复制动作，在 `plugin.xml` 注册动作及 Markdown 预览扩展。
- 新增预览桥接、选区序列化脚本及 HTML/纯文本 `Transferable`；复用已有 Markdown 插件依赖和 Jsoup。
- 初期针对项目当前基线 IDEA 262 的本地 JCEF Markdown 预览；其他预览引擎明确提示不支持。
- 新增单元测试、IDEA 预览集成验证，更新 README 使用说明。
