# 待办卡片优先级竖条 + 优先级颜色统一重构

**日期**：2026-06-24
**类型**：UI 视觉重构 + 颜色规范统一
**状态**：已批准，待实施

## 一、背景与目标

### 1.1 现状

待办列表卡片右侧展示一个优先级圆点（绿色、黄色或红色），与"温暖、治愈"的设计语言存在割裂感，且优先级信息以小圆点形式呈现，识别度低。

**更严重的问题**：项目里优先级颜色定义分散且相互冲突：

| 来源 | 高 | 中 | 低 | 数值约定 |
|------|---|---|---|---------|
| 待办编辑页 ([TodoEditScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt)) | #F44336 | #FF9800 | #4CAF50 | 0=无 1=低 2=中 3=高 |
| [PriorityDot.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/PriorityDot.kt) | #EF4444 | #F59E0B | #10B981 | 0=低 1=中 2=高 |
| [PriorityBadge.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/PriorityBadge.kt) | #DC2626 | #D97706 | #16A34A | 0=低 1=中 2=高 |
| [UI 设计规范](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/UI设计规范.md) | #FF8A80 | #FFB74D | #90CAF9 | — |

### 1.2 目标

1. **移除待办卡片上的优先级文本/圆点图标**，用左侧 4dp 竖线替代
2. **统一所有 UI 层的优先级颜色**到 UI 设计规范定义的柔和色系
3. **引入 `PriorityColors` 单一颜色源**，避免后续再出现"同一概念多种颜色"问题
4. **保持数值约定与待办编辑页一致**（0=无、1=低、2=中、3=高）

## 二、范围

### 2.1 包含

- 待办卡片视觉重构：移除 PriorityDot，新增 PriorityBar
- 新增 `PriorityColors.kt`（单一颜色源）
- 6 个 UI 渲染文件的颜色统一改造
- 动画过渡（颜色 200ms tween）

### 2.2 不包含（明确边界）

- 数值约定的修正：`HomeScreen.kt:2311` 和 `InspirationEditScreen.kt:890` 的 0/1/2 反向约定保持原样（属于业务侧逻辑，不在 UI 重构范围）
- 通知颜色（`NotificationHelper.kt`）：使用 Android 原生 `Color.parseColor()` 路径，本次不动
- 备份/导出（`CsvSerializer.kt`、`IcsExporter.kt`、`ImageExporter.kt` 内的颜色）：仅同步 `ImageExporter.kt` 和 `ShareCardComponent.kt`，CSV/Ics 不动
- 置顶竖条代码：项目未实际实现，无需删除

## 三、架构与组件

### 3.1 新增文件：`PriorityColors.kt`

**位置**：`app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt`

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
    val High = Color(0xFFFF8A80)
    val Medium = Color(0xFFFFB74D)
    val Low = Color(0xFF90CAF9)
    val None = Color.Transparent

    /**
     * 数值 → 颜色
     * @param priority 优先级数值
     * @return 对应颜色，0 或非法值返回 None（透明）
     */
    fun colorOf(priority: Int): Color = when (priority) {
        3 -> High
        2 -> Medium
        1 -> Low
        else -> None
    }
}
```

### 3.2 新增组件：`PriorityBar`

**位置**：在 `TodoListItem.kt` 中新增（不单独建文件，因为只在一处使用）

```kotlin
/**
 * 优先级竖条 - 显示在待办卡片左侧 4dp 宽的彩色线条
 *
 * @param priority 优先级数值（0=无、1=低、2=中、3=高）
 * @param modifier Modifier
 */
@Composable
private fun PriorityBar(
    priority: Int,
    modifier: Modifier = Modifier
) {
    /** 纯颜色过渡动画：与卡片其他动画 200ms 节奏一致 */
    val targetColor = PriorityColors.colorOf(priority)
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 200),
        label = "PriorityBarColor"
    )

    /** fillMaxHeight() 自适应父容器高度，无需硬编码 */
    Box(
        modifier = modifier
            .width(4.dp)
            .fillMaxHeight()
            .background(animatedColor)
    )
}
```

### 3.3 卡片布局改造：`TodoListItem.kt`

**当前结构**（line 183-222 附近）：

```
Card
└── Column
    ├── Row  (复选框/标题/分类等)
    └── Column  (展开的子任务)
```

**目标结构**：

```
Card
└── Row                          ← 新增
    ├── PriorityBar              ← 新增（4dp 宽竖线）
    └── Column (weight=1f)       ← 原本的 Card 内 Column
        ├── Row  (复选框/标题/分类等)
        └── Column  (展开的子任务)
