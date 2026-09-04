# 灵感编辑页 · 字号/颜色面板 — 任务概览（原型已审核，源码已落地）

## 已完成
1. **原型**（`原型/工具栏/灵感编辑页字体选择面板.html`）：新增「字号与颜色」按钮与面板，图标候选扩至各 8 个（Material + Lucide），自定义取色器去掉 Aa/色号按钮。已提交 `6d75681d`。
2. **源码落地**（2026-09-04 本轮，按已审核原型实现，用户四点要求全部落实）。

## 本轮源码改动
- **依赖**：`gradle/libs.versions.toml` + `app/build.gradle.kts` 接入 `io.github.ardasoyturk.compose.icons:lucide:2.0.7`（DevSrSouza 风格 API；用法 `compose.icons.LucideIcons` + `compose.icons.lucideicons.Type/CaseSensitive`，坐标已从 maven central AAR classes.jar 核实）。
- **`RichTextFormatToolbar.kt`**：
  - 字体按钮图标 `FontDownload` → **Lucide「Type」**（要求 1）
  - 字体按钮与 B 之间插入**字号与颜色按钮**，图标 **Lucide「CaseSensitive」**（要求 2），新增 `isSizeColorPanelOpen`/`onSizeColorPanelClick` 参数
  - 新按钮复用 `FormatIconButton`（40dp/22dp/8dp 圆角），与整行视觉一致（要求 3）
  - **激活态去背景块**：`FormatIconButton`/`FormatWeightButton`/`FormatWeightTierButton` 全部删除 `0xFFFFE0C0` 浅橙背景，激活态 = 图标/文字变 primary 橙 `0xFFFF9A5C`（要求 4）；连带清理 `background`/`clip`/`RoundedCornerShape` 三个 import
- **新建 `FontSizeColorPanel.kt`**（对照原型）：
  - 面板头 40dp（标题 + 当前值回显「16sp · 默认」+「完成」）；`GridCells.Fixed(4)`、gap 8dp、44dp 格
  - 字号 8 档（12~32sp，Aa 按档位真实缩放 + 数值 baseline 对齐）；颜色 12 色 + 默认（白底斜杠圆块 26dp）
  - **HSV 自定义取色器**：18dp 色相条（六段渐变 + 白把手）+ 84dp SV 板（三层背景：色相纯色→白渐变→黑渐变 + 圆点），拖动每帧回调 HEX；HSV 转换走平台 `AndroidColor.HSVToColor/colorToHSV`
  - 手势闭包通过 `var hsv by remember` delegate 读最新值，规避 `pointerInput(Unit)` 陈旧闭包
- **`InspirationEditBottomBar.kt`**：新增字号颜色面板 `AnimatedVisibility`（与字体面板互斥、同用 `keyboardHeight`，切换不跳动）+ 参数/回调透传。
- **`InspirationEditScreen.kt`**：
  - `isSizeColorPanelExpanded` 状态，与字体面板双向互斥；展开时收键盘
  - 面板回显值**直接从 `richTextState.currentSpanStyle` 派生**（字号未指定回落 16sp；颜色匹配预设→下标，不匹配→自定义 hex），不持双份状态
  - 字号写入：枚举 8 档 `removeSpanStyle`（仅值匹配生效，防叠加）→ 非默认档 `toggleSpanStyle`；颜色写入同构（枚举预设 + remove 当前值 → toggle；默认项只清除）
  - 自定义色：`AndroidColor.parseColor` + runCatching 兜底；拖动每帧写（仅 remove 当前值，性能优先）

## 验证
- 未跑 gradle（约定）；静态自检：4 文件括号平衡与 HEAD 基线一致、新增 import 全部真实使用、删除 import 无残留
- 依赖坐标从 maven central 的 lucide-android AAR 实测核实（`TypeKt`/`CaseSensitiveKt` 类存在）

## 待确认 / 待办
- 用户首次构建验证（新依赖解析 + Lucide 图标渲染 + 拖动取色真机手感）
- 非本次但相关：`FontManager` vs `ContentFontManager` 字重探测来源不一致，建议后续一并修
