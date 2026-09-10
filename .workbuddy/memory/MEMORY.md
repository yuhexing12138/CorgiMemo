# CorgiMemo 项目长期记忆

## 项目约定
- **不主动编译**：Kotlin 改动后不跑 gradlew，除非用户明确要求。
- **无 BuildConfig**：版本走 `getPackageInfo().versionName`。
- **依赖签名核对**：项目技能 `gradle-cache-source-lookup`——`find_sources_jar.py --class <裸类名>` 定位 sources jar；`extract_source.py --jar <jar> --match <路径片段> --out <目录>` 提取。
- **检索点目录**（`.workbuddy`/`.gradle`）：Glob 绝对路径放 `path`，`pattern` 只写相对通配。
- 设计稿 Ardot fileId 707225018209249；块方案文档 `docs/路线4-块级图片-实施方案.md`；子模块 compose-rich-editor 指针可能指向丢失提交。

## 正文字体体系
- 9 OFL 中文 + 3 拉丁；`FontCatalog`/`FontManager`/`buildTypography`；默认=系统默认（DEFAULT_ID="system_default"）。新增字体：拷资源→FontCatalog 登记一条。
- 预览统一 `FontPreviewEngine`（有界池+位图 LruCache）。**铁律：预览禁用 ResourcesCompat.getFont / Text(fontFamily) 批量渲染**（全局缓存驻留→OOM，曾五连崩）。
- 分离式预览：最多同时两种字体；编辑页 pending→「应用」「完成」，设置页未点「确定」返回=丢弃。
- FontFamilyResolver 进程级全局缓存；真丢弃=emptyCacheFontFamilyResolver+TypefaceCompat.clearCache()；FontResolverPolicy 按 fontCacheKey remember 隔离。
- 合成族按字重 `combinedFamilyFonts`（Typeface.Builder(latin).addCustomFallback(cjk)），否则同字重中文被拉丁覆盖回落系统。
- 作用域解耦：设置字体只管 App chrome；用户内容走 ContentFontManager+LocalContentTypography。

## 编辑态块（路线 4）
- `BodyBlocksEditor`：每块一个 RichTextEditor；撤销=自建 Command 栈（块级）+库内 history（块内），焦点判断是调度核心。**新增 sealed 块子类必须全项目 Grep `is BodyBlock.` 补穷尽 when**。
- 块内容左右缘 ±16dp：RichTextEditor contentPadding 与非文本块 BLOCK_CONTENT_PADDING 双向同步。
- **跨帧焦点迁移**：focusSpec 只写 pendingFocus、下一帧才 requestFocus；删除时记 `hideCursorUntilFocusBlockId` 防中间帧光标闪现，`onBlockFocused` 解除。

### 分割线块
- `---` 独占段；点选仅高亮；删除=空行替换+焦点落位+可撤销。
- **非文本块点选铁律**：焦点必须留在 Text 块（cursorColor=Transparent+光标安置相邻 Text 块）——软键盘只跟随聚焦编辑框，夺焦点=键盘消失。onPreInterceptKeyBeforeSoftKeyboard 只拦硬件键盘事件，拦不了 IME deleteSurroundingText。
- 删除走高亮态悬浮按钮（Popup 跟随手指 x）；点击点亮才弹按钮（highlightedTapX），退格点亮不弹（highlightForTwoStepDelete），退格高亮后再点击=补弹。
- 清高亮时机：输入变长/命令执行/点其它或已聚焦文本块（非消费 awaitFirstDown 观测按下）；退格不清。

### 复选框块
- checked:Boolean?；GFM 前缀只在块边界处理，绝不进 RichTextState；勾选=SetCheckboxCheckedCommand 就地换块对象保引用（不丢 history/光标）。
- 缩进=布局级同步：indentLevel(1..6)+EM(U+2003) 前缀（每级2个）；渲染图标与编辑器各加 (L-1)×30sp。**教训：跨排版体系对齐要改架构（同容器整体变换），别对齐外部数值**。
- 详情页 split("\n\n") 迭代原始段序列；勾选写库只读最新实体合并 contentFormat。

