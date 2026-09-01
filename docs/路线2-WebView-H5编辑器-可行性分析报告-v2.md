# 路线 2 可行性分析报告 v2：Compose 内嵌 WebView + H5 富文本编辑器

- 报告日期：2026-09-01
- 评估对象：CorgiMemo 灵感编辑页「内联图片」方案改造
- 版本说明：本报告**取代** `docs/路线2-WebView-TipTap-内联图片-可行性分析报告.md`（v1，09:38）。v1 在两处关键前提上与实际代码不符，已在第 0 节列明修正。

---

## 0. 相对 v1 的修正（请优先看这一节）

v1 的结论是"不建议作为首选"，但其论证建立在两个未经核实的前提上。本轮我逐条读了代码与构建配置，结论有实质变化：

| # | v1 的前提 | 核实结果 | 对结论的影响 |
| --- | --- | --- | --- |
| 1 | 「Android / iOS / 鸿蒙三端各写一套桥接，成本高」 | **不成立（当期）**。灵感编辑页只存在于 Android 原生模块 `app/src/main/java/.../InspirationEditScreen.kt`；`kuikly-shared/shared` 下仅有 `RouterPage` / `TodoDetailPage` / `BasePager` / `CorgiBridgeModule` 四个文件，`grep Inspiration\|RichText\|Editor` 零命中。即 Kuikly 侧**没有**编辑器页面 | 路线 2 的当期改造面从"三端 × 编辑器"缩小为"Android 一个组件"。v1 最重的一条否决理由被拿掉 |
| 2 | 「多轮未解更可能是未定位到真因，原生路线未必不可行」 | **需要反转**。Compose BOM `2026.04.01`（= Compose 1.11）的 `BasicTextField` **至今没有 `inlineContent` 参数**（社区通行做法是 `Text` 叠加 + `VisualTransformation`，光标必然错位，Stack Overflow 上有大量失败案例）。这是**架构性缺失，不是 bug** | 路线 1（继续修覆盖层）的天花板是硬的：编辑态永远拿不到"由排版引擎分配空间的行内图片"。v1 建议的"再诊断一次"价值下降 |

**净效果**：路线 2 的相对吸引力显著上升。v1 给的是"不建议"，本报告修正为**"可行且在有条件下推荐，但必须限定改造边界 + 过 POC 关卡"**。

---

## 1. 结论摘要（TL;DR）

| 维度 | 结论 |
| --- | --- |
| 能否解决内联图片重叠 | **能，且是根治**。H5 编辑器里行内图片是排版引擎的一等公民，不存在"手工算坐标"这回事 |
| 中文输入法（IME）风险 | **中偏高，但可控**。ProseMirror（TipTap 内核）是业界对 Android / CJK 支持最好的引擎之一；风险集中在少数场景，有明确缓解手段 |
| Compose 集成风险 | **中**。嵌套滚动是已知硬伤，但本项目是 `Column + verticalScroll`（非 LazyColumn），且改造后编辑器可独占页面滚动，风险可控 |
| 当期改造面 | **小**。只替换正文编辑器一个组件，标题 / 工具栏 / 画廊 / 语音 / 存储全部留在原生 |
| 多端一致性 | **当期无影响**（见第 0 节）；未来 iOS / 鸿蒙可复用同一份 H5，但需先验证 Kuikly 是否提供 WebView 组件 |
| 迁移成本 | **中**。主要成本在桥接层与序列化，不在编辑器本身 |

**建议排序（更新后）**：

1. **首选：路线 2-minimal —— WebView 只承载"编辑态正文"**。其余一切保持原生。这是本报告推荐方案，详见第 4 节。
2. **备选：路线 4 —— Compose 原生块编辑器**（正文拆成多个段落级 `BasicTextField`，图片作为独立 Composable 块插在段落之间）。完全避开 `inlineContent` 缺失与 WebView 全部风险，代价是图片只能插在**段落之间**、不能插在句子中间。若产品能接受这个约束，它是比路线 2 更稳的答案，详见第 8 节。
3. **不推荐继续投入：路线 1**（继续给覆盖层打补丁）。见第 3 节的架构性原因。

