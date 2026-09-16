import { BlockNoteSchema, defaultBlockSpecs, defaultStyleSpecs } from "@blocknote/core";
import { DividerStyledBlock } from "./dividerBlock";
import { fontSizeStyle } from "./fontSizeStyle";

/**
 * 探针 schema：默认块/样式全集 + 自定义分割线块 + 字号行内样式。
 * 若 v0.52 的 schema 组装 API 与此不同，tsc 阶段会暴露（这本身就是探针结论之一）。
 */
export const probeSchema = BlockNoteSchema.create({
  blockSpecs: {
    ...defaultBlockSpecs,
    dividerStyled: DividerStyledBlock,
  },
  styleSpecs: {
    ...defaultStyleSpecs,
    fontSize: fontSizeStyle,
  },
});
