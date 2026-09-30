import "@blocknote/mantine/style.css";
import { BlockNoteView } from "@blocknote/mantine";
import {
  DeleteLinkButton,
  EditLinkButton,
  EditLinkMenuItems,
  FormattingToolbar,
  LinkToolbarController,
  PositionPopover,
  useBlockNoteEditor,
  useComponentsContext,
  useCreateBlockNote,
  useEditorState,
  useExtension,
  useExtensionState,
  type FloatingUIOptions,
  type LinkToolbarProps,
} from "@blocknote/react";
import { BlockNoteEditor, blockHasType, defaultProps } from "@blocknote/core";
import { NodeSelection, Selection } from "prosemirror-state";
import type { DefaultProps } from "@blocknote/core";
import { FormattingToolbarExtension } from "@blocknote/core/extensions";
// floating-ui 的防裁剪三件套：与官方 FormattingToolbarController 完全同款
// （offset 离锚点 10px、shift 贴边回拉治右裁剪、flip 上放不下翻转治上裁剪）。
// ★ 注意：`flip` **必须保留** —— 宿主避让逻辑（applyShift）是单向的，只把
// 浮层往键盘上方推、不保证不捅穿视口上缘；`flip` 是「上方确实不够时翻到
// 下方」的唯一兜底。曾试过 `flip({ fallbackPlacements: [] })` 禁用翻转，
// 后果是工具条被避让逻辑顶出视口上缘后**没有任何机制救它**（真机实证：
// 「编辑页看不到工具条，无法继续操作」）。
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

/** 块对齐 → 浮层 placement 映射（照抄官方 FormattingToolbarController） */
const textAlignmentToPlacement = (
  textAlignment: DefaultProps["textAlignment"],
) => {
  switch (textAlignment) {
    case "left":
      return "top-start";
    case "center":
      return "top";
    case "right":
      return "top-end";
    default:
      return "top-start";
  }
};

/**
 * ★★ **常驻**裁剪放开样式表（v2026-09-30 第十五轮真机定案）。
 *
 * ## 它解决什么
 * `.bn-formatting-toolbar` 自身带 `overflow: auto`（Mantine 注入）、高仅 35px，
 * 而 Rename 弹层 `.bn-form-popover` 是它的**子元素**、位于它**上方约 40px**
 * ⇒ 弹层完全落在工具条矩形之外，被 `overflow: auto` **直接裁掉**。
 * 放开为 `visible` 后弹层才可见（第十四轮 `POP(clip)`/`POP(stack)` 铁证）。
 *
 * ## 为什么必须是「模块级 + 常驻」而不是写在避让 effect 里
 * - 该 effect 依赖 `[imeHeightPx, imeGapDp]`，**键盘动画期会重建上百次**；
 *   若在 effect 体内创建样式表，就会随每次重建反复"移除→插入"，
 *   造成样式表**瞬时失效**（弹层闪断）。
 * - 且效果上「工具条容器容不下上方 40px 的弹层」是**结构性**问题，
 *   **与键盘在不在无关**（弹层无键盘遮挡时同样不该被裁）。
 * ⇒ 提为模块级单例：**只创建一次**，随页面存活，cleanup 时也不移除。
 *
 * ## 真机闪断实录（第十五轮，用户反馈「弹层出现了一下又消失」）
 * | 时间 | ime | occluded | 状态 |
 * |---|---|---|---|
 * | 36.501 | 15.3dp | **−56.7** | ⚠️ `occluded<=0` → `clearAll()` 清空整表 |
 * | 36.517 | 52.0dp | **−20.0** | ⚠️ 仍被清空 |
 * | 36.532 | 94.9dp | +22.9 | ✅ 规则恢复 → 弹层才可见 |
 * 键盘刚从 0 升起时 `ime < imeGapDp`（还没吃掉底部工具栏那段 gap）⇒ `occluded<=0`
 * ⇒ `clearAll()` 把 `overflow: visible` 一起清掉 ⇒ 弹层被裁 31ms ⇒ 视觉「闪一下」。
 *
 * ## 安全性
 * 工具条内容（若干按钮）**不滚动**，去掉 `overflow: auto` 外观零变化；
 * `!important` 压过 Mantine 注入；不改 React 结构 / 内联 style。
 *
 * ## ★★ 第十七轮追加：还要覆盖浮动包裹层的 `will-change: transform`
 *
 * ### 新证据（真机 `POP(comp)` 探针）
 * `POP(clip)` 全程 `NONE`（祖先 `overflow` 确实都是 `visible`），
 * 但 `POP(comp)` 报出唯一一个可疑祖先：
 * ```
 * div.(noclass)[op=1 wc=transform pos=absolute z=40 rect=(20,593,291x35)]
 * ```
 * 它正是 floating-ui 给**工具条**创建的**浮动包裹层**
 * （尺寸 291x35 与工具条一致、`z=40` 与本项目 `elementProps.style.zIndex=40` 一致），
 * 来源是 `@floating-ui/react-dom` 第 220 行写死的**内联** `willChange: 'transform'`。
 *
 * ### 为什么 `overflow: visible` 救不了它
 * `will-change: transform` 会让该元素**提升为独立合成层**（composited layer）。
 * 合成层在 Chromium 中按**自身 bounds** 建立绘制边界——**这不是 `overflow` 边界**，
 * 所以 `overflow: visible` 对它无效。而弹层位于包裹层**上方约 69px**
 * （包裹层 `top=593` vs 弹层 `y=524`），**完全落在包裹层 bounds 之外** ⇒ 被裁。
 *
 * ### 为什么能解释全部矛盾现象
 * | 现象 | 解释 |
 * |---|---|
 * | 工具条可见 | 它在包裹层 bounds 内 |
 * | 弹层不可见 | 它在包裹层 bounds 外、被合成层裁掉 |
 * | `POP(clip)=NONE` | `overflow` 确实是 visible（裁的不是 overflow） |
 * | `hit=IN_POP` | hit-test 走 **layout 树**，不感知合成层裁剪 |
 * | `err=±0.0` 几何全对 | 位置计算没问题，是**绘制**被裁 |
 *
 * ### 修法
 * 用 `:has()` 精准命中「**直接包含工具条**的那一层包裹层」，
 * 压掉 `will-change`（降回 `auto`，不再提升合成层）+ 兜底 `overflow: visible`。
 * `:has()` 在 Chromium 105+ 可用（本项目 WebView 远高于此）。
 * 用 `> ` 直接子选择器**收敛作用域**——只影响紧邻的包裹层，
 * 不波及页面内的其它浮层（如链接面板、字号面板的宿主）。
 *
 * ### 风险
 * `will-change` 是 floating-ui 为**动画性能**加的优化；去掉后工具条跟随选区
 * 移动时可能少了 GPU 加速。但本工具条只在选区变化时跳变（无连续动画帧），
 * 性能影响可忽略，**换取弹层可见性是值得的**。
 */
const CROP_STYLE_ID = "data-ime-crop-style";
const ensureCropStyle = (): void => {
  if (document.querySelector(`style[${CROP_STYLE_ID}]`)) return;
  const el = document.createElement("style");
  el.setAttribute(CROP_STYLE_ID, "");
  el.textContent =
    // 1) 工具条自身的 overflow:auto 会裁掉上方 40px 的弹层（第十五轮定案）
    `.bn-formatting-toolbar { overflow: visible !important; }\n` +
    // 2) 工具条的**浮动包裹层**被 floating-ui 写了内联 will-change:transform，
    //    提升为合成层后按自身 bounds 裁掉上方弹层（第十七轮定案）。
    //    用 :has(> …) 精准只打这一层，避免波及其它浮层宿主。
    `div:has(> .bn-formatting-toolbar) {` +
    ` will-change: auto !important;` +
    ` overflow: visible !important; }\n`;
  document.head.appendChild(el);
};

/**
 * v2026-09-28：媒体限定版格式工具栏控制器（替代官方 FormattingToolbarController 挂载）。
 *
 * **为什么自建**：0.52 架构里，点击图片/视频/分割线弹出的「媒体编辑条」与
 * 文本格式条是**同一个** FormattingToolbar——官方 FormattingToolbarExtension
 * 的 shouldShow 对 NodeSelection(媒体块) 放行，而官方 Controller 硬编码消费
 * 该状态、不提供 shouldShow 过滤 prop。用户决策：**文本块上禁用此条**
 * （行内样式统一走宿主底部栏入口），**媒体块上保留**（官方按钮集里的
 * 下载/改名/删除等媒体按钮也在这套条上）。
 *
 * 实现：数据源与官方 Controller 完全同款（useExtension / useExtensionState /
 * useEditorState），仅叠加一个块类型过滤——`canApplyInlineStyles` 为真
 * （content==="inline" 的纯文本块）时强制不渲染，其余块保持官方原行为。
 * 浮层配置（middleware 三件套 / focusManager disabled / zIndex 40 /
 * placement 映射）照抄官方 FormattingToolbarController，不另发明。
 */