> 一句话决策依据：**如果产品要求"图片能插在句子中间"，选路线 2-minimal；如果只要求"图文交错排列"，选路线 4。**

---

## 2. 现状核实（本轮实测的代码事实）

| 项目 | 实际值 | 位置 |
| --- | --- | --- |
| 页面容器 | `Column + verticalScroll(rememberScrollState())` | `InspirationEditScreen.kt:1248/1256` |
| 标题编辑器 | 独立 `RichTextEditor`（`titleRichTextState`），`headlineMedium` | `InspirationEditScreen.kt:1277/1296` |
| 正文编辑器 | 单个 `RichTextEditor`（`richTextState`），`heightIn(min=200.dp)`，`bodyLarge` | `InspirationEditScreen.kt:1507` |
| 图片载体 | `RichSpanStyle.Image`，编辑态由覆盖层绘制 | `InlineImageOverlay.kt:251 drawInlineImages` |
| 语音载体 | `trigger:voice` Markdown token | 库侧 `TokenInteractionHandlers.kt` |
| 块结构 | `ReorderableColumn` 仍在，但 `items` 恒为空（`contentBlocks` 初始化时清空非 Text 块），`when` 分支只剩兜底 | `InspirationEditScreen.kt:1393/1461-1484` |
| 依赖的原生能力 | 覆盖层绘制、点击预览画廊、`@`/`#` 触发建议弹窗、undo/redo、Markdown 序列化、语音播放 | — |
| 版本基线 | Kotlin 2.4.0 / Compose BOM 2026.04.01（Compose 1.11）/ AGP 9.0 / minSdk 26 / targetSdk 35 / compileSdk 36 | `gradle/libs.versions.toml`、`app/build.gradle.kts` |
| 现有 WebView 使用 | **零**（`grep WebView\|androidx.webkit` 在 `app/src/main` 无命中） | — |
| 跨端范围 | 编辑器在 `app`（Android only）；Kuikly 仅承载 Todo 页 | `settings.gradle.kts`、`kuikly-shared/shared` |

一个容易忽略的事实：**`ReorderableColumn` 的块结构还活着，只是被掏空了**。这意味着路线 4（块编辑器）不是从零起步——拖拽排序、可见性追踪、两步删除的骨架都在，恢复成本比想象中低。

---

## 3. 为什么原生路线反复修不好（根因层）

### 3.1 硬约束：`BasicTextField` 没有 `inlineContent`

Compose 的 `Text` 支持 `inlineContent` + `Placeholder`，占位符由排版引擎分配宽高、参与换行计算。但 `BasicTextField` **不支持**：

- Compose 1.11（BOM `2026.04.01`，即本项目当前版本）的 API 中，`BasicTextField` 只有 `value/onValueChange`、`decorationBox`、`visualTransformation`，**无 `inlineContent`**。
- 社区唯一可用的替代是"隐藏真实文本 + `decorationBox` 里叠一个 `Text(annotatedString, inlineContent = ...)`"，但这条路的 `OffsetMapping` 无法正确处理光标：占位符在原始文本里占 N 个字符，在渲染文本里占 1 个，光标位置必然漂移。Stack Overflow（`79220099`）与多个中文社区的结论一致：**"cursor position doesn't match what it renders"**。
- 只读态的 `BasicRichText` 是 `Text` 的封装，所以**只读态内联图片工作正常**——这也解释了为什么"看详情页没问题、一进编辑就重叠"。

### 3.2 覆盖层方案的结构性矛盾

现有实现（`InlineImageOverlay.kt`）走的是三步绕行：

```
① resolveInlineImagePlacements  组合期解码图片 → 算出显示尺寸
② ApplyInlineImageSizes        尺寸写回 Image span → 重建 annotatedString
                               → 让段落 lineHeight 预留纵向空间
③ drawInlineImages             用 textLayoutResult 反查占位符所在行 → 在文字之上画位图
```

问题在于这三步是**异步时序耦合**的：