### 列表/缩进
- 无序列表缩进不换符号（DefaultUnorderedListStyleType 单元素 from("•")）。
- 缩进数据源三处同序：复选框块（含组合态）优先读 indentLevel，纯列表才读库层级。
- 回车续行层级显式传 spec.listLevel，不依赖 markdown 前缀往返（rebuildBlock 会剥前缀）。
- **孤立列表行加载必剥前缀**：层级前缀 ≥4 空格被 CommonMark 解析成代码块。App 语义永远 spec 显式携带。
- marker 宽度缓存进程级共享（SharedStartTextWidthCache）。**教训：需跨块延续的渲染态必须进程级共享**（每 block 一个 state，库内 per-state 缓存会丢）。

### 图片块
- 撑满：fillMaxWidth+aspectRatio(真实比例)，撑满态内部不加水平 padding；进程级 ImageAspectRatioCache 防重载高度塌陷。
- ReorderableColumn 必须传块 id 做 itemKey，否则交换后槽位复用→图片重载回弹。
- **选中工具栏必须 Popup 独立窗口**（focusable=false, clippingEnabled=false），布局内渲染被后续兄弟块盖住。动画两陷阱：①首组合 visible 已 true 丢 enter→先 false 一帧再 true；②visible=false 直接卸载截断 exit→延迟 350ms 卸载。
- 缩小/恢复动画 widthFraction 真实缩放（animateContentSize 会 clipToBounds 瞬间裁小）；与工具栏退场共用 250ms。
- 图片间必须空 Text 块（拖拽打包 CompositeCommand 一步撤销）；空块序列化 EMPTY_BLOCK_PLACEHOLDER（NBSP 占位段）、载入侧还原为空块；阅读态 skipRenderIndexes 不渲染空行。isBlank() 不认 NBSP/ZWSP。
- **载体空块不变量（2026-09-10 修复"反复交换后图片上下空行越来越多"）**：正确不变量是「载体空块数 == 图片相邻对数，且每个都恰好夹在两张图片之间」。旧实现只有 `InsertImageSeparatorCommand`（**只增不减**），而 `moveBlockById` 是"移除+插入"→ 被拖图片走了、原载体留在原索引上漂到图片组外侧，下次换位再补一个 → **每次交换空块 +1**（连续交换还会出现两个空行贴上）。
  - 解法：`BodyBlock.Text.isImageSeparator` 显式标记载体（**不进 markdown**；`BlockSpec.TextSpec` 同名字段随命令往返；`textSpec()` 是唯一出口）。载入时 `initialize()` 按**位置**认领（恰好夹在两图之间的空白块 → 标记；markdown 里载体与用户空行都是 NBSP 占位段，位置是唯一可靠判据）。
  - `normalizeImageSeparators()` 替代旧 `buildImageSeparatorCommands()`，**两规则**：①删漂移载体（带标记 **+ 仍为空白** + 不再夹在两图之间）②两图**直接相邻**处补插（带标记）。**返回顺序即 apply 顺序**：删除按索引降序在前、插入按索引降序在后（撤销逆序 = 插入先按 id 删、删除再按升序插回，位置精确对称）。
  - ⚠️ **身份与内容绑定（关键，2026-09-10 修复"载体里打的字交换后消失"）**：载体标记必须在块**一有内容时即刻清掉**——接线在 `BlockTextItem` 的 `snapshotFlow{state.annotatedString}` 观察者 → `BodyBlocksController.onBlockContentChanged(blockId)` → `demoteImageSeparatorIfFilled()`（就地换块对象、复用 state/FocusRequester；观察者 key 是 state 对象 → 不重启）。`normalizeImageSeparators` 另有"有内容一律不删"的防御判据。**教训：任何"自动插入的占位块身份"都必须与"块是否仍为空"绑定，否则用户一输入就会被当占位块回收。**
  - **用户手打的空白块（无标记）永不触碰**：不删、不压缩，哪怕同一图-图间隙里有两个（用户要求）。它们天然隔开两张图片，规则②只在**直接相邻**时才补。
  - 图片组**外侧**的历史空行同样不动（无法区分漂移载体与用户有意留白）。`plainText()` 跳过"空白且带标记"的载体（漂移中间态不进正文；有内容则照常输出）。

