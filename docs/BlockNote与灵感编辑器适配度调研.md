# BlockNote 与本项目灵感编辑器适配度调研

> 调研日期：2026-09-16
> 关联文档：`灵感编辑页-WebView接入editorjs-评估报告.md`、`灵感编辑页-WebView多块选择专项调研.md`（其结论：若走 WebView 且多块选择是刚需，POC 对象应换成 BlockNote）
> 调研目标：逐项评估 BlockNote 与本项目灵感编辑器（`BodyBlocksEditor` 体系）的功能适配度
> 结论先行：**适配度显著高于 editor.js**——本项目 15 项核心能力中 7 项原生/架构性消解、6 项需配置或轻量自研、2 项存在模型级冲突（行级任务、双层撤销打包）。但 WebView 公共成本与双技术栈成本不变，POC 决策逻辑不变。

---

## 1. 结论摘要（TL;DR）

| 维度 | 结论 |
|---|---|
| 总体适配度 | **高**（15 项能力：7 🟢 / 6 🟡 / 2 🔴）。对比 editor.js（3 🟢 / 4 🟡 / 8 🔴），缺口从「8 项深度自研」收敛为「2 项模型冲突」 |
| 最大的意外利好 | ① **divider 块内置**（`---`+回车 input rule 与本项目 GFM 语法天然一致）；② **markdown 导入导出是官方 API**（editor.js 完全没有），常用块全支持往返 |
| 最硬的缺口 | ① TaskList「单段多行任务」模型与 BlockNote「一项一块」模型冲突；② 撤销无「块级 Command 打包合并」可编程性（本项目拖拽打包一步撤销无法等价实现） |
| 代价变化 | React 技术栈（@blocknote/react），WebView 内 bundle 与内存比 editor.js 更大约 1.5–2 倍；undo/redo 必须全留 JS 侧 |
| 建议 | 适配度不构成否决项，也不构成放行项——**是否迁移仍由 5 点 POC 决定**，但 POC 的自研项清单可以从本报告矩阵直接生成 |

---

## 2. BlockNote 基本面（2026-09）

| 项目 | 现状 |
|---|---|
| 版本 | v0.52.1（2026-07-20），活跃维护，版本节奏快（0.x，大迁移时有 breaking，如 0.52 解耦 Yjs、迁移 Vite+） |
| 架构 | ProseMirror + Tiptap 内核，**React 组件库**（@blocknote/react + mantine/shadcn/ariakit 皮肤）；单 contenteditable 单文档模型，块 UI 是内核节点的样式壳 |
| 许可 | 主体 **MPL-2.0**（商用闭源可用；但修改 BlockNote 源文件需公开该文件——尽量用 extension/组件替换，避免 fork）。⚠️ `@blocknote/xl-*`（多栏、AI 等）为 GPL-3.0 或商业许可，本项目不要引入 |
| 数据模型 | `Block = { id, type, props, content, children }`——与本项目 `BodyBlock(id + 子类属性)` **同构**（含稳定块 id、属性表、可嵌套 children） |
| 格式转换 | **官方 HTML/Markdown 转换 API**（`blocksToMarkdownLossy` / `tryParseMarkdownToBlocks`）；Markdown 模式下 Paragraph/Heading/Quote/双列表/Checklist/Code/Table/**Divider**/Image 全部往返支持（Toggle 类除外） |
| 协作 | 内置 Yjs 协作（0.52 起非协作模式已解耦 yjs，减小体积） |

**内置块清单**：paragraph、heading（1–6，可配置 levels/isToggleable）、quote、bulletListItem、numberedListItem、checkListItem、toggleListItem、codeBlock、table、image、video、audio、file、**divider**、pageBreak。
**内置行内样式**：bold、italic、underline、strikethrough、textColor、backgroundColor（**无 fontSize**）。
**Input rules**：`-`+空格 → 无序列表；`1.`+空格 → 有序列表；`#`/`##`/`###`+空格 → 标题；`[ ]`/`[x]`+空格 → 任务项；**`---`+回车 → 分割线**。

---

## 3. 适配度矩阵（核心）

判定分级：🟢 原生/架构性消解 ｜ 🟡 需配置或轻量自研（有官方路径）｜ 🔴 无对应或模型级冲突。

### 3.1 文本与行内

