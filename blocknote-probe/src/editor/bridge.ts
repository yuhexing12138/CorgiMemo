/**
 * Bridge 协议 v1（详见 docs/bridge-protocol.md）
 *
 * 下行（Kotlin → JS）：Kotlin 侧 evaluateJavascript 调
 *   window.BlockNoteEditorHost.onMessage(JSON)
 * 上行（JS → Kotlin）：JS 侧 window.AndroidBridge.postMessage(JSON)
 *   （Kotlin 侧 addJavascriptInterface(BridgeHost, "AndroidBridge")）
 *
 * 纪律：单向数据流——Kotlin 永不向 JS 回灌内容变更；undo/redo 的**历史栈**留在 JS 侧，
 * 仅把「是否可撤销/可重做」的布尔态经 undoState 上行供宿主按钮置灰（v1.7）。
 */

/** 主题载荷（P0：深浅态 + 主色；P1 扩展六色主题；v1.9 增背景色） */
export type ThemePayload = {
  /** 深色模式 */
  dark: boolean;
  /** 主题主色（hex，如 "#1976d2"） */
  primary: string;
  /**
   * 宿主编辑区背景色（hex，如 "#FFFBF5"；v1.9）
   *
   * BlockNote 的 `.bn-editor` 默认铺 `--bn-colors-editor-background`（亮色 #fff、
   * 暗色 #1f1f1f），与宿主主题背景不一致时会形成"画中画"的白底圆角框。
   * 宿主把编辑区实际背景色下行到这里，JS 侧同时覆盖：
   * ① `--bn-colors-editor-background`（编辑器本体）② `body`（WebView 底色）。
   *
   * 缺省（旧版宿主未下发）时回落 white / #1f1f1f，保持向后兼容。
   */
  background?: string;
};

/** 下行消息（Kotlin → JS） */
export type DownMessage =
  | {
      type: "init";
      /** GFM markdown 正文 */
      markdown: string;
      readOnly: boolean;
      theme: ThemePayload;
      /** 字体 id（FontCatalog.id；"system_default" = 系统默认） */
      fontFamily: string;
      /** 可用字体清单（S5）：JS 据此生成 @font-face，字体文件走 shouldInterceptRequest 流 */
      fonts?: Array<{ id: string; weights: number[] }>;
    }
  | { type: "setReadOnly"; readOnly: boolean }
  | { type: "setTheme"; theme: ThemePayload }
  | { type: "setFontFamily"; fontFamily: string }
  /** 主动要一次快照（返回键/切后台前） */
  | { type: "requestSave" }
  /** 撤销/重做（v1.2：原页面撤销/重做按钮经桥触发） */
  | { type: "requestUndo" }
  | { type: "requestRedo" }
  /** 图片插入（v1.3：原页面相册/相机选图后经桥插入光标处；path 为本地绝对路径） */
  | { type: "insertImage"; path: string }
  /** 分割线插入（v1.3：原页面分割线按钮经桥在光标处插入） */
  | { type: "insertDivider" }
  /** 视频/音频/文件插入（v1.5：本地路径，JS 转 file:// URL；媒体播放经 shouldInterceptRequest 流） */
  | { type: "insertVideo"; path: string }
  | { type: "insertAudio"; path: string }
  | { type: "insertFile"; path: string }
  /** 打开表情选择面板（v1.5：JS 自绘网格，点击插入） */
  | { type: "openEmojiPicker" }
  /**
   * 格式命令（v1.4：底部格式工具栏按钮经桥作用于当前选区/光标块）。
   * action 取值：
   * - bold / italic / underline / strike / codeSpan（toggleStyles 行内格式）
   * - fontSize / textColor（value = "18px" / "#ff0000" 或 "default"=清除）
   * - bulletList / numberedList / checkList / paragraph（光标块类型切换）
   * - indent / outdent（嵌套层级 ±1）
   * - alignLeft / alignCenter / alignRight（光标块对齐）
   * - transform（value = heading1/2/3、quote、codeBlock、table、pageBreak——块类型转换/插入）
   */
  | { type: "format"; action: string; value?: string };

/** 上行消息（JS → Kotlin） */
export type UpMessage =
  | {
      type: "ready";
      /**
       * 构建指纹（v1.8）：由 vite define 在构建期注入，形如
       * "<构建时间> <commit 短 hash><-dirty?>"。
       * 宿主收到后打进 logcat，用于排查「JS 源码改了但真机没生效」
       * ——即 assets 里的产物是否被重新构建过。
       */
      build?: string;
    }
  /** 内容变更快照（JS 侧防抖 800ms） */
  | { type: "changed"; markdown: string }
  /**
   * 撤销/重做可用态（v1.7）：JS 侧历史栈变化后上报，
   * 宿主据此给左上角撤销/重做按钮置灰（对齐 Compose 版 canUndo/canRedo 语义）。
   */
  | { type: "undoState"; canUndo: boolean; canRedo: boolean }
  | { type: "error"; message: string };

declare global {
  interface Window {
    /** Kotlin 注入的原生桥（真机环境存在；dev/浏览器环境缺失） */
    AndroidBridge?: { postMessage(json: string): void };
    /** JS 侧下行消息宿主（Kotlin evaluateJavascript 调用入口） */
    BlockNoteEditorHost?: { onMessage(raw: unknown): void };
  }
}

/** 是否运行在 WebView 宿主内 */
export function isEmbedded(): boolean {
  return typeof window !== "undefined" && !!window.AndroidBridge;
}

/** 上行一条消息（无宿主时打 console，便于 dev 调试） */
export function sendUp(msg: UpMessage): void {
  const json = JSON.stringify(msg);
  if (window.AndroidBridge) {
    window.AndroidBridge.postMessage(json);
  } else {
    // eslint-disable-next-line no-console
    console.log("[bridge:up]", json);
  }
}

/** 绑定下行消息处理器（幂等：重复绑定覆盖前一次）。
 *  raw 兼容两种形态：Kotlin evaluateJavascript 注入的「对象字面量」（已是对象）
 *  与字符串 JSON——typeof 判别后再走解析，避免 "[object Object]" 二次解析错误。 */
export function bindDown(handler: (msg: DownMessage) => void): void {
  window.BlockNoteEditorHost = {
    onMessage: (raw: unknown) => {
      try {
        const msg = (typeof raw === "string" ? JSON.parse(raw) : raw) as DownMessage;
        handler(msg);
      } catch (e) {
        sendUp({ type: "error", message: `bad down message: ${String(e)}` });
      }
    },
  };
}

/**
 * 构建指纹（v1.8）
 *
 * 由 `vite.editor.config.ts` 的 define 在**构建期**文本替换注入，形如
 * `"2026-09-17 18:20:31 a1b2c3d"`（构建时间 + commit 短 hash，工作区脏时带 -dirty）。
 *
 * 存在的意义：assets 里的 `editor.html` 是**静态资源**，Gradle 不会重新生成它，
 * 曾经的坑是「JS 源码改了、App 也重编了，但真机仍是旧 bundle」且无从判断。
 * 现在宿主把该值打进 logcat，一眼就能确认 WebView 加载的是哪一版产物。
 *
 * 兜底：直接用 dev server（`npm run dev`）时该常量未被 define 替换，
 * 此时回落到 "dev"。用 typeof 守卫而非直接引用，避免 ReferenceError。
 */
export const BUILD_FINGERPRINT: string =
  typeof __BUILD_FINGERPRINT__ === "string" ? __BUILD_FINGERPRINT__ : "dev";

/** vite define 注入的全局常量声明（构建期被替换为字符串字面量） */
declare const __BUILD_FINGERPRINT__: string;
