# CorgiMemo 项目长期记忆

## 项目约定与工具链
- 不主动编译（除明确要求）；无 BuildConfig（版本走 getPackageInfo().versionName）。
- 依赖签名核对：技能 gradle-cache-source-lookup（find_sources_jar.py --class <裸类名> / extract_source.py）。
- 检索 .workbuddy/.gradle：Glob path 放绝对路径，pattern 只写相对通配。
- 本机坑：PowerShell stdout 可能被吞→重定向落盘再 Read 或优先 Read/Glob/Grep；Bash 的 ls/head/find/grep/wc 全 Exit 127→用绝对路径调 python；rm 被 safe-delete 拦截→[System.IO.File]::Delete()。
- 设计稿 Ardot fileId 707225018209249。
- 顶层扩展属性包归属：Color.isSpecified 在 androidx.compose.ui.graphics（非 ui.unit）；TextUnit.isSpecified 在 ui.unit。判据：inline val X.isY 的 import 包=其声明文件 package，与类型名无关。

## 字体体系
- 9 OFL 中文+3 拉丁；FontCatalog/FontManager/buildTypography，默认=系统默认。
- 预览铁律：统一 FontPreviewEngine（有界池+位图 LruCache），禁用 ResourcesCompat.getFont/Text(fontFamily) 批量渲染（驻留→OOM）。
- 合成族按字重 combinedFamilyFonts（Typeface.Builder(latin).addCustomFallback(cjk)）；用户内容走 ContentFontManager+LocalContentTypography。

## 主题色（ui/theme/Color.kt）
- 六色主题；亮色 background=暖米色 #FFFBF5，surface=White；暗色各异。
- 内容区背景收敛三语义：userPickedBackgroundColor（用户选了什么）/ contentBackgroundColor（唯一真值，Transparent→主题 background 回落，告知子组件）/ contentBackgroundPaint（绘制层真值，宿主 Column .background()）。别用一个变量兼两义（会漂移）。

## 编辑态块 / 图片 / TaskList
- BodyBlocksEditor UI 已下线，正文改 BlockNote WebView；BodyBlocksController 仅保留数据层。
- 新增 sealed 块子类必须全项目 Grep `is BodyBlock.` 补穷尽 when。
- 非文本块点选：焦点必须留 Text 块（cursorColor=Transparent），夺焦点=软键盘消失。
- 图片块：撑满=fillMaxWidth+aspectRatio(真实比例)；ImageAspectRatioCache 防塌陷；选中工具栏必须 Popup 独立窗口（focusable=false, clippingEnabled=false）；图片间必须空 Text 块（EMPTY_BLOCK_PLACEHOLDER，判空用 isBlank()）；载体空块 isImageSeparator 一旦有内容即清标记。
- 分割线五按钮工具条 256dp；DividerStyle↔markdown "---"/"--- dashed"/"--- wavy"。
- TaskList 行级（v2026-09-16 定稿）：单段落+段内 \n；checked=行0，checkedLines=行≥1；命中=行首；withCheckedLines 换新实例（deepCopy 带行级状态）；reconcileCheckedLines 行数变清 checkedLines（行0保留）后必恢复 textRange；parser 编码逐行前缀、连续同层级并段续行。

## 软键盘（IME）与面板让位（2026-09-21）
- 灵感编辑页「T 字体面板 / Aa 字号颜色面板」展开 = 键盘让位：派生值 `isFormatPanelOpen = isFontPanelExpanded || isSizeColorPanelExpanded`（二者互斥占同一槽位，同一帧切换故不会闪 false）→ 正文 WebView 传 `suppressIme`；标题 RichTextEditor 用 `PointerEventPass.Initial` 消费指针（点击不聚焦⇒不弹键盘，**勿用 enabled=false**：会切禁用色并清焦点）。
- 抑制软键盘四层（缺一不可，互兜）：① setter 变化即 hideSoftInputFromWindow（windowToken 需空值守卫）；② onCreateInputConnection→null；③ onCheckIsTextEditor→false；④ 页面注入 `inputmode="none"`（Chromium 官方抑制语义）+ MutationObserver 持续补标、按 data-ime-prev 精确还原。
- 实现类 `private ImeSuppressibleWebView`（BlockNoteEditorWebView.kt），JS 注入必须覆盖三时机：状态变化 / onPageFinished / 桥 ready（此时编辑器 DOM 才挂载）。
- 约定：抑制期间**保留光标与选区**；解除只恢复"可唤起"能力，**不主动弹回**键盘。

