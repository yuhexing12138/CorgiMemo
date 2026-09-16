# 灵感编辑页 WebView 接入 editor.js 方案评估报告

> 评估日期：2026-09-16
> 评估对象：CorgiMemo 灵感编辑页（`InspirationEditScreen` + `BodyBlocksEditor`）
> 拟替换方案：Accompanist WebView 嵌入 + codex-team/editor.js
> 结论先行：**不建议替换**。详细理由与替代路线见第 8、9 节。

---

## 1. 结论摘要（TL;DR）

| 维度 | 结论 |
|---|---|
| 技术可行性 | 可行但前提不成立——**Accompanist WebView 已被官方弃维护**，实际只能裸用 `AndroidView + WebView` 并自行维护全部胶水层 |
| 功能对齐 | **6+ 项深度定制能力在 editor.js 无对应物**，全部需用 JS 插件/fork 重写，重写量 ≈ 把现有 1.8 万行 Compose 编辑器逻辑用 JS 再写一遍 |
| 移动端质量 | editor.js 存在多个长期 open 的移动端硬伤（多块选择缺失、iOS 光标错位、DOM 内存泄漏、onChange 不可靠），恰好踩在笔记应用「内容不丢」的生命线上 |
| 成本估算 | 一次性迁移 **12–18 人周（约 3–4.5 人月）**，此后进入 Kotlin + JS 双技术栈长期维护 |
| 建议 | 维持 Compose 自研路线（路线 A）；如仍存疑，先花 2 周做 POC 验证三个关键点（路线 B），不要直接启动迁移 |

---

## 2. 评估范围与方法

- **现状盘点**：统计灵感编辑页与 compose-rich-editor 子模块的代码规模，梳理功能面与定制深度（基于 `BodyBlocksEditor.kt`、`InspirationViewCard.kt`、`.gitmodules` 与项目工程记忆）。
- **目标方案调研**：editor.js 当前版本、维护状态、issue 库（2025–2026 移动端相关）、undo 插件生态；Accompanist WebView 官方状态。
- **评估口径**：功能对齐矩阵 → 架构/性能风险 → 一次性成本 → 长期维护成本 → 收益面，最后给出结论与分期路线。

---

## 3. 方案前提修正：Accompanist WebView 已弃维护

原命题「通过 Accompanist WebView 接入」的**前提本身已不成立**：

- Accompanist 官方文档已标注：**"This library is deprecated, and the API is no longer maintained. We recommend forking the implementation and customising it to your needs."**
- 官方 FAQ 定位 Accompanist 为「实验室」：API 上游化后即弃。`accompanist-webview` 既未上游化（没有官方 Compose WebView 替代物），也不再发修复版本。

**实际形态**：如果走 WebView 方案，真实的技术底座是 `AndroidView { WebView(...) }` 裸接 + 自建以下胶水层（Accompanist 原本提供的都是这些，且约 600 行量级，fork 价值低）：

- `WebViewClient` / `WebChromeClient` 封装
- 导航返回拦截（`BackHandler`）
- 页面加载状态管理
- **JS Bridge（`addJavascriptInterface` / `evaluateJavascript`）双向通信协议**——这是真正的难点，Accompanist 本来就不提供

即：WebView 方案的全部工程成本都必须由本项目自担，且没有任何官方 Compose 封装可以借力。

---

## 4. 现状盘点：当前灵感编辑页的能力与定制深度

### 4.1 代码规模（2026-09-16 实测）

| 模块 | 规模 | 性质 |
|---|---|---|
| `ui/screens/inspiration/` | **31 个 kt / 18,083 行** | App 侧（编辑页 + 阅读卡 + 画廊 + 统计） |
| `BodyBlocksEditor.kt` | **单文件 4,903 行** | 块编辑器核心（模型 + 控制器 + 撤销栈 + 渲染） |
| `compose-rich-editor`（子模块） | 244 个 kt / 48,356 行 | **自维护 fork**（git submodule），TaskList 行级渲染等库层改动在此 |
| `Reorderable`（子模块） | 自维护 fork | `BlocksReorderableList`：块级拖拽重排 settle 定制 |

