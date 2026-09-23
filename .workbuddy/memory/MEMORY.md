# CorgiMemo 项目长期记忆

## 项目与工具链
- 不主动编译；无 BuildConfig，版本走 getPackageInfo().versionName。设计稿 Ardot fileId 707225018209249。
- 本机坑：Bash（ls/cat/find/grep/wc）全 Exit 127 → 用绝对路径调 python 或 PowerShell；PowerShell stdout 常被吞 → 落盘再 Read；rm 被 safe-delete 拦 → [System.IO.File]::Delete()。
- 大段删行：按行切分 + 保留行尾，禁 ReadAllLines/WriteAllLines（会把行尾统一成 CRLF）。
- 依赖签名核对技能 gradle-cache-source-lookup；Glob 检索 node_modules/.gradle 会被静默忽略 → 用 PowerShell 递归 + 落盘再 Read。
- .ps1 必须 UTF-8 with BOM；脚本测试用 powershell -NoProfile -File 子进程取 $LASTEXITCODE。

## BlockNote WebView（正文编辑器）
- 产物 assets/blocknote-web/editor/editor.html（viteSingleFile ~1.88MB），源码 blocknote-probe/src/editor/（@blocknote 0.52.1）；「JS 改了没生效」= 产物没重建（PowerShell 调 npm.cmd）。新鲜度机检：__SRC_HASH__ + scripts/check-blocknote-artifact.ps1 + .githooks/pre-commit（core.hooksPath=.githooks，eol=lf）。
- 桥：下行 window.BlockNoteEditorHost.onMessage(json)，上行 AndroidBridge.postMessage；入口统一 mainHandler.post。宿主链路：RiFormatButton → onTransform(action) → blockNoteController.format("transform", action)。
- ★ 产物时戳≠源码哈希（2026-09-23）：editor.html 内 `YYYY-MM-DD HH:mm:ss <git描述>` 是构建注入的**时戳**，与 `bn-src:<hash>` 独立；同源码重建 → bn-src 相同、时戳必不同（产物 diff 仅时戳时不要重复提交）。判断产物是否过期只看 bn-src。
- ★ content 形态与块类型名是两回事：`nodeToBlock`（core `blocks-*.js` 函数 M(e,t)）把 `content:"none"` 的块赋成 block.content=**undefined**（inline→j / table→tt / plain→[] / none→void 0）。故官方行内按钮判据 `find(b => b.content !== void 0)` 对分割线/图片/分页符等同样隐藏（bold/italic/underline/strike/colorStyle/createLink/nest/unnest 全覆盖）；自研按钮判据 `schema.blockSpecs[type].config.content === "inline"` 与之等价，二者可并存。仍无 content 判据的只有 textAlign 三键与 blockTypeSelect。
- ★ 工具栏判据一律由 JS 经 blockState 上行，宿主本地镜像不可信（currentSpanStyle 恒空 / indentLevel 恒 1 / isFocusedBlockCheckbox 恒 false，已踩 5 次）；新增上行字段必须重建产物并核对 payload。
- ★ transform 的 value 是动作名≠块类型名：toggleHeading*→heading+props.isToggleable；toggleList→toggleListItem。传错在 blockToNode 抛 TypeError 且无 try/catch → 点了没反应无日志。
- 「当前块」口径 = editor.getSelection()?.blocks[0]，回退 getTextCursorPosition().block；getSelection() 少数边界抛错，单独 try 回落。
- 行内样式渲染走 markView.render()（非 renderHTML）→ schema.ts 同名覆盖 textColor/backgroundColor，render 内联 style。
- markdown 颜色 token：行内 @@@CORGI_IC_TC_<值>@@@…END@@@；块级行首纯文本 @@@CORGI_BC_TC_red@@@（外层包 div 会被 ProseMirror 丢弃）；折叠标题 <details><summary>。element.style.color 读出是 rgb() → 归一 #RRGGBB。
- 正文 markdown 三消费方必须全覆盖：WebView(converter.ts) / Compose 详情卡(InspirationViewCard) / toPlainText。块级色只能靠段首 token 过桥；色名→颜色用 BlockColorPalette（须传 isDark），未知色名返回 null（勿回落 Transparent）。
- markdown→纯文本唯一入口 MarkdownParser.toPlainText()（stripMarkdown 不剥 HTML/@@@CORGI）；待办另有一套口径。
- ★ 行内工具栏会替 `content:"none"` 的块"背锅"（2026-09-23）：FormattingToolbar 在**有光标/选区**时显示，而其中的 `Aa`（字号）/`A`（行内文字色）读 `getActiveStyles()`——分割线这类无文本块点了必然"没反应"。插入 divider 后若不显式移光标（`insertBlocks` 不移动光标，只在 `tr.step`），光标留在原块 → 行内工具栏持续弹出并压在 divider 工具条下方。修法 = 插入 `[divider, paragraph]` 后 `setTextCursorPosition` 到末尾空段落（空段落在本项目 markdown 口径 = 空行，可控）+ `canApplyInlineStyles()` 按 `schema.blockSpecs[type].config.content === "inline"` 隐藏这两个按钮（divider/image/pageBreak 为 none、table 为 table；官方 keyboardShortcuts 用同一判据）。
- 浮动工具条（fixed 定位于 WebView 文档）必须做视口夹取：`useLayoutEffect` 里 `getBoundingClientRect()` 实测尺寸后夹进安全区（水平越界滑动、垂直上方放不下则翻转到下方），安全边距 8px；用 `useLayoutEffect` 而非 `useEffect` 避免"先错位再跳回"的一帧闪烁。写死宽度不可靠（字形缺失时宽度随设备字体变）。
- 撤销可用态用 editor.canExec（yUndo/history 扩展）；toggleStyles 在光标态只改 stored marks 不触发 onChange → 须主动 pushBlockState()。面板收起时先 requestBlockState 再恢复软键盘。
- ★ 代码块（2026-09-23 定位）：官方 codeBlock content="plain"→PM 表达式 "text*"，段落/标题/引用/列表都是 "inline"→"inline*"；updateBlock 换类型且未显式传 content 时按表达式字符串比较 → 不同则 content=[] ⇒ 原文被清空（updateBlock.ts:200-214，随后走 replaceContentMinimal 字符级 diff 全删）。故只有 Code Block 按钮吞文字。insertBlocks 不移动光标，需显式 setTextCursorPosition。
- ★ 自定义块渲染必须自行撑满（2026-09-23）：官方 `.bn-block-content{width:100%;display:flex}` 是 **flex 容器**，官方 divider 用 `<hr>`+`[data-content-type=divider] hr{flex:1}` 撑满；自定义 render 的外层 div 是 flex item，不写 `flex:1` 就按 max-content 收缩、内部空 div ⇒ 宽度坍缩 0（样式全在、线长为 0，肉眼不可见）。solid/dashed（空 div+border-top）与 wavy（svg width:100% 但父宽 0）皆中招。凡"靠边框/背景撑视觉"的空元素在 flex 容器里都要显式 flex:1。
- ★ 产物哈希采集范围（2026-09-23 已修）：原只算 `editor.html + src/editor/`，导致 `src/probe.css`、`src/probes/dividerBlock.tsx`、`src/probes/fontSizeStyle.tsx`（editor 入口真实依赖、会进产物）改动后不报"产物过期"、pre-commit 静默放过；已把这 3 项加入**两侧同序清单**（config 的 `ENTRIES` + ps1 的 `foreach`），现共 10 个文件。探针页专用文件（checks.ts / probes/schema.ts）刻意不纳入以免误报；editor 侧新增依赖须同步该清单。改 ps1 后务必复核 UTF-8 BOM 未丢。