### 图片备注
- 占位进 BasicTextField decorationBox 同容器；常显态文本取 block.note 权威值；高亮=仅选中时 ImageHighlightColor(0xFFFFB74D) alpha 0.55 底色（Text wrap 贴合文字宽度）。
- **BasicTextField 对 LocalTextStyle 零引用**（只认 textStyle 参数）→ CompositionLocalProvider(LocalTextStyle) 包裹统一排版基线。
- 常显态可点回编辑态（clickable indication=null 重置守卫）；点图片先 focusManager.clearFocus()（只 hide 键盘不够）。
- **onFocusChanged 首组合必回调一次 Inactive**（focusState 初值 null≠Inactive）→ hasBeenFocused 守卫防同帧退出编辑态，进入编辑前重置守卫。

## 块级拖拽重排（自维护 fork，2026-09-10）
- 底层换成 `app/.../ui/components/reorderable/BlocksReorderableList.kt`（fork 自 `sh.calvin.reorderable:reorderable:3.1.0`，Apache-2.0 头保留 + 改动说明；连带复制了同包 internal 的 `draggable.kt` 手势实现）。其它页面（设置类列表）仍用库原版 `ReorderableColumn`，互不影响。
- **唯一语义改动**：`settle()` 由「先 `animateTo` 播 300~500ms 落位弹簧滑行 → 再回调 onSettle」改为「抓视觉快照 → **立即 onSettle** → 滑行交给 `BlocksGlideController` 接续」。目的：让 `moveBlock` 的换位与「相邻图片补空行」在手指抬起的**同一帧**生效。
- 滑行接续原理：提交前记每项「视觉顶边（`columnTopInWindow + itemIntervals[j].start + itemOffsets[j].value`，窗口坐标）」，重排后每项在 `onGloballyPositioned` 里按 key 认领，`startOffset = 落下视觉Y − 新布局Y` → `snapTo` → `animateTo(0)`；统一滑行同时解决"被拖块"与"邻居弹簧未跑完"两种残留位移。`glide` 必须 `remember` 在 state 之外（列表一变 state 即重建）。
- **约束（别忘）**：拖拽期间绝不能改列表——库 state 用 `remember(list, spacing)` 建、`itemIntervals/itemOffsets` 按构造时 size 定长，列表一变拖拽当场断。所以"拖拽中预插占位空行"这条路是死的。
- `itemKey` 现在身兼三职：组合身份锚定（防图片重载）+ 滑行归属 + `zIndex` 抬升判据。
- 方案文档：`docs/拖拽换位与空行弹出同步-实施方案.md`。

## 阴影渲染（2026-09-10）
- **Modifier.shadow（elevation 投影）在 scale+alpha 动画中会出方角阴影**：shadow=GraphicsLayer.shadowElevation+shape→RenderNode.elevation/outline→RenderThread 投影；AnimatedVisibility 的 fadeIn（alpha<1 离屏合成）+scale 变换期间胶囊 outline 投影变形（左下/右下方角）。静态正常、仅动画异常。
- **alpha 动画必裁"布局边界外的绘制"（阴影切割）**：GraphicsLayer 官方注释——内容可画出 bounds，但**离屏缓冲只按 bounds 尺寸栅格化**；fadeIn/fadeOut 全程 alpha<1 → 阴影光晕（bounds 外 ~8-10dp）被缓冲区直边切掉。静态 alpha=1 无缓冲→完整。此裁切在外层动画层，换 dropShadow 也躲不掉；外扩 bounds（透明 padding 包住阴影 + offset 补偿）用户实测**仍见切割感**。
- **最终定版（用户决策）：图片工具栏彻底无阴影**，层次感用 **1dp 阴影色（黑 25%）外边框**（border，CircleShape，background 后 padding 前）承担。两次阴影尝试（shadow/dropShadow+外扩）均已回退移除；`Modifier.dropShadow` 自绘 API 本身可用于**无 alpha 动画**的静态悬浮元素。教训：**悬浮元素若带 alpha 出入场动画，别给它挂任何依赖"画出布局边界"的视觉效果**。

