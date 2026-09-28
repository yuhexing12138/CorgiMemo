/**
 * 图片 Toggle preview 往返契约（v2026-09-28 新增；同日第二轮改为持久化契约）。
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
 * **修复演进**：
 * - 第一轮：insertImage 补 name + 存量兜底；showPreview 统一置 true（不持久化）。
 * - 第二轮（用户需求：文件形态保存后编辑页要保持）：showPreview=false **持久化**，
 *   载体借用 figure 契约——导出侧 caption 尾部注入 `@@@CORGI_FILEVIEW@@@` token
 *   （官方按「caption 非空」走 figure HTML，形态与 v2026-09-28 figure 契约同构，
 *   下游 isImageSegment / saveInlineMediaBlocks / toPlainText 天然兼容）；
 *   载入侧 [restoreImageFileView] 剥 token 还原 showPreview=false。
 *   **禁止官方 `<a>` 形态**：showPreview=false 直接导出落成 `[name](path)` 链接
 *   语法，载入降级 paragraph（丢块，v1 实测），且与手打链接无法区分。
 *
 * **契约**（本文件断言）：
 * - 预览态（showPreview=true）带 name：导出 `![name](path)`，载入还原 name
 *   （官方 parseImageElement: alt → name）；
 * - 存量形态（name 为空）：载入兜底出 basename，下次保存写回 `![name](path)`；
 * - 文件形态（showPreview=false）：导出 figure HTML + figcaption 尾随 token；
 *   往返还原 image 块 + showPreview=false + caption 干净（纯 token → 空串；
 *   备注尾随 token → 备注还原）；预览态 caption 不受 token 污染。
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

describe("文件形态（showPreview=false）持久化契约（v2026-09-28 第二轮）", () => {
  it("导出：文件形态转 figure HTML，figcaption 尾部携带 CORGI_FILEVIEW token", () => {
    const md = blocksToMd(editor, [
      { type: "image", props: { url: LOCAL_PATH, name: "IMG_0928.jpg", showPreview: false } },
    ]);
    // 载体 = figure 契约形态（下游 isImageSegment / saveInlineMediaBlocks 已认）；
    // 禁止官方 <a> 形态（[name](path) 链接语法载入丢块，v1 实测）
    expect(md).toContain("<figure");
    expect(md).toContain(`alt="IMG_0928.jpg"`);
    expect(md).toContain("@@@CORGI_FILEVIEW@@@");
    expect(md).not.toContain("![");
  });

  it("往返：文件形态保存 → 重进编辑页还原 image 块 + showPreview=false + caption 干净", async () => {
    const md = blocksToMd(editor, [
      { type: "image", props: { url: LOCAL_PATH, name: "IMG_0928.jpg", showPreview: false } },
    ]);
    const blocks = await mdToBlocks(editor, md);
    const img = blocks.find((b: any) => b?.type === "image");
    expect(img).toBeDefined();
    expect(img.props.showPreview).toBe(false);
    expect(img.props.name).toBe("IMG_0928.jpg");
    // 纯 token figcaption → 剥离后 caption 为空（无备注的文件形态）
    expect(img.props.caption).toBe("");
  });

  it("备注 + 文件形态并存：caption 尾随 token，载入剥离后备注还原", async () => {
    const md = blocksToMd(editor, [
      {
        type: "image",
        props: { url: LOCAL_PATH, name: "IMG_0928.jpg", caption: "备注1", showPreview: false },
      },
    ]);
    expect(md).toContain("备注1@@@CORGI_FILEVIEW@@@");
    const blocks = await mdToBlocks(editor, md);
    const img = blocks.find((b: any) => b?.type === "image");
    expect(img).toBeDefined();
    expect(img.props.showPreview).toBe(false);
    expect(img.props.caption).toBe("备注1");
  });

  it("预览态不受污染：caption 不注 token，往返 showPreview 恒非 false", async () => {
    const md = blocksToMd(editor, [NAMED_IMAGE]);
    expect(md).not.toContain("@@@CORGI_FILEVIEW@@@");
    const blocks = await mdToBlocks(editor, md);
    const img = blocks.find((b: any) => b?.type === "image");
    expect(img.props.showPreview).not.toBe(false);
  });
});
