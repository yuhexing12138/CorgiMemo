# CorgiMemo 项目长期记忆

## 本机工具链坑
- Bash 内建命令全 Exit 127 → Python 绝对路径（`C:/Users/EDY/.workbuddy/binaries/python/versions/3.13.12/python.exe`）或 PowerShell；PowerShell stdout 被吞 → **落盘再 Read**（重定向文件是 UTF-16，读前解码）；`rm` 被 safe-delete 拦 → `[System.IO.File]::Delete()`；`cmd.exe` 禁用 → `npm.cmd` 直接调。
- vitest 不能走 `./node_modules/.bin/vitest`（shell shim 依赖缺失的 sed/dirname → `Cannot find module 'C:\vitest\vitest.mjs'`）→ 直跑 `node node_modules/vitest/vitest.mjs run <file>`；npm-cli.js 不在 probe 的 node_modules → `npm.cmd run build:editor`。
- 大段删行：按行切分+保留行尾（**禁 ReadAllLines/WriteAllLines** → 行尾全变 CRLF）；★ 行号必须用「当前」行号；正解=内容锚定+括号配平（`{`/`}` 计数）+打印首尾行核对；删前 `Copy-Item` 备份。
- .ps1 必须 UTF-8 with BOM；Glob 不索引 node_modules/.gradle → PowerShell 递归落盘再读。

## BlockNote WebView（产物与哈希）
- 产物 `assets/blocknote-web/editor/editor.html`（viteSingleFile ~1.9MB），源码 `blocknote-probe/src/editor/`（@blocknote 0.52.1）；「JS 改了没生效」=产物没重建（`npm.cmd run build:editor`）。机检 `scripts/check-blocknote-artifact.ps1` + pre-commit（core.hooksPath=.githooks；**禁 --no-verify**）。
- ★ 判断过期只看 `bn-src:<sha256前12>`（构建时戳≠哈希，同源码重建时戳必不同）；核验产物只按**语义特征**（消息名字符串/类名/正则字面量），minify 会改函数名与引号。
- ★ 哈希清单 10 文件：editor.html+src/editor/ 全量+src/probe.css+src/probes/dividerBlock.tsx+fontSizeStyle.tsx；两侧同序清单（vite config ENTRIES + ps1 foreach）。
- ★★ 并行会话编辑清单内文件会锁死提交（钩子按工作区现算哈希）→ 等所有会话停手后再重建一次；识别=「[FAIL] 产物已过期」且差异与自己的改动无关。
- ★ 并行编辑的 tsc 假报错：报错全在本轮没碰过的文件 → 先查 mtime 保存竞态，重跑即 0 错，别急着修别人的代码。
- 桥：下行 `window.BlockNoteEditorHost.onMessage(json)` / 上行 `AndroidBridge.postMessage`。

## BlockNote 块与样式判据
- ★ `content:"none"` 块（divider/图片/分页符）`block.content===undefined`；行内按钮可用判据 `schema.blockSpecs[type].config.content==="inline"`。
- ★ 工具栏判据一律 JS 经 blockState 上行，宿主本地镜像不可信（已踩 5 次）。
- ★ transform value=动作名≠块类型名（`toggleList`→`toggleListItem`），传错 blockToNode 抛 TypeError 无日志；updateBlock 换类型不显式传 content 按表达式比较→不同则清空原文（代码块吞文字根因）；insertBlocks 不移光标。
- ★ 自定义块渲染必须显式 `flex:1` 撑满（官方 .bn-block-content 是 flex 容器），否则宽度坍缩 0、样式全在但肉眼不可见。
- 行内样式渲染走 markView.render()；光标态 toggleStyles 只改 stored marks 须主动 pushBlockState；撤销可用态 `editor.canExec`。

