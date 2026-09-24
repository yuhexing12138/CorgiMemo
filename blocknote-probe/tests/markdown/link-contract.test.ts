/**
 * 链接 markdown 往返契约（v2026-09-24 新增）。
 *
 * **背景（真机故障）**：「链接面板只填 URL 插入（显示文本 == href）→ 保存 →
 * 详情页跳转正常 → 重进编辑页链接降级为普通文字」。
 *
 * **根因链**：
 * 1. BlockNote 导出时 `htmlToMarkdown.ts` 的 `formatLink` 对「显示文本 == URL」
 *    的链接**有意退化为裸 URL**（BlockNote#2661，`if (!text || text === href) return href`）；
 * 2. 「光标停在段落文字末尾插链接」导出的裸 URL 会**粘连在前文后面**
 *    （`前文https://a.com`，无空白分隔）；
 * 3. 载入端 `autolinkBareUrls` 的 `splitBareUrls` 原边界「URL 必须在片段开头或
 *    空白之后」把粘连形态整段判为普通文字；而详情页 GFM_AUTOLINK 对
 *    「CJK 后跟 URL」宽容 ⇒ 形成「详情页正常、编辑页丢失」的迷惑现象。
 *
 * **修复**：`splitBareUrls` 前置边界**终版取消**（v2026-09-24，按用户决策）——
 * **URL 无论粘在什么字符后面都拆**。演进：开头/空白后 → 放宽 CJK → 放宽标点
 * → 彻底取消（真机连续两轮暴露：CJK 粘连、冒号粘连）。权衡：**宁可多成链、
 * 不可丢链**——链接误判代价极低（点开即知、可编辑/移除），链接丢失是静默降级。
 *
 * **契约**（本文件断言）：
 * - 「显示文本 == href」的链接导出为裸 URL（官方行为，不拦）；
 * - 裸 URL **无论粘在什么字符后**（CJK / 半角冒号 / 全角括号 / 英文字母 / 数字）
 *   均还原为 link 行内内容，前文保持普通文字；
 * - 显式 `[text](url)`（显示文本 ≠ href）导出/载入全程保形；
 * - URL **内部**仍不吞 CJK/空白（`https://a.com，很有用` 的中文留在 link 外）；
 * - 载入还原的 link 形态与官方 `nodeToBlock` 一致
 *   （`{ type: "link", href, content: [{ type: "text", text, styles }] }`）。
 */
import { describe, it, expect, beforeAll } from "vitest";
import { BlockNoteEditor } from "@blocknote/core";
import { mdToBlocks, blocksToMd } from "../../src/editor/markdown/converter";
import { editorSchema } from "../../src/editor/schema";

let editor: any;

beforeAll(async () => {
  editor = BlockNoteEditor.create({ schema: editorSchema });
});

/** 「显示文本 == href」的链接块（URL 面板只填 URL 的插入形态） */
const SELF_LINK_BLOCK = {
  type: "paragraph",
  content: [
    {
      type: "link",
      href: "https://example.com",
      content: [{ type: "text", text: "https://example.com", styles: {} }],
    },
  ],
};

/** 断言 blocks 的首个段落含 link 行内内容，且 href 与显示文本正确 */
function expectLink(blocks: any[], href: string, text: string) {
  const para = blocks.find((b: any) => b?.type === "paragraph");
  expect(para).toBeDefined();
  const link = (para.content ?? []).find((c: any) => c?.type === "link");
  expect(link).toBeDefined();
  expect(link.href).toBe(href);
  expect(link.content?.[0]?.text).toBe(text);
}

describe("链接导出契约：官方 formatLink 对「显示文本 == URL」退化为裸 URL", () => {
  it("独立链接段导出为裸 URL（不拦官方行为）", () => {
    const md = blocksToMd(editor, [SELF_LINK_BLOCK]);
    expect(md.trim()).toBe("https://example.com");
  });

  it("前文 + 链接段导出为「前文+裸URL」粘连形态（无空白分隔）", () => {
    const blocks: any[] = [
      {
        type: "paragraph",
        content: [
          { type: "text", text: "前文", styles: {} },
          {
            type: "link",
            href: "https://example.com",
            content: [{ type: "text", text: "https://example.com", styles: {} }],
          },
        ],
      },
    ];
    const md = blocksToMd(editor, blocks);
    expect(md.trim()).toBe("前文https://example.com");
  });

  it("显示文本 ≠ href 的链接导出为标准 [text](url)", () => {
    const blocks: any[] = [
      {
        type: "paragraph",
        content: [
          {
            type: "link",
            href: "https://example.com",
            content: [{ type: "text", text: "显示文字", styles: {} }],
          },
        ],
      },
    ];
    const md = blocksToMd(editor, blocks);
    expect(md).toContain("[显示文字](https://example.com)");
  });
});

