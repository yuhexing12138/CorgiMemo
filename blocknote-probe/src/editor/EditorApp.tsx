import "@blocknote/mantine/style.css";
import { BlockNoteView } from "@blocknote/mantine";
import {
  DeleteLinkButton,
  EditLinkButton,
  EditLinkMenuItems,
  FormattingToolbar,
  FormattingToolbarController,
  LinkToolbarController,
  PositionPopover,
  useBlockNoteEditor,
  useComponentsContext,
  useCreateBlockNote,
  type LinkToolbarProps,
} from "@blocknote/react";
import { BlockNoteEditor, blockHasType } from "@blocknote/core";
// floating-ui 的防裁剪三件套：与官方 FormattingToolbarController 完全同款
// （offset 离锚点 10px、shift 贴边回拉治右裁剪、flip 上放不下翻转治上裁剪）。
import { flip, offset, shift } from "@floating-ui/react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { editorSchema } from "./schema";
import { bindDown, sendUp, BUILD_FINGERPRINT, SRC_HASH, type ThemePayload } from "./bridge";
import { mdToBlocks, blocksToMd, toWebImageUrl } from "./markdown/converter";
import { linkHrefSyncExtension } from "./linkHrefSync";
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

/**
 * 链接地址归一化（v2026-09-22）：给缺少协议的输入自动补 `https://`。
 *
 * 背景：用户常直接输入 `example.com` / `www.a.cn`，若原样写进 href，
 * ProseMirror 会把它当成**相对路径**渲染，点击后跳到编辑器所在目录下的路径
 * （宿主 WebView 的 base URL），表现为"点了链接没反应或跳错"。
 * BlockNote 官方的 LinkToolbar 也有同款处理（`validateUrl`），此处沿用其口径：
 * 已带scheme（http/https/mailto/tel/file 等）或锚点/相对路径（`#`、`/`）时原样保留。
 */
const HAS_PROTOCOL_RE = /^[a-zA-Z][a-zA-Z0-9+.-]*:/;
function normalizeLinkUrl(raw: string): string {
  const trimmed = raw.trim();
  if (trimmed === "") return trimmed;
  if (HAS_PROTOCOL_RE.test(trimmed)) return trimmed;
  if (trimmed.startsWith("//")) return `https:${trimmed}`;
  if (trimmed.startsWith("#") || trimmed.startsWith("/")) return trimmed;
  return `https://${trimmed}`;
}

/**
 * 读某个位置上的链接信息（v2026-09-22）。
 *
 * 口径照抄官方：`@blocknote/core` 的 `StyleManager.getLinkMarkAtPos(pos)`，
 * 内部 `doc.resolve(pos).marks()` 找 link mark，并回带命中的**完整范围与文字**
 * （官方 LinkToolbar 的 `getLinkAtPos` 就是这么用的）。
 *
 * ⚠️ 必须包 try：`resolve(pos)` 在文档边界 / 文档刚被替换的瞬间会抛
 * `RangeError: Position … outside of current document`；此处只用于**回显与分流**，
 * 取不到就当"不在链接上"，绝不能让它把整条 blockState 推去 error 分支
 * （那会让宿主工具栏停在旧状态）。
 *
 * @param pos 目标位置；undefined（取不到选区）时直接返回 undefined
 */
function linkDataAt(ed: any, pos: number | undefined): { href: string; text: string; from: number; to: number } | undefined {
  if (typeof pos !== "number" || !Number.isFinite(pos)) return undefined;
  try {
    const data = ed.getLinkMarkAtPos(pos);
    const href = data?.href;
    if (typeof href !== "string" || href.length === 0) return undefined;
    return {
      href,
      text: typeof data.text === "string" ? data.text : "",
      from: typeof data.from === "number" ? data.from : pos,
      to: typeof data.to === "number" ? data.to : pos,
    };
  } catch {
    return undefined;
  }
}

/** 只要 href（回显与激活态用） */
function linkHrefAt(ed: any, pos: number | undefined): string | undefined {
  return linkDataAt(ed, pos)?.href;
}

/**
 * 链接地址的安全归一（v2026-09-24）
 *
 * 口径与官方 `@blocknote/react` 内部的 `sanitizeUrl` 一致：能解析出 URL 且协议
 * **不是 `javascript:`** 才放行，否则返回空串（调用方据此放弃打开）。
 *
 * **为什么必须留这道闸**：链接 href 的内容来自用户输入与 markdown 导入，
 * 进宿主后要经 `Intent.ACTION_VIEW` 交给系统——放行 `javascript:` 之类伪协议
 * 既无意义（外部浏览器不认），也平添攻击面。相对路径（无 base 时解析失败）
 * 同样丢弃：在编辑器语境里它指向 WebView 的 assets 目录，打开必然 404。
 *
 * @param raw 链接原始 href（可能是 `example.com`、`#anchor`、`javascript:…`）
 * @returns 可安全外部打开的绝对 URL；不可用时为空串
 */
function sanitizeOutboundUrl(raw: string | undefined): string {
  if (typeof raw !== "string" || raw.trim() === "") return "";
  try {
    const url = new URL(raw, window.location.href);
    if (url.protocol === "javascript:") return "";
    return url.href;
  } catch {
    return "";
  }
}

/**
 * 链接点击处理器（v2026-09-24）—— 挂在官方 `useCreateBlockNote` 的 `links.onClick`
 *
 * ## 它替换掉了什么
 *
 * 官方 `clickHandler` 插件（`@blocknote/core` 的
 * `extensions/tiptap-extensions/Link/plugins/clickHandler.ts`）在**未配置**
 * `links.onClick` 时会走默认分支：`window.open(href, target)`，而 Link 扩展的
 * `HTMLAttributes` 默认带 `target: "_blank"`。
 *
 * 这条默认路径在 Android WebView 上有两个致命问题：
 * ① `setSupportMultipleWindows` 默认 false → **带 target 的 `window.open` 被
 *    Chromium 静默丢弃**：不导航、不报错、无日志，"点了没反应"；
 * ② 官方 `clickHandler` 首行是 `if (event.button !== 0 || !view.editable) return false;`
 *    ——**编辑态能拦、只读态直接放行**给 DOM 默认行为，一旦放行就有原地导航
 *    把编辑器顶掉的风险。
 *
 * 官方源码注释明确：配置 `onClick` 后「the default open-on-click behavior is
 * disabled and this function is called instead」——即本函数一挂，
 * 上面那条 `window.open` 默认路径**彻底不再执行**，两个问题一并消失。
 *
 * ## 本函数的行为（按产品决策）
 *
 * - **编辑态（`editable === true`）**：返回 `true` 吃掉事件，**只落光标不打开**。
 *   光标落下后官方 LinkToolbar 自会弹出，用户通过它的「打开」按钮决定是否打开
 *   （见下方 `bindDown` 里对该按钮的接管）。理由：编辑态里用户更可能是在改链接
 *   文字，误触即跳走会打断编辑。
 * - **只读态（`editable === false`）**：无 LinkToolbar 可用，此时点击即"要打开"
 *   ——上行 `openLink` 交宿主送系统浏览器。
 *
 * ⚠️ **必须返回 `true`（已处理）而非 `false`**：返回 false 表示"我没处理，交给
 * ProseMirror 继续"，会再次落到 DOM 默认行为（`<a>` 锚点导航）——那正是要避免的。
 *
 * @param event  ProseMirror 透传的原始 MouseEvent
 * @param editor 触发点击的 BlockNoteEditor 实例
 */
function handleLinkClick(event: MouseEvent, editor: any): boolean {
  /**
   * 中键 / 右键不接管：官方 `clickHandler` 同样只认左键（`event.button !== 0`
   * 时 return false），保持一致，避免抢掉长按菜单等系统交互。
   */
  if (event.button !== 0) return false;

  /**
   * 从事件目标上读 href，而不是从编辑器选区读：点击位置与键盘光标位置可能不同
   * （用户点的是另一个链接），`closest("a")` 才是"用户实际点的那个链接"。
   */
  const anchor = (event.target as HTMLElement | null)?.closest?.("a");
  const href = sanitizeOutboundUrl(anchor?.getAttribute("href") ?? undefined);
  if (href === "") return true; // 空 href / 伪协议：吃事件，不做任何事

  /** 编辑态：落光标 + 弹 LinkToolbar，是否打开交给用户 */
  if (editor?.isEditable !== false) {
    return true;
  }

  /** 只读态：无工具栏可用，点击即打开 */
  sendUp({ type: "openLink", url: href });
  return true;
}

/**
 * 就地改块类型，并保住原有的行内文本（v2026-09-23）
 *
 * **为什么必须包这一层**：官方 `updateBlock` 在调用方**没有显式传 content** 时，
 * 只按 ProseMirror 的 content 表达式**字符串**判断能否沿用旧内容
 * （`@blocknote/core` 的 `api/blockManipulation/commands/updateBlock/updateBlock.ts:200-214`）：
 *
 *   - 段落 / 标题 / 引用 / 列表 / 折叠列表 → `content: "inline"` → `"inline*"`
 *     （`schema/blocks/createSpec.ts:185-186` 编译而来）
 *   - **代码块** → `content: "plain"` → `"text*"`（`blocks/Code/block.ts:65`）
 *
 * 两者字符串不等即 `content = []`，**旧文本被主动清空**。真机现象：在代码块上点
 * 标题/引用/列表等任何"块类型"按钮，代码文本直接消失（Code Block 按钮上已实测复现）。
 * 反向（段落 → 代码块）同理，只是方向相反。凡"跨内容类型"的改型都必须走本函数。
 *
 * **回传口径**：只回传 **inline 内容形态**（StyledText 数组 / 字符串）。
 * 表格（`tableContent`）与无内容块（divider / pageBreak 等）原样放行 ——
 * 它们本就没有行内文本可保留，而 tableContent 也不能当 inline content 交给
 * `inlineContentToNodes`（会走到 `UnreachableCaseError` 抛错）。
 *
 * @param ed    编辑器实例
 * @param block 目标块（按 id 就地改类型，不改变块的位置）
 * @param type  目标块类型（paragraph / heading / quote / toggleListItem / codeBlock …）
 * @param props 需要一并写入的块 props（如 heading 的 `level`）；未列出的 props 由官方保留
 * @returns 官方 `updateBlock` 返回的新块对象（可再交给 `setTextCursorPosition`）
 */
