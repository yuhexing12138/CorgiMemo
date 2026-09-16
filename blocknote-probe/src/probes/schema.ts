import { BlockNoteSchema, defaultBlockSpecs, defaultStyleSpecs } from "@blocknote/core";

/** 探针 schema：与正式编辑器共用同一份 schema 定义（单一来源）。 */
export { editorSchema as probeSchema } from "../editor/schema";
// 保持 import 形态（避免 isolatedModules 对纯 re-export 的歧义）
void BlockNoteSchema;
void defaultBlockSpecs;
void defaultStyleSpecs;
