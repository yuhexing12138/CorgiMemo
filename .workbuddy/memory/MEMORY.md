# CorgiMemo 项目长期记忆

## 项目约定与工具链
- 不主动编译（除明确要求）；无 BuildConfig（版本走 getPackageInfo().versionName）。
- 依赖签名核对：技能 gradle-cache-source-lookup。检索 .workbuddy/.gradle：Glob path 用绝对路径、pattern 只写相对通配。
- 本机坑：Bash 的 ls/head/find/grep/wc 全 Exit 127 → 用绝对路径调 python；PowerShell stdout 可能被吞 → 落盘再 Read；rm 被 safe-delete 拦 → [System.IO.File]::Delete()。
- 大段删行：「行范围切分 + 保留行尾」，禁 ReadAllLines/WriteAllLines（会把行尾统一成 CRLF → git diff 全文件）。
- 设计稿 Ardot fileId 707225018209249。顶层扩展归属：Color.isSpecified→ui.graphics，TextUnit.isSpecified→ui.unit。

## 字体体系
- 单一真相源 = ContentFontManager（四路消费：标题 LocalContentTypography、字重探测、面板回显、VM 持久化）。改面板/桥必须闭环「谁更新真相源」；孤儿函数 = 断链信号。
- 预览铁律：统一 FontPreviewEngine（有界池 + 位图 LruCache），禁批量 ResourcesCompat.getFont（驻留 → OOM）。合成族 = latin + cjk fallback；Web 侧 fontStack = [latin, cjk, system-ui]（拉丁在前）。
- WebView 字体四关：①桥下发（down 日志须打值）②CSS 声明链两层——`.bn-root` 的 var **与** `.bn-default-styles` 的**字面量 Inter**（editor.css 用选择器组统一 `!important` 覆盖；只搜 var 引用会漏）③@font-face 可达（document.fonts.check/load）④诊断三件套（console 转发 + JS 诊断行 + sendDown 打值）。判读：rootFf 对而 contentFf 错 = 中间层有声明。
- 字体流：JS 请求 `https://corgimemo.local/fonts/{id}/{weight}.ttf` → shouldInterceptRequest → openRawResource。

## 主题色 / 编辑态块 / TaskList
- 六色主题；亮色 background = #FFFBF5。背景三语义别混：userPickedBackgroundColor / contentBackgroundColor（唯一真值）/ contentBackgroundPaint（绘制真值）。
- 正文 = BlockNote WebView，BodyBlocksController 仅数据层。新增 sealed 块子类须全项目 Grep `is BodyBlock.` 补穷尽 when。
- 非文本块点选焦点必须留 Text 块（cursorColor=Transparent），夺焦点 = 键盘消失。图片块 fillMaxWidth+aspectRatio + ImageAspectRatioCache；选中工具栏 Popup 独立窗口（focusable=false）。
- TaskList 行级（v2026-09-16）：单段落 + 段内 \n；checked=行0，checkedLines=行≥1；reconcileCheckedLines 后必恢复 textRange。

## IME 抑制与底部栏面板
- T/H/A 三面板互斥占同一槽位（单一状态 `openPanel: EditBottomPanel?`，**勿退回多 boolean**），高度 = 键盘高度。
- 抑制四层互兜：setter 即 hide / onCreateInputConnection→null / onCheckIsTextEditor→false / 注入 `inputmode=none` + MutationObserver；注入三时机（状态变化、onPageFinished、桥 ready）。抑制期间保留光标与选区。
- **v2026-09-22：面板收起后按焦点态弹回键盘**。Screen 用 `LaunchedEffect(isFormatPanelOpen)` + `wasFormatPanelOpen` 判「true→false」，delay 180ms 后：正文 editorFocused → `controller.restoreIme()`（focusEditor 下行 → requestFocus → restartInput → showSoftInput）；否则标题 isTitleFocused → `keyboardController.show()`。弹键盘只能靠 IMM 显式 showSoftInput（post 里执行，等焦点稳定）。
- ⚠️ `WebSettings` **不存在** `keyboardDisplayRequiresUserGesture`（API 35 android.jar 里只有 MediaPlayback 那个；该语义属别的平台/别的 WebView 封装，别再搬）→ 程序化聚焦弹键盘只能走 IMM。
- 焦点真值只能由 JS 提供：Android `View.hasFocus()` 失真（点底部栏按钮后视图焦点已转到 Compose 根视图，DOM 焦点仍在 contenteditable）。
- 锁定态面板：禁用仅内容区 alpha(0.38) + Initial 消费指针，面板头「完成」保持可点。
- 标题回显靠「是否 heading 块」+ headingToggleable 布尔分流（折叠标题 = `heading` + `props.isToggleable`，**不是**独立块类型；级别数字两类重叠）。

