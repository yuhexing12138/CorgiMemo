/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * markdown ↔ Blocks 转换管线（迁移计划 §2.2，P1-S10/S11 更新）
 *
 * 官方 API 不认识本项目的自定义编码，采用「预处理 + 后处理」：
 *
 * 载入 mdToBlocks：
 *   ① 预处理（逐行）：分割线样式行 `--- dashed` / `--- wavy` → token 占位段落；
 *      载体空块占位（纯 NBSP 行）→ 剥离；裸 `---`/`***`/`___` 交官方 parser（→ divider solid）
 *   ② editor.tryParseMarkdownToBlocks()
 *   ③ 后处理：
 *      - token 占位段落 → divider 块（props.style）
 *      - image 块本地路径 → file:// URL（WebView 可加载；保存时剥离还原）
 *
 *   可折叠标题（`<details>`）**无需在此特殊处理**：官方 markdown tokenizer 已把
 *   `details` / `summary` 列入 HTML 块白名单（原样透传），heading 块 spec 的
 *   `parse()` 命中 DETAILS 后会返回 `{ props: { level, isToggleable: true } }`，
 *   并由 `getDetailsContent` 取 `<summary>` 内的标题与其后内容作为 blocks。
 *
 * 保存 blocksToMd：
 *   ① blocks 中 divider(style=dashed/wavy) → token 占位段落（solid 走官方 hr）
 *      image 块 file:// URL → 本地原始路径
 *   ②**可折叠标题 → 三段 HTML 标记包裹**（v2026-09-22 新增，见下方
 *      [encodeToggleHeadings] 与 [TOGGLE_MARKER_HTML]；官方导出会丢掉该标记）
 *   ③ editor.blocksToMarkdownLossy()
 *   ④ 后处理：token 占位行 → `--- dashed` / `--- wavy` 文本行；折叠标题 token 行 →
 *      `<details><summary>` / `</summary>` / `</details>` 标记行
 *
 * 已接受损失（不在往返保证内）：fontSize 等自定义 inline 样式（官方 lossy 导出丢弃）。
 */

/** 与 Compose 版 BodyBlocksEditor.EMPTY_BLOCK_PLACEHOLDER 同语义（NBSP） */
export const EMPTY_BLOCK_PLACEHOLDER = "\u00A0";

/** 分割线样式 token（ASCII 安全，避免 markdown 转义干扰） */
const DIVIDER_TOKEN_PREFIX = "@@@CORGI_DIVIDER_";
const DIVIDER_TOKEN_SUFFIX = "@@@";

/** 带样式的分割线行：`--- dashed` / `*** wavy` 等（裸 `---` 不匹配，交官方 parser） */
const DIVIDER_STYLED_LINE = /^(-{3,}|\*{3,}|_{3,})[ \t]+(dashed|wavy)[ \t]*$/;

/** 整行仅由 NBSP/空白构成（载体空块占位行） */
const NBSP_ONLY_LINE = /^(?:[\u00A0\s])*$/

function isDividerStyledLine(line: string): string | null {
  const m = line.match(DIVIDER_STYLED_LINE);
  return m ? m[2] : null;
}

function isPlaceholderLine(line: string): boolean {
  // 仅 NBSP 行命中（空字符串行是合法的 markdown 段落分隔，保留）
  return line.length > 0 && line.replace(/[\u00A0]/g, "").trim() === "" && line.includes("\u00A0");
}

function dividerToken(style: string): string {
  return `${DIVIDER_TOKEN_PREFIX}${style}${DIVIDER_TOKEN_SUFFIX}`;
}

function parseDividerToken(text: string): string | null {
  if (!text.startsWith(DIVIDER_TOKEN_PREFIX) || !text.endsWith(DIVIDER_TOKEN_SUFFIX)) return null;
  const style = text.slice(DIVIDER_TOKEN_PREFIX.length, text.length - DIVIDER_TOKEN_SUFFIX.length);
  return style === "dashed" || style === "wavy" || style === "solid" ? style : null;
}

/* ===== 可折叠标题的 markdown 往返编码（v2026-09-22 新增）===== */

