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
      const payload = {
        blockType: block.type as string,
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
          setFontFamily(msg.fontFamily);
          break;
        /**
         * 编辑区最小高度（v1.11.6）：宿主下发 dp 值，写入 CSS 变量供 editor.css 消费。
         * 用法与 `SIDE_MENU_*` 无关的那套 CSS 变量一致——CSS 侧不写魔法数字。
         */
        case "setEditorMinHeight":
          setEditorMinHeight(msg.height);
          break;
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
            case "bold":
              ed.toggleStyles({ bold: true });
              break;
            case "italic":
              ed.toggleStyles({ italic: true });
              break;
            case "underline":
              ed.toggleStyles({ underline: true });
              break;
            case "strike":
              ed.toggleStyles({ strike: true });
              break;
            case "fontSize":
              // value = "18px"（px 字面量），"default" / 空清除
              if (value && value !== "default") ed.addStyles({ fontSize: value });
              else ed.removeStyles({ fontSize: "16px" });
              break;
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
                case "heading1":
                case "heading2":
                case "heading3": {
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
                case "toggleHeading":
                case "toggleHeading2":
                case "toggleHeading3":
                case "toggleList": {
                  // 可折叠标题/可折叠列表（独立块类型，toggle 语义；带档位的解析尾数）
                  const { block } = ed.getTextCursorPosition();
                  const targetType = block.type === value ? "paragraph" : value;
                  if (value === "toggleHeading2" || value === "toggleHeading3") {
                    const level = Number(value.slice(-1));
                    ed.updateBlock(block, {
                      type: targetType,
                      props: { level },
                    } as any);
                  } else {
                    ed.updateBlock(block, { type: targetType } as any);
                  }
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

  useEffect(() => {
    props.onReady(editor);
    // 初次挂载后上报一次可用态（初始内容装载本身不产生可撤销历史，通常为 false/false）
    props.onUndoStateChange();
    // v1.11：同时上报一次当前块状态，避免宿主工具栏的按钮在首次点击前处于无状态
    props.onBlockStateChange();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editor]);

  // 字体栈：系统默认 → system-ui；自定义字体 → "ff-{id}"（@font-face 已注入）
  const contentFont =
    props.fontFamily === "system_default"
      ? "system-ui"
      : `"ff-${props.fontFamily}", system-ui`;

  return (
    <div
      className="editor-page"
      style={{
        ["--content-font" as any]: contentFont,
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
