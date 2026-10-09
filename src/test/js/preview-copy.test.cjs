// Run with Node's test runner and jsdom 26 available via NODE_PATH.
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { JSDOM } = require("jsdom");
const script = readFileSync(new URL("../../main/resources/preview/paste-to-md-preview-copy.js", `file://${__filename}`), "utf8");

function fixture(html) {
  const dom = new JSDOM(html, { runScripts: "outside-only" });
  const callbacks = {};
  const posted = [];
  const nativeCopies = [];
  dom.window.document.execCommand = command => {
    assert.equal(command, "copy");
    const range = dom.window.getSelection().getRangeAt(0);
    const container = dom.window.document.createElement("div");
    container.appendChild(range.cloneContents());
    nativeCopies.push(container.innerHTML);
    return true;
  };
  dom.window.document.hasFocus = () => true;
  dom.window.TextDecoder = TextDecoder;
  dom.window.__IntelliJTools = { messagePipe: {
    subscribe: (tag, callback) => { callbacks[tag] = callback; },
    post: (tag, data) => posted.push({ tag, data }),
  } };
  dom.window.eval(script);
  function select(start, startOffset, end, endOffset) {
    const range = dom.window.document.createRange();
    range.setStart(start, startOffset);
    range.setEnd(end, endOffset);
    const selection = dom.window.getSelection();
    selection.removeAllRanges();
    selection.addRange(range);
  }
  function copy() {
    callbacks["paste-to-md/copy-request"]("42");
    return JSON.parse(posted.at(-1).data);
  }
  return { dom, document: dom.window.document, callbacks, posted, nativeCopies, select, copy };
}

function diagram(source) {
  return `<pre class="code-fence"><div class="code-fence-highlighter-copy-button"><span class="tooltiptext">Copy to clipboard</span></div><code class="language-mermaid"><div class="mermaid" data-actual-fence-content="${Buffer.from(source).toString("base64")}"><svg><text>diagram label</text></svg></div></code></pre>`;
}

test("ordinary HTML comes exclusively from one native copy with unchanged selection and DOM", () => {
  const f = fixture("<p><strong>before</strong></p><table class='existing'><tr><td>cell</td></tr></table><p>after</p>");
  const paragraphs = f.document.querySelectorAll("p");
  f.select(paragraphs[0].firstChild.firstChild, 2, paragraphs[1].firstChild, 3);
  const before = f.document.body.innerHTML;
  const result = f.copy();
  assert.equal(result.nativeCopy, true);
  assert.deepEqual(result.sources, []);
  assert.equal(result.html, undefined); // The backend reads native clipboard HTML.
  assert.equal(result.text, null); // Keep native plain text for ordinary content.
  assert.equal(f.nativeCopies.length, 1);
  assert.equal(f.document.body.innerHTML, before);
  assert.equal(f.dom.window.getSelection().toString(), "forecellaft");
  f.dom.window.close();
});

test("partial Mermaid label or toolbar selects complete native fence then restores original selection", () => {
  for (const selector of ["text", ".tooltiptext"]) {
    const source = 'flowchart LR\nA[中文 😀 &<>" &quot; ```] --> B';
    const f = fixture(diagram(source));
    const label = f.document.querySelector(selector).firstChild;
    f.select(label, 1, label, 4);
    const original = f.document.body.innerHTML;
    const result = f.copy();
    assert.equal(result.nativeCopy, true);
    assert.deepEqual(result.sources, [source]);
    assert.equal(result.text, "````mermaid\n" + source + "\n````");
    assert.equal(f.nativeCopies[0], original);
    assert.equal(f.dom.window.getSelection().anchorNode, label);
    assert.equal(f.dom.window.getSelection().anchorOffset, 1);
    assert.equal(f.dom.window.getSelection().focusOffset, 4);
    assert.equal(f.document.body.innerHTML, original);
    f.dom.window.close();
  }
});

test("mixed and repeated diagrams retain source order and ordinary text boundaries", () => {
  const f = fixture(`<p>before</p>${diagram("graph TD; A-->B")}<p>middle</p>${diagram("graph TD; A-->B")}<p>after</p>${diagram("unselected")}`);
  const paragraphs = f.document.querySelectorAll("p");
  f.select(paragraphs[0].firstChild, 2, paragraphs[2].firstChild, 3);
  const result = f.copy();
  assert.deepEqual(result.sources, ["graph TD; A-->B", "graph TD; A-->B"]);
  assert.ok(result.text.startsWith("fore\n\n```mermaid"));
  assert.ok(result.text.endsWith("\n\naft"));
  assert.ok(!result.text.includes("Copy to clipboard") && !result.text.includes("diagram label"));
  assert.ok(!f.nativeCopies[0].includes("unselected"));
  f.dom.window.close();
});

