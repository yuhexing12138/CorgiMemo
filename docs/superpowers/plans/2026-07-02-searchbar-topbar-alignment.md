# 搜索栏与导航栏精确对齐 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 调整"我的待办"、"灵感"、"日期"三个 Tab 页面搜索栏的 padding 配置，使其顶部边缘精确等于 EnhancedTopBar 底部边缘（零间距对齐），并在所有页面状态（默认、滚动、批量模式、异形屏）下保持视觉一致性。

**Architecture:** 保持现有布局机制不变，对三个 Tab 页面的 SearchBar modifier 统一调整：移除顶部 8dp padding（改为仅保留底部 8dp）。HomeScreen 使用 `searchRevealProgress` 滚动隐藏动画，InspirationScreen 和 SpecialDateScreen 搜索栏为固定显示，三者变更点相同。通过新增 dimens 常量 `ui_search_bar_bottom_margin` 提供修改入口，并使用 `dimensionResource()` 引用以保持 XML 与 Kotlin 同步。

**Tech Stack:** Jetpack Compose (Modifier.padding, dimensionResource), Android Resources (dimens.xml)

**参考设计文档：** [2026-07-02-searchbar-topbar-alignment-design.md](file:///C:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-02-searchbar-topbar-alignment-design.md)

**影响范围（4 个 Tab 中 3 个有搜索栏）：**
- ✅ 我的待办 (HomeScreen.kt) — 有搜索栏
- ✅ 灵感 (InspirationScreen.kt) — 有搜索栏
- ✅ 日期 (SpecialDateScreen.kt) — 有搜索栏
- ❌ 我的 (ProfileScreen.kt) — 无搜索栏，无需调整

---

## 文件结构

**修改文件：**
- `app/src/main/res/values/dimens.xml` — 新增 `ui_search_bar_bottom_margin` 常量
- `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` — 调整 SearchBar padding，添加 dimensionResource import
- `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt` — 调整 SearchBar padding，添加 dimensionResource import
- `app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt` — 调整 SearchBar padding，添加 dimensionResource import

**未修改文件：**
- `app/src/main/java/com/corgimemo/app/ui/components/EnhancedTopBar.kt` — 保持不变
- `app/src/main/java/com/corgimemo/app/ui/components/SearchBar.kt` — 保持不变
- `app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt` — 保持不变
- `app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt` — 无搜索栏，保持不变

---

### Task 1: dimens.xml 新增 ui_search_bar_bottom_margin 常量

**Files:**
- Modify: `app/src/main/res/values/dimens.xml:37-39`

- [ ] **Step 1: 定位搜索栏常量区**

打开 [dimens.xml](file:///C:/Users/EDY/Desktop/CorgiMemo/app/src/main/res/values/dimens.xml)，定位到第 37-39 行的"搜索栏"区：

```xml
<!-- ========== 搜索栏 ========== -->
<dimen name="ui_search_bar_height">48dp</dimen>
<dimen name="ui_search_bar_corner_radius">24dp</dimen>
```

- [ ] **Step 2: 在 ui_search_bar_corner_radius 行后添加新常量**

在第 39 行 `</dimen>` 之后、第 41 行 `<!-- ========== FAB ========== -->` 之前，添加：

```xml
<!-- ========== 搜索栏 ========== -->
<dimen name="ui_search_bar_height">48dp</dimen>
<dimen name="ui_search_bar_corner_radius">24dp</dimen>
<dimen name="ui_search_bar_bottom_margin">8dp</dimen>
```

**说明：**
- 该常量表示搜索栏容器与其下方列表内容之间的底部间距
- 仅作用于搜索栏底部（顶部 = 0dp，紧贴导航栏）
- 8dp 取自设计规范中"元素间距 8dp"

- [ ] **Step 3: 验证 XML 格式正确**

检查文件结构：
- 每个 `<dimen>` 标签正确闭合
- 无多余空格或换行符
- 文件末尾 `</resources>` 标签存在

- [ ] **Step 4: 提交 dimens.xml 变更**

```bash
git add app/src/main/res/values/dimens.xml
git commit -m "feat(dimens): 新增搜索栏底部间距常量 ui_search_bar_bottom_margin"
```

---

### Task 2: HomeScreen.kt 添加 dimensionResource import

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 定位 import 区域**

打开 [HomeScreen.kt](file:///C:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt)，定位到 import 区域（约第 3-141 行）。

- [ ] **Step 2: 添加 dimensionResource import**

在合适的 `import androidx.compose.ui.platform.*` 附近添加：

```kotlin
import androidx.compose.ui.res.dimensionResource
```

**建议位置：** 紧跟 `import androidx.compose.ui.platform.LocalContext`（约第 138 行）之后

- [ ] **Step 3: 验证 import 不重复**

使用编辑器搜索 `dimensionResource`，确保整个文件中没有重复 import。

- [ ] **Step 4: 提交 import 变更**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(home): 添加 dimensionResource import 以引用搜索栏底部间距"
```

---

### Task 3: HomeScreen.kt 调整 SearchBar padding 配置

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt:620-659`（SearchBar 容器区域）

- [ ] **Step 1: 定位 SearchBar 容器代码**

打开 [HomeScreen.kt](file:///C:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt)，定位到第 620-659 行的 SearchBar 容器 Box：

```kotlin
Box(
    modifier = Modifier
        .fillMaxWidth()
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val targetHeight = (placeable.height * searchRevealProgress.value).roundToInt()
            layout(placeable.width, targetHeight) {
                // place(0, 0) 保证上边缘固定
                placeable.place(0, 0)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = searchRevealProgress.value }
) {
    SearchBar(
        query = searchQuery,
        onQueryChange = { newQuery ->
            viewModel.updateSearchQuery(newQuery)
        },
        onClear = {
            viewModel.clearSearch()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),  // ← 待修改
        trailingIcon = { ... }
    )
}
```

- [ ] **Step 2: 修改 SearchBar 的 modifier padding**

将 SearchBar 的 modifier 从：

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 20.dp, vertical = 8.dp),
```

修改为：

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 20.dp)
    .padding(bottom = dimensionResource(R.dimen.ui_search_bar_bottom_margin)),
```

**变更要点：**
- 移除 `vertical = 8.dp`（即移除顶部 8dp padding）
- 显式添加 `bottom = dimensionResource(R.dimen.ui_search_bar_bottom_margin)` 保留底部 8dp 间距
- 使用 `dimensionResource` 而非 `8.dp` 字面量，确保与 dimens.xml 同步

- [ ] **Step 3: 检查外层 Box 的 layout 逻辑未受影响**

确认第 623-630 行的 `layout { }` 代码块未修改：

```kotlin
.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val targetHeight = (placeable.height * searchRevealProgress.value).roundToInt()
    layout(placeable.width, targetHeight) {
        // place(0, 0) 保证上边缘固定
        placeable.place(0, 0)
    }
}
```

**关键不变量：** `placeable.place(0, 0)` 保证上边缘固定，因此搜索栏顶部位置不会因 padding 调整而漂移。

- [ ] **Step 4: 检查 R 类引用**

确认 `R.dimen.ui_search_bar_bottom_margin` 在 IDE 中能正常解析（无红色下划线）。

如果出现 `Unresolved reference` 错误，可能原因：
- dimens.xml 未正确保存（重新保存并等待 IDE 索引更新）
- import 缺失（回到 Task 2 检查）

- [ ] **Step 5: 提交 HomeScreen.kt 变更**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(home): 移除搜索栏顶部 8dp padding 实现与导航栏精确对齐"
```

---

### Task 4: 手动验证对齐效果

**Files:**
- 无（仅验证）

- [ ] **Step 1: 编译项目**

```bash
./gradlew assembleDebug
```

**预期结果：** 编译成功，无错误。

- [ ] **Step 2: 启动模拟器并安装 APK**

```bash
./gradlew installDebug
```

**预期结果：** 安装成功，应用启动到"我的待办"页面。

- [ ] **Step 3: 默认状态验证**

进入"我的待办"页面，观察搜索栏顶部与导航栏底部。

**预期结果：** 两者之间**无可见间隙**，视觉上"无缝衔接"。

- [ ] **Step 4: 输入搜索词验证**

点击搜索框输入任意文字（如"测试"），观察搜索栏顶部。

**预期结果：** 顶部仍与导航栏底部对齐，无漂移。

- [ ] **Step 5: 滚动列表验证**

向上滚动列表，让搜索框开始收缩隐藏。

**预期结果：** 搜索框收缩过程中，**顶部锚点固定**（不向下漂移）。

继续向下滚动到顶部，让搜索框完全展开。

**预期结果：** 搜索框展开后，顶部仍与导航栏底部对齐。

- [ ] **Step 6: 批量模式验证**

长按任意待办卡片进入批量模式。

**预期结果：** 搜索栏完全隐藏（alpha = 0），无残留间隙或残留阴影。

退出批量模式后，搜索栏重新出现，仍与导航栏对齐。

- [ ] **Step 7: Tab 切换验证**

依次切换到"灵感"、"日期"、"我的"三个 Tab，再切回"待办"。

**预期结果：** 切回"待办"时，搜索栏与导航栏仍精确对齐。

- [ ] **Step 8: 横屏验证（可选）**

旋转设备到横屏（模拟器快捷键 `Ctrl+F11` 或 `Cmd+←`）。

**预期结果：** 对齐关系在横屏下保持一致。

- [ ] **Step 9: 异形屏验证（可选）**

在 Android 模拟器中：
1. 进入开发者选项 → 启用"强制使用刘海屏"
2. 重启应用

**预期结果：** 状态栏高度变化后，搜索栏与导航栏仍精确对齐（无 8dp 间隙出现）。

- [ ] **Step 10: 提交验证记录**

如果以上 9 步全部通过，无需提交新代码。如发现任何视觉问题，记录在 issues 中并修复。

### Task 5: InspirationScreen.kt 添加 dimensionResource import

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt`

- [ ] **Step 1: 定位 import 区域**

打开 [InspirationScreen.kt](file:///C:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt)，定位到 import 区域。

- [ ] **Step 2: 添加 dimensionResource import**

在 `import androidx.compose.ui.platform.*` 附近添加：

```kotlin
import androidx.compose.ui.res.dimensionResource
```

**建议位置：** 紧跟现有的 `androidx.compose.ui.platform.*` 相关 import 之后

- [ ] **Step 3: 验证 import 不重复**

使用编辑器搜索 `dimensionResource`，确保整个文件中没有重复 import。

---

### Task 6: InspirationScreen.kt 调整 SearchBar padding

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt:62-75`

- [ ] **Step 1: 定位 SearchBar 代码**

定位到第 62-75 行的 SearchBar 组件：

```kotlin
SearchBar(
    query = searchQuery,
    onQueryChange = { newQuery ->
        viewModel.search(newQuery)
    },
    onClear = {
        viewModel.clearSearch()
    },
    placeholder = "搜索灵感...",
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)  // ← 待修改
)
```

- [ ] **Step 2: 修改 SearchBar 的 modifier padding**

将 SearchBar 的 modifier 从：

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 20.dp, vertical = 8.dp)
```

修改为：

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 20.dp)
    .padding(bottom = dimensionResource(R.dimen.ui_search_bar_bottom_margin))
```

- [ ] **Step 3: 检查 Spacer(8.dp) 是否需要同步调整**

第 77 行有 `Spacer(modifier = Modifier.height(8.dp))`，位于 SearchBar 之后、列表内容之前。

**评估：**
- 原设计：SearchBar 底部 8dp padding + Spacer 8dp = 16dp 总间距
- 新设计：SearchBar 底部 8dp padding（来自 dimens）+ Spacer 8dp = 16dp 总间距
- **结论：** Spacer 保持不变，搜索栏与列表之间仍保留 16dp 视觉间距

- [ ] **Step 4: 检查 R 类引用**

确认 `R.dimen.ui_search_bar_bottom_margin` 在 IDE 中能正常解析（无红色下划线）。

- [ ] **Step 5: 提交 InspirationScreen 变更**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt
git commit -m "feat(inspiration): 移除搜索栏顶部 8dp padding 实现与导航栏精确对齐"
```

---

### Task 7: SpecialDateScreen.kt 添加 dimensionResource import

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt`

- [ ] **Step 1: 定位 import 区域**

打开 [SpecialDateScreen.kt](file:///C:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt)，定位到 import 区域。

- [ ] **Step 2: 添加 dimensionResource import**

在 `import androidx.compose.ui.platform.*` 附近添加：

```kotlin
import androidx.compose.ui.res.dimensionResource
```

- [ ] **Step 3: 验证 import 不重复**

使用编辑器搜索 `dimensionResource`，确保整个文件中没有重复 import。

---

### Task 8: SpecialDateScreen.kt 调整 SearchBar padding

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt:62-70`

- [ ] **Step 1: 定位 SearchBar 代码**

定位到第 62-70 行的 SearchBar 组件：

```kotlin
SearchBar(
    query = searchQuery,
    onQueryChange = { viewModel.updateSearchQuery(it) },
    onClear = { viewModel.updateSearchQuery("") },
    placeholder = "搜索日期...",
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 8.dp)  // ← 待修改
)
```

- [ ] **Step 2: 修改 SearchBar 的 modifier padding**

将 SearchBar 的 modifier 从：

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 20.dp, vertical = 8.dp)
```

修改为：

```kotlin
modifier = Modifier
    .fillMaxWidth()
    .padding(horizontal = 20.dp)
    .padding(bottom = dimensionResource(R.dimen.ui_search_bar_bottom_margin))
```

- [ ] **Step 3: 检查 Spacer(8.dp) 是否需要同步调整**

第 72 行有 `Spacer(modifier = Modifier.height(8.dp))`。

**评估：** 与 InspirationScreen 相同，Spacer 保持不变，搜索栏与列表之间仍保留 16dp 视觉间距。

- [ ] **Step 4: 检查 R 类引用**

确认 `R.dimen.ui_search_bar_bottom_margin` 在 IDE 中能正常解析。

- [ ] **Step 5: 提交 SpecialDateScreen 变更**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt
git commit -m "feat(date): 移除搜索栏顶部 8dp padding 实现与导航栏精确对齐"
```

---

### Task 9: 多 Tab 验证对齐效果

**Files:**
- 无（仅验证）

- [ ] **Step 1: 编译项目**

```bash
./gradlew assembleDebug
```

**预期结果：** 编译成功，无错误。

- [ ] **Step 2: 启动模拟器并安装 APK**

```bash
./gradlew installDebug
```

**预期结果：** 安装成功，应用启动。

- [ ] **Step 3: "我的待办" Tab 默认状态验证**

进入"我的待办"页面。

**预期结果：** 搜索栏顶部与导航栏底部无间隙，零间距对齐。

- [ ] **Step 4: "我的待办" Tab 滚动验证**

向上滚动列表，让搜索框开始收缩隐藏。

**预期结果：** 搜索框收缩过程中，顶部锚点固定（不向下漂移）。

- [ ] **Step 5: "我的待办" Tab 批量模式验证**

长按任意待办卡片进入批量模式。

**预期结果：** 搜索栏完全隐藏（alpha = 0），无残留间隙。

- [ ] **Step 6: "灵感" Tab 验证**

切换到"灵感" Tab。

**预期结果：** 搜索栏顶部与导航栏底部无间隙，零间距对齐。

- [ ] **Step 7: "灵感" Tab 滚动验证**

滚动灵感列表（如果有滚动内容）。

**预期结果：** 搜索栏保持固定位置，与导航栏对齐（灵感页搜索栏不滚动隐藏）。

- [ ] **Step 8: "日期" Tab 验证**

切换到"日期" Tab。

**预期结果：** 搜索栏顶部与导航栏底部无间隙，零间距对齐。

- [ ] **Step 9: "日期" Tab 滚动验证**

滚动日期列表（如果有滚动内容）。

**预期结果：** 搜索栏保持固定位置，与导航栏对齐。

- [ ] **Step 10: "我的" Tab 验证**

切换到"我的" Tab。

**预期结果：** 导航栏与下方内容（如设置项列表）紧贴，无意外间隙（此页无搜索栏，主要验证导航栏与列表的衔接）。

- [ ] **Step 11: 异形屏验证（可选）**

在 Android 模拟器中启用"强制使用刘海屏"后重启应用。

**预期结果：** 状态栏高度变化后，三个 Tab 的搜索栏与导航栏仍精确对齐。

- [ ] **Step 12: 横屏验证（可选）**

旋转设备到横屏。

**预期结果：** 对齐关系在横屏下保持一致。

---

## 自审检查

**1. Spec 覆盖：**
- ✅ 默认状态对齐 → Task 9 Step 3、6、8、10
- ✅ 滚动动画保持（HomeScreen） → Task 9 Step 4
- ✅ 批量模式隐藏（HomeScreen） → Task 9 Step 5
- ✅ 异形屏适配 → Task 9 Step 11
- ✅ 横屏适配 → Task 9 Step 12
- ✅ dimens 常量定义 → Task 1
- ✅ HomeScreen 代码变更 → Task 2、Task 3
- ✅ InspirationScreen 代码变更 → Task 5、Task 6
- ✅ SpecialDateScreen 代码变更 → Task 7、Task 8
- ✅ 多 Tab 测试方案 → Task 9

**2. 占位符扫描：**
- ✅ 无 TBD/TODO
- ✅ 所有代码片段完整
- ✅ 所有命令完整

**3. 类型一致性：**
- ✅ `R.dimen.ui_search_bar_bottom_margin` 在 Task 1 定义，在 Task 3、Task 6、Task 8 引用
- ✅ `dimensionResource` 在 Task 2、Task 5、Task 7 import，在 Task 3、Task 6、Task 8 使用
- ✅ 文件路径、行号准确

**4. 范围扩展检查：**
- ✅ InspirationScreen 和 SpecialDateScreen 搜索栏结构与 HomeScreen 类似
- ✅ InspirationScreen 和 SpecialDateScreen 搜索栏为固定显示，无 searchRevealProgress 机制
- ✅ 不影响 ProfileScreen（无搜索栏）

---

## 实施完成标准

- [ ] Task 1 完成（dimens.xml 新增常量）
- [ ] Task 2 完成（HomeScreen import）
- [ ] Task 3 完成（HomeScreen padding 调整）
- [ ] Task 4 完成（HomeScreen commit）
- [ ] Task 5 完成（InspirationScreen import）
- [ ] Task 6 完成（InspirationScreen padding 调整 + commit）
- [ ] Task 7 完成（SpecialDateScreen import）
- [ ] Task 8 完成（SpecialDateScreen padding 调整 + commit）
- [ ] Task 9 全部 12 步手动验证通过
- [ ] 5 个 commit 全部完成
- [ ] 无编译错误、无运行时崩溃

**完成后报告实施结果。**
