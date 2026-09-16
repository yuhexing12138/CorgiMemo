import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { viteSingleFile } from "vite-plugin-singlefile";

/** 正式编辑器构建（editor/index.html），容器加载 assets/blocknote-web/editor/index.html */
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    outDir: "../app/src/main/assets/blocknote-web/editor",
    emptyOutDir: true,
    assetsInlineLimit: 100000000,
  },
});