## 块级拖拽 / 视觉教训
- BlocksReorderableList fork：settle()=抓快照→立即 onSettle→滑行交 Controller；拖拽期间不能改列表；itemKey 身兼身份锚定+滑行归属+zIndex。
- alpha 动画裁边界 → 悬浮元素勿挂 shadow；animateContentSize 内部 clipToBounds 持续裁剪。
- 无限高约束穿透吞 heightIn(min) → 逐层 grep fillMaxSize 全改 fillMaxWidth。
- 盒等距 ≠ 墨迹等距：RichTextEditor 默认 minHeight 56dp 幽灵空隙；Text 无显式 lineHeight 继承大行盒。常量 UiDimensions.inspirationTitleToMetaGap(6)/inspirationMetaToBodyGap(8)，改一须复测另一。

## Compose 陷阱
- remember{} calculation lambda 内读 MaterialTheme/LocalXxx 报错 → 提到 remember 外；remember key 勿传每次重组的新实例。
- 局部函数/变量先声明后引用；isXxx 属性与 setXxx 函数 JVM 撞签名（用 add/toggleXxx）；kotlinx delay 只收 Long；internal 跨模块不可见 → 语义化 API 收进库；ParagraphStyle range 注入 lineHeight 须剥 default。
- ⚠️ TextUnit（`Int.sp`）toString 是 `"18.0.sp"` → `"${x.sp}px"` 拼出 `"18.0.sppx"` 非法值；桥接 CSS 一律数值直接插值。

## 图片附件页
- MainActivity configChanges 不含 uiMode → 旋转不重建，改 requestedOrientation。
- 双指缩放 consume；单指 scale>1 才平移否则放行 Pager；缩放锚点 offset_new=d−(d−offset_old)×ratio；越界跟手翻页+橡皮筋（≤72dp）；缩放期间绝不夹紧。
- findDialogWindow()（tailrec）；系统栏竖屏常显、横屏双通道 hide；Pager 阈值横屏 0.08 竖屏 0.35。

## BlockNote WebView
- 产物 `assets/blocknote-web/editor/editor.html`（viteSingleFile ~1.88MB），源码 blocknote-probe/src/editor/，Gradle `buildBlockNoteEditor` 已接入 assemble。「JS 改了没生效」第一反应 = 产物没重建；用关键字计数而非 diff 行数；构建指纹随 ready 上行（logcat `build=`）。
- 桥：下行 `window.BlockNoteEditorHost.onMessage(json)`，上行 `AndroidBridge.postMessage`。⚠️ 桥回调在 Java 桥线程，入口已统一 mainHandler.post，别拆散包装。
- 撤销可用态用 `editor.canExec(command)`（`editor.can` 不存在；命令取 yUndo/history 扩展）。
- ⚠️ **transform 的 value 是动作名，不等于块类型名**：`toggleHeading*` → `heading` + `props.isToggleable`；`toggleList` → `toggleListItem`。合法类型只有 defaultBlockSpecs 那一份。传错会在 `blockToNode` 抛 TypeError，而 format 分支无 try/catch → "点了没反应且无日志"。
- ⚠️ **行内样式渲染走 markView 的 `render()`，不是 renderHTML**；官方 textColor/backgroundColor 的 render() 只造裸 span，颜色靠 CSS 预设色名规则 → 自由 hex 不渲染。修法：schema.ts 同名覆盖 spec，render 内联 `span.style.color`。
- ⚠️ 官方 markdown 导出剥颜色 span / 丢折叠标记 → converter.ts 用 token 往返：行内色 `@@@CORGI_IC_TC_<值>@@@`…`@@@CORGI_IC_END@@@`；块级色用**块内容行首**纯文本 token `@@@CORGI_BC_TC_red@@@`（外包 div 会被 ProseMirror 下钻丢弃）；折叠标题用 `<details><summary>` 三段。纯文本转换必须剥离这些标记（Kotlin markdownToPlainText 已加）。
- ⚠️ `element.style.color` 读出是 `rgb(r,g,b)` 而非 hex → 必须归一成大写 `#RRGGBB`，否则二次保存被"值形态不安全"跳过。
- 样式：padding-inline `var(--bn-editor-gutter,20px)!important`；sideMenu 已删（HTML5 DnD 触摸不触发）。

## 工具/验证教训
- 新增 layout/Modifier API 必须逐项核对 import（漏 import 只编译期暴露）。「API 应该有但没反应」先 grep 确认存在。静默 catch 至少上行诊断；连续 2 次猜测失败 → 停猜加埋点。同一文件多次 Edit 串行。
- 删文件/裁 import 前按 `by ` 反查委托依赖；删后全项目 grep 顶层声明名。
- 产物关键字计数只证字符串在文件里；样式生效必须真机/computed style；逐像素分析截图（pillow）是定位渲染问题的可靠手段。
- 提交：中文提交信息，Write 临时文件 → 提交 → 删除。
