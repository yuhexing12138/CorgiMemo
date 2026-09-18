# CorgiMemo 项目长期记忆

## 项目约定与工具链
- **不主动编译**（除明确要求）；**无 BuildConfig**（版本走 `getPackageInfo().versionName`）。
- 依赖签名核对：技能 `gradle-cache-source-lookup`（`find_sources_jar.py --class <裸类名>` / `extract_source.py`）。
- 检索 `.workbuddy`/`.gradle`：Glob `path` 放绝对路径，`pattern` 只写相对通配。
- 本机坑：PowerShell stdout 可能被吞 → 重定向落盘再 Read，或优先 Read/Glob/Grep；
  Bash 工具 `ls/head/find/grep/wc` 全 Exit 127 → 用绝对路径调 python；
  `rm` 被 safe-delete 拦截 → `[System.IO.File]::Delete()`。
- 设计稿 Ardot fileId 707225018209249。
- ⚠️ **顶层扩展属性的包归属最容易猜错**：`Color.isSpecified` 在 `androidx.compose.ui.graphics`
  （**不是** `ui.unit`）；`TextUnit.isSpecified` 才在 `ui.unit`。判据：`inline val X.isY` 的 import 包
  = 其声明文件的 `package`，**与类型名无关**。

## 字体体系
- 9 OFL 中文 + 3 拉丁；`FontCatalog`/`FontManager`/`buildTypography`，默认=系统默认。
- **预览铁律**：统一 `FontPreviewEngine`（有界池+位图 LruCache），**禁用 `ResourcesCompat.getFont`/`Text(fontFamily)` 批量渲染**（全局缓存驻留→OOM）。
- 合成族按字重 `combinedFamilyFonts`（`Typeface.Builder(latin).addCustomFallback(cjk)`）。
  作用域解耦：设置字体管 App chrome；用户内容走 `ContentFontManager`+`LocalContentTypography`。

## 主题色（`ui/theme/Color.kt`）
- 六色主题（orange/pink/green/blue/purple/brown），`getColorScheme(themeColor, darkTheme)`。
- 亮色 `background` = 暖米色系（orange `#FFFBF5`），`surface` = `Color.White`；暗色 `background` 各异。
- ⚠️ **内容区背景 ≠ 主题 background**。`InspirationEditScreen` 收敛为三个语义显式的值：
  `userPickedBackgroundColor`（只答"用户选了什么"）/ `contentBackgroundColor`（**唯一真值**：实际生效色，
  Transparent → 主题 background 回落，用于**告知子组件**）/ `contentBackgroundPaint`（绘制层真值，
  用于宿主 Column `.background()`，让主题背景可透出）。**别用一个变量兼两种含义**（会漂移）。

## 编辑态块（路线 4）
- `BodyBlocksEditor` UI 已下线（正文改 BlockNote WebView）；`BodyBlocksController` 仅保留**数据层**
  （图片备注/缩放持久化、旧数据媒体迁移、语音 token、图片删除、`focusedOrFirstTextState` 激活态回显）。
- **新增 sealed 块子类必须全项目 Grep `is BodyBlock.` 补穷尽 when**。
- 非文本块点选铁律：**焦点必须留在 Text 块**（cursorColor=Transparent），夺焦点=软键盘消失。
- 复选框 `checked:Boolean?`；GFM 前缀只在块边界处理、绝不进 RichTextState；缩进=布局级（`indentLevel` 1..6 + EM 前缀）。

### 图片块
- 撑满=fillMaxWidth+aspectRatio(真实比例)；进程级 `ImageAspectRatioCache` 防重载高度塌陷；ReorderableColumn 必须传块 id 做 itemKey。
- **选中工具栏必须 Popup 独立窗口**（focusable=false, clippingEnabled=false）；退场动画需延迟 ≥动画时长再卸载。
- 图片间必须空 Text 块；空块序列化 `EMPTY_BLOCK_PLACEHOLDER`（NBSP，**判空用 `isBlank()`**）。
- **载体空块不变量**：`BodyBlock.Text.isImageSeparator`；合法位置=「紧邻至少一个不可输入块且不与另一载体相邻」；
  并排⇒整串全灭→补插收敛。⚠️ 身份与内容绑定：一旦有内容即刻清标记（`demoteImageSeparatorIfFilled()`）。
