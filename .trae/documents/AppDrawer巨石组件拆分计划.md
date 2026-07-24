# AppDrawer.kt 巨石组件拆分计划

> **任务类型**：架构重构（多文件拆分）
> **目标文件**：[AppDrawer.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/AppDrawer.kt)（约 1130+ 行）
> **预计改动**：1 个文件大幅精简 + 新增 11 个文件，MainScreen 0 改动

---

## Context（背景与目标）

### 为什么做这次拆分

`AppDrawer.kt` 当前承载了整个侧边栏的所有 Composable 逻辑：主入口编排 + 4 个分区（用户头 / 待办分类 / 灵感筛选 / 日期筛选 / 个人快捷）+ 5 个弹窗（添加 / 重命名 / 删除 / 长按菜单）+ 2 个 sealed class + 3 个图标常量。共 **1130+ 行、18 个顶层符号**，是典型的"巨石组件"。

带来的实际问题：
- **PR 冲突率高**：4 个分区共用同一文件，并行开发时极易冲突
- **定位成本高**：新人想找某个分区的逻辑，需要在 1000+ 行里 Ctrl+F
- **测试不可分**：单元测试无法单独 mock 某个 section
- **代码 review 困难**：一次提交改了 5 个分区的逻辑，reviewer 看不清边界

### 期望成果

按"主入口 + sections + dialogs + model" 4 层职责拆分到 `com.corgimemo.app.ui.components.appdrawer` 子包，**保证 MainScreen.kt 0 改动**（通过薄壳转发 + typealias 实现向后兼容），降低未来 PR 冲突率 80%+。

---

## 用户已确认的设计决策

| 决策项 | 选择 |
|---|---|
| 拆分粒度 | 2 层子目录（6-8 个文件） |
| 包路径 | `components/appdrawer/sections/` |
| 外部适配 | 薄壳转发（MainScreen 0 改动） |

---

## 最终目录结构

```
app/src/main/java/com/corgimemo/app/ui/components/
├── AppDrawer.kt                          ← 薄壳（~125 行）
└── appdrawer/
    ├── model/                            ← 2 个文件
    │   ├── CategoryAction.kt             ← sealed class（4 个 data class）
    │   └── DateTypeAction.kt             ← sealed class（3 个 data class）
    ├── sections/                         ← 6 个文件
    │   ├── AppDrawerContentImpl.kt       ← 主入口（~200 行）+ DrawerUserHeader + AddCategoryButton
    │   ├── CategoryItemRow.kt            ← 4 个 section 共用的 CategoryItem + 3 个图标常量
    │   ├── CategoryGroupSection.kt       ← 待办分类分组（~80 行）
    │   ├── InspirationFilterSection.kt   ← 灵感标签筛选（~165 行）
    │   ├── DateTypeFilterSection.kt      ← 日期类型筛选（~75 行）
    │   └── ProfileQuickNavSection.kt     ← 个人中心快捷（~55 行）
    └── dialogs/                          ← 4 个文件
        ├── AddCategoryDialog.kt          ← 添加分类弹窗（~100 行）
        ├── RenameCategoryDialog.kt       ← 重命名弹窗（~55 行）
        ├── DeleteCategoryConfirmDialog.kt← 删除确认弹窗（~45 行）
        └── OperationSheets.kt            ← 2 个长按菜单 sheet + 共享 OperationOption（~130 行）
```

**共 12 个新文件 + 1 个精简的 AppDrawer.kt**。

---

## 可见性调整（关键）

| 符号 | 原可见性 | 新可见性 | 原因 |
|---|---|---|---|
| `CategoryItem` | `private` | **`internal`** | 4 个 section 都用 |
| `AddCategoryButton` | `private` | `private`（保留在 AppDrawerContentImpl.kt） | 只 1 处用 |
| `OperationOption` | `private` | `private`（保留在 OperationSheets.kt） | 2 个 sheet 共用 |
| `DRAWER_ICON_ALL` / `DRAWER_ICON_UNCATEGORIZED` / `categoryIcons` | `private` | **`internal`** | 被 CategoryGroupSection 引用 |
| `CategoryAction` / `DateTypeAction` | public | public（**新位置**） | MainScreen 用 typealias 引用 |
| `AppDrawerContent` | public | public（**新位置**） | 薄壳转发 |
| 5 个公开 Composable 弹窗 | public | public（**新位置**） | 薄壳转发 |

