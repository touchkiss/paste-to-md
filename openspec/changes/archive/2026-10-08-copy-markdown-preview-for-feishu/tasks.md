## 1. 确认预览接入方式

- [x] 1.1 核对本地 IDEA 262 Markdown 插件的浏览器扩展点名称、脚本资源提供方式和 BrowserPipe 前端消息格式，确认无需读取私有 panel 字段。
- [x] 1.2 由用户在 IDEA 沙箱确认纯预览与分栏预览的焦点识别及动作快捷键派发；已实现浏览器按当前 Keymap 转发及重复触发合并。

## 2. 实现预览选区与表格增强

- [x] 2.1 新增 MarkdownPreviewCopyExtension、脚本资源及服务登记，绑定预览生命周期并在销毁时清理订阅。
- [x] 2.2 实现非空选区校验，对预览 DOM 的 table 使用 classList.add('ace-table')，保留已有 class 并支持重复调用及预览重建。
- [x] 2.3 实现 HTML 和纯文本选区序列化，补齐部分表格选区的最小祖先结构，保留混合内容顺序，不增加未选中内容；拒绝不连续 Range。
- [x] 2.4 为请求添加 ID、最新请求校验、3 秒超时及面板/项目/活动预览有效性检查，失效回调不写剪贴板。

## 3. 实现复制动作与剪贴板

- [x] 3.1 新增 HTML 与纯文本 Transferable，使用 CopyPasteManager 写入系统剪贴板，结构化校验输出表格含 ace-table。
- [x] 3.2 新增 CopyMarkdownPreviewForFeishuAction，在 plugin.xml 注册 Find Action 入口及 macOS Control+Command+C 默认快捷键，支持 Keymap 修改。
- [x] 3.3 实现无选区、加载失败、不支持的预览引擎、超时及序列化错误的非模态提示，保留原剪贴板。

## 4. 验证与文档

- [x] 4.1 验证无 class、已有 class、已有 ace-table、多表格及重复调用，确认 class 幂等且其他属性保留。
- [x] 4.2 使用 DOM 夹具验证部分单元格、多行、跨表格与段落、代码块及非表格选区的序列化，确认复制范围准确。
- [x] 4.3 验证 Transferable 的 HTML/纯文本格式及失效请求、超时、无选区时不写入剪贴板。
- [x] 4.4 由用户执行最终版本 ./gradlew test 和 ./gradlew buildPlugin。此前新增功能相关测试和构建通过；完整测试存在缺失 part.html 的既有失败。
- [x] 4.5 由用户在 IDEA 沙箱验证纯预览/分栏、默认和修改后的快捷键、普通复制及源文件不变，检查预览 DOM 与系统剪贴板 HTML 均保留 ace-table；无需验证飞书粘贴类型。
- [x] 4.6 更新 README，说明快捷键、预览选区复制、class 追加及当前 JCEF 支持范围。

## 验收记录

2026-10-08，用户确认“我验证完了，你写的没问题”，剩余用户验证任务据此标记完成。此记录表示用户验收完成，不将此前缺失 `../../../../res/part.html` 的完整测试失败改写为代理实测通过。
