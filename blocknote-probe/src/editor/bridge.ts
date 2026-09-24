/**
 * Bridge 协议 v1（详见 docs/bridge-protocol.md）
 *
 * 下行（Kotlin → JS）：Kotlin 侧 evaluateJavascript 调
 *   window.BlockNoteEditorHost.onMessage(JSON)
 * 上行（JS → Kotlin）：JS 侧 window.AndroidBridge.postMessage(JSON)
 *   （Kotlin 侧 addJavascriptInterface(BridgeHost, "AndroidBridge")）
 *
 * 纪律：单向数据流——Kotlin 永不向 JS 回灌内容变更；undo/redo 的**历史栈**留在 JS 侧，
 * 仅把「是否可撤销/可重做」的布尔态经 undoState 上行供宿主按钮置灰（v1.7）。
 */

/** 主题载荷（P0：深浅态 + 主色；P1 扩展六色主题；v1.9 增背景色） */
export type ThemePayload = {
  /** 深色模式 */
  dark: boolean;
  /** 主题主色（hex，如 "#1976d2"） */
  primary: string;
  /**
   * 宿主编辑区背景色（hex，如 "#FFFBF5"；v1.9）
   *
   * BlockNote 的 `.bn-editor` 默认铺 `--bn-colors-editor-background`（亮色 #fff、
   * 暗色 #1f1f1f），与宿主主题背景不一致时会形成"画中画"的白底圆角框。
   * 宿主把编辑区实际背景色下行到这里，JS 侧同时覆盖：
   * ① `--bn-colors-editor-background`（编辑器本体）② `body`（WebView 底色）。
   *
   * 缺省（旧版宿主未下发）时回落 white / #1f1f1f，保持向后兼容。
   */
  background?: string;
};

