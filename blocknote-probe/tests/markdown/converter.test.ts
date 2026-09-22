import { describe, it, expect, beforeAll } from "vitest";
import { BlockNoteEditor } from "@blocknote/core";
import { mdToBlocks, blocksToMd, EMPTY_BLOCK_PLACEHOLDER } from "../../src/editor/markdown/converter";

/** 用户指定的主样本：一段关于秋天的描述（含标题/列表/任务/粗体/引用/分割线三样式） */
const AUTUMN_MD = `# 秋日短笺

清晨推开窗，桂花香先一步涌进来，**深秋就这样毫无预兆地到了**。院里的银杏一夜之间黄透，风一过，叶子像被谁撒下的金箔，铺满整条小径。

- 上午：整理相册，挑出去年秋天的照片
- 下午：去郊外看红叶
  - 带上新的广角镜头
  - 记得穿防滑的鞋

- [x] 把落叶扫进花坛
- [ ] 给远方的朋友寄一张明信片

---

*** wavy

--- dashed

午后的阳光变得很低，斜斜地穿过纱帘，在木地板上投下一格一格的光斑。猫睡在光斑里，尾巴偶尔动一动，像在替这个季节打着拍子。

> 秋天不是结束，而是一种温柔的收藏。
`;

let editor: any;

beforeAll(async () => {
  editor = BlockNoteEditor.create();
});

describe("markdown 转换层：mdToBlocks", () => {
  it("秋天描述样本可解析且块数合理", async () => {
    const blocks = await mdToBlocks(editor, AUTUMN_MD);
    expect(blocks.length).toBeGreaterThan(8);
  });

  it("标题块解析（heading level 1）", async () => {
    const blocks = await mdToBlocks(editor, AUTUMN_MD);
    const heading = blocks.find((b) => b.type === "heading");
    expect(heading).toBeTruthy();
    expect(heading.props.level).toBe(1);
  });

  it("任务项勾选态保留（一勾一未勾）", async () => {
    const blocks = await mdToBlocks(editor, AUTUMN_MD);
    const checks = blocks.filter((b) => b.type === "checkListItem");
    expect(checks).toHaveLength(2);
    expect(checks[0].props.checked).toBe(true);
    expect(checks[1].props.checked).toBe(false);
  });

  it("分割线：solid + 样式 wavy/dashed 全部就位（S10 同名覆盖统一类型）", async () => {
    const blocks = await mdToBlocks(editor, AUTUMN_MD);
    const dividers = blocks.filter((b) => b.type === "divider");
    // 三条分割线：裸 `---`（solid 默认）+ wavy + dashed
    expect(dividers).toHaveLength(3);
    const styles = dividers.map((b) => (b.props as any).style ?? "solid").sort();
    expect(styles).toEqual(["dashed", "solid", "wavy"]);
  });

  it("图片：本地路径包装 file:// URL（WebView 可加载）", async () => {
    const md = "![配图](/data/user/0/com.corgimemo.app/files/imgs/秋景.png)";
    const blocks = await mdToBlocks(editor, md);
    const img = blocks.find((b) => b.type === "image");
    expect(img?.props?.url).toBe(
      "file:///data/user/0/com.corgimemo.app/files/imgs/%E7%A7%8B%E6%99%AF.png"
    );
    // 保存时剥离还原
    const mdBack = blocksToMd(editor, blocks);
    expect(mdBack).toContain("(/data/user/0/com.corgimemo.app/files/imgs/秋景.png)");
  });

  it("引用块解析", async () => {
    const blocks = await mdToBlocks(editor, AUTUMN_MD);
    expect(blocks.some((b) => b.type === "quote")).toBe(true);
  });

  it("正文关键词保留（桂花香/金箔）", async () => {
    const blocks = await mdToBlocks(editor, AUTUMN_MD);
    const allText = JSON.stringify(blocks);
    expect(allText).toContain("桂花香");
    expect(allText).toContain("金箔");
  });

  it("载体空块占位行（纯 NBSP 行）被剥离", async () => {
    const md = `第一段\n${EMPTY_BLOCK_PLACEHOLDER}\n第二段`;
    const blocks = await mdToBlocks(editor, md);
    const nbSpBlock = blocks.find((b) => JSON.stringify(b).includes("\u00A0"));
    expect(nbSpBlock).toBeUndefined();
  });
});