function MediaOnlyFormattingToolbarController() {
  const editor = useBlockNoteEditor<any, any, any>();
  const formattingToolbar = useExtension(FormattingToolbarExtension, { editor });
  const show = useExtensionState(FormattingToolbarExtension, { editor });
  // ★ 与官方唯一的差异点：纯文本块（可承载行内样式）上强制不显示；
  //   媒体/分割线（content:"none"）与表格（content:"table"）保持官方放行。
  const visible = show && !canApplyInlineStyles(editor);

  // 选区快照锚点：extension 处于展示态时取 from/to（照抄官方 Controller）
  const position = useEditorState({
    editor,
    selector: ({ editor: ed }) =>
      formattingToolbar.store.state
        ? {
            from: ed.prosemirrorState.selection.from,
            to: ed.prosemirrorState.selection.to,
          }
        : undefined,
  });

  // placement 按块 textAlignment 映射（照抄官方 Controller）
  const placement = useEditorState({
    editor,
    selector: ({ editor: ed }) => {
      const block = ed.getTextCursorPosition().block;
      if (
        !blockHasType(block, ed, block.type, {
          textAlignment: defaultProps.textAlignment,
        })
      ) {
        return "top-start";
      } else {
        return textAlignmentToPlacement(block.props.textAlignment);
      }
    },
  });

  const floatingUIOptions = useMemo<FloatingUIOptions>(
    () => ({
      useFloatingOptions: {
        open: visible,
        // 与官方一致：dismiss 触发的关闭要同步回 extension store，
        // Esc 关闭后把焦点还回编辑器。
        onOpenChange: (open, _event, reason) => {
          formattingToolbar.store.setState(open);
          if (reason === "escape-key") {
            editor.focus();
          }
        },
        placement,
        middleware: [offset(10), shift(), flip()],
      },
      focusManagerProps: {
        disabled: true,
      },
      elementProps: {
        style: {
          zIndex: 40,
        },
      },
    }),
    [visible, placement, formattingToolbar.store, editor],
  );

  return (
    <PositionPopover position={position} {...floatingUIOptions}>
      {visible && (
        <>
          <FormattingToolbar />
          <FontSizeButton />
          <TextColorButton />
        </>
      )}
    </PositionPopover>
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
  /**
   * 软键盘高度（dp，v2026-09-30 键盘遮挡弹层修复）与视口底边到屏底的距离（dp）
   *
   * 由宿主经 `imeHeight` 下行主动推送（键盘收起时都是 0）。用于表单弹层
   * （`.bn-form-popover`）的键盘避让——宿主在弹层期不避让键盘，WebView
   * 高度恒定，键盘物理覆盖屏幕底部。
   *
   * ⚠️ 不能用 `visualViewport` 自行测量：WebView 高度不变时系统不 resize 它，
   * `visualViewport.resize` 不触发（真机日志：`viewport` 埋点 0 次打印）。
   * ⚠️ 必须用 **dp**：WebView `initial-scale=1.0` 下 1 CSS px = 1 dp，
   * 而 `window.innerHeight` 是 CSS px——同单位才能相减。早期误用宿主物理 px
   * （905 vs 329dp，density 2.75）导致位移放大 2.75 倍、弹层飞出屏幕。
   */
  const [imeHeightPx, setImeHeightPx] = useState(0);
  /** WebView 视口底边到屏幕底的距离（dp）；键盘从屏幕底升起先吃掉这段 gap */
  const [imeGapDp, setImeGapDp] = useState(0);
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

  /**
   * TEMP-DEBUG（v2026-09-29 rename 键盘探针，定位后删除）：焦点去向明细追踪。
   *
   * **背景**：Rename video 弹层输入框聚焦后键盘"展开又收起"，焦点门控修复后
   * 仍复现——需精确定位键盘收起瞬间焦点的去向。本探针在 document **capture**
   * 段记录每一对 focusin/focusout 的目标与来向：
   * - `out input to=null activeAfter=body` = 焦点凭空消失（系统/卸载行为）；
   * - `out input to=div.xxx` = 有代码主动把焦点转移走了（顺藤摸瓜找调用方）。
   * capture 段先于任何目标处理器，不会被业务代码 stopPropagation 吞掉。
   */
  useEffect(() => {
    const describe = (el: EventTarget | null): string => {
      if (!(el instanceof Element)) return String(el);
      const tag = el.tagName.toLowerCase();
      const cls =
        typeof el.className === "string"
          ? el.className
              .split(" ")
              .filter((c) => c && !c.startsWith("bn-"))
              .slice(0, 2)
              .join(".")
          : "";
      const ct = el.getAttribute?.("data-content-type") ?? "";
      const ce = el.getAttribute?.("contenteditable");
      return `${tag}${cls ? "." + cls : ""}${ct ? `[ct=${ct}]` : ""}${ce ? "[ce]" : ""}`;
    };
    const onFocusIn = (e: FocusEvent) => {
      sendUp({
        type: "diagnostic",
        message: `[focusTrace] in ${describe(e.target)} from=${describe(e.relatedTarget)}`,
      });
    };
    const onFocusOut = (e: FocusEvent) => {
      sendUp({
        type: "diagnostic",
        message: `[focusTrace] out ${describe(e.target)} to=${describe(e.relatedTarget)} activeAfter=${describe(document.activeElement)}`,
      });
    };
    document.addEventListener("focusin", onFocusIn, true);
    document.addEventListener("focusout", onFocusOut, true);
    return () => {
      document.removeEventListener("focusin", onFocusIn, true);
      document.removeEventListener("focusout", onFocusOut, true);
    };
  }, []);

  /**
   * 表单弹层（`.bn-form-popover`）挂载/卸载监控（v2026-09-29 探针，
   * v2026-09-30 **转正为生产信号源**）：Mantine Dropdown（`.bn-form-popover`，
   * keepMounted=false）卸载 = 输入框从 DOM 移除 = 键盘必然收起的直接证据；
   * 配合 [focusTrace] 可区分「弹层被关」（React 卸载路径）与「仅焦点丢失」。
   * placeholder 摘要用于区分同类的链接/题注弹层。
   *
   * v2026-09-30：在 diagnostic 探针之外**新增正经上行 `formPopoverOpen`**——
   * 宿主收到 open=true 即让底部工具栏**不避让键盘**（弹层期 WebView 高度恒定，
   * 保住 Chromium 输入会话，见 bridge.ts 该消息注释），open=false 恢复
   * `safeAreaForEditBar()` 的正常避让。⚠️ 生产职责已挂在本 effect 上，后续清理
   * TEMP-DEBUG 时**只删 diagnostic 那行 sendUp，不得删除本 effect**。
   */
  useEffect(() => {
    let lastPresent = false;
    /**
     * ⚠️ v2026-09-30 视觉真值埋点（**临时**，定位「静置期弹层消失」真因）：
     * 弹层卸载瞬间无法再查询它的几何（DOM 已移除），故在 `addEventListener` 之外
     * 常驻记录**上一帧**的几何与焦点，作为「弹层消失前长什么样」的视觉证据。
     *
     * 记录项：
     * - `rect`：弹层 `getBoundingClientRect()`（确认它是否在可视区内、是否与工具条重叠）
     * - `active`：`document.activeElement` 摘要（确认卸载前 input 是否仍持焦点）
     * - `vv`：`visualViewport` 的 `height`/`offsetTop`（键盘真值：键盘弹出时 vv 会缩小）
     * 定位后随埋点一并删除。
     */
    let lastRect = "none";
    let lastActive = "none";
    /**
     * ⚠️ v2026-09-30 第二轮视觉真值埋点（**临时**，定位「静置期弹层消失」真因）：
     *
     * 上一轮已排除「DOM 卸载」——真机日志 `unmounted` **0 次**、`insets=false` 也不再出现、
     * `compose.imeBottom` 恒定 905px 八秒不动，但用户仍观察到「弹层消失」。
     * ⇒ 必须区分两种「消失」：**DOM 移除**（React 卸载）vs **视觉不可见**（被盖 / 被移出视口）。
     *
     * 本轮补三项证据：
     * 1. `chain`：弹层向上 6 层祖先链（tag + class + 是否含 `.bn-formatting-toolbar`）
     *    —— 直接判定「弹层是否是工具条子元素」（第二十二轮连带方案的前提）。
     *    `GenericPopover` 源码用 `FloatingPortal`（L248/260/271），疑 portal 到 body ⇒ 连带不成立。
     * 2. `cover`：用 `document.elementFromPoint` 在弹层中心点做命中测试
     *    —— 返回的不是弹层自己 = **被别的元素盖住**（如键盘容器 / 底部栏）。
     * 3. `z`：弹层与工具条的 `z-index` / `position` 对比。
     *
     * 采样时机：挂载瞬间 + 此后每 300ms（覆盖「静置后消失」那个时刻）。
     * 定位后随埋点一并删除。
     */
    const describeEl = (e: Element | null): string => {
      if (!e) return "null";
      const he = e as HTMLElement;
      const cs = getComputedStyle(he);
      return (
        `${e.tagName.toLowerCase()}` +
        `${e.className ? "." + String(e.className).split(" ").slice(0, 2).join(".") : ""}` +
        `[pos=${cs.position} z=${cs.zIndex} op=${cs.opacity} disp=${cs.display}]`
      );
    };
    /**
     * ⚠️⚠️ v2026-09-30 **第四轮身份探针**（TEMP-DEBUG，定位「每秒 relative/static 往返」真因）。
     *
     * ## 为什么需要（前三轮为什么都没定死）
     * 第三轮日志（17:22，201550 字节）把范围压到了极致：
     * - `inputN=1[v=28,FOCUS]` 全程 74 条恒定 ⇒ **input 从未消失**（机制 A/B 全否）；
     * - `[shift]` 最后一条在 `17:22:29.519`（`kt=370.9`）后**再无输出** ⇒ `applyShift` 停了；
     * - 但 `layerProbe` 的 `toolbar=` 字段在此后**每 1000ms 精确一次**地在
     *   `pos=relative`（`popRect.t=371.9`）↔ `pos=static`（`popRect.t=633.4`）间往返，
     *   且 **`pos=static` 那一瞬 `html=990`、`relative` 时 `html=959`**（差 31 字符）。
     *
     * 两个候选机制**无法用几何读数区分**：
     * - **机制 X「元素被替换」**：每秒 React 提交时 `.bn-form-popover` 指向**新 DOM 节点**，
     *   新节点尚未继承 `data-ime-shift`（或压根不在同名属性上）⇒ 位移为 0 ⇒ `t=633.4`。
     * - **机制 Y「属性被外部摘除」**：节点是同一个，但有东西每秒 `removeAttribute`
     *   再 `setAttribute`。
     *
     * ## 本探针怎么区分
     * `WeakMap<Node, number>` 给每个见过的弹层节点分配**递增稳定 token**（`#1`/`#2`/…）。
     * 每 tick 输出三件事：
     * 1. `node=#<token>`——token 变了 ⇒ 机制 X（节点被替换）；token 恒定 ⇒ 同一节点；
     * 2. `attr=`——该节点**自身**是否带 `data-ime-shift`（直接读，不经 `getComputedStyle`）；
     * 3. `cssTop=`——该节点通过样式表拿到的最終 `top` 计算值（属性在才有意义）。
     * 另加 `id=`（节点自增序号，便于日志里肉眼比对）。
     *
     * ## 同时给属性写入装钩子（见 `armAttrHooks`）
     * 对 `.bn-form-popover` 与 `.bn-formatting-toolbar` 两个元素分别**包裹**
     * `setAttribute` / `removeAttribute` / `removeAttributeNode`，任何第三方（React /
     * floating-ui / Mantine）摘或写 `data-ime-shift` 都会打一条带**调用栈前 3 帧**的
     * `[attrHook]` 日志——**这是「谁摘的」的直接答案**。
     * 只包裹目标元素实例（不污染 prototype），且用 `Object.defineProperty` 定义在
     * 实例自身上（可枚举 false），避免被 `for...in` / JSON 序列化看到。
     *
     * 定位后整段删除（连同 `armAttrHooks`）。
     *
     * ## ★★ 本探针的实战结论（17:31 日志，第二十八轮定案）
     * 答案**完全出乎预料，但被这组探针一次锁死**：
     * - `pop=#1 tb=#2` **全程恒定** ⇒ **机制 X 排除**（节点从未被替换）；
     * - `[attrHook]` 106 条，调用栈**全部指向 `editor.html:277:6527 / :7095 / :53`**
     *   ——即本项目编译产物中的 `applyShift` / `clearAll`，**没有任何第三方**触碰该属性；
     * - 统计 `removeAttribute x70` vs `setAttribute x36` ⇒ 属性「不存在」的时间
     *   **多于**存在的时间 ⇒ 位移多数时刻**根本没生效**；
     * - `[idProbe]` 的毫秒级序列把因果钉死：
     *   ```
     *   t=632.4  tbAttr=N  tbPos=static     ← 摘属性 ⇒ 弹层掉回键盘后
     *   t=378.4  tbAttr=Y  tbTop=-254.7px   ← 装回 ⇒ 抬起
     *   t=632.4  tbAttr=N  tbPos=static     ← 又摘
     *   t=371.9  tbAttr=Y  tbTop=-261.3px   ← 又装
     *   ```
     * ⇒ **元凶是 `applyShift` 自己的「摘属性以便测量」写法** + 它自己的
     * `MutationObserver(document.body, childList+subtree)` 形成的**自激反馈环**。
     * 修法见调度器段的 KDoc（改为纯算术反算 `rawBottom`，永不摘属性）。
     *
     * 这套「身份 token + 属性写入钩子 + 高频只在翻转时打点」的组合**非常有效**，
     * 一次跑就终结了连续三轮的猜测。后续遇到「同一段代码反复自我触发」类问题
     * 可复用同样思路。
     */
    const nodeTokens = new WeakMap<Node, number>();
    let nodeTokenSeq = 0;
    /** 已装钩子的元素（避免重复包裹） */
    const hooked = new WeakSet<Element>();
    /**
     * 给元素实例装属性写入钩子（只在实例上覆盖三个方法，不动原型）。
     * @param node 目标元素（`.bn-form-popover` 或 `.bn-formatting-toolbar`）
     * @param tag 日志标记，用于区分两个目标
     */
    const armAttrHooks = (node: Element, tag: string) => {
      if (hooked.has(node)) return;
      hooked.add(node);
      (["setAttribute", "removeAttribute"] as const).forEach((m) => {
        const orig = (node as any)[m].bind(node);
        Object.defineProperty(node, m, {
          value: (name: string, value?: string) => {
            if (name === "data-ime-shift") {
              let st = "";
              try {
                st = (new Error().stack || "")
                  .split("\n")
                  .slice(2, 5)
                  .map((s) => s.trim().replace(/\s+/g, " ").slice(0, 70))
                  .join(" | ");
              } catch {
                /* 栈不可用则忽略 */
              }
              sendUp({
                type: "diagnostic",
                message: `[attrHook] ${tag} ${m} ${name}${m === "setAttribute" ? "=" + value : ""} | ${st}`,
              });
            }
            return orig(name, value as any);
          },
          configurable: true,
          writable: true,
          enumerable: false,
        });
      });
    };
    /** 采集弹层的祖先链 / 覆盖 / z-index 三项证据并上行 */
    const snapLayers = (tag: string) => {
      const el = document.querySelector<HTMLElement>(".bn-form-popover");
      if (!el) return;
      armAttrHooks(el, "popover");
      const tbEl = document.querySelector<HTMLElement>(".bn-formatting-toolbar");
      if (tbEl) armAttrHooks(tbEl, "toolbar");
      if (!nodeTokens.has(el)) nodeTokens.set(el, ++nodeTokenSeq);
      const ntok = nodeTokens.get(el);
      const tbTok = tbEl ? (nodeTokens.has(tbEl) ? nodeTokens.get(tbEl) : (nodeTokens.set(tbEl, ++nodeTokenSeq), nodeTokens.get(tbEl))) : undefined;
      const r = el.getBoundingClientRect();
      // ① 祖先链（6 层内遇到 .bn-formatting-toolbar 即标记）
      const chain: string[] = [];
      let cur: Element | null = el.parentElement;
      let inTb = false;
      for (let i = 0; i < 6 && cur; i++) {
        if (cur.matches(".bn-formatting-toolbar")) inTb = true;
        chain.push(
          `${cur.tagName.toLowerCase()}${cur.className ? "." + String(cur.className).split(" ")[0] : ""}`,
        );
        cur = cur.parentElement;
      }
      // ② 覆盖测试：弹层中心点的命中元素
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      const hit = document.elementFromPoint(cx, cy);
      const coveredBy = hit === el || el.contains(hit) ? "self" : describeEl(hit);
      // ③ 工具条对照
      const tb = tbEl;
      const tbInfo = tb ? describeEl(tb) : "none";
      const vv = window.visualViewport;
      /**
       * ⚠️ v2026-09-30 第三轮（**临时**，定位「静置期**输入框**消失」真因）：
       *
       * 用户纠错：**弹层外壳仍在（t/op/vis 都正常），消失的是里面的 `input` 编辑框**。
       * 已读官方源码，**唯一能单独卸载 `input` 而不动外壳**的路径是
       * `FileRenameButton.tsx` L83 `if (block === undefined) return null` ——
       * 它会把整个 `Popover.Root`（含 `Content`）从 React 树摘掉，而 Mantine
       * `OptionalPortal` + `PopoverDropdown` 的 `Transition` 卸载存在一个渲染
       * 间隙 ⇒ 外壳 DOM 残留在 portal 里、`input` 先行消失。
       *
       * `block` 走 `useEditorState` selector，返回 `undefined` 的三个条件：
       * ① `!editor.isEditable`；② `getSelection()?.blocks` 长度 ≠ 1；
       * ③ 该块不含 `{url,name}` props（如光标落到段落块）。
       *
       * 本组证据（不改 DOM，纯读）：
       * - `input`：弹层内 `input` 数量 + `value` 长度 + 是否 `focus`（区分「被卸载」与「空值」）
       * - `shell`：弹层 `childElementCount` + `innerHTML.length`（外壳是否被清空）
       * - `inner`：弹层**后代**全部 `tag.class` 摘要（看 React 换成了什么）
       * 三者在同一 tick 采样，能直接判定「input 消失」属于哪种机制。
       * 定位后随埋点一并删除。
       */
      const inps = el.querySelectorAll("input");
      const inpHtml = Array.from(inps)
        .map((n) => {
          const i = n as HTMLInputElement;
          return `[v=${i.value.length}${i === document.activeElement ? ",FOCUS" : ""}]`;
        })
        .join("");
      const inner = Array.from(el.querySelectorAll("*"))
        .slice(0, 8)
        .map(
          (n) =>
            `${n.tagName.toLowerCase()}${n.className ? "." + String(n.className).split(" ")[0] : ""}`,
        )
        .join(">");
      sendUp({
        type: "diagnostic",
        message:
          `[layerProbe] ${tag} popover=${describeEl(el)}` +
          ` | node=#${ntok} tbNode=${tbTok === undefined ? "?" : "#" + tbTok}` +
          ` | attr=${el.hasAttribute("data-ime-shift") ? "Y" : "N"}` +
          ` cssTop=${getComputedStyle(el).top} pos=${getComputedStyle(el).position}` +
          ` | tbAttr=${tbEl ? (tbEl.hasAttribute("data-ime-shift") ? "Y" : "N") : "?"}` +
          ` tbTop=${tb ? getComputedStyle(tb).top : "?"}` +
          ` | rect=t${r.top.toFixed(1)} b${r.bottom.toFixed(1)} h${r.height.toFixed(1)}` +
          ` | inToolbar=${inTb}` +
          ` | chain=${chain.join(" < ")}` +
          ` | hitAtCenter=${coveredBy}` +
          ` | toolbar=${tbInfo}` +
          ` | inputN=${inps.length}${inpHtml}` +
          ` | shell=child=${el.childElementCount} html=${(el.innerHTML || "").length}` +
          ` | inner=${inner}` +
          (vv ? ` | vv.h=${vv.height.toFixed(0)} vv.top=${vv.offsetTop.toFixed(0)}` : ""),
      });
    };
    const snapGeom = () => {
      const el = document.querySelector(".bn-form-popover") as HTMLElement | null;
      if (el) {
        const r = el.getBoundingClientRect();
        lastRect = `l=${r.left.toFixed(1)} t=${r.top.toFixed(1)} w=${r.width.toFixed(1)} h=${r.height.toFixed(1)} b=${r.bottom.toFixed(1)}`;
      }
      const a = document.activeElement as HTMLElement | null;
      lastActive = a
        ? `${a.tagName.toLowerCase()}${a.className ? "." + String(a.className).split(" ")[0] : ""}`
        : "null";
    };
    /** 静置期周期采样句柄（弹层挂载起、卸载止） */
    let layerTimer: ReturnType<typeof setInterval> | null = null;
    const stopLayerTimer = () => {
      if (layerTimer !== null) {
        clearInterval(layerTimer);
        layerTimer = null;
      }
    };
    /**
     * ⚠️⚠️ v2026-09-30 **第四轮身份采样器**（TEMP-DEBUG，100ms 高频，仅记状态翻转）。
     *
     * 300ms 的 `snapLayers` 已经能看到 `relative ↔ static` 每秒往返，但**看不到
     * 翻转的先后关系**（同一毫秒内谁先谁后）。本采样器 100ms 一次，且**只在
     * `(nodeToken, hasAttr, cssTop)` 三元组变化时打点**，输出极简一行：
     * ```
     * [idProbe] #3 attr=N cssTop=auto pos=static t=633.4 html=990 connected=true
     * [idProbe] #3 attr=Y cssTop=-260.5px pos=relative t=371.9 html=959 connected=true
     * ```
     * 判读：
     * - `#token` 变化 ⇒ **节点被 React 替换**（机制 X）；
     * - `#token` 恒定但 `attr` 翻转 ⇒ **同一个节点上属性被外部摘/写**（机制 Y）
     *   ⇒ 配合 `[attrHook]` 的调用栈即可定位到具体代码路径；
     * - `connected=false` ⇒ 节点已脱离文档（幽灵残留）。
     *
     * 与 `snapLayers` 的差异：本行**不遍历祖先链、不做命中测试**，开销极低，
     * 可以放心跑在 100ms；且**只要状态不变就不打点**，日志量可控。
     */
    let idTimer: ReturnType<typeof setInterval> | null = null;
    let lastTriple = "";
    const stopIdTimer = () => {
      if (idTimer !== null) {
        clearInterval(idTimer);
        idTimer = null;
      }
      lastTriple = "";
    };
    const sampleIdentity = () => {
      const el = document.querySelector<HTMLElement>(".bn-form-popover");
      if (!el) {
        if (lastTriple !== "GONE") {
          lastTriple = "GONE";
          sendUp({
            type: "diagnostic",
            message: `[idProbe] GONE（querySelector 未命中 .bn-form-popover）`,
          });
        }
        return;
      }
      if (!nodeTokens.has(el)) nodeTokens.set(el, ++nodeTokenSeq);
      const tok = nodeTokens.get(el);
      const attr = el.hasAttribute("data-ime-shift");
      const cs = getComputedStyle(el);
      const tbEl2 = document.querySelector<HTMLElement>(".bn-formatting-toolbar");
      if (tbEl2 && !nodeTokens.has(tbEl2)) nodeTokens.set(tbEl2, ++nodeTokenSeq);
      const tbAttr = tbEl2 ? tbEl2.hasAttribute("data-ime-shift") : false;
      const r = el.getBoundingClientRect();
      const triple = `${tok}|${attr}|${cs.top}|${cs.position}|${tbAttr}|${(cs as any).display}`;
      if (triple === lastTriple) return;
      lastTriple = triple;
      sendUp({
        type: "diagnostic",
        message:
          `[idProbe] pop=#${tok} attr=${attr ? "Y" : "N"} cssTop=${cs.top} pos=${cs.position}` +
          ` disp=${cs.display} op=${cs.opacity} t=${r.top.toFixed(1)} h=${r.height.toFixed(1)}` +
          ` html=${(el.innerHTML || "").length} connected=${el.isConnected}` +
          ` | tb=#${tbEl2 ? nodeTokens.get(tbEl2) : "?"} tbAttr=${tbAttr ? "Y" : "N"}` +
          ` tbTop=${tbEl2 ? getComputedStyle(tbEl2).top : "?"}` +
          ` tbPos=${tbEl2 ? getComputedStyle(tbEl2).position : "?"}`,
      });
    };
    /**
     * ⚠️ v2026-09-30 第三轮（**临时**，定位「静置期**输入框**消失」真因）：
     *
     * 300ms 的 `snapLayers("tick")` 可能**跨过**瞬时状态（React 一次提交内先删
     * `input`、下一帧才可能补回）。本观察者专门盯**弹层子树的 `childList`**，
     * 在 `input` 数量发生变化的**那一瞬**打点，并记录当时的 `innerHTML` 摘要，
     * 用于区分三种机制：
     * - `innerHTML` 被整体替换 → 官方 `GenericPopover` 的 close 快照路径
     * - 子树被清空（`childElementCount→0`）→ Mantine `Transition` 卸载 children
     * - 整个 `Popover.Root` 被摘（外壳也消失）→ `FileRenameButton` 的 `return null`
     *
     * 定位后随埋点一并删除。
     */
    let subObs: MutationObserver | null = null;
    let lastInputN = -1;
    /**
     * ⚠️ v2026-09-30 第四轮补充（TEMP-DEBUG）：弹层**子树任意变更**的观察者。
     *
     * `[idProbe]` 只打「三元组变化」（100ms 粒度），可能仍错过"属性摘掉又立刻装回"
     * 这种**亚 100ms** 的瞬时态。本观察者以 `attributes:true` 监听**两个目标元素
     * 自身**的 `class` / `style` 属性写入（**不含 `data-ime-shift`**，那个由
     * `armAttrHooks` 负责），用来判断「每秒 React 提交改了什么」——
     * 从而回答「`html` 从 959 变 990 差的那 31 字符是 `style` 还是 `class`」。
     *
     * 只在**属性真的变化**时打点，并附 `MutationRecord.attributeName` 与新值摘要。
     */
    let attrObs: MutationObserver | null = null;
    const watchAttrs = (popEl: HTMLElement, tbEl: HTMLElement | null) => {
      attrObs?.disconnect();
      attrObs = new MutationObserver((recs) => {
        const parts: string[] = [];
        recs.forEach((rec) => {
          const t = rec.target as HTMLElement;
          const who = t === popEl ? "pop" : t === tbEl ? "tb" : t.tagName.toLowerCase();
          const oldLen = (rec.oldValue || "").length;
          const newLen = ((t.getAttribute(rec.attributeName || "") || "") as string).length;
          parts.push(`${who}.${rec.attributeName}(${oldLen}→${newLen})`);
        });
        if (parts.length) {
          sendUp({
            type: "diagnostic",
            message: `[attrProbe] ${parts.join(" ")}`,
          });
        }
      });
      const opts: MutationObserverInit = {
        attributes: true,
        attributeOldValue: true,
        attributeFilter: ["class", "style"],
      };
      attrObs.observe(popEl, opts);
      if (tbEl) attrObs.observe(tbEl, opts);
    };
    const watchSubtree = (el: HTMLElement) => {
      subObs?.disconnect();
      lastInputN = el.querySelectorAll("input").length;
      subObs = new MutationObserver(() => {
        const n = el.querySelectorAll("input").length;
        if (n === lastInputN) return;
        const from = lastInputN;
        lastInputN = n;
        const cs = getComputedStyle(el);
        sendUp({
          type: "diagnostic",
          message:
            `[inputProbe] input ${from}→${n} el.connected=${el.isConnected}` +
            ` child=${el.childElementCount} html=${(el.innerHTML || "").length}` +
            ` disp=${cs.display} vis=${cs.visibility} op=${cs.opacity}` +
            ` active=${document.activeElement?.tagName.toLowerCase() ?? "null"}` +
            ` inner=${Array.from(el.querySelectorAll("*"))
              .slice(0, 8)
              .map(
                (x) =>
                  `${x.tagName.toLowerCase()}${x.className ? "." + String(x.className).split(" ")[0] : ""}`,
              )
              .join(">")}`,
        });
      });
      subObs.observe(el, { childList: true, subtree: true });
    };
    const obs = new MutationObserver(() => {
      const el = document.querySelector(".bn-form-popover");
      const present = !!el;
      if (present === lastPresent) return;
      lastPresent = present;
      // 生产信号：驱动宿主 softInputMode 切换（v2026-09-30 键盘收起修复）
      sendUp({ type: "formPopoverOpen", open: present });
      const placeholder =
        el?.querySelector("input")?.getAttribute("placeholder") ?? "";
      // 挂载瞬间采集几何；卸载时用上一帧快照（DOM 已移除，无法现查）
      snapGeom();
      const vv = window.visualViewport;
      const vvInfo = vv
        ? `vv.h=${vv.height.toFixed(0)} vv.top=${vv.offsetTop.toFixed(0)} innerH=${window.innerHeight}`
        : `innerH=${window.innerHeight}`;
      sendUp({
        type: "diagnostic",
        message: `[renameProbe] form-popover ${present ? "mounted" : "unmounted"}${present ? ` placeholder=${placeholder}` : ""} | rect=${lastRect} | active=${lastActive} | ${vvInfo}`,
      });
      // 分层证据：挂载即采一次，此后每 300ms 采一次（覆盖静置期到消失那一刻）
      if (present) {
        snapLayers("mounted");
        stopLayerTimer();
        layerTimer = setInterval(() => snapLayers("tick"), 300);
        watchSubtree(el as HTMLElement);
        // ★ 第四轮身份证据：100ms 高频状态翻转采样 + 目标元素属性变更观察
        const popEl = el as HTMLElement;
        const tbElNow = document.querySelector<HTMLElement>(".bn-formatting-toolbar");
        armAttrHooks(popEl, "popover");
        if (tbElNow) armAttrHooks(tbElNow, "toolbar");
        watchAttrs(popEl, tbElNow);
        stopIdTimer();
        sampleIdentity();
        idTimer = setInterval(sampleIdentity, 100);
      } else {
        // ⚠️ 此处 `snapLayers` 必然空转（DOM 已移除，querySelector 返回 null 即 return）——
        // 保留调用只为「卸载即停表」，真正有效的证据是卸载前的 `tick` 系列。
        stopLayerTimer();
        stopIdTimer();
        subObs?.disconnect();
        subObs = null;
        attrObs?.disconnect();
        attrObs = null;
      }
    });
    obs.observe(document.body, { childList: true, subtree: true });
    return () => {
      obs.disconnect();
      stopLayerTimer();
      stopIdTimer();
      subObs?.disconnect();
      attrObs?.disconnect();
    };
  }, []);

  /**
   * 浮动浮层「键盘避让」（v2026-09-30 键盘收起修复·**浮层露出承担者**）。
   *
   * 管**块选中格式工具条** `.bn-formatting-toolbar`（见下方 `SHIFT_TARGETS`）。
   * 历史上曾同时管表单弹层 `.bn-form-popover`，**第二十二轮起已移出**
   * ——弹层是工具条子元素、靠工具条的位移连带即可（真机 `[layerProbe]`
   * `inToolbar=true` 实证），且对官方 Popover 直写位移会干扰其定位/关闭判定
   * （详见 `SHIFT_TARGETS` 的 KDoc）。
   *
   * ★★ 第二十五轮：位移载体由 `transform: translateY()` 改为
   * `position: relative + top`（**布局层位移**）——因为 `transform` 与官方
   * floating-ui 的 `autoUpdate` 形成每秒一次的弹层重定位反馈环（真机铁证见
   * `shiftStyleEl` 的 KDoc）。连带语义不变（父子相对位置仍保持）。
   *
   * ## 背景
   * 宿主侧修复键盘"展开后又收起"时，**弹层打开期间底部工具栏不再避让键盘**
   * （`InspirationEditScreen.kt` 的 bottomBar modifier：`formPopoverOpen` 为真用
   * `Modifier`、否则用 `safeAreaForEditBar()`）——因为 `imePadding()` 读逐帧
   * `WindowInsets.ime`，会让 WebView 每帧收缩、打断 Chromium 输入会话。
   * 代价是 WebView 高度恒定、**键盘物理盖住屏幕底部**，浮层需要自行避开。
   *
   * ## 为什么用宿主下发的 [imeHeightPx]，而不是 `visualViewport`
   * 初版方案读 `visualViewport.height + offsetTop` 当「键盘上沿」，真机证伪：
   * **WebView 高度不变时系统不会 resize 它**，`visualViewport.resize` 不触发
   * （诊断埋点 `viewport |` 在整个键盘展开过程中 0 次打印），JS 侧根本收不到通知。
   * 故改由宿主在 insets 变化时经 `imeHeight` 下行主动推送键盘高度，JS 直接使用。
   *
   * ## 做法
   * 键盘上沿（视口 y）= `innerHeight - (imeHeightPx - imeGapDp)`——键盘从屏幕底
   * 升起、先吃掉视口底边到屏底的 gap（弹层期 = 底部工具栏高），剩下的才是真正
   * 盖住视口的高度。与浮层 `getBoundingClientRect().bottom` 比较，被遮挡则施加
   * 向上位移 `overflow + 4`。
   *
   * ## ★ 位移怎么施加：属性开关 + 动态样式表，**不是** `el.style.top`
   * v2026-09-30 真机定案：目标的 `style` 由 **React / floating-ui 托管**，
   * DOM 直写会被下一帧抹掉——
   * - `.bn-form-popover` 是本项目 JSX 节点，React 重渲染会 diff `style` 对象并
   *   **移除不在对象里的属性**（第二十二轮起它已不在清单内，此处保留作历史依据）；
   * - `.bn-formatting-toolbar` 的 `style` 由 `floatingStyles`（floating-ui 定位，
   *   本身就是 `transform: translate(X,Y)`）+ `useTransitionStyles` 逐帧写入。
   *
   * 铁证（本轮 `[shift]` 日志）：两者 rect **全程一动不动**，而 `overflow` 从
   * -40.5 涨到 +216.6 ⇒ 位移算了、设了、被抹了（用户看到「工具条能上移」实为
   * 它本来就没被遮：`overflow` 初始仅 0.2，位置是自然位置而非位移结果）。
   *
   * 改走 **属性开关 + 动态 `<style>` 规则**（`data-ime-shift` + `position/top`）：
   * 置属性即让带 `!important` 的样式表规则生效。内联样式**无** `!important` 时
   * 样式表 `!important` 优先 ⇒ 稳压 React 与 floating-ui 的写入；关闭时移除属性，
   * 规则不再匹配，进出场动画照常。
   *
   * ⚠️ 测量前必须**暂时移除属性开关**拿「无位移的原始 rect」（CSS `!important`
   * 下清不掉它），否则会「位移叠加位移」逐帧上漂。
   *
   * ⚠️ 单位必须是 dp：WebView `initial-scale=1.0` 下 1 CSS px = 1 dp、
   * `window.innerHeight` 也是 CSS px。宿主早期误传物理 px（905 vs 329dp，
   * density 2.75）导致位移放大 2.75 倍、弹层直接飞出屏幕。
   *
   * ⚠️⚠️ **为什么用 `position:relative + top` 而不是 `transform`**
   * （v2026-09-30 第二十五轮真机定案，**本论断已被实证推翻，勿再走回头路**）：
   *
   * 旧论断曾写「`transform` 是纯视觉位移、不改变测量基准，故优于 `top`」——
   * **这是错的**。真机 17:03 日志显示，`transform` 反而**破坏了测量基准**：
   * 它改变 `getBoundingClientRect()` 的返回却不改变布局，导致官方 floating-ui
   * 的 `autoUpdate` 周期性回读到「已位移」的 rect、把弹层算回原始位，
   * **每秒一次横跳**（`t=371.9` ↔ `t=633.4`，后者在 `vv.h=629` 之外 ⇒ 视觉消失）。
   * 详见 `shiftStyleEl` 的 KDoc（含完整时间线铁证）。
   *
   * 正解是**布局层位移**：`position: relative; top: -Npx`。它把偏移**真实写进
   * 布局结果**，双方读到的几何一致 ⇒ 反馈环消失。`relative` 不脱离文档流、
   * `z-index: auto` 下不创建新层叠上下文，无副作用。
   *
   * （历史注：早期曾试过直写 `top` 导致「改定位 → 重算 → 又改定位」的循环——
   * 那是因为**直写的是 `el.style.top`，与 floating-ui 的 inline `top/left`
   * 争抢同一个属性**；如今走 `!important` 样式表规则 + `data-ime-shift` 开关，
   * 不碰 inline，两者不再争抢同一个写入通道。）
   *
   * ⚠️ 为什么不用 `scrollIntoView`：浮层是 `position: fixed/absolute` 的
   * Portal 浮层，不在文档流内，`scrollIntoView` 对它无效（且会误滚动正文）。
   */
  useEffect(() => {
    /**
     * 需要键盘避让的浮动元素选择器清单。
     *
     * ★★ **第二十二轮真机定案：只剩工具条一项，弹层再次移出清单。**
     *
     * ## 本轮为什么要移出（一句话）
     * 弹层的「消失」**从来不是位移/裁切/命中问题**——真机截图证实：弹层挂载后
     * 约 **1 秒**被官方 `FileRenameButton` **自身**卸载（组件 `return null`），
     * 而**工具条与键盘全程都在**。⇒ 宿主对 `.bn-form-popover` 写 `transform`
     * 非但无用，反而有干扰官方 Popover 定位/关闭判定的嫌疑（见下「干扰假设」）。
     *
     * ## ★★ 第二十二轮证据链（截图法，本轮突破）
     * 前二十一轮的探针全部基于 `getBoundingClientRect()` / `elementFromPoint()`，
     * 属「布局几何 + 命中测试」层 ⇒ **看不到真实绘制像素**。本轮改用
     * `adb exec-out screencap -p` 抓真机屏幕（`log/auto_shot.py` 监听
     * `form-popover mounted` 后按 0.8/1.2/1.6/2.0/2.6/3.2s 自动连拍）：
     * ```
     * pop2_0.png (0.8s)：Rename 输入框在、工具条 8 个图标、键盘在、蓝色选中框在
     * pop2_1.png (1.2s)：输入框【消失】、工具条仍在（图标重排为 7 个）、键盘仍在
     * ```
     * ⇒ 同时刻 `POP(draw) IN=9 OUT=0 OFF=0`（9 点全命中弹层内部）、
     * `POP(chain)` 全链 `ovf=visible clip=- wc=- op=1`、`POP(comp) NONE`
     * ——**几何层毫无异常，像素层却已没了** ⇒ 唯一解释：**DOM 被卸载**（非隐藏）。
     *
     * ## ★★ 官方源码侧的机制（`FileRenameButton.tsx` L26-85）
     * ```tsx
     * const block = useEditorState({ editor, selector: ({editor}) => {
     *   if (!editor.isEditable) return undefined;
     *   const selectedBlocks = editor.getSelection()?.blocks
     *                          || [editor.getTextCursorPosition().block];
     *   if (selectedBlocks.length !== 1) return undefined;   // ← 关键字
     *   ...
     * }});
     * const [popoverOpen, setPopoverOpen] = useState(false);
     * if (block === undefined) { return null; }              // ← 整个按钮（含弹层）不渲染
     * ```
     * 即：**一旦选区状态解析不出「唯一的块」，官方组件直接卸载弹层**。
     * 键盘仍在、工具条仍在（它是另一套渲染路径），所以用户看到的是
     * 「弹层凭空直接消失」（用户原话）。
     *
     * ## 干扰假设（本轮移出的动机）
     * 宿主此前把 `.bn-form-popover` 纳入清单、对它**直写 `transform`**：
     * - 弹层是 `position: fixed`（Mantine Popover），其**定位包含块**会因此改变；
     * - floating-ui 的 `[offset, shift, flip]` 每帧读锚点与弹层的几何做决策，
     *   宿主的 `!important` 位移会**污染**这些读数 ⇒ 中间件持续重定位；
     * - 而官方 `Popover` 的关闭路径（`useDismiss` / 内部 `onClose`）同样依赖
     *   「点击/交互是否落在弹层内」的判定，被搬走的弹层会让判定错乱。
     * ⇒ 结论：**给官方弹层写 `transform` 是「越界操作第三方 DOM」**，风险大于收益。
     * 正确姿势回到第十四轮的思路：**只动工具条，弹层靠父子连带**（见下）。
     *
     * ## ★ 连带是否成立（第十四轮验证 → 第二十四轮 `[layerProbe]` 二次实证）
     * 三探针交叉验证（`inToolbar=true` + 弹层宽度逐帧横跳 + 父子差值恒定）
     * 证明 `.bn-form-popover` 是 `.bn-formatting-toolbar` 的子元素；
     * 第二十四轮 `[layerProbe]` 输出 `chain=div.bn-toolbar < div < div < ...`、
     * `inToolbar=true`，**再次确证**。工具条的位移会**连带**弹层（相对位置保持）。
     *
     * ⚠️ 但「连带成立」**不等于**「问题解决」：第二十四轮同时发现弹层会
     * **每秒一次横跳回屏外**（`t=371.9 ↔ 633.4`），根因是工具条的 `transform`
     * 干扰了 floating-ui 对参考元素几何的读取 ⇒ 第二十五轮改用
     * `position: relative + top` 的布局层位移（详见 `shiftStyleEl` 的 KDoc）。
     *
     * ## 那弹层还会不会被裁 / 被 flip
     * - **裁切**：已由 `ensureCropStyle()`（常驻样式表，压 `.bn-formatting-toolbar`
     *   的 `overflow: visible`）+ `will-change: auto` 修复，真机 `POP(wcFix) wcFix=auto
     *   match=1`、`POP(comp) NONE` 全程成立 ⇒ 工具条子树内的弹层不会再被裁。
     * - **flip**：第十八轮曾观察到官方 `flip()` 把弹层翻到工具条下方；但其触发
     *   条件是「上方空间不足」。`ensureCropStyle` 修好后，弹层在工具条子树内
     *   已不再受裁切影响、上方空间判定也恢复正常（第十九轮 `gapToTb=4`、
     *   `yAxis=above` 全程成立）。
     *
     * ## 历史沿革（避免走回头路）
     * | 轮次 | 清单内容 | 结果 |
     * |---|---|---|
     * | 第十轮 | 两者同目标位 | 弹层（w=305.5）盖住工具条（w=291.5）❌ |
     * | 第十一轮 | 只工具条（赌连带） | 当时误判连带不成立 ❌ |
     * | 第十二轮 | 两者各自避让 | 弹层被推出裁剪区 ❌ |
     * | 第十四轮 | 只工具条 + 放开 overflow | 裁切解决，但当时见官方 `flip` 翻到下方 ❌ |
     * | 第十八轮 | 工具条 + 弹层（各自目标位） | 弹层约 1s 后被官方卸载 ❌ |
     * | 第二十二轮 | 只工具条（弹层靠连带） | 当时方案 |
     * | **第二十九轮** | **工具条 + 弹层（弹层按自身高度定目标位）** | **本轮方案** |
     *
     * ## ★★ 第二十九轮：弹层必须重新纳入清单（真机 17:39 日志定案）
     *
     * ### 症状
     * 工具条避让**完全正确**（`err=0.0`，`rect=t331 b366.4`，稳稳在键盘上沿
     * `370.9` 之上），但 **Rename 弹层整块落在键盘里**（`rect=t371.9 b409.4`，
     * 顶边就比键盘上沿低 1px）⇒ 用户看到「工具条在、弹层不可见」。
     *
     * ### 根因：弹层的 offsetParent 就是工具条，连带 ≠ 正确
     * 第二十二轮的假设「只动工具条、弹层靠父子连带即可」在这一轮**被证伪**：
     * 连带确实成立（弹层跟着上移了 `261.3px`，`632.4 − 261.3 = 371.1 ≈ 371.9`），
     * 但**连带的方向不对**——弹层本来就在工具条**下方**（`gapToTb=-77.9`），
     * 一起上移后仍然在工具条下方，而工具条下方**正好是键盘**。
     *
     * 为什么弹层在工具条下方（而不是官方默认的「上方」）：
     * - 锚点是**选区矩形**（`posToDOMRect`，见 `PositionPopover`），不是工具条；
     * - 视频块选区原始位置约 `y=460..628`（屏幕下半部）；
     * - 键盘升起后 floating-ui 的 `shift()`/`flip()` 按「锚点 + 视口可见边界」
     *   重算，判定锚点上方放不下 ⇒ 翻到锚点下方 ⇒ 而锚点下方更没空间，
     *   最终由 `shift()` 夹到「工具条正下方」这个位置（`cssTop=39.7272px`
     *   全程恒定，说明 floating-ui 从头到尾都认为弹层该在工具条下 39.7px）。
     * - ⇒ **这不是 `flip()` 的朝向导致的**，改 `flip` 参数无用（它本就是
     *   `below`，因为锚点泡在键盘里）。
     *
     * ### 修法
     * 把 `.bn-form-popover` 加回清单，**按弹层自身几何**定目标位：
     * `targetBottom = keyboardTop − WANT_GAP`（即把弹层底边拉到键盘上沿之上）。
     * 由闭环校正逐帧回读**真实 rect** 并累加补偿 ⇒ 天然吸收「父级工具条
     * 连带位移」造成的双重叠加，无需手工推算相对量（这正是闭环校正的设计初衷）。
     *
     * ⚠️ 与第二十二轮「越界操作第三方 DOM」顾虑的差异：第二十二轮时位移载体是
     * `transform`（改变 `getBoundingClientRect` 但不改布局 ⇒ 与 floating-ui
     * `autoUpdate` 打架形成每秒横跳，见 `shiftStyleEl` KDoc 的 17:03 铁证）；
     * 第二十五轮起已改为 `position: relative + top`（**真实写入布局**），
     * floating-ui 读到的就是我们写下的最终位置 ⇒ 反馈环不复存在。
     * 真机验证：第二十五轮后弹层位置**恒定不变**（17:39 日志 19 帧
     * `t371.9 b409.4` 零抖动），证明布局层位移与 floating-ui 能和平共存。
     */
    const SHIFT_TARGETS = [".bn-formatting-toolbar", ".bn-form-popover"];

    /**
     * 目标底边与键盘上沿之间保留的余量（px）。
     *
     * ⚠️⚠️ **必须声明在任何读取点之前**（v2026-09-30 第十三轮真机定案，
     * 一次雪崩级事故）：
     * 本轮新增的「弹层退化分支」（`SHIFT_TARGETS` 遍历段内）需要读 `WANT_GAP`，
     * 但它当时被声明在 `applyShift` **下半部分**的闭环校正段前。同一函数作用域内
     * 先读后声明 `const` ⇒ **TDZ（暂时性死区）**，抛出
     * `Uncaught ReferenceError: Cannot access '<minified>' before initialization`。
     *
     * 真机症状极具迷惑性：**`[shift]` 零输出**（上报点在函数末尾，永远到不了）、
     * 两个浮层**都不显示**（effect 每帧崩溃，避让逻辑从未真正跑过），
     * 而宿主侧的 `imeHeight` 下发（102 次）**全部正常**——因为崩的是 JS 端 effect。
     * 日志里 30 次 `Uncaught ReferenceError` 就是铁证（每次 `imeHeight` 后紧跟一条）。
     *
     * ⇒ 教训：**函数体内被多个不连续段落共用的常量，一律提到函数顶部声明**。
     */
    const WANT_GAP = 4;

    /**
     * 弹层底边与**工具条顶边**之间保留的间隙（px）。
     *
     * 用户需求原话：「弹层**恒在工具条上方**」。工具条目标底边为
     * `keyboardTop − WANT_GAP`，故弹层目标底边 =
     * `keyboardTop − WANT_GAP − 工具条高 − POP_ABOVE_GAP`。
     *
     * 取值 `4`：与 `WANT_GAP` 对称，视觉上两块浮层之间的呼吸感一致。
     *
     * ⚠️ 该常量在第二十二轮曾随「弹层移出清单」被删除，第二十九轮弹层回归
     * 清单后**重新引入**（见 `SHIFT_TARGETS` KDoc 的第二十九轮说明）。
     * 同样必须声明在函数顶部，防 TDZ（见 `WANT_GAP` 的教训）。
     */
    const POP_ABOVE_GAP = 4;

    /**
     * 键盘**绝对高度**小于该值（dp）即视为「键盘已收起」。
     *
     * ⚠️ **阈值不能写成 `<= 0`**（v2026-09-30 第八轮真机定案）：宿主下发的
     * `ime bottom` 在收起动画尾部会落到 `0.4 / 0.7 / 1.1 / 1.8 / 2.9 dp`
     * 这类**残余值**而非干净的 0。若只判 `<= 0`，这些帧会继续走避让逻辑：
     * 真机症状：`ime=0.4` 时仍输出 `FINAL ... shiftY=-42.4`、`after` 被钉在 624。
     */
    const KEYBOARD_HIDDEN_DP = 4;

    /**
     * ★★ **视口上缘安全余量（px）—— 避让位移的「上限保护」**（v2026-09-30 第十七轮
     * 真机定案）。
     *
     * ## 为什么需要它
     * 本避让逻辑是**单向硬顶**：只算 `overflow = rect.bottom − targetBottom`，
     * 只要元素底边超出键盘上沿就往上推，**完全不检查推上去会不会捅穿视口上缘**。
     * 当锚点（视频块/光标）在编辑区**靠上**位置时：
     * - 键盘弹起 → `keyboardTop` 上移 → `targetBottom = keyboardTop − 4` 上移；
     * - 元素被一路往上顶，`rect.top` 很快变成**负数**（跑到视口外）；
     * - 用户视角：**编辑页里完全看不到工具条，连点都点不到**，无法继续操作。
     *
     * ## 为什么不能靠 `flip` 兜底
     * 曾试过禁用/启用 `flip` 来治这个问题，但 `flip` 是 floating-ui 层的行为：
     * ① 它只看**锚点**周围的可用空间，不知道键盘的存在；
     * ② 它与本避让逻辑（`data-ime-shift` 样式表规则）**互不感知**，两套机制各推各的，
     *    谁也保证不了最终位置 —— 这正是历史上「改来改去要么不显示、要么只显示
     *    一个」的根因。
     * ⇒ **必须在避让逻辑内部自己划出上限**，不把它外包给 floating-ui。
     *
     * ## 取值与语义
     * 元素**顶边**（`rect.top`）允许的最小值。取 8px：既留出视口上缘的呼吸感，
     * 又几乎不牺牲可用空间（8px ÷ 视口 628px ≈ 1.3%）。
     * 一旦 `rect.top < VIEWPORT_TOP_MARGIN`，说明「即便顶到上限也塞不进
     * `keyboardTop` 之上」——此时**接受被键盘部分遮挡**，把元素钉在
     * `VIEWPORT_TOP_MARGIN` 处（保证可见、可点），而不是继续往上推出屏幕。
     * 这是「**可见性优先于完全避让**」的取舍：用户能用 > 完全不被遮。
     */
    const VIEWPORT_TOP_MARGIN = 8;

    /**
     * 位移开关**属性名**（规则在下方动态 `<style>` 里生成）。
     *
     * ⚠️ **为什么用 `data-` 属性而不是 class**（v2026-09-30 第二轮真机定案）：
     * 首版用 `classList.add("ime-shift")`，结果 **`.bn-form-popover` 生效、
     * `.bn-formatting-toolbar` 完全不生效**（日志：前者 `after == want` 逐帧
     * 精确吻合；后者 `after` **恒为 628.2 一丝不变**）。差异根源在 className
     * 的归属——
     * - `.bn-form-popover` 的 `className="bn-popover-content bn-form-popover"`
     *   是**本项目 JSX 的静态字面量**，值永不变化 ⇒ React 不重设 ⇒ class 存活；
     * - `.bn-formatting-toolbar` 带 **Mantine 动态 hash class**
     *   （日志实测 `bn-toolbar bn-formatting-toolbar m_8bffd616 mantine-Flex-root
     *   __m__-_r_6_`），每次渲染该字符串都可能不同 ⇒ React 判定 `className`
     *   prop 变化、**整串重写** ⇒ 我们加的 class 被抹掉。
     *
     * `data-` 属性是 React **不认识的属性**（不在它 props 声明里），React 永不
     * 触碰它 ⇒ 开关可靠存活。位移**具体值**也不走内联 CSS 变量（同样会被
     * React 的 style diff 移除），改为写进动态 `<style>` 规则里的具体像素值。
     *
     * 注：上述 `.bn-form-popover` 的对比是**第二轮的历史实录**；第二十二轮起
     * 它已不在 `SHIFT_TARGETS` 内（见其 KDoc），此段保留仅为解释 `data-` 属性的由来。
     */
    const SHIFT_ATTR = "data-ime-shift";

    /**
     * 动态避让样式表（`<style>` 元素，常驻 `document.head`）。
     *
     * 每帧用最新的位移值**整表重写**，生成带具体 px 的规则，形如：
     * ```css
     * .bn-formatting-toolbar[data-ime-shift] {
     *   position: relative !important;
     *   top: -260.5px !important;
     * }
     * ```
     * （历史上弹层进清单时还会有 `.bn-form-popover[data-ime-shift]` 一条；
     * 第二十二轮起清单只剩工具条，故不再生成弹层规则。）
     *
     * 优点：**完全不触碰目标元素的 `style` / `class`** ⇒ 不受 React、floating-ui
     * 任何一方的写入竞争影响。`!important` 再压一层内联样式，双保险。
     *
     * ## ★★ 第二十五轮：`transform` → `position:relative + top`（布局位移）
     *
     * **旧方案的致命缺陷（真机 17:03 日志铁证）**：写 `transform: translateY()`
     * 虽然在合成层"移动"了工具条，但会与官方 floating-ui 的 `autoUpdate` 形成
     * **每秒一次的重定位反馈环**：
     *
     * ```
     * 03.698  [layerProbe] popover t=371.9 hit=self   ← 正确避让位
     * 04.487  [layerProbe] popover t=633.4 hit=null   ← 弹回屏外（被裁 h=18）
     * 05.489  [layerProbe] popover t=633.4 hit=null   ← 周期 ≈ 1s
     * 06.491  [layerProbe] popover t=633.4 hit=null
     * ```
     * 而且横跳期间**日志里没有任何其他事件**（无 `[shift]` 写入、无 insets 变化、
     * 无 DOM mutation）⇒ 纯属 floating-ui 内部行为。用户可见症状：
     * **「弹层出现一下又消失」**（跳到 `t=633.4` 时落在 `vv.h=629` 之外，
     * 命中测试 `hitAtCenter=null`，视觉上就是"没了"）。
     *
     * **成因**：`transform` 不改变元素的布局位置，但**改变
     * `getBoundingClientRect()` 的返回**。floating-ui 的 `autoUpdate`
     * （`ResizeObserver` + `IntersectionObserver`）周期性回读参考元素几何，
     * 读到的是「已经位移过」的 rect，于是重算弹层位置——把弹层算回
     * **工具条未避让时的原始位**（`t≈633.4` ≈ 屏底外）。下一轮观察回调再纠正，
     * 如此往复。**双方在同一份几何上互相干扰，谁也收敛不了。**
     *
     * **修法**：改用**布局层位移** `position: relative; top: -Npx`。
     * - `relative` 不脱离文档流（保留占位），因此不会影响工具条之后的兄弟布局；
     * - `top` 偏移**真实写入布局结果** ⇒ `getBoundingClientRect()` 反映的是
     *   **双方一致认可的最终位置**，floating-ui 重定位的结果与我们相同；
     * - `z-index: auto` 下的 `relative` **不创建新层叠上下文**，无 z 轴副作用。
     *
     * ⇒ 反馈环从"两个信号源打架"退化为"单一真相"，横跳消失。
     */
    const shiftStyleEl = document.createElement("style");
    shiftStyleEl.setAttribute("data-ime-shift-style", "");
    document.head.appendChild(shiftStyleEl);

    /**
     * 确保「放开工具条裁剪」的常驻样式表在位（幂等，见 `ensureCropStyle` KDoc）。
     * 与本 effect 的位移规则**完全解耦**：不受 `clearAll()` / effect 重建影响。
     */
    ensureCropStyle();

    /** 已被施加位移的元素 → 其位移值（卸载 / 键盘收起时按记录清理） */
    const shiftedMap = new Map<HTMLElement, number>();

    /** 按当前记录（重新）生成整张避让样式表 */
    const writeShiftStyles = () => {
      if (shiftedMap.size === 0) {
        shiftStyleEl.textContent = "";
        return;
      }
      let css = "";
      shiftedMap.forEach((dy, el) => {
        const sel = SHIFT_TARGETS.find((s) => el.matches(s));
        if (!sel) return;
        /**
         * ⚠️⚠️ **必须用 `shiftY` 直接拼带符号的数值，不能写 `top: -${dy}px`**
         * （v2026-09-30 第七轮真机定案，是全链路卡死的元凶；第二十五轮由
         * `translateY` 迁移到 `top` 时沿用同一约定）。
         *
         * 闭环校正会把 `dy` 累加成**负值**（弹层实测 `dy=-80`）。此时
         * `top: -${-80}px` 拼出的字符串是 **`top: --80px`** ——
         * 双负号是**非法 CSS 值**，浏览器**整条声明直接丢弃**（不报错、不生效）。
         * 后果是一条完美自锁的死循环：
         *   写非法值 → 元素不动 → 校正回读到恒定误差（弹层恒 `err=-40`）
         *   → 再累加进 `dy` → 仍是负值 → 还是非法值 → 永远不动。
         * 真机症状：父级工具条 `err=-0.0` 完美收敛，弹层 `dy` 冻死在 `-80`、
         * `after` 恒等于 `want-40`（该 40 全是父级连带位移贡献，自身位移为 0）。
         *
         * ⇒ **`dy` 是「带符号的向上位移量」（正=向上、负=向下）**，
         * 输出时算 `shiftY = -dy`（即 `top: ${shiftY}px`），**负值也必须合法输出**
         * ——`top: 80px` 完全合法，而 `top: --80px` 才是非法的。
         *
         * ★★ 第二十五轮：由 `transform: translateY(shiftY)` 改为
         * `position: relative; top: shiftY` —— 语义完全对应（都是"相对原位上移
         * `-shiftY`"），但走**布局层**而非合成层，避免与 floating-ui 的
         * `autoUpdate` 形成重定位反馈环（详见 `shiftStyleEl` 的 KDoc）。
         * `position: relative` 与 `top` 必须成对出现，缺一则不生效。
         *
         * ★★ **第二十九轮：弹层必须走 `margin-top`，不能写 `position`**
         * （踩过即雪崩，务必遵守）。
         *
         * 工具条原始 `position: static`，写 `position: relative` 只是让它
         * **能**接受 `top` 偏移，语义无损。
         *
         * 但 `.bn-form-popover` 原始是 **`position: absolute`**（Mantine
         * Popover 由 floating-ui 显式写 `top`/`left` 定位，真机 `pos=absolute`
         * `cssTop=39.7272px`）：
         * - 写 `position: relative` 会**把 absolute 覆盖成 relative**
         *   ⇒ 元素脱离 floating-ui 的定位体系、退回文档流
         *   ⇒ 定位彻底错乱（比不动还糟）；
         * - 弹层同时是工具条的**子元素**，工具条的 `relative` 已是它的
         *   `offsetParent`，弹层的 `absolute` 相对工具条定位——
         *   这层关系**不能动**。
         *
         * ⇒ 弹层改走 **`margin-top`**：它是**布局属性**，不改 `position`
         * 取值、不改 `offsetParent`、不影响浮层自身的定位计算，只是把元素
         * 在布局流中整体推移（配合闭环校正回读真实 rect 收敛）。
         * 真机语义验证：`.bn-form-popover` 是常规流内元素（`disp=block`），
         * `margin-top` 能生效。
         */
        const shiftY = -dy;
        const isPopover = el.matches(".bn-form-popover");
        css += isPopover
          ? `${sel}[${SHIFT_ATTR}] { margin-top: ${shiftY}px !important; }\n`
          : `${sel}[${SHIFT_ATTR}] { position: relative !important; top: ${shiftY}px !important; }\n`;
      });
      shiftStyleEl.textContent = css;
    };

    /** 清理所有已施加的位移 */
    const clearAll = () => {
      shiftedMap.forEach((_, el) => {
        el.removeAttribute(SHIFT_ATTR);
      });
      shiftedMap.clear();
      shiftStyleEl.textContent = "";
    };

    /**
     * TEMP-DEBUG（验证期用，清理时整段删除）：把本帧的位移结果**汇总上行**。
     *
     * ## 为什么抽成独立函数
     * 原实现把诊断代码**内联在 `applyShift` 末尾**，与业务逻辑交织：
     * ① 函数体被撑到 300+ 行，新增分支时极易踩「先读后声明」（第十三轮 TDZ 事故）；
     * ② 诊断段自身声明了 `sel` / `pop` / `node` 等局部名，与业务段**同名遮蔽**，
     *   极易在静态检查与人工阅读中互相干扰。
     * ⇒ 抽离后 `applyShift` 只保留「编排」职责，诊断全部收敛到本函数。
     *
     * ## 输出内容
     * - `FINAL`：每个已施加位移元素校正后的最终位置。
     *   被遮元素达标时 `after == want`、`err == 0`；未被遮元素 `want=N/A`。
     *   `after <= 0` 即 `GHOST!`（元素已脱离文档，`getBoundingClientRect` 返回全 0）。
     * - `POP(obs)`：弹层观测项 + 祖先链探针（是否在工具条子树内、途中带 transform 的节点）。
     *
     * @param dbgParts  调用方在遍历阶段已收集的逐目标摘要（本函数会继续 push）
     * @param needsShift 本帧「确实被遮」的元素集合（用于 `want` / `err` 判定）
     * @param wantsBottom 各元素本帧目标底边（逐元素，见 `applyShift` 内说明）
     * @param keyboardTop 本帧键盘上沿 y（用于兜底目标位与摘要输出）
     */
    const emitShiftDiagnostics = (
      dbgParts: string[],
      needsShift: Set<HTMLElement>,
      wantsBottom: Map<HTMLElement, number>,
      keyboardTop: number,
    ) => {
      // 逐元素输出校正后的最终位置
      shiftedMap.forEach((dy, el) => {
        const sel = SHIFT_TARGETS.find((s) => el.matches(s)) ?? "?";
        const after = el.getBoundingClientRect().bottom;
        const shifted = needsShift.has(el);
        const want = shifted ? (wantsBottom.get(el) ?? keyboardTop - WANT_GAP) : NaN;
        /**
         * `after <= 0` 是**元素已脱离文档**的特征（`getBoundingClientRect` 返回全 0）。
         * 正常回收后不该出现；若再出现说明有新的幽灵路径，需继续排查。
         */
        const ghost = after <= 0 ? " GHOST!" : "";
        dbgParts.push(
          `FINAL ${sel} dy=${dy.toFixed(1)}` +
            ` shiftY=${(-dy).toFixed(1)}` +
            ` after=${after.toFixed(1)}` +
            ` want=${shifted ? want.toFixed(1) : "N/A"}` +
            ` err=${shifted ? (after - want).toFixed(1) : "N/A"}${ghost}`,
        );
      });

      if (dbgParts.length === 0) return;

      /**
       * 弹层观测项：记录「弹层底边相对工具条底边」的**实际偏移**。
       *
       * ★ 第十四轮结论：弹层是工具条子元素、会被连带，前两帧该差值应恒定
       * ≈ +40.0（= 原始布局的 40.7）——第十四轮日志实证「前两帧 +40.0、
       * 第三帧起突变为负并持续恶化」，正是 `overflow:auto` 触发 floating-ui
       * 防溢出中间件反复重定位所致（修法见 `writeShiftStyles`）。
       */
      const pop = document.querySelector<HTMLElement>(".bn-form-popover");
      /**
       * ★★ **弹层枚举探针**（TEMP-DEBUG，第十七轮新增）——用于回答一个此前
       * 被忽略的问题：**页面上到底有几个 `.bn-form-popover`？**
       *
       * ## 为什么必须查
       * 上面用 `querySelector` 只取**第一个**。若页面同时存在：
       * ① 官方工具条自己渲染的浮层（如 URL / 颜色输入框，位于
       *    `.bn-formatting-toolbar` 子树内 ⇒ `inToolbar=true`）；
       * ② 本项目 Rename 面板（经 `panelHost` Portal 到 `document.body` 下，
       *    容器 `.bn-root.bn-mantine` ⇒ **不在**工具条子树内）；
       * 则整个「连带位移」分析可能一直在**观察错误的对象**——看到几何全部
       * 正常却「用户看不到」，因为真正给用户看的那一个根本没被测量。
       *
       * ## 输出
       * 每个弹层一段：`#i[inToolbar= rect=(..) parentCls= hostCls=]`，
       * 其中 `hostCls` 是最近的 `.bn-root` / `.bn-mantine` 容器类名
       * （`bn-root bn-mantine` 即 `panelHost` ⇒ 是本项目的 Rename 面板）。
       */
      const allPops = Array.from(document.querySelectorAll<HTMLElement>(".bn-form-popover"));
      if (allPops.length) {
        dbgParts.push(
          `POP(list) n=${allPops.length} ` +
            allPops
              .map((p, i) => {
                const pr = p.getBoundingClientRect();
                const it = p.closest(".bn-formatting-toolbar") !== null;
                const host = p.parentElement?.closest(".bn-root, .bn-mantine") as HTMLElement | null;
                const hostCls = host ? (host.className || "(noclass)").toString().slice(0, 30) : "NONE";
                return (
                  `#${i}[inToolbar=${it}` +
                  ` rect=(${pr.left.toFixed(0)},${pr.top.toFixed(0)},` +
                  `${pr.width.toFixed(0)}x${pr.height.toFixed(0)})` +
                  ` pHost=${hostCls}]`
                );
              })
              .join(" "),
        );
      }
      if (pop) {
        const r = pop.getBoundingClientRect();
        /**
         * 祖先链探针（TEMP-DEBUG）：判断弹层**是否位于工具条的祖先链内**。
         *
         * 这是「连带位移」成立的前提——`position:fixed` 元素的**定位包含块**，
         * 只有在某个祖先带 `transform` 时才变成该祖先。若弹层渲染在
         * `FloatingPortal`（挂在 `body` 下）、**不在工具条子树内**，
         * 则工具条的 `transform` 对它**毫无影响**，必须自己避让。
         *
         * 输出：从弹层向上爬，记录是否遇到工具条、以及途中带 `transform` 的节点。
         */
        let inToolbar = false;
        const tfChain: string[] = [];
        /**
         * ★★ 裁剪探针（TEMP-DEBUG，第十四轮新增）：逐层检查祖先的
         * `overflow` / `clip` / 尺寸，并**用弹层矩形与该祖先矩形求交**，
         * 判定「弹层是否被该祖先裁掉」。
         *
         * ## 为什么需要它
         * 第十三轮日志已证明：位移后弹层 `after=326.9`、工具条 `after=366.9`、
         * 键盘上沿 `keyboardTop=370.9` —— **两者都在键盘上方，几何上没被遮挡**，
         * 但用户仍看不见弹层。而 `inToolbar=true` 证明弹层在**工具条子树内**，
         * 于是唯一剩下的解释是：**被祖先容器的裁剪边界切掉**
         * （或 z-index 被覆盖）。
         * 本探针一次性给出「哪一层裁、裁多少」，避免继续猜。
         *
         * ## 输出格式
         * 仅记录**可疑层**（`overflow` 非 visible、`clip-path` 非 none、
         * 或非 body/html 的固定尺寸容器），形如：
         * `div.cls[ov=hidden rect=(x,y,w,h) popOutside=true]`
         * `popOutside=true` 表示弹层矩形**部分落在该祖先矩形之外** ⇒ 该层是裁切嫌疑。
         */
        const clipChain: string[] = [];
        /**
         * ★★ **合成/容器属性探针**（TEMP-DEBUG，第十七轮新增）——当前最后一块盲区。
         *
         * ## 为什么加
         * 第十七轮真机现象：**工具条可见、弹层不可见**，但两者几何全部正常：
         * `gapToTb=+4.5`、`hit=IN_POP`（elementFromPoint 命中弹层内部 input）、
         * `op=1`、`vis=visible`、`disp=block`、`POP(clip)=NONE`、`rect` 在视口内。
         * 「工具条可见」直接排除了「WebView 可见区域被 Android 裁到键盘上沿」
         * 这一整块假设（否则父级的工具条也该不可见）。
         *
         * ⇒ 只剩一种可能：**弹层被某个祖先的「合成/包含」属性隔离了**。
         * `POP(clip)` 只查了 `overflow` 与 `clip-path`，漏掉以下会创建
         * **包含块 / 层叠上下文 / 扁平化**的属性：
         * - `opacity < 1`：让子元素被限制在父级绘制范围，且**新建层叠上下文**；
         * - `filter` / `backdrop-filter`：同上，且改变 `position:fixed` 的包含块；
         * - `contain: paint|layout|strict|content`：**强制裁切**子元素；
         * - `will-change: transform|opacity|filter`：提升为合成层，可能触发
         *   WebView 硬件层尺寸/位置计算差异；
         * - `perspective` / `transform-style: preserve-3d`：影响 3D 渲染上下文。
         *
         * ## 关键线索
         * 祖先链第二层是 `div.`（**类名为空**）且带 `transform: translate(20px, 641.455px)`
         * ——一个无类名 div 却带位移，极可能是 floating-ui 的浮动包裹层。
         * 它的这些属性必须逐一核对。
         *
         * ## 输出
         * 每层一条：`div.cls[op= filter= contain= wc= pos= z= rect=(..)]`，
         * 只记录**有可疑属性**的层，避免日志爆炸。
         */
        const compChain: string[] = [];
        let node: HTMLElement | null = pop.parentElement;
        let depth = 0;
        while (node && depth < 12) {
          const tag = node.tagName.toLowerCase();
          const cls = (node.className || "").toString().slice(0, 40);
          if (node.matches(".bn-formatting-toolbar")) inToolbar = true;
          const cs = getComputedStyle(node);
          const t = cs.transform;
          if (t && t !== "none") {
            tfChain.push(`${tag}.${cls}[${t.slice(0, 30)}]`);
          }
          // ★ 第二十五轮：位移载体改为 position/top，祖先链同步检测（定位后随埋点删除）
          if (cs.position === "relative" || cs.position === "absolute") {
            if (cs.top && cs.top !== "auto" && cs.top !== "0px") {
              tfChain.push(`${tag}.${cls}[${cs.position} top=${cs.top}]`);
            }
          }
          // 只在「可疑层」才记裁剪信息，避免日志爆炸
          const ovX = cs.overflowX;
          const ovY = cs.overflowY;
          const clip = cs.clipPath;
          // overflow 只要有一轴不是 visible（含 clip/hidden/auto/scroll）即可能裁切
          const isClipping = ovX !== "visible" || ovY !== "visible";
          const hasClipPath = clip && clip !== "none";
          if (isClipping || hasClipPath) {
            const ar = node.getBoundingClientRect();
            // 弹层是否部分越出该祖先矩形（含 1px 容差）
            const outside =
              r.top < ar.top - 1 ||
              r.bottom > ar.bottom + 1 ||
              r.left < ar.left - 1 ||
              r.right > ar.right + 1;
            clipChain.push(
              `${tag}.${cls}[ovX=${ovX} ovY=${ovY}` +
                (hasClipPath ? ` clip=${clip.slice(0, 20)}` : "") +
                ` aRect=(${ar.left.toFixed(0)},${ar.top.toFixed(0)},` +
                `${ar.width.toFixed(0)}x${ar.height.toFixed(0)})` +
                ` popOutside=${outside}]`,
            );
          }
          /**
           * 合成属性采集（TEMP-DEBUG）：只记**可疑**层——opacity < 1、
           * 有 filter/backdrop-filter、contain 非 none、will-change 非 auto、
           * 或 transform-style 非 flat。每层附尺寸与 position，便于对齐几何。
           */
          const op = parseFloat(cs.opacity);
          const filt = cs.filter;
          const bfilt = (cs as unknown as { backdropFilter?: string }).backdropFilter;
          const contain = cs.contain;
          const wc = cs.willChange;
          const ts3d = cs.transformStyle;
          const suspicious =
            op < 1 ||
            (filt && filt !== "none") ||
            (bfilt && bfilt !== "none") ||
            (contain && contain !== "none") ||
            (wc && wc !== "auto") ||
            (ts3d && ts3d !== "flat");
          if (suspicious) {
            const ar2 = node.getBoundingClientRect();
            compChain.push(
              `${tag}.${cls || "(noclass)"}[op=${cs.opacity}` +
                (filt && filt !== "none" ? ` filter=${filt.slice(0, 24)}` : "") +
                (bfilt && bfilt !== "none" ? ` bFilter=${bfilt.slice(0, 24)}` : "") +
                (contain && contain !== "none" ? ` contain=${contain}` : "") +
                (wc && wc !== "auto" ? ` wc=${wc}` : "") +
                (ts3d && ts3d !== "flat" ? ` ts=${ts3d}` : "") +
                ` pos=${cs.position} z=${cs.zIndex}` +
                ` rect=(${ar2.left.toFixed(0)},${ar2.top.toFixed(0)},` +
                `${ar2.width.toFixed(0)}x${ar2.height.toFixed(0)})]`,
            );
          }
          node = node.parentElement;
          depth++;
        }
        dbgParts.push(
          `POP(obs) bottom=${r.bottom.toFixed(1)} top=${r.top.toFixed(1)}` +
            ` h=${r.height.toFixed(1)}` +
            ` inToolbar=${inToolbar}` +
            ` tfAncestors=${tfChain.length ? tfChain.join(" < ") : "NONE"}`,
        );
        // ★ 裁剪探针独立一行（内容可能很长，避免挤掉上面的关键项）
        dbgParts.push(
          `POP(clip) ${clipChain.length ? clipChain.join(" < ") : "NONE"}`,
        );
        // ★ 合成/容器属性探针独立一行（第十七轮新增，见 compChain 的 KDoc）
        dbgParts.push(
          `POP(comp) ${compChain.length ? compChain.join(" < ") : "NONE"}`,
        );
        /**
         * ★★ 堆叠探针（TEMP-DEBUG，第十四轮新增）：若祖先链**没有裁切层**
         * （`clipChain` 为空），则「看不见」的原因只可能是**堆叠/合成**问题。
         * 这里一次性输出弹层自身与工具条的 `z-index` / `opacity` /
         * `visibility` / `display`，以及**弹层中心点在视口内的实际命中元素**
         * （`elementFromPoint`）—— 后者是判定「是否被别的元素盖住」的黄金标准：
         * 若命中结果不是弹层或其子孙，说明弹层被覆盖。
         */
        const popCs = getComputedStyle(pop);
        const cx = (r.left + r.right) / 2;
        const cy = (r.top + r.bottom) / 2;
        let hit = "?";
        if (cx >= 0 && cy >= 0 && cx <= window.innerWidth && cy <= window.innerHeight) {
          const hitEl = document.elementFromPoint(cx, cy);
          if (hitEl) {
            const hTag = hitEl.tagName.toLowerCase();
            const hCls = (hitEl.className || "").toString().slice(0, 40);
            const inPop = pop.contains(hitEl) || hitEl === pop;
            hit = `${hTag}.${hCls}${inPop ? " IN_POP" : " OUTSIDE_POP"}`;
          } else {
            hit = "null";
          }
        } else {
          hit = "OFFSCREEN";
        }
        const tb = document.querySelector<HTMLElement>(".bn-formatting-toolbar");
        const tbZ = tb ? getComputedStyle(tb).zIndex : "?";
        /**
         * ★★ **合成层修法验证探针**（TEMP-DEBUG，第十七轮新增）。
         *
         * `ensureCropStyle()` 用 `div:has(> .bn-formatting-toolbar)` 压掉浮动
         * 包裹层的 `will-change`。但 `:has()` 若未匹配（选择器写错 / 层级不是
         * 直接子元素 / 该层根本不带工具条），修法会**静默失效**——必须可视验证。
         *
         * 输出：`wcFix=<覆盖后实测值> match=<:has() 命中数>`
         * - `match=0` ⇒ 选择器没命中 ⇒ 需换写法（去掉 `>` 或用属性选择器）；
         * - `wcFix=transform` ⇒ 命中了但没压住（内联优先级问题）⇒ 改 `style` 直改。
         * 期望：`wcFix=auto` 且 `match>=1`。
         */
        if (tb) {
          const wrap = tb.parentElement;
          const wrapWc = wrap ? getComputedStyle(wrap).willChange : "?";
          let matchCount = 0;
          try {
            matchCount = document.querySelectorAll("div:has(> .bn-formatting-toolbar)").length;
          } catch {
            matchCount = -1; // :has() 不被支持
          }
          dbgParts.push(
            `POP(wcFix) wcFix=${wrapWc} match=${matchCount}` +
              ` wrapCls=${wrap ? (wrap.className || "(noclass)").toString().slice(0, 24) : "NONE"}`,
          );
        }
        /**
         * ★★ **轴向关系探针**（TEMP-DEBUG，第十六轮新增）：判定弹层相对工具条
         * 究竟是「在**上方**（正常）」还是「被 `flip()` 翻到**下方**」。
         *
         * ## 为什么必须量化
         * 第十五/十六轮真机出现两种截然不同的轨迹（同一份代码）：
         * | keyboardTop | 上轮弹层 y | 本轮弹层 y |
         * |---|---|---|
         * | 605.1 | 524 | 524 |
         * | 562.9 | 481 | 481 |
         * | 493.5 | **412**（跟随） | **495**（不跟随） |
         * | 370.9 | **289**（跟随） | **373**（不跟随） |
         * ⇒ 首两帧一致、第 3 帧（工具条首次被施加 `transform`）分道扬镳。
         * 本轮差值恒 **−42.4**（弹层底边在工具条底边**下方** 42.4）。
         *
         * ## 输出
         * - `place`：floating-ui 写入弹层 DOM 的 `data-popper-placement`
         *   （`top-start` = 在上方 ✅ 正确；`bottom-start` = 被翻到下方 ❌）
         * - `tbMatrix`：工具条 `transform` 的实际平移量（分离出 `translateY`）
         * - `yAxis=above|below`：按几何判定弹层中心在工具条中心的上/下
         * - `gapToTb`：弹层底边 − 工具条顶边（正数 = 弹层整体在工具条上方）
         */
        let place = "?";
        for (const attr of ["data-popper-placement", "data-placement", "data-floating-ui-placement"]) {
          const v = pop.getAttribute(attr);
          if (v) {
            place = v;
            break;
          }
        }
        const tbMatrix = tb ? getComputedStyle(tb).transform : "?";
        /**
         * ★ 第二十五轮：位移载体改为 `position:relative + top`，故补读 `top`。
         * `tbMatrix` 仍保留——它现在**只反映 floating-ui 自身的定位 transform**，
         * 不再包含我们的避让量（这本身也是一个可用的对照信号）。
         * 定位后随埋点一并删除。
         */
        const tbTopCss = tb ? getComputedStyle(tb).top : "?";
        const tbPos = tb ? getComputedStyle(tb).position : "?";
        const tbRect = tb ? tb.getBoundingClientRect() : null;
        const yAxis =
          tbRect === null ? "?" : r.top + r.height / 2 < tbRect.top + tbRect.height / 2 ? "above" : "below";
        const gapToTb = tbRect === null ? NaN : tbRect.top - r.bottom;
        dbgParts.push(
          `POP(stack) z=${popCs.zIndex} tbZ=${tbZ}` +
            ` op=${popCs.opacity} vis=${popCs.visibility} disp=${popCs.display}` +
            ` pos=${popCs.position}` +
            ` rect=(${r.left.toFixed(0)},${r.top.toFixed(0)},` +
            `${r.width.toFixed(0)}x${r.height.toFixed(0)})` +
            ` vp=${window.innerWidth}x${window.innerHeight} hit=${hit}` +
            ` place=${place} yAxis=${yAxis}` +
            ` gapToTb=${Number.isNaN(gapToTb) ? "?" : gapToTb.toFixed(1)}` +
            ` tbMatrix=${tbMatrix === "none" ? "NONE" : tbMatrix.slice(0, 40)}` +
            // ★ 第二十五轮：位移载体为 position:relative + top，补打实际值
            ` tbPos=${tbPos} tbTop=${tbTopCss}`,
        );
        /**
         * ★★ **渲染位置全链探针**（TEMP-DEBUG，第二十二轮新增）。
         *
         * ## 为什么需要
         * 第十二~二十一轮的探针已能证明：弹层 `getBoundingClientRect()` 全程在
         * 工具条上方 4px、`POP(clip) NONE`、`POP(comp) NONE`、`op=1`、`vis=visible`、
         * 中心点 `elementFromPoint` 命中弹层自身（`hit=IN_POP`）——**几何与命中全绿，
         * 但用户仍反馈"看不见"**。这说明问题在「**实际绘制到屏幕的像素**」这一层，
         * 而现有探针全部基于 `getBoundingClientRect()`（**布局几何**）——
         * 布局几何正确 ≠ 绘制可见（例：被 `overflow` 的**合成层**按自身 bounds 裁剪、
         * 被同级更高 `z-index` 的元素覆盖、被祖先 `transform` 搬到屏幕外、被
         * `clip-path`/`mask` 裁掉、或自身 `content-visibility` 跳过了绘制）。
         *
         * ## 输出设计（多点采样 + 全链几何）
         * 1. **`POP(draw)`**：在弹层矩形上取 **9 个采样点**（四角内缩 2px + 四边中点
         *    + 中心），逐点 `elementFromPoint` 并记录**命中元素是否在弹层内**。
         *    单点命中可能恰好落在弹层的某个"透明填充"区（如 padding 空隙、
         *    `pointer-events:none` 的装饰层）而误判；9 点全 `OUTSIDE` 才能确证被盖。
         * 2. **`POP(chain)`**：从弹层自身向上**逐层**输出每个祖先的
         *    `getBoundingClientRect` + `transform` + `overflow` + `clipPath` +
         *    `willChange` + `opacity` + `zIndex` + `pointerEvents`。
         *    一次性看清「是哪一层把弹层搬走/裁掉/盖住」，避免逐轮加探针。
         *
         * ## 判读
         * - 9 点全 `IN_POP` 但仍看不见 ⇒ 问题在**合成层裁剪**（查 `chain` 里的
         *   `overflow`/`transform`/`willChange` 组合）或**GPU 层被顶掉**；
         * - 部分点 `OUTSIDE` ⇒ 该位置被别的元素覆盖（`chain` 里找同级高 z-index）；
         * - `chain` 中某祖先 `rect` 的 `top` 为大负值 ⇒ 弹层被祖先 `transform`
         *   搬出视口。
         */
        try {
          const pts: Array<[string, number, number]> = [
            ["TL", r.left + 2, r.top + 2],
            ["TR", r.right - 2, r.top + 2],
            ["BL", r.left + 2, r.bottom - 2],
            ["BR", r.right - 2, r.bottom - 2],
            ["MT", (r.left + r.right) / 2, r.top + 2],
            ["MB", (r.left + r.right) / 2, r.bottom - 2],
            ["ML", r.left + 2, (r.top + r.bottom) / 2],
            ["MR", r.right - 2, (r.top + r.bottom) / 2],
            ["C", (r.left + r.right) / 2, (r.top + r.bottom) / 2],
          ];
          const sample: string[] = [];
          let inCount = 0;
          let outCount = 0;
          let offCount = 0;
          pts.forEach(([name, x, y]) => {
            if (x < 0 || y < 0 || x > window.innerWidth || y > window.innerHeight) {
              sample.push(`${name}=OFF`);
              offCount++;
              return;
            }
            const el = document.elementFromPoint(x, y);
            if (el && (pop.contains(el) || el === pop)) {
              sample.push(`${name}=IN`);
              inCount++;
            } else {
              const t = el ? `${el.tagName.toLowerCase()}.${(el.className || "").toString().slice(0, 16)}` : "null";
              sample.push(`${name}=OUT(${t})`);
              outCount++;
            }
          });
          dbgParts.push(
            `POP(draw) IN=${inCount} OUT=${outCount} OFF=${offCount} [${sample.join(" ")}]`,
          );
        } catch (e: any) {
          dbgParts.push(`POP(draw) ERR ${e?.message ?? e}`);
        }
        try {
          const chain: string[] = [];
          let cur: HTMLElement | null = pop;
          for (let d = 0; d < 8 && cur; d++) {
            const cs2 = getComputedStyle(cur);
            const rr = cur.getBoundingClientRect();
            const cls = (cur.className || "(noclass)").toString().replace(/\s+/g, ".").slice(0, 26);
            chain.push(
              `${cur.tagName.toLowerCase()}.${cls}` +
                `[${rr.left.toFixed(0)},${rr.top.toFixed(0)},` +
                `${rr.width.toFixed(0)}x${rr.height.toFixed(0)}]` +
                ` tf=${cs2.transform === "none" ? "-" : cs2.transform.slice(0, 28)}` +
                ` ovf=${cs2.overflow}` +
                ` clip=${cs2.clipPath === "none" ? "-" : cs2.clipPath.slice(0, 16)}` +
                ` wc=${cs2.willChange === "auto" ? "-" : cs2.willChange.slice(0, 12)}` +
                ` op=${cs2.opacity}` +
                ` z=${cs2.zIndex}` +
                ` pe=${cs2.pointerEvents}`,
            );
            cur = cur.parentElement;
          }
          dbgParts.push(`POP(chain) ${chain.join(" < ")}`);
        } catch (e: any) {
          dbgParts.push(`POP(chain) ERR ${e?.message ?? e}`);
        }
      }

      sendUp({
        type: "diagnostic",
        message:
          `[shift] ime=${imeHeightPx.toFixed(1)} gap=${imeGapDp.toFixed(1)}` +
          ` innerH=${window.innerHeight} keyboardTop=${keyboardTop.toFixed(1)}` +
          ` domN=${SHIFT_TARGETS.map((s) => document.querySelectorAll(s).length).join("/")}` +
          ` || ${dbgParts.join(" ;; ")}`,
      });
    };

    /** 计算并应用位移；元素消失或键盘收起时清理残留位移 */
    const applyShift = () => {
      /**
       * 【第一道门】键盘**绝对高度**过小 ⇒ 视为收起（阈值见 `KEYBOARD_HIDDEN_DP`，
       * 它已提前到 effect 顶部声明，避免 TDZ）。
       *
       * 注：第九轮又加了**第二道门**（`occluded <= 0`，见下）——它从
       * 「键盘侵入视口的净量」角度覆盖了更广的情况，本门在语义上已被包含；
       * 但两者判据不同（绝对高度 vs 净侵入量），保留双重检查更直观也更能容错。
       */
      if (imeHeightPx < KEYBOARD_HIDDEN_DP) {
        clearAll();
        return;
      }
      /**
       * 键盘上沿在 **WebView 视口坐标**里的 y。
       *
       * 推导：键盘从**屏幕底**向上升起 `imeHeightPx`；而 WebView 视口底边
       * 距离屏幕底还有 `imeGapDp`（弹层期 = 底部工具栏高度），所以键盘先吃掉
       * 这段 gap，**真正盖住视口的高度** = `imeHeightPx - imeGapDp`。
       * 视口底边 y = `window.innerHeight`，故：
       *   键盘上沿 y = innerHeight - (imeHeightPx - imeGapDp)
       *
       * 真机数值核对：innerHeight=628、ime=329dp、gap=72dp
       * → 键盘上沿 y = 628 - (329-72) = 371（合理：视口下部 257dp 被盖）。
       *
       * ⚠️⚠️ **`occluded <= 0` 时必须整体跳过避让**（v2026-09-30 第九轮真机定案）。
       *
       * 旧写法把 `occluded` 夹成 `Math.max(occluded, 0)` 得到 `keyboardTop = 视口底`，
       * 再拿它当「键盘上沿」逐元素比 `rect.bottom`——但**键盘根本没进视口**时，
       * 这个基准毫无意义，会把本来正常渲染的元素判成「被遮」并强行推走。
       * 真机铁证（键盘收起动画尾部 `ime=66.2 → 4.0`，此时 `keyboardTop` 恒为 628）：
       * ```
       * .bn-formatting-toolbar RAW overflow=0.2  → dy=0     ✅ 未遮，正确
       * .bn-form-popover       RAW overflow=42.9 → dy=46.9  ❌ 被强推
       * ```
       * 弹层 `bottom=669.9` 超出视口底（628）是**它的正常渲染态**（贴视口下沿、
       * 部分溢出），根本不是「被键盘遮挡」。
       * ⇒ **判据是「键盘是否真的侵入视口」（`occluded > 0`），而不是「元素底部
       * 是否超过某条线」**。侵入量 ≤ 0 就整帧不避让。
       */
      const occluded = imeHeightPx - imeGapDp;
      if (occluded <= 0) {
        clearAll();
        return;
      }
      const keyboardTop = window.innerHeight - occluded;

      // 本轮仍在场的目标（用于回收已消失元素的位移记录）
      const alive = new Set<HTMLElement>();

      /**
       * 本轮**确实被键盘遮挡**（`overflow > 1`）的目标。
       * 只有它们才需要进闭环校正——未被遮的元素保持 `dy = 0` 不动，
       * 否则会被校正段强行拽到键盘上沿（见下方 `else` 分支的说明）。
       */
      const needsShift = new Set<HTMLElement>();

      /**
       * 每个目标**本帧的目标底边位置**（`rect.bottom` 应收敛到的值）。
       *
       * ★ v2026-09-30 第二十二轮：清单只剩工具条一项，目标位统一为
       * `max(keyboardTop − WANT_GAP, VIEWPORT_TOP_MARGIN + rect.height)`
       * （贴键盘上沿 + 视口上缘夹取，见遍历段内的说明）。
       *
       * 仍保留 Map 结构（而非退化成单个数字）：① 与下方闭环校正段的
       * `wantsBottom.get(el)` 取值方式保持一致；② 后续若再增避让目标，
       * 只需给它在遍历段写入各自目标位，无需改动校正段。
       */
      const wantsBottom = new Map<HTMLElement, number>();

      /** TEMP-DEBUG：本帧各目标的位移摘要（清理时整段删除） */
      const dbgParts: string[] = [];

      SHIFT_TARGETS.forEach((sel) => {
        const el = document.querySelector<HTMLElement>(sel);
        if (!el) {
          // TEMP-DEBUG：选择器未命中（清理时删除该分支的 push）
          dbgParts.push(`${sel} MISSING`);
          return;
        }
        alive.add(el);

        /**
         * ★★ v2026-09-30 第二十八轮：**不再摘属性**，用「已施加位移」反算原始 rect。
         *
         * ## 旧写法为什么必须改（真机铁证见调度器段的 KDoc）
         * 旧写法 `el.removeAttribute(SHIFT_ATTR); const rect = el.getBoundingClientRect();`
         * 为了「拿到未位移的 rect」，把元素的位移**真实地**撤掉了一瞬。这一瞬：
         * ① 工具条回到原始位（`t=592.7`）、弹层连带掉回键盘后（`t=632.4`）
         *    —— `[idProbe]` 实测在同一毫秒区间内 `t` 在 `632.4 ↔ 371.9` 横跳；
         * ② 每次摘/装都是一次 DOM 属性变更，被本 effect 自己的
         *    `MutationObserver(document.body, childList+subtree)` 捕获
         *    ⇒ `schedule()` ⇒ `applyShift()` ⇒ 又摘又装 ⇒ **自激反馈环**；
         * ③ 真机 `[attrHook]` 统计 `removeAttribute x70` vs `setAttribute x36`
         *    ⇒ 属性「不存在」的时间**多于**存在的时间，用户看到弹层在键盘后闪烁。
         *
         * ## 新写法（纯算术，零 DOM 写入）
         * 元素当前的 `rect.bottom` **已经包含**了本 effect 上一轮施加的位移。
         * 设上一轮记录的「向上位移量」为 `prevDy`（`shiftedMap` 里的值，
         * 正=向上），则样式表施加的 `top` 为 `shiftY = −prevDy`，
         * 元素实际被移动了 `shiftY`（`position:relative` 的位移计入布局），
         * 故**未位移时的底边**为：
         * ```
         * rawBottom = rect.bottom − shiftY = rect.bottom + prevDy
         * ```
         * ⚠️ 上一帧若**未**施加位移（`shiftedMap` 无记录），则 `prevDy = 0`，
         * `rawBottom = rect.bottom`——正确。
         *
         * ⚠️ **不能假设 `prevDy` 一定等于「使 `rect.bottom == targetBottom` 的值」**：
         * 元素可能受人 `<html>/<body>` 缩放、第三方 transform、滚动位置影响，
         * 导致「写进去的 `top`」与「实际移动量」不成严格 1:1。
         * ⇒ 闭环校正（下方 `for pass < 3`）**必须保留**，它每帧回读真实位置并补偿，
         * 是通用保险。本段只负责给出一个**合理的初始估计**。
         *
         * ⚠️ **顺序不变**：仍需先测量（拿 `height` 算视口上缘夹取），再算目标位。
         * 只是「测量」不再需要副作用。
         */
        const prevDy = shiftedMap.get(el) ?? 0;
        const rect = el.getBoundingClientRect();
        /**
         * 未位移时的底边/顶边（供下方 `overflow` / `topOverflow` 判定使用）。
         *
         * ⚠️ 符号方向：`prevDy` 正 = 向上 ⇒ 未位移位在**下方** ⇒
         * `rawBottom = rect.bottom + prevDy`（`rawTop` 同理）。
         *
         * 真机核对（17:31 `[shift]` 日志）：`dy=261.3` ⇒ `rect.bottom=366.9`
         * ⇒ `rawBottom = 366.9 + 261.3 = 628.2`，与旧实现「摘属性后实测」
         * 的 `RAW bottom=628.2` **完全一致** ⇒ 公式正确。
         */
        const rawBottom = rect.bottom + prevDy;
        const rawTop = rect.top + prevDy;
        const rectH = rect.height;
        const rectW = rect.width;

        /**
         * 目标底边：**贴键盘上沿，并做视口上缘夹取**（v2026-09-30 第十七轮定案）。
         *
         * ## 朴素目标
         * `keyboardTop − WANT_GAP`（紧贴键盘上沿、留 4px 呼吸感）。
         *
         * ## ★ 视口上缘夹取（为什么必须有）
         * 朴素目标是**单向硬顶**：只算底边超出，不检查顶边会不会捅穿视口上缘。
         * 当锚点（视频块/光标）位于编辑区**靠上**处时，键盘一顶、
         * `targetBottom` 随之上移，元素被一路往上推出屏幕（`rect.top < 0`）
         * ⇒ 用户视角「编辑页里完全看不到工具条，连点都点不到」。
         *
         * 夹取规则：元素**最高只能到** `VIEWPORT_TOP_MARGIN + rect.height`，
         * 即
         * ```
         * targetBottom = max(keyboardTop − WANT_GAP, VIEWPORT_TOP_MARGIN + rect.height)
         * ```
         * 取 `max`（取**更低**的位）：空间足时贴键盘；空间不足时接受部分被挡，
         * 但**保证整个元素在视口内**（可见、可点）。
         * 这是「**可见性优先于完全避让**」的取舍。
         *
         * ## ★★ 第二十二轮：清单只剩工具条一项，本分支不再有「角色区分」
         * 弹层 `.bn-form-popover` 已移出 `SHIFT_TARGETS`（见其 KDoc 的证据链），
         * 原因是对官方 Popover 直写 `transform` 属于**越界操作第三方 DOM**：
         * ① 它是 `position: fixed`，宿主位移会改变其定位包含块；
         * ② floating-ui 中间件每帧读几何做决策，会被 `!important` 位移污染；
         * ③ 官方 `Popover` 的关闭判定（`useDismiss` 等）依赖交互落点，同样错乱。
         * ⇒ 改为**只动工具条**、弹层靠父子连带（第十四轮已验证连带成立）。
         * 原本的 `isPopover` 分支（目标位 = 工具条目标底边 − 工具条高 −
         * `POP_ABOVE_GAP`）随之删除，`POP_ABOVE_GAP` 常量一起移除。
         *
         * ## ★★ 第二十九轮：弹层回归清单，目标位分「角色」计算
         * 上一条「只动工具条」在第二十九轮被证伪（弹层连带后仍在工具条下方、
         * 正好落进键盘，见 `SHIFT_TARGETS` 的 KDoc）。本轮弹层重新纳入。
         *
         * ⚠️⚠️ **两者不能共用同一个目标位**——这正是第十轮踩过的坑：
         * ```
         * 第十轮：两者同目标位 ⇒ 弹层（w=305.5）盖住工具条（w=291.5）❌
         * ```
         * 工具条和弹层都贴 `keyboardTop − WANT_GAP` 时，底边重合、两块浮层
         * **完全重叠**，用户看到的是「弹层盖住工具条」（弹层更宽更高、z 更大）。
         *
         * ⇒ 目标位按**角色**区分（本段即「角色区分」的回归）：
         * - **工具条**：贴键盘上沿 `keyboardTop − WANT_GAP`（与第十七轮一致）；
         * - **弹层**：落在**工具条正上方**，即
         *   `工具条目标底边 − 工具条实际高 − POP_ABOVE_GAP`。
         *   用户需求原话：「弹层**恒在工具条上方**」。
         *
         * 视口上缘夹取（第十七轮）对两者都保留：空间不足时保证整体可见。
         */
        const isPopoverEl = el.matches(".bn-form-popover");
        /**
         * 取工具条当前几何（用于算弹层的目标位）。
         *
         * ⚠️ 用 `offsetHeight`（布局高度）而非 `getBoundingClientRect().height`：
         * 弹层的定位基准是工具条的**布局盒**，且工具条此刻可能带 `top` 位移
         * （`relative` 位移**不改布局高度**），两者数值一致但前者语义更准。
         * 取不到工具条时退化为「弹层自己也贴键盘上沿」（至少不遮键盘）。
         */
        const tbEl = document.querySelector<HTMLElement>(".bn-formatting-toolbar");
        const tbH = tbEl ? tbEl.offsetHeight : 0;
        const targetBottom = isPopoverEl
          ? Math.max(
              keyboardTop - WANT_GAP - tbH - POP_ABOVE_GAP,
              VIEWPORT_TOP_MARGIN + rectH,
            )
          : Math.max(keyboardTop - WANT_GAP, VIEWPORT_TOP_MARGIN + rectH);
        wantsBottom.set(el, targetBottom);

        /**
         * ⚠️ 判定必须用**未位移**的 `rawBottom` / `rawTop`（第二十八轮改动）。
         *
         * 旧写法用 `rect.bottom`（当前实际位置）——在旧实现里那是"刚摘掉位移"的
         * 读数，等价于未位移位；新实现不再摘属性，故必须**显式加回 `prevDy`**，
         * 否则判定会拿"已避让后"的位置去比目标位，`overflow` 恒 ≤1
         * ⇒ 误判为「无需避让」⇒ 位移被归零 ⇒ 下一帧又被判「需要避让」
         * ⇒ 又一次自激振荡（这正是第七轮 `else if` 删记录那个坑的同类）。
         *
         * ## ★★ 第二十九轮：弹层**例外**——不能反算 `rawBottom`
         *
         * 弹层是工具条的**子元素**，工具条的 `relative + top` 位移会把它
         * **整体带走**。因此弹层的 `rect.bottom` 里混入了两层位移：
         * ```
         * rect.bottom = 原始位 + 工具条连带位移(−261.3) + 弹层自身位移
         * ```
         * 而 `prevDy` **只记录弹层自身那一层**，于是
         * `rawBottom = rect.bottom + prevDy` 会把工具条的连带位移也当成
         * 「我们自己施加的」⇒ 反算值**偏小 261.3px** ⇒ `overflow` 误判。
         *
         * ⇒ 弹层改用**实际 `rect.bottom`** 判定（不反算），理由：
         * ① 它的「原始位」本身没有稳定语义（取决于 floating-ui 每帧的决策）；
         * ② `needAvoid` 只需回答「现在是否被键盘遮」——用当前真实位置判定最直接；
         * ③ 弹层只要出现在键盘区就**必须**避让，不存在「上一帧已避让、
         *    这一帧不用动」的稳态（工具条一动它就动），故无需反算原始位。
         */
        const overflow = (isPopoverEl ? rect.bottom : rawBottom) - targetBottom;
        const topOverflow = VIEWPORT_TOP_MARGIN - (isPopoverEl ? rect.top : rawTop);
        const needAvoid = overflow > 1;
        const needPullDown = topOverflow > 1;
        // TEMP-DEBUG：无条件记录几何判据（清理时删除）
        const cs = getComputedStyle(el);
        dbgParts.push(
          `${sel} RAW rawB=${rawBottom.toFixed(1)} rawT=${rawTop.toFixed(1)}` +
            ` prevDy=${prevDy.toFixed(1)}` +
            ` curB=${rect.bottom.toFixed(1)}` +
            ` h=${rectH.toFixed(1)} w=${rectW.toFixed(1)}` +
            // ★ 第二十五轮：位移载体由 transform 改为 position/top，诊断同步
            ` pos=${cs.position}` +
            ` topCss=${cs.top}` +
            ` transform=${cs.transform === "none" ? "NONE" : "SET"}` +
            ` opacity=${cs.opacity} vis=${cs.visibility}` +
            ` target=${targetBottom.toFixed(1)}` +
            ` overflow=${overflow.toFixed(1)}` +
            ` topOv=${topOverflow.toFixed(1)}` +
            ` kt=${keyboardTop.toFixed(1)}` +
            ` mode=${needAvoid ? "AVOID" : needPullDown ? "PULL" : "IDLE"}`,
        );
        /**
         * ⚠️⚠️ **不要因 `overflow <= 1` 就删除记录**（v2026-09-30 第七轮真机定案）。
         *
         * 旧写法 `else if (shiftedMap.has(el)) shiftedMap.delete(el)` 会造成
         * **振荡**：删除记录 → 元素丢掉 `data-ime-shift` → 自身位移归零 →
         * 下一帧测量又变成「被遮」→ 重新写入记录。而弹层恰好处在「父级连带
         * 抬起后自身不需要位移」的临界态，于是逐帧在「有位移 / 无位移」间跳。
         *
         * ⇒ 只要元素**在场**就保留记录，让**闭环校正**把它收敛到正确值
         * （`overflow <= 1` 时初值给 0，校正再按实际误差微调）。真正需要
         * 清记录的只有「元素已从 DOM 消失」，那由 effect 末尾的 `alive` 回收段处理。
         */
        /**
         * ★★ **第十七轮：避让触发条件扩为「三选一」**（真机定案）。
         *
         * 旧逻辑只看 `overflow > 1`（底边超出目标位）⇒ 单向往上推。第十七轮
         * 在 `targetBottom` 上加了视口上缘夹取后，必须**同时**处理「顶边越界」：
         *
         * ```
         * 情形 A（正常避让）：底边超出目标位  ⇒ 往上推（旧逻辑，不变）
         * 情形 B（上缘越界）：顶边 < 上缘余量 ⇒ 往下拉回视口内（本轮新增）
         * 情形 C（无需避让）：两者都不成立   ⇒ 位移归零（旧逻辑，不变）
         * ```
         *
         * 情形 B 的存在意义：元素**原位就已经捅穿视口上缘**时（例：floating-ui
         * 把工具条摆到了 `top = -20`），即使 `overflow <= 1`（不需要为键盘让位）
         * 也必须把它**拉回可见区域**，否则用户看不到它。这正是本轮要修的
         * 用户可见症状：「编辑页里都看不到工具条」。
         */
        if (needAvoid || needPullDown) {
          /**
           * ★★ 第二十八轮：**仅在属性尚不存在时才写**。
           *
           * 旧写法无条件 `setAttribute`——即使属性已存在（值相同），
           * 也会产生一次 DOM 属性变更记录，被本 effect 自己的
           * `MutationObserver` 捕获 ⇒ 多一次无谓的 `schedule()`。
           * 属性存在与否是**布尔状态**，`setAttribute` 幂等，故先查再写即可。
           */
          if (!el.hasAttribute(SHIFT_ATTR)) el.setAttribute(SHIFT_ATTR, "");
          /**
           * 位移量（向上为正）：
           * - 情形 A：`overflow + 4`（往上推，4px 余量避免贴边，闭环校正收敛）；
           * - 情形 B（且不需避让）：`-topOverflow`（**负值 = 往下推**，
           *   把顶边拉回 `VIEWPORT_TOP_MARGIN`）。
           * 两者同时成立时取**避让方向**（往上）——因为夹取后的 `targetBottom`
           * 已经保证了「顶到上限就不会出上缘」，避让本身即满足可见性。
           *
           * ## ★★ 第二十九轮：弹层必须**累加**，不能重置
           *
           * 工具条能重置是因为它用 `rawBottom`（**反算出的原始位**）算 `overflow`
           * ⇒ `overflow + 4` 是一个**相对原始位的绝对量**，每帧算出来都一样，
           * 重置即正确。
           *
           * 弹层用的是**当前实际位** `rect.bottom`（见上方判定段的第二十九轮说明）
           * ⇒ `overflow` 是「**还差多少**」的相对量。若仍写
           * `shiftedMap.set(el, overflow + 4)`（重置），会形成死循环：
           * ```
           * 帧1：rect.bottom=409.4 目标=327.4 ⇒ overflow=82 ⇒ dy=82 写入 margin-top:-82
           * 帧2：rect.bottom=327.4 目标=327.4 ⇒ overflow=0 ⇒ 不进 A 分支
           *      ⇒ 走 else 归零 ⇒ 弹层弹回 409.4
           * 帧3：回到帧1状态 ⇒ 无限振荡（每帧一弹）
           * ```
           * ⇒ 弹层改为**在上一帧位移基础上累加本帧误差**：
           * `dy_new = dy_old + overflow`。这样 `overflow` 收敛到 0 时
           * `dy` 保持稳定，弹层稳稳停在目标位。
           *
           * ⚠️⚠️ **弹层不要加那个 `+4`**（工具条才需要）：
           * `overflow` 对弹层是「当前实际位与目标位的**差值**」，直接作为
           * `margin-top` 增量即可精确落位（`overflow=82` ⇒ `margin-top:-82`
           * ⇒ `409.4−82=327.4` = 目标位，一步到位）。
           * 多加 4 会**过量 4px**（弹层会越过目标位 4px，虽不影响可见性，
           * 但与工具条之间的间隙变成 8px，与 `POP_ABOVE_GAP=4` 的设计不符）。
           * 工具条需要 `+4` 是因为它的 `overflow` 是**相对原始位**的量，
           * 4px 用于避开「贴边即又判超」的临界抖动。
           *
           * ⚠️ 累加会继承上一帧的 `prevDy`——这正是我们要的：弹层位置是
           * **累积量**（工具条连带 + 自身位移），只有累加才能表达。
           */
          shiftedMap.set(
            el,
            isPopoverEl
              ? prevDy + (needAvoid ? overflow : -topOverflow)
              : needAvoid
                ? overflow + 4
                : -topOverflow,
          );
          needsShift.add(el);
        } else {
          /**
           * 当前未被遮且未越界 ⇒ **工具条位移归零 / 弹层保持原位**
           * （不是「留着让校正微调」）。
           *
           * ⚠️ 注意此处**不能**因为归零就 `removeAttribute`：属性一摘规则即不匹配，
           * 会让下一帧又判成「被遮」而振荡（第七轮踩过）。正确做法是
           * **保留属性（规则仍匹配）+ 把值写成 0**，等价于无位移且状态稳定。
           *
           * ⚠️ **未被遮挡的元素不要进闭环校正**（v2026-09-30 第八轮真机定案）：
           * 校正段的唯一目标是「把 `rect.bottom` 推到目标位」——
           * 对**本来就没被遮**的元素（如键盘很矮时的弹层，`overflow=-40.5`），
           * 这会把元素**强行拽到键盘上沿**，产生毫无必要的位移。
           * 真机症状：`ime=15.3` 时弹层 `RAW overflow=-40.5` 却被施加 `shiftY=40.0`
           * （往下推 40px）；键盘收起后仍被钉在 `after=624`。
           * ⇒ 只有需要避让/拉回的元素才需要校正。
           *
           * ## ★★ 第二十九轮：弹层**不能归零**，必须保留上一帧位移
           *
           * 工具条归零是对的：它用 `rawBottom` 反算原始位，归零后弹回原始位、
           * 下一帧再判也无妨（`rawBottom` 不变，判定结果稳定）。
           *
           * 弹层归零则会**振荡**——因为它的「原始位」在工具条下方、恰好在键盘里
           * （真机 `b=409.4`，键盘上沿 `370.9`）。归零 ⇒ 弹层落回键盘 ⇒
           * 下一帧 `overflow > 1` ⇒ 又推上去 ⇒ 每帧一弹。真机症状：**弹层闪烁**。
           *
           * ⇒ 弹层在此分支**保持 `prevDy` 不动**（当前位置已满足目标，
           * 无需再调），且**不加入 `needsShift`**（不参与闭环校正，避免被拽走）。
           * 这样弹层一旦到位就稳定停住。
           *
           * ⚠️ 弹层的 `dy` 必须**写回 `shiftedMap`**（不能只是不写）：
           * `writeShiftStyles()` 遍历 `shiftedMap` 生成规则，若删了记录则
           * 规则消失、`margin-top` 失效 ⇒ 弹层弹回键盘。
           */
          if (!el.hasAttribute(SHIFT_ATTR)) el.setAttribute(SHIFT_ATTR, "");
          shiftedMap.set(el, isPopoverEl ? prevDy : 0);
          needsShift.delete(el);
        }
      });

      /**
       * ★★ **先回收已消失的元素，再做校正与诊断**（v2026-09-30 第十轮真机定案）。
       *
       * ## 为什么回收必须前置
       * `.bn-form-popover` 的内容会被 React 用 `dangerouslySetInnerHTML` 重建
       * （如 Rename 弹层重渲染），**旧的 DOM 引用会从文档树脱离**。此时：
       * - 旧引用仍留在 `shiftedMap` / `needsShift` 里（key 是元素对象，不随 DOM 走）；
       * - 已脱离文档的元素 `getBoundingClientRect()` **返回全 0**；
       * - 若校正段先跑，就会读到 `actual = 0`，算出 `err = 0 − (keyboardTop−4) ≈ −371`
       *   这样的大负数，把 `dy` 污染成一个荒谬值；
       * - `writeShiftStyles` 还会为死元素生成**重复规则**。
       * 真机铁证（第十轮）：一帧内 `FINAL` 输出 6 条（应 2 条），其中
       * `after=0.0` + `dy=261.3` 的组合就是幽灵元素——`after=0` 是"已脱离文档"的
       * 典型特征，`dy` 是它活着时的残留值。**用户可见症状：工具条被推到视口外、看不见。**
       *
       * ⇒ **回收（按 `alive` 剔除死元素）必须在「校正 → 诊断」之前完成**，
       * 三者顺序固定为：**清理幽灵 → 闭环校正 → 诊断输出**。
       */
      Array.from(shiftedMap.keys()).forEach((el) => {
        if (!alive.has(el)) {
          el.removeAttribute(SHIFT_ATTR);
          shiftedMap.delete(el);
          needsShift.delete(el);
        }
      });
      writeShiftStyles();

      /**
       * ★★ **闭环校正**（v2026-09-30 第六轮，本问题的最终修法）。
       *
       * ## 为什么必须闭环
       * 上一步的位移是**开环**算的：`dy = 当前 rect.bottom − keyboardTop`。
       * 但「施加 transform 后元素实际移动多少」并**不等于** `dy`。历史上
       * `.bn-form-popover`（`position:fixed`、包含块受祖先 `transform` 影响）曾
       * 因父级工具条同时位移而**双重叠加**，真机铁证（第六轮）：
       * ```
       * .bn-formatting-toolbar dy=261.3 after=366.9 want=366.9   ✅
       * .bn-form-popover      dy=220.6 after=106.4 want=366.9   ❌ 多冲 260.5
       * ```
       * 而**父级是否已位移是逐帧变化的**（键盘动画期、浮层进出场），
       * 静态推导叠加关系不可靠 ⇒ **直接量测误差并补偿**。
       *
       * ★ 第十一轮起清单只剩工具条一个元素，闭环校正仍然**必须保留**：
       * 工具条自身也可能受 `<html>/<body>` 级缩放、第三方 `transform`、
       * 滚动位置等影响，开环算出的 `dy` 未必等于实际位移量。闭环是**通用保险**。
       *
       * ## 做法
       * 每帧最多三轮：施加 → 回读 `rect.bottom` → 与**该元素的目标位**
       * `wantsBottom.get(el)` 比对 → 误差超过 1px 就把误差**累加**进已记录位移
       * 并重写样式表。
       * 样式表重写**同步生效**，回读即为最终位置 ⇒ 理论上两轮收敛；**留三轮**
       * 是因为初值偏差可能很大（第七轮实测初始误差达 108.6px，需要更大搜索空间；
       * 三轮后 `err` 应 ≤1）。
       *
       * ⚠️ 累加方向：`dy` 是「向上为正」的量，`err = actual − want`
       * （`>0` 表示仍偏低、需再抬）⇒ `dy += err` 方向正确，**负值合法**
       * （表示需要向下超出原位的量，见 `writeShiftStyles` 的 `-${dy}` 陷阱注释）。
       *
       * ⚠️ **只校正被遮元素**（见 `needsShift`）：没被遮的元素 `dy` 保持 0，
       * 不能被拽到目标位。
       *
       * ⚠️ **目标位逐元素取值**（`wantsBottom`），不再统一用 `keyboardTop − 4`
       * —— 每个元素的目标位可能不同（历史上弹层曾贴工具条正上方，
       * 第二十二轮起只剩工具条、目标位即 `keyboardTop − 4` + 上缘夹取）。
       */
      for (let pass = 0; pass < 3; pass++) {
        writeShiftStyles();
        let worst = 0;
        needsShift.forEach((el) => {
          const dy = shiftedMap.get(el);
          if (dy === undefined) return;
          const actual = el.getBoundingClientRect().bottom;
          const err = actual - (wantsBottom.get(el) ?? keyboardTop - WANT_GAP);
          if (Math.abs(err) > 1) {
            shiftedMap.set(el, dy + err);
            worst = Math.max(worst, Math.abs(err));
          }
        });
        if (worst <= 1) break;
      }
      writeShiftStyles();

      /**
       * TEMP-DEBUG（验证期用，清理时删除）：汇总位移结果上行。
       * 实现见 `emitShiftDiagnostics`（已抽离，避免与业务逻辑交织 + 局部名遮蔽）。
       */
      emitShiftDiagnostics(dbgParts, needsShift, wantsBottom, keyboardTop);
    };

    /** 用 rAF 合并连续触发（键盘动画期宿主会高频下发 imeHeight） */
    let raf = 0;
    const schedule = () => {
      if (raf) return;
      raf = requestAnimationFrame(() => {
        raf = 0;
        applyShift();
      });
    };

    /**
     * ★★ v2026-09-30 **第二十八轮：自激反馈环修复**（本问题的最终定案）。
     *
     * ## 真机铁证（17:31 日志，222068 字节，`[attrHook]` 106 条 / `[idProbe]` 14 条）
     *
     * 第四轮身份探针把前三轮的猜测全部枪毙并锁定到**我们自己的代码**：
     * - `pop=#1 tb=#2` **全程恒定** ⇒ 节点从未被 React 替换（机制 X 排除）；
     * - `[attrHook]` 的调用栈**全部指向 `editor.html:277:6527 / :7095 / :53`**，
     *   即本项目编译产物中的 `applyShift`（`el.removeAttribute`）与
     *   `clearAll`（`el.removeAttribute`）——**没有任何第三方代码触碰该属性**；
     * - 时序上 `removeAttribute`(70 次) 与 `setAttribute`(36 次) **相差 34 次**
     *   ⇒ 属性处于「被摘掉」状态的时间**远多于**「装上」的时间。
     *
     * ## 因果链（自激循环）
     * ```
     * applyShift()
     *  ├─ el.removeAttribute(SHIFT_ATTR)   ← ★ 摘属性 = 工具条瞬间回到原始位
     *  │      ⇒ 弹层连带回落 t=632.4（键盘后面）
     *  ├─ el.getBoundingClientRect()        ← 读到"未位移"的 rect
     *  ├─ el.setAttribute(SHIFT_ATTR, "")   ← 再装回
     *  │      ⇒ 弹层再抬回 t=371.9
     *  └─ writeShiftStyles()（重写 <style> 文本 = 又一个 DOM 变更）
     *         ↓
     *   MutationObserver(document.body, childList+subtree)
     *         ↓
     *   schedule() → rAF → applyShift()  ← 循环回到第 1 步
     * ```
     * `[idProbe]` 同一毫秒级的证据（`11.105 → 11.199 → 11.299 → 11.388`）：
     * ```
     * t=632.4 tbAttr=N tbPos=static    ← 摘属性，弹层掉回键盘后
     * t=378.4 tbAttr=Y tbTop=-254.7px  ← 装上，抬起
     * t=632.4 tbAttr=N tbPos=static    ← 又摘！
     * t=371.9 tbAttr=Y tbTop=-261.3px  ← 又装
     * ```
     * **摘和装之间的那一帧被真实渲染出来** ⇒ 用户看到弹层在键盘后面闪/看不见。
     *
     * ## 修法：**不再摘属性**，改用「位移量反算原始 rect」
     *
     * `removeAttribute` 这一步的本意是「拿到未位移的 `rect` 作为初始估计」，
     * 但这个估计**立刻就被闭环校正（下方 `for pass < 3`）覆盖**——它唯一的作用
     * 是把初始 `dy` 从 0 抬到一个接近正确的值。而**同样的结果可以纯算术得到**：
     * ```
     * 原始 rect.bottom = 当前 rect.bottom − 已施加的 shiftY
     *                  = 当前 rect.bottom − (−dy) = 当前 rect.bottom + dy
     * ```
     * 因为 `position: relative + top: shiftY`（`shiftY = −dy`）**恰好把元素移动了
     * `shiftY`**，`getBoundingClientRect()` 的返回值也正好偏移了 `shiftY`
     * （`relative` 位移计入布局，`rect` 反映最终位置——与第二十五轮的结论一致）。
     *
     * ⇒ **整个 `applyShift` 不再触碰任何目标元素的属性**，`MutationObserver` 的
     * `childList` 不再被自身的属性写入触发，自激环从**源头断开**。
     *
     * ## 为什么保留 `data-ime-shift` 属性
     * 它仍是「样式表规则是否匹配」的开关（`writeShiftStyles` 生成
     * `[data-ime-shift]` 选择器）——但现在**只在 `needAvoid`/`needPullDown`
     * 状态**真正翻转**时写，且**绝不为了"测量"而摘**。属性写入次数从
     * 「每帧 2 次」降到「每次状态变化 1 次」。
     *
     * ## 配套：`MutationObserver` 只认「弹层增删」
     * 原 `MutationObserver(() => schedule())` 对 `document.body` 的**任意子树变更**
     * 都重算——正文打字、图片加载、floating-ui 改弹层 `style`……都会触发
     * `schedule()`，其中任何一次 `applyShift` 的 DOM 写入又回头触发它。
     * ⇒ 改为**只在 `.bn-form-popover` 的挂载/卸载**（`present` 翻转）时调度，
     * 这才是「弹层打开/关闭」的语义。键盘高度变化另有
     * `[imeHeightPx, imeGapDp]` 依赖触发，不需要 mutation 兜底。
     *
     * ⚠️ 本观察者**只调度、不写 DOM**，故不会自触发。
     */
    let popPresent = !!document.querySelector(".bn-form-popover");
    const obs = new MutationObserver(() => {
      const now = !!document.querySelector(".bn-form-popover");
      if (now === popPresent) return;
      popPresent = now;
      schedule();
    });
    obs.observe(document.body, { childList: true, subtree: true });

    schedule();

    return () => {
      obs.disconnect();
      if (raf) cancelAnimationFrame(raf);
      clearAll();
      shiftStyleEl.remove();
      // 注：常驻的裁剪放开样式表（ensureCropStyle）**故意不移除**——
      // 它跨 effect 重建存活，且与键盘无关（见其 KDoc）。
    };
    // imeHeightPx / imeGapDp 变化即重新计算：它们是避让的唯一输入
  }, [imeHeightPx, imeGapDp]);

  /**
   * 组件**卸载**时移除常驻的裁剪放开样式表（`ensureCropStyle` 创建的）。
   *
   * 为什么要单独一个空依赖 effect：避让 effect 会随键盘动画重建上百次，
   * 不能在里面做「移除」（会闪断，见 `ensureCropStyle` KDoc）；
   * 但组件真正卸载（离开灵感编辑页）后样式表必须清掉，
   * 否则会残留到其他页面、影响别处的 `.bn-formatting-toolbar`。
   */
  useEffect(() => {
    return () => {
      document.querySelector(`style[${CROP_STYLE_ID}]`)?.remove();
    };
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
         * 软键盘高度下行（v2026-09-30 键盘遮挡弹层修复）：宿主在键盘 insets
         * 变化时推送当前高度与视口底边 gap（都是 dp，收起为 0）。JS 侧据此驱动
         * 弹层避让——宿主弹层期不避让键盘导致 WebView 高度恒定，
         * `visualViewport` 不触发 resize，只能由宿主主动告知。
         */
        case "imeHeight": {
          const h = (msg as any).heightPx;
          const g = (msg as any).gapDp;
          if (typeof h === "number" && h >= 0) setImeHeightPx(h);
          if (typeof g === "number" && g >= 0) setImeGapDp(g);
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
              [
                {
                  type: "image",
                  props: {
                    url: toWebImageUrl(path),
                    /**
                     * v2026-09-28：补 `name`（文件名含扩展名，与下方 insertFile 分支同口径）。
                     *
                     * **为什么必须**：官方文件形态渲染（图片工具条 Toggle preview 把
                     * `showPreview` 翻成 false 后）走 `createFileBlockWrapper` →
                     * `createFileNameWithIcon`，文件名直接读 `block.props.name`
                     * ——不传时恒为 propSchema 默认空串，文件形态只剩图标、无名字无格式
                     * （真机现象）。上传/替换链路的 name 由官方 UploadTab 自写（File.name），
                     * 仅本命令链路漏配。
                     *
                     * **往返闭环**：预览态导出 `<img alt=name>` → markdown `![name](path)`；
                     * 载入 `parseImageElement` 把 alt 还原回 name，无 converter 配套改动。
                     */
                    name: path.split("/").pop() ?? "",
                  },
                },
              ],
              cursor.block,
              "after"
            );
            /**
             * v2026-09-24：插入后立即把焦点还给 WebView（官方 `editor.focus()`）。
             *
             * **为什么必须**：官方悬浮格式工具栏由 `FormattingToolbarExtension`
             * 驱动（@blocknote/core/extensions），其显示状态重算机制是——
             * ① `pointerdown` 时强制隐藏（`store.setState(false)`）并把
             *    `preventShowWhileMouseDown` 置真，此期间 onChange /
             *    onSelectionChange 的重算**全部跳过**；
             * ② `pointerup`（root capture）时仅在 **`editor.isFocused()` 为真**
             *    的前提下才重算 `shouldShow()`。
             * 而本命令由宿主 Compose 底部栏触发：点按钮那一刻 WebView 已失焦，
             * JS 侧 `insertBlocks` 又不恢复焦点 → 用户 tap 图片时 pointerup 的
             * `isFocused()=false` → 不重算；选区（NodeSelection）此后不再变化，
             * 再无重算时机 → 点击图片官方工具栏**永不出现**。
             * 文字工具栏正常，是因为 tap 正文先把焦点还给了编辑器。
             *
             * **修法**：插入即 `focus()`（= `prosemirrorView.focus()`，与
             * `isFocused()` 同源），此后 tap 图片 pointerup 时编辑器已持有
             * 焦点 → `shouldShow()` 对 NodeSelection(image) 放行 → 工具栏
             * 正常弹出。副作用：焦点回归可能让软键盘随之前弹（与宿主
             * `focusEditor` 命令同路径，属预期行为）。
             */
            ed.focus();
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
                    /**
                     * v2026-09-28：统一补 `name`（与上方 insertImage 分支同口径）。
                     * 官方文件形态（工具条 Toggle preview 翻 showPreview=false）渲染
                     * `createFileNameWithIcon` 直读 `block.props.name`——缺省恒为
                     * 空串，文件形态只剩图标、无名字。file 分支此前已传，video/audio 补齐。
                     */
                    name: mediaPath.split("/").pop() ?? "",
                  },
                },
              ],
              cursor.block,
              "after"
            );
            /**
             * v2026-09-28：插入后立即把焦点还给 WebView（官方 `editor.focus()`）
             * ——与上方 insertImage 分支（v2026-09-24）完全同构的修复。
             *
             * **为什么必须**：官方悬浮工具栏由 `FormattingToolbarExtension` 驱动，
             * `pointerup`（root capture）时仅在 **`editor.isFocused()` 为真**时才
             * 重算 `shouldShow()`。本命令由宿主 Compose 底部栏触发：点按钮那一刻
             * WebView 已失焦，`insertBlocks` 又不恢复焦点；且 tap 媒体块
             * （video/audio 与 img 同理）不会像 tap 正文那样把焦点还给编辑器
             * → 用户 tap 视频 pointerup 时 `isFocused()=false` → 不重算；
             * 点击建立的 NodeSelection 此后不再变化，再无重算时机
             * → 工具条永不出现（真机现象）。
             *
             * **修法**：插入即 `focus()`（= `prosemirrorView.focus()`，与
             * `isFocused()` 同源），tap 视频 pointerup 时编辑器已持有焦点
             * → `shouldShow()` 对 NodeSelection(video) 放行 → 工具条正常弹出。
             * 副作用：焦点回归可能让软键盘随之前弹（与图片分支同款预期行为）。
             */
            mediaEd.focus();
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
        case "uploadImageResult": {
          /**
           * 图片上传桥的结转（v2026-09-24）：对上行 `uploadImage` 的应答。
           * 按 requestId 查 {@link pendingUploads} 等待表：path 有值 →
           * resolve `toWebImageUrl(path)`（file:// URL，官方 UploadTab 随即
           * updateBlock 替换图片）；缺省（取消/失败）→ reject → 官方显示
           * "Upload error"。查不到表项（超时已清 / 重复应答）静默忽略。
           */
          const entry = pendingUploads.get(msg.requestId);
          pendingUploads.delete(msg.requestId);
          if (entry) {
            if (msg.path) entry.resolve(toWebImageUrl(msg.path));
            else entry.reject(new Error("uploadImage cancelled or failed"));
          } else {
            // eslint-disable-next-line no-console
            console.log("[editor] uploadImageResult: no pending entry for", msg.requestId);
          }
          break;
        }
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
                // v2026-09-24 移除 case "pageBreak"（分页插入）：宿主工具栏入口已删，
                // 与 Media 组「文件」按钮共用 RiFile2Line 图标、视觉无法区分。
                // 已有文档中的分页符块仍正常渲染（下方 content:"none" 放行逻辑保留）。
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