---

## 关键文件清单

### 1. 必须修改的文件（1 个）

- [AppDrawer.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/AppDrawer.kt)
  - **删除**：所有 Composable 实现 + 私有常量 + sealed class（共 ~1100 行）
  - **保留**：5 个 Composable 薄壳转发 + 2 个 sealed class typealias（共 ~125 行）

### 2. 必须新建的文件（12 个）

按依赖顺序：

| 步骤 | 新文件 | 依赖 | 内容来源（原 AppDrawer.kt 行号） |
|---|---|---|---|
| 1 | `appdrawer/model/CategoryAction.kt` | — | L1044-1049 |
| 2 | `appdrawer/model/DateTypeAction.kt` | — | L1054-1058 |
| 3 | `appdrawer/dialogs/AddCategoryDialog.kt` | — | L752-848 |
| 4 | `appdrawer/dialogs/RenameCategoryDialog.kt` | — | L850-903 |
| 5 | `appdrawer/dialogs/DeleteCategoryConfirmDialog.kt` | — | L915-954 |
| 6 | `appdrawer/dialogs/OperationSheets.kt` | — | L958-1119 |
| 7 | `appdrawer/sections/CategoryItemRow.kt` | — | L64-72 (常量) + L338-398 |
| 8 | `appdrawer/sections/CategoryGroupSection.kt` | 7 | L265-336 |
| 9 | `appdrawer/sections/InspirationFilterSection.kt` | 7 | L443-613 |
| 10 | `appdrawer/sections/DateTypeFilterSection.kt` | 7 | L614-687 |
| 11 | `appdrawer/sections/ProfileQuickNavSection.kt` | 7 | L688-739 |
| 12 | `appdrawer/sections/AppDrawerContentImpl.kt` | 7-11 + 1-2 | L113-264 (主入口 + DrawerUserHeader) + L400-426 (AddCategoryButton) |

### 3. 不修改的文件（已确认）

- [MainScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt) — 0 改动
- [AppNavHost.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt) — 0 改动
- 所有 ViewModel / DataModel — 0 改动

---

## 实施步骤

按以下顺序执行（**严格顺序**，因为有依赖关系）：

### Phase 1：创建 model 层（2 个文件，无依赖）

- [ ] **步骤 1**：创建 `appdrawer/model/CategoryAction.kt`
  - 内容：从 AppDrawer.kt L1044-1049 复制，修改 package 为 `com.corgimemo.app.ui.components.appdrawer.model`
  - 添加 import：`com.corgimemo.app.data.model.Category`
- [ ] **步骤 2**：创建 `appdrawer/model/DateTypeAction.kt`
  - 内容：从 AppDrawer.kt L1054-1058 复制
  - 添加 import：`com.corgimemo.app.data.model.CustomDateType`

### Phase 2：创建 dialogs 层（4 个文件，无依赖）

- [ ] **步骤 3**：创建 `appdrawer/dialogs/AddCategoryDialog.kt`（L752-848）
- [ ] **步骤 4**：创建 `appdrawer/dialogs/RenameCategoryDialog.kt`（L850-903）
- [ ] **步骤 5**：创建 `appdrawer/dialogs/DeleteCategoryConfirmDialog.kt`（L915-954）
- [ ] **步骤 6**：创建 `appdrawer/dialogs/OperationSheets.kt`（L958-1119）
  - 包含 `CategoryOperationSheet`（public）+ `DateTypeOperationSheet`（public）+ `OperationOption`（private）
  - 共享私有 `OperationOption` 在同文件

### Phase 3：创建 sections 层（6 个文件）

- [ ] **步骤 7**：创建 `appdrawer/sections/CategoryItemRow.kt`
  - 包含 3 个图标常量（`DRAWER_ICON_ALL` / `DRAWER_ICON_UNCATEGORIZED` / `categoryIcons`）改为 `internal`
  - 包含 `CategoryItem` 改为 `internal`