/**
 * 可折叠标题的三段标记 token
 *
 * **为什么需要**：BlockNote 里折叠标题 = `heading` 块 + `props.isToggleable = true`，
 * 而官方 markdown 导出器（`htmlToMarkdown.serializeDetails`）会把
 * `<details><summary><h3>标题</h3></summary>…</details>` 削成**普通** `### 标题` 一行
 * ——`isToggleable` 整个丢失。本项目正文只以 markdown 持久化（`changed` 快照 → 数据库），
 * 于是折叠标题一退出编辑页就退化成普通标题，故必须由本模块自行编码。
 *
 * **编码形态**（token 用 ASCII，避免 markdown 转义干扰，与 [DIVIDER_TOKEN_PREFIX] 同思路）：
 * ```
 * <details><summary>
 *
 * ### 标题文本
 *
 * </summary>
 *
 * 子块 markdown
 *
 * </details>
 * ```
 * 解析侧能还原的原因：官方 markdown tokenizer 把 `details` / `summary` 列入
 * HTML 块白名单并**原样透传**，各 token 拼接成 HTML 字符串后再由 DOMParser 解析，
 * 于是 `<h3>` 落在未闭合的 `<summary>` 内 → 满足 heading spec `parse()` 的
 * `summary.querySelector("h1..h6")` 判定；`</summary>` 之后的块则成为
 * `<details>` 的直接子节点 → 被 `getDetailsContent` 收成该块的 children。
 */
const TOGGLE_OPEN_TOKEN = "@@@CORGI_TOGGLE_HEADING_OPEN@@@";
const TOGGLE_SUMMARY_END_TOKEN = "@@@CORGI_TOGGLE_HEADING_SUMMARY_END@@@";
const TOGGLE_CLOSE_TOKEN = "@@@CORGI_TOGGLE_HEADING_CLOSE@@@";

/** token → html 标记（导出后处理阶段替换；用 Map 而非对象字面量，避免 `constructor` 之类的原型键误命中） */
const TOGGLE_MARKER_HTML = new Map<string, string>([
  [TOGGLE_OPEN_TOKEN, "<details><summary>"],
  [TOGGLE_SUMMARY_END_TOKEN, "</summary>"],
  [TOGGLE_CLOSE_TOKEN, "</details>"],
]);

/** 构造"只含 token 文本"的占位段落（与 divider token 同一套路：独占一段、行级可定位） */
function markerParagraph(token: string): any {
  return { type: "paragraph", content: [{ type: "text", text: token, styles: {} }] };
}

/**
 * 导出前预处理：把「可折叠标题」块展开为"标记段落 + 原标题 + 标记段落 + 子块 + 标记段落"序列
 *
 * 为什么要**展开成同级序列**而不是替换单个块：
 * 折叠态是块的 props，markdown 没有对应语法，只能靠 HTML 标记包裹；而包裹需要
 * "开标记 / 关标记"分居两端，中间夹着标题自身与其子块，故展开是最自然的形式。
 *
 * 副作用说明（可接受）：
 * - 标题块自身去掉 `isToggleable` 后走官方导出，于是**行内样式（粗体/链接）完整保留**
 *   （若自己拼 HTML 就得手动转义内联内容，反而更易出错）；
 * - 子块被"提升"到与标题同级后再包进 `<details>`，重新解析时会成为该标题块的 children
 *   ——比修复前的"子块永久平铺成兄弟块"更接近原语义；
 * - 但子块在详情里的**中间层级**仍会丢失（markdown 无缩进语义承载），属既有 lossy 范围。
 *
 * @param blocks 任一层的块数组（会递归处理 children，支持嵌套折叠标题）
 * @returns 展开后的块数组（可安全交给 editor.blocksToMarkdownLossy）
 */
function encodeToggleHeadings(blocks: any[]): any[] {
  const out: any[] = [];
  for (const b of blocks) {
    if (b?.type === "heading" && (b.props as any)?.isToggleable === true) {
      /** 剥掉 isToggleable：官方导出会因此输出普通 `### 标题`（正好是要放进 <summary> 的内容） */
      const { isToggleable: _dropped, ...restProps } = (b.props ?? {}) as Record<string, unknown>;
      out.push(markerParagraph(TOGGLE_OPEN_TOKEN));
      out.push({
        type: "heading",
        props: restProps,
        ...(b.content !== undefined ? { content: b.content } : {}),
      });
      out.push(markerParagraph(TOGGLE_SUMMARY_END_TOKEN));
      if (Array.isArray(b.children) && b.children.length > 0) {
        out.push(...encodeToggleHeadings(b.children));
      }
      out.push(markerParagraph(TOGGLE_CLOSE_TOKEN));
      continue;
    }
    /** 非折叠块原样保留，但递归其 children（折叠标题可能嵌在列表项等容器块里） */
    out.push(
      Array.isArray(b?.children) && b.children.length > 0
        ? { ...b, children: encodeToggleHeadings(b.children) }
        : b
    );
  }
  return out;
}

/** 本地路径（/data/... 或 /storage/...）→ file:// URL（逐段 URI 编码，WebView 可加载） */
export function toWebImageUrl(path: string): string {
  if (/^(https?|data|file):/.test(path)) return path; // 网络/data/file 已是可加载形态
  const clean = path.split("?")[0].split("#")[0];
  return (
    "file://" +
    clean
      .split("/")
      .map((seg) => (seg === "" ? "" : encodeURIComponent(seg)))
      .join("/")
  );
}

