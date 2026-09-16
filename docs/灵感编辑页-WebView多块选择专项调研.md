# 灵感编辑页 WebView 方案专项调研：移动端多块选择问题

> 调研日期：2026-09-16
> 关联文档：`docs/灵感编辑页-WebView接入editorjs-评估报告.md`（其硬伤清单首条即 editor.js #2908「移动端无法多块选择」）
> 调研目标：① editor.js 生态内有无解法；② 有无多块选择能力更强的替代 WebView 编辑器方案
> 结论先行：**多块选择问题的解不在 editor.js 生态内，而在「单文档模型」内核的编辑器（BlockNote / Tiptap）**——但换内核无法改变原评估报告的成本结构，且本项目曾在 Compose 侧自研过跨块选择并主动删除，性价比需重新掂量。

---

## 1. 结论摘要（TL;DR）

| 问题 | 答案 |
|---|---|
| editor.js 能否在生态内修复移动端多块选择？ | **不能**。#2908 open 一年半无官方修复；唯一社区插件 `editorjs-multiblock-selection-plugin` 只覆盖桌面端选择机制的「后续动作」，不解决移动端触摸选择本身 |
| 根因是什么？ | **架构性**：editor.js 每个块是独立 contenteditable（官网原文承认），浏览器原生选择手柄无法跨 contenteditable 工作；桌面端靠自绘 cross-block selection 补，移动端选择手柄/菜单被系统接管，自绘无法替代 |
| 替代方案里谁的多块选择最强？ | **单文档模型**编辑器原生支持跨块选择：BlockNote（底层 Tiptap/ProseMirror，块式 UI 开箱即用）> Tiptap（灵活但块式 UI 自建）> Lexical（Android 实际问题偏多）≈ Quill 2（块式 UI 全自建）；Slate 系直接排除（Android 二等公民） |
| 换内核的代价变了没有？ | **没变**。原评估报告的功能对齐矩阵（行级任务、分割线三样式、载体块、markdown 往返、双层撤销 8 项自研）只是从「editor.js 插件」改名为「BlockNote extension / Tiptap extension」，工作量同量级 |
| 建议 | 维持路线 A；若坚持验证，POC 对象从 editor.js **换成 BlockNote**，POC 清单升级为 5 点（见第 6 节） |

---

## 2. #2908 根因与 editor.js 生态内调研

### 2.1 架构性根因

editor.js 官网对自身架构的描述（原文）：

> "Editor.js workspace consists of separate Blocks: paragraphs, headings, images, lists, quotes, etc. **Each of them is an independent contenteditable element** … provided by Plugin and united by Editor's Core."

由此产生的选择能力断层：

| 端 | 选择机制 | 跨块选择 |
|---|---|---|
| 桌面 | editor.js 自绘 cross-block selection（鼠标拖拽 + Shift） | 有，但有 bug（#2167 选区状态漂移） |
| 移动 | 长按后由**浏览器/OS 接管**选择手柄与菜单 | **无**（#2908）：手柄只能在一个 contenteditable 内拖动，跨块时选择被截断在块边界 |

移动端的自绘替代之所以不可行：Android/iOS 的长按选择手柄、放大镜、系统选择菜单都是浏览器/系统组件，JS 层无法拦截「手柄拖出 contenteditable 边界」这一事件——这与桌面「鼠标事件全归页面」根本不同。这也是为什么该 issue 从 2025-02 open 至今（2026-09 仍 open）没有任何官方修复路径。

### 2.2 生态内唯一相关插件及其局限

社区插件 **`editorjs-multiblock-selection-plugin`**（sebmeister2077，WiziShop 维护 fork，npm 可装，2025-07 仍有更新，兼容 editor.js v2.31+）：

- **实现方式**：MutationObserver 监听 `.ce-block--selected` class 收集选中块集合 → 手动给 toolbar 加 class 显示 → 每个 inline tool（如 underline）继承改写 `surround()`，对选中块的每个 `[contenteditable=true]` 逐个做 DOM 包装/解包。
- **关键局限**：
  1. **它解决的是「桌面端块级选择后 inline 工具不生效」**（#2474），块的选择本身仍依赖 editor.js 桌面鼠标机制——**对移动端触摸长按选择无能为力**，即不解决 #2908；
  2. 每个 inline tool 都要改写为逐块 DOM 操作版本，侵入性高且随工具数量线性膨胀；
  3. 依赖 editor.js 内部 CSS class（`.ce-block--selected` 等），版本升级随时失效（其 changelog 已经历过 2.28→2.31 的兼容断裂）。

**生态内结论：无解。** 这是块隔离架构的固有代价，插件层修复不了。

---

## 3. 选型分水岭：块隔离架构 vs 单文档模型

WebView 编辑器在「跨块选择」上的能力，**不取决于产品形态（是不是块式 UI），而取决于底层文档模型**：

