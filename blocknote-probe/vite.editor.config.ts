import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { viteSingleFile } from "vite-plugin-singlefile";
import { resolve } from "path";

/**
 * 正式编辑器构建（editor/editor.html），容器加载 assets/blocknote-web/editor/editor.html。
 * ⚠️ 必须显式指定 input=editor.html——缺省时 vite 回退到根目录 index.html（探针入口），
 * 产物会错装成探针应用（真机已踩：两次构建 hash 相同）。
 */
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    outDir: "../app/src/main/assets/blocknote-web/editor",
    emptyOutDir: true,
    assetsInlineLimit: 100000000,
    rollupOptions: {
      input: resolve(__dirname, "editor.html"),
    },
  },
});
