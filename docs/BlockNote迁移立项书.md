# BlockNote 迁移立项书

> 立项日期：2026-09-16
> 状态：**已立项**（POC 三项一票否决项全过，技术可行性确认）
> 决策记录：TaskList 改「一行一块」✅ ｜ undo 事务级精度可接受（含自建 undo UI）✅ ｜ 阅读卡同走 WebView 共享渲染 ✅（分层执行，见 §3.3）
> 关联文档：`灵感编辑页-WebView接入editorjs-评估报告.md`、`灵感编辑页-WebView多块选择专项调研.md`、`BlockNote与灵感编辑器适配度调研.md`（§7 探针与 POC 证据）

---

## 1. 背景与决策记录

灵感编辑器现由自研 Compose 实现（31 kt / 18,083 行 + compose-rich-editor 子模块），为解决移动端跨块选择（Compose 自研版已删）并获得 Web 生态排版能力，经评估（editor.js 否决）→ 替代调研（BlockNote 胜出）→ API 探针（8 项断言 5 PASS / 3 INFO / 0 FAIL）→ 真机 POC（#1 跨块选择、#2 中文 IME、#3 软键盘 inset 全过），现正式立项迁移。

**已拍板决策**（2026-09-16）：

| # | 决策点 | 结论 |
|---|---|---|
| 1 | TaskList 模型 | **改为「一行一块」**（checkListItem），放弃单段多行方案；迁移时顺带废弃载体空块体系 |
| 2 | 撤销精度 | **接受事务级 undo**（Yjs UndoManager，无块级打包合并）；**undo/redo UI 自建**（BlockNote 默认无入口，POC 已验证 API 可用） |
| 3 | 阅读卡渲染 | **同走 WebView 共享渲染**——按 §3.3 分层执行：详情/阅读页 WebView 只读，列表卡片保持轻量渲染 |
| 4 | 迁移时机 | 随本立项书启动，按 §4 分期推进 |

---

## 2. 许可与商用分析（重点）

### 2.1 结论先行

**按本项目圈定的使用范围，商用成本 = 0，无任何「不能商用」的阻断点**；存在两个**红线约束**（不用 XL 包、不 fork 源码）和一个**合规义务**（保留许可声明）。

### 2.2 依赖许可总表

| 组件 | 许可 | 商用闭源 | 义务 | 本项目用法 |
|---|---|---|---|---|
| **@blocknote/core / react / mantine** | **MPL-2.0** | ✅ 可以 | 文件级 copyleft（见 2.3）+ 保留 LICENSE 与版权声明 | 编辑器内核，npm 引入 |
| React / React DOM | MIT | ✅ | 保留声明 | WebView 内运行（v0.52 强制 React 19） |
| ProseMirror | MIT | ✅ | 保留声明 | BlockNote 底层内核 |
| Tiptap（开源 core） | MIT | ✅ | 保留声明 | BlockNote 底层框架 |
| Mantine（UI 皮肤） | MIT | ✅ | 保留声明 | BlockNoteView 默认皮肤 |
| Yjs | MIT | ✅ | 保留声明 | 0.52 起非协作模式已解耦，随包不启用 |
| Inter 字体 | SIL OFL | ✅ | 可内嵌；**本项目已剔除**（探针构建已去 inter.css） | 不随包分发 |
| 项目自有 9 款中文字体 | SIL OFL | ✅ | OFL 内嵌合规（保留字体自身 LICENSE） | @font-face 注入 WebView |

### 2.3 「不能商用 / 受限」的点（红线）

严格说 **MPL-2.0 与 MIT 都允许商用闭源**，不存在完全禁商用的组件；受限的是以下三种行为：

1. **🔴 修改 BlockNote 源码文件而不公开** —— MPL-2.0 是文件级 copyleft：一旦 fork 并修改任何 MPL 授权的源文件，**被修改的文件必须以 MPL 公开源码**（其余自有代码不受影响）。对本项目的落地纪律：**只通过 extension / `createReactBlockSpec` / 组件替换（ComponentsContext）扩展，不改上游源文件**；确需改上游时，优先向上游提 PR 或用组合包装绕过。
2. **🔴 使用 @blocknote/xl-* 系列包** —— `xl-ai`（AI 集成）、`xl-multi-column`（多栏布局）、`xl-comments`（协作评论）等为 **GPL-3.0 OR PROPRIETARY 双许可**：要么整机遵守 GPL-3.0 开源（对本项目等于不能闭源商用），要么向 BlockNote 购买商业许可。**本项目禁用全部 xl-\* 包**（上述功能均不在需求内）。
3. **🟡 BlockNote 名称与 Logo** —— 不用于产品宣传暗示官方背书即可，页内"Powered by BlockNote"类标识按其文档要求保留或移除（MIT 部分无强制，注意其 UI 内的 attribution marks 配置项 `attributionMarks`）。

