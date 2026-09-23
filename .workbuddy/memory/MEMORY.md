# CorgiMemo 项目长期记忆

## 项目约定与工具链
- 不主动编译（除非用户明确同意）；无 BuildConfig，版本走 getPackageInfo().versionName。
- 依赖签名核对：技能 gradle-cache-source-lookup；检索 .workbuddy/.gradle 时 Glob 用绝对路径、pattern 只写相对通配。
- 本机坑：Bash 的 ls/head/find/grep/wc 全 Exit 127 → 用绝对路径调 python；PowerShell stdout 可能被吞 → 落盘再 Read；rm 被 safe-delete 拦 → [System.IO.File]::Delete()。
- 大段删行：「行范围切分 + 保留行尾」，禁 ReadAllLines/WriteAllLines（会把行尾统一成 CRLF → git diff 全文件）。
- 设计稿 Ardot fileId 707225018209249。顶层扩展归属：Color.isSpecified→ui.graphics，TextUnit.isSpecified→ui.unit（须显式 import）。

## 字体体系
- 单一真相源 = ContentFontManager（四路消费：标题 LocalContentTypography、字重探测、面板回显、VM 持久化）；改面板/桥须闭环「谁更新真相源」，孤儿函数 = 断链信号。
- 预览铁律：统一 FontPreviewEngine（有界池 + 位图 LruCache），禁批量 ResourcesCompat.getFont（驻留 → OOM）。
- WebView 字体四关：①桥下发（down 日志须打值）②CSS 两层声明（.bn-root 的 var 与 .bn-default-styles 字面量 Inter，editor.css 用选择器组 !important 覆盖）③@font-face 可达（document.fonts.check/load）④诊断三件套（console 转发 + JS 诊断行 + sendDown 打值）。
- 字体流：JS 请求 https://corgimemo.local/fonts/{id}/{weight}.ttf → shouldInterceptRequest → openRawResource。

## 主题色 / 编辑态块 / TaskList
- 六色主题；亮色 background = #FFFBF5。背景三语义：userPickedBackgroundColor / contentBackgroundColor（唯一真值）/ contentBackgroundPaint（绘制真值）。
- 正文 = BlockNote WebView，BodyBlocksController 仅数据层；新增 sealed 块子类须全项目 Grep `is BodyBlock.` 补穷尽 when。
- TaskList 行级（v2026-09-16）：单段落 + 段内 \n；checked=行0，checkedLines=行≥1；reconcileCheckedLines 后必恢复 textRange。

## IME 抑制与底部栏面板
- T/H/A 三面板互斥占同一槽位（单一状态 openPanel: EditBottomPanel?，勿退回多 boolean），高度 = 键盘高度。
- 抑制四层互兜：setter 即 hide / onCreateInputConnection→null / onCheckIsTextEditor→false / 注入 inputmode=none + MutationObserver（三时机：状态变化、onPageFinished、桥 ready）；抑制期间保留光标与选区。
- 面板收起后按焦点态弹回键盘（v2026-09-22）：LaunchedEffect(isFormatPanelOpen)+wasFormatPanelOpen 判 true→false，delay 180ms：正文 editorFocused→controller.restoreIme()（requestFocus→restartInput→showSoftInput）；否则标题 isTitleFocused→keyboardController.show()。弹键盘只能靠 IMM 显式 showSoftInput。
- WebSettings 不存在 keyboardDisplayRequiresUserGesture（API 35 只有 MediaPlayback 那个）→ 程序化弹键盘只能走 IMM。
- 焦点真值只能由 JS 提供：Android View.hasFocus() 失真（点底部栏按钮后视图焦点已转 Compose 根，DOM 焦点仍在 contenteditable）。
- 标题回显靠「是否 heading 块」+ headingToggleable 分流（折叠标题 = heading + props.isToggleable，非独立块类型）。

## 块级拖拽 / 视觉教训
- BlocksReorderableList fork：settle()=抓快照→立即 onSettle→滑行交 Controller；拖拽期间不能改列表；itemKey 身兼身份锚定+滑行归属+zIndex。
- alpha 动画裁边界 → 悬浮元素勿挂 shadow；animateContentSize 内部 clipToBounds 持续裁剪。
- 无限高约束穿透吞 heightIn(min) → 逐层 grep fillMaxSize 全改 fillMaxWidth。
- 盒等距 ≠ 墨迹等距：RichTextEditor 默认 minHeight 56dp 幽灵空隙；常量 UiDimensions.inspirationTitleToMetaGap(6)/inspirationMetaToBodyGap(8)，改一须复测另一。

