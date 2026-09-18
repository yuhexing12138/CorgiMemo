# 灵感编辑区 - BlockNote 结合 Reorderable 实现块拖拽可行性报告

> 日期：2026-09-18
> 问题：灵感编辑区（WebView + BlockNote）中，能否结合 `Reorderable` 库（`sh.calvin.reorderable:3.1.0`）实现**编辑器内部块**的拖拽排序？
> 关联：`docs/bridge-protocol.md`（v1.11.5）、`blocknote-probe/`、`Reorderable/`、`docs/拖拽换位与空行弹出同步-实施方案.md`

---

## 一、结论速览（TL;DR）

1. **Reorderable 库本身无法直接拖拽 BlockNote 内的块**：它作用于 Compose `LazyColumn/LazyGrid` 的 item，而块是 WebView 内的 DOM 元素——对 Compose 而言，WebView 只是一个不透明矩形，没有任何可重排的 Compose item。此路不通。
2. 但「块拖拽」这个能力本身**可行**。推荐走 **方案 C：WebView 内自写触摸拖拽（Pointer Events + `editor.moveBlocks()`）**，纯 JS 侧改动，逻辑闭环在 WebView 内，预估 2~4 天（含真机调优）。这正是 `bridge-protocol.md` v1.11.5 删除 ⋮⋮ 手柄时预留的方向。
3. Reorderable 的定位：**继续服务 Compose 原生列表**（待办卡片、侧栏等），不建议为本需求 fork——旧 Compose 块编辑器时代的 fork 副本（`BlocksReorderableList.kt`）已随 BlockNote 迁移废弃，前车之鉴。

---

## 二、现状盘点（事实清单）

| # | 事实 | 出处 |
|---|---|---|
| 1 | 灵感编辑页为**局部 WebView** 架构：正文编辑区一个 WebView，标题为 Compose 原生 `RichTextEditor`，底部栏为 Compose | `app/.../inspiration/InspirationEditScreen.kt:1864、1704、1232` |
| 2 | WebView 封装为 `BlockNoteEditorWebView`，加载 `assets/blocknote-web/editor/editor.html`；Kotlin→JS 用 `evaluateJavascript`，JS→Kotlin 用 `addJavascriptInterface` | `app/.../probe/BlockNoteEditorWebView.kt:39、485、523` |
| 3 | BlockNote 版本 **0.52.1**；**⋮⋮ 拖拽手柄已于今日（v1.11.5）整体删除**（`sideMenu={false}`） | `blocknote-probe/src/editor/EditorApp.tsx:1061`；`docs/bridge-protocol.md` v1.11.5 |
| 4 | 删除原因（平台级限制）：BlockNote 块拖拽**纯用 HTML5 原生 Drag & Drop**（`dragstart/dragover/drop` + `dataTransfer`，零 touch/pointer 处理），而 W3C 将 drag 事件定义为鼠标驱动行为——**Android WebView / Chrome for Android / iOS Safari 在触摸下根本不派发这些事件**。旁证：OpenProject 团队记录 *"Blocknote has some features that have to be disabled in mobile (drag-and-drop, toolbar)"* | `docs/bridge-protocol.md` v1.11.5 |
| 5 | 现行块移动方式：工具栏「上移/下移」→ 下行 `moveBlockUp/moveBlockDown` → `editor.moveBlocksUp()/moveBlocksDown()`，按**选区首/末块**口径，天然支持多选与嵌套块，到边界安全 no-op | `EditorApp.tsx:657-673` |
| 6 | Reorderable 3.1.0 **已引入**并在主工程使用：待办列表 `ReorderableLazyColumn.kt`、`ZonedReorderableLazyColumn.kt`、侧栏多处 | `gradle/libs.versions.toml:31,93`；`app/build.gradle.kts:292` |
| 7 | fork 副本 `ui/components/reorderable/BlocksReorderableList.kt` 存在但**已无调用点**（消费方 `BodyBlocksEditor.kt` 已随 BlockNote 迁移删除） | `inspiration/components/BodyBlocksController.kt:29-31` |
| 8 | BlockNote 块**不以 JSON 同步到 Compose 侧**：上行只有 GFM markdown（800ms 防抖）+ 光标块状态 | `docs/bridge-protocol.md:36、38` |