## SwipeableImageStack
- 可见深度锁 4；旋转角 -(M-1)*15；收起按钮半胶囊吸附时间线；祖先 animateContentSize 裁剪→「Stage 左扩+内容层 offset 补偿」。

## 图片附件页（`InspirationImageGallery`，全屏沉浸预览，2026-09-10）
- **宿主已声明 configChanges**：`MainActivity` 现为 `orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden`（**不含 uiMode**，深色模式仍走重建）。⇒ 屏幕旋转只 `onConfigurationChanged`、**不重建 Activity**。任何"改 `requestedOrientation`"的页面都依赖这一条，否则全屏 Dialog 会被一起销毁。
- **横屏 = 真旋转窗口**（用户决策，替代旧的"内容层 rotate(90°) 伪横屏"）：`landscape` → `activity.requestedOrientation = SCREEN_ORIENTATION_LANDSCAPE / UNSPECIFIED`；退出附件页在 `DisposableEffect(activity).onDispose` 兜底恢复方向 + 恢复宿主窗口系统栏。伪横屏的代价：翻页方向被换算成屏幕上下滑动、系统栏无法真隐藏。
- ⚠️ **缩放手势与翻页手势必须按「指针数」分流**（2026-09-10 用户实测"双指完全无法放大缩小"）：原写法 `if (scale > 1f) { detectTransformGestures { … } }` 是**死锁** —— `scale` 初值就是 `1f`，手势**从未被注册**，而 `scale` 要变大又必须先有手势 ⇒ 缩放永远不可能生效。也**不能**改成无条件 `detectTransformGestures`：它会**无条件消费所有指针** ⇒ `HorizontalPager` 收不到单指拖动、翻页失效。**正解**用 `awaitEachGesture` 自己分流：**双指始终处理缩放并 consume**；**单指仅在 `scale > 1f` 时 consume 用于平移，否则放行给 Pager**。（`event.calculateZoom()` 在单指时恒为 1，可放心调用。）
- **点击切换 UI 显隐的坑（必记）**：`detectTapGestures` 内部会 `down.consume()`，父级 `awaitFirstDown()` 默认 `requireUnconsumed = true` ⇒ **挂在父节点上的 tap 检测永远收不到事件**（子节点的双击检测器已消费）。正解：把 `onTap` 与 `onDoubleTap` 放进**同一个** `detectTapGestures`（在图片自身节点），互斥判定；代价是单击要等双击超时（~300ms）。拖拽自动取消 tap ⇒ 滑动不触发。
- **竖屏顶部间距（最终定版）**：基准常量 `ChromeTopGapFromStatusBar = 12.dp` —— **与录音附件页顶栏同高**（用户决策：两个全屏页的顶部元素必须在同一条水平线）。`padding.top = (SB + 12dp − windowTopPx − 元素自身 16dp)`。
  - ⚠️ **口径陷阱**：用户口中的"半个时间栏高度"应以**录音页视觉**为准（12dp），而不是按 insets 算的"半个"（该机 insets=40dp → 20dp，会偏低约 8dp）。差异还来自"元素自身内缩"：录音页 18sp 文字在 40dp 行内居中 +9dp、图片页标题胶囊内边距 +6dp。
  - 标题/页码/右上按钮的 top padding 必须**统一为 16dp**，三者顶边才对齐。
