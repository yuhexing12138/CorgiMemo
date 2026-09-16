/**
 * Bridge 协议 v1（详见 docs/bridge-protocol.md）
 *
 * 下行（Kotlin → JS）：Kotlin 侧 evaluateJavascript 调
 *   window.BlockNoteEditorHost.onMessage(JSON)
 * 上行（JS → Kotlin）：JS 侧 window.AndroidBridge.postMessage(JSON)
 *   （Kotlin 侧 addJavascriptInterface(BridgeHost, "AndroidBridge")）
 *
 * 纪律：单向数据流——Kotlin 永不向 JS 回灌内容变更；undo/redo 状态全部留在 JS 侧。
 */

/** 主题载荷（P0：深浅态 + 主色；P1 扩展六色主题） */
export type ThemePayload = {
  /** 深色模式 */
  dark: boolean;
  /** 主题主色（hex，如 "#1976d2"） */
  primary: string;
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
  | { type: "requestSave" };

/** 上行消息（JS → Kotlin） */
export type UpMessage =
  | { type: "ready" }
  /** 内容变更快照（JS 侧防抖 800ms） */
  | { type: "changed"; markdown: string }
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
