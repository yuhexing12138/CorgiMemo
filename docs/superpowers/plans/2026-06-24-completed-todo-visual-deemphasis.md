# 已完成待办视觉降权优化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对已完成状态的父待办和子待办进行视觉降权，文字/勾选/优先级竖线全部弱化为非彩色系，已降低视觉对比度

**Architecture:** 新建 `CompletedColors.kt` 集中管理已完成态色值；在 `PriorityColors.kt` 中新增 `HighDim/MediumDim/LowDim` 三个浅色色值（Material 200 色调色板）；`TodoListItem.kt` 中相关 Composable 添加 `isCompleted` 参数，按状态切换色源

**Tech Stack:** Jetpack Compose、Material Design 200 色调色板

---

## 文件结构

| 操作 | 文件路径 | 职责 |
|------|---------|------|
| 修改 | `docs/superpowers/specs/UI设计规范.md` | §12.1.2.4 新增"状态色-已完成"子表 |
| 新建 | `app/src/main/java/com/corgimemo/app/ui/components/CompletedColors.kt` | 已完成态文字/勾选/竖线色值常量 |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt` | 新增 `HighDim/MediumDim/LowDim` + `dimColorOf()` |
| 修改 | `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt` | PriorityBar/SubTaskCheckbox/完成时间/分类行的颜色分支 |

---

## Task 1：更新 UI 设计规范文档

**Files:**
- Modify: `docs/superpowers/specs/UI设计规范.md`（在 §12.1.2.3 功能色表后追加 §12.1.2.4 状态色-已完成 子表）

- [ ] **Step 1: 在文件中定位插入点**

使用 Read 工具定位 `12.1.2.3 功能色` 表格结束位置（约第 45 行 "低优先级" 之后）。

- [ ] **Step 2: 追加 §12.1.2.4 状态色 - 已完成 子表**

在文件末尾（或者在 §12.1.3 主题配色方案 之前）追加以下内容：

```markdown
#### 12.1.2.4 状态色 - 已完成（视觉降权）

> 应用于已完成（status=1）的待办卡片，对所有彩色元素进行灰色化降权处理，降低视觉对比度。

| 用途 | 亮色 | 深色 | 说明 |
|------|------|------|------|
| **已完成-文字** | #888888 | #6E6E6E | 弱于次要文字 #666666，建立"完成项更弱"层级 |
| **已完成-勾选背景** | #BDBDBD | #5A5A5A | 浅灰，不抢视觉焦点 |
| **已完成-优先级竖线（高）** | #FFCDD2 | (浅色系列自动派生) | 浅红，原 #FF8A80 淡化 |
| **已完成-优先级竖线（中）** | #FFE0B2 | 同上 | 浅橙，原 #FFB74D 淡化 |
| **已完成-优先级竖线（低）** | #BBDEFB | 同上 | 浅蓝，原 #90CAF9 淡化 |

**降权原则**：
- 所有彩色（红/橙/蓝/绿）替换为灰色系或同色系浅色版
- 删除线颜色 = 文字色（自动保持一致）
- 勾选 "✓" 符号保持白色不变
- 已完成态无优先级时，竖线仍保持透明
```

- [ ] **Step 3: 验证修改**

用 Read 工具确认 §12.1.2.4 已成功追加。

- [ ] **Step 4: Commit**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add docs/superpowers/specs/UI设计规范.md
git commit -m "docs(ui): 新增 §12.1.2.4 已完成态视觉降权色值规范"
```

---

## Task 2：新建 CompletedColors.kt

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/CompletedColors.kt`

- [ ] **Step 1: 创建文件**

使用 Write 工具创建文件，路径：`app/src/main/java/com/corgimemo/app/ui/components/CompletedColors.kt`

文件内容：

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.ui.graphics.Color

/**
 * 已完成态视觉降权色值统一源
 *
 * 应用于已完成（status=1）的待办卡片元素。所有彩色元素降权为灰色系，
 * 弱于正常态的次要文字（#666666），实现"完成项更弱"的视觉层级。
 *
 * 颜色来源：[UI 设计规范] §12.1.2.4 状态色 - 已完成
 *   - 已完成-文字：#888888（亮）/ #6E6E6E（暗）
 *   - 已完成-勾选背景：#BDBDBD（亮）/ #5A5A5A（暗）
 *
 * 注意：优先级竖线不使用本文件，详见 [PriorityColors.dimColorOf] 的浅色版色值
 *   （#FFCDD2 / #FFE0B2 / #BBDEFB，保留原优先级色相但降低饱和度）。
 */
object CompletedColors {
    /**
     * 已完成态文字色
     *
     * 应用于：标题、描述、完成时间、分类文字等所有彩色文字元素
     * 亮色模式：#888888（中灰，弱于次要文字 #666666）
     * 深色模式：#6E6E6E（中深灰）
     */
    val Text = Color(0xFF888888)
    val TextDark = Color(0xFF6E6E6E)

    /**
     * 已完成态勾选框背景色
     *
     * 应用于：SubTaskCheckbox 已勾选且父待办已完成时
     * 亮色模式：#BDBDBD（浅灰，不抢焦点）
     * 深色模式：#5A5A5A（中深灰）
     */
    val CheckboxBg = Color(0xFFBDBDBD)
    val CheckboxBgDark = Color(0xFF5A5A5A)
}
```

