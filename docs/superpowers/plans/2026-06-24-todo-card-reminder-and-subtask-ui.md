# 待办卡片提醒显示 + 子任务进度位置 + 展开按钮阴影 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 `TodoListItem.kt` 中实现提醒信息渲染、子任务进度位置调整、展开按钮 2dp 圆形阴影，并提交。

**Architecture:** 单文件 UI 增量改动。沿用既有 `formatReminderDisplay()` 与 `Color(0xFFDC2626)` 过期色，不新增数据流/ViewModel 改动。优先级竖条、卡片布局、右滑删除等所有现有逻辑零回归。

**Tech Stack:** Jetpack Compose / Material 3 / Kotlin

**关联设计文档**：[2026-06-24-todo-card-reminder-and-subtask-ui-design.md](../specs/2026-06-24-todo-card-reminder-and-subtask-ui-design.md)

---

## 任务清单

### Task 1: 新增 import（Alarm / formatReminderDisplay / Surface）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`（import 区域）

- [ ] **Step 1: 新增 Alarm 图标 import**

在 `TodoListItem.kt:25-29` 区域（`material.icons.filled.*` 区块）**已存在**的 `import androidx.compose.material.icons.filled.Delete` **附近**新增一行：

```kotlin
import androidx.compose.material.icons.filled.Alarm
```

完整上下文（参考当前 imports.txt 第 25-28 行）：
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
```

- [ ] **Step 2: 新增 formatReminderDisplay import**

在文件 import 区域（`com.corgimemo.app.*` 块附近）新增：

```kotlin
import com.corgimemo.app.ui.util.formatReminderDisplay
```

定位参考：在 `import com.corgimemo.app.data.model.TodoItem`（第 55 行）**之前**插入。

- [ ] **Step 3: 新增 Surface import**

在 `androidx.compose.material3.*` 块（已有 `Card`、`Checkbox`、`Icon`、`IconButton`、`MaterialTheme`、`Text` 等）新增：

```kotlin
import androidx.compose.material3.Surface
```

定位参考：紧跟 `import androidx.compose.material3.IconButton`（第 33 行）**之后**插入。

- [ ] **Step 4: 增量编译验证（仅 import 改动）**

执行：
```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过，无新错误。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): add imports for reminder + surface shadow components"
```

---

### Task 2: 删除标题行内子任务进度文本

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:324-331`

- [ ] **Step 1: 删除 8 行代码块**

**删除**以下整段（位于主标题 `Row(verticalAlignment = Alignment.CenterVertically)` 内末尾）：

```kotlin
subTaskProgress?.let { progress ->
    Text(
        text = " ($progress)",
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
    )
}
```

> 提示：用 Edit 工具匹配完整 8 行块，确保唯一性。

- [ ] **Step 2: 编译验证**

执行：
```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过（标题行布局仍合法，subTaskProgress 参数未被引用但函数签名不变）。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "refactor(todo): remove subtask progress text from title row"
```

---

### Task 3: 新增提醒信息行

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`（在"完成时间"块与"分类行"块之间）

- [ ] **Step 1: 定位插入点**

当前文件结构（节选 Line 334-345）：
```kotlin
// 完成时间
if (todo.status == 1 && todo.completedAt != null) {
    Text(
        text = formatCompletedTime(todo.completedAt),
        ...
    )
}

// 分类行
if (categoryName != null) {
    ...
}
```

在 `// 完成时间` 块（结束于 Line 342 `}`）**之后**、`// 分类行` 注释（Line 344）**之前**插入。

- [ ] **Step 2: 插入提醒行代码**

在上述定位点插入：

```kotlin
/** 提醒时间（与编辑页 [formatReminderDisplay] 渲染规则一致） */
if (todo.reminderTime != null) {
    val reminder = formatReminderDisplay(todo.reminderTime)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Alarm,
            contentDescription = if (reminder.isOverdue) "已过期提醒" else "提醒",
            tint = if (reminder.isOverdue) Color(0xFFDC2626)
                   else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = reminder.text,
            fontSize = 12.sp,
            color = if (reminder.isOverdue) Color(0xFFDC2626)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (reminder.isOverdue)
                FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
```

- [ ] **Step 3: 编译验证**

