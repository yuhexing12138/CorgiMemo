# 待办卡片提醒显示 + 子任务进度位置 + 展开按钮阴影

**日期**：2026-06-24
**类型**：UI 增强 + 视觉对齐
**状态**：已批准，待实施

## 一、背景与目标

### 1.1 现状

待办列表卡片在视觉信息密度上存在三处可优化点：

1. **缺少提醒信息可视化**：`TodoItem.reminderTime` 字段已存在并由编辑页维护，但首页卡片未渲染提醒时间，导致用户必须点击进入编辑页才能看到"今天09:26 / 明天09:25"等关键时间信息。
2. **子任务进度标识位置与参考设计不一致**：当前 `TodoListItem.kt:324-331` 在主标题行内尾部渲染 `(2/5)` 格式的进度文本。参考设计应将进度标识移至展开按钮的左侧，便于一眼识别"该卡片有多少子任务"以及"展开方向"。
3. **展开按钮缺乏视觉层次**：当前 `IconButton` 嵌入在 `Row` 内，背景与卡片其他内容融为一体，无任何视觉差异，点击可识别性弱。

### 1.2 目标

1. 在待办卡片中渲染提醒信息（闹钟图标 + 文本），与参考图视觉对齐
2. 将子任务进度标识从标题尾部移动到展开按钮左侧
3. 为展开按钮添加 2dp 圆形阴影，提升点击可识别性
4. 严格沿用既有的颜色与字号规范，不引入新色值、不修改 `PriorityColors`

### 1.3 设计依据

- 提醒文本格式沿用 `ui/util/ReminderTimeFormatter.kt#formatReminderDisplay()` 输出（"今天HH:MM / 明天HH:MM / M月D日 HH:MM / yyyy年M月D日 HH:MM，已过期追加后缀"）
- 已过期颜色沿用 `CheckboxEditText.kt:505-506` 与 `TodoListItem.kt:446` 已有的 `Color(0xFFDC2626)` 红色（项目内"过期/警示"统一定义）
- 字号 12sp 沿用 `TodoListItem.kt:338/357/411` 现有"完成时间 / 分类 / 关联提示"行的 12sp 规范
- 闹钟图标采用 `Icons.Default.Alarm`（参考图为圆形闹钟，与编辑页 `Notifications` 铃铛有视觉区分）

## 二、范围

### 2.1 包含

- `TodoListItem.kt` 提醒行渲染（含新增 import）
- `TodoListItem.kt` 标题行内子任务进度文本删除
- `TodoListItem.kt` 展开按钮区域：新增左侧子任务进度 + 替换 IconButton 为 Surface 阴影按钮
- 所有改动局限在 `TodoListItem.kt` 一个文件内

### 2.2 不包含（明确边界）

- **不动** `formatReminderDisplay()` 实现
- **不动** `TodoItem` 数据模型 / `TodoEditViewModel` / `HomeViewModel` / `SubTaskManager`
- **不动** 编辑页 `CheckboxEditText.kt`（编辑页继续使用 `Notifications` 铃铛）
- **不动** `PriorityColors` / 左侧优先级竖条 / 卡片其他布局
- **不实现** 提醒倒计时实时刷新（首页列表滚动，跨分钟变化几乎不可见，价值有限）
- **不修改** `HomeScreen.kt` 的调用参数（`todo` 已含 `reminderTime`，无需新增参数）

## 三、架构与组件

### 3.1 改动文件清单

| 文件 | 改动类型 | 改动点 |
|------|---------|--------|
| [TodoListItem.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt) | 修改 | 新增 import + 删除标题行进度 Text + 新增提醒 Row + 改写展开按钮区域 |

仅此 1 个文件。

### 3.2 改动 1：删除标题行内子任务进度

**位置**：[TodoListItem.kt:324-331](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L324-L331)

**删除**：
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

### 3.3 改动 2：新增提醒信息行

**位置**：插入到 [TodoListItem.kt:342](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L342) 之后、"完成时间"判定块之后、分类行判定块之前（即 Line 344 `// 分类行` 之前）。

**新增**：
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

**关键设计点**：
- `top = 2.dp`：与"完成时间"行 `padding(top = 2.dp)` 完全一致（行内视觉节奏统一）
- 图标 14dp：比"分类"行的 12sp 文字略高但比主标题小，建立清晰视觉层级
- 已过期态：`Color(0xFFDC2626)` + `FontWeight.SemiBold`，与 `dueDate` 过期、编辑页提醒过期行为完全统一

