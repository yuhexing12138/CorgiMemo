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
- 产物 `assets/blocknote-web/editor/editor.html`（viteSingleFile ~1.88MB），源码 blocknote-probe/src/editor/，Gradle `buildBlockNoteEditor` 已接入 assemble。「JS 改了没生效」第一反应 = 产物没重建；用关键字计数而非 diff 行数；构建指纹随 ready 上行（logcat `build=`）。⚠️ **提交 JS 源码 ≠ 产物入库**：产物不在同一批编辑里，改完 `src/` 必须单独重建 + 单独提交，否则提交里只有源码、真机仍跑旧 bundle；核对产物要取**上下文片段**（`inlineBackgroundColor` 后一串）而非只看关键字存在。重建命令走 **PowerShell 调 npm.cmd**（bash shim 缺 coreutils）。
- 桥：下行 `window.BlockNoteEditorHost.onMessage(json)`，上行 `AndroidBridge.postMessage`。⚠️ 桥回调在 Java 桥线程，入口已统一 mainHandler.post，别拆散包装。
- 撤销可用态用 `editor.canExec(command)`（`editor.can` 不存在；命令取 yUndo/history 扩展）。
- ⚠️ **工具栏选中态的真值必须走 JS 上行**：BlockNote 模式下宿主 `RichTextState.currentSpanStyle` 恒空（本地镜像而已），照它判断 → 按钮永不点亮。**B/I/U/S 是行内样式**（`getActiveStyles()`），**对齐是块级 prop**（`props.textAlignment`，spec 默认 `"left"` → 缺省即左对齐亮）——维度不同，不能一起取。另：`toggleStyles` 在**光标态（无选区）**只改 stored marks、**不产生文档变更** → `onChange` 不触发 → 必须在这类分支后**主动 `pushBlockState()`**，否则「点了加粗但不高亮」。
- ⚠️ **transform 的 value 是动作名，不等于块类型名**：`toggleHeading*` → `heading` + `props.isToggleable`；`toggleList` → `toggleListItem`。合法类型只有 defaultBlockSpecs 那一份。传错会在 `blockToNode` 抛 TypeError，而 format 分支无 try/catch → "点了没反应且无日志"。
- ⚠️ **行内样式渲染走 markView 的 `render()`，不是 renderHTML**；官方 textColor/backgroundColor 的 render() 只造裸 span，颜色靠 CSS 预设色名规则 → 自由 hex 不渲染。修法：schema.ts 同名覆盖 spec，render 内联 `span.style.color`。
- ⚠️ 官方 markdown 导出剥颜色 span / 丢折叠标记 → converter.ts 用 token 往返：行内色 `@@@CORGI_IC_TC_<值>@@@`…`@@@CORGI_IC_END@@@`；块级色用**块内容行首**纯文本 token `@@@CORGI_BC_TC_red@@@`（外包 div 会被 ProseMirror 下钻丢弃）；折叠标题用 `<details><summary>` 三段。纯文本转换必须剥离这些标记（Kotlin markdownToPlainText 已加）。
- ⚠️ `element.style.color` 读出是 `rgb(r,g,b)` 而非 hex → 必须归一成大写 `#RRGGBB`，否则二次保存被"值形态不安全"跳过。
- ⚠️ **正文 markdown（`contentFormat`）有三个消费方，新增内部标记必须三处全覆盖**（同日连踩两次：先漏列表摘要、再漏详情卡）：① **WebView 编辑器**（converter.ts 编解码，token 载入即消费）；② **Compose 详情卡正文**（`InspirationViewCard` 的只读 `RichText` **直接解析原始 markdown** → `extractBlockColorMarkers()` **解析并消费**块级色 token（映射成段的 textColor/backgroundColor）+ `stripStructuralMarkers()` 剥 `<details>` 及残余 token；**保留 `style` span**；勾选回写须用 `leadingInternalTokens()` 补回段首 token）；③ **纯文本抽取**（`toPlainText`）。**折叠标题在详情页 = 显示为普通标题**（用户确认；曾实现折叠已回退，勿再引入）。
- ⚠️ **块级色（段落色）只能靠段首 token 过桥**：官方 markdown 导出只把块级色写成块元素的 `data-text-color` 属性，而 markdown 块序列化器**不读元素属性** → 属性在导出瞬间即丢。故**任何"读 markdown 展示"的新出口都必须解析 `@@@CORGI_BC_TC_*/BG_*@@@`**——只剥不解析等于丢色（详情页曾因此完全不显示段落色）。色名→颜色用 `BlockColorPalette`（明暗两套）且**必须传当前主题 isDark**；未知色名要返回 null 不覆盖，不能回落 `Color.Transparent`（否则文字变全透明="消失"）。
- ⚠️ **「markdown → 纯文本」唯一入口 = `MarkdownParser.toPlainText()`**：`stripMarkdown` 只处理 markdown 语法，不剥 HTML 标签与 `@@@CORGI_…@@@`。灵感保存路径（InspirationEditViewModel）与启动回填曾各自误用 stripMarkdown → 摘要出现字面量。`InspirationTextUtils.markdownToPlainText` 现为薄委托。⚠️ 待办有独立口径（`#标签` 要保留），勿混用。
- 样式：padding-inline `var(--bn-editor-gutter,20px)!important`；sideMenu 已删（HTML5 DnD 触摸不触发）。