| 文档模型 | 代表 | 跨块选择 | 原理 |
|---|---|---|---|
| **块隔离**（每块独立 contenteditable） | editor.js | ❌ 移动端无 / 桌面自绘 | 浏览器原生选区无法跨 contenteditable |
| **单文档**（一个 contenteditable + 内部选区映射） | ProseMirror/Tiptap、Lexical、Quill、Slate | ✅ **原生支持**（含移动端拖手柄跨块） | 选区由浏览器统一管理，编辑器内核做 DOM↔模型映射 |

**关键洞察**：BlockNote 这类「块式 UI + 单文档内核」的编辑器，观感上与 editor.js 同类，但多块选择能力天壤之别——块的外观是内核渲染的段落/列表节点的样式壳，底层仍是一个连续文档，原生选择手柄可以自由跨块拖动。

---

## 4. 替代编辑器横向对比（移动端多块选择视角 + Android WebView 适配）

### 4.1 对比总表

| 维度 | **BlockNote** | **Tiptap** | **Lexical** | **Quill 2** | editor.js（现状） |
|---|---|---|---|---|---|
| 内核 | Tiptap/ProseMirror | ProseMirror | 自研 | 自研（Parchment） | 自研（块隔离） |
| 许可 | MPL-2.0（商用闭源可用） | MIT | MIT | BSD | Apache-2.0 |
| 块式 UI | ✅ 开箱即用（Notion-like：块手柄/拖拽/斜杠菜单/格式工具栏） | ❌ 需自建（或官方付费 Notion-like 模板） | ❌ 需自建 | ❌ 需自建 | ✅ |
| **移动端跨块选择** | ✅ 原生（单文档） | ✅ 原生 | ✅ 原生 | ✅ 原生 | ❌ #2908 |
| Android IME 现状 | 继承 ProseMirror 的业内最成熟处理 | 同左；v2.11.3 修复移动端 IME 组合后格式重置（CJK 同机制受益） | 问题偏多：三星键盘预测文本覆盖（0.24→0.25 回退修复）、退格字符复活、换行不可删（社区自写 patch）、Android 13 退格失效 | 成熟但需大量自适配（触摸选择/气泡工具栏/键盘顶布局） | 光标/滚动类问题多个 |
| Android 已知 open bug | #2035 退格创建换行（open）；#2122 iOS 双菜单（P2，sponsor） | 键盘适配碎片化（见 4.2） | pre-1.0，API 不稳定 | 长文档性能一般 | #2908 等 |
| 移动 WebView 嵌入先例 | **flutter_blocknote_editor**（beta，2026 活跃） | **TenTap**（React Native 事实标准）、TAMSIV 生产案例 | 有（Eidos 等笔记应用） | 有（大量 CMS） | 少 |
| 复杂自定义块成本 | 中（extension 体系） | 中（extension 体系，文档最全） | 中高 | 高（Parchment 模型受限） | 中（插件体系） |

### 4.2 ProseMirror/Tiptap 系的 Android 真实成本（需清醒）

- ProseMirror 官方论坛经典帖《Contenteditable on Android is the Absolute Worst》：Android IME 把 contenteditable 视为一整段纯文本、几乎所有键入都走 composition；Gboard/三星/LG 键盘各有怪癖；成熟团队需要自写 **~450 行 Android 输入插件 + 输入状态跟踪插件**（cursor parking 等 hack）。
- React Native WebView 嵌 Tiptap 的生产实战（TAMSIV，2026）：**键盘是最大挑战**——光标被键盘遮挡、滚动不跟随、部分设备键盘反复开合；Android 端监听 resize 手动调高度，仅键盘问题花费约一周。
- 但同时，Hacker News 社区长文测试公认：**只有 ProseMirror 通过了 Moby Dick 长文档编辑测试**（虽有延迟），Lexical 未通过——Android 兼容性内核层面仍是 ProseMirror 最强。

### 4.3 BlockNote 的 WebView 嵌入工程经验（对 Android 端最有参考价值）

`flutter_blocknote_editor`（beta）的 changelog 等于一份 Android WebView 嵌入踩坑清单，其中 **2026 年新坑**尤其重要：

1. **Chromium 148+（Android 16 / 最新 WebView）对 `file://` 收紧 CORS**：file:// origin 被视为 null，ES module / CSS / 字体加载被拦截，编辑器静默不初始化——必须走本地 HTTP 服务或开启 `allowFileAccessFromFileURLs` / `allowUniversalAccessFromFileURLs`（并把资产锁定在应用私有目录内）。
2. **冷启动优化**：编辑器池预热（预载一个 headless WebView，编辑页秒开，用后自动回填）。
3. **撤销/重做必须全部留在 JS 侧**：原生侧永不回灌事务（单向数据流），否则 undo 历史被污染——这与本项目「Kotlin 命令栈 + 库内 history」的双层撤销模型直接冲突，是隐藏的大成本。
4. **事务批处理 + debounce** 同步、Android WebView 内容注入时序轮询（TenTap 的 `contentInjected` workaround 同款问题）。