function retypeBlockSafely(
  ed: any,
  block: any,
  type: string,
  props?: Record<string, unknown>,
): any {
  /**
   * ⚠️ 用 `unknown` 中转再判类型：`Block["content"]` 的声明类型不含 string，
   * 直接 `typeof block.content === "string"` 会被 TS 判为"类型不相交"（TS2367）。
   */
  const rawContent: unknown = block?.content;
  const keepContent =
    Array.isArray(rawContent) || typeof rawContent === "string"
      ? (rawContent as any)
      : undefined;
  return ed.updateBlock(block, {
    type,
    ...(props ? { props } : {}),
    ...(keepContent !== undefined ? { content: keepContent } : {}),
  });
}

/**
 * 当前光标 / 选区的「链接锚点位置」（v2026-09-22）
 *
 * - **光标态**（选区折叠）取 `anchor`——与官方 LinkToolbar 的 `getLinkAtSelection`
 *   （`getLinkAtPos(tr.selection.anchor)`）同口径，这样"光标停在链接末尾"也能命中；
 * - **有选区**取 `from`——与官方 `getSelectedLinkUrl()`（`getLinkMarkAtPos(selection.from)`）同口径。
 *
 * 两者不同源是有意为之：官方自己也用了两套（工具条按钮读 from、链接工具条读 anchor），
 * 我们按"哪种形态更可能命中"来选。
 */
function linkAnchorPos(view: any): number | undefined {
  const sel = view?.state?.selection;
  if (!sel) return undefined;
  return sel.empty ? sel.anchor : sel.from;
}

/**
 * 校验并夹紧宿主下发的链接目标区间（v2026-09-22）。
 *
 * 快照区间来自 `saveSelection` 时刻的文档；若文档此后真的变了（弹窗期间宿主从不
 * 注入内容变更，理论上不发生），位置可能越界或落在另一个块里。此处不拒绝、只夹紧
 * ——"按夹紧后的位置写"总比"什么都不发生"好，且外层 try/catch + diagnostic 兜底。
 *
 * @returns 夹紧后的 { from, to }；任一位置缺失 / 非有限数 / 无文档时返回 null
 *          （null = 宿主没给区间，走旧的"读当前选区"路径）
 */
function clampLinkRange(
  ed: any,
  from: number | undefined,
  to: number | undefined
): { from: number; to: number } | null {
  if (typeof from !== "number" || !Number.isFinite(from)) return null;
  const size = ed?.prosemirrorView?.state?.doc?.content?.size;
  if (typeof size !== "number") return null;
  const lo = Math.max(0, Math.min(from, size));
  const hi =
    typeof to === "number" && Number.isFinite(to)
      ? Math.max(0, Math.min(to, size))
      : lo;
  return lo <= hi ? { from: lo, to: hi } : { from: hi, to: lo };
}

/**
 * 在**显式区间**上写链接（v2026-09-22）。
 *
 * 与 BlockNote `StyleManager.createLink` 同构，但接受 from/to 而非读当前选区——
 * 这是"链接精确落点"的核心：宿主把快照位置传回来，编辑器当前选区在不在都无所谓。
 *
 * ⚠️ 不能用 `ed.createLink(url, text)`：它内部固定读 `tr.selection`，位置传不进去；
 *   而 `ed.transact()` + `ed.pmSchema.mark()` 都是公开 API，与官方实现完全同构。
 *
 * @returns 诊断用动作名（insert-at-range / replace-range / mark-range）
 */
function writeLinkAtRange(
  ed: any,
  url: string,
  text: string | undefined,
  from: number,
  to: number
): string {
  const linkMark = ed.pmSchema.mark("link", { href: url });
  if (from === to) {
    /** 光标态：插入文字（标题或 URL 原文）并挂 link mark——正是"未选中文字直接插链接" */
    const display = text || url;
    ed.transact((tr: any) => {
      tr.insertText(display, from, to).addMark(from, from + display.length, linkMark);
    });
    return "insert-at-range";
  }
  if (text) {
    /** 有区间 + 填了标题：标题替换区间内文字后再挂链接 */
    ed.transact((tr: any) => {
      tr.insertText(text, from, to).addMark(from, from + text.length, linkMark);
    });
    return "replace-range";
  }
  /** 有区间 + 未填标题：只给区间内已有文字挂 link mark */
  ed.transact((tr: any) => {
    tr.addMark(from, to, linkMark);
  });
  return "mark-range";
}

