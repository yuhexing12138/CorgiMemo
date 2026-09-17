# CorgiMemo 项目长期记忆

## 项目约定
- **不主动编译**（除用户明确要求）；**无 BuildConfig**（版本走 `getPackageInfo().versionName`）。
- 依赖签名核对：项目技能 `gradle-cache-source-lookup`（`find_sources_jar.py --class <裸类名>` / `extract_source.py`）。
- 检索 `.workbuddy`/`.gradle`：Glob `path` 放绝对路径，`pattern` 只写相对通配。
- PowerShell 输出可能被吞 → 优先 Read/Glob/Grep；Bash 工具在本机 `ls/head/find` 不可用，须用绝对路径调 python。
- 设计稿 Ardot fileId 707225018209249。
- ⚠️ `Color.isSpecified` 在 `androidx.compose.ui.graphics`（**不是** `ui.unit`）；`TextUnit.isSpecified` 才在 `ui.unit`。

## 字体体系
- 9 OFL 中文 + 3 拉丁；`FontCatalog`/`FontManager`/`buildTypography`，默认=系统默认。
- **预览铁律**：统一 `FontPreviewEngine`（有界池+位图 LruCache），**禁用 `ResourcesCompat.getFont`/`Text(fontFamily)` 批量渲染**（全局缓存驻留→OOM）。
- 合成族按字重 `combinedFamilyFonts`（`Typeface.Builder(latin).addCustomFallback(cjk)`）。作用域解耦：设置字体管 App chrome；用户内容走 `ContentFontManager`+`LocalContentTypography`。

## 主题色（`ui/theme/Color.kt`）
- 六色主题（orange/pink/green/blue/purple/brown），`getColorScheme(themeColor, darkTheme)`。
- 亮色 `background` = 暖米色系（orange `#FFFBF5`），`surface` = `Color.White`；暗色 `background` 各异（orange `#1A0F08`）。
- ⚠️ **内容区背景 ≠ 主题 background**：`InspirationEditScreen` 的 `contentBackgroundColor` 默认 `Color.Transparent`
  （未自选背景色时），所以**给子组件传背景色必须做 Transparent → `MaterialTheme.colorScheme.background` 的回落**。

## 编辑态块（路线 4）
- `BodyBlocksEditor` UI 已下线（正文改 BlockNote WebView）；`BodyBlocksController` 仅保留**数据层**
  （图片备注/缩放持久化、旧数据媒体迁移、语音 token、图片删除、`focusedOrFirstTextState` 激活态回显）。
- **新增 sealed 块子类必须全项目 Grep `is BodyBlock.` 补穷尽 when**。
- 非文本块点选铁律：**焦点必须留在 Text 块**（cursorColor=Transparent），夺焦点=软键盘消失。
- 复选框 `checked:Boolean?`；GFM 前缀只在块边界处理、绝不进 RichTextState；缩进=布局级（`indentLevel` 1..6 + EM 前缀）。

### 图片块
- 撑满=fillMaxWidth+aspectRatio(真实比例)；进程级 `ImageAspectRatioCache` 防重载高度塌陷；ReorderableColumn 必须传块 id 做 itemKey。
- **选中工具栏必须 Popup 独立窗口**（focusable=false, clippingEnabled=false）；退场动画需延迟 ≥动画时长再卸载。
- 图片间必须空 Text 块；空块序列化 `EMPTY_BLOCK_PLACEHOLDER`（NBSP）。
- **载体空块不变量**：`BodyBlock.Text.isImageSeparator` 显式标记；合法位置 =「紧邻至少一个不可输入块且不与另一载体相邻」；
  载体并排⇒整串全灭→补插收敛。⚠️ **身份与内容绑定**：载体标记在块一有内容时即刻清掉（`demoteImageSeparatorIfFilled()`）。
- **分割线五按钮工具条**：虚线/波浪/上插/下插/删除，宽 256dp。`DividerStyle` ↔ markdown `"---"`/`"--- dashed"`/`"--- wavy"`
  （`parseDividerStyle` 严格全段匹配）。`afterCommandMutation` 会清点选态 → toggle 内先存 tapX、命令后立即恢复。