describe("markdown 转换层：round-trip 往返", () => {
  it("秋天样本：保存→回读，分割线样式与任务勾选语义等价", async () => {
    const blocks1 = await mdToBlocks(editor, AUTUMN_MD);
    const md2 = blocksToMd(editor, blocks1);
    const blocks2 = await mdToBlocks(editor, md2);

    // 分割线样式集合等价（S10 统一类型：divider + props.style）
    const styleSig = (bs: any[]) =>
      bs
        .filter((b) => b.type === "divider")
        .map((b) => ((b.props as any).style ?? "solid"))
        .sort();
    expect(styleSig(blocks2)).toEqual(styleSig(blocks1));

    // 任务勾选等价
    const checks2 = blocks2.filter((b) => b.type === "checkListItem").map((b) => b.props.checked);
    expect(checks2).toEqual([true, false]);

    // 正文关键词仍在
    expect(JSON.stringify(blocks2)).toContain("桂花香");
  });

  it("空文档：不抛错", async () => {
    const blocks = await mdToBlocks(editor, "");
    expect(Array.isArray(blocks)).toBe(true);
  });
});

describe("markdown 转换层：链接往返（裸 URL autolink，v2026-09-22）", () => {
  /**
   * 背景：官方导出 `formatLink` 对「显示文本 == URL」的链接**有意退化为裸 URL**
   * （BlockNote#2661），而官方 markdown 解析器没有 autolink ——
   * "未选中文字插入链接"保存重进后编辑页链接丢失（详情页走 GFM_AUTOLINK 不受影响）。
   * 修复：mdToBlocks 后处理在块模型层面把裸 URL 还原成 link 行内内容。
   */
  it("裸 URL（官方退化产物）载入还原为 link", async () => {
    const blocks = await mdToBlocks(editor, "https://a.com/b");
    const link = blocks[0]?.content?.find((x: any) => x.type === "link");
    expect(link?.href).toBe("https://a.com/b");
    expect(link?.content?.[0]?.text).toBe("https://a.com/b");
  });

  it("裸 URL 后跟中文标点：标点留在链接外", async () => {
    const blocks = await mdToBlocks(editor, "看这个 https://a.com/b，很有用");
    const content = blocks[0]?.content ?? [];
    const linkIdx = content.findIndex((x: any) => x.type === "link");
    expect(linkIdx).toBeGreaterThan(0); // 前面有"看这个 "文本
    expect(content[linkIdx]?.href).toBe("https://a.com/b");
    const tail = content[content.length - 1];
    expect(tail?.type).toBe("text");
    expect((tail?.text as string).startsWith("，")).toBe(true);
  });

  it("正文中间的裸 URL 也能识别（前置边界=空白）", async () => {
    const blocks = await mdToBlocks(editor, "先看 https://a.com/x 再说");
    const link = blocks[0]?.content?.find((x: any) => x.type === "link");
    expect(link?.href).toBe("https://a.com/x");
  });

  it("显式标题链接不受 autolink 影响（不重复拆分）", async () => {
    const blocks = await mdToBlocks(editor, "[点这里](https://a.com/b)");
    const link = blocks[0]?.content?.find((x: any) => x.type === "link");
    expect(link?.href).toBe("https://a.com/b");
    expect(link?.content?.[0]?.text).toBe("点这里");
  });

  it("完整往返幂等：载入→导出（退化裸 URL）→再载入，链接不丢", async () => {
    const blocks1 = await mdToBlocks(editor, "https://a.com/b");
    // 官方 formatLink：text==href → 导出为裸 URL（不含 ]( 结构）
    const md2 = blocksToMd(editor, blocks1);
    expect(md2).not.toContain("](");
    // 再载入：链接仍在（修复前这一步丢链接）
    const blocks2 = await mdToBlocks(editor, md2);
    const link = blocks2[0]?.content?.find((x: any) => x.type === "link");
    expect(link?.href).toBe("https://a.com/b");
  });
});
