# BlockNote 编辑器 Bridge 协议 v1

> 关联：`BlockNote迁移实施计划.md` §2.1 ｜ 实现位置：`blocknote-probe/src/editor/bridge.ts`（JS 侧）、`BlockNoteEditorScreen.kt`（Kotlin 侧）
> 纪律：**单向数据流**——Kotlin 永不向 JS 回灌内容变更；undo/redo 状态全部留在 JS 侧。

## 通道

| 方向 | 机制 |
|---|---|
| Kotlin → JS | `webView.evaluateJavascript("window.BlockNoteEditorHost.onMessage(${json})")` |
| JS → Kotlin | `addJavascriptInterface(BridgeHost, "AndroidBridge")`，JS 调 `window.AndroidBridge.postMessage(json)` |

所有消息均为 JSON 字符串，带 `type` 字段。

## 下行消息（Kotlin → JS）

| type | 载荷 | 说明 |
|---|---|---|
| `init` | `{ markdown, readOnly, theme, fontFamily, fonts }` | 编辑器装载；`markdown` 为 GFM 文本；`theme = { dark: bool, primary: hex, background?: hex }`；`fontFamily` 为 FontCatalog.id（`system_default` = 系统默认）；`fonts = [{ id, weights: number[] }]` 为可用字体清单（v1.1，JS 据此生成 @font-face，文件走 shouldInterceptRequest 流） |
| `setReadOnly` | `{ readOnly }` | 只读切换 |
| `setTheme` | `{ theme }` | 主题切换（P1 扩展六色主题；v1.9 起 `theme.background` 携带宿主编辑区背景色，JS 写入 `--bn-colors-editor-background` 与 `html/body` 底色，消除编辑器白底与外层主题不一致的"画中画"） |
| `setFontFamily` | `{ fontFamily }` | 内容字体切换（配合 shouldInterceptRequest 字体流） |
| `requestSave` | `{}` | 主动要一次快照（返回键/切后台前），JS 侧立即触发一次 `changed` |
| `deleteBlock` | `{}` | 删除块（v1.11）。原 ⋮⋮ 手柄点击菜单的「删除」项。命中口径与官方 `RemoveBlockItem` 一致：当前**选区**若包含光标块则删整个选区（支持多选一起删），否则只删光标块——故无需传参 |
| `setBlockColor` | `{ textColor?, backgroundColor? }` | 设置当前块的**块级**颜色（v1.11）。原 ⋮⋮ 菜单的「颜色」项。取值是 BlockNote 预设色名（`gray`/`brown`/`red`/`orange`/`yellow`/`green`/`blue`/`purple`/`pink`；`"default"` 表示清除）。字段缺省 = 不改动该维度。⚠️ 与 `format` 的 `textColor` **不是一回事**：本条写块 props（整个块），后者写行内 span 样式（仅选区文字） |
| `setTableHeader` | `{ target, enabled }` | 切换表头行/列（v1.11）。原 ⋮⋮ 菜单的「表头行 / 表头列」项。`target` = `"row"` / `"column"`。仅 `table` 块生效（非表格静默忽略）。官方目前只支持 1 行 / 1 列，故用布尔开关 |
| `moveBlockUp` | `{}` | 块上移（v1.11.5）。**替代原 ⋮⋮ 手柄的拖拽重排**（该手柄依赖 HTML5 原生 DnD，在移动端触摸下不触发，已删除）。JS 侧调 `editor.moveBlocksUp()`，不传 `blockIdentifier` → 取**选区首块**或**光标块**，故天然支持多选一起移动与嵌套块。到首块时内部安全 no-op |
| `moveBlockDown` | `{}` | 块下移（v1.11.5）。同上，内部取**选区末块**或**光标块**，调 `editor.moveBlocksDown()` |
| `setEditorMinHeight` | `{ height }` | 设置编辑区最小高度（v1.11.6，单位 **dp**）。BlockNote 未给 `.bn-editor` 任何 `min-height`，高度完全由内容决定；宿主却给 WebView 设了 `heightIn(min = 屏高 × 62%)`。两者不一致会在 WebView 内留下一片**不属于 contenteditable 盒子**的"死区"（点击无法聚焦光标）。下发同一个高度值后 JS 写入 `--bn-editor-min-height`，由 `.bn-editor { min-height }` 消费，编辑区即铺满 WebView。**缺省 0 时与修复前一致**（向后兼容旧宿主）。⚠️ 不写 `62vh` 是因为本项目 WebView 高度会随内容增长、可能撑出屏幕（外层 Column 滚动），`vh` 语义不直观 |