- [ ] **Step 2: 验证文件创建**

用 Read 工具确认文件已正确写入。

- [ ] **Step 3: Commit**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/CompletedColors.kt
git commit -m "feat(ui): 新增 CompletedColors 已完成态视觉降权色值源"
```

---

## Task 3：扩展 PriorityColors 新增浅色调色板

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt`

- [ ] **Step 1: 定位文件**

用 Read 工具读取 `PriorityColors.kt` 全文（约 44 行）。

- [ ] **Step 2: 新增 3 个浅色色值常量 + dimColorOf() 函数**

在原 `object PriorityColors` 内追加（在 `colorOf()` 函数之后）：

```kotlin
    /**
     * 已完成态浅色版（Material Design 200 色调色板）
     *
     * 应用于已完成（status=1）待办的优先级竖线。
     * 在原 priority 色基础上大幅降低饱和度、提亮，
     * 保留原色色相但视觉上"淡化"，建立"完成项更弱"层级。
     *
     * 颜色来源：[UI 设计规范] §12.1.2.4 状态色 - 已完成
     *   - 高优先级淡化：#FFCDD2（Material Red 200）
     *   - 中优先级淡化：#FFE0B2（Material Orange 200）
     *   - 低优先级淡化：#BBDEFB（Material Blue 200）
     */
    val HighDim = Color(0xFFFFCDD2)
    val MediumDim = Color(0xFFFFE0B2)
    val LowDim = Color(0xFFBBDEFB)

    /**
     * 数值 → 已完成态浅色版颜色
     *
     * @param priority 优先级数值（0=无、1=低、2=中、3=高）
     * @return 对应浅色；0 或其他非法值返回 None（透明，与未完成态一致）
     */
    fun dimColorOf(priority: Int): Color = when (priority) {
        3 -> HighDim
        2 -> MediumDim
        1 -> LowDim
        else -> None
    }
```

- [ ] **Step 3: 验证修改**

用 Read 工具确认 `HighDim/MediumDim/LowDim` 和 `dimColorOf()` 已添加。

- [ ] **Step 4: Commit**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt
git commit -m "feat(ui): PriorityColors 新增 HighDim/MediumDim/LowDim 浅色版与 dimColorOf()"
```

---

## Task 4：修改 PriorityBar 接受 isCompleted 参数

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:812-832`（PriorityBar 函数定义）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:231`（PriorityBar 调用点）

- [ ] **Step 1: 定位 PriorityBar 函数**

用 Read 工具读取 `TodoListItem.kt:812-832` 确认当前实现。

- [ ] **Step 2: 修改 PriorityBar 函数签名 + 颜色分支**

将 [Line 812-832](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L812-L832) 的 PriorityBar 函数替换为：

```kotlin
private fun PriorityBar(
    priority: Int,
    isCompleted: Boolean = false,
    modifier: Modifier = Modifier
) {
    /** 目标颜色：未完成用原色，已完成用浅色版（保留色相但降饱和） */
    val targetColor = if (isCompleted) {
        PriorityColors.dimColorOf(priority)
    } else {
        PriorityColors.colorOf(priority)
    }

    /** 颜色过渡动画：与卡片其他动画保持 200ms 节奏一致 */
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 200),
        label = "PriorityBarColor"
    )

    Box(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(animatedColor)
    )
}
```

- [ ] **Step 3: 修改 PriorityBar 调用点**

将 [Line 231](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L231) 的调用从：

```kotlin
PriorityBar(priority = todo.priority)
```

改为：

```kotlin
PriorityBar(priority = todo.priority, isCompleted = todo.status == 1)
```

- [ ] **Step 4: 验证修改**

用 Read 工具确认 PriorityBar 函数和调用点都已更新。

- [ ] **Step 5: Commit**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): PriorityBar 支持 isCompleted 状态，已完成时切换为浅色版"
```

---

## Task 5：修改完成时间文字颜色

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:330-336`（完成时间 Text）

- [ ] **Step 1: 定位完成时间 Text**

用 Read 工具读取 [Line 330-336](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L330-L336) 确认当前实现。

- [ ] **Step 2: 修改完成时间文字颜色**

将当前代码：

```kotlin
// 完成时间
if (todo.status == 1 && todo.completedAt != null) {
    Text(
        text = formatCompletedTime(todo.completedAt),
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp)
    )
}
```

改为：

```kotlin
// 完成时间（已完成态用灰色降权）
if (todo.status == 1 && todo.completedAt != null) {
    Text(
        text = formatCompletedTime(todo.completedAt),
        fontSize = 12.sp,
        // 已完成态视觉降权：使用 CompletedColors.Text 而非 primary 橙色
        color = if (todo.status == 1) CompletedColors.Text
                else MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 2.dp)
    )
}
```

注意：因为此 Text 已经在 `if (todo.status == 1 && ...)` 内，颜色条件其实总是 true，简化后：

```kotlin
// 完成时间（已完成态用灰色降权）
if (todo.status == 1 && todo.completedAt != null) {
    Text(
        text = formatCompletedTime(todo.completedAt),
        fontSize = 12.sp,
        color = CompletedColors.Text,
        modifier = Modifier.padding(top = 2.dp)
    )
}
```

- [ ] **Step 3: 验证修改**

用 Read 工具确认完成时间 Text 的 color 已被替换。

- [ ] **Step 4: Commit**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): 完成时间文字改用 CompletedColors.Text 灰色降权"
```