- ②③ 之间存在"重建 → 重组 → 再绘制"的循环，任何一帧的 `textLayoutResult` 都可能落后于最新尺寸；
- 段落 `lineHeight` 是**段落级**的，而图片是**字符级**的——同一段落内两张图只能共用同一个 `lineHeight`，只能靠 `max` 兜底；
- 代码里为此加的"同一 line 内多图垂直错开"（`InlineImageOverlay.kt:276-298`）本质是**在绘制层伪造排版结果**：排版引擎认为两张图在同一行，绘制层却把它们上下拉开。此时**文字不会跟着让位**，于是图片压文字、文字压图片。

**结论**：这不是"某个坐标算错了"，而是"覆盖层在跟排版引擎抢布局权"。这类问题可以一个一个打补丁，但每加一张图、每换一种插入顺序都可能冒出新组合，**收敛性没有保证**。过去几轮的修复轨迹（问号方块 → 点击预览 → 多图只插一张 → 行高锁死 → 仍然重叠）已经印证了这一点。

> 顺带说明：v1 文档里的诊断实验（`docs/内联图片重叠-诊断实验操作指引.md`）仍有价值——它能给出"当前重叠的具体量级"，但**它只能量化症状，不能改变 3.1 的硬约束**。如果只是想确认"最新一次修复是否真的生效"，跑一次仍值得；如果已经决定换路线，可以直接跳过。

---

## 4. 路线 2-minimal：方案设计

### 4.1 分层架构

```
┌──────────────────────────────────────────────────────────┐
│ Compose 层（Kotlin，保持原样）                             │
│   标题 RichTextEditor │ 时间戳 │ 工具栏 │ 画廊 │ 语音     │
├──────────────────────────────────────────────────────────┤
│ AndroidView { WebView }  ←── 只替换这一个组件              │
│   ├─ addJavascriptInterface("Android")  JS → 原生          │
│   ├─ evaluateJavascript               原生 → JS           │
│   └─ shouldInterceptRequest            本地图片拦截        │
├──────────────────────────────────────────────────────────┤
│ WebView（系统 Chromium）                                   │
│   └─ assets:///editor.html（离线，无网络依赖）              │
│       └─ TipTap 3.x（ProseMirror）                        │
│           ├─ @tiptap/extension-image  inline: true        │
│           ├─ 自定义 Voice node（atom, inline）             │
│           └─ 自定义 Mention / Hashtag node                │
└──────────────────────────────────────────────────────────┘
```

### 4.2 替换边界（这是"minimal"的关键）

| 能力 | 归属 | 说明 |
| --- | --- | --- |
| 标题编辑 | **原生** | 单段落无内联图片，现有实现无问题 |
| 正文编辑 | **WebView** | 唯一替换点 |
| 正文只读渲染 | **原生** | `BasicRichText` 支持 `inlineContent`，工作正常。笔记 90% 的时间是"看"不是"编"，WebView 只在编辑会话存在 |
| 格式工具栏 | **原生** | 经桥接调用 `editor.chain().focus().toggleBold().run()` |
| 图片选择 / 拍照 | **原生** | 选完把路径经桥接插入，JS 侧用 `aspect-ratio` 先占位 |
| 点击图片预览 | **原生** | JS 回调 `onImageTap(path)` → 现有 `inlineImageViewerPath` 流程不变 |
| 语音录制 / 播放 | **原生** | WebView 内只渲染🎤 标记节点，点击回调原生播放 |
| `@`/`#` 建议弹窗 | **原生** | JS 侧上报触发字符与坐标 → 原生弹窗 → 选中后 JS 插入节点 |
| undo/redo | **TipTap 内建** | 原生工具栏按钮经桥接触发 |
| 存储 | **原生** | 见 4.6 |

**这个边界的意义**：WebView 只负责"文字 + 行内节点"的编辑，所有涉及系统能力、相册、音频、弹窗、存储的都留在原生。一旦某天要回退，只需把这一处 `AndroidView` 换回 `RichTextEditor`。

### 4.3 桥接 API 设计

需要新增依赖：`androidx.webkit:webkit`（用于 `WebViewAssetLoader` 与 `WebMessage`）。

