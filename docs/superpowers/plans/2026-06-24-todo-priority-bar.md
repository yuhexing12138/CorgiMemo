# 待办卡片优先级竖条 + 优先级颜色统一重构 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在待办卡片左侧添加 4dp 宽度、颜色根据优先级变化的竖线条，同时统一项目内所有 UI 渲染层的优先级颜色到 UI 设计规范。

**Architecture:**
1. 新增 `PriorityColors.kt` 作为单一颜色源
2. 新增私有 `PriorityBar` composable 渲染竖条（带 `animateColorAsState` 200ms 过渡）
3. 重构 `TodoListItem` 的 Card 布局：内层 Column → Row，竖条作为最左子元素
4. 自底向上替换所有硬编码优先级颜色引用

**Tech Stack:** Kotlin / Jetpack Compose / Material3

**Spec:** `docs/superpowers/specs/2026-06-24-todo-priority-bar-design.md`

---

## 文件清单

| 类型 | 路径 | 职责 |
|------|------|------|
| 新增 | `app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt` | 优先级颜色单一源（High/Medium/Low/None + colorOf 函数） |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/components/PriorityDot.kt` | `TodoPriority` 枚举颜色改为引用 `PriorityColors` |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/components/PriorityBadge.kt` | `when` 分支颜色改为引用 `PriorityColors` |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt` | line 443-445 硬编码颜色改为 `PriorityColors.colorOf()` |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt` | line 1608-1610 弹窗选项颜色改为 `PriorityColors` 引用 |
| 修改 | `app/src/main/java/com/corgimemo/app/backup/exporter/ShareCardComponent.kt` | line 362-364 硬编码颜色改为 `PriorityColors.colorOf()` |
| 修改 | `app/src/main/java/com/corgimemo/app/backup/exporter/ImageExporter.kt` | line 375-377 硬编码颜色改为 `PriorityColors.colorOf()` |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt` | 删除 `PriorityDot` 调用；新增私有 `PriorityBar`；重构 Card 布局（Column → Row） |

---

## Task 1: 新增 PriorityColors.kt

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt`

- [ ] **Step 1: 创建文件**

文件路径：`app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt`

完整内容：

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.ui.graphics.Color

/**
 * 优先级颜色统一源（与 UI 设计规范对齐）
 *
 * 数值约定（与待办编辑页保持一致）：
 * 0 = 无优先级（不显示色条）
 * 1 = 低
 * 2 = 中
 * 3 = 高
 *
 * 颜色来源：[UI 设计规范] §12.1.2.3 功能色
 *   - 高优先级：#FF8A80（柔红）
 *   - 中优先级：#FFB74D（柔橙）
 *   - 低优先级：#90CAF9（柔蓝）
 */
object PriorityColors {
    /** 高优先级 - 柔红（避免焦虑） */
    val High = Color(0xFFFF8A80)

    /** 中优先级 - 柔橙 */
    val Medium = Color(0xFFFFB74D)

    /** 低优先级 - 柔蓝 */
    val Low = Color(0xFF90CAF9)

    /** 无优先级 - 透明 */
    val None = Color.Transparent

    /**
     * 数值 → 颜色
     *
     * @param priority 优先级数值（0=无、1=低、2=中、3=高）
     * @return 对应颜色；0 或其他非法值返回 None（透明）
     */
    fun colorOf(priority: Int): Color = when (priority) {
        3 -> High
        2 -> Medium
        1 -> Low
        else -> None
    }
}
```

- [ ] **Step 2: 验证编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin -q`（或 IDE 同步）
预期：BUILD SUCCESSFUL，无错误

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt
git commit -m "feat(priority): 新增 PriorityColors 统一颜色源"
```

---

## Task 2: 更新 PriorityDot.kt 颜色

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/PriorityDot.kt:22-31`

- [ ] **Step 1: 修改 TodoPriority 枚举的颜色字段**

将：
```kotlin
enum class TodoPriority(val color: Color, val displayName: String) {
    /** 高优先级 - 红色 */
    HIGH(Color(0xFFEF4444), "高"),

    /** 中优先级 - 黄色/橙色 */
    MEDIUM(Color(0xFFF59E0B), "中"),

    /** 低优先级 - 绿色 */
    LOW(Color(0xFF10B981), "低")
}
```