test("diagram endpoints expand only diagram boundaries and restore backward selection", () => {
  const f = fixture(`<p>before</p>${diagram("graph TD; A-->B")}<p>between</p>${diagram('pie\n"A": 2')}<p>after</p>`);
  const labels = f.document.querySelectorAll("text");
  const selection = f.dom.window.getSelection();
  selection.setBaseAndExtent(labels[1].firstChild, 4, labels[0].firstChild, 3);
  const result = f.copy();
  assert.equal(result.sources.length, 2);
  assert.ok(!f.nativeCopies[0].includes("before") && !f.nativeCopies[0].includes("after"));
  assert.ok(f.nativeCopies[0].includes("between"));
  assert.equal(selection.anchorNode, labels[1].firstChild);
  assert.equal(selection.anchorOffset, 4);
  assert.equal(selection.focusNode, labels[0].firstChild);
  assert.equal(selection.focusOffset, 3);
  f.dom.window.close();
});

test("adjacent unselected diagrams and ordinary SVG are not Mermaid sources", () => {
  const f = fixture(`${diagram("excluded-left")}<p>selected</p><svg><text>ordinary</text></svg>${diagram("excluded-right")}`);
  f.select(f.document.body, 1, f.document.body, 3);
  assert.deepEqual(f.copy().sources, []);
  assert.ok(f.nativeCopies[0].includes("<svg>"));
  f.dom.window.close();
});

test("source validation happens before native copy", () => {
  for (const encoded of [null, "", "IA==", "!!!!", "wyg=", "YQ", "YWJj\n"]) {
    const f = fixture(`<div class="mermaid"${encoded === null ? "" : ` data-actual-fence-content="${encoded}"`}><svg><text>bad</text></svg></div>`);
    f.select(f.document.body, 0, f.document.body, 1);
    assert.ok(["mermaid-source-missing", "mermaid-source-invalid"].includes(f.copy().error));
    assert.equal(f.nativeCopies.length, 0);
    f.dom.window.close();
  }
});

test("source is read again after a preview patch", () => {
  const f = fixture(diagram("old"));
  f.select(f.document.body, 0, f.document.body, 1);
  assert.deepEqual(f.copy().sources, ["old"]);
  f.document.querySelector(".mermaid").setAttribute("data-actual-fence-content", Buffer.from("new").toString("base64"));
  assert.deepEqual(f.copy().sources, ["new"]);
  f.dom.window.close();
});

test("native copy rejection or exception restores the original Mermaid selection", () => {
  for (const throws of [false, true]) {
    const f = fixture(diagram("flowchart TD; A-->B"));
    const label = f.document.querySelector("text").firstChild;
    f.select(label, 1, label, 4);
    f.document.execCommand = () => { if (throws) throw new Error("copy unavailable"); return false; };
    assert.equal(f.copy().error, "native-copy-failed");
    assert.equal(f.dom.window.getSelection().anchorNode, label);
    assert.equal(f.dom.window.getSelection().anchorOffset, 1);
    assert.equal(f.dom.window.getSelection().focusOffset, 4);
    f.dom.window.close();
  }
});

test("empty unfocused and discontinuous selections never run native copy", () => {
  const f = fixture("<p>abc</p>");
  assert.equal(f.copy().error, "empty-selection");
  f.document.hasFocus = () => false;
  assert.equal(f.copy().error, "not-focused");
  f.document.hasFocus = () => true;
  f.dom.window.getSelection = () => ({ rangeCount: 2, isCollapsed: false });
  assert.equal(f.copy().error, "multiple-ranges");
  assert.equal(f.nativeCopies.length, 0);
  f.dom.window.close();
});

test("configurable shortcut handles exactly one event and leaves ordinary copy alone", () => {
  const f = fixture("<p>abc</p>");
  const first = { key: "c", ctrl: true, meta: true, shift: false, alt: false };
  f.callbacks["paste-to-md/copy-config"](JSON.stringify([{ first, second: null }]));
  const press = args => {
    const event = new f.dom.window.KeyboardEvent("keydown", { key: "c", code: "KeyC", bubbles: true, cancelable: true, ...args });
    f.dom.window.dispatchEvent(event);
    return event.defaultPrevented;
  };
  assert.equal(press({ metaKey: true }), false);
  assert.equal(press({ ctrlKey: true, metaKey: true }), true);
  assert.equal(f.posted.filter(item => item.tag === "paste-to-md/copy-shortcut").length, 1);
  assert.equal(press({ ctrlKey: true, metaKey: true, repeat: true }), false);
  f.callbacks["paste-to-md/copy-config"](JSON.stringify([{ first: { ...first, meta: false, shift: true }, second: null }]));
  assert.equal(press({ ctrlKey: true, metaKey: true }), false);
  assert.equal(press({ ctrlKey: true, shiftKey: true, key: "C" }), true);
  f.dom.window.close();
});

test("two-stroke shortcuts require the configured second stroke", () => {
  const f = fixture("<p>abc</p>");
  const first = { key: "k", ctrl: true, meta: false, shift: false, alt: false };
  const second = { ...first, key: "c" };
  f.callbacks["paste-to-md/copy-config"](JSON.stringify([{ first, second }]));
  for (const key of ["k", "c"]) f.dom.window.dispatchEvent(new f.dom.window.KeyboardEvent("keydown", {
    key, code: `Key${key.toUpperCase()}`, ctrlKey: true, cancelable: true,
  }));
  assert.equal(f.posted.filter(item => item.tag === "paste-to-md/copy-shortcut").length, 1);
  f.dom.window.close();
});
