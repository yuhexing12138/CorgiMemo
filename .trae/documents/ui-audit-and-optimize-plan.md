# UI 审计与优化计划 — 灵感记录 + 特殊日期记录

> 审计日期：2026-05-28
> 对比基准：`灵感记录功能指导文档.md` + `特殊日期记录功能指导文档.md` + `UI设计规范.md`

---

## 一、灵感记录功能 — 差异清单

### A. 样式差异（需修正为符合 UI 设计规范）

| # | 组件 | 属性 | 当前值 | 规范要求 | 优先级 |
|---|------|------|--------|---------|--------|
| A1 | InspirationScreen | 页面边距 | `padding(horizontal = 16.dp)` | `20dp` (12.1.5.2) | 中 |
| A2 | InspirationScreen | 页面标题字号 | `20.sp` | `24.sp Bold` (12.1.4.2) | 中 |
| A3 | InspirationCard | 卡片圆角 | `RoundedCornerShape(16.dp)` | `20dp` (12.1.5.1) | 高 |
| A4 | InspirationCard | 缩略图尺寸 | `48.dp × 48.dp` | `32.dp × 32.dp` (11.4.2) | 中 |
| A5 | InspirationCard | 标签圆角 | `RoundedCornerShape(12.dp)` | `20dp` 胶囊 (12.1.5.1) | 低 |
| A6 | InspirationEmptyState | 图标尺寸 | `80.sp` / 容器 `120.dp` | `120.dp` 容器 (11.7) | 低 |
| A7 | InspirationEmptyState | 主文案颜色 | `#333333` | `#2D2D2D` (12.1.2.2) | 低 |

### B. 功能缺失

| # | 组件 | 缺失功能 | 规范依据 | 优先级 |
|---|------|---------|---------|--------|
| B1 | InspirationCard | **关联提示未显示**（如 `@待办:买礼物`） | 11.4.2 关联提示行 | **高** |
| B2 | InspirationEditScreen | **编辑模式数据加载为 TODO**（未从 repository 加载） | 正常编辑流程 | **高** |
| B3 | InspirationCard | **长按操作菜单未实现**（仅空回调） | 11.3.7 第6步 | 中 |
| B4 | InspirationScreen | FAB 使用默认 Add 图标 | 规范要求 💡 图标 | 低 |

---

## 二、特殊日期记录功能 — 差异清单

### A. 样式差异（需修正为符合 UI 设计规范）

| # | 组件 | 属性 | 当前值 | 规范要求 | 优先级 |
|---|------|------|--------|---------|--------|
| D1 | SpecialDateScreen | 页面边距 | `padding(horizontal = 16.dp)` | `20dp` (12.1.5.2) | 中 |
| D2 | SpecialDateScreen | 页面标题字号 | `20.sp` | `24.sp Bold` (12.1.4.2) | 中 |
| D3 | SpecialDateCard | 卡片圆角 | `RoundedCornerShape(16.dp)` | `20dp` (12.1.5.1) | 高 |
| D4 | SpecialDateCard | 天数字号 | `22.sp` | `24.sp Bold` (11.4.2) | **高** |
| D5 | SpecialDateCard |天数颜色-RED | `#E53935` | `#FF8A80` (11.4.3/12.1.2.3) | **高** |
| D6 | SpecialDateCard | 天数颜色-GREEN | `#4CAF50` | `#7EC8A0` (11.4.3/12.1.2.3) | **高** |
| D7 | SpecialDateCard | 缩略图尺寸 | `40.dp × 40.dp` | `32.dp × 32.dp` (11.4.2) | 中 |
| D8 | SpecialDateCard | 缩略图数量 | 仅显示1张 | 最多2张 (11.4.2) | 中 |
| D9 | SpecialDateEditScreen | TopAppBar 背景/文字色 | 硬编码 `Color.White` / `#1C1B1F` | 引用 `ui_surface` / `ui_text_primary` | 中 |
| D10 | SpecialDateEmptyState | 主文案颜色 | `#333333` | `#2D2D2D` (12.1.2.2) | 低 |
| D11 | DateGroupHeader | 格式 | 圆点+文本 | `"── 分组名 ──"` 格式 (11.4.1) | 中 |

### B. 布局差异（重大）

| # | 组件 | 当前实现 | 规范要求 | 优先级 |
|---|------|---------|---------|--------|
| L1 | **SpecialDateCard** | **全宽列表卡片** (Row横向布局) | **网格卡片** (宽≈屏幕1/2, 2列) (11.4.1) | **🔴 致命** |
| L2 | **SpecialDateScreen** | 单列 LazyColumn | 2列 LazyVerticalGrid (11.4.1) | **🔴 致命** |

