/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * markdown ↔ Blocks 转换管线（迁移计划 §2.2，P1-S10/S11 更新）
 *
 * 官方 API 不认识本项目的自定义编码，采用「预处理 + 后处理」：
 *
 * 载入 mdToBlocks：
 *   ① 预处理（逐行）：分割线样式行 `--- dashed` / `--- wavy` → token 占位段落；
 *      **裸实线分割线 `---` / `***` / `___` → 同款 `solid` token 占位段**
 *      （v2026-09-23：不再交官方 parser，官方会静默丢弃 ⇒ 实线进编辑页消失）；
 *      载体空块占位（纯 NBSP 行）→ 剥离
 *   ② editor.tryParseMarkdownToBlocks()
 *   ③ 后处理：
 *      - token 占位段落 → divider 块（props.style，三种样式同一条路径）
 *      - 块级色的行首 token → 块 props（见 [decodeBlockColorTokens]）
 *      - image 块本地路径 → file:// URL（WebView 可加载；保存时剥离还原）
 *
 *   另外两类自编码**无需在此特殊处理**：
 *   - 可折叠标题（`<details>`）：官方 markdown tokenizer 已把 `details` / `summary`
 *     列入 HTML 块白名单（原样透传），heading 块 spec 的 `parse()` 命中 DETAILS 后
 *     返回 `{ props: { level, isToggleable: true } }`，并由 `getDetailsContent`
 *     取 `<summary>` 内的标题与其后内容作为 blocks；
 *   - 行内色（`<span style="color:…">`）：官方 `tryInlineHtml` 原样透传行内标签，
 *     再由本项目覆盖后的 color 样式 `parse` 还原为 mark。
 *
 * 保存 blocksToMd：
 *   ① blocks 中 divider(style=dashed/wavy) → token 占位段落（solid 走官方 hr）
 *      image 块 file:// URL → 本地原始路径
 *   ②**行内色 → 行内 token**（v2026-09-22 新增，见 [encodeInlineColorsDeep] /
 *      [decodeInlineColorTokens]；官方导出会把颜色 span 直接剥掉）
 *   ③**块级色 → 块内容行首 token**（v2026-09-22 新增，见 [encodeBlockColorsDeep] /
 *      [decodeBlockColorTokens]；官方块序列化器不读块元素属性）
 *   ④**可折叠标题 → 三段 HTML 标记包裹**（v2026-09-22 新增，见下方
 *      [encodeToggleHeadings] 与 [TOGGLE_MARKER_HTML]；官方导出会丢掉该标记）
 *   ⑤ editor.blocksToMarkdownLossy()
 *   ⑥ 后处理：行内色 token → `<span style="color|background-color:…">`
 *   ⑦ 后处理：token 占位行 → `--- dashed` / `--- wavy` 文本行；折叠标题 token 行 →
 *      `<details><summary>` / `</summary>` / `</details>` 标记行
 *      （块级色 token 保持纯文本原样落库——它的还原发生在载入方向）
 *
 * 已接受损失（不在往返保证内）：fontSize 行内样式（官方 lossy 导出丢弃）。
 * v2026-09-22 追加：**textColor / backgroundColor 已纳入往返**（行内 + 块级，曾经都是损失项）。
 */

/** 与 Compose 版 BodyBlocksEditor.EMPTY_BLOCK_PLACEHOLDER 同语义（NBSP） */
export const EMPTY_BLOCK_PLACEHOLDER = "\u00A0";

/** 分割线样式 token（ASCII 安全，避免 markdown 转义干扰） */
const DIVIDER_TOKEN_PREFIX = "@@@CORGI_DIVIDER_";
const DIVIDER_TOKEN_SUFFIX = "@@@";

/** 带样式的分割线行：`--- dashed` / `*** wavy` 等（裸 `---` 不匹配，交官方 parser） */
const DIVIDER_STYLED_LINE = /^(-{3,}|\*{3,}|_{3,})[ \t]+(dashed|wavy)[ \t]*$/;

