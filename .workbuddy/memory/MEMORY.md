# CorgiMemo 项目长期记忆

## 项目约定
- 不主动编译：Kotlin 改动后不跑 gradlew，除非用户明确要求。
- **无 BuildConfig**：读版本号走 `packageManager.getPackageInfo(pkg,0).versionName`（`SettingsScreen.kt:126`）。字体 cacheDir 副本名 `ff_font_<resId>_v<versionName>.ttf`。
- **核对依赖真实签名**：AndroidX/Compose 编译报错时用项目技能 `.workbuddy/skills/gradle-cache-source-lookup`（扫 gradle 缓存 `*-sources.jar`）核对，勿凭记忆。要点：先 `find_sources_jar.py --class <裸类名> [--group androidx.compose.ui]`（group 目录是点分隔扁平名）；`--class` 只传裸类名；`-android` 产物也含 `commonMain/`。
- **检索点目录**（`.workbuddy`/`.gradle`）：Glob 绝对路径放 `path`、`pattern` 只写相对通配。

## 正文字体体系
- 9 款 OFL 中文 + 3 款拉丁（回退层），49 文件在 `res/font/`；授权随 APK 分发 `assets/licenses/`。
- 架构：`FontCatalog`(`FontEntry`: id/名/授权/FontFamily/字重→resId，`isSystemDefault`，`isLatin`) + `FontManager` + `buildTypography(family)`。默认 = **系统默认字体**（`FontCatalog.DEFAULT_ID="system_default"`，偏好键 `font_id`），设置页 `AppearanceScreen` 切换即时生效。
- 预览/探测统一走 `ui/theme/FontPreviewEngine.kt`：两个有界池（`previewTypefacePool` 容量2、`probeTypefacePool` 容量3）+ 位图 `LruCache(32)`；字体资源拷 cacheDir 后 `Typeface.Builder(String)` 构建（本工程 android.jar 无 InputStream 重载）。**铁律：预览绝不用 `ResourcesCompat.getFont`/`Text(fontFamily)` 批量渲染**（会驻留 TypefaceCompat LruCache(16)/FontFamilyResolver → OOM，曾五次崩溃）。
- **分离式预览**：一次最多同时加载两种字体（中文1+拉丁1）。编辑页面板点选只改 pending，「应用」= 应用但保持展开，再点「完成」收起；设置页两个分组各一个「确定」按钮，未点直接返回=丢弃。
- **FontFamilyResolver**：`createFontFamilyResolver` 所有实例共享进程级全局缓存，换实例不丢缓存；真丢弃需 `emptyCacheFontFamilyResolver`(私有缓存) + `TypefaceCompat.clearCache()`。`ui/theme/FontResolverPolicy.kt` + Theme.kt 按 `fontCacheKey` remember 隔离 resolver。代价：无 AndroidFontResolveInterceptor（无障碍粗体加成失效）。
- **合成族必须按字重串成单一回退 Typeface**：`FontFamily(latin.fonts+cjk.fonts)` 会让同字重中文被拉丁覆盖→回落系统字体。正确：`FontCatalog.combinedFamilyFonts(cjk,latin)` 每档 `Typeface.Builder(latin).addCustomFallback(cjk).build()`（minSdk≥26）。
- **作用域解耦**：设置字体只管 App chrome（`MaterialTheme.typography`）；用户内容走 `ContentFontManager` + `LocalContentTypography`（默认系统字体）。工具栏 `FontWeightProbe` 用 ContentFontManager。
- 新增字体：拷资源 → `FontCatalog` 登记一条。

