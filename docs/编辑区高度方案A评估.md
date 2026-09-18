# 编辑区高度：方案 A（视口真实剩余高度）单独评估

> 关联：`docs/BlockNote编辑器占满与背景色修复方案.md` §3.8／§3.10，
> `docs/bridge-protocol.md` v1.11.6
>
> 背景：当前编辑区最小高度是 `屏高 × 62%` 的**经验值**（v2026-09-17 方案 B），
> 当时注明"更精确的做法是方案 A，但会波及两条链路，需单独评估"。
> 本文即该评估。

## 一、结论先行

**方案 A 可行，且成本远低于当初的估计**——因为当初记录的两条"受影响链路"，
经代码核查**都已是死代码**：

| 当初担心的影响 | 核查结果 |
| --- | --- |
| 图片画廊 inset（`editorViewportBounds`） | ❌ **不存在**——该状态全项目只被赋值、**无读取方** |
| 滚动清除块选中态（`clearBlockSelection()`） | ❌ **无实际效果**——它清除的三个状态**无 UI 消费方** |

方案 A 的真实改动很小（内容区只有 4 个子元素），主要成本转为**真机验证**。

### 实施状态：✅ 已落地（2026-09-18）

- `d070cad6` 死代码清理：`editorViewportBounds` + 3 个孤立 import
- `4f320648` 方案 A：去掉 `verticalScroll`，WebView 改 `weight(1f)` +
  `onSizeChanged` 下发实测高度；移除 `contentScrollState`、`editorMinHeight`

待真机验证项见 §5，其中**"标题/日期行固定不滚"**是需要用户最终确认的 UX 变化。

---

## 二、现状（改动前）

### 布局结构

内容区是一个 `Column`，**只有 4 个子元素**：

```
Column( fillMaxSize + padding(innerPadding) + padding(horizontal 8dp) + verticalScroll )
├── RichTextEditor      标题
├── Spacer              8dp
├── Row                 日期 + 字数
└── BlockNoteEditorWebView   Modifier.heightIn(min = 屏高 × 0.62f)
```

### 62% 的三个问题

1. **与真实可用空间脱钩**：真实剩余高度 = 视口 − 状态栏 − 顶栏 − 底栏 − 标题 − 日期行。
   62% 只考虑了"屏高"，没减掉这些固定占用 → 机型越大/顶栏越高，误差越大。
2. **键盘弹出时不变**：`heightIn(min)` 是固定值，软键盘弹出压缩了 Column 高度，
   但 62% 仍按全屏高算 → 编辑区相对过高。
3. **嵌套滚动**：外层 `Column.verticalScroll` + WebView 内部内容增长，
   两套高度机制叠加，语义不清晰。

### 实测参考（用户机型）

- 屏高 873dp → 62% = **541dp**
- `.bn-editor` 实际 `offsetH = 541`（诊断日志确认，min-height 已生效）
- 该机型下 541dp 是"够用"的，但这只是巧合——换成小屏或大屏未必合适。

---

## 三、方案 A 的改动

```diff
 Column(
     modifier = Modifier
         .fillMaxSize()
         .padding(innerPadding)
         .background(contentBackgroundPaint)
         .padding(horizontal = 8.dp)
-        .verticalScroll(contentScrollState)
-        .onGloballyPositioned { editorViewportBounds = it.boundsInWindow() }
 ) {
     RichTextEditor(标题)          // 高度 wrap content，固定
     Spacer(8.dp)                  // 固定
     Row(日期 + 字数)              // 固定
     BlockNoteEditorWebView(
-        modifier = Modifier.heightIn(min = editorMinHeight),
+        modifier = Modifier.weight(1f),   // 占满剩余空间
     )
 }
```

配套两点：

1. **min-height 下发值改为 WebView 实测高度**：用 `onSizeChanged` 测量后经
   `setEditorMinHeight` 下发（复用 v1.11.6 的既有通道，无需改协议）。
2. **顺带清理死代码**：`contentScrollState`（含其 `LaunchedEffect`）、
   `editorViewportBounds`——去掉 `verticalScroll` 后前者永不触发，后者本就无人读取。

---

## 四、两条"拦路虎"的核查详情

