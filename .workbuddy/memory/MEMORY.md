# CorgiMemo 项目长期记忆

## 项目约定
- **不需要自动编译**：完成 Kotlin/代码改动后，不要主动运行 `./gradlew` 编译或构建验证（Gradle 在本机常因文件写权限 `AccessDeniedException` 失败）；除非用户明确要求，跳过构建。

## 关键组件：SwipeableImageStack（灵感页堆叠图）
- 可见深度 `visibleDepth: Int = 4`；`M = minOf(visibleDepth, cardCount).coerceIn(1,4)` 把可见深度上限锁 4。
- 扇形四要素（x/y/scale/rotation）均按 `ei = min(stackIndex, M-1)` 夹取，超出 M 的卡片叠栈底。
- 旋转角由可见张数派生：`effectiveTiltAngle = -(M-1)*15`（M=1→0°、2→-15°、3→-30°、4→-45°）；旧原型 `preview_stack.html` 用 120dp/−60° 已过时，源码展开态视觉边长 S≈150dp、fanAngle=−45°。
- 展开态「收起」按钮：半胶囊形，高度与「展开」按钮宽度动态相等，吸附**时间线竖线左缘 77dp**（相切、圆头朝外 `RoundedCornerShape(topStart=H/2,topEnd=0,bottomEnd=0,bottomStart=H/2)`）；水平 offset `(StackLeftCompensation - 11.dp - collapseBtnW)*(1-p)`；垂直 `topCardAnchorY + cardHeight/2 - H/2`。
- 因祖先 `animateContentSize` 的 clipToBounds 会裁掉移出 Stage 左缘的按钮，采用「Stage 左扩 `collapseBtnLeftExtend*(1-p)` + 内容层 `+collapseBtnLeftExtend*p` 补偿」机制。
- 调用方 `TimelineInspirationItem.kt` 图片外层 Box 挂 `pointerInput(detectTapGestures(onTap={}))` 兜底整行空白吞噬；组件内 Stage 层保留接住「屏宽-36~屏宽」段。

## 编辑态内联图片 / 语音（v2026-08-30 内联方案）
- 编辑态 `BasicRichTextEditor`（基于 `BasicTextField`）Compose 1.11 无 `inlineContent` 参数，问号方块根因；只读态 `BasicRichText` 才支持。库内 `InlineImageOverlay.kt` 已覆盖层方案：`resolveInlineImagePlacements`→`ApplyInlineImageSizes`→`drawInlineImages`。
- 图片是 `RichSpanStyle.Image`（非 token）；编辑态点击预览：`TokenInteractionHandlers.kt` 新增 `ImageClickHandler`+`LocalImageClickHandler`，`InlineImageOverlay.kt` 新增 `findInlineImagePlacementAt`+`pointerInputInlineImages`，App 侧 `InspirationEditScreen` 注入 handler 打开全屏预览 Dialog。
- 语音是 `trigger:voice` 的原子 token（点击经 `LocalTokenClickHandler` 播放）；旧 `VoicePlayerComponent` 不再渲染；`saveInlineMediaBlocks` 从 markdown 反解析重建 content_blocks。
- 警惕：主仓库 `compose-rich-editor` 子模块指针可能指向已丢失提交，需重新提交指针。

