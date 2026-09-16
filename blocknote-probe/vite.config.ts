import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { viteSingleFile } from "vite-plugin-singlefile";

/**
 * 探针工程 Vite 配置
 * - viteSingleFile：JS/CSS 全部内联进单 HTML——规避 Android WebView file://
 *   下 ES module 的 CORS 拦截（Chromium 148+ 收紧，origin=null）
 * - outDir 直接指向 APK assets：构建产物随包加载
 */
export default defineConfig({
  plugins: [react(), viteSingleFile()],
  build: {
    outDir: "../app/src/main/assets/blocknote-probe",
    emptyOutDir: true,
    assetsInlineLimit: 100000000,
  },
  preview: {
    port: 4173,
    strictPort: true,
  },
});