**JS → 原生**（`addJavascriptInterface`，注意 Android 4.2+ 需 `@JavascriptInterface`）

| 方法 | 用途 |
| --- | --- |
| `onContentChanged(json: String)` | 内容变更（防抖 300ms），原生落 ViewModel |
| `onHeightChanged(px: Int)` | `ResizeObserver` 上报内容高度 |
| `onSelectionChanged(state: String)` | 光标处格式状态，驱动原生工具栏高亮 |
| `onImageTap(src: String)` | 点击图片 → 打开原生画廊 |
| `onVoiceTap(id: String)` | 点击语音节点 → 原生播放 |
| `onTrigger(type: String, query: String, rect: String)` | `@`/`#` 触发 → 原生弹窗定位 |
| `onFocusChange(focused: Boolean)` | 用于键盘与滚动协调 |

**原生 → JS**（`evaluateJavascript`）

| 调用 | 用途 |
| --- | --- |
| `setContent(json)` | 初始化 / 外部数据同步 |
| `insertImage(path, w, h)` | 插入行内图片（先给宽高避免跳动） |
| `insertVoice(id, label, duration)` | 插入语音节点 |
| `exec(command, arg)` | 格式命令（bold / italic / heading / undo / redo） |
| `setTheme(dark: Boolean)` | 深浅色同步 |
| `setReadOnly(bool)` | 锁定态 |
| `focusAtEnd()` / `blur()` | 焦点控制 |

> 安全提醒：`addJavascriptInterface` 在加载**任何**外部页面时都是攻击面。本方案只加载 `assets` 本地页 + 拦截本地图片，**不要**用同一个 WebView 打开任意 URL。

### 4.4 高度同步（最容易翻车的一处）

`Column + verticalScroll` 内放 WebView，`wrap_content` 高度常被算成 0（社区高频踩坑）。正确做法：

```
JS: new ResizeObserver(...) → document.body.scrollHeight → Android.onHeightChanged(px)
Kotlin: var contentHeightPx by remember { mutableIntStateOf(0) }
        AndroidView(modifier = Modifier.height(with(LocalDensity.current){ contentHeightPx.toDp() }))
```

配套要点：

- CSS 里 `html, body { height: auto }`（**绝不能** `height: 100%`，否则 `scrollHeight` 恒等于视口高度）；
- 高度回调做 16ms 节流 + 与前值差值 > 1px 才更新，避免抖动引起重组风暴；
- 图片插入前用 `BitmapFactory.Options.inJustDecodeBounds` 取原始宽高，JS 侧以 `aspect-ratio` 先占位，图片真正加载完不再改变高度——**这一步能消掉"插入图片时页面跳一下"的绝大多数观感问题**；
- 编辑器高度超过屏幕时，让 WebView 独占滚动（关掉外层对该区域的滚动），而不是"外层滚 + 内层滚"双套手感。

### 4.5 本地图片加载

图片在 `filesDir` 私有目录，WebView 不能直接 `file://`（跨源限制；放开 `allowUniversalAccessFromFileURLs` 不安全）。推荐 `shouldInterceptRequest` 自建拦截（可控性最高，便于后续加鉴权/缓存）：

```
请求: https://corgimemo.local/img/<relativePath>
拦截: shouldInterceptRequest → 读 filesDir/<relativePath> → 返回 WebResourceResponse(mime, "UTF-8", FileInputStream)
```

备选：`androidx.webkit.WebViewAssetLoader`（映射到 `https://appassets.androidplatform.net/...`），更省事但目录映射不如自建灵活。
不推荐：base64 data URI（多图时内存与序列化体积都爆炸）。

### 4.6 序列化

**建议直接用 ProseMirror JSON 作为落库格式**，而不是现有的 Markdown 反解析。