/**
 * 裸 thematic break 的**别名**写法（`***` / `___`，含 3 个以上的变体）。
 *
 * 官方导出器对 divider 块产出 `***`，而本管线统一以 `---` 作为实线分割线的载体，
 * 故 `blocksToMd` ⑦ 与 `mdToBlocks` ① 都要用本判定把别名翻转成 `---`。
 *
 * 必须整行**仅由同一种符号**构成，避免误伤 `**粗体**` 之类：`*` 与 `_`
 * 已由 `[ \t]*` 之外的字符排除，`---` 自身不在此列（无需翻转）。
 *
 * ⚠️ **两侧必须同时归一**（v2026-09-23 修复「重进编辑页实线消失」）：
 * 只在导出侧归一是不够的——落库的 markdown 未必都经过本管线（旧数据、
 * 其它写入路径、用户手输），一旦库里存的是 `***`，载入侧若不认，
 * 官方 parser 会把它当 thematic break **静默丢弃**（divider 块不生成、
 * 也不留字面文本），表现为"实线分割线凭空消失"。详见 [mdToBlocks] ①。
 */
const BARE_THEMATIC_BREAK_ALIAS = /^(?:\*{3,}|_{3,})[ \t]*$/;

/**
 * 整行恰为 `---`（裸实线分割线载体；`--- dashed` / `--- wavy` 由
 * [isDividerStyledLine] 先行匹配，不落到这里）。
 */
const BARE_HYPHEN_THEMATIC_BREAK = /^-{3,}[ \t]*$/;

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

/* ===== 行内文字色 / 背景色的 markdown 往返编码（v2026-09-22 新增）===== */

/**
 * 行内色的开/闭标记 token
 *
 * **为什么需要**：官方 markdown 导出会**主动剥掉颜色 span**——
 * `htmlToMarkdown.serializeInlineContent` 里明写
 * `case "span": // Color spans, etc. — strip the tag, keep content`。
 * 而本项目正文只以 markdown 持久化，于是行内色在保存那一刻就丢了
 * （与 fontSize 同属"markdown 无对应语法"的一类，只是颜色还被官方显式丢弃）。
 *
 * **编码形态**（与折叠标题同一套"token + 后处理"思路）：
 * 导出前把带色的 text 片段拆成
 * `@@@CORGI_IC_TC_#FF9A5C@@@` + 原文 + `@@@CORGI_IC_END@@@`（背景色用 `BG_` 前缀），
 * 官方导出后再把 token 反向替换成**原生行内 HTML**
 * `<span style="color:#FF9A5C">…</span>`。
 *
 * **解析侧为何能还原**：官方 markdown tokenizer 有 `tryInlineHtml`，
 * 会把形如 `<span style="…">` 的行内标签**原样透传**进 HTML 字符串；
 * 随后 heading/paragraph 的 inline 解析由各 style spec 的 `parse` 处理，
 * 而本项目已同名覆盖 color 样式（见 `editor/schema.ts`），其 `parse` 认
 * `<span style="color:…">` / `<span style="background-color:…">`。
 *
 * ⚠️ 值只接受 `[0-9A-Za-z#]+`（本项目面板下发 6 位 hex；同时兼容 3 位 hex 与
 * 官方色名）。含其它字符的值（如 `rgb(255,0,0)`）**不编码**，退化为既有行为
 * （颜色不持久化），以免把引号等危险字符带进 style 属性。
 */
