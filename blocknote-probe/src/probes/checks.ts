/* eslint-disable @typescript-eslint/no-explicit-any */
import type { CheckResult } from "../probeTypes";

/** 截断长文本用于面板展示 */
const clip = (s: string, n = 400): string =>
  s.length > n ? s.slice(0, n) + `…(共 ${s.length} 字符)` : s;

/**
 * 运行全部探针断言。
 * 覆盖：自定义块静态结构、HTML 解析（parse 路径）、markdown 导出/回读、
 * fontSize 序列化、DOM 渲染、schema 注册清单。
 */
export async function runChecks(editor: any): Promise<CheckResult[]> {
  const out: CheckResult[] = [];

  // ---- 1. 分割线（S10：同名覆盖内置 divider，追加 style prop） ----
  try {
    const doc: any[] = editor.document;
    const divider = doc.find((b) => b.type === "divider");
    if (!divider) {
      out.push({ id: "divider-structure", name: "分割线：文档结构", status: "FAIL", detail: "document 中未找到 divider 块" });
    } else {
      const style = (divider.props as any)?.style;
      out.push({
        id: "divider-structure",
        name: "分割线：文档结构",
        status: style === "wavy" ? "PASS" : "FAIL",
        detail: `找到 divider，props.style = ${JSON.stringify(style)}（期望 "wavy"；S10 起同名覆盖内置，无独立 dividerStyled 类型）`,
      });
    }
  } catch (e: any) {
    out.push({ id: "divider-structure", name: "分割线：文档结构", status: "FAIL", detail: "异常: " + e.message });
  }

  // ---- 2a. 分割线：HTML → 块（data-divider-style 通路） ----
  try {
    const blocks: any[] = await editor.tryParseHTMLToBlocks(
      '<div data-divider-style="wavy"></div>'
    );
    const b = blocks[0];
    const style = (b?.props as any)?.style;
    out.push({
      id: "divider-html-parse",
      name: "分割线：HTML 解析（data 属性通路）",
      status: b?.type === "divider" && style === "wavy" ? "PASS" : "FAIL",
      detail: `解析 <div data-divider-style="wavy"> → type=${b?.type}, props.style=${JSON.stringify(style)}（期望 divider/wavy）`,
    });
  } catch (e: any) {
    out.push({ id: "divider-html-parse", name: "分割线：HTML 解析", status: "FAIL", detail: "异常: " + e.message });
  }

  // ---- 2b. <hr> 归属：同名覆盖后 <hr> 即 divider（S10 统一语义） ----
  try {
    const blocks: any[] = await editor.tryParseHTMLToBlocks("<hr />");
    const b = blocks[0];
    const style = (b?.props as any)?.style;
    out.push({
      id: "divider-hr-ownership",
      name: "<hr> 归属（S10 同名覆盖后）",
      status: b?.type === "divider" ? "PASS" : "FAIL",
      detail: `<hr /> 解析为 type=${b?.type}, props.style=${JSON.stringify(style)} —— 同名覆盖后 <hr> 与样式分割线为同一类型（默认 solid），归属分裂问题已消除`,
    });
  } catch (e: any) {
    out.push({ id: "divider-hr-ownership", name: "<hr> 归属", status: "FAIL", detail: "异常: " + e.message });
  }

  // ---- 3/4. markdown 导出 + 回读（自定义块的往返表现；blocksToMarkdownLossy 为同步 API） ----
  try {
    const md: string = editor.blocksToMarkdownLossy(editor.document);
    out.push({
      id: "md-export",
      name: "markdown 导出（blocksToMarkdownLossy，同步）",
      status: "INFO",
      detail: clip(md),
    });
    const back: any[] = await editor.tryParseMarkdownToBlocks(md);
    const wavyBack = back.find(
      (b) => b.type === "divider" && (b.props as any)?.style === "wavy"
    );
    const fontSizeBack = back.some((b) =>
      Array.isArray(b.content) &&
      b.content.some((inline: any) => inline.styles?.fontSize)
    );
    out.push({
      id: "md-reimport",
      name: "markdown 回读：波浪分割线 / 字号保留",
      status: "INFO",
      detail: `分割线回读 props.style = ${JSON.stringify((wavyBack?.props as any)?.style) || "无 wavy divider（样式丢失）"}；fontSize 保留 = ${fontSizeBack}`,
    });
  } catch (e: any) {
    out.push({ id: "md-export", name: "markdown 导出/回读", status: "FAIL", detail: "异常: " + e.message });
  }

  // ---- 5. fontSize：文档 JSON 序列化 ----
  try {
    const doc: any[] = editor.document;
    let found: string | null = null;
    for (const b of doc) {
      const content = b.content;
      if (Array.isArray(content)) {
        for (const inline of content) {
          const fs = (inline.styles as any)?.fontSize;
          if (fs) found = JSON.stringify(fs);
        }
      }
    }
    out.push({
      id: "fontsize-doc",
      name: "字号样式：文档 JSON 序列化",
      status: found ? "PASS" : "FAIL",
      detail: found ? `styles.fontSize = ${found}` : "未在任何行内内容上发现 styles.fontSize",
    });
  } catch (e: any) {
    out.push({ id: "fontsize-doc", name: "字号样式：文档 JSON 序列化", status: "FAIL", detail: "异常: " + e.message });
  }

  // ---- 6. fontSize：DOM 渲染路径 ----
  await new Promise((r) => setTimeout(r, 100));
  try {
    const span = document.querySelector<HTMLElement>(
      ".bn-inline-content span[style*='font-size']"
    );
    out.push({
      id: "fontsize-dom",
      name: "字号样式：DOM 渲染",
      status: span ? "PASS" : "FAIL",
      detail: span
        ? `渲染节点 <span style="${span.getAttribute("style")}">${span.textContent?.slice(0, 20)}</span>`
        : "未找到带 font-size 的行内 span（render 路径未生效或选择器不匹配）",
    });
  } catch (e: any) {
    out.push({ id: "fontsize-dom", name: "字号样式：DOM 渲染", status: "FAIL", detail: "异常: " + e.message });
  }

  // ---- 7. schema 注册清单（同名覆盖 divider + fontSize 是否真的进了 schema） ----
  try {
    const specs = (editor.schema as any)?.blockSpecs ?? {};
    const styles = (editor.schema as any)?.styleSpecs ?? {};
    const blockNames = Object.keys(specs);
    const styleNames = Object.keys(styles);
    const dividerPropHasStyle = Object.keys(specs.divider?.config?.propSchema ?? {}).includes(
      "style"
    );
    out.push({
      id: "schema-registry",
      name: "schema 注册清单",
      status:
        blockNames.includes("divider") &&
        dividerPropHasStyle &&
        styleNames.includes("fontSize")
          ? "PASS"
          : "FAIL",
      detail: `blockSpecs(${blockNames.length}): ${blockNames.join(", ")}\ndivider propSchema 含 style = ${dividerPropHasStyle}\nstyleSpecs(${styleNames.length}): ${styleNames.join(", ")}`,
    });
  } catch (e: any) {
    out.push({ id: "schema-registry", name: "schema 注册清单", status: "FAIL", detail: "异常: " + e.message });
  }

  return out;
}
