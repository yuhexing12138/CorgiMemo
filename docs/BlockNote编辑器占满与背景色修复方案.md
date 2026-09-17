# BlockNote 编辑器「占满编辑区」与「背景色对齐主题」修复方案

> 日期：2026-09-17
> 现象来源：真机截图（灵感编辑页，标题「测试」下方 WebView 区域）

## 一、问题现象

| 编号 | 现象 | 用户描述 |
| --- | --- | --- |
| 1 | WebView 编辑区右侧约 1/4 宽为空白，且内部还套着一个白底、带圆角的浅色框 | BlockNote 编辑器需要占满屏幕上的编辑区，图片上的明显没占满 |
| 2 | 编辑区背景是纯白，与页面主题的暖米色背景不一致 | BlockNote 编辑器的背景色要与主题的背景颜色相同 |

## 二、根因分析

### 问题 1-A：`.bn-editor { padding-inline: 54px }`（左右各 54px 被吃掉）

来源：`@blocknote/core/dist/style.css`

```css
.bn-editor {
  font-synthesis: style weight;
  outline: none;
  padding-inline: 54px;      /* ← 预留左右侧边菜单（+ / ⋮⋮ 手柄）的绘制空间 */
}
```

BlockNote 用这 54px 给侧边菜单（拖拽手柄、加号按钮）留出绘制位，官方示例中编辑器本身是
**居中窄栏**（约 700–780px），54px 占比例不大；但在手机全宽（约 360dp）下，左右各 54px
等于直接吞掉近 30% 的可用宽度，与宿主编辑区已有的 8dp 水平 padding 叠加后尤其明显。

### 问题 1-B：宿主 `Column` 是 `verticalScroll` + `fillMaxSize`，WebView 被压成 wrap-content

来源：`app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationEditScreen.kt`

```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .verticalScroll(contentScrollState)   // ← 高度约束变为无限
    ...
) {
    ...
    BlockNoteEditorWebView(
        controller = blockNoteController,
        modifier = Modifier.fillMaxWidth()    // ← 高度没指定 → 只能 wrap-content
    )
}
```

`verticalScroll` 会把子项的高度约束改成 `Constraints.Infinity`，因此 `BlockNoteEditorWebView`
即便内部写了 `Modifier.fillMaxSize()`，其 `AndroidView` 也只能拿到「内容高度」而非「剩余空间」。
结果是编辑区高度不受控（内容少时只有几行高），底部大片空白不属于编辑器——**用户截图里
浅色框以下的大片米色区域，其实根本不是 WebView 画的**。

（注：截图里「浅色框 + 圆角」是 `.bn-editor` 的 `border-radius: var(--bn-border-radius-large)`
+ 白色背景共同作用的结果，属于问题 2 的视觉表现。）

### 问题 2：`.bn-editor { background-color: var(--bn-colors-editor-background) }` 默认 `#ffffff`

来源：`@blocknote/react/dist/style.css`

```css
.bn-root { --bn-colors-editor-background: #ffffff; }        /* 亮色 */
.bn-root[data-color-scheme="dark"] { --bn-colors-editor-background: #1f1f1f; }  /* 暗色 */

.bn-editor {
  background-color: var(--bn-colors-editor-background);
  border-radius: var(--bn-border-radius-large);
  color: var(--bn-colors-editor-text);
}
```

而 WebView 容器自身的背景（`<html>/<body>`）**从未设置过**：

- `editor.html` 只声明了 viewport，无任何背景样式；
- `src/probe.css` 里只有 `body { background: #f5f5f7 }`，且该文件是给**探针页**用的
  （`EditorApp.tsx` 虽 import 了它，但 `#f5f5f7` 与主题暖米色 `#FFFBF5` 也不一致）；
- 宿主侧 `EditorCore` 只注入了 `--editor-bg` 等变量给**自绘的 emoji 面板**用，
  从未真正作用到 `.bn-editor` / `body`。