### 2.4 「需要付费」的点

| 付费项 | 触发条件 | 本项目是否需要 |
|---|---|---|
| BlockNote 商业许可（XL 包） | 要用 AI / 多栏 / 协作评论且拒绝 GPL | **否**（功能不在需求内） |
| Tiptap Pro 扩展 / Tiptap Cloud（Hocuspocus 协作后端、付费 Collab） | 要官方实时协作云服务 | **否**（单人本地笔记，无协作） |
| BlockNote 官方支持/咨询合同 | 需要上游优先支持 | 否（社区 + 源码自读已够，探针已验证） |

**付费合计：0 元。** 唯一可能的未来付费触发点：若产品规划加入 AI 编辑辅助，优先评估 `xl-ai` 的商业许可报价，再评估自研（届时另立项）。

### 2.5 合规动作清单（分发前）

- [ ] APK 分发的 assets 中保留 BlockNote / React / ProseMirror / Mantine / Yjs 的 LICENSE 与版权声明（汇总一份 `assets/blocknote-probe/THIRD_PARTY_NOTICES.md`）；
- [ ] CI 加依赖扫描规则：禁止 `@blocknote/xl-*` 进入依赖树；
- [ ] 代码评审纪律：不改 `node_modules` 与上游源文件（fork 需走法务评审）；
- [ ] 保留 9 款中文字体各自的 OFL 许可文件随 assets 分发。

---

## 3. 目标架构

### 3.1 分层

```
┌─ Kotlin 宿主 ─────────────────────────────┐
│ InspirationEditScreen（改造）             │
│   └ AndroidView { WebView }               │
│      · imePadding 容器策略（POC 定案）     │
│      · Bridge: Kotlin ⇄ JS 单向数据流      │
│        - Kotlin→JS: 初始化 markdown、      │
│          主题/字体配置、只读切换           │
│        - JS→Kotlin: 变更防抖快照(markdown)、│
│          就绪事件、错误上报               │
│      · undo/redo 全部 JS 侧（永不回灌）    │
├─ WebView（assets 单文件，singlefile）─────┤
│ BlockNote React 应用（自建轻壳）           │
│   · probeSchema 扩展为正式 schema：        │
│     dividerStyled(extendBlockSpec)、      │
│     fontSize custom style、九字体注入     │
│   · 自建 undo/redo 按钮（顶栏/工具条）     │
│   · markdown ↔ Blocks JSON 转换层          │
└───────────────────────────────────────────┘
```

### 3.2 数据层（存储真源：保持 markdown）

- **存储继续用 GFM markdown**（现有数据零迁移、分享/备份链路不动）；
- 转换走官方 API：载入 `tryParseMarkdownToBlocks`、保存 `blocksToMarkdownLossy`；
- **有损补偿规范**（探针已验证方案）：分割线样式用 `toExternalHTML` 输出 `<hr data-divider-style="wavy|dashed">` + `parse` 回读；未来新增自定义样式一律遵循 `data-*` 属性编码约定；
- 存量数据清洗：`EMPTY_BLOCK_PLACEHOLDER`（NBSP 载体空块）载入时剥离、行级任务段落重载后自然成为逐行 checkListItem 块（写迁移说明到用户可感知的变更清单）。

### 3.3 阅读卡分层执行（对拍板项 #3 的专业约束）

「阅读卡同走 WebView」**必须分层**，否则列表场景会翻车：

| 场景 | 方案 | 理由 |
|---|---|---|
| 灵感**详情/阅读页**（单实例常驻） | **WebView 只读渲染**（editable=false，与编辑页共享同一 assets 与 schema） | 与编辑态像素级一致，共享渲染成立 |
| 时间线**列表卡片**（一屏多实例） | **不逐卡起 WebView**：保持现有 Compose 摘要渲染，或改用 `toExternalHTML` 输出 + `HtmlCompat` 轻量渲染 | 每卡一个 WebView = 内存与滚动帧率灾难（每实例 40–80MB 起步） |

### 3.4 交互定案（继承 POC）

