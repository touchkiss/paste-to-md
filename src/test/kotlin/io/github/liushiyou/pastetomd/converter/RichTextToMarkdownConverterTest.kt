package io.github.liushiyou.pastetomd.converter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RichTextToMarkdownConverterTest {

    private val converter = RichTextToMarkdownConverter()

    @Test
    fun `converts headings links emphasis and paragraphs from html`() {
        val html = """
            <h1>标题</h1>
            <p>这是一段 <strong>加粗</strong> 和 <em>强调</em>，还有 <a href="https://example.com">链接</a>。</p>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            # 标题

            这是一段 **加粗** 和 *强调*，还有 [链接](https://example.com)。
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `converts nested lists blockquotes and code blocks from html`() {
        val html = """
            <blockquote><p>引用内容</p></blockquote>
            <ol>
              <li>第一项</li>
              <li>第二项
                <ul>
                  <li>子项</li>
                </ul>
              </li>
            </ol>
            <pre><code class="language-java">System.out.println("hi");</code></pre>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            > 引用内容

            1. 第一项
            2. 第二项
               - 子项

            ```java
            System.out.println("hi");
            ```
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `converts gfm tables and preserves remote images`() {
        val html = """
            <table>
              <tr><th>姓名</th><th>分数</th></tr>
              <tr><td>Alice</td><td>98</td></tr>
            </table>
            <p><img src="https://img.example.com/a.png" alt="示例图"></p>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            | 姓名 | 分数 |
            | --- | --- |
            | Alice | 98 |

            ![示例图](https://img.example.com/a.png)
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `expands merged table cells into a rectangular markdown table`() {
        val html = """
            <table>
              <tr>
                <th rowspan="2">模块</th>
                <th colspan="2">环境</th>
              </tr>
              <tr>
                <th>测试</th>
                <th>生产</th>
              </tr>
              <tr>
                <td>user</td>
                <td colspan="2">已部署</td>
              </tr>
            </table>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            | 模块 | 环境 | 环境 |
            | --- | --- | --- |
            | 模块 | 测试 | 生产 |
            | user | 已部署 | 已部署 |
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `drops local and embedded images but keeps alt text`() {
        val html = """
            <p><img src="file:///tmp/demo.png" alt="本地图"></p>
            <p><img src="data:image/png;base64,AAAA" alt="内嵌图"></p>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            本地图

            内嵌图
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `strips noisy word html wrappers`() {
        val html = """
            <html>
              <body>
                <p class="MsoNormal"><span style="font-weight:700">加粗</span><o:p></o:p></p>
              </body>
            </html>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals("**加粗**", markdown)
    }

    @Test
    fun `promotes class based headings from feishu style html`() {
        val html = """
            <div class="block docx-heading3-block focused" data-block-type="heading3">
              <div class="heading-block">
                <div class="heading heading-h3 heading-block-align-">
                  <div class="heading-content">
                    <div class="zone-container text-editor non-empty text-editor-focused" data-zone-id="69" data-slate-editor="true" contenteditable="true">
                      <div class="ace-line" data-node="true" dir="auto">
                        <span data-string="true" style="font-weight:bold;">方案三：Client 端</span>
                        <span data-string="true" style="font-weight:bold;">负载均衡器</span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <div role="heading" aria-level="2">这是二级标题</div>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            ### 方案三：Client 端负载均衡器

            ## 这是二级标题
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `preserves original order for nested list headings and following table blocks`() {
        val html = """
            <div data-lark-html-role="root">
              <ol start="2">
                <li data-list="number">
                  <h1>商品体系全景调研</h1>
                  <div>
                    <ol start="1">
                      <li data-list="number">
                        <h2>&nbsp;&nbsp;商品模型</h2>
                        <ol start="1">
                          <li data-list="number">
                            <h3>&nbsp;&nbsp;&nbsp;&nbsp;<strong>概念介绍</strong></h3>
                          </li>
                        </ol>
                        <div>
                          <table>
                            <tr><th>左</th><th>右</th></tr>
                            <tr><td>A</td><td>B</td></tr>
                          </table>
                        </div>
                      </li>
                    </ol>
                  </div>
                </li>
              </ol>
            </div>
        """.trimIndent()

        val markdown = converter.convertHtml(html)
        val headingIndex = markdown.indexOf("### 1. **概念介绍**")
        val tableIndex = markdown.indexOf("| 左 | 右 |")
        val headingCount = markdown.windowed("### 1. **概念介绍**".length, 1, partialWindows = false)
            .count { it == "### 1. **概念介绍**" }

        assertTrue(headingIndex >= 0, "应当先渲染嵌套标题")
        assertTrue(tableIndex >= 0, "应当渲染后续表格")
        assertTrue(headingIndex < tableIndex, "嵌套标题必须保持在表格前面")
        assertEquals(1, headingCount, "嵌套标题不应重复输出")
        assertTrue(markdown.lines().any { it == "## 1. 商品模型" }, "所有标题都应顶格输出")
        assertTrue(markdown.lines().any { it == "### 1. **概念介绍**" }, "嵌套标题前不应保留缩进空格")
    }

    @Test
    fun `converts feishu block types including quote code list and divider`() {
        val html = """
            <div class="block docx-text-block" data-block-type="text">
              <div class="text-block-wrapper"><div class="text-block">
                <div class="zone-container text-editor non-empty" data-slate-editor="true" contenteditable="true">
                  <div class="ace-line" data-node="true" dir="auto">
                    <span data-string="true" style="font-weight:bold;">核心思路</span>
                    <span data-string="true">：在 gRPC-client 端实现自定义的 </span>
                    <span><span class="inline-code inline-code_start inline-code_end"><span data-string="true">LoadBalancer</span></span></span>
                    <span data-string="true">。</span>
                  </div>
                </div>
              </div></div>
            </div>
            <div class="block docx-bullet-block" data-block-type="bullet">
              <div class="list-wrapper bullet-list"><div class="list list-style-group-1 list-align-">
                <div class="list-content">
                  <div class="zone-container text-editor non-empty" data-slate-editor="true" contenteditable="true">
                    <div class="ace-line" data-node="true" dir="auto"><span data-string="true">需要维护节点权重状态</span></div>
                  </div>
                </div>
              </div></div>
            </div>
            <div class="block docx-ordered-block" data-block-type="ordered">
              <div class="list-wrapper ordered-list"><div class="list list-align-">
                <button class="ud__button order" type="button">5.</button>
                <div class="list-content">
                  <div class="zone-container text-editor non-empty" data-slate-editor="true" contenteditable="true">
                    <div class="ace-line" data-node="true" dir="auto"><span data-string="true">预热失败仍注册</span></div>
                  </div>
                </div>
              </div></div>
            </div>
            <div class="block docx-quote_container-block" data-block-type="quote_container">
              <div class="quote-container-block"><div class="quote-container-block-children">
                <div class="render-unit-wrapper quote-container-render-unit">
                  <div class="block docx-text-block quote-container-render-unit" data-block-type="text">
                    <div class="text-block-wrapper"><div class="text-block">
                      <div class="zone-container text-editor non-empty" data-slate-editor="true" contenteditable="true">
                        <div class="ace-line" data-node="true" dir="auto">
                          <span data-string="true">[!NOTE] </span>
                          <span><span class="inline-code inline-code_start inline-code_end"><span data-string="true">IWarmUp.getOrder()</span></span></span>
                        </div>
                      </div>
                    </div></div>
                  </div>
                </div>
              </div></div>
            </div>
            <div class="block docx-code-block" data-block-type="code">
              <div class="editor-kit-code-block code-block code-fold-block">
                <div class="code-block-content">
                  <div class="zone-container text-editor non-empty code-block-zone-container" data-slate-editor="true" contenteditable="true">
                    <div class="ace-line" data-node="true" dir="auto"><div class="code-line-wrapper" data-line-num="1"><span data-string="true">line1</span></div></div>
                    <div class="ace-line" data-node="true" dir="auto"><div class="code-line-wrapper" data-line-num="2"><span data-string="true">line2</span></div></div>
                  </div>
                </div>
              </div>
            </div>
            <div class="block docx-divider-block" data-block-type="divider"></div>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            **核心思路**：在 gRPC-client 端实现自定义的 `LoadBalancer`。

            - 需要维护节点权重状态

            5. 预热失败仍注册

            > [!NOTE] `IWarmUp.getOrder()`

            ```
            line1
            line2
            ```

            ---
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `rtf fallback returns plain text markdown paragraphs`() {
        val rtf = """{\rtf1\ansi This is\line line two\par\par Another para}"""

        val markdown = converter.convertRtf(rtf)

        assertEquals(
            """
            This is
            line two

            Another para
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `returns empty when html is blank`() {
        assertTrue(converter.convertHtml("   ").isEmpty())
    }

    @Test
    fun `preserves top level block order when rendering many html elements`() {
        val html = buildString {
            repeat(12) { index ->
                append("<p>段落")
                append(index + 1)
                append("</p>")
            }
        }

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            段落1

            段落2

            段落3

            段落4

            段落5

            段落6

            段落7

            段落8

            段落9

            段落10

            段落11

            段落12
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `does not output empty bold markers for feishu keyword wrappers`() {
        val html = """
            <div class="ace-line">
              <strong><span data-zero-space="true">​</span></strong>
              <strong>有效内容</strong>
              <span> 正常文本 </span>
              <strong><span data-string="true"></span></strong>
            </div>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals("**有效内容** 正常文本", markdown)
    }

    @Test
    fun `converts sheet container by reusing inner table conversion`() {
        val html = """
            <div data-type="sheet">
              <div class="sheet-wrapper">
                <table>
                  <tr><th>服务</th><th>状态</th></tr>
                  <tr><td>user</td><td>OK</td></tr>
                </table>
              </div>
            </div>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            | 服务 | 状态 |
            | --- | --- |
            | user | OK |
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `prioritizes heading when list item wraps heading element`() {
        val html = """
            <ol>
              <li><h1>需求背景</h1></li>
              <li><h2>商品体系全景调研</h2></li>
            </ol>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            # 1. 需求背景
            ## 2. 商品体系全景调研
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `respects ordered list start attribute for heading list items`() {
        val html = """
            <ol start="3">
              <li><h2>第三步</h2></li>
              <li><h2>第四步</h2></li>
            </ol>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            ## 3. 第三步
            ## 4. 第四步
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `keeps blank line before markdown table after heading list item`() {
        val html = """
            <ol>
              <li><h1>需求背景</h1></li>
            </ol>
            <table>
              <tr><th>姓名</th><th>分数</th></tr>
              <tr><td>Alice</td><td>98</td></tr>
            </table>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            # 1. 需求背景

            | 姓名 | 分数 |
            | --- | --- |
            | Alice | 98 |
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `converts table cell line breaks to br tags`() {
        val html = """
            <table>
              <tr><th>字段</th><th>值</th></tr>
              <tr><td>说明</td><td><div>第一行</div><div>第二行</div></td></tr>
            </table>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            | 字段 | 值 |
            | --- | --- |
            | 说明 | 第一行<br/>第二行 |
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `renders table code block as html pre code and keeps indentation`() {
        val html = """
            <table>
              <tr><th>字段</th><th>代码</th></tr>
              <tr>
                <td>示例</td>
                <td><pre><code class="language-java">if (ok) {
    System.out.println(&quot;hi&quot;);
}</code></pre></td>
              </tr>
            </table>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertTrue(markdown.contains("<pre><code class=\"language-java\">if (ok) {<br/>    System.out.println(\"hi\");<br/>}</code></pre>"))
    }

    @Test
    fun `replaces nbsp entity and non breaking space with regular spaces`() {
        val html = "<p>hello&nbsp;world</p><p>foo\u00A0bar</p>"

        val markdown = converter.convertHtml(html)

        assertEquals(
            """
            hello world

            foo bar
            """.trimIndent(),
            markdown
        )
    }

    @Test
    fun `renders rich text in table cells with html tags and br separators`() {
        val html = """
            <table>
              <tr><th>字段</th><th>内容</th></tr>
              <tr>
                <td>描述</td>
                <td>
                  <div><strong>加粗</strong> + <em>强调</em></div>
                  <div><a href="https://example.com">链接</a></div>
                  <ul><li>条目A</li><li>条目B</li></ul>
                </td>
              </tr>
            </table>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertTrue(markdown.contains("<strong>加粗</strong> + <em>强调</em><br/><a href=\"https://example.com\">链接</a><br/>- 条目A<br/>- 条目B"))
    }

    @Test
    fun `recursively processes deeply nested html elements`() {
        val html = """
            <div>
              <div>
                <div>
                  <p>层级1 > 层级2 > 层级3 > 层级4</p>
                </div>
              </div>
            </div>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertEquals("层级1 > 层级2 > 层级3 > 层级4", markdown)
    }

    @Test
    fun `processes deeply nested rich text in table cells`() {
        val html = """
            <table>
              <tr><th>示例</th><th>内容</th></tr>
              <tr>
                <td>深层嵌套</td>
                <td>
                  <div>
                    <span>
                      <strong>
                        <div>深层富文本</div>
                      </strong>
                    </span>
                  </div>
                </td>
              </tr>
            </table>
        """.trimIndent()

        val markdown = converter.convertHtml(html)

        assertTrue(markdown.contains("深层富文本"))
    }
}