| `focusEditor` | `{}` | 让编辑器重新获得 DOM 焦点（v2026-09-22）。宿主收起「T / H / A」面板、要把键盘弹回来**之前**下发：Chromium 只在编辑元素持有焦点时才建立输入连接，宿主随后的 `InputMethodManager.showSoftInput` 才有效。JS 侧对 `.bn-editor` 调 `focus()`（已聚焦时为空操作，不打断现有选区），并回一条 `diagnostic` 说明是否命中节点。⚠️ 必须在 **IME 抑制解除之后**下发，否则页面还带着 `inputmode="none"`，聚焦后 Chromium 仍不会弹键盘 |
| `requestBlockState` | `{}` | **主动要一次**块状态（v2026-09-22）。宿主在「T / H / A」面板**收起**时下发，让工具栏选中态在收起瞬间就是最新的——面板展开期间正文里的样式 / 选区变化若因 JS 侧 JSON 去重或时序没赶在收起前上行，收起后高亮会**滞后一拍**（显示面板操作之前的状态）。**无副作用**：不产生任何文档变更，只重新采集一次，且仍走 `pushBlockState` 的去重逻辑（状态没变则实际不上行），可放心多调。ready 前下发会进 `pendingCommands` 缓存，调用方无需判时机 |
| `format` | `{ action, value?, text? }` | 底部格式工具栏的统一格式通道。`action` 见下表；`value` 随 action 而定（`fontSize="18px"`、`textColor="#RRGGBB"`、`transform` 的块类型名等）。⚠️ v2026-09-22：`text` 目前**只有 `createLink` 用**（可选显示文字），其余 action 一律不带该字段 |
| └ `format.action = createLink` | `{ action: "createLink", value: url, text? }` | 插入/写入链接（v2026-09-22）。**宿主不判断有没有选区**——选区真值只在 WebView 的 ProseMirror state 里，Kotlin 侧无从取得（`View.hasFocus()` 会失真），故把 URL 与可选显示文字如实下发，由 JS 分流：① 有选区 + 未填标题 → 给选中文字挂 link mark；② 有选区 + 填了标题 → 标题替换选中文字再挂链接；③ **无选区且光标落在已有链接上 → 走 `editLink` 改这条链接**（不这么分流会插出「链接里套链接」）；④ **无选区且无链接 → 以标题（留空则用 URL 原文）为文字插入一段带链接的新文本**。缺协议的 URL 由 JS 侧 `normalizeLinkUrl()` 自动补 `https://`（对齐官方 LinkToolbar 的 `validateUrl`）。⚠️ 历史坑：只发 `createLink(url)` 时，空选区下走的是 `tr.addMark(from, to)` 且 `from == to` → **零长度区间加 mark 是静默空操作**，表现为"填了 URL 点确定毫无反应" |
| `saveSelection` | `{}` | 保存当前选区快照（v2026-09-22）。宿主在**打开链接对话框之前**下发。存在理由：链接对话框是 Compose 的 `AlertDialog`，弹出时 WebView 会失焦；若 Android WebView 在失焦时折叠了内部选区，随后的 `createLink` 就会按「无选区」处理 —— 用户明明选了字，却在光标处插了 URL。**无副作用**（只读快照，存 Selection 对象 + doc 引用，故 JS 侧无需引入 prosemirror-state 依赖）。JS 回 `diagnostic`：`saveSelection: <from>-<to> empty=<bool>` |
| `restoreSelection` | `{}` | 还原上一次 `saveSelection` 的选区（v2026-09-22）。宿主在对话框**确认 / 移除之后、写链接之前**下发；桥命令按序执行，故 restore 必定先于 `createLink` / `deleteLink` 生效。容错：文档未变则复用原选区对象；文档已变（理论不发生）则用保存的位置重建；两者都失败时**静默保持现状**并回 `diagnostic`，绝不抛错中断后续命令 |
| `deleteLink` | `{}` | 移除光标 / 选区所在位置的**链接本身，保留文字**（v2026-09-22）。链接对话框「编辑链接」模式的「移除链接」按钮，JS 侧调 `editor.deleteLink()`：优先按光标位置定位链接范围去 mark，找不到时回落为「去掉当前选区上的 link mark」。与 `restoreSelection` 搭配使用 |

## 上行消息（JS → Kotlin）

| type | 载荷 | 说明 |
|---|---|---|
| `ready` | `{ build?, srcHash? }` | 编辑器脚本就绪并已绑定下行宿主（Kotlin 侧解除 loading、随后发 `init`），并带上**构建指纹**（v1.8）与**源码内容哈希**（v2026-09-22，见「构建指纹与产物校验」章） |
| `changed` | `{ markdown }` | 内容变更快照；**JS 侧防抖 800ms**；由 `blocksToMd` 生成（含分割线样式编码） |
| `undoState` | `{ canUndo, canRedo }` | 撤销/重做可用态（v1.7）；历史栈变化后上报，宿主左上角按钮据此置灰 |
| `blockState` | `{ blockType, headingLevel?, headingToggleable?, inlineTextColor?, inlineBackgroundColor?, fontSizePx?, bold?, italic?, underline?, strike?, textAlignment?, canNestBlock?, canUnnestBlock?, canSetBlockColor, blockTextColor?, blockBackgroundColor?, canToggleHeader, isHeaderRow, isHeaderCol, linkUrl? }` | 当前光标块状态（v1.11；`headingLevel`/`fontSizePx` 为 v2026-09-21 新增，`headingToggleable`/`inlineTextColor`/`inlineBackgroundColor`/`linkUrl` 为 v2026-09-22 新增）。驱动宿主工具栏「块操作」菜单的可用态与回显、以及「标题面板」「颜色面板」的选中态。`headingLevel`：`blockType === "heading"` → 1–6（**普通与可折叠标题共用**）；非标题块 → 0。`headingToggleable`：`heading` 块且 `props.isToggleable === true` → 折叠标题；两类级别数字重叠，**必须靠该字段分流**（v2026-09-22 勘误：折叠标题不是独立块类型 `toggleHeading*`）。`inlineTextColor` / `inlineBackgroundColor`：`getActiveStyles()` 的原始串（宿主下发的自由 hex，也可能是粘贴来的色名 / `rgb()`），供 A 面板两个「选中色」行反查色名回显；**缺失 = 该维度未设置**（宿主高亮第一个「/」清除块）。⚠️ 与 `blockTextColor` / `blockBackgroundColor`（**块级**色名，作用于整段）不是一回事。`fontSizePx`：当前选区字号（`getActiveStyles().fontSize` 的 `"18px"` 解析为整数；WebView 内 1px=1dp，数值与宿主 sp 档位对应）；0 = 无字号样式（宿主回落默认档 16）。`linkUrl`：光标/选区上的**已有链接** URL（缺失 / 空串 = 不在链接上），供 🔗 按钮激活态与链接对话框的「编辑链接」模式。`canNestBlock` / `canUnnestBlock`：缩进 / 回退缩进的**可用态**（v2026-09-22 新增；口径照抄官方 `editor.canNestBlock()` / `canUnnestBlock()` —— 前者 = 当前块前面还有块、首块为 false；后者 = 嵌套深度 > 1、顶层块为 false），宿主两个缩进按钮据此置灰。`bold` / `italic` / `underline` / `strike`：当前选区的四个**行内布尔样式激活态**（v2026-09-22 新增；取自同一份 `getActiveStyles()` 快照并归一为布尔），供底部工具栏 B / I / U / S 四个按钮高亮。`textAlignment`：光标块的**对齐方式**（v2026-09-22 新增；`"left"` / `"center"` / `"right"` / `"justify"`，非字符串时回落 `"left"`），供三个对齐按钮**互斥**高亮。⚠️ 对齐是**块级 prop**（`props.textAlignment`）、与上面四个行内样式不是一个维度；各 block spec 的默认值即 `"left"`，故未设置 = 左对齐按钮亮。**仅在状态变化时上报**（JS 侧按 JSON 串去重） |
| `editorFocus` | `{ focused }` | 编辑器 DOM 焦点态（v2026-09-22）。JS 侧在 `document` 上监听 `focusin`/`focusout`，判定 `document.activeElement` 是否落在 `.bn-editor` 内，**仅在翻转时上行**。宿主据此在面板收起后决定是否弹回键盘。⚠️ 该真值只能由 JS 提供：Android 的 `View.hasFocus()` 会失真（点底部栏按钮后视图焦点已转移到 Compose 根视图，而 WebView 内的 contenteditable 仍持有 DOM 焦点、光标仍在闪） |
| `error` | `{ message }` | JS 异常上报（Kotlin 侧打 logcat / 展示错误态） |