- **分割线五按钮工具条**：虚线/波浪/上插/下插/删除，宽 256dp。`DividerStyle` ↔ markdown
  `"---"`/`"--- dashed"`/`"--- wavy"`（`parseDividerStyle` 严格全段匹配）。
  `afterCommandMutation` 会清点选态 → toggle 内先存 tapX、命令后立即恢复。

### TaskList 行级渲染（v2026-09-16 定稿，f631159..f386b1e）
- **结构**：单段落 + 段内 `\n`（方案 D）；`checked`=行 0，`checkedLines: Map<Int,Boolean>`=行 ≥1。
- **渲染**：`ModifierExt` 对 CheckBox 传**段落全 range**；`drawCustomStyle` 按 `\n` 分行（行盒=`getBoundingBoxes(lineStart,+1)`）。
- **命中**：命中=**行首**（段首或前字符 `\n`）；翻转走 `withCheckedLines` **换新实例**（deepCopy 必须带行级状态）。
- **对账**：`reconcileCheckedLines`——行数变⇒清 checkedLines（行 0 保留）；重建 marker 后**必须立即恢复 textRange**。
- **parser 往返**：编码逐行前缀；解码连续**同层级**任务行并段续行；普通列表项行恢复分段。
- 已知取舍：增删 `\n` 清行级状态；段末空行不画框；跨行样式被前缀截断。

## 块级拖拽重排（自维护 fork）
- `ui/components/reorderable/BlocksReorderableList.kt`（fork 自 `sh.calvin.reorderable:3.1.0`）。
  唯一改动：`settle()` 改「抓快照→立即 onSettle→滑行交 `BlocksGlideController` 接续」。
- **拖拽期间绝不能改列表**（库 intervals 定长）。`itemKey` 身兼组合身份锚定+滑行归属+zIndex。

## 视觉/渲染教训
- **alpha 动画必裁布局边界外绘制** → 悬浮元素不要挂 shadow/dropShadow（被切）；定版用 1dp 黑 25% 外边框。
- `Modifier.shadow` 在 scale+alpha 动画中出方角阴影。`animateContentSize` 内部 `clipToBounds()` 持续裁剪。
- ⚠️⚠️ **无限高约束会"穿透多层"逐级吞掉宿主意图**：`host(verticalScroll) → 外层Box → 内层AndroidView`
  这种嵌套里，只要**任意一层**写着 `fillMaxSize()`，该层在无限高下解不出有限高度 → 退化为"按子内容包装"
  → 把外层顶到内容高 → 宿主 `heightIn(min)` 被**静默吞掉**（不报错）。
  **判据**：宿主传了 `heightIn(min)` 但视觉没生效 → 逐层 grep `fillMaxSize`，全改 `fillMaxWidth()`。
  自维护组件「不 fill 高度」的约定**要在每一层都写**，只写外层无效（真机实测）。

## ⚠️ Compose 高频陷阱
- **`remember { }` 的 calculation lambda 不是 `@Composable`**：里面读 `MaterialTheme.colorScheme.*` /
  `LocalXxx.current` → 报 `@Composable invocations can only happen from...`。
  **迷惑点：key 位置合法**（`remember(MaterialTheme.colorScheme.background) { ... }` 能编过，key 在组合期求值），
  只有**大括号内**非法。修法：把组合读取提到 `remember` 外存成局部变量。
  同类：`derivedStateOf { }` 的 lambda、`LaunchedEffect { }` 里读组合属性（能编过但语义错——不随主题重组）。
