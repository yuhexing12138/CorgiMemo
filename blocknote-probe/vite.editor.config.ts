import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { viteSingleFile } from "vite-plugin-singlefile";
import { resolve } from "path";
import { execSync } from "child_process";
import { createHash } from "crypto";
import { readFileSync, readdirSync, statSync } from "fs";

/**
 * 采集构建指纹：`<构建时间> <commit 短 hash><-dirty?>`
 *
 * 用途见 `src/editor/bridge.ts` 的 BUILD_FINGERPRINT：宿主把它打进 logcat，
 * 用于确认 WebView 实际加载的是哪一版产物（assets 是静态资源，Gradle 不会重建）。
 *
 * 容错：git 不可用（未装 / 非仓库）时 hash 回落 "nogit"，绝不因采集失败中断构建。
 */
function collectBuildFingerprint(): string {
  const time = new Date().toLocaleString("sv-SE").replace("T", " "); // YYYY-MM-DD HH:mm:ss
  let hash = "nogit";
  let dirty = "";
  try {
    hash = execSync("git rev-parse --short HEAD", {
      cwd: __dirname,
      stdio: ["ignore", "pipe", "ignore"],
    })
      .toString()
      .trim();
    // 只看编辑器相关源码是否脏——避免无关文件的改动把指纹标脏
    const status = execSync("git status --porcelain -- src", {
      cwd: __dirname,
      stdio: ["ignore", "pipe", "ignore"],
    })
      .toString()
      .trim();
    if (status) dirty = "-dirty";
  } catch {
    /* git 不可用时保持 "nogit"，不阻断构建 */
  }
  return `${time} ${hash}${dirty}`;
}

/**
 * 编辑器源码内容哈希（v2026-09-22 新增，取 sha256 前 12 位）
 *
 * 用途：**静态校验产物是否由当前源码构建**。产物 `editor.html` 是构建生成物，
 * 不在源码编辑的同一批文件里——改完 `src/editor/` 常常忘记重建就提交，
 * 真机于是继续跑旧 bundle（本项目已踩过两次）。有了它，宿主/脚本只要把
 * 产物里的哈希与"此刻源码算出来的哈希"一比，就能立刻发现"源码变了、产物没跟上"。
 *
 * ⚠️ 与上面的**构建指纹不是一回事**：构建指纹 = "什么时候、用什么 commit 构建的"
 * （人读的时间戳），本哈希 = "构建进去的内容是什么"（可机检的内容摘要）。
 * 前者能回答"我加载的是哪一版"，只有后者能回答"这版是不是最新的源码"。
 *
 * 采集范围 = **真正会被打进产物的文件**（v2026-09-23 修正，原范围有漏检盲区）：
 * - `editor.html` + `src/editor/` 全量；
 * - **外加 editor 入口的 3 个外部依赖**：`src/probe.css`（EditorApp.tsx import）、
 *   `src/probes/dividerBlock.tsx` 与 `src/probes/fontSizeStyle.tsx`（schema.ts import）——
 *   它们会被打包进产物，但原范围只按目录取 `src/editor`，导致「改了这些文件也不会
 *   被判定为产物过期」（实测：改完 dividerBlock.tsx + probe.css 后校验仍报 OK，
 *   pre-commit 静默放过——与之前踩过的两次同类，只是换了目录）。
 *   注意 `src/probes/` 下另有 `checks.ts` / `schema.ts` 是**探针页专用**（只被 `src/App.tsx`
 *   import），刻意不纳入，以免"改了探针自检面板却要求重建编辑产物"的误报。
 *
 * ⚠️ 新增 editor 侧依赖时必须同步本清单（判断方法：从 `editor.html` → `src/editor/`
 * 逐层 grep import 闭包）。顺序即喂哈希顺序，必须与
 * `scripts/check-blocknote-artifact.ps1` 完全一致，否则恒等失配。
 *
 * 稳定性约定（校验侧脚本必须**逐条对齐**，否则会恒等失败）：
 * - 只算文件**原始字节**，与换行符 / 编码无关；
 * - 路径取相对于 blocknote-probe 的**相对路径、分隔符统一为 `/`、按字典序升序**；
 * - 每条记录按「相对路径 + 内容字节」顺序喂进同一个 sha256。
 */
function collectSrcHash(): string {
  const hash = createHash("sha256");
  const root = __dirname;
  /** 相对路径（分隔符统一 `/`），用于喂哈希与排序 */
  const rel = (abs: string) => abs.slice(root.length).replace(/\\/g, "/").replace(/^\//, "");

  /**
   * 深度优先收集文件（目录内按名称升序，保证跨进程/跨平台顺序一致）。
   * @param abs 绝对路径（文件或目录）
   */
  const walk = (abs: string): void => {
    const st = statSync(abs);
    if (st.isDirectory()) {
      for (const name of readdirSync(abs).sort()) walk(resolve(abs, name));
      return;
    }
    hash.update(rel(abs), "utf8");
    hash.update(readFileSync(abs));
  };

  /** 采集清单：顺序必须与 PS1 校验脚本一致 */
  const ENTRIES = [
    "editor.html",
    "src/editor",
    "src/probe.css",
    "src/probes/dividerBlock.tsx",
    "src/probes/fontSizeStyle.tsx",
  ];
  for (const entry of ENTRIES) walk(resolve(root, entry));
  return hash.digest("hex").slice(0, 12);
}

/**
 * 正式编辑器构建（editor/editor.html），容器加载 assets/blocknote-web/editor/editor.html。
 * ⚠️ 必须显式指定 input=editor.html——缺省时 vite 回退到根目录 index.html（探针入口），
 * 产物会错装成探针应用（真机已踩：两次构建 hash 相同）。
 */
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  define: {
    /** 构建指纹注入（v1.8）：构建期文本替换为字符串字面量 */
    __BUILD_FINGERPRINT__: JSON.stringify(collectBuildFingerprint()),
    /**
     * 源码内容哈希注入（v2026-09-22）：带上 `bn-src:` 前缀，
     * 便于校验脚本用正则从 1.9MB 的压缩产物里稳定定位（压缩后变量名会被改写，
     * 但字符串字面量原样保留）。
     */
    __SRC_HASH__: JSON.stringify(`bn-src:${collectSrcHash()}`),
  },
  build: {
    outDir: "../app/src/main/assets/blocknote-web/editor",
    emptyOutDir: true,
    assetsInlineLimit: 100000000,
    rollupOptions: {
      input: resolve(__dirname, "editor.html"),
    },
  },
});