/**
 * Replace Image「Upload」标签的宿主桥等待表（v2026-09-24 新增）
 *
 * **为什么需要**：官方 Replace Image 弹层的「Upload」标签只有在配置了
 * `editor.uploadFile` 时才渲染（@blocknote/react 0.52.1 的
 * `FilePanel.tsx:44`——`editor.uploadFile !== undefined` 才加入 Upload 页）。
 * 而 JS 侧拿到的 File 无法自行落盘（WebView 沙盒没有写应用私有目录的能力），
 * 故 {@link EditorCore} 的 `uploadFile` 被调用时生成 requestId 挂入本表并上行
 * `uploadImage`，宿主完成「选图 → 拷贝进应用目录」后下行
 * `uploadImageResult{requestId, path}`，在此查表结转（resolve / reject）。
 *
 * **为什么是模块级而不是组件状态**：上行（useCreateBlockNote 的 uploadFile 闭包）
 * 与下行（bindDown 的消息 switch）分属两个作用域，且 Promise 的生命周期跨越
 * React 渲染周期——模块级 Map 天然稳定，本项目编辑器单实例，无并发冲突。
 *
 * 结转语义：`path` 有值 → `toWebImageUrl(path)`（file://，与 insertImage 同构，
 * 保存/重进持久化链路一致）；缺省（取消/失败）→ reject，官方 UploadTab 会
 * 显示 "Upload error" 并复位 loading（UploadTab.tsx 的 catch 分支）。
 */