- ✅✅ **真正根因（2026-09-10，`GalleryDiag` 埋点实锤）**：两页取 Dialog 窗口的那一行
  `val dialogWindow = remember(view) { (view as? DialogWindowProvider)?.window }`
  **恒为 `null`** —— 日志实打实打出 `【图片附件页】埋点触发 landscape=false dialogWindow=null`。
  原因：`LocalView.current` 在 Dialog content 中**并不是** `DialogLayout` 本身。
  而紧随其后的
  `DisposableEffect(dialogWindow) { if (window == null) { onDispose { } } else { …全部窗口设置… } }`
  **把整段窗口设置全部跳过** ⇒ `setLayout` / `WindowCompat.setDecorFitsSystemWindows(false)` / 系统栏 show-hide / `layoutInDisplayCutoutMode` **一次都没执行过**。
  - **这就是此前所有"改 flag 毫无效果"的真相**：flag 根本从未被设置过；也解释了"横屏状态栏始终可见"（`hide()` 从未被调用）与"左侧挖孔区拿不到"。
  - ❌ **以下曾经的"结论"全部作废**（都建立在"设置已执行"的错误前提上）：`FLAG_LAYOUT_IN_SCREEN` 无效、`SHORT_EDGES` 无效、`dimAmount` 会被主题回滚、`configChanges` 导致窗口不重建、用 `key` 重建 Dialog。**没有一条成立** —— 都是在给一段不会执行的代码加内容。
  - ✅ **修法**：沿 View 父链向上查找 `DialogWindowProvider` ——
    `private tailrec fun View.findDialogWindow(): Window? = when (this) { is DialogWindowProvider -> window; else -> (parent as? View)?.findDialogWindow() }`
    （两页各自 private 定义，不同包不冲突。）
  - 🔑 **最重要的教训**：**"改了没效果"必须先确认"代码到底执行了没有"** —— 一个 `Log` 打出的 `null`，胜过三轮读代码猜 flag。给窗口/insets 这类代码加一行断点日志只要几分钟，而盲猜的代价是用户反复编译验证。
  - **补偿式写法（与 `dialogWindow` 无关，仍然有效）**：`LocalView.current.getLocationOnScreen()` 照常可用，`padding.top = 目标位置 − windowTopPx` 的写法保留。
  - ✅ **窗口被缩进的具体数值（埋点实测，修好 `dialogWindow` 后拿到）**：竖屏 `decorView screenY=111`（= 状态栏高），高 2400−111−44；横屏 `screenX=111`（= 挖孔 `l=111`），宽 2400−111；两屏 `padding` 四边**全 0**、`layInScreen=false`、`cutoutMode=0(DEFAULT)`。⇒ 确诊是**窗口 frame 被缩进**（而非内容内缩），缩进量恒等于对应边的 inset。
  - ✅ **最终修法**：在 `DisposableEffect(dialogWindow)` 里 `window.addFlags(FLAG_LAYOUT_IN_SCREEN)` + `layoutInDisplayCutoutMode = SHORT_EDGES`（API 28+）。这两项以前"设过"，只因 `dialogWindow` 恒为 null 而**从未执行**，现在才第一次真正生效。
  - ✅ **修复后日志验证（2026-09-10）**：图片页横屏 `decorView 2400x1080 screenX=0 screenY=0`（= 屏幕满尺寸，完全铺满）、`layInScreen=true`、`cutoutMode=1(SHORT_EDGES)`；竖屏 `1080x2356 screenY=0`（2356 = 2400−44，少的 44px 是**导航栏区域**，由系统绘制手势条、不露宿主，无观感问题）。对照修复前的 `2289x1080 screenX=111`。**⇒ 这两个 flag 确实是正解，唯一前提是 `dialogWindow` 取得到。**
  - ✅ **用户实测确认横屏灰带消失；诊断埋点已整体删除；提交 `dd740511`。**
  - ⚠️ **警示：`if (x == null) { /* 什么都不做 */ }` 这种写法会掩盖致命错误**。本次 bug 的本质就是 `dialogWindow` 为 null 时静默跳过了全部窗口设置，症状却表现为"flag 无效"，把排查方向带偏了 4 轮。**凡"关键前提可能为 null"的分支，至少要留一条日志或 fail-fast。**
  - **同因旁证**：语音附件页 `VoicePreviewDialog` 是完全相同的写法。它"12dp padding 看起来刚好"，同样是因为 `setDecorFitsSystemWindows(false)` 从未执行、内容自动避让了状态栏 —— 与图片页是**同一个 bug 的两种表现**（图片页横屏缺左侧、竖屏顶部偏下，根源都是它）。
  - **排查口诀（修正）**：全屏浮层出现"莫名偏下 / 偏一侧，且偏移量恰等于系统栏或挖孔尺寸"时，**第一件事是确认窗口设置代码到底执行了没有**（在最外层打一行 `Log` 看 `window` 是否为空），而不是去调 flag、cutout 模式或 padding。
