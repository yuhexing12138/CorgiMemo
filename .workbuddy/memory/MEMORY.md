# CorgiMemo 项目长期记忆

## 项目约定
- **不主动编译**：Kotlin 改动后不跑 gradlew，除非用户明确要求。
- **无 BuildConfig**：版本号走 `packageManager.getPackageInfo(pkg,0).versionName`（`SettingsScreen.kt:126`）；字体 cacheDir 副本名 `ff_font_<resId>_v<versionName>.ttf`。
- **核对依赖真实签名**：AndroidX/Compose 编译报错时用项目技能 `.workbuddy/skills/gradle-cache-source-lookup`（扫 gradle 缓存 `*-sources.jar`）。要点：`find_sources_jar.py --class <裸类名> [--group androidx.compose.ui]`；group 目录是点分隔扁平名；`-android` 产物也含 `commonMain/`。
- **检索点目录**（`.workbuddy`/`.gradle`）：Glob 绝对路径放 `path`、`pattern` 只写相对通配。
- 设计稿 Ardot fileId 707225018209249；字体素材 `free-font/`；报告 `free-font-可商用字体库调研报告.md`；块方案文档 `docs/路线4-块级图片-实施方案.md`。
- 子模块 `compose-rich-editor` 指针可能指向丢失提交，需重提。

## 正文字体体系
- 9 款 OFL 中文 + 3 款拉丁（回退层），49 文件 `res/font/`；授权随 APK 分发 `assets/licenses/`。
- 架构：`FontCatalog`(`FontEntry`: id/名/授权/FontFamily/字重→resId，`isSystemDefault`，`isLatin`) + `FontManager` + `buildTypography(family)`。默认 = 系统默认字体（`FontCatalog.DEFAULT_ID="system_default"`，偏好键 `font_id`），设置页 `AppearanceScreen` 切换即时生效。新增字体：拷资源 → `FontCatalog` 登记一条。
- 预览/探测统一走 `ui/theme/FontPreviewEngine.kt`：有界池（`previewTypefacePool` 容量2、`probeTypefacePool` 容量3）+ 位图 `LruCache(32)`；字体拷 cacheDir 后 `Typeface.Builder(String)` 构建（本工程 android.jar 无 InputStream 重载）。**铁律：预览绝不用 `ResourcesCompat.getFont`/`Text(fontFamily)` 批量渲染**（驻留 TypefaceCompat LruCache(16)/FontFamilyResolver → OOM，曾五次崩溃）。
- **分离式预览**：一次最多同时加载两种字体（中文1+拉丁1）。编辑页面板点选只改 pending，「应用」保持展开，再点「完成」收起；设置页两组各一个「确定」，未点直接返回=丢弃。
- **FontFamilyResolver**：`createFontFamilyResolver` 所有实例共享进程级全局缓存；真丢弃需 `emptyCacheFontFamilyResolver`(私有缓存) + `TypefaceCompat.clearCache()`。`ui/theme/FontResolverPolicy.kt` + Theme.kt 按 `fontCacheKey` remember 隔离。代价：无 AndroidFontResolveInterceptor（无障碍粗体加成失效）。
- **合成族必须按字重串成单一回退 Typeface**：`FontFamily(latin.fonts+cjk.fonts)` 会让同字重中文被拉丁覆盖→回落系统字体。正确：`FontCatalog.combinedFamilyFonts(cjk,latin)` 每档 `Typeface.Builder(latin).addCustomFallback(cjk).build()`（minSdk≥26）。
- **作用域解耦**：设置字体只管 App chrome（`MaterialTheme.typography`）；用户内容走 `ContentFontManager` + `LocalContentTypography`（默认系统字体）。工具栏 `FontWeightProbe` 用 ContentFontManager。

## 编辑态块结构（路线 4，块级图文交错）
- `BodyBlocksEditor` + 每块一个 RichTextEditor（Compose 1.11 BasicTextField 无 inlineContent）。语音保持 `trigger:voice` 内联。
- 撤销：自建 Command 栈（块增删/排序/图片属性）+ 库内 `RichTextState.history`（块内富文本），两套历史隔离，焦点判断是调度核心。
- **新增 sealed 块子类必须全项目 Grep `is BodyBlock.` 补穷尽 when**。
- **块内水平对齐常量**：Text 块文字左右缘 = 块内容边界 ± 16dp（RichTextEditor `contentPadding(start/end=16.dp)`）；非文本块用 `BodyBlocksEditor.BLOCK_CONTENT_PADDING = 16.dp`，改动双向同步。

