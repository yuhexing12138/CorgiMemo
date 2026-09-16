import "@blocknote/mantine/style.css";
import { BlockNoteView } from "@blocknote/mantine";
import { useCreateBlockNote } from "@blocknote/react";
import { useCallback, useEffect, useRef, useState } from "react";
import { probeSchema } from "./probes/schema";
import { runChecks } from "./probes/checks";
import type { CheckResult } from "./probeTypes";
import "./probe.css";

/** 内联 SVG 小图（data URI）：作为跨块选择的图片块边界 */
const PROBE_IMAGE_URL =
  "data:image/svg+xml;utf8," +
  encodeURIComponent(
    '<svg xmlns="http://www.w3.org/2000/svg" width="320" height="120">' +
      '<rect width="320" height="120" rx="8" fill="#dbe7ff"/>' +
      '<text x="160" y="66" font-size="18" text-anchor="middle" fill="#3b5bab">图片块（选择边界）</text></svg>'
  );

/** 初始内容：≥8 块，图片夹在段落之间（跨块选择需跨图片边界） */
const initialContent: any[] = [
  {
    type: "paragraph",
    content: "真机验证页：长按拖拽跨块选择（跨图片边界）、中文 IME、软键盘 inset。下方面板实时显示检测数据。",
  },
  { type: "dividerStyled", props: { style: "wavy" } },
  { type: "heading", content: "第一段标题" },
  {
    type: "paragraph",
    content: "段落 A：长按这句话，拖拽选择手柄向下越过图片块，观察下方「选区检测」是否显示跨块信息，以及系统复制菜单是否可用。",
  },
  { type: "image", props: { url: PROBE_IMAGE_URL } },
  { type: "paragraph", content: "段落 B：选择的终点落在这里。" },
  {
    type: "checkListItem",
    props: { checked: false },
    content: "任务项：点我勾选（回归验证）",
  },
  {
    type: "paragraph",
    content: "中文输入测试区：把光标放这里，用输入法打「今天天气不错」。",
  },
  { type: "paragraph" },
];

/** 节流后的 selectionchange 描述（块索引） */
function describeSelection(): string {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0 || sel.isCollapsed) return "（无选区）";
  const blocks = Array.from(
    document.querySelectorAll<HTMLElement>(".bn-block-content")
  );
  const idxOf = (node: Node | null): number => {
    let el: HTMLElement | null =
      node instanceof Element ? (node as HTMLElement) : node?.parentElement ?? null;
    while (el && !el.classList.contains("bn-block-content")) el = el.parentElement;
    return el ? blocks.indexOf(el) : -1;
  };
  const a = idxOf(sel.anchorNode);
  const f = idxOf(sel.focusNode);
  const textLen = sel.toString().length;
  if (a < 0 || f < 0) return `选中 ${textLen} 字符（选区不在块内）`;
  if (a === f) return `单块内选择：块 #${a}，${textLen} 字符`;
  return `跨块选择：块 #${Math.min(a, f)} → 块 #${Math.max(a, f)}（共 ${Math.abs(f - a) + 1} 块），${textLen} 字符`;
}