## 内联图片方案路线决策（2026-09-01 核实，详见 docs/路线2-WebView-H5编辑器-可行性分析报告-v2.md）
- **架构性天花板（最重要）**：Compose BOM 2026.04.01（= 1.11）的 `BasicTextField` **至今无 `inlineContent`**。社区绕行（隐藏文本 + decorationBox 叠 `Text(inlineContent=…)` + VisualTransformation）必然光标错位。→ 覆盖层方案的重叠不是普通 bug，是"覆盖层与排版引擎抢布局权"，**收敛性无保证，不再继续投时间**。只读态 `BasicRichText` 支持 inlineContent，所以详情页正常、编辑态重叠。
- **编辑器只有 Android 端**：灵感编辑页只在 `app` 模块（`InspirationEditScreen.kt`，`Column + verticalScroll`；标题编辑器 1277/1296，正文编辑器 1507）。`kuikly-shared/shared` 只有 `RouterPage`/`TodoDetailPage`/`BasePager`/`CorgiBridgeModule`，**无编辑器页面**，故"三端各写桥接"当期不成立。
- **`ReorderableColumn` 块骨架仍在**（`InspirationEditScreen.kt:1393`）：`items` 恒空、`when` 只剩兜底分支。恢复块级图片的成本比从零低。
- ~~推荐方案排序~~ → **已决策（2026-09-01）：走路线 4，块级图片 + 图文交错；语音保持 `trigger:voice` 内联不动。** 方案见 `docs/路线4-块级图片-实施方案.md`（**首轮已实现，待编译联调**）。"句子中间插图"保留扩展接口（序列化协议 + 渲染策略），暂不实现。
- **首轮实现（2026-09-01）**：新文件 `app/.../inspiration/components/BodyBlocksEditor.kt`；用户确认 **Enter 拆块**（段落级块，软键盘走 snapshotFlow 差分 + 硬键盘 onPreviewKeyEvent 拦截；`toMarkdown(TextRange)` 做光标→markdown 精确映射）+ **每块可拖拽**（侧边手柄长按拖拽，避免与文本长按选择冲突）。Room 一次加全预留字段（`blockId/textContent/note/cropRect/originalPath/displayWidthRatio`），`MIGRATION_56_57`、version 57。屏幕重接线：兼容层 `val richTextState get() = bodyBlocks.focusedOrFirstTextState()` 让旧引用继续工作；保存链路零改动（VM `_richTextState` 不再注入 → saveInspiration 走 `_contentFormat` 回退，onDocChanged 用 **SideEffect** 赋值保证迁移内容不漏同步）。
- **撤销栈（v2026-09-02 方案A：自建 Command 命令栈 + 库内 history，两套历史隔离）**：controller 内 `undoCommands`/`redoCommands`（`ArrayDeque<BodyBlocksCommand>`，100 条）只存操作增量 Command 不存全量快照——管块的增删/拖拽排序/图片块属性；**块内富文本**复用 compose-rich-editor 库自带 `RichTextState.history`，不进全局栈。统一调度入口 `undo()`/`redo()`：聚焦块（未聚焦回退首块）`history.canUndo` 非空 → 先回退块内文字；空则走命令栈（焦点判断是核心，避免按撤销时而回退文字、时而回退块操作）。Command 体系：`BlockSpec`（TextSpec/ImageSpec，**绝不持 Bitmap**——只存 markdown/path）/`FocusSpec`/`ReplaceBlocksCommand`（区间替换，覆盖插图拆块/Enter拆块/粘贴归一化/退格合并/删图/首空块删除）/`MoveBlockCommand`/`UpdateImageBlockCommand`（图片属性三件套模板，当前无调用方）/`CompositeCommand`（批量插图=一个撤销单位）。`BodyBlocksController` 整体由 `InspirationEditViewModel` 持有（**坑点4：屏幕旋转不丢命令栈**，不再 UI remember；新增 `hasInitialized` 标志防旋转重跑 initialize 清空栈）。物理键盘 Ctrl+Z/Ctrl+Shift+Z/Ctrl+Y 由 BlockTextItem.onPreviewKeyEvent 拦截调同一入口（`undoBehavior=UndoBehavior.Disabled` 保持）。**已知语义边界（方案A固有）**：块级 undo 会丢弃"非聚焦块"未撤销的文字编辑（Command 增量栈 vs 快照栈本质差异，ProseMirror 用 steps rebasing 解决，本项目不引入）；setMarkdown 清块内 history（apply/redo 重建文字块时局部文字历史失效——但 **revert 路径已修复**：`ReplaceBlocksCommand.apply` 暂存被替换的**原始块对象**（带 RichTextState 历史），`revert` 经 `restoreBlockRange` **原样还原**这些对象而非从 markdown 重建，从而撤销块级命令后可继续撤销命令前的打字，直至文字清空）。
- 判定依据一句话：**要"图片插在句子中间"→ 路线 2-minimal；只要求"图文交错"→ 路线 4。**
- **已知后续需求（2026-09-01 用户确认）**：图片还需**裁剪 / 缩放 / 备注**。这三项把图片语义从"行内字符"升级为"结构化媒体对象"，**路线 2 与路线 4 的差距因此显著缩小**（裁剪两边一样；缩放与备注路线 4 更直接、无需桥接）。路线 2 的独有优势收敛为唯一一条：**图片能否插在句子中间**。
- 若走路线 2 且坚持 inline：备注不能用 `figcaption`（figure 是块节点，与 inline 冲突），须用节点 `note` 属性 + 原生底部弹窗。
- 裁剪**不要覆盖原图**：存 `originalPath` + `cropRect`，否则无法"重新裁剪"。
- 当前数据模型无图片属性字段：`ContentBlock.Image(path)` 只有 path；`ContentBlockEntity` 只有 filePath/duration/orderIndex 等，扩字段需 Room migration。图片插入走 markdown 路径（`InspirationEditScreen.kt:199`）。项目无裁剪库依赖。`ImagePickerDialog` 被灵感页与待办页共用（`TodoEditScreen.kt:1655`）。
- 若走路线 2：落库改 ProseMirror JSON（消除 `saveInlineMediaBlocks` markdown 反解析这个历史 bug 源头）；POC 7 关卡，**中文 IME（G1）一票否决**；粗估 13–21 人日。
- H5 选型（若将来启用路线 2）：TipTap（ProseMirror，MIT，3.27.1/2026-06，免费版已含 `inline:true`）为本场景最优选；Slate 因 Android 支持差排除；Lexical 备选。

