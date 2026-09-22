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
   *   toggleList、quote、paragraph、codeBlock、table、pageBreak——块类型转换/插入）
   *
   * ⚠️ transform 的 value 是**动作名**，不等于块类型名（v2026-09-22 踩坑）：
   * - `toggleHeading*` → 实为 `heading` + `props.isToggleable = true`（无 toggleHeading 类型）
   * - `toggleList` → 实为 `toggleListItem`
   * 把动作名直接当 `type` 传给 `updateBlock` 会因 schema 查不到类型而抛 TypeError。
   */
  | { type: "format"; action: string; value?: string }
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
  | { type: "focusEditor" };

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
   * 诊断信息上行（v1.11.7）：仅用于 logcat 排错，**不参与业务逻辑**。
   *
   * 存在的理由：WebView 侧的很多运行时真值（CSS 变量是否注入、min-height 是否生效、
   * 元素实际高度）在 Kotlin 侧完全看不到，只能靠猜。有了这条通道，JS 可主动回传，
   * 宿主以 `BlockNoteEditor` tag 打进 logcat。
   *
   * ⚠️ 与 `error` 的区别：`error` 表示**异常**（宿主可能据此提示用户）；
   * `diagnostic` 只是**观测值**，宿主只打 log，不做任何 UI 反应。
   */
  | { type: "diagnostic"; message: string };

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