- 现状：`saveInlineMediaBlocks` 从 Markdown 反解析重建 `content_blocks`——这是"字符串往返"，脆弱且是历史上多个 bug 的源头。
- 改造后：`editor.getJSON()` → 存 `content_blocks` 的 Text 块（一个 JSON 字符串字段）。图片/语音天然是节点，无需反解析。
- 迁移：旧 Markdown 数据在 `hasInitializedWithData` 的 `LaunchedEffect` 里做一次性转换（现有路径已在做类似的事），转换后写回新格式。
- 纯文本搜索 / 字数统计：从 JSON 树抽 `text`，或用 `editor.getText()` 经桥接取。

### 4.7 生命周期与预热

- **预热**：Application 启动时用一个 `MutableContextWrapper` 建一个隐藏 WebView 并加载空白页，进编辑页时换 context 复用，可把首屏从 ~200-400ms 压到 ~50ms 内。
- **销毁**：`DisposableEffect` 里 `stopLoading()` → `loadUrl("about:blank")` → `destroy()`。官方明确警告 WebView 跨进程，未正确销毁会造成**累积性**内存泄漏。
- **状态保存**：编辑内容实时落 ViewModel（已有防抖机制），不依赖 DOM 存活。Activity 重建时从 ViewModel `setContent(json)` 恢复。

---

## 5. H5 编辑器选型：TipTap 是正确选择吗？

| 候选 | 行内图片 | Android / CJK IME | 体量 | 结论 |
| --- | --- | --- | --- | --- |
| **TipTap（ProseMirror）** | ✅ `extension-image` 的 `inline: true`，一等公民 | **最好一档**。ProseMirror 有久经考验的 MutationObserver 协调逻辑；Notion 工程师公开评价"如果必须支持 Android 或 CJK，ProseMirror 是当今最好的选择" | 生产级编辑器 80–250 KB gzip | ✅ **推荐** |
| Lexical（Meta） | ✅ 自定义 DecoratorNode | 中。同样用 MutationObserver 且"看起来对 Android 的处理方式正确"，但 IME 部分需要自己兜的更多，中文场景公开资料少 | 核心 22 KB gzip | 备选 |
| Quill 2 | 有限（Delta 是扁平结构，复杂内联结构吃力） | 好（更依赖原生 contenteditable 行为，自己干预少） | ~57 KB gzip | 不适合：结构化能力不足 |
| Slate | ✅ 灵活 | **差**。Android 是二等公民，主代码路径用 `beforeInput.preventDefault`，在 Android 上不生效 | ~50 KB gzip | ❌ 排除 |
| 直接用 ProseMirror | ✅ | 同 TipTap | 相当 | 可行但 TipTap 封装省掉大量样板 |

**TipTap 关键事实（已核实）**：

- 许可证 **MIT**（`@tiptap/extension-image` 3.x，Snyk / deps.dev 均确认，无直接漏洞记录）；
- 版本活跃：3.27.1 发布于 2026-06-18，近 90 天 30 次提交，GitHub 约 37k star；
- **免费版已包含行内图片能力**，不需要付费 Pro（Pro 主要是协作、评论等云能力）。

一句话：**你选 TipTap 是对的**，它恰好是这个场景下最强的那个。

---

## 6. 风险清单（分级 + 缓解 + 如何验证）