/** 下行消息（Kotlin → JS） */
export type DownMessage =
  | {
      type: "init";
      /** GFM markdown 正文 */
      markdown: string;
      readOnly: boolean;
      theme: ThemePayload;
      /** 字体 id（FontCatalog.id；"system_default" = 系统默认） */
      fontFamily: string;
      /**
       * 英文/数字字体 id（v2026-09-21：拉丁回退层；可选，旧宿主缺省 = 跟随中文）。
       * JS 侧组装字体栈时**拉丁在前**：拉丁字形走 ff-<latinId>、中文回落 ff-<fontFamily>，
       * 与 Compose 侧 combinedFamily 的「拉丁主体 + 中文兜底」语义一致。
       */
      latinFontId?: string;
      /** 可用字体清单（S5）：JS 据此生成 @font-face，字体文件走 shouldInterceptRequest 流 */
      fonts?: Array<{ id: string; weights: number[] }>;
    }
  | { type: "setReadOnly"; readOnly: boolean }
  | { type: "setTheme"; theme: ThemePayload }
  | { type: "setFontFamily"; fontFamily: string }
  /** 英文/数字字体切换（v2026-09-21：拉丁回退层；空串 = 跟随中文） */
  | { type: "setLatinFontFamily"; latinFontId: string }
  /**
   * 正文基础字号切换（v2026-09-21，单位 px，WebView 内 1px=1dp）：
   * H 面板「正文字号」在**无文字选区**时点选 → JS 侧改全局基础字号
   * （`.bn-default-styles` 消费的 CSS 变量），作用于所有未叠加行内
   * fontSize 样式的文字；有选区时仍走 `format("fontSize")` 行内样式。
   */
  | { type: "setBaseFontSize"; fontSizePx: number }
  /** 主动要一次快照（返回键/切后台前） */
  | { type: "requestSave" }
  /** 撤销/重做（v1.2：原页面撤销/重做按钮经桥触发） */
  | { type: "requestUndo" }
  | { type: "requestRedo" }
  /** 图片插入（v1.3：原页面相册/相机选图后经桥插入光标处；path 为本地绝对路径） */
  | { type: "insertImage"; path: string }
  /** 分割线插入（v1.3：原页面分割线按钮经桥在光标处插入） */
  | { type: "insertDivider" }
  /** 视频/音频/文件插入（v1.5：本地路径，JS 转 file:// URL；媒体播放经 shouldInterceptRequest 流） */
  | { type: "insertVideo"; path: string }
  | { type: "insertAudio"; path: string }
  | { type: "insertFile"; path: string }
  /** 打开表情选择面板（v1.5：JS 自绘网格，点击插入） */
  | { type: "openEmojiPicker" }
  /**
   * 格式命令（v1.4：底部格式工具栏按钮经桥作用于当前选区/光标块）。
   * action 取值：
   * - bold / italic / underline / strike / codeSpan（toggleStyles 行内格式）
   * - fontSize / textColor（value = "18px" / "#ff0000" 或 "default"=清除）
   * - bulletList / numberedList / checkList / paragraph（光标块类型切换）
   * - indent / outdent（嵌套层级 ±1）
   * - alignLeft / alignCenter / alignRight（光标块对齐）
   * - transform（value = heading1–heading6、toggleHeading / toggleHeading2 / toggleHeading3、
   *   toggleList、quote、paragraph、codeBlock、table——块类型转换/插入；
   *   pageBreak v2026-09-24 移除：工具栏入口已删，与「文件」按钮图标重复；
   *   宿主若误发此值将走 default 兜底上行 error）
   *
   * ⚠️ transform 的 value 是**动作名**，不等于块类型名（v2026-09-22 踩坑）：
   * - `toggleHeading*` → 实为 `heading` + `props.isToggleable = true`（无 toggleHeading 类型）
   * - `toggleList` → 实为 `toggleListItem`
   * 把动作名直接当 `type` 传给 `updateBlock` 会因 schema 查不到类型而抛 TypeError。
   */
  | {
      type: "format";
      action: string;
      value?: string;
      /**
       * 可选的**显示文字**（v2026-09-22 新增，目前仅 `action = "createLink"` 使用）。
       *
       * 空标题时宿主**不下发**本字段（靠"字段缺失"而非空串判定未填写），
       * 故 JS 侧取到 `undefined` 即"用户没填标题"。
       */
      text?: string;
      /**
       * 可选的**目标区间**（v2026-09-22 新增，目前仅 `action = "createLink"` 使用）。
       *
       * 来源 = 宿主 `saveSelection` 后 JS 经 `selectionRange` 上行、宿主暂存下来的
       * 选区位置。有了它，写链接就**不依赖"编辑器当前选区是否还在"**：
       * 即使 WebView 失焦折叠了选区、甚至 `restoreSelection` 失败，也能按快照位置
       * 精确落点（`from === to` 表示光标态 → 插入新文本；`from < to` 表示区间 → 挂 mark）。
       *
       * 缺省（旧宿主 / saveSelection 未上行）时 JS 回落「读当前选区」的旧路径。
       */
      from?: number;
      to?: number;
    }
  /**
   * 删除块（v1.11：原 ⋮⋮ 手柄点击菜单的「删除」项，桥接到宿主工具栏）。
   *
   * 语义与官方 `RemoveBlockItem` 一致：若当前选区包含了光标块，则删除**选区内的全部块**；
   * 否则只删光标所在的那一个块。因此宿主无需关心"选中了几个块"。
   */
  | { type: "deleteBlock" }
  /**
   * 设置当前块的**块级**颜色（v1.11：原 ⋮⋮ 手柄菜单的「颜色」项）。
   *
   * ⚠️ 与 `format` 的 `textColor` 是**不同维度**：
   * - `format.textColor` 走 `addStyles`，作用于**选区内的行内文字**（inline span style）；
   * - 本条走 `updateBlock(block, { props })`，作用于**整个块**（block props）。
   * 两者互不覆盖，但宿主 UI 上要区分入口，避免用户误以为是同一件事。
   *
   * 取值为 BlockNote 预设色名（如 "red" / "blue" / "default" 表示清除）。
   */
  | { type: "setBlockColor"; textColor?: string; backgroundColor?: string }
  /**
   * 切换表格的表头行 / 表头列（v1.11：原 ⋮⋮ 手柄菜单的「表头行 / 表头列」项）。
   *
   * 仅在 `block.type === "table"` 且 `editor.settings.tables.headers` 为真时生效
   * （与官方 `TableHeadersItem` 的判定一致；条件不满足时 JS 侧静默忽略）。
   * 官方当前只支持 1 行 / 1 列表头，故用布尔开关而非数量。
   */
  | { type: "setTableHeader"; target: "row" | "column"; enabled: boolean }
  /**
   * 块上移 / 下移（v1.11.5）
   *
   * **替代原 ⋮⋮ 手柄的拖拽重排**：该手柄的拖拽纯用 HTML5 原生 Drag & Drop，
   * 而该 API 在 Android WebView / iOS Safari 的触摸下不触发（W3C 将 drag 事件
   * 定义为鼠标驱动），因此手柄已整体删除，块移动改走程序化 API
   * `editor.moveBlocksUp()` / `moveBlocksDown()`。
   *
   * 无需传参：JS 侧不传 `blockIdentifier`，BlockNote 会取**选区首/末块**或**光标块**，
   * 因而天然支持「多选块一起移动」与嵌套块。到达首/末块时内部安全 no-op。
   */
  /** 块上移 / 下移（v1.11.5） */
  | { type: "moveBlockUp" }
  | { type: "moveBlockDown" }
  /**
   * 设置编辑区最小高度（v1.11.6，单位为 **dp**，宿主下发）
   *
   * 背景：`.bn-editor` 是 `contenteditable`，但 BlockNote 未给它任何 `min-height`
   * ——它的高度**完全由内容决定**。而宿主给 WebView 设了 `heightIn(min = 屏高 × 62%)`，
   * 于是内容少时（如新灵感只有 1 个空块 ≈ 30dp）编辑器只有 30dp 高，
   * 下方几百 dp 虽在 WebView 内、却**不在 `contenteditable` 盒子里**：
   * 点击那片空白不会聚焦光标、也不会把光标移到最后一行（"死区"）。
   *
   * 修法：宿主把自己的最小高度值下发，JS 侧写入 CSS 变量
   * `--bn-editor-min-height`，由 editor.css 的 `.bn-editor { min-height: ... }` 消费，
   * 让编辑区始终铺满 WebView。
   *
   * 为何用宿主下发而不是 `62vh`：`vh` 是相对**视口**的单位，而本项目 WebView
   * 高度会随内容增长（外层 Column 滚动、WebView 可能撑出屏幕），此时 vh 语义不直观；
   * 下发一个确定值行为才可预测。1 CSS px = 1 dp（`initial-scale=1.0`），故单位即 dp。
   */
  | { type: "setEditorMinHeight"; height: number }
  /**
   * 让编辑器重新获得 DOM 焦点（v2026-09-22）
   *
   * 背景：宿主「T / H / A」面板展开期间会抑制软键盘（`inputmode="none"` +
   * 原生拦截输入连接），此时用户在正文里点光标**仍然会聚焦** contenteditable，
   * 只是不弹键盘。面板收起后宿主要把键盘弹回来，而 Chromium 只有在**编辑元素
   * 持有焦点**时才肯建立输入连接——故宿主先下发本命令把焦点交还 `.bn-editor`，
   * 再调 `InputMethodManager.showSoftInput`。两步顺序不能反。
   *
   * 幂等：编辑器已聚焦时 `focus()` 为空操作，不会打断现有选区。
   */
  | { type: "focusEditor" }
  /**
   * 主动要一次块状态（v2026-09-22 新增）
   *
   * 宿主在「T / H / A」面板**收起**时下发，让工具栏选中态在收起瞬间就是最新的
   * （面板期间的变化若因去重 / 时序未上行，收起后高亮会滞后一拍）。
   *
   * 无副作用：**不产生任何文档变更**，只重新采集并上行一次 `blockState`
   * （仍在 `pushBlockState` 的去重逻辑内，状态未变则实际不上行）。
   */
  | { type: "requestBlockState" }
  /**
   * 保存当前选区（v2026-09-22 新增）
   *
   * 宿主在**打开链接对话框之前**下发，把此刻 ProseMirror 的选区快照留在 JS 侧。
   *
   * 存在理由：链接对话框是 Compose 的 `AlertDialog`，弹出时 WebView 会失焦；
   * 若 Android WebView 在失焦时折叠了内部选区，后续 `createLink` 就会按
   * 「无选区」处理 —— 用户明明选了字，结果却在光标处插了 URL。有了快照，
   * `restoreSelection` 就能在写链接前把选区还原回去。
   *
   * 无副作用：只读快照，不改变任何文档或选区状态。
   */
  | { type: "saveSelection" }
  /**
   * 还原上一次 `saveSelection` 的选区（v2026-09-22 新增）
   *
   * 宿主在链接对话框**确认/移除之后、写链接之前**下发（桥命令按序执行，
   * 故 restore 一定先于 createLink / deleteLink 生效）。
   *
   * 容错：若文档在快照之后发生了变化（无法安全复用原选区对象），则用位置信息
   * 重建；两者都失败时**静默保持现状**并回一条 `diagnostic`，绝不抛错中断后续命令。
   *
   * ⚠️ 本命令只是**第一道防线**（恢复可见选区与后续书写起点）。
   * 真正的落点精度由 `format.createLink` 的 `from` / `to` 参数保证（第二道防线）——
   * 即便本命令失败，写链接也不会插错位置。
   */
  | { type: "restoreSelection" }
  /**
   * 移除光标（或选区）所在位置的链接，**保留文字**（v2026-09-22 新增）
   *
   * 链接对话框在「编辑链接」模式下的「移除链接」按钮。JS 侧调 `editor.deleteLink()`：
   * 优先按光标位置找链接范围并去 mark；找不到时回落为「去掉当前选区上的 link mark」。
   *
   * @param from 快照选区的位置（可选，来自 `selectionRange`）。传了就按它定位，
   *             免去"依赖当前选区恰好还在链接上"的隐患；缺省则按当前选区锚点。
   */
  | { type: "deleteLink"; from?: number }
  /**
   * 打开 / 关闭「链接面板」（v2026-09-24 新增）
   *
   * 底部工具栏 🔗 按钮不再走宿主自绘的 `AlertDialog`，改为复用官方
   * `CreateLinkButton` 那套**受控** popover——面板内容直接用官方
   * `EditLinkMenuItems`（URL 输入框 + 显示文字输入框），外观、`https://` 补全
   * 校验、提交行为与 WebView 内官方按钮**逐字一致**。
   *
   * 为什么必须由宿主主动开关：面板渲染在 WebView 内部的 React 树上，宿主（Compose）
   * 碰不到它。官方 `CreateLinkButton` 恰好把 popover 写成受控
   * （`open={showPopover}`），本命令就是接到那个开关上。
   *
   * 另有一条反向的上行 {@link UpMessage} `linkPanelClosed`：用户点面板外部关掉时
   * 宿主无从得知，需 JS 主动通报，否则宿主会以为面板还开着。
   */
  | { type: "openLinkPanel" }
  | { type: "closeLinkPanel" };