## 路线 4 实施方案 · 已核实的关键事实（2026-09-01）
- **旧版块结构不是"图文交错"**：`git show 2dbd5855^:.../InspirationEditScreen.kt` 的结构是 `ReorderableColumn(图片/语音块)` 在 **RichTextEditor 上方**——即"附件区 + 正文"，图片不能插在段落之间。**不能简单 revert，交错结构必须新做。**（修正了此前"恢复成本比从零低"的判断：`ReorderableColumn` 骨架可复用，但交错是新功能。）
- **可复用资产**：`InlineImagePreview.kt`、`VoicePlayerComponent.kt` 组件文件**都还在**（未删除）；`ReorderableColumn` 是 app 侧包装（`ui/components/ReorderableLazyColumn.kt:23`），底层 `sh.calvin.reorderable.ReorderableColumn`（`ReorderableList.kt:376`），泛型 + `content(index, item, isDragging)` **支持异构列表**，Text/Image 可混排。
- **必须"多编辑器"**：要图文交错，图片须能插在任意两段之间，而 BasicTextField 不支持 inlineContent → 唯一办法是正文拆成 N 个块、每块一个 RichTextEditor。最大单点改动：`InspirationEditViewModel.kt:431` 只持有**一个** `_richTextState`，须改为管理 N 个。
- **落库改造**：删掉 `saveInlineMediaBlocks`（`InspirationEditViewModel.kt:1400`）里对**图片**的正则反解析，改为遍历 contentBlocks；语音因仍内联在 Text 块内，其正则提取逻辑单独保留。
- **删除图片会物理删文件**（`removeImagePath` `:1281` → `ImageUtils.deleteImageFromInternalStorage`）。块删除若沿用会导致"删除→撤销→文件没了"，须改为延迟清理。
- 粗估 **12–18 人日**；最难的是焦点管理（建议抽 `BlockFocusManager`）。

## 已弃用工具清理（2026-08-31 完成）
- 已编辑 3 份文档移除引用 → 提交 `564e19b8` → 推送 `origin/master`，云端已无追踪引用（`git grep` 校验通过）。
- 本地残留（被 safe-delete/genie-trash 守卫拦截）：输出目录 watch 日志、虚拟环境 Scripts 下 CLI 可执行文件，须本机文件管理器删。用户 10:54 手动删除后四层验证全过。
- 遗留：git 历史对象库可能仍含旧字样（清理提交 `564e19b8` 及更早历史），属 `.git` 对象库不出现在工作区，改写风险大故不动；服务端用户配置可能仍注入旧指令（需在平台用户设置清理）。

## 资源位置
- 设计稿：Ardot `刻记+ APP 线框图与交互原型`（fileId 707225018209249）。
- 堆叠图原型：`图片库/preview_stack.html`（已本地化 vendor 依赖，可离线打开）；`designs/stack-collapse-button/collapse-button-position.html` + `overview.md`。
- 源码：`app/.../ui/components/SwipeableImageStack.kt`、`TimelineInspirationItem.kt`、`InspirationEditScreen` 等。