## 编辑态块结构（路线 4，块级图文交错）
- `BodyBlocksEditor` + 每块一个 RichTextEditor（Compose 1.11 BasicTextField 无 inlineContent）。语音保持 `trigger:voice` 内联。详见 `docs/路线4-块级图片-实施方案.md`。
- 撤销：自建 Command 栈（块增删/排序/图片属性）+ 库内 `RichTextState.history`（块内富文本），两套历史隔离，焦点判断是调度核心。
- 子模块 `compose-rich-editor` 指针可能指向丢失提交，需重提。
- **分割线块** `BodyBlock.Divider`：markdown 独占段 `---`（整段恰为 `---`）；工具栏 `SeparatorHorizontal` 按钮插入；点击仅高亮（`highlightedBlockId`），高亮态悬浮「删除」按钮（Popup 独立窗口，x 跟随点击手指位置，图标 LucideIcons.Trash2），删除=行变空行、焦点落空行行首、可撤销；相邻块退格/删除两步删除保留；详情页识别 `---` 渲染 HorizontalDivider（不喂库）。
- **复选框块**（Text 块属性 `checked: Boolean?`）：GFM `- [ ] `/`- [x] ` 前缀只在块边界处理，**绝不进 RichTextState**。工具栏 `SquareCheck` 整块转换；勾选走 `SetCheckboxCheckedCommand` **就地换块对象、保持 state/focusRequester 引用**（不丢 history、光标不动）。视觉 `CheckboxIcon.kt`（18dp/圆角5dp，勾选=primary 填充）。**缩进 = App 布局级同步**：`indentLevel:Int`(1..6)，markdown 用 `- [ ] ` 后的 EM(U+2003) 前缀（每级2个），state 恒无 TextIndent；渲染时 Row 内图标与编辑器各加 `(L-1)×30sp` start padding。**教训：跨排版体系对齐要改架构（同容器整体变换），别对齐外部数值**。详情页 `InspirationBodyRichText` 迭代 `split("\n\n")` 原始段序列；勾选写库只读最新实体合并 contentFormat。
- **非文本块「点选」态铁律（2026-09-08，分割线验证过两版）**：Android 软键盘只跟随**聚焦的编辑框**——焦点一离开 TextField 键盘必定收起；且屏幕键盘的退格走 `InputConnection.deleteSurroundingText`，Compose 的 `onPreInterceptKeyBeforeSoftKeyboard`（ui 1.11.2 源码注释）**只拦硬件键盘转交 IME 的事件，拦不住软键盘自身删除**。故「选中分割线 + 键盘不消失 + 按键能删」唯一解 = **焦点留在 Text 块**：`cursorColor=Transparent` 让光标视觉消失 + 光标安置到「分割线之后最近 Text 块」块首（退格无字可删 → 空块走 ZWSP 差分、硬键盘走 atContentStart → 命中 `onBackspaceAtStart` 的分割线分支）。**曾误用"夺焦点到分割线 + focusable/onKeyEvent"** → 键盘消失、触屏按不到删除键，已回退。
- **分割线删除交互定版（2026-09-08 第四版）**：点选后软键盘退格删除方案**已废弃**（软键盘退格走 `deleteSurroundingText` 无法拦截 + 手动收键盘后焦点不变、requestFocus 空操作键盘不重弹），删除统一走高亮态悬浮「删除」按钮。实现：`pointerInput + detectTapGestures` 捕获手指 x（clickable 拿不到位置）→ `Popup(alignment=TopStart, offset=IntOffset)` 独立窗口渲染（不被父容器裁剪、focusable=false 不抢焦点），x=clamp(手指x-半宽, 边距, 行宽-边距-按钮宽)，y=-(按钮高+间距)。**高亮来源区分（`highlightedTapX`）**：点击点亮才弹按钮；退格/Delete 两步删除点亮走 `highlightForTwoStepDelete`（tapX=null 不弹按钮，防凭空出现在行首/旧位置）；退格高亮后再点击=补弹按钮（不取消），点选态再点=取消。**保留**：`hideCursorUntilFocusBlockId`（删除后焦点跨帧迁移期隐藏光标防跳变）、两步删除、清高亮时机（输入文本变长/命令执行/点其它文本块/**点已聚焦的文本块**——后者无焦点变化 onBlockFocused 不触发，靠 BlockTextItem 的 Row 挂非消费 `awaitFirstDown(requireUnconsumed=false)` 观测按下 → `onTextBlockPressed()`，不影响 TextField 自身手势；退格不清）。
- **跨帧焦点迁移必须"延续"光标隐藏**：`focusSpec` 只写 `pendingFocus`，真 requestFocus 在块 LaunchedEffect 的下一帧；而 `afterCommandMutation` 同步清选中态 → 中间那一帧焦点还在旧块、光标已恢复可见 ⇒ 光标在旧位置闪一下再跳走。解法：删除时记 `hideCursorUntilFocusBlockId = 新块id`，由 `onBlockFocused` 无条件解除；光标隐藏判据统一走 `isCursorVisuallyHidden`（= 点选态 ∨ 迁移期且目标块仍在列表）。
- **删除分割线 = 行变空行**：`deleteDividerBlock` 用空 TextSpec 替换 DividerSpec，焦点落空行行首，可撤销。
- **新增 sealed 块子类必须全项目 Grep `is BodyBlock.` 补穷尽 when**。
- **无序列表缩进不换符号（2026-09-08）**：库默认符号表 `DefaultUnorderedListStyleType` 改为单元素 `from("•")`，任意层级 marker 恒为黑色圆点（缩进只移动位置）。层级→符号取 `prefixes[(level-1).coerceIn(indices)]`，单元素表即恒取首项。有序列表编号轮换（1.→(2)→①）保持不动。App 侧 `refocusListBlock` 正则仍保留 `[•◦▪]` 兼容旧数据/自定义符号表。
- **缩进数据源同源**：`indentFocusedBlock` / `canIncreaseIndent` / `canDecreaseIndent` 三处判断顺序必须一致——复选框块（含「复选框+列表」组合态）**优先**读块对象 `indentLevel`（App 布局级），纯列表块才读库层级（`listLevelOfMd`），否则按钮置灰会失准。
- **回车续行层级随 spec 显式走（2026-09-08）**：拆块/归一化的列表层级必须传 `spec.listLevel`（有序/无序通用 `srcListLevel`），**不依赖 markdown 前缀往返**——rebuildBlock 会剥前缀（stripListLevelPrefix，防 ≥4 空格代码块），range 版 toMarkdown 对「仅分隔符入范围」段落退化无前缀（库 extractRangeState）。此前只传有序（srcOrderedLevel）→ 无序列表回车前后块全掉一级。尾空块传 `lastSpecLevel`（最后内容行层级）；`textSpec()` 也带 listLevel 保撤销还原。
- **孤立缩进列表行加载必剥前缀（2026-09-08）**：层级前缀每级 2 空格，**≥4 空格（三级起）被 CommonMark 解析成缩进代码块**（CODE_LINE 字面输出，渲染成字面 "- 第三行"，探针实证 markdown-jvm 0.7.7；2 空格行正常）。`initialize` 对单行列表段（`SingleLineListMdRegex`）统一 `createTextBlock(剥前缀, listLevel, strip=true)`；详情页 `InspirationBodyParagraph` 同款。多行嵌套段走原路径。**层级这类 App 语义永远 spec/代码显式携带，markdown 前缀只适合 ≤3 空格**。
- **列表换行右跳一帧（2026-09-08）**：marker 宽度 `startTextWidth` 默认 0.sp，End 对齐公式 `firstLine=(base-prefix).clamp0` 首帧=base 偏右，onTextLayout 测量回写后回落。宽度缓存原为 RichTextState 实例字段——拆块重建新建 state 即丢缓存必闪。已改**进程级共享**（companion `SharedStartTextWidthCache`，key=`fontSizeSp|fontFamily|prefix`，实例缓存保留为快路径，测量同步双写，预热 miss 回查）。**教训：块架构每 block 一个 state，库内 per-state 缓存跨块会丢，需延续的渲染态必须进程级共享。**

## SwipeableImageStack
- 可见深度锁 4；扇形 `ei=min(stackIndex,M-1)`；旋转角 `-(M-1)*15`。展开态收起按钮半胶囊吸附时间线竖线；祖先 `animateContentSize` 会裁剪 →「Stage 左扩 + 内容层 offset 补偿」。

## 资源位置
- 设计稿 Ardot fileId 707225018209249；字体素材 `free-font/`；报告 `free-font-可商用字体库调研报告.md`。