/** 上行消息（JS → Kotlin） */
export type UpMessage =
  | {
      type: "ready";
      /**
       * 构建指纹（v1.8）：由 vite define 在构建期注入，形如
       * "<构建时间> <commit 短 hash><-dirty?>"。
       * 宿主收到后打进 logcat，用于排查「JS 源码改了但真机没生效」
       * ——即 assets 里的产物是否被重新构建过。
       */
      build?: string;
      /**
       * 源码内容哈希（v2026-09-22）：由 vite define 注入，形如 `bn-src:a1b2c3d4e5f6`。
       * 与 `build` 互补——`build` 说明"哪一版"，本字段说明"是不是当前源码编的"，
       * 另有 `scripts/check-blocknote-artifact.ps1` 用它拦截「改了源码忘重建产物就提交」。
       */
      srcHash?: string;
    }
  /** 内容变更快照（JS 侧防抖 800ms） */
  | { type: "changed"; markdown: string }
  /**
   * 撤销/重做可用态（v1.7）：JS 侧历史栈变化后上报，
   * 宿主据此给左上角撤销/重做按钮置灰（对齐 Compose 版 canUndo/canRedo 语义）。
   */
  | { type: "undoState"; canUndo: boolean; canRedo: boolean }
  /**
   * 当前光标块状态（v1.11）
   *
   * 原 ⋮⋮ 手柄的点击菜单有 4 项，其中「删除块」无条件可用，另外三项各有前置条件
   * （块级颜色看块类型是否声明 textColor/backgroundColor；表头看是否表格块）。
   * 菜单被移除、功能移入宿主工具栏后，宿主必须自己知道**当前能不能点**，
   * 否则会出现"点了没反应"的哑按钮，故由 JS 侧统一判定后上行。
   *
   * 仅在上报内容发生变化时上行（JS 侧做去重），避免选区移动时刷屏。
   */
  | {
      type: "blockState";
      /** 光标块类型（BlockNote 的 block.type，如 paragraph / heading / table / image） */
      blockType: string;
      /**
       * 当前块是否为「复选框块」（v2026-09-22 新增；工具栏复选框按钮的激活态）
       *
       * 判据 = `blockType === "checkListItem"`（BlockNote 的复选框是独立块类型）。
       *
       * ⚠️ 为什么要单独上行：宿主原先读 Compose 时代遗留的
       * `BodyBlocksController.isFocusedBlockCheckbox`（读**宿主本地块对象**的段落类型），
       * 而 BlockNote 模式下正文只在 ProseMirror 文档树里 → 那份镜像恒为 false
       * → 复选框按钮**永远不高亮**。与 B / I / U / S 是同一个坑。
       * 缺省（undefined / 旧产物未下发）时宿主按 false 处理。
       */
      isCheckboxBlock?: boolean;
      /**
       * 光标位置（或选区起点）上的**已有链接 URL**（v2026-09-22 新增）
       *
       * 供宿主做两件事：
       * ① 底部工具栏 🔗 按钮的**激活态**（有链接即高亮）——此前取的是 Compose 时代
       *    遗留的 `RichTextState.isLink`，BlockNote 模式下恒为 false，永远不高亮；
       * ② 链接对话框据此进入「**编辑链接**」模式：预填 URL、按钮改为「更新」、
       *    并额外提供「移除链接」。
       *
       * 口径与官方 `CreateLinkButton` 一致：`editor.getSelectedLinkUrl()`
       * （内部 = `getLinkMarkAtPos(selection.from)`）。**缺省 / 空串 = 当前不在链接上**。
       */
      linkUrl?: string;
      /**
       * 光标块的标题级别（v2026-09-21 新增，供宿主「标题面板」回显选中态）：
       * - `blockType === "heading"` → 1–6（普通标题**与**可折叠标题同级，级别取 `props.level`）
       * - 非标题块 → 0（宿主据此不高亮任何标题格子）
       *
       * ⚠️ v2026-09-22 修正：可折叠标题**不是独立块类型**，它同样落在 `heading` 上，
       * 靠 `headingToggleable` 区分。原注释"以 `toggleHeading` 开头 → 1–3"是错的。
       */
      headingLevel?: number;
      /**
       * 是否为「可折叠标题」（v2026-09-22 新增）
       *
       * BlockNote 里折叠标题 = `type === "heading"` + `props.isToggleable === true`，
       * 级别仍取 `headingLevel`。宿主「标题面板」据此在「普通标题 / 可折叠标题」
       * 两个分区之间分流——两类都有 1/2/3 级，**只靠 headingLevel 无法区分**。
       * 普通标题、非标题块一律为 false。
       */
      headingToggleable?: boolean;
      /**
       * 当前**选区**的行内文字色 / 行内背景色（v2026-09-22 新增）
       *
       * 取 `getActiveStyles().textColor` / `.backgroundColor` 的原始字符串值——
       * 宿主 A 面板下发的自由 hex（如 `#FF9A5C`），也可能是粘贴内容里的色名 / `rgb()`。
       * 宿主据此在当前主题的色板里反查色名以点亮对应色点；**缺省（undefined）表示该维度
       * 未设置**，宿主按「默认」处理并高亮第一个「/」清除块。
       *
       * ⚠️ 与下面的 `blockTextColor` / `blockBackgroundColor` **不是一回事**：
       * 那两项是**块级**颜色（色名，作用于整段，走块 props）；
       * 本两项是**行内**颜色（作用于选区文字，走 mark 样式）。
       */
      inlineTextColor?: string;
      inlineBackgroundColor?: string;
      /**
       * 当前选区的四个**行内布尔样式**激活态（v2026-09-22 新增）
       *
       * 供宿主底部工具栏 B / I / U / S 四个按钮的高亮回显。取自同一份
       * `getActiveStyles()` 快照的 `bold` / `italic` / `underline` / `strike`
       * （布尔型样式，已归一为布尔——`undefined` 一律转 false，宿主不必再做真值判断）。
       *
       * ⚠️ 为什么必须由 JS 上行：BlockNote 模式下正文只存在于 ProseMirror 文档树里，
       * 宿主那份 `RichTextState` 只是「聚焦块 / 首块」的本地镜像，其 `currentSpanStyle`
       * 恒为空 → 四个按钮永远不高亮（v2026-09-22 修复的问题）。
       */
      bold?: boolean;
      italic?: boolean;
      underline?: boolean;
      strike?: boolean;
      /**
       * 光标块的对齐方式（v2026-09-22 新增）
       *
       * 供宿主底部工具栏「左 / 居中 / 右」三个对齐按钮的高亮回显。
       *
       * ⚠️ 对齐在 BlockNote 里是**块级 prop**（`props.textAlignment`），
       * **不是**行内样式——与上面四个布尔样式不在同一维度，故读的是块 props 而非
       * `getActiveStyles()`。合法值 "left" / "center" / "right" / "justify"，
       * 各 block spec 的默认值为 "left"，故缺省（未显式设置）即等同左对齐。
       */
      textAlignment?: string;
      /** 当前块是否支持块级颜色（块 spec 声明了 textColor 或 backgroundColor） */
      canSetBlockColor: boolean;
      /** 当前块的文本色（预设色名；"default" 或 undefined = 默认色） */
      blockTextColor?: string;
      /** 当前块的背景色（预设色名；"default" 或 undefined = 无背景色） */
      blockBackgroundColor?: string;
      /** 当前块是否可切换表头（table 块 且 settings.tables.headers 为真） */
      canToggleHeader: boolean;
      /** 表格当前是否有标题行（非表格恒 false） */
      isHeaderRow: boolean;
      /** 表格当前是否有标题列（非表格恒 false） */
      isHeaderCol: boolean;
    }
  | { type: "error"; message: string }
  /**
   * 正文基础字号变化上行（v2026-09-21）：JS 侧「无选区点正文字号」改全局基础
   * 字号后通知宿主——宿主据此更新 BodyFontSizeManager（内存态下发幂等）并写
   * SharedPreferences 持久化。
   */
  | { type: "baseFontSize"; fontSizePx: number }
  /**
   * 编辑器焦点态上行（v2026-09-22）
   *
   * 宿主「T / H / A」面板收起时要判断「正文里是不是还有光标」，以决定是否弹回键盘。
   * 这个真值**只能在 JS 侧取**：Android 侧 `View.hasFocus()` 会失真——点底部栏按钮时
   * 焦点已转移到 Compose 根视图，但 WebView 内的 contenteditable **仍持有 DOM 焦点**
   * （用户看到光标还在闪）。
   *
   * JS 侧在 document 上监听 `focusin` / `focusout`，判定 `document.activeElement`
   * 是否落在 `.bn-editor` 内；仅在状态翻转时上行（去重，避免刷屏）。
   */
  | { type: "editorFocus"; focused: boolean }
  /**
   * 选区区间快照（v2026-09-22 新增）：对下行 `saveSelection` 的应答。
   *
   * 链路：宿主点 🔗 → `saveSelection` → JS 记下选区对象 + doc 引用，**并把区间上行** →
   * 宿主暂存 from/to → 用户确认时，`createLink` / `deleteLink` 把这组位置带回 JS。
   *
   * 为什么要绕这一圈：`restoreSelection` 靠"文档未变则复用选区对象"工作，一旦文档
   * 在快照后被改动就只能按位置重建/放弃。把位置**显式交给宿主保管**，写链接时即使
   * 编辑器当前选区已丢失也能精确落点——两道防线各管一段，互不依赖。
   *
   * 位置语义与 ProseMirror 一致：文档内偏移（首块内首字符约为 1）。
   * 宿主须原样暂存、不得自行推算（`from === to` = 光标态，`from < to` = 有选区）。
   */
  | { type: "selectionRange"; from: number; to: number }
  /**
   * 诊断信息上行（v1.11.7）：仅用于 logcat 排错，**不参与业务逻辑**。
   *
   * 存在的理由：WebView 侧的很多运行时真值（CSS 变量是否注入、min-height 是否生效、
   * 元素实际高度）在 Kotlin 侧完全看不到，只能靠猜。有了这条通道，JS 可主动回传，
   * 宿主以 `BlockNoteEditor` tag 打进 logcat。
   *
   * ⚠️ 与 `error` 的区别：`error` 表示**异常**（宿主可能据此提示用户）；
   * `diagnostic` 只是**观测值**，宿主只打 log，不做任何 UI 反应。
   */
  | { type: "diagnostic"; message: string }
  /**
   * 请求宿主在**外部浏览器**打开链接（v2026-09-24 新增）
   *
   * = JS 侧 `links.onClick`（只读态）与自定义 LinkToolbar「打开」按钮的统一出口。
   *
   * **为什么要走这条上行，而不是让 JS 自己 `window.open`**：
   * Android WebView 的 `setSupportMultipleWindows` 默认 false，任何**带 target 的
   * `window.open`（含官方 LinkToolbar 的 `_blank`）会被 Chromium 静默丢弃**——
   * 不导航、不报错、无日志，表现就是"点了没反应"（WebView 侧完全黑盒，无从排查）。
   * 而 WebView 内主框架导航又会让编辑器被目标页顶掉。故链接一律交宿主用
   * `Intent.ACTION_VIEW` 送系统浏览器，WebView 自身永不导航。
   *
   * **调用时机**（两处，均为用户**显式表达"我要打开"**）：
   * ① 自定义 LinkToolbar 的「打开」按钮（本项目的规范打开入口）；
   * ② 只读态下点击链接（`editable === false`，无 LinkToolbar 可用，点击即打开）。
   *
   * ⚠️ **编辑态单击链接不在此列**：按产品决策，编辑态单击只落光标并弹出
   * LinkToolbar，由用户自行决定是否打开——见 `EditorApp.tsx` 的 `handleLinkClick`。
   *
   * @param url 完整 URL（JS 侧已过滤 `javascript:` 等伪协议与解析失败的相对路径）
   */
  | { type: "openLink"; url: string }
  /**
   * 链接面板已关闭（v2026-09-24 新增）：对 `openLinkPanel` 的**反向通报**
   *
   * 面板有两条关闭路径，而宿主只用显式命令控制其中一条：
   * ① **用户提交表单**（Enter / 提交按钮）→ 面板自己关，宿主按约定也会收到一次
   *    `closeLinkPanel` 收尾（幂等，重复关不报错）；
   * ② **用户点面板外部 / 按 Esc** → 由官方 popover 的 dismiss 行为关闭，
   *    宿主**完全无从得知**。没有这条上行，宿主会一直以为面板还开着，
   *    进而影响「面板收起后是否把软键盘弹回来」的判定。
   *
   * 故 JS 在 `onOpenChange(false)` 时无条件上行一次，宿主据此把本地状态对齐。
   */
  | { type: "linkPanelClosed" };

