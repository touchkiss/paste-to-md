# 2. 商品体系全景调研

## 1. 商品模型

      ### 1. **概念介绍**

| <strong>SPU</strong><br/>Standard Product Unit 标准化产品单元 | <strong>SKU</strong><br/>Stock Keeping Unit 库存量单位 |
| --- | --- |
| SPU 是一组共享相同核心属性的商品集合，代表一个"产品概念"。用户在商品详情页看到的就是一个 SPU。<br/>- 描述的是"<strong>是什么产品</strong>"<br/>- 例：iPhone 15 Pro Max、Nike Air Max 270<br/>- 包含：品牌、型号、产地、详情描述、主图<br/>- <strong>不直接参与交易和库存管理</strong> | SKU 是 SPU 下由销售属性组合确定的最小库存/销售单元，代表一个"可购买的实体"。<br/>- 描述的是"<strong>买哪一个</strong>"<br/>- 例：iPhone 15 Pro Max 256GB 原色钛金属<br/>- 包含：价格、库存、条形码、重量<br/>- <strong>直接参与交易、库存扣减、物流发货</strong> |


## 为什么做这个插件

JetBrains 自带的 Markdown 编辑器已经很好用了，但有一个痛点一直很真实：

- 从网页复制内容，粘进来通常不是 Markdown
- 从 Word 复制内容，格式经常要手工重排
- 从飞书文档复制内容，看起来像 HTML，实际上背后还是一套半结构化文档系统
- 从预览态 Markdown 页面复制内容，粘回来经常失去“可继续编辑”的结构

这个插件的目标很直接：

- 使用 `Shift + Cmd/Ctrl + V` 触发智能粘贴转换
- 仅在 Markdown 文件中生效
- 尽量保留结构语义，而不是保留视觉噪音
- 转换失败时安静降级，不打断输入流