- 产物**新鲜度可机检**（v2026-09-22）：`vite.editor.config.ts` 的 `collectSrcHash()` 把 `editor.html + src/editor/` 全量算 sha256 前 12 位、以 `bn-src:` 前缀注入 `__SRC_HASH__` → `bridge.ts` 导出 `SRC_HASH` 随 `ready` 上行（logcat `src=`）。校验脚本 `scripts/check-blocknote-artifact.ps1` 同口径现算比对（`-WarnOnly` / `-StagedOnly` / `-PrintOnly`）；hook 模板 `scripts/git-hooks/pre-commit` 已装到 `.git/hooks/pre-commit`，凡提交涉及 `blocknote-probe/src/` 即校验、滞后则**阻断提交**（跳过用 `--no-verify`）。⚠️ 哈希口径三处必须逐条对齐（字节、范围、`/` 分隔+码元序、路径+内容的拼接顺序）。
- ⚠️ **模式判据要显式命名**：`boldSingleTier`（B 按钮 UI 形态）与 `useBlockNote`（选中态真值读 JS 还是读本地）已拆分，别再合并——名字必须能说明"改它影响哪些按钮"。
- ⚠️ **面板收起时要主动刷一次 `blockState`**（下行 `requestBlockState` → `refreshBlockState()`）：与"恢复软键盘"同一时机、**先刷状态再弹键盘**，避免高亮滞后一拍。
- ⚠️ **工具栏任何「可用态 / 选中态」判据都必须由 JS 经 `blockState` 上行，宿主本地镜像一律不可信**（同一坑已踩 5 次）：正文在 WebView 里，宿主的 `RichTextState.currentSpanStyle` 恒空、`BodyBlocksController.indentLevel` 恒为 1、`isFocusedBlockCheckbox` 恒 false —— 沿用 Compose 时代判据的表现是「按钮永远不高亮」或「永远置灰」。已上行：headingLevel / headingToggleable / inlineTextColor / inlineBackgroundColor / fontSizePx / bold·italic·underline·strike / textAlignment / canNestBlock·canUnnestBlock / isCheckboxBlock / canUndo·canRedo / editorFocus。新增同类状态一律照抄官方同名 API 的口径（如 `ed.canNestBlock()`），不要自创判定；**迁移完记得删掉宿主侧那个已零调用的镜像属性**（否则后人接着误用）。
- ⚠️ 新增上行字段后**必须重建产物**并在 `editor.html` 里按**上下文**（不能只看关键字计数）确认 payload 里带上了该字段；宿主侧默认 false 时「两个按钮一起灰」= 产物没重建。