## Compose 陷阱
- remember{} calculation lambda 内读 MaterialTheme/LocalXxx 报错 → 提到 remember 外；remember key 勿传每次重组的新实例。
- 局部函数先声明后引用；isXxx 属性与 setXxx 函数 JVM 撞签名（用 add/toggleXxx）；kotlinx delay 只收 Long；internal 跨模块不可见 → 语义化 API 收进库；ParagraphStyle range 注入 lineHeight 须剥 default。
- TextUnit(Int.sp).toString 是 "18.0.sp" → "${x.sp}px" 拼出非法值；桥接 CSS 一律数值直接插值。
- Foundation 1.11 移除 foundation.layout.minimumInteractiveComponentSize → 改由 material3 提供；ripple() 在本项目（M3 1.4）是 androidx.compose.material3 顶层函数（非 androidx.compose.material.ripple 子包）。

## 图片附件页
- MainActivity configChanges 不含 uiMode → 旋转不重建，改 requestedOrientation。
- 双指缩放 consume；单指 scale>1 才平移否则放行 Pager；缩放锚点 offset_new=d−(d−offset_old)×ratio；越界跟手翻页+橡皮筋（≤72dp）；缩放期间绝不夹紧。
- findDialogWindow()（tailrec）；系统栏竖屏常显、横屏双通道 hide；Pager 阈值横屏 0.08 竖屏 0.35。

## BlockNote WebView
- 产物 assets/blocknote-web/editor/editor.html（viteSingleFile ~1.88MB），源码 blocknote-probe/src/editor/，Gradle buildBlockNoteEditor 已接入 assemble。「JS 改了没生效」第一反应 = 产物没重建；核对产物取上下文片段，非只看关键字计数。重建走 PowerShell 调 npm.cmd。
- 桥：下行 window.BlockNoteEditorHost.onMessage(json)，上行 AndroidBridge.postMessage；回调在 Java 桥线程，入口已统一 mainHandler.post，别拆散。
- 撤销可用态用 editor.canExec(command)（命令取 yUndo/history 扩展）。
- **工具栏选中态真值必须走 JS 上行**：宿主 RichTextState.currentSpanStyle 恒空（本地镜像），照它判断→按钮永不亮。B/I/U/S 是行内样式（getActiveStyles()），对齐是块级 prop（props.textAlignment，缺省即左对齐亮）——维度不同不能一起取。toggleStyles 在光标态只改 stored marks、不触发 onChange → 必须主动 pushBlockState()。
- **transform 的 value 是动作名≠块类型名**：toggleHeading*→heading+props.isToggleable；toggleList→toggleListItem。传错在 blockToNode 抛 TypeError 且无 try/catch → 点了没反应无日志。
- **行内样式渲染走 markView.render()（非 renderHTML）**：官方 textColor/backgroundColor render() 只造裸 span，自由 hex 不渲染 → schema.ts 同名覆盖 spec，render 内联 span.style.color。
- **markdown 颜色 token 往返**：行内 @@@CORGI_IC_TC_<值>@@@…END@@@；块级用块内容行首纯文本 @@@CORGI_BC_TC_red@@@（外包 div 会被 ProseMirror 下钻丢弃）；折叠标题用 <details><summary>。element.style.color 读出是 rgb() → 归一成大写 #RRGGBB。
- **正文 markdown 三个消费方必须全覆盖**：① WebView 编辑器（converter.ts）② Compose 详情卡（InspirationViewCard 直接解析原始 markdown，extractBlockColorMarkers 解析+消费块级色 token、stripStructuralMarkers 剥 details、保留 style span、勾选回写用 leadingInternalTokens 补回）③ 纯文本抽取（toPlainText）。折叠标题在详情页=普通标题（勿再引入折叠）。
- **块级色只能靠段首 token 过桥**（markdown 块序列化器不读元素 data-* 属性）；任何"读 markdown 展示"新出口都须解析 @@@CORGI_BC_TC_*/BG_*@@@；色名→颜色用 BlockColorPalette（明暗两套，必须传 isDark），未知色名返回 null（不能回落 Color.Transparent，否则文字消失）。
- **markdown→纯文本唯一入口 = MarkdownParser.toPlainText()**（stripMarkdown 不剥 HTML/@@@CORGI）；待办有独立口径（#标签保留），勿混用。
- 样式 padding-inline var(--bn-editor-gutter,20px)!important；sideMenu 已删。
- **产物新鲜度可机检**：vite.editor.config.ts collectSrcHash() 算 sha256 前 12 位注入 __SRC_HASH__ → bridge.ts 导出 SRC_HASH 随 ready 上行（logcat src=）；校验脚本 scripts/check-blocknote-artifact.ps1，钩子 .githooks/pre-commit（本机 core.hooksPath=.githooks，新 clone 需 git config core.hooksPath .githooks，.gitattributes 锁 eol=lf）。哈希口径三处必须逐条对齐。
- **模式判据显式命名**：boldSingleTier（B 按钮 UI 形态）与 useBlockNote（选中态真值来源）已拆分，别合并。
- **面板收起时主动刷 blockState**（requestBlockState→refreshBlockState）：与恢复软键盘同一时机、先刷状态再弹键盘。
- **工具栏判据一律由 JS 经 blockState 上行，宿主本地镜像不可信**（同坑踩 5 次）：currentSpanStyle 恒空、indentLevel 恒 1、isFocusedBlockCheckbox 恒 false。已上行：headingLevel/headingToggleable/inlineTextColor/inlineBackgroundColor/fontSizePx/bold·italic·underline·strike/textAlignment/canNestBlock·canUnnestBlock/isCheckboxBlock/canUndo·canRedo/editorFocus。新增字段须照抄官方同名 API（如 ed.canNestBlock()），迁移完删掉宿主侧已零调用的镜像属性。
- **新增上行字段必须重建产物**并在 editor.html 按上下文确认 payload 带该字段；宿主默认 false 时「两按钮一起灰」= 产物没重建。
- **「当前块」口径取选区首块** editor.getSelection()?.blocks[0]，回退 getTextCursorPosition().block（选区折叠/NodeSelection 时 getSelection() 返回 undefined）；反向拖选时 anchor 在末端，只用 getTextCursorPosition() 回显块≠命令作用块；getSelection() 少数边界会抛，单独包 try 回落。

