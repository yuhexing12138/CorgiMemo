# CorgiMemo 项目长期记忆

## 项目约定与工具链
- 不主动编译（除明确要求）；无 BuildConfig（版本走 getPackageInfo().versionName）。
- 依赖签名核对：技能 gradle-cache-source-lookup（find_sources_jar.py / extract_source.py）。
- 检索 .workbuddy/.gradle：Glob path 放绝对路径，pattern 只写相对通配。
- 本机坑：Bash 的 ls/head/find/grep/wc 全 Exit 127→用绝对路径调 python；PowerShell stdout 可能被吞→落盘再 Read；rm 被 safe-delete 拦→[System.IO.File]::Delete()。
- 设计稿 Ardot fileId 707225018209249。
- 顶层扩展属性包归属：Color.isSpecified 在 androidx.compose.ui.graphics；TextUnit.isSpecified 在 ui.unit。判据：inline val X.isY 的 import 包=声明文件 package。

## 字体体系与 T 面板字体链（v2026-09-21 修复）
- 9 OFL 中文+3 拉丁；FontCatalog/FontManager/buildTypography；预览铁律：统一 FontPreviewEngine（有界池+位图 LruCache），禁批量 ResourcesCompat.getFont（驻留→OOM）；合成族 combinedFamilyFonts（latin+cjk fallback）；用户内容走 ContentFontManager+LocalContentTypography。
- ⚠️ **单一真相源=ContentFontManager**，四路消费：标题 LocalContentTypography、字重探测、面板回显、VM 保存持久化。v2026-09-21 实测根因：T 面板「应用」只给 WebView 单发 setFontFamily、**绕过状态链**（VM onCjkFontSelected/onLatinFontSelected 定义后 0 调用点）→ 标题不换字/字体不持久化/重开面板回显旧值/「应用」恒亮。修复：onFontPanelDismiss 走 VM 回调；新增 LaunchedEffect(contentFontEntry.id) 响应式下发 WebView；load(markdown, fontFamilyId) init 携带回显（原硬编码 system_default）。教训：改面板/桥时「谁更新真相源」必须闭环；孤儿函数=断链信号。
- 拉丁下行通道（v2026-09-21 已打通）：bridgeFontResMap=entries+latinEntries 全量；桥 load 双 id + setLatinFontFamily 命令；JS fontStack=[latin, cjk, system-ui]（**拉丁在前**=拉丁字形优先，与 Compose combinedFamily 语义一致）；Screen 双 LaunchedEffect 分别跟随 contentFontEntry.id 与 contentLatinFontId。
- ⚠️ WebView 字体四关（缺一即"正文不生效"）：①桥下发（init/setFontFamily，Kotlin down 日志须打值）②CSS 声明链两层——库在 `.bn-root`（var(--bn-font-family)）**和** `.bn-default-styles`（**字面量** Inter，@blocknote/core editor.css，挂在编辑器容器、位于 root 与 .ProseMirror 之间，自身声明压过祖先继承；搜 var 引用会漏掉字面量）都声明了 font-family，editor.css 用选择器组 `.bn-root,.bn-default-styles,.bn-editor` 统一覆盖 `var(--content-font,...)!important`。诊断判读：rootFf 对而 contentFf 错 = 中间层有声明 ③@font-face 资源可达（document.fonts.check/load 探测，check=false=未注册、loaded=0=文件加载失败）④诊断三件套：WebChromeClient console 转发 + JS `font |` 诊断行（setFontFamily 后 600ms 上行）+ sendDown 打参数值（init 特判 fontFamily+fonts 条数）。
- WebView 字体流：JS 请求 https://corgimemo.local/fonts/{id}/{weight}.ttf → shouldInterceptRequest openRawResource 回流；@font-face 由 init fonts 清单生成。

## 主题色 / 编辑态块 / TaskList
- 六色主题；亮色 background=暖米色 #FFFBF5。内容区背景三语义：userPickedBackgroundColor / contentBackgroundColor（唯一真值）/ contentBackgroundPaint（绘制真值），别一变量两义。
- 正文=BlockNote WebView；BodyBlocksController 仅数据层。新增 sealed 块子类必须全项目 Grep `is BodyBlock.` 补穷尽 when。
- 非文本块点选焦点必须留 Text 块（cursorColor=Transparent），夺焦点=键盘消失。
- 图片块：fillMaxWidth+aspectRatio；ImageAspectRatioCache 防塌陷；选中工具栏 Popup 独立窗口（focusable=false, clippingEnabled=false）；图片间空 Text 块（EMPTY_BLOCK_PLACEHOLDER，判空 isBlank()）。
- TaskList 行级（v2026-09-16 定稿）：单段落+段内 \n；checked=行0，checkedLines=行≥1；withCheckedLines 换新实例；reconcileCheckedLines 行数变清 checkedLines（行0保留）后必恢复 textRange。

