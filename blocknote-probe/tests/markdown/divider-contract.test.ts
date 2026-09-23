import { describe, it, expect, beforeAll } from "vitest";
import { BlockNoteEditor } from "@blocknote/core";
import { mdToBlocks, blocksToMd } from "../../src/editor/markdown/converter";
import { editorSchema } from "../../src/editor/schema";

/**
 * 分割线 markdown 载体契约（v2026-09-23 新增，同日补载入侧）。
 *
 * **背景（真机故障，两轮）**：
 * ① 导出侧：WebView 保存的 solid 分割线经官方导出器产出 `***`，而宿主侧
 *    `BodyBlocksController.DIVIDER_MD` 定义的载体**只有 `---` 系列**且判定是精确等值
 *    —— 于是存库后详情页不画线、重进编辑页被还原成普通文本。
 * ② 载入侧：导出归一成 `---` 后，详情页恢复正常，但**重进编辑页实线仍消失**
 *    （虚线/波浪都在）。根因是裸 `---` 曾被直接交给官方
 *    `tryParseMarkdownToBlocks`，而本项目在 schema 里同名覆盖了 divider
 *    （`StyledDividerBlock`，多 `style` prop、`content: "none"`）——
 *    官方 markdown tokenizer 按内置 `thematicBreak` 找不到可落地的块类型，
 *    **静默丢弃整行**（不建块、不留字面文本）。同一份 markdown 在**官方 schema**
 *    下能正常产出 divider，这就是"官方能、我们不能"的分水岭。
 *
 * **本文件的作用**：把"WebView 存出的分割线载体必须是 `---` 系列"与
 * "载入时三种样式（含裸 `---`）都必须还原成 divider 块"钉死为契约，
 * 与 Compose 侧 `isDividerMarkdown` / `parseDividerStyle` 的判据严格对齐。
 *
 * ⚠️ 修改 `blocksToMd` / `mdToBlocks` 的分割线分支时，务必同步 Compose 侧
 * `app/.../inspiration/components/BodyBlocksController.kt` 的 `DIVIDER_MD*` 常量。
 */
let editor: any;

/** 与 Compose 侧 DIVIDER_MD / DIVIDER_MD_DASHED / DIVIDER_MD_WAVY 一一对应 */
const COMPOSE_ACCEPTED = ["---", "--- dashed", "--- wavy"];

beforeAll(async () => {
  editor = BlockNoteEditor.create();
});

describe("分割线载体契约：存出的必须是 Compose 能认的 `---` 系列", () => {
  it("solid（默认实线）：导出 `---` 而非官方的 `***`", () => {
    const blocks: any[] = [
      { type: "paragraph", content: [{ type: "text", text: "上", styles: {} }] },
      { type: "divider", props: { style: "solid" } },
      { type: "paragraph", content: [{ type: "text", text: "下", styles: {} }] },
    ];
    const md = blocksToMd(editor, blocks);
    expect(md).not.toContain("***");
    expect(md.split("\n").map((l: string) => l.trim())).toContain("---");
  });

  it("props 为空对象时也按 solid 处理（真机 insertBlocks 产物形态）", () => {
    const blocks: any[] = [
      { type: "paragraph", content: [{ type: "text", text: "上", styles: {} }] },
      { type: "divider", props: {} },
      { type: "paragraph", content: [{ type: "text", text: "下", styles: {} }] },
    ];
    const md = blocksToMd(editor, blocks);
    expect(md).not.toContain("***");
  });

  it("三种样式存出的载体段全部落在 Compose 白名单内", () => {
    for (const style of ["solid", "dashed", "wavy"]) {
      const blocks: any[] = [
        { type: "paragraph", content: [{ type: "text", text: "上", styles: {} }] },
        { type: "divider", props: { style } },
        { type: "paragraph", content: [{ type: "text", text: "下", styles: {} }] },
      ];
      const md = blocksToMd(editor, blocks);
      // 抽出"非空且非上下正文"的那一段，即分割线载体
      const seg = md
        .split("\n")
        .map((l: string) => l.trim())
        .filter((l: string) => l !== "" && l !== "上" && l !== "下");
      expect(seg).toHaveLength(1);
      expect(COMPOSE_ACCEPTED).toContain(seg[0]);
    }
  });

  it("连续两条分割线：导出两段独立 `---`（各自独占段）", () => {
    const blocks: any[] = [
      { type: "divider", props: { style: "solid" } },
      { type: "divider", props: { style: "solid" } },
    ];
    const md = blocksToMd(editor, blocks);
    expect(md).not.toContain("***");
    const segs = md.split("\n").map((l: string) => l.trim()).filter(Boolean);
    expect(segs).toEqual(["---", "---"]);
  });

  it("正文里的 `***` 粗体/强调不被误翻转成 `---`", () => {
    const blocks: any[] = [
      {
        type: "paragraph",
        content: [
          { type: "text", text: "加粗", styles: { bold: true } },
          { type: "text", text: "普通文本", styles: {} },
        ],
      },
    ];
    const md = blocksToMd(editor, blocks);
    // 行内粗体是 `**加粗**`，不应命中整行别名正则（该正则要求整行仅由 * 构成）
    expect(md).toContain("**加粗**");
    expect(md).not.toContain("---");
  });

  it("窄边界：三个以上符号（****、____）也归一为 `---`", async () => {
    const blocks: any[] = [
      { type: "paragraph", content: [{ type: "text", text: "上", styles: {} }] },
      { type: "divider", props: { style: "solid" } },
      { type: "paragraph", content: [{ type: "text", text: "下", styles: {} }] },
    ];
    const md = blocksToMd(editor, blocks);
    expect(md).not.toMatch(/\*{3,}/);
    expect(md).not.toMatch(/_{3,}/);
  });
});