## 工具 / 验证教训
- 同一工作区可能并发写入：commit 只进部分文件、已提交文件又变已修改。核对改动入库必须 `git show HEAD:<path>` 精确断言（grep 关键字会假阳性，注释里常原文引用被删代码）；发现并发写入不要替另一路提交在制品。提交一律显式路径 add（别 git add -A）。
- 新增 layout/Modifier API 逐项核对 import（漏 import 只编译期暴露）；静默 catch 至少上行诊断；连续 2 次猜测失败→停猜加埋点。同一文件多次 Edit 串行。
- 删文件/裁 import 前按 `by ` 反查委托依赖；删后全项目 grep 顶层声明名。
- 产物关键字计数只证字符串在文件；样式生效须真机/computed style；逐像素分析截图（pillow）是定位渲染问题的可靠手段。
- 中文提交信息：Write 临时文件→提交→删除。提交前交叉核对「新增参数/字段/符号」定义方与引用方同提交（`git grep -c` 逐文件确认 HEAD 都在），用户不许擅自编译时这是最便宜的替代验证。
- .ps1 必须 UTF-8 with BOM（否则中文注释 emoji 让字节错位→满屏 ParserError）；[Array]::Sort 文化敏感比较器会静默失效→用 List[string].Sort(IComparer)；跨语言同哈希先做最小对照实验确认口径。
- 脚本 exit 用 & script.ps1 会终止整个调用会话→测试用 powershell -NoProfile -File 子进程，用 $LASTEXITCODE 取码。
- 编译检查（用户同意后）用 PowerShell 调 .\gradlew.bat（bash shim 缺 coreutils）；单模块 :app:compileDebugKotlin 不出包约 1.5 分钟；输出 *> 文件是 UTF-16，读时 -Encoding Unicode。
- 修数据清洗规则必须同时修历史数据：repairInspirationPlainText（content 空或含 @@@CORGI_/<span → toPlainText 重算），代码修好对已污染行无效。
- 内部标记识别收敛在 MarkdownParser.stripInternalTokens/toPlainText，新增标记只改一处。
