(function () {
  "use strict";
  const pipe = window.__IntelliJTools.messagePipe;
  let shortcuts = [];
  let secondStrokes = [];
  let secondStrokeDeadline = 0;

  function sourceForDiagram(element) {
    const encoded = element.getAttribute("data-actual-fence-content");
    if (!encoded) throw new Error("mermaid-source-missing");
    let source;
    try {
      if (!/^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/.test(encoded)) {
        throw new Error("invalid-base64");
      }
      const bytes = Uint8Array.from(atob(encoded), character => character.charCodeAt(0));
      source = new TextDecoder("utf-8", { fatal: true, ignoreBOM: true }).decode(bytes);
    } catch (_) {
      throw new Error("mermaid-source-invalid");
    }
    if (!source.trim()) throw new Error("mermaid-source-missing");
    return source;
  }

  function startOf(range) {
    const point = range.cloneRange();
    point.collapse(true);
    return point;
  }

  function endOf(range) {
    const point = range.cloneRange();
    point.collapse(false);
    return point;
  }

  function compare(left, right) {
    return left.compareBoundaryPoints(Range.START_TO_START, right);
  }

  function nodeRange(node) {
    const result = document.createRange();
    result.selectNode(node);
    return result;
  }

  function overlaps(selection, candidate) {
    return compare(startOf(selection), endOf(candidate)) < 0 &&
      compare(endOf(selection), startOf(candidate)) > 0;
  }

  function diagramFence(source) {
    let length = 3;
    for (const match of source.matchAll(/`+/g)) length = Math.max(length, match[0].length + 1);
    const fence = "`".repeat(length);
    return fence + "mermaid\n" + source + (source.endsWith("\n") ? "" : "\n") + fence;
  }

  function diagramRoot(element) {
    // IDEA places its copy button beside <code>, inside the same fence.
    // The entire fence is the atomic drawing; none of its preview UI is content.
    const code = element.closest("code.language-mermaid");
    return code?.closest("pre.code-fence") || code || element;
  }

  function copySelection() {
    if (!document.hasFocus()) return { error: "not-focused" };
    const selection = window.getSelection();
    if (!selection || selection.rangeCount === 0 || selection.isCollapsed) {
      return { error: "empty-selection" };
    }
    if (selection.rangeCount !== 1) return { error: "multiple-ranges" };
    const range = selection.getRangeAt(0).cloneRange();
    const diagrams = Array.from(document.querySelectorAll(".mermaid"))
      .map(element => ({ element, root: diagramRoot(element) }))
      .filter(({ root }) => overlaps(range, nodeRange(root)))
      .map(({ element, root }) => ({ root, source: sourceForDiagram(element) }));
    // Validate all sources before changing the clipboard. Copy with the browser's
    // native command, rather than reconstructing ordinary HTML from a Range.
    const nativeRange = range.cloneRange();
    let text = "";
    let cursor = startOf(range);
    for (const { root, source } of diagrams) {
      const bounds = nodeRange(root);
      const before = startOf(bounds);
      if (compare(cursor, before) < 0) {
        const gap = range.cloneRange();
        gap.setStart(cursor.startContainer, cursor.startOffset);
        gap.setEnd(before.startContainer, before.startOffset);
        text += gap.toString();
      }
      text += (text && !text.endsWith("\n\n") ? "\n\n" : "") + diagramFence(source);
      cursor = compare(endOf(bounds), endOf(range)) < 0 ? endOf(bounds) : endOf(range);
      if (compare(cursor, endOf(range)) < 0) text += "\n\n";
      if (compare(startOf(range), before) > 0) nativeRange.setStartBefore(root);
      if (compare(endOf(range), endOf(bounds)) < 0) nativeRange.setEndAfter(root);
    }
    if (compare(cursor, endOf(range)) < 0) {
      const gap = range.cloneRange();
      gap.setStart(cursor.startContainer, cursor.startOffset);
      text += gap.toString();
    }
    const anchorNode = selection.anchorNode, anchorOffset = selection.anchorOffset;
    const focusNode = selection.focusNode, focusOffset = selection.focusOffset;
    try {
      if (diagrams.length) {
        selection.removeAllRanges();
        selection.addRange(nativeRange);
      }
      if (!document.execCommand("copy")) return { error: "native-copy-failed" };
    } finally {
      if (diagrams.length) selection.setBaseAndExtent(anchorNode, anchorOffset, focusNode, focusOffset);
    }
    return { nativeCopy: true, sources: diagrams.map(block => block.source),
      text: diagrams.length ? text : null };
  }

  pipe.subscribe("paste-to-md/copy-request", requestId => {
    let result;
    try {
      result = copySelection();
    } catch (error) {
      const known = ["mermaid-source-missing", "mermaid-source-invalid"];
      result = { error: known.includes(error.message) ? error.message : "native-copy-failed" };
    }
    pipe.post("paste-to-md/copy-result", JSON.stringify({ requestId, ...result }));
  });

  pipe.subscribe("paste-to-md/copy-config", data => {
    shortcuts = JSON.parse(data);
    secondStrokes = [];
  });

  function matches(event, stroke) {
    // Physical letter keys still match when Shift is held or an alternate layout is active.
    const punctuation = {
      Comma: ",", Period: ".", Slash: "/", Semicolon: ";", Quote: "'",
      BracketLeft: "[", BracketRight: "]", Backslash: "\\", Backquote: "`",
      Minus: "-", Equal: "=",
    };
    const physical = /^Key[A-Z]$/.test(event.code) ? event.code.slice(3).toLowerCase()
      : /^Digit[0-9]$/.test(event.code) ? event.code.slice(5) : punctuation[event.code] || event.key;
    return physical.toLowerCase() === stroke.key.toLowerCase() &&
      event.ctrlKey === stroke.ctrl && event.metaKey === stroke.meta &&
      event.altKey === stroke.alt && event.shiftKey === stroke.shift;
  }

  window.addEventListener("keydown", event => {
    if (!document.hasFocus() || event.repeat) return;
    // Pressing a modifier for the second stroke must not discard the pending shortcut.
    if (["Control", "Shift", "Alt", "Meta"].includes(event.key)) return;
    const completed = Date.now() < secondStrokeDeadline && secondStrokes.some(stroke => matches(event, stroke));
    secondStrokes = [];
    const candidates = shortcuts.filter(shortcut => matches(event, shortcut.first));
    if (completed || candidates.some(shortcut => !shortcut.second)) {
      event.preventDefault();
      event.stopImmediatePropagation();
      pipe.post("paste-to-md/copy-shortcut", "");
    } else if (candidates.length) {
      event.preventDefault();
      event.stopImmediatePropagation();
      secondStrokes = candidates.map(shortcut => shortcut.second);
      secondStrokeDeadline = Date.now() + 1000;
    }
  }, true);

  pipe.post("paste-to-md/copy-ready", "");
})();
