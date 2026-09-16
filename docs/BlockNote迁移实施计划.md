# BlockNote 迁移实施计划

> 版本：v1（2026-09-16，待审核）
> 性质：**施工图**——审核通过后按步骤编号（S1、S2…）逐个执行，每步有产出与验收，完成一步提交一次 git
> 决策基线：`BlockNote迁移立项书.md`（TaskList 一行一块 / undo 事务级 + 自建 UI / 阅读卡分层 WebView / 分期 10–14 周）
> 执行方式：每步完成后向用户汇报验收结果，经确认再进入下一步；任何一步触发 §5 停机条件即暂停重评

---

## 1. 总体里程碑

| 里程碑 | 步骤 | 周期 | 出口条件 |
|---|---|---|---|
| **P0 基座** | S1–S7 | 2–3 周 | 真机全链路：打开→编辑→保存→重开内容一致；字体/主题切换生效；灰度开关可回退 |
| **P1 编辑对齐** | S8–S14 | 3–4 周 | 与 Compose 编辑器逐功能对照清单全过；50 篇真实笔记往返回归全绿 |
| **P2 体验打磨** | S15–S19 | 3 周 | 长文档压测达标（300+ 块）；冷启动 <300ms；交互手感验收 |
| **P3 周边与切换** | S20–S24 | 2–3 周 | 阅读页/分享/深色全量核验；灰度放量，Compose 编辑器下线 |

分支策略：`feature/blocknote-editor` 分支承载 S1–S24，每期结束合入 master；灰度开关（§S3）保证 master 随时可发布。

Web 侧工作区：**沿用 `blocknote-probe/` 目录**（改名破坏 git 历史且无收益），语义升级为正式 Web 工作区——正式代码进 `src/editor/`，探针断言保留在 `src/probes/` 作为回归门禁。

---

## 2. 关键技术方案（审核重点）

### 2.1 Bridge 协议 v1（S2 定稿，全项目遵守）

- **通道**：Kotlin→JS 用 `webView.evaluateJavascript(...)`；JS→Kotlin 用 `addJavascriptInterface(BridgeHost, "AndroidBridge")`，消息一律 JSON 字符串。
- **下行（Kotlin→JS）**：
  - `init { markdown, readOnly, theme, fontFamily }` —— 编辑器装载
  - `setReadOnly(bool)` / `setTheme(theme)` / `setFontFamily(name)`
  - `requestSave()` —— 主动要一次快照（如返回键前）
- **上行（JS→Kotlin）**：
  - `ready` —— 编辑器完成装载（Kotlin 侧解除 loading）
  - `changed { markdown }` —— 内容变更，**JS 侧防抖 800ms** 后上行（undo/redo 状态永不出 JS 侧）
  - `error { message }` —— JS 异常上报（logcat）
- **纪律**：Kotlin 永不向 JS 回灌内容变更（单向数据流，POC/调研已定）；协议消息表随代码维护在 `bridge-protocol.md`。

### 2.2 markdown ↔ Blocks 转换管线（S4 核心，含分割线样式方案）

官方 API 不认识我们的自定义编码，采用「**预处理 + 后处理**」管线：

```
载入：markdown 文本
  ① 预处理（逐行正则）：
     - 分割线样式行 `--- dashed` / `--- wavy` → 占位段落 «DIVIDER:dashed»
     - 载体空块占位 EMPTY_BLOCK_PLACEHOLDER(NBSP 行) → 删除该行
  ② tryParseMarkdownToBlocks()
  ③ 后处理：把 «DIVIDER:xxx» 占位段落替换为 dividerStyled 块（props.style=xxx）
  ⇒ blocks → editor

保存：blocks
  ① blocksToMarkdownLossy()
  ② 后处理：dividerStyled 块 → `--- wavy` / `--- dashed` / `---` 文本行
  ⇒ markdown → Kotlin 落库
```

- 分割线样式在存储中**保持现有 `--- style` 编码不变**（与 Compose 版数据互通）；
- 转换层放 `src/editor/markdown/`，**必须配 vitest 单测**：以 ≥20 篇真实笔记 markdown 为 fixtures，断言往返后语义等价（分割线样式/任务勾选/标题层级/图片）；
- 已知有损项（fontSize 等 inline 样式）不在往返保证内，列入转换层文档「已接受损失清单」。

### 2.3 灰度开关

- P0–P2 期间：新编辑器**仅经 adb intent 直达**（现有 `navigate_to=blocknote_probe` 模式，加 `editor` 值），不影响任何用户路径；
- P3：设置页加正式灰度项（默认关），放量后翻转默认值，Compose 编辑器代码保留一个版本周期后删除。

---

## 3. 步骤清单

### P0 基座

**S1 分支与 Web 工程骨架**（0.5 天）
- 内容：建 `feature/blocknote-editor` 分支；`blocknote-probe/src/editor/` 目录（schema 提升、EditorApp 正式应用）；vite 多入口（`index.html` 探针 + `editor.html` 正式编辑器），构建产物 `assets/blocknote-editor/` 与 `assets/blocknote-probe/` 分离
- 产出：dev 模式 editor.html 可用空编辑器
- 验收：两个入口互不影响；typecheck + build 通过

**S2 Bridge 协议 v1**（1 天）
- 内容：按 §2.1 实现 Web 侧 `src/editor/bridge.ts`（无 AndroidBridge 时 fallback 到 console，dev 可测）；协议文档 `docs/bridge-protocol.md`
- 产出：Web 侧协议层 + 文档
- 验收：dev 页模拟 `init{markdown}` 载入、`changed` 上行打 log