const pendingUploads = new Map<
  string,
  { resolve: (url: string) => void; reject: (e: Error) => void }
>();

/** requestId 自增序号（配合时间戳保证唯一；不依赖 crypto.randomUUID 的安全上下文） */
let uploadSeq = 0;

/** uploadImage 结转超时（ms）：宿主拉起选择器后用户选完才会走到上行，
 * 正常链路毫秒级即回；15s 仍无应答视为宿主异常（未接线/崩溃），防 loading 永挂 */
const UPLOAD_TIMEOUT_MS = 15000;

/* ===== 视频块手势接管（v2026-09-28：长按弹工具条，点击收起） ===== */

/** 长按判定时长（ms）：略长于 Android 系统长按阈值（400ms），避免抢在系统手势前过灵敏 */
const VIDEO_LONG_PRESS_MS = 500;

/** 长按期间允许的位移半径（px）：超过即视为滚动/拖拽意图，取消长按。
 *  v10.1：10 → 20——500ms 按住期间手指自然颤动即可超 10px（系统 touch slop
 *  同量级），误杀导致"长按弹条不稳定"；滚动/拖拽意图位移通常远超 50px，
 *  20px 不引入误判面。 */
const VIDEO_LONG_PRESS_MOVE_TOLERANCE = 20;

