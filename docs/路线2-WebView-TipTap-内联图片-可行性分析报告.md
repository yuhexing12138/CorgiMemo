# 路线 2 可行性分析报告：Compose 内嵌 WebView + TipTap 实现内联图片

- 报告日期：2026-09-01
- 评估对象：CorgiMemo 灵感编辑页「内联图片」方案改造
- 项目基线：Kotlin Multiplatform + Kuikly（androidApp / iosApp / ohosApp 三端）、App 模块 `minSdk 26 / targetSdk 35 / compileSdk 36`、Android 侧为 Jetpack Compose

---

## 一、结论摘要（TL;DR）

**路线 2 技术上可行，但对本项目的性价比偏低，不建议作为解决「内联图片重叠」的首选方案。**

| 维度 | 结论 |
| --- | --- |
| 富文本与内联图片能力 | TipTap 显著强于当前 compose-rich-editor，是真正的行内（inline）节点模型 |
| 解决当前重叠 Bug | 能解决，但**属于"用架构换 bug"**：为 1 个渲染缺陷引入 3 端 × N 个新风险 |
| 中文输入法（IME）风险 | **高**。contenteditable 在移动端的 IME/选区是行业公认深水区，中文场景尤其 |
| Compose 集成风险 | **高**。Android 官方明示 WebView 与 Compose 滚动容器嵌套"目前无法正常工作" |
| 多端一致性 | **低**。Android / iOS / 鸿蒙三端需各写一套桥接，且 iOS 恰是 TipTap 缺陷重灾区 |
| 迁移成本 | **高**。编辑器交互层整体重写，现有原生能力需跨桥重建 |

**建议排序**：

1. **首选：路线 3（块级图片，Compose 原生）** —— 回到项目 v2026-08-30 之前已验证的 `content_blocks` 结构，图片不进富文本。风险最低、工作量最小，且与现有 `SwipeableImageStack` 展示体系天然契合。
2. **次选：路线 1（继续修 compose-rich-editor 覆盖层）** —— 先做一次**决定性诊断实验**（见第七节），判断根因是否真的不可解。目前"多轮未解"更可能是**未定位到真因**，而非原生路线不可行。
3. **谨慎评估：路线 2（WebView + TipTap）** —— 若产品确实需要"图文自由混排"（如图片插在句子中间），且能接受三端投入，则先做 POC 过关卡（见第八节）再决策。

---

## 二、现状与问题澄清

### 2.1 已知事实

- 项目在 v2026-08-30 将图片从「块结构」改为「内联方案」（`compose-rich-editor` 改造 + `InlineImageOverlay.kt` 覆盖层绘制）。
- 之后经历多轮修复，依次解决了：问号方块、点击预览、语音标识、voice token 唯一化、多图只插入一张、空文档插入失败、`insertParagraphs` 空文档 Bug。
- 8-31 定位到「段落行高被 textStyle 锁死」这一根因并修复（`a594eb5` / `fef00122`），**但你实测后重叠依旧**。

### 2.2 必须澄清的一点

> **"多轮未解" ≠ "原生路线不可行"。**

上一轮修复是基于 Compose 排版源码的静态分析（`AnnotatedString.normalizedParagraphStyles` 中 `defaultParagraphStyle.merge(range.item)` 的优先级），逻辑链本身是成立的，但**尚未被真机数据证实或证伪**。在换架构之前，值得先花 30 分钟做一次决定性诊断（第七节），这能把"原生到底行不行"从猜测变成结论。

### 2.3 现有编辑器结构（影响方案评估的关键事实）

