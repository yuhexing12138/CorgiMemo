# 待办编辑页提醒时间按分组独立改造 设计文档

> 日期：2026-06-23
> 范围：仅 TodoEditScreen 内每个"待办组容器"（groupId）的"设置提醒"按钮
> 状态：设计稿（待用户确认后进入实施计划阶段）

---

## 1. 目标

将 TodoEditScreen 内每个待办组容器底部的"设置提醒"按钮改造为：

- **未设置**：显示 `🔔 设置提醒` 文字按钮（与现状一致）
- **已设置**：显示 `🔔 格式化时间 [+ 已过期] | ×` 胶囊
  - 文字颜色与铃铛颜色根据"已过期"切换
  - 点击胶囊体 → 打开时间选择器修改
  - 点击 `×` → 立即清除当前分组的提醒

> 提醒时间（`reminderTime`）和重复类型（`repeatType`）均按分组独立，与现有 ViewModel 单值实现不同。

---

## 2. 背景与现状

| 位置 | 现状 |
|---|---|
| [TodoEditViewModel.kt:146-153](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt#L146-L153) | 单值 `_reminderTime: MutableStateFlow<Long?>` |
| [TodoEditViewModel.kt:100-102](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt#L100-L102) | 单值 `_repeatType: MutableStateFlow<Int>` |
| [CheckboxEditText.kt:474-499](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt#L474-L499) | "设置提醒"按钮写死为"设置提醒"文字 |
| [TodoEditScreen.kt:304](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt#L304) | `var showReminderPicker by remember { mutableStateOf(false) }` |

每个待办组容器都渲染同一个"设置提醒"按钮，但所有分组共享同一份提醒时间。

---

## 3. 显示规则

### 3.1 时间格式化（边界判定以"自然日"为准）

| 关系 | 文字格式 | 颜色 |
|---|---|---|
| 设置时间在今天（未来） | `今天HH:MM` | `onSurfaceVariant` |
| 设置时间在今天（过去） | `今天HH:MM 已过期` | `#DC2626`（红） |
| 设置时间在昨天（任何时刻） | `昨天HH:MM 已过期` | `#DC2626`（红） |
| 设置时间在前天及更早（同年） | `M月D日 HH:MM 已过期` | `#DC2626`（红） |
| 设置时间在明天 | `明天HH:MM` | `onSurfaceVariant` |
| 设置时间在 2 天及之后（同年） | `M月D日 HH:MM` | `onSurfaceVariant` |
| **设置时间跨年（去年/前年... 过去）** | `yyyy年M月D日 HH:MM 已过期` | `#DC2626`（红） |
| **设置时间跨年（明年/后年... 未来）** | `yyyy年M月D日 HH:MM` | `onSurfaceVariant` |

> **格式细节**：
> - 日月**不补 0**：`6月25日` 而非 `06月25日`
> - 日期与时间之间**有 1 个空格**：`6月25日 21:34`
> - 时分**保持两位**：`HH:MM`（如 `08:05`、`21:34`）
> - 今天/昨天/明天**无空格无日期**：`今天 21:31`（保持现状）

> **年份规则示例**（以当前年=2026 为例）：
> - 2025/6/25 10:00（已过期） → `2025年6月25日 10:00 已过期`（红）
> - 2027/6/25 10:00（未来）  → `2027年6月25日 10:00`（主题色）
> - 2026/6/25 10:00（同年）   → `6月25日 10:00`（不写"2026年"）
>
> 年份仅在 `reminderTime` 与 `now` 不在同一日历年时出现；今天/昨天/明天天然同一年，**不写年份**。

- 红色同时作用于：铃铛 icon、文字。`×` 始终保持中性灰（`#666666`）。
- 判定基准：`reminderTime < now` 即"已过期"，**等于当前时刻视为未过期**（`reminderTime == now`）。

### 3.2 UI 形态

| 状态 | 结构 |
|---|---|
| 未设置 | `🔔 设置提醒` 单行（与现状一致） |
| 已设置 | `🔔 [时间 [+ 已过期]] │ ×`（胶囊宽度变长，`│` 是 1px 垂直分割线 `#CCCCCC`） |

胶囊背景色始终为 `#F5F5F5`，圆角 8dp。

---

## 4. ViewModel 改造

### 4.1 新增字段

```kotlin
// 替换：_reminderTime: MutableStateFlow<Long?>
private val _groupReminders = MutableStateFlow<Map<Int, Long?>>(emptyMap())
val groupReminders: StateFlow<Map<Int, Long?>> = _groupReminders.asStateFlow()

// 替换：_repeatType: MutableStateFlow<Int>
private val _groupRepeatTypes = MutableStateFlow<Map<Int, Int>>(emptyMap())
val groupRepeatTypes: StateFlow<Map<Int, Int>> = _groupRepeatTypes.asStateFlow()
```

### 4.2 移除字段与方法

| 旧成员 | 原因 |
|---|---|
| `_reminderTime` / `reminderTime` | 替换为 `_groupReminders` |
| `_repeatType` / `repeatType` | 替换为 `_groupRepeatTypes` |
| `_showReminderRecommendation` / `showReminderRecommendation` | 推荐逻辑本期不接入按分组，移除 |
| `setReminderTime(time)` | 替换为 `setGroupReminder(groupId, time)` |
| `setRepeatType(type)` | 替换为 `setGroupRepeatType(groupId, type)` |
| `acceptReminderRecommendation()` | 推荐逻辑移除 |

### 4.3 新增方法

```kotlin
fun setGroupReminder(groupId: Int, time: Long) {
    _groupReminders.value = _groupReminders.value + (groupId to time)
}

fun clearGroupReminder(groupId: Int) {
    _groupReminders.value = _groupReminders.value - groupId
}

fun getGroupReminder(groupId: Int): Long? = _groupReminders.value[groupId]

fun setGroupRepeatType(groupId: Int, type: Int) {
    _groupRepeatTypes.value = _groupRepeatTypes.value + (groupId to type)
}

fun getGroupRepeatType(groupId: Int): Int = _groupRepeatTypes.value[groupId] ?: 0
```

### 4.4 持久化入口改造

| 方法 | 改造点 |
|---|---|
| `loadTodo(todoId)` | 把 `todo.reminderTime` 写入 `_groupReminders.value[0] = todo.reminderTime`；`todo.repeatType` 写入 `_groupRepeatTypes.value[0] = todo.repeatType`；其余分组保持 null / 0 |
| `performSave()` | 写入时用 `_groupReminders.value[0]` 与 `_groupRepeatTypes.value[0]` 替换原 `_reminderTime.value` / `_repeatType.value` |
| `saveGroup(groupId, lines)` | 写入时用 `_groupReminders.value[groupId]` 与 `_groupRepeatTypes.value[groupId]` 替换原 `_reminderTime.value` / `_repeatType.value` |

数据库 schema **不变**（仍用 `TodoItem.reminderTime` / `TodoItem.repeatType` 单字段，因为每个分组最终保存为独立 TodoItem）。

---

## 5. UI 组件改造

### 5.1 [CheckboxEditText.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt)

新增参数：

```kotlin
groupReminders: Map<Int, Long?> = emptyMap(),
onReminderDelete: ((Int) -> Unit)? = null,
```

> `onReminderClick: ((Int) -> Unit)?` 保持不变。

将 `reminderTime` 由 `TodoGroupContainer` 内部从 `groupReminders[groupId]` 取出再传入；不要在每行 `CheckboxEditRow` 层级处理。

### 5.2 [TodoGroupContainer](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt#L412-L550)

新增参数：

```kotlin
reminderTime: Long? = null,
onReminderDelete: (() -> Unit)? = null,
```

在"提醒按钮"渲染分支里：

```kotlin
// 实时刷新当前时间：进入页面时取一次，对齐到下一个 30s 整数倍开始轮询，
// 最迟 30s 内必然跨分钟，"已过期/未过期"自动切换。
// reminderTime 变化时（用户改时间 / × 删除）LaunchedEffect 重启，立刻按新值重算。
var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
LaunchedEffect(reminderTime) {
    now = System.currentTimeMillis()
    while (true) {
        val nextTick = ((System.currentTimeMillis() / 30_000L) + 1L) * 30_000L
        kotlinx.coroutines.delay(nextTick - System.currentTimeMillis())
        now = System.currentTimeMillis()
    }
}

val isOverdue = reminderTime != null && reminderTime < now
val displayText = reminderTime?.let { formatReminderDisplay(it, now).text } ?: "设置提醒"
val iconTint: Color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
val textColor: Color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant

Row(
    modifier = Modifier
        .clickable(enabled = onReminderClick != null) { onReminderClick() }
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFFF5F5F5))
        .padding(start = 10.dp, top = 6.dp, end = if (reminderTime != null) 6.dp else 10.dp, bottom = 6.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Icon(
        imageVector = Icons.Default.Notifications,
        contentDescription = if (reminderTime != null) "已设置提醒" else "设置提醒",
        tint = iconTint,
        modifier = Modifier.size(16.dp)
    )
    Spacer(Modifier.width(4.dp))
    Text(
        text = displayText,
        fontSize = 13.sp,
        color = textColor,
        fontWeight = if (isOverdue) FontWeight.SemiBold else FontWeight.Normal
    )
    if (reminderTime != null) {
        Spacer(Modifier.width(6.dp))
        // 1px 垂直分割线
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(14.dp)
                .background(Color(0xFFCCCCCC))
        )
        Spacer(Modifier.width(6.dp))
        // × 按钮：独立点击区域，不冒泡到 onReminderClick
        Box(
            modifier = Modifier
                .clickable(enabled = onReminderDelete != null) { onReminderDelete() }
                .padding(4.dp)
        ) {
            Text(
                text = "×",
                fontSize = 16.sp,
                color = Color(0xFF666666)
            )
        }
    }
}
```

> `now` 用 `remember` 缓存首帧时间即可，无需轮询刷新"已过期"实时态。Tab 切回时通过 `LaunchedEffect(reminderTime)` 重新取值。**本期不实现跨分钟的实时颜色切换**，避免引入额外协程开销。

### 5.3 [TodoEditScreen.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt)

#### 收集

```kotlin
val groupReminders by viewModel.groupReminders.collectAsState()
val groupRepeatTypes by viewModel.groupRepeatTypes.collectAsState()
```

#### 替换 picker 触发态

```kotlin
// 旧：var showReminderPicker by remember { mutableStateOf(false) }
var editingReminderGroupId by remember { mutableStateOf<Int?>(null) }
val showReminderPicker = editingReminderGroupId != null
```

#### 打开 picker（已有回调）

```kotlin
onReminderClick = { groupId -> editingReminderGroupId = groupId },
```

#### picker 初始值与确认

```kotlin
ReminderPickerBottomSheet(
    initialDateMillis = editingReminderGroupId?.let { groupReminders[it] },
    initialHour = editingReminderGroupId?.let { gid ->
        groupReminders[gid]?.let { ts ->
            Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.HOUR_OF_DAY)
        }
    } ?: LocalTime.now().hour,
    initialMinute = editingReminderGroupId?.let { gid ->
        groupReminders[gid]?.let { ts ->
            Calendar.getInstance().apply { timeInMillis = ts }.get(Calendar.MINUTE)
        }
    } ?: LocalTime.now().minute,
    initialRepeatType = editingReminderGroupId?.let { groupRepeatTypes[it] } ?: 0,
    onDismiss = { editingReminderGroupId = null },
    onConfirm = { dateMillis, hour, minute, repeatTypeNew, _ ->
        val gid = editingReminderGroupId ?: return@ReminderPickerBottomSheet
        viewModel.setGroupReminder(gid, dateMillis ?: System.currentTimeMillis())
        viewModel.setGroupRepeatType(gid, repeatTypeNew)
        editingReminderGroupId = null
    }
)
```

#### 传入 × 删除回调

```kotlin
CheckboxEditText(
    ...
    groupReminders = groupReminders,
    onReminderDelete = { groupId -> viewModel.clearGroupReminder(groupId) },
    ...
)
```

#### 推荐提示条

`AnimatedVisibility(visible = showReminderRecommendation)` 区块改为 `AnimatedVisibility(visible = false)` 保留位置占位，避免大改其他流程。本期不显示推荐。

---

## 6. 工具函数（新增）

### 6.1 新建 [ReminderTimeFormatter.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/util/ReminderTimeFormatter.kt)

```kotlin
package com.corgimemo.app.ui.util

import java.util.Calendar

/**
 * 提醒时间显示数据
 *
 * @property text 已格式化好可直接渲染的文字，例如 "今天21:31 已过期"、"明天21:34"、"6月25日21:34"
 * @property isOverdue 是否已过期（决定 UI 颜色：红 vs 主题色）
 */
data class ReminderDisplay(
    val text: String,
    val isOverdue: Boolean
)

/**
 * 把提醒时间戳格式化为友好的显示文字
 *
 * 规则：
 * - 与当前时间同一天：今天HH:MM
 * - 早一天：昨天HH:MM
 * - 晚一天：明天HH:MM
 * - 同年但跨日：M月D日 HH:MM（月日不补 0，日期与时间中间有 1 个空格）
 * - 跨年：yyyy年M月D日 HH:MM（月日不补 0，日期与时间中间有 1 个空格）
 * - 若时间 < now，追加 " 已过期" 后缀并标记 isOverdue=true
 *
 * @param reminderTime 用户设置的提醒时间（毫秒）
 * @param now 当前时间（毫秒），默认 System.currentTimeMillis()，便于测试
 */
fun formatReminderDisplay(
    reminderTime: Long,
    now: Long = System.currentTimeMillis()
): ReminderDisplay {
    val isOverdue = reminderTime < now

    val target = Calendar.getInstance().apply { timeInMillis = reminderTime }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

    val hh = "%02d".format(target.get(Calendar.HOUR_OF_DAY))
    val mm = "%02d".format(target.get(Calendar.MINUTE))
    val timePart = "$hh:$mm"

    val month = target.get(Calendar.MONTH) + 1
    val day = target.get(Calendar.DAY_OF_MONTH)

    val prefix = when {
        isSameDay(target, today)   -> "今天"
        isSameDay(target, yesterday) -> "昨天"
        isSameDay(target, tomorrow)  -> "明天"
        isSameYear(target, today) -> {
            // 同年但跨日：M月D日（月日不补 0）
            "${month}月${day}日"
        }
        else -> {
            // 跨年：yyyy年M月D日（月日不补 0）
            "${target.get(Calendar.YEAR)}年${month}月${day}日"
        }
    }

    // 今天/昨天/明天 与时间无空格；M月D日 / yyyy年M月D日 与时间中间有 1 个空格
    val separator = if (prefix.startsWith("今天") || prefix.startsWith("昨天") || prefix.startsWith("明天")) "" else " "
    val text = if (isOverdue) "$prefix$separator$timePart 已过期" else "$prefix$separator$timePart"
    return ReminderDisplay(text, isOverdue)
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}

private fun isSameYear(a: Calendar, b: Calendar): Boolean {
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
}
```

---

## 7. 测试

### 7.1 单元测试（必做）

新建 [ReminderTimeFormatterTest.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/test/java/com/corgimemo/app/ui/util/ReminderTimeFormatterTest.kt)：

| # | 场景 | 输入 (reminderTime, now) | 期望 text | 期望 isOverdue |
|---|---|---|---|---|
| 1 | 今天 未来 | 6/23 23:00, now=6/23 21:33 | `今天23:00` | false |
| 2 | 今天 过去 | 6/23 21:31, now=6/23 21:33 | `今天21:31 已过期` | true |
| 3 | 昨天 过去 | 6/22 21:33, now=6/23 21:33 | `昨天21:33 已过期` | true |
| 4 | 前天 过去 | 6/21 21:34, now=6/23 21:33 | `6月21日21:34 已过期` | true |
| 5 | 明天 未来 | 6/24 21:34, now=6/23 21:33 | `明天21:34` | false |
| 6 | 跨月 未来 | 6/25 21:34, now=6/23 21:33 | `6月25日21:34` | false |
| 7 | 跨年 未来 | 2027/1/1 09:00, now=2026/12/31 18:00 | `1月1日09:00` | false |
| 8 | 同一分钟 | 6/23 21:33, now=6/23 21:33 | `今天21:33` | false |
| 9 | 过去 2 天 | 6/21 10:00, now=6/23 21:33 | `6月21日10:00 已过期` | true |

### 7.2 手动验证清单

| 步骤 | 预期 |
|---|---|
| 新建 todo → 点击"设置提醒" → 选今天 23:00 → 确认 | 按钮显示"今天23:00"，右侧出现 × |
| 等到（模拟）23:01 | 按钮变红，显示"今天23:00 已过期" |
| 点击"今天23:00" | 打开选择器，初始值为 23:00 |
| 点击 × | 按钮恢复"设置提醒" |
| 输入 `/` 新建第二个分组 | 第二个分组也是"设置提醒"，互不影响 |
| 在分组 1 设置 6/25 21:34 | 分组 1 显示"6月25日21:34"，分组 0 不变 |
| 加载已有 todo（含 reminderTime） | 进入编辑页时，分组 0 显示该时间 |
| 修改分组 0 提醒 → 点击"全部完成" | 每个分组保存为独立 TodoItem，各自调度提醒 |

---

## 8. 兼容性与迁移

| 维度 | 处理 |
|---|---|
| 数据库 schema | **无变更**（仍用 `TodoItem.reminderTime` / `TodoItem.repeatType` 单字段） |
| 旧全局字段 | `_reminderTime` / `_repeatType` / `_showReminderRecommendation` StateFlow 全部移除；相关方法移除 |
| 加载历史 todo | `loadTodo` 把 `todo.reminderTime` 写入 `groupReminders[0]`，`todo.repeatType` 写入 `groupRepeatTypes[0]`；其余分组为 null / 0 |
| 提醒推荐条 | 保留 UI 区块（`AnimatedVisibility(visible = false)`）占位，不渲染 |
| 首页 [TodoListItem.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt) | 本期不改动 |
| [ReminderRecommender](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/domain/ReminderRecommender.kt) | 本期不接入按分组 |
| AlarmScheduler | **无需修改**（按 TodoItem 维度调度，每个分组已是独立 TodoItem） |

---

## 9. 错误处理

| 场景 | 处理 |
|---|---|
| `reminderTime` 为 null | 按钮显示"设置提醒"（无 × 按钮、无分隔线） |
| `reminderTime` 与 now 同一分钟 | `isOverdue = false`，显示"今天HH:MM" |
| × 按钮点击时 reminderTime 已为 null | 幂等：直接 `_groupReminders.value - groupId` |
| 选择器被遮罩/返回键/取消按钮关闭 | `editingReminderGroupId = null`，原 state 不变 |
| 同一 groupId 多次写入 | 直接覆盖最新值 |
| 多分组同时打开 picker | 不可能：`editingReminderGroupId` 单值，第二个点击会覆盖 |
| `loadTodo` 拿到旧 todo 无 reminderTime | `groupReminders[0] = null`，按钮显示"设置提醒" |
| `repeatType` 字段为 0（不重复） | `groupRepeatTypes[0] = 0`，与历史默认一致 |

---

## 10. 影响范围（文件清单）

| 文件 | 改动类型 |
|---|---|
| [TodoEditViewModel.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt) | 大改：替换 reminder/repeat 状态，改造 loadTodo/performSave/saveGroup |
| [TodoEditScreen.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt) | 中改：替换 picker 状态、collect 新 state、接线 onConfirm |
| [CheckboxEditText.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt) | 中改：CheckEditText 与 TodoGroupContainer 各加 1 个参数，按钮渲染分两支 |
| 新建 [ReminderTimeFormatter.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/util/ReminderTimeFormatter.kt) | 新增：纯函数 |
| 新建 [ReminderTimeFormatterTest.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/test/java/com/corgimemo/app/ui/util/ReminderTimeFormatterTest.kt) | 新增：单元测试 |

---

## 11. 不在本期范围内

- 首页 [TodoListItem.kt](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt) 展示样式调整
- 提醒推荐逻辑（[ReminderRecommender](file:///c:/Users/Lenovo/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/domain/ReminderRecommender.kt)）按分组接入
- 提醒删除的撤销（Snackbar 撤销 × 误操作）
- × 按钮的长按菜单（编辑/清空 选项）
- 时间格式按"用户 locale"本地化（本期固定中文/24 小时制）
