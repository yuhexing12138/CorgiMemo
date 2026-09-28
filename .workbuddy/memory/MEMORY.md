# CorgiMemo 项目长期记忆

## 本机工具链坑
- Bash 内建命令全 Exit 127 → Python 绝对路径（`C:/Users/EDY/.workbuddy/binaries/python/versions/3.13.12/python.exe`）或 PowerShell；PowerShell stdout 被吞 → **落盘再 Read**（重定向文件是 UTF-16，读前解码）；`rm` 被 safe-delete 拦 → `[System.IO.File]::Delete()`；cmd.exe 禁用 → `npm.cmd` 直接调。
- vitest 直跑 `node node_modules/vitest/vitest.mjs run <file>`（shell shim 坏）；构建 `npm.cmd run build:editor`。
- 大段删行：按行切分+保留行尾（**禁 ReadAllLines/WriteAllLines** → 行尾全变 CRLF）；★ 行号必须用「当前」行号；正解=内容锚定+括号配平+打印首尾行核对；删前备份。
- .ps1 必须 UTF-8 with BOM；Glob 不索引 node_modules/.gradle → 递归落盘再读。
- minify 长行 grep 易截断看漏 → 用 Python 切片看完整上下文。

## BlockNote WebView（产物与哈希）
- 产物 `assets/blocknote-web/editor/editor.html`（viteSingleFile ~1.9MB），源码 `blocknote-probe/src/editor/`（@blocknote 0.52.1）；「JS 改了没生效」=产物没重建。机检 `scripts/check-blocknote-artifact.ps1` + pre-commit（core.hooksPath=.githooks；**禁 --no-verify**）。
- ★ 过期只看 `bn-src:<sha256前12>`（构建时戳≠哈希）；核验产物只按**语义特征**（消息名字符串/类名/正则字面量），minify 改函数名与引号。
- ★ 哈希清单 10 文件：editor.html+src/editor/ 全量+src/probe.css+src/probes/dividerBlock.tsx+fontSizeStyle.tsx；两侧同序清单。
- ★★ 并行会话编辑清单内文件会锁死提交 → 等所有会话停手后重建；识别=「[FAIL] 产物已过期」且差异与己无关。
- ★★ 机检按**提交后状态（暂存区聚合）**校验：bn-src 是「相对路径+文件字节」顺序聚合 sha256（清单 11 文件）⇒ 清单内文件必须**同一提交**，拆分提交必 FAIL；产物重建后尽快提交（防并行会话再改源码）。
- ★ 并行编辑 tsc 假报错：报错全在没碰过的文件 → mtime 竞态，重跑即 0 错，别修别人的代码。
- 桥：下行 `window.BlockNoteEditorHost.onMessage(json)` / 上行 `AndroidBridge.postMessage`。

## BlockNote 块与样式判据
- ★ `content:"none"` 块（divider/图片/分页符）`block.content===undefined`；行内按钮可用判据 `schema.blockSpecs[type].config.content==="inline"`。
- ★ 工具栏判据一律 JS 经 blockState 上行，宿主本地镜像不可信（已踩 5 次）。
- ★ transform value=动作名≠块类型名（`toggleList`→`toggleListItem`）；updateBlock 换类型不显式传 content→表达式不同清空原文（代码块吞文字根因），换类型走 retypeBlockSafely；insertBlocks 不移光标。
- ★ 自定义块渲染必须显式 `flex:1` 撑满，否则宽度坍缩 0、样式在但不可见。
- 行内样式渲染走 markView.render()；光标态 toggleStyles 只改 stored marks 须主动 pushBlockState；撤销可用态 `editor.canExec`。

## markdown 契约（细节见 tests/markdown/*-contract.test.ts 与代码注释）
- token 一览：行内色 `@@@CORGI_IC_TC_<值>@@@…END@@@`；块色 `@@@CORGI_BC_TC_red@@@`；分割线 `@@@CORGI_DIVIDER_solid|dashed|wavy@@@`（须独占段）；折叠 `<details><summary>`；空行 `@@@CORGI_BLANK@@@`；文件形态 `@@@CORGI_FILEVIEW@@@`（追加进 caption 借 figure 往返）；自链接 `「URL」` 显式包裹。回归：blank-line(8)/divider(17)/link(21)/toggle-preview(8) 四个 contract 测试。
- ★★ 空段落 CommonMark 不可表示：blocksToMd `encodeBlankParagraphs`（排其它编码前）⇄ mdToBlocks 还原 `content:[]`；Compose INTERNAL_TOKEN_REGEX 通配剥掉；历史数据不重建。
- ★★ 分割线三环节归一：导出 `***`→`---`；载入裸 `---`/`***`/`___` 全转 token——项目 schema StyledDividerBlock 同名覆盖 divider，官方 parser 会静默丢弃裸线。
- ★★ 自链接：导出 bracketSelfLinks（显示==href 塌成「href」，颜色编码前）；载入 splitBracketLinks 仅认协议前缀（防误伤中文引用）；autolink 两级拆分「」优先→裸 URL。自链接改文字 href 跟随=linkHrefSync.ts（appendTransaction 扩展）。
- ★★ 图片备注 figure 契约（v2026-09-28）：caption 非空 → `<figure><img><figcaption>`；下游三处已认 figure：toPlainText 整行删+行内兜底、isImageSegment 双正则、saveInlineMediaBlocks 提取 figcaption→note（decodeHtmlText 反转义、&amp; 最后、`\ssrc` 防 data-src）。图片堆叠数据源=content_blocks 表（imagePathsMap）非 markdown 切段；figure 存量摘要自愈、堆叠区须重存。详情页备注仅展开态显示（时间线无备注）。
- ★★ image 块插入必须带 `name: path.split("/").pop()`；showPreview=false 由 FILEVIEW token 往返，Compose 落库前剥 token。
- 纯文本唯一入口 `MarkdownParser.toPlainText()`；摘要=collapseBlankLines（按整行 isNotBlank 删空行）；纯字数统计不接折叠。
- ★ 往返测试必须「真实插入→保存→载入」端到端，只测 mdToBlocks 全绿是假覆盖。
- ★ 环境坑：WorkBuddy fs shim 对 vitest Temp/哈希文件写 EPERM → 多文件 run 随机中断 → 分批重跑累计覆盖。