declare global {
  interface Window {
    /** Kotlin 注入的原生桥（真机环境存在；dev/浏览器环境缺失） */
    AndroidBridge?: { postMessage(json: string): void };
    /** JS 侧下行消息宿主（Kotlin evaluateJavascript 调用入口） */
    BlockNoteEditorHost?: { onMessage(raw: unknown): void };
  }
}

/** 是否运行在 WebView 宿主内 */
export function isEmbedded(): boolean {
  return typeof window !== "undefined" && !!window.AndroidBridge;
}

/** 上行一条消息（无宿主时打 console，便于 dev 调试） */
export function sendUp(msg: UpMessage): void {
  const json = JSON.stringify(msg);
  if (window.AndroidBridge) {
    window.AndroidBridge.postMessage(json);
  } else {
    // eslint-disable-next-line no-console
    console.log("[bridge:up]", json);
  }
}

/** 绑定下行消息处理器（幂等：重复绑定覆盖前一次）。
 *  raw 兼容两种形态：Kotlin evaluateJavascript 注入的「对象字面量」（已是对象）
 *  与字符串 JSON——typeof 判别后再走解析，避免 "[object Object]" 二次解析错误。 */
export function bindDown(handler: (msg: DownMessage) => void): void {
  window.BlockNoteEditorHost = {
    onMessage: (raw: unknown) => {
      try {
        const msg = (typeof raw === "string" ? JSON.parse(raw) : raw) as DownMessage;
        handler(msg);
      } catch (e) {
        sendUp({ type: "error", message: `bad down message: ${String(e)}` });
      }
    },
  };
}