### TaskList 行级渲染（v2026-09-16 定稿，提交 f631159..f386b1e）
- **结构**：单段落 + 段内 `\n`（方案 D）；`checked`=行 0，`checkedLines: Map<Int,Boolean>`=行 ≥1。
- **渲染**：`ModifierExt` 对 CheckBox 传**段落全 range**；`drawCustomStyle` 按 `\n` 分行（行盒 = `getBoundingBoxes(lineStart,+1)`）。
- **命中**：命中=**行首**（段首或前字符 `\n`）；翻转走 `withCheckedLines` **换新实例**（deepCopy 必须带行级状态）。
- **对账**：`reconcileCheckedLines`——行数变⇒清 checkedLines（行 0 保留）；重建 marker 后**必须立即恢复 textRange**。
- **parser 往返**：编码逐行前缀；解码连续**同层级**任务行并段续行；普通列表项行恢复分段。
- 已知取舍：增删 `\n` 清行级状态；段末空行不画框；跨行样式被前缀截断。

## 块级拖拽重排（自维护 fork）
- `ui/components/reorderable/BlocksReorderableList.kt`（fork 自 `sh.calvin.reorderable:3.1.0`）。唯一改动：`settle()` 改「抓快照→立即 onSettle→滑行交 `BlocksGlideController` 接续」。
- **拖拽期间绝不能改列表**（库 intervals 定长）。`itemKey` 身兼组合身份锚定+滑行归属+zIndex。

## 视觉/渲染教训
- **alpha 动画必裁布局边界外绘制** → 悬浮元素不要挂 shadow/dropShadow（被切）；定版用 1dp 黑 25% 外边框。
- `Modifier.shadow` 在 scale+alpha 动画中出方角阴影。`animateContentSize` 内部 `clipToBounds()` 持续裁剪。
- ⚠️ **`verticalScroll` 会把子项高度约束改成 `Constraints.Infinity`** → 内部 `fillMaxSize()` 失效，子组件只能按内容高撑开。
  要在滚动容器内给子组件保底高度，必须由宿主传 `heightIn(min=...)`，且子组件**不要再追加 `fillMaxSize()`**（会覆盖宿主意图）。
- ⚠️⚠️ **无限高约束会"穿透多层"逐级吞掉宿主意图（2026-09-17 实测，改一层不够）**：
  `host(verticalScroll) → 外层Box → 内层AndroidView` 这种嵌套里，只要**任意一层**还写着 `fillMaxSize()`，
  该层在无限高约束下解不出有限高度 → 退化为"按子内容包装" → 把外层顶到内容高 → 宿主的 `heightIn(min)` 被静默吞掉。
  **判据**：宿主传了 `heightIn(min)` 但视觉没生效时，逐层 grep `fillMaxSize`，全部改为 `fillMaxWidth()`。
  自维护组件「不 fill 高度」的约定，**要在每一层都写，不能只写外层**——首版只在 Box 上去掉 `fillMaxSize`，真机仍没占满。

## ⚠️ Compose 高频陷阱
- **`remember { }` 的 calculation lambda 不是 `@Composable`**：在里面读 `MaterialTheme.colorScheme.*` /
  `LocalXxx.current` 等组合属性 → 报 `@Composable invocations can only happen from the context of a @Composable function`。
  **迷惑点：key 位置是合法的**——`remember(MaterialTheme.colorScheme.background) { ... }` 能编过，
  因为 key 在组合期求值；只有**大括号内**非法。修法：把组合读取提到 `remember` 外部存成普通局部变量：
  ```kotlin
  val themeBg = MaterialTheme.colorScheme.background          // 组合期读取 ✅
  val x = remember(userColor, themeBg) { if (...) themeBg else userColor }  // 纯计算 ✅
  ```
  同类：`derivedStateOf { }` 的 lambda、`LaunchedEffect { }` 里读组合属性（后者虽能编过但语义错——不会随主题重组）。
- **`remember(key)` 的 key 不要传"每次重组都是新实例"的对象**（如新建的 lambda、`listOf(...)`），会导致缓存永久失效。

