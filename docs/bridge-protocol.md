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

## 上行消息（JS → Kotlin）

| type | 载荷 | 说明 |
|---|---|---|
| `ready` | `{}` | 编辑器脚本就绪并已绑定下行宿主（Kotlin 侧解除 loading、随后发 `init`） |
| `changed` | `{ markdown }` | 内容变更快照；**JS 侧防抖 800ms**；由 `blocksToMd` 生成（含分割线样式编码） |
| `undoState` | `{ canUndo, canRedo }` | 撤销/重做可用态（v1.7）；历史栈变化后上报，宿主左上角按钮据此置灰 |
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
```

## 构建指纹（v1.8）

`assets/blocknote-web/editor/editor.html` 是**静态资源**——Gradle 只负责原样打包，**不会**触发
npm/vite 重建。因此「JS 源码改了但真机行为没变」的根因通常就是产物没重建（真机已踩过一次）。

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
# D BlockNoteEditor: ready received | build=2026-09-17 18:20:31 a1b2c3d
```

比对这里的 build 与 `git log -1 -- blocknote-probe/src` 的 commit 是否一致，即可判定产物新鲜度。

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
