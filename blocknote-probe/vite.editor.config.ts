import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { viteSingleFile } from "vite-plugin-singlefile";
import { resolve } from "path";
import { execSync } from "child_process";

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
 * 正式编辑器构建（editor/editor.html），容器加载 assets/blocknote-web/editor/editor.html。
 * ⚠️ 必须显式指定 input=editor.html——缺省时 vite 回退到根目录 index.html（探针入口），
 * 产物会错装成探针应用（真机已踩：两次构建 hash 相同）。
 */
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  define: {
    /** 构建指纹注入（v1.8）：构建期文本替换为字符串字面量 */
    __BUILD_FINGERPRINT__: JSON.stringify(collectBuildFingerprint()),
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