## 颜色入口与色板组件（2026-09-21）
- 入口唯一化：颜色全部集中在底部工具栏「A」按钮（位于 **Aa 正右侧**，T → Aa → A → B 三连）→ 展开**内联面板 `ColorStylePanel`**（与 T/Aa **三面板互斥、同槽位、同高度=键盘高度**），四组色板——**选中文字色 / 选中背景色**（行内，`format("textColor"/"backgroundColor", hex|"default")`）+ **段落文字色 / 段落背景色**（块级，`setBlockColor(textColor=/backgroundColor= 色名|"default")`）。⚠️ 两种维度不同、互不覆盖，别混。
- ⚠️ 「A」曾是 **AlertDialog 弹窗**（`ColorStyleDialog`，已删），v2026-09-21 按需求改为与 T/Aa 一致的内联面板；参数 `onOpenColorStyleDialog/showColorStyleDialog` 已改名 `onColorPanelClick/isColorPanelOpen`。面板内点选**不收起**（与 Aa 一致），靠「完成」或再点 A 收起。
- 三面板互斥：**单一状态 `openPanel: EditBottomPanel?`（FONT / SIZE_COLOR / COLOR，定义在 components/InspirationEditBottomBar.kt 的 public enum）**，`isFormatPanelOpen = openPanel != null` → 同时驱动 WebView `suppressIme` 与标题消费指针（键盘让位，见「软键盘（IME）与面板让位」节）。⚠️ 曾用三个 boolean（isFontPanelExpanded/isSizeColorPanelExpanded/isColorPanelExpanded）表达，靠"记得把另两个置 false"维持互斥，漏关一处即出「从 A 切到 T/Aa 面板叠加」；收敛后互斥由状态本身保证，**别退回多 boolean**。
- 三面板切换统一走 Screen 内**局部函数 `togglePanel(panel)`**（同值→置 null 收起；否则替换并 `keyboardController?.hide()`；收起不弹回键盘）；面板专属副作用（字体面板展开前重置 pending）在调用**之前**执行。⚠️ 局部函数**必须先声明后引用**（Kotlin 局部函数声明顺序即可见性），故它定义在 keyboardController 之后、三个回调之前。
- ⋮ 菜单（`BlockOpsMenuButton`）**已无颜色项**，只剩删除块 / 上移 / 下移 / 表头行·列；`onSetBlockColor` 参数链（Toolbar→BottomBar→Screen）已全部删除。
- 共用色板 `components/BlockColorPicker.kt`：`internal BlockColorPalette`（BlockNote 官方明暗各 9 色 + `names`）/ `BlockColorRow`（标签 + 默认斜杠点 + 9 色点）/ `blockColorHexOf(name,isDark,background)`（色名→"#RRGGBB"，供行内色下行）/ private `BlockColorDot(size, …)`。
- 色板排版（v2026-09-21，改前先读常量）：**色点边长按可用宽度自适应**——`BoxWithConstraints.maxWidth` → `(available − gap×(n−1)) / n` 并 clamp 到 `MinColorDotSize=24dp` / `MaxColorDotSize=36dp`；分布用 `Arrangement.SpaceBetween` 且**首尾留白固定 `ColorRowHorizontalPadding=12dp`**（与面板头标题对齐，用户要求保持），点间距 `ColorDotGap=8dp`。被 clamp 截断时剩余空间由 SpaceBetween 平均吸收，观感仍均匀。斜杠字号 = `size×0.5`（等比缩放）。
- 行内色**无状态上行**（桥未上行行内样式）→ 传 `showSelection=false`，否则「默认」恒高亮误导；块级色靠 `blockState.blockTextColor/blockBackgroundColor` 回显。

## 块级拖拽（自维护 fork BlocksReorderableList.kt）
- settle() 改「抓快照→立即 onSettle→滑行交 BlocksGlideController」；拖拽期间绝不能改列表（库 intervals 定长）；itemKey 身兼身份锚定+滑行归属+zIndex。

