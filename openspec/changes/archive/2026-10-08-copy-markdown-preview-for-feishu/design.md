## Context

现有插件使用 Kotlin/JVM、IntelliJ Platform 262，已依赖 `org.intellij.plugins.markdown`，并使用 Jsoup。现有动作处理从剪贴板到 Markdown 的粘贴，本功能新增反向的预览富文本复制路径。

已检查本地 IDEA Markdown 插件字节码：存在 `MarkdownBrowserPreviewExtension.Provider`、脚本资源扩展及 `BrowserPipe.send/subscribe/removeSubscription`；JCEF 面板为 `com.intellij.markdown.jcef.preview.MarkdownJCEFHtmlPanel`。`MarkdownPreviewFileEditor` 没有公开的 panel getter，不能把访问私有字段当作设计基础。扩展点名称、前端消息调用形式及焦点下动作派发，需要实施时对本地插件描述符和预览脚本进一步核对。

## Goals / Non-Goals

**Goals:**

- 在 JCEF Markdown 预览内，用 `Control+Command+C` 为预览中的表格添加 `ace-table` 并复制选区。
- 同时提供 HTML 和纯文本剪贴板数据，保持原有 class、表格结构和混合内容。
- 支持纯预览及分栏预览；只有预览区域具有焦点时处理快捷键。
- 对无选区、失效面板、超时和不支持的引擎提供明确反馈。

**Non-Goals:**

- 不改写 Markdown 源文件，不覆盖普通 `Command+C`，不重新渲染整篇 Markdown。
- 不增加自动监听所有复制或所有预览更新的全局行为，不实现 Compose 等其他预览引擎。
- 不调查飞书粘贴类型，不验证该 class 的飞书识别效果，不实现飞书私有剪贴板协议。

## Decisions

### 1. 使用预览扩展连接页面与 IDE

新增 `MarkdownPreviewCopyExtension`，通过已有 Markdown 浏览器扩展机制加载脚本，使用 `BrowserPipe` 接收复制请求和返回 `{requestId, html, text, error}`。服务登记与面板生命周期绑定的上下文，动作根据焦点选择当前预览；关闭面板时注销上下文、消息订阅和请求。避免通过反射读取私有 panel，以及从源编辑器选区推测网页选区，这两种方式分别存在兼容性和内容准确性问题。

新增 `CopyMarkdownPreviewForFeishuAction`，注册 macOS `ctrl meta C` 及 Find Action 入口；保留 Keymap 可配置性。验证 JCEF 焦点下 IDEA 是否收到动作；如需桥接浏览器键盘事件，桥接 MUST 读取动作当前配置的快捷键并走同一请求入口，防止固定按键绕过 Keymap 或双重触发。

实现采用 BrowserPipe 可用性判断支持能力，不直接依赖 JCEF 面板具体类，避免额外引入 JCEF 模块编译依赖。预览脚本接收当前 Keymap 配置并转发匹配的键盘事件；服务合并 100 毫秒内的重复请求，避免 IDEA 与浏览器双重派发。按用户最新要求，最终构建和 IDEA 运行验收由用户执行。

### 2. 在复制请求内追加 class，再从 DOM 获取真实选区

脚本先验证 `window.getSelection()` 存在非空 Range，随后对当前预览内容的所有 `table` 调用 `classList.add('ace-table')`，使实际预览 DOM 满足要求，保留其他 class。用 Range 克隆当前选区，确保输出中的每个 `table` 也包含该 class；后续预览重渲染丢失 class 时，下次触发重新添加，无需永久 MutationObserver。

`cloneContents()` 对仅选中单元格文字时可能不包含祖先 `table`，因此序列化器必须按最近的表格、行和单元格祖先补齐最小 HTML 结构，只保留选中的内容和必要结构，不扩展为整张表。跨表格与段落选区按文档顺序组合；纯文本由同一时刻选区取得。多个不连续 Range 首版明确拒绝，避免静默丢失内容。Jsoup 用于输出结构化处理与幂等 class 校验，不使用字符串替换 `<table>`。

### 3. 显式写入 HTML 与纯文本剪贴板

由 IDE 端构造支持 `DataFlavor.fragmentHtmlFlavor`、`DataFlavor.selectionHtmlFlavor`、`DataFlavor.allHtmlFlavor` 及 `DataFlavor.stringFlavor` 的 Transferable，通过 `CopyPasteManager.setContents` 写入系统剪贴板。HTML flavors 返回语义一致的片段或文档，纯文本返回选区文本；在 macOS 上检查系统剪贴板实际导出的 HTML 是否保留该 class。

相比仅调用网页原生复制，这条路径能校验最终 HTML 并统一错误处理；避免浏览器 Clipboard API 权限和用户手势限制。调用失败时不清空剪贴板，也不退化为复制源 Markdown。

### 4. 异步结果必须验证上下文

每次请求使用唯一 ID 和递增序号，记录面板、文件和项目；新请求替代旧请求，设置可取消的 3 秒超时。回调只在项目与面板存活、请求仍为最新、用户未切换活动预览时写入剪贴板；所有写入调度到 IDE 合适的 UI 线程，不阻塞 EDT。错误采用非模态提示，日志不记录选区正文。

## Risks / Trade-offs

- [Markdown 浏览器扩展 API 可能随 IDEA 更新变化] → 隔离桥接适配层，在当前 262 基线编译和运行验证；其他引擎明确反馈。
- [JCEF 抢占键盘事件或 Keymap 冲突] → 在纯预览及分栏实测默认快捷键、修改后的快捷键和 Find Action 入口，并验证每次只触发一次。
- [部分表格选区丢失祖先结构或扩大范围] → 用单元格局部、多行、跨表格与段落的 DOM 夹具验证，只复制所选内容。
- [DOM class 可能影响现有样式] → 仅在显式调用时添加，不注入新样式，在沙箱验证预览外观和选择不受影响。
- [异步回调覆盖较新的请求] → 请求序号、上下文有效性检查及超时取消；明确不追踪其他应用的外部剪贴板更新。

## Migration Plan

无需数据迁移。实现后通过 Gradle 单元测试、构建及 IDEA 沙箱验证，确认预览 DOM 与剪贴板 HTML 的表格 class 后更新 README。回滚时移除新增动作与扩展注册及对应模块，已有粘贴功能继续运行；预览重建后恢复原始 DOM。

## Open Questions

- 当前 IDEA 262 的扩展注册名称及浏览器快捷键转发机制，需要在实施第一步核对；若无法可靠支持预览内快捷键，应明确报告阻塞，不能用修改源文件或读取私有字段代替。