> `ready` 载荷自 v1.8 起带可选 `build` 字段（构建指纹，见下），故其类型为 `{ build?: string }`。

> `undoState` 的取值经 BlockNote 公开 API **`editor.canExec(command)`** 判定
> （内部以 `dispatch === undefined` 调用命令，**只判定不派发**，不会污染历史栈）。
> JS 侧做变化去重，仅在布尔态翻转时上行。
> Kotlin 侧映射为 `BlockNoteBridgeController.canUndo` / `canRedo`（Compose 快照态）。
>
> ⚠️⚠️ **只能用 `canExec`，`editor.can(...)` 不存在**（v1.10 实测）：
> `BlockNoteEditor` 原型上**没有 `can` 方法**（全类只有 `exec` / `canExec`），
> `StateManager.can(cb)` 也从未转发到 editor。任何 `editor.can(x)` 都会在运行时抛
> `TypeError: editor.can is not a function`——若外层还有静默 catch，症状就是
> **「按钮永远灰」且毫无错误线索**（v1.7 → v1.10 两轮踩坑的共同根因）。
>
> ⚠️ **`command` 必须是 prosemirror 命令，不能传 `editor.undo`**：
> `editor.undo` 是原型实例方法，传裸引用会丢 `this`；而 prosemirror 命令需要
> `(state, dispatch, view)` 三元组。正确形态：取
> `editor.getExtension("yUndo") ?? editor.getExtension("history")` 的
> `undoCommand` / `redoCommand`（即 `@tiptap/pm/history` 的 `undo` / `redo`），
> 交给 `canExec` —— 这与 `StateManager.undo()` 的内部实现完全一致。
>
> ⚠️ `canExec` 在 `editor.transact()` 回调内调用会抛错。本项目的 `pushUndoState`
> 由 `BlockNoteView` 的 `onChange` 驱动（事务已提交），不在该限制内。

## 时序

```
WebView 创建 → editor.html 加载 → React 挂载 → bindDown + sendUp(ready{build})
Kotlin 收 ready → logcat 打印构建指纹 → evaluateJavascript(init{markdown,...})
JS 收 init → mdToBlocks → 渲染编辑器
用户编辑 → onChange 防抖 800ms → blocksToMd → sendUp(changed)
          同时 sendUp(undoState) 刷新宿主撤销/重做按钮可用态
Kotlin 收 changed → 落库（P0 内存态，P1 接 Repository）
返回键/切后台 → Kotlin sendDown(requestSave) → JS 立即 changed → Kotlin 落库 → 关闭
点宿主撤销/重做 → Kotlin sendDown(requestUndo|requestRedo) → JS editor.undo()/redo()
              → sendUp(undoState) → Kotlin 更新按钮可用态
光标块变化（onSelectionChange / onChange） → JS sendUp(blockState) → Kotlin 更新块状态快照
              → 工具栏「块操作」菜单据此决定表头项显隐、色板高亮
点工具栏「块操作」某项 → Kotlin sendDown(deleteBlock|setBlockColor|setTableHeader)
              → JS 执行 → onChange/onSelectionChange → sendUp(blockState) 回传新状态
点工具栏 🔗（链接）→ Kotlin sendDown(saveSelection) 快照选区 → 弹 Compose 对话框
                 （高亮态与「编辑链接」模式取自上一条 blockState.linkUrl）
              → 确认/移除 → Kotlin sendDown(restoreSelection) → sendDown(createLink|deleteLink)
              （桥命令按序执行，restore 必定先于写入命令生效）
              → JS 执行 → onChange/onSelectionChange → sendUp(blockState)（linkUrl 随之更新）
点面板收起（T / H / A）→ Kotlin sendDown(requestBlockState) → JS 重采一次 → sendUp(blockState)
              → 随后按焦点态恢复软键盘（见 focusEditor / editorFocus）
              （先刷状态再弹键盘：状态在收起瞬间即最新，不会"滞后一拍"）
```

## 构建指纹与产物校验（v1.8 / v2026-09-22）

`assets/blocknote-web/editor/editor.html` 是 vite 打出的**单文件内联产物**，源码在 `blocknote-probe/`。

**v1.8 起 Gradle 会自动重建它**：`app/build.gradle.kts` 注册了 `buildBlockNoteEditor`
（`Exec` 执行 `npm run build:editor`），并让所有 `merge*Assets` 任务 `dependsOn` 它——
因此 `assembleDebug/Release` 会自动带上最新产物，**不再需要人工记得跑 npm**；
源码未变时该任务 UP-TO-DATE（增量，45s 只在真改 JS 时花）。构建失败**不阻断** App 编译
（仅告警并沿用既有产物），手动强制重建：`./gradlew :app:buildBlockNoteEditor --rerun-tasks`。

> ⚠️ v1.8 **之前** Gradle 确实不触发重建，出现过「JS 源码已改、App 也重编、真机仍是旧 bundle」
> 的事故。若现在仍遇到「JS 改了没生效」，按这两条排查：① `ready` 上行里的构建指纹是否最新（见下）；
> ② WebView 缓存（卸载重装 / 清数据）。

为了让这个问题一眼可查，构建期由 `vite.editor.config.ts` 的 `define` 注入全局常量
`__BUILD_FINGERPRINT__`，格式为：

```
<构建时间 YYYY-MM-DD HH:mm:ss> <commit 短 hash><-dirty?>
例：2026-09-17 18:20:31 a1b2c3d-dirty
```

- `-dirty` 仅在 `blocknote-probe/src` 有未提交改动时追加（只看编辑器源码，无关文件不影响）
- git 不可用时 hash 回落 `nogit`，采集失败**不阻断构建**
- JS 侧 `bridge.ts` 导出 `BUILD_FINGERPRINT`，经 `ready` 上行

Kotlin 侧在 `handleUpMessage` 的 `ready` 分支打出：

```
adb logcat -s BlockNoteEditor:V | grep "ready received"
# D BlockNoteEditor: ready received | build=2026-09-17 18:20:31 a1b2c3d | src=bn-src:201c3b34583f
```

比对这里的 build 与 `git log -1 -- blocknote-probe/src` 的 commit 是否一致，即可判定产物新鲜度。

### 源码内容哈希与产物校验（v2026-09-22）

**动机**：构建指纹只能回答"这是什么时候、用哪个 commit 构建的"，**答不了**"这版
产物装进去的源码内容是否等于工作区现在这份"。而本项目已两次出现
**「改了 `src/editor/` 并提交、但产物没重建」**——提交里只有源码，真机继续跑旧
bundle，表现为"改了没生效"，且从提交记录上完全看不出来。