## markdown 契约（★ 分割线三环节）
- 颜色 token：行内 `@@@CORGI_IC_TC_<值>@@@…END@@@`；块级行首纯文本 `@@@CORGI_BC_TC_red@@@`；折叠标题 `<details><summary>`；**空行 `@@@CORGI_BLANK@@@`**（v2026-09-23，export 常量 `BLANK_LINE_TOKEN`）。
- ★★ **空段落（编辑页"空行"）在 CommonMark 里不可表示（vitest 实测）**：官方导出空段=连续空行（`甲\n\n\n\n乙`），任何解析器（编辑页 tryParseMarkdownToBlocks / 详情页 compose-rich-editor）都折叠成单个段落分隔 ⇒ 载入必丢；再经一次「解析→导出」循环（编辑页改任意内容保存）连 markdown 痕迹都没了。真机症状=「敲空行→保存→详情页无空行→重进编辑页消失」。**修法**：`blocksToMd` ①b `encodeBlankParagraphs`（判据 paragraph+content 空数组+无 children，递归，排其它编码之前）⇄ `mdToBlocks` ③ 还原 `content:[]`；Compose 侧 INTERNAL_TOKEN_REGEX 通配剥掉（详情页空段渲染空行占位 v2026-09-23；分割线 token 载体段仍跳过；保存竞态修复=requestSaveAndAwait 即时快照绕防抖）。**历史数据不重建**（无法与折叠标题标记人为插的空行区分）。回归 `tests/markdown/blank-line-contract.test.ts`（8 例）。
- 纯文本唯一入口 `MarkdownParser.toPlainText()`；摘要渲染=`collapseBlankLines(markdownToPlainText(content))`（★按整行 isNotBlank 删空行，不能 `\n{2,}` 折叠——「空格占位行」躲得过折叠）；纯字数统计路径不得接折叠。
- ★★ 分割线两侧必须同时归一：①导出侧官方 `blocksToMarkdownLossy` 产 `***`，宿主只认 `---` 系列 → blocksToMd 后处理归一；②**载入侧项目 schema 用 StyledDividerBlock 同名覆盖 divider ⇒ 官方 parser 按内置 thematicBreak 语义找不到落地块、静默丢弃裸 `---`** → mdToBlocks 预处理把裸 `---`/`***`/`___` 全转 `@@@CORGI_DIVIDER_solid@@@` token（与 dashed/wavy 同路径）；③`---` 必须独占段。回归 `tests/markdown/divider-contract.test.ts`（17 例，含根因固化：项目 schema 丢线、官方 schema 正常）。
- ★ 测试起点决定覆盖面：往返测试必须覆盖「真实插入→保存→载入」端到端，只测 mdToBlocks(样本) 会全绿却测不到真机路径。
- toPlainText 分割线剥离须含带样式载体 `--- dashed|wavy`；带样式正则排裸线之前、删整行+吃行尾换行。

## 浮层 / 事件 / 定位
- ★★ `createPortal`/`position:fixed` 都不阻断 React 合成事件（沿 fiber 树传播，Portal 只改 DOM 归属）→ 要阻断只能 `e.stopPropagation()`。
- ★ 浮动工具条尺寸全写死（width/height+boxSizing:border-box+lineHeight:1+SVG 图标）；定位可由实测改常量推算（注释写清算式防漂移）；必须视口夹取（useLayoutEffect；键盘会改 innerHeight 勿缓存 vh）。
- ★ Portal 出去的 UI 必须查 CSS 继承断链（--editor-primary/--mantine-*/--bn-* 静默退回 fallback 不报错）。
- ★ 复用官方 PositionPopover 两件套缺一不可：`focusManagerProps={{disabled:true}}`（FloatingFocusManager 在 WebView 当帧自关：autoFocus 搬焦点+relatedTarget=null）+ 自建 Portal 宿主 `div.bn-root.bn-mantine`（editor.portalElement 被祖先裁；document.body 丢变量→图标乱码）。
- ★★ 全屏 fixed Portal 宿主必须 `pointer-events:none` + 浮层经 `elementProps={{style:{pointerEvents:"auto"}}}` 分层恢复——透明≠点击穿透（CSS 命中测试不看透明度）；`width:0` 时无命中面积故不暴露，改 `inset:0` 后全屏吃触摸→编辑器无法聚焦、键盘呼不出。elementProps.style 在 GenericPopover mergedProps 合并顺序里存活（先展开、不被 zIndex/floatingStyles 覆盖）。
- ★★ PositionPopover 必须带官方 middleware 三件套 `middleware:[offset(10),shift(),flip()]`（FormattingToolbarController:101 同款）——缺 flip 上放不下不翻转（上裁）、缺 shift 右越界不回拉（右裁）。「同库同组件官方能用我不能用」→ 逐项 diff 官方用法。
- ★ FloatingPortal 即使传 root 也先包一层无样式 div（subRoot）再挂浮层 → 测量必须穿透 static 层找第一个非 static 元素；`h=0` 但子内容可见 = 子元素 absolute 脱流信号，测到的是包装层。
- ★★ 官方 useDismiss 对「点编辑器内」失效：outside 判定排除 `elements.domReference`（floating-ui.react.mjs:2727），而 GenericPopover 把整个编辑器内容根设为 domReference → 点正文永不关面板。FormattingToolbar 不踩坑是靠选区变化关闭、不靠 dismiss。宿主命令驱动的面板须自补 document 级 click capture 兜底（target 不在 `.bn-popover-content` 内 → onClose；EmojiGridPanel 先例）。
- ★ 渲染依赖的快照值用 `useMemo` 而非 useRef+useEffect（ref 不触发重渲→永远差一帧，「第一次点不出现第二次才出现」根因）。

