# 「+」菜单功能清单与工具栏桥接对照

> 生成日期：2026-09-17 ｜ 依据：`@blocknote/react 0.52.1` 源码 `getDefaultReactSlashMenuItems.tsx`（图标映射）+ `@blocknote/core i18n/en.ts`（标题字典）+ 默认 schema（levels 1–6 全开）
> 结论先行：**「+」菜单共 23 项；工具栏已桥接 14 项；未桥接 9 项**（Heading 4–6 三项、Toggle Heading 2/3 两项、Video/Audio/File/Emoji 四项），明细与原因见第三节。

---

## 一、「+」菜单全量清单（23 项，默认 schema 全开）

图标列 = 官方 UI 实际渲染图标（react-icons/ri 系）。

| # | key | 菜单标题 | 官方图标 |
|---|---|---|---|
| 1 | heading | Heading 1 | RiH1 |
| 2 | heading_2 | Heading 2 | RiH2 |
| 3 | heading_3 | Heading 3 | RiH3 |
| 4 | heading_4 | Heading 4 | RiH4 |
| 5 | heading_5 | Heading 5 | RiH5 |
| 6 | heading_6 | Heading 6 | RiH6 |
| 7 | toggle_heading | Toggle Heading 1 | RiH1 |
| 8 | toggle_heading_2 | Toggle Heading 2 | RiH2 |
| 9 | toggle_heading_3 | Toggle Heading 3 | RiH3 |
| 10 | quote | Quote | RiQuoteText |
| 11 | toggle_list | Toggle List | RiPlayList2Fill |
| 12 | numbered_list | Numbered List | RiListOrdered |
| 13 | bullet_list | Bullet List | RiListUnordered |
| 14 | check_list | Check List | RiListCheck3 |
| 15 | code_block | Code Block | RiCodeBlock |
| 16 | page_break | Page Break | （官方 icons 表缺该 key，渲染为空） |
| 17 | table | Table | RiTable2 |
| 18 | image | Image | RiImage2Fill |
| 19 | video | Video | RiFilmLine |
| 20 | audio | Audio | RiVolumeUpFill |
| 21 | file | File | RiFile2Line |
| 22 | emoji | Emoji | RiEmotionFill |
| 23 | divider | Divider | RiSubtractLine |

> 说明：`paragraph` 不在插入菜单（仅出现在块类型转换下拉）；`heading/toggle_heading` 默认 levels 1–6 全开，故标题家族共 9 项。

---

## 二、与工具栏的桥接关系（逐项对照）

工具栏入口图例：**顶部栏** = 撤销/重做/复制一排；**底部 ⋮ 展开栏** = 字体/字号颜色面板 + 六组格式按钮（含 S10 新增第五/六组）。

| # | 菜单项 | 桥接状态 | 工具栏入口 | 下发命令（协议 v1.4） |
|---|---|---|---|---|
| 1 | Heading 1 | ✅ | 底部第六组「H1」 | transform: heading1 |
| 2 | Heading 2 | ✅ | 底部第六组「H2」 | transform: heading2 |
| 3 | Heading 3 | ✅ | 底部第六组「H3」 | transform: heading3 |
| 4 | Heading 4 | ⛔ 未桥接 | —（低频；需要时在 + 菜单使用） | — |
| 5 | Heading 5 | ⛔ 未桥接 | 同上 | — |
| 6 | Heading 6 | ⛔ 未桥接 | 同上 | — |
| 7 | Toggle Heading 1 | ✅ | 底部第五组「▸H」 | transform: toggleHeading |
| 8 | Toggle Heading 2 | ⛔ 未桥接 | —（同 H4-6 定位） | — |
| 9 | Toggle Heading 3 | ⛔ 未桥接 | — | — |
| 10 | Quote | ✅ | 底部第六组「❝」 | transform: quote |
| 11 | Toggle List | ✅ | 底部第五组「▸≡」 | transform: toggleList |
| 12 | Numbered List | ✅ | 底部第三组「有序列表」 | format: numberedList |
| 13 | Bullet List | ✅ | 底部第三组「无序列表」 | format: bulletList |
| 14 | Check List | ✅ | 底部第三组「复选框」 | format: checkList |
| 15 | Code Block | ✅ | 底部第六组「{ }」 | transform: codeBlock |
| 16 | Page Break | ✅ | 底部第六组「分页」 | transform: pageBreak |
| 17 | Table | ✅ | 底部第六组「表格」 | transform: table |
| 18 | Image | ✅ | 底部栏「相册/相机」 | insertImage: path |
| 19 | Video | ⛔ 未桥接（P2） | — | 需媒体选择+存储链路 |
| 20 | Audio | ⛔ 未桥接（P2） | —（原语音为 token 语法，另路桥接） | — |
| 21 | File | ⛔ 未桥接（P2） | — | 需文件选择链路 |
| 22 | Emoji | ⛔ 未桥接（P2） | — | 需表情选择面板 |
| 23 | Divider | ✅ | 底部第三组「分割线」 + 点击分割线弹样式工具条 | insertDivider / updateBlock style |

