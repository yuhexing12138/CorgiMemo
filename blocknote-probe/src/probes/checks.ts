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

  // ---- 1. 自定义分割线块：文档结构（初始内容含 wavy） ----
  try {
    const doc: any[] = editor.document;
    const divider = doc.find((b) => b.type === "dividerStyled");
    if (!divider) {
      out.push({ id: "divider-structure", name: "自定义块：文档结构", status: "FAIL", detail: "document 中未找到 dividerStyled 块" });
    } else {
      const style = (divider.props as any)?.style;
      out.push({
        id: "divider-structure",
        name: "自定义块：文档结构",
        status: style === "wavy" ? "PASS" : "FAIL",
        detail: `找到 dividerStyled，props.style = ${JSON.stringify(style)}（期望 "wavy"）`,
      });
    }
  } catch (e: any) {
    out.push({ id: "divider-structure", name: "自定义块：文档结构", status: "FAIL", detail: "异常: " + e.message });
  }

  // ---- 2a. 自定义分割线：HTML → 块（自定义 parse 通路，用非 hr 元素避开内置规则） ----
  try {
    const blocks: any[] = await editor.tryParseHTMLToBlocks(
      '<div data-divider-style="wavy"></div>'
    );
    const b = blocks[0];
    const style = (b?.props as any)?.style;
    out.push({
      id: "divider-html-parse",
      name: "自定义块：HTML 解析（parse 通路）",
      status: b?.type === "dividerStyled" && style === "wavy" ? "PASS" : "FAIL",
      detail: `解析 <div data-divider-style="wavy"> → type=${b?.type}, props.style=${JSON.stringify(style)}（期望 dividerStyled/wavy）`,
    });
  } catch (e: any) {
    out.push({ id: "divider-html-parse", name: "自定义块：HTML 解析（parse 通路）", status: "FAIL", detail: "异常: " + e.message });
  }

  // ---- 2b. <hr> 归属：内置 divider 与自定义块的抢占关系（迁移设计关键事实） ----
  try {
    const blocks: any[] = await editor.tryParseHTMLToBlocks("<hr />");
    const b = blocks[0];
    out.push({
      id: "divider-hr-ownership",
      name: "<hr> 归属（内置 divider vs 自定义块）",
      status: "INFO",
      detail: `<hr /> 解析为 type=${b?.type}, props=${JSON.stringify(b?.props)} —— 迁移时应扩展内置 divider（extendBlockSpec）而非新建块，否则粘贴 <hr> 语义分裂`,
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
    const wavyBack = back.find((b) => b.type === "dividerStyled");
    const fontSizeBack = back.some((b) =>
      Array.isArray(b.content) &&
      b.content.some((inline: any) => inline.styles?.fontSize)
    );
    out.push({
      id: "md-reimport",
      name: "markdown 回读：波浪分割线 / 字号保留",
      status: "INFO",
      detail: `分割线回读 props.style = ${JSON.stringify((wavyBack?.props as any)?.style) || "无 dividerStyled 块（样式丢失）"}；fontSize 保留 = ${fontSizeBack}`,
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

  // ---- 7. schema 注册清单（自定义 spec 是否真的进了 schema） ----
  try {
    const specs = (editor.schema as any)?.blockSpecs ?? {};
    const styles = (editor.schema as any)?.styleSpecs ?? {};
    const blockNames = Object.keys(specs);
    const styleNames = Object.keys(styles);
    out.push({
      id: "schema-registry",
      name: "schema 注册清单",
      status:
        blockNames.includes("dividerStyled") && styleNames.includes("fontSize")
          ? "PASS"
          : "FAIL",
      detail: `blockSpecs(${blockNames.length}): ${blockNames.join(", ")}\nstyleSpecs(${styleNames.length}): ${styleNames.join(", ")}`,
    });
  } catch (e: any) {
    out.push({ id: "schema-registry", name: "schema 注册清单", status: "FAIL", detail: "异常: " + e.message });
  }

  return out;
}
