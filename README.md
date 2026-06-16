# Paste to Markdown

让 IntelliJ IDEA 里的 Markdown 粘贴体验，终于像个现代编辑器。

当你从网页、Word、飞书文档、预览态 Markdown 等地方复制富文本内容时，`Paste to Markdown` 会在你粘贴到 `.md` 文件时自动把剪贴板内容转换成更干净、更可编辑的 Markdown，而不是丢进来一坨样式残骸。

<!-- Plugin description -->

Paste rich text from web pages, Word, Feishu documents, and other preview surfaces directly into Markdown files and convert it into clean Markdown automatically.

Highlights:

- Automatic rich-text to Markdown conversion on `Shift + Cmd/Ctrl + V` paste in Markdown files
- HTML first, RTF fallback, plain-text safe degradation
- Preserves headings, tables, links, lists, blockquotes, code blocks, and inline formatting
- Supports Feishu-specific structured clipboard HTML, including Mermaid text-diagram blocks
- Provides actions to paste original content, reconvert clipboard content, and inspect clipboard source

<!-- Plugin description end -->

## 为什么做这个插件

JetBrains 自带的 Markdown 编辑器已经很好用了，但有一个痛点一直很真实：

- 从网页复制内容，粘进来通常不是 Markdown
- 从 Word 复制内容，格式经常要手工重排
- 从飞书文档复制内容，看起来像 HTML，实际上背后还是一套半结构化文档系统
- 从预览态 Markdown 页面复制内容，粘回来经常失去“可继续编辑”的结构

这个插件的目标很直接：

- 使用 `Shift + Cmd/Ctrl + V` 触发智能粘贴转换
- 仅在 Markdown 文件中生效
- 尽量保留结构语义，而不是保留视觉噪音
- 转换失败时安静降级，不打断输入流

## 核心能力

### 自动粘贴转换

在 Markdown 文件中使用 `Shift + Cmd/Ctrl + V` 粘贴时，插件会优先读取剪贴板中的富文本格式：

1. `text/html`
2. `text/rtf`
3. 纯文本兜底

如果识别到富文本并成功转换，就直接插入 Markdown；否则回退为原始粘贴行为。

### 通用 HTML / 富文本转换

当前支持的主要 Markdown 结构包括：

- 标题
- 段落
- 加粗、斜体、删除线
- 链接
- 行内代码、代码块
- 有序列表、无序列表
- 引用块
- GFM 表格
- 分隔线
- 远程图片链接

### 飞书文档专项支持

这是这个插件目前最“有灵魂”的部分。

飞书复制出来的内容经常不是普通 HTML，而是两层信息叠在一起：

- 一层是 `data-lark-html-role="root"` 的 HTML 根节点
- 另一层是 `data-lark-record-data` 里的结构化 JSON 元数据

插件会优先按飞书的一级块节点顺序转换，并针对以下内容做专项处理：

- 各级标题
- 正文块
- 列表
- 引用块
- 代码块
- 表格
- 分隔线
- 文本绘图 / 文本图表类 `ISV_BLOCK`

其中飞书的文本绘图块会从 `recordMap[blockId].snapshot.data.data` 中恢复真实内容，并输出为：

```markdown
```mermaid
...
```
```

像白板、第三方嵌入卡片这类无法可靠还原为 Markdown 的内容，会被明确跳过，而不是硬塞一段“暂时无法展示此内容”的占位文字进文档。

## 插件动作

除了自动粘贴转换之外，插件还提供了几个实用动作：

- `Paste as Original`
  直接按原始文本粘贴，跳过 Markdown 转换
- `Reconvert Clipboard to Markdown`
  手动把当前剪贴板内容重新转换为 Markdown 并插入
- `Show Clipboard Rich Text Source`
  显示当前剪贴板中的 `HTML`、`RTF`、纯文本及 `DataFlavor`，便于调试来源内容

最后这个动作对飞书尤其有用。很多时候问题不是“没写转换器”，而是“剪贴板里复制到的东西根本和你以为的不是同一个宇宙”。

## 设置项

在 IDEA 设置页中可以控制以下选项：

- 是否启用 Markdown 智能粘贴
- 是否优先转换 HTML
- 是否尝试处理 RTF
- 是否保留网络图片链接
- 是否开启调试日志

## 当前策略与边界

这个插件优先追求“文档可继续编辑”，而不是“视觉像素级还原”。

当前默认策略：

- 自动转换，不弹确认框
- 图片不落地成本地文件
- 网络图片尽量保留为 Markdown 图片链接
- 本地图片 / 内嵌图片默认不做文件导出
- 无法可靠表达的复杂嵌入内容优先跳过
- 出错时静默降级为普通粘贴

这意味着它更适合：

- 技术文档整理
- 方案说明迁移
- 文章摘录与沉淀
- 把飞书/网页内容转进代码仓库里的 Markdown

而不是做：

- 所见即所得排版还原
- Office 级格式忠实迁移
- 富媒体内容完整搬运

## 开发与调试

### 运行

```bash
./gradlew runIde
```

### 测试

```bash
./gradlew test
```

### 重点调试样例

仓库中提供了一些真实样本，适合调转换逻辑时回归：

- `part.html`
  少量飞书块内容，适合调 `data-lark-record-data` 与文本绘图恢复
- `data-lark-record-data.json`
  从飞书剪贴板元数据中提取出的结构化 JSON
- `full.html`
  富元素飞书文档样本，覆盖标题、表格、列表、引用、代码块、链接等
- `feishu-doc.html`
  更偏编辑态 DOM 的飞书样例，适合验证块类型识别与性能

## 适用版本

- IntelliJ Platform `2024.1+`
- 主要面向 IntelliJ IDEA，但理论上也适用于其他 JetBrains IDE 中的 Markdown 编辑场景

## 路线图

后续可以继续增强的方向包括：

- 飞书图片块 / 附件块恢复
- 更多 `ISV_BLOCK` 类型识别，而不只 Mermaid 文本绘图
- 嵌套列表和复杂表格的进一步优化
- 粘贴预览 / 差异确认模式
- 图片导出到相对目录并自动插入引用

## 一句话总结

`Paste to Markdown` 不是把富文本“塞进” Markdown，而是尽量把它“翻译成” Markdown。

前者像搬家时把整间客厅直接推进门里，后者至少知道沙发得先拆开。


