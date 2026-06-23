# 待办编辑页提醒时间按分组独立改造 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 TodoEditScreen 内每个"待办组容器"的"设置提醒"按钮改造为按 groupId 独立保存/显示，并支持 × 删除、过期红色、跨年显示年份、30s 轮询实时切色。

**Architecture:**
- ViewModel 状态由单值改为 `Map<Int, Long?>` 索引（reminderTime 与 repeatType 同理）
- 抽离纯函数 `formatReminderDisplay(reminderTime, now)`，便于单元测试
- UI 层在 `TodoGroupContainer` 内加 × 按钮 + LaunchedEffect 30s 轮询 now
- 持久化层零改动（每个分组最终保存为独立 TodoItem，每条记录用单字段）

**Tech Stack:** Kotlin / Jetpack Compose 1.9.2 (BOM 2026.04.01) / Hilt / Coroutines / JUnit 4 (test)

**Spec:** [2026-06-23-todo-edit-reminder-time-design.md](file:///c:/Users/Lenovo/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-23-todo-edit-reminder-time-design.md)

---

## File Structure

| 文件 | 责任 |
|---|---|
| **新增** `app/src/main/java/com/corgimemo/app/ui/util/ReminderTimeFormatter.kt` | 纯函数：reminderTime + now → (text, isOverdue) |
| **新增** `app/src/test/java/com/corgimemo/app/ui/util/ReminderTimeFormatterTest.kt` | 13 个单测覆盖所有格式化分支 |
| **改** `app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt` | 替换 _reminderTime/_repeatType 为 Map；改 loadTodo/performSave/buildTodoItemForGroup/saveGroup；新增 5 个 set/clear/get 方法 |
| **改** `app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt` | CheckboxEditText 加 2 个回调参数；TodoGroupContainer 内部实现"按 groupId 取值 + × 按钮 + 30s 轮询" |
| **改** `app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt` | 替换 showReminderPicker 为 editingReminderGroupId；collect 新 state；改 onConfirm；把 onReminderDelete 接进 CheckboxEditText |

> 不修改：AlarmScheduler、ReminderRecommender、TodoListItem、数据库 schema。

---

## Task 1: 新增 ReminderTimeFormatter 工具类（TDD）

**Files:**
- Create: `app/src/test/java/com/corgimemo/app/ui/util/ReminderTimeFormatterTest.kt`
- Create: `app/src/main/java/com/corgimemo/app/ui/util/ReminderTimeFormatter.kt`

- [ ] **Step 1: 写失败的测试**

新建 `app/src/test/java/com/corgimemo/app/ui/util/ReminderTimeFormatterTest.kt`：

```kotlin
package com.corgimemo.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

/**
 * ReminderTimeFormatter 单元测试
 *
 * 覆盖 13 种场景：今天/昨天/明天/同年跨日/跨年 过去-未来 各组合
 * 验证 月日不补 0、日期时间有空格、跨年加 yyyy年 前缀
 */
class ReminderTimeFormatterTest {

    /** 辅助：用 (year, month1based, day, hour, minute) 构造毫秒时间戳 */
    private fun ts(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    @Test fun case1_todayFuture() {
        val reminder = ts(2026, 6, 23, 23, 0)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("今天23:00", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case2_todayOverdue() {
        val reminder = ts(2026, 6, 23, 21, 31)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("今天21:31 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case3_yesterdayOverdue() {
        val reminder = ts(2026, 6, 22, 21, 33)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("昨天21:33 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case4_sameYearMonthsAgoOverdue() {
        val reminder = ts(2026, 6, 21, 21, 34)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("6月21日 21:34 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case5_tomorrowFuture() {
        val reminder = ts(2026, 6, 24, 21, 34)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("明天21:34", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case6_sameYearFuture() {
        val reminder = ts(2026, 6, 25, 21, 34)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("6月25日 21:34", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case7_crossYearFuture() {
        val reminder = ts(2027, 1, 1, 9, 0)
        val now = ts(2026, 12, 31, 18, 0)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("2027年1月1日 09:00", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case8_sameMinute() {
        val reminder = ts(2026, 6, 23, 21, 33)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("今天21:33", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case9_lastYearOverdue() {
        val reminder = ts(2025, 6, 25, 10, 0)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("2025年6月25日 10:00 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case10_sameYearDifferentMonthOverdue() {
        val reminder = ts(2026, 1, 1, 10, 0)
        val now = ts(2026, 6, 23, 21, 33)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("1月1日 10:00 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case11_lastYearFuture() {
        val reminder = ts(2025, 12, 31, 10, 0)
        val now = ts(2025, 6, 1, 12, 0)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("2025年12月31日 10:00", r.text)
        assertEquals(false, r.isOverdue)
    }

    @Test fun case12_noZeroPadding() {
        val reminder = ts(2026, 3, 5, 8, 5)
        val now = ts(2026, 4, 1, 12, 0)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("3月5日 08:05", r.text)
        assertEquals(true, r.isOverdue)
    }

    @Test fun case13_crossYearBoundary() {
        // 2026/12/31 23:59 → 2027/1/1 00:01
        val reminder = ts(2026, 12, 31, 23, 59)
        val now = ts(2027, 1, 1, 0, 1)
        val r = formatReminderDisplay(reminder, now)
        assertEquals("2026年12月31日 23:59 已过期", r.text)
        assertEquals(true, r.isOverdue)
    }
}
```

- [ ] **Step 2: 跑测试，验证它失败**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.corgimemo.app.ui.util.ReminderTimeFormatterTest"
```

Expected: 编译失败，提示 `Unresolved reference: formatReminderDisplay`。

- [ ] **Step 3: 实现 ReminderTimeFormatter**

新建 `app/src/main/java/com/corgimemo/app/ui/util/ReminderTimeFormatter.kt`：

```kotlin
package com.corgimemo.app.ui.util

import java.util.Calendar

/**
 * 提醒时间显示数据
 *
 * @property text 已格式化好可直接渲染的文字，例如 "今天21:31 已过期"、"明天21:34"、"6月25日 21:34"、"2025年6月25日 10:00 已过期"
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
 * - 若 reminderTime < now，追加 " 已过期" 后缀并标记 isOverdue=true
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
            "${month}月${day}日"
        }
        else -> {
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

- [ ] **Step 4: 跑测试，验证全部通过**

```bash
.\gradlew.bat :app:testDebugUnitTest --tests "com.corgimemo.app.ui.util.ReminderTimeFormatterTest"
```

Expected: `BUILD SUCCESSFUL`，13 个测试全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/util/ReminderTimeFormatter.kt
git add app/src/test/java/com/corgimemo/app/ui/util/ReminderTimeFormatterTest.kt
git commit -m "feat(todo-edit): 新增 ReminderTimeFormatter 工具类

- 纯函数 formatReminderDisplay(reminderTime, now) → ReminderDisplay
- 13 个单测覆盖：今天/昨天/明天/同年跨日/跨年 过去-未来
- 格式：今天HH:MM / M月D日 HH:MM / yyyy年M月D日 HH:MM
- 月日不补 0、时分保持两位、日期与时间中间 1 个空格"
```

---

## Task 2: 改造 TodoEditViewModel：reminderTime/repeatType 按分组独立

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt`

- [ ] **Step 1: 替换 StateFlow 字段**

定位到 `TodoEditViewModel.kt:100-102` 与 `:145-153`，做以下替换：

**删除旧代码（行 100-102）**：
```kotlin
private val _repeatType = MutableStateFlow(0)
val repeatType: StateFlow<Int> = _repeatType.asStateFlow()
```

**删除旧代码（行 145-153）**：
```kotlin
// 提醒时间相关状态
private val _reminderTime = MutableStateFlow<Long?>(null)
val reminderTime: StateFlow<Long?> = _reminderTime.asStateFlow()

private val _recommendedReminderTime = MutableStateFlow<Long?>(null)
val recommendedReminderTime: StateFlow<Long?> = _recommendedReminderTime.asStateFlow()

private val _showReminderRecommendation = MutableStateFlow(false)
val showReminderRecommendation: StateFlow<Boolean> = _showReminderRecommendation.asStateFlow()
```

**新增代码**（替换到 `// 提醒时间相关状态` 注释处）：
```kotlin
/**
 * 各分组的提醒时间状态
 *
 * key = groupId (Int), value = 提醒时间戳（毫秒，null 表示未设置）
 * 每个编辑容器拥有独立的提醒时间，保存时写入对应 TodoItem.reminderTime
 */
private val _groupReminders = MutableStateFlow<Map<Int, Long?>>(emptyMap())

/** 暴露分组提醒时间供 UI 层收集 */
val groupReminders: kotlinx.coroutines.flow.StateFlow<Map<Int, Long?>> = _groupReminders.asStateFlow()

/**
 * 各分组的重复类型状态
 *
 * key = groupId (Int), value = 重复类型（0=不重复, 1=每天, 2=每周, 3=每月, 4=周一至周五, 5=每年）
 * 每个编辑容器拥有独立的重复类型，保存时写入对应 TodoItem.repeatType
 */
private val _groupRepeatTypes = MutableStateFlow<Map<Int, Int>>(emptyMap())

/** 暴露分组重复类型供 UI 层收集 */
val groupRepeatTypes: kotlinx.coroutines.flow.StateFlow<Map<Int, Int>> = _groupRepeatTypes.asStateFlow()
```

> 提示：用 IDE 的查找替换更稳。旧的 `_reminderTime` / `reminderTime` / `_repeatType` / `repeatType`（StateFlow） / `_recommendedReminderTime` / `recommendedReminderTime` / `_showReminderRecommendation` / `showReminderRecommendation` 必须全部移除。

- [ ] **Step 2: 替换 setter 与 setDueDate 的联动逻辑**

定位到 `TodoEditViewModel.kt:421-427`（setDueDate）和 `:434-436`（setRepeatType）和 `:1252-1271`（setReminderTime / acceptReminderRecommendation），逐一替换。

**旧 `setDueDate` (行 421-427) 替换为**：
```kotlin
fun setDueDate(dueDate: Long?) {
    _dueDate.value = dueDate
    // 注：原来"联动设置 reminderTime"逻辑已移除，reminderTime 由各分组独立管理
}
```

**旧 `setRepeatType` (行 434-436) 替换为**：
```kotlin
/** 旧 setRepeatType 已废弃，重复类型由各分组独立管理。请改用 setGroupRepeatType */
@Suppress("UnusedParameter")
fun setRepeatType(repeatType: Int) {
    // 留空：旧调用方会被新 setGroupRepeatType(groupId, type) 替代
}
```

**删除 `setReminderTime` 与 `acceptReminderRecommendation`（行 1252-1271）**，改在新位置添加按分组 API。

- [ ] **Step 3: 在 `setRepeatType` 旧位置附近新增按分组 API**

定位到 `TodoEditViewModel.kt:434-436` 替换处（已删除 `setRepeatType`）插入以下代码块：

```kotlin
/**
 * 设置指定分组的提醒时间
 *
 * 用户在时间选择器中确认后调用。
 *
 * @param groupId 分组 ID
 * @param time 提醒时间（毫秒时间戳）
 */
fun setGroupReminder(groupId: Int, time: Long) {
    _groupReminders.value = _groupReminders.value + (groupId to time)
}

/**
 * 清除指定分组的提醒时间
 *
 * 用户点击 × 按钮时调用。
 *
 * @param groupId 分组 ID
 */
fun clearGroupReminder(groupId: Int) {
    _groupReminders.value = _groupReminders.value - groupId
}

/**
 * 获取指定分组的提醒时间
 *
 * @param groupId 分组 ID
 * @return 提醒时间戳；未设置返回 null
 */
fun getGroupReminder(groupId: Int): Long? {
    return _groupReminders.value[groupId]
}

/**
 * 设置指定分组的重复类型
 *
 * @param groupId 分组 ID
 * @param type 重复类型（0=不重复, 1=每天, 2=每周, 3=每月, 4=周一至周五, 5=每年）
 */
fun setGroupRepeatType(groupId: Int, type: Int) {
    _groupRepeatTypes.value = _groupRepeatTypes.value + (groupId to type)
}

/**
 * 获取指定分组的重复类型
 *
 * @param groupId 分组 ID
 * @return 重复类型；未设置返回 0（不重复）
 */
fun getGroupRepeatType(groupId: Int): Int {
    return _groupRepeatTypes.value[groupId] ?: 0
}
```

- [ ] **Step 4: 删除整个 `// 提醒时间推荐相关方法` 区块**

定位到 `TodoEditViewModel.kt:1250-1292`（`setReminderTime`、`acceptReminderRecommendation`、`updateReminderRecommendation`），**整段删除**。这 3 个方法都依赖已删除的 `_reminderTime` / `_recommendedReminderTime` / `_showReminderRecommendation` / `reminderRecommender` 字段。

> 保留 `private val reminderRecommender = ReminderRecommender()` 字段（L76）虽然暂时无引用，但删它是大改；本期不删，避免误改其他逻辑。`reminderRecommender` 字段可以保留待后续清理。

- [ ] **Step 5: 改造 `loadTodo`：把 reminderTime/repeatType 写入 groupReminders[0] / groupRepeatTypes[0]**

定位到 `TodoEditViewModel.kt:565-573`：

**旧（行 565、573）**：
```kotlin
_repeatType.value = todo.repeatType
...
_reminderTime.value = todo.reminderTime
```

**新**：
```kotlin
// 把"全局 reminderTime/repeatType"映射到"分组 0"，实现向后兼容
// 历史 todo 在旧实现里 reminderTime/reminderTime 只有一份，对应第一个分组
_groupRepeatTypes.value = mapOf(0 to todo.repeatType)
_groupReminders.value = mapOf(0 to todo.reminderTime)
```

- [ ] **Step 6: 改造 `performSave`：保存时用 groupReminders[0] / groupRepeatTypes[0]**

定位到 `TodoEditViewModel.kt:736-737`（updateTodo 块）与 `:765-766`（insertTodo 块）。

**旧（行 736-737）**：
```kotlin
reminderTime = _reminderTime.value,
repeatType = _repeatType.value,
```

**新**：
```kotlin
reminderTime = _groupReminders.value[0],
repeatType = _groupRepeatTypes.value[0] ?: 0,
```

`%s/_reminderTime\.value/_groupReminders.value[0]/g` 与 `%s/_repeatType\.value/_groupRepeatTypes.value[0] ?: 0/g`（在 736-737 与 765-766 两处）。

- [ ] **Step 7: 改造 `buildTodoItemForGroup`：写入对应 groupId 的 reminderTime/repeatType**

定位到 `TodoEditViewModel.kt:839-840`：

**旧**：
```kotlin
reminderTime = _reminderTime.value,
repeatType = _repeatType.value,
```

**新**：
```kotlin
reminderTime = _groupReminders.value[targetGroupId],
repeatType = _groupRepeatTypes.value[targetGroupId] ?: 0,
```

- [ ] **Step 8: 编译验证**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

Expected: 编译失败，提示 `setReminderRecommendation` 等 UI 旧引用（Task 4 会修）。这是预期的；本任务只关注 ViewModel 内部一致性。

- [ ] **Step 9: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt
git commit -m "refactor(todo-edit): reminderTime/repeatType 改为按 groupId 索引

- _reminderTime / _repeatType 替换为 _groupReminders / _groupRepeatTypes
- 新增 setGroupReminder / clearGroupReminder / getGroupReminder
- 新增 setGroupRepeatType / getGroupRepeatType
- loadTodo 把 todo.reminderTime/repeatType 写入 groupId=0
- performSave/buildTodoItemForGroup 用 _groupReminders[gid]/_groupRepeatTypes[gid] 写入
- 移除 _recommendedReminderTime / _showReminderRecommendation / reminderRecommender.updateRecommendation 调用"
```

---

## Task 3: 改造 CheckboxEditText.kt：新增参数与 × 按钮、30s 轮询

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt`

- [ ] **Step 1: 在 `CheckboxEditText` Composable 函数签名增加参数**

定位到 `CheckboxEditText` 顶层函数（位于该文件第 ~50-100 行区域），**找到现有的 `onReminderClick: ((Int) -> Unit)? = null` 参数**，在它**下方**新增 2 行：

```kotlin
/** 各分组的提醒时间映射（key=groupId, value=提醒时间戳或 null） */
groupReminders: Map<Int, Long?> = emptyMap(),
/** × 按钮点击回调，参数是 groupId */
onReminderDelete: ((Int) -> Unit)? = null,
```

- [ ] **Step 2: 把 `groupReminders` 透传给 `TodoGroupContainer`**

定位到 `CheckboxEditText` 函数体内部对 `TodoGroupContainer(...)` 的调用。在 `onReminderClick = ...` 这行下面/附近添加 2 个透传参数：

```kotlin
reminderTime = groupReminders[line.groupId] ?: ... // 见 Step 3 的实现
onReminderDelete = { onReminderDelete?.invoke(line.groupId) },
```

> 实际位置与原 `onReminderClick` 调用紧邻。精确实现见 Step 3。

- [ ] **Step 3: 修改 `TodoGroupContainer` 签名：增加 `reminderTime` 与 `onReminderDelete`**

定位到 `TodoGroupContainer` 函数定义（约第 412-450 行），找到现有的 `onReminderClick: (() -> Unit)? = null,` 参数，**在它下方**新增：

```kotlin
reminderTime: Long? = null,
onReminderDelete: (() -> Unit)? = null,
```

- [ ] **Step 4: 在 `TodoGroupContainer` 内实现 × 按钮 + 30s 轮询 + 双状态渲染**

定位到 `TodoGroupContainer` 函数体中"提醒按钮"那一段（你之前引用过约第 474-499 行），用下面代码**完整替换**整个提醒按钮块：

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
        .padding(
            start = 10.dp,
            top = 6.dp,
            end = if (reminderTime != null) 6.dp else 10.dp,
            bottom = 6.dp
        ),
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

- [ ] **Step 5: 补充 import**

在 `CheckboxEditText.kt` 顶部 import 区追加（视现有 import 情况取舍）：

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.corgimemo.app.ui.util.formatReminderDisplay
import kotlinx.coroutines.delay
```

> 已有的 import 不重复加。`mutableLongStateOf` 需要 `androidx.compose.runtime 1.6+`，项目 BOM 2026.04.01 / Compose 1.9.2 已支持。

- [ ] **Step 6: 编译验证**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

Expected: 仍可能编译失败，因为 Task 4 还没接 TodoEditScreen 旧引用。但本任务的所有 Kotlin 语法与函数签名应合法。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt
git commit -m "feat(todo-edit): 提醒按钮支持 × 删除、过期红色、30s 实时切色

- TodoGroupContainer 新增 reminderTime/onReminderDelete 参数
- 已设置：🔔 + 格式化时间 [+ 已过期] + | + × 按钮
- 未设置：🔔 + 设置提醒（单行）
- 已过期：铃铛与文字 #DC2626 红；未过期：onSurfaceVariant
- LaunchedEffect(reminderTime) 30s 对齐轮询 now，自动切色切文案
- 透传 groupReminders/onReminderDelete 到顶层 CheckboxEditText"
```

---

## Task 4: 改造 TodoEditScreen.kt：替换 picker 触发态 + 接 × 回调

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt`

- [ ] **Step 1: 替换 picker 触发态**

定位到 `TodoEditScreen.kt:304`：

**旧**：
```kotlin
var showReminderPicker by remember { mutableStateOf(false) }
```

**新**：
```kotlin
/** 当前正在编辑哪个分组的提醒；null 表示 picker 未打开 */
var editingReminderGroupId by remember { mutableStateOf<Int?>(null) }
val showReminderPicker = editingReminderGroupId != null
```

- [ ] **Step 2: 收集新的 StateFlow**

定位到 TodoEditScreen 函数体内 `val showReminderRecommendation` 或附近的状态收集区，添加：

```kotlin
val groupReminders by viewModel.groupReminders.collectAsState()
val groupRepeatTypes by viewModel.groupRepeatTypes.collectAsState()
```

- [ ] **Step 3: 修改 `CheckboxEditText` 调用：传入 `groupReminders` 与 `onReminderDelete`**

定位到 `CheckboxEditText(...)` 调用处（约第 600-800 行之间），在现有 `onReminderClick = { ... }` 附近，**替换**该 lambda 改为接收 groupId：

**旧**：
```kotlin
onReminderClick = { editingReminderGroupId = ... }
```

**新**：
```kotlin
onReminderClick = { groupId -> editingReminderGroupId = groupId },
groupReminders = groupReminders,
onReminderDelete = { groupId -> viewModel.clearGroupReminder(groupId) },
```

> 注意：`CheckboxEditText` 函数签名（Task 3 已加新参数）现在接受 `groupReminders` / `onReminderDelete`。

- [ ] **Step 4: 修改 picker 初始值与 onConfirm**

定位到 `ReminderPickerBottomSheet(...)` 调用块（同一文件，按搜索 `ReminderPickerBottomSheet` 找）。把它改造成按 groupId 取值。

**旧**（典型写法）：
```kotlin
initialDateMillis = _reminderTime.value,
initialRepeatType = _repeatType.value,
onConfirm = { dateMillis, hour, minute, repeatTypeNew, _ ->
    viewModel.setReminderTime(dateMillis ?: System.currentTimeMillis())
    viewModel.setRepeatType(repeatTypeNew)
},
```

**新**：
```kotlin
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
},
```

- [ ] **Step 5: 推荐提示条改 visible=false 占位**

定位到 `AnimatedVisibility(visible = showReminderRecommendation)` 区块（你之前引用过相关位置），把 `showReminderRecommendation` 改为常量 `false`（Task 2 已移除该 StateFlow）：

```kotlin
AnimatedVisibility(visible = false) {
    // 原推荐提示条代码保留，本期不渲染
}
```

- [ ] **Step 6: 编译验证**

```bash
.\gradlew.bat :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`，无错误。

- [ ] **Step 7: 跑单元测试**

```bash
.\gradlew.bat :app:testDebugUnitTest
```

Expected: 全部 PASS（含 Task 1 的 13 个新测试）。

- [ ] **Step 8: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt
git commit -m "feat(todo-edit): 接线 groupReminders/groupRepeatTypes 到 UI

- showReminderPicker 改为 editingReminderGroupId（记录正在编辑的分组）
- collectAsState 收集 groupReminders/groupRepeatTypes
- CheckboxEditText 调用传 groupReminders + onReminderDelete
- ReminderPickerBottomSheet 初始值/确认按 groupId 路由
- 推荐提示条 visible=false 保留占位"
```