const INLINE_TEXT_COLOR_PREFIX = "@@@CORGI_IC_TC_";
const INLINE_BG_COLOR_PREFIX = "@@@CORGI_IC_BG_";
const INLINE_COLOR_TOKEN_SUFFIX = "@@@";
/** 闭标记（开闭总是成对输出，故单一只即可） */
const INLINE_COLOR_END_TOKEN = "@@@CORGI_IC_END@@@";
/** token 内允许出现的值字符集（hex 与色名；刻意不含引号/括号/空格） */
const INLINE_COLOR_SAFE_VALUE = /^[0-9A-Za-z#]+$/;

/** 只含标记文本的行内片段（styles 留空，确保官方导出不会为它再生成 span） */
function colorMarkerText(token: string): any {
  return { type: "text", text: token, styles: {} };
}

/**
 * 把一段 inline content 里的行内色"转义"为 token 序列
 *
 * 处理粒度是**单个带色的 text 片段**（不做跨片段的同色合并）：
 * 同色相邻片段会各自带一对标记，markdown 略长，但实现简单、不会因合并逻辑
 * 引入配对错误；解析回来是多个 mark 片段，视觉完全一致。
 *
 * ⚠️ `link` 的行内内容（`content: StyledText[]`）也要递归——带色链接文字同样要保色。
 *
 * @param content 块的 inline content 数组
 * @returns 展开后的 inline content 数组
 */
function encodeInlineColors(content: any[]): any[] {
  const out: any[] = [];
  for (const item of content) {
    /** 链接：递归其内部文本，其余字段保持不变 */
    if (item?.type === "link" && Array.isArray(item.content)) {
      out.push({ ...item, content: encodeInlineColors(item.content) });
      continue;
    }
    /** 非文本行内内容（自定义 inline content 等）原样透传 */
    if (item?.type !== "text" || typeof item.text !== "string") {
      out.push(item);
      continue;
    }
    const styles = (item.styles ?? {}) as Record<string, unknown>;
    const textColor = typeof styles.textColor === "string" ? styles.textColor : undefined;
    const bgColor =
      typeof styles.backgroundColor === "string" ? styles.backgroundColor : undefined;
    const encodeTc = textColor !== undefined && INLINE_COLOR_SAFE_VALUE.test(textColor);
    const encodeBg = bgColor !== undefined && INLINE_COLOR_SAFE_VALUE.test(bgColor);
    if (!encodeTc && !encodeBg) {
      out.push(item);
      continue;
    }
    /** 剥掉已编码成功的颜色维度（未编码的维度保留，交给官方行为处理） */
    const restStyles = { ...styles };
    if (encodeTc) delete restStyles.textColor;
    if (encodeBg) delete restStyles.backgroundColor;

    if (encodeTc) out.push(colorMarkerText(`${INLINE_TEXT_COLOR_PREFIX}${textColor}${INLINE_COLOR_TOKEN_SUFFIX}`));
    if (encodeBg) out.push(colorMarkerText(`${INLINE_BG_COLOR_PREFIX}${bgColor}${INLINE_COLOR_TOKEN_SUFFIX}`));
    out.push({ ...item, styles: restStyles });
    /** 闭标记按开标记的逆序补回，保证 span 正确嵌套 */
    if (encodeBg) out.push(colorMarkerText(INLINE_COLOR_END_TOKEN));
    if (encodeTc) out.push(colorMarkerText(INLINE_COLOR_END_TOKEN));
  }
  return out;
}

/**
 * 导出前预处理：递归整棵块树，把所有行内色片段编码为 token
 *
 * @param blocks 任一层的块数组（递归 content 与 children）
 * @returns 编码后的块数组（可安全交给 editor.blocksToMarkdownLossy）
 */
function encodeInlineColorsDeep(blocks: any[]): any[] {
  return blocks.map((b) => {
    const next: any = { ...b };
    if (Array.isArray(b?.content) && b.content.length > 0) {
      next.content = encodeInlineColors(b.content);
    }
    if (Array.isArray(b?.children) && b.children.length > 0) {
      next.children = encodeInlineColorsDeep(b.children);
    }
    return next;
  });
}

/**
 * 导出后处理：把行内色 token 还原成原生 HTML 标签
 *
 * token 是**行内**出现的（不像折叠标题那样独占一行），故对整篇 markdown 做
 * 全局替换，而非逐行处理。
 *
 * @param md 官方导出的 markdown
 * @returns 带 `<span style="…">` 的 markdown
 */
function decodeInlineColorTokens(md: string): string {
  return md
    .replace(
      new RegExp(`${INLINE_TEXT_COLOR_PREFIX}([0-9A-Za-z#]+)${INLINE_COLOR_TOKEN_SUFFIX}`, "g"),
      '<span style="color:$1">'
    )
    .replace(
      new RegExp(`${INLINE_BG_COLOR_PREFIX}([0-9A-Za-z#]+)${INLINE_COLOR_TOKEN_SUFFIX}`, "g"),
      '<span style="background-color:$1">'
    )
    .split(INLINE_COLOR_END_TOKEN)
    .join("</span>");
}

/* ===== 段落（块级）文字色 / 背景色的 markdown 往返编码（v2026-09-22 新增）===== */

/**
 * 块级色的行首标记 token
 *
 * **为什么需要**：块级色是**块 props**（`textColor` / `backgroundColor`，取值为 BlockNote
 * 预设色名），官方导出会把它们写成块元素上的 `data-text-color` / `data-background-color`
 * 属性（`blocks/defaultProps.ts` 的 `addDefaultPropsExternalHTML`）——但
 * `htmlToMarkdown` 的块序列化器**只读结构与 inline 内容、不读元素属性**，
 * 于是块级色同样进不了 markdown，重进笔记即丢。
 *
 * **为什么不像行内色那样用 HTML 承载**：块级色需要贴在"块自己的标签"上
 * （`<p data-text-color="red">`），而该块导出成 `p` / `h2` / `li` / `blockquote`
 * 哪一种**无法预知**，固定开闭标记包不出这个形态；若外包一层 `<div data-…>`，
 * 该 div 在解析时会被 ProseMirror 当作不匹配元素**下钻丢弃**，属性也随之丢失。
 *
 * **采用机制**：把 token 作为**块内容的第一个文本片段**（`@@@CORGI_BC_TC_red@@@`
 * 与/或 `@@@CORGI_BC_BG_blue@@@`，TC 恒在 BG 之前）。token 是块内普通文本，
 * 会随 markdown 正常往返、位置永远贴在该块内容的最前面；载入后再由
 * [decodeBlockColorTokens] 从行首剥离并写回块 props——**不需要任何 CSS**，
 * 块级色继续走官方渲染路径，语义无损（含"背景色铺满整块"这一块级特性）。
 *
 * ⚠️ 只在**成功插入 token** 时才剥掉原 props（与行内色同一原则：
 * 不具备编码条件的块宁可维持现状，也不制造"属性丢了、标记也没带上"的净损失）。
 */
const BLOCK_TEXT_COLOR_PREFIX = "@@@CORGI_BC_TC_";
const BLOCK_BG_COLOR_PREFIX = "@@@CORGI_BC_BG_";

/** 块内容行首的 token 匹配（值字符集与行内色一致，`@` 不在集内故不会吞掉后缀） */
const BLOCK_COLOR_PREFIX_RE = /^@@@CORGI_BC_(TC|BG)_([0-9A-Za-z#]+)@@@/;

/** 块级色的有效取值（非空、非 "default"） */
function blockColorValue(raw: unknown): string | undefined {
  return typeof raw === "string" && raw.length > 0 && raw !== "default" && INLINE_COLOR_SAFE_VALUE.test(raw)
    ? raw
    : undefined;
}

/**
 * 导出前预处理：把带块级色的块打上行首 token，并剥掉其颜色 props
 *
 * @param blocks 任一层的块数组（递归 children；块级色可能出现在嵌套块上）
 * @returns 编码后的块数组
 */
function encodeBlockColorsDeep(blocks: any[]): any[] {
  return blocks.map((b) => {
    const textColor = blockColorValue((b?.props ?? {}).textColor);
    const bgColor = blockColorValue((b?.props ?? {}).backgroundColor);
    /** 无块级色，或该块没有 inline 内容（table / image / divider 等）→ 原样透传 */
    if (
      (textColor === undefined && bgColor === undefined) ||
      !Array.isArray(b?.content)
    ) {
      return Array.isArray(b?.children) && b.children.length > 0
        ? { ...b, children: encodeBlockColorsDeep(b.children) }
        : b;
    }
    const restProps = { ...(b.props ?? {}) } as Record<string, unknown>;
    delete restProps.textColor;
    delete restProps.backgroundColor;
    const markers: any[] = [];
    if (textColor !== undefined) {
      markers.push(colorMarkerText(`${BLOCK_TEXT_COLOR_PREFIX}${textColor}${INLINE_COLOR_TOKEN_SUFFIX}`));
    }
    if (bgColor !== undefined) {
      markers.push(colorMarkerText(`${BLOCK_BG_COLOR_PREFIX}${bgColor}${INLINE_COLOR_TOKEN_SUFFIX}`));
    }
    return {
      ...b,
      props: restProps,
      content: [...markers, ...b.content],
      ...(Array.isArray(b.children) && b.children.length > 0
        ? { children: encodeBlockColorsDeep(b.children) }
        : {}),
    };
  });
}

/**
 * 从文本**开头**连续剥离块级色 token
 *
 * 之所以做"前缀剥离"而不是"整段等于 token"判断：token 与其后的正文在
 * markdown → HTML → 文本节点这条路上**可能被合并成同一个 text 片段**
 * （`@@@CORGI_BC_TC_red@@@标题文本`），整段相等判断会漏掉这种情况。
 *
 * @param text 待剥离的文本
 * @returns 剥离结果（含剩余文本与取到的色值）；开头没有 token 时返回 null
 */
function stripBlockColorTokens(
  text: string
): { text: string; textColor?: string; bgColor?: string } | null {
  let rest = text;
  let textColor: string | undefined;
  let bgColor: string | undefined;
  for (;;) {
    const matched = BLOCK_COLOR_PREFIX_RE.exec(rest);
    if (!matched) break;
    if (matched[1] === "TC") textColor = matched[2];
    else bgColor = matched[2];
    rest = rest.slice(matched[0].length);
  }
  if (textColor === undefined && bgColor === undefined) return null;
  return { text: rest, textColor, bgColor };
}

/**
 * 载入后处理：从块内容行首剥离块级色 token，并写回块 props
 *
 * 必须在编辑器装载**之前**完成（本函数在 [mdToBlocks] 的解析后处理里调用）——
 * 否则 token 会作为可见文本一闪而过。
 *
 * @param blocks 解析得到的块数组（递归 children）
 * @returns 已还原块级色的块数组
 */
function decodeBlockColorTokens(blocks: any[]): any[] {
  return blocks.map((b) => {
    let textColor: string | undefined;
    let bgColor: string | undefined;
    let content = b?.content;
    if (Array.isArray(content) && content.length > 0) {
      const rest = [...content];
      /** 逐个剥离：导出的 token 是独立文本节点，但可能被合并回同一个片段 */
      while (rest.length > 0) {
        const head = rest[0];
        if (head?.type !== "text" || typeof head.text !== "string") break;
        const stripped = stripBlockColorTokens(head.text);
        if (!stripped) break;
        textColor = stripped.textColor ?? textColor;
        bgColor = stripped.bgColor ?? bgColor;
        if (stripped.text.length === 0) rest.shift();
        else rest[0] = { ...head, text: stripped.text };
      }
      if (textColor !== undefined || bgColor !== undefined) content = rest;
    }
    const children =
      Array.isArray(b?.children) && b.children.length > 0
        ? decodeBlockColorTokens(b.children)
        : undefined;
    if (content === b?.content && children === undefined) return b;
    const props = { ...(b?.props ?? {}) } as Record<string, unknown>;
    if (textColor !== undefined) props.textColor = textColor;
    if (bgColor !== undefined) props.backgroundColor = bgColor;
    return {
      ...b,
      props,
      ...(content !== b?.content ? { content } : {}),
      ...(children !== undefined ? { children } : {}),
    };
  });
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

/* ===== 裸 URL 的块模型级 autolink（v2026-09-22 新增，修复"链接重进失效"）===== */

/**
 * 裸 URL 识别（载入端）。只认 `http(s)://` 前缀——与编辑器内 autolink 插件
 * （`@blocknote/core` Link 扩展的 `autolink.ts`）和详情页 markdown 解析
 * （compose-rich-editor 认 `GFM_AUTOLINK`）的覆盖面保持一致；不带协议的
 * `www.` 不处理，避免把普通文本误判成链接。
 *
 * ⚠️ 字符集**必须排除 CJK**（汉字 U+4E00-9FFF、CJK 标点 U+3000-303F、
 * 全角字符 U+FF00-FFEF）：中文笔记里 URL 后面紧跟着中文极常见
 * （`https://a.com/b，很有用`），若不排除，`[^\s]+` 会把整句中文吞进 URL。
 * 排除空白与 `< > " \`` 则保证不吞 HTML 属性的引号（结构化 text 里本就
 * 没有标签，此处属双保险）。
 */
const BARE_URL_GLOBAL_RE =
  /https?:\/\/[^\s<>"`\u4e00-\u9fff\u3000-\u303f\uff00-\uffef]+/g;

/** URL 尾部要剥掉的标点（URL 不以这些收尾；中英文一并处理） */
const URL_TRAILING_PUNCT_RE = /[.,;:!?"'，。；：！？、）】」》…]+$/;

/**
 * 剥掉 URL 尾部标点与**不平衡**的右括号 / 方括号。
 * 按配对处理：`https://a.com/b_(c)` 平衡 → 保留；`https://a.com/b)` 多一个
 * 右括号 → 剥掉（否则链接文字会拖上正文里的括号）。
 */
function trimUrlTail(raw: string): string {
  let url = raw.replace(URL_TRAILING_PUNCT_RE, "");
  for (;;) {
    const last = url[url.length - 1];
    if (last === ")") {
      const open = (url.match(/\(/g) ?? []).length;
      const close = (url.match(/\)/g) ?? []).length;
      if (close > open) {
        url = url.slice(0, -1);
        continue;
      }
    }
    if (last === "]") {
      const open = (url.match(/\[/g) ?? []).length;
      const close = (url.match(/\]/g) ?? []).length;
      if (close > open) {
        url = url.slice(0, -1);
        continue;
      }
    }
    break;
  }
  return url;
}

/**
 * 把单个 text 片段里的裸 URL 拆成 text / link 交替序列。
 *
 * 前置边界对齐 tiptap autolink 的触发条件：URL 必须在片段开头，
 * 或前一个字符是空白——`foohttps://a.com` 这类粘连文本不会误判。
 *
 * @returns 拆分后的片段数组；无匹配时原样返回单元素数组（不做无谓拷贝）
 */
function splitBareUrls(item: any): any[] {
  const text = item.text as string;
  /** 原片段样式：拆出的每个片段（前缀 / 链接内文字 / 后缀）都必须继承，
   *  否则"带色文字里的裸 URL"拆完前后会掉色 */
  const baseStyles = { ...((item.styles ?? {}) as Record<string, unknown>) };
  const out: any[] = [];
  let cursor = 0;
  for (const m of text.matchAll(BARE_URL_GLOBAL_RE)) {
    const url = trimUrlTail(m[0]);
    if (!url) continue;
    const start = m.index ?? 0;
    if (start > 0 && !/\s/.test(text[start - 1])) continue;
    const end = start + url.length;
    if (end <= start) continue;
    if (start > cursor) {
      out.push({ type: "text", text: text.slice(cursor, start), styles: { ...baseStyles } });
    }
    /**
     * link 行内内容与官方 `nodeToBlock` 产出的形态完全一致：
     * `{ type: "link", href, content: [{ type: "text", text, styles }] }`
     */
    out.push({
      type: "link",
      href: url,
      content: [{ type: "text", text: text.slice(start, end), styles: { ...baseStyles } }],
    });
    cursor = end;
  }
  if (out.length === 0) return [item];
  if (cursor < text.length) {
    out.push({ type: "text", text: text.slice(cursor), styles: { ...baseStyles } });
  }
  return out;
}

/**
 * 载入后处理：把解析结果里 text 片段中的裸 URL 还原成链接（块模型级 autolink）。
 *
 * ⚠️ **为什么必须做**（修复"插入链接保存重进后编辑页失效"）：
 * BlockNote 导出时，`htmlToMarkdown.ts` 的 `formatLink` 对「显示文本 == URL」的
 * 链接**有意导出为裸 URL**（`if (!text || text === href) return href`，BlockNote#2661：
 * 裸 URL 粘到别的输入框能被目标自动识别，避免 `<url>` 尖括号或冗余的 `[url](url)`）。
 * 而"未选中文字插入链接"正是显示文本 = URL 原文 → 保存后 markdown 里只剩裸 URL。
 * 但官方 markdown **解析器没有 autolink**（`markdownToHtml.ts` 的 inline tokenizers
 * 只认 `[text](url)`），于是重进时裸 URL 被当纯文本，链接丢失。
 * （详情页不受影响：compose-rich-editor 的 markdown 解析认 `GFM_AUTOLINK`。）
 *
 * **为什么在块模型层面做而不是给 markdown 加正则**：解析完成后数据已结构化，
 * 代码块、行内代码、HTML 标记、已有链接都各归其位——在这里拆分天然不会误伤它们；
 * 若在 markdown 文本上跑正则，就得逐个排除 `<a href="…">` 属性、``` 围栏、
 * `](…)` 链接目标等一堆上下文，极易出漏。
 *
 * 幂等：载入拆成 link → 导出又退化为裸 URL（官方行为）→ 再载入再拆回 link，
 * 数据形态稳定，不会越循环越乱。
 *
 * @param blocks 解析得到的块数组（原地修改；递归 children，quote/列表项等容器一并覆盖）
 */
function autolinkBareUrls(blocks: any[]): void {
  for (const b of blocks) {
    if (!b || typeof b !== "object") continue;
    if (Array.isArray(b.content)) {
      const next: any[] = [];
      for (const item of b.content) {
        if (item?.type === "text" && typeof item.text === "string") {
          next.push(...splitBareUrls(item));
        } else {
          /** 已是 link（显式标题链接）/ 其它行内内容原样保留，不重复处理 */
          next.push(item);
        }
      }
      b.content = next;
    }
    if (Array.isArray(b.children) && b.children.length > 0) {
      autolinkBareUrls(b.children);
    }
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
    /**
     * 裸实线分割线（`---` / `***` / `___`）→ **显式 token 占位段**
     * （v2026-09-23 修复「重进编辑页实线分割线消失」）。
     *
     * **原实现**：把裸 `---` / `***` 直接留给官方 `tryParseMarkdownToBlocks`，
     * 指望它解析出 divider 块。**这在本项目 schema 下不成立**——实测（真机数据 id=14）：
     * ```
     * 项目 schema： [para:"实线分割线", para:"虚线"]            ← divider 凭空消失
     * 官方 schema： [para:"实线分割线", divider, para:"虚线"]    ← 正常
     * ```
     * 同一份 markdown、同一个官方 parser，差别只在 schema：本项目在
     * `schema.ts` 用 `StyledDividerBlock` **同名覆盖**了内置 divider
     * （多一个 `style` prop、`content: "none"`），官方 markdown tokenizer
     * 按内置 `thematicBreak` 语义找不到可落地的块类型，于是**静默丢弃整行**
     * ——既不建块、也不留字面文本（这正是"没有线、也没有文字"的原因）。
     *
     * **为什么此前只暴露实线**：虚线 / 波浪走 token 路径
     * （`--- dashed` → `@@@CORGI_DIVIDER_dashed@@@` → ③ 还原成 divider 块），
     * 压根不经过官方 thematic break 分支；唯有裸实线依赖官方识别，
     * 于是「详情页正常（那里是 Compose 直接按 `---` 画线）、重进编辑页却没了」。
     *
     * **归一方向**：三种等价写法都收敛到 `solid` token，与 ③ 的还原逻辑对接，
     * 使实线与另两种样式走**同一条**载入路径（不再依赖官方 thematic break 行为）。
     *
     * **安全性**：与导出侧同款理由——本项目块间恒以 `\n\n` 连接、分割线独占一段，
     * `---` 不会被当作 setext 标题下划线；且此处仅做整行等价替换，不增删行。
     */
    if (BARE_HYPHEN_THEMATIC_BREAK.test(line.trim()) ||
      BARE_THEMATIC_BREAK_ALIAS.test(line.trim())) {
      pre.push(dividerToken("solid"));
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

  // ③ 后处理：token 段落 → divider 块（带样式）
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

  // ④ 后处理：块级色行首 token → 块 props（必须在装载编辑器前完成，否则 token 会闪现）
  const colored = decodeBlockColorTokens(result);

  // ⑤ 后处理：裸 URL → link 行内内容（官方 markdown 解析器无 autolink，
  //    而"显示文本==URL"的链接导出时会被官方退化为裸 URL，见 autolinkBareUrls 注释）
  autolinkBareUrls(colored);

  // ⑥ 后处理：图片 URL 规范化（本地路径 → file://，WebView 可加载）
  normalizeImageUrls(colored);
  return colored;
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

  // ② 行内色编码为 token（官方导出会剥掉颜色 span，必须自编码）
  const colored = encodeInlineColorsDeep(mapped);

  // ③ 块级色编码为行首 token（官方块序列化器不读块元素属性，必须自编码）
  const blockColored = encodeBlockColorsDeep(colored);

  // ④ 可折叠标题展开为 HTML 标记序列（官方导出会丢掉 isToggleable，必须自编码）
  const encoded = encodeToggleHeadings(blockColored);

  // ⑤ 官方导出（同步）
  let md: string = editor.blocksToMarkdownLossy(encoded);

  // ⑥ 后处理：行内色 token → 原生 <span style>（块级色 token 是纯文本，此处不动）
  md = decodeInlineColorTokens(md);

  // ⑦ 后处理：token 行 → 带样式分割线行 / 折叠标题的 HTML 标记行
  md = md
    .split("\n")
    .flatMap((line) => {
      const style = parseDividerToken(line.trim());
      if (style) return [`--- ${style}`];
      /**
       * 裸 `***` / `___` → `---`（v2026-09-23 修复「分割线存库后丢失」）。
       *
       * **背景**：官方 `blocksToMarkdownLossy` 对 divider 块输出的是 `***`
       * （CommonMark thematic break 的等价写法之一），而宿主侧
       * `BodyBlocksController.DIVIDER_MD` 定义的分割线载体**只有 `---` 系列**
       * （`---` / `--- dashed` / `--- wavy`），且 `isDividerMarkdown()` 是**精确等值**判定
       * ——`***` 不匹配任何一条 ⇒ 详情页不画线、重进编辑页被还原成普通文本
       * （用户感知为"分割线消失"）。样式版（dashed/wavy）因走 token 路径被规范成
       * `--- <style>` 而不受影响，故此前只暴露在默认实线上。
       *
       * **为何不直接在 `blocksToMd` 的 solid 分支改**：那里是"块 → 块"的预处理，
       * divider 块须交给官方导出器（它同时负责 `---` 前后的空行语义）。
       * 改块模型反而要自己重建空行，故在文本后处理阶段归一 —— 与 token 行同一处、同一时机。
       *
       * ⚠️ 导出侧归一**不足以保证载入侧认得**（v2026-09-23 补）：落库 markdown 未必
       * 都经本管线。载入侧的对应归一在 [mdToBlocks] ①，两侧必须成对存在。
       *
       * **安全性**：`---` 紧跟非空行时会被 markdown 当作 setext 标题下划线（把上一行
       * 变成 h2）。本项目块间恒以 `\n\n` 连接、分割线独占一段，故天然不触发；
       * 此处仅做等价翻转（`***` → `---`），不新增行、不改变空行结构。
       */
      if (BARE_THEMATIC_BREAK_ALIAS.test(line.trim())) return ["---"];
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