### 4.2 已定稿的深度定制能力（迁移必须逐一对齐）

1. **块模型**：`sealed BodyBlock`（Text / Image / Divider…），块级 `BodyBlocksCommand` 撤销栈（区间替换命令覆盖拆块/合并/插图拆块/粘贴归一化全部形态），与库内块内 history 双层统一。
2. **TaskList 行级渲染**：单段落多行任务、逐行勾选（`checkedLines`）、库层 `drawCustomStyle` 按 `\n` 分行画框、parser 编解码往返——editor.js 的 checklist 是「一行一项」模型，**无对应物**。
3. **图片块**：撑满宽度 + 真实宽高比（进程级 `ImageAspectRatioCache`）、独立窗口 Popup 工具栏（focusable=false）、删除走高亮态悬浮菜单、图片间载体空块不变量（懒插入/收敛/身份与内容绑定）。
4. **分割线**：三样式（实线/虚线/波浪）+ 五按钮工具条 + 就地换块切换命令；编辑页与阅读卡共享 `drawDashedDivider`/`drawWavyDivider` 渲染原语。
5. **缩进**：布局级 `indentLevel` 1..6 + 列表缩进，编辑页与阅读卡配置一致。
6. **字体体系**：9 款 OFL 中文 + 3 款拉丁，App chrome 与用户内容字体作用域解耦（`ContentFontManager` / `LocalContentTypography`），`FontPreviewEngine` 有界池防 OOM。
7. **焦点体系**：跨帧焦点迁移（pendingFocus 下一帧 requestFocus）、非文本块点选时焦点强制留在 Text 块（保软键盘）、`hideCursorUntilFocusBlockId` 防光标闪现。
8. **数据层**：markdown（GFM）序列化往返（`toMarkdown` / `initialize` 剥前缀），块内容、勾选态、分割线样式、缩进全部编码进 markdown 文本。
9. **拖拽重排**：自维护 fork 的 settle→glide 接续、拖拽期间禁改列表约束、CompositeCommand 一步撤销。

### 4.3 关键架构事实

- **编辑态与阅读态**是「双实现 + 共享原语」：阅读卡 `InspirationViewCard` 独立渲染，但复用同一套缩进语义、占位符语义与分割线绘制原语。迁移 WebView 只处理编辑态会造成双栈分裂；处理阅读态则阅读卡也要重写。
- **数据存储是 markdown 文本**，不是 HTML/JSON。接 editor.js 必须二选一：① 存储改 editor.js JSON（schema 迁移 + 旧数据双向兼容）；② 建 markdown ↔ editor.js JSON 转换器（存在有损风险，尤其行级任务与缩进语义）。

---

## 5. editor.js 现状评估（2026-09 调研）

### 5.1 基本面

| 项目 | 现状 |
|---|---|
| 版本 | v2.31.6（2026-04-07 发布），仍活跃维护 |
| 许可 | Apache-2.0（商用无障碍） |
| 体积 | npm 安装包 743 KB / 解包 1.1 MB（不含工具插件与字体） |
| 架构 | 块插件模型（contenteditable），JSON 存取（`save()` / `render()`） |
| Roadmap | 协作编辑、Undo/Redo Manager、统一工具栏等仍停留在 roadmap，**未进核心** |

### 5.2 移动端已知硬伤（与本场景直接相关，均长期 open）

| Issue | 内容 | 对本项目的影响 |
|---|---|---|
| #2908 | **移动端（Android/iOS/Safari）无法多块选择** | 笔记应用的多段选择/批量操作缺失 |
| #2902 | iOS 切块时光标错位（good first issue 挂了几个月） | 光标一致性 |
| #2917 | iOS 删块后滚动跳顶 | 编辑体验硬伤 |
| #2954 | "Adaptive is terrible" | 小屏适配差 |
| #2866 | **大内存泄漏：所有 DOM 节点永久驻留** | 长文档 + Android WebView 内存压力叠加 |
| #2873 / #2975 | `blocks.render` 不触发 `onChange`（反复复发） | **自动保存链路丢数据风险，P0 级** |
| #2936 | 无字号 inline 工具（社区反复询问） | 当前项目已有字号/颜色面板，需全部自研 |