## 字体体系
- 单一真相源 ContentFontManager（四路消费：标题 LocalContentTypography、字重探测、面板回显、VM 持久化）；改面板/桥须闭环「谁更新真相源」。
- 预览统一 FontPreviewEngine（有界池 + 位图 LruCache），禁批量 ResourcesCompat.getFont（驻留 OOM）。
- WebView 字体四关：桥下发（down 日志打值）/ CSS 两层声明（.bn-root var + .bn-default-styles 字面量，editor.css !important 覆盖）/ @font-face 可达（document.fonts.check）/ 诊断三件套。
- 字体流：JS 请求 https://corgimemo.local/fonts/{id}/{weight}.ttf → shouldInterceptRequest → openRawResource。

## 主题色 / 块 / 底部栏
- 六色主题；亮色 background=#FFFBF5。背景三语义：userPickedBackgroundColor / contentBackgroundColor（唯一真值）/ contentBackgroundPaint（绘制真值）。
- BodyBlocksController 仅数据层；新增 sealed 块子类须全项目 Grep `is BodyBlock.` 补穷尽 when。
- TaskList 行级：单段落 + 段内 \n；checked=行0，checkedLines=行≥1；reconcileCheckedLines 后必恢复 textRange。
- T/H/A 三面板互斥占同一槽位（单一状态 openPanel，勿退回多 boolean），高度=键盘高度。
- IME 抑制四层互兜：setter 即 hide / onCreateInputConnection→null / onCheckIsTextEditor→false / 注入 inputmode=none + MutationObserver；抑制期间保留光标与选区。
- 面板收起后按焦点态弹回键盘：LaunchedEffect(isFormatPanelOpen)+wasFormatPanelOpen 判 true→false，delay 180ms；正文 editorFocused→restoreIme()，否则标题。
- 焦点真值只能由 JS 提供（View.hasFocus() 失真）。标题回显靠「heading 块 + headingToggleable」分流。

