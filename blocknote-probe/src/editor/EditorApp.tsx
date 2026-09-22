import "@blocknote/mantine/style.css";
import { BlockNoteView } from "@blocknote/mantine";
import {
  FormattingToolbar,
  FormattingToolbarController,
  useBlockNoteEditor,
  useComponentsContext,
  useCreateBlockNote,
} from "@blocknote/react";
import { BlockNoteEditor, blockHasType } from "@blocknote/core";
import { useCallback, useEffect, useRef, useState } from "react";
import { editorSchema } from "./schema";
import { bindDown, sendUp, BUILD_FINGERPRINT, type ThemePayload } from "./bridge";
import { mdToBlocks, blocksToMd, toWebImageUrl } from "./markdown/converter";
import "../probe.css";
import "./editor.css";

/* eslint-disable @typescript-eslint/no-explicit-any */

/**
 * markdown 解析专用的临时 editor 实例（模块级单例）：
 * tryParseMarkdownToBlocks 是实例方法，正式实例要等 blocks 就绪才创建（鸡生蛋），
 * 故用一个与正式实例同 schema 的独立实例承担解析。
 */
let mdLoaderEditor: any = null;
function getMdLoader(): any {
  if (!mdLoaderEditor) {
    mdLoaderEditor = BlockNoteEditor.create({ schema: editorSchema as any });
  }
  return mdLoaderEditor;
}

/** 光标块类型切换（已是目标类型则退回普通段落）——列表/任务按钮的 toggle 语义 */
function toggleBlockType(ed: any, type: string): void {
  const { block } = ed.getTextCursorPosition();
  const target = block.type === type ? "paragraph" : type;
  ed.updateBlock(block, { type: target });
}

/** 字号循环序列（S8）：点击依次加大，末档点击清除 */
const FONT_SIZE_CYCLE = ["14px", "16px", "18px", "20px", "24px", "28px", "32px"];
/** 文字颜色循环序列（S8）：BlockNote 内置 textColor 值名 */
const TEXT_COLOR_CYCLE = ["red", "orange", "yellow", "green", "blue", "purple"];

/** 字号循环应用到当前选区：无 → 最小 → 递增 → 末档清除（S8） */
function cycleFontSize(editor: any): void {
  const cur = editor.getActiveStyles()?.fontSize as string | undefined;
  if (!cur) {
    editor.addStyles({ fontSize: FONT_SIZE_CYCLE[0] });
    return;
  }
  const i = FONT_SIZE_CYCLE.indexOf(cur);
  if (i === -1 || i === FONT_SIZE_CYCLE.length - 1) {
    editor.removeStyles({ fontSize: cur });
    return;
  }
  editor.addStyles({ fontSize: FONT_SIZE_CYCLE[i + 1] });
}

/** 文字颜色循环应用到当前选区：默认 → 红 → … → 紫 → 清除（S8） */
function cycleTextColor(editor: any): void {
  const cur = editor.getActiveStyles()?.textColor as string | undefined;
  if (!cur || cur === "default") {
    editor.addStyles({ textColor: TEXT_COLOR_CYCLE[0] });
    return;
  }
  const i = TEXT_COLOR_CYCLE.indexOf(cur);
  if (i === -1 || i === TEXT_COLOR_CYCLE.length - 1) {
    editor.removeStyles({ textColor: "default" });
    return;
  }
  editor.addStyles({ textColor: TEXT_COLOR_CYCLE[i + 1] });
}

/** S8：字号循环按钮（格式工具栏内）——无 → 最小 → 递增 → 末档清除 */
function FontSizeButton() {
  const Components = useComponentsContext()!;
  const editor = useBlockNoteEditor<any, any, any>();
  const cur = editor.getActiveStyles()?.fontSize as string | undefined;
  return (
    <Components.FormattingToolbar.Button
      className="bn-button"
      onClick={() => cycleFontSize(editor)}
      label={`字号 ${cur ?? "默认"}`}
      mainTooltip="字号（点击切换，末档清除）"
    >
      <span style={{ fontSize: 13, fontWeight: 600 }}>Aa</span>
    </Components.FormattingToolbar.Button>
  );
}

/** S8：文字颜色循环按钮（格式工具栏内）——默认 → 红 → … → 紫 → 清除 */
function TextColorButton() {
  const Components = useComponentsContext()!;
  const editor = useBlockNoteEditor<any, any, any>();
  const cur = editor.getActiveStyles()?.textColor as string | undefined;
  return (
    <Components.FormattingToolbar.Button
      className="bn-button"
      onClick={() => cycleTextColor(editor)}
      label={`文字颜色 ${cur ?? "默认"}`}
      mainTooltip="文字颜色（点击切换，末档清除）"
    >
      <span
        style={{
          color: cur && cur !== "default" ? cur : "var(--editor-primary, #1976d2)",
          fontWeight: 700,
        }}
      >
        A
      </span>
    </Components.FormattingToolbar.Button>
  );
}

/** 生成并注入 @font-face（字体文件走 Kotlin shouldInterceptRequest 流） */
function applyFontFaces(fontWeights: Record<string, number[]>): void {
  let css = "";
  for (const [id, weights] of Object.entries(fontWeights)) {
    for (const w of weights) {
      css += `@font-face{font-family:"ff-${id}";src:url("https://corgimemo.local/fonts/${id}/${w}.ttf") format("truetype");font-weight:${w};font-display:swap;}\n`;
    }
  }
  let style = document.getElementById("content-fonts") as HTMLStyleElement | null;
  if (!style) {
    style = document.createElement("style");
    style.id = "content-fonts";
    document.head.appendChild(style);
  }
  style.textContent = css;
}

/**
 * 正式编辑器应用（P0）：
 * - Bridge 装载：init{markdown, readOnly, theme, fontFamily, fonts} → **解析完成后**才挂编辑器核心
 *   （initialContent 只在 useCreateBlockNote 实例创建时生效——解析必须先于挂载，否则得到空文档）
 * - 变更上行：onChange 防抖 800ms → blocksToMd → sendUp(changed)
 * - 主题/字体：下行消息 → CSS 变量；字体文件由 Kotlin shouldInterceptRequest 流式提供（S5）
 * - undo/redo：JS 侧按钮（P0 就位，正式 UI 归属 P1 工具条）
 */