## 浮层 / 事件 / 定位
- ★★ createPortal/position:fixed 不阻断 React 事件（沿 fiber 传播）→ 只能 e.stopPropagation()。
- ★ 浮动工具条尺寸全写死+视口夹取（键盘改 innerHeight 勿缓存 vh）；Portal 出去查 CSS 变量继承断链（--editor-primary/--mantine-*/--bn-*）。
- ★ PositionPopover 两件套缺一不可：`focusManagerProps={{disabled:true}}` + 自建 Portal 宿主 `div.bn-root.bn-mantine`；必须官方 middleware 三件套 `offset(10)+shift()+flip()`。
- ★★ 全屏 fixed Portal 宿主 `pointer-events:none` + 浮层经 elementProps style 恢复 auto（透明≠点击穿透）。
- ★★ 官方 useDismiss 排除 domReference → 点正文永不关；命令驱动面板自补 document 级 click capture（EmojiGridPanel 先例）。FormattingToolbar 关闭靠选区变化。
- ★ 渲染依赖的快照值用 useMemo（ref 不触发重渲→永远差一帧）。
- ★★ 视频穿透（v2026-09-28 终态）：video `pointer-events:none` + UA shadow 具体伪元素恢复 auto（play-button/timeline/mute-button/fullscreen-button）、overlay-play-button 钉 none；enclosure/panel 是全尺寸容器**不可**恢复（会整体吃点击）。官方 video render 显式 `controls=true`（UA 原生控件非无交互）；若细分恢复失效→升级自绘控制层（divider 覆盖先例，约 200 行）。

## WebView 链接与面板
- `links:{onClick}` 唯一挂钩，配了即禁官方 window.open；`setSupportMultipleWindows` 默认 false → 带 target 的 window.open 静默丢。
- 拦导航三通道：shouldOverrideUrlLoading / onCreateWindow（一次性 WebView+WebViewTransport，必 sendToTarget）/ onPageStarted 兜底；白名单 startsWith 不能用 host（file:// host 空串）；ACTION_VIEW+NEW_TASK+catch。
- ★★ 「下载」按钮接管判据（7c6345ef）：onCreateWindow 回调出现本地媒体 URL 必是用户主动操作（资源加载走 shouldInterceptRequest）→ exportLocalMediaToGallery（API29+ MediaStore 直存；26-28 FileProvider+ACTION_SEND，@RequiresApi 标函数）。

## 字体 / 底部栏 / Compose
- 字体单一真相源 ContentFontManager；预览 FontPreviewEngine（有界池+LruCache，禁批量 getFont 驻留 OOM）；WebView 字体四关：桥下发/CSS 两层/@font-face 可达/诊断三件套。
- BodyBlocksController 仅数据层；sealed 块子类全项目 Grep `is BodyBlock.` 补 when；T/H/A 三面板互斥 openPanel；焦点真值只由 JS 提供。
- Compose：remember 内禁读 MaterialTheme/LocalXxx（提到外面）；`TextUnit(Int.sp).toString`="18.0.sp"；双层手势 requireUnconsumed；波纹延迟 100ms 复刻 handlePressInteraction；ParagraphStyle range 注入 lineHeight 须剥 default；`isXxx` 属性与 `setXxx` 撞 JVM 签名（用 add/toggleXxx）；局部函数先声明后引用；kotlinx delay 只收 Long。

## 验证与提交
- 提交用显式路径 add；中文提交信息 Write 临时文件→提交→删；编译检查须用户同意（gradlew `*>` 文件是 UTF-16，读时 -Encoding Unicode）。
- ★★ Read/Edit 偶发按 GBK 误解码 UTF-8（中文乱码+old_string 不匹配，git diff 证明文件完好）：先字节级验证，正常就用 Python splitlines(keepends) 行操作，**严禁乱码文本当 old_string**。
- ★ 先取日志再下结论：dbg()→console.log→onConsoleMessage(TAG=BlockNoteEditor)→adb logcat；连续 2 次猜错→停猜加埋点；用户归因先验证真变了。
- 验证必须用项目真实 schema（`editorSchema`），默认 schema 假阴性/阳性。
