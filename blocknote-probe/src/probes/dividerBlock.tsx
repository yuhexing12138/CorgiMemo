import { createReactBlockSpec } from "@blocknote/react";
import {
  useEffect,
  useState,
  type CSSProperties,
  type MouseEvent as ReactMouseEvent,
} from "react";
// Portal 让工具条挂到 document.body：DOM 上脱离分割线子树，
// 从结构上消除"点击冒泡回外层、坐标被覆写"的跳位隐患（见 DividerToolbar 注释）
import { createPortal } from "react-dom";

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
 * 工具条按钮的固定尺寸（px，v2026-09-23 第二轮修复）。
 *
 * **为什么必须写死**：工具条按钮原先只给了 `padding` 与 `fontSize`，靠内容自然撑开，
 * 于是宽度由**最长按钮**（虚线档）决定，高度由**折行行数**决定——而这两者都随
 * 实际渲染时的字体度量浮动（真机实测同一处分割线两次点击，因折行行数不同，
 * 工具条宽度与按钮高度都不一样）。写死宽高后：
 * - 三个样式档按钮**等宽等高**，切换档位不会引起任何尺寸变化；
 * - 图标改用 SVG（见 `DividerStyleIcon`）后宽度绝对可控，不依赖设备字体是否含 `╌` `〰`；
 * - 工具条整体尺寸恒定 ⇒ `useLayoutEffect` 实测的 `rect` 恒定 ⇒ 夹取位置也恒定，
 *   不会再出现"同一位置两次点击、位置也不一样"的抖动。
 */
const TOOLBAR_BTN_W = 52;
const TOOLBAR_BTN_H = 28;
/** 删除按钮稍宽（容纳两个汉字），高度与样式档按钮对齐 */
const TOOLBAR_DEL_W = 46;

/**
 * 工具条基准字号（px，v2026-09-23 第二轮修复）。
 *
 * **必须显式写死，不能靠继承**：本工具条渲染在 `.editor-page` 的祖链之下
 * （`EditorApp.tsx` 在该元素内联注入了 `--bn-editor-base-font-size`，值 = 宿主
 * 「正文字号」设置），若工具条自身不声明字号，子元素就会继承到这个可变值。
 * 虽然当前各按钮的 `font-size` 已是绝对 px，但 `line-height: normal`
 * 与行高计算仍会**按继承字号**推导，导致按钮高度跟着宿主字号浮动。
 * 写死 12px 即把这条继承链彻底截断：无论正文字号设成 14 还是 32，
 * 工具条尺寸完全一致。
 */
const TOOLBAR_BASE_FONT = 12;

/**
 * 工具条内边距与按钮间距（px，v2026-09-23 第三轮：配合尺寸推算抽出）。
 *
 * 原先这两个值直接写在 JSX 的 `padding: 6` / `gap: 4` 里，
 * 现在位置改为**纯常量推算**（不再实测），必须让"写进 style 的值"
 * 与"推导尺寸用的值"读同一个常量，避免两处各写一个数字后改一处漏一处。
 */
const TOOLBAR_PADDING = 6;
const TOOLBAR_GAP = 4;

/**
 * 工具条整体尺寸（px，v2026-09-23 第三轮：由"实测"改为"推算"）。
 *
 * **为什么可以推算**：自 v2026-09-23 第二轮起，三个样式按钮
 * （`TOOLBAR_BTN_W × TOOLBAR_BTN_H`）、删除按钮（`TOOLBAR_DEL_W × TOOLBAR_BTN_H`）、
 * 内边距与间距全部写死，且字号也写死（`TOOLBAR_BASE_FONT`）——
 * 工具条的内容尺寸已不再受宿主字号、设备字体、折行行为影响。
 * 因此 `getBoundingClientRect()` 的返回值**每次必然等于下面两个推导值**，
 * 实测已无信息量，可以省掉。
 *
 * ⚠️ **改按钮尺寸时这两个值必须同步改**：它们与 style 里的常量同源，
 * 但"布局结构"（几个按钮、外层有无边框）是硬编码在算式里的。
 * 具体构成：
 * - 宽 = (3 个样式按钮 + 1 个删除按钮 + 3 个 gap) + 2×padding + 2×1px 边框
 * - 高 = 按钮高 + 2×padding + 2×1px 边框
 *
 * 当前取值（改动按钮尺寸后请重算这两行）：
 * - `TOOLBAR_W = 3×52 + 46 + 3×4 + 2×6 + 2 = 228`
 * - `TOOLBAR_H = 28 + 2×6 + 2 = 42`
 */