## 图片附件页（`InspirationImageGallery`，全屏沉浸预览）
- `MainActivity` 已声明 `configChanges`（不含 uiMode）→ 旋转不重建，改 `requestedOrientation`。
- ⚠️ **缩放与翻页按指针数分流**：双指始终缩放并 consume；单指仅 `scale>1f` 时 consume 用于平移，否则放行 Pager。
- **缩放锚点**：`offset_new = d − (d − offset_old)×ratio`；回弹=scale+offset 同一 progress；区间 0.6~4f。
- ⚠️ **越界=实时跟手翻页+兜底橡皮筋**：到边界位移 1:1 喂 `pagerState.dispatchRawDelta`，Pager 吃不下才橡皮筋（上限 72dp）。
  `pan` 直接累加，**绝不再加「上一帧派发量」**（正反馈死锁）；结算目标页用**本页索引 `page`** 而非 `currentPage`。
- ⚠️ **缩放期间绝不夹紧边界**（缩小必越界，否则锚点白算）；手势结束再做「越界收回」。
- **窗口/insets**：`LocalView.current` 在 Dialog content 非 `DialogLayout` → 沿父链 `findDialogWindow()`（tailrec）。
  系统栏：竖屏始终显示，横屏双通道 hide。边距 300ms 补间。Pager 阈值：横屏 0.08，竖屏 0.35。

## BlockNote WebView 编辑器（迁移 P1.5+）
- 资源：`app/src/main/assets/blocknote-web/editor/editor.html`（`viteSingleFile` 内联单文件，约 1.88MB）。
  **源码在 `blocknote-probe/src/editor/`，改完必须重建**。自 v1.8 起 `:app:buildBlockNoteEditor` 已接入 Gradle
  （`merge*Assets` 依赖它，带增量），`assembleDebug/Release` 会自动带上最新产物。
  手动重建：`cd blocknote-probe && npm run build:editor`（缺省走 `vite.editor.config.ts`，输出到 assets）。
  同名 `BlockNoteEditorScreen.kt` 是独立探针页，与灵感编辑页共用的 `BlockNoteEditorWebView.kt` 是两套实现，勿混。
- ⚠️⚠️ **「JS 改了但真机没生效」第一反应就是产物没重建**：Gradle 把 `assets/` 当静态资源原样打包，不触发 npm/vite。
  - 快速判据：`git log -1 -- <源码>` vs `git log -1 -- <产物>`，产物提交时间落后 = 没重建。
  - ⚠️ 产物 diff 显示「N 增 N 删」是**假象**（vite 重排压缩短变量名，语义等价）。
    **绝不能用 diff 行数判断产物是否刷新，要用关键字计数**。
  - 仍不生效再排 WebView 缓存 → 卸载重装或清应用数据。
- **构建指纹（v1.8）**：vite `define` 注入 `__BUILD_FINGERPRINT__`（`<构建时间> <commit 短hash><-dirty?>`），
  随 `ready` 上行，宿主打 logcat `ready received | build=...`。**排查产物新鲜度直接看这行**。
- Bridge：下行 `evaluateJavascript("window.BlockNoteEditorHost.onMessage(<json>)")`；
  上行 `AndroidBridge.postMessage(json)`（匿名对象，只这一个方法）。协议见 `docs/bridge-protocol.md`。
  上行 `ready`/`changed`/`error`/`undoState`。
- BlockNote 版本 0.52.1。**`BlockNoteEditor` 没有 transaction 事件**（`extends EventEmitter<{create: void}>`）。
- ⚠️ **撤销/重做可用态用公开 API `editor.can(editor.undo / editor.redo)`**：`StateManager.can()` 置 `isInCan`
  使 `exec()` 走 `canExec()` **只判定不 dispatch**，不污染历史栈。别读 `_tiptapEditor`。
- **v1.7**：JS 侧自绘撤销/重做按钮已删除；唯一入口是宿主顶栏图标按钮，可用态经 `undoState` 驱动置灰。
  **BlockNote 官方从无撤销/重做 UI，`BlockNoteView` 没有关闭它的配置——只能删自绘代码。**
