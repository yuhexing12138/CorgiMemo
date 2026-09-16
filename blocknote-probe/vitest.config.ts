import { defineConfig } from "vitest/config";
import { resolve } from "path";

/** 转换层单测配置：jsdom 环境（ProseMirror 需要 DOM） */
export default defineConfig({
  test: {
    environment: "jsdom",
    include: ["tests/**/*.test.ts"],
  },
  resolve: {
    alias: {
      // jsdom 环境下避免解析 CSS 副作用导入
      "@blocknote/core/fonts/inter.css": resolve(__dirname, "src/empty.css"),
    },
  },
});
