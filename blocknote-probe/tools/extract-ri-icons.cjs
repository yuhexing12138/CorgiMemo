/* 从 react-icons/ri 提取 BlockNote「+」菜单图标 → 生成 Kotlin ImageVector 集合 */
const fs = require("fs");

const RI_SOURCE = process.argv[2];
const OUT_KT = process.argv[3];

const riSource = fs.readFileSync(RI_SOURCE, "utf8");

/** 图标 → Kotlin 属性名（+ 菜单 23 项全覆盖） */
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
];

/** 提取单个图标（viewBox + path d 数组） */
function extract(riName) {
  const idx = riSource.indexOf(`function ${riName} (props)`);
  if (idx < 0) return null;
  const start = riSource.indexOf("GenIcon(", idx);
  const objStart = riSource.indexOf("{", start);
  // 括号配平找 GenIcon 参数对象
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

/** 去重 Ri 名（toggle_heading 与 heading 共用 RiH1 等） */
const uniqueRi = [...new Set(WANT.map(([, ri]) => ri))];
const extracted = {};
const missing = [];
for (const ri of uniqueRi) {
  const r = extract(ri);
  if (r) extracted[ri] = r;
  else missing.push(ri);
}
if (missing.length) {
  console.error("MISSING:", missing.join(", "));
  process.exit(1);
}

/** 生成每个唯一 Ri 图标的 Kotlin 属性 */
function kotlinProp(riName) {
  const { viewport, paths } = extracted[riName];
  const pathsKt = paths
    .map((d) => `            "${d.replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`)
    .join(",\n");
  return `    val ${riName}: RiIconDef by lazy {
        RiIconDef(${viewport}f, listOf(
${pathsKt}
        ))
    }`;
}

const kotlin = `package com.corgimemo.app.ui.screens.probe

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.graphics.PathParser

/**
 * BlockNote「+」菜单同款图标集（P1-S10/S11 桥接）。
 * SVG path 数据提取自 react-icons/ri（Remix Icon，Apache-2.0），
 * 与 WebView「+」菜单渲染的图标逐字节一致。
 * 由 tools/extract-ri-icons.cjs 生成，勿手工编辑 path 数据。
 */
object BlockNotePlusMenuIcons {

    /** 单个图标定义：viewBox 边长（Remix 全系 24）+ path 的 d 数据列表 */
    data class RiIconDef(val viewport: Float, val paths: List<String>)

${uniqueRi.map(kotlinProp).join("\n\n")}

    /** 解析缓存 */
    private val cache = mutableMapOf<String, ImageVector>()

    /** 取 ImageVector（同 key 复用；path 经 androidx PathParser 解析为 Compose Path） */
    fun vectorFor(riName: String): ImageVector? {
        cache[riName]?.let { return it }
        val def = defs[riName] ?: return null
        val builder = ImageVector.Builder(
            name = "Ri.$riName",
            defaultWidth = dp(def.viewport),
            defaultHeight = dp(def.viewport),
            viewportWidth = def.viewport,
            viewportHeight = def.viewport,
        )
        for (d in def.paths) {
            builder.addPath(
                PathParser.createPathFromPathData(d).asComposePath()
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
    uniqueRi.length,
    "| missing:",
    missing.join(",") || "none"
);
