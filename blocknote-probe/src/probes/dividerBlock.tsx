import { createReactBlockSpec } from "@blocknote/react";

/**
 * 探针 1：三样式分割线自定义块（solid / dashed / wavy）。
 * 验证目标：createReactBlockSpec 在 v0.52 的 propSchema / render / toExternalHTML / parse
 * 四个环节是否顺畅，对应本项目「分割线三样式」能力的迁移路径。
 * v0.52 注意：createReactBlockSpec 返回工厂函数，需调用一次得到 BlockSpec 再进 schema。
 */
export const DividerStyledBlock = createReactBlockSpec(
  {
    type: "dividerStyled",
    propSchema: {
      style: { default: "solid", values: ["solid", "dashed", "wavy"] },
    },
    content: "none",
  },
  {
    // 编辑态渲染：实线/虚线用 border，波浪用内联 SVG（与本项目 DividerLine 思路一致）
    render: (props) => {
      const style = props.block.props.style;
      if (style === "wavy") {
        return (
          <svg
            className="probe-divider"
            viewBox="0 0 400 8"
            preserveAspectRatio="none"
            height={8}
          >
            <path
              d="M0 4 Q 12.5 0 25 4 T 50 4 T 75 4 T 100 4 T 125 4 T 150 4 T 175 4 T 200 4 T 225 4 T 250 4 T 275 4 T 300 4 T 325 4 T 350 4 T 375 4 T 400 4"
              fill="none"
              stroke="currentColor"
              strokeWidth={1.5}
            />
          </svg>
        );
      }
      return <div className="probe-divider" data-style={style} />;
    },
    // 外部导出（复制/HTML 导出）：语义化 hr + data 属性携带样式
    toExternalHTML: (props) => (
      <hr data-divider-style={props.block.props.style} />
    ),
    // 粘贴/导入解析：仅当元素显式携带 data-divider-style 时命中（不抢占内置 <hr> 规则）
    parse: (element) => {
      const s = element.getAttribute?.("data-divider-style");
      if (s === "wavy" || s === "dashed") return { style: s };
      return undefined;
    },
  }
)();