---

## 5. 对 CorgiMemo 的决策建议

### 5.1 一个必须先回答的前置问题

**本项目在 Compose 侧已经自研过跨块选择，并于 2026-09-14 主动删除**（提交 `e9abf6f7`，理由：滑动拖拽选区手柄跨多块连续选中的交互不符合预期）。也就是说：

- 如果「多块选择」的**交互质量**没有超过被删除的自研版，为了它迁移整个编辑器内核 = 把一个已放弃的功能当作迁移理由；
- WebView 方案（即使 BlockNote）给出的也是「原生选择手柄 + 浏览器选区」的交互，与当初被删除的「自绘手柄」**交互形态不同**，可能更符合预期——但这必须靠 POC 真机验证，不能纸面认定。

### 5.2 决策矩阵

| 若你的判断是… | 那么应该… |
|---|---|
| 多块选择是「锦上添花」，核心仍是现有编辑体验 | **维持路线 A**（Compose 自研深化），多块选择作为低优先级 backlog 项 |
| 多块选择是刚需，且愿意为验证支付 2 周 | POC 对象 **从 editor.js 换成 BlockNote**（见 5.3 清单）；POC 通过再谈迁移（原评估报告 P0–P3 分期依然适用，自研项从 editor.js 插件换成 BlockNote extension） |
| 多块选择是刚需，但接受块式 UI 自建、换取最大灵活性 | 裸 **Tiptap**（ProseMirror 内核）POC，同清单 |
| 想继续用 editor.js | 接受移动端无多块选择，或把它当桌面/Web 端专属能力——**不要指望生态插件** |

### 5.3 更新版 POC 验证清单（若走 BlockNote/Tiptap）

| # | 验证点 | 通过标准 | 风险等级 |
|---|---|---|---|
| 1 | **移动端跨块选择**（本次调研的主目标） | Android WebView 内长按拖拽手柄跨 ≥3 个块（含图片块边界）选择稳定，系统菜单/复制可用；**并与被删除的 Compose 自研版对比交互，确认更优** | 🔴 一票否决项 |
| 2 | 中文 IME | Gboard / 搜狗 / 三星键盘：组合输入、块边界退格、撤销链路无异常 | 🔴 一票否决项 |
| 3 | 键盘 inset 与光标跟随 | `adjustResize` + 内滚动容器下光标始终可见；键盘开合无闪烁 | 🔴 一票否决项 |
| 4 | undo 接自定义块 | BlockNote/Tiptap extension 的自定义块（分割线/行级任务）参与撤销不丢状态 | 🟡 |
| 5 | Chromium 148+ 资产加载 | file:// CORS 收紧后的加载策略（本地 HTTP / allowFileAccess）+ 预热池冷启动 < 300ms | 🟡 |

---

## 6. 附录：数据来源

- editor.js 官网架构描述（每块独立 contenteditable）；#2908（2025-02 open，移动端无法多块选择，无修复）、#2474（桌面块选择后 inline 工具不生效，社区插件出处）、#2167（桌面 cross-block selection 状态 bug）。
- `editorjs-multiblock-selection-plugin`（sebmeister2077 原仓库 + WiziShop fork，npm，2025-07 更新，兼容 v2.31+）：MutationObserver + 逐块改写 inline tool 的实现与局限。
- BlockNote（TypeCellOS）：#2035（Android 退格创建换行）、#2122（iOS 选择双菜单 P2）、#1922（`getSelectionCutBlocks` 块选择 API 存在性佐证）；MPL-2.0。
- `flutter_blocknote_editor` 0.0.24 changelog：Chromium 148+ file:// CORS 收紧、allowFileAccessFromFileURLs、编辑器池预热、undo/redo 单向流规则、事务批处理。
- ProseMirror discuss《Contenteditable on Android is the Absolute Worst》（Android IME 机制、450 行输入插件、cursor parking）；Hacker News 讨论（Moby Dick 测试仅 ProseMirror 通过、Slate Android 二等公民、Lexical 选区丢失）。
- Tiptap：v2.11.3 移动端 IME 格式重置修复（CJK 同机制）、焦点管理修复；discussion #5733（iOS IME + clearDocument 插件冲突，Android WebView 不复现）。
- TAMSIV《TipTap in React Native》（键盘为最大挑战，Android resize 手动适配约一周）；TenTap（@10play/tentap-editor）bridge 架构与 Android 内容注入时序 workaround。
- Lexical：官方浏览器支持矩阵（Android Chrome 86+ 全支持声明）；三星键盘 0.24.0 回归与 0.25.0 回退；stacker.news PR #2708（Android IME 竞态三 bug 修复）；Eidos Android 13 退格修复。
- Quill 2：移动端适配实践（触摸选择/气泡工具栏/键盘顶布局/tab 冲突需逐一自适配，无块式 UI）。
- 本项目：跨块选择自研版删除记录（工作日志 2026-09-14 / 提交 `e9abf6f7`）。