**做法**：`vite.editor.config.ts` 的 `collectSrcHash()` 把
`editor.html + src/editor/` 全量内容算成 sha256 前 12 位，注入全局常量
`__SRC_HASH__`（带 `bn-src:` 前缀，便于从 1.9MB 的压缩产物里稳定定位）。
`bridge.ts` 导出 `SRC_HASH`，随 `ready` 上行；Kotlin 侧打进 logcat（上面的 `src=`）。

**校验脚本**：`scripts/check-blocknote-artifact.ps1` 用**同一口径**现算一次并与
产物内嵌值比对。

```powershell
.\scripts\check-blocknote-artifact.ps1              # 校验；不一致 exit 1
.\scripts\check-blocknote-artifact.ps1 -WarnOnly   # 只告警
.\scripts\check-blocknote-artifact.ps1 -StagedOnly # 仅当暂存区含 src 改动时才校验
.\scripts\check-blocknote-artifact.ps1 -PrintOnly  # 只打印当前源码哈希
```

**pre-commit 校验**：钩子文件为 **`.githooks/pre-commit`**（随仓库入库）。

本仓库已配置 `core.hooksPath = .githooks`，故 Git 读的是仓库内的这个目录
（**不是** `.git/hooks`，后者在本机是空的）。新 clone 的机器需执行一次：

```sh
git config core.hooksPath .githooks
```

生效后，凡本次提交涉及 `blocknote-probe/src/` 的，钩子会用 `-StagedOnly`
自动校验：产物滞后则**阻断提交**并提示重建命令；顺带提醒"产物重建了但没 `git add`"。
不碰编辑器源码的提交一律跳过（零误伤）。临时跳过用 `git commit --no-verify`。

> 钩子在"缺脚本 / 缺 powershell"时**放行并提示**，不阻断——那属于本机环境没装好，
> 不是产物过期；真正的校验失败由脚本自己 `exit 1` 表达。避免提交被无关原因卡住。

> ⚠️ **哈希口径三处必须逐条对齐**（vite `collectSrcHash()` / 校验脚本 / 任何新的
> 重算实现）：① 只算**文件原始字节**（与换行符、编码无关）；② 范围 =
> `editor.html` + `src/editor/` 全量，路径相对 `blocknote-probe`；
> ③ 路径分隔符统一 `/`、无前导斜杠、按 **UTF-16 码元序（Ordinal）** 升序；
> ④ 每条记录按「相对路径(UTF-8) + 文件字节」顺序喂进同一个 sha256。
>
> ⚠️ PowerShell 侧**不能用** `[Array]::Sort($names, [StringComparer]::Ordinal)`：
> `Get-ChildItem | Select -ExpandProperty Name` 得到 `PSObject[]`，重载解析会挑中
> 不带比较器的 `Sort(Array)`，comparer **静默失效**、实际按文化敏感规则排序
> （`bridge.ts < editor.css < EditorApp.tsx`），而 Node 是
> `EditorApp.tsx < bridge.ts < editor.css` → 顺序不同 → **哈希恒不相等**，
> 脚本会一直误报"产物过期"。改用 `List[string].Sort(IComparer<string>)` 才可靠。
>
> ⚠️ 脚本文件本身必须是 **UTF-8 with BOM**：Windows PowerShell 5.1 对无 BOM 的
> `.ps1` 按系统 ANSI 代码页读取，中文注释（尤其 emoji）会乱码并吞掉引号 →
> 直接抛一堆 `ParserError`。

## 版本

- v1（2026-09-16，P0）：上述五个下行 + 三个上行。升级时在本文档追加变更记录，JS/Kotlin 两侧同步实现。
- v1.7（2026-09-17）：新增上行 `undoState`；JS 侧自绘的「撤销/重做」按钮移除，
  统一由宿主顶栏图标按钮承担（点击→`requestUndo`/`requestRedo`，可用态→`undoState`）。
- v1.8（2026-09-17）：`ready` 新增可选 `build` 构建指纹字段（vite define 注入），
  宿主打进 logcat 以排查 assets 产物滞后问题；协议本身无行为变化。
- v1.10（2026-09-17，**修复**）：修正 `undoState` 的判定用法。原实现（v1.7 起）写的是
  `editor.can(editor.undo)`——但 **`BlockNoteEditor` 上根本没有 `can` 方法**，运行时抛
  `TypeError: editor.can is not a function`，又被静默 `catch` 吞掉，导致 `undoState`
  **永不上行**、宿主撤销/重做按钮恒灰。改用公开 API `editor.canExec(command)`，
  命令取 `getExtension("yUndo" | "history")` 的 `undoCommand` / `redoCommand`。
  同时把 `pushUndoState` 的静默 `catch` 改为上行 `error`，让同类故障在 logcat 可见。
  协议字段与形状**无变化**（纯 JS 侧实现修复）。
- v1.11（2026-09-17）：**侧边菜单收敛为纯拖拽把手，其点击菜单 4 项移入宿主工具栏**。
  新增下行 `deleteBlock` / `setBlockColor` / `setTableHeader`，新增上行 `blockState`。

  **背景**（真机复现 + 源码核实）：`.bn-editor` 的 `padding-inline: 0`（v1.9 的改法）
  导致「+/⋮⋮ 手柄被裁」。根因是侧边菜单为 Floating UI 浮层（`placement: "left-start"`，
  portal 到 `.bn-root`，即 `.bn-editor` **之外**），其**右边缘紧贴块内容左边缘**再向左
  延伸自身宽度 W，故可见条件是 `padding-left ≥ W`；置 0 时菜单整体落在 `[−W, 0]`，
  跑到 WebView 视口左侧之外。W 的构成为 `2 × MantineActionIcon size=24 = 48px`。

  **决策**（用户）：`+` 手柄删除（其功能早已桥接到工具栏）、`⋮⋮` 手柄**保留**用于拖拽重排
  （原生手势，无法按钮化），但它的**点击菜单**（删除块 / 块颜色 / 表头行 / 表头列）
  全部移入工具栏 → 所需左侧留白由 54px 降为 **24px**（= 手柄宽 24 + 间隙 0，
  由 CSS 变量 `--bn-editor-gutter` 单点控制，值在 JS 侧由常量算出）。
  手柄因此**紧贴编辑区左边缘**（`padding-left 24 − 手柄宽 24 = 0`）；
  ⚠️ 24px 是该值的下限——再小就会裁掉嵌套列表位于 `left: -20px` 的竖向缩进线。
  右侧留白自 v1.11.3 起**与左侧取齐**（同用 `padding-inline`），
  使文本左右边缘到屏幕的距离一致；代价是内容宽度比"仅左留白"时少 24px。

  ⚠️ **`blockState` 的判定口径照抄官方**，避免"官方菜单能点、桥过来的按钮却置灰"：
  块颜色用 `blockHasType(block, ed, block.type, { textColor | backgroundColor })`
  （官方 `BlockColorsItem` 写法）；表头用 `block.type === "table" && settings.tables.headers`
  （官方 `TableHeadersItem` 写法）。