| # | 本项目能力 | BlockNote 对应 | 判定 | 说明 |
|---|---|---|---|---|
| 1 | 富文本段落 + 加粗/斜体/删除线等 | paragraph + 6 种内置 inline styles | 🟢 | 覆盖面比 editor.js 内置全（含下划线、颜色） |
| 2 | 字号/颜色面板 | textColor/backgroundColor 内置；fontSize 无 | 🟡 | 字号走官方 Custom Styles API 自定义 inline style；颜色直接用 |
| 3 | 复选框块（单行） | checkListItem（`checked` prop + `[ ]` input rule） | 🟢 | 语义一致；勾选态为块 prop，进文档模型 |

### 3.2 块类型

| # | 本项目能力 | BlockNote 对应 | 判定 | 说明 |
|---|---|---|---|---|
| 4 | **TaskList 行级渲染**（单段多行任务、逐行勾选、行级对账） | 无对应：checkListItem 是**一项一块**模型 | 🔴 | 模型级冲突，见 §4.1。可行出路：① 迁移时改用「一行一块」模型（行为变化：增删行=增删块，不再受现有行级方案约束）；② 自定义块复刻行级模型（成本≈把方案 D 用 JS 再写一遍） |
| 5 | 缩进 1..6（布局级 + 列表） | 列表/可嵌套块原生缩进（children 嵌套 + Tab/Shift+Tab）；**普通段落缩进无** | 🟡 | 列表缩进原生；段落缩进自定义 prop + render 层 padding（与本项目「布局级缩进」思路一致） |
| 6 | 图片块（撑满 + 真实比例 + 工具栏 + 高亮删除） | image 块（url/caption/previewWidth）+ `uploadFile` 回调 | 🟡 | 真实比例浏览器天然保持；「撑满宽度」用 CSS 覆盖 `.bn-visual-media { width:100% }`；选中工具栏基于其可替换 UI 组件重做（见 #12）；本地文件经 bridge 传 blob 走 uploadFile |
| 7 | **分割线三样式**（实线/虚线/波浪） | **divider 内置**（实线；`---`+回车 input rule 与本项目 GFM 语法一致） | 🟡 | 实线零成本；虚线/波浪用 `createReactBlockSpec` 自定义块（`props.style: solid\|dashed\|wavy`，render 画 SVG/Border），markdown 编码沿用本项目 `"--- dashed"` 约定 |
| 8 | 载体空块不变量（懒插入/边缘 tap/身份绑定） | 无此概念（块间插入/斜杠菜单天然存在） | 🟢 | **整体丢弃**（迁移减负项）；仅存量 markdown 中 `EMPTY_BLOCK_PLACEHOLDER` 的清洗映射 |

### 3.3 编辑行为

| # | 本项目能力 | BlockNote 对应 | 判定 | 说明 |
|---|---|---|---|---|
| 9 | 块级拖拽重排（自维护 fork 滑行手感） | 内置侧边块手柄 drag & drop（Notion 式） | 🟢 | 原生；滑行动画手感与自维护 fork 有差距，可接受或后续调 CSS/实现 |
| 10 | **双层撤销**（块级 Command 栈 + 块内 history、拖拽打包 CompositeCommand 一步撤销） | 内置 `editor.undo()/redo()`（协作模式为 Yjs UndoManager 事务级） | 🟡→🔴 | undo 本身可靠且内置；**但不可编程「打包合并」**——「图片拖入拆块 + 载体补插」合成一步撤销的能力无法等价实现，只能靠 Yjs origin/`captureTimeout` 近似。对本项目是精度降级，见 §4.2 |
| 11 | 跨帧焦点迁移 / 非文本块点选焦点保护 | **架构性消解**：单文档模型光标由 ProseMirror 统一管理，点非文本块不再发生「焦点被夺、软键盘消失」问题 | 🟢 | 本项目两大焦点铁律（焦点留 Text 块、跨帧 pendingFocus）在 BlockNote 中不再需要——迁移减负项 |

### 3.4 数据与集成

