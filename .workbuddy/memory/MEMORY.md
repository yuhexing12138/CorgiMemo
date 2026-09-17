# CorgiMemo 项目长期记忆

## 项目约定
- **不主动编译**（除用户明确要求）；**无 BuildConfig**（版本走 `getPackageInfo().versionName`）。
- 依赖签名核对：项目技能 `gradle-cache-source-lookup`（`find_sources_jar.py --class <裸类名>` / `extract_source.py`）。
- 检索 `.workbuddy`/`.gradle`：Glob `path` 放绝对路径，`pattern` 只写相对通配。
- 设计稿 Ardot fileId 707225018209249；块方案 `docs/路线4-块级图片-实施方案.md`。

## 字体体系
- 9 OFL 中文 + 3 拉丁；`FontCatalog`/`FontManager`/`buildTypography`，默认=系统默认。
- **预览铁律**：统一 `FontPreviewEngine`（有界池+位图 LruCache），**禁用 `ResourcesCompat.getFont`/`Text(fontFamily)` 批量渲染**（全局缓存驻留→OOM）。
- 合成族按字重 `combinedFamilyFonts`（`Typeface.Builder(latin).addCustomFallback(cjk)`）。作用域解耦：设置字体管 App chrome；用户内容走 `ContentFontManager`+`LocalContentTypography`。

## 编辑态块（路线 4）
- `BodyBlocksEditor`：每块一个 RichTextEditor；撤销=自建 Command 栈（块级）+库内 history（块内）。**新增 sealed 块子类必须全项目 Grep `is BodyBlock.` 补穷尽 when**。
- 块内容左右缘 ±16dp 双向同步。
- **跨帧焦点迁移**：focusSpec 只写 pendingFocus、下一帧 requestFocus；删除时记 `hideCursorUntilFocusBlockId` 防中间帧光标闪现。
- 非文本块点选铁律：**焦点必须留在 Text 块**（cursorColor=Transparent），夺焦点=软键盘消失。删除走高亮态悬浮 Popup。
- 复选框 `checked:Boolean?`；GFM 前缀只在块边界处理、绝不进 RichTextState；勾选=就地换块对象保引用。缩进=布局级（`indentLevel` 1..6 + EM 前缀）。

### 图片块
- 撑满=fillMaxWidth+aspectRatio(真实比例)；进程级 `ImageAspectRatioCache` 防重载高度塌陷。ReorderableColumn 必须传块 id 做 itemKey。
- **选中工具栏必须 Popup 独立窗口**（focusable=false, clippingEnabled=false）；退场动画需延迟 ≥动画时长再卸载（否则被截断）。
- 图片间必须空 Text 块（拖拽打包 CompositeCommand 一步撤销）；空块序列化 `EMPTY_BLOCK_PLACEHOLDER`（NBSP）。
- **载体空块不变量（v2026-09-11 懒插入改版 v3）**：`BodyBlock.Text.isImageSeparator` 显式标记；**首/尾图前/后默认无载体**，合法位置判据 =「**紧邻至少一个不可输入块（图/线）且不与另一载体相邻**」——覆盖两图之间/边缘/分割线上下；载体并排⇒整串全灭→补插收敛（两图直接相邻才补、恒一行）。`insertEdgeSeparator(head)` 边缘 tap 条懒插入（守卫：边缘是载体→聚焦；图/线→插入；否则无操作）；分割线工具条 `toggleDividerNeighborSeparator` 上/下 toggle 载体行。命令（Insert/RemoveImageSeparatorCommand）都可撤销。
  - ⚠️ **身份与内容绑定**：载体标记在块一有内容时即刻清掉（`demoteImageSeparatorIfFilled()`）。任何"自动占位块身份"都必须与"是否仍为空"绑定。
  - 用户手打空白块（无标记）永不触碰。
  - ⚠️ **分割线五按钮工具条（v2026-09-11 样式切换扩容）**：左→右 = Ellipsis 虚线 / Waves 波浪 / ArrowUpToLine / ArrowDownToLine / Trash2，宽 256dp（5×40+4×8+2×12）。样式模型 `DividerStyle`(SOLID/DASHED/WAVY) ↔ markdown `"---"`/`"--- dashed"`/`"--- wavy"`（`parseDividerStyle` 严格全段匹配，保守不误伤手输变体）；切换走 `UpdateDividerStyleCommand`+`toggleDividerStyle`（当前即该样式→回实线；就地换块零损失）。`afterCommandMutation`（=clearBlockSelection）会清点选态 → toggle 内先存 tapX、命令同步执行完立即恢复高亮+tapX（同帧无闪烁）；工具条 Popup 独立窗口、样式对齐图片工具条（白胶囊+黑15%边框）、激活态=图标变 DividerHighlightColor（用户定：仅变色）。线体渲染 `DividerLine`（SOLID=HorizontalDivider / DASHED=PathEffect 虚线 / WAVY=贝塞尔波浪），编辑页+阅读卡复用 `drawDashedDivider`/`drawWavyDivider`（internal 同包）。