### 5.3 撤销/重做生态

- 核心不含 undo（roadmap 未完成），依赖第三方 `editorjs-undo`（kommitters 维护，MIT）：
  - v1「稳定」版基于**全文档 re-render** 实现撤销，大文档性能差、多图场景闪烁；
  - v2 重写为 block-update 模型但**仍处 beta（2.0.0-rc.x）**，breaking changes 未定；
  - 默认 200ms debounce 记录历史，与本项目「块级 Command + 块内 history 双层统一、拖拽打包 CompositeCommand 一步撤销」的精度不可同日而语；
  - 图片删除、载体块收敛等自定义行为的撤销联动需自行验证与补齐。

### 5.4 客观优势（公允记录）

- 块插件生态成熟：header / list / checklist / quote / code / table / delimiter / embed / attaches 等开箱即用；
- HTML/CSS 排版上限高，行内混排、粘贴清洗（paste config）能力现成；
- JSON 输出便于未来跨端（iOS/Web）复用编辑内核；
- contenteditable 的 IME 组合输入是浏览器级实现，长按光标/选词栏等系统行为与系统一致性较好。

---

## 6. 功能对齐矩阵（迁移工作量的核心依据）

| # | 当前能力 | editor.js 对应物 | 缺口与代价 |
|---|---|---|---|
| 1 | 富文本段落 + 行内样式（加粗/斜体等，已有格式工具栏） | paragraph + inline tools | ✅ 基本对齐，样式需 CSS 重调 |
| 2 | 字号 / 颜色面板（`FontSizeColorPanel`） | **无官方 inline tool**（#2936） | 需自研 2 个 inline tool（JS） |
| 3 | 复选框块（`checked`） | checklist 插件 | ⚠️ 语义近似，但勾选态渲染位置/降级逻辑需重做 |
| 4 | **TaskList 行级渲染**（单段多任务、逐行勾选、行级对账） | **无对应物**（checklist 一行一项） | 🔴 需自定义块插件重写全部行级逻辑 |
| 5 | 缩进 1..6（布局级 + 列表缩进） | list 嵌套有；**普通段落缩进无** | 🔴 自定义 tune + CSS |
| 6 | 图片块（撑满+真实比例+比例缓存+Popup 工具栏+高亮态删除） | image 插件（面向 URL/上传） | 🔴 本地文件需 bridge 转 blob；工具栏、选中态全自研 |
| 7 | 载体空块不变量（懒插入/边缘 tap/身份绑定/并排收敛） | 无此概念（其 + 按钮天然插块） | 🟡 逻辑可整体丢弃（减负），但**存量 markdown 空块往返映射**必须做 |
| 8 | 分割线三样式 + 五按钮工具条 | delimiter 仅实线 | 🔴 自定义 tune + 样式状态机 |
| 9 | 块级 Command 撤销栈 + 块内 history 统一 | 第三方 editorjs-undo（beta） | 🔴 精度/性能对齐需深度改造或自研 |
| 10 | 块级拖拽重排（自维护 fork 的滑行手感） | 内置 drag&drop | 🟡 手感差距需接受或改其内核 |
| 11 | 跨帧焦点迁移 / 非文本块焦点保护 | 无（contenteditable 焦点管理在其内核内部） | 🔴 Kotlin 侧不再可控，需在 JS 内核层解决 |
| 12 | markdown 序列化往返（含勾选/缩进/分割线样式编码） | **仅 JSON** | 🔴 需自建 markdown ↔ JSON 转换器（或改存储 schema） |
| 13 | 阅读卡复用渲染原语 + 一致缩进/占位符 | readOnly 模式可渲染 | 🟡 阅读卡要么换 WebView 渲染（双栈分裂），要么维护两套渲染 |
| 14 | 字体体系（9+3 款、chrome/内容解耦、预览引擎） | 无 | 🔴 `@font-face` 全量注入 assets（体积 +10MB 级），预览面板另做 |
| 15 | 自动保存（onChange 驱动） | onChange 有已知不可靠 issue | 🔴 需加对账/兜底校验层 |