**S3 正式容器（Kotlin）**（1–2 天）
- 内容：`BlockNoteEditorScreen.kt`（探针容器演化：Bridge 注入、loading 态、错误态）；`navigate_to=blocknote_editor` intent 分支；临时内存存储（P0 不接真库）
- 产出：真机可打开的编辑器页（空内容）
- 验收：真机输入 → logcat 见 `changed` 上行；返回/重进状态一致

**S4 markdown 转换层**（3–4 天，P0 最重）
- 内容：§2.2 管线实现 + vitest 工程 + fixtures（从现有笔记数据取 ≥20 篇真实样本，覆盖：普通段落/标题/列表/任务/图片/分割线三样式/空块/长文档）
- 产出：`src/editor/markdown/` + 单测套件
- 验收：fixtures 往返全绿；「已接受损失清单」文档化
- ⚠️ 停机条件：任何语义丢失（非清单内）→ 停下重设计编码

**S5 字体注入**（1–2 天）
- 内容：`@font-face` 生成脚本（9 款中文 OFL 字体，复用现有字体资产路径，S5 首日确认位置）；CSS 变量 `--content-font`；`setFontFamily` 下行接线；评估字体子集化（体积 vs 首包）
- 验收：切换字体编辑器即时生效；APK 体积增量记录并确认可接受

**S6 主题同步**（1 天）
- 内容：CorgiMemo 6 色 + 深/浅 → BlockNoteTheme/CSS 变量映射；`setTheme` 下行
- 验收：系统深色模式切换即时生效，无白闪

**S7 P0 集成验收**（0.5 天）
- 内容：真机全链路走查（§1 出口条件逐项）；灰度回退演练；P0 验收单签字
- 验收：里程碑出口条件全部满足

### P1 编辑对齐

**S8 行内格式面板**（2 天）：字号 custom style + 颜色的格式工具栏按钮（对应 FontSizeColorPanel 的 JS 版，Mantine 组件替换 FormattingToolbar）
**S9 任务块**（2 天）：checkListItem 勾选视觉降级（CSS）、任务格式按钮、markdown `- [x]` 往返核对
**S10 分割线工具条**（2 天）：extendBlockSpec 定稿（追加 style prop）、选中弹出三样式切换 + 删除（Mantine popover，对齐设计稿视觉）
**S11 图片块**（3 天）：撑满宽度 CSS、`uploadFile` bridge（Kotlin 收流存文件回传 URI）、删除/预览交互
**S12 undo/redo UI**（1 天）：编辑器顶栏按钮（bridge 调 `editor.undo()/redo()`）+ 长按连发
**S13 自动保存落库**（2 天）：`changed` 接真 Repository（替换 S3 mock）；退出/切后台强制 `requestSave`
**S14 P1 回归与验收**（1–2 天）：50 篇真实笔记往返回归全绿；与 Compose 编辑器逐功能对照清单全过

### P2 体验打磨

**S15 拖拽重排调优**（2 天）：内置手柄手感/动画对齐验收
**S16 斜杠菜单与工具栏中文化**（2 天）：组件替换 + 文案中文化（ComponentsContext）
**S17 缩进**（2 天）：自定义 prop + render padding（1..6 级），markdown 嵌套列表映射
**S18 长文档压测**（2 天）：300+ 块（含图片/分割线混排）滚动帧率、内存曲线、打字延迟；不达标触发 §5 重评
**S19 预热池**（2 天）：headless WebView 预载 + 复用，冷启动 <300ms 验证

### P3 周边与切换

**S20 阅读详情页**（2–3 天）：InspirationViewScreen 正文改 WebView 只读（共享 assets/schema）；列表卡片**保持 Compose 摘要**（§3.3 分层约束）
**S21 分享/导出**（1–2 天）：`toExternalHTML` → 分享意图（替换现有 markdown 直发，视觉增强可选）
**S22 深色模式全量核验**（1 天）
**S23 存量回归与灰度**（2 天）：全量笔记抽样回归；设置页正式灰度项（默认关）
**S24 放量与下线**（按灰度反馈）：默认值翻转 → 观察一个版本周期 → Compose 编辑器代码删除

---

## 4. 通用约定

- **提交纪律**：每步一提交，中文提交信息；P0 起每期结束打 tag（`blocknote-p0` 等）；
- **测试分层**：转换层 vitest 单测（必过）+ 探针 8 项断言（升级回归门禁）+ 真机走查清单（每期）；
- **文档同步**：bridge-protocol、markdown 编码规范、已接受损失清单随代码进 `docs/`；
- **不编译约定**：Web 侧 npm build 由助手执行；gradle 构建与真机安装始终由用户执行。

---

## 5. 停机条件（触发即暂停重评，灰度开关兜底）

1. S4 往返测试出现「清单外语义丢失」；
2. S18 压测帧率/内存不达标且虚拟化方案不可行；
3. P1 对照清单出现无法对齐的核心交互；
4. 真机出现键盘/IME 回归（POC 已过项劣化）。

---

## 6. 待确认项（审核本文档时请一并确认）

1. 分支策略：`feature/blocknote-editor` 长分支 vs 直接 master 推进（建议长分支，灰度期隔离）；
2. 字体资产位置确认（S5 首日核对，9 款 ttf 当前所在目录）；
3. fixtures 样本授权：转换层单测使用真实笔记内容（本地 fixtures，不入库明文——脱敏后入库 or 仅本地保留？建议脱敏入库）；
4. P3 列表卡片渲染二选一：保持 Compose 摘要（推荐，零风险）vs HtmlCompat 渲染（视觉更强但新增解析层）。