export default function EditorApp() {
  /** init 是否已到达 */
  const [booted, setBooted] = useState(false);
  const [initialMarkdown, setInitialMarkdown] = useState("");
  const [readOnly, setReadOnly] = useState(false);
  const [theme, setTheme] = useState<ThemePayload>({ dark: false, primary: "#1976d2" });
  /**
   * 编辑区最小高度（dp，v1.11.6）
   *
   * 由宿主经 `setEditorMinHeight` 下发。`.bn-editor` 本身没有 min-height、
   * 高度完全由内容决定，宿主却给 WebView 设了 `heightIn(min)`，
   * 内容少时会在 WebView 内留下大片**不在 contenteditable 盒子里**的死区
   * （点击无法聚焦）。下发该值后由 editor.css 的 `.bn-editor { min-height }` 消费。
   */
  const [editorMinHeight, setEditorMinHeight] = useState(0);
  const [fontFamily, setFontFamily] = useState("system_default");
  /** 英文/数字字体 id（v2026-09-21：拉丁回退层；空串 = 跟随中文） */
  const [latinFontId, setLatinFontId] = useState("");
  /**
   * 正文基础字号（v2026-09-21，px；默认 16）：H 面板「正文字号」在**无文字选区**
   * 时点选 → 修改此值（全局正文基础字号，作用于未叠加行内样式的全部文字）。
   * 与行内 fontSize 样式层级清晰：CSS 继承 < 行内 style。
   */
  const [baseFontSize, setBaseFontSize] = useState(16);
  /**
   * baseFontSize 的 ref 镜像：pushBlockState 是空依赖 useCallback，其上行
   * fontSizePx 在无行内样式时要回落**当前**基础字号——经 ref 读最新值。
   */
  const baseFontSizeRef = useRef(16);
  /** 可用字体清单（S5）：id → 字重数组 */
  const [fontWeights, setFontWeights] = useState<Record<string, number[]>>({});
  /** 表情选择面板显隐（v1.5 openEmojiPicker 下行切换） */
  const [emojiOpen, setEmojiOpen] = useState(false);
  /** 解析完成的初始块 */
  const [initialBlocks, setInitialBlocks] = useState<any[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  /** editor 实例引用（bridge 下行的 requestSave 需要） */
  const editorRef = useRef<any>(null);

  /** 上一次上报的撤销/重做可用态（做变化去重，避免冗余上行，v1.7） */
  const lastUndoStateRef = useRef<{ canUndo: boolean; canRedo: boolean } | null>(null);

  /**
   * 取历史扩展里的撤销/重做 prosemirror 命令（v1.10）。
   *
   * ⚠️⚠️ 判定可用态必须走 `editor.canExec(command)`，**不能用 `editor.can(...)`**：
   * `BlockNoteEditor` 原型上**根本没有 `can` 方法**（全类仅有 `exec` / `canExec`），
   * `StateManager.can(cb)` 也**未**被转发到 editor 上。所以任何 `editor.can(x)` 写法
   * 都会在运行时抛 `TypeError: ed.can is not a function`——若外面还包着静默 catch，
   * 表现就是「按钮永远灰」而毫无线索（v1.7 的原始 bug 正是如此）。
   *
   * 另注：即便 `can` 存在也传不得裸引用——`can(cb)` 内部是**无参** `cb()`：
   * - 传实例方法 `ed.undo` → `this` 丢失 → `this._stateManager` 抛 TypeError；
   * - 传 prosemirror 命令（需 `(state, dispatch, view)`）→ state 为 undefined →
   *   `historyKey.getState(undefined)` 抛 TypeError。
   * 正确形态即 BlockNote 自身 `StateManager.undo()` 的用法：把命令交给 `canExec`，
   * 由它用**真实**的 prosemirrorState 调 `command(state, undefined, view)`（只判定不 dispatch）。
   *
   * 命令来源用 `yUndo` 优先、`history` 兜底——与 `StateManager.undo()` 内部一致
   * （`HistoryExtension` 由 `getDefaultExtensions()` 无条件注册，两个 key 必有一个存在）。
   */
  const getHistoryCommands = useCallback(() => {
    const ed = editorRef.current;
    if (!ed) return null;
    const ext = (ed.getExtension("yUndo") ?? ed.getExtension("history")) as any;
    if (!ext?.undoCommand || !ext?.redoCommand) return null;
    return { undoCommand: ext.undoCommand, redoCommand: ext.redoCommand };
  }, []);

  /**
   * 上报撤销/重做可用态（v1.7）：宿主左上角按钮据此置灰。
   *
   * `canExec(command)` 只判定不 dispatch（内部传 `dispatch === undefined`），
   * 不污染历史栈。JS 侧做变化去重，仅在布尔态翻转时上行。
   *
   * 异常（v1.10）：不再静默吞——上行 `error` 让宿主 logcat 可见，避免同类问题再次无声失败。
   */
  const pushUndoState = useCallback(() => {
    const ed = editorRef.current;
    if (!ed) return;
    const cmds = getHistoryCommands();
    if (!cmds) return; // 历史扩展尚未注册（编辑器挂载前的空窗）：静默跳过，非异常
    let canUndo = false;
    let canRedo = false;
    try {
      canUndo = !!ed.canExec(cmds.undoCommand);
      canRedo = !!ed.canExec(cmds.redoCommand);
    } catch (e: any) {
      // v1.10：异常必须可见——历史插件未就绪等情况下保守上报 false，同时上行诊断
      sendUp({ type: "error", message: `undoState probe failed: ${e?.message ?? e}` });
      return;
    }
    const prev = lastUndoStateRef.current;
    if (prev && prev.canUndo === canUndo && prev.canRedo === canRedo) return;
    lastUndoStateRef.current = { canUndo, canRedo };
    sendUp({ type: "undoState", canUndo, canRedo });
  }, [getHistoryCommands]);

  /** 上一次上报的当前块状态（JSON 串做去重键，v1.11） */
  const lastBlockStateRef = useRef<string | null>(null);

  /**
   * 上报当前光标块状态（v1.11）
   *
   * 背景：原 ⋮⋮ 手柄的点击菜单有 4 项，按用户决策全部移入宿主底部工具栏，
   * 手柄本身只留拖拽。宿主因此必须知道「当前块能点什么」，否则会出现
   * 点了没反应的哑按钮。其中「删除块」无条件可用（只要有块），故不参与判定。
   *
   * 判定口径**照抄官方**，避免"官方菜单能点、桥过来的按钮却置灰"这类不一致：
   * - 块颜色 → `blockHasType(block, ed, block.type, { textColor | backgroundColor })`
   *   （官方 `BlockColorsItem` 的写法）
   * - 表头   → `block.type === "table" && editor.settings.tables.headers`
   *   （官方 `TableHeadersItem` 的写法；官方目前只支持 1 行 / 1 列，故用布尔）
   * - 标题级别 → v2026-09-21 新增 `headingLevel`：普通/可折叠标题**同为 `heading` 块**，
   *   级别都取 `props.level`（1–6），非标题块为 0；另附 `headingToggleable`（v2026-09-22）
   *   区分"是否折叠"，宿主「标题面板」据此高亮对应格子；
   * - Nest / Unnest → v2026-09-22 新增 `canNestBlock` / `canUnnestBlock`：
   *   直接读 `ed.canNestBlock()` / `ed.canUnnestBlock()`（官方同款口径），
   *   宿主工具栏两个缩进按钮据此置灰；
   * - 行内四样式 → v2026-09-22 新增 `bold` / `italic` / `underline` / `strike`：
   *   取自本函数已有的 `getActiveStyles()` 快照（布尔型样式），供底部工具栏
   *   B / I / U / S 四个按钮的**高亮**回显；
   * - 对齐 → v2026-09-22 新增 `textAlignment`：读块级 prop `props.textAlignment`
   *   （对齐是**块级**属性，不是行内样式），供三个对齐按钮的高亮回显；
   *
   * 用 JSON 串做去重键：只有选区跨块移动、或有色/表头状态真的变了才上行，
   * 同一块内移动光标不产生流量。
   *
   * 异常同样不静默吞（延续 v1.10 的教训），但做去重，避免选区每次移动都刷 error。
   */
  const pushBlockState = useCallback(() => {
    const ed = editorRef.current;
    if (!ed) return;
    try {
      const { block } = ed.getTextCursorPosition();
      const supportsTextColor = blockHasType(block, ed, block.type, {
        textColor: "string",
      });
      const supportsBgColor = blockHasType(block, ed, block.type, {
        backgroundColor: "string",
      });
      const isTable = block.type === "table";
      const props = (block.props ?? {}) as Record<string, unknown>;
      const content = (block.content ?? {}) as Record<string, unknown>;
      /**
       * 标题级别（v2026-09-21 新增）：供宿主「标题面板」回显"当前块是不是 Hx"。
       *
       * ⚠️ v2026-09-22 修正：两类标题在 BlockNote 里是**同一个块类型** `heading`，
       * 靠 `props.isToggleable` 区分（折叠标题**不是**独立块类型，详见下方 transform
       * 分支的说明）。原实现按 `block.type.startsWith("toggleHeading")` 判定可折叠，
       * 而真实 blockType 永远是 `heading` → 该分支**永不成立**，于是：
       * - 面板永远点亮不了「可折叠标题」那一排；
       * - 反而命中 `heading` 分支，把可折叠标题误判成普通标题去点亮。
       *
       * 现改为：`headingLevel` = `heading` 块的 `props.level`（1–6，非标题块为 0）；
       * 另上行 `headingToggleable` 布尔字段，宿主据此在两类分区之间分流。
       *
       * 非标题块一律上行 0（宿主据此不高亮任何格子）。
       * `props.level` 容错：非有限数或 ≤0 时按 0 处理，避免 NaN 上行污染去重键。
       */
      const rawLevel = Number(props.level);
      const safeLevel = Number.isFinite(rawLevel) && rawLevel > 0 ? rawLevel : 0;
      const isHeading = block.type === "heading";
      /** 是否为「可折叠标题」（v2026-09-22 新增；普通标题恒为 false） */
      const headingToggleable = isHeading && props.isToggleable === true;
      const headingLevel = isHeading ? safeLevel : 0;
      /**
       * 当前选区的行内样式（v2026-09-22 新增：A 面板两个「选中色」行的回显）
       *
       * 只取一次 `getActiveStyles()`，供 fontSizePx 与两个颜色字段共用——
       * 该方法内部要读 ProseMirror 选区与 mark 集合，避免在同一 payload 里重复调用。
       *
       * 颜色字段取出的是 mark 的 `stringValue`（宿主下发的自由 hex，或粘贴来的色名/rgb），
       * 宿主拿到后按当前主题色板反查色名，点亮对应色点；无该维度样式时为 undefined
       * （宿主视为「默认」，高亮第一个「/」清除块——与面板语义一致）。
       */
      const activeStyles = ed.getActiveStyles() ?? {};
      const inlineTextColor =
        typeof activeStyles.textColor === "string" ? activeStyles.textColor : undefined;
      const inlineBackgroundColor =
        typeof activeStyles.backgroundColor === "string"
          ? activeStyles.backgroundColor
          : undefined;
      const payload = {
        blockType: block.type as string,
        headingLevel,
        headingToggleable,
        /**
         * 当前选区字号（v2026-09-21：H 面板「正文字号」档位回显）：
         * `getActiveStyles().fontSize` 为 "18px" 形式字符串 → 解析为整数 px
         * （WebView 内 1px=1dp，宿主档位为 sp 值，数值直接对应）；
         * 无行内样式 → 回落**当前基础字号**（baseFontSizeRef，全局正文字号）——
         * 档位高亮据此正确点亮。随 blockState 走同一去重与上行时机
         * （选区变化 / 内容变化，含 addStyles 引起的 mark 变化）。
         */
        fontSizePx: (() => {
          const fs = activeStyles.fontSize as string | undefined;
          const m = typeof fs === "string" ? /^(\d+(?:\.\d+)?)px$/.exec(fs) : null;
          return m ? Math.round(parseFloat(m[1])) : baseFontSizeRef.current;
        })(),
        /** 行内文字色 / 背景色（v2026-09-22 新增；缺失 = 该维度未设置） */
        inlineTextColor,
        inlineBackgroundColor,
        /**
         * 四个行内布尔样式的激活态（v2026-09-22 新增）
         *
         * 宿主底部工具栏 B / I / U / S 四个按钮的高亮判据。这四个样式在 BlockNote
         * 里是**布尔型**（`getActiveStyles().bold` 等），取出为 `true` / `undefined`；
         * 此处统一归一为布尔再上行，宿主直接用，不必再判真值。
         *
         * ⚠️ 必须随 blockState 上行：宿主原先读的是 Compose 时代的
         * `state.currentSpanStyle`，而 BlockNote 模式下正文只存在于 ProseMirror 文档树，
         * 宿主那份 RichTextState 的 spanStyle 恒为空 → 四个按钮**永远不高亮**。
         *
         * 用 `=== true` 而非 `!!`：值可能是 undefined / false / true 三态，
         * 显式比较既完成归一，也避免把 `0` / `""` 之类边缘值误判为激活。
         */
        bold: activeStyles.bold === true,
        italic: activeStyles.italic === true,
        underline: activeStyles.underline === true,
        strike: activeStyles.strike === true,
        /**
         * 光标块的对齐方式（v2026-09-22 新增）
         *
         * 宿主三个对齐按钮的高亮判据。⚠️ 对齐是**块级 prop**（`props.textAlignment`），
         * 不是行内样式——与上面四个布尔样式不同维度，故读 `props` 而非 activeStyles。
         * 非字符串（未设置 / 异常值）时回落 "left"：各 block spec 的 `textAlignment`
         * 默认值就是 "left"，回落值与「未设置时的真实渲染结果」一致（左对齐高亮）。
         */
        textAlignment:
          typeof props.textAlignment === "string" ? props.textAlignment : "left",
        /**
         * Nest / Unnest（缩进 / 回退缩进）可用态（v2026-09-22 新增）
         *
         * 宿主底部工具栏这两个按钮的**置灰判据**。口径**照抄官方**：
         * @blocknote/react 的 `NestBlockButton` / `UnnestBlockButton` 就是直接读
         * `editor.canNestBlock()` / `editor.canUnnestBlock()`；自己另写一套判定
         * 必然出现"官方能点、宿主却置灰"的不一致。
         *
         * 语义（见 @blocknote/core 的 `commands/nestBlock/nestBlock.ts`）：
         * - `canNestBlock`  = 当前块**前面还有块**（可挂到前一块之下）→ **首块为 false**；
         * - `canUnnestBlock` = 当前块**嵌套深度 > 1**（已被缩进过）→ **顶层块为 false**。
         * 两者正是用户要的两条规则：「无法再向右缩进 → Nest 降权」、
         * 「无法再向左回退 → Unnest 降权」。
         *
         * ⚠️ 必须随 blockState 一起上行：宿主此前用的是 Compose 时代遗留的
         * `canIncreaseIndent / canDecreaseIndent`（读本地块对象的 indentLevel），
         * 而在 BlockNote 模式下真实层级只存在于 ProseMirror 文档树里，宿主那份
         * 数据恒为 1 → Unnest **永远置灰不可点**（v2026-09-22 修复的 bug）。
         */
        canNestBlock: ed.canNestBlock(),
        canUnnestBlock: ed.canUnnestBlock(),
        canSetBlockColor: supportsTextColor || supportsBgColor,
        blockTextColor: supportsTextColor
          ? (props.textColor as string | undefined)
          : undefined,
        blockBackgroundColor: supportsBgColor
          ? (props.backgroundColor as string | undefined)
          : undefined,
        canToggleHeader: isTable && !!ed.settings?.tables?.headers,
        isHeaderRow: isTable ? Boolean(content.headerRows) : false,
        isHeaderCol: isTable ? Boolean(content.headerCols) : false,
      };
      const key = JSON.stringify(payload);
      if (lastBlockStateRef.current === key) return;
      lastBlockStateRef.current = key;
      sendUp({ type: "blockState", ...payload });
    } catch (e: any) {
      const message = `blockState probe failed: ${e?.message ?? e}`;
      // 首次或错误内容变化时才上行，避免选区移动反复刷同一条
      if (lastBlockStateRef.current === message) return;
      lastBlockStateRef.current = message;
      sendUp({ type: "error", message });
    }
  }, []);

  /** 变更上行（防抖 800ms） */
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pushChanged = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    if (debounceRef.current) clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => {
      try {
        const md = blocksToMd(editor, editor.document);
        sendUp({ type: "changed", markdown: md });
      } catch (e: any) {
        sendUp({ type: "error", message: `changed: ${e.message}` });
      }
    }, 800);
  }, []);

  /**
   * 编辑器 DOM 焦点判定（v2026-09-22）
   *
   * 判据是 `document.activeElement` 是否落在 `.bn-editor` 内（编辑器容器本身带
   * `contenteditable`，子节点是各块的 DOM）。用它而不是 BlockNote 的编辑器 API：
   * 面板收起后宿主只需知道「编辑元素是否持有焦点」，与 ProseMirror 的选区无关。
   */
  const isEditorDomFocused = useCallback(() => {
    const el = document.activeElement as HTMLElement | null;
    if (!el || typeof el.closest !== "function") return false;
    return !!el.closest(".bn-editor") || el.getAttribute("contenteditable") === "true";
  }, []);

  /** 上一次上报的焦点态（去重：只在翻转时上行，v2026-09-22） */
  const lastEditorFocusRef = useRef<boolean | null>(null);

  /**
   * 上报编辑器焦点态（v2026-09-22）
   *
   * 用途：宿主收起「T / H / A」面板后，据此决定要不要把软键盘弹回来
   * （面板展开期间键盘被抑制，但光标一直在正文里）。
   *
   * 初值不上报：`null` 表示"未知"，宿主侧初值同为 false，语义一致；
   * 首次真实事件（用户点正文 / 失焦）才会产生上行。
   */
  const pushEditorFocus = useCallback(
    (focused: boolean) => {
      if (lastEditorFocusRef.current === focused) return;
      lastEditorFocusRef.current = focused;
      sendUp({ type: "editorFocus", focused });
    },
    []
  );

  /**
   * document 级焦点监听（v2026-09-22）
   *
   * 挂在 `document` 而非编辑器节点上：编辑器 DOM 由 BlockNote 动态挂载/局部重建，
   * 绑在具体节点上会随重建丢失；`focusin` / `focusout` 都会冒泡到 document，
   * 且 `focusout` 时 `document.activeElement` 尚未完成切换，故延后一拍再判定。
   *
   * 监听与编辑器挂载时机无关（挂载前绑好也能收到后续事件），因此放在本组件顶层、
   * `EditorCore` 之外——`booted` 之前也不影响。
   */
  useEffect(() => {
    const report = () => pushEditorFocus(isEditorDomFocused());
    /** focusout 时新焦点尚未生效，setTimeout(0) 等浏览器完成焦点转移后再判 */
    const onFocusOut = () => setTimeout(report, 0);
    document.addEventListener("focusin", report);
    document.addEventListener("focusout", onFocusOut);
    return () => {
      document.removeEventListener("focusin", report);
      document.removeEventListener("focusout", onFocusOut);
    };
  }, [pushEditorFocus, isEditorDomFocused]);

  // ---- Bridge 下行绑定 ----
  useEffect(() => {
    // 诊断日志：确认宿主桥是否存在（真机缺失 = ready 上行走不出去）
    // eslint-disable-next-line no-console
    console.log("[editor] mounted, AndroidBridge =", !!window.AndroidBridge);
    bindDown((msg) => {
      // eslint-disable-next-line no-console
      console.log("[editor] down:", msg.type);
      switch (msg.type) {
        case "init":
          setReadOnly(msg.readOnly);
          setTheme(msg.theme);
          setFontFamily(msg.fontFamily);
          setLatinFontId(msg.latinFontId ?? "");
          if (typeof (msg as any).baseFontSizePx === "number" && (msg as any).baseFontSizePx > 0) {
            setBaseFontSize((msg as any).baseFontSizePx);
            baseFontSizeRef.current = (msg as any).baseFontSizePx;
          }
          if (msg.fonts) {
            const map: Record<string, number[]> = {};
            for (const f of msg.fonts) map[f.id] = f.weights;
            setFontWeights(map);
          }
          setInitialMarkdown(msg.markdown);
          setBooted(true);
          break;
        case "setReadOnly":
          setReadOnly(msg.readOnly);
          break;
        case "setTheme":
          setTheme(msg.theme);
          break;
        case "setFontFamily":
          /** 字体链四点诊断（font | 临时埋点）已随 2026-09-21 验证通过移除；
           *  再排查时参考 docs/bridge-protocol.md 与 logcat console 转发。 */
          setFontFamily(msg.fontFamily);
          break;
        /** 英文/数字字体切换（v2026-09-21：拉丁回退层下行；空串 = 跟随中文） */
        case "setLatinFontFamily":
          setLatinFontId(msg.latinFontId);
          break;
        /**
         * 正文基础字号下行（v2026-09-21）：宿主在启动装载（SettingsViewModel 从
         * 偏好读出）与内存态变化时下发；编辑页组合期即下发一次（值相同幂等），
         * ready 前由桥缓存、ready 后随 init 补发——早于 EditorCore 挂载，无跳动。
         */
        case "setBaseFontSize": {
          const px = (msg as any).fontSizePx;
          if (typeof px === "number" && px > 0) {
            setBaseFontSize(px);
            baseFontSizeRef.current = px;
          }
          break;
        }
        /**
         * 编辑区最小高度（v1.11.6）：宿主下发 dp 值，写入 CSS 变量供 editor.css 消费。
         * 用法与 `SIDE_MENU_*` 无关的那套 CSS 变量一致——CSS 侧不写魔法数字。
         */
        case "setEditorMinHeight":
          setEditorMinHeight(msg.height);
          // v1.11.7 诊断：命令是否抵达 JS（与宿主 logcat 的 down(setEditorMinHeight) 配对排查）
          sendUp({
            type: "diagnostic",
            message: `setEditorMinHeight received: ${msg.height}`,
          });
          break;
        /**
         * 重新聚焦编辑器（v2026-09-22）
         *
         * 宿主收起「T / H / A」面板、要把键盘弹回来之前下发：Chromium 只在**编辑元素
         * 持有焦点**时才建立输入连接，宿主侧的 `showSoftInput` 也才有效。
         * 已聚焦时 `focus()` 为空操作，不会打断现有选区（抑制期间选区是保留的）。
         *
         * 找不到 `.bn-editor`（编辑器尚未挂载）时上行诊断而非静默——宿主据此
         * 知道"键盘没弹起来"的原因。
         */
        case "focusEditor": {
          const el = document.querySelector<HTMLElement>(".bn-editor");
          if (el) el.focus();
          sendUp({
            type: "diagnostic",
            message: `focusEditor: ${el ? "focused" : ".bn-editor missing"}`,
          });
          break;
        }
        case "requestSave":
          pushChanged();
          break;
        case "requestUndo":
          editorRef.current?.undo();
          // v1.7：撤销后立即回传可用态，宿主按钮同步置灰
          pushUndoState();
          break;
        case "requestRedo":
          editorRef.current?.redo();
          pushUndoState();
          break;
        case "insertImage": {
          // S11：本地路径 → file:// URL（converter.toWebImageUrl 语义），插入光标所在块之后
          const path = (msg as any).path as string;
          const ed = editorRef.current;
          if (ed && path) {
            const cursor = ed.getTextCursorPosition();
            ed.insertBlocks(
              [{ type: "image", props: { url: toWebImageUrl(path) } }],
              cursor.block,
              "after"
            );
          }
          break;
        }
        case "insertDivider": {
          const ed = editorRef.current;
          if (ed) {
            const cursor = ed.getTextCursorPosition();
            ed.insertBlocks([{ type: "divider", props: { style: "solid" } }], cursor.block, "after");
          }
          break;
        }
        case "insertVideo":
        case "insertAudio":
        case "insertFile": {
          // v1.5：媒体/文件块插入（本地路径 → file:// URL；播放/下载经 shouldInterceptRequest 流）
          const mediaPath = (msg as any).path as string;
          const mediaEd = editorRef.current;
          if (mediaEd && mediaPath) {
            const blockType = msg.type === "insertVideo" ? "video" : msg.type === "insertAudio" ? "audio" : "file";
            const cursor = mediaEd.getTextCursorPosition();
            mediaEd.insertBlocks(
              [
                {
                  type: blockType,
                  props: {
                    url: toWebImageUrl(mediaPath),
                    ...(blockType === "file"
                      ? { name: mediaPath.split("/").pop() ?? "file" }
                      : {}),
                  },
                },
              ],
              cursor.block,
              "after"
            );
          }
          break;
        }
        case "openEmojiPicker": {
          setEmojiOpen((v) => !v);
          break;
        }
        case "format": {
          // v1.4：底部格式工具栏桥接（作用于当前选区/光标块）
          const ed = editorRef.current;
          const action = (msg as any).action as string;
          const value = (msg as any).value as string | undefined;
          if (!ed || !action) break;
          switch (action) {
            /**
             * 四个基础行内样式（B / I / U / S）
             *
             * ⚠️ v2026-09-22：toggled 后**必须主动刷一次 blockState**。
             * 光标态（无文字选区）下 `toggleStyles` 只改 ProseMirror 的 **stored marks**，
             * **不产生文档变更** → `onChange` 不触发 → 上行不刷新 → 工具栏高亮纹丝不动
             * （表现为"点了加粗但 B 不高亮"）。主动调用一次即可覆盖该路径；
             * 有选区时本就会触发 onChange，此时重复调用被 `lastBlockStateRef` 去重吸收。
             */
            case "bold":
              ed.toggleStyles({ bold: true });
              pushBlockState();
              break;
            case "italic":
              ed.toggleStyles({ italic: true });
              pushBlockState();
              break;
            case "underline":
              ed.toggleStyles({ underline: true });
              pushBlockState();
              break;
            case "strike":
              ed.toggleStyles({ strike: true });
              pushBlockState();
              break;
            case "fontSize": {
              /**
               * 字号档位点选（v2026-09-21 用户决策升级为双语义）：
               * - **有文字选区** → 行内样式（仅选中文字）："18px" / "default" = 清除回落
               * - **无选区（光标态）** → **全局正文字号**：改基础字号（CSS 变量驱动，
               *   作用于未叠加行内样式的全部文字），上行 baseFontSize 让宿主持久化，
               *   并主动刷一次 blockState（基础字号不产生文档变更，需手动触发回显）
               */
              const pm = ed.prosemirrorView;
              const hasSelection = pm ? !pm.state.selection.empty : true;
              if (!hasSelection) {
                const px =
                  value && value !== "default" ? parseInt(value, 10) : 16;
                const next = Number.isFinite(px) ? px : 16;
                setBaseFontSize(next);
                baseFontSizeRef.current = next;
                sendUp({ type: "baseFontSize", fontSizePx: next });
                pushBlockState();
              } else {
                if (value && value !== "default") ed.addStyles({ fontSize: value });
                else ed.removeStyles({ fontSize: "16px" });
              }
              break;
            }
            case "textColor":
              // value = "#rrggbb"（自由值）或 "default"=清除
              if (value && value !== "default") ed.addStyles({ textColor: value });
              else ed.removeStyles({ textColor: "default" });
              break;
            case "backgroundColor":
              // v1.6：背景色（ColorStyleButton 同款能力；default=清除）
              if (value && value !== "default") ed.addStyles({ backgroundColor: value });
              else ed.removeStyles({ backgroundColor: "default" });
              break;
            case "bulletList":
              toggleBlockType(ed, "bulletListItem");
              break;
            case "numberedList":
              toggleBlockType(ed, "numberedListItem");
              break;
            case "checkList":
              toggleBlockType(ed, "checkListItem");
              break;
            case "indent":
              ed.nestBlock();
              break;
            case "outdent":
              ed.unnestBlock();
              break;
            case "createLink":
              // v1.6：底部链接按钮 → 当前选区加链接（BlockNote 公开 API createLink）
              if (value) ed.createLink(value);
              break;
            case "alignLeft":
            case "alignCenter":
            case "alignRight": {
              // 对齐 = 光标块 textAlignment prop（P1：当前块级；跨多块选区转换留 P2）
              const alignMap: Record<string, string> = {
                alignLeft: "left",
                alignCenter: "center",
                alignRight: "right",
              };
              const { block } = ed.getTextCursorPosition();
              ed.updateBlock(block, {
                props: { textAlignment: alignMap[action] },
              } as any);
              break;
            }
            case "codeSpan":
              ed.toggleStyles({ code: true });
              break;
            case "transform":
              // S10 补充：块类型转换/插入（+ 菜单同款能力进工具栏）
              switch (value) {
                /**
                 * 普通标题 H1–H6
                 *
                 * ⚠️ v2026-09-22 修复：原分支只声明 heading1–3，于是宿主标题面板
                 * 「普通标题」的 H4/H5/H6 下发 `heading4`–`heading6` 后**落不进任何 case**，
                 * 内层 switch 又无 default → 整个操作被静默丢弃（不报错、无上行诊断），
                 * 真机表现为"点了没反应"。该缺陷自旧工具栏时代就存在（旧
                 * RichTextFormatToolbar 的 RiH4–RiH6 发的是同样的 action），并非面板迁移引入。
                 *
                 * 六级齐备的依据：@blocknote/core 的 heading spec 取
                 * `HEADING_LEVELS = [1,2,3,4,5,6]` 作 `level` 的合法值域，渲染侧
                 * `createElement("h" + level)` 不分级，CSS 也备有 1em/.9em/.8em 三档，
                 * 故 JS 侧补齐分支后 H4–H6 即可正常渲染（下方 `Number(value.slice(-1))`
                 * 本就按尾字符取级别，无需额外改动）。
                 */
                case "heading1":
                case "heading2":
                case "heading3":
                case "heading4":
                case "heading5":
                case "heading6": {
                  const level = Number(value.slice(-1));
                  const { block } = ed.getTextCursorPosition();
                  const targetType =
                    block.type === "heading" &&
                    (block.props as any).level === level
                      ? "paragraph"
                      : "heading";
                  ed.updateBlock(block, {
                    type: targetType,
                    props: { level },
                  } as any);
                  break;
                }
                /**
                 * 可折叠标题 H1–H3
                 *
                 * ⚠️ v2026-09-22 修复：原实现把 `toggleHeading` / `toggleHeading2` /
                 * `toggleHeading3` 当成**独立块类型**传给 `updateBlock`，但 BlockNote
                 * **没有这些类型** —— 折叠标题的真实模型是 `heading` 块 + `props.isToggleable`，
                 * 见官方斜杠菜单 `getDefaultSlashMenuItems.ts`：
                 *   `insertOrUpdateBlockForSlashMenu(editor, { type: "heading",
                 *      props: { level, isToggleable: true } })`
                 * 也见 heading block spec（`allowToggleHeadings` 开启时 propSchema 才含
                 * `isToggleable`）。而本项目 schema 的合法块类型里根本查不到
                 * `toggleHeading*`（见 defaultBlocks.ts 的 defaultBlockSpecs），于是
                 * `blockToNode` 执行 `schema.nodes["toggleHeading2"].isInGroup(...)` 时
                 * 因 `undefined` 抛 TypeError → 操作完全没发生（"点了没反应"）。
                 *
                 * 级别解析：1 级写成 `toggleHeading`（**没有**尾数字，不能对尾字符取 Number，
                 * 否则得到 NaN），2/3 级才是 `toggleHeading2/3`。
                 *
                 * toggle 语义与「普通标题」保持一致：当前块已是**同级别的可折叠标题**时
                 * 再点一次 → 退回普通段落；否则（含"当前是普通标题"）→ 转为可折叠标题。
                 */
                case "toggleHeading":
                case "toggleHeading2":
                case "toggleHeading3": {
                  const level =
                    value === "toggleHeading" ? 1 : Number(value.slice(-1));
                  const { block } = ed.getTextCursorPosition();
                  const curProps = (block.props ?? {}) as Record<string, unknown>;
                  const already =
                    block.type === "heading" &&
                    curProps.level === level &&
                    curProps.isToggleable === true;
                  ed.updateBlock(block, {
                    type: already ? "paragraph" : "heading",
                    props: { level, isToggleable: !already },
                  } as any);
                  break;
                }
                /**
                 * 可折叠列表（v2026-09-22 修复）
                 *
                 * 原实现用的 `toggleList` **同样不是合法块类型**：BlockNote 的折叠列表
                 * 类型名是 `toggleListItem`（见 defaultBlocks.ts 的 defaultBlockSpecs），
                 * 于是「折叠列表」按钮与可折叠标题一样是死的。列表项的折叠态由
                 * `props.isCollapsed` 承担，不在 type 上。
                 */
                case "toggleList": {
                  const { block } = ed.getTextCursorPosition();
                  const targetType =
                    block.type === "toggleListItem" ? "paragraph" : "toggleListItem";
                  ed.updateBlock(block, { type: targetType } as any);
                  break;
                }
                case "quote": {
                  const { block } = ed.getTextCursorPosition();
                  const targetType = block.type === "quote" ? "paragraph" : "quote";
                  ed.updateBlock(block, { type: targetType } as any);
                  break;
                }
                case "paragraph": {
                  // + 菜单 Paragraph：光标块转普通段落（toggle 语义）
                  const { block } = ed.getTextCursorPosition();
                  ed.updateBlock(block, { type: "paragraph" } as any);
                  break;
                }
                case "codeBlock": {
                  const { block } = ed.getTextCursorPosition();
                  const targetType =
                    block.type === "codeBlock" ? "paragraph" : "codeBlock";
                  ed.updateBlock(block, { type: targetType } as any);
                  break;
                }
                case "table":
                  ed.insertBlocks(
                    [
                      {
                        type: "table",
                        content: {
                          type: "tableContent",
                          rows: [{ cells: ["", "", ""] }, { cells: ["", "", ""] }],
                        },
                      },
                    ],
                    ed.getTextCursorPosition().block,
                    "after"
                  );
                  break;
                case "pageBreak":
                  ed.insertBlocks(
                    [{ type: "pageBreak" }],
                    ed.getTextCursorPosition().block,
                    "after"
                  );
                  break;
                /**
                 * 未知 transform 值的兜底诊断（v2026-09-22 新增）
                 *
                 * 本分支的直接由来就是 heading4–6 曾在此静默消失——内层 switch 无 default 时，
                 * 任何拼错/漏声明的 action 都会表现为"点了完全没反应"，且不产生任何日志，
                 * 只能靠逐行读源码排查。现改为上行 `error`（宿主 BlockNoteEditorWebView
                 * 的 "error" 分支会打 logcat：`js error: ...`），使同类问题一次可诊。
                 */
                default:
                  sendUp({
                    type: "error",
                    message: `transform: unknown value "${value}" (ignored)`,
                  });
                  break;
              }
              break;
          }
          break;
        }

        /**
         * 删除块（v1.11）：原 ⋮⋮ 手柄点击菜单的「删除」项，移入宿主工具栏。
         *
         * 命中口径与官方 `RemoveBlockItem` 完全一致：若当前**选区**包含光标块，
         * 则删除选区内的全部块（支持多选一起删）；否则只删光标那一块。
         * 这样宿主无需关心用户选了几个块。
         */
        case "deleteBlock": {
          const ed = editorRef.current;
          if (!ed) break;
          try {
            const cur = ed.getTextCursorPosition();
            const selected = ed.getSelection()?.blocks as any[] | undefined;
            const targets =
              selected && selected.some((b) => b.id === cur.block.id)
                ? selected
                : [cur.block];
            ed.removeBlocks(targets);
          } catch (e: any) {
            sendUp({ type: "error", message: `deleteBlock: ${e.message}` });
          }
          break;
        }

        /**
         * 块级颜色（v1.11）：原 ⋮⋮ 手柄点击菜单的「颜色」项。
         *
         * ⚠️ 与 `format` 的 `textColor` 是不同维度：本条走 `updateBlock` 写
         * **块 props**，作用于整个块；后者走 `addStyles` 写**行内 span 样式**，
         * 只作用于选区内的文字。两者可同时存在、互不覆盖。
         *
         * 传 `"default"` 表示清除（与 BlockNote 预设色语义一致）。
         */
        case "setBlockColor": {
          const ed = editorRef.current;
          if (!ed) break;
          try {
            const { block } = ed.getTextCursorPosition();
            const props: Record<string, string> = {};
            if (msg.textColor !== undefined) props.textColor = msg.textColor;
            if (msg.backgroundColor !== undefined) {
              props.backgroundColor = msg.backgroundColor;
            }
            if (Object.keys(props).length === 0) break;
            ed.updateBlock(block, { props } as any);
          } catch (e: any) {
            sendUp({ type: "error", message: `setBlockColor: ${e.message}` });
          }
          break;
        }

        /**
         * 表头行 / 表头列（v1.11）：原 ⋮⋮ 手柄点击菜单的「表头行 / 表头列」项。
         *
         * 官方 TODO 注明目前只支持 1 行 / 1 列，故桥协议用布尔开关
         * （enabled → 1，disabled → undefined）。
         * 非表格块静默忽略——宿主已按上行的 `canToggleHeader` 置灰，正常不会走到这里。
         */
        case "setTableHeader": {
          const ed = editorRef.current;
          if (!ed) break;
          try {
            const { block } = ed.getTextCursorPosition();
            if (block.type !== "table") break;
            const content = (block.content ?? {}) as Record<string, unknown>;
            const next =
              msg.target === "row"
                ? { headerRows: msg.enabled ? 1 : undefined }
                : { headerCols: msg.enabled ? 1 : undefined };
            ed.updateBlock(block, { content: { ...content, ...next } } as any);
          } catch (e: any) {
            sendUp({ type: "error", message: `setTableHeader: ${e.message}` });
          }
          break;
        }

        /**
         * 块上移 / 下移（v1.11.5）
         *
         * **替代原 ⋮⋮ 手柄的拖拽重排**。该手柄的拖拽依赖 HTML5 原生 Drag & Drop，
         * 在 Android WebView / iOS Safari 的触摸下不触发（W3C 把 drag 事件定义为
         * 鼠标驱动），故手柄整体删除，块移动改用这套程序化 API。
         *
         * ⚠️ 无需自己判断边界：BlockNote 内部用 `getMoveUpPlacement` /
         * `getMoveDownPlacement` 求目标位置，**到顶 / 到底时返回 undefined 直接 return**
         * （安全 no-op，不会抛错）。正因如此宿主侧也没法预知能否移动，
         * 工具栏不再对这两项做置灰（点了没反应即已在边界）。
         *
         * ⚠️ 也无需传参：不传 `blockIdentifier` 时会取**选区首块 / 末块**或**光标块**，
         * 因此天然支持「多选块一起移动」与嵌套块（内部会处理 parentBlock）。
         */
        case "moveBlockUp": {
          const ed = editorRef.current;
          if (!ed) break;
          try {
            ed.moveBlocksUp();
          } catch (e: any) {
            sendUp({ type: "error", message: `moveBlockUp: ${e.message}` });
          }
          break;
        }
        case "moveBlockDown": {
          const ed = editorRef.current;
          if (!ed) break;
          try {
            ed.moveBlocksDown();
          } catch (e: any) {
            sendUp({ type: "error", message: `moveBlockDown: ${e.message}` });
          }
          break;
        }
      }
    });
    // v1.8：ready 带上构建指纹，宿主打进 logcat，便于确认 WebView 加载的产物版本
    sendUp({ type: "ready", build: BUILD_FINGERPRINT });
    return () => {
      window.BlockNoteEditorHost = undefined;
    };
  }, [pushChanged, pushUndoState, getHistoryCommands]);

  // ---- booted 后解析 markdown（完成才挂编辑器核心） ----
  useEffect(() => {
    if (!booted) return;
    let cancelled = false;
    (async () => {
      try {
        const blocks = await mdToBlocks(getMdLoader(), initialMarkdown);
        if (!cancelled) setInitialBlocks(blocks);
      } catch (e: any) {
        if (!cancelled) {
          setLoadError(`解析正文失败：${e.message}`);
          sendUp({ type: "error", message: `mdToBlocks: ${e.message}` });
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [booted, initialMarkdown]);

  if (loadError) {
    return <div className="editor-loading">{loadError}</div>;
  }
  if (!booted) {
    return <div className="editor-loading">正在装载…</div>;
  }
  if (initialBlocks == null) {
    return <div className="editor-loading">正在解析正文…</div>;
  }

  return (
    <EditorCore
      initialBlocks={initialBlocks}
      readOnly={readOnly}
      theme={theme}
      fontFamily={fontFamily}
      latinFontId={latinFontId}
      baseFontSize={baseFontSize}
      fontWeights={fontWeights}
      minHeight={editorMinHeight}
      onReady={(editor) => {
        editorRef.current = editor;
      }}
      onChange={pushChanged}
      onUndoStateChange={pushUndoState}
      onBlockStateChange={pushBlockState}
      emojiOpen={emojiOpen}
      onEmojiClose={() => setEmojiOpen(false)}
      onEmojiPick={(emoji) => {
        editorRef.current?.insertInlineContent([
          { type: "text", text: emoji, styles: {} },
        ]);
      }}
    />
  );
}

/**
 * 内容区左右留白（px，v1.11 → v1.11.5 定为 20）
 *
 * 是 `.bn-editor` 的 `padding-inline` 值，经 CSS 变量 `--bn-editor-gutter` 注入
 * （CSS 侧不写魔法数字）。**左右同值**，保证文本两侧到屏幕的距离一致。
 *
 * 这个 20px 是**下限**而非随手取的值：嵌套列表的竖向缩进线位于
 * `left: -20px`（Block.css），toggle 块的添加按钮另有 `margin-left: 22px`——
 * 左侧留白小于 20px 时那条缩进线会被裁掉（表现为嵌套列表左侧竖线消失）。
 *
 * ⚠️ **历史沿革（别把结论看反）**：
 * - v1.11 时该值是 **24px**，因为左侧要**容纳一个 24px 宽的拖拽手柄**
 *   （手柄左边缘 = padding-left − 手柄宽，padding 小于手柄宽就会把手柄挤出视口）；
 * - v1.11.5 起**手柄已整体删除**。原因是实测确认：BlockNote 的块拖拽纯用
 *   **HTML5 原生 Drag & Drop**（`SideMenu.ts` 只有 dragstart/dragover/drop/dragend，
 *   零 touch 处理），而该 API 在 **Android WebView / iOS Safari 的触摸下根本不触发**
 *   （W3C 把 drag 事件定义为鼠标驱动行为）——手柄注定拖不动，留着只会让人
 *   "按住没反应"。块移动改由工具栏的「上移 / 下移」承担，走程序化
 *   `editor.moveBlocksUp()/moveBlocksDown()`。
 *   于是左侧不再需要 24px，回落到本下限 20px。
 */
const EDITOR_CONTENT_GUTTER = 20;

/** 编辑器核心（initialBlocks 就绪后挂载，useCreateBlockNote 仅执行一次） */
function EditorCore(props: {
  initialBlocks: any[];
  readOnly: boolean;
  theme: ThemePayload;
  fontFamily: string;
  /** 英文/数字字体 id（v2026-09-21：拉丁回退层；空串 = 跟随中文） */
  latinFontId: string;
  /** 正文基础字号（v2026-09-21：无选区点「正文字号」改全局；CSS 变量消费） */
  baseFontSize: number;
  fontWeights: Record<string, number[]>;
  /** 编辑区最小高度（dp，v1.11.6）：由宿主下发，写入 `--bn-editor-min-height` */
  minHeight: number;
  emojiOpen: boolean;
  onEmojiClose: () => void;
  onEmojiPick: (emoji: string) => void;
  onReady: (editor: any) => void;
  onChange: () => void;
  /** v1.7：撤销/重做可用态上报（宿主左上角按钮置灰用） */
  onUndoStateChange: () => void;
  /** v1.11：当前光标块状态上报（宿主工具栏的删除/块颜色/表头按钮置灰与回显用） */
  onBlockStateChange: () => void;
}) {
  // @font-face 注入（S5）
  useEffect(() => {
    applyFontFaces(props.fontWeights);
  }, [props.fontWeights]);

  /**
   * 编辑区背景色（v1.9）
   *
   * 宿主下发的 `theme.background` 优先；缺省（旧版宿主未下发 / 探针页）时按深浅回落
   * ——亮色 white、暗色 #1f1f1f，即 BlockNote 自身的默认值，保持向后兼容。
   */
  const editorBackground =
    props.theme.background ?? (props.theme.dark ? "#1f1f1f" : "#ffffff");

  /**
   * 应用编辑区背景色（v1.9 引入 → v1.11.4 修复）
   *
   * 目标是让 `.bn-editor` 的背景与宿主主题背景一致（暖米色），消除"白底画中画"。
   * 同一背景色写入三处：
   * ① `--bn-colors-editor-background`：BlockNote 官方变量，`.bn-editor` 直接消费
   * ② `html` / `body` 的 `background-color`：WebView 自身底色，未铺到的地方不留白
   * ③ 外层 `.editor-page` 容器：由内联 style 的 `--editor-bg` 驱动（见 editor.css）
   *
   * ⚠️⚠️ v1.9 的 bug：① 被写到了 `<html>` 上，但官方在 `.bn-root` 里定义过同名变量
   *（亮色 `#fff` / 暗色 `#1f1f1f`）。**CSS 变量就近取值**——`.bn-root` 离 `.bn-editor`
   * 更近，其默认值直接盖过 `<html>` 上的值，于是 `.bn-editor` 始终是纯白。
   * 现改为写到**所有 `.bn-root` 元素**上，并用 `"important"` 优先级
   *（editor.css 里也有同值声明作兜底；两者同值时以 inline 的 important 为准）。
   *
   * ⚠️ 验证方式提醒：此问题**用「产物关键字计数」查不出来**（产物里一直含 #FFFBF5），
   * 必须在真机/浏览器里读 `.bn-editor` 的 computed background-color 才算验过。
   *
   * 依赖 [editorBackground]，主题（含背景色）变化时重新应用。
   */
  useEffect(() => {
    document.documentElement.style.backgroundColor = editorBackground;
    document.body.style.backgroundColor = editorBackground;
    document.body.style.margin = "0";

    /**
     * 官方变量的**真值位置**是 `.bn-root`（BlockNoteView 渲染的容器，可能有多个：
     * 外层 `.bn-container.bn-root` 与 portalElement 都带这个类）。
     * 逐个写入而不是只写 `<html>`，正是 v1.11.4 修复的核心。
     * BlockNoteView 是子组件，其 DOM 在本 effect 执行前已提交，故此处能查到。
     */
    document.querySelectorAll<HTMLElement>(".bn-root").forEach((el) => {
      el.style.setProperty("--bn-colors-editor-background", editorBackground, "important");
    });
  }, [editorBackground]);

  const editor = useCreateBlockNote({
    schema: editorSchema as any,
    initialContent: props.initialBlocks,
    editable: !props.readOnly,
  });

    /**
     * v1.11.7 诊断：回传编辑区的真实几何，用于排查"点击正文下方空白不聚焦"。
     *
     * 同时上报三个值，任一环出问题都能一眼定位：
     * - `prop`：宿主下发、经 React 传到 CSS 变量的值
     * - `computed`：浏览器实际解析出的 `min-height`（CSS 变量没注入时这里会是 0px）
     * - `offsetH`：元素实际渲染高度（min-height 没生效时这里仍是内容高 ≈ 30）
     */
    useEffect(() => {
      const el = document.querySelector(".bn-editor") as HTMLElement | null;
      sendUp({
        type: "diagnostic",
        message:
          `editor geom | prop=${props.minHeight}px` +
          ` computedMinHeight=${el ? getComputedStyle(el).minHeight : "n/a"}` +
          ` offsetH=${el?.offsetHeight ?? -1}`,
      });
    }, [props.minHeight]);

    /**
     * WebView 高度变化后，用 ProseMirror 的一等 API 保证选区可见（v1.11.9）。
     *
     * **根因链路**（真机日志 + 源码确证）：
     * 1. `enableEdgeToEdge` 下 `adjustResize` 不再 resize 窗口——ime 只以 insets
     *    形式派发给应用，**Chromium 层永远收不到"键盘导致窗口 resize"的信号**；
     * 2. 键盘弹出时 BottomBar 经 `imePadding()` 抬起并占据键盘位 → content 区域
     *    收缩 → WebView（weight）高度随之变化（实测 620→332dp）；
     * 3. Android WebView 对**应用布局驱动**的 View 尺寸变化只做"保持 scrollY"，
     *    它的「滚动到聚焦输入框」逻辑仅由系统 ime/resize 信号触发（见 1，收不到）
     *    ——于是光标落进被键盘遮住的下沿区，无人负责把它滚回来。
     *
     * **修法**：WebView 高度变化（`visualViewport` resize 与之同源同刻）后，
     * 由编辑器自己执行 `tr.scrollIntoView()`——这是 ProseMirror 为"保证选区可见"
     * 提供的一等 API（与打字时的自动滚动同一机制），非 DOM 层面的补丁。
     * 动画期间连续触发无害：选区已可见时 PM 不产生滚动。
     */
    useEffect(() => {
      /**
       * v1.11.9 诊断（**临时**，定位"滑到底 vs 滑到中间的滚动差异"后移除）：
       * 逐帧记录光标可见性状态——
       * - innerH：WebView 视口高（收缩过程）
       * - scrollY：内容滚动位置（滚动跟随曲线）
       * - caretVY：光标视口 y（越过 innerH 的时刻 = 出界时机）
       * - caretCY：光标内容 y（光标深度，两场景的固有差异）
       * 两条时间线（滑到底 / 滑到中间）并排对比即可定位差异环节。
       */
      const report = (tag: string) => {
        const view = editor.prosemirrorView;
        const doc = document.documentElement;
        let caretVY = -1;
        try {
          if (view) {
            caretVY = Math.round(view.coordsAtPos(view.state.selection.from).top);
          }
        } catch {
          /* 编辑器销毁等场景忽略 */
        }
        const scrollY = Math.round(doc.scrollTop);
        sendUp({
          type: "diagnostic",
          message:
            `caret[${tag}] innerH=${window.innerHeight}` +
            ` scrollY=${scrollY} caretVY=${caretVY}` +
            ` caretCY=${caretVY < 0 ? -1 : caretVY + scrollY}` +
            ` visible=${caretVY >= 0 && caretVY <= window.innerHeight}`,
        });
      };
      /**
       * rAF 指数逼近（v1.11.9 定案）：每帧向「光标可见」的目标位置走 30%。
       *
       * 两轮真机日志已否决的方案：
       * - PM scrollIntoView 瞬时逐帧执行：20 来次小瞬跳，断续 ❌
       * - CSS scroll-behavior: smooth（固定 ~500ms 动画）：resize 逐帧触发时
       *   每帧都重启动画——动画从未跑完即被取消，**收敛不可预期**
       *   （实测：滑到底侥幸 +277dp 到位；滑到中间只滚了 11% 即停，光标不可见）❌
       *
       * 指数逼近无固定时长：目标（随 innerH 逐帧变化）每帧重算、位置逐帧
       * 收敛，键盘动画结束事件流停止后循环仍会跑到位。收敛即退出，
       * 下次 resize 再 kick。双向：键盘收起时目标回落、平滑恢复。
       */
      let raf = 0;
      let running = false;
      let prevDiff = 0;
      const step = () => {
        const view = editor.prosemirrorView;
        const doc = document.documentElement;
        if (!view || view.isDestroyed) {
          running = false;
          return;
        }
        try {
          const caretTop = view.coordsAtPos(view.state.selection.from).top;
          const vh = window.innerHeight;
          const margin = 24; // 光标贴视口底部时，上方预留约一行
          /**
           * ⚠️ 目标必须是「可见性 clamp」而非「无条件贴底」（v1.11.9 修复）：
           * 贴底公式 `caretTop + scrollTop - vh + margin` 在光标位于视口
           * 中上部时目标会**小于**当前 scrollY → 内容先被往下滚（光标下移
           * 贴底），innerH 收缩后才反转为上滚——正是「先往下再往上」的来源。
           * 正确语义与 PM scrollIntoView 一致：光标可见则不动，出界才滚。
           */
          let target = doc.scrollTop; // 默认：光标可见，不动
          if (caretTop > vh - margin) {
            // 出界下方：上滚至光标贴视口底部
            target = Math.round(caretTop + doc.scrollTop - vh + margin);
          } else if (caretTop < 0) {
            // 出界上方：下滚至光标贴视口顶部
            target = Math.round(caretTop + doc.scrollTop);
          }
          const diff = target - doc.scrollTop;
          /**
           * 诊断（临时）：滚动方向翻转 = rAF 自身振荡的直接证据。
           * 门限 |diff|>8 过滤收敛末尾 ±1 的数值抖动。
           */
          if (
            prevDiff !== 0 &&
            Math.sign(diff) !== Math.sign(prevDiff) &&
            Math.abs(diff) > 8
          ) {
            report("flip");
          }
          prevDiff = diff;
          if (Math.abs(diff) <= 1) {
            doc.scrollTop = target;
            running = false;
            report("converged");
            return;
          }
          doc.scrollTop += Math.round(diff * 0.3);
          raf = requestAnimationFrame(step);
        } catch {
          running = false;
        }
      };
      const kickScrollFollow = () => {
        if (!running) {
          running = true;
          step();
        }
      };
      report("initial");
      const onResize = () => kickScrollFollow();
      const vv = window.visualViewport;
      vv?.addEventListener("resize", onResize);
      window.addEventListener("resize", onResize);
      /** 键盘动画稳定后补一条终态 */
      const settleTimer = window.setTimeout(() => report("settled"), 1500);
      /**
       * 诊断（临时）：区分滚动来源，定位「滑动时页面上下反复跳跃」。
       * - touching=true 时段内的 scroll = 用户手势
       * - touching=false 且 rafRunning=false 的 scroll = **WebView 自动滚回
       *   聚焦光标**的实锤（既非用户、也非我们的 rAF）
       * - rafRunning=true 的 scroll = 我们 rAF 写入（正常跟随）
       */
      let touching = false;
      let lastTouchEnd = 0;
      let lastScrollLog = 0;
      const onTouchStart = () => {
        touching = true;
      };
      const onTouchEnd = () => {
        touching = false;
        lastTouchEnd = performance.now();
      };
      const onDocScroll = () => {
        const now = performance.now();
        /**
         * v1.11.9 防御性校准：非手势、非 rAF 运行、且越过手势静默窗（500ms）
         * 的滚动 → kick rAF 做一次「光标可见性」校准。
         * - 目标是 clamp 语义：光标可见则收敛退出（**零动作**）——WebView 若
         *   因「聚焦输入框保持可见」自动滚回，我们校准时通常已是可见态，
         *   目标一致不产生拉锯
         * - 手势与惯性滚动（touching=true，或 touchend 后 500ms 内）不校准，
         *   用户的阅读滚动不被干扰
         */
        if (!running && !touching && now - lastTouchEnd > 500) {
          kickScrollFollow();
        }
        if (now - lastScrollLog < 40) return; // 节流（仅限埋点上报）
        lastScrollLog = now;
        sendUp({
          type: "diagnostic",
          message:
            `scrollObserved[top=${Math.round(document.documentElement.scrollTop)}` +
            ` rafRunning=${running} touching=${touching}` +
            ` sinceTouchEnd=${Math.round(now - lastTouchEnd)}]`,
        });
      };
      document.addEventListener("touchstart", onTouchStart, { passive: true });
      document.addEventListener("touchend", onTouchEnd, { passive: true });
      document.documentElement.addEventListener("scroll", onDocScroll, {
        passive: true,
      });
      return () => {
        window.clearTimeout(settleTimer);
        document.removeEventListener("touchstart", onTouchStart);
        document.removeEventListener("touchend", onTouchEnd);
        document.documentElement.removeEventListener("scroll", onDocScroll);
        cancelAnimationFrame(raf);
        running = false;
        vv?.removeEventListener("resize", onResize);
        window.removeEventListener("resize", onResize);
      };
    }, []);

    /**
     * v1.11.9 诊断（**临时**，定位"键盘弹出 WebView 不收缩"后移除）：
     * 监听三处高度变化并上报——
     * - `bnEditor`：`.bn-editor` 实际高度（min-height 生效与否的直接证据）
     * - `innerHeight`：WebView 视口高（若窗口被 resize 会跟着变）
     * - `visualViewport.height`：**关键指标**——Android 上键盘弹出且系统真正 resize
     *   时它会变小；保持不变则说明窗口没被 resize（edge-to-edge 下 ime 需应用自行消费）
     */
    useEffect(() => {
      const el = document.querySelector(".bn-editor") as HTMLElement | null;
      if (!el) return;
      const report = () => {
        sendUp({
          type: "diagnostic",
          message:
            `viewport | bnEditor=${el.offsetHeight}px` +
            ` innerHeight=${window.innerHeight}` +
            ` visualViewportH=${window.visualViewport?.height ?? -1}`,
        });
      };
      const ro = new ResizeObserver(report);
      ro.observe(el);
      const vv = window.visualViewport;
      vv?.addEventListener("resize", report);
      window.addEventListener("resize", report);
      report();
      return () => {
        ro.disconnect();
        vv?.removeEventListener("resize", report);
        window.removeEventListener("resize", report);
      };
    }, []);

    useEffect(() => {
      props.onReady(editor);
      // 初次挂载后上报一次可用态（初始内容装载本身不产生可撤销历史，通常为 false/false）
      props.onUndoStateChange();
    // v1.11：同时上报一次当前块状态，避免宿主工具栏的按钮在首次点击前处于无状态
    props.onBlockStateChange();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editor]);

  /**
   * 字体栈组装（v2026-09-21 升级为三层回退链）：
   * - 拉丁层：`ff-<latinId>`（**在前**——拉丁字形优先走英文/数字字体，与 Compose 侧
   *   combinedFamily 的「拉丁主体 + 中文兜底」语义一致）；空串 = 未选，不叠加
   * - 中文层：`ff-<fontId>`；系统默认时为 system-ui（无内置 @font-face）
   * - 兜底：system-ui
   * 各字体族的 @font-face 由 applyFontFaces 按 init fonts 清单生成（清单已含拉丁）。
   */
  const fontStack = [
    props.latinFontId ? `"ff-${props.latinFontId}"` : null,
    props.fontFamily === "system_default" ? null : `"ff-${props.fontFamily}"`,
    "system-ui",
  ]
    .filter(Boolean)
    .join(", ");

  return (
    <div
      className="editor-page"
      style={{
        ["--content-font" as any]: fontStack,
        // 正文基础字号（v2026-09-21：无选区点「正文字号」改全局；editor.css 的
        // .bn-default-styles/.bn-editor 消费——行内 fontSize 样式仍可按文字覆盖）
        ["--bn-editor-base-font-size" as any]: `${props.baseFontSize}px`,
        ["--editor-primary" as any]: props.theme.primary,
        ["--editor-bg" as any]: editorBackground,
        ["--editor-fg" as any]: props.theme.dark ? "#e0e0e0" : "#333333",
        ["--editor-border" as any]: props.theme.dark ? "#444444" : "#cccccc",
        // 内容区左右留白（v1.11.5：左右同值 20px，见 EDITOR_CONTENT_GUTTER）；editor.css 的 .bn-editor 消费
        ["--bn-editor-gutter" as any]: `${EDITOR_CONTENT_GUTTER}px`,
        // 编辑区最小高度（v1.11.6，宿主下发 dp）；让 .bn-editor 铺满 WebView，消除点击死区
        ["--bn-editor-min-height" as any]: `${props.minHeight}px`,
      }}
    >
      <div style={{ fontFamily: "var(--content-font, system-ui)" }}>
        <BlockNoteView
          editor={editor}
          theme={props.theme.dark ? "dark" : "light"}
          formattingToolbar={false}
          /**
           * 关闭官方默认侧边菜单 —— 即「+ / ⋮⋮」两个块手柄（v1.11 起）
           *
           * 本项目不再渲染侧边菜单：
           * - `+` 手柄的功能（插入媒体/分割线/emoji/块类型转换）早已桥接到宿主工具栏；
           * - `⋮⋮` 手柄的点击菜单（删除块/块颜色/表头行列）也已桥接到工具栏，
           *   而它唯一的自有功能「拖拽重排」——实测确认**在移动端无法工作**
           *   （BlockNote 纯用 HTML5 原生 Drag & Drop，该 API 在 Android WebView /
           *   iOS Safari 触摸下不触发），故 v1.11.5 整体删除。块移动改由工具栏的
           *   「上移 / 下移」承担（`editor.moveBlocksUp/Down()`）。
           */
          sideMenu={false}
          onChange={() => {
            // v1.7：内容变更既推 markdown 快照，也刷新撤销/重做可用态
            props.onChange();
            props.onUndoStateChange();
            // v1.11：块类型可能因 markdown 前缀输入而改变（如 "# " → heading），需刷新块状态
            props.onBlockStateChange();
          }}
          /**
           * 选区变化 → 上报当前块状态（v1.11）
           *
           * 光标在不同块之间移动时，工具栏的「块颜色 / 表头」按钮可用态与回显需要跟着变。
           * 上报函数内部按 JSON 串去重，同块内移动光标不会产生上行流量。
           */
          onSelectionChange={() => props.onBlockStateChange()}
        >
          <FormattingToolbarController
            formattingToolbar={() => (
              <>
                <FormattingToolbar />
                <FontSizeButton />
                <TextColorButton />
              </>
            )}
          />
        </BlockNoteView>
      </div>
      {props.emojiOpen && (
        <EmojiGridPanel
          onPick={(e) => {
            props.onEmojiPick(e);
            props.onEmojiClose();
          }}
          onClose={props.onEmojiClose}
        />
      )}
    </div>
  );
}

/** 常用表情（v1.5 emoji 面板：两行 24 个高频表情，点击插入光标处） */
const EMOJIS = [
  "😀", "😄", "😂", "🤣", "😊", "😍", "🤔", "😎",
  "😭", "😡", "👍", "👎", "👏", "🙏", "💪", "🔥",
  "❤️", "💚", "💙", "⭐", "🌟", "✨", "💡", "📌",
  "✅", "❌", "⚠️", "❓", "❗", "💯", "🎯", "🚀",
];

/** 表情选择网格（v1.5 Emoji 桥接：点击插入光标处） */
function EmojiGridPanel(props: { onPick: (emoji: string) => void; onClose: () => void }) {
  useEffect(() => {
    const onDocClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (!target.closest(".emoji-panel")) props.onClose();
    };
    const t = setTimeout(() => document.addEventListener("click", onDocClick, true), 0);
    return () => {
      clearTimeout(t);
      document.removeEventListener("click", onDocClick, true);
    };
  });
  return (
    <div
      className="emoji-panel"
      style={{
        position: "fixed",
        left: "50%",
        top: "40%",
        transform: "translate(-50%, -50%)",
        background: "var(--editor-bg, #fff)",
        border: "1px solid var(--editor-border, #ddd)",
        borderRadius: 12,
        boxShadow: "0 8px 24px rgba(0,0,0,0.2)",
        padding: 12,
        zIndex: 10000,
      }}
    >
      <div style={{ display: "grid", gridTemplateColumns: "repeat(8, 36px)", gap: 4 }}>
        {EMOJIS.map((e) => (
          <button
            key={e}
            onClick={() => props.onPick(e)}
            style={{ fontSize: 22, background: "none", border: "none", cursor: "pointer", padding: 2 }}
          >
            {e}
          </button>
        ))}
      </div>
    </div>
  );
}