- **Dialog 的 insets 不可靠**：一律从 `activity.window.decorView` 的 rootWindowInsets 读。横屏时系统栏已 hide 且 insets 归零 ⇒ 系统栏高度**只在竖屏更新 + `> 0` 守卫**（退出横屏瞬间可能还没 show 回来）；挖孔安全区用 `Type.displayCutout()` 横竖屏都读。系统旋转异步（约 300ms），需 `delay(350)` 补读一次。
  - ⚠️ **但补读不能只有一次**（2026-09-10 用户实测）：单次 `delay(350)` 的问题是 —— `configuration` 变化时系统栏/挖孔 insets **还没到位**，350ms 后才读到真实值 ⇒ 安全边距在**旋转动画结束之后**才开始变化，用户看到"画面都已经稳定了，按钮却又向右上方位移了一下"。**正确做法：高频重读** —— `LaunchedEffect(activity, configuration) { repeat(10) { delay(50); readSafeInsets() } }`（前 500ms 每 50ms 一次），让边距与旋转动画同步到位；语音页同样抽出 `refreshInsets()` 后高频重读。
  - **旁证**：右下角「详情/下载」不动 —— 因为横屏时 `end`/`bottom` 的 padding 恒为 0，只有 `start`（挖孔）与 `top`（状态栏）在变。**规律：哪些元素位移，就看哪个方向的 inset 在变。**
- **系统栏"彻底隐藏"要双通道**：同时对 Dialog 子窗口与宿主 Activity 窗口 `hide(systemBars())`（个别 ROM 只认 Activity 窗口）。宿主窗口**只做 show/hide，不改图标明暗**，防退出后残留。
- **Pager 翻页阈值**：`snapPositionalThreshold` 按**页宽百分比**算，横屏页宽是屏幕长边（≈812dp）⇒ 默认 50% 要滑 ≈400dp。横屏用 0.08（≈65dp）、竖屏 0.35。该阈值只管慢速拖动的落点，快速轻扫由速度阈值翻页。
- **横屏安全边距：四边全为 0**（2026-09-10 用户实测决策）。理由：① 顶部 —— 横屏时系统栏已 hide，无需避让；② **左侧不避让挖孔** —— `displayCutout` 是按**整条边**报避让量的（该机 `left = 111px ≈ 40dp`），而挖孔实际只占左侧边缘**中段**一小块；本页 UI 是四角布局（标题左上 / 页码中上 / 按钮右上 / 删除左下 / 详情·下载右下），y 区间与挖孔完全错开，用户实测确认"不影响显示与点击"。**避让只会让整屏 UI 无谓右移**（正是用户反馈的"右移"来源）。`cutoutXxxPx` 仍保留在 `readSafeInsets()` 中，将来若需精细避让可用 `displayCutout.getBoundingRects()` 判断"是否真的与 UI 重叠"。
  - 语音页同理：横屏时 `topSafePadding` / `bottomSafePadding` 必须归零（`if (isLandscapeLayout || windowInsetActive) 0.dp else …`），否则会拿**竖屏时**测到的系统栏高度去避让一个**已经隐藏**的栏。