- ⚠️ **「当前块」口径一律取选区首块**：`editor.getSelection()?.blocks[0]`，回退 `getTextCursorPosition().block`（选区折叠/NodeSelection 时 `getSelection()` 返回 undefined）。绝不能只用 `getTextCursorPosition()` —— 它按 selection.**anchor** 取块，反向拖选时 anchor 在选区末端，回显块 ≠ 命令实际作用的块；它也**不抛异常**（别再按"抛异常"描述）。`getSelection()` 少数边界会抛，单独包 try 回落。

## 工具/验证教训
- ⚠️ **同一工作区可能被并发写入**（另一会话/IDE 操作）：表现为 commit 只进了部分文件、上一轮已提交的文件又变「已修改」、`git log` 出现不认识的提交。核对某改动是否已入库必须 `git show HEAD:<path>` 做**精确断言**（如 `val canIncreaseIndent` → false）；**grep 关键字会假阳性**（自己写的注释里常原文引用被删的代码）。发现并发写入时不要替另一路提交它的在制品。
- 新增 layout/Modifier API 必须逐项核对 import（漏 import 只编译期暴露）。「API 应该有但没反应」先 grep 确认存在。静默 catch 至少上行诊断；连续 2 次猜测失败 → 停猜加埋点。同一文件多次 Edit 串行。
- 删文件/裁 import 前按 `by ` 反查委托依赖；删后全项目 grep 顶层声明名。
- 产物关键字计数只证字符串在文件里；样式生效必须真机/computed style；逐像素分析截图（pillow）是定位渲染问题的可靠手段。
- 提交：中文提交信息，Write 临时文件 → 提交 → 删除。
- ⚠️ **`.ps1` 必须存成 UTF-8 with BOM**：Windows PowerShell 5.1 对**无 BOM** 文件按系统 ANSI 读，中文注释里的 emoji 会让字节错位、吞掉引号 → 满屏 `ParserError`（"[" 后面缺少类型名称 / 字符串缺少终止符）——**看着像语法错，其实是编码错**。写法：`[System.IO.File]::WriteAllText($p,$s,(New-Object System.Text.UTF8Encoding($true)))`。
- ⚠️ **`[Array]::Sort($arr, [StringComparer]::Ordinal)` 的比较器会静默失效**：`Get-ChildItem | Select -ExpandProperty Name` 是 `PSObject[]`，重载解析挑中不带比较器的 `Sort(Array)`，实际按**文化敏感**排序（与 Node 的码元序不同）。要码元序排序用 `List[string].Sort(IComparer<string>)`。凡"跨语言同一套哈希/摘要"的实现，先造一个**最小对照实验**（两边各算一次同参输入）确认口径一致，别拿"结果不同"直接下结论。
- ⚠️ 脚本里的 `exit` 用 `& script.ps1` 调用时会**终止整个调用会话**（后续语句不执行、重定向文件可能不落地）→ 测试脚本要开子进程 `powershell -NoProfile -File ...`，并用 `$LASTEXITCODE` 取退出码。
- 编译检查（经用户同意后）：**不用 bash 跑 `./gradlew`**（本机 shim 缺 coreutils，`dirname` 都报 not found）→ 用 **PowerShell 调 `.\gradlew.bat`**；输出重定向 `*> 文件` 后是 **UTF-16**，读时须 `-Encoding Unicode`。单模块校验用 `:app:compileDebugKotlin`（不出包、不安装，约 1.5 分钟）。
- ⚠️ **修数据清洗规则必须同时修历史数据**：卡片摘要优先读 `Inspiration.content`（非空即不走兜底），所以"代码修好"对已污染行无效。做法：启动时幂等清洗（`InspirationRepository.repairInspirationPlainText`：content 空 或 含 `@@@CORGI_`/`<span` → 以 toPlainText 重算）。
- ⚠️ 同一份数据的**多个渲染出口**要用「共享的识别函数」，不要各写正则：内部标记的识别现收敛在 `MarkdownParser.stripInternalTokens` / `toPlainText`，新增标记只需改一处（本轮两次泄漏都源于"另一处消费方自己拼正则")。
