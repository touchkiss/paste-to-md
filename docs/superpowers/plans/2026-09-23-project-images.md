# 项目级图片处理 Implementation Plan

**Goal:** 项目独立配置图片保留、本地下载和 Base64。
**Architecture:** 独立路径/存储/下载模块，设置页与后台粘贴调用该模块。
**Tech Stack:** Kotlin/JVM 17、IntelliJ 2024.1、Jackson。

## 实施顺序
- [x] 图片存储：路径解析、SHA-256、目录锁、原子 JSON、递归重建；测试内容去重和重建。
- [x] 图片处理：识别图片目标、并发 HTTP 下载、Base64、失败保留；测试链接替换与并发。
- [x] 项目设置：projectService/projectConfigurable，三个 radio、目录输入、后台重建。
- [x] 粘贴动作：立即捕获剪贴板和编辑器位置，后台处理，检查文档版本后单个写命令插入。
- [x] 更新 README，执行 ./gradlew test buildPlugin，记录实际验证结果。

## 验证结果

图片、粘贴、剪贴板及通用转换回归共 54 项通过，buildPlugin 成功。首次全量运行中的既有飞书测试因仓库缺少 part.html 失败；本次回归未包含该测试类。未执行 IDE 设置页和撤销的手工 UI 验证。