| 项目 | 现状 |
| --- | --- |
| 页面容器 | `Column + verticalScroll(rememberScrollState())`（`InspirationEditScreen.kt:1248/1256`） |
| 编辑器组件 | material3 `RichTextEditor`，`textStyle = bodyLarge`（自带 `lineHeight = 24.sp`） |
| 数据结构 | `content_blocks` 表（Text / Image / Voice），语音以 `trigger:voice` token 内联 |
| 图片存储 | `filesDir/` 私有目录 + 相册选择 |
| 依赖的原生能力 | 覆盖层绘制、点击预览画廊、`@` 触发建议弹窗、hashtag trigger、undo/redo、Markdown 序列化、语音 token 播放 |
| 多端目标 | Android / iOS / 鸿蒙（Kuikly KMP） |

---

## 三、方案描述（路线 2）

```
┌─────────────────────────────────────────────────┐
│  Compose 层（Kotlin）                            │
│  AndroidView { WebView }                         │
│   ├─ addJavascriptInterface  → JS 调用原生        │
│   ├─ evaluateJavascript      → 原生调用 JS        │
│   └─ 高度回调 / 图片选择 / 语音录制 / 主题同步      │
├─────────────────────────────────────────────────┤
│  WebView（Chromium）                             │
│   └─ 本地 H5（assets，WebViewAssetLoader 托管）   │
│       └─ TipTap（ProseMirror）                   │
│           ├─ Image 节点 inline: true（行内图片）   │
│           ├─ 自定义 Voice 节点（🎤 语音标记）       │
│           └─ 自定义 Mention / Hashtag 节点        │
└─────────────────────────────────────────────────┘
```

数据流向：编辑器内容以 **JSON（ProseMirror doc）或 HTML** 序列化，经桥接传回 Kotlin，再落到现有 `content_blocks` / Markdown 存储。

---

## 四、逐维度可行性评估

### 4.1 富文本与内联图片能力：★★★★★（强）

TipTap 基于 ProseMirror，节点模型中 `inline: true` 的行内图片是**一等公民**：图片与文字同段落混排、选区、删除、撤销都是引擎内建能力，不存在"覆盖层手工算坐标"的问题。**这是路线 2 相对当前方案的最大优势**——当前的重叠本质上就是"手工覆盖层 vs 排版引擎"的对抗。

### 4.2 中文输入法（IME）与光标选区：★★☆☆☆（高风险）

这是本方案**最需要警惕**的维度。证据：

- **contenteditable 缺陷追踪站**（contenteditable.realerror.com）收录了 186 个场景 / 312 个案例，其中大量集中在移动端 IME 组合输入，典型包括：
  - Android + 中文 IME：Backspace 删除粒度异常（整音节 / 部分拼音 / 组合边界错乱）
  - Android Chrome：`beforeinput` 的 `getTargetRanges()` 返回空数组，选区在 `beforeinput` 与 `input` 之间不一致
  - iOS Safari：`compositionstart` / `compositionupdate` 可能不触发，导致编辑器状态不同步
  - 重复 input/beforeinput 事件 → 重复插入字符
- **TipTap 已知 Issue**：iOS Safari / Chrome 下使用日语（及中文、韩语、泰语）输入时，出现整段文本被持续选中、预测文本选择后吞字、回车替换而非换行的缺陷（与 ProseMirror-view 版本相关，需 ≥ 1.33.4）；另有 iOS 选区层 z-index 覆盖原生 UI 的问题（Issue #6276）。
- 中文技术社区普遍反馈：Android 定制系统（华为 / 小米 / OPPO）WebView 上，切换输入法后 selection 错乱、动态更新数据后聚焦失效。

> **对本项目的意义**：CorgiMemo 是中文笔记 App，中文 IME 是**最高频路径**（不是边缘 case）。这些问题一旦命中，影响面远大于当前的图片重叠。而 **iOS 端恰好是 TipTap 缺陷最集中**的平台。

### 4.3 图片粘贴与相册选择：★★★★☆

TipTap 对粘贴 HTML / 图片有成熟处理（Paste 规则、`handlePaste`）。相册选择需原生侧完成后把路径/URI 经桥接传给 JS 插入节点——可控，但同步时机（软键盘弹起时插入）需谨慎处理。

