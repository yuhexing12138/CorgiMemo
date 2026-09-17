import { BlockNoteSchema, defaultBlockSpecs, defaultStyleSpecs } from "@blocknote/core";
import { StyledDividerBlock } from "../probes/dividerBlock";
import { fontSizeStyle } from "../probes/fontSizeStyle";

/**
 * 正式编辑器 schema（P1-S10 定稿）：
 * **同名覆盖内置 divider**（追加 style prop：solid/dashed/wavy）——类型统一后
 * `<hr>` 粘贴归属、`---` 回车 input rule、markdown `---` 往返全部保持官方语义。
 * 另有 fontSize 自定义行内样式（S8）。
 */
export const editorSchema = BlockNoteSchema.create({
  blockSpecs: {
    ...defaultBlockSpecs,
    divider: StyledDividerBlock,
  },
  styleSpecs: {
    ...defaultStyleSpecs,
    fontSize: fontSizeStyle,
  },
});

export type EditorSchema = typeof editorSchema;