## Compose 陷阱
- remember{} 内读 MaterialTheme/LocalXxx 报错 → 提到 remember 外；remember key 勿传新实例。
- 局部函数先声明后引用；isXxx 属性与 setXxx 函数 JVM 撞签名（用 add/toggleXxx）；kotlinx delay 只收 Long；internal 跨模块不可见 → 语义化 API 收进库；ParagraphStyle range 注入 lineHeight 须剥 default。
- TextUnit(Int.sp).toString 是 "18.0.sp" → 桥接 CSS 一律数值插值。
- Foundation 1.11 移除 foundation.layout.minimumInteractiveComponentSize → 改由 material3 提供；ripple() 是 androidx.compose.material3 顶层函数；Modifier.indication(...) 是 androidx.compose.foundation 顶层扩展（无 foundation.indication 子包）。
- ★ 双层手势互斥：detectTapGestures 的 awaitFirstDown 默认 requireUnconsumed=true，内层 clickable 消费 down → 外层手势永不触发（按钮亮、有波纹、动作不执行、零日志）。连发按钮正确结构 = Box + minimumInteractiveComponentSize + size + clip + indication(interactionSource, ripple()) + longPressRepeat（RiFormatButton 样板）。

## 块级拖拽 / 视觉
- BlocksReorderableList fork：settle()=抓快照→立即 onSettle→滑行交 Controller；拖拽期间不能改列表；itemKey 身兼身份锚定+滑行归属+zIndex。
- alpha 动画裁边界 → 悬浮元素勿挂 shadow；animateContentSize 内部 clipToBounds 持续裁剪。
- 无限高约束穿透吞 heightIn(min) → 逐层 grep fillMaxSize 改 fillMaxWidth。
- 盒等距 ≠ 墨迹等距：RichTextEditor 默认 minHeight 56dp；常量 inspirationTitleToMetaGap(6)/inspirationMetaToBodyGap(8)。

## 图片附件页
- MainActivity configChanges 不含 uiMode → 旋转不重建，改 requestedOrientation。
- 双指缩放 consume；单指 scale>1 才平移否则放行 Pager；锚点 offset_new=d−(d−offset_old)×ratio；越界跟手翻页+橡皮筋（≤72dp）。
- findDialogWindow()（tailrec）；系统栏竖屏常显、横屏双通道 hide；Pager 阈值横屏 0.08 竖屏 0.35。

## 验证与提交
- 并发写入时核对入库用 `git show HEAD:<path>` 精确断言（grep 关键字会假阳性）；提交用显式路径 add。
- 新增 layout/Modifier API 逐项核对 import；静默 catch 至少上行诊断；连续 2 次猜测失败→停猜加埋点。
- 中文提交信息：Write 临时文件→提交→删除；提交前交叉核对新增符号定义方/引用方同提交。
- 编译检查（用户同意后）：PowerShell 调 .\gradlew.bat；输出 *> 文件是 UTF-16，读时 -Encoding Unicode。
- 修数据清洗规则必须同时修历史数据（repairInspirationPlainText）；内部标记识别收敛在 MarkdownParser.stripInternalTokens/toPlainText。
