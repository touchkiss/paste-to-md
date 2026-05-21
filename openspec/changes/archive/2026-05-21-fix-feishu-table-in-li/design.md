## Context

飞书文档复制的 HTML 结构中，当表格嵌套在列表项内时，会输出如下非标准结构：

```html
<ul class="list-bullet1">
  <li>
    <div>列表文字</div>
    <ul class="list-bullet2">
      <div>          <!-- 直接是 div，不是 li -->
        <table>...</table>
      </div>
    </ul>
  </li>
</ul>
```

`renderList()` 当前实现：

```kotlin
val children = listElement.children().asSequence()
    .filter { it.tagName().equals("li", ignoreCase = true) }  // 只取 li
    .toList()
```

非 `<li>` 子元素（即含 `<table>` 的 `<div>`）被过滤掉，导致表格丢失。

## Goals / Non-Goals

**Goals:**
- 在 `renderList()` 中，对 `<ul>`/`<ol>` 的非 `<li>` 直接子元素，以块级方式渲染并追加到输出
- 有序列表的序号计数只跟随 `<li>` 元素，非 `<li>` 元素不占用序号
- 新增测试用例覆盖"li 内含 table"场景

**Non-Goals:**
- 不修改飞书结构化路径（`FeishuStructuredHtmlParser`）
- 不处理 `<table>` 内嵌套 `<ul>/<ol>` 的新场景（已有 `renderTableList` 处理）
- 不支持 `renderListItemLine()` 内部的表格（inline 位置的表格）

## Decisions

### 决策：复用 `renderBlockNode()` 处理非 `<li>` 子元素

在 `renderList()` 遍历 `listElement.children()` 时，将逻辑从"只处理 `<li>`"改为：

- `<li>` → 按原逻辑渲染为列表项
- 非 `<li>` → 调用 `renderBlockNode(child, indentLevel)` 渲染为块内容，追加到结果

**替代方案**：在 `renderList()` 内单独处理 `<div>` 含 `table` 的情况 → 放弃，不够通用，如果飞书将来放入其他块元素也会再度失效。

**选择 `renderBlockNode()` 的理由**：已有的 `renderBlockNode()` 能正确处理 `div`、`table`、`p` 等所有块级情况，复用成本低、健壮性高。

### 决策：不缩进非 `<li>` 块内容

非 `<li>` 块（表格）不添加额外列表缩进，原样输出 Markdown 表格。这符合 Markdown 的惯例：表格不能被缩进并仍然可解析。

## Risks / Trade-offs

- **风险**: 其他 HTML 来源若也在 `<ul>` 中放非 `<li>` 元素，可能引入意外的块内容 → 缓解：`renderBlockNode` 对空内容返回空列表，不会产生额外空行
- **权衡**: 非 `<li>` 的块内容不带列表缩进，可能在视觉上与列表上下文略有脱节 → 可接受，Markdown 表格本身不支持缩进语法

## Migration Plan

仅改动 `RichTextToMarkdownConverter.renderList()`，属于纯逻辑修复，无数据迁移需要。