- v1.11.1（2026-09-17，**补齐断链**）：修正 `setReadOnly` **只有 JS 实现、没有 Kotlin 下发方**
  的问题。该消息自 v1 起就在协议与 `EditorApp` 里，但 controller 从未提供方法，
  于是**锁定态（`isLocked`）下正文实际仍可编辑**，且工具栏按钮照常可点。
  现补 `BlockNoteBridgeController.setReadOnly(readOnly)`，宿主在 `LaunchedEffect(isLocked)` 中下发。
  ⚠️ 只读只挡**用户输入**，挡不住 `removeBlocks` / `updateBlock` 这类程序化 API，
  故工具栏另加 `enabled = !isLocked`（整条 38% 不透明度 + `PointerEventPass.Initial`
  阶段的指针拦截）——用 `Initial` 而非 `Main` 是因为 `Initial` 阶段事件**由父流向子**，
  父级先 consume 才能拦住子按钮；`Main` 阶段子按钮早已处理完。
- v1.11.4（2026-09-18，**修复实现，协议无变化**）：修正 `setTheme.background` **运行时未生效**。
  `setTheme` 下发的 `background` 本意是让 `.bn-editor` 与宿主主题同色（v1.9），
  但 JS 侧把 `--bn-colors-editor-background` 设在了 `<html>` 上，
  而 **BlockNote 官方在 `.bn-root` 里定义过同名变量**（亮 `#fff` / 暗 `#1f1f1f`）——
  **CSS 变量就近取值**，`.bn-root` 离 `.bn-editor` 更近，其默认值直接盖过 `<html>` 的值，
  于是编辑器背景始终是纯白（真机截图中表现为文字处一条白色横带）。
  修法：CSS 在 `.bn-root`（含暗色分支）重新声明该变量为 `var(--editor-bg)`，
  JS 侧同时写到所有 `.bn-root` 元素（`"important"` 优先级）。**协议字段与形状无变化**。

  > ⚠️ **验证方式教训**：此问题**用「产物关键字计数」查不出来**（产物里一直含 `#FFFBF5`）。
  > 凡涉及「变量/样式是否真的生效」的修复，必须在真机或浏览器里读 **computed style** 才算验过
  > ——「产物里有这个字符串」与「浏览器用得上它」是两件事。
- v1.11.5（2026-09-18）：**删除 ⋮⋮ 拖拽手柄；新增下行 `moveBlockUp` / `moveBlockDown`**。

  **起因**：真机反馈「按住 ⋮⋮ 无法拖拽」。排查确认**不是本项目代码的问题，而是平台级限制**：
  BlockNote 的块拖拽纯用 **HTML5 原生 Drag & Drop**（`SideMenu.ts` 只有
  `dragstart`/`dragover`/`drop`/`dragend` + `dataTransfer`，**零 touch/pointer 处理**），
  而 **W3C 把 drag 事件定义为鼠标驱动行为**——Android WebView / Chrome for Android /
  iOS Safari 在触摸下**根本不派发这些事件**（`draggable="true"` 与否无关，
  `dataTransfer` 在触摸上下文也是空操作）。
  旁证：OpenProject 团队评估 BlockNote 时明确记录
  *"Blocknote has some features that have to be disabled in mobile (drag-and-drop, toolbar)"*。

  **决策**（用户）：既然手柄注定拖不动，留着只剩"按住没反应"的误导，故**整体删除**
  （`sideMenu={false}`，不再自渲染任何实例）；块移动改由工具栏「上移 / 下移」承担，
  走程序化 `editor.moveBlocksUp()/moveBlocksDown()`（不传 `blockIdentifier` 时取
  **选区首/末块**或**光标块**，天然支持多选一起移动与嵌套块；到首/末块时安全 no-op）。

  **连带效果**：左侧留白不再需要容纳手柄的 24px，回落到 **20px**（嵌套列表缩进线
  在 `left:-20px` 的下限，`--bn-editor-gutter` 值更新，左右同值保持对称）。

  > 注：若日后确需触屏拖拽，只能自写 touchstart/move/end 逻辑并用 `moveBlocks()` 落位，
  > 原生 DnD 事件流触屏下拿不到，无法桥接。
- v2026-09-21：**`blockState` 新增可选字段 `headingLevel`**（供宿主「标题面板」回显选中态）。

  **动机**：标题面板把工具栏原来的 9 个标题键（H1–H6 + 可折叠标题 1–3）收进一处，
  但原有 `blockState` 只上行 `blockType`（如 `heading`）、**没有级别**——
  宿主无法判断"当前块是不是 H3"，面板里九个格子只能一律不高亮。

  **口径**（JS 侧 `pushBlockState`）：
  - `blockType === "heading"` → `headingLevel = props.level`（1–6）
  - 非标题块 → `0`（宿主据此不高亮任何格子）
  - `props.level` 非有限数或 ≤ 0 时按 0 处理，避免 NaN 污染 JS 侧的 JSON 去重键

  Kotlin 侧 `BlockState` 增加 `headingLevel: Int = 0`（缺省 0 = 不高亮）。
  字段**可选**：旧 JS 产物（未带上行）时 `optInt(…, 0)` 兜底为 0，
  宿主行为退化为"无回显"，不会异常。

- v2026-09-22：**`blockState` 新增可选字段 `headingToggleable`**（修正上一版对折叠标题的误判）。

  ⚠️ **勘误**：上一版称"可折叠标题是独立块类型 `toggleHeading*`"，**这是错的**。
  BlockNote 里折叠标题 = `heading` 块 + `props.isToggleable = true`（官方斜杠菜单
  `getDefaultSlashMenuItems.ts` 即如此构造），并不存在 `toggleHeading*` 块类型。
  原口径 `blockType.startsWith("toggleHeading")` 因此**永不成立**，后果是：
  可折叠标题的格子永远点不亮，且会命中 `heading` 分支、误点亮「普通标题」的同号格子。

  **现行口径**：
  - `headingLevel = props.level`（普通与可折叠**共用**，1–6）
  - `headingToggleable = block.type === "heading" && props.isToggleable === true`
  - 两类标题级别数字重叠，**必须靠 `headingToggleable` 分流**，只按 level 无法区分

  Kotlin 侧 `BlockState` 增加 `headingToggleable: Boolean = false`，
  `HeadingPanel` 以「是否 `heading` 块」+ 该布尔字段分流两个分区。
  同样**可选**：缺失时 `optBoolean(…, false)` 兜底为普通标题，向后兼容旧产物。