## 通用方法论
- ★ 先取日志再下结论：dbg()→console.log→onConsoleMessage(TAG=BlockNoteEditor)→adb logcat；连续 2 次猜测失败→停猜加埋点；用户描述的归因先用原图验证是否真变了。
- 验证必须用项目真实 schema（`editorSchema`），默认 schema 会给假阴性/假阳性结论。

## WebView 链接与面板（v2026-09-24）
- `useCreateBlockNote({links:{onClick}})` 是唯一挂钩点，配了即禁官方 window.open 默认路径；`setSupportMultipleWindows` 默认 false ⇒ 带 target 的 window.open 被 Chromium 静默丢弃。
- 拦导航三通道：shouldOverrideUrlLoading / onCreateWindow（两开关成对；拿不到 URL 须造一次性 WebView 塞 WebViewTransport 发回、必 sendToTarget）/ onPageStarted 兜底；白名单判据用 startsWith 不能用 host（file:// host 空串）；ACTION_VIEW 带 NEW_TASK+catch ActivityNotFoundException；跨文件复用工具函数须 internal（Kotlin private 是文件级）。
- 链接面板：官方 CreateLinkButton（受控）与 EditLinkButton（非受控）渲染同一 EditLinkMenuItems；锚点/预填 JS 现取（宿主 saveSelection 回传是异步的）；关闭路径双向通报（上行 linkPanelClosed）。

## 字体 / 主题 / 底部栏
- 字体单一真相源 ContentFontManager；预览 FontPreviewEngine（有界池+LruCache，禁批量 getFont 驻留 OOM）；WebView 字体四关：桥下发/CSS 两层/@font-face 可达/诊断三件套。
- BodyBlocksController 仅数据层；新增 sealed 块子类全项目 Grep `is BodyBlock.` 补 when；T/H/A 三面板互斥单一状态 openPanel；焦点真值只能由 JS 提供。

## Compose 陷阱
- remember{} 内读 MaterialTheme/LocalXxx 报错→提到 remember 外；`TextUnit(Int.sp).toString`="18.0.sp"→桥接 CSS 数值插值；双层手势互斥（detectTapGestures 默认 requireUnconsumed）→ RiFormatButton 样板；滑动起手波纹延迟 100ms（TapIndicationDelay）需复刻官方 handlePressInteraction 语义；ParagraphStyle range 注入 lineHeight 须剥 default；`isXxx` 属性与 `setXxx` 函数 JVM 撞签名（用 add/toggleXxx）；局部函数先声明后引用；kotlinx delay 只收 Long。

## 验证与提交
- 提交用显式路径 add；中文提交信息 Write 临时文件→提交→删除；并发写入核对用 `git show HEAD:<path>` 精确断言；编译检查须用户同意（gradlew 输出 `*>` 文件是 UTF-16，读时 -Encoding Unicode）。
- `log/` 已加 .gitignore（`/log/` 前导斜杠锚定根目录）。
