import "@blocknote/mantine/style.css";
import { BlockNoteView } from "@blocknote/mantine";
import {
  FormattingToolbar,
  FormattingToolbarController,
  useBlockNoteEditor,
  useComponentsContext,
  useCreateBlockNote,
} from "@blocknote/react";
import { BlockNoteEditor } from "@blocknote/core";
import { useCallback, useEffect, useRef, useState } from "react";
import { editorSchema } from "./schema";
import { bindDown, sendUp, type ThemePayload } from "./bridge";
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

/**
 * 长按连发按钮（S12）：按下立即执行一次，450ms 后每 150ms 重复；抬起/移出停止。
 * 用于 undo/redo——软键盘没有 Ctrl+Z，长按连退是移动端刚需。
 */
function AutoRepeatButton(props: { label: string; title: string; onAction: () => void }) {
  const timers = useRef<{ delay?: ReturnType<typeof setTimeout>; rep?: ReturnType<typeof setInterval> }>({});
  const start = () => {
    props.onAction();
    timers.current.delay = setTimeout(() => {
      timers.current.rep = setInterval(props.onAction, 150);
    }, 450);
  };
  const stop = () => {
    if (timers.current.delay) clearTimeout(timers.current.delay);
    if (timers.current.rep) clearInterval(timers.current.rep);
    timers.current = {};
  };
  return (
    <button
      title={props.title}
      onPointerDown={start}
      onPointerUp={stop}
      onPointerLeave={stop}
      onPointerCancel={stop}
    >
      {props.label}
    </button>
  );
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
        case "requestSave":
          pushChanged();
          break;
        case "requestUndo":
          editorRef.current?.undo();
          break;
        case "requestRedo":
          editorRef.current?.redo();
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
      }
    });
    sendUp({ type: "ready" });
    return () => {
      window.BlockNoteEditorHost = undefined;
    };
  }, [pushChanged]);

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
      onReady={(editor) => {
        editorRef.current = editor;
      }}
      onChange={pushChanged}
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

/** 编辑器核心（initialBlocks 就绪后挂载，useCreateBlockNote 仅执行一次） */
function EditorCore(props: {
  initialBlocks: any[];
  readOnly: boolean;
  theme: ThemePayload;
  fontFamily: string;
  fontWeights: Record<string, number[]>;
  emojiOpen: boolean;
  onEmojiClose: () => void;
  onEmojiPick: (emoji: string) => void;
  onReady: (editor: any) => void;
  onChange: () => void;
}) {
  // @font-face 注入（S5）
  useEffect(() => {
    applyFontFaces(props.fontWeights);
  }, [props.fontWeights]);

  const editor = useCreateBlockNote({
    schema: editorSchema as any,
    initialContent: props.initialBlocks,
    editable: !props.readOnly,
  });

  useEffect(() => {
    props.onReady(editor);
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
        ["--editor-bg" as any]: props.theme.dark ? "#1e1e1e" : "#ffffff",
        ["--editor-fg" as any]: props.theme.dark ? "#e0e0e0" : "#333333",
        ["--editor-border" as any]: props.theme.dark ? "#444444" : "#cccccc",
      }}
    >
      <div className="editor-toolbar">
        <AutoRepeatButton label="↶ 撤销" title="撤销（长按连发）" onAction={() => editor.undo()} />
        <AutoRepeatButton label="↷ 重做" title="重做（长按连发）" onAction={() => editor.redo()} />
      </div>
      <div style={{ fontFamily: "var(--content-font, system-ui)" }}>
        <BlockNoteView
          editor={editor}
          theme={props.theme.dark ? "dark" : "light"}
          formattingToolbar={false}
          onChange={props.onChange}
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