---

## 三、核心矛盾分析：为什么 Reorderable 拖不动 WebView 里的块

Reorderable 的工作前提（`Reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/`）：

1. **item 是 Compose 可组合项**，注册进 `ReorderableLazyCollection`；
2. 重排 = 对 LazyList 的 **item 布局索引**做 swap，靠 LazyList 自身的 item 平移动画出动画；
3. 需要随时读到每个 item 的 **Compose 布局坐标**做 hit-test 与 ghost 定位。

而 BlockNote 的块是 **ProseMirror 文档模型 → React 渲染的 DOM 节点**，位置由浏览器排版引擎决定。两者存在三层错位：

| 层面 | Reorderable 需要 | BlockNote 块实际是 |
|---|---|---|
| 渲染层 | Compose LayoutNode | WebView 内 DOM 节点（Compose 不可见） |
| 状态层 | Kotlin `List<Item>` 顺序 | ProseMirror doc JSON（在 JS 堆里） |
| 事件层 | Compose 手势系统接管 pointer | 触摸事件被 WebView 消费，Compose 收不到 |

**附带说明**：即便为了 Reorderable 把块 JSON 同步到 Compose 侧做一套「镜像列表」，也要面对双源真相、光标/选区回传、800ms 防抖时序错位等问题——理论可行但得不偿失，不展开。

---

## 四、候选方案评估

### 方案 A：Reorderable 直接拖 WebView 内的块 —— ❌ 不可行

原理见第三节三层错位。Reorderable 的 API 面（`rememberReorderableLazyListState` + `longPressDraggableHandle`）从签名上就要求 LazyListState 与 Compose item，对 WebView 内容**零作用域**。结论：不是「难做」，是「结构上不成立」。

### 方案 B：Compose 层做拖拽编排（fork Reorderable 手势内核），JS 只做落位 —— ⚠️ 技术可行但不推荐

**做法**：复用 Reorderable 的 `DragGestureDetector`（长按判定、速度跟踪）与 `Scroller`（自动滚动），在 Compose 层接管拖拽手势；JS 侧定期上报块几何（boundingRect）；Compose 浮层渲染 ghost 与插入指示线；drop 时桥接调用 `editor.moveBlocks()`。

**四大难点**：

1. **块几何高频同步**：块位置随输入、滚动、软键盘弹出持续变化，节流上报仍有错帧，ghost 与实际块会肉眼可见地错位；
2. **ghost 内容渲染**：WebView 内 DOM 无法低成本截成 Compose 图像（html2canvas 成本高、保真差），只能用文本快照近似；
3. **滚动联动所有权不清**：拖到边缘该滚 WebView 内容还是外层 Compose 页面？两套滚动状态叠加极易抖动；
4. **事件拦截是硬伤**：handle 在 WebView 内部，长按后 pointer 事件流被 WebView 捕获，Compose 层拿不到后续 move 轨迹；若把 handle 改成 Compose 覆盖层，则回到「双端几何同步」的老问题，复杂度不降反升。

**成本/风险**：1~2 周级 / 高。**结论**：每一步都在对抗平台边界，不建议。

### 方案 C：WebView 内自写触摸拖拽（Pointer Events + moveBlocks）—— ✅ 推荐

**做法**（全部在 JS 侧）：