### 4.4 本地图片加载：★★★★☆（有成熟方案，但增加复杂度）

图片在 `filesDir` 私有目录，WebView 不能直接 `file://` 加载（Android 7+ 跨源限制，放开 `allowUniversalAccessFromFileURLs` 不安全）。正确做法：

- `androidx.webkit.WebViewAssetLoader`：把本地文件映射为 `https://appassets.androidplatform.net/...` 虚拟源，安全且无 file:// 限制；
- 或自定义 `shouldInterceptRequest` 返回 `WebResourceResponse`（可控性最高，推荐此路径，便于做鉴权与缓存）；
- 或 base64 data URI（图片多时内存开销大，不推荐）。

**额外代价**：图片异步加载 → 内容高度持续变化 → 必须用 `ResizeObserver` 持续回调高度给 Compose（见 4.5）。

### 4.5 Compose 集成（滚动 / 高度 / 焦点）：★★☆☆☆（高风险）

- **官方明确警示**：Android 开发者文档（"Wrap a WebView in Compose"）写道——
  > "Nested scrolling is not easily supported when using WebView in Compose. When placing a WebView inside a scrollable Compose container, such as a LazyColumn, the WebView may consume all scroll gestures... **nesting it with LazyColumn does not currently work properly.**"

  当前编辑页正是 `Column + verticalScroll`，虽比 LazyColumn 情况略好，但仍需处理手势归属。
- **高度问题**：WebView 内容高度对 Compose 不可知，`wrap_content` 常算出 0（社区高频踩坑，html/body `height:100%` 时尤其），必须 JS 侧测量 → 桥接回调 → 以固定 px 高度设置，或让 WebView 固定高度内部滚动（体验割裂：页面滚动与编辑器内滚动两套手感）。
- **生命周期与内存**：官方警告 WebView 跨进程运行，离开组合时未正确 destroy 会造成累积性的 Activity 与 native 内存泄漏；Activity 重建时 DOM 状态丢失，需自行做状态保存/恢复。
- **软键盘**：`windowSoftInputMode`、键盘弹起时 `resize` 与视口变化，需 `adjustResize` + JS 侧 `visualViewport` 配合，否则光标被键盘遮挡。

### 4.6 性能与内存：★★★☆☆

- WebView 实例本身开销较大（额外渲染进程 + 数十 MB 级内存）；
- 首屏：WebView 初始化 + JS bundle 解析（TipTap + ProseMirror 数百 KB ~ MB 级）带来可感知延迟，编辑器首帧明显慢于原生；
- 长文档：需自行做虚拟化/增量渲染优化，否则长笔记滚动掉帧。

### 4.7 包体积：★★★★☆

H5 资源（TipTap/ProseMirror 压缩后约数百 KB）打进 assets，对 APK 体积影响可接受。

### 4.8 多端一致性：★☆☆☆☆（最弱项）

项目是 KMP + Kuikly 三端，路线 2 意味着：

| 端 | 需要的工作 | 风险 |
| --- | --- | --- |
| Android | `AndroidView + WebView` + JSBridge | 中（有成熟路径） |
| iOS | `WKWebView` + `WKScriptMessageHandler` | **高**（TipTap iOS IME 缺陷集中；WKWebView 输入体验差异需单独打磨） |
| 鸿蒙 OHOS | ArkWeb 组件 + 自有桥接机制 | **很高**（ArkWeb API 与 Android WebView 不同，Kuikly 是否提供跨端 WebView 组件需另行验证） |

即**同一套编辑体验要维护三套宿主集成**，且三端 WebView 内核行为不一致——这恰恰是"H5 编辑器跨平台"承诺的反面。

### 4.9 调试与线上问题定位：★★☆☆☆

- JS 侧问题需 `chrome://inspect` 远程调试，无法像 Kotlin 一样断点；
- WebView 渲染进程崩溃独立于 App 进程，需额外监听与恢复；
- 系统 WebView 版本碎片化（不同厂商/系统版本内核差异），线上问题难复现。

