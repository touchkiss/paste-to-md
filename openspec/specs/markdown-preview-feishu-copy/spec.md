# markdown-preview-feishu-copy Specification

## Purpose
支持从 IDEA Markdown 预览选区执行原生复制，为剪贴板中的表格追加 `ace-table` class，并将 Mermaid 预览替换为源码代码块，方便粘贴到飞书文档。

## Requirements

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
有效复制请求 MUST 先执行预览浏览器原生复制，再为剪贴板 HTML 中的每个 table 追加 ace-table。已有 class、表格属性和单元格内容 SHALL 保留，重复处理 SHALL 幂等。表格结构 SHALL 沿用原生复制输出。

#### Scenario: Existing table class
- **WHEN** 原生复制 HTML 包含 class 为 existing 的表格
- **THEN** 处理后包含 existing 和 ace-table，单元格属性与内容保持原生结果

#### Scenario: Repeated processing
- **WHEN** 再次处理带有 ace-table 的表格
- **THEN** 不重复追加该 class

#### Scenario: Table without a class
- **WHEN** 原生复制 HTML 包含没有 class 的表格
- **THEN** 后处理为该表格添加 `class="ace-table"`，不修改真实预览 DOM

#### Scenario: Repeated copy and preview reload
- **WHEN** 用户连续触发复制，随后修改 Markdown 导致预览重建并再次复制
- **THEN** 每次复制输出的表格均包含且只包含一个 `ace-table` class token

### Requirement: Preserve selection scope and HTML structure
插件 MUST 使用真实预览单一非折叠选区执行原生复制，普通内容 SHALL 保留原生复制提供的文本范围、HTML 结构及格式，不自行生成普通节点或 ID。选区命中 Mermaid 围栏任意内容时 SHALL 复制完整图源码；仅边界相接的图 MUST NOT 被包含。图内端点可临时扩展到该图边界，复制后 MUST 恢复原选区及方向。

#### Scenario: Ordinary mixed selection
- **WHEN** 用户选中标题、段落、列表、表格及普通代码块
- **THEN** 插件以原生复制 HTML 为基础保留其内容顺序和结构，只为表格追加 ace-table

#### Scenario: Partial table selection
- **WHEN** 用户仅选择表格内部分文字
- **THEN** 使用原生复制提供的局部表格结构与所选内容，并为输出中的 table 添加 ace-table

#### Scenario: Partial Mermaid selection
- **WHEN** 用户只选中图内部分标签文字或绘图工具栏文字
- **THEN** 输出完整 Mermaid 源码代码块且恢复原选区，不扩展图外的普通文本范围

#### Scenario: Adjacent diagram
- **WHEN** 选区仅与另一图边界相接
- **THEN** 不输出该图

#### Scenario: Discontinuous ranges
- **WHEN** 选区包含多个不连续 Range
- **THEN** 提示暂不支持，并在原生复制前终止请求

#### Scenario: Mixed selection across tables
- **WHEN** 连续选区跨越表格、段落及另一张表格
- **THEN** 沿用原生复制结果按文档顺序保留所选内容，所有输出表格包含 `ace-table`

#### Scenario: Selection without tables
- **WHEN** 用户选中普通段落或代码块并触发动作
- **THEN** 插件复制原生 HTML 与纯文本，不凭空生成表格

### Requirement: Rich text and plain text clipboard payload
成功处理 MUST 同时提供 HTML 与纯文本。无图选区的纯文本 SHALL 使用原生复制结果；含图时 SHALL 按选区顺序输出 Mermaid 围栏源码与普通文本，不重复 SVG 标签和预览 UI 文字。HTML 中的表格 MUST 包含 ace-table，所选 Mermaid MUST 表示为 pre/code 源码块。

#### Scenario: Ordinary plain text
- **WHEN** 复制选区没有 Mermaid
- **THEN** 纯文本沿用原生复制内容