## IME 抑制与底部栏面板（v2026-09-21 现状）
- T/H/A 三面板互斥占同一槽位（单一状态 openPanel: EditBottomPanel?，**勿退回多 boolean**），高度=键盘高度；isFormatPanelOpen → WebView suppressIme + 标题 PointerEventPass.Initial 消费指针（勿 enabled=false）。H=标题+字号（FONT_SIZE_TIERS/DEFAULT_BODY_SP 在 HeadingPanel.kt 须 public）；A=行内/块级四组色板（两种维度别混）；T=字体（分离式预览：点选只改 pending 高亮，「应用」才生效且不收起）。
- IME 抑制四层互兜：setter 即 hide（windowToken 守卫）/onCreateInputConnection→null/onCheckIsTextEditor→false/注入 inputmode=none+MutationObserver 补标；注入覆盖三时机（状态变化/onPageFinished/桥 ready）。抑制期间保留光标选区；解除不主动弹回键盘。实现 ImeSuppressibleWebView（BlockNoteEditorWebView.kt）。
- 图标 BlockNotePlusMenuIcons：属性+defs map 两处都加，path 取 react-icons/ri；必须同步 blocknote-probe/tools/extract-ri-icons.cjs 的 WANT 清单。
- 面板行距节奏（H 对齐 A）：每行/分区上下 6dp（BlockColorRow vertical padding，横向 12dp）；H 分区间不放额外 Spacer。
- 锁定态：HeadingPanel/ColorStylePanel 有 enabled 参数——禁用仅内容区 alpha(0.38)+Initial 消费，面板头「完成」保持可点；T 面板尚无 enabled（未统一）。
- 行内色无状态上行→showSelection=false；块级色靠 blockState 回显；标题回显靠 blockType+headingLevel 分流（不能只看 level，两类都有 1/2/3）。
- 色板排版：色点边长按可用宽度自适应 clamp 24~36dp，SpaceBetween，首尾留白 ColorRowHorizontalPadding=12dp，点间距 8dp；斜杠字号=size×0.5。

## 块级拖拽 / 视觉教训
- BlocksReorderableList fork：settle()=抓快照→立即 onSettle→滑行交 BlocksGlideController；拖拽期间不能改列表（库 intervals 定长）；itemKey 身兼身份锚定+滑行归属+zIndex。
- alpha 动画裁边界→悬浮元素勿挂 shadow；animateContentSize 内部 clipToBounds 持续裁剪。
- 无限高约束穿透吞 heightIn(min)→逐层 grep fillMaxSize 全改 fillMaxWidth（每层都写）。
- 盒等距≠墨迹等距：RichTextEditor 默认 minHeight 56dp 幽灵空隙（矮内容传 0.dp）；Text 无显式 lineHeight 继承大行盒。常量 UiDimensions.inspirationTitleToMetaGap(6)/inspirationMetaToBodyGap(8)，改任一须逐像素复测另一段。

## Compose 陷阱
- remember{} calculation lambda 内读 MaterialTheme/LocalXxx 报错→组合读取提到 remember 外；remember key 勿传每次重组的新实例。
- 局部函数/变量先声明后引用；isXxx 属性与 setXxx 函数 JVM 撞签名（用 add/toggleXxx）；kotlinx delay 只收 Long；internal 跨模块不可见→语义化 API 收进库；ParagraphStyle range 注入 lineHeight 会被编辑器 textStyle 压制（须剥 default）。
- ⚠️ TextUnit（`Int.sp`）的 toString 是 `"18.0.sp"` 格式——字符串插值 `"${x.sp}px"` 会拼出 `"18.0.sppx"` 非法值（H 面板字号不生效真因，v2026-09-21）；桥接 CSS 值一律数值直接插值。

## 图片附件页
- MainActivity configChanges 不含 uiMode→旋转不重建，改 requestedOrientation。
- 双指缩放 consume；单指 scale>1 才平移否则放行 Pager；缩放锚点 offset_new=d−(d−offset_old)×ratio；越界跟手翻页+橡皮筋（≤72dp）；缩放期间绝不夹紧；手势结束才收回。
- findDialogWindow()（tailrec）；系统栏竖屏常显横屏双通道 hide；Pager 阈值横屏 0.08 竖屏 0.35。

## BlockNote WebView
- 产物 assets/blocknote-web/editor/editor.html（viteSingleFile ~1.88MB）；源码 blocknote-probe/src/editor/，改完必须重建（Gradle :app:buildBlockNoteEditor 已接入 assemble）。⚠️「JS 改了没生效」第一反应=产物没重建；diff 行数是假象，用关键字计数。构建指纹随 ready 上行（logcat build=）。
- Bridge：下行 window.BlockNoteEditorHost.onMessage(json)；上行 AndroidBridge.postMessage；⚠️ 桥回调在 Java 桥线程，入口已统一 mainHandler.post（handleUpMessageOnMainThread），别拆散包装（踩坑：ready 分支同步 evaluateJavascript→异常被吞→init 永不下发→正文卡「正在装载」）。JS booted 只在收 init 置真。
- 撤销可用态用 editor.canExec(command)（editor.can 不存在；命令从 yUndo/history 扩展取）。
- 样式：padding-inline var(--bn-editor-gutter,20px)!important；sideMenu 已删（WebView 触摸不触发 HTML5 DnD→工具栏上移/下移）；库 CSS 变量就近覆盖（.bn-root 带 !important+JS setProperty important）；.bn-editor padding:0 首块 3px 0。

## 工具/验证教训
- 新增 layout/Modifier API 必须逐项核对 import（漏 import 只编译期暴露；本项目不主动编译）。「API 应该有但没反应」先 grep 确认存在。catch{return} 静默失败至少上行诊断。连续 2 次猜测失败→停猜加埋点。同一文件多次 Edit 串行。
- 删文件/裁 import 前按 `by ` 反查依赖（by 委托隐式 getValue/setValue）；删后反向全项目 grep 顶层声明名；最终以编译为准。
- 产物关键字计数只证字符串在文件里；样式是否生效必须真机/computed style。逐像素分析截图（pillow 扫描）是定位渲染问题的可靠手段。
- 提交：中文提交信息，Write 临时文件→提交→删除。