**统计**：23 项 = ✅ 桥接 **14** 项 ＋ ⛔ 未桥接 **9** 项（H4–6 三项与 ToggleH2/3 两项为低频档位；Video/Audio/File/Emoji 四项为 P2 媒体与选择器链路）。

> **勘误（v2026-09-22）**
>
> 1. **上表的"未桥接"已过时**：H4–H6 与 Toggle Heading 2/3 现已全部进「标题与字号」面板（H 按钮），
>    编号 4–6、8–9 应视为已桥接；上表首列序号仍沿用当时的 + 菜单顺序，未重排。
> 2. **`transform` 的值是动作名，不等于块类型名**——dispatch 时必须翻译：
>    - `toggleHeading` / `toggleHeading2` / `toggleHeading3` → `type: "heading"` + `props: { level, isToggleable: true }`
>      （BlockNote **没有** `toggleHeading*` 块类型；照抄当 type 传 `updateBlock` 会抛 TypeError）
>    - `toggleList` → `type: "toggleListItem"`
>    - 另注意 1 级折叠标题的动作名**无尾数字**，不能对尾字符取 `Number`（会得到 NaN）
> 3. 折叠标题的持久化：markdown 原生无该语法，已由 `converter.ts` 用
>    `<details><summary>…</summary></details>` 标记包裹往返（详见 `bridge-protocol.md`）。

### 工具栏特有（+ 菜单没有的）

| 工具栏功能 | 下发命令 | 说明 |
|---|---|---|
| 加粗 B（单档） | format: bold | BlockNote 模式点击直接 toggle，不展开 B1/B2/B3 |
| 斜体/下划线/删除线/代码样式 | format: italic / underline / strike / codeSpan | 行内格式 |
| 字号面板点选 | format: fontSize(value px/default) | |
| 颜色面板预设色/自定义取色 | format: textColor(hex / default) | 拖动取色逐帧下发 |
| 撤销/重做 | requestUndo / requestRedo | v1.7：JS 自绘按钮已移除，唯一入口是宿主顶栏图标按钮；可用态经 `undoState` 上行置灰 |

---

## 三、B 按钮单档化说明（2026-09-17）

- BlockNote 模式（`boldSingleTier=true` 自动传入）：点击 **B** 直接 toggle 加粗（`format: bold`），**不再展开 B1/B2/B3 字重菜单**——HTML 语义只有单档加粗；
- Compose 模式（开关关闭）：原三档字重菜单照旧。

---

## 四、遗漏项处理建议

| 遗漏项 | 建议 |
|---|---|
| Heading 4–6、Toggle Heading 2/3 | 可后补（第六组加 H4-H6、第五组加 ▸H2/▸H3），建议观察真实使用频率再决定 |
| Video / Audio / File | P2：需统一「媒体文件选择 + 内部存储拷贝 + file:// 流」链路（与图片 insertImage 同构，可复用拦截器） |
| Emoji | P2：可桥接为下行打开 `:` 表情选择面板（BlockNote GridSuggestionMenu） |
