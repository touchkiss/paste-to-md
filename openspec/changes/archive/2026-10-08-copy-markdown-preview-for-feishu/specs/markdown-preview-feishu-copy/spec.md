## ADDED Requirements

### Requirement: Preview copy action and configurable shortcut
插件 SHALL 提供“Copy Markdown Preview for Feishu”动作，macOS 默认快捷键为 `Control+Command+C`，允许通过 IDEA Keymap 修改。快捷键 MUST 仅在 Markdown 预览具有焦点时处理，并同时支持纯预览与分栏中的预览区域。

#### Scenario: Copy from preview only mode
- **WHEN** 用户在已加载的 JCEF Markdown 纯预览模式选中内容并按默认快捷键
- **THEN** 插件执行一次预览选区复制

#### Scenario: Copy from split preview
- **WHEN** 分栏模式的预览区域具有焦点和选区，用户触发动作
- **THEN** 插件复制预览选区而非源编辑器选区

#### Scenario: Customized shortcut
- **WHEN** 用户在 Keymap 替换默认快捷键并在预览中按新的快捷键
- **THEN** 插件通过新快捷键执行一次复制，原默认按键不再由此动作处理

#### Scenario: Focus outside preview
- **WHEN** 用户在源编辑器或其他窗口按该快捷键
- **THEN** 插件不读取其他预览的选区，也不写入剪贴板

### Requirement: Add ace-table without removing existing classes
有效复制请求执行时，插件 MUST 给当前预览内容 DOM 中的每个 `table` 追加 `ace-table` class，并确保复制 HTML 中的每个表格带有此 class；已有 class、表格属性和单元格内容 SHALL 保留，重复操作 SHALL 幂等。

#### Scenario: Table without a class
- **WHEN** 预览选区包含没有 class 的表格，用户触发动作
- **THEN** 预览 DOM 和复制 HTML 中的表格带有 `class="ace-table"`

#### Scenario: Existing table class
- **WHEN** 表格已带有 `class="existing"`
- **THEN** 新动作保留 `existing` 并追加 `ace-table`

#### Scenario: Repeated copy and preview reload
- **WHEN** 用户连续触发复制，随后修改 Markdown 导致预览重建并再次复制
- **THEN** 每次复制输出的表格均包含且只包含一个 `ace-table` class token

### Requirement: Preserve selection scope and HTML structure
插件 MUST 从真实预览 DOM 选区获取内容，保留所选文本、内联格式、段落及表格结构。部分表格选区 SHALL 补齐最小必要的表格祖先结构，但 MUST NOT 增加未选中的单元格内容或整篇文档。首版 MUST 拒绝多个不连续 Range 并给出提示。

#### Scenario: Partial cell selection
- **WHEN** 用户仅选中一个单元格内的部分文字并触发动作
- **THEN** HTML 保留包含 `ace-table` 的最小 `table`、行和单元格结构，正文仅含所选文字

#### Scenario: Mixed selection across tables
- **WHEN** 连续选区跨越表格、段落及另一张表格
- **THEN** 输出按文档顺序保留所选内容，所有输出表格包含 `ace-table`

#### Scenario: Selection without tables
- **WHEN** 用户选中普通段落或代码块并触发动作
- **THEN** 插件复制对应 HTML 与纯文本，不凭空生成表格

#### Scenario: Discontinuous ranges
- **WHEN** 浏览器选区包含多个不连续 Range
- **THEN** 插件提示暂不支持且保留原剪贴板

### Requirement: Rich text and plain text clipboard payload
成功复制后插件 MUST 同时提供可由系统应用读取的 HTML 与纯文本剪贴板格式；HTML MUST 保留 `ace-table`，纯文本 MUST 对应同一次预览选区。

#### Scenario: Clipboard exposes both representations
- **WHEN** 有效选区复制成功
- **THEN** 系统剪贴板可以读取 HTML 和纯文本，HTML 表格含 `ace-table`

#### Scenario: Paste into plain text destination
- **WHEN** 用户把复制结果粘贴到仅支持纯文本的目标
- **THEN** 目标获取选区文字而非 HTML 标记

### Requirement: Safe failures and asynchronous lifecycle
无选区、预览不可用、引擎不支持、序列化失败或请求超过 3 秒时，插件 MUST 保留剪贴板并提供非模态提示。失效、被替代、项目关闭或用户切换活动预览后的请求回调 MUST NOT 写入剪贴板。

#### Scenario: Empty selection
- **WHEN** 已加载预览没有选区或选区折叠，用户触发动作
- **THEN** 插件提示先选择预览内容，不复制整篇文档或清空剪贴板

#### Scenario: Unsupported or unavailable preview
- **WHEN** 预览未加载或使用不支持的引擎，用户从动作入口尝试复制
- **THEN** 插件说明不可复制的原因并保留剪贴板

#### Scenario: Timeout or serialization error
- **WHEN** 当前请求超时或选区序列化失败
- **THEN** 插件取消请求并提示，迟到的回调不写入剪贴板

#### Scenario: Stale response
- **WHEN** 用户发起新请求、切换活动预览或关闭项目后旧请求返回
- **THEN** 旧结果被丢弃

### Requirement: Preserve existing editing and copy behavior
插件 MUST NOT 修改 Markdown 源文件、接管普通复制快捷键或改变已有富文本粘贴转换行为。

#### Scenario: Source file remains unchanged
- **WHEN** 用户使用新动作复制表格
- **THEN** Markdown 源文件内容及修改状态保持不变

#### Scenario: Ordinary copy remains available
- **WHEN** 用户在预览使用普通 `Command+C`
- **THEN** 仍执行 IDEA 原有复制行为，不自动触发本动作