const TOOLBAR_W =
  3 * TOOLBAR_BTN_W + TOOLBAR_DEL_W + 3 * TOOLBAR_GAP + 2 * TOOLBAR_PADDING + 2;
const TOOLBAR_H = TOOLBAR_BTN_H + 2 * TOOLBAR_PADDING + 2;

/**
 * 工具条垂直方向与点击点的间隙（px）。
 *
 * 抽成常量是因为它在夹取逻辑里出现两次（上方残留判断、翻转后偏移），
 * 原先分别写成字面量 4，改一处容易漏另一处。
 */
const TOOLBAR_ANCHOR_GAP = 4;

/**
 * 分割线样式档的线性图标（v2026-09-23 第二轮新增）。
 *
 * **为什么换成 SVG**：原先三个按钮用文本标签 `─────` / `╌ ╌ ╌` / `〰〰〰`，
 * 这些制表符类字形在真机上：
 * - 各设备字体度量不同，宽度浮动，最窄时挤成两行（甚至三行），按钮高度随之变化；
 * - `╌`(U+254C) 与 `〰`(U+3030) 属冷僻码位，部分设备字体缺失时会渲染成豆腐块；
 * - 折行后视觉上完全看不出"这是虚线"，反而像乱码。
 * 改用 SVG 后线型**由几何路径确定**，与字体、字号、设备完全无关，
 * 且能保证三个档位在固定尺寸按钮内视觉重量一致。
 *
 * 三个图标共用 `viewBox="0 0 24 12"`：宽 24 高 12 的坐标系，
 * 线段统一放在 `y=6` 中线，`stroke-width=1.5` 与正文分割线观感对齐。
 *
 * @param style 要绘制的线型档位
 * @param color 描边色（选中态由调用方传入主题主色）
 */
function DividerStyleIcon(props: { style: DividerStyle; color: string }) {
  const common = {
    width: 24,
    height: 12,
    viewBox: "0 0 24 12",
    fill: "none",
    stroke: props.color,
    strokeWidth: 1.5,
    // 让线型端点圆润，避免短横显得生硬（与真机分割线观感一致）
    strokeLinecap: "round" as const,
  };

  // 实线：一条整线
  if (props.style === "solid") {
    return (
      <svg {...common}>
        <path d="M1 6 H23" />
      </svg>
    );
  }

  // 虚线：四段短横，间隔均匀（几何确定，不依赖字体里的 ╌ 字形）
  if (props.style === "dashed") {
    return (
      <svg {...common}>
        <path d="M1 6 H5 M8 6 H12 M15 6 H19 M22 6 H23" />
      </svg>
    );
  }

  // 波浪：两段正弦曲线，与正文 wavy 分割线的视觉语义一致
  return (
    <svg {...common}>
      <path d="M1 6 Q 3.75 2, 6.5 6 T 12 6" />
      <path d="M12.5 6 Q 15.25 2, 18 6 T 23.5 6" />
    </svg>
  );
}

/**
 * 读取编辑器主题主色（v2026-09-23 第三轮新增）。
 *
 * **为什么需要这个函数**：工具条改用 `createPortal` 挂到 `document.body` 后，
 * 它已**不在** `.editor-page` 的祖链上，原先靠 CSS 继承拿到的
 * `--editor-primary` 不再可见（`var(--editor-primary, #1976d2)` 会退回硬编码蓝，
 * 主题换色时选中态就不跟随了）。故改为**主动读取**该变量。
 *
 * 取值来源就是 `.editor-page`——`EditorApp.tsx` 把主题色以内联 style 形式
 * 写在该元素上（`["--editor-primary"]: props.theme.primary`）。
 * 找不到元素或取不到值时回落 `#1976d2`（与改造前 fallback 一致，保证不劣化）。
 *
 * ⚠️ 若与宿主共用同一份取值口径，将来主题色改由别处下发时需同步这里。
 */
function readEditorPrimary(): string {
  const el = document.querySelector<HTMLElement>(".editor-page");
  const v = el?.style.getPropertyValue("--editor-primary")?.trim();
  return v || "#1976d2";
}