### 4.10 迁移成本：★☆☆☆☆

需重做或跨桥重建的能力清单：

1. 正文富文本编辑（标题/正文两个编辑器）
2. 图片内联插入与删除（含相册选择、粘贴）
3. 🎤 语音 token 插入、播放、时长展示
4. `@` 提及触发建议弹窗、hashtag trigger（触发逻辑需移到 JS 或跨桥同步）
5. 点击图片 → 沉浸式画廊（需 JS → Kotlin 回调坐标与路径）
6. undo/redo 与用户现有习惯保持一致
7. Markdown / HTML 序列化与 `content_blocks` 的双向转换（现有 `saveInlineMediaBlocks` 反解析逻辑要重写）
8. 主题（深浅色）、字号、行距与原生一致
9. 锁定态（readOnly）、字数统计、时间戳

---

## 五、关键风险清单（按严重度排序）

| # | 风险 | 影响 | 概率 | 缓解措施 |
| --- | --- | --- | --- | --- |
| R1 | 中文 IME 组合输入异常（吞字、选区错乱、回车替换） | 核心输入体验受损 | **高** | 锁定 ProseMirror-view ≥ 1.33.4；POC 阶段用真机 + 主流输入法（搜狗/百度/讯飞/Gboard）专项测试；必要时自定义 IME 处理 |
| R2 | WebView 与 Compose 滚动容器嵌套 | 页面滚动手感割裂或失效 | **高** | POC 必测；方案：JS 测量高度 + 固定高度 WebView，或改为 WebView 全屏内部滚动 |
| R3 | 三端桥接各写一套 | 工作量 ×3，体验不一致 | **高** | 先只做 Android 单端验证；确认 iOS/鸿蒙 WebView 组件能力后再扩展 |
| R4 | WebView 内存泄漏 / 状态丢失 | OOM、编辑内容丢失 | 中 | 严格 `onRelease` 销毁；`saveState/restoreState`；编辑内容实时落库而非依赖 DOM |
| R5 | 首屏延迟 | 打开编辑页可感知卡顿 | 中 | WebView 预热（全局单例复用）、H5 资源本地化、骨架屏 |
| R6 | 图片高度变化引起跳动 | 输入时光标/视图跳动 | 中 | ResizeObserver + 高度回调节流；插入前先取本地图片尺寸预占位 |
| R7 | 系统 WebView 碎片化 | 机型相关疑难 bug | 中 | 线上埋点采集 WebView 版本；建立机型白/黑名单 |
| R8 | 与现有原生功能（画廊、语音、触发弹窗）耦合复杂 | 交互一致性下降 | 中 | 明确"哪些留在原生、哪些进 H5"的边界；优先把交互留给原生，H5 只负责文本与内联节点 |

---

## 六、替代路线对比

| 维度 | 路线 1：继续修覆盖层 | **路线 2：WebView + TipTap** | 路线 3：回到块级图片（原生） |
| --- | --- | --- | --- |
| 解决重叠的确定性 | 中（根因未最终确认） | **高**（引擎内建能力） | **高**（图片根本不进文本排版） |
| 工作量 | 小（若诊断顺利） | **很大**（三端 × 编辑器重写） | 中（恢复原有块渲染分支） |
| 引入新风险 | 无 | **多**（IME/滚动/内存/碎片化） | 少 |
| 图文自由混排 | 支持（当前目标） | 支持 | **不支持**（图片是独立块，不能插在句子中间） |
| 多端一致性 | 取决于 Kuikly 文本能力 | 需三端各写桥接 | 好（纯原生） |
| 与现有数据/组件兼容 | 好 | 差（需重写序列化与交互） | **很好**（项目原本即此结构，`SwipeableImageStack` 直接可用） |
| 长期维护 | 依赖对库的深度改造 | 依赖 H5 资产与三端桥接 | 简单、可控 |