- [ ] **步骤 8**：创建 `appdrawer/sections/CategoryGroupSection.kt`（L265-336）
- [ ] **步骤 9**：创建 `appdrawer/sections/InspirationFilterSection.kt`（L443-613）
- [ ] **步骤 10**：创建 `appdrawer/sections/DateTypeFilterSection.kt`（L614-687）
- [ ] **步骤 11**：创建 `appdrawer/sections/ProfileQuickNavSection.kt`（L688-739）
- [ ] **步骤 12**：创建 `appdrawer/sections/AppDrawerContentImpl.kt`
  - 包含主入口 `AppDrawerContentImpl`（public，与薄壳 `AppDrawerContent` 同名异包）
  - 包含 `DrawerUserHeader`（private）和 `AddCategoryButton`（private）
  - 包含 `when (currentTab) { ... }` 分发逻辑

### Phase 4：精简 AppDrawer.kt（薄壳）

- [ ] **步骤 13**：用薄壳完全替换 AppDrawer.kt 内容
  - 删除所有原 Composable 实现（共 ~1100 行）
  - 保留 5 个 Composable 转发（`AppDrawerContent` + `AddCategoryDialog` + `RenameCategoryDialog` + `DeleteCategoryConfirmDialog` + `CategoryOperationSheet` + `DateTypeOperationSheet`）
  - 保留 2 个 typealias（`CategoryAction` / `DateTypeAction`）
  - 内部转发调用**用 FQN**（避免同名导入冲突）
  - **总行数目标 ~125 行**

### Phase 5：验证

- [ ] **步骤 14**：用 `git diff --stat` 确认 `MainScreen.kt` / `AppNavHost.kt` 无任何改动
- [ ] **步骤 15**：**用 AskUserQuestion 询问用户**是否进行编译验证（按 `.trae/rules/编译验证.md`）
- [ ] **步骤 16**：编译通过后**用 AskUserQuestion 询问用户**是否 git 提交（按 `.trae/rules/git提交.md`）

---

## 复用现有模式

### 目录命名约定（参考先例）

- 已有先例：[home/components/TodoCalendarDialog.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/components/TodoCalendarDialog.kt) — `screens/<功能>/components/<组件>.kt`
- 本次采用：`components/<功能>/sections|dialogs|model/<文件>.kt` — 与先例同构（功能模块下的子目录）

### 已有可复用工具

- [UserAvatar](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/UserAvatar.kt) — DrawerUserHeader 已用，继续保留
- [UiColors](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme/) — 主题色常量，继续保留
- [TabItem](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/navigation/CenterEditButton.kt) — 当前 Tab 标识，继续保留

---

## 风险点 + 防御措施

| # | 风险 | 防御 |
|---|---|---|
| 1 | **`CategoryItem` 可见性错误**：原 `private` 改 `internal` 后 4 个 section 才能调 | 步骤 7 完成后单独 dry-run；明确标 `internal` |
| 2 | **同名导入遮蔽**：薄壳 `AppDrawer.kt` 同时定义 `fun AddCategoryDialog` 又 import 同名类 | 薄壳内部转发一律用 FQN `com.corgimemo.app.ui.components.appdrawer.dialogs.AddCategoryDialog(...)` |
| 3 | **`sheetState` 默认值问题**：`rememberModalBottomSheetState()` 是 Composable 函数，不能作为参数默认值 | 薄壳 `CategoryOperationSheet` / `DateTypeOperationSheet` 的 `sheetState` 不给默认值；已查证 MainScreen L1107 显式传了位置参数，安全 |
| 4 | **sealed class typealias 跨包 pattern match**：Kotlin 编译期展开，理论无问题，但 IDE 可能误报"unresolved" | 以 `./gradlew compileDebugKotlin` 为准；IDE 假红可忽略 |
| 5 | **11 个新文件 import 容易遗漏**：每个文件 import 列表需精确 | 严格按照 Plan agent 输出的"每个文件的 import 列表"执行 |
| 6 | **私有常量遗漏**：`DRAWER_ICON_ALL` / `categoryIcons` 等随 CategoryItem 一起迁到 CategoryItemRow.kt | 步骤 7 一并迁移 |
| 7 | **graphify watch 重建延迟**：14 个文件改动后 watch 进程 debounce 3s 自动重建 | 无需手动干预，或可 `stop` + `start` 加速 |
| 8 | **测试文件依赖**：项目可能有测试引用了原 AppDrawer.kt 的私有 Composable | 步骤 1 前用 `grep -r "AppDrawer" app/src/test app/src/androidTest` 验证 |