## 视觉/渲染教训
- alpha 动画必裁边界外绘制→悬浮元素不要挂 shadow/dropShadow（被切），定版用 1dp 黑25% 外边框；Modifier.shadow 在 scale+alpha 出方角阴影；animateContentSize 内部 clipToBounds() 持续裁剪。
- 无限高约束穿透多层吞宿主意图：host(verticalScroll)→Box→AndroidView 任一层 fillMaxSize 即在无限高解不出有限高→heightIn(min) 被静默吞。判据：heightIn(min) 没生效→逐层 grep fillMaxSize 全改 fillMaxWidth()，约定要在每一层都写。
- ⚠️ 布局盒等距≠墨迹视觉等距（2026-09-18）：Spacer 6dp 精确等距，截图实测墨迹间距 30/17dp。两个隐形行盒空间：①RichTextEditor 默认 minHeight=56dp（M3 标准），矮内容（单行标题）被强撑、12dp 幽灵空隙堆文字下方→矮输入场景传 0.dp；②Text(fontSize=X) 不显式 lineHeight 会 merge LocalTextStyle(bodyLarge 16/24) 大行盒→小字墨迹上下各 ~5dp。大字行盒的字形下沉空间（36sp 盒 ~7.8dp）字体固有，只能靠 Spacer 不对称补偿。已定版：密度 2.2（该机 864×1920 屏）复测 15.5/13.6dp，残差=正文首行 1.5 行距上方死空间 ~4.3dp>时间行 ~1.8dp；时间行→正文 Spacer 6→8dp 后两段均 ≈15.5dp。常量收敛：UiDimensions.inspirationTitleToMetaGap(6.dp)/inspirationMetaToBodyGap(8.dp)，改任一值须逐像素复测另一段。

## ⚠️ Compose 高频陷阱
- remember{} 的 calculation lambda 不是 @Composable，里面读 MaterialTheme.colorScheme.* / LocalXxx.current 报错；key 位置合法（组合期求值），只有大括号内非法→把组合读取提到 remember 外。derivedStateOf / LaunchedEffect 里读组合属性能编过但语义错（不随主题重组）。
- remember(key) 的 key 不要传每次重组新实例对象（新建 lambda/listOf）→缓存永久失效。

## 图片附件页（InspirationImageGallery）
- MainActivity configChanges（不含 uiMode）→旋转不重建，改 requestedOrientation。
- 缩放/翻页按指针数分流：双指始终缩放并 consume；单指仅 scale>1f 时 consume 平移否则放行 Pager。
- 缩放锚点 offset_new = d−(d−offset_old)×ratio；越界=实时跟手翻页+兜底橡皮筋（≤72dp），pan 直接累加绝不再加上一帧派发量；缩放期间绝不夹紧边界；手势结束才收回。
- 窗口/insets：LocalView.current 非 DialogLayout→findDialogWindow()（tailrec）；系统栏竖屏常显横屏双通道 hide；Pager 阈值横屏0.08 竖屏0.35。