- **录音附件页 `VoicePreviewDialog`**：与图片页共用同一套 `findDialogWindow()` 修复；顶栏 `top = 12dp + topSafePadding`、底栏 `bottom = 12dp + bottomSafePadding`，其中 `safePadding = if (windowTopPx > 0 || windowLeftPx > 0) 0.dp else 系统栏高度`（避免双重避让）。项目内用 `DialogWindowProvider` 的全屏浮层**仅这两处**。
  - **系统栏策略统一为「始终显示」**（用户决策，弃用录音页原来的 `hide()` 沉浸）：部分 ROM 在 hide() 后仍绘制系统栏但 insets 归零，「看得见的栏 + 为零的 insets」会让所有按 insets 的避让失效；始终显示则 insets 永远真实。深色页面配 `isAppearanceLightStatusBars/NavigationBars = false`。
  - ⚠️ **布局必须跟「实际方向」而非「意图方向」**：`landscape`（点按钮即翻转）与 `configuration.orientation`（系统真正旋转后才变）要严格分开。前者只用于 `requestedOrientation` / 系统栏 hide-show / Pager 翻页阈值 / 按钮图标态；后者用于 **`uiSafePadding`**、insets 重读的"仅竖屏更新"判断、以及 insets 相关 effect 的 key。否则点按钮后 UI 会在**窗口仍是旧方向**时切到目标边距（竖屏 `top≈36dp` ⇄ 横屏 `start=挖孔宽,top=0`），等屏幕转过来再稳定 —— 用户看到的就是"按钮先向上/向下跳一下"。**通用原则：凡是"点了立刻翻转、但系统异步生效"的状态，一律不得驱动布局。**
  - **边距补间（ChromePaddingTransitionMillis = 300ms）**：`uiSafePadding` 四边各用 `animateDpAsState` 补间，与系统旋转动画同长。**两个必须做的防护**：① 系统栏高度初值用 `remember{}` 在首次组合时**同步读**（否则 0 → 36dp 会让进页面时元素从顶部滑入）；② `chromePaddingSpec = if (insetsMeasured) tween() else snap()`，`insetsMeasured` 靠 `LaunchedEffect(Unit){ withFrameNanos{}; ... }` **错开一帧**再置位 —— 不能只用状态切换，因为 `statusBarTopPx` 与标记会**同帧**变化、Compose 状态合并后仍会播动画。
  - ⚠️ **「横屏意图」必须是三态，且显隐/语义要跟「实际方向」取或**（2026-09-10 用户实测截图）：图片页原来写 `if (landscape)` 决定 hide/show，而 `landscape` 只在点按钮时翻转、**手动旋转手机不会改它** → 手动转到横屏时系统栏没隐藏，状态栏压住页面顶部的标题/页码/按钮，且与「按钮旋转」的横屏表现不一致。
    - 修法：`orientationOverride: Boolean?`（`null` = 跟随系统 / `true` = 请求横屏 / **`false` = 请求竖屏**），派生 `isLandscapeActive = orientationOverride == true || isLandscapeLayout`；系统栏显隐、Pager 阈值、按钮图标与点击语义全部改用它。
    - **`false` 必须是「请求竖屏（PORTRAIT）」而不是「回退 UNSPECIFIED」**：手动旋进来的横屏若只回退 UNSPECIFIED，手机仍横握时会被系统带回横屏，**根本转不回竖屏**。
    - 语音页没有横屏按钮，用 `LaunchedEffect(dialogWindow, configuration.orientation)`：横屏 hide / 竖屏 show。
    - **通用原则：凡与画面观感相关的显隐/避让，一律以「实际方向」为准；「意图」只用于需要即时响应的动作。**

## 工具使用教训
- **同一文件的多次 Edit 必须串行**：并行发多个 Edit 到同一文件会出现**写竞态**——工具都返回 Successfully，但后写覆盖前写，某次删除被静默丢失（本次 `requiredSize` import 就是这样残留的）。
- ⚠️ **连续 2 次「猜测 → 改码 → 用户验证 → 失败」后，必须停止猜测、改为加埋点取真实数据**。本项目实测：定位「全屏 Dialog 横屏左侧铺不满」连续猜了 **3 轮**（`layoutInDisplayCutoutMode` → `FLAG_LAYOUT_IN_SCREEN` → 用 `key` 重建 Dialog 窗口）全部无效，每次都要用户编译验证；用户叫停后改为在代码里打日志才收敛。**窗口 frame / insets 这类系统行为靠读代码推不出来**，一个屏幕坐标 + 一个 padding 值就能定性。
  - 埋点判据：`decorView.getLocationOnScreen()` 的 `screenX > 0` ⇒ **窗口本身没铺满**（窗口被系统缩进）；`screenX == 0` 而 `paddingLeft > 0` ⇒ **窗口是满的、内容被 insets 内缩**。
  - 抓取：`adb logcat -c && adb logcat -s <自定义TAG>`，并在埋点里 `delay(1000)` 等旋转与布局稳定。