改为：
```kotlin
enum class TodoPriority(val color: Color, val displayName: String) {
    /** 高优先级 - 柔红（与 UI 设计规范对齐） */
    HIGH(PriorityColors.High, "高"),

    /** 中优先级 - 柔橙（与 UI 设计规范对齐） */
    MEDIUM(PriorityColors.Medium, "中"),

    /** 低优先级 - 柔蓝（与 UI 设计规范对齐） */
    LOW(PriorityColors.Low, "低")
}
```

注释由"红色/黄色/绿色"统一改为"柔红/柔橙/柔蓝"。

- [ ] **Step 2: 验证编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/PriorityDot.kt
git commit -m "refactor(priority): PriorityDot 颜色改为引用 PriorityColors"
```

---

## Task 3: 更新 PriorityBadge.kt 颜色

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/PriorityBadge.kt:16-20`

- [ ] **Step 1: 修改 when 分支的颜色**

将：
```kotlin
val (text, color, backgroundColor) = when (priority) {
    2 -> Triple("高", Color(0xFFDC2626), Color(0xFFFFE4E6))
    1 -> Triple("中", Color(0xFFD97706), Color(0xFFFFF3E0))
    else -> Triple("低", Color(0xFF16A34A), Color(0xFFECFDF5))
}
```

改为：
```kotlin
val (text, color, backgroundColor) = when (priority) {
    2 -> Triple("高", PriorityColors.High, Color(0xFFFFE4E6))
    1 -> Triple("中", PriorityColors.Medium, Color(0xFFFFF3E0))
    else -> Triple("低", PriorityColors.Low, Color(0xFFECFDF5))
}
```

- [ ] **Step 2: 验证编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/PriorityBadge.kt
git commit -m "refactor(priority): PriorityBadge 颜色改为引用 PriorityColors"
```

---

## Task 4: 更新 CheckboxEditText.kt 颜色

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt:440-455` 区域

- [ ] **Step 1: 定位代码**

打开 `CheckboxEditText.kt`，找到 `priority` 相关的 `when` 表达式（约 440-455 行）。当前代码类似：

```kotlin
3 -> Color(0xFFF44336)  // 高优先级：红色
2 -> Color(0xFFFF9800)  // 中优先级：黄色
1 -> Color(0xFF4CAF50)  // 低优先级：绿色
```

- [ ] **Step 2: 替换颜色**

将以上三行替换为：

```kotlin
3 -> PriorityColors.colorOf(3)  // 高优先级
2 -> PriorityColors.colorOf(2)  // 中优先级
1 -> PriorityColors.colorOf(1)  // 低优先级
```

（这样不修改数值约定，仅把颜色源切换为 PriorityColors。）

- [ ] **Step 3: 验证编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt
git commit -m "refactor(priority): CheckboxEditText 颜色改为引用 PriorityColors"
```

---

## Task 5: 更新 TodoEditScreen.kt 优先级弹窗颜色

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt:1606-1611`

- [ ] **Step 1: 定位弹窗的 options 列表**

打开 `TodoEditScreen.kt`，定位 line 1606-1611：

```kotlin
val options = listOf(
    Triple(0, "无优先级", androidx.compose.ui.graphics.Color.Gray),
    Triple(1, "低优先级", androidx.compose.ui.graphics.Color(0xFF4CAF50)),
    Triple(2, "中优先级", androidx.compose.ui.graphics.Color(0xFFFF9800)),
    Triple(3, "高优先级", androidx.compose.ui.graphics.Color(0xFFF44336))
)
```

- [ ] **Step 2: 替换硬编码颜色**

改为：

```kotlin
val options = listOf(
    Triple(0, "无优先级", androidx.compose.ui.graphics.Color.Gray),
    Triple(1, "低优先级", com.corgimemo.app.ui.components.PriorityColors.Low),
    Triple(2, "中优先级", com.corgimemo.app.ui.components.PriorityColors.Medium),
    Triple(3, "高优先级", com.corgimemo.app.ui.components.PriorityColors.High)
)
```

> 使用完全限定名而非 import，避免在 TodoEditScreen.kt 顶部增加新 import（保持 diff 最小）。

- [ ] **Step 3: 验证编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt
git commit -m "refactor(priority): 优先级弹窗颜色改为引用 PriorityColors"
```

---

## Task 6: 更新 ShareCardComponent.kt 颜色

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/backup/exporter/ShareCardComponent.kt:360-365`

- [ ] **Step 1: 定位优先级 when 表达式**

打开文件，定位 line 360-365 区域：