/**
 * 视频块手势接管（v2026-09-29 第十轮 v10.3：**确定性弹条**——dispatch 后
 * 手动 setState(true)，不再依赖官方事件链的非确定触发）
 *
 * **背景**：官方工具条弹出 = 点击建立 NodeSelection + pointerup 重算
 * shouldShow，与 Android 原生媒体手势（tap 显示控件/播放/seek）在同一
 * 物理区域互斥。用户定稿交互：
 * - **长按视频**（≥500ms、位移 ≤10px、松手时弹）→ 弹工具条；
 * - 工具条开着时**单击视频** → 收起；
 * - **单击视频本体 / 边缘 / 同行空白** → 一律不弹；
 * - 原生媒体控件（中央键/底部面板/三点）照常可点、**tap 画面照常显示控件**。
 *
 * **v9 埋点实锤的两个根因**（真机日志 2026-09-28 17:37）：
 * 1. 单击不出媒体控件：六次单击全部 `tgt=div.bn-visual-media-wrapper`、
 *    video 直挂探针（[onVideo]）零命中——`pointer-events: none` 穿透让
 *    hit-test target 永远落不到 video 本体，Chromium "tap 画面显示控件"
 *    手势（监听在 video 上）**架构性失效**，JS 侧任何放行都救不了 →
 *    本轮 video 恢复默认命中（editor.css 穿透规则整体删除）；
 * 2. 长按 fire `targetPos=-1`：`node.type.name === "video"` 在 PM 层匹配
 *    失败（BlockNote 的 PM 块结构与 BlockNote 层类型名不一一对应）→
 *    改用 `node.attrs.id === blockId` 匹配（DOM 的 data-id 即来自该
 *    PM attr），并加 matchedType 埋点揭示 PM 层真实类型名。
 *
 * **v10 实现机关**（与 v7~v9 的 capture-stopPropagation 拦截根本不同）：
 * - **零传播拦截**：pointer/mouse 事件一律放行——video（恢复命中后成为
 *   target）在 target 段先收到全部事件，原生控件手势全活、tap 手势完整；
 * - **PM 选区建立拦截走 `view.setProps({ handleDOMEvents })`**：
 *   mousedown/click 命中视频区域时 handler 返回 true → ProseMirror 跳过
 *   内置处理（不建选区 → "一律不弹"），但**事件传播不受影响**（返回
 *   true ≠ preventDefault ≠ stopPropagation）——这是"PM 不弹"与
 *   "video 收到事件"能共存的唯一层；
 * - **长按 fire 挪到 pointerup 之后**（setTimeout 0）：pointerdown 放行后
 *   官方 FormattingToolbarExtension 的 pointerdown 监听会设
 *   preventShowWhileMouseDown 抑制窗，按住期间 dispatch 会被吞；抬手后的
 *   宏任务触发时官方 flag 已清（其 pointerup 监听已跑完）→ dispatch
 *   NodeSelection → shouldShow 正常 → 弹。交互语义不变：按住 ≥500ms 且
 *   位移 ≤20px（v10.1 放宽），**松手弹**；
 * - pointermove 超半径 / pointercancel 取消长按；单击（非长按）时若选区
 *   是长按建立的块选中（按 lastSelectedId 判定，不依赖 PM 类型名）→
 *   光标移到视频后方（selection.empty → 收起）；
 * - 图片/音频/其它块不受影响（inVideoPictureArea 只认 video 块区域，
 *   caption 等 contenteditable 仍放行）。
 *
 * **v10.1（长按弹条稳定性三连修 + 键盘副作用修复，真机反馈"时弹时不弹"）**：
 * 1. **IME 门（方案 B，用户选定）**：v10 的 ed.focus() 拉起软键盘（日志
 *    ime bottom→900px），视口高度剧变 → 浮动工具条 flip/shift 重算被顶走/
 *    夹边——不稳定嫌疑之首。focus 前临时打 inputmode="none"、focus 后宏
 *    任务还原（机关详见 selectMediaBlock 注释）；
 * 2. **blockId 按下时刻快照**（downBlockId）：500ms 按住期间 React 重渲染
 *    可能替换 DOM，原"抬手后从 startEl 反查"在 detached 节点上 closest()
 *    失败 → fire skip → 不弹。嫌疑之二，一并消除；
 * 3. **位移容差 10→20px**：500ms 按住的手指自然颤动即可超 10px（系统
 *    touch slop 同量级）被误判为滚动意图取消长按。嫌疑之三。
 * 另 editor.css 为视频手势区加 user-select:none，防 Chromium 长按文本
 * 选择手势与自实现计时竞态（低概率防御，零副作用）。
 *
 * **v10.2 回归修正（真机实锤 18:38：v10.1 两次长按 dispatch 成功但
 * after300 toolbar=null——v10.1 把"弹条不稳定"修成了"稳定不弹"）**：
 * IME 门改条件式——焦点已在编辑器时不打标不 focus（对已聚焦元素动
 * inputmode 会触发 Chromium restartInput，焦点扰动污染 dispatch，工具条
 * 被杀）；仅焦点不在编辑器时才走"打标→focus→还原"。详见
 * selectMediaBlock 注释。同时加 v10.2 定性埋点（post-dispatch /
 * after300-deep 读官方 store + canApplyInlineStyles + hasFocus +
 * inputmode），若回归仍在可一轮日志三分根因。
 *
 * **v10.3 确定性弹条（真机实锤 09-29 13:15：v10.2 下弹条**仍非确定**——
 * 同一代码路径，成功案 post-dispatch store=true 且 toolbar 渲染；失败案
 * store=false 且 300ms 不变；两案 gate/焦点/dispatch 全同）**：官方
 * FormattingToolbar 的 onSelectionChange→setState 链存在时序竞态
 * （preventShowWhileMouseDown flag 与 tiptap selectionUpdate 的先后），
 * 无法从外部修复 → **dispatch 后直接 store.setState(true)**，与官方正常
 * 路径终态等价；关闭仍由既有机制负责（selection 变化 / Esc dismiss）。
 * 渲染链健康性已由成功案实证（store=true → bn-toolbar 渲染，
 * curBlock=video → canApplyInlineStyles=false 放行）。
 * BN 层 onSelectionChange 探针（unsubSelProbe）继续定性竞态根因。
 *
 * **v10.4 收起链确定性 + 键盘/蓝框修正（真机 09-29 反馈：长按弹条后无
 * 蓝色选区边框；再点按"工具条仍在 + 蓝框出现 + 键盘弹起"）**：
 * - collapse dispatch 后手动 store.setState(false)（与弹条对称的确定性
 *   关闭——v10.3 日志实锤 collapse 后 store=true 理应 false，官方链同款
 *   非确定）；
 * - collapse 后 view.dom.blur()：光标进文本+焦点在编辑器会必然弹 IME
 *   （"点视频收条却弹键盘"反直觉），blur 断开 IME；点文本时正常恢复；
 * - 埋点补 selNode（.ProseMirror-selectednode 有无）与 activeElement——
 *   定性"长按后蓝框不出现"（PM 装饰渲染与 DOM 焦点的关系）。
 *
 * **v10.5 蓝框反相修复（真机 09-29 13:42 日志 + 源码考古闭环：长按后无
 * 蓝框、collapse 后有蓝框，与 class 存在性完全反相）**：
 * - **根因**：官方蓝框样式 `.bn-block-content.ProseMirror-selectednode>*`
 *   （Block.css，无条件）要求 class 挂在 **.bn-block-content 层**；而 PM
 *   把 class 加在 NodeSelection 目标 node 的 nodeview dom（viewdesc.ts
 *   `nodeDOM = spec.dom`）。本功能长按 dispatch 的是 **blockContainer**
 *   （descendants 按 attrs.id 匹配到的唯一层——blockContent node 的
 *   attrs 只有 props 没有 id），其 dom = `.bn-block-outer`（BlockContainer
 *   .ts renderHTML）→ 两条件蓝框选择器全不匹配 → 无框；collapse 后快速
 *   tap 的**合成 mousedown/click** 被 PM 原生处理，重新选中 video
 *   blockContent 本身 → class 挂 `.bn-block-content` → 有框。日志旁证：
 *   collapse 后 4ms `BN selChange store=true`（caret 型 TextSelection
 *   不可能让官方链置 true = 重选实锤）+ after300-collapse selNode=true。
 * - **修法 ① 选中下沉**：targetPos + 1 进 blockContainer 首子节点
 *   （BN 结构不变式 `content: "blockContent blockGroup?"`），用
 *   `type.groups.includes("blockContent")` 防御校验后 dispatch——
 *   class 挂对层，长按即出框（视觉与官方点击选中一致）；
 * - **修法 ② collapse 防重选**：collapse 后 400ms 时间窗内以 capture
 *   + preventDefault + stopPropagation 吞掉合成的 mousedown/click
 *   （PM 对 contentEditable=false nodeview 的点击默认行为就是重建
 *   NodeSelection），窗后自动解绑不影响真实点击；
 * - **探针升级**：selNode 从"有无"升级为"挂载层身份 + 蓝框 computed
 *   outline"（tagName / data-content-type / outlineWidth|Style|Color），
 *   一轮日志直接验证两态挂载层与样式生效性。
 *
 * **v10.6 collapse 选区修正（真机 14:20 日志实锤：v10.5 长按链全绿
 * ——下沉生效 matchedType=video、selNode=video|4px/solid/rgb(100,160,255)
 * 蓝框出现、拦截器生效零 BN selChange；唯 collapse 后蓝框不消失）**：
 * collapse 用的 `TextSelection.near` **从未真正把光标移出视频块**——
 * 它继承 Selection.near，findFrom 的 textOnly 默认 false，对相邻 atom
 * node（video）直接返回 NodeSelection 而非跳过：
 * - v10.4：near(resolve(5))→NodeSelection(video@6)，层级变化使 class
 *   从 .bn-block-outer 挂到 .bn-block-content →"collapse 后反而有框"；
 * - v10.5：下沉后 near(resolve(6))→同位 NodeSelection，prev.eq(new)=true
 *   → PM 无事发生 → class 残留（"蓝框不消失"），syncNodeSelection 的
 *   clearNodeSelection 分支根本不触发。
 * **修法**：collapse 改用 `Selection.findFrom($to,1,true) ??
 * findFrom($from,-1,true)`（textOnly=true 只找文本位置，先块后再块前）。
 * 见 collapse 分支处详注。
 *
 * **v10.7 文件形态单击弹条（showPreview=false 手势闭环）**：用户经工具条
 * 把视频转成「文件展示」（官方 createFileBlockWrapper 在 showPreview=false
 * 时走 createFileNameWithIcon 渲染 `.bn-file-name-with-icon`）后，单击文件
 * 无反应——该 DOM 仍在 `.bn-block-content[data-content-type="video"]` 内，
 * inVideoPictureArea 判定命中 → PM 层 mousedown/click 拦截生效 → 单击不建
 * 选区 → 不弹条（视频画面的保护逻辑误伤了文件形态）。**修法**：onPointerUp
 * 快速 tap 路径在 collapse 分支之后加 else if——未选中且按在
 * `.bn-file-name-with-icon` 上 → 宏任务复用 selectMediaBlock（IME 门 +
 * v10.5 选中下沉 + v10.3 确定性弹条全链同构，dispatch 前置位
 * lastSelectedId）；再单击走既有 collapse 链收起；键盘全程不弹（IME 门 +
 * NodeSelection 无文本焦点 + collapse blur）。视频预览形态（视频画面）单击
 * 保持现状不弹条——原生播放控件手势优先；官方 Block.css 另给文件形态
 * 选中态灰底（`.ProseMirror-selectednode .bn-file-name-with-icon`），
 * 弹条后视觉反馈 = 蓝框 + 灰底双重。
 *
 * **v11 图片单击选中（用户需求 2026-09-29：图片点击「选中→工具条→键盘不弹」
 * 闭环）**：图片块此前走官方默认点击路径——PM 建 NodeSelection（蓝框）+ 官方
 * 工具条，但 DOM 焦点随点击回到 contenteditable → 软键盘弹起（反直觉），且
 * 没有「再击收起」。本轮把手势接管从 video 扩展到 image（预览态与文件展示态
 * `.bn-file-name-with-icon` 同属 image 块区域、自然覆盖；caption 等
 * contenteditable 仍放行正常编辑），交互定稿：
 * - **单击未选中图片** → 选中（蓝框）+ 弹工具条 + 键盘不弹（若已弹则收起）；
 * - **再次单击已选中图片** → 蓝框与工具条消失 + 键盘不弹。
 * 实现上与视频共用同一套基础设施（本函数统一单绑定，按块类型分发交互；
 * 拆成两个独立绑定会让 handleDOMEvents 的 props 保存/恢复互相覆盖泄漏）：
 * - PM `handleDOMEvents` 拦截扩到 image 区域——官方「点击建选区 + 焦点回
 *   编辑器」路径整体停用（这正是弹键盘的根因）；
 * - 选中复用 selectMediaBlock 全链（v10.5 下沉挂对蓝框层 + v10.3 确定性弹条
 *   + v10.2 条件式 IME 门），新增 `blurFirst`：编辑器已持焦（键盘多半弹着）
 *   时先 blur 收键盘，再走 IME 门防重弹；
 * - 收起复用抽取出的 collapseSelectedMediaBlock（v10.6 findFrom textOnly +
 *   v10.4 确定性关条 + blur 断 IME + v10.5 400ms 防重选窗）；
 * - 图片 tap 无长按语义（不需要 500ms 计时），仅保留 20px 位移容差防滚动
 *   误触；tap-select 放宏任务（官方 pointerup 清抑制窗监听须先跑完，v10
 *   同理由）。图片埋点前缀 [iGesture]。
 * 视频全部交互保持 v10.x 现状（长按选中/单击收起/预览画面单击不弹/文件形态
 * 单击选中），埋点保持 [vGesture]。
 *
 * **v10 排错埋点**（TEMP-DEBUG，验证后删除）：v10 installed 指纹 /
 * [onVideo] video 直挂探针（**本轮关键验证点：单击后应出现 [onVideo] 行**，
 * 证明事件真正到达 video）/ [touch] tap 完整性 / [cancel] / after300 /
 * fire 的 matchedType（PM 层类型名真相）。v10.1 新增：fire 带 connected、
 * ime gate on/off。
 *
 * @param ed BlockNote 编辑器实例
 * @returns 解绑函数（React cleanup 时移除全部监听并恢复原 props）
 */