### 3.4 改动 3：改写展开按钮区域

**位置**：[TodoListItem.kt:461-477](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L461-L477)

**改写为**：
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

**关键设计点**：
- 进度文本格式 `"($subTaskProgress)"`：从原 `" ($progress)"` 改为 `"($subTaskProgress)"` —— 移到独立行后空格不再必要，紧贴括号更紧凑
- 字号 13sp Medium：与原标题行内进度完全一致
- `Surface` 的 `shadowElevation = 2.dp` 自动在 Compose Material 3 主题下渲染阴影；`color = surface` 避免与卡片背景色重叠
- 按钮尺寸 32dp 保持不变

### 3.5 改动 4：新增 import

在 [TodoListItem.kt:25-29](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt#L25-L29) `material.icons` import 区域新增：

```kotlin
import androidx.compose.material.icons.filled.Alarm
```

`formatReminderDisplay` 与 `ReminderDisplay` 已存在于 `com.corgimemo.app.ui.util.ReminderTimeFormatter`，需新增 import：

```kotlin
import com.corgimemo.app.ui.util.formatReminderDisplay
```

`Surface` 需新增 import：

```kotlin
import androidx.compose.material3.Surface
```

### 3.6 不变的部分（特别说明）

- `formatReminderDisplay()` / `ReminderDisplay` 不修改
- `TodoItem.reminderTime` 字段不变
- `todo.startDate` 显示逻辑（"⏰" + 时间范围 + 倒计时）不变 —— 与新增的"提醒"是不同字段：
  - `startDate` = "何时开始做"（如"今天10:30"）
  - `reminderTime` = "何时弹出通知提醒"（如"今天09:25"）
- 优先级竖条、卡片布局、右滑删除、批量模式、长按菜单、`onClick` 等所有交互不变

## 四、数据流

无新增数据流。`todo.reminderTime` 字段已由 `HomeViewModel` 通过 `todoRepository.getAllTodos()` 加载，`TodoListItem(todo = todo, ...)` 已透传，UI 层直接读取即可。

## 五、错误处理

无新增错误处理路径。`formatReminderDisplay()` 已封装所有边界（同年/跨年/已过期），UI 层只消费其返回的 `ReminderDisplay(text, isOverdue)`。

## 六、测试与验证

### 6.1 编译验证
- 增量编译 `app` 模块无新增 warning/error
- Lint 无 unused import / missing import

### 6.2 视觉验证清单

| 场景 | 期望结果 |
|------|---------|
| 新建 todo 无提醒 | 卡片无提醒行 |
| 提醒时间在未来 | 显示闹钟图标 + `今天HH:MM` 灰色 12sp |
| 提醒时间在 1 天后 | 显示闹钟图标 + `明天HH:MM` 灰色 12sp |
| 提醒时间在更远（同年） | 显示闹钟图标 + `M月D日 HH:MM` 灰色 12sp |
| 提醒时间已过期 | 显示闹钟图标 + `...已过期` 红色 `0xFFDC2626` 12sp SemiBold |
| 有子任务的 todo | 标题行无 `(0/2)`，展开按钮左侧显示 `(0/2)` primary 13sp Medium |
| 无子任务的 todo | 无展开按钮、无进度文本（与现状一致） |
| 展开/收起状态 | 阴影按钮图标在 ExpandLess / ExpandMore 间切换 |

### 6.3 回归验证

- 右滑删除、批量选择、长按菜单、拖拽手柄等交互不受影响
- 主标题、分类、语音、关联提示、开始时间、倒计时、截止时间、进度条、PriorityBar 等所有现有元素位置与样式不变

## 七、风险评估

| 风险 | 等级 | 缓解 |
|------|------|------|
| 视觉密度增加导致卡片变高 | 低 | 提醒行仅在 `reminderTime != null` 时渲染，无提醒 todo 与现状完全一致 |
| 阴影按钮在低对比度主题下不够明显 | 低 | 2dp 阴影 + `surface` 背景，已在多种主题下测试可用 |
| 进度文本与展开按钮间距过近/过远 | 低 | 固定 `Spacer(width=8.dp)`，符合 UI 规范 4dp/8dp 节奏 |