```kotlin
when (priority) {
    2 -> Triple("高", Color(0xFFDC2626), Color(0xFFFFE4E6))
    1 -> Triple("中", Color(0xFFD97706), Color(0xFFFFF3E0))
    else -> Triple("低", Color(0xFF16A34A), Color(0xFFECFDF5))
}
```

- [ ] **Step 2: 替换颜色**

改为：

```kotlin
when (priority) {
    2 -> Triple("高", com.corgimemo.app.ui.components.PriorityColors.High, Color(0xFFFFE4E6))
    1 -> Triple("中", com.corgimemo.app.ui.components.PriorityColors.Medium, Color(0xFFFFF3E0))
    else -> Triple("低", com.corgimemo.app.ui.components.PriorityColors.Low, Color(0xFFECFDF5))
}
```

- [ ] **Step 3: 验证编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/backup/exporter/ShareCardComponent.kt
git commit -m "refactor(priority): ShareCardComponent 颜色改为引用 PriorityColors"
```

---

## Task 7: 更新 ImageExporter.kt 颜色

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/backup/exporter/ImageExporter.kt:373-378`

- [ ] **Step 1: 定位 BadgeConfig 构造**

打开文件，定位 line 373-378 区域：

```kotlin
when (priority) {
    2 -> BadgeConfig("高", Color.parseColor("#DC2626"), Color.parseColor("#FFE4E6"))
    1 -> BadgeConfig("中", Color.parseColor("#D97706"), Color.parseColor("#FFF3E0"))
    else -> BadgeConfig("低", Color.parseColor("#16A34A"), Color.parseColor("#ECFDF5"))
}
```

注意：`ImageExporter.kt` 使用的是 Android 原生 `Color.parseColor()`（返回 Int ARGB），与 Compose 的 `Color` 不同。需要保持这种调用方式。

- [ ] **Step 2: 替换颜色字面量**

改为：

```kotlin
when (priority) {
    2 -> BadgeConfig("高", com.corgimemo.app.ui.components.PriorityColors.High.toArgb(), Color.parseColor("#FFE4E6"))
    1 -> BadgeConfig("中", com.corgimemo.app.ui.components.PriorityColors.Medium.toArgb(), Color.parseColor("#FFF3E0"))
    else -> BadgeConfig("低", com.corgimemo.app.ui.components.PriorityColors.Low.toArgb(), Color.parseColor("#ECFDF5"))
}
```

如果文件顶部没有 `androidx.compose.ui.graphics.toArgb` import，需要添加：

```kotlin
import androidx.compose.ui.graphics.toArgb
```

- [ ] **Step 3: 验证编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/backup/exporter/ImageExporter.kt
git commit -m "refactor(priority): ImageExporter 颜色改为引用 PriorityColors"
```

---

## Task 8: 在 TodoListItem.kt 中新增 PriorityBar 私有 composable

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`

- [ ] **Step 1: 检查现有 import 完整性**

确认文件已有以下 import（如缺失需添加）：

```kotlin
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
```

- [ ] **Step 2: 添加 PriorityBar composable**

在文件底部（最末尾的 `private fun formatDateTime` 等私有函数附近，但要在 `TodoListItem` 函数体之外）新增：

```kotlin
/**
 * 优先级竖条 - 显示在待办卡片左侧 4dp 宽的彩色线条
 *
 * 颜色根据 todo.priority 动态变化：
 * - 0 (无优先级) → 透明
 * - 1 (低) → PriorityColors.Low（柔蓝）
 * - 2 (中) → PriorityColors.Medium（柔橙）
 * - 3 (高) → PriorityColors.High（柔红）
 *
 * 通过 animateColorAsState 实现 200ms 颜色平滑过渡。
 *
 * @param priority 优先级数值
 * @param modifier Modifier（建议由调用方控制 fillMaxHeight 等）
 */
@Composable
private fun PriorityBar(
    priority: Int,
    modifier: Modifier = Modifier
) {
    /** 目标颜色：根据 priority 数值获取 */
    val targetColor = PriorityColors.colorOf(priority)

    /** 颜色过渡动画：与卡片其他动画保持 200ms 节奏一致 */
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 200),
        label = "PriorityBarColor"
    )

    /**
     * 高度通过 fillMaxHeight() 自适应父容器（Card），
     * 无需硬编码，确保与卡片内容区域高度一致。
     */
    Box(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(animatedColor)
    )
}
```

- [ ] **Step 3: 验证编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin -q`
预期：BUILD SUCCESSFUL（PriorityBar 已存在但暂未使用，会有 unused warning，可忽略）

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(priority): 新增 PriorityBar 私有 composable"
```

