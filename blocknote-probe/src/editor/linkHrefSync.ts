/**
 * 自链接文字编辑同步扩展（v2026-09-24 新增）。
 *
 * **需求**：链接面板「只填 URL」插入的链接（显示文本 == href，下称**自链接**），
 * 用户在编辑器里直接编辑它的文字时，**href 要同步跟随显示文本**——
 * 用户把 `https://www.baidu.com` 改成 `https://百度www.baidu.com`，
 * 链接本身（href）也要变成 `https://百度www.baidu.com`。
 *
 * **为什么必须做**：轮十六已让「显示文本 == URL」的链接导出为裸 URL、重进
 * 自动还原。但用户在编辑器里改了显示文字后（显示 ≠ href），导出退化为
 * `[新文字](旧href)`——重进后链接指的还是旧地址，与用户「我在改这个链接」
 * 的预期相悖。
 *
 * **实现**：ProseMirror `appendTransaction` 插件（与官方 autolink 同机制）。
 * 每次文档变化后：
 * 1. 在**旧文档**的变化区间内（±1 字符，覆盖「光标在链接右缘打字」时
 *    changedRange 从链接末尾之后开始的情况）找 link mark 的 text 节点；
 * 2. 仅处理**自链接**（节点文本 === href）；
 * 3. 用 `transform.mapping` 把该节点区间**映射到新文档**；
 * 4. 在新文档里收集映射邻域（±1 字符）内**同 href** 的相邻 text 片段
 *    （打字时新字符可能继承 mark 而扩出旧边界），拼出链接全文；
 * 5. 全文非空且与 href 不同 → 重设该区间的 link mark（href = 新全文）。
 *
 * **已知的刻意取舍**：
 * - **多 text 节点的自链接不同步**：链接文字被拆成多个 text 节点（如局部带色）
 *   时，单节点文本 ≠ href，插件不会触发（安全降级为"编辑不跟随"）——
 *   纯 URL 链接绝大多数是单节点，此局限几乎不可见；
 * - **文本删空不同步**：映射后区间为空（链接文字被删光）时不动——
 *   link mark 随文本消失，链接自然解除，符合直觉；
 * - **href 允许任意字符串**（含中文等非合法 URL）：同步语义由用户自担，
 *   与「编辑文字 = 改链接」的直觉一致。
 *
 * **无循环**：appendTransaction 返回的 tr 不会被本插件再次处理（PM 一轮机制）；
 * 且下一轮 oldState 里 href 已更新，「自链接」身份由 `文本 === href` 天然延续。
 */
import { createExtension } from "@blocknote/core";
import { combineTransactionSteps, getChangedRanges } from "@tiptap/core";
import { Plugin, PluginKey } from "prosemirror-state";

/** 自链接文字与 href 同步的 PM 插件（实现见文件头注释） */
const linkTextHrefSyncPlugin = new Plugin({
  key: new PluginKey("linkTextHrefSync"),
  appendTransaction: (transactions, oldState, newState) => {
    if (!transactions.some((t) => t.docChanged)) return null;
    if (oldState.doc.eq(newState.doc)) return null;

    const linkType = newState.schema.marks.link;
    if (!linkType) return null;

    const transform = combineTransactionSteps(oldState.doc, [
      ...transactions,
    ]);
    const changes = getChangedRanges(transform);
    const tr = newState.tr;
    let touched = false;

    for (const { oldRange } of changes) {
      // 旧区间略扩 1 字符：覆盖「光标在链接右缘打字」时 changedRange
      // 恰从链接末尾之后开始、扫不到链接最后一个 text 节点的情况
      const scanFrom = Math.max(0, oldRange.from - 1);
      const scanTo = Math.min(oldState.doc.content.size, oldRange.to + 1);

      oldState.doc.nodesBetween(scanFrom, scanTo, (node, pos) => {
        if (!node.isText) return true;
        const linkMark = node.marks.find((m) => m.type === linkType);
        if (!linkMark) return true;
        const href = linkMark.attrs.href as string | undefined;
        if (typeof href !== "string" || href === "") return true;

        const tFrom = pos;
        const tTo = pos + node.nodeSize;
        // 仅处理自链接（显示文本 == href）
        const oldText = oldState.doc.textBetween(tFrom, tTo, undefined, "\uFFFC");
        if (oldText !== href) return true;

        // 映射到新文档位置
        const mappedFrom = transform.mapping.map(tFrom, -1);
        const mappedTo = transform.mapping.map(tTo, 1);
        // 文本被删光：link mark 随之消失，无需同步
        if (mappedFrom === mappedTo) return true;

        // 新文档里收集映射邻域内、同 href 的相邻 text 片段
        // （±1 字符：打字时新字符可能继承 mark 而位于映射边界之外）
        const segs: { from: number; to: number; text: string }[] = [];
        newState.doc.nodesBetween(
          Math.max(0, mappedFrom - 1),
          Math.min(newState.doc.content.size, mappedTo + 1),
          (n2, p2) => {
            if (!n2.isText) return true;
            const m2 = n2.marks.find(
              (mm) => mm.type === linkType && mm.attrs.href === href,
            );
            if (m2) segs.push({ from: p2, to: p2 + n2.nodeSize, text: n2.text ?? "" });
            return true;
          },
        );
        if (segs.length === 0) return true;

        const segFrom = segs[0].from;
        const segTo = segs[segs.length - 1].to;
        const newText = segs.map((s) => s.text).join("");
        // 空文本（不该发生，防御）或文本未变 → 不同步
        if (!newText || newText === href) return true;

        tr.removeMark(segFrom, segTo, linkType);
        tr.addMark(segFrom, segTo, linkType.create({ href: newText }));
        touched = true;
        return true;
      });
    }

    return touched ? tr : null;
  },
});

/**
 * 自链接文字编辑同步扩展。
 *
 * 用法：`useCreateBlockNote({ ..., extensions: [linkHrefSyncExtension] })`。
 * 无状态、无 options，按 `createExtension` 的对象重载直接声明。
 */
export const linkHrefSyncExtension = createExtension({
  key: "linkHrefSync",
  prosemirrorPlugins: [linkTextHrefSyncPlugin],
});