| # | 本项目能力 | BlockNote 对应 | 判定 | 说明 |
|---|---|---|---|---|
| 12 | markdown（GFM）序列化往返（含勾选/缩进/分割线样式编码） | **官方 API** `blocksToMarkdownLossy` / `tryParseMarkdownToBlocks`：Paragraph/Heading/Quote/双列表/Checklist/Code/Table/Divider/Image 全部 Yes | 🟢 | 相对 editor.js（零官方转换）是质变；本项目扩展编码需补：分割线样式 `"--- dashed"`（自定义块 parse/toExternalHTML）、载体空块清洗、任务行↔块模型映射（取决于 #4 的出路） |
| 13 | 阅读卡复用渲染（编辑页/阅读卡共享原语） | `toExternalHTML` / 只读模式（editable=false + 组件替换隐藏编辑 UI）；或 React 静态渲染 | 🟡 | 若阅读卡保持 Compose：需维护「Blocks JSON→Compose」渲染器（等于重写 InspirationViewCard 的取数逻辑）；若阅读卡也走 WebView：与编辑页共享同一渲染，双端一致性最好 |
| 14 | 字体体系（9 OFL 中文 + 3 拉丁、chrome/内容解耦） | customCss `@font-face` 注入（先例：flutter_blocknote_editor 的 customCssAssetPaths）；内置 Inter 可剔除 | 🟡 | 可行；9 款中文字体全部打包进 WebView 资产（+10MB 级 APK 体积）或首用异步注入，字体切换走 CSS 变量 |
| 15 | **移动端跨块选择**（本次调研的主目标） | **原生**：单文档选区，长按拖拽手柄跨块 | 🟢 | editor.js #2908 的直接解；Android 端 open bug 仅 #2035（退格建换行，见 §5） |

**统计：🟢 7 项 ｜ 🟡 6 项 ｜ 🔴 2 项。**

---

## 4. 两个模型级冲突深析（🔴 项）

### 4.1 TaskList：单段多行 vs 一项一块

- 本项目：TaskList 段落 = 单段落 + 段内 `\n`（方案 D），行级勾选（`checkedLines`）、行级渲染（库层按行画框）、行级命中——这是为 Compose `BasicTextField` 生态定制的模型。
- BlockNote：`checkListItem` 一项一块，勾选是块 prop；增删行 = 增删块，天然走块级 undo、块级拖拽。
- **评估**：迁移时**建议顺势改为「一行一块」**——它更符合生态通用语义（Notion/BlockNote/编辑器社区惯例），且能白拿块级 undo/拖拽/跨块选择；代价是接受与现行 Compose 版的行为差异（如行级样式延续、空行处理）。若产品坚持单段多行的视觉行为，需要自定义块 + 自行实现行级勾选——成本回到自研，**此时迁移 BlockNote 对该功能没有净收益**。

### 4.2 撤销：事务级 undo vs 可编程 Command 栈

- 本项目：`ReplaceBlocksCommand` 区间替换命令统一拆块/合并/插图/粘贴归一化，拖拽打包 `CompositeCommand` 一步撤销；块内走库内 history。
- BlockNote：`editor.undo()/redo()`，底层按事务记录（协作模式 Yjs UndoManager，含 origin 标记与 captureTimeout 合并窗口）。**没有公开的「把任意一组操作合并为一个 undo 单元」的编程接口**。
- **评估**：常规打字/删字/格式操作无损；块级复合操作（插图拆块+载体补插、拖拽重排+多块增删）可能退化为多步撤销。缓解手段：桥接层把原生侧触发的批量操作走 `editor.transact()` 类单事务入口 + Yjs origin 合并——**这是 POC 第 4 项要实测的核心**。

---

## 5. Android WebView 嵌入适配（沿用专项调研结论 + BlockNote 特有项）

| 项 | 内容 | 状态 |
|---|---|---|
| 冷启动 | React + BlockNote bundle 比 editor.js 大约 1.5–2 倍（editor.js 743KB 安装包）；**必须用预热池**（先例：flutter_blocknote_editor HeadlessInAppWebView 预载） | 🟡 有先例可抄 |
| CORS 新坑 | Chromium 148+ 对 `file://` 收紧 CORS（origin=null，模块/CSS/字体被拦）——走本地 HTTP 或 `allowFileAccessFromFileURLs` + 私有目录资产 | 🟡 有解法 |
| Android 已知 bug | **#2035：退格在某些 Android 浏览器/WebView 创建换行**（open）；iOS 双菜单 #2122（对本项目 Android 优先不构成阻塞） | 🟡 POC 验证 |
| undo 单向流 | 原生侧永不回灌编辑事务，undo/redo 全留 JS 侧——Kotlin 侧只收「内容快照/变更通知」 | 🔴 架构约束（硬接受） |
| 桥接 | React 实例跑在 WebView 内，Kotlin↔JS 单向数据流 + 变更 debounce 同步；块模型 JSON 直接过桥 | 🟡 成熟模式 |
| 主题/字体 | dark mode 内置（colorScheme）；customCss 注入字体与本项目设计 token | 🟢 |

