# 灵感编辑页撤销栈方案A重建（v2026-09-02）

## 完成事项

把灵感编辑页撤销架构从「统一时间线快照栈」改为**方案A：自建 Command 命令栈 + 库内 history，两套历史隔离**。

## 核心架构

**两套互相隔离的历史**：
- **全局命令栈**（`BodyBlocksController.undoCommands` / `redoCommands`，`ArrayDeque<BodyBlocksCommand>`）——只存操作增量 Command，不存全量快照。管块的增删、拖拽排序、图片块属性编辑。
- **块内富文本 history**（compose-rich-editor 库自带 `RichTextState.history`）——每个 Text 块的打字/加粗/样式自己管自己，**不进全局栈**。

**统一调度入口** `BodyBlocksController.undo()` / `redo()`（焦点判断是核心）：
```
聚焦块（未聚焦回退首块）的 history.canUndo 非空 → 先回退块内文字
块内回退完 → 走全局命令栈
```

## Command 体系

新增于 `BodyBlocksEditor.kt` 文件头：

| 类型 | 覆盖场景 |
|---|---|
| `BlockSpec`（TextSpec/ImageSpec） | Command 的轻量重建载荷，**绝不持 Bitmap**（坑点2）。ImageSpec 只存 uri，将来裁剪/缩放/备注三件套沿用此模式 |
| `FocusSpec` | 块 id + 块内有效文本偏移（剥 ZWSP 坐标系） |
| `ReplaceBlocksCommand` | 把 `[index, index+n)` 区间替换为 inserted 序列——覆盖插图拆块、Enter 拆块、粘贴归一化、退格合并、删图、首空块删除全部形态 |
| `MoveBlockCommand` | 拖拽排序（blockId + fromIndex + toIndex） |
| `UpdateImageBlockCommand` | 图片属性编辑模板（当前无 UI 调用，作三件套扩展点） |
| `CompositeCommand` | 复合命令——批量插图（坑点5），将来批量删除 |

**落盘原语**：`replaceBlockRange` / `rebuildBlock`（id 复用保证焦点稳定）/ `drainBlockHistory`（revert 前把产物块库内 history 撤到底，避免静默丢弃用户文字）/ `locateRangeStart`（id 防御定位）。

## 五大坑点落地

1. **富文本内部撤销与全局撤销冲突（焦点判断）**：`undo()` 入口先查聚焦块 `history.canUndo`，空才走 `undoBlocks()`。物理键盘 Ctrl+Z/Ctrl+Shift+Z/Ctrl+Y 由 `BlockTextItem.onPreviewKeyEvent` 拦截后调同一入口（`undoBehavior = UndoBehavior.Disabled` 保持，避免库绕过调度）。
2. **ImageBlock 绝不持 Bitmap**：`BlockSpec.ImageSpec` 只存 `path: String`。
3. **拖拽压栈时机**：`BlocksReorderableColumn` 的 `onSettle`（手指抬起落定）才回调 `controller.moveBlock` → 一次拖拽恰好一条 `MoveBlockCommand`。
4. **屏幕旋转**：`BodyBlocksController` 整体由 `InspirationEditViewModel` 持有（不再 UI `remember`）。新增 `hasInitialized` 标志随 controller 存活，**旋转后不重跑 `initialize`**（避免清空命令栈）。trigger 注册一并移入 VM。
5. **复合操作**：`CompositeCommand`（apply 顺序、revert 逆序）+ `insertImagesAtFocused` 批量插图 = 一个撤销单位。

## 关键语义

- **新编辑清全局 redo**：observer 检测到非重放（`replaying=false`）的 markdown 变化 → `clearGlobalRedo()`。保证"redo 可达 ⇒ 各块当前内容 == 上次全局操作结束时的内容"，命令重放记录值因此安全。
- **replaying 标志**：Command 重放与块内 history undo/redo 期间置 true，observer 跳过结构检测（\n 拆块/退格合并）与清 redo，保留 ZWSP 维护。
- **drain 语义**：`ReplaceBlocksCommand.revert` 删除产物块前先 drain 它们的库内 history（让文字逐步回退可见），而非被块级 undo 静默丢弃。
- **ZWSP 剥离一致性**：`blockMarkdown()` 统一剥 ZWSP 作为 Command 载荷出口，重建走 `createTextBlock`（空 markdown → ZWSP 预置）。

## 文件改动

- `app/.../inspiration/components/BodyBlocksEditor.kt`（核心重构 ~700 行）：
  - 删除统一时间线快照栈（`BlockSnapshot`/`undoTimeline`/`redoTimeline`/`pushTimelineSnapshot`/`pushTextUndoSnapshot`/`captureCurrentSnapshot`/`restoreFromSnapshot`/`restoreCursor`/`applyFocusAndCursor` 旧版 等）
  - 新增 Command 体系 + 命令栈 + 落盘辅助
  - 所有结构操作入口 Command 化（插图/拆块/合并/删除/重排）
  - observer 改造：文字变化不再压全局栈，改为清全局 redo 栈；replaying 门控
  - `BlockTextItem.onPreviewKeyEvent` 加 Ctrl+Z/Ctrl+Shift+Z/Ctrl+Y 拦截
  - 删除 `rememberBodyBlocksController` 工厂（controller 移入 VM）
  - 删除无调用的 `focusAtOffset`、`specOf`
  - 新增 `hasInitialized` 标志
- `app/.../viewmodel/InspirationEditViewModel.kt`：
  - 新增 `val bodyBlocks: BodyBlocksController`（构造时创建 + trigger 注册移入）
  - 新增 import：Trigger/SpanStyle/FontWeight/Color/BodyBlocksController
  - 更新撤销架构注释（344 行附近）
- `app/.../inspiration/InspirationEditScreen.kt`：
  - `rememberBodyBlocksController(...)` → `viewModel.bodyBlocks`
  - 撤销/重做按钮：`canUndoTimeline/undoTimeline()` → `canUndo/undo()`
  - 初始化守卫 `hasInitializedWithData`（remember）→ `bodyBlocks.hasInitialized`（controller 持有）
  - 删除已无代码使用的 `Trigger` import
  - 更新 Undo/Redo 状态说明注释、格式化操作注释、voice trigger 注释

## 已知语义边界（方案A固有，文档已注明）

- **块级 undo 丢弃"非聚焦块"未撤销的文字编辑**：如聚焦块 B 时按撤销到全局栈，C 块里的未撤销字会被 revert 替换。这是 Command 增量栈 vs 快照栈的本质差异（ProseMirror 用 steps rebasing 解决，本项目不引入）。`drainBlockHistory` 缓解了"聚焦块未撤销"场景；非聚焦块边缘场景需用户先聚焦再撤销。
- **setMarkdown 清块内 history**：块级 Command 用 `setMarkdown` 恢复块时会清该块的库内 history。这是两套历史隔离的固有语义——块级操作后局部文字历史失效（业界常见行为）。

## 未编译

按用户约定，未运行 gradle 构建。
