## Why

此前飞书 Mermaid 占位与记录 JSON 的复制实验仍未达到用户预期。按最新要求，暂停输出飞书绘图，改为以 IDEA 预览原生复制 HTML 为基础，仅处理表格和 Mermaid 源码。

## What Changes

- `Control+Command+C` 调用预览浏览器原生复制，再读取系统剪贴板 HTML。
- 每个 table 追加 `ace-table`，保留原有 class、属性和所选内容。
- 所选 Mermaid 一律输出 `pre > code.language-mermaid` 完整源码，整段替换绘图 SVG 及复制按钮、图标、tooltip。
- 保留普通段落、标题、列表、换行和代码块的原生复制结构；不生成飞书 root、随机 ID、ace-line 或记录 JSON。
- 普通选区的纯文本沿用原生复制结果；含图选区使用 Mermaid 围栏源码。
- 保留 Keymap、焦点、请求序号和超时检查；不改源文件或普通 Command+C。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `markdown-preview-feishu-copy`: 原生复制后处理及 Mermaid 源码代码块。

## Impact

修改预览脚本、剪贴板集成与轻量 HTML 转换器，更新测试及使用说明。生产流程不再调用飞书记录编码器或固定样本诊断模式。无需外部 API、新依赖或数据迁移。用户自行构建和验证实际 IDEA → 飞书行为。