/**
 * 构建指纹（v1.8）
 *
 * 由 `vite.editor.config.ts` 的 define 在**构建期**文本替换注入，形如
 * `"2026-09-17 18:20:31 a1b2c3d"`（构建时间 + commit 短 hash，工作区脏时带 -dirty）。
 *
 * 存在的意义：assets 里的 `editor.html` 是**静态资源**，Gradle 不会重新生成它，
 * 曾经的坑是「JS 源码改了、App 也重编了，但真机仍是旧 bundle」且无从判断。
 * 现在宿主把该值打进 logcat，一眼就能确认 WebView 加载的是哪一版产物。
 *
 * 兜底：直接用 dev server（`npm run dev`）时该常量未被 define 替换，
 * 此时回落到 "dev"。用 typeof 守卫而非直接引用，避免 ReferenceError。
 */
export const BUILD_FINGERPRINT: string =
  typeof __BUILD_FINGERPRINT__ === "string" ? __BUILD_FINGERPRINT__ : "dev";

/** vite define 注入的全局常量声明（构建期被替换为字符串字面量） */
declare const __BUILD_FINGERPRINT__: string;

/**
 * 源码内容哈希（v2026-09-22 新增，形如 `bn-src:a1b2c3d4e5f6`）
 *
 * 与 [BUILD_FINGERPRINT] 互补：指纹回答"这版是什么时候、用哪个 commit 构建的"，
 * 本哈希回答"这版**装进去的源码内容**是不是当前工作区这份"。
 * 宿主把它随 `ready` 上行打进 logcat，另有校验脚本拿它与"此刻 `src/editor/` +
 * `editor.html` 现算的哈希"比对，用来拦截「改了源码却忘了重建产物就提交」。
 *
 * 兜底同 [BUILD_FINGERPRINT]：dev server 下未注入，回落 "dev"（校验脚本见此值直接放行）。
 */
export const SRC_HASH: string =
  typeof __SRC_HASH__ === "string" ? __SRC_HASH__ : "dev";

/** vite define 注入的全局常量声明（构建期被替换为字符串字面量） */
declare const __SRC_HASH__: string;