/**
 * 分割线浮动工具条（样式三选 + 删除；点击分割线弹出，点外部关闭）
 *
 * **定位策略（v2026-09-23 修复被编辑器边缘裁剪；第三轮改为常量推算）**：
 * 理想位置是"点击处正上方居中"（水平居中于点击点、垂直抬到点击点上方）。
 * 但真机上分割线常常贴近编辑区顶部，或点击点落在左右两端，
 * 理想位置会越出编辑区被裁掉，故都要**夹取到视口安全区内**。
 *
 * v2026-09-23 第二轮之前是"两步实测"：先 `visibility:hidden` 挂载，
 * `useLayoutEffect` 里读 `getBoundingClientRect()`，夹取后再显示。
 * 当时必须实测，是因为工具条尺寸会随内容/字体浮动。
 *
 * **第三轮起改为常量推算**：尺寸与字号全部写死后（见 `TOOLBAR_W` / `TOOLBAR_H`
 * 的注释），`rect` 必然等于这两个推导值，实测已无信息量。
 * 于是夹取直接在渲染前用常量算好，**一次渲染到位**：
 * 省掉一次强制同步布局（`getBoundingClientRect` 会强制 reflow），
 * 也省掉"隐藏态 → 显示态"的第二趟渲染和 `visibility` 闪烁隐患。
 *
 * **DOM 归属：改用 Portal（v2026-09-23 第三轮）**：
 * 最早工具条直接渲染在分割线外层 div 之内，于是点样式按钮时事件冒泡到外层
 * `onClick`，外层拿"按钮的点击坐标"当成"分割线的点击坐标"重新定位工具条
 * ⇒ 工具栏跟着手指跳到按钮处。第三轮改为 `createPortal` 挂到 `document.body`，
 * 让工具条在 DOM 上不再是分割线的后代（真机日志 `measure parent="BODY"` 已证实）。
 *
 * ★ **但 Portal 不足以阻断事件（第四轮真机日志实锤）**：
 * 当时据此**删掉了** `stopPropagation`，理由是"DOM 上已不是后代，冒泡不存在"——
 * **这个判断是错的**。`createPortal` 只改 **DOM 归属**，不改 **React 组件树（fiber）关系**；
 * React 17+ 的合成事件是**沿 fiber 树传播**的，Portal 出去的节点在 React 树上
 * **仍然是 `DividerRender` 的子节点** ⇒ `onClick` 照旧冒泡到分割线本体的
 * `openToolbarAt` ⇒ 外层再次用按钮坐标重定位 ⇒ 工具条跟着点击跳，
 * 且 `closeToolbar` 刚置空就被 `openToolbar` 填回 ⇒ 工具条一直不消失。
 * 日志证据：`openToolbar inToolbar=true targetTag="svg"`。
 *
 * ⇒ 结论：**`position:fixed` / `createPortal` 都不阻断事件，要阻断只能 `stopPropagation`**。
 * 现恢复本层拦截，与按钮各自的 `stopPropagation` 形成两道。
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
  /**
   * 主题主色（选中态描边与图标色）。
   *
   * Portal 到 `body` 后无法再靠 CSS 继承拿到 `--editor-primary`，故主动读取；
   * 用 `useState` 惰性初始化，保证只在首次渲染时查一次 DOM，
   * 而不是每次重渲染都 `querySelector`。
   */
  const [primary] = useState(readEditorPrimary);

  // 点外部关闭（capture 在冒泡前拦截，避免先触发样式按钮的 onClick 又立即关闭）
  useEffect(() => {
    const onDocClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      const inside = !!target.closest(".probe-divider-toolbar");
      if (!inside) props.onClose();
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

  /**
   * 夹取后的最终位置（v2026-09-23 第三轮：改为常量推算，一次算好）。
   *
   * 直接由 `props.x/y` + `TOOLBAR_W/H` 推导，不再经 state 中转，
   * 因此不会有"先按理想位置渲染、再跳回来"的中间帧。
   * 视口尺寸在打开工具条这一刻读取即可——工具条生命周期很短
   * （点外部即关闭），期间发生旋转/尺寸变化由宿主重建 WebView 处理。
   */
  const vw = window.innerWidth;
  const vh = window.innerHeight;

  // 水平：居中于点击点 → 越界则滑动进安全区
  const left = Math.max(
    TOOLBAR_SAFE_GAP,
    Math.min(props.x - TOOLBAR_W / 2, vw - TOOLBAR_SAFE_GAP - TOOLBAR_W)
  );

  // 垂直：优先置于点击点上方；上方放不下则翻转到底部
  const above = props.y - TOOLBAR_H - TOOLBAR_ANCHOR_GAP;
  const top =
    above >= TOOLBAR_SAFE_GAP
      ? above
      : Math.min(
          props.y + TOOLBAR_ANCHOR_GAP,
          vh - TOOLBAR_SAFE_GAP - TOOLBAR_H
        );

  /** 三个样式档：key 决定图标线型，title 供长按/悬停提示（原先靠文本标签自解释） */
  const styles: Array<{ key: DividerStyle; title: string }> = [
    { key: "solid", title: "实线" },
    { key: "dashed", title: "虚线" },
    { key: "wavy", title: "波浪线" },
  ];

  return createPortal(
    <div
      className="probe-divider-toolbar"
      /**
       * ★ 兜底阻断（v2026-09-23 第四轮，真机日志实证）。
       *
       * 第三轮曾误判"Portal 后不必再拦截"并删除此处，结果是 bug 依旧——
       * 因为 `createPortal` 只改 **DOM 归属**，React 17+ 的**合成事件仍按 fiber 树
       * 冒泡**，Portal 出去的节点在 fiber 上依旧是 `DividerRender` 的子节点，
       * 点击照旧触达线外侧 div 的 `onClick`（真机 `openToolbar inToolbar=true` 实锤）。
       *
       * 现恢复本层拦截，与按钮各自的 `stopPropagation` 形成两道：
       * - 按钮层：就近拦截，也是主要防线；
       * - 本层：控件整体与外部隔离的语义边界，将来新增按钮忘了写也不会复发。
       */
      onClick={(e) => e.stopPropagation()}
      style={{
        position: "fixed",
        // 位置已由常量算好（含夹取），无需隐藏态过渡，一次渲染到位
        left,
        top,
        background: "#ffffff",
        border: "1px solid #ddd",
        borderRadius: 10,
        boxShadow: "0 4px 16px rgba(0,0,0,0.15)",
        // ⚠️ 必须写死字号：截断来自 .editor-page 的「正文字号」继承链，
        // 否则按钮高度会随宿主字号设置浮动（详见 TOOLBAR_BASE_FONT 注释）。
        // Portal 到 body 后已不在该继承链上，但这行仍保留——它是尺寸可推算的前提，
        // 也是将来万一改回非 Portal 渲染时的保险。
        fontSize: TOOLBAR_BASE_FONT,
        lineHeight: 1,
        display: "flex",
        // gap / padding 与 TOOLBAR_W/H 的推导同源，改这里必须同步改那个算式
        gap: TOOLBAR_GAP,
        padding: TOOLBAR_PADDING,
        // 固定尺寸后内容不会再溢出，禁止任何意外折行
        whiteSpace: "nowrap",
        zIndex: 10000,
      }}
    >
      {styles.map((s) => {
        const active = s.key === props.current;
        return (
          <button
            key={s.key}
            title={s.title}
            aria-label={s.title}
            onClick={(e) => {
              /**
               * ★ 必须阻断传播（v2026-09-23 第四轮，真机日志实证）。
               *
               * **为什么 Portal 救不了这里**：`createPortal` 只改变 **DOM 归属**
               * （工具条确实挂在 `body` 下，实测 `parent=BODY`），
               * 但 React 17+ 的**合成事件是按 React 组件树（fiber 树）传播**的，
               * 而 Portal 出去的节点在 fiber 树上**仍然是本组件的子节点**。
               * 于是按钮点击照旧冒泡到 `DividerRender` 外层 div 的 `onClick`，
               * 外层拿"按钮坐标"当成"分割线坐标"重新置位 `toolbar` ⇒ 工具栏跳位。
               *
               * **真机日志（15:15:24.657）实锤**：
               * ```
               * clickStyle   style="dashed"
               * closeToolbar from="toolbar-wavy"
               * openToolbar  x=237 y=61 targetTag="svg" inToolbar=true   ← 跳位
               * ```
               * `inToolbar=true` 说明这次 `openToolbar` 就是按钮自己触发的；
               * 同时它紧跟在 `closeToolbar` 之后，把刚置空的 `toolbar` 又填回按钮坐标，
               * 于是"工具栏一直显示"——每次关闭都被这次重新打开覆盖。
               *
               * **教训**：`position: fixed` 与 `createPortal` 解决的都是**布局/DOM 归属**，
               * 都不是**事件传播**。跨 Portal 的父子仍共用 React 事件链，
               * 该 `stopPropagation` 时躲不掉。
               */
              e.stopPropagation();
              props.onStyle(s.key);
              props.onClose();
            }}
            style={{
              // 宽高写死：三档按钮等宽等高，切档不会引起工具条尺寸变化
              width: TOOLBAR_BTN_W,
              height: TOOLBAR_BTN_H,
              padding: 0,
              boxSizing: "border-box",
              fontSize: TOOLBAR_BASE_FONT,
              lineHeight: 1,
              // 图标与边框留出呼吸位，避免 SVG 撑满显得局促
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              borderRadius: 6,
              border: active ? `1.5px solid ${primary}` : "1px solid #ddd",
              background: active ? "#eef4ff" : "#fff",
              cursor: "pointer",
            }}
          >
            <DividerStyleIcon style={s.key} color={active ? primary : "#888"} />
          </button>
        );
      })}
      <button
        onClick={(e) => {
          /** 与样式按钮同理：跨 Portal 的 React 事件仍会冒泡到线外侧 div，必须阻断 */
          e.stopPropagation();
          props.onDelete();
          props.onClose();
        }}
        style={{
          // 删除按钮宽度单独给（容纳「删除」两字），高度与样式档按钮严格对齐
          width: TOOLBAR_DEL_W,
          height: TOOLBAR_BTN_H,
          padding: 0,
          boxSizing: "border-box",
          fontSize: TOOLBAR_BASE_FONT,
          lineHeight: 1,
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          borderRadius: 6,
          border: "1px solid #f3c2c2",
          background: "#fff2f2",
          color: "#c0392b",
          cursor: "pointer",
        }}
      >
        删除
      </button>
    </div>,
    document.body
  );
}

/** 分割线渲染（含点击工具条交互） */
function DividerRender(props: {
  style: DividerStyle;
  onUpdateStyle: (s: DividerStyle) => void;
  onDelete: () => void;
}) {
  const [toolbar, setToolbar] = useState<{ x: number; y: number } | null>(null);

  /**
   * 打开工具条（仅由"点分割线本体"触发）。
   *
   * 这里的坐标是**分割线的点击位置**，`DividerToolbar` 据此居中并夹取。
   *
   * ⚠️ **本函数只应被"点分割线"触发**。工具条内的点击必须在此之前被阻断，
   * 否则会带着"按钮坐标"重新来到这里，把 `toolbar` 置成按钮位置 ⇒ 工具栏跳位。
   *
   * **为什么 Portal 不能替代拦截（v2026-09-23 第四轮真机日志实证）**：
   * 第三轮曾以为 `createPortal` 到 `document.body` 后事件就不会再传到这里，
   * 并据此删掉了按钮的 `stopPropagation`——实测**无效**。
   * 原因是 `createPortal` 只改变 DOM 归属，而 React 17+ 的**合成事件沿 fiber 树
   * 传播**，Portal 出去的节点在 fiber 上仍是本组件的子节点，
   * 点击照旧触达本 div 的 `onClick`。真机日志：
   * `closeToolbar` 之后紧跟 `openToolbar inToolbar=true`（坐标即按钮位置）。
   *
   * 现由 `DividerToolbar` 的按钮层 + 容器层两道 `stopPropagation` 拦住。
   */
  const openToolbarAt = (e: ReactMouseEvent) => {
    setToolbar({ x: e.clientX, y: e.clientY });
  };

  /** 关闭工具条 */
  const closeToolbar = () => {
    setToolbar(null);
  };

  if (props.style === "wavy") {
    return (
      <div style={DIVIDER_ROW_STYLE} onClick={openToolbarAt}>
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
            onClose={closeToolbar}
          />
        )}
      </div>
    );
  }

  return (
    <div style={DIVIDER_ROW_STYLE} onClick={openToolbarAt}>
      <div className="probe-divider" data-style={props.style} />
      {toolbar && (
        <DividerToolbar
          x={toolbar.x}
          y={toolbar.y}
          current={props.style}
          onStyle={props.onUpdateStyle}
          onDelete={props.onDelete}
          onClose={closeToolbar}
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