执行：
```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过。所有 import（`Icons`、`Alarm`、`formatReminderDisplay`、`Color`、`Modifier.size/width/padding`、`Spacer`、`Text`、`FontWeight`）已在文件中或 Task 1 已添加。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): render reminder time with alarm icon on todo card"
```

---

### Task 4: 改写展开按钮区域（Surface 阴影 + 左侧进度）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt:461-477`

- [ ] **Step 1: 定位替换区**

当前代码（Line 461-477）：
```kotlin
// 展开/收起按钮（仅在有子任务且非批量模式时显示）
if (subTaskProgress != null && !isBatchMode) {
    IconButton(
        onClick = onToggleExpand,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = if (isExpanded) {
                Icons.Default.ExpandLess
            } else {
                Icons.Default.ExpandMore
            },
            contentDescription = if (isExpanded) "收起" else "展开",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 2: 替换为 Surface 实现**

用以下代码**完整替换**原 16 行块：

```kotlin
// 子任务进度（移至展开按钮左侧）+ 展开/收起按钮（带阴影）
if (subTaskProgress != null && !isBatchMode) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 子任务进度文本：紧贴展开按钮左侧
        Text(
            text = "($subTaskProgress)",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(8.dp))

        // 展开/收起按钮：Surface 圆形阴影 2dp
        Surface(
            onClick = onToggleExpand,
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = if (isExpanded) "收起" else "展开",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
```

- [ ] **Step 3: 检查 Box 是否已 import**

`androidx.compose.foundation.layout.Box` 已在第 12 行 import，确认未删除。

如未 import，在 `androidx.compose.foundation.layout.*` 块新增：
```kotlin
import androidx.compose.foundation.layout.Box
```

- [ ] **Step 4: 编译验证**

执行：
```bash
.\gradlew :app:compileDebugKotlin
```

预期：通过。如出现 `Unresolved reference 'Box'`，按 Step 3 补 import。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "feat(todo): move subtask progress next to expand button + add 2dp shadow"
```

---

### Task 5: 全量编译 + 推送

- [ ] **Step 1: 全量编译（包含 lint）**

执行：
```bash
.\gradlew :app:assembleDebug
```

预期：BUILD SUCCESSFUL，无新 warning。

- [ ] **Step 2: 检查 git 状态**

执行：
```bash
git status
```

预期：`working tree clean`，本地有 4 个新提交：
- `feat(todo): add imports for reminder + surface shadow components`
- `refactor(todo): remove subtask progress text from title row`
- `feat(todo): render reminder time with alarm icon on todo card`
- `feat(todo): move subtask progress next to expand button + add 2dp shadow`

- [ ] **Step 3: 推送至远端**

```bash
git push origin master
```

预期：4 commits pushed successfully.

---

## 验证清单（人工目视）

| 场景 | 期望结果 |
|------|---------|
| 新建 todo 无提醒 | 卡片无提醒行；与改动前完全一致 |
| 提醒在今天未来 | `Alarm` 图标 + `今天HH:MM` 灰色 12sp，**在主标题正下方** |
| 提醒在明天 | `Alarm` 图标 + `明天HH:MM` 灰色 12sp |
| 提醒跨年 | `Alarm` 图标 + `yyyy年M月D日 HH:MM` 灰色 12sp |
| 提醒已过期 | `Alarm` 图标 + `...已过期` 红色 `0xFFDC2626` 12sp SemiBold |
| 有子任务的 todo | 标题行无 `(0/2)`；**展开按钮左侧**显示 `(0/2)` primary 13sp Medium |
| 无子任务的 todo | 无展开按钮、无进度文本（与改动前一致） |
| 展开/收起切换 | 阴影按钮图标在 `ExpandLess` / `ExpandMore` 间切换，点击有 2dp 圆形阴影 |
| 主标题/分类/语音/开始时间/截止时间/进度条/PriorityBar | 位置与样式完全不变 |

## 风险与回退

- 所有改动为纯 UI 层增量，未触碰数据/ViewModel
- 单文件改动可通过 `git revert <commit>` 单独回退任意一个任务
- 若 2.dp 阴影在某些主题下不够明显，可直接调整 `shadowElevation` 数值