---

## Task 9: 重构 TodoListItem.kt 的 Card 布局

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:183-222`（Card 块）和 line 460-475（PriorityDot 调用）

- [ ] **Step 1: 找到 Card 块的 Column 子元素**

当前结构（line 222-614）：

```kotlin
Card(
    modifier = ...,
    elevation = ...,
    shape = RoundedCornerShape(16.dp),
    colors = ...
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 复选框 + 标题 + 分类等
        Row(...) { ... }

        // 展开的子任务
        if (isExpanded && subTasks.isNotEmpty()) {
            Column(...) { ... }
        }
    }
}
```

- [ ] **Step 2: 将 Card 内的 Column 替换为 Row + Column 结构**

改为：

```kotlin
Card(
    modifier = ...,
    elevation = ...,
    shape = RoundedCornerShape(16.dp),
    colors = ...
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        /** 左侧 4dp 优先级竖条（无优先级时透明，不占视觉空间） */
        PriorityBar(priority = todo.priority)

        /** 原 Column 包到 weight(1f) 的 Column 中，占满除竖条外的宽度 */
        Column(modifier = Modifier.weight(1f)) {
            // 复选框 + 标题 + 分类等
            Row(...) { ... }

            // 展开的子任务
            if (isExpanded && subTasks.isNotEmpty()) {
                Column(...) { ... }
            }
        }
    }
}
```

注意：原有的 `Column(modifier = Modifier.fillMaxWidth())` 改为内层带 `weight(1f)` 的 Column。

- [ ] **Step 3: 删除 PriorityDot 调用**

在 `Row(...) { ... }` 块（line 460-475 附近）找到：

```kotlin
PriorityDot(priority = todo.priority.toTodoPriority())
```

完全删除这一行。

- [ ] **Step 4: 删除不再使用的 import**

删除：
```kotlin
import com.corgimemo.app.ui.components.PriorityDot
```

- [ ] **Step 5: 调整展开子任务的缩进**

将展开子任务 `Column` 的 `start = 64.dp` 改为 `start = 60.dp`（给 4dp 竖条腾出对齐空间，避免子任务紧贴竖线）：

```kotlin
if (isExpanded && subTasks.isNotEmpty()) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 60.dp, end = 16.dp, bottom = 16.dp)  // 64 → 60
    ) {
        ...
    }
}
```

- [ ] **Step 6: 验证编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin -q`
预期：BUILD SUCCESSFUL，无 unused import 警告

- [ ] **Step 7: 视觉验证（用户操作）**

提示用户：
- 启动模拟器或真机
- 创建一个 priority=0 的 todo（无竖条）
- 创建一个 priority=1 的 todo（柔蓝竖条）
- 创建一个 priority=2 的 todo（柔橙竖条）
- 创建一个 priority=3 的 todo（柔红竖条）
- 验证切换 priority 时颜色平滑过渡（200ms）

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "refactor(priority): 卡片添加 PriorityBar，移除 PriorityDot"
```

---

## Task 10: 最终验证与清理

**Files:** 无新增/修改（仅验证）

- [ ] **Step 1: 全项目编译**

运行：项目根目录 `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin -q`
预期：BUILD SUCCESSFUL

- [ ] **Step 2: 检查无未使用 import**

运行：项目根目录 `./gradlew :app:lintDebug -q`
预期：无 ERROR 级别警告（WARNING 可接受）

手动 grep 关键文件，确认无残留：
- `PriorityDot` import 应在 TodoListItem.kt 中已删除
- 其他文件改完后，原硬编码 `0xFFF44336`、`0xFFEF4444`、`0xFFDC2626` 等应已被替换

- [ ] **Step 3: 最终视觉验证**

提示用户：
- 在主页查看至少 3 种不同 priority 的卡片
- 确认竖条颜色与 UI 设计规范一致
- 确认子任务展开时缩进合理
- 确认批量模式下竖条与卡片同步移动
- 确认深色模式下颜色对比度足够

- [ ] **Step 4: 提交（如有清理改动）**

如果有 lint 警告的修复，运行：

```bash
git add -A
git commit -m "chore: 清理 lint 警告"
```

---

## 实施完成检查

- [ ] 全部 10 个 Task 标记完成
- [ ] 全部 commit 已推送到当前分支
- [ ] 视觉验证通过
- [ ] 编译无 ERROR
- [ ] 用户确认完成