#### Scenario: Mermaid plain text
- **WHEN** 复制选区包含多张 Mermaid
- **THEN** 纯文本按顺序包含全部源码，使用足够长的围栏以保留源码反引号

#### Scenario: Clipboard exposes both representations
- **WHEN** 有效选区复制成功
- **THEN** 系统剪贴板可以读取 HTML 和纯文本，HTML 表格含 `ace-table`

#### Scenario: Paste into plain text destination
- **WHEN** 用户把复制结果粘贴到仅支持纯文本的目标
- **THEN** 目标获取选区文字及所选 Mermaid 围栏源码，而非 HTML 标记

### Requirement: Safe failures and asynchronous lifecycle
无选区、焦点无效、源码缺失或 Base64/UTF-8 无效时 MUST 在原生复制前终止并提示。原生复制成功后的 HTML 缺失或转换失败 MUST 保留该原生复制结果。后处理 MUST 遵守 3 秒超时、最新请求、焦点及生命周期规则，且在提交前确认剪贴板未被用户的新复制替换。

#### Scenario: Invalid source
- **WHEN** 所选图源码无效
- **THEN** 不调用原生复制，保留原剪贴板并提示

#### Scenario: Native HTML unavailable or inconsistent
- **WHEN** 原生复制没有提供 HTML 或绘图数量与当前选中源码不一致
- **THEN** 不写入转换结果，保留原生复制内容并提示

#### Scenario: Clipboard changed during conversion
- **WHEN** 用户在后台转换完成前再次复制、切换预览或原请求已过期
- **THEN** 迟到的转换结果不写入剪贴板

#### Scenario: Empty selection
- **WHEN** 已加载预览没有选区或选区折叠，用户触发动作
- **THEN** 插件在原生复制前提示先选择预览内容，不复制整篇文档或清空剪贴板

#### Scenario: Unsupported or unavailable preview
- **WHEN** 预览未加载或使用不支持的引擎，用户从动作入口尝试复制
- **THEN** 插件说明不可复制的原因并保留剪贴板

#### Scenario: Timeout or conversion error
- **WHEN** 当前请求超时或转换失败
- **THEN** 插件取消请求并提示，迟到的回调不写入剪贴板；原生复制前的失败保留原剪贴板，原生复制后的失败保留原生结果

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

### Requirement: Mermaid source code instead of Feishu drawing records
所有类型的所选 Mermaid MUST 从当前预览容器读取完整 Base64/UTF-8 源码，并在原生复制 HTML 的原位置替换为 pre > code.language-mermaid。源码 MUST 通过文本节点安全转义。整个绘图预览围栏 SHALL 被替换，包括 SVG、复制按钮、图标及 tooltip；普通 SVG 和其他语言代码块 SHALL 保留。

插件 MUST NOT 新增飞书 root 包装、随机图或行 ID、ace-line、占位 span 或记录 JSON。正常复制 MUST NOT 使用 res 中的固定样本，MUST NOT 将图转换为飞书可编辑绘图。

#### Scenario: Any Mermaid type
- **WHEN** 用户复制 flowchart、sequenceDiagram、classDiagram 或其他 Mermaid 图
- **THEN** 每张图均为完整源码 pre/code，不做 view 映射或生成飞书记录

#### Scenario: Mixed output without protocol wrappers
- **WHEN** 用户复制普通内容、表格及两张 Mermaid
- **THEN** 按顺序保留原生普通内容、ace-table 表格与两个源码块，不添加 root、ID 或 JSON

#### Scenario: Source escaping
- **WHEN** 源码含中文、emoji、换行、缩进、引号、反斜杠、&<> 或字面实体
- **THEN** 读取代码块文本可恢复完整源码，字符不会变成额外 HTML 节点

#### Scenario: Preview refresh
- **WHEN** 源码修改且预览更新后再次复制
- **THEN** 使用当前容器的源码
