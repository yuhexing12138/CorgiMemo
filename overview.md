# 灵感编辑页 · 字号/颜色面板原型 — 任务概览

## 已完成
按用户决策修改原型 HTML（`原型/工具栏/灵感编辑页字体选择面板.html`），插入新增的「字号与颜色」按钮和面板，并保持视觉与已审核的字体面板一致。

## 主要改动
- **工具栏**：
  - 在「字体按钮」与「加粗 B」之间插入新按钮 `styleBtn`，图标 4 选 1（`format_size` / `format_color_text` / `text_format` / `text_fields`）
  - 字体按钮本身也提供 4 选 1（`font_download` / `text_fields` / `title` / `text_format`），两个候选切换器在右侧并排可实时对比
- **新面板 `sizePanel`**：与字体面板共用同一槽位、同高 388dp、同 40dp 面板头、4 列网格、44dp 格子、相同选中态样式；展开时与字体面板互斥，键盘收起。
- **三组内容**：
  1. 字号（8 档 12/14/16/18/20/24/28/32），格内 Aa 按档位真实缩放 + 数值小字
  2. 字体颜色（12 色文字色板，第 1 格为白底斜线「默认」）
  3. 自定义颜色（HSV 取色器：色相条 + SV 板 + 实时 HEX 预览）
- **即时生效**：字号/颜色点选即写 SpanStyle，面板头只保留「完成」收起，无 pending「应用」两段式（与字体面板刻意不同，因换字体代价高、换样式代价低）。
- **顶部说明与规格表**更新到新面板；新增 6、7 两个 Q&A（SpanStyle 坑、与字体面板互斥的设计原因）；旧的「顺带不一致」Q&A 顺延为 8。
- **页面头部** 标题改为「灵感编辑页 · 字体 & 字号颜色面板」。
- 字体面板头部同步从 34px 抬到 40px（与源码 `FontPickerPanel` 一致）。

## 验证
- 静态检查：内联脚本语法 OK、所有 getElementById / el.* / state.* 引用完备
- Chrome 无头实跑：注入错误捕获 + 自动点击序列（展开面板 → 选 24sp → 选靛蓝 → 切图标 → 拖色相条），控制台零报错，运行时探针显示 `sizePanelOpen=true / styleBtnOn=true / kbHidden=true / 字号 8 格 / 颜色 12 格 / 面板头 40px / 正文即时变色` 全部生效

## 待确认 / 待办
- 用户审核原型后，再改源码落点：
  1. `RichTextFormatToolbar.kt`：第一组插入新按钮 + `isSizeColorPanelOpen` 参数 + `onStylePickerClick` 回调
  2. 新建 `FontSizeColorPanel.kt`（同 `FontPickerPanel.kt` 目录，复用 GridCells.Fixed(4)、44dp cell、40dp 头、相同选中态）
  3. `InspirationEditBottomBar.kt`：在字体面板的 `AnimatedVisibility` 旁并列插入字号/颜色面板的 `AnimatedVisibility`，两者高度共用 `keyboardHeight`
  4. `InspirationEditScreen.kt`：新增 `isSizeColorPanelExpanded` 状态（与 `isFontPanelOpen` 互斥），并接入 `RichTextState.toggleSpanStyle(SpanStyle(fontSize/color))`（先 removeSpanStyle 清旧值，照抄现有加粗写法）
- 非本次但相关：Q&A 8 里提到的 `FontManager` vs `ContentFontManager` 字重探测来源不一致，建议一并修。