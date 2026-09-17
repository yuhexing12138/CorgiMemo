import { createReactBlockSpec } from "@blocknote/react";
import { useEffect, useState } from "react";

/**
 * 三样式分割线（迁移 P1-S10 定稿）：
 * 以 **type: "divider" 覆盖内置 spec**（而非新建类型）——`<hr>` 粘贴归属、
 * `---` 回车 input rule、markdown 往返全部保持官方语义，仅追加 style prop。
 * - solid：官方 hr 细线；dashed：CSS 虚线；wavy：SVG 波浪（对齐 Compose 版 DividerLine）
 * - 点击弹样式工具条（三选 + 删除，P1-S10）
 */

type DividerStyle = "solid" | "dashed" | "wavy";

/** 分割线浮动工具条（样式三选 + 删除；点击分割线弹出，点外部关闭） */
function DividerToolbar(props: {
  x: number;
  y: number;
  current: DividerStyle;
  onStyle: (s: DividerStyle) => void;
  onDelete: () => void;
  onClose: () => void;
}) {
  // 点外部关闭（capture 在冒泡前拦截，避免先触发样式按钮的 onClick 又立即关闭）
  useEffect(() => {
    const onDocClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      if (!target.closest(".probe-divider-toolbar")) props.onClose();
    };
    // 延迟绑定：跳过打开工具条的那次点击
    const t = setTimeout(
      () => document.addEventListener("click", onDocClick, true),
      0
    );
    return () => {
      clearTimeout(t);
      document.removeEventListener("click", onDocClick, true);
    };
  });

  const styles: Array<{ key: DividerStyle; label: string }> = [
    { key: "solid", label: "─────" },
    { key: "dashed", label: "╌ ╌ ╌" },
    { key: "wavy", label: "〰〰〰" },
  ];

  return (
    <div
      className="probe-divider-toolbar"
      style={{
        position: "fixed",
        left: props.x,
        top: props.y,
        transform: "translate(-50%, -120%)",
        background: "#ffffff",
        border: "1px solid #ddd",
        borderRadius: 10,
        boxShadow: "0 4px 16px rgba(0,0,0,0.15)",
        display: "flex",
        gap: 4,
        padding: 6,
        zIndex: 10000,
      }}
    >
      {styles.map((s) => (
        <button
          key={s.key}
          onClick={() => {
            props.onStyle(s.key);
            props.onClose();
          }}
          style={{
            padding: "4px 10px",
            fontSize: 12,
            borderRadius: 6,
            border:
              s.key === props.current
                ? "1.5px solid var(--editor-primary, #1976d2)"
                : "1px solid #ddd",
            background: s.key === props.current ? "#eef4ff" : "#fff",
            color: "#333",
            cursor: "pointer",
          }}
        >
          {s.label}
        </button>
      ))}
      <button
        onClick={() => {
          props.onDelete();
          props.onClose();
        }}
        style={{
          padding: "4px 10px",
          fontSize: 12,
          borderRadius: 6,
          border: "1px solid #f3c2c2",
          background: "#fff2f2",
          color: "#c0392b",
          cursor: "pointer",
        }}
      >
        删除
      </button>
    </div>
  );
}

/** 分割线渲染（含点击工具条交互） */
function DividerRender(props: {
  style: DividerStyle;
  onUpdateStyle: (s: DividerStyle) => void;
  onDelete: () => void;
}) {
  const [toolbar, setToolbar] = useState<{ x: number; y: number } | null>(null);

  if (props.style === "wavy") {
    return (
      <div
        style={{ cursor: "pointer", padding: "2px 0" }}
        onClick={(e) => setToolbar({ x: e.clientX, y: e.clientY })}
      >
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
        {toolbar && (
          <DividerToolbar
            x={toolbar.x}
            y={toolbar.y}
            current={props.style}
            onStyle={props.onUpdateStyle}
            onDelete={props.onDelete}
            onClose={() => setToolbar(null)}
          />
        )}
      </div>
    );
  }

  return (
    <div
      style={{ cursor: "pointer", padding: "2px 0" }}
      onClick={(e) => setToolbar({ x: e.clientX, y: e.clientY })}
    >
      <div className="probe-divider" data-style={props.style} />
      {toolbar && (
        <DividerToolbar
          x={toolbar.x}
          y={toolbar.y}
          current={props.style}
          onStyle={props.onUpdateStyle}
          onDelete={props.onDelete}
          onClose={() => setToolbar(null)}
        />
      )}
    </div>
  );
}

export const StyledDividerBlock = createReactBlockSpec(
  {
    // 覆盖内置 divider（schema.ts 中 map key 同名替换）——类型统一，
    // `<hr>` 归属 / `---` input rule / markdown `---` 往返全部保持官方语义
    type: "divider",
    propSchema: {
      style: { default: "solid", values: ["solid", "dashed", "wavy"] },
    },
    content: "none",
  },
  {
    render: (props) => (
      <DividerRender
        style={(props.block.props as any).style as DividerStyle}
        onUpdateStyle={(s) =>
          props.editor.updateBlock(props.block, {
            props: { style: s },
          } as any)
        }
        onDelete={() => props.editor.removeBlocks([props.block])}
      />
    ),
    // 外部导出（复制/HTML 导出）：语义化 hr + data 属性携带样式
    toExternalHTML: (props) => (
      <hr data-divider-style={(props.block.props as any).style} />
    ),
    // 粘贴/导入解析：仅当元素显式携带 data-divider-style 时追加样式（其余走官方 HR 规则）
    parse: (element) => {
      const s = element.getAttribute?.("data-divider-style");
      if (s === "wavy" || s === "dashed") return { style: s };
      return undefined;
    },
  }
)();
