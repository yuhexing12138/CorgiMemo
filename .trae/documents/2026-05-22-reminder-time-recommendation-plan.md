# 提醒时间智能推荐功能 - 实现计划

## 概述

在创建/编辑待办时，根据任务分类和截止时间，自动推荐最佳提醒时间。同时支持用户偏好学习，记录用户的手动修改行为以优化后续推荐。

---

## 当前状态分析

| 模块 | 文件 | 现状 |
|------|------|------|
| 提醒调度 | `notification/AlarmScheduler.kt` | `getDefaultAdvanceMinutes()` 使用旧值与新需求不一致 |
| DataStore | `data/local/datastore/CorgiPreferences.kt` | 已有 `reminder_advance_{categoryId}` 读写方法，直接复用 |
| 创建待办 | `ui/components/TodoCreateBottomSheet.kt` | 有截止日期按钮，无提醒时间选择器 |
| 编辑待办 | `ui/screens/todo/TodoEditScreen.kt` | 同上，无提醒时间选择器 |
| ViewModel | `viewmodel/TodoEditViewModel.kt` | 已有分类推荐模式（`recommendedCategory`），可参考 |
| 分类模型 | `data/model/Category.kt` | `CategoryType`: STUDY=0, WORK=1, LIFE=2, SPORT=3, CUSTOM=4 |
| 待办模型 | `data/model/TodoItem.kt` | 已有 `reminderTime` 字段（Long?） |
| 推荐标签 | `ui/components/RecommendationChip.kt` | 分类推荐 UI 模式，样式可参考 |
| Material3 | gradle `libs.versions.toml` | v1.2.1，支持 `TimePicker` / `TimePickerDialog` |

### 关键设计决策（已确认）

1. **时间选择器**：新增 Material3 TimePicker，推荐标签在旁边供快速填入
2. **推荐时间语义**：绝对时间 + advance=0（推荐器输出绝对时间，存入 `reminderTime`，AlarmScheduler 传 advance=0 精确触发）
3. **偏好学习**：全部一起实现（记录用户手动修改，更新推荐策略）

---

## 文件变更清单

| 操作 | 文件路径 | 说明 |
|------|----------|------|
| 新建 | `domain/ReminderRecommender.kt` | 提醒时间推荐引擎（纯 Kotlin 类） |
| 修改 | `viewmodel/TodoEditViewModel.kt` | 新增 3 个 StateFlow + 4 个方法 |
| 修改 | `ui/components/TodoCreateBottomSheet.kt` | 新增 TimePicker + 推荐标签 UI |
| 修改 | `ui/screens/todo/TodoEditScreen.kt` | 同上，编辑页面同步 |

---

## 详细实现

### Step 1: 新建 `domain/ReminderRecommender.kt`

**路径**: `app/src/main/java/com/corgimemo/app/domain/ReminderRecommender.kt`

**职责**: 纯逻辑推荐引擎，根据分类和截止时间计算推荐提醒时间

**输入**:
- `categoryId: Long` - 分类 ID
- `categoryType: Int?` - 分类类型（0=学习, 1=工作, 2=生活, 3=运动, 4=自定义）
- `dueDate: Long?` - 截止时间（毫秒时间戳）
- `corgiPreferences: CorgiPreferences` - 用于读取用户学习到的偏好

**输出**: `Long?` - 推荐提醒时间（绝对毫秒时间戳），null 表示不推荐

**内部结构**:

```kotlin
// 推荐规则密封类
sealed class ReminderRule {
    /** 相对于截止时间提前 N 分钟 */
    data class Advance(val minutes: Int) : ReminderRule()
    /** 截止时间当天指定时刻（如 9:00） */
    data class SameDayTime(val hour: Int, val minute: Int) : ReminderRule()
}
```

**默认规则表（硬编码）**:

| 分类 | 规则 | 示例（截止 2026-05-23 18:00） |
|------|------|------------------------------|
| WORK | Advance(15) | 推荐 2026-05-23 17:45 |
| STUDY | Advance(60) | 推荐 2026-05-23 17:00 |
| LIFE | SameDayTime(9, 0) | 推荐 2026-05-23 09:00 |
| SPORT | Advance(30) | 推荐 2026-05-23 17:30 |
| CUSTOM / 未分类 | Advance(30) | 推荐 2026-05-23 17:30 |