**结论**：15 项中 8 项 🔴（无对应物/需深度自研）、4 项 🟡、3 项 ✅。红色项合计工作量即相当于用 JS 把 `BodyBlocksEditor` 重写一遍。

---

## 7. 架构与性能风险评估（WebView 固有成本）

| 风险域 | 具体问题 | 等级 |
|---|---|---|
| 软键盘 | Android WebView + `adjustResize` + 内滚动容器是历史重灾区；键盘 inset→光标滚到可见区需自建 | 🔴 |
| 滚动嵌套 | 编辑器外层是 Compose 滚动容器，WebView 内滚动与外层手势冲突需逐版本真机调 | 🔴 |
| 冷启动 | WebView 首次初始化 100–300ms（低端机更久）+ editor.js bundle 解析 + 9 款中文字体加载 | 🟡 |
| 内存 | WebView 进程 +40–80MB，叠加 editor.js #2866 DOM 泄漏 issue，中低端机风险放大 | 🔴 |
| Bridge 性能 | 每次内容变更过 `evaluateJavascript`/JSI 序列化，长文档打字高频同步需做节流与脏标记 | 🟡 |
| 无障碍 | TalkBack 对 contenteditable 支持弱于 Compose 语义树 | 🟡 |
| 安全 | 需开启 `javaScriptEnabled`，本地 assets 加载需收紧 file 访问与接口暴露面 | 🟡 |
| 深色模式 | 主题色需 CSS 变量与 Compose 侧双向同步 | 🟢 |

---

## 8. 成本估算

### 8.1 一次性迁移成本（单人全职口径）

| 阶段 | 内容 | 估算 |
|---|---|---|
| P0 基建 | AndroidView+WebView 宿主、JS Bridge 协议、editor.js 集成、主题/字体注入 | 2–3 周 |
| P1 文本链路 | 段落/行内样式/字号颜色 inline tool、undo 接入与精度对齐、markdown↔JSON 转换器、存储兼容 | 3–4 周 |
| P2 块能力 | 图片块全套、分割线三样式、复选框、行级 TaskList（若保留）、缩进、拖拽重排对齐 | 4–6 周 |
| P3 打磨 | 键盘 inset、滚动联动、载体块映射、自动保存对账、阅读卡/分享/导出适配、真机碎片化 | 3–5 周 |
| **合计** | | **12–18 周（3–4.5 人月）** |

### 8.2 长期维护成本（常被低估的部分）

- 双技术栈：Kotlin 侧 bridge 宿主 + JS 侧编辑器内核与自定义插件，**现有 1.8 万行 Compose 实现要么冻结要么删除**；
- editor.js 核心升级（2.x → roadmap 的 3.0 方向）可能破坏自定义块 API，需持续跟进；
- `editorjs-undo` 处于 beta，其 breaking changes 直接变成项目债务；
- 相比之下，Compose 路线新增一个块能力的既有模式成本 ≈ **2–5 天/项**（新块子类 + Command + 库层钩子，模式已多次验证）。

---

## 9. 结论：不建议替换

