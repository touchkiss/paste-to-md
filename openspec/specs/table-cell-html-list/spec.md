# Spec: Table Cell HTML List Rendering

## Purpose

Defines how HTML list elements (`<ul>`, `<ol>`) inside Markdown table cells are rendered. Lists in table cells are converted to inline HTML strings rather than plain-text Markdown bullet syntax, preserving structure and nesting.

## Requirements

### Requirement: Table cell unordered list renders as HTML
Inside a Markdown table cell, a `<ul>` element SHALL be converted to an HTML `<ul><li>…</li></ul>` string rather than plain-text Markdown bullet syntax.

#### Scenario: Single-level unordered list in table cell
- **WHEN** a table cell contains `<ul><li>A</li><li>B</li></ul>`
- **THEN** the rendered output contains `<ul><li>A</li><li>B</li></ul>`

#### Scenario: Nested unordered list in table cell
- **WHEN** a table cell contains `<ul><li>Parent<ul><li>Child</li></ul></li></ul>`
- **THEN** the rendered output contains `<ul><li>Parent<ul><li>Child</li></ul></li></ul>`
- **AND** no `&#160;` indentation characters appear in the output for that list

#### Scenario: Multi-level deep nesting in table cell
- **WHEN** a table cell contains three or more levels of nested `<ul>`
- **THEN** each level is wrapped in its own `<ul>`/`<li>` tags in the output
- **AND** the nesting depth is preserved without limit

### Requirement: Table cell ordered list renders as HTML
Inside a Markdown table cell, an `<ol>` element SHALL be converted to an HTML `<ol><li>…</li></ol>` string.

#### Scenario: Single-level ordered list in table cell
- **WHEN** a table cell contains `<ol><li>First</li><li>Second</li></ol>`
- **THEN** the rendered output contains `<ol><li>First</li><li>Second</li></ol>`

#### Scenario: Nested ordered list inside unordered list in table cell
- **WHEN** a table cell contains `<ul><li>Item<ol><li>Step 1</li></ol></li></ul>`
- **THEN** the rendered output preserves the `<ol>` inside the `<ul><li>`

### Requirement: Inline text formatting is preserved inside HTML list items
Text content within `<li>` elements SHALL preserve inline formatting (bold, italic, code, links) via the existing `renderTableCellNode()` dispatch.

#### Scenario: Bold text inside table cell list item
- **WHEN** a `<li>` contains `<strong>bold</strong>`
- **THEN** the rendered `<li>` content includes `**bold**`

### Requirement: renderTableList is removed
The `renderTableList()` function SHALL be deleted from `RichTextToMarkdownConverter`. Its responsibilities are fully assumed by `renderTableCellListHtml()`.

#### Scenario: No call site references renderTableList after the change
- **WHEN** the change is applied
- **THEN** the codebase contains no calls to `renderTableList`