| # | 风险 | 等级 | 缓解措施 | 验证方式 |
| --- | --- | --- | --- | --- |
| R1 | 中文 IME 组合输入异常：Android 拼音退格粒度异常（整音节/部分拼音）、`beforeinput` 与 `input` 选区不一致、`getTargetRanges()` 返回空数组、重复 input 事件导致重字 | **高** | 锁 `prosemirror-view` ≥ 1.33.4（含东亚语言组合修复）；禁止在 composition 期间改动 DOM / 选区；原生侧防抖同步避开 `isComposing` | POC 必测：搜狗 / 百度 / 讯飞 / Gboard，连打、选候选、退格、回车、中途切输入法 |
| R2 | WebView 与 Compose 滚动嵌套（官方明示与 LazyColumn 嵌套"目前无法正常工作"） | **中** | 本项目是 `Column + verticalScroll` 而非 LazyColumn；采用 4.4 的固定高度方案，长文档时 WebView 独占滚动 | POC 必测：20 张图的长笔记滚动手感、与页面其他区域交界处 |
| R3 | 高度同步抖动 / 首帧 0 高度 | **中** | `ResizeObserver` + 节流 + `height:auto`；插入前 `aspect-ratio` 占位 | POC 必测：连续插入 5 张图的跳动情况 |
| R4 | 软键盘遮挡光标 | **中** | `adjustResize` + JS 侧 `visualViewport` 监听 + `scrollIntoView({block:'nearest'})` | POC 必测：光标在文档底部时弹键盘 |
| R5 | WebView 内存泄漏 / 状态丢失 | **中** | 严格 `DisposableEffect` 销毁；内容实时落 ViewModel | 反复进出编辑页 20 次，观察内存曲线 |
| R6 | 系统 WebView 版本碎片化（厂商内核差异） | **中** | 埋点采集 `WebView.getCurrentWebViewPackage()`；minSdk 26 已覆盖绝大多数现代内核 | 灰度期监控，建立机型黑名单 |
| R7 | 首屏延迟 | **低-中** | 全局预热 WebView + H5 资源本地 assets + 骨架屏 | 冷启动 / 热启动分别测 |
| R8 | 无障碍与长按菜单行为与原生不一致 | **低** | `role="textbox"`、`aria-multiline`；长按选择交 WebView 原生 action mode | 手动回归 |
| R9 | 未来 iOS / 鸿蒙复用需各端 WebView 宿主 | **低（当期）** | 当期不影响；若将来需要，先验证 Kuikly 是否提供通用 WebView 组件（目前仅查到小程序端的 `WXWebView`，通用组件待确认） | 立项前确认 |

---

## 7. 工作量粗估（Android 单端，1 人）

| 阶段 | 内容 | 估时 |
| --- | --- | --- |
| P0 POC | H5 编辑器页面 + 最小桥接（setContent / onContentChanged / onHeightChanged / insertImage）+ 中文 IME 与滚动验证 | 2–3 天 |
| P1 功能对齐 | 格式命令、图片选择插入、语音节点、点击回调、`@`/`#` 触发、undo/redo、主题同步 | 5–8 天 |
| P2 数据与迁移 | ProseMirror JSON ↔ `content_blocks`、旧 Markdown 数据迁移、字数统计、搜索 | 3–5 天 |
| P3 打磨 | 预热、内存、无障碍、机型适配、埋点 | 3–5 天 |
| **合计** | | **约 13–21 人日** |

对比参考：路线 4（原生块编辑器）粗估 15–25 人日（要自己处理段落拆分/合并/光标跨块跳转，这部分不轻松）；继续修路线 1 的估时无法给出——**这本身就是不推荐它的理由**。

---

## 8. 路线总对比

| 维度 | 路线 1：继续修覆盖层 | **路线 2-minimal：WebView + TipTap** | 路线 4：原生块编辑器 |
| --- | --- | --- | --- |
| 图片能否插在句子中间 | 理论能，实际重叠 | ✅ 能 | ❌ 只能在段落之间 |
| 解决重叠的确定性 | 低（架构性缺失） | ✅ 高 | ✅ 高 |
| 中文 IME 风险 | 无（原生 TextField） | 中（可缓解） | 无 |
| 滚动 / 高度集成风险 | 无 | 中 | 低 |
| 改动面 | 局部（但无收敛保证） | 一个组件 + 桥接层 | 编辑器交互层重写 |
| 复用现有资产 | 高 | 中（序列化要换） | 高（`ReorderableColumn` 骨架可复活） |
| 跨端前景 | 各端各写 | H5 可复用（待确认 Kuikly WebView） | 各端各写 |
| 长期维护 | 依赖对三方库的深度改造 | 依赖 H5 资产 + 桥接 | 完全自控 |

---

## 9. POC 计划与验收关卡

**范围**：Android 单端，只做正文编辑器，不做语音 / `@`触发 / 主题（留到 P1）。

**验收关卡（任一不过即止损回退，不要带病全量）**：

