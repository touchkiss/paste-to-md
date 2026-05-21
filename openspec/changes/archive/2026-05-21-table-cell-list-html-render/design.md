## Context

`renderTableList()` in `RichTextToMarkdownConverter.kt` renders `<ul>`/`<ol>` elements inside table cells as plain-text Markdown-style lists, using `&#160;&#160;` repeated `indent` times to simulate indentation. This approach works around the fact that real Markdown list syntax (`-`, `1.`) doesn't render inside GFM table cells, but any indentation deeper than one level becomes fragile: the spacing relies on non-breaking-space entities surviving the `MarkdownPostProcessor` pipeline, and the output is tightly coupled to that implementation detail.

GitHub Flavored Markdown and IntelliJ Markdown preview both support inline HTML inside table cells, including `<ul>`/`<ol>`/`<li>` tags with arbitrary nesting. Switching to native HTML removes all indentation workarounds and delegates nesting to the browser/renderer's own CSS.

## Goals / Non-Goals

**Goals:**
- Replace `renderTableList()` text-based output with a new `renderTableCellListHtml()` function that emits `<ul>`/`<ol>` HTML
- Correctly handle arbitrary nesting depth without any `&#160;` workarounds
- Keep `renderTableCellNode()` as the single dispatch point for all table cell rendering

**Non-Goals:**
- Changing how lists are rendered outside table cells (`renderList()` is unaffected)
- Supporting table cell lists in renderers that do not accept inline HTML (out of scope)
- Reformatting or pretty-printing the emitted HTML (compact single-line per `<li>` is fine)

## Decisions

### Decision: Emit compact HTML rather than indented HTML

**Choice**: Produce `<ul><li>item</li><li>item<ul><li>nested</li></ul></li></ul>` as a single compact string.

**Rationale**: Table cells ultimately appear on one Markdown line. Newlines inside the HTML would be interpreted as paragraph breaks by some renderers. Compact HTML avoids this edge case entirely.

**Alternatives considered**:
- Pretty-printed multi-line HTML: risks paragraph-break misinterpretation inside table cells.
- Continuing with `&#160;` workaround: fragile; breaks if `MarkdownPostProcessor` changes.

### Decision: New `renderTableCellListHtml(list, ordered)` replaces `renderTableList()`

**Choice**: Add a new function and change the call site in `renderTableCellNode()`. Remove `renderTableList()` (or leave unreachable — delete is cleaner).

**Rationale**: The two functions have fundamentally different output formats (HTML vs. plain text). A single function with a flag parameter would be harder to read and test than two focused functions. Since `renderTableList()` is called from only one location (`renderTableCellNode()`), deleting it after the call site is updated is safe.

### Decision: Recurse on `<li>` children to find nested `<ul>`/`<ol>`

**Choice**: Within each `<li>`, collect inline text content first, then append any child `<ul>`/`<ol>` by recursing into `renderTableCellListHtml()`.

**Rationale**: Mirrors the existing `renderTableList()` structure (text extraction then nested list processing), minimising behavioural regression. The text extraction reuses the existing `renderTableCellNode()` dispatch for non-list nodes inside each `<li>`.

## Risks / Trade-offs

- **Renderer compatibility**: Inline HTML in table cells is widely supported (GFM, IntelliJ) but not universal. → Acceptable: the proposal explicitly scopes this to GFM and IntelliJ.
- **Loss of Markdown-style indentation as plain text**: Clients that strip HTML from Markdown will lose the list structure entirely rather than degrading to indented plain text. → Acceptable tradeoff for the cases that do render HTML.
- **`renderTableList()` deletion**: If other call sites are added in the future they will need to use `renderTableCellListHtml()`. → Low risk; the function was always internal.

## Open Questions

None — design is straightforward given the existing `renderTableCellNode()` dispatch pattern.