1. **触发**：恢复 sideMenu 并用 BlockNote 自定义组件接口（`components={{ SideMenu }}`）渲染 ⋮⋮ 手柄，或完全自绘；
2. **拖起**：`pointerdown` + `setPointerCapture`，克隆块 DOM 做 ghost（`position: fixed`），原位留占位；
3. **移动**：`pointermove` 中用 `document.elementFromPoint` / 块几何遍历计算插入位置，画指示线；接近上下边缘时对编辑器滚动容器做自动滚动；
4. **落位**：`pointerup` 调 `editor.moveBlocks(blocksToMove, destination)`——该 API 在 `@blocknote/core` 0.52.1 的 dist 中已验证存在；
5. **同步**：落位后 markdown 上行由现有 `changed` 800ms 防抖机制自动完成，桥接协议可**零改动或极小改动**（建议落位后主动 flush 一次，避免时序尾巴）。

**优点**：逻辑闭环在 WebView（无跨层延迟与同步问题）；是 Notion/Craft 等同类移动端编辑器的通行做法；项目已有 `moveBlocksUp/Down` 的块口径（选区首末块、多选、嵌套）经验可直接复用。

**风险与缓解**：

| 风险 | 缓解 |
|---|---|
| 嵌套块插入位置判定 | 用 BlockNote 的块位置/textCursorPosition API 辅助，而非纯 DOM 几何 |
| 拖拽与文本选择/长按菜单冲突 | 拖拽期间 `user-select: none` + `preventDefault` + `touch-action` 约束 |
| 与 800ms 防抖上行的落位时序 | 落位后立即主动 flush 一次 markdown |
| 与惯性滚动/软键盘冲突 | 拖拽期间禁用编辑器惯性滚动；POC 阶段真机重点验证 |

**成本**：2~4 天（含真机调优）。

### 方案 D：维持现状（工具栏上移/下移）—— ✅ 兜底（已上线）

零成本，已支持多选与嵌套块。缺点：交互不直觉，多块远距离移动效率低。

---

## 五、方案对比总表

| 维度 | A：Reorderable 直拖 | B：Compose 编排+JS 落位 | C：WebView 内自写 | D：程序化上/下移 |
|---|---|---|---|---|
| 技术可行性 | ❌ 结构上不成立 | ⚠️ 可行但高风险 | ✅ | ✅ 已上线 |
| 预估成本 | — | 1~2 周 | 2~4 天 | 0 |
| 交互体验 | — | 好 | 好 | 一般 |
| 架构侵入 | — | 大（双端联动） | 小（纯 JS） | 无 |
| 可维护性 | — | 差（对抗平台边界） | 好 | 最好 |
| 与桥接协议关系 | — | 需新增几何上行消息 | 零/极小改动 | 无 |

---

## 六、推荐结论

1. **主推荐：方案 C**。建议分两步走：
   - **POC（≤1 天）**：⋮⋮ 长按拖起 + ghost + `moveBlocks()` 落位跑通基础链路，真机验证与滚动/选区/软键盘无冲突；
   - **完善（2~3 天）**：插入指示线、边缘自动滚动、多选块一起拖、嵌套块判定、落位后主动 flush。
   - **POC 通过判据**：真机从长按到落位全流程无错帧、无手势冲突、落位后 markdown 上行正确。
2. **Reorderable 的定位**：留在 Compose 原生列表场景（待办、侧栏）继续使用，能力上是优秀且已被项目验证的库；但它解决的是「Compose item 重排」，不是「WebView 内块重排」——**不建议为本需求再 fork**（旧 fork 已废弃是前车之鉴）。
3. 若不想投入，**维持方案 D** 也是合理选择，后续可随时升级到方案 C，两者不冲突。

---

## 七、参考

- `docs/bridge-protocol.md` v1.11.5 —— 平台限制证据链与删除手柄的决策记录
- `blocknote-probe/src/editor/EditorApp.tsx` —— `sideMenu={false}`、`moveBlockUp/Down` 现行实现
- `Reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/` —— 库的作用域证据（LazyCollection/LazyList）
- `docs/拖拽换位与空行弹出同步-实施方案.md` —— 旧 Compose 块编辑器时代 fork Reorderable 的历史与失效原因