- 容器：`imePadding` 缩放（键盘缩放开关开）；WebView 全屏 + visualViewport 方案弃用；
- undo/redo：JS 侧全权，Kotlin 侧自建按钮经 bridge 调 `editor.undo()/redo()`；
- 字号：Custom Styles（`propSchema: "string"`）；颜色：内置 textColor/backgroundColor；
- 分割线：`extendBlockSpec` 扩展内置 divider 追加 style prop（不新建块，`<hr>` 归属不受影响）。

---

## 4. 分期计划（单人全职口径，合计 10–14 周）

| 期 | 内容 | 验收标准 | 估时 |
|---|---|---|---|
| **P0 基座** | 正式容器（探针升级：路由替换、灰度开关保留 Compose 回退）；Bridge 协议（init/change/save/readonly/undo/redo）；markdown↔JSON 转换层 + 有损补偿编码；字体 @font-face 注入框架（9 款中文 + 动态切换）；主题 token 同步（深/浅色） | 新旧编辑器开关切换；打开→编辑→保存→重开往返无损（含分割线样式/任务项）；字体切换生效 | 2–3 周 |
| **P1 编辑对齐** | 段落/行内样式/字号/颜色；checkListItem 一行一块 + markdown 映射（`- [x]` 往返）；分割线 extendBlockSpec 三样式 + 工具条（经 bridge 由原生弹 or JS 内绘）；图片块（撑满 CSS、uploadFile 走 bridge、删除/预览）；undo/redo UI；自动保存（onChange 防抖→落库） | 与现有编辑器逐功能对照清单全过；旧笔记全量回归抽测（50 篇） | 3–4 周 |
| **P2 体验打磨** | 块拖拽重排（内置手柄调优）；斜杠菜单/格式工具栏组件替换（贴合 CorgiMemo UI）；缩进（自定义 prop + render padding）；占位符；**长文档压测（300+ 块滚动/内存）**；WebView 预热池（冷启动 <300ms） | 压测报告（滚动帧率、内存曲线）；冷启动达标；交互手感验收 | 3–4 周 |
| **P3 周边与切换** | 阅读详情页 WebView 只读渲染替换（§3.3）；分享/导出走 `toExternalHTML`；深色模式全量核验；存量数据迁移回归；灰度放量 → Compose 编辑器下线 | 灰度期间崩溃率/回退率达标；三份回归清单全绿 | 2–3 周 |

---

## 5. 风险与缓解

| 风险 | 等级 | 缓解 |
|---|---|---|
| 长文档性能未压测（300+ 块） | 🟡 | P2 专项；不达标则加虚拟化/分页加载 |
| undo 粒度与现有用户习惯差异（无打包合并） | 🟡 | 已拍板接受；P1 验收时专项体验走查 |
| @blocknote 0.x 升级 breaking（如 0.52 的 yjs 解耦） | 🟡 | 锁定精确版本（0.52.1）；升级走独立分支 + 探针回归 |
| 存量数据边缘 case（载体空块、行级任务、混合列表） | 🟡 | P0 转换层单测覆盖 + P3 全量回归；灰度开关兜底 |
| 中文字体注入体积（+10MB 级 assets） | 🟢 | 字体子集化（可选项，P0 评估）或按需下载 |
| 三星/LG 等 OEM 键盘长尾行为 | 🟢 | POC 已过 Gboard/主流输入法；P2 加 OEM 矩阵抽测 |

---

## 6. 资源与协作

- 人力：1 人（Android/Kotlin）+ 前端 React 基础学习成本（约 3–5 天，探针已铺路）；
- 构建链：探针工程继续作为 Web 侧工作区（`npm run build` 产 assets，gradle 打包由项目常规流程承担）；
- 上游跟进：关注 BlockNote release notes（0.x 节奏快），每次升级跑探针 8 项断言作回归门禁。

## 7. 待定技术决策（P0 内定案，不阻塞启动）

1. markdown 转换的**节流与落库粒度**（每次变更 vs 退出时全量）——P0 Bridge 设计时定；
2. 图片存储路径（现有本地文件 → `uploadFile` bridge 的 URI 方案）——P1 前定；
3. 列表卡片轻量渲染二选一（Compose 摘要保持 vs HtmlCompat）——P3 前定。

## 8. 附录：证据链

- 探针 8 项断言（5 PASS / 3 INFO / 0 FAIL）：适配度报告 §7.1–7.4；
- 真机 POC 三项全过 + 容器策略定案：适配度报告 §7.5；
- editor.js 否决理由与移动端硬伤溯源：评估报告 §5、§9；
- 多块选择根因（块隔离 vs 单文档模型）：专项调研 §3。
