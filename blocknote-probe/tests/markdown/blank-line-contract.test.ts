import { describe, it, expect, beforeAll } from "vitest";
import { BlockNoteEditor } from "@blocknote/core";
import { mdToBlocks, blocksToMd, BLANK_LINE_TOKEN } from "../../src/editor/markdown/converter";
import { editorSchema } from "../../src/editor/schema";

/**
 * 空行（空段落块）markdown 往返契约（v2026-09-23 新增）。
 *
 * **背景（真机故障）**：编辑页手动敲的空行（空段落块）保存后，详情页不显示、
 * 重进编辑页也消失。根因 = CommonMark 里"空段落"不可表示：官方导出把空段
 * 输出为连续空行（`甲\n\n\n\n乙`），而任何 markdown 解析器（编辑页
 * tryParseMarkdownToBlocks / 详情页 compose-rich-editor）都把连续空行折叠成
 * 单个段落分隔 ⇒ 载入方向必丢；再次导出后 markdown 里的多余空行也被压缩，
 * 数据层面彻底丢失（vitest 实测：两轮往返后 `甲\n\n乙`）。
 *
 * **契约**：空段落块 ⇄ `@@@CORGI_BLANK@@@` 独占行 token（与分割线/折叠标题
 * 同一 token 家族）。Compose 侧 `MarkdownParser.INTERNAL_TOKEN_REGEX` 通配剥掉
 * ⇒ 详情页保持"不显示空行"、摘要纯文本不受影响（均有 Kotlin 侧既有口径兜底）。
 *
 * ⚠️ 历史数据（本修复上线前保存的空行）已是"连续空行"形态且无法与
 * 折叠标题标记人为插入的空行区分，**不做重建**（与分割线 `***` 历史数据同例）。
 */
let editor: any;

beforeAll(async () => {
  editor = BlockNoteEditor.create({ schema: editorSchema });
});

/** Compose 侧 INTERNAL_TOKEN_REGEX 的 JS 等价（形态兼容性断言用） */
const KOTLIN_INTERNAL_TOKEN = /^@@@CORGI_[A-Za-z0-9_#]*@@@$/;

describe("空行导出契约：空段落块 → BLANK token 独占行", () => {
  it("单个空段导出为 token 行（而非连续空行）", () => {
    const blocks: any[] = [
      { type: "paragraph", content: [{ type: "text", text: "甲", styles: {} }] },
      { type: "paragraph", content: [] },
      { type: "paragraph", content: [{ type: "text", text: "乙", styles: {} }] },
    ];
    const md = blocksToMd(editor, blocks);
    const lines = md.split("\n").map((l: string) => l.trim());
    expect(lines).toContain(BLANK_LINE_TOKEN);
    // token 行数与空段数一致（1 个）
    expect(lines.filter((l: string) => l === BLANK_LINE_TOKEN).length).toBe(1);
  });

  it("多个连续空段各占一行 token", () => {
    const blocks: any[] = [
      { type: "paragraph", content: [] },
      { type: "paragraph", content: [] },
    ];
    const md = blocksToMd(editor, blocks);
    const lines = md.split("\n").map((l: string) => l.trim());
    expect(lines.filter((l: string) => l === BLANK_LINE_TOKEN).length).toBe(2);
  });

  it("有内容的段落不被误转换", () => {
    const blocks: any[] = [
      { type: "paragraph", content: [{ type: "text", text: "正文", styles: {} }] },
    ];
    const md = blocksToMd(editor, blocks);
    expect(md).not.toContain(BLANK_LINE_TOKEN);
  });

  it("token 形态必须落在 Compose INTERNAL_TOKEN_REGEX 白名单内", () => {
    expect(BLANK_LINE_TOKEN).toMatch(KOTLIN_INTERNAL_TOKEN);
  });
});

describe("空行载入契约：BLANK token 行 → 空段落块", () => {
  it("token 行还原为空段落块", async () => {
    const md = `甲\n\n${BLANK_LINE_TOKEN}\n\n乙`;
    const blocks = await mdToBlocks(editor, md);
    expect(blocks.length).toBe(3);
    expect(blocks[0].type).toBe("paragraph");
    expect(blocks[1].type).toBe("paragraph");
    expect(blocks[1].content).toEqual([]);
    expect(blocks[2].type).toBe("paragraph");
  });

  it("非空内容不受影响", async () => {
    const md = `甲\n\n乙`;
    const blocks = await mdToBlocks(editor, md);
    expect(blocks.length).toBe(2);
    expect(blocks.every((b: any) => Array.isArray(b.content) && b.content.length > 0)).toBe(true);
  });
});

describe("空行全链路往返：两轮后空段保留", () => {
  it("[甲, 空, 乙] → md → blocks → md → blocks，空段稳定存活", async () => {
    const blocks0: any[] = [
      { type: "paragraph", content: [{ type: "text", text: "甲", styles: {} }] },
      { type: "paragraph", content: [] },
      { type: "paragraph", content: [{ type: "text", text: "乙", styles: {} }] },
    ];
    const md1 = blocksToMd(editor, blocks0);
    expect(md1).toContain(BLANK_LINE_TOKEN);

    const blocks1 = await mdToBlocks(editor, md1);
    expect(blocks1.length).toBe(3);
    expect(blocks1[1].content).toEqual([]);

    const md2 = blocksToMd(editor, blocks1);
    expect(md2).toContain(BLANK_LINE_TOKEN);

    const blocks2 = await mdToBlocks(editor, md2);
    expect(blocks2.length).toBe(3);
    expect(blocks2[1].content).toEqual([]);
  });

  it("空段与分割线相邻时往返稳定", async () => {
    const blocks0: any[] = [
      { type: "paragraph", content: [{ type: "text", text: "甲", styles: {} }] },
      { type: "paragraph", content: [] },
      { type: "divider", props: { style: "solid" } },
      { type: "paragraph", content: [{ type: "text", text: "乙", styles: {} }] },
    ];
    const md1 = blocksToMd(editor, blocks0);
    const blocks1 = await mdToBlocks(editor, md1);
    expect(blocks1.length).toBe(4);
    expect(blocks1[1].type).toBe("paragraph");
    expect(blocks1[1].content).toEqual([]);
    expect(blocks1[2].type).toBe("divider");
  });
});
