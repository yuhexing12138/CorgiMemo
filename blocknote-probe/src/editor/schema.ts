import {
  BlockNoteSchema,
  COLORS_DEFAULT,
  createStyleSpec,
  defaultBlockSpecs,
  defaultStyleSpecs,
} from "@blocknote/core";
import { StyledDividerBlock } from "../probes/dividerBlock";
import { fontSizeStyle } from "../probes/fontSizeStyle";

/* ===== 行内文字色 / 背景色的同名覆盖（v2026-09-22）===== */

/**
 * 颜色值归一：官方默认色名 → 对应色值；其余（hex / rgb / CSS 颜色名）原样返回。
 *
 * 与官方 `toExternalHTML` 的口径完全一致（`value in COLORS_DEFAULT ? … : value`），
 * 保证「传色名」和「传 hex」两种用法都能上色。
 *
 * @param name 样式值（BlockNote 预设色名，或任意 CSS 颜色值）
 * @param background true = 取背景色维度；false = 取文字色维度
 */
function resolveColorValue(name: string, background: boolean): string {
  const preset = COLORS_DEFAULT[name];
  if (preset) return background ? preset.background : preset.text;
  return name;
}

/**
 * 解析方向的颜色值归一（v2026-09-22）
 *
 * **为什么必须归一**：`parse` 只能读到浏览器**计算后**的 `element.style.color`——
 * 写进去的 `#FF9A5C` 读出来是 `rgb(255, 154, 92)`（官方 spec 同样如此）。
 * 若原样存入文档，本项目 converter 的行内色编码（只接受 `[0-9A-Za-z#]+`）
 * 会在**第二次保存时判为不可编码而跳过**，于是「第一次保存有颜色、重进再存就丢」
 * 这种极难排查的半失效。统一还原成大写 `#RRGGBB` 后，值形态在往返中保持稳定。
 *
 * 处理范围刻意收窄：
 * - `#RRGGBB` → 统一大写；
 * - `rgb(r, g, b)` 与不透明 `rgba(r, g, b, 1)` → 还原为 `#RRGGBB`；
 * - 其余（预设色名、CSS 颜色名、带透明度的 rgba 等）**原样保留**——
 *   宁可退化为"不参与编码"，也不做有损转换。
 *
 * @param raw `element.style.color` / `element.style.backgroundColor` 的读值
 */
function normalizeColorValue(raw: string): string {
  const value = raw.trim();
  if (/^#[0-9a-fA-F]{6}$/.test(value)) return value.toUpperCase();
  const rgb = /^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)$/.exec(value);
  if (rgb) {
    /** 带透明度且不足 1 时不还原（hex 三通道无法表达 alpha，转了就是有损） */
    if (rgb[4] !== undefined && Number(rgb[4]) < 1) return value;
    return (
      "#" +
      [rgb[1], rgb[2], rgb[3]]
        .map((channel) => Number(channel).toString(16).padStart(2, "0"))
        .join("")
        .toUpperCase()
    );
  }
  return value;
}

/**
 * 覆盖官方 `textColor` 行内样式
 *
 * **为什么必须覆盖**：官方 color 样式是 `createStyleSpec` 型（定义了 `addMarkView`），
 * 而 Tiptap 的 `EditorView` 会**无条件**启用 mark views（`@tiptap/core/src/Editor.ts`），
 * 于是编辑器内渲染走的是 spec 的 **`render()`**；偏偏官方 `render()` 只造一个**裸 span**、
 * 一个颜色都不设（颜色只写在 `toExternalHTML()` 里，而那一路只在导出/粘贴时用）。
 * 结果：颜色完全依赖 CSS 的 9 条预设色名规则
 * （`Block.css` 的 `[data-style-type="textColor"][data-value="gray"…"pink"]`），
 * 宿主 A 面板下发的**自由 hex**（如 `#FF9A5C`）没有任何规则命中
 * → mark 确实写进了文档，但屏幕上零变化（"点了没反应"）。
 *
 * **修法**：同名覆盖 spec，在 `render()` 里把值直接写进**内联 style**——
 * 与本项目 fontSize 样式（`probes/fontSizeStyle.tsx`）同一机制。
 * 同时保留官方 `parse`（`<span style="color:…">` → 值），
 * 使粘贴外来带色 HTML、以及本项目自编码的颜色标记仍能还原。
 *
 * ⚠️ 覆盖不是新增：`styleSpecs` 展开后同名 key 只留一份，不会产生重复 mark。
 * ⚠️ 只影响**行内**色；「段落文字色/背景色」走的是块 props
 * （`data-text-color` / `data-background-color`，由 Block.css 的
 * `[data-text-color=…]` 规则渲染），与本 spec 无关，不受影响。
 */
const projectTextColor = createStyleSpec(
  { type: "textColor", propSchema: "string" },
  {
    render: (value) => {
      const span = document.createElement("span");
      span.style.color = resolveColorValue(value, false);
      return { dom: span, contentDOM: span };
    },
    /** 导出/复制路径与 render 保持一致（官方此处也是内联 style） */
    toExternalHTML: (value) => {
      const span = document.createElement("span");
      span.style.color = resolveColorValue(value, false);
      return { dom: span, contentDOM: span };
    },
    /** 沿用官方口径（`<span style="color:…">` → 该颜色值），并做形态归一 */
    parse: (element) =>
      element.tagName === "SPAN" && element.style.color
        ? normalizeColorValue(element.style.color)
        : undefined,
  },
);

/**
 * 覆盖官方 `backgroundColor` 行内样式
 *
 * 与 {@link projectTextColor} 完全同因同法，只是维度换成 `style.backgroundColor`
 * 与 `COLORS_DEFAULT[name].background`。
 */
const projectBackgroundColor = createStyleSpec(
  { type: "backgroundColor", propSchema: "string" },
  {
    render: (value) => {
      const span = document.createElement("span");
      span.style.backgroundColor = resolveColorValue(value, true);
      return { dom: span, contentDOM: span };
    },
    toExternalHTML: (value) => {
      const span = document.createElement("span");
      span.style.backgroundColor = resolveColorValue(value, true);
      return { dom: span, contentDOM: span };
    },
    parse: (element) =>
      element.tagName === "SPAN" && element.style.backgroundColor
        ? normalizeColorValue(element.style.backgroundColor)
        : undefined,
  },
);

/**
 * 正式编辑器 schema（P1-S10 定稿；v2026-09-22 补行内色覆盖）：
 * **同名覆盖内置 divider**（追加 style prop：solid/dashed/wavy）——类型统一后
 * `<hr>` 粘贴归属、`---` 回车 input rule、markdown `---` 往返全部保持官方语义。
 * 另有 fontSize 自定义行内样式（S8）。
 *
 * v2026-09-22 新增两处同名覆盖：`textColor` / `backgroundColor`
 * ——官方版本的 mark view 渲染不上色，导致 A 面板的自由 hex 行内色完全不可见，
 * 详见 {@link projectTextColor} 的说明。
 */
export const editorSchema = BlockNoteSchema.create({
  blockSpecs: {
    ...defaultBlockSpecs,
    divider: StyledDividerBlock,
  },
  styleSpecs: {
    ...defaultStyleSpecs,
    fontSize: fontSizeStyle,
    textColor: projectTextColor,
    backgroundColor: projectBackgroundColor,
  },
});

export type EditorSchema = typeof editorSchema;