---

## Task 6：修改分类行文字颜色（已完成态）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:383-409`（分类行 if 块）

- [ ] **Step 1: 定位分类行代码**

用 Read 工具读取 [Line 383-409](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L383-L409) 确认当前实现。

- [ ] **Step 2: 修改分类文字颜色**

将分类名 Text（[Line 395-405](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L395-L405)）的 `color = MaterialTheme.colorScheme.primary` 改为条件分支：

```kotlin
Text(
    text = categoryName,
    fontSize = 12.sp,
    // 已完成态视觉降权：使用 CompletedColors.Text 而非 primary 橙色
    color = if (todo.status == 1) CompletedColors.Text
            else MaterialTheme.colorScheme.primary,
    modifier = Modifier
        .background(
            color = if (todo.status == 1) CompletedColors.Text.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(4.dp)
        )
        .padding(horizontal = 8.dp, vertical = 2.dp)
)
```

（同时调整 background 颜色：已完成态用 12% 透明度的灰；未完成态保持原 primaryContainer）

- [ ] **Step 3: 验证修改**

用 Read 工具确认分类行 color 和 background 已更新。

- [ ] **Step 4: Commit**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): 分类行文字与背景在已完成态使用灰色降权"
```

---

## Task 7：修改 SubTaskCheckbox 支持父待办完成态

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:700-726`（SubTaskCheckbox 函数定义）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:650-653`（SubTaskCheckbox 调用点）

- [ ] **Step 1: 定位 SubTaskCheckbox 函数和调用点**

用 Read 工具读取 [Line 650-653](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L650-L653) 和 [Line 700-726](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L700-L726) 确认当前实现。

- [ ] **Step 2: 修改 SubTaskCheckbox 函数签名**

将 [Line 700-726](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L700-L726) 的 SubTaskCheckbox 函数替换为：

```kotlin
private fun SubTaskCheckbox(
    isCompleted: Boolean,
    isParentCompleted: Boolean = false,
    onClick: () -> Unit
) {
    /**
     * 勾选框背景色：
     * - 子任务未完成 → 浅灰描边
     * - 子任务完成 + 父待办未完成 → primary 橙色（部分完成的视觉强调）
     * - 子任务完成 + 父待办已完成 → CompletedColors.CheckboxBg 灰色（整体降权）
     */
    val bgColor = when {
        !isCompleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isParentCompleted -> CompletedColors.CheckboxBg
        else -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isCompleted) {
            Text(
                text = "✓",
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 11.sp
            )
        }
    }
}
```

- [ ] **Step 3: 修改 SubTaskCheckbox 调用点**

将 [Line 650-653](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L650-L653) 的调用从：

```kotlin
SubTaskCheckbox(
    isCompleted = subTask.isCompleted,
    onClick = onToggleComplete
)
```

改为：

```kotlin
SubTaskCheckbox(
    isCompleted = subTask.isCompleted,
    isParentCompleted = todo.status == 1,  // 父待办是否已完成
    onClick = onToggleComplete
)
```

- [ ] **Step 4: 验证修改**

用 Read 工具确认 SubTaskCheckbox 函数和调用点都已更新。

- [ ] **Step 5: Commit**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): SubTaskCheckbox 支持 isParentCompleted 参数，整体已完成时用灰色降权"
```

---

## Task 8：全量编译验证 + 推送

- [ ] **Step 1: 编译验证**

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
.\gradlew :app:assembleDebug
```

预期：`BUILD SUCCESSFUL`

如果失败：检查 `TodoListItem.kt` 中 CompletedColors、PriorityColors 的 import 是否已添加；如果缺 import，在 `TodoListItem.kt` 顶部补上 `import com.corgimemo.app.ui.components.CompletedColors`。

- [ ] **Step 2: 多场景手动测试**

打开应用，验证以下场景：
1. **未完成待办**：保持原色（红/橙/蓝优先级竖线、橙色分类、橙色勾选）
2. **已完成待办**：所有彩色变灰，竖线变浅红/浅橙/浅蓝
3. **未完成父 + 已完成子任务**：子任务勾选保持橙色
4. **已完成父 + 已完成子任务**：子任务勾选变浅灰
5. **深色模式**：所有降权色值自动适配

- [ ] **Step 3: 推送（按用户要求）**

如果用户要求推送：

```bash
cd c:\Users\EDY\Desktop\CorgiMemo
git push origin master
```

否则保持本地领先，不推送。