- **`remember(key)` 的 key 不要传"每次重组都是新实例"的对象**（新建 lambda、`listOf(...)`）→ 缓存永久失效。

## 图片附件页（`InspirationImageGallery`）
- `MainActivity` 已声明 `configChanges`（不含 uiMode）→ 旋转不重建，改 `requestedOrientation`。
- ⚠️ **缩放与翻页按指针数分流**：双指始终缩放并 consume；单指仅 `scale>1f` 时 consume 用于平移，否则放行 Pager。
- **缩放锚点**：`offset_new = d − (d − offset_old)×ratio`；回弹=scale+offset 同一 progress；区间 0.6~4f。
- ⚠️ **越界=实时跟手翻页+兜底橡皮筋**：边界位移 1:1 喂 `pagerState.dispatchRawDelta`，吃不下才橡皮筋（≤72dp）。
  `pan` 直接累加，**绝不再加「上一帧派发量」**（正反馈死锁）；结算目标页用**本页索引 `page`** 而非 `currentPage`。
- ⚠️ **缩放期间绝不夹紧边界**（缩小必越界，否则锚点白算）；手势结束再做「越界收回」。
- **窗口/insets**：`LocalView.current` 在 Dialog content 非 `DialogLayout` → 沿父链 `findDialogWindow()`（tailrec）。
  系统栏：竖屏始终显示，横屏双通道 hide。边距 300ms 补间。Pager 阈值：横屏 0.08，竖屏 0.35。

## BlockNote WebView 编辑器（迁移 P1.5+）
- 资源 `app/src/main/assets/blocknote-web/editor/editor.html`（`viteSingleFile` 内联单文件 ~1.88MB）。
  **源码在 `blocknote-probe/src/editor/`，改完必须重建**。自 v1.8 起 `:app:buildBlockNoteEditor` 已接入 Gradle
  （`merge*Assets` 依赖它，带增量），`assembleDebug/Release` 自动带最新产物。
  手动重建：`cd blocknote-probe && npm run build:editor`。同名 `BlockNoteEditorScreen.kt` 是独立探针页，
  与灵感编辑页共用的 `BlockNoteEditorWebView.kt` 是两套实现，**勿混**。
- ⚠️⚠️ **「JS 改了但真机没生效」第一反应就是产物没重建**（Gradle 把 assets 当静态资源，不触发 npm/vite）。
  - 判据：`git log -1 -- <源码>` vs `git log -1 -- <产物>`，产物落后=没重建。
  - ⚠️ 产物 diff 显示「N 增 N 删」是**假象**（vite 重排压缩短变量名）。**绝不用 diff 行数判断，要用关键字计数**。
  - 仍不生效再排 WebView 缓存 → 卸载重装或清应用数据。
- **构建指纹（v1.8）**：vite `define` 注入 `__BUILD_FINGERPRINT__`（`<构建时间> <commit 短hash><-dirty?>`），
  随 `ready` 上行，宿主打 logcat `ready received | build=...`。**排查产物新鲜度直接看这行**。
- Bridge：下行 `evaluateJavascript("window.BlockNoteEditorHost.onMessage(<json>)")`；
  上行 `AndroidBridge.postMessage(json)`。协议见 `docs/bridge-protocol.md`（`ready`/`changed`/`error`/`undoState`）。