describe("分割线载体契约：往返后语义等价", () => {
  it("solid 保存 → 回读：divider 存活且样式为 solid", async () => {
    const blocks: any[] = [
      { type: "paragraph", content: [{ type: "text", text: "上", styles: {} }] },
      { type: "divider", props: { style: "solid" } },
      { type: "paragraph", content: [{ type: "text", text: "下", styles: {} }] },
    ];
    const md = blocksToMd(editor, blocks);
    const back = await mdToBlocks(editor, md);
    const d = back.filter((b) => b.type === "divider");
    expect(d).toHaveLength(1);
    expect((d[0].props as any).style ?? "solid").toBe("solid");
  });

  it("真机路径复现：insertBlocks 插入 → 保存 → 导出不含 `***`", () => {
    const ed = BlockNoteEditor.create();
    const first = ed.document[0];
    ed.insertBlocks([{ type: "divider" } as any], first, "after");
    const md = blocksToMd(ed, ed.document);
    // 修复前此处是 `***`，正是详情页不显示 / 重进编辑页消失的根因
    expect(md).not.toContain("***");
    expect(md.split("\n").map((l: string) => l.trim()).filter(Boolean)).toContain("---");
  });
});

/**
 * 载入侧契约（v2026-09-23 补）。
 *
 * **背景（第二轮真机故障）**：导出侧归一 `***` → `---` 修复了"详情页不画线"，
 * 但用户随即反馈**详情页正常、重进编辑页实线却消失**（虚线/波浪线都在）。
 * 根因在载入侧：裸 `---` 此前直接交给官方 `tryParseMarkdownToBlocks`，而本项目的
 * divider 是**自定义块**（多 `props.style`），官方 markdown tokenizer 只认识自己的
 * `thematicBreak` —— 会把它当**已消费但不产出块**处理，既不生成 divider 块、
 * 也不留字面文本 ⇒ 该行彻底消失。虚线/波浪走 token 路径，故唯独实线暴露。
 *
 * 真实数据（真机数据库 id=14「测试分割线」）：
 *   `实线分割线\n\n---\n\n虚线\n\n--- dashed\n\n波浪线\n\n--- wavy`
 */