### 跨块文字选择（v2026-09-11）【已删除】
> ⚠️ **该功能已于 2026-09-14 彻底删除**（提交 `e9abf6f7`）。用户认为"滑动拖拽选区手柄跨多个 Block 连续选中"不符合预期，已移除 `CrossBlockSelection`/`BlockLayoutInfo` 数据模型、控制器方法、`rememberCrossBlockSelectionGesture` 手势、`CrossBlockSelectionHandles` 手柄绘制、`drawBehind` 跨块高亮、`onCrossBlockSelectAll` 全选重定向，以及 12 个失效 import。下方仅作历史参考，相关代码已不在 `BodyBlocksEditor`。原生单块选区（`BasicTextField` 内置）与块级 `clearBlockSelection`（图片/分割线高亮）保留。`docs/跨块文字选择优化方案.md`、`docs/跨块选择-库层钩子能力评估与方案.md` 已孤儿化，建议归档或标 DEPRECATED。

- （历史）⚠️ 全局手势观察铁律：挂父容器 + PointerEventPass.Initial。全屏透明覆盖层（兄弟节点 + Main pass 常驻 await）实测会让子级所有点击失效（含覆盖层自己的 clickable）——禁止再用。观察阶段零消费；接管后在 Initial pass consume，子级 Main pass 见 isConsumed 自动退出。
- （历史）操作入口挂系统 TextToolbar：抬指定格主动 `textToolbar.showMenu(rect=窗口坐标)`，非null 回调决定菜单项；主动弹出的工具栏不随焦点迁移消失——控制器 `onCrossSelectionCleared` 回调 + clearCrossSelection 真清除时才 invoke → 编辑层 SideEffect 接 `textToolbar.hide()`。
- （历史）跨块选区模型/布局注册表/hitTest/富文本拼接见 `docs/跨块文字选择优化方案.md` §8。

### TaskList 行级渲染（v2026-09-16 定稿，4 提交 f631159..f386b1e）
- **结构不变**：TaskList 段落 = 单段落 + 段内 `\n`（方案 D）；勾选按行：`checked`=行 0，`checkedLines: Map<Int,Boolean>`=行 ≥1（setter 重建 startRichSpan）。
- **渲染**：`ModifierExt` 对 CheckBox 传**段落全 range**；`drawCustomStyle` 按 `\n` 分行逐行画（行盒 = `getBoundingBoxes(lineStart, +1)` 单字符 range）；x 以行 0 marker 左缘为基准。
- **命中**：`toggleTaskListCheckedAtTextOffset` 命中 = **行首**（段首或前字符 `\n`）；行号 = 段首到 offset 的 `\n` 数；翻转走 `withCheckedLines` **换新实例**（快照 deepCopy → copy 必须带行级状态）。
- **对账**：`updateAnnotatedString` 内 `reconcileCheckedLines`——行数变 ⇒ 清 checkedLines（行 0 保留）；reconcile 重建 marker 后**必须立即恢复 textRange**（否则本帧 (0,0) 错位）。
- **parser 往返**：编码逐行前缀（`- [ ] 行1\n- [x] 行2`，尾 `\n`=空项）；解码连续**同层级**任务行并段续行 + 逐行回填/剥前缀（行首 span = children 最后一个 span）；普通列表项行恢复分段（修复 `- a\n- b` 并段回归）。
- 已知取舍：增删 `\n` 清行级状态（行 0 保留）；段末空行不画框；跨行样式被前缀截断；文字降级仍整段按行 0。App 侧零行级逻辑（`toggleCheckboxAtFocused` = 整块 toggleTaskList）。

## 块级拖拽重排（自维护 fork）
- `ui/components/reorderable/BlocksReorderableList.kt`（fork 自 `sh.calvin.reorderable:3.1.0`）。唯一改动：`settle()` 改「抓快照→立即 onSettle→滑行交 `BlocksGlideController` 接续」。
- **拖拽期间绝不能改列表**（库 intervals 定长）。`itemKey` 身兼组合身份锚定+滑行归属+zIndex。