因此：
- `.bn-editor` 恒为白（亮色）或 `#1f1f1f`（暗色），与宿主主题背景（暖米色 `#FFFBF5` /
  暗色 `#1A0F08`）不一致；
- `.bn-editor` 的圆角 + 与 body 的色差，在视觉上形成「画中画」的浅色框。

## 三、修复方案

### 3.1 JS 侧（`blocknote-probe/src/editor/`）

**改动 1 —— 主题载荷扩展 `ThemePayload` 增加 `background` 字段（`bridge.ts`）**

```ts
export type ThemePayload = {
  dark: boolean;
  primary: string;
  /** 宿主编辑区背景色（hex，如 "#FFFBF5"）——WebView 与 .bn-editor 均跟随，避免"画中画" */
  background?: string;
};
```

**改动 2 —— `EditorApp.tsx` 的 `EditorCore` 注入 CSS 变量并把背景铺到三处**

| 目标 | 选择器 | 作用 |
| --- | --- | --- |
| `.bn-editor` | `--bn-colors-editor-background` | BlockNote 官方变量，覆盖白底 |
| `body` / `html` | `background-color` | 消除 WebView 与编辑器的色差 |
| `.editor-page` | `background-color` | 让 padding 区域也是同色 |

要点：
- **`.bn-editor` 的 `border-radius` 必须同时归零**——否则同色背景下仍会残留圆角边界痕迹
  （暗色下尤其明显），且它本就是"居中卡片"语义，全宽模式下不应存在。
- **`.editor-page` 的 `max-width: 780px` 与 `margin: 0 auto` 必须去掉**（`editor.css`）——
  这是桌面端居中窄栏的写法，手机上 780px 不会生效但仍无意义，改为 `width: 100%`。

**改动 3 —— 抵消 54px 侧边留白（`editor.css`）**

```css
/* 手机全宽编辑：BlockNote 默认给 .bn-editor 左右各 54px 侧边菜单绘制位，
   在移动端会白白吞掉近 30% 宽度。这里改为 0，侧边菜单以浮层形式叠加显示。 */
.bn-editor { padding-inline: 0 !important; }
```

> ⚠️ 风险评估：置 0 后，`+` 与 `⋮⋮` 手柄在块左缘 **可能** 溢出到自绘区之外
> 或被裁掉（它们默认定位在 `padding-inline` 留出的空间里）。这也是本次需要
> 用户真机确认的首要点。备选方案见 §3.4。

**改动 4 —— WebView 容器自身背景（`editor.html` 内联 style）**

给 `<html>` 与 `<body>` 加 `background: transparent`，交给 React 侧按主题着色；
避免首帧闪白（FOUC）：在 `editor.html` 的 `<head>` 内联一个默认暖米色
（`#FFFBF5`），React 挂载后由 `--editor-bg` 立即接管。

### 3.2 Kotlin 侧（`BlockNoteEditorWebView.kt`）

**改动 5 —— 下行背景色**

```kotlin
/**
 * 背景色下行（v1.9）：让 WebView 内部与宿主编辑区同色。
 * @param background 宿主编辑区背景 hex（"#RRGGBB"）
 */
fun setTheme(dark: Boolean, primary: String, background: String) {
    val theme = JSONObject().put("dark", dark).put("primary", primary).put("background", background)
    enqueueCommand(JSONObject().put("type", "setTheme").put("theme", theme))
}
```

同步修改 `BlockNoteEditorWebView` 组合函数：新增 `backgroundColor: Color` 参数，
计算 hex 后在 `LaunchedEffect` 中下行；同时 `AndroidView` 加 `setBackgroundColor()` 兜底
（防止 WebView 首帧未绘制时透出宿主 `contentBackgroundColor` 的差异）。

**改动 6 —— 让编辑区真正"占满"（宿主布局）**

由用户决策（见 §3.3），三种取向：