---

## 6. 综合评分与建议

### 6.1 与 editor.js 的适配度对比

| | editor.js | BlockNote |
|---|---|---|
| 功能适配（15 项） | 3 🟢 / 4 🟡 / 8 🔴 | **7 🟢 / 6 🟡 / 2 🔴** |
| markdown 往返 | 无官方转换器，全自研 | **官方 API，常用块全支持** |
| 移动端跨块选择 | ❌ 架构性无解 | ✅ 原生 |
| 撤销可编程打包 | ❌（第三方 beta 插件） | 🟡 内置但不可打包 |
| WebView 内体积/内存 | 较小 | 较大（React 栈，约 1.5–2 倍） |
| 自定义块生态 | 插件体系（TS） | React 组件体系（propSchema/render/parse 完整，文档好） |

### 6.2 建议

1. **适配度不构成否决，也不构成放行**：BlockNote 把「自研工作量」从 editor.js 的 8 项压缩到 2 项（且 2 项都有明确出路——行级任务可顺势改模型、撤销可单事务近似），但 WebView 公共成本（键盘/IME/CORS/体积/双栈维护）一项没少。
2. **POC 决策逻辑不变，清单可落地**：上一轮 5 点 POC 清单直接有效；POC 中的自研项按本报告矩阵生成——自定义分割线块（#7）与字号 custom style（#2）可作为「自定义 API 路径是否顺畅」的探针，半天即可验证。
3. **若 POC 通过**，迁移设计上优先做两个「顺势减负」决策：载体空块体系整体废弃（#8）、TaskList 改一项一块（#4）——两者都能显著降低 Kotlin 侧桥接与序列化的复杂度。
4. **许可红线**：只用 MPL-2.0 主包，不引入 `@blocknote/xl-*`；不做 BlockNote 源码 fork（用组件替换/extension），避免 MPL 文件级开源义务。

---

## 7. 附录：数据来源

- BlockNote 官方文档：Built-in Blocks（block-types，含 divider/input rules/DefaultProps）、Custom Block Types（createReactBlockSpec：propSchema/render/toExternalHTML/parse/meta）、Document Structure（Block 模型）。
- `@blocknote/xl-multi-column` npm 页（yarn classic）：v0.52.1（2026-07-20）、MPL-2.0 + XL GPL-3.0/PROPRIETARY、0.52.0 changelog（Yjs 解耦 withCollaboration、Vite+ 迁移、plain block content）。
- DeepWiki（TypeCellOS/BlockNote）：Block System（ProseMirror blockGroup/blockContainer 结构、DOM class 体系）、Input Handling（input rules 全表、`---`→divider）、Collaboration Plugins（UndoPlugin = Yjs UndoManager、editor.undo()/redo()）。
- Cubitt（基于 BlockNote 的文档系统）Content Formats：markdown 往返支持矩阵（Paragraph/Heading/Quote/列表/Checklist/Code/Table/Divider/Image = Yes；Toggle = No）。
- Wener 笔记（BlockNote 模型速览：Block/InlineContent/Styles 类型、image props、组件可替换 ComponentsContext、不支持粘贴图片 issue #693）。
- flutter_blocknote_editor 0.0.20–0.0.24 changelog：Chromium 148+ file:// CORS、预热池、undo 单向流、customCss 字体注入。
- BlockNote issues：#2035（Android 退格建换行）、#2122（iOS 选择双菜单）、#1922（getSelectionCutBlocks）、#693（粘贴图片限制）。
- 本项目现状：`BodyBlocksEditor.kt`（4,903 行）块模型/命令栈/载体块/分割线工具条；工作记忆 TaskList 行级渲染（2026-09-16 定稿）、字体体系、焦点铁律、自维护 fork（compose-rich-editor / BlocksReorderableList）。