## 视觉/渲染教训
- **alpha 动画必裁布局边界外绘制** → 悬浮元素不要挂 shadow/dropShadow（被切）；定版用 1dp 黑 25% 外边框。
- `Modifier.shadow` 在 scale+alpha 动画中出方角阴影。`animateContentSize` 内部 `clipToBounds()` 持续裁剪。
- SwipeableImageStack：可见深度锁 4，旋转角 -(M-1)*15，收起按钮半胶囊吸附时间线。

## 图片附件页（`InspirationImageGallery`，全屏沉浸预览）
- `MainActivity` 已声明 `configChanges`（不含 uiMode）→ 旋转不重建，改 `requestedOrientation`。横屏=真旋转窗口（`LANDSCAPE`/退出用 `PORTRAIT`）。
- ⚠️ **缩放与翻页按指针数分流**：双指始终缩放并 consume；单指仅 `scale>1f` 时 consume 用于平移，否则放行 Pager。
- **缩放锚点**：`offset_new = d − (d − offset_old)×ratio`，`d=centroid−C`，`C=size/2`。回弹=scale+offset 同一 progress 驱动；缩放区间 0.6~4f。
- ⚠️ **越界=实时跟手翻页+兜底橡皮筋**：到边界位移 1:1 喂 `pagerState.dispatchRawDelta`，Pager 吃不下才橡皮筋（上限 72dp）。`pan` 直接累加，**绝不再加「上一帧派发量」**（正反馈死锁）；0.9 页封顶加在累计 `scr` 上；翻页中把图像冻结在出发边界；交给 Pager 的量扣掉「图片让位」；结算目标页用**本页索引 `page`** 而非 `currentPage`。
- ⚠️ **缩放期间绝不夹紧边界**（缩小必越界，否则锚点白算）；手势结束再做「越界收回」。
- **窗口/insets**：`LocalView.current` 在 Dialog content 非 `DialogLayout` → 沿父链 `findDialogWindow()`（tailrec）。insets 从 `activity.window.decorView` 读；旋转后高频重读。系统栏：竖屏始终显示，横屏双通道 hide。边距 300ms 补间。
- Pager 阈值：横屏 0.08，竖屏 0.35。顶栏 `ChromeTopGapFromStatusBar=12.dp`。`VoicePreviewDialog` 与本页共用 `findDialogWindow()`。

## BlockNote WebView 编辑器（迁移 P1.5+）
- 资源：`app/src/main/assets/blocknote-web/editor/editor.html`（1.8MB `viteSingleFile` 内联单文件，`file:///android_asset/...`）。
  **源码在 `blocknote-probe/src/editor/`，改完必须 `cd blocknote-probe && npm run build:editor` 才生效**（输出目录写在 `vite.editor.config.ts`）。
  同名 `BlockNoteEditorScreen.kt` 是独立探针页，与灵感编辑页共用的 `BlockNoteEditorWebView.kt` 是两套实现，勿混。
- Bridge：下行 `evaluateJavascript("window.BlockNoteEditorHost.onMessage(<json>)")`；上行 `AndroidBridge.postMessage(json)`（匿名对象，只这一个方法）。
  协议见 `docs/bridge-protocol.md`。上行 `ready`/`changed`/`error`/`undoState`。
- BlockNote 版本 0.52.1。**`BlockNoteEditor` 没有 transaction 事件**（`extends EventEmitter<{create: void}>` 仅 `create`）。
- ⚠️ **撤销/重做可用态用公开 API `editor.can(editor.undo / editor.redo)`**：`StateManager.can()` 置 `isInCan`
  使 `exec()` 走 `canExec()` **只判定不 dispatch**，不污染历史栈。别读 `_tiptapEditor`（私有字段）、别扫自定义调用点。
- **v1.7（2026-09-17）**：JS 侧自绘的 `.editor-toolbar`「↺ 撤销/↻ 重做」胶囊按钮（`AutoRepeatButton`）**已删除**；
  唯一入口是宿主顶栏 `InspirationEditScreen` 的 ↶/↷ 图标按钮，可用态经 `undoState` → `BlockNoteBridgeController.canUndo/canRedo`（mutableStateOf）驱动置灰。
  原「长按连发」未在 Compose 侧复刻。**BlockNote 官方从无撤销/重做 UI，`BlockNoteView` 没有关闭它的配置——只能删自绘代码。**

## 工具/协作教训
- ⚠️ 连续 2 次「猜测→改码→失败」后，停止猜测、加埋点取真实数据。
- **同一文件多次 Edit 必须串行**（并行写竞态）。
- 提交：中文提交信息，Write 写临时文件后提交再删除。
