/* 从 react-icons/ri 提取 BlockNote「+」菜单图标 → 生成 Kotlin ImageVector 集合
 * 用法：node extract-ri-icons.cjs <react-icons/ri/index.mjs 路径> <输出 .kt 路径>
 */
const fs = require("fs");

const RI_SOURCE = process.argv[2];
const OUT_KT = process.argv[3];

const riSource = fs.readFileSync(RI_SOURCE, "utf8");

/** 图标 id → Ri 源名（+ 菜单 23 项全覆盖；paragraph 备用） */
const WANT = [
  ["heading", "RiH1"],
  ["heading_2", "RiH2"],
  ["heading_3", "RiH3"],
  ["heading_4", "RiH4"],
  ["heading_5", "RiH5"],
  ["heading_6", "RiH6"],
  ["toggle_heading", "RiH1"],
  ["toggle_heading_2", "RiH2"],
  ["toggle_heading_3", "RiH3"],
  ["quote", "RiQuoteText"],
  ["toggle_list", "RiPlayList2Fill"],
  ["numbered_list", "RiListOrdered"],
  ["bullet_list", "RiListUnordered"],
  ["check_list", "RiListCheck3"],
  ["code_block", "RiCodeBlock"],
  ["divider", "RiSubtractLine"],
  ["table", "RiTable2"],
  ["image", "RiImage2Fill"],
  ["video", "RiFilmLine"],
  ["audio", "RiVolumeUpFill"],
  ["file", "RiFile2Line"],
  ["emoji", "RiEmotionFill"],
  ["paragraph", "RiText"],
  ["bold", "RiBold"],
  ["italic", "RiItalic"],
  ["underline", "RiUnderline"],
  ["strike", "RiStrikethrough"],
  ["link", "RiLink"],
  ["align_left", "RiAlignLeft"],
  ["align_center", "RiAlignCenter"],
  ["align_right", "RiAlignRight"],
  ["nest", "RiIndentIncrease"],
  ["unnest", "RiIndentDecrease"],
  // v2026-09-21 追加：宿主「标题与字号」面板的「正文字号」档位图标
  // （不在 BlockNote 浮层里，但需要与其余图标同一套渲染管线）
  ["font_size", "RiFontSize"],
];


/** Kotlin 字符串字面量转义（\、"、$ 模板符） */
function ktStr(d) {
  return '"' + d.replace(/\\/g, "\\\\").replace(/"/g, '\\"').replace(/\$/g, "\\$") + '"';
}

/** 提取单个图标（viewBox + path d 数组；括号配平定位 GenIcon 参数对象） */
function extract(riName) {
  const idx = riSource.indexOf(`function ${riName} (props)`);
  if (idx < 0) return null;
  const start = riSource.indexOf("GenIcon(", idx);
  const objStart = riSource.indexOf("{", start);
  let depth = 0;
  let end = objStart;
  for (let i = objStart; i < riSource.length; i++) {
    if (riSource[i] === "{") depth++;
    else if (riSource[i] === "}") {
      depth--;
      if (depth === 0) {
        end = i + 1;
        break;
      }
    }
  }
  const data = eval("(" + riSource.slice(objStart, end) + ")");
  const paths = [];
  const collect = (children) => {
    for (const c of children ?? []) {
      if (c.tag === "path" && c.attr?.d) paths.push(c.attr.d);
      if (Array.isArray(c.child)) collect(c.child);
    }
  };
  collect(data.child);
  const vb = String(data.attr?.viewBox ?? "0 0 24 24").split(" ");
  return { viewport: Number(vb[2]) || 24, paths };
}

/** 提取全部并去重 */
const extracted = {};
const missing = [];
for (const [, ri] of WANT) {
  if (extracted[ri]) continue;
  const r = extract(ri);
  if (r) extracted[ri] = r;
  else missing.push(ri);
}
if (missing.length) {
  console.error("MISSING:", missing.join(", "));
  process.exit(1);
}

/** 生成唯一 Ri 图标的 lazy 属性 */
function kotlinProp(riName) {
  const { viewport, paths } = extracted[riName];
  const pathsKt = paths.map((d) => "            " + ktStr(d)).join(",\n");
  return `    val ${riName}: RiIconDef by lazy {
        RiIconDef(${viewport}f, listOf(
${pathsKt}
        ))
    }`;
}

/** 生成 defs map 条目（menuId → RiIconDef） */
function kotlinDefsEntry(riName) {
  const { viewport, paths } = extracted[riName];
  const pathsKt = paths.map((d) => "                " + ktStr(d)).join(",\n");
  return `            "${riName}" to RiIconDef(${viewport}f, listOf(
${pathsKt}
            ))`;
}

const kotlin = `package com.corgimemo.app.ui.screens.probe

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser

/**
 * BlockNote「+」菜单同款图标集（P1-S10/S11 桥接）。
 * SVG path 数据提取自 react-icons/ri（Remix Icon，Apache-2.0），
 * 与 WebView「+」菜单渲染的图标逐字节一致。
 * 由 tools/extract-ri-icons.cjs 生成，勿手工编辑 path 数据。
 */
object BlockNotePlusMenuIcons {

    /** 单个图标定义：viewBox 边长（Remix 全系 24）+ path 的 d 数据列表 */
    data class RiIconDef(val viewport: Float, val paths: List<String>)

${[...new Set(WANT.map(([, ri]) => ri))].map(kotlinProp).join("\n\n")}

    /** id → 图标定义（id 即 + 菜单 key） */
    private val defs: Map<String, RiIconDef> = mapOf(
${[...new Set(WANT.map(([, ri]) => ri))].map(kotlinDefsEntry).join(",\n")}
    )

    /** 解析缓存 */
    private val cache = mutableMapOf<String, ImageVector>()

    /** 取 ImageVector（同 key 复用；d 经 compose vector.PathParser 解析为 PathNode 列表后 addPath） */
    fun vectorFor(riName: String): ImageVector? {
        cache[riName]?.let { return it }
        val def = defs[riName] ?: return null
        val builder = ImageVector.Builder(
            name = "Ri.$riName",
            defaultWidth = def.viewport.dp,
            defaultHeight = def.viewport.dp,
            viewportWidth = def.viewport,
            viewportHeight = def.viewport,
        )
        for (d in def.paths) {
            val nodes = PathParser().parsePathString(d).toNodes()
            builder.addPath(
                nodes,
                pathFillType = PathFillType.NonZero,
                fill = SolidColor(Color.Black),
            )
        }
        val v = builder.build()
        cache[riName] = v
        return v
    }
}
`;

fs.writeFileSync(OUT_KT, kotlin);
console.log(
  "generated:",
  OUT_KT,
  "| unique icons:",
  Object.keys(extracted).length,
  "| missing:",
  missing.join(",") || "none"
);
