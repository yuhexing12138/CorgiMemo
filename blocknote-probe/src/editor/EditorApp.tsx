import "@blocknote/mantine/style.css";
import { BlockNoteView } from "@blocknote/mantine";
import { useCreateBlockNote } from "@blocknote/react";
import { BlockNoteEditor } from "@blocknote/core";
import { useCallback, useEffect, useRef, useState } from "react";
import { editorSchema } from "./schema";
import { bindDown, sendUp, type ThemePayload } from "./bridge";
import { mdToBlocks, blocksToMd } from "./markdown/converter";
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
        <button onClick={() => editor.undo()}>↶ 撤销</button>
        <button onClick={() => editor.redo()}>↷ 重做</button>
      </div>
      <div style={{ fontFamily: "var(--content-font, system-ui)" }}>
        <BlockNoteView
          editor={editor}
          theme={props.theme.dark ? "dark" : "light"}
          onChange={props.onChange}
        />
      </div>
    </div>
  );
}
