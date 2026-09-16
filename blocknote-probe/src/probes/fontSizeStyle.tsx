import { createReactStyleSpec } from "@blocknote/react";

/**
 * 探针 2：字号自定义行内样式。
 * 验证目标：Custom Styles API 能否注册进 schema、正确渲染 DOM、随文档 JSON 序列化。
 * v0.52 注意：style 的 propSchema 是单值类型（"boolean" | "string"），
 * render 拿 { value, contentRef, editor }——children 由 core 渲染进 contentRef 指向的元素。
 */
export const fontSizeStyle = createReactStyleSpec(
  {
    type: "fontSize",
    propSchema: "string",
  },
  {
    render: (props) => (
      <span ref={props.contentRef} style={{ fontSize: props.value }} />
    ),
  }
);