### C. 功能缺失

| # | 组件 | 缺失功能 | 规范依据 | 优先级 |
|---|------|---------|---------|--------|
| F1 | SpecialDateCard | **≤3天左侧4dp高优先级色竖条** | 11.4.3 表格 | **高** |
| F2 | SpecialDateCard | **已过期整体透明度0.6** | 11.4.3 表格 | **高** |
| F3 | SpecialDateCard | **长按操作菜单未实现** | 交互完整性 | 中 |
| F4 | SpecialDateEditScreen | **编辑模式数据加载逻辑有误** | 从 specialDates.value 查找而非 repository | **高** |
| F5 | SpecialDateScreen | FAB 默认 Add 图标 | 规范要求 📅 图标 | 低 |

---

## 三、实施步骤

### Phase 1: 特殊日期卡片重构（最关键）
> **L1+L2**: 将 SpecialDateCard 从全宽列表卡片改为网格卡片布局

**Step 1.1** — 重构 [SpecialDateCard.kt](app/src/main/java/com/corgimemo/app/ui/screens/date/components/SpecialDateCard.kt)
- 改为 Column 纵向布局（适合网格卡片）
- 天数字号改为 24sp Bold
- 颜色改为 ui_days_urgent/ui_days_soon 等 resource 引用
- 添加 ≤3天左侧竖条（Box + width=4dp + background=dayColor）
- 添加已过期 alpha=0.6 效果
- 缩略图改为 32dp，支持最多2张
- 圆角改为 20dp

**Step 1.2** — 重构 [SpecialDateScreen.kt](app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt)
- LazyColumn → LazyVerticalGrid(columns = 2, 固定列宽)
- 页面边距改为 20dp
- 标题字号改为 24sp Bold
- 修复搜索栏在网格中的适配

**Step 1.3** — 更新 [DateGroupHeader.kt](app/src/main/java/com/corgimemo/app/ui/screens/date/components/DateGroupHeader.kt)
- 改为 `"── 即将到来（倒计时） ──"` 格式
- 占满整行（grid 的 span）

### Phase 2: 样式统一修正

**Step 2.1** — 修正 [InspirationCard.kt](app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationCard.kt)
- 卡片圆角 16→20dp
- 缩略图 48→32dp
- 标签圆角 12→20dp
- 新增关联提示显示（接收 hasRelation 参数）

**Step 2.2** — 修正 [InspirationScreen.kt](app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt)
- 边距 16→20dp
- 标题字号 20→24sp

**Step 2.3** — 修正 [SpecialDateEditScreen.kt](app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateEditScreen.kt)
- TopAppBar 背景色改用 MaterialTheme.colorScheme.surface
- 文字颜色改用 MaterialTheme.colorScheme.onSurface
- 保存按钮颜色改用 UiColors.Primary
- 修复编辑模式数据加载（通过 LaunchedEffect 从 repository 加载）

**Step 2.4** — 修正 [InspirationEditScreen.kt](app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationEditScreen.kt)
- 修复编辑模式数据加载（TODO → 真正的 repository 调用）

**Step 2.5** — 修正两个 EmptyState 组件的主文案颜色 #333333 → #2D2D2D

### Phase 3: 功能补全

**Step 3.1** — 为两个 Card 组件添加基础的长按菜单（AlertDialog: 编辑/删除）

**Step 3.2** — InspirationCard 补充关联提示数据传递和显示

---

## 四、影响范围

| 文件 | 修改类型 | 变更量 |
|------|---------|--------|
| SpecialDateCard.kt | **重写** | 布局从 Row→Column，新增竖条/透明度/颜色修正 |
| SpecialDateScreen.kt | **中度修改** | LazyColumn→LazyVerticalGrid + 样式修正 |
| DateGroupHeader.kt | **小修改** | 格式变更 + grid span |
| InspirationCard.kt | **中度修改** | 样式修正 + 关联提示 |
| InspirationScreen.kt | **小修改** | 样式修正 |
| InspirationEditScreen.kt | **小修改** | 数据加载修复 |
| SpecialDateEditScreen.kt | **小修改** | 样式修正 + 数据加载修复 |
| InspirationEmptyState.kt | **微调** | 颜色修正 |
| SpecialDateEmptyState.kt | **微调** | 颜色修正 |