## BlockNote WebView 编辑器（迁移 P1.5+）
- 产物 app/src/main/assets/blocknote-web/editor/editor.html（viteSingleFile 内联 ~1.88MB）；源码 blocknote-probe/src/editor/，改完必须重建。自 v1.8 :app:buildBlockNoteEditor 接入 Gradle（merge*Assets 依赖，带增量），assembleDebug/Release 自动带最新产物；手动 cd blocknote-probe && npm run build:editor。BlockNoteEditorScreen.kt（探针）与 BlockNoteEditorWebView.kt（共用）勿混。
- ⚠️ 「JS 改了真机没生效」第一反应=产物没重建。判据：git log -1 比对源码 vs 产物；产物 diff「N增N删」是假象（vite 重排短变量名），绝不用 diff 行数判断，用关键字计数；仍不生效再排 WebView 缓存（卸载重装/清数据）。
- 构建指纹 __BUILD_FINGERPRINT__ 随 ready 上行，logcat `ready received | build=...` 查新鲜度。
- Bridge：下行 evaluateJavascript("window.BlockNoteEditorHost.onMessage(<json>)");上行 AndroidBridge.postMessage(json)；协议 docs/bridge-protocol.md（ready/changed/error/undoState）。
- ⚠️ **桥回调在 Java 桥线程**（2026-09-21 实测踩坑，已结构性加固）：`@JavascriptInterface` 的 `AndroidBridge.postMessage` → `handleUpMessage` 运行在 WebView 的 Java 桥线程，其中**任何 WebView 方法（evaluateJavascript / loadUrl / setBackgroundColor）都只能在 UI 线程调用**。现已在入口统一 `mainHandler.post` 再解析（`handleUpMessage` 切线程 → `handleUpMessageOnMainThread` 处理），新增分支不必再单独 post，**别拆散这层包装**。踩坑实例：在 `ready` 分支同步调 evaluateJavascript → IllegalStateException 被 catch 吞掉 → `flushIfReady()` 不执行 → `init` 永不下发 → JS `booted` 恒 false → 正文一直「正在装载…」。
- ⚠️ JS 侧 `booted` 只在收到 `init` 下行时置真（EditorApp.tsx `case "init"`）；装载文案在 JS、开关在 Kotlin——排查 WebView 问题先分清两端状态。
- ⚠️ 撤销/重做可用态只能用 editor.canExec(command)——editor.can 不存在（v1.7→v1.10 按钮恒灰真根因）。命令必须从扩展取（getExtension("yUndo")??getExtension("history") 的 undoCommand/redoCommand），不能传 editor.undo（丢 this）也不能传 @tiptap/pm/history 裸 undo；别读 _tiptapEditor；canExec 在 editor.transact() 内会抛。
- 样式约束（最终态，改前必看）：
  - padding-inline 用 var(--bn-editor-gutter,20px)!important（左右同值；20px 是嵌套列表竖线 left:-20px 下限）；右侧 54px 官方手柄位在手机全宽浪费，sideMenu={false} 整体删除（BlockNote 块拖拽纯 HTML5 DnD，Android WebView 触摸不触发→手机拖不动，改工具栏上移/下移）。
  - ⚠️ CSS 变量就近取值：库在 .bn-root 定义 --bn-colors-*，设在 <html> 会被盖掉→编辑器背景始终纯白。修法：在 .bn-root（含暗色分支）带 !important 重声明 + JS querySelectorAll('.bn-root').forEach setProperty(k,v,'important')。通用规则：覆盖库 CSS 变量前先在产物搜 `.bn-xxx{--var` 找定义选择器。
  - .bn-editor border-radius:0；html/body background 也设宿主背景消除画中画；.editor-page 去 max-width/margin auto；探针 src/probe.css body 背景须改 transparent；首帧防闪白 html,body 内联 background。
  - 编辑器首部留白：.bn-editor{padding:0}，首块 .bn-block-content{padding:3px 0} 上 3px；宿主用 Compose Spacer 控制间距时需在 editor.css 把首部压 0 才能精确等距（v2026-09-18 灵感编辑页三段间距即此做法；⚠️ 但盒间距等距≠墨迹视觉等距，见视觉/渲染教训）。

## 工具/协作教训
- 遇「API 看起来应该有但没反应」先查它在不在（grep public xxx），别论证语义。
- catch{return} 是隐形杀手：静默失败至少上行一次诊断。
- 连续 2 次猜测→改码→失败，停猜加埋点取真实数据。
- 同一文件多次 Edit 必须串行（并行写竞态）。
- 提交：中文提交信息，Write 临时文件→提交→删除。

## ⚠️ 验证边界（2026-09-18）
- 产物关键字计数只证"字符串在文件里"不证"浏览器用得上"；涉及变量/样式/覆盖是否生效，必须真机/浏览器读 computed style 才算验过。
- 逐像素分析截图是定位渲染问题的可靠手段（pillow 扫描非背景像素得边界/颜色，实测像素÷已知 dp 反推 density 交叉验证）；目测与纯推算只能给方向。

## ⚠️ 删文件/裁 import 的隐身依赖
- 靠符号名 grep/词频判 import 可删必然出错——Kotlin 无名字依赖：①by 委托（mutableStateOf/lazy/remember/Delegates.observable）隐式 getValue/setValue，误删级联十几处假象错误；②跨文件 internal 顶层函数/扩展（同包无 import 调用）；③LocalXxx 组合局部变量（只剩 .hide() 也需 import）；④import X as Y 别名按原名 grep 漏。
- 正确姿势：裁 import 前按 `by ` 反查全文；删文件/大段代码后反向全项目 grep 被删文件顶层声明名（尤其 internal fun ...Scope.xxx）；最终以编译为准。