1. **前提不成立**：Accompanist WebView 已弃维护，方案实际等于「裸 WebView + 全自研胶水」，引入第三方编辑器的主要动机（省自研成本）被抵消。
2. **功能对齐缺口过大**：15 项核心能力中 8 项无对应物或需深度自研——行级任务、分割线样式、载体块语义、双层撤销、焦点保护、markdown 往返，全部要重写；重写量 ≈ 现有 `BodyBlocksEditor`（4,900 行）+ 库层定制的 JS 复刻。
3. **移动端硬伤踩在生命线上**：多块选择缺失（#2908）、DOM 内存泄漏（#2866）、onChange 不可靠（#2873/#2975）分别对应笔记应用的选择操作、中低端机内存、**自动保存不丢内容**三条底线。
4. **撤销精度倒退**：自研「块级 Command + 块内 history + 拖拽打包一步撤销」将被第三方 beta 插件的 200ms debounce 模型取代，属于核心体验降级。
5. **性能与内存负资产**：WebView 冷启动 + 进程内存 + 泄漏 issue，与「流畅手写灵感」的产品定位相悖；而现有 Compose 方案刚刚完成 TaskList 行级渲染等定稿（2026-09-16，4 个提交），能力势能完整。

**一句话**：这不是「换一个编辑器内核」，而是「用一个生态能力更泛化的 JS 引擎，替换掉一个已经深度贴合产品语义的自研内核」，付出 3–4.5 人月 + 双栈长期维护，换来的是能力面上的冗余（表格/embed/代码块——灵感笔记场景当前并不需要）与三条底线的风险敞口。

---

## 10. 分期路线建议

### 路线 A（推荐）：维持并深化 Compose 自研

- 继续沿用「新块子类 + `BodyBlocksCommand` + 库层钩子」的既有模式扩展能力（如引用块、简单表格），单项 2–5 天；
- 若动机是「排版上限」，优先在 compose-rich-editor 库层补齐（段内混排、段落样式），该库已在自维护 fork 中，可控性 100%；
- 若动机是「跨端复用」，先明确 iOS 版是否真实立项——未立项前不为假想需求支付双栈成本。

### 路线 B（如仍存疑）：两周 POC，先证伪再谈迁移

> 不要直接启动迁移。POC 只验证三个最可能一票否决的点，全部用裸 `AndroidView + WebView`（不含 Accompanist）：

| POC 验证点 | 通过标准 |
|---|---|
| 中文 IME + 软键盘 | 中文输入组合态稳定、键盘弹出/收起时光标始终可见（含 `adjustResize` + 内滚动场景） |
| undo 插件 | `editorjs-undo` beta 接图片删除/拖拽重排/自定义块的撤销不丢状态、不闪烁 |
| 内存与 onChange | 长文档（300+ 块）连续编辑 30 分钟，内存无持续增长；`onChange` 全链路无丢失 |

POC 通过 → 再按第 8.1 节 P0–P3 分期迁移，且 P0 起就要包含「旧 markdown 数据只读兼容」；任一点失败 → 关闭该方向，回到路线 A。

### 路线 C（低成本增强，可与 A 并行）：仅输出链路用 HTML

- 编辑态保持 Compose 不动，仅在**分享/导出链路**把 markdown 渲染为 HTML 卡片（WebView 或分享意图），获得排版分享的收益；
- 成本约 3–5 天，不触碰编辑器内核，无双栈维护负担。

---

## 附录：数据来源

- Accompanist 官方文档（google.github.io/accompanist/web）deprecation 声明；Accompanist FAQ（实验室定位）。
- editor.js：npm `@editorjs/editorjs` v2.31.6（2026-04-07，Apache-2.0，安装体积 743KB）；GitHub issues #2866、#2873、#2902、#2908、#2917、#2936、#2954、#2975（2024-11 ～ 2026-02，均 open）。
- `editorjs-undo`（kommitters，MIT）：v1 稳定（全文档 re-render 模型）、v2.0.0-rc.x beta（block-update 模型）。
- 本项目实测（2026-09-16）：`inspiration/` 31 kt / 18,083 行；`BodyBlocksEditor.kt` 4,903 行；`compose-rich-editor` 子模块 244 kt / 48,356 行；`.gitmodules` 确认 compose-rich-editor / Reorderable / tyme4kt 均为自维护 fork。
- 项目工程记忆：TaskList 行级渲染（2026-09-16 定稿）、分割线五按钮工具条（2026-09-11）、载体空块不变量 v3、块级拖拽重排 fork 等定制项明细。