| 方案 | 做法 | 影响 |
| --- | --- | --- |
| A（最小改动） | `Column` 去掉 `verticalScroll`，改为 `Column(Modifier.fillMaxSize())`，WebView 用 `Modifier.weight(1f)`；内部滚动交给 WebView 自己 | 页面整体不再滚动，仅编辑区滚动；标题/时间行固定顶部 |
| B（推荐） | 保持 `verticalScroll`，WebView 加 `Modifier.heightIn(min = 屏高 × 0.62f)` 保底 | 改动最小，WebView 有最小高度不再塌缩；但滚动仍是外层 |
| C（不推荐） | 仅 `heightIn(min = 400.dp)` 硬编码 | 不同屏适配差 |

> ⚠️ **方案 A 的连锁风险**：`InspirationImageGallery`、图片块选中工具栏（`Popup`）、
> 底部编辑栏 `EditToolbar` 的 inset 计算均依赖当前滚动容器；且 WebView 内部
> ProseMirror 自带滚动，外层去掉 `verticalScroll` 后 `contentScrollState.isScrollInProgress`
> 的"滚动清除块选中态"逻辑会失效。因此**推荐 B**。

### 3.3 待用户确认

见对话中的提问（AskUserQuestion）：
1. 占满方案取向（A / B / C）；
2. 背景色取值口径——直接跟随宿主 `MaterialTheme.colorScheme.background`，
   还是把用户自选的「灵感背景色」（`contentBackgroundColor`，默认透明）也一并传下去。

> **决策结果（2026-09-17）**：占满取 **B（保底高度）**；背景色取 **跟随灵感自选背景**
> （优先用用户为该条灵感选的背景色，未选时回落主题背景）；侧边菜单 **先置 0，看真机再定**。

### 3.5 ⚠️ 真机复现：`fillMaxSize()` 会"穿透多层"吞掉宿主意图（首版修复的漏网之鱼）

**现象**：首版修复（Box 去 `fillMaxSize` + 宿主 `heightIn(min=屏高×0.62f)`）打包后，
真机编辑区**仍然没占满**。

**根因**：`BlockNoteEditorWebView` 内部是两层结构——

```
宿主 Column (verticalScroll)
  └─ 外层 Box      modifier.fillMaxWidth().imePadding()   ← 首版已修正
      └─ AndroidView modifier.fillMaxSize()               ← 漏了这一层 ❌
```

`verticalScroll` 给出的**无限高约束**会沿父链逐级下传。外层 Box 只 `fillMaxWidth()` 是对的，
但它把「无限高 + 未定高」的约束继续传给 `AndroidView`；后者 `fillMaxSize()` 在无限高下
**解不出有限高度**，于是退化为"按子内容包装"，再把 Box 顶到内容高 →
宿主的 `heightIn(min=...)` 在链路上被**静默吞掉**（不报错，只是不起作用）。

**修复**：`AndroidView` 的 `modifier` 由 `Modifier.fillMaxSize()` 改为 `Modifier.fillMaxWidth()`，
同时删除已无用的 `import androidx.compose.foundation.layout.fillMaxSize`。

**通用规律（已写入项目 MEMORY.md）**：
> 自维护组件「不 fill 高度」的约定，**必须在每一层都写，不能只写外层**。
> 判据：宿主传了 `heightIn(min)` 却视觉没生效时，**逐层 grep `fillMaxSize`**，全部改 `fillMaxWidth()`。

### 3.6 背景色收敛为「内容区单一真值」（2026-09-17 第二轮）

首版存在两处**独立求值**同一语义的着色值：

| 位置 | 首版写法 | 用途 |
| --- | --- | --- |
| 宿主 `Column` 的 `.background(...)` | `contentBackgroundColor` | 宿主自己铺底色 |
| 下行给 WebView 的 `backgroundColor` | 局部 `editorBackgroundColor`（重复求值） | 告知子组件色值 |