**路线 3 的关键前提**：需要你确认产品是否接受"图片作为独立块、不参与句子内混排"。若接受，这是**性价比最高的选择**——项目在 v2026-08-30 之前正是这一结构，回退风险极低。

---

## 七、建议的决策路径

### 短期（建议先做，成本 < 1 小时）

**做一次决定性诊断，判定路线 1 是否真的走不通。** 在 `drawInlineImages` 中临时打印：

- 每个 placement 的 `line`、`lineTop`、`heightPx`
- `textLayoutResult.lineCount` 与各行 `getLineHeight()`

判定规则：

- 若**相邻图片 lineTop 间隔 ≈ 文字行高** → 行高仍未生效，说明上一轮修复未命中（继续查注入时序/是否有第二处覆盖）
- 若**相邻图片 lineTop 间隔 ≈ 图片高度** → 行高已生效，问题在别处（很可能是 placements 的 `textRange` 陈旧，或图片绘制高度 > 段落行高），此时修复方向明确且成本很低

这一步能把"原生到底行不行"从猜测变成结论，避免为一个渲染缺陷付出架构级代价。

### 中期（按诊断结果二选一）

- 若路线 1 可解 → 沿用原生，路线 2 搁置
- 若路线 1 确认不可解，且产品**必须**图文自由混排 → 进入路线 2 的 POC

### 若走路线 2：POC 必过关卡（任一不过则不建议全量）

1. 中文输入法专项：搜狗/百度/讯飞/Gboard，测试连续输入、候选词选择、退格、回车、中途切输入法
2. 多图插入：连续插入 5 张，检查布局、删除、撤销
3. Compose 滚动嵌套：`Column + verticalScroll` 内 WebView 的高度自适应与滚动手感
4. 软键盘：弹出/收起时光标可见、不被遮挡、`visualViewport` 正确
5. 本地图片加载：`WebViewAssetLoader` 或 `shouldInterceptRequest` 全链路通
6. 内存：反复进出编辑页 20 次，观察内存增长与 WebView 正确销毁
7. 桥接往返：JSON 序列化 ↔ `content_blocks` 转换正确性（含图片/语音节点）

POC 建议**只做 Android 单端**，验证通过后再评估 iOS/鸿蒙。

---

## 八、我的建议

1. **不要因为一个渲染 bug 更换编辑器架构。** 当前问题集中在"覆盖层与排版引擎的坐标对抗"，而路线 2 引入的 IME / 滚动嵌套 / 三端桥接风险，量级都大于重叠本身。
2. **先做第七节的诊断实验**，用真机数据决定路线 1 的去留。
3. **同步确认产品需求**：是否真的需要"图片插在句子中间"？若不需要，路线 3（块级图片）是最省事且最稳的答案。
4. 若最终确认必须走 H5 编辑器，**建议先做 Android 单端 POC**，把中文 IME 与滚动嵌套这两个最高风险项验证掉再谈全量。

---

## 附录：关键证据来源

1. **Android 官方文档** — Wrap a WebView in Compose（嵌套滚动限制、内存泄漏警告）：https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/wrap-webview-in-compose
2. **contenteditable 缺陷追踪站** — 186 scenarios / 312 cases，IME & Composition 专题（Android 中文 IME 退格粒度、getTargetRanges 空数组、iOS composition 事件缺失等）：https://contenteditable.realerror.com/scenarios
3. **TipTap Issue #6276** — 移动端选区层覆盖原生 UI：https://github.com/ueberdosis/tiptap/issues/6276
4. **TipTap Issue #7301** — Safari 选区在 blur/focus 切换时损坏：https://github.com/ueberdosis/tiptap/issues/7301
5. **ProseMirror-view ≥ 1.33.4** — 包含 iOS 东亚语言组合输入的修复（Shadow DOM 场景问题报告）
6. **社区案例** — Android 定制系统 WebView 中 contenteditable 光标丢失/选区错乱（uni-app editor、Vue 移动端富文本编辑器相关讨论）