- v2026-09-22（同上）：**可折叠标题的 markdown 往返编码**。

  官方 markdown 导出（`htmlToMarkdown.serializeDetails`）会把
  `<details><summary><h3>标题</h3></summary>…</details>` 削成普通 `### 标题`，
  `isToggleable` 丢失——由于本项目正文只以 markdown 持久化，折叠状态会出现
  "退出编辑页即退化"。故 JS 侧 `converter.ts` 自行编码，正文中会出现三段标记：

  ```html
  <details><summary>

  ### 标题文本

  </summary>

  子块 markdown

  </details>
  ```

  解析侧无需特殊处理：官方 markdown tokenizer 已把 `details` / `summary` 列入
  HTML 块白名单并原样透传，拼接后的 HTML 交由 DOMParser 解析时，
  `<h3>` 落在未闭合的 `<summary>` 内 → 命中 heading spec 的 DETAILS 分支
  （`{ level, isToggleable: true }`），`</summary>` 之后的块成为该块的 children。

  ⚠️ 消费方注意：这两类标记行**不含可见文字**，任何把 markdown 转纯文本的地方
  （如 Kotlin 侧 `InspirationTextUtils.markdownToPlainText`，供列表摘要 / 字数 / 搜索）
  都必须剥离 `details` / `summary` 标记行，否则会污染摘要与字数。

  **详情卡的口径**（v2026-09-22 用户确认）：Compose 详情卡**也剥**这组标记（调
  `MarkdownParser.stripStructuralMarkers`），折叠标题在详情页**显示为普通标题即可**——
  标记行整行剥掉后，标题行照常按 `### 文本` 渲染（库支持 ATX 标题 → HeadingStyle），
  详情页**不做**折叠交互。曾经实现过"详情卡可折叠"（`buildBodyToggleLayout` + Canvas 自绘箭头），
  按用户决定已回退，勿再引入。

- v2026-09-22（同上）：**行内文字色 / 背景色的渲染修复与 markdown 往返编码**。

  **渲染侧**（A 面板「选中文字色 / 选中背景色」为何点了没反应）：
  `format("textColor" | "backgroundColor", "#RRGGBB")` 的通道本身是通的、mark 也确实写进
  了文档，但**看不见**——官方 color 样式是 `createStyleSpec` 型（定义了 `addMarkView`），
  而 Tiptap 的 `EditorView` 会无条件启用 mark views，编辑器内渲染走 spec 的 `render()`；
  官方 `render()` 只造**裸 span**（不设任何颜色，颜色只写在 `toExternalHTML()` 里），
  颜色因而**完全依赖 CSS 的 9 条预设色名规则**
  （`Block.css` 的 `[data-style-type="textColor"][data-value="gray"…"pink"]`）。
  宿主下发的是**自由 hex**，没有任何规则命中 → 视觉零变化。

  对照：同面板「段落文字色 / 段落背景色」传的是**色名**且走块 props
  （`data-text-color` / `data-background-color`），因此正常。

  **修法**：JS 侧 `editor/schema.ts` **同名覆盖** `textColor` / `backgroundColor`，
  在 `render()` 里把值直接写进**内联 style**（与项目 fontSize 样式同一机制），
  并把 `parse` 读到的值做**形态归一**（`rgb(r,g,b)` → 大写 `#RRGGBB`）——
  否则第二轮保存时编码判定会因值形态变化而失效。

  **持久化侧**：官方 markdown 导出**主动剥掉颜色 span**
  （`htmlToMarkdown.serializeInlineContent` 的 `case "span"`）。故 `converter.ts`
  采用与折叠标题同一套 token 手法：导出前把带色 text 片段拆成
  `@@@CORGI_IC_TC_<值>@@@` + 原文 + `@@@CORGI_IC_END@@@`（背景色用 `BG_` 前缀），
  导出后反向替换为**原生行内 HTML**：

  ```html
  <span style="color:#FF9A5C">文字</span>
  ```

  解析侧无需改动：官方 markdown tokenizer 的 `tryInlineHtml` 会原样透传行内标签，
  再交由本项目覆盖后的 color 样式 `parse` 还原。

  ⚠️ 消费方注意：行内 `<span …>` **行内出现**（不像折叠标题独占一行），
  纯文本转换必须只删标签本体、**保留标签之间的文字**
  （Kotlin 侧 `markdownToPlainText` 已加规则）。

  **块级色（段落文字色 / 段落背景色）的持久化**：同属"进不了 markdown"——
  块级色是块 props，官方导出把它写成块元素的 `data-text-color` /
  `data-background-color` 属性，而 `htmlToMarkdown` 的块序列化器只读结构与
  inline 内容、**不读元素属性**。

  这里刻意**不用 HTML 承载**（与行内色不同）：块级属性必须贴在"块自己的标签"上
  （`<p data-text-color="red">`），而该块导出成 `p` / `h2` / `li` / `blockquote`
  哪一种无法预知；外包 `<div data-…>` 又会在解析时被当作不匹配元素**下钻丢弃**，
  属性照样丢。故改用**块内容行首的纯文本 token**：

  ```
  @@@CORGI_BC_TC_red@@@段落文本
  ```

  - 导出：把 token 插到该块 inline 内容最前面，并剥掉原 props；
  - 载入：解析后从句首剥离 token，写回块 props 并删除该文本
    （必须在装载编辑器**之前**完成，否则 token 会闪现）。

  好处是**不需要任何 CSS**，块级色继续走官方渲染路径，语义无损
  （含"背景色铺满整块"这一块级特性）。

  ⚠️ 消费方注意：这类 token 是**纯文本**、会留在库里的 markdown 原文中，
  纯文本转换必须整段移除 `@@@CORGI_…@@@`（Kotlin 侧 `markdownToPlainText`
  已按统一前缀通配剥离），否则会污染摘要与正文字数。

