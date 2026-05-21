## ADDED Requirements

### Requirement: 渲染列表中直接包含的块级子元素
当 `<ul>` 或 `<ol>` 中存在非 `<li>` 的直接子元素（如包含 `<table>` 的 `<div>`）时，系统 SHALL 将其作为块级内容渲染并追加到列表输出中，而不是忽略。

#### Scenario: 飞书列表项内嵌套表格被渲染
- **WHEN** HTML 包含 `<ul><li>文字</li></ul>` 外层，且内层 `<ul>` 中存在 `<div><table>...</table></div>` 结构
- **THEN** 转换结果中 SHALL 包含对应的 Markdown 表格（`| 列头 | ... |` 格式）

#### Scenario: 表格内容完整保留
- **WHEN** 飞书嵌套列表中的 `<table>` 包含多行多列数据
- **THEN** 所有行列数据 SHALL 出现在输出的 Markdown 表格中，无内容丢失

#### Scenario: 有序列表序号不受非 li 元素影响
- **WHEN** `<ol>` 中同时存在 `<li>` 元素和非 `<li>` 块级子元素
- **THEN** 有序列表的序号 SHALL 仅按 `<li>` 数量递增，非 `<li>` 元素不占用序号

#### Scenario: 列表文字部分正常保留
- **WHEN** 一个 `<li>` 同时含有文字内容和内嵌子列表（含表格）
- **THEN** 列表项的文字部分 SHALL 出现在表格之前，两者均不丢失

#### Scenario: 无非 li 子元素时行为不变
- **WHEN** `<ul>` 中所有直接子元素均为标准 `<li>`
- **THEN** 渲染结果 SHALL 与修改前完全一致，无回归