**推荐逻辑流程**:

```
1. 边界检查
   ├── dueDate == null → return null（无截止时间不推荐）
   └── dueDate < 今天 0:00 → return null（任务已过期）

2. 获取规则
   ├── 查 CorgiPreferences.getReminderAdvanceMinutes(categoryId)
   ├── 有偏好 → 用 Advance(learnedAdvance) 替代默认
   └── 无偏好 → 用默认规则表

3. 计算推荐时间
   ├── Advance(minutes) → dueDate - (minutes * 60 * 1000)
   └── SameDayTime(h, m) → 截取 dueDate 的年月日 + h:m

4. 边界修正
   └── 推荐时间 ≤ 当前时间 → return now + 5分钟

5. 返回推荐时间
```

**`isExpired(dueDate)` 实现**:
- 获取今天 0:00（`Calendar` 取年月日，时分秒毫秒设 0）
- `dueDate < todayStart` → 已过期

**`SameDayTime` 实现**:
- 用 `Calendar` 取 `dueDate` 的年月日
- 设置 `HOUR_OF_DAY = hour`, `MINUTE = minute`, `SECOND = 0`, `MILLISECOND = 0`
- 返回 `timeInMillis`

---

### Step 2: 修改 `viewmodel/TodoEditViewModel.kt`

**初始化**: 通过 Hilt 构造注入 `CorgiPreferences`（已有），新建 `ReminderRecommender` 实例

#### 新增 StateFlow

```kotlin
// 提醒时间（用户手动选择或接受推荐后的值）
private val _reminderTime = MutableStateFlow<Long?>(null)
val reminderTime: StateFlow<Long?> = _reminderTime.asStateFlow()

// 推荐提醒时间（由 ReminderRecommender 计算）
private val _recommendedReminderTime = MutableStateFlow<Long?>(null)
val recommendedReminderTime: StateFlow<Long?> = _recommendedReminderTime.asStateFlow()

// 是否显示推荐标签
private val _showReminderRecommendation = MutableStateFlow(false)
val showReminderRecommendation: StateFlow<Boolean> = _showReminderRecommendation.asStateFlow()
```

#### 新增方法

| 方法 | 签名 | 触发时机 | 行为 |
|------|------|---------|------|
| `setReminderTime` | `fun setReminderTime(time: Long)` | 用户在 TimePicker 中确认时间 | 更新 `_reminderTime`，记录偏好学习 |
| `setReminderTimeWithLearning` | `private suspend fun setReminderTimeWithLearning(time: Long)` | 被 `setReminderTime` 内部调用 | 计算 advance = dueDate - time，若有 dueDate 且属于合理范围，存入 DataStore |
| `acceptReminderRecommendation` | `fun acceptReminderRecommendation()` | 用户点击推荐标签 | 将 `_recommendedReminderTime` 赋给 `_reminderTime`，隐藏推荐标签 |
| `updateReminderRecommendation` | `private suspend fun updateReminderRecommendation()` | `setDueDate()` 和 `setCategoryId()` 调用后 | 调用 `recommender.recommend()`，更新 `_recommendedReminderTime` 和 `_showReminderRecommendation` |

#### 修改现有方法

- **`setDueDate(dueDate: Long?)`**: 末尾追加 `updateReminderRecommendation()`
- **`setCategoryId(categoryId: Long)`**: 末尾追加 `updateReminderRecommendation()`（因为分类变化可能影响推荐）
- **`loadTodo(todoId: Long)`**: 加载时恢复 `_reminderTime.value = todo.reminderTime`

#### 偏好学习实现细节

```
learnReminderPreference(chosenTime):
    dueDate = _dueDate.value
    if (dueDate == null) return  // 无截止时间不学习
    advance = ((dueDate - chosenTime) / 60000).toInt()
    if (advance in 0..43200) {  // 合理范围：0分钟 ~ 30天
        corgiPreferences.saveReminderAdvanceMinutes(categoryId, advance)
    }
    // 用户手动选了时间 → 隐藏推荐标签
    _showReminderRecommendation.value = false
```

#### 推荐标签显示逻辑