### 4.1 `editorViewportBounds` —— 无读取方

全项目仅 3 处：

```
290:  var editorViewportBounds by remember { mutableStateOf<Rect?>(null) }   // 声明
1558: * 垂直 clamp 边界（即"屏幕展示边界"，见 editorViewportBounds）。        // 注释
1560: .onGloballyPositioned { editorViewportBounds = it.boundsInWindow() }    // 赋值
```

286 行的注释称它是"图片选中工具栏的垂直 clamp 依据"，但**没有任何代码读取它**。
推测：图片/分割线选中工具栏是 Compose 版 `BodyBlocksEditor` 的能力，
该 UI 已在 BlockNote 迁移中下线，状态残留了下来。

→ **方案 A 不会波及任何实际功能**，可直接删。

### 4.2 `clearBlockSelection()` —— 清除的状态无 UI 消费

```kotlin
fun clearBlockSelection() {
    if (highlightedBlockId == null && highlightedTapX == null && imageToolbarBlockId == null) return
    highlightedBlockId = null
    highlightedTapX = null
    imageToolbarBlockId = null
}
```

这三个状态的引用**全部在 `BodyBlocksController.kt` 内部**，加上
`InspirationEditScreen.kt:1532` 这一处调用。**没有任何外部 UI 消费方**。

→ 滚动时调用它**不产生任何可见效果**。去掉 `verticalScroll` 后该
`LaunchedEffect` 只是"永不触发"的无害残留，可一并清理。

> 注意：`BodyBlocksController` 本身仍在用（图片备注/缩放持久化、旧数据媒体迁移、
> 语音 token、图片删除等**数据层**能力），只是选中态相关部分已失效。

---

## 五、方案 A 的收益与风险

### 收益

| 项 | 说明 |
| --- | --- |
| 高度精确 | 编辑区 = 视口真实剩余高度，不再依赖经验值 |
| 键盘自适应 | 键盘弹出 → `innerPadding` 变化 → WebView 高度自动变化 → 重新下发 min-height |
| 消除嵌套滚动 | 只有 WebView 内部一套滚动机制，语义清晰 |
| 布局更标准 | 标题/日期固定、正文滚动，与 Notion / 飞书等一致 |

### 风险与需验证项

| # | 风险 | 说明 | 缓解 |
| --- | --- | --- | --- |
| 1 | **UX 变化** | 标题与日期行将**固定**在顶部（原本随内容滚走） | 需用户确认是否接受；这是最需要拍板的一点 |
| 2 | **首帧闪烁** | 初次测量到 WebView 高度前，min-height = 0，编辑区可能先矮后高 | 可用 CSS `min-height: 100%`（配合父链高度）消除，或实测是否可感知 |
| 3 | WebView 内滚动 + 软键盘 | 光标可见性、键盘弹出/收起时的滚动位置 | 真机验证 |
| 4 | 内容极少时 | WebView 被 `weight(1f)` 撑满，编辑区很大——符合"书写区"预期 | 无 |

---

## 六、备选方案对比

| 方案 | 精确性 | 改动量 | 风险 | 备注 |
| --- | --- | --- | --- | --- |
| **A. 去 verticalScroll + weight(1f)** | ★★★ 精确 | 小（4 个子元素） | 中（UX 变化 + 真机验证） | **推荐** |
| B. 保留 verticalScroll，测量真实剩余高度 | ★★ 较精确 | 中 | 低 | verticalScroll 下 WebView 高度随内容增长，测量值意义有限，且键盘弹出时难同步 |
| C. 仅调比例（62% → 70~75%） | ★ 经验值 | 极小 | 无 | 治标不治本，换机型/键盘状态仍不准 |

---

## 七、建议

1. **推荐实施方案 A**。当初阻止它的两条链路都不存在，实际成本远低于预期。
2. **先确认 UX**：标题固定是否可接受？如果希望标题也随滚动滚走，则不能简单去掉
   `verticalScroll`，需要改成"外层滚动容器 + WebView 高度 = 剩余空间"的更复杂结构
   （那就要保留 verticalScroll，走方案 B）。
3. 实施后按 §5 的四项风险逐条真机验证，尤其第 1、3 项。