/** file:// URL → 本地原始路径（保存时剥离；非 file:// 原样返回） */
export function toLocalPath(url: string): string {
  if (!url.startsWith("file://")) return url;
  const p = url.slice("file://".length);
  return p
    .split("/")
    .map((seg) => (seg === "" ? "" : decodeURIComponent(seg)))
    .join("/");
}

/** 图片块 URL 规范化（载入后处理）：本地路径包装 file://（幂等） */
function normalizeImageUrls(blocks: any[]): void {
  for (const b of blocks) {
    if (b?.type === "image" && typeof b.props?.url === "string") {
      b.props.url = toWebImageUrl(b.props.url);
    }
    // 嵌套子块同样处理
    if (Array.isArray(b?.children)) normalizeImageUrls(b.children);
  }
}

/** 图片块 URL 还原（保存前处理）：file:// 剥离回本地原始路径（幂等） */
function restoreImageUrls(blocks: any[]): void {
  for (const b of blocks) {
    if (b?.type === "image" && typeof b.props?.url === "string") {
      b.props.url = toLocalPath(b.props.url);
    }
    if (Array.isArray(b?.children)) restoreImageUrls(b.children);
  }
}

/**
 * 载入：markdown → blocks
 * @param editor BlockNoteEditor 实例（使用其 tryParseMarkdownToBlocks）
 */
export async function mdToBlocks(editor: any, markdown: string): Promise<any[]> {
  // ① 预处理
  const lines = markdown.split("\n");
  const pre: string[] = [];
  for (const line of lines) {
    const style = isDividerStyledLine(line);
    if (style) {
      pre.push(dividerToken(style));
      continue;
    }
    if (isPlaceholderLine(line)) {
      // 载体空块占位行剥离：一行一块模型下不再需要载体
      continue;
    }
    pre.push(line);
  }
  const preMarkdown = pre.join("\n");

  // ② 官方解析
  const blocks: any[] = await editor.tryParseMarkdownToBlocks(preMarkdown);

  // ③ 后处理：token 段落 → divider 块（带样式）；图片 URL 规范化
  const result = blocks.map((b) => {
    const content = b?.content;
    if (b?.type === "paragraph" && Array.isArray(content) && content.length === 1) {
      const inline = content[0];
      if (inline?.type === "text") {
        const style = parseDividerToken(inline.text ?? "");
        if (style) {
          return { type: "divider", props: { style } };
        }
      }
    }
    return b;
  });
  normalizeImageUrls(result);
  return result;
}

/**
 * 保存：blocks → markdown
 * @param editor BlockNoteEditor 实例（使用其 blocksToMarkdownLossy，同步）
 */
export function blocksToMd(editor: any, blocks: any[]): string {
  // ① blocks 层面预处理：styled divider → token 段落（防样式丢失）；image file:// → 本地路径
  const mapped = blocks.map((b) => {
    if (b?.type === "divider") {
      const style = (b.props as any)?.style ?? "solid";
      if (style === "dashed" || style === "wavy") {
        return {
          type: "paragraph",
          content: [{ type: "text", text: dividerToken(style), styles: {} }],
        };
      }
      // solid 走官方 divider 语义（导出 `---`/`***`，parser 再认回）
      return { type: "divider" };
    }
    if (b?.type === "image" && typeof b.props?.url === "string") {
      return { ...b, props: { ...b.props, url: toLocalPath(b.props.url) } };
    }
    return b;
  });

  // ② 可折叠标题展开为 HTML 标记序列（官方导出会丢掉 isToggleable，必须自编码）
  const encoded = encodeToggleHeadings(mapped);

  // ③ 官方导出（同步）
  let md: string = editor.blocksToMarkdownLossy(encoded);

  // ④ 后处理：token 行 → 带样式分割线行 / 折叠标题的 HTML 标记行
  md = md
    .split("\n")
    .flatMap((line) => {
      const style = parseDividerToken(line.trim());
      if (style) return [`--- ${style}`];
      const marker = TOGGLE_MARKER_HTML.get(line.trim());
      /**
       * 折叠标题标记**前后必须各留一个空行**（故返回三个元素）：
       * - 前空行：结束上一段的 raw HTML 块，标记才不会与上文粘成一行；
       * - 后空行：让标记行成为独立的 raw HTML 块 —— 标记之后紧随的标题行才会被
       *   markdown tokenizer 当成 heading 处理（产出 `<h3>`，落在未闭合的
       *   `<summary>` 内），子块行也才会各自成段（成为 `<details>` 的子节点）。
       * 多余的连续空行对 markdown 无副作用。
       */
      if (marker !== undefined) return ["", marker, ""];
      return [line];
    })
    .join("\n");

  return md;
}
