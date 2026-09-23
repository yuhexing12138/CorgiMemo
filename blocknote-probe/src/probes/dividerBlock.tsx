import { createReactBlockSpec } from "@blocknote/react";
import { useEffect, useLayoutEffect, useRef, useState, type CSSProperties } from "react";

/**
 * 三样式分割线（迁移 P1-S10 定稿）：
 * 以 **type: "divider" 覆盖内置 spec**（而非新建类型）——`<hr>` 粘贴归属、
 * `---` 回车 input rule、markdown 往返全部保持官方语义，仅追加 style prop。
 * - solid：官方 hr 细线；dashed：CSS 虚线；wavy：SVG 波浪（对齐 Compose 版 DividerLine）
 * - 点击弹样式工具条（三选 + 删除，P1-S10）
 */

type DividerStyle = "solid" | "dashed" | "wavy";

/**
 * 分割线「行容器」样式（v2026-09-23 修复：插入分割线后编辑区看不见）。
 *
 * **原现象**：点底部工具栏 Divider 插入成功（文档里确实多了该块、占一行垂直空白），
 * 但三种样式都看不到任何线。
 *
 * **根因**：BlockNote 官方样式里块内容容器是 **flex 容器**
 * （`@blocknote/mantine` 的 `.bn-block-content { width:100%; padding:3px 0; display:flex }`），
 * 官方内置 divider 渲染的是 `<hr>`，并配套
 * `[data-content-type=divider] hr { flex: 1; … }` 把线**撑满主轴宽度**；
 * 本组件自定义 render 后这层 div 成了 flex item，`flex-grow:0` 且宽度按 max-content
 * 收缩，内部又是一个**空** div ⇒ 两层 div 宽度同时坍缩为 **0**。
 * 于是 `border-top`（solid/dashed）与 `svg`（wavy）的样式全都命中，
 * 但**线长为 0**，肉眼完全不可见——这正是"有空白、没有线"的成因。
 *
 * **修法**：外层行 div 显式撑满（`flex: 1 1 auto`），另带 `width:100%` 兼容
 * 非 flex 父容器（如探针页把该块渲染在普通 block 流中）的场景；
 * `min-width:0` 防止未来放入 flex 行时被内容撑破。CSS 侧另有一条同类兜底规则，
 * 见 `probe.css` 的 `.bn-block-content[data-content-type="divider"] > div`。
 */
const DIVIDER_ROW_STYLE: CSSProperties = {
  cursor: "pointer",
  padding: "2px 0",
  flex: "1 1 auto",
  width: "100%",
  minWidth: 0,
};

/**
 * 工具条与视口边缘的最小安全间距（px）。
 *
 * 工具条 `position: fixed` 挂在**编辑器文档**里，而 Android WebView 里
 * 该文档坐标系 = 屏幕可视区，因此"视口边缘"就是"编辑器可视边缘"。
 * 原实现只写了 `transform: translate(-50%, -120%)` 而不做任何夹取，
 * 于是点击分割线偏上时工具条整个跑到编辑区上边缘之外被裁掉；
 * 点击偏左/偏右时左右两端也被裁掉。此处统一预留 8px 呼吸位。
 */
const TOOLBAR_SAFE_GAP = 8;

/**
 * 分割线浮动工具条（样式三选 + 删除；点击分割线弹出，点外部关闭）
 *
 * **定位策略（v2026-09-23 修复被编辑器边缘裁剪）**：理想位置是"点击处正上方居中"
 * （水平居中于点击点、垂直抬到点击点上方 120% 处）。但真机上分割线常常贴近
 * 编辑区顶部，或点击点落在左右两端，理想位置会越出编辑区被裁掉。
 * 因此改为**两步**：
 *
 * 1. **渲染前先用理想位置挂上**（`visibility:hidden`），
 * 2. `useLayoutEffect` 里量出真实尺寸后夹取到视口安全区内，再显示。
 *
 * 之所以不能"用固定尺寸直接算"：按钮内容含 `┈ ╌ 〰` 等**本地字体可能缺失**的字形，
 * 以及"删除"两个汉字，工具条宽度会随设备字体变化——最准的办法就是**实测**。
 * 放在 `useLayoutEffect`（而非 `useEffect`）是因为它在浏览器绘制前同步执行，
 * 用户看不到"先错位再跳回来"的一帧闪烁。
 *
 * 夹取规则：
 * - 水平：先按点击点居中，若左/右越界则水平滑动到安全区内（**不**越界时保持居中）；
 * - 垂直：默认抬到点击点上方；上方空间不足时翻转到**下方**（仍优先贴住点击点）。
 */
function DividerToolbar(props: {
  x: number;
  y: number;
  current: DividerStyle;
  onStyle: (s: DividerStyle) => void;
  onDelete: () => void;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  /** 夹取后的最终位置；null = 尚未量取（此帧以隐藏态渲染在理想位置） */
  const [pos, setPos] = useState<{ left: number; top: number } | null>(null);

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

  /** 量取真实尺寸后计算安全位置（绘制前同步执行，避免跳位闪烁） */
  useLayoutEffect(() => {
    const el = ref.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    const vw = window.innerWidth;
    const vh = window.innerHeight;

    // 水平：居中于点击点 → 越界则滑动进安全区
    let left = props.x - rect.width / 2;
    left = Math.min(left, vw - TOOLBAR_SAFE_GAP - rect.width);
    left = Math.max(left, TOOLBAR_SAFE_GAP);

    // 垂直：优先置于点击点上方（留 4px 间隙）；上方放不下则翻转到底部
    const above = props.y - rect.height - 4;
    const flipGap = 4; // 翻转后与点击点的间隙
    const top =
      above >= TOOLBAR_SAFE_GAP
        ? above
        : Math.min(props.y + flipGap, vh - TOOLBAR_SAFE_GAP - rect.height);

    setPos({ left, top });
  }, [props.x, props.y]);

  const styles: Array<{ key: DividerStyle; label: string }> = [
    { key: "solid", label: "─────" },
    { key: "dashed", label: "╌ ╌ ╌" },
    { key: "wavy", label: "〰〰〰" },
  ];

  return (
    <div
      ref={ref}
      className="probe-divider-toolbar"
      style={{
        position: "fixed",
        // 未量取前先按理想位置挂载（隐藏态），量取后立即切到夹取结果
        left: pos ? pos.left : props.x,
        top: pos ? pos.top : props.y,
        transform: pos ? undefined : "translate(-50%, -120%)",
        visibility: pos ? "visible" : "hidden",
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
        style={DIVIDER_ROW_STYLE}
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
      style={DIVIDER_ROW_STYLE}
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