describe("链接载入契约：裸 URL / 显式链接均还原为 link 行内内容", () => {
  it("裸 URL 独占段还原为 link（URL 在片段开头）", async () => {
    const blocks = await mdToBlocks(editor, "https://example.com");
    expectLink(blocks, "https://example.com", "https://example.com");
  });

  it("裸 URL 粘在 CJK 文本后还原为 link（「链https://…」场景）", async () => {
    const blocks = await mdToBlocks(editor, "前文https://example.com");
    // 前文保持普通文字；URL 拆成 link
    const para = blocks.find((b: any) => b?.type === "paragraph");
    const types = (para.content ?? []).map((c: any) => c.type);
    expect(types).toContain("link");
    const link = (para.content ?? []).find((c: any) => c?.type === "link");
    expect(link.href).toBe("https://example.com");
    expect(link.content?.[0]?.text).toBe("https://example.com");
    expect((para.content ?? []).some((c: any) => c.type === "text" && c.text === "前文")).toBe(true);
  });

  it("裸 URL 粘在半角冒号后还原为 link（「链接1:https://…」真机故障场景）", async () => {
    const blocks = await mdToBlocks(editor, "链接1:https://www.baidu.com");
    // 「链接1:」保持普通文字；URL 拆成 link
    const para = blocks.find((b: any) => b?.type === "paragraph");
    const link = (para.content ?? []).find((c: any) => c?.type === "link");
    expect(link).toBeDefined();
    expect(link.href).toBe("https://www.baidu.com");
    expect(link.content?.[0]?.text).toBe("https://www.baidu.com");
    expect(
      (para.content ?? []).some((c: any) => c.type === "text" && c.text === "链接1:"),
    ).toBe(true);
  });

  it("裸 URL 粘在全角括号后还原为 link（「（https://…」标点前缀）", async () => {
    const blocks = await mdToBlocks(editor, "（https://example.com）参考");
    const para = blocks.find((b: any) => b?.type === "paragraph");
    const link = (para.content ?? []).find((c: any) => c?.type === "link");
    expect(link).toBeDefined();
    expect(link.href).toBe("https://example.com");
    // 全角括号与「参考」保持在 link 之外
    expect((para.content ?? []).some((c: any) => c.type === "text" && c.text.includes("）参考"))).toBe(true);
  });

  it("裸 URL 粘在英文字母后也拆（foohttps:// → foo + link，终版无前置边界）", async () => {
    const blocks = await mdToBlocks(editor, "foohttps://example.com");
    const para = blocks.find((b: any) => b?.type === "paragraph");
    const link = (para.content ?? []).find((c: any) => c?.type === "link");
    expect(link).toBeDefined();
    expect(link.href).toBe("https://example.com");
    expect((para.content ?? []).some((c: any) => c.type === "text" && c.text === "foo")).toBe(true);
  });

  it("裸 URL 粘在数字后也拆（v2https:// → v2 + link）", async () => {
    const blocks = await mdToBlocks(editor, "v2https://example.com");
    const para = blocks.find((b: any) => b?.type === "paragraph");
    const link = (para.content ?? []).find((c: any) => c?.type === "link");
    expect(link).toBeDefined();
    expect(link.href).toBe("https://example.com");
    expect((para.content ?? []).some((c: any) => c.type === "text" && c.text === "v2")).toBe(true);
  });

  it("显式 [text](url) 还原为 link（显示文本 ≠ href）", async () => {
    const blocks = await mdToBlocks(editor, "[显示文字](https://example.com)");
    expectLink(blocks, "https://example.com", "显示文字");
  });

  it("URL 尾部紧跟中文不把中文吞进链接（CJK 排除字符集）", async () => {
    const blocks = await mdToBlocks(editor, "见https://example.com，很有用");
    const para = blocks.find((b: any) => b?.type === "paragraph");
    const link = (para.content ?? []).find((c: any) => c?.type === "link");
    expect(link).toBeDefined();
    expect(link.href).toBe("https://example.com");
    // 中文标点「，很有用」保持在 link 之外的普通文字里
    expect((para.content ?? []).some((c: any) => c.type === "text" && c.text.includes("，很有用"))).toBe(true);
  });
});

describe("链接端到端契约：插入 → 保存 → 载入（真机完整路径）", () => {
  it("独立链接段：blocks → md → blocks 链接保留", async () => {
    const md = blocksToMd(editor, [SELF_LINK_BLOCK]);
    const blocks = await mdToBlocks(editor, md);
    expectLink(blocks, "https://example.com", "https://example.com");
  });

  it("前文+链接段：blocks → md → blocks 链接保留（本轮故障场景）", async () => {
    const blocksIn: any[] = [
      {
        type: "paragraph",
        content: [
          { type: "text", text: "前文", styles: {} },
          {
            type: "link",
            href: "https://example.com",
            content: [{ type: "text", text: "https://example.com", styles: {} }],
          },
        ],
      },
    ];
    const md = blocksToMd(editor, blocksIn);
    const blocks = await mdToBlocks(editor, md);
    const para = blocks.find((b: any) => b?.type === "paragraph");
    // 前文 + link 共存，与插入时形态一致
    expect((para.content ?? []).some((c: any) => c.type === "text" && c.text === "前文")).toBe(true);
    const link = (para.content ?? []).find((c: any) => c?.type === "link");
    expect(link?.href).toBe("https://example.com");
  });

  it("「链接1:」后插链接：blocks → md → blocks 链接保留（真机故障端到端）", async () => {
    // 真机复现形态：光标停在「链接1:」后半角冒号处插入链接
    const blocksIn: any[] = [
      {
        type: "paragraph",
        content: [
          { type: "text", text: "链接1:", styles: {} },
          {
            type: "link",
            href: "https://www.baidu.com",
            content: [{ type: "text", text: "https://www.baidu.com", styles: {} }],
          },
        ],
      },
    ];
    const md = blocksToMd(editor, blocksIn);
    expect(md.trim()).toBe("链接1:https://www.baidu.com");
    const blocks = await mdToBlocks(editor, md);
    const para = blocks.find((b: any) => b?.type === "paragraph");
    expect(
      (para.content ?? []).some((c: any) => c.type === "text" && c.text === "链接1:"),
    ).toBe(true);
    const link = (para.content ?? []).find((c: any) => c?.type === "link");
    expect(link?.href).toBe("https://www.baidu.com");
    expect(link?.content?.[0]?.text).toBe("https://www.baidu.com");
  });
});