`_showReminderRecommendation` 在以下条件同时满足时为 `true`:
1. `_dueDate.value != null`（有截止时间）
2. `_recommendedReminderTime.value != null`（推荐结果非空）
3. `_reminderTime.value` 为 null 或与推荐不同（用户还没接受/修改）

---

### Step 3: 修改 `ui/components/TodoCreateBottomSheet.kt`

#### 新增状态收集

```kotlin
val reminderTime by viewModel.reminderTime.collectAsState()
val recommendedReminderTime by viewModel.recommendedReminderTime.collectAsState()
val showReminderRecommendation by viewModel.showReminderRecommendation.collectAsState()
```

#### 新增 UI 区域（位于截止日期按钮下方，LocationPicker 上方）

```
┌──────────────────────────────────────────┐
│  提醒时间                                  │  ← labelLarge
│  ┌──────────────────────────────────────┐│
│  │  点击选择提醒时间（可选）              ││  ← TimePicker 触发的 Button
│  │  或显示已选时间 "14:45"               ││  ← 选了之后显示具体时间
│  └──────────────────────────────────────┘│
│  ┌──────────────────────────────────────┐│
│  │  💡 推荐提醒：14:45                  ││  ← AnimatedVisibility
│  └──────────────────────────────────────┘│  ← 浅蓝底色，可点击
└──────────────────────────────────────────┘
```

#### TimePicker 实现

使用 Material3 `TimePickerDialog`:

```kotlin
var showTimePicker by remember { mutableStateOf(false) }
val timePickerState = rememberTimePickerState(
    initialHour = /* 从现有 reminderTime 解析 */
    initialMinute = /* 从现有 reminderTime 解析 */
    is24Hour = true
)

if (showTimePicker) {
    TimePickerDialog(
        onCancel = { showTimePicker = false },
        onConfirm = {
            val cal = Calendar.getInstance()
            cal.timeInMillis = dueDate ?: System.currentTimeMillis()
            cal.set(Calendar.HOUR_OF_DAY, timePickerState.hour)
            cal.set(Calendar.MINUTE, timePickerState.minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            viewModel.setReminderTime(cal.timeInMillis)
            showTimePicker = false
        }
    ) {
        TimePicker(state = timePickerState)
    }
}
```

**提醒时间 Button 显示文本逻辑**:
- `reminderTime != null` → 格式化显示 "yyyy-MM-dd HH:mm"
- `reminderTime == null` → "点击选择提醒时间（可选）"

#### 推荐标签实现

参考 [RecommendationChip.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/RecommendationChip.kt) 模式:

```kotlin
AnimatedVisibility(visible = showReminderRecommendation) {
    recommendedReminderTime?.let { time ->
        val format = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val timeText = format.format(Date(time))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .background(Color(0xFFE3F2FD), RoundedCornerShape(8.dp))
                .clickable { viewModel.acceptReminderRecommendation() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💡 推荐提醒：$timeText", fontSize = 14.sp, color = Color(0xFF1565C0))
        }
    }
}
```

**显示条件**: `AnimatedVisibility(visible = showReminderRecommendation)` — 由 ViewModel 控制

---

### Step 4: 修改 `ui/screens/todo/TodoEditScreen.kt`

与 Step 3 完全相同的 UI 变更，插入位置在截止日期按钮下方。代码复用 Step 3 中的模式。

**额外需要注意**:
- 编辑已有待办时（`loadTodo`），`reminderTime` 从已有 TodoItem 恢复
- `setReminderTime()` 触发后也应在 `saveTodo()` 时存储到 `TodoItem.reminderTime`

---

## 验证步骤

1. **编译验证**: `./gradlew assembleDebug` 无报错
2. **功能验证**:
   - 打开创建待办 → 选截止日期 → 选分类 → 推荐标签出现 → 点击标签 → 时间填入
   - 手动选时间 → TimePicker 弹出 → 选时间确认 → 标签消失
   - 无截止日期 → 不显示推荐标签
   - 截止日期为过去 → 不显示推荐
   - 生活类截止日 → 推荐当天 9:00
   - 编辑已有待办 → 提醒时间正确恢复
3. **偏好学习验证**:
   - 工作类手动改为提前 10 分钟 → 数据存入 DataStore
   - 新建工作类待办 → 推荐变为提前 10 分钟