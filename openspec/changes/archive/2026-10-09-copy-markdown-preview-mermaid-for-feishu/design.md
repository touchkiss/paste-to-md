## Context

用户已取消飞书 Mermaid 绘图支持及 root/ID/JSON 实验。当前方案以浏览器执行原生 copy 所写入的剪贴板 HTML 为输入，不再自行克隆选区作为普通内容的 HTML 来源。Mermaid 实际渲染源码由当前 `.mermaid[data-actual-fence-content]` 的 Base64/UTF-8 属性读取。

## Goals / Non-Goals

保留原生复制的普通结构；表格追加 ace-table；所有 Mermaid 类型统一复制为源码 pre/code。暂不生成飞书绘图、占位 span、页面包装、行 ID、recordIds、recordMap 或元数据。

## Decisions

### 原生复制与选区

前端检查焦点及单一非折叠 Range，同步读取所有命中图的源码。图内部分选区按完整绘图处理，临时将 Range 端点扩到整个 Mermaid 围栏以确保原生 HTML 含绘图容器；调用 `document.execCommand("copy")`，finally 恢复原选区及方向。普通内容不重新序列化、不修改 DOM。仅边界相接的图不纳入。

BrowserPipe 返回 nativeCopy、按选区顺序的 sources，含图时还返回围栏纯文本。IDE 读取当前系统剪贴板 HTML 和原生纯文本；源图数量与原生 HTML 中绘图围栏数量须一致。绘图源码在同次请求内按原生 DOM 顺序关联，不按全篇文件中的图序号猜测。

### HTML 最小转换

PreviewClipboardHtmlNormalizer 使用 Jsoup 读取原生 HTML 片段，保留普通节点、属性及顺序。每个 table 追加 ace-table。Mermaid 的整个 pre 围栏（或 code / Mermaid 容器）替换为新 pre/code.language-mermaid，源码用 text 写入，序列化时安全转义。复制按钮、图标、tooltip、SVG 因整段替换而移除。普通 SVG 和其他代码块保留。

不进行图类型/view 映射，不生成 root、ID 或 JSON，也不读取 res 固定样本。原生复制自身已有的普通 div、class 和属性不被额外剥除。HTML 序列化可能规范化标记拼写，不保证与原生剪贴板逐字相同。

### 纯文本及失败

无图时沿用原生纯文本。含图时前端按原选区文本范围输出完整 Mermaid 围栏，围栏长度避开源码中的反引号。源码缺失或编码无效在原生复制前报错；原生复制成功后如果缺少 HTML 或映射失败，保留这次原生复制结果并提示。

转换在后台完成，提交前检查最新请求、3 秒超时、焦点、生命周期及剪贴板仍等于已读取的原生结果。用户期间再次复制或切换预览时，不覆盖后来的内容。

## Risks / Trade-offs

- 原生 copy 命令在 JCEF 的可用性、系统 HTML 的时序及局部表格祖先形状仍须实际 IDEA 验证；JS 测试使用原生复制 mock，不能证明系统剪贴板行为。
- 如果原生 HTML 丢失 Mermaid 围栏，转换明确失败并保留原生内容，不用其他图或 SVG 标签猜测源码。
- 复制的是 IDEA 预览实际渲染源码，可能经过 IDEA 自身预处理。

## Validation

运行 JS 模拟 DOM/原生命令测试及 OpenSpec 校验。新增 Kotlin HTML 转换测试，由用户自行构建及运行。人工验收覆盖普通内容、部分表格、所有图类型、图内选区、多图混合、纯文本、增量更新及普通 Command+C。