- BlockNote 版本 0.52.1。**`BlockNoteEditor` 没有 transaction 事件**（`extends EventEmitter<{create: void}>`）。
- ⚠️⚠️ **撤销/重做可用态只能用 `editor.canExec(command)`——`editor.can` 不存在！**
  `BlockNoteEditor` 原型上只有 `exec` / `canExec`，`StateManager.can(cb)` **从未转发到 editor**。
  任何 `editor.can(x)` 都是运行时 `TypeError: editor.can is not a function`（v1.7→v1.10 按钮恒灰的真根因）。
  正确写法：`editor.canExec(getExtension("yUndo") ?? getExtension("history")) 的 undoCommand/redoCommand`
  —— `canExec` 内部以 `dispatch === undefined` 调命令，**只判定不 dispatch**，不污染历史栈。
  ⚠️ 命令必须从扩展取，**不能传 `editor.undo`**（丢 `this`）也**不能传 `@tiptap/pm/history` 的裸 `undo`**
  （`can(cb)` 是无参 `cb()`，state 为 undefined → `historyKey.getState(undefined)` 抛错）。
  **别读 `_tiptapEditor`**。`canExec` 在 `editor.transact()` 回调内会抛。
- **v1.7**：JS 侧自绘撤销/重做按钮已删除；唯一入口是宿主顶栏图标按钮，可用态经 `undoState` 驱动置灰。
  **BlockNote 官方从无撤销/重做 UI，`BlockNoteView` 没有关闭它的配置——只能删自绘代码。**
- **v1.8 长按连发**：`ui/components/LongPressRepeatIconButton.kt` 导出
  `Modifier.longPressRepeat(onAction, enabled, canRepeat)` 与 `LongPressRepeatIconButton(...)` 薄封装。
  已接入：顶栏撤销/重做、格式栏 Nest/UnNest。新增连发按钮只需给 `RiFormatButton` 传 `canRepeat`。

### ⚠️ BlockNote 官方样式约束（v1.9 实测，改前必看）
- `.bn-editor { padding-inline: 54px }`（`@blocknote/core`）——给侧边 `+`/`⋮⋮` 手柄留的**绘制位**。
  桌面端窄栏（780px）不明显；**手机全宽下左右各 54px ≈ 吞掉 30%，且右侧那 54px 无任何功能**。
- ⚠️⚠️ **侧边菜单几何约束（v1.11 读源码确证，改 padding 前必看）**：
  菜单 = `AddBlockButton`+`DragHandleButton`，`gap={0}`，每按钮 `MantineActionIcon size={24}`
  → **总宽 48px**（官方 54 = 48 + 6 间隙，正好吻合）。
  它是 Floating UI 浮层（`placement:"left-start"`，portal 到 `.bn-root`，在 `.bn-editor` **之外**），
  **右边缘紧贴块内容左边缘**再向左延伸，故 `菜单左边缘 = padding-left − W`，
  **可见条件 `padding-left ≥ W`**。
  → `padding-inline: 0` 会让菜单整体落到视口左侧之外（**v1.9 的真实回归，真机已复现**）；
  `16px + translateX(-16px)` 之类"退路"更糟（完全不可见）——**勿再采用**。
  留白过小还会连带裁掉嵌套列表竖线（`left:-20px`）与 toggle 添加按钮（`margin-left:22px`）。
- **v1.11 定案**：`+` 删除（功能早已桥接工具栏），`⋮⋮` **保留**（拖拽重排是原生手势，无法按钮化），
  但其**点击菜单 4 项移入工具栏** → 宽度 48→24px，留白改
  `padding-inline-start: var(--bn-side-menu-gutter, 24px) !important` + `padding-inline-end: 0 !important`
  （变量值在 JS 侧由常量算出，CSS 无魔法数字）。
  ⚠️ **24px 是该值的下限**：嵌套列表竖线在 `left:-20px`、toggle 添加按钮 `margin-left:22px`，
  手柄本身已占 24px，故间隙常量只能为 0、不能为负（v1.11.2 已按用户要求收紧到 0）。
  实现细节**详见 `docs/bridge-protocol.md` v1.11 与
  `docs/BlockNote编辑器占满与背景色修复方案.md` §3.8** —— 含三条必知坑：①关默认菜单用官方开关
  `sideMenu={false}` 再自渲染 `SideMenuController`；②自定义菜单**必须复用官方 `SideMenu` 容器**
  （它算 `data-block-type`/`data-level` 供样式表对齐块高，自绘会让手柄在标题/图片上**垂直错位**）；
  ③禁用点击菜单须传 `dragHandleMenu={() => null}`（内部是 `|| DragHandleMenu`，不传会回落官方菜单）。
  另：`setBlockColor` 写**块 props**，与 `format` 的 `textColor`（行内 span）是**不同维度**。