function bindMediaBlockGestures(ed: any): () => void {
  const view = ed.prosemirrorView;
  const root: HTMLElement = view.dom;

  let timer: number | null = null;
  let longPressDone = false;
  let startX = 0;
  let startY = 0;
  /** v11：本次按下的交互模式——video=长按语义（计时），image=单击选中/再击收起。
   *  onPointerUp 按 mode 分发；cleanupTransient 时清空。 */
  let downMode: "video" | "image" | null = null;
  /** 本次按下时命中的元素（v10：长按 fire 挪到抬手后，据此反查块 id）。
   *  v10.1：降级为纯观测——blockId 改由 downBlockId 在按下时刻快照。 */
  let downStartEl: Element | null = null;
  /** v10.1：按下时刻快照的目标块 id（fire 不再依赖 startEl 反查——
   *  按住期间 React 重渲染替换 DOM 会让 detached 节点的 closest() 失效） */
  let downBlockId: string | null = null;
  /** 长按成功选中的块 id（v10：单击收起判定用，不依赖 PM 层类型名） */
  let lastSelectedId: string | null = null;

  /** TEMP-DEBUG（v10 排错埋点，验证后删除）：绑定指纹——确认本版本 JS 真的在运行。
   *  v11：手势接管扩展到 image，指纹升级 media-v11。 */
  sendUp({ type: "diagnostic", message: "[vGesture] media-v11 installed" });

  /**
   * TEMP-DEBUG（v10.3 定性探针，验证后删除）：BlockNote 层 selectionUpdate
   * 观测——v10.2 真机实证同一 dispatch 路径下官方 store 结果非确定
   * （成功案 post-dispatch store=true / 失败案 false 且 300ms 不变），疑似
   * 官方 preventShowWhileMouseDown flag 或 tiptap selectionUpdate 时序竞态
   * 吞掉了 setState(true)。此探针记录每次 selection 事件触发时的 store
   * 值，与手动 setState 的终态对照定位根因。 */
  const unsubSelProbe = ed.onSelectionChange(() => {
    let st = "n/a";
    try {
      st = String(ed.getExtension(FormattingToolbarExtension)?.store?.state);
    } catch {
      /* 探针只读，失败静默 */
    }
    sendUp({ type: "diagnostic", message: `[vGesture] BN selChange store=${st}` });
  });

  /** TEMP-DEBUG（长按弹条排错，验证后删除）：target 描述（tag+类名前 60 字符） */
  const describeTarget = (t: EventTarget | null): string => {
    if (!(t instanceof Element)) return String(t);
    const cls = (t.className && typeof t.className === "string" ? t.className : "").split(/\s+/).slice(0, 2).join(".");
    return `${t.tagName.toLowerCase()}${cls ? "." + cls : ""}`;
  };

  /** TEMP-DEBUG（v10 探针，验证后删除）：给 video 元素直挂只读监听，验证手势事件是否真的到达 video 本体。
   *  v10 关键验证点：穿透退役后单击视频应出现 [onVideo] 行（v9 时全程零命中 = 架构性失效实锤）。 */
  const attachVideoProbe = (video: HTMLVideoElement) => {
    const anyV = video as any;
    if (anyV.__vGestureProbe) return;
    anyV.__vGestureProbe = true;
    /** 探针事件上报（只读，不拦截不修改） */
    const report = (type: string) => () => {
      sendUp({ type: "diagnostic", message: `[vGesture][onVideo] ${type}` });
    };
    ["pointerdown", "click", "touchstart"].forEach((t) => {
      video.addEventListener(t, report(t));
    });
    sendUp({ type: "diagnostic", message: "[vGesture][onVideo] attach" });
  };

  /** TEMP-DEBUG（v10 探针，验证后删除）：监听子树新增 video，动态补挂探针（React 重渲染会换元素） */
  const videoObserver = new MutationObserver(() => {
    root.querySelectorAll("video").forEach((v) => attachVideoProbe(v as HTMLVideoElement));
  });
  videoObserver.observe(root, { childList: true, subtree: true });
  root.querySelectorAll("video").forEach((v) => attachVideoProbe(v as HTMLVideoElement));

  /** TEMP-DEBUG（v10 探针，验证后删除）：root 层只读 touch 记录（仅视频区域），验证 tap 手势是否完整派发 */
  const makeTouchProbe = (label: string) => (e: Event) => {
    const t = e.target;
    if (!(t instanceof Element) || !t.closest('[data-content-type="video"]')) return;
    sendUp({ type: "diagnostic", message: `[vGesture][touch] ${label} tgt=${describeTarget(t)}` });
  };
  const onTouchStart = makeTouchProbe("start");
  const onTouchEnd = makeTouchProbe("end");
  root.addEventListener("touchstart", onTouchStart, { capture: true, passive: true });
  root.addEventListener("touchend", onTouchEnd, { capture: true, passive: true });

  /** 判定事件是否落在 video 块区域；caption 等 contenteditable 放行 */
  const inVideoPictureArea = (t: EventTarget | null): boolean => {
    if (!(t instanceof Element)) return false;
    /** caption 等 contenteditable 文本 → 放行（正常文本编辑/长按选择） */
    if ((t as HTMLElement).isContentEditable) return false;
    /** 只接管 video 块区域（image/audio 共用 wrapper 类，须按块类型 data 属性区分）。
     *  v10：穿透退役后 hit-test target 就是 video 本体，**同样算命中**（v9 版
     *  这里对 video 元素放行是穿透架构的产物，已随架构一并退役）。 */
    return !!t.closest('[data-content-type="video"]');
  };

  /**
   * v11：判定事件是否落在 image 块区域（预览态 img 与文件展示态
   * `.bn-file-name-with-icon` 同属 `[data-content-type="image"]` 子树，一并
   * 接管）；caption 等 contenteditable 放行——点图片说明文字应正常弹键盘编辑。
   * 与 inVideoPictureArea 判定结构同构，分开放以便 handleDOMEvents 合并命中。
   */
  const inImageBlockArea = (t: EventTarget | null): boolean => {
    if (!(t instanceof Element)) return false;
    if ((t as HTMLElement).isContentEditable) return false;
    return !!t.closest('[data-content-type="image"]');
  };

  /** v11：PM handleDOMEvents 拦截的合并命中判定——video 与 image 任一区域
   *  命中即拦（返回 true 让 PM 跳过内置「点击建选区」，弹键盘的官方路径停用），
   *  各自的交互语义由 pointer 手势层分发。 */
  const inManagedMediaArea = (t: EventTarget | null): boolean =>
    inVideoPictureArea(t) || inImageBlockArea(t);

  /**
   * 长按成立（v10：由 onPointerUp 在抬手后的宏任务中调用）：
   * 手动建立块选中 → 官方 onChange 重算 → 工具条弹出。
   *
   * v10.1 两处改动：
   * 1. **blockId 快照制**：blockId 由调用方传入（pointerdown 时刻快照），
   *    不再从 startEl 反查——500ms 按住期间 React 重渲染可能替换子树
   *    DOM，detached 节点上 closest() 返回 null → "fire skip: no
   *    blockId" → 弹条不稳定（真机"时弹时不弹"嫌疑之二）。startEl 仅
   *    存留于日志与 isConnected 观测。
   * 2. **IME 门（方案 B）**：ed.focus() 会拉起软键盘（v10 真机日志实锤
   *    ime bottom 涨至 900px），键盘改变视口高度 → 浮动工具条 flip/shift
   *    重算被顶走/夹到边缘（"弹条不稳定"嫌疑之首）。修法：focus 前给
   *    PM contenteditable 根临时打 `inputmode="none"`，focus 后宏任务
   *    还原——Chromium 的 IME 弹出决策发生在焦点切换时刻，打标期间
   *    focus 不弹键盘，事后还原不会主动重弹（与宿主 IME_SUPPRESS_SCRIPT
   *    同机制；备份值存闭包而非 data-ime-prev 属性，避免与宿主脚本
   *    消费同名属性冲突）。
   *
   * **v10.2 回归修正（真机实锤：v10.1 两次长按 dispatch 成功但
   * after300 toolbar=null，v10 同场景弹条成功）**：v10.1 的 IME 门在
   * **焦点已在编辑器**时也会打标——对已聚焦元素 setAttribute("inputmode")
   * 会触发 Chromium restartInput（IME 会话重建），伴随焦点扰动污染
   * dispatch 前后状态，工具条随之被杀。修正为**条件式 IME 门**：
   * - 焦点已在编辑器（`view.hasFocus()`，长按前刚编辑过的主路径）→
   *   既不打标也不 focus（focus 是无操作，跳过无副作用）——行为完全
   *   回归 v10，且焦点本就在，**不会**新弹键盘；
   * - 焦点不在编辑器 → 才走"打标 → focus → 宏任务还原"（此路径打标
   *   发生在非聚焦元素上，Chromium 不做 IME 重评估，是门的设计场景）。
   * 另加 v10.2 定性埋点：dispatch 后同步 + after300 复读官方
   * FormattingToolbar store 状态 / 当前块类型 / canApplyInlineStyles /
   * hasFocus / inputmode 现值——区分"setState 没跑"与"渲染层闸门"
   * 与"弹了又收"三类根因。
   *
   * 匹配条件 = `attrs.id === blockId`（DOM 的 data-id 即来自该 PM attr），
   * **不依赖 PM 层类型名**——v9 实锤 `type.name === "video"` 匹配失败
   * （targetPos=-1，BlockNote 的 PM 块结构与 BlockNote 层类型名不一一对应）。
   *
   * v10.5：匹配到的必然是 blockContainer（blockContent 无 id attr），
   * 选中目标**下沉一层**至 blockContent（蓝框 class 挂载层）——详见
   * 头注释 v10.5 节与下沉代码处注释。
   *
   * **v11 改名 selectMediaBlock + 新增 `blurFirst`（图片路径专用）**：
   * 用户需求「点图片键盘不应弹起，**若已经弹起则收起**」。编辑器已持焦时
   * （用户刚打完字的主路径）软键盘多半弹着，单纯 IME 门只能「防弹」不能
   * 「收起」→ `blurFirst=true` 时先 `view.dom.blur()` 断开 IME 收起键盘；
   * blur 后 `hasFocus()=false`，下方 IME 门随即走「打标→focus→还原」的
   * 防弹分支（打标发生在非聚焦元素上，Chromium 不做 IME 重评估，焦点扰动
   * 风险与 v10.2 已验证路径一致）。blur 已失焦时为无害无操作。视频长按
   * 路径传 `false`，保持 v10.2 既有行为（已持焦则完全不动焦点）。
   * v11 同步把调用点扩展为两处：视频长按 / 视频文件形态 tap（blurFirst=false）、
   * 图片 tap 选中（blurFirst=true）。
   *
   * @param blockId 长按目标块的 id（pointerdown 时刻快照，可能为 null）
   * @param startEl 按下时命中的元素（仅日志观测用）
   * @param blurFirst v11：选中前若编辑器持焦则先 blur 收起软键盘（图片路径）
   */
  const selectMediaBlock = (
    blockId: string | null,
    startEl: Element | null,
    blurFirst: boolean,
  ) => {
    /** TEMP-DEBUG（v10.1 埋点，验证后删除）：startEl 是否仍在文档中（观测 DOM 替换频率） */
    sendUp({
      type: "diagnostic",
      message: `[vGesture] fire blockId=${blockId} blurFirst=${blurFirst} connected=${startEl?.isConnected ?? "null-el"}`,
    });
    if (!blockId) {
      /** TEMP-DEBUG（v10 埋点，验证后删除） */
      sendUp({ type: "diagnostic", message: "[vGesture] fire skip: no blockId" });
      return;
    }
    /**
     * v11 blurFirst：图片路径——编辑器持焦（键盘多半弹着）先 blur 收键盘。
     * 放在 IME 门之前：blur 后必走 IME 门的「打标→focus→还原」防弹分支。
     */
    if (blurFirst && view.hasFocus()) {
      try {
        view.dom.blur();
        /** TEMP-DEBUG（v11 埋点，验证后删除） */
        sendUp({ type: "diagnostic", message: "[iGesture] blurFirst: ime collapsed" });
      } catch (e: any) {
        sendUp({ type: "error", message: `imageBlurFirst: ${e?.message ?? e}` });
      }
    }
    /** IME 门（v10.2 条件式）：焦点已在编辑器 → 完全跳过（见头注释） */
    const editable = view.dom as HTMLElement;
    if (!view.hasFocus()) {
      const prevIme = editable.getAttribute("inputmode");
      /** TEMP-DEBUG（v10.1 埋点，验证后删除） */
      sendUp({ type: "diagnostic", message: `[vGesture] ime gate on prev=${prevIme === null ? "unset" : prevIme}` });
      editable.setAttribute("inputmode", "none");
      ed.focus();
      window.setTimeout(() => {
        if (prevIme === null) editable.removeAttribute("inputmode");
        else editable.setAttribute("inputmode", prevIme);
        /** TEMP-DEBUG（v10.1 埋点，验证后删除） */
        sendUp({ type: "diagnostic", message: "[vGesture] ime gate off" });
      }, 0);
    } else {
      /** TEMP-DEBUG（v10.2 埋点，验证后删除） */
      sendUp({ type: "diagnostic", message: "[vGesture] ime gate skipped (already focused)" });
    }
    let targetPos = -1;
    let matchedType = "";
    view.state.doc.descendants((node: any, pos: number) => {
      if (targetPos >= 0) return false;
      if (node.attrs && node.attrs.id === blockId) {
        targetPos = pos; /** descendants 回调的 pos 即节点前位置，NodeSelection.create 直接可用 */
        matchedType = node.type.name;
        return false;
      }
      return true;
    });
    /**
     * v10.5 选中下沉：attrs.id 只存在于 blockContainer 层（blockContent
     * node 的 attrs = propsToAttributes(propSchema)，无 id），故上面匹配到
     * 的一定是 blockContainer（v10.4 日志实锤 matchedType=blockContainer）。
     * 其 nodeview dom = `.bn-block-outer`，官方蓝框选择器要求 class 挂
     * `.bn-block-content` 层 → 长按后无框（详见头注释 v10.5 节）。
     * BN 结构不变式 `content: "blockContent blockGroup?"` → 首子节点即
     * blockContent，下沉一层并做 groups 防御校验后选中。
     */
    if (targetPos >= 0) {
      try {
        const $inner = view.state.doc.resolve(targetPos + 1);
        const inner = $inner.nodeAfter;
        if (inner && Array.isArray(inner.type.groups) && inner.type.groups.includes("blockContent")) {
          targetPos = targetPos + 1;
          matchedType = inner.type.name;
        }
      } catch {
        /* 下沉失败则维持原目标（退化为 v10.4 行为：无框但功能可用） */
      }
    }
    /** TEMP-DEBUG（v10 埋点，验证后删除）：matchedType 揭示 PM 层真实类型名（v10.1 改名 fire-resolved，与入参快照 fire 区分） */
    sendUp({
      type: "diagnostic",
      message: `[vGesture] fire-resolved targetPos=${targetPos} matchedType=${matchedType}`,
    });
    if (targetPos >= 0) {
      lastSelectedId = blockId;
      view.dispatch(view.state.tr.setSelection(NodeSelection.create(view.state.doc, targetPos)));
      /** TEMP-DEBUG（验证后删除）：dispatch 后的选区与工具条 store 状态 */
      const sel = view.state.selection;
      sendUp({
        type: "diagnostic",
        message: `[vGesture] dispatched sel=${sel.constructor.name} empty=${sel.empty}`,
      });
      /**
       * TEMP-DEBUG（v10.2 定性埋点，验证后删除）：读官方 FormattingToolbar
       * store 布尔 + 自定义闸门（MediaOnlyFormattingToolbarController 的
       * visible = show && !canApplyInlineStyles）的各输入项——
       * store=false → setState 没跑/被覆盖（事件层）；
       * store=true 且 inlineOK=true → 渲染闸门拦下（canApplyInlineStyles
       * 在 NodeSelection 下解析错块）；
       * store=true 且 inlineOK=false 且 toolbar=null → PositionPopover 渲染层。
       */
      const probeState = (label: string) => {
        let store = "n/a";
        let curBlock = "n/a";
        let inlineOK = "n/a";
        let selNode = "n/a";
        let active = "n/a";
        try {
          store = String(ed.getExtension(FormattingToolbarExtension)?.store?.state);
        } catch (e: any) {
          store = "err:" + (e?.message ?? e);
        }
        try {
          curBlock = String(ed.getTextCursorPosition()?.block?.type);
        } catch (e: any) {
          curBlock = "err:" + (e?.message ?? e);
        }
        try {
          inlineOK = String(canApplyInlineStyles(ed));
        } catch (e: any) {
          inlineOK = "err:" + (e?.message ?? e);
        }
        /**
         * TEMP-DEBUG（v10.4 埋点 / v10.5 升级，验证后删除）：蓝框观测。
         * v10.4 的"有无"已实锤 class 恒在但视觉反相 → v10.5 改报
         * 「挂载层身份 + 蓝框 computed style」：contentType=挂载层
         * data-content-type（blockContainer 无此属性、blockContent=video），
         * outline=官方蓝框目标（class 元素直接子级）的 computed
         * outlineWidth/Style/Color——直接回答"蓝框渲染层是否生效"。
         */
        try {
          const sn = document.querySelector(".ProseMirror-selectednode");
          if (!sn) {
            selNode = "false";
          } else {
            const holder = sn as HTMLElement;
            const cType = holder.getAttribute("data-content-type") ?? "-";
            const target = holder.firstElementChild ?? holder;
            const cs = window.getComputedStyle(target);
            selNode = `${cType}|${cs.outlineWidth}/${cs.outlineStyle}/${cs.outlineColor}`;
          }
        } catch {
          /* 探针只读 */
        }
        try {
          const ae = document.activeElement;
          active = ae ? `${ae.tagName}.${String(ae.className).split(/\s+/).slice(0, 1).join("")}` : "null";
        } catch {
          /* 探针只读 */
        }
        sendUp({
          type: "diagnostic",
          message: `[vGesture] ${label} store=${store} curBlock=${curBlock} inlineOK=${inlineOK} hasFocus=${view.hasFocus()} inputmode=${editable.getAttribute("inputmode") ?? "unset"} selNode=${selNode} active=${active}`,
        });
      };
      probeState("post-dispatch-native");
      /**
       * v10.3 确定性弹条：dispatch 后直接 setState(true)。
       * v10.2 真机实证：同一代码路径下官方 onSelectionChange→setState 链
       * 结果非确定（成功案 store=true / 失败案 false 且 300ms 不变）——
       * 官方 preventShowWhileMouseDown flag 或 tiptap selectionUpdate 时序
       * 竞态所致（探针继续定性）。本场景语义明确（长按=选中视频块=要弹
       * 条），手动置 true 与官方正常路径的终态等价；关闭仍由既有机制负责
       * （点别处 → selection 变化 → setState(shouldShow()=false)；Esc/dismiss
       * → onOpenChange → setState(open)）。若官方链正常触发（成功案路径），
       * 本置位幂等无害。
       */
      try {
        ed.getExtension(FormattingToolbarExtension)?.store?.setState(true);
      } catch (e: any) {
        sendUp({ type: "error", message: `videoForceShow: ${e?.message ?? e}` });
      }
      probeState("post-dispatch-forced");
      /** TEMP-DEBUG（v9 埋点，验证后删除）：300ms 后回看——选区是否仍为 NodeSelection、
       *  工具条 DOM 是否真的渲染出来（区分"选区没建立"与"选区建立了但工具条没渲染"） */
      window.setTimeout(() => {
        const s = view.state.selection;
        const tb = document.querySelector('[class*="toolbar"]');
        const tbDesc = tb ? (tb.getAttribute("class") || "").slice(0, 80) : "null";
        sendUp({
          type: "diagnostic",
          message: `[vGesture] after300 sel=${s.constructor.name} empty=${s.empty} toolbar=${tbDesc}`,
        });
        probeState("after300-deep");
      }, 300);
    }
  };

  const onPointerMove = (e: PointerEvent) => {
    if (Math.hypot(e.clientX - startX, e.clientY - startY) > VIDEO_LONG_PRESS_MOVE_TOLERANCE) {
      cleanupTransient(); /** 位移超阈值 = 滚动意图 → 取消长按（本次拦截的点击就此吞掉） */
    }
  };

  /**
   * v11：收起当前媒体块选中（自视频 v10.4~v10.6 collapse 链抽取共用）——
   * 视频单击收起与图片再击收起走同一条链：
   * 1. **选区移出**：`Selection.findFrom($to,1,true) ?? findFrom($from,-1,true)`
   *    （v10.6：textOnly=true 只找文本位、跳过 atom——`TextSelection.near`
   *    会把相邻视频/图片块解析成 NodeSelection，导致蓝框残留/同位无操作）；
   * 2. **确定性关条**：手动 `store.setState(false)`（v10.4，与弹条对称，
   *    官方 onSelectionChange→setState 链非确定不能依赖）；
   * 3. **blur 断 IME**：光标进文本位 + 持焦必弹键盘（v10.4），blur 后键盘
   *    收起——图片路径「再击收起键盘不弹起」依赖此步；
   * 4. **400ms 防重选窗**（v10.5）：吞掉 tap 的合成 mousedown/click（PM 对
   *    contentEditable=false nodeview 的点击默认重建 NodeSelection → 蓝框
   *    复现），窗后自动解绑不影响真实点击。
   *
   * @param sel 当前 NodeSelection（调用方已判定其目标块 = lastSelectedId）
   */
  const collapseSelectedMediaBlock = (sel: NodeSelection) => {
    lastSelectedId = null;
    /** v10.6 修法（详见头注释 v10.6 节）：findFrom textOnly 替代 near */
    const $from = view.state.doc.resolve(sel.from);
    const $to = view.state.doc.resolve(sel.to);
    const $textPos = Selection.findFrom($to, 1, true) ?? Selection.findFrom($from, -1, true);
    if ($textPos) {
      view.dispatch(view.state.tr.setSelection($textPos));
    }
    /** v10.4：确定性关闭 + blur 收键盘（见上注释） */
    try {
      ed.getExtension(FormattingToolbarExtension)?.store?.setState(false);
    } catch (e: any) {
      sendUp({ type: "error", message: `videoForceHide: ${e?.message ?? e}` });
    }
    try {
      view.dom.blur();
    } catch (e: any) {
      sendUp({ type: "error", message: `videoCollapseBlur: ${e?.message ?? e}` });
    }
    /**
     * v10.5 collapse 防重选：快速 tap 的合成 mousedown/click 发生在
     * pointerup 之后（Chromium 顺序 pointerup → touchend → mousedown →
     * mouseup → click），PM 对 contentEditable=false nodeview 的点击
     * 默认行为 = 重建 NodeSelection(video) → class 挂回
     * .bn-block-content → 蓝框"collapse 后又出现"（v10.4 日志实锤：
     * collapse 后 4ms BN selChange store=true + selNode 残留）。
     * root（= view.dom）上以 capture 挂 400ms 时间窗拦截器，窗内
     * preventDefault + stopPropagation 吞掉合成事件（preventDefault
     * mousedown 同时阻断焦点回移与 click 合成，巩固 v10.4 的防弹键
     * 成果）；handler 内自检过期自解绑 + setTimeout 兜底，窗后真实
     * 点击不受影响。
     */
    const collapseAt = Date.now();
    const swallowSyntheticTap = (e: Event) => {
      if (Date.now() - collapseAt < 400) {
        e.preventDefault();
        e.stopPropagation();
      } else {
        root.removeEventListener("mousedown", swallowSyntheticTap, true);
        root.removeEventListener("click", swallowSyntheticTap, true);
      }
    };
    root.addEventListener("mousedown", swallowSyntheticTap, true);
    root.addEventListener("click", swallowSyntheticTap, true);
    window.setTimeout(() => {
      root.removeEventListener("mousedown", swallowSyntheticTap, true);
      root.removeEventListener("click", swallowSyntheticTap, true);
    }, 500);
    /** TEMP-DEBUG（v10.4 埋点，验证后删除）：collapse 后快照 + 300ms 复读
     *  （定性"工具条仍在"：store/toolbar 最终态；蓝框：selNode class） */
    const probeAfterCollapse = (label: string) => {
      const s = view.state.selection;
      const tb = document.querySelector('[class*="toolbar"]');
      let sn = "n/a";
      let act = "n/a";
      let st = "n/a";
      try {
        const snEl = document.querySelector(".ProseMirror-selectednode");
        if (!snEl) {
          sn = "false";
        } else {
          /** v10.5 升级：挂载层身份 + 蓝框 computed（与 probeState 同口径） */
          const holder = snEl as HTMLElement;
          const cType = holder.getAttribute("data-content-type") ?? "-";
          const target = holder.firstElementChild ?? holder;
          const cs = window.getComputedStyle(target);
          sn = `${cType}|${cs.outlineWidth}/${cs.outlineStyle}/${cs.outlineColor}`;
        }
      } catch {
        /* 探针只读 */
      }
      try {
        const ae = document.activeElement;
        act = ae ? `${ae.tagName}.${String(ae.className).split(/\s+/).slice(0, 1).join("")}` : "null";
      } catch {
        /* 探针只读 */
      }
      try {
        st = String(ed.getExtension(FormattingToolbarExtension)?.store?.state);
      } catch {
        /* 探针只读 */
      }
      sendUp({
        type: "diagnostic",
        message: `[vGesture] ${label} sel=${s.constructor.name} empty=${s.empty} store=${st} toolbar=${tb ? "in" : "null"} selNode=${sn} active=${act} hasFocus=${view.hasFocus()}`,
      });
    };
    probeAfterCollapse("post-collapse");
    window.setTimeout(() => probeAfterCollapse("after300-collapse"), 300);
  };

  const onPointerUp = () => {
    const wasLongPress = longPressDone;
    const startEl = downStartEl;
    const upBlockId = downBlockId; /** v10.1：cleanup 前取快照 */
    const mode = downMode; /** v11：cleanup 前快照交互模式（cleanup 会清空） */
    cleanupTransient();
    /** TEMP-DEBUG（v10 埋点，验证后删除）：无条件记录抬指与长按状态 */
    sendUp({ type: "diagnostic", message: `[vGesture] up wasLongPress=${wasLongPress} mode=${mode}` });
    if (mode === "image") {
      /**
       * v11 图片 tap 分流：单击选中 / 再击收起（交互定义见头注释 v11 节）。
       * - 收起判定与视频同构：当前选区是 NodeSelection 且其块 id
       *   （blockContent 无 id → 从父级 blockContainer 取，v10.5 口径）
       *   === lastSelectedId（只能由本文件 tap-select 建立，官方点击路径
       *   已被 handleDOMEvents 拦停）→ collapseSelectedMediaBlock
       *   （findFrom textOnly + 确定性关条 + blur 收键盘 + 400ms 防重选窗，
       *   键盘全程不弹）；
       * - 未选中 → 宏任务 selectMediaBlock(blurFirst=true)：先 blur 收起
       *   已弹键盘，再经 IME 门（打标→focus→还原）dispatch 下沉 NodeSelection
       *   + 手动 setState(true) 确定性弹条。放宏任务是 v10 既有要求（官方
       *   FormattingToolbarExtension 的 pointerup 清抑制窗监听须先跑完）。
       * 位移超 20px 的滚动意图已在 onPointerMove 里 cleanup（up 不触发）。
       */
      try {
        const sel = view.state.selection;
        const selId: string | null =
          sel instanceof NodeSelection
            ? ((sel.node.attrs?.id as string | undefined) ??
              ((sel.$from.parent?.attrs?.id as string | undefined) ?? null))
            : null;
        if (selId && selId === lastSelectedId) {
          /** TEMP-DEBUG（v11 埋点，验证后删除） */
          sendUp({ type: "diagnostic", message: "[iGesture] up: collapse" });
          collapseSelectedMediaBlock(sel);
        } else if (upBlockId && startEl) {
          /** TEMP-DEBUG（v11 埋点，验证后删除） */
          sendUp({ type: "diagnostic", message: "[iGesture] up: tap-select" });
          window.setTimeout(() => {
            try {
              selectMediaBlock(upBlockId, startEl, true);
            } catch (err: any) {
              sendUp({ type: "error", message: `imageTapSelect: ${err?.message ?? err}` });
            }
          }, 0);
        }
      } catch (e: any) {
        sendUp({ type: "error", message: `imageTap: ${e?.message ?? e}` });
      }
      return;
    }
    if (wasLongPress && startEl) {
      /**
       * v10：fire 挪到抬手后的宏任务。pointerdown 放行后官方
       * FormattingToolbarExtension 的 pointerdown 监听已设
       * preventShowWhileMouseDown 抑制窗，按住期间 dispatch 会被吞；
       * setTimeout(0) 的宏任务在本次事件派发全部结束后执行——官方
       * pointerup 监听（清抑制 flag）已跑完 → dispatch 后 shouldShow
       * 正常放行 → 工具条弹出。交互语义：按住 ≥500ms，**松手弹**。
       */
      window.setTimeout(() => {
        try {
          /** v11：视频长按路径 blurFirst=false——保持 v10.2 既有焦点行为 */
          selectMediaBlock(upBlockId, startEl, false);
        } catch (err: any) {
          sendUp({ type: "error", message: `videoLongPress: ${err?.message ?? err}` });
        }
      }, 0);
      return;
    }
    /** 单击：工具条开着（= 长按建立的块选中还在）→ 收起（光标移到视频后方最近文本位）。
     *  v10：按 lastSelectedId 判定（NodeSelection 的 node 可能不是 media 类型名）。
     *
     *  v10.4 两处补强（真机 09-29 反馈：再点按"工具条仍在 + 键盘弹起"）：
     *  1. **确定性关闭**：与弹条（v10.3 手动 setState(true)）对称——collapse
     *     dispatch 后手动 store.setState(false)。官方 onSelectionChange→
     *     setState 链与弹条同款非确定（v10.3 日志实锤 collapse 后 store=true，
     *     理应 false），不能依赖；
     *  2. **blur 收键盘**：collapse 把光标放进最近文本位且焦点在编辑器时，
     *     PM 同步 DOM caret → Chromium 必然弹 IME——"点视频收条却弹键盘"
     *     反直觉。collapse 后 view.dom.blur()：DOM 焦点离开 contenteditable
     *     → IME 断开收起；用户点文本时会重新 focus+弹键盘（正常输入路径
     *     不受影响）。NodeSelection 装饰（蓝框）随选区变 TextSelection + 无
     *     焦点双重条件下必然消失。 */
    try {
      const sel = view.state.selection;
      /**
       * v10.5：选中下沉后 sel.node = blockContent（attrs 无 id）→ id 改从
       * 父级 blockContainer（$from.parent，BN 结构不变式父级即容器）取；
       * node.attrs.id 保留在前作为兜底（兼容下沉失败等退化路径）。
       */
      const selId: string | null =
        sel instanceof NodeSelection
          ? ((sel.node.attrs?.id as string | undefined) ??
            ((sel.$from.parent?.attrs?.id as string | undefined) ?? null))
          : null;
      if (selId && selId === lastSelectedId) {
        /** TEMP-DEBUG（验证后删除） */
        sendUp({ type: "diagnostic", message: "[vGesture] up: collapse" });
        /**
         * v11：collapse 链（v10.6 findFrom textOnly + v10.4 确定性关条 +
         * blur 断 IME + v10.5 400ms 防重选窗）抽取为共享
         * collapseSelectedMediaBlock——图片再击收起走同一条链，机制详见
         * 函数处注释。
         */
        collapseSelectedMediaBlock(sel);
      } else if (upBlockId && startEl && startEl.closest(".bn-file-name-with-icon")) {
        /**
         * v10.7 文件形态单击弹条：视频转「文件展示」（showPreview=false）后，
         * 文件名区域（.bn-file-name-with-icon）仍被 inVideoPictureArea 命中
         * → PM 层拦截挡住了官方「点击建选区」→ 未选中态单击永远不弹条。
         * 此处补齐单击语义：复用 selectMediaBlock 全链（IME 门防键盘 +
         * v10.5 下沉让蓝框挂对层 + v10.3 手动 setState(true) 确定性弹条），
         * 与长按路径完全同构；fire 放宏任务是 v10 的既有要求（官方
         * FormattingToolbarExtension 的 pointerup 清抑制窗监听须先跑完）。
         * 判定用 closest 实时探测（downStartEl 快照仅观测用，选中语义以
         * 按下时刻元素为准即可）；预览形态（视频画面）不命中此分支，单击
         * 维持零动作，原生控件手势不受影响。再单击由上方 collapse 分支
         * 收起（selectMediaBlock 已置 lastSelectedId，闭环成立）。
         * v11：函数更名 selectMediaBlock，blurFirst=false（视频路径不变，
         * 键盘语义仍由 IME 门 + collapse blur 负责）。
         */
        /** TEMP-DEBUG（v10.7 埋点，验证后删除） */
        sendUp({ type: "diagnostic", message: "[vGesture] up: tap-select (file form)" });
        window.setTimeout(() => {
          try {
            selectMediaBlock(upBlockId, startEl, false);
          } catch (err: any) {
            sendUp({ type: "error", message: `videoTapSelect: ${err?.message ?? err}` });
          }
        }, 0);
      }
    } catch (e: any) {
      sendUp({ type: "error", message: `videoTapCollapse: ${e?.message ?? e}` });
    }
  };

  const onPointerCancel = () => {
    /** TEMP-DEBUG（v9 埋点，验证后删除）：pointercancel 会取消长按计时——真机长按若被
     *  浏览器原生手势（文本选择/上下文菜单）接管就会派发 cancel，是"长按不弹"的头号嫌疑 */
    sendUp({ type: "diagnostic", message: "[vGesture] cancel" });
    cleanupTransient();
  };

  const cleanupTransient = () => {
    if (timer !== null) {
      window.clearTimeout(timer);
      timer = null;
    }
    /** v11：交互模式一并复位（调用方 cleanup 前已自行快照） */
    downMode = null;
    root.removeEventListener("pointermove", onPointerMove, { capture: true });
    root.removeEventListener("pointerup", onPointerUp, { capture: true });
    root.removeEventListener("pointercancel", onPointerCancel, { capture: true });
  };

  const onPointerDown = (e: PointerEvent) => {
    /** TEMP-DEBUG（验证后删除）：进入判定前先报 target 与判定结果 */
    const videoHit = inVideoPictureArea(e.target);
    const imageHit = inImageBlockArea(e.target);
    sendUp({
      type: "diagnostic",
      message: `[vGesture] down tgt=${describeTarget(e.target)} video=${videoHit} image=${imageHit} primary=${e.isPrimary}`,
    });
    if (!e.isPrimary || (!videoHit && !imageHit)) return;
    /**
     * v10：**零拦截**（连 stopPropagation 都不做）——事件自然传播，
     * video（穿透退役后恢复命中、成为 hit-test target）在 target 段收到
     * 事件，原生控件手势全活；tap 手势完整。这里只启动长按计时；
     * PM 的"点击建立选区"由下方 handleDOMEvents prop 层挡
     * （返回 true 跳过 PM 处理，不改事件传播）。
     *
     * v11：image 命中走 tap-toggle 模式——无长按语义（不需要 500ms 计时），
     * 仅快照 downMode/downStartEl/downBlockId 并挂 move/up/cancel（move 的
     * 20px 位移容差对图片同样生效：滚动/拖拽意图取消 tap，防误选中）；
     * tap 语义在 onPointerUp 的 image 分支分发。
     */
    longPressDone = false;
    downMode = imageHit ? "image" : "video";
    downStartEl = e.target instanceof Element ? e.target : null;
    /** v10.1：块 id 在按下时刻快照（不受按住期间 DOM 替换影响） */
    downBlockId = downStartEl?.closest(".bn-block[data-id]")?.getAttribute("data-id") ?? null;
    startX = e.clientX;
    startY = e.clientY;
    if (videoHit) {
      /** 仅视频需要长按计时（v10：计时到点只置标记，fire 挪到 pointerup 后宏任务） */
      timer = window.setTimeout(() => {
        timer = null;
        longPressDone = true;
      }, VIDEO_LONG_PRESS_MS);
    }
    root.addEventListener("pointermove", onPointerMove, { capture: true });
    root.addEventListener("pointerup", onPointerUp, { capture: true });
    root.addEventListener("pointercancel", onPointerCancel, { capture: true });
  };

  /**
   * PM props 层选区拦截（v10 新机关，替代 v7~v9 的 stopPropagation 闸；
   * v11 命中判定扩展为 video ∪ image 两类媒体区域）。
   *
   * ProseMirror 的 `handleDOMEvents` prop：事件冒泡到 view.dom 时 PM 先征询
   * 这些 handler，**返回 true = PM 跳过该事件的全部内置处理**（含 mousedown
   * 建选区 / click 的 handleClickOn——官方"点击弹工具条"的两个入口）——但
   * **事件本身的传播与浏览器默认行为不受任何影响**（返回 true ≠
   * preventDefault ≠ stopPropagation）。时序上 video（target）早已在
   * target 段收到事件（先于冒泡到 view.dom）——这就是"PM 不弹工具条"与
   * "原生媒体手势全活"能共存的机关。
   *
   * v11 图片侧：官方"点击建 NodeSelection + 焦点回 contenteditable（弹
   * 键盘）"路径由此整体停用，改由 pointer 手势层接管（tap-select /
   * tap-collapse，见 onPointerUp image 分支）。
   */
  const mediaHandleDOMEvents: Record<string, (view: unknown, event: MouseEvent) => boolean> = {
    mousedown: (_pmView, event) => {
      const hit = inManagedMediaArea(event.target);
      /** TEMP-DEBUG（v10 埋点，验证后删除） */
      sendUp({
        type: "diagnostic",
        message: `[vGesture] mouse mousedown hit=${hit} tgt=${describeTarget(event.target)}`,
      });
      return hit;
    },
    click: (_pmView, event) => {
      const hit = inManagedMediaArea(event.target);
      /** TEMP-DEBUG（v10 埋点，验证后删除） */
      sendUp({
        type: "diagnostic",
        message: `[vGesture] mouse click hit=${hit} tgt=${describeTarget(event.target)}`,
      });
      return hit;
    },
  };
  /** 保存旧 props 供 unbind 恢复（防 React 重复挂载叠加 / 恢复期异常） */
  const prevHandleDOMEvents: unknown = (view as any).props?.handleDOMEvents;
  (view as any).setProps({
    handleDOMEvents: {
      ...((prevHandleDOMEvents as Record<string, unknown>) ?? {}),
      ...mediaHandleDOMEvents,
    },
  });

  root.addEventListener("pointerdown", onPointerDown, { capture: true });
  return () => {
    cleanupTransient();
    unsubSelProbe(); /** TEMP-DEBUG（v10.3 探针，验证后删除） */
    videoObserver.disconnect(); /** TEMP-DEBUG（v10 探针，验证后删除） */
    root.removeEventListener("touchstart", onTouchStart, { capture: true }); /** TEMP-DEBUG（v10 探针，验证后删除） */
    root.removeEventListener("touchend", onTouchEnd, { capture: true }); /** TEMP-DEBUG（v10 探针，验证后删除） */
    root.removeEventListener("pointerdown", onPointerDown, { capture: true });
    /** v10：恢复进入前的 handleDOMEvents（unbind 时一并还原 props 层） */
    (view as any).setProps({ handleDOMEvents: prevHandleDOMEvents });
  };
}

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
    /**
     * 图片上传桥（v2026-09-24 新增）
     *
     * **直接动因**：官方 Replace Image 弹层的「Upload」标签只在配置了本回调
     * 时才渲染（`FilePanel.tsx:44`），否则只剩「Embed」一页——真机上点击
     * Replace Image 只见 Embed 输入框，与官方 demo（Upload/Embed 双页）不一致。
     *
     * **实现**：官方流程是 `<input type="file">` → 宿主 `onShowFileChooser`
     * 拉起图片选择器 → WebView 把选中文件递回 → 本回调被调用。JS 没有落盘
     * 能力，故此处**忽略 File 字节**（宿主在选择阶段已把图拷贝进应用目录），
     * 只生成 requestId 挂 {@link pendingUploads} 等待表并上行 `uploadImage`；
     * 宿主下行 `uploadImageResult{requestId, path}` 后按 path 结转——
     * resolve `toWebImageUrl(path)`（file:// URL），官方 UploadTab 随即
     * `updateBlock` 替换图片 URL。保存/重进的持久化与 insertImage 完全同构。
     *
     * @param _file 选中的文件（字节不用；名字由官方 UploadTab 自己写进 props.name）
     * @param _blockId 被替换的图片块 id（官方在上传完成时自行 updateBlock，无需使用）
     * @returns Promise<file:// URL>；取消/失败/超时 reject → 官方显示 Upload error
     */
    uploadFile: async (_file: File, _blockId?: string) => {
      const requestId = `upload-${Date.now()}-${++uploadSeq}`;
      return new Promise<string>((resolve, reject) => {
        /** 超时兜底：宿主未接线 / 进程异常时防 loading 永挂（见 UPLOAD_TIMEOUT_MS 注释） */
        const timer = window.setTimeout(() => {
          pendingUploads.delete(requestId);
          reject(new Error("uploadImage timeout"));
        }, UPLOAD_TIMEOUT_MS);
        pendingUploads.set(requestId, {
          resolve: (url) => {
            window.clearTimeout(timer);
            resolve(url);
          },
          reject: (e) => {
            window.clearTimeout(timer);
            reject(e);
          },
        });
        sendUp({ type: "uploadImage", requestId });
      });
    },
  });

  /**
   * 媒体块手势接管（v2026-09-29 v11）：video 长按弹工具条 / 点击收起 /
   * 其余点击一律不弹（v10.x 交互不变）；image（预览态 + 文件展示态）
   * 单击选中 / 再击收起、键盘不弹（已弹则收）。两类块统一单绑定——共用
   * IME 门 / 选中下沉 / 确定性弹条收条 / 防重选窗与 handleDOMEvents 拦截
   * （拆双绑定会让 props 保存/恢复互相覆盖泄漏），交互分发见函数内注释。
   * editor 为单次创建的稳定实例，绑定/解绑各执行一次。
   */
  useEffect(() => {
    return bindMediaBlockGestures(editor);
  }, [editor]);

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
      /**
       * v2026-09-29 焦点门控：焦点不在编辑器（contenteditable）内时，本机制
       * **完全不参与滚动**（入口短路 + step 每帧复查，滚动中途失焦即退出）。
       *
       * **为什么必须有**（Replace video → Rename video 键盘"展开后又收起"根因）：
       * 本机制的滚动目标是 `coordsAtPos(selection.from)`——**PM 选区**坐标。
       * 聚焦 Rename 输入框只转移 **DOM 焦点**，PM 选区仍是 NodeSelection(视频块)；
       * 键盘弹出 → WebView 高度收缩 → 本机制被 resize 触发，把**视频块**当
       * "光标"拉回可见区，与 Chromium 的"让输入框可见"滚动互相拉锯——
       * 竖屏大视频必然出界必然触发（横屏小视频零动作，完美解释"特定尺寸才出现"）。
       * 门控后失焦期零滚动，Chromium 独占输入框滚动，键盘稳定弹出。
       *
       * **适用性**：本机制语义是"文本光标跟随"，前提即焦点在编辑器内；
       * v1.11.9 原始场景（正文打字键盘弹出光标被遮）焦点本就在编辑器，零回归。
       */
      const editorHasFocus = () => {
        const view = editor.prosemirrorView;
        return !!view && !view.isDestroyed && view.hasFocus();
      };
      const step = () => {
        const view = editor.prosemirrorView;
        const doc = document.documentElement;
        if (!view || view.isDestroyed || !editorHasFocus()) {
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
        /** v2026-09-29 焦点门控：失焦期直接短路，连 rAF 都不起 */
        if (!editorHasFocus()) return;
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
          {/*
            v2026-09-28：格式工具栏改为「媒体限定」挂载——用户决策。
            文本块（可承载行内样式的块）上不再弹出；点击图片/视频/分割线等
            媒体块时仍弹出（即「图片的编辑悬浮工具条」，官方按钮集含
            下载/改名/删除等媒体按钮）。详见 {@link MediaOnlyFormattingToolbarController}。
          */}
          <MediaOnlyFormattingToolbarController />
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
         *
         * ⚠️ **不要禁用 `flip` 的翻转**（v2026-09-30 实测教训）：曾改成
         * `flip({ fallbackPlacements: [] })` 想治「弹层翻到工具条下方撞键盘」，
         * 结果是工具条被避让逻辑顶出视口上缘后**没有任何机制把它拉回来**
         * ——用户视角是「编辑页完全看不到工具条，无法继续操作」。
         * 正确方向是给**避让逻辑**（`applyShift`）加视口上缘下限，
         * 而不是抽掉浮动层的自保能力。
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
