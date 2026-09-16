# blocknote-probe：BlockNote 自定义 API 探针实验

> 目的：验证 BlockNote v0.52.1 两条自定义 API 路径在本项目场景下的可用性，为「WebView 接入 BlockNote」的 POC 决策提供前置证据。
> 关联：`docs/BlockNote与灵感编辑器适配度调研.md`（建议的半天级探针）。

## 两个探针

| # | 探针 | 验证的 API | 对应本项目能力 |
|---|---|---|---|
| 1 | 三样式分割线自定义块（solid/dashed/wavy） | `createReactBlockSpec`：propSchema / render / toExternalHTML / parse | 分割线三样式（现为五按钮工具条 + 三样式渲染原语） |
| 2 | 字号行内样式 | `createReactStyleSpec`（Custom Styles） | 字号/颜色面板（现为 FontSizeColorPanel） |

## 判读标准

- **PASS**：文档 JSON 结构正确、`tryParseHTMLToBlocks` 从 `<hr data-divider-style>` 正确解析回样式、fontSize 进文档 JSON 并渲染到 DOM、schema 注册清单含两个自定义 spec。
- **INFO**：markdown 导出/回读——`tryBlocksToMarkdownLossy` 对自定义块的输出与样式保留情况（lossy 预期，记录实际行为供桥接层设计参考）。
- **FAIL**：任一 API 签名/行为与文档不符——对应 🟡 项成本上修。

## 运行

```bash
npm install
npm run typecheck   # tsc --noEmit（API 签名验证）
npm run build
npm run preview     # http://localhost:4173
```

页面自动运行 7 项断言并展示 PASS/FAIL/INFO；也可在编辑器里手动操作后点「重新自检」。
