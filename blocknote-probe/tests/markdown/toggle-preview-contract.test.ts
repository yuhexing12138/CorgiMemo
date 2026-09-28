/**
 * 图片 Toggle preview 往返契约（v2026-09-28 新增）。
 *
 * **背景（真机故障）**：「图片编辑工具条点 Toggle preview 切到文件形态后，
 * 文件名与格式不显示（只剩图标）」。
 *
 * **根因**：宿主底栏「插入图片」命令（EditorApp 的 insertImage）创建 image 块
 * 时只传 `url`，`name` prop 恒为默认空串；而官方文件形态渲染
 * （createFileBlockWrapper → createFileNameWithIcon）直接读 `block.props.name`
 * ——空串即空白文件名。同文件 insertFile 分支早已带 name（`path.split("/").pop()`），
 * image 分支漏配。
 *
 * **修复**（两处 converter 配套 + 一处命令链）：
 * 1. insertImage 命令补 `name`（本地路径 basename，与 insertFile 同口径）；
 * 2. 载入侧 `normalizeImageUrls` 对 name 为空的**存量**块从 url 解码提取 basename 兜底；
 * 3. 导出侧 `blocksToMd` 统一 `showPreview: true`——官方对文件形态（showPreview=false）
 *    导出 `<a>` 链接语法（`[name](path)`），载入后图片块降级为 paragraph（**丢图**，
 *    本文件 v1 观察测试实测）。showPreview 状态不持久化：Toggle preview 是编辑器内
 *    临时预览切换，保存即回到图片形态。
 *
 * **契约**（本文件断言）：
 * - 预览态（showPreview=true）带 name：导出 `![name](path)`，载入还原 name
 *   （官方 parseImageElement: alt → name）；
 * - 存量形态（name 为空）：载入兜底出 basename，下次保存写回 `![name](path)`；
 * - 文件形态（showPreview=false）：导出与预览态同形（`![name](path)`），
 *   载入还原 image 块（丢图回归固化）。
 */
import { describe, it, expect, beforeAll } from "vitest";
import { BlockNoteEditor } from "@blocknote/core";
import { mdToBlocks, blocksToMd } from "../../src/editor/markdown/converter";
import { editorSchema } from "../../src/editor/schema";

let editor: any;

beforeAll(async () => {
  editor = BlockNoteEditor.create({ schema: editorSchema });
});

const LOCAL_PATH = "/data/user/0/com.corgimemo.app/files/insp/IMG_0928.jpg";

/** 带 name 的 image 块（修复后的插入形态） */
const NAMED_IMAGE = {
  type: "image",
  props: { url: LOCAL_PATH, name: "IMG_0928.jpg" },
};

/** 不带 name 的 image 块（存量数据形态，name 走默认空串） */
const UNNAMED_IMAGE = {
  type: "image",
  props: { url: LOCAL_PATH },
};

describe("图片导出契约：name → markdown alt（Toggle preview 修复的前置）", () => {
  it("带 name 的预览态 image 导出 ![name](path)", () => {
    const md = blocksToMd(editor, [NAMED_IMAGE]);
    expect(md.trim()).toBe(`![IMG_0928.jpg](${LOCAL_PATH})`);
  });

  it("不带 name 的存量 image 维持 ![](path)（空 alt）", () => {
    const md = blocksToMd(editor, [UNNAMED_IMAGE]);
    expect(md.trim()).toBe(`![](${LOCAL_PATH})`);
  });

  it("文件形态（showPreview=false）导出与预览态同形——状态不持久化，不落链接语法", () => {
    const md = blocksToMd(editor, [
      { type: "image", props: { url: LOCAL_PATH, name: "IMG_0928.jpg", showPreview: false } },
    ]);
    // 修复前实测：官方 <a> 形态落成 [IMG_0928.jpg](path)（丢图源头）
    expect(md.trim()).toBe(`![IMG_0928.jpg](${LOCAL_PATH})`);
  });
});

describe("图片载入契约：markdown alt → name（往返闭环）", () => {
  it("![name](path) 载入还原 name prop（官方 parseImageElement: alt → name）", async () => {
    const blocks = await mdToBlocks(editor, `![IMG_0928.jpg](${LOCAL_PATH})`);
    const img = blocks.find((b: any) => b?.type === "image");
    expect(img).toBeDefined();
    expect(img.props.name).toBe("IMG_0928.jpg");
    // 载入侧 normalizeImageUrls 会把 url 包装成 file:// 形态（真实链路行为），
    // 保存时 restoreImageUrls 再剥回本地路径——file:// 即正确预期
    expect(img.props.url).toBe(`file://${LOCAL_PATH}`);
  });

  it("存量块兜底：![](path) 载入后 name 补为 url 的 basename（历史数据自愈）", async () => {
    const blocks = await mdToBlocks(editor, `![](${LOCAL_PATH})`);
    const img = blocks.find((b: any) => b?.type === "image");
    expect(img).toBeDefined();
    expect(img.props.name).toBe("IMG_0928.jpg");
  });
});

describe("丢图回归固化：文件形态保存 → 重进编辑页", () => {
  it("showPreview=false 的块导出/载入往返后仍为 image 块（修复前还原为 paragraph）", async () => {
    const md = blocksToMd(editor, [
      { type: "image", props: { url: LOCAL_PATH, name: "IMG_0928.jpg", showPreview: false } },
    ]);
    const blocks = await mdToBlocks(editor, md);
    const img = blocks.find((b: any) => b?.type === "image");
    expect(img).toBeDefined();
    expect(img.props.name).toBe("IMG_0928.jpg");
  });
});