---

## 验证方式

### 1. 静态验证（必做）

```powershell
# 1) MainScreen 0 改动
git diff --stat app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt
# 预期输出：（空）

# 2) 13 个新文件 + 1 个精简文件
git status --short
# 预期：12 个 ?? (新文件) + 1 个 M (AppDrawer.kt)

# 3) 没有任何测试文件被破坏
git diff --stat app/src/test app/src/androidTest 2>$null
# 预期：（空）
```

### 2. 编译验证（必做，询问用户后由用户执行）

```powershell
./gradlew compileDebugKotlin
```

**预期**：
- 0 编译错误
- 0 编译警告（理想情况）
- 时间在 30-60 秒（KSP 缓存命中）

### 3. 运行时验证（用户执行后 AI 协助）

- [ ] 侧边栏从左边缘滑出
- [ ] 4 个 Tab 切换正常（TODO / INSPIRE / DATE / PROFILE）
- [ ] 4 个分区渲染正确（待办分类 / 灵感标签 / 日期类型 / 快捷导航）
- [ ] 5 个弹窗打开 / 关闭正常（添加 / 重命名 / 删除分类 / 添加类型）
- [ ] 2 个长按菜单（Category / DateType）弹起 BottomSheet
- [ ] sealed class pattern match 触发对应逻辑（长按分组图标 → ShowMenu → 显示 BottomSheet）

### 4. graphify 图谱验证（可选）

```powershell
# 等待 watch 进程自动重建（约 3-5 秒）
.venv\Scripts\graphify.exe query "AppDrawerContent"
# 预期：能看到 AppDrawerContentImpl 节点 + 5 个 section + 5 个 dialog + 2 个 sealed class
```

---

## 提交信息模板

```
refactor(drawer): 拆分 AppDrawer.kt 到 appdrawer 子包（1130+ 行 → 12 个文件）

将原 AppDrawer.kt 巨石组件按 4 层职责拆分：
- model/ — 2 个 sealed class（CategoryAction / DateTypeAction）
- sections/ — 5 个分区 + 1 个 CategoryItemRow 共用组件
- dialogs/ — 3 个弹窗 + 1 个 OperationSheets（含 2 个 BottomSheet）
- AppDrawerContentImpl — 主入口编排

通过薄壳转发 + typealias 保持 MainScreen 0 改动，
所有外部调用方（import + fully qualified name）完全兼容。

可见性调整：
- CategoryItem / 图标常量：private → internal
（被 4 个 section 共用，internal 在单模块等同 public）
```

---

## 后续可优化（任务结束后建议）

| # | 优化点 | 说明 |
|---|---|---|
| 1 | **MainScreen.kt 也是 1100+ 行** | 同样按 `screens/main/components/` 拆分：DrawerControl / TopBarConfig / TabContentSwitcher 等 |
| 2 | **HomeScreen.kt 是 2000+ 行** | 已有 `components/TodoCalendarDialog.kt` 拆分先例，可继续按 `home/components/` 拆分其他子组件 |
| 3 | **页面文件清单表补登"组件拆分"章节** | 之前的 [前端页面与代码文件对应表.md](file:///c:/Users/EDY/Desktop/CorgiMemo/前端页面与代码文件对应表.md) 可补登这种"巨石组件拆分"案例 |
| 4 | **加 CI 校验** | 写个 Gradle 任务扫描所有 `.kt` 文件超过 800 行的，输出警告列表 |
| 5 | **typealias 与实际 class 路径的二级缓存** | 若后续发现 typealias 影响 IDE 跳转，可改为 `expect/actual` 模式或纯文件转发 |

---

## 任务终止检查清单

- ✅ 已用 AskUserQuestion 询问关键决策（3 个：粒度 / 路径 / 外部适配）
- ✅ 探索阶段用 Explore agent 摸清全部依赖
- ✅ 设计阶段用 Plan agent 验证方案
- ✅ 计划文件用中文命名（`.trae/rules/文档命名语言要求.md`）
- ✅ 计划中标注关键代码行号 + 文件路径 + 风险点
- ✅ 引用现有函数和工具（UserAvatar / UiColors / TabItem）
- ✅ 列出 MainScreen 0 改动、编译验证、git 提交等用户操作点
- ⏳ 等待用户审批