describe("分割线载入契约：三种样式的裸载体都要还原成 divider 块", () => {
  it("裸 `---` 载入 → 生成 solid divider 块（修复前该行彻底消失）", async () => {
    const blocks = await mdToBlocks(editor, "上\n\n---\n\n下");
    const d = blocks.filter((b: any) => b.type === "divider");
    expect(d).toHaveLength(1);
    expect((d[0].props as any).style ?? "solid").toBe("solid");
  });

  it("真机数据整体回读：三种样式各存活一个 divider 块", async () => {
    const md = "实线分割线\n\n---\n\n虚线\n\n--- dashed\n\n波浪线\n\n--- wavy";
    const blocks = await mdToBlocks(editor, md);
    const styles = blocks
      .filter((b: any) => b.type === "divider")
      .map((b: any) => (b.props as any)?.style ?? "solid");
    expect(styles).toEqual(["solid", "dashed", "wavy"]);
  });

  it("别名载入：裸 `***` / `___`（历史/手输数据）同样还原为 solid divider", async () => {
    for (const alias of ["***", "*****", "___", "_____"]) {
      const blocks = await mdToBlocks(editor, `上\n\n${alias}\n\n下`);
      const d = blocks.filter((b: any) => b.type === "divider");
      expect(d, `别名 ${alias} 未还原`).toHaveLength(1);
      expect((d[0].props as any).style ?? "solid").toBe("solid");
    }
  });

  it("载入不吞掉正文：`---` 前后的段落块原样保留", async () => {
    const blocks = await mdToBlocks(editor, "上\n\n---\n\n下");
    const texts = blocks
      .filter((b: any) => b.type === "paragraph")
      .map((b: any) => (b.content ?? []).map((c: any) => c.text).join(""));
    expect(texts).toEqual(["上", "下"]);
  });

  it("行内粗体不被误判成分割线载体", async () => {
    const blocks = await mdToBlocks(editor, "**加粗**普通文本");
    expect(blocks.filter((b: any) => b.type === "divider")).toHaveLength(0);
  });

  it("连续两条裸 `---`：各自还原为独立 divider 块", async () => {
    const blocks = await mdToBlocks(editor, "---\n\n---");
    expect(blocks.filter((b: any) => b.type === "divider")).toHaveLength(2);
  });
});

describe("分割线两侧归一的一致性（导出 ↔ 载入 必须成对）", () => {
  it("三种样式的完整往返：块 → md → 块 无损", async () => {
    const blocks: any[] = [];
    for (const style of ["solid", "dashed", "wavy"]) {
      blocks.push({ type: "paragraph", content: [{ type: "text", text: `段${style}`, styles: {} }] });
      blocks.push({ type: "divider", props: { style } });
    }
    const md = blocksToMd(editor, blocks);
    const back = await mdToBlocks(editor, md);
    const styles = back
      .filter((b: any) => b.type === "divider")
      .map((b: any) => (b.props as any)?.style ?? "solid");
    expect(styles).toEqual(["solid", "dashed", "wavy"]);
  });

  it("只归一导出侧不归一载入侧会丢线（回归护栏）", async () => {
    // 模拟"库里存的就是裸 ---"（旧数据 / 其它写入路径），载入侧必须自认
    const viaBare = await mdToBlocks(editor, "上\n\n---\n\n下");
    // 模拟"走了导出管线"的等价输入
    const viaExport = await mdToBlocks(editor, blocksToMd(editor, [
      { type: "paragraph", content: [{ type: "text", text: "上", styles: {} }] },
      { type: "divider", props: { style: "solid" } },
      { type: "paragraph", content: [{ type: "text", text: "下", styles: {} }] },
    ]));
    const count = (bs: any[]) => bs.filter((b: any) => b.type === "divider").length;
    expect(count(viaBare)).toBe(count(viaExport));
  });
});

/**
 * 根因固化（v2026-09-23）：**证明裸 `---` 在项目 schema 下会被官方 parser 丢弃**。
 *
 * 这一条是上一条修复的"前提断言"——若将来 BlockNote 升级后官方 parser 能在
 * 自定义 divider type 上正确落地 thematic break，本用例会失败，
 * 提示可以撤掉 `mdToBlocks` ① 的人工归一（少一层特判）。
 */
describe("根因固化：项目 schema 与官方 schema 对裸 `---` 的行为差异", () => {
  it("项目 schema 下官方 parser 丢弃 divider，官方 schema 下保留", async () => {
    const md = "上\n\n---\n\n下";
    const projectEd = BlockNoteEditor.create({ schema: editorSchema });
    const defaultEd = BlockNoteEditor.create();

    const projectTypes = (await projectEd.tryParseMarkdownToBlocks(md)).map((b: any) => b.type);
    const defaultTypes = (await defaultEd.tryParseMarkdownToBlocks(md)).map((b: any) => b.type);

    // 官方 schema：divider 存活
    expect(defaultTypes).toContain("divider");
    // 项目 schema：官方路径**不产出** divider —— 这正是必须自行归一的理由
    expect(projectTypes).not.toContain("divider");
  });
});