### 分割线块 `BodyBlock.Divider`
- markdown 独占段 `---`；工具栏 `SeparatorHorizontal` 插入；点击仅高亮（`highlightedBlockId`）。详情页识别 `---` 渲染 HorizontalDivider（不喂库）。删除 = 行变空行（`deleteDividerBlock` 用空 TextSpec 替换），焦点落空行行首，可撤销；相邻块退格/删除两步删除保留。
- **非文本块「点选」态铁律**：Android 软键盘只跟随**聚焦的编辑框**；软键盘退格走 `InputConnection.deleteSurroundingText`，`onPreInterceptKeyBeforeSoftKeyboard`（ui 1.11.2 源码注释）**只拦硬件键盘转交 IME 的事件**。故「选中 + 键盘不消失 + 按键能删」唯一解 = **焦点留在 Text 块**（`cursorColor=Transparent` + 光标安置到后面最近 Text 块块首）。**曾误用"夺焦点到分割线 + focusable/onKeyEvent"** → 键盘消失，已回退。
- **删除交互定版**：删除统一走高亮态悬浮「删除」按钮。`pointerInput + detectTapGestures` 捕获手指 x（clickable 拿不到位置）→ `Popup(alignment=TopStart, offset=IntOffset, focusable=false)`，x=clamp(手指x-半宽, 边距, 行宽-边距-按钮宽)，y=-(按钮高+间距)。**高亮来源区分**：点击点亮才弹按钮（`highlightedTapX`）；退格两步删除点亮走 `highlightForTwoStepDelete`（tapX=null 不弹）；退格高亮后再点击=补弹按钮（不取消），点选态再点=取消。
- **清高亮时机**：输入文本变长/命令执行/点其它文本块/**点已聚焦的文本块**（后者无焦点变化，靠 BlockTextItem 的 Row 挂非消费 `awaitFirstDown(requireUnconsumed=false)` 观测按下 → `onTextBlockPressed()`）；退格不清。

### 复选框块
- Text 块属性 `checked: Boolean?`；GFM `- [ ] `/`- [x] ` 前缀只在块边界处理，**绝不进 RichTextState**。工具栏 `SquareCheck` 整块转换；勾选走 `SetCheckboxCheckedCommand` **就地换块对象、保持 state/focusRequester 引用**（不丢 history、光标不动）。视觉 `CheckboxIcon.kt`（18dp/圆角5dp，勾选=primary 填充）。
- **缩进 = App 布局级同步**：`indentLevel:Int`(1..6)，markdown 用 `- [ ] ` 后的 EM(U+2003) 前缀（每级2个），state 恒无 TextIndent；渲染时 Row 内图标与编辑器各加 `(L-1)×30sp` start padding。**教训：跨排版体系对齐要改架构（同容器整体变换），别对齐外部数值**。
- 详情页 `InspirationBodyRichText` 迭代 `split("\n\n")` 原始段序列；勾选写库只读最新实体合并 contentFormat。

### 列表 / 缩进
- **无序列表缩进不换符号**：库 `DefaultUnorderedListStyleType` 改为单元素 `from("•")`（层级→符号取 `prefixes[(level-1).coerceIn(indices)]`，单元素表恒取首项）。有序列表编号轮换（1.→(2)→①）不动。App 侧 `refocusListBlock` 正则保留 `[•◦▪]` 兼容旧数据。
- **缩进数据源同源**：`indentFocusedBlock`/`canIncreaseIndent`/`canDecreaseIndent` 三处顺序一致——复选框块（含组合态）**优先**读块对象 `indentLevel`，纯列表块才读库层级（`listLevelOfMd`）。
- **回车续行层级随 spec 显式走**：拆块/归一化必须传 `spec.listLevel`（通用 `srcListLevel`），**不依赖 markdown 前缀往返**（rebuildBlock 会剥前缀；range 版 toMarkdown 对「仅分隔符入范围」段落退化无前缀）。尾空块传 `lastSpecLevel`；`textSpec()` 也带 listLevel。
- **孤立缩进列表行加载必剥前缀**：层级前缀每级 2 空格，**≥4 空格（三级起）被 CommonMark 解析成缩进代码块**（探针实证 markdown-jvm 0.7.7）。`initialize` 对单行列表段（`SingleLineListMdRegex`）统一 `createTextBlock(剥前缀, listLevel, strip=true)`；详情页 `InspirationBodyParagraph` 同款。**层级这类 App 语义永远 spec/代码显式携带，markdown 前缀只适合 ≤3 空格**。
- **列表换行右跳一帧**：marker 宽度 `startTextWidth` 默认 0.sp 首帧偏右，onTextLayout 回写后回落。宽度缓存已改**进程级共享**（companion `SharedStartTextWidthCache`，key=`fontSizeSp|fontFamily|prefix`）。**教训：块架构每 block 一个 state，库内 per-state 缓存跨块会丢，需延续的渲染态必须进程级共享。**

### 图片块
- **块级图片撑满宽度**：`InlineImagePreview(fillMaxWidth = true)` → 宽 `fillMaxWidth()`、高 `aspectRatio(painter 真实比例)`；撑满态内部**不加**水平 padding（调用方给 16dp）。旧路径（maxWidth=300.dp）不变。
- **宽高比缓存**：`InlineImagePreview` 内进程级 `ImageAspectRatioCache`（path→宽/高），success 写入、loading/error 读取；占位高度用缓存 ratio 而非固定 180dp，防重载高度塌陷。
- **块重排必须加 key**：`ReorderableColumn`（非 Lazy）内部 `Column { list.forEachIndexed }` **item 无 key** → 交换后槽位复用 → `SubcomposeAsyncImage` 重载、高度塌陷回弹。`BlocksReorderableColumn` 已加 `itemKey`，调用方必须传**块 id**。
- **选中工具栏必须 Popup 独立窗口**：布局内渲染会被**后续兄弟块**盖住/拦截（父 Column 后组合者画上层）。已迁 `Popup(alignment=TopStart, offset=组合期IntOffset, PopupProperties(focusable=false, clippingEnabled=false))`。**Popup 动画两陷阱**：① 首组合 visible 已 true 会丢 enter → 内容内 `toolbarAnimatedIn` 先 false 一帧再 true；② visible=false 直接卸载会截断 exit → 挂载状态延迟卸载（`ImageToolbarPopupUnmountDelayMillis=350L`）。shadow 不被裁（FLAG_LAYOUT_NO_LIMITS）；bounds 外触摸穿透（WATCH_OUTSIDE_TOUCH）。
- **缩小/恢复动画与工具栏退场同步**：`animateFloatAsState` 驱动 `widthFraction`（1f⇄0.5f）**真实缩放**（不用 animateContentSize，其 clipToBounds 会瞬间裁小）；与工具栏退场共用 `ImageScaleAnimationDurationMillis=250`；工具栏位置用冻结快照原地退场；undo 翻转 shrunk 自动跟随。
- **图片间必须留空行**：任意两个 Image 块之间必须有空 Text 块。`InsertImageSeparatorCommand` + `buildImageSeparatorCommands()`（自后向前扫相邻对）+ `insertBlockAt/removeBlockById`；单张走 `executeAndPushWithImageSeparators`，批量整批后统一补，拖拽 `moveBlock` 打包 CompositeCommand（一步撤销）。空块序列化走 `EMPTY_BLOCK_PLACEHOLDER`。删除/加载路径**不做**兜底（用户决策）。
- **图片间空行阅读态不渲染**：详情页 `InspirationBodyRichText` 用 `skipRenderIndexes`；首页走源头 `plainText()`。**Kotlin isBlank() 不认 NBSP/ZWSP**，判空须先剔除。旧数据不迁移（用户决策）。

### 图片备注（2026-09-09）
- **渲染三条规则**：① 占位「图片描述」必须放进 BasicTextField 的 `decorationBox`、与 innerTextField 同容器（`Box(Modifier.fillMaxWidth(), propagateMinConstraints = true)`）；② 常显态文本取块对象 `block.note`（权威值），编辑态才用本地 `noteText`；③ 高亮 = **仅图片选中时**给文字加底色（`Text` wrap content + `background` 贴合文字宽度非整行），色取 `ImageHighlightColor`(0xFFFFB74D, `InlineImagePreview.kt`) alpha 0.55，与图片高亮描边同色同 alpha，单一真相源。
- **排版统一**：`BasicTextField` 对 `LocalTextStyle` **零引用**（只认 `textStyle` 参数），而 material3 `Text` 会 merge `LocalTextStyle` 继承主题行高 → 占位与输入文字基线不同。解法：`CompositionLocalProvider(LocalTextStyle provides noteTextStyle)` 包裹备注块。
- **常显态必须可点回编辑态**：只读 `Text` 加 `.clickable(interactionSource, indication=null)` 重置 `noteHasBeenFocused=false` + `noteText=block.note` + `noteEditing=true`。
- **点图片必须先清焦点**：图片非可聚焦组件，点它不会让备注输入框失焦 → 仍停编辑态（无底色）。图片 onClick 中 `softwareKeyboardController?.hide()` 后加 `focusManager.clearFocus()`。
- **备注行闪退**：`onFocusChanged` 节点附加时必回调一次 Inactive（源码：`focusState` 初值 null ≠ Inactive）→ 同帧把 `noteEditing` 改回 false。修复：`noteHasBeenFocused` 守卫（聚焦置 true，`noteHasBeenFocused && noteEditing` 才退出并复位），进入编辑前重置守卫。

## 跨帧焦点迁移
- **必须"延续"光标隐藏**：`focusSpec` 只写 `pendingFocus`，真 requestFocus 在块 LaunchedEffect 的下一帧；而 `afterCommandMutation` 同步清选中态 → 中间那帧焦点在旧块、光标已恢复可见 ⇒ 闪一下再跳。解法：删除时记 `hideCursorUntilFocusBlockId = 新块id`，由 `onBlockFocused` 无条件解除；判据统一走 `isCursorVisuallyHidden`。

## SwipeableImageStack
- 可见深度锁 4；扇形 `ei=min(stackIndex,M-1)`；旋转角 `-(M-1)*15`；展开态收起按钮半胶囊吸附时间线竖线；祖先 `animateContentSize` 会裁剪 →「Stage 左扩 + 内容层 offset 补偿」。