- v2026-09-22（同上）：**`contentFormat` 的消费方共三处，新增内部标记必须全部覆盖**。

  同一份正文 markdown 有三个不同渲染/抽取路径，漏掉任何一处就会在对应界面上泄漏内部标记
  （本日连踩两次：先漏列表摘要，再漏详情卡正文）：

  | # | 消费方 | 处理方式 |
  |---|---|---|
  | 1 | **WebView 编辑器**（`assets/blocknote-web/editor`） | `converter.ts` 负责编码与**解码**：`@@@CORGI_…@@@` 在载入时被消费（块级色写回 props、折叠标题还原为块），token 不进文档 |
  | 2 | **Compose 详情卡正文**（`InspirationViewCard` → 只读 `RichText`） | 逐段 `MarkdownParser.extractBlockColorMarkers()` **解析并消费**块级色 token（映射成 Compose 色 → 段的 `textColor` / `backgroundColor`），再 `stripStructuralMarkers()` 剥掉 `<details>` 标记行；**保留 `style` span**（排版来源）。勾选回写时用 `leadingInternalTokens()` 把段首 token 补回，避免动数据 |
  | 3 | **纯文本抽取**（列表摘要 / 字数 / 搜索 / 分享） | `MarkdownParser.toPlainText()`（剥 markdown 语法 + HTML 标签 + 内部 token + 折叠标记行） |

  ⚠️ 第 2 与第 3 的差别：**块级色 token 在第 2 处是"载荷"（要被解析）、在第 3 处是"噪声"（要被丢弃）**；
  行内 `<span style>` 在第 2 处必须保留（排版来源）、在第 3 处要剥标签留文字。
  其余结构标记的剥离同源（都走 `MarkdownParser.stripStructuralMarkers`）。

  **块级色为什么必须靠 token 过桥**：块级色是块 props，官方 markdown 导出只把它写成块元素的
  `data-text-color` / `data-background-color` 属性，而 markdown 的块序列化器**不读元素属性**
  —— 属性在导出瞬间即丢失，token 是唯一载体。故**任何"读 markdown 展示"的新出口，
  都必须解析这两个 token**，只剥不解析就等于丢色（详情页曾经如此：段落文字色/背景色完全不显示）。

  **色名 → 颜色的映射**：`BlockColorPalette`（宿主硬编码，明暗两套，与 `defaultColors.ts` 逐条对应）。
  ⚠️ 必须传入**当前主题**的 `isDark`，且未知色名要返回 null（不覆盖）而不是 `Color.Transparent`
  —— 否则会把文字染成全透明（表现为"文字消失"）。

- v2026-09-22（同上）：**`blockState` 新增可选字段 `inlineTextColor` / `inlineBackgroundColor`**
  （A 面板两个「选中色」行的选中回显）。

  **动机**：这两行此前传 `showSelection = false`——因为当时没有行内色的状态上行，
  宿主拿不到"选区已有的行内色"，若强行回显会让「默认」项恒定高亮造成误导。
  用户要求"色块按钮应该有选中高亮，默认第一个「/」块高亮"，故补上行并按需回显。

  **口径**：JS 侧直接取 `getActiveStyles().textColor` / `.backgroundColor` 的**原始字符串**
  （即宿主下发的自由 hex，也可能是粘贴内容带来的色名 / `rgb()`）。
  宿主在面板内按**当前主题**色板反查色名以点亮色点：

  | 上行值 | 宿主表现 |
  |---|---|
  | 空 / 缺失 | 视为「默认」→ 高亮第一个「/」清除块 |
  | 命中色板 | 高亮对应色点 |
  | 不在色板（外部粘贴的自定义色、或切主题后色板值已变） | **无高亮**（不谎报成「默认」） |

  Kotlin 侧 `BlockState` 增加 `inlineTextColor` / `inlineBackgroundColor: String = ""`，
  反查函数为 `blockColorNameOf`（`blockColorHexOf` 的反函数）。回显时机沿用 `blockState`
  既有的上行契机（选区变化 / 内容变化），无需新增通道。

- v2026-09-22（同上）：**修复摘要漏出内部标记，并统一"markdown → 纯文本"入口**。

  **症状**：列表页 / 详情卡摘要出现 `@@@CORGI_BC_TC_red@@@` 字面量。

  **根因**（两处口径并存 + 历史脏数据）：
  - 灵感正文的纯文本字段 `Inspiration.content` 由**保存流程**写入，而该处调的是
    `MarkdownParser.stripMarkdown`——它**只处理 markdown 语法**，既不剥 HTML 标签、
    也不剥本项目自编码的 `@@@CORGI_…@@@` 占位 token；
  - 卡片摘要优先读 `content`（非空时不再走 `markdownToPlainText` 兜底），
    于是污染值直接上了卡面；
  - **代码修好也不会自动清洗历史数据**，用户必须逐条重存才能消除。

  **修法**：
  - 完整口径下沉为 `MarkdownParser.toPlainText(markdown)`（**单点真相**），
    `InspirationTextUtils.markdownToPlainText` 改为薄委托；
  - 保存路径（`InspirationEditViewModel`）与启动回填路径
    （`InspirationRepository.repairInspirationPlainText`）统一改调 `toPlainText`；
  - 回填函数顺势扩权：除"content 为空"外，**同时清洗已污染的 content**
    （判定线索 `@@@CORGI_` / `<span`，保守起见只看本项目独有的串），幂等可重复执行。

  ⚠️ 待办正文有独立口径（`#标签` 文字必须保留），**不要**把待办的 `stripMarkdown`
  调用一并换成本方法。

- v2026-09-21（同日续）：**`blockState` 新增可选字段 `fontSizePx`**（供 H 面板「正文字号」档位回显）。

  **动机**：字号档位回显原先从 `richTextState.currentSpanStyle` 派生——那是旧
  Compose 编辑器的状态，正文迁 WebView 后恒 Unspecified，高亮永远停在默认档 16
  （用户观察："点字号档连选中态高亮都没有"）。现由 JS 侧 `pushBlockState` 带上
  `getActiveStyles().fontSize`（`"18px"` 形式）解析的整数，0 = 无样式；
  Kotlin `BlockState.fontSizeSp` 接收（`optInt(…, 0)` 兜底，旧产物兼容），
  Screen 的 `currentFontSizeSp` 改从 blockState 派生（0 → 回落默认档 16）。

  ⚠️ 行内样式的**选区语义**：光标无选区时 `addStyles({fontSize})` 只设置
  "输入中样式"（影响后续输入的文字），**已显示的正文不变**——与加粗/斜体等
  行内格式一致；要改已有文字的字号需先选中文字。