| # | 关卡 | 通过标准 |
| --- | --- | --- |
| G1 | 中文 IME | 搜狗 / 百度 / 讯飞 / Gboard 四种输入法下：连续输入、选候选词、退格、回车、中途切输入法，**无吞字、无重字、无选区错乱** |
| G2 | 多图布局 | 连续插入 5 张不同宽高比的图，无重叠、无压字；删除其中任一张，其余不跳位 |
| G3 | 滚动与高度 | 20 图长笔记：页面滚动连续、无双套滚动手感、无 0 高度闪白 |
| G4 | 软键盘 | 光标在文档底部时弹键盘，光标可见不被遮挡；收起键盘布局回正 |
| G5 | 图片加载 | `filesDir` 图片经拦截链路正常显示，横竖屏切换不丢 |
| G6 | 内存 | 反复进出编辑页 20 次，内存无持续增长，WebView 正确销毁 |
| G7 | 数据往返 | `setContent` → `getJSON` 往返 100 次内容一致；与现有 `content_blocks` 转换正确 |

**止损条件**：G1 不过 → 立即回退到路线 4，不要再投入。

---

## 10. 我的建议

1. **不要再往覆盖层投时间了**。`BasicTextField` 没有 `inlineContent` 是架构性缺失，继续修是在跟排版引擎抢布局权，收敛性没有保证。这也意味着 v1 报告里"先做一次决定性诊断"的建议可以降级——诊断只能量化症状。
2. **先花半天确认产品需求**：到底要不要"图片插在句子中间"？
   - 要 → 走路线 2-minimal，按第 9 节做 POC，**G1（中文 IME）是一票否决项**。
   - 不要 → 走路线 4，`ReorderableColumn` 的块骨架还在，比从零做省事，且完全绕开 WebView 与 IME 全部风险。
3. **如果走路线 2，务必守住 4.2 的边界**：WebView 只管"文字 + 行内节点"的编辑，系统能力一律留原生。边界一旦守不住（比如把画廊、语音播放也搬进 H5），成本会失控。
4. **落库格式换 ProseMirror JSON**，顺便把现有 Markdown 反解析这个历史 bug 源头一并解决。
5. **POC 只做 Android 单端**。当期 iOS / 鸿蒙没有编辑器页面，不必提前为三端买单。

---

## 附录：证据来源

1. **Compose 版本与 API** — Compose BOM `2026.04.01` = Compose 1.11（Android Developers Blog, "What's new in the Jetpack Compose April '26 release"）。`BasicTextField` 无 `inlineContent`：Stack Overflow #79220099（"Text can use inlineContentMap, but BasicTextField can't"）及社区多个失败案例。
2. **Android 官方 — 在 Compose 中封装 WebView**（嵌套滚动限制、内存泄漏警告）：https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/wrap-webview-in-compose
3. **contenteditable 缺陷追踪站** — 186 scenarios / 312 cases，含 "Backspace granularity during Chinese IME on Android"、"Selection mismatch between beforeinput and input"（Android Chrome）、"getTargetRanges() returns empty array"（Android Chrome）、"Duplicate beforeinput or input events during IME composition"：https://contenteditable.realerror.com/scenarios
4. **TipTap 许可与版本** — `@tiptap/extension-image` 3.x，MIT，无直接漏洞记录，3.27.1 发布于 2026-06-18（deps.dev / Snyk / libraries.io）。
5. **编辑器框架对比与 Android/CJK 评价** — Hacker News 讨论中 Notion 工程师对 ProseMirror / Lexical / Slate 的 Android 与 CJK 支持评价：https://news.ycombinator.com/item?id=31814983
6. **Kuikly 架构与平台支持** — 官方架构介绍 https://kuikly.tds.qq.com/ ；小程序端 `WXWebView` 组件清单 https://kuikly.tds.qq.com/DevGuide/miniapp-wx-components.html
7. **项目内证据** — `InlineImageOverlay.kt:102-109`（明确记录了 BasicTextField 无 inlineContent 的事实与绕行方案）、`InspirationEditScreen.kt:1461-1484`（块结构被掏空的注释）、`docs/内联图片重叠-诊断实验操作指引.md`。
