import { BlockNoteSchema, defaultBlockSpecs, defaultStyleSpecs } from "@blocknote/core";
import { DividerStyledBlock } from "../probes/dividerBlock";
import { fontSizeStyle } from "../probes/fontSizeStyle";

/**
 * 正式编辑器 schema（P0）：
 * 内置块/样式全集 + 三样式分割线自定义块 + 字号行内样式。
 * P1-S10 计划将 dividerStyled 收敛为 extendBlockSpec 扩展内置 divider。
 */
export const editorSchema = BlockNoteSchema.create({
  blockSpecs: {
    ...defaultBlockSpecs,
    dividerStyled: DividerStyledBlock,
  },
  styleSpecs: {
    ...defaultStyleSpecs,
    fontSize: fontSizeStyle,
  },
});

export type EditorSchema = typeof editorSchema;