- v2026-09-21（同日续 2）：**新增下行 `setBaseFontSize` 与上行 `baseFontSize`**
  （「正文字号」双语义，用户决策：无选区点档位 = 改**全局正文基础字号**）。

  - 下行 `setBaseFontSize { fontSizePx }`：宿主启动装载（SettingsViewModel 从偏好
    读出）与内存态变化时下发；JS 写入 CSS 变量 `--bn-editor-base-font-size`
    （editor.css 的 `.bn-default-styles`/`.bn-editor` 消费——必须打穿库在
    `.bn-default-styles` 上的字面量 `font-size:16px`），作用于所有未叠加
    行内 fontSize 样式的文字；行内样式仍可按文字覆盖（CSS 继承 < 行内 style）。
  - 上行 `baseFontSize { fontSizePx }`：JS 侧 `case "fontSize"` 按
    `prosemirrorView.state.selection.empty` 分流——无选区时改基础字号并上行，
    宿主经 `BodyFontSizeManager`（内存）+ SharedPreferences（持久化）落库，
    再由 Screen 的响应式下发幂等确认。
  - 持久化为 **App 级排版偏好**（`body_font_size_px`，默认 16），非灵感实体字段。

- v2026-09-22：**新增下行 `focusEditor` 与上行 `editorFocus`**（面板收起后按需恢复软键盘）。

  - 背景：三个面板展开期间正文被 `suppressIme` 抑制（键盘不弹），但**光标一直在**正文里。
    收起面板后用户看到的仍是闪烁光标，却要再点一次正文才拿得回键盘——多余的一步。
  - 上行 `editorFocus { focused }`：JS 侧 document 级 `focusin`/`focusout` 监听，
    判 `document.activeElement` 是否落在 `.bn-editor` 内，翻转才上行；
    Kotlin 侧落到 `BlockNoteBridgeController.editorFocused`（Compose 快照态）。
  - 下行 `focusEditor {}`：宿主收起面板后、调 `showSoftInput` **之前**下发，
    让 `.bn-editor` 重新聚焦（Chromium 才建立输入连接）。
  - 宿主侧顺序（Kotlin `BlockNoteBridgeController.restoreIme`）：
    面板 `openPanel = null` → `suppressIme` 回 false（解除 `inputmode="none"`）
    → 延迟 `IME_RESTORE_DELAY_MS`(180ms) → `focusEditor` → `requestFocus`
    → `InputMethodManager.restartInput` + `showSoftInput`。
    ⚠️ **`WebSettings` 上没有 `keyboardDisplayRequiresUserGesture`**（v2026-09-22 编译报错实测）：
    android.jar（API 35）里与键盘相关的只有 `MediaPlaybackRequiresUserGesture`，该语义属于别的
    平台/别的 WebView 封装。弹键盘只能靠 IMM 显式 `showSoftInput`（直接请求输入法，
    不受 WebView 对用户手势的策略约束），JS `focus()` 只是保证"有输入连接可建"。
  - 判据优先级：正文 `editorFocused` → `restoreIme()`；否则标题 `isTitleFocused`
    → `SoftwareKeyboardController.show()`；都没有则不弹（无光标就不抢键盘）。

- v2026-09-22：**`blockState` 新增可选字段 `bold` / `italic` / `underline` / `strike` /
  `textAlignment`**（底部工具栏 B / I / U / S 与三个对齐按钮的选中态高亮）。

  **根因**（与 Nest/Unnest 同一类"宿主本地镜像不可信"）：
  - I / U / S 原读 `richTextState.currentSpanStyle`，B（单档模式）与三个对齐按钮
    **根本没接 `isActive`**。BlockNote 接管正文后内容只存在于 ProseMirror 文档树，
    宿主那份 `RichTextState` 只是"聚焦块 / 首块"的本地镜像、`currentSpanStyle` **恒空**
    → 四个按钮永不点亮，三个对齐按钮更是永远不高亮。
  - 现改由 JS 侧 `pushBlockState` 上行。⚠️ **维度不同，不能一起取**：
    `bold` / `italic` / `underline` / `strike` 是**行内样式**（`getActiveStyles()`，
    归一为布尔）；`textAlignment` 是**块级 prop**（`props.textAlignment`），
    非字符串时回落 `"left"`——各 block spec 的默认值就是 `"left"`，
    故"未设置"与"旧产物未下发"都表现为**左对齐按钮亮**（用户要求：默认左对齐高亮）。
  - Kotlin 侧 `BlockState` 增加对应字段（`isBold` / `isItalic` / `isUnderline` /
    `isStrikethrough` / `textAlignment = "left"`）；**空串必须归一回 `"left"`**
    （`optString` 对缺失字段返回 `""`，而 `""` 不等于任何合法值 → 三个按钮会全灭）。

  ⚠️ **`toggleStyles` 在光标态（无选区）不产生文档变更**：它只改 ProseMirror 的
  **stored marks**，`onChange` 不触发 → 上行不刷新 → 表现为「点了加粗但 B 不高亮」。
  故 JS 侧在这四个 `case` 后**主动 `pushBlockState()`**（有选区时本就触发，
  重复调用被 `lastBlockStateRef` 去重吸收）。同一手法 `fontSize` 分支早已在用。

  ⚠️ 宿主侧判据走**显式的 `useBlockNote: Boolean`**（v2026-09-22 从 `boldSingleTier`
  拆出）：后者只描述"B 按钮是单档还是字重菜单"，不兼作模式判据。

- v2026-09-22（同上）：**新增下行 `requestBlockState`**（面板收起时主动刷一次块状态）。

  宿主在「T / H / A」面板**收起**的副作用里下发（与"恢复软键盘"同一时机，**先刷状态**），
  避免面板期间的状态变化因去重 / 时序没上行而让工具栏高亮"滞后一拍"。
  Kotlin 侧方法为 `BlockNoteBridgeController.refreshBlockState()`。
  无副作用、可重复调用（JS 侧仍按内容去重）。

- v2026-09-22（同上）：**`ready` 新增可选字段 `srcHash`**（源码内容哈希）
  + 产物校验脚本 + pre-commit 钩子（详见「构建指纹与产物校验」章）。

  哈希由 `vite.editor.config.ts` 的 `collectSrcHash()` 计算并注入 `__SRC_HASH__`
  （`bn-src:` + sha256 前 12 位），`bridge.ts` 导出 `SRC_HASH` 随 `ready` 上行，
  Kotlin 侧打进 logcat（`ready received | build=… | src=bn-src:…`）。
  校验脚本 `scripts/check-blocknote-artifact.ps1` 同口径现算比对，
  钩子模板 `scripts/git-hooks/pre-commit` 在提交涉及 `blocknote-probe/src/` 时自动拦截
  「源码改了但产物没重建」。