/** 探针主页：操作按钮 + 自检面板 + 真机验证面板 + 编辑器 */
export default function App() {
  const editor = useCreateBlockNote({ schema: probeSchema, initialContent });
  const [checks, setChecks] = useState<CheckResult[]>([]);
  const [running, setRunning] = useState(false);

  // ---- 真机验证面板状态 ----
  const [selectionText, setSelectionText] = useState("（无选区）");
  const [inputLog, setInputLog] = useState<string[]>([]);
  const [viewportText, setViewportText] = useState("（visualViewport 待监听）");

  /** 追加一条输入事件（保留最近 6 条） */
  const pushInput = useCallback((line: string) => {
    const ts = new Date().toLocaleTimeString("zh-CN", { hour12: false });
    setInputLog((prev) => [`${ts}  ${line}`, ...prev].slice(0, 6));
  }, []);

  /** 运行自检并落状态 */
  const run = useCallback(async () => {
    setRunning(true);
    try {
      setChecks(await runChecks(editor));
    } finally {
      setRunning(false);
    }
  }, [editor]);

  // 编辑器渲染完成后自动跑一次自检
  useEffect(() => {
    const t = setTimeout(() => {
      run();
    }, 800);
    return () => clearTimeout(t);
  }, [run]);

  // ---- selectionchange（节流 ~150ms） ----
  useEffect(() => {
    let pending = false;
    const onSel = () => {
      if (pending) return;
      pending = true;
      setTimeout(() => {
        pending = false;
        setSelectionText(describeSelection());
      }, 150);
    };
    document.addEventListener("selectionchange", onSel);
    return () => document.removeEventListener("selectionchange", onSel);
  }, []);

  // ---- 输入事件流（双通道）：composition（桌面/iOS 常见）+ beforeinput（Android 实际通道） ----
  useEffect(() => {
    const onStart = (e: CompositionEvent) => pushInput(`compositionstart "${e.data}"`);
    const onUpdate = (e: CompositionEvent) => pushInput(`compositionupdate "${e.data}"`);
    const onEnd = (e: CompositionEvent) => pushInput(`compositionend "${e.data}"`);
    const onBeforeInput = (e: InputEvent) =>
      pushInput(`beforeinput ${e.inputType} "${(e.data ?? "").slice(0, 12)}"`);
    document.addEventListener("compositionstart", onStart);
    document.addEventListener("compositionupdate", onUpdate);
    document.addEventListener("compositionend", onEnd);
    document.addEventListener("beforeinput", onBeforeInput);
    return () => {
      document.removeEventListener("compositionstart", onStart);
      document.removeEventListener("compositionupdate", onUpdate);
      document.removeEventListener("compositionend", onEnd);
      document.removeEventListener("beforeinput", onBeforeInput);
    };
  }, [pushInput]);

  // ---- visualViewport（软键盘 inset 观察） ----
  const vvRef = useRef(false);
  useEffect(() => {
    const vv = window.visualViewport;
    if (!vv) {
      setViewportText("（本环境无 visualViewport API）");
      return;
    }
    const report = () => {
      setViewportText(
        `viewport 高度 ${Math.round(vv.height)}px（offsetTop ${Math.round(vv.offsetTop)}）｜window.innerHeight ${window.innerHeight}px`
      );
    };
    const onResize = () => {
      if (!vvRef.current) {
        vvRef.current = true;
        report();
      } else {
        report();
      }
    };
    vv.addEventListener("resize", onResize);
    vv.addEventListener("scroll", report);
    report();
    return () => {
      vv.removeEventListener("resize", onResize);
      vv.removeEventListener("scroll", report);
    };
  }, []);

  /** 在光标后插入指定样式分割线（propSchema values 收紧为字面量联合） */
  const insertDivider = (style: "solid" | "dashed" | "wavy") => {
    const cursor = editor.getTextCursorPosition();
    editor.insertBlocks(
      [{ type: "dividerStyled", props: { style } }],
      cursor.block,
      "after"
    );
  };

  /** 在光标处插入 24px 文本 */
  const insertBigText = () => {
    editor.insertInlineContent([
      { type: "text", text: "24px 新文本 ", styles: { fontSize: "24px" } },
    ]);
  };

  return (
    <div className="probe-page">
      <h1>BlockNote 探针·真机版（0.52.1）</h1>
      <div className="probe-buttons">
        <button onClick={() => insertDivider("solid")}>+ 实线</button>
        <button onClick={() => insertDivider("dashed")}>+ 虚线</button>
        <button onClick={() => insertDivider("wavy")}>+ 波浪</button>
        <button onClick={insertBigText}>+ 24px 文本</button>
        <button onClick={run} disabled={running}>
          {running ? "自检中…" : "重新自检"}
        </button>
      </div>

      <div id="self-check" className="probe-checks">
        <h2>真机验证面板</h2>
        <div className="probe-check probe-info">
          <div className="probe-check-head">
            <span className="probe-badge">选择</span>
            <span className="probe-check-name">跨块选择检测</span>
          </div>
          <pre className="probe-detail">{selectionText}</pre>
        </div>
        <div className="probe-check probe-info">
          <div className="probe-check-head">
            <span className="probe-badge">输入</span>
            <span className="probe-check-name">输入事件流（Android 键盘多走 beforeinput，无 composition 属正常）</span>
          </div>
          <pre className="probe-detail">
            {inputLog.length > 0
              ? inputLog.join("\n")
              : "（在编辑器里打字后，这里会滚动显示输入事件）"}
          </pre>
        </div>
        <div className="probe-check probe-info">
          <div className="probe-check-head">
            <span className="probe-badge">键盘</span>
            <span className="probe-check-name">visualViewport（键盘弹出后高度应变小且光标可见）</span>
          </div>
          <pre className="probe-detail">{viewportText}</pre>
        </div>
        <h2>自检面板（{checks.length} 项）</h2>
        {checks.length === 0 && <p className="probe-muted">尚未运行</p>}
        {checks.map((c) => (
          <div key={c.id} className={`probe-check probe-${c.status.toLowerCase()}`}>
            <div className="probe-check-head">
              <span className="probe-badge">{c.status}</span>
              <span className="probe-check-name">{c.name}</span>
            </div>
            <pre className="probe-detail">{c.detail}</pre>
          </div>
        ))}
      </div>

      <BlockNoteView editor={editor} theme="light" />
    </div>
  );
}