- `.bn-editor { background-color: var(--bn-colors-editor-background) }`（`@blocknote/react`），默认亮 `#ffffff` /
  暗 `#1f1f1f` → 与宿主暖米色不一致时形成"白底圆角卡片"画中画。修复：JS 侧
  `documentElement.style.setProperty('--bn-colors-editor-background', bg)` + `html/body` 底色 +
  `.bn-editor { border-radius: 0 !important }`（圆角是"居中卡片"语义，全宽下不需要）。
- `.editor-page` 原有 `max-width: 780px; margin: 0 auto` 属桌面窄栏写法，移动端全宽须去掉。
- 探针页 `src/probe.css` 的 `body { background: #f5f5f7 }` **会被 EditorApp 一并 import** → 必须改 `transparent`。
- **首帧防闪白**：`editor.html` 的 `<head>` 内联 `html,body { background-color: #fffbf5 }`，React 挂载后 JS 接管。
- **`ThemePayload` 自 v1.9 增可选 `background?: string`**；缺省时 JS 回落 `white`/`#1f1f1f`，向后兼容旧宿主。

## 工具/协作教训
- ⚠️ **不要在未验证「方法是否存在」的前提下论证它的语义**（2026-09-17 实测翻车）：
  花力气论证 `can(cb)` 的参数语义，却没跑一句 `grep 'public can' <库文件>`。
  **遇到「API 看起来应该有但没反应」，第一步查它在不在，而不是猜它怎么工作。**
- ⚠️ **`catch { return; }` 是隐形杀手**：任何「静默失败 + 上层无从感知」的 catch 都应至少上行一次诊断。
- 连续 2 次「猜测→改码→失败」后，停止猜测、加埋点取真实数据。
- **同一文件多次 Edit 必须串行**（并行写竞态）。
- 提交：中文提交信息，Write 写临时文件后提交再删除。

### ⚠️ 删文件/裁 import 的隐身依赖（2026-09-17 实测，一次翻车 20+ 错误）
**靠「符号名 grep/词频」判断 import 是否可删，方法论上必然出错**——Kotlin 存在**无名字依赖**：
1. **委托操作符 `by`**：`var x by mutableStateOf(0)` 隐式调用 `getValue`/`setValue`，源码里这两个名字**一次都不出现**。
   误删 → 报 `MutableState<T> has no method 'getValue'/'setValue'`，还**级联**出十几处假象错误
   `Cannot infer type for T/R`（真根因只有这一个，别逐个查级联点）。同类：`by lazy`、`by remember`、`Delegates.observable`。
2. **跨文件 `internal` 顶层函数/扩展**：如 `internal fun DrawScope.drawDashedDivider(...)` 定义在 A 文件、
   被 B 文件**同包无 import 调用**。删 A → B 报 `Unresolved reference`（B 的 import 列表里没有它）。
3. **`LocalXxx` 组合局部变量**：即使调用点只剩 `.hide()` 也仍需该 import。
4. **`import X as Y` 别名**：以**别名**被引用，按原名 grep 会漏。

**正确姿势**：裁 import 前先按 `by ` 反查全文（`grep -n "by mutableStateOf\|by lazy\|by remember"`），
命中文件必留 `getValue`/`setValue`；**删任何文件/大段代码后必须反向全项目 grep 被删文件里的顶层声明名**
（尤其 `internal fun ...Scope.xxx`），而非只 grep 文件名；最终以**编译**为准，
静态分析只能缩小范围（可扫「大写开头但未 import 也未本地声明」的符号逐个确认来源）。