```

**关键变化**：
- `Card` 内由 `Column` 改为 `Row`，让竖条作为最左侧子元素
- 原 `Column` 加 `Modifier.weight(1f)` 占满剩余宽度
- 删除原 `PriorityDot` 的导入和调用
- 展开的子任务 `Column` 缩进需要从 `start = 64.dp` 调整为 `start = 60.dp`（给竖条腾出视觉空间，避免子任务紧贴竖线）

### 3.4 其他文件改造

| 文件 | 改造内容 | 数值约定改动？ |
|------|---------|---------------|
| [PriorityDot.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/PriorityDot.kt) | `TodoPriority` 枚举的 color 改为引用 `PriorityColors.High/Medium/Low`；`toTodoPriority()` 函数体不变（保持 0→LOW/1→MEDIUM/2→HIGH） | **否**（仅改色） |
| [PriorityBadge.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/PriorityBadge.kt) | `Triple("高", Color(0xFFDC2626), Color(0xFFFFE4E6))` 改为 `Triple("高", PriorityColors.High, Color(0xFFFFE4E6))`；同样改中、低 | **否**（仅改色） |
| [CheckboxEditText.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt) | line 443-445 硬编码颜色 `Color(0xFFF44336)/0xFFFF9800/0xFF4CAF50` 改为 `PriorityColors.colorOf(priority)` | **否**（仅改色） |
| [TodoEditScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt) | line 1608-1610 优先级选项弹窗的硬编码颜色改为 `PriorityColors.Low/Medium/High` | **否**（仅改色） |
| [ShareCardComponent.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/backup/exporter/ShareCardComponent.kt) | line 362-364 硬编码颜色改用 `PriorityColors.colorOf(priority)` | **否**（仅改色） |
| [ImageExporter.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/backup/exporter/ImageExporter.kt) | line 375-377 改用 `PriorityColors.colorOf(priority)` | **否**（仅改色） |

**关键决策**：本次**只统一颜色，不统一数值约定**。`PriorityColors.colorOf()` 内部使用 1=低/2=中/3=高（与编辑页 TodoItem.priority 字段一致），仅在 TodoListItem 的 PriorityBar 中使用；其它文件（PriorityBadge/CheckboxEditText/ShareCardComponent/ImageExporter）的 `when` 分支保持原有 0/1/2 映射，但颜色值改为 `PriorityColors` 引用。

### 3.5 动画策略

| 动画 | 实现 |
|------|------|
| 颜色变化 | `animateColorAsState` + `tween(durationMillis = 200)` |
| 首次出现 | 卡片本身的 `ReorderableLazyColumn` 列表动画已涵盖（新增/删除有 fadeIn/fadeOut） |
| 拖拽过程 | 无需特殊处理（竖条是 Card 子元素，跟随 Card 一起动画） |

## 四、数据流

无后端改动。所有改动均在 UI 渲染层。`TodoItem.priority: Int` 字段保持不变（数据库 schema 不动）。

```
数据库 (TodoItem.priority: Int)
    ↓
ViewModel (透传)
    ↓
TodoListItem (UI)
    ├── todo.priority = 0  → PriorityBar 透明
    ├── todo.priority = 1  → PriorityBar 显示 #90CAF9（柔蓝）
    ├── todo.priority = 2  → PriorityBar 显示 #FFB74D（柔橙）
    └── todo.priority = 3  → PriorityBar 显示 #FF8A80（柔红）
```

## 五、错误处理与边界

- **priority 为 null/负数/超出范围**：`PriorityColors.colorOf()` 使用 `when` 的 `else` 分支返回 `Color.Transparent`，保证不崩溃
- **PriorityBar 在空 Card 中的高度**：`fillMaxHeight()` 在 `Row` 中由 Card 高度决定，最小高度为 Card 自身 padding
- **批量模式下竖条位置**：Card 整体右移 8dp（由 `checkboxStartPadding` 控制），PriorityBar 跟随 Card 一起右移，**无需特殊处理**
- **拖拽中竖条颜色**：`isDragging` 仅影响透明度/缩放（由 `ReorderableLazyColumn` 处理），不影响 PriorityBar 颜色

## 六、测试与验证

### 6.1 视觉验证

- [ ] 创建 4 个 todo，分别设置 priority = 0/1/2/3
- [ ] 验证 priority=0 时无竖线（透明）
- [ ] 验证 priority=1 时竖线为柔蓝 (#90CAF9)
- [ ] 验证 priority=2 时竖线为柔橙 (#FFB74D)
- [ ] 验证 priority=3 时竖线为柔红 (#FF8A80)
- [ ] 验证 priority 切换时颜色平滑过渡（200ms）
- [ ] 验证卡片展开子任务时，竖条高度跟随扩展
- [ ] 验证批量模式下，竖条与卡片一起右移
- [ ] 验证深色模式颜色对比度足够（可能需要单独适配）

### 6.2 编译验证

- [ ] 全部 Kotlin 文件无编译错误
- [ ] 无未使用 import
- [ ] 无未使用变量

### 6.3 业务回归

- [ ] 优先级弹窗（待办编辑页）选择 1/2/3 后，回到列表页竖线颜色对应正确
- [ ] 待办编辑页的优先级颜色弹窗（line 1606-1610）显示新规范颜色
- [ ] CheckboxEditText 中分组级优先级的颜色显示正确
- [ ] 导出分享图（ShareCardComponent / ImageExporter）颜色与规范一致

## 七、风险与权衡

| 风险 | 缓解措施 |
|------|---------|
| 数值约定不一致（编辑页 0/1/2/3 vs 其他 0/1/2） | 本次**不修复**数值约定，仅统一颜色；数值不匹配会导致颜色错乱但不会崩溃。后续单独做"数值约定统一"任务 |
| 大量文件同时改动 | 改动按"组件→消费者"的依赖顺序：先新增 `PriorityColors.kt` → 改 `PriorityDot.kt`/`PriorityBadge.kt` → 改 `TodoListItem.kt` → 改 `CheckboxEditText.kt`/`TodoEditScreen.kt` → 改导出相关 |
| 柔和颜色辨识度不足 | 优先级区分主要靠**位置**（左竖条）和**色相**（红橙蓝），即使低饱和度也可识别；如用户反馈辨识度低，可单独调亮 |

## 八、后续优化（不在本次范围）

1. **数值约定统一**：修复 `HomeScreen.kt:2311` 和 `InspirationEditScreen.kt:890` 的反向映射
2. **深色模式适配**：当前 `PriorityColors` 是固定值，深色模式下可考虑降低饱和度
3. **PriorityBar 形状**：当前是矩形（4dp 宽直线），可考虑改为圆角矩形或带轻微渐变
4. **expand sub-task 缩进统一**：当前各文件硬编码 `start = 64.dp`，可考虑用 modifier 统一