- **v1.8 长按连发**：`ui/components/LongPressRepeatIconButton.kt` 导出
  `Modifier.longPressRepeat(onAction, enabled, canRepeat)` 与 `LongPressRepeatIconButton(...)` 薄封装。
  已接入：顶栏撤销/重做、格式栏 Nest/UnNest。新增连发按钮只需给 `RiFormatButton` 传 `canRepeat`。

### ⚠️ BlockNote 官方样式约束（v1.9 实测，改前必看）
- `.bn-editor { padding-inline: 54px }`（`@blocknote/core`）——给侧边 `+`/`⋮⋮` 手柄留的**绘制位**。
  桌面端是居中窄栏（~780px）所以不明显；**手机全宽下左右各 54px ≈ 吞掉 30% 宽度**。
  修复：`editor.css` 里 `.bn-editor { padding-inline: 0 !important }`。
- `.bn-editor { background-color: var(--bn-colors-editor-background) }`（`@blocknote/react`），
  默认值亮色 `#ffffff` / 暗色 `#1f1f1f` → **与宿主暖米色主题不一致时形成"白底圆角卡片"画中画**。
  修复：JS 侧 `documentElement.style.setProperty('--bn-colors-editor-background', bg)` + `html/body` 底色，
  同时 `.bn-editor { border-radius: 0 !important }`（圆角是"居中卡片"语义，全宽下不需要）。
- `.editor-page` 原有 `max-width: 780px; margin: 0 auto` 属桌面居中窄栏写法，移动端全宽须去掉。
- 探针页 `src/probe.css` 的 `body { background: #f5f5f7 }` **会被 EditorApp 一并 import** →
  必须改成 `transparent`，否则污染正式编辑器背景。
- **首帧防闪白**：`editor.html` 的 `<head>` 内联 `html,body { background-color: #fffbf5 }`，
  React 挂载后由 JS 立即接管为主题实际色。
- **`ThemePayload` 自 v1.9 增可选 `background?: string`**（宿主编辑区背景 hex）；
  缺省时 JS 回落 `white`/`#1f1f1f`，向后兼容旧宿主。

## 工具/协作教训
- ⚠️ 连续 2 次「猜测→改码→失败」后，停止猜测、加埋点取真实数据。
- **同一文件多次 Edit 必须串行**（并行写竞态）。
- 提交：中文提交信息，Write 写临时文件后提交再删除。

### ⚠️ 删文件/裁 import 的隐身依赖（2026-09-17 实测踩坑，一次翻车 20+ 错误）
**靠「符号名 grep/词频」判断 import 是否可删，方法论上必然出错**——Kotlin 存在**无名字依赖**：
1. **委托操作符 `by`**：`var x by mutableStateOf(0)` 隐式调用 `getValue`/`setValue`，源码里这两个名字**一次都不出现**。
   误删 → 报 `Type 'MutableState<T>' has no method 'getValue'/'setValue' ... cannot serve as a delegate`，
   还**级联**出十几处假象错误 `Cannot infer type for T/R`（真正根因只有这一个，别去逐个查级联点）。
   同类：`by lazy`、`by remember`、`Delegates.observable`。
2. **跨文件 `internal` 顶层函数/扩展**：如 `internal fun DrawScope.drawDashedDivider(...)` 定义在 A 文件、
   被 B 文件**同包无 import 调用**。删 A 文件 → B 报 `Unresolved reference`（B 的 import 列表里没有它）。
3. **`LocalXxx` 组合局部变量**：如 `val textToolbar = LocalTextToolbar.current`，即使调用点只剩 `.hide()` 也仍需该 import。
4. **`import X as Y` 别名**：以**别名**（而非原名）被引用，按原名 grep 会漏。

**正确姿势（按序执行）**：
- 裁 import 前，**先按 `by ` 反查全文**（`grep -n "by mutableStateOf\|by lazy\|by remember"`），命中文件必留 `getValue`/`setValue`。
- **删任何文件/大段代码后，必须反向全项目 grep 被删文件里的顶层声明名**（`internal`/`public` 函数、类、常量），
  而不是只 grep 文件名。尤其是 `internal fun ...Scope.xxx` 这类扩展。
- 最终以**编译**为准，静态分析只能缩小范围。
- 反向验证：扫出「大写开头但未 import 也未本地声明」的符号清单，逐个确认来源。