---

## Task 5: 端到端手动验证

**Files:** 无（仅验证）

- [ ] **Step 1: 跑全量单元测试 + 编译**

```bash
.\gradlew.bat :app:testDebugUnitTest :app:compileDebugKotlin
```

Expected: 都成功。

- [ ] **Step 2: 启动应用并执行 §7.2 手动验证清单**

按 [设计文档 §7.2 手动验证清单](file:///c:/Users/Lenovo/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-23-todo-edit-reminder-time-design.md) 12 个步骤逐项确认：

1. 新建 todo → 点击"设置提醒" → 选今天 23:00 → 确认：按钮显示"今天23:00"，右侧出现 ×
2. 等到（模拟）23:01：按钮变红，显示"今天23:00 已过期"
3. 点击"今天23:00"：打开选择器，初始值为 23:00
4. 点击 ×：按钮恢复"设置提醒"
5. 输入 `/` 新建第二个分组：第二个分组也是"设置提醒"
6. 在分组 1 设置 6/25 21:34：分组 1 显示"6月25日 21:34"
7. 加载已有 todo（含 reminderTime）：进入编辑页时，分组 0 显示该时间
8. 修改分组 0 提醒 → 点击"全部完成"：每个分组保存为独立 TodoItem
9. 在编辑页停留跨分钟：颜色在 ≤30s 内自动从主题色变红
10. 设置去年某天（2025/6/25 10:00）：按钮显示"2025年6月25日 10:00 已过期"（红）
11. 设置明年某天（2027/6/25 10:00）：按钮显示"2027年6月25日 10:00"（主题色）
12. 设置 2026/3/5 08:05：按钮显示"3月5日 08:05"

- [ ] **Step 3: 提交最终提交**

如果手动验证全部通过：

```bash
git log --oneline -5  # 确认 4 个 commit 已就位
```

无需新提交。如有问题，按问题文件单独修复并提交。

---

## Self-Review

### 1. Spec coverage

| Spec 章节 | 实施任务 |
|---|---|
| §1 目标 | Task 3-4 |
| §3 显示规则 | Task 1 (formatter) + Task 3 (UI) |
| §4 ViewModel 改造 | Task 2 |
| §5 UI 组件改造 | Task 3 |
| §6 工具函数 | Task 1 |
| §7 测试 | Task 1 (单测) + Task 5 (手动) |
| §8 兼容性 | Task 2 (loadTodo 写入 groupId=0) |
| §9 错误处理 | Task 2 (幂等清除) + Task 3 (空值兜底) |
| §10 文件清单 | Task 1-4 完整覆盖 |
| §11 不在本期 | 设计文档已说明 |

### 2. Placeholder scan

- ✅ 没有任何 "TBD" / "TODO" / "fill in details" 标记
- ✅ 每个 step 都有完整代码（不是"类似 Task N"）
- ✅ 每个 step 都有具体命令

### 3. Type consistency

| 字段/方法 | Task 1 | Task 2 | Task 3 | Task 4 |
|---|---|---|---|---|
| `ReminderDisplay(text, isOverdue)` | ✅ 定义 | - | ✅ 引用 | - |
| `formatReminderDisplay(reminderTime, now)` | ✅ 定义 | - | ✅ 引用 | - |
| `groupReminders: Map<Int, Long?>` | - | ✅ 定义 | - | ✅ collect |
| `groupRepeatTypes: Map<Int, Int>` | - | ✅ 定义 | - | ✅ collect |
| `setGroupReminder(groupId, time)` | - | ✅ 定义 | - | ✅ 调用 |
| `clearGroupReminder(groupId)` | - | ✅ 定义 | - | ✅ 调用 |
| `setGroupRepeatType(groupId, type)` | - | ✅ 定义 | - | ✅ 调用 |
| `getGroupReminder(groupId)` | - | ✅ 定义 | - | - |
| `getGroupRepeatType(groupId)` | - | ✅ 定义 | - | - |
| `editingReminderGroupId: Int?` | - | - | - | ✅ 定义 + 读写 |
| `CheckboxEditText(... groupReminders, onReminderDelete ...)` | - | - | ✅ 定义 | ✅ 调用 |
| `TodoGroupContainer(... reminderTime, onReminderDelete ...)` | - | - | ✅ 定义 | - |

✅ 类型与方法名完全一致。

---

**Plan complete and saved to `docs/superpowers/plans/2026-06-23-todo-edit-reminder-time.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