两者逻辑等价但物理独立 → 任一处后续改动都会悄悄漂移（例如只改了 Column 的回落口径，
WebView 仍按旧口径着色，重新出现"画中画"色差）。

**修复**：在 `InspirationEditScreen` 顶部拆成三个**语义显式**的值，两处**共用**：

```kotlin
val userPickedBackgroundColor =   // 只回答"用户选了什么"
    if (backgroundColorInt == -1 || rawBackgroundColor == Color.White) Color.Transparent
    else rawBackgroundColor

val contentBackgroundColor =      // 唯一真值："内容区实际生效色"
    remember(userPickedBackgroundColor, MaterialTheme.colorScheme.background) {
        if (userPickedBackgroundColor == Color.Transparent) MaterialTheme.colorScheme.background
        else userPickedBackgroundColor
    }

val contentBackgroundPaint = userPickedBackgroundColor  // 绘制层真值："要不要真铺一层色"
```

- 宿主 `Column` 铺色 → 用 `contentBackgroundPaint`（保持"未自选不绘制、主题背景透出"原视觉）；
- 下行 WebView → 用 `contentBackgroundColor`（WebView 需要具体色值，不能是 Transparent）。

### 3.7 已知取舍与后续排期

`heightIn(min = 屏高 × 0.62f)` 是**经验值**，不同机型/字号会有偏差。更严谨的做法是让编辑区高度
= `Constraints.maxHeight`（视口真实剩余高度），但那要求去掉外层 `verticalScroll`（即 §3.2 的方案 A），
会波及"滚动清除块选中态"（`contentScrollState.isScrollInProgress` 链路）与图片画廊的 inset 计算，
**故单独排期，本轮不做**（已在代码注释中标注取舍）。

### 3.8 备选：54px 保留但改为浮层

若置 0 后侧边菜单被裁，可退为：

```css
/* 保留极小安全边距，侧边菜单靠 transform 溢出显示 */
.bn-editor { padding-inline: 16px !important; }
.bn-side-menu { transform: translateX(-16px); }
```

## 四、改动文件清单

| 文件 | 改动 |
| --- | --- |
| `blocknote-probe/src/editor/bridge.ts` | `ThemePayload` 增 `background?`；跨块选择类文档已孤儿化故未动 |
| `blocknote-probe/src/editor/EditorApp.tsx` | 注入 `--editor-bg` 到 blocknote 官方变量；body/编辑页背景；圆角归零 |
| `blocknote-probe/src/editor/editor.css` | 去 `max-width` 居中；`padding-inline: 0`；`.bn-editor` 圆角归零 |
| `blocknote-probe/src/probe.css` | `body` 背景 `#f5f5f7` → `transparent`（防污染正式编辑器） |
| `blocknote-probe/editor.html` | `<html>/<body>` 默认底色，防首帧闪白 |
| `app/.../probe/BlockNoteEditorWebView.kt` | `setTheme` 增 `background`；`backgroundColor` 参数 + hex 下行 + 底色兜底；**Box 与 AndroidView 均只 `fillMaxWidth()`**（§3.5）；`composeColorToHex` 工具函数 |
| `app/.../inspiration/InspirationEditScreen.kt` | 三个语义显式的背景色值（§3.6）；WebView `heightIn(min=屏高×0.62f)` |
| `docs/bridge-protocol.md` | `init` / `setTheme` 的 theme 载荷补 `background?: hex` |
| `app/src/main/assets/blocknote-web/editor/editor.html` | **产物重建**（1,882,083 bytes） |
| `.workbuddy/memory/MEMORY.md` | 新增「BlockNote 官方样式约束（v1.9 实测）」；补「无限高约束穿透多层」教训 |

> ⚠️ **产物必须重建**：改完 `blocknote-probe/src/editor/` 后，需
> `cd blocknote-probe && npm run build:editor`（或依赖 `:app:buildBlockNoteEditor`）。
> 验证判据：logcat 的 `ready received | build=<构建时间>` 应为本次时间。
