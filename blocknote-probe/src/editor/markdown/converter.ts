/* eslint-disable @typescript-eslint/no-explicit-any */
/**
 * markdown ↔ Blocks 转换管线（迁移计划 §2.2）
 *
 * 官方 API 不认识本项目的自定义编码，采用「预处理 + 后处理」：
 *
 * 载入 mdToBlocks：
 *   ① 预处理（逐行）：分割线样式行 `--- dashed` / `--- wavy` → token 占位段落；
 *      载体空块占位（纯 NBSP 行）→ 剥离；裸 `---`/`***`/`___` 交官方 parser（→ 内置 divider）
 *   ② editor.tryParseMarkdownToBlocks()
 *   ③ 后处理：token 占位段落 → dividerStyled 块（props.style）
 *
 * 保存 blocksToMd：
 *   ① blocks 中 dividerStyled → token 占位段落（防止样式信息在 md 导出中丢失）
 *   ② editor.blocksToMarkdownLossy()
 *   ③ 后处理：token 占位行 → `--- dashed` / `--- wavy` 文本行
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

  // ③ 后处理：token 段落 → dividerStyled 块
  return blocks.map((b) => {
    const content = b?.content;
    if (b?.type === "paragraph" && Array.isArray(content) && content.length === 1) {
      const inline = content[0];
      if (inline?.type === "text") {
        const style = parseDividerToken(inline.text ?? "");
        if (style) {
          return { type: "dividerStyled", props: { style } };
        }
      }
    }
    return b;
  });
}

/**
 * 保存：blocks → markdown
 * @param editor BlockNoteEditor 实例（使用其 blocksToMarkdownLossy，同步）
 */
export function blocksToMd(editor: any, blocks: any[]): string {
  // ① blocks 层面把 dividerStyled 换成 token 段落（防止样式在 md 导出中丢失）
  const mapped = blocks.map((b) => {
    if (b?.type === "dividerStyled") {
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
    return b;
  });

  // ② 官方导出（同步）
  let md: string = editor.blocksToMarkdownLossy(mapped);

  // ③ 后处理：token 行 → 带样式分割线行
  md = md
    .split("\n")
    .map((line) => {
      const style = parseDividerToken(line.trim());
      return style ? `--- ${style}` : line;
    })
    .join("\n");

  return md;
}