/** 光标块类型切换（已是目标类型则退回普通段落）——列表/任务按钮的 toggle 语义 */
function toggleBlockType(ed: any, type: string): void {
  const { block } = ed.getTextCursorPosition();
  const target = block.type === type ? "paragraph" : type;
  // ⚠️ v2026-09-23：必须走 [retypeBlockSafely] 保住行内文本 ——
  // 当前块若是代码块（content 表达式 "text*"，与其它块的 "inline*" 不同），
  // 官方 updateBlock 会因"内容类型变了"把原文本清空
  retypeBlockSafely(ed, block, target);
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
 * 当前光标/选区所在块**是否可承载行内样式**（v2026-09-23）。
 *
 * **为什么需要这个判定**：行内样式（字号 fontSize / 文字色 textColor）只能挂在
 * **行内文本**上，而 BlockNote 的块有三种内容形态：
 * - `inline`（段落/标题/引用/列表…）→ 有文本，可施加行内样式；
 * - `table` → 内容由单元格承载，本工具栏不处理；
 * - `none`（**divider / image / video / audio / file / pageBreak**）→ **零文本**，
 *   在它上面点「字号 / 文字颜色」必然无效（`getActiveStyles()` 拿不到任何可写的 mark），
 *   真机表现就是"按钮亮着、点了没反应"。
 *
 * 分割线场景正是踩到这里：点分割线弹样式条时，编辑器自带的行内工具栏同时出现，
 * 其中的 Aa / A 两个按钮完全无效，纯属干扰（用户截图里那两个蓝色块）。
 *
 * **判据来源**：直接问 schema 拿该块类型的 `content` 声明——
 * 比按块类型名硬编码白名单更稳（自定义块、后续新增块类型都自动适配）。
 * 读不到声明时返回 true（宁可多显示，也不误藏有用按钮）。
 *
 * @param editor BlockNote 编辑器实例
 */
function canApplyInlineStyles(editor: any): boolean {
  try {
    const block = editor.getTextCursorPosition()?.block;
    if (!block) return true;
    const spec = editor.schema?.blockSpecs?.[block.type];
    const content = spec?.config?.content;
    return content === "inline";
  } catch {
    // 取不到光标位置（无选区等边界）时按"可施加"处理，不影响正常输入路径
    return true;
  }
}

/** S8：字号循环按钮（格式工具栏内）——无 → 最小 → 递增 → 末档清除 */
function FontSizeButton() {
  const Components = useComponentsContext()!;
  const editor = useBlockNoteEditor<any, any, any>();
  // 无文本块（分割线/图片等）上直接隐藏，避免"点了没反应"的无效按钮
  if (!canApplyInlineStyles(editor)) return null;
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
  // 同 FontSizeButton：无文本块上隐藏（行内色无处施加）
  if (!canApplyInlineStyles(editor)) return null;
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
  /**
   * 链接面板显隐 + 其锚点/预填数据（v2026-09-24）
   *
   * 由宿主下行 `openLinkPanel` / `closeLinkPanel` 驱动（底部工具栏 🔗 按钮）。
   * `range` 取宿主 `saveSelection` 快照回来的区间，`url` 取 `blockState.linkUrl`
   * ——三者在**打开那一刻**一并定下，随后由 `LinkEditPanel` 内部冻结，避免用户
   * 打字期间被外部上行覆写。
   */
  const [linkPanel, setLinkPanel] = useState<{
    open: boolean;
    range: { from: number; to: number };
    url: string;
    text: string;
  }>({ open: false, range: { from: -1, to: -1 }, url: "", text: "" });
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
   * 选区快照（v2026-09-22 新增；配合下行 `saveSelection` / `restoreSelection`）
   *
   * 链接对话框是 Compose 的 `AlertDialog`，弹出时 WebView 会失焦。若 Android WebView
   * 在失焦时把内部选区折叠了，写链接时就会按「无选区」处理 —— 用户明明选了字，
   * 却在光标处插了 URL。故宿主在开弹窗前下发 `saveSelection` 把此刻的选区快照留下，
   * 确认时先 `restoreSelection` 再写链接。
   *
   * 同时存 **doc 引用**：ProseMirror 的 `Selection` 对象与文档强绑定，只要文档没变
   * （弹窗期间宿主从不下发内容变更）就可以原样复用；文档变了才走"按位置重建"的兜底。
   */
  const savedSelectionRef = useRef<{ selection: any; doc: any } | null>(null);

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
   * - 复选框块 → v2026-09-22 新增 `isCheckboxBlock`：`block.type === "checkListItem"`，
   *   供工具栏复选框按钮的激活态（原先读宿主本地镜像 `isFocusedBlockCheckbox`，恒 false）；
   *
   * ⚠️ **跨块多选**（v2026-09-22）：以上所有"当前块"口径一律取
   * `getSelection().blocks[0]`（选区折叠时回退 `getTextCursorPosition().block`），
   * 与官方一致——不能用 `getTextCursorPosition()`（它按 selection.**anchor**
   * 取块，反向拖选时落在选区末端，与命令实际作用的块不一致）。详见函数内注释。
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
      /**
       * 当前「光标块」（v2026-09-22 修正为**跨块多选安全**）
       *
       * 口径照抄官方（@blocknote/react 的 `TextAlignButton` / `NestBlockButtons`
       * / `ColorStyleButton` 等一律这么写）：
       *   `editor.getSelection()?.blocks || [editor.getTextCursorPosition().block]`
       * 再取 `selectedBlocks[0]`——工具栏命令（对齐 / 块色 / 缩进 / 删除…）作用的
       * 是**整个选区**，回显也必须以**选区首块**为准，否则会出现
       * "按钮显示的是 A 块的状态、一点下去却改了 A~C 三块"。
       *
       * ⚠️ 原先只调 `getTextCursorPosition()` 的问题（已核实源码，不是抛异常）：
       * 它底层走 `getBlockInfoFromSelection` → `getBlockInfoAtNearest(selection.anchor)`，
       * 取的是 **anchor 所在块**。跨块多选时 anchor 可能在**选区末端**
       * （反向拖选 → anchor = 选区终点），于是回显块 ≠ 命令实际作用的首块。
       *
       * ⚠️ 为什么必须有 `|| [光标块]` 回退：`getSelection()` 在**选区折叠**（纯光标）
       * 或 NodeSelection 时返回 `undefined`（见 core 的 `selections/selection.ts`），
       * 那是绝大多数编辑时刻，少了回退就永远拿不到块。
       *
       * ⚠️ `getSelection()` 在极少数文档边界会抛「node not found at position」，
       * 故单独包一层 try：失败时**回落光标块**而不是让整条 blockState 走 error
       * 分支（那会让宿主工具栏停在旧状态 = 状态失真）。
       */
      const block = (() => {
        try {
          const selectionBlocks = ed.getSelection()?.blocks;
          if (selectionBlocks && selectionBlocks.length > 0) {
            return selectionBlocks[0];
          }
        } catch {
          /* 选区解析失败 → 回落光标块（下方） */
        }
        return ed.getTextCursorPosition().block;
      })();
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
        /**
         * 当前块是否为「复选框块」（v2026-09-22 新增；工具栏复选框按钮的激活态）
         *
         * 判据就是块类型本身：BlockNote 的复选框（任务项）是独立块类型
         * `checkListItem`（与 `toggleBlockType(ed, "checkListItem")` 同一口径，
         * 本项目 schema 的 defaultBlockSpecs 里只有这一个勾选类块）。
         *
         * ⚠️ 必须随 blockState 上行：宿主此前读的是 Compose 时代遗留的
         * `BodyBlocksController.isFocusedBlockCheckbox`——它读**宿主本地块对象**
         * 的段落类型，而 BlockNote 模式下正文只在 ProseMirror 文档树里，
         * 那份镜像与真实块类型无关 → 按钮**永远不高亮**（与 B/I/U/S 同一个坑）。
         */
        isCheckboxBlock: block.type === "checkListItem",
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
        /**
         * 光标 / 选区上的已有链接 URL（v2026-09-22 新增）
         *
         * 宿主两处消费：① 底部工具栏 🔗 的激活态（此前读 Compose 时代遗留的
         * `RichTextState.isLink`，BlockNote 模式下恒 false → **永远不高亮**）；
         * ② 链接对话框据此进入「编辑链接」模式（预填 URL + 「移除链接」按钮）。
         *
         * ⚠️ 与 B/I/U/S 同属"必须由 JS 上行"的一类：正文只在 ProseMirror 文档树里，
         * 宿主那份镜像读不到 link mark。位置口径见 linkAnchorPos()。
         */
        linkUrl: linkHrefAt(ed, linkAnchorPos(ed.prosemirrorView)),
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
   * 变更立即上行（v2026-09-23 保存竞态修复）
   *
   * 背景：正常编辑经 [pushChanged] 防抖 800ms 上行；宿主「完成」按钮在用户
   * 停手不足 800ms 时点下，读到的 `_contentFormat` 还是上一次防抖快照——
   * 最后一批编辑（敲的空行/文字）没进库。真机复现：连敲 3 次回车立刻点完成，
   * 重进只剩 1 个空行（JS 导出/载入链路四轮探针已证无损，竞态在防抖窗口）。
   *
   * 行为：收到 `requestSave` 命令时**同步导出、立即上行**，同时清掉 pending
   * 的防抖任务（避免 800ms 后重复上行一条同内容 changed）。宿主侧配套
   * `requestSaveAndAwait`：发出命令后挂起等 changed 上行（超时兜底），
   * 拿到最新 markdown 再落库。
   */
  const pushChangedNow = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
      debounceRef.current = null;
    }
    try {
      const md = blocksToMd(editor, editor.document);
      sendUp({ type: "changed", markdown: md });
    } catch (e: any) {
      sendUp({ type: "error", message: `changedNow: ${e.message}` });
    }
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
        /**
         * 主动上报一次块状态（v2026-09-22 新增）
         *
         * 用途：宿主在「T / H / A」面板**收起**时下发本命令，强制刷一次
         * `blockState`，让工具栏的选中态在面板收起瞬间就是最新的。
         *
         * 为什么需要：面板展开期间正文处于 IME 抑制态，且宿主把注意力放在面板上，
         * 期间发生的选区 / 样式变化若因为去重或时序原因没有上行，收起后工具栏
         * 高亮会**滞后一拍**（显示面板操作之前的旧状态）。与其让宿主猜，不如
         * 由它显式要一次——本命令无副作用、不产生文档变更，多调无害
         * （`pushBlockState` 内部按 JSON 串去重，状态没变不会上行）。
         */
        case "requestBlockState":
          pushBlockState();
          break;
        case "requestSave":
          // v2026-09-23 保存竞态修复：立即导出上行（原走 pushChanged 防抖，
          // 宿主点完成后立刻保存会读到 800ms 前的旧快照）
          pushChangedNow();
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
          /**
           * 在聚焦块之后插入分割线，并把光标落到**分割线之后的空段落**（v2026-09-23）。
           *
           * **为什么必须显式定位光标**：`insertBlocks` 内部只做 `tr.step`，**不移动光标**
           * （见下方 codeBlock 分支的同类注释）。原实现插入后光标仍停在分割线**之前**的
           * 原块上，带来两个真机可见的副作用：
           * 1. 点分割线弹出样式工具条时，**编辑器的行内工具栏（Aa / A 等）也一并弹出**，
           *    正好压在分割线工具条下方（截图里"Aa A"两个蓝色块）——而分割线块是
           *    `content: "none"`、没有可加的样式，那些按钮点了本就无效，属于纯干扰；
           * 2. 分割线之后没有可落笔的块，继续输入会挤在原行。
           *
           * 修法 = 官方斜杠菜单口径（`insertOrUpdateBlockForSlashMenu` 插入后同样
           * `setTextCursorPosition` 到新块）：分割线**之后**再补一个空段落并把光标移过去。
           * 这样光标不在分割线上 → 行内工具栏不弹；同时输入位置符合"我刚插了一条线，
           * 接着要在下面写"的直觉。
           *
           * ⚠️ 插入的是**普通空段落**而非官方 TrailingNode 那样的"占位隐式块"：
           * 本项目 markdown 转换里空段落 = 空行，`isBlankBodyParagraph` / 尾部裁剪会
           * 如实处理，不会在往返中凭空多出内容，也不必与 Compose 版的占位符常量耦合同步。
           */
          const ed = editorRef.current;
          if (ed) {
            try {
              const cursor = ed.getTextCursorPosition();
              const inserted = ed.insertBlocks(
                [
                  { type: "divider", props: { style: "solid" } },
                  { type: "paragraph" },
                ],
                cursor.block,
                "after"
              ) as any[];
              /** 末尾那个空段落 = 光标新落点（`insertBlocks` 返回按插入顺序排列的块数组） */
              const tail = inserted?.[inserted.length - 1];
              if (tail) ed.setTextCursorPosition(tail, "start");
            } catch (e: any) {
              // 静默失败会让"点了没反应"零线索，统一上行诊断
              sendUp({ type: "error", message: `insertDivider: ${e.message}` });
            }
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
        /**
         * 打开 / 关闭链接面板（v2026-09-24）
         *
         * 锚点与预填数据**都在 JS 侧现取**，不依赖宿主回传：
         * - `range`：直读 `prosemirrorView.state.selection`——选区真值只在 WebView 里
         *   （宿主的 `saveSelection` 快照要经 `selectionRange` 上行、是异步的，
         *   命令紧随其后就下发，宿主那会儿多半还没收到）；
         * - `url`：`ed.getSelectedLinkUrl?.()` 与官方 `CreateLinkButton` 同款判据，
         *   光标落在已有链接上时预填出来，用户可直接改。
         */
        case "openLinkPanel": {
          /** ⚠️ `ed` 在本 case 内单独取：上方 `case "format"` 里的 `ed` 是块级作用域，出不来 */
          const ed = editorRef.current;
          if (!ed) break;
          const view = ed.prosemirrorView;
          if (!view) break;
          const sel = view.state.selection;
          /** 官方 CreateLinkButton 的取值口径；旧产物无此方法时回落空串 */
          const existingUrl = (() => {
            try {
              return (ed.getSelectedLinkUrl?.() as string) || "";
            } catch {
              return "";
            }
          })();
          setLinkPanel({
            open: true,
            range: { from: sel.from, to: sel.to },
            url: existingUrl,
            text: ed.getSelectedText?.() || "",
          });
          sendUp({
            type: "diagnostic",
            message: `openLinkPanel: ${sel.from}-${sel.to} url=${existingUrl}`,
          });
          break;
        }
        case "closeLinkPanel": {
          setLinkPanel((p) => (p.open ? { ...p, open: false } : p));
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
            case "createLink": {
              /**
               * 底部链接按钮 → 写链接（v2026-09-22 修复「未选中文字时点了没反应」）
               *
               * ⚠️ 原实现只有 `ed.createLink(value)`：BlockNote 的 `StyleManager.createLink`
               * 在未传 text 时走 `tr.addMark(from, to)`（见 core/src/editor/managers/StyleManager.ts），
               * 而**空选区下 from == to** —— 给零长度区间加 mark 是**静默空操作**，
               * 既不报错也不产生任何文档变更，真机表现为"点了按钮、填了 URL、什么都没发生"。
               *
               * **落点优先级（v2026-09-22 两道防线）**：
               * 1. 宿主下发的 `from` / `to`（`saveSelection` → `selectionRange` 上行 →
               *    宿主暂存 → 随本命令带回）：**不依赖当前选区是否还在**，即便 WebView
               *    失焦折叠了选区、`restoreSelection` 失败，也能按快照位置精确落点；
               * 2. 缺省（旧宿主 / 未上行）→ 回落读**当前选区**（旧路径，向后兼容）。
               *
               * 按落点形态分流：
               * - **有区间 + 未填标题** → 只给区间内文字挂 link mark（选中文字即显示文字）；
               * - **有区间 + 填了标题** → 标题替换区间内文字后再挂链接；
               * - **光标态 + 落点在已有链接上** → 走 `editLink` **改**这条链接
               *   （否则会在链接内部插出第二条链接，真机上表现为"链接里套链接"）；
               * - **光标态 + 无链接** → 以「标题 or URL 原文」为文字**插入**一段带链接的新文本
               *   （这正是本次需求：未选择文字时直接把链接插进去）。
               *
               * 另：`value` 经 [normalizeLinkUrl] 补协议，避免 `example.com` 变成相对路径。
               */
              const linkText = (msg as any).text as string | undefined;
              const pm = ed.prosemirrorView;
              const url = normalizeLinkUrl(value || "");
              if (!url) break;
              try {
                let mode: string;
                /** 防线一：宿主带回来的快照区间（优先） */
                const range = clampLinkRange(
                  ed,
                  (msg as any).from as number | undefined,
                  (msg as any).to as number | undefined
                );
                if (range) {
                  if (range.from === range.to) {
                    /** 光标态：落点在已有链接上 → 改链（保留原显示文字，除非填了标题）。
                     *  末位再兜一层 url：万一命中了零长度链接（理论不该出现），
                     *  editLink(url, "") 会把文字删掉 —— 宁可退回"显示 URL"。 */
                    const existing = linkDataAt(ed, range.from);
                    if (existing) {
                      ed.editLink(url, linkText || existing.text || url, range.from);
                      mode = "edit-existing@range";
                    } else {
                      mode = writeLinkAtRange(ed, url, linkText, range.from, range.to);
                    }
                  } else {
                    mode = writeLinkAtRange(ed, url, linkText, range.from, range.to);
                  }
                  sendUp({
                    type: "diagnostic",
                    message: `createLink: ${mode} (${range.from}-${range.to})`,
                  });
                  break;
                }
                /** 防线二：宿主没给区间 → 按当前选区分流（旧路径） */
                const hasSelection = pm ? !pm.state.selection.empty : true;
                if (hasSelection) {
                  if (linkText) ed.createLink(url, linkText);
                  else ed.createLink(url);
                  mode = "mark-selection";
                } else {
                  const existing = linkDataAt(ed, linkAnchorPos(pm));
                  if (existing) {
                    ed.editLink(url, linkText || existing.text || url);
                    mode = "edit-existing";
                  } else {
                    ed.createLink(url, linkText || url);
                    mode = "insert-text";
                  }
                }
                sendUp({ type: "diagnostic", message: `createLink: ${mode}` });
              } catch (e) {
                /**
                 * 光标落在无内联内容的块（图片/分割线等）上时 insertText 会抛异常；
                 * 区间越界（文档已变且夹紧后仍非法）同理。
                 * 静默吞掉会让问题再次变成"点了没反应"，故至少上行诊断。
                 */
                sendUp({
                  type: "diagnostic",
                  message: `createLink failed: ${String(e)}`,
                });
              }
              break;
            }
            /**
             * 选区快照 / 还原（v2026-09-22 新增；链接对话框跨弹窗保住选区）
             *
             * 宿主在开弹窗前 saveSelection、确认前 restoreSelection。桥命令按序下发与执行，
             * 故 restore 一定先于随后的 createLink / deleteLink 生效。
             */
            case "saveSelection": {
              const view = ed.prosemirrorView;
              if (!view) break;
              /**
               * 存 Selection **对象** + doc 引用，而不是只存 from/to——
               * 这样还原时无需 import 任何 ProseMirror 的 Selection 构造器
               * （prosemirror-state 只是 @blocknote/core 的传递依赖，本项目未声明）。
               */
              const sel = view.state.selection;
              savedSelectionRef.current = {
                selection: sel,
                doc: view.state.doc,
              };
              /**
               * 区间同时上行给宿主：createLink / deleteLink 会把它带回 JS，
               * 作为「不依赖当前选区」的精确落点（防线一）。
               */
              sendUp({ type: "selectionRange", from: sel.from, to: sel.to });
              sendUp({
                type: "diagnostic",
                message: `saveSelection: ${sel.from}-${sel.to} empty=${sel.empty}`,
              });
              break;
            }
            case "restoreSelection": {
              const view = ed.prosemirrorView;
              const saved = savedSelectionRef.current;
              savedSelectionRef.current = null;
              if (!view || !saved) break;
              /** 选区仍在原处（WebView 失焦并未折叠选区）→ 无需还原，静默跳过 */
              const cur = view.state.selection;
              if (
                view.state.doc === saved.doc &&
                cur.from === saved.selection.from &&
                cur.to === saved.selection.to
              ) {
                sendUp({ type: "diagnostic", message: "restoreSelection: unchanged" });
                break;
              }
              try {
                const tr = view.state.tr;
                if (view.state.doc === saved.doc) {
                  /** 文档未变：原选区对象直接复用（Selection 与 doc 绑定，同一 doc 合法） */
                  tr.setSelection(saved.selection);
                } else {
                  /**
                   * 文档已变（理论上不会发生）：原选区对象不可复用，改用位置重建。
                   * 构造器从被保存的选区实例上取，避免引入 prosemirror-state 依赖；
                   * 重建失败（如位置越界）由外层 catch 兜住，绝不中断后续命令。
                   */
                  const ctor: any = (saved.selection as any)?.constructor;
                  if (typeof ctor?.create !== "function") throw new Error("no selection ctor");
                  tr.setSelection(ctor.create(tr.doc, saved.selection.from, saved.selection.to));
                }
                view.dispatch(tr);
                sendUp({
                  type: "diagnostic",
                  message: `restoreSelection: -> ${view.state.selection.from}-${view.state.selection.to}`,
                });
              } catch (e) {
                sendUp({ type: "diagnostic", message: `restoreSelection failed: ${String(e)}` });
              }
              break;
            }
            /** 移除链接（保留文字）：链接对话框「编辑链接」模式的「移除链接」按钮 */
            case "deleteLink": {
              try {
                /**
                 * 位置优先用宿主带回来的快照区间（防线一）：`StyleManager.deleteLink(position)`
                 * 内部是 `getLinkMarkAtPos(position + 1)`，传快照位置即可不依赖
                 * "当前选区恰好还在链接上"；缺省（旧宿主）回落当前选区锚点。
                 */
                const from = (msg as any).from as number | undefined;
                ed.deleteLink(typeof from === "number" ? from : undefined);
                sendUp({ type: "diagnostic", message: "deleteLink: ok" });
              } catch (e) {
                sendUp({ type: "diagnostic", message: `deleteLink failed: ${String(e)}` });
              }
              break;
            }
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
                  // 走 retypeBlockSafely 保住行内文本（当前块可能是代码块 "text*"，
                  // 直接 updateBlock 会因跨内容类型而清空）
                  retypeBlockSafely(ed, block, targetType, { level });
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
                  // 走 retypeBlockSafely 保住行内文本（当前块可能是代码块 "text*"）
                  retypeBlockSafely(ed, block, already ? "paragraph" : "heading", {
                    level,
                    isToggleable: !already,
                  });
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
                  // 走 retypeBlockSafely 保住行内文本：当前块可能是代码块（"text*"），
                  // 直接 updateBlock 会因跨内容类型而清空文本
                  retypeBlockSafely(ed, block, targetType);
                  break;
                }
                case "quote": {
                  const { block } = ed.getTextCursorPosition();
                  const targetType = block.type === "quote" ? "paragraph" : "quote";
                  // 走 retypeBlockSafely 保住行内文本：当前块可能是代码块（"text*"），
                  // 直接 updateBlock 会因跨内容类型而清空文本
                  retypeBlockSafely(ed, block, targetType);
                  break;
                }
                /**
                 * + 菜单 Paragraph：光标块转普通段落
                 *
                 * ⚠️ v2026-09-23：统一走 [retypeBlockSafely]。
                 * 原实现直接 `updateBlock(block, { type: "paragraph" })` 不传 content，
                 * 在代码块上点本按钮会因跨内容类型（`"text*"` → `"inline*"`）清空代码文本
                 * —— 与 Code Block 按钮同一根因，详见该函数与 codeBlock 分支的注释。
                 */
                case "paragraph": {
                  const { block } = ed.getTextCursorPosition();
                  retypeBlockSafely(ed, block, "paragraph");
                  break;
                }
                /**
                 * 代码块（v2026-09-23 修复：文字被吞 + 插入位置）
                 *
                 * ⚠️ 症状：光标放在非空段落上（如"测试二"）点宿主工具栏的 Code Block 按钮，
                 * 该行文字**直接消失**，只留下一个空代码块。
                 *
                 * ⚠️ 根因（官方 `@blocknote/core` 0.52.1）：原实现
                 * `ed.updateBlock(block, { type: "codeBlock" })` **没有显式传 content**，
                 * 于是走 `updateBlockTr` → `updateBlockContentNode` 的"沿用旧内容"分支，
                 * 而该分支只按 **ProseMirror content 表达式字符串**判断能否沿用
                 * （`api/blockManipulation/commands/updateBlock/updateBlock.ts:200-214`）：
                 *   - paragraph / heading / quote / listItem … → `"inline*"`
                 *     （`schema/blocks/createSpec.ts:185-186` 由 `content: "inline"` 编译而来）
                 *   - codeBlock → `"text*"`
                 *     （官方 code block config 是 `content: "plain"`，
                 *      见 `blocks/Code/block.ts:65` → `createSpec.ts:187-188`）
                 * 两者字符串不相等 → 命中 `content = []`，**旧文本被主动清空**。
                 * 又因旧文本 `text("测试二")` 对 `text*` 恰好合法
                 * （`newNodeType.validContent(...)` 为 true，同一文件 259-262 行），
                 * 不会走"整体替换"兜底，而是 `replaceContentMinimal` 字符级 diff → 全删。
                 * 这也是**只有 Code Block 会吞字**的原因：其余块类型与段落同为 `inline*`，
                 * 走的都是"保留内容"分支。
                 *
                 * ⚠️ 位置语义：官方斜杠菜单的代码块项走
                 * `insertOrUpdateBlockForSlashMenu`（`extensions/SuggestionMenu/
                 * getDefaultSlashMenuItems.ts:46-83`），口径是：
                 *   - 当前块为空 → 就地 `updateBlock` 转换；
                 *   - 当前块有内容 → `insertBlocks(..., "after")` **插到下一块**，原文原样保留。
                 * 本分支据此对齐，使宿主工具栏与官方行为一致（表格/分页早已是 insert 语义）。
                 * 与官方唯一的差异：官方把"内容仅为一个 `/`"也视为空块（斜杠菜单触发字符），
                 * 本按钮无触发字符，故不采纳，避免误吞用户真实输入的 `/`。
                 */
                case "codeBlock": {
                  try {
                    const { block } = ed.getTextCursorPosition();
                    /** 当前块的 inline 内容数组；非 inline 块（表格/分页等）取不到即空数组 */
                    const content = Array.isArray(block.content)
                      ? (block.content as any[])
                      : [];

                    if (block.type === "codeBlock") {
                      // 已是代码块 → 再点一次退回普通段落（本项目 toggle 语义）
                      // ⚠️ 走 retypeBlockSafely：codeBlock("text*") → paragraph("inline*")
                      //    同样跨内容类型，不显式回传 content 就同样会被清空
                      const updated = retypeBlockSafely(ed, block, "paragraph");
                      ed.setTextCursorPosition(updated, "start");
                      break;
                    }

                    if (content.length === 0) {
                      // 空块：就地转换（官方口径；内容本为空故无损失）
                      const updated = retypeBlockSafely(ed, block, "codeBlock");
                      ed.setTextCursorPosition(updated, "start");
                    } else {
                      // 有内容：在下一行插入代码块，原文保持不动（官方 insertOrUpdateBlockForSlashMenu 口径）
                      // ⚠️ insertBlocks 内部只做 tr.step，不移动光标，必须显式定位，
                      //    否则光标停在原行、用户会误以为"没生效"
                      const [newBlock] = ed.insertBlocks(
                        [{ type: "codeBlock" }],
                        block,
                        "after",
                      );
                      ed.setTextCursorPosition(newBlock, "start");
                    }
                  } catch (e: any) {
                    // 静默失败会让"点了没反应"零线索，统一上行诊断
                    sendUp({ type: "error", message: `codeBlock: ${e.message}` });
                  }
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
    /**
     * v2026-09-22：`ready` 除构建指纹外，再带一个**源码内容哈希**（`srcHash`）。
     * 指纹只说明"哪一版"，哈希才能说明"是不是当前源码编出来的"——宿主打进 logcat，
     * 与 `blocknote-probe/src/editor/` 现算的哈希一眼可比。
     */
    sendUp({ type: "ready", build: BUILD_FINGERPRINT, srcHash: SRC_HASH });
    return () => {
      window.BlockNoteEditorHost = undefined;
    };
  }, [pushChanged, pushChangedNow, pushUndoState, getHistoryCommands]);

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
      linkPanel={linkPanel}
      onLinkPanelClose={() => {
        setLinkPanel((p) => (p.open ? { ...p, open: false } : p));
        /**
         * 无条件上行一次：用户点面板外部 / 按 Esc 关闭时，宿主无从得知
         * （面板渲染在 WebView 内部的 React 树上）。宿主据此对齐本地状态，
         * 才能正确处理「面板收起后是否把软键盘弹回来」。
         */
        sendUp({ type: "linkPanelClosed" });
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
  /** 链接面板状态（v2026-09-24）：宿主下行 openLinkPanel / closeLinkPanel 驱动 */
  linkPanel: { open: boolean; range: { from: number; to: number }; url: string; text: string };
  /** 链接面板关闭回调：用户点外部 / 按 Esc / 提交表单后，经 `linkPanelClosed` 通报宿主 */
  onLinkPanelClose: () => void;
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
    /**
     * 自链接文字编辑同步（v2026-09-24 新增）
     *
     * 注册 {@link linkHrefSyncExtension}：「只填 URL」插入的自链接
     * （显示文本 == href）被用户在编辑器里直接改文字时，href 同步跟随——
     * 改文字即改链接本身，保存/重进后链接指向新地址。详见扩展文件头注释。
     */
    extensions: [linkHrefSyncExtension],
    /**
     * 链接点击接管（v2026-09-24）
     *
     * 配置本回调即**关闭官方默认的 `window.open`**（官方源码注释原文：
     * "If provided, the default open-on-click behavior is disabled and this
     * function is called instead"）——那是 Android WebView 下"点链接没反应 /
     * 编辑器被顶掉"两条病根的源头，详见 {@link handleLinkClick}。
     */
    links: {
      onClick: handleLinkClick,
    },
  });

  /**
   * 禁用官方 autolink / 粘贴成链（v2026-09-24 新增，用户决策）。
   *
   * **口径**：手打的任何链接（`https://…`、`www.…`）都不应被识别成可点击
   * 链接——**只有链接编辑器（链接面板）写的才识别**。
   *
   * BlockNote 的 Link tiptap 扩展内置两个自动成链插件：
   * - `autolink`：输入时把 URL 文字实时转成 link mark；
   * - `handlePasteLink`：粘贴 URL 文本时成链。
   * 两者都在编辑期自动把"手打文本"升级为链接，与本口径冲突。
   *
   * **为什么不用官方 `links.isValidLink` 钩子**：它同时被 mark 的
   * `renderHTML` 消费——返回 false 时渲染出的 `<a>` 的 `href` 会被置空
   * （link.ts 的 false 分支 `mergeAttributes({ href: "" }, …)`），**面板链接
   * 的点击回调会拿到空地址**，副作用不可接受。
   *
   * **做法**：编辑器创建后用 tiptap 公开 API `unregisterPlugin` 按插件 key
   * 注销两个插件（幂等；只影响"自动成链"，link mark 本身与面板
   * createLink/editLink 完全不受影响）。
   */
  useEffect(() => {
    const tiptapEditor = (editor as any)?._tiptapEditor;
    if (!tiptapEditor) return;
    tiptapEditor.unregisterPlugin("autolink");
    tiptapEditor.unregisterPlugin("handlePasteLink");
  }, [editor]);

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
          {/*
            覆盖官方链接工具栏（v2026-09-24）
            —— 只替换「打开」按钮的行为：官方那版调 `window.open(url, "_blank")`，
            在 Android WebView（`setSupportMultipleWindows` 默认 false）下被静默丢弃，
            点「打开」毫无反应。改为经桥上行 `openLink` 交宿主送系统浏览器。
            无打开按钮之外的任何改动，详见 {@link ProjectLinkToolbar}。
          */}
          <LinkToolbarController linkToolbar={ProjectLinkToolbar} />
          {/*
            链接面板（v2026-09-24）
            —— 底部工具栏 🔗 按钮的弹层。内容用官方 `EditLinkMenuItems`，
            定位用官方 `PositionPopover`（锚点跟随选区），与 WebView 内官方
            FormattingToolbar 的「链接」按钮收敛为同一套实现。
            详见 {@link LinkEditPanel}。
          */}
          <LinkEditPanel
            open={props.linkPanel.open}
            range={props.linkPanel.range}
            initialUrl={props.linkPanel.url}
            initialText={props.linkPanel.text}
            onClose={props.onLinkPanelClose}
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

/**
 * 自定义链接工具栏（v2026-09-24）
 *
 * **唯一改动**：把官方的「打开」按钮换成走桥的版本。
 *
 * 官方 `OpenLinkButton`（`@blocknote/react` 的
 * `components/LinkToolbar/DefaultButtons/OpenLinkButton.tsx`）实现是
 * `window.open(sanitizeUrl(url, window.location.href), "_blank")`——
 * 在 Android WebView 里 `setSupportMultipleWindows` 默认 false，
 * 带 `_blank` 的 `window.open` **被 Chromium 静默丢弃**（不导航、不报错、无日志），
 * 于是点「打开」看起来毫无反应。宿主侧另需 `onCreateWindow` 才能接管，
 * 但那条通道拿不到"用户确实点了打开"的语义，且与 WebView 的新窗口策略耦合。
 *
 * 因此改为**直接经桥上行 `openLink`**，由宿主 `Intent.ACTION_VIEW` 送系统浏览器
 * ——链路最短、语义最明确、与 WebView 的窗口策略完全解耦。
 *
 * **其余两个按钮照用官方**（`EditLinkButton` / `DeleteLinkButton`）：
 * 编辑按钮内含官方的 URL 表单弹层（`EditLinkMenuItems`），删除按钮走
 * `deleteLink(range.from)`；两者与本项目既有 `deleteLink` / `format.createLink`
 * 桥能力并存不冲突（它们作用于 JS 侧文档，不涉及导航）。自绘会丢掉这些细节，
 * 而本次改动与它们无关，最小影响面。
 *
 * ⚠️ 官方默认布局是 `[编辑, 打开, 删除]`（`LinkToolbar.tsx` 的 children 顺序），
 * 此处保持一致，避免用户肌肉记忆错位。
 *
 * ⚠️ **不套官方 `<LinkToolbar>` 组件本身**：它只接受 `LinkToolbarProps`、**不接受
 * `className`**，且内部把 `"bn-toolbar bn-link-toolbar"` 硬编码在
 * `Components.LinkToolbar.Root` 上。若沿用它就得把整份 props 透传、又无法加类名。
 * 直接渲染 `Components.LinkToolbar.Root` 更直接，且类名与官方逐字一致
 * （样式零偏移——`bn-link-toolbar` 的定位/间距规则全部照旧命中）。
 */
function ProjectLinkToolbar(props: LinkToolbarProps) {
  const Components = useComponentsContext()!;
  return (
    <Components.LinkToolbar.Root className="bn-toolbar bn-link-toolbar">
      <EditLinkButton
        url={props.url}
        text={props.text}
        range={props.range}
        setToolbarOpen={props.setToolbarOpen}
        setToolbarPositionFrozen={props.setToolbarPositionFrozen}
      />
      <Components.LinkToolbar.Button
        className="bn-button"
        label="打开链接"
        mainTooltip="打开链接"
        isSelected={false}
        onClick={() => {
          const href = sanitizeOutboundUrl(props.url);
          if (href === "") return;
          sendUp({ type: "openLink", url: href });
        }}
        icon={<OpenLinkIcon />}
      />
      <DeleteLinkButton
        range={props.range}
        setToolbarOpen={props.setToolbarOpen}
      />
    </Components.LinkToolbar.Root>
  );
}

/**
 * 「打开」按钮的图标（外链箭头 + 方框）
 *
 * 官方用的是 mantine 图标库的图标，本项目未引入该依赖的独立包，
 * 故按官方图标的视觉（方框 + 右上出箭头）自绘一个 16×16 的内联 SVG：
 * 尺寸、`currentColor` 取色方式都与官方图标一致，
 * 在深/浅主题下均自动跟随文字色。
 */
function OpenLinkIcon() {
  return (
    <svg
      width="16"
      height="16"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
      <polyline points="15 3 21 3 21 9" />
      <line x1="10" y1="14" x2="21" y2="3" />
    </svg>
  );
}

/**
 * 链接面板（v2026-09-24 新增）—— 底部工具栏 🔗 按钮的弹层
 *
 * **背景**：此前底部按钮弹的是宿主自绘的 Compose `AlertDialog`，与 WebView 内官方
 * `CreateLinkButton` 的弹层构成**两套并行实现**——外观、校验规则、提交路径各不相同
 * （自绘版判 `!= "https://"`；官方用 `VALID_LINK_PROTOCOLS` 补全协议）。现收敛为一套：
 * 底部按钮也走官方那套表单。
 *
 * **为什么能复用官方组件**：官方 `CreateLinkButton`（FormattingToolbar 里的「链接」）
 * 与 `EditLinkButton`（LinkToolbar 里的「Edit link」）渲染的是**同一个**
 * `EditLinkMenuItems`。二者唯一的结构差异在 popover 的控制方式：
 * - `EditLinkButton`：非受控（只给 `onOpenChange`）→ **无法从外部打开**；
 * - `CreateLinkButton`：受控（`open={showPopover}`）→ 这正是可被外部驱动的钩子。
 *
 * 本组件即照 `CreateLinkButton` 的结构，把开关从「点按钮」换成宿主下行的
 * `openLinkPanel` / `closeLinkPanel`。
 *
 * **定位**：官方 `CreateLinkButton` 用的是 `Generic.Popover.Root`，而 mantine 那层
 * 实现里 `withinPortal={!!portalRoot}` 且调用方未传 `portalRoot` ⇒ 走 `withinPortal=false`，
 * popover 留在原地 DOM & 被祖先的 `overflow` 裁剪。故此处改用官方 `PositionPopover`：
 * 它以**文档位置区间**（`posToDOMRect`）为锚点，并默认 Portal 到 `editor.portalElement`，
 * 既不被裁剪，锚点语义又与「Edit link / 打开 / 删除」工具条一致（同为 `top-start`），
 * 位置表现与用户已熟悉的那个面板对齐。
 *
 * ⚠️ **不显式传 `middleware`（`offset` / `flip`）**：这两个函数来自
 * `@floating-ui/react`，而它只是 `@blocknote/react` 的**传递依赖**、本项目未在
 * `package.json` 声明（同 `prosemirror-state` 的情形）。直接 import 会在依赖树变动时
 * 埋下解析隐患，故只传 `placement`，其余定位交给官方默认值。
 *
 * **与已有桥能力的关系**：`EditLinkMenuItems` 提交走 `editLink(url, text, range.from)`。
 * `range` 由宿主经 `saveSelection` 快照后随命令带回（见 `savedSelectionFrom/To`），
 * 故无选区时 `from == to`，行为与官方在浏览器里点「链接」完全一致。宿主原有的
 * `format.createLink` / `deleteLink` 桥能力保留不动（供其它入口使用），本次不删。
 */
function LinkEditPanel(props: {
  open: boolean;
  /** 面板锚点与提交落点：`saveSelection` 快照回来的区间（-1 = 无有效快照） */
  range: { from: number; to: number };
  /** 预填的已有链接 URL（空串 = 新建） */
  initialUrl: string;
  /** 预填的显示文字 */
  initialText: string;
  onClose: () => void;
}) {
  const editor = useBlockNoteEditor<any, any, any>();

  /**
   * 面板的 Portal 宿主（v2026-09-24 定案）。
   *
   * ★ **既不能用 `document.body`、也不能用 `editor.portalElement`**，两者各缺一半：
   *
   * | 目标 | 逃出 `overflow` 裁剪 | 继承 `.bn-mantine` 的 CSS 变量 |
   * |---|---|---|
   * | `editor.portalElement`（默认） | ✗ 在 `bn-container` 内，被祖先裁 | ✓ |
   * | `null` → `document.body` | ✓ | **✗ → 图标渲染成乱码** |
   * | **本容器（自建）** | ✓（挂在 `document.body` 下） | ✓（自带两个类名） |
   *
   * **为什么必须带 `bn-mantine`**：`mantineStyles.css:133` 的注释写明
   * 「Mantine default CSS variables, scoped to `.bn-mantine` element」——
   * **全部 `--mantine-*` 变量都直接定义在 `.bn-mantine` 这个选择器上**
   * （第 135、401、532 行三条规则，其中 401 / 532 分别是
   * `.bn-mantine[data-mantine-color-scheme="dark"|"light"]` 的暗/亮分支）。
   * 本轮实测：传 `portalElement={null}` 后 `document.body` 不是 `.bn-mantine`
   * ⇒ 变量全失效 ⇒ `TextInput` 的 `leftSection` 尺寸/定位塌掉 ⇒ 左侧图标 SVG 被挤压，
   * 只露出中间几个字母残片（真机截图里 `Edit URL` 前显示成 "eat"）。
   *
   * **为什么还要带 `bn-root`**：BlockNote 的 `--bn-*` 主题变量（边框/圆角/阴影/菜单底色）
   * 同样挂在 `.bn-root` 上；`editor.portalElement` 本身也是
   * `mergeCSSClasses("bn-root", …)`（`BlockNoteView.tsx:198`）。缺它则面板无边框、无背景。
   *
   * 容器挂在 `document.body` 下（而非编辑区内部）⇒ 天然逃出编辑区滚动容器的 `overflow`，
   * 这就是上一轮「面板被裁剪」的根治手法，只是当时漏了继承链。
   *
   * ⚠️ 容器本身**不设** `position` / `overflow` / `transform`：它只作为 Portal 挂点，
   * 内部 `GenericPopover` 自己用 `position: fixed` + 内联 `left/top` 定位，
   * 父级一旦引入 `transform`/`filter` 会让 `fixed` 退化为相对该父级定位、破坏定位。
   */
  const panelHost = useMemo(() => {
    if (typeof document === "undefined") return undefined;
    const el = document.createElement("div");
    el.className = "bn-root bn-mantine";
    /**
     * 色彩方案必须用 **`data-mantine-color-scheme`**（不是 `data-color-scheme`）：
     * `.bn-mantine[data-mantine-color-scheme="dark"|"light"]` 才是承载
     * `--mantine-*` 明暗变量的选择器（mantineStyles.css 第 401 / 532 行）。
     *
     * ⚠️ **不能只读 `editor.portalElement` 的属性**：那两处属性由 `BlockNoteView`
     * 在 `useEffect` 里赋值，而本 `useMemo` 可能在赋值之前就跑完（时序不稳）。
     * 故改为**从活着的 `.bn-mantine` 元素上现查**——它一定已挂载且带齐属性；
     * 查不到再回落 `data-color-scheme` 与系统偏好，保证任何时序下都有合理取值。
     */
    const liveMantine = document.querySelector(".bn-mantine[data-mantine-color-scheme]");
    const scheme =
      liveMantine?.getAttribute("data-mantine-color-scheme") ??
      editor?.portalElement?.getAttribute("data-color-scheme") ??
      (window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light");
    if (scheme) el.setAttribute("data-mantine-color-scheme", scheme);
    /**
     * **容器样式**（内联写，不依赖样式表加载时序）。
     *
     * ★★ **`width:0` 是错的，会让面板宽度塌陷**（v2026-09-24 实测修正）。
     *
     * 面板尺寸不由内容撑开，而由**包含块的可用空间**决定：
     * - `.bn-form-popover` 自身**没有 width**（库 `blocknoteStyles.css:218`），
     *   它靠内部 `.bn-form-popover .mantine-TextInput-root { width:300px }`
     *   （同文件 17-20 行）把父级撑到 300px；
     * - 但 `GenericPopover` 的浮层 div 是 `display:flex` + floating-ui 给的
     *   **`position:absolute; width:auto`** ⇒ **可用宽度 = 包含块宽度 − left**；
     * - 容器 `width:0` ⇒ 可用宽度近乎 0 ⇒ flex 项被压缩 ⇒ 面板只剩
     *   「锚点到容器右缘」那一小段（真机截图：左缘贴锚点、右缘贴编辑区右边界）。
     *
     * 正确做法：容器**铺满视口**，给内部绝对定位元素留出完整可用空间。
     * 用 `position: fixed; inset: 0` 而非默认 static，理由：
     * ① 它明确成为包含块，且内容区与视口**完全重合**（左上角对齐），
     *    于是 floating-ui 按视口算出的 `left/top` 落进来后坐标不变；
     * ② `fixed` 元素**脱离文档流**，不会把 `body` 撑高、不产生滚动条。
     *
     * ⚠️ **绝不能设 `transform` / `filter` / `will-change`**：那会让内部 `fixed`
     * 退化为相对本容器定位，浮层位置全崩。
     * ⚠️ **必须设 `pointer-events: none`（v2026-09-24 二次修正，曾误删）**：
     * 容器 `position:fixed; inset:0` 铺满视口后是一个**常驻的全屏透明层**——
     * 「透明 ≠ 点击穿透」，CSS 命中测试不看透明度。它挂在 `document.body`
     * 下、DOM 顺序靠后且是定位元素 ⇒ paint 在编辑内容之上 ⇒ **吃掉全屏所有
     * 触摸**：点正文 → 命中本容器 → 编辑器收不到事件 → 光标无法聚焦、软键盘
     * 呼不出（真机故障：整个编辑页聚焦失效）。上一轮删掉 `none` 是错误的——
     * 「输入框要能点」的正确解法不是让容器可点，而是**分层恢复**：
     * 容器 `none` 挡一切 + 浮层自身经 `elementProps` 恢复 `auto`（见下方
     * `<PositionPopover elementProps=…>`）。
     * 附带收益：`none` 后点击穿透回编辑器/页面本身，`useDismiss` 的
     * 「点外部关闭」判定也回归正常语义（点在浮层外 → 关）。
     * ⚠️ **不能设 `display:none` / `visibility:hidden` / `width:0`**：子树无法
     * 测量或可用空间不足，floating-ui 的「先测后定」会直接失效。
     */
    el.style.position = "fixed";
    el.style.inset = "0";
    el.style.pointerEvents = "none";
    document.body.appendChild(el);
    return el;
  }, [editor]);

  useEffect(() => {
    return () => {
      panelHost?.remove();
    };
  }, [panelHost]);

  /**
   * 色彩方案校准（v2026-09-24）。
   *
   * `panelHost` 在 `useMemo` 里创建，当时编辑器可能还没挂载完
   * （`BlockNoteView` 的属性是挂载后在 `useEffect` 里写的）⇒ scheme 会读到 fallback。
   * 这里在**每次打开面板时**再对齐一次活着的 `.bn-mantine` 元素，
   * 保证暗色主题下面板不会以亮色变量绘制（否则会看到一帧白底闪烁）。
   * 用 `useLayoutEffect` 语义等价的做法：该 `useEffect` 依赖 `props.open`，
   * 在面板绘制前完成属性写入（React 会在 commit 后、paint 前同步跑 layout effect；
   * 此处用普通 `useEffect` + 仅改属性（不触发重渲）已足够，因为 Portal 内容
   * 依赖的是 CSS 变量而非 React 状态）。
   */
  useEffect(() => {
    if (!panelHost || !props.open) return;
    const live = document.querySelector(".bn-mantine[data-mantine-color-scheme]");
    const scheme = live?.getAttribute("data-mantine-color-scheme");
    if (scheme && panelHost.getAttribute("data-mantine-color-scheme") !== scheme) {
      panelHost.setAttribute("data-mantine-color-scheme", scheme);
    }
  }, [panelHost, props.open]);

  /**
   * 点外部关闭兜底（v2026-09-24 新增；官方 `useDismiss` 在本环境失效）。
   *
   * **根因（floating-ui.react.mjs:2727 实证）**：`useDismiss` 的 outside 判定
   * ```
   * if (isEventTargetWithin(event, elements.floating)
   *   || isEventTargetWithin(event, elements.domReference) || ...) return;
   * ```
   * **点在 `domReference` 内不算「外部」**。而 `GenericPopover` 会把
   * `editorDOMElement.firstElementChild`（**整个编辑器内容根**）设为
   * `domReference`（`refs.setReference`，FocusManager disabled 时必设）——
   * 于是**点击正文任何位置都被排除在 outside 之外**，dismiss 永不触发
   * （真机日志佐证：关闭时从无 `onOpenChange: open=false` 的诊断行）。
   * 官方 FormattingToolbar 不踩这个坑：它靠「选区变化 → show=false」关闭，
   * 不依赖 dismiss；本面板由宿主命令驱动、不跟随选区，故必须自补关闭时机。
   *
   * **兜底方案**（照 `EmojiGridPanel` 既有先例）：document 级 `click`
   * **capture** 监听——点击目标不在面板内容（`.bn-popover-content`）内即关。
   * capture 先于一切目标处理器，PM 不会拦；同一次 click 继续传给编辑器
   * （移动光标）正是期望行为。与官方 dismiss 并存且幂等（dismiss 在
   * 「面板外非编辑器区域」仍会先触发，二者都落到同一个 onClose）。
   *
   * ⚠️ 依赖数组刻意不写：每次渲染重挂监听，保证闭包里的 `props.onClose`
   * 恒为最新引用（与 EmojiGridPanel 同款约定）。
   */
  useEffect(() => {
    if (!props.open) return;
    const onDocClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement | null;
      if (target && !target.closest(".bn-popover-content")) {
        props.onClose();
      }
    };
    document.addEventListener("click", onDocClick, true);
    return () => document.removeEventListener("click", onDocClick, true);
  });

  /**
   * 「只填 URL、显示文字留空」→ 直接以 URL 作为显示文字插入（v2026-09-24 新增）。
   *
   * **官方为什么插不进去**：`EditLinkMenuItems` 的「完成」→ extension.editLink →
   * `StyleManager.editLink`（core `StyleManager.ts:219-239`）。光标无选区
   * （新建链接，from=to）且 `text=""` 时：
   * - `existingText = textBetween(from, to) = ""`，
   *   `text !== existingText` 为 **false** → `insertText` 被跳过（不插文字）；
   * - `tr.addMark(from, from + 0, mark)` —— **0 长度区间加 mark 等于什么都没做**。
   * 即「无选区 + 空文字」是官方提交逻辑的真空档，点击完成后静默无效。
   *
   * **修法（面板期包装 `editor.editLink`，官方组件零改动）**：
   * 提交链路为 `EditLinkMenuItems → extension.editLink → editor.editLink`，
   * 在链路末端包装：`text` 为空且目标位置不在已有链接上（= 新建链接）时，
   * 以 URL 兜底为显示文字。光标在已有链接上（编辑模式）时保持官方行为不动，
   * 影响面最小。面板关闭时还原原方法。
   */
  useEffect(() => {
    if (!props.open) return;
    const original = editor.editLink;
    editor.editLink = (url: string, text: string, position?: number) => {
      const at = position ?? editor.transact((tr) => tr.selection.anchor);
      /** 目标位置已在链接内 = 编辑已有链接，text 留空走官方语义（不兜底） */
      const inExistingLink = !!editor.getLinkMarkAtPos(at + 1);
      const resolvedText = !text && !inExistingLink ? url : text;
      return original.call(editor, url, resolvedText, position);
    };
    return () => {
      editor.editLink = original;
    };
  }, [props.open, editor]);

  /**
   * 面板打开瞬间的 props 快照。
   *
   * ⚠️ 必须**冻结**：面板开着时编辑器仍会因用户操作上行新的选区 / `linkUrl`，
   * 若实时读 props，输入框内容与锚点会在用户打字期间被外部变化覆写。
   * 只在 `props.open` 由 false → true 的那一刻重取。
   *
   * ★ **不能用 `useRef` + `useEffect` 承载**（v2026-09-24 修复「第一次点不出现」）。
   * `useEffect` 在渲染**之后**才跑，`ref` 的赋值又**不触发重渲染** ⇒ 首次打开时：
   * 第 1 帧 `snapRef.current` 仍是初始值（`range.from === -1`）⇒ `anchorPosition`
   * 为 `undefined` ⇒ `PositionPopover` 按 `position !== undefined && children`
   * **不渲染任何 children**；`useEffect` 随后更新了 ref，却因为没有 setState
   * 而不会再来一帧 ⇒ 面板在整个「打开」周期里始终是空的。
   * 直到用户**第二次**点击（`props.open` 再次翻转）才补上——这正是
   * 「第一次不出现、再点才出现」的根因之一。
   *
   * 用 `useMemo([props.open])` 在**同一帧内**取快照，首帧即可拿到正确锚点。
   */
  const snap = useMemo(
    () => ({ range: props.range, url: props.initialUrl, text: props.initialText }),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 仅在 open 翻转时重取快照
    [props.open],
  );

  /**
   * 锚点位置：用「快照区间的起点到起点」——无选区时光标处弹出，有选区时落在选区起始位置
   * （与官方 FormattingToolbar 的呈现一致）。
   *
   * ⚠️ 三个前置条件缺一不可，否则 `PositionPopover` 内部的 `posToDOMRect` 会抛错
   * （该异常发生在渲染期，会直接卸载编辑器子树 → 编辑区变空白）：
   * ① 面板已打开；② 区间有效（`from >= 0`，即宿主/JS 拿到了选区快照）；
   * ③ `prosemirrorView` 已就绪。
   */
  const anchorPosition = useMemo(() => {
    if (!props.open || snap.range.from < 0) return undefined;
    if (!editor?.prosemirrorView) return undefined;
    return { from: snap.range.from, to: snap.range.from };
  }, [props.open, snap.range.from, editor]);

  return (
    <PositionPopover
      position={anchorPosition}
      /**
       * Portal 到**自建容器**（见 `panelHost` 的注释）：
       * 同时拿到「逃出 `overflow` 裁剪」+「`.bn-mantine` / `.bn-root` 的 CSS 变量继承」。
       * 不能用 `null`（丢变量 → 图标乱码），也不能用 `undefined`（落回
       * `editor.portalElement`，在 `bn-container` 内被裁）。
       */
      portalElement={panelHost ?? null}
      /**
       * ★ `elementProps.style.pointerEvents = "auto"` 与容器 `none` 是**一对**
       * （v2026-09-24 修复「编辑页无法聚焦」）：
       *
       * `panelHost` 铺满视口且 `pointerEvents:"none"`（挡不住点击了），
       * 但浮层是这个 `none` 容器的子节点——`pointer-events` 是**可继承属性**，
       * 不显式恢复的话浮层（连同里面的两个输入框）也收不到任何点击。
       * `GenericPopover` 会把 `elementProps` 展开进浮层根 div 的属性里
       * （其源码 `mergedProps = { ...props.elementProps, style: {...} }`，
       * `pointerEvents` 不在后续覆盖项中，能存活）⇒ 在这里恢复 `auto`
       * 即可让面板本身恢复交互，容器其余区域仍保持点击穿透。
       */
      elementProps={{ style: { pointerEvents: "auto" } }}
      /**
       * ★ `focusManagerProps={{ disabled: true }}` 是**必须**的（v2026-09-24 修复）。
       *
       * 官方 `PositionPopover` 底层是 `GenericPopover`，后者无条件套了一层
       * `FloatingFocusManager`，默认行为是「弹出时把焦点移入面板 + 焦点离开面板即关闭」
       * （`closeOnFocusOut`）。本场景下这套机制会**在打开当帧就把自己关掉**：
       *
       * ① 面板内是 `EditLinkMenuItems` 的两个 Mantine `TextInput`（`autoFocus`），
       *    `FloatingFocusManager` 尝试把焦点交给它们 ⇒ 编辑器 `contenteditable` 失焦
       *    （真机日志实证：`openLinkPanel` 后 ~20ms 即出现 `editorFocus=false`）；
       * ② Android WebView 里焦点转移的 `relatedTarget` 常为 `null`，
       *    `closeOnFocusOut` 据此判定「焦点跑到面板外」⇒ 立刻 `onOpenChange(false)`；
       * ③ 结果：**第一次点只有一帧、肉眼看不到面板**，第二次点因焦点已在面板内不再搬移
       *    才正常显示——这正是「第一次不出现、再点才出现」的根因。
       *
       * 面板本身不需要焦点管理：它是宿主命令驱动的受控浮层，关闭权在 `props.open`；
       * 点外部 / 按 Esc 由 `useDismiss`（`getFloatingProps` 注入的监听）继续覆盖。
       */
      focusManagerProps={{ disabled: true }}
      useFloatingOptions={{
        open: props.open,
        onOpenChange: (open) => {
          /**
           * 关闭一律经 `onClose` 回宿主：
           * - 用户提交表单 → 面板自关，同时宿主收到 `closeLinkPanel` 收尾（幂等）；
           * - 用户点外部 / 按 Esc → 官方 dismiss 行为关闭，宿主无从得知，
           *   故由这里上行 `linkPanelClosed`（见 `onClose` 的实现）。
           * 只处理「关」不处理「开」——开只能由宿主命令驱动，避免误开。
           */
          if (!open) props.onClose();
        },
        placement: "top-start",
        /**
         * ★★ **防裁剪三件套 middleware 是官方标配，缺了就裁**（v2026-09-24 实证修复）。
         *
         * 官方 `FormattingToolbarController.tsx:101` 传的是
         * `middleware: [offset(10), shift(), flip()]`，本面板此前**一个都没传**，
         * 后果（真机日志 + 截图实证）：
         * - 无 `flip()`：锚点（光标）在编辑区顶部时面板仍坚持放上方，
         *   顶出视口 → **上边缘被裁**；
         * - 无 `shift()`：锚点靠右时面板（`top-start` 左对齐锚点）右半截
         *   越出视口右缘 → **右边缘被裁**；
         * - 无 `offset()`：面板紧贴锚点 0 间距，观感生硬且与官方不一致。
         *
         * 三者全为 floating-ui 内置 middleware，与官方工具栏同参数，不另发明。
         */
        middleware: [offset(10), shift(), flip()],
      }}
    >
      <div
        className="bn-popover-content bn-form-popover"
        /**
         * ★ **宽度必须显式给，不能靠内容撑**（v2026-09-24 修复「面板宽度塌陷」）。
         *
         * **官方原本是怎么撑开的**：库样式 `blocknoteStyles.css:17-20`
         * ```
         * .bn-form-popover .mantine-TextInput-root { width: 300px; }
         * ```
         * 即 `.bn-form-popover` 自身**不设宽度**，靠内部 TextInput 的 `300px`
         * 把父级顶开。**那个机制只在「浮层不受外部宽度约束」时成立**——
         * 我们的容器修好后它本来也能工作。
         *
         * **为什么还要显式写死**：本面板的两个前置条件（Portal 到自建容器 +
         * 官方 `.bn-form-popover` 的 `display:flex` 由 `GenericPopover` 注入）
         * 让「谁撑谁」依赖 floating-ui 的可用空间推断，太脆弱。写死宽度后
         * **不再依赖任何上下文推断**，与 `flexShrink:0` 配合可彻底钉死。
         *
         * **取值口径（对齐官方，不另创尺寸）**：
         * `TextInput-root 300px` + `.bn-form-popover` 左右 padding 各 2px
         * （库第 14 行 `padding: 2px`）+ 左右 border 各 1px（库第 8 行
         * `border: var(--bn-border)`，本项目主题为 1px）**= 306px**。
         * 外层再包 `boxSizing:border-box`，故 `width:306px` 即最终外框宽度。
         * 小屏用 `calc(100vw - 32px)` 收缩（左右各留 16px 安全边距），
         * `100vw` 在 WebView 等于可视宽度，无需读 `window.innerWidth`
         * （避免键盘弹出导致取值过时）。
         */
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 4,
          boxSizing: "border-box",
          width: "min(306px, calc(100vw - 32px))",
          maxWidth: "calc(100vw - 32px)",
          /** 覆盖库的 `min-width:145px`，避免与上面的宽度计算打架 */
          minWidth: 0,
          /**
           * 面板自身是 flex item，禁止收缩 —— 否则外层 flex 容器（`fit-content`）
           * 在空间不足时仍会把它压窄，写死的 `width` 会被 `flex-shrink` 抵消。
           */
          flexShrink: 0,
        }}
        /**
         * 阻断冒泡到编辑器：面板虽经 `PositionPopover` Portal 到 `editor.portalElement`，
         * 但 React 合成事件**沿 fiber 树传播**（Portal 只改 DOM 归属），
         * 不拦住会让点击穿透到编辑器导致光标/选区变化。
         */
        onClick={(e) => e.stopPropagation()}
        onMouseDown={(e) => e.stopPropagation()}
      >
        {/*
          `showTextField` 保持默认 `true`：本项目链接面板一直提供「显示文字」输入框
          （官方 CreateLinkButton 传 `false` 隐藏它，此处刻意不跟随——留住既有能力）。
        */}
        <EditLinkMenuItems
          url={snap.url}
          text={snap.text}
          range={{ from: snap.range.from, to: snap.range.to }}
          setToolbarOpen={(open) => {
            if (!open) props.onClose();
          }}
        />
      </div>
    </PositionPopover>
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
