# 待办卡片交互重构（多选模式直达 + MoreOptions 菜单）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构待办卡片长按交互——长按松手直达多选模式；多选页采用"选中X项"标题 + "全选+4图标"底栏；新增 ⋮ 按钮的 MoreOptions 6 项菜单（完成/置顶/优先级/提醒时间/创建副本/转换为灵感）。

**Architecture:** 在原 HomeScreen 顶部/底部操作栏内做"模式切换式"渲染（复用页面以保留滚动位置），不引入新路由。新增 `MoreOptionsSheet.kt`（复用 `ActionBottomSheet`）和 `PriorityPickerSheet.kt`（ModalBottomSheet + 4 选 1 单选）。复用现有 `CircularCheckbox`（已是橙色）。删除 `TodoActionSheet.kt` 与 `TodoListItem` 中的弹窗状态。

**Tech Stack:** Kotlin + Jetpack Compose + Material3 + Coroutines + StateFlow + Room（已有 `TodoRepository.updateTodo`/`insertTodo`/`getTodoById` 等方法直接复用）

**设计文档参考:** `docs/superpowers/specs/2026-06-26-todo-batch-interaction-redesign-design.md`

---

## 文件变更总览

| 文件 | 操作 | 职责 |
|---|---|---|
| `TodoListItem.kt` | 修改 | 删除长按弹窗 state/回调（`showLongPressMenu`、`actionSheetState`、`if (showLongPressMenu) TodoActionSheet(...)`）；简化 `onLongClick`；改用 `CircularCheckbox` 作批量模式 Checkbox（已是橙色） |
| `HomeScreen.kt` | 修改 | 自定义顶部栏 Row（◀ + "选中X项"）；重写底部操作栏（"全选" + 4 图标）；新增 MoreOptions/PriorityPicker 弹窗调用 |
| `HomeViewModel.kt` | 修改 | 新增 `batchPin()` / `batchUpdatePriority()` / `batchUpdateReminder()` / `batchDuplicate()` |
| 新增 `MoreOptionsSheet.kt` | 新建 | 6 项菜单底部弹窗（复用 ActionBottomSheet） |
| 新增 `PriorityPickerSheet.kt` | 新建 | 优先级 4 选 1 弹窗 |
| 删除 `TodoActionSheet.kt` | 删除 | 长按松手弹窗已废弃 |

---

## Task 1: 移除 TodoListItem 中长按弹窗逻辑

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`

- [ ] **Step 1: 移除不再使用的 state 变量**

定位 `TodoListItem.kt` 中以下行并删除：

```kotlin
var showLongPressMenu by remember { mutableStateOf(false) }

/** 长按已激活但手指还未松开的标记：松手时才弹出弹窗 */
var longPressActivated by remember { mutableStateOf(false) }
```

- [ ] **Step 2: 移除 `actionSheetState` 变量**

删除：
```kotlin
val actionSheetState = androidx.compose.material3.rememberModalBottomSheetState(
    skipPartiallyExpanded = true
)
```

- [ ] **Step 3: 简化 `pointerInput` 内的 `onLongPress` 与 `onPress`**

将原 `onLongPress` 改为：直接调用 `onLongClick()`（无论是否在批量模式，统一走回调，HomeViewModel 内 enterBatchMode 已支持 toggle 语义）。将 `onPress` 中 `if (longPressActivated) showLongPressMenu = true` 的逻辑删除，最终 `onPress` 块只保留：发射 PressInteraction → 等待释放 → 发射 Release/Cancel（**不弹任何弹窗**）。

最终 `detectTapGestures` 块应如下：

```kotlin
.pointerInput(isBatchMode, onClick, onLongClick, onSelectClick) {
    detectTapGestures(
        onTap = {
            if (isBatchMode) {
                onSelectClick()
            } else {
                onClick()
            }
        },
        onLongPress = {
            // 长按触发时执行震动反馈（脉冲式长震动）
            HapticFeedbackManager.performHapticFeedback(
                context = context,
                type = InteractionType.LONG_CLICK,
                enabled = hapticEnabled
            )
            // 普通模式：进入批量模式并选中该条
            // 批量模式：切换该条选中状态（toggleSelection 语义）
            onLongClick()
        },
        onPress = { pressOffset ->
            // 按下时发射 Press 事件，触发水波纹效果
            val pressInteraction = PressInteraction.Press(pressOffset)
            interactionSource.emit(pressInteraction)

            // 等待手指释放
            tryAwaitRelease()

            // 手指释放，结束水波纹动画
            interactionSource.emit(PressInteraction.Release(pressInteraction))
        }
    )
}
```

- [ ] **Step 4: 移除 `TodoActionSheet` 调用块**

删除文件末尾附近（`if (showLongPressMenu) { TodoActionSheet(...) }`）整段代码：

```kotlin
if (showLongPressMenu) {
    TodoActionSheet(
        sheetState = actionSheetState,
        onDismiss = {
            showLongPressMenu = false
            longPressActivated = false
        },
        onEdit = {
            onClick()
        },
        onShare = {
            onShareAsImage()
        },
        onBatchSelect = {
            onLongClick()
        },
        onDelete = {
            onDelete(todo.id)
        }
    )
}
```

- [ ] **Step 5: 批量模式 Checkbox 改用 `CircularCheckbox`（橙色）**

定位当前批量模式分支：
```kotlin
if (isBatchMode) {
    Checkbox(
        checked = isSelected,
        onCheckedChange = { onSelectClick() },
        modifier = Modifier.padding(end = 12.dp)
    )
}
```

替换为：
```kotlin
if (isBatchMode) {
    CircularCheckbox(
        checked = isSelected,
        onCheckedChange = { onSelectClick() },
        // 已完成待办在批量模式下也保持橙色系降权（dimmed）
        dimmed = todo.status == 1,
        modifier = Modifier.padding(end = 12.dp)
    )
}
```

- [ ] **Step 6: 验证 import**

确保仍引用 `androidx.compose.material3.Checkbox` 的位置无遗漏删除（如果不再使用，可移除 import）。`CircularCheckbox` 已在同包，无需新增 import。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "refactor(todo): 长按松手直达批量模式，删除长按弹窗逻辑"
```

---

## Task 2: HomeViewModel 新增批量操作方法

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt`

- [ ] **Step 1: 新增 `batchPin()` 方法**

定位 `batchMove` 方法之前，插入：

```kotlin
/**
 * 批量置顶选中的待办（统一置顶）
 *
 * 仅对未置顶的待办更新（避免无谓的 updatedAt 变化）。
 */
fun batchPin() {
    val selectedIds = _selectedTodoIds.value
    if (selectedIds.isEmpty()) return
    viewModelScope.launch {
        selectedIds.forEach { id ->
            todoRepository.getTodoById(id)?.let { todo ->
                if (!todo.isPinned) {
                    todoRepository.updateTodo(
                        todo.copy(
                            isPinned = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: 新增 `batchUpdatePriority()` 方法**

紧接 `batchPin()` 之后：

```kotlin
/**
 * 批量设置优先级
 *
 * @param priority 0=无 1=低 2=中 3=高
 */
fun batchUpdatePriority(priority: Int) {
    val selectedIds = _selectedTodoIds.value
    if (selectedIds.isEmpty()) return
    viewModelScope.launch {
        selectedIds.forEach { id ->
            todoRepository.getTodoById(id)?.let { todo ->
                if (todo.priority != priority) {
                    todoRepository.updateTodo(
                        todo.copy(
                            priority = priority,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: 新增 `batchUpdateReminder()` 方法**

紧接 `batchUpdatePriority()` 之后：

```kotlin
/**
 * 批量设置提醒时间
 *
 * @param reminderTime 提醒时间戳（null 表示清除提醒）
 * @param repeatType 重复类型（0=不重复，1=每天，2=每周，3=每月，4=周一至周五，5=每年）
 *
 * 注意：ReminderPickerBottomSheet 的 `calendarEnabled` 参数仅是 UI 内部状态
 * （用于切换农历显示），不持久化到 TodoItem 字段。本方法仅更新 reminderTime
 * 与 repeatType。Alarm 调度由 TodoRepository.updateTodo 内部统一处理。
 */
fun batchUpdateReminder(reminderTime: Long?, repeatType: Int) {
    val selectedIds = _selectedTodoIds.value
    if (selectedIds.isEmpty()) return
    viewModelScope.launch {
        selectedIds.forEach { id ->
            todoRepository.getTodoById(id)?.let { todo ->
                todoRepository.updateTodo(
                    todo.copy(
                        reminderTime = reminderTime,
                        repeatType = repeatType,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
```

- [ ] **Step 4: 新增 `batchDuplicate()` 方法**

紧接 `batchUpdateReminder()` 之后：

```kotlin
/**
 * 批量复制选中的待办
 *
 * 每条新待办使用 Room 自增 id（id=0），createdAt/updatedAt 重置为当前时间，
 * 状态重置为未完成（status=0），completedAt 清空。
 */
fun batchDuplicate() {
    val selectedIds = _selectedTodoIds.value
    if (selectedIds.isEmpty()) return
    val currentTime = System.currentTimeMillis()
    viewModelScope.launch {
        selectedIds.forEach { id ->
            todoRepository.getTodoById(id)?.let { todo ->
                todoRepository.insertTodo(
                    todo.copy(
                        id = 0,           // Room 自增
                        status = 0,        // 复制为未完成
                        completedAt = null,
                        createdAt = currentTime,
                        updatedAt = currentTime
                    )
                )
            }
        }
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt
git commit -m "feat(todo): 新增批量置顶/优先级/提醒/复制方法"
```

---

## Task 3: 创建 PriorityPickerSheet 组件

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/PriorityPickerSheet.kt`

- [ ] **Step 1: 创建文件并写入完整内容**

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 优先级选择底部弹窗（批量模式专用）
 *
 * 4 选 1 单选：用户必须选中其中一项后回调。
 * 选中后弹窗自动关闭（由调用方控制 onConfirm 后调 onDismiss）。
 *
 * 优先级对应：
 * - 0 = 无
 * - 1 = 低
 * - 2 = 中
 * - 3 = 高
 *
 * @param sheetState 弹窗状态
 * @param initialPriority 初始选中的优先级（默认 0）
 * @param onDismiss 关闭弹窗回调
 * @param onConfirm 确认选择回调（传入选中的优先级）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriorityPickerSheet(
    sheetState: SheetState,
    initialPriority: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (priority: Int) -> Unit
) {
    /**
     * 优先级选项（id, 名称）
     *
     * 注意：保持 0/1/2/3 数值语义，与 TodoItem.priority 字段一致。
     */
    val priorities = listOf(
        0 to "无",
        1 to "低",
        2 to "中",
        3 to "高"
    )

    /**
     * 当前选中项（受控）：
     * - 初始值取自 initialPriority
     * - 点击单项时更新
     */
    var selectedPriority by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableIntStateOf(initialPriority)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            /** 标题 */
            Text(
                text = "设置优先级",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )

            /** 4 选 1 列表 */
            priorities.forEach { (priority, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPriority = priority
                            onConfirm(priority)
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    /** 左侧：选项文本 */
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = if (priority == selectedPriority) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        color = if (priority == selectedPriority) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f)
                    )

                    /** 右侧：选中态显示 ✓ */
                    if (priority == selectedPriority) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "已选中",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/PriorityPickerSheet.kt
git commit -m "feat(ui): 新增 PriorityPickerSheet 优先级选择弹窗"
```

---

## Task 4: 创建 MoreOptionsSheet 组件

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/MoreOptionsSheet.kt`

- [ ] **Step 1: 创建文件并写入完整内容**

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LightbulbOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState

/**
 * 更多选项底部弹窗（More Options Sheet）
 *
 * 多选模式底部 ⋮ 按钮触发的 6 项菜单：
 * 1. 完成 — 触发批量完成并退出多选
 * 2. 置顶 — 批量置顶
 * 3. 优先级 — 弹 PriorityPickerSheet
 * 4. 提醒时间 — 弹 ReminderPickerBottomSheet
 * 5. 创建副本 — 批量复制
 * 6. 转换为灵感 — Toast "功能开发中"（暂未实现）
 *
 * 复用现有 ActionBottomSheet 组件实现，无需新建 ModalBottomSheet 容器。
 *
 * @param sheetState 弹窗状态
 * @param onDismiss 关闭弹窗回调
 * @param onComplete 完成回调（批量完成 + 退出多选）
 * @param onPin 置顶回调
 * @param onPriority 优先级回调（弹 PriorityPickerSheet）
 * @param onReminder 提醒时间回调（弹 ReminderPickerBottomSheet）
 * @param onDuplicate 创建副本回调
 * @param onConvertToInspiration 转换为灵感回调（暂为 Toast）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreOptionsSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onPin: () -> Unit,
    onPriority: () -> Unit,
    onReminder: () -> Unit,
    onDuplicate: () -> Unit,
    onConvertToInspiration: () -> Unit
) {
    /** 菜单项列表（按从上到下顺序排列） */
    val actions = listOf(
        ActionItem(
            icon = Icons.Default.Check,
            text = "完成",
            onClick = onComplete
        ),
        ActionItem(
            icon = Icons.Default.PushPin,
            text = "置顶",
            onClick = onPin
        ),
        ActionItem(
            icon = Icons.Default.Flag,
            text = "优先级",
            onClick = onPriority
        ),
        ActionItem(
            icon = Icons.Default.Alarm,
            text = "提醒时间",
            onClick = onReminder
        ),
        ActionItem(
            icon = Icons.Default.ContentCopy,
            text = "创建副本",
            onClick = onDuplicate
        ),
        ActionItem(
            icon = Icons.Default.LightbulbOutline,
            text = "转换为灵感",
            onClick = onConvertToInspiration
        )
    )

    /** 复用 ActionBottomSheet，不插入分割线 */
    ActionBottomSheet(
        sheetState = sheetState,
        title = null,
        actions = actions,
        dividerIndex = null,
        onDismiss = onDismiss
    )
}
```

- [ ] **Step 2: 验证图标存在性**

确认以下图标在 `androidx.compose.material.icons.filled` 中存在：
- `Check` ✅（已有引用）
- `PushPin` ✅
- `Flag` ✅
- `Alarm` ✅
- `ContentCopy` ✅
- `LightbulbOutline` ⚠️（如不可用，使用 `Icons.Default.Lightbulb`）

如果 `LightbulbOutline` 编译失败，将 import 改为 `androidx.compose.material.icons.filled.Lightbulb`。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/MoreOptionsSheet.kt
git commit -m "feat(ui): 新增 MoreOptionsSheet 多选页 6 项菜单弹窗"
```

---

## Task 5: 重构 HomeScreen 顶部栏（自定义 Row + 选中X项）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 替换批量模式 TopAppBar 为自定义 Row**

定位现有 `if (isBatchMode) { TopAppBar(...) }` 块（第 347 行附近）整体替换为：

```kotlin
if (isBatchMode) {
    /**
     * 批量模式自定义顶部栏
     *
     * 高度 56dp，背景与原 TopAppBar 一致。
     * - 左侧：◀ 返回箭头（点击 → exitBatchMode）
     * - 中部："选中 X 项" 文本（X 实时同步 selectedTodoIds.size）
     */
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** 返回箭头（24dp 圆形可点击区域） */
        IconButton(onClick = { viewModel.exitBatchMode() }) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "退出批量模式",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        /** 选中数量文本 */
        Text(
            text = "选中 ${selectedTodoIds.size} 项",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}
```

- [ ] **Step 2: 添加 `import androidx.compose.material.icons.filled.ArrowBack`**

在文件顶部 import 区追加：
```kotlin
import androidx.compose.material.icons.filled.ArrowBack
```

- [ ] **Step 3: 移除已废弃的 TopAppBar imports（可选）**

如不再使用 `TopAppBar` / `Close` 等 import，可清理（但谨慎，避免影响其他位置）。本次仅修改批量模式分支，可保留未使用 import。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "refactor(home): 批量模式顶部栏改为自定义 Row + 选中X项"
```

---

## Task 6: 重构 HomeScreen 底部操作栏（"全选" + 4 图标）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 添加底部图标状态变量**

定位 `var showBatchDeleteDialog by remember { mutableStateOf(false) }` 附近，添加：

```kotlin
/** MoreOptions 弹窗显示状态 */
var showMoreOptionsSheet by remember { mutableStateOf(false) }

/** PriorityPicker 弹窗显示状态 */
var showPriorityPickerSheet by remember { mutableStateOf(false) }

/** ReminderPicker 弹窗显示状态 */
var showReminderPickerSheet by remember { mutableStateOf(false) }
```

- [ ] **Step 2: 替换底部操作栏 Surface 内部 Row 内容**

定位第 681-747 行的批量操作栏 `AnimatedVisibility` 块，**仅修改内部 Row 内容**，整体容器（`AnimatedVisibility` + `Surface` + `safeAreaForBottomBar`）保持不变。

将内部 Row 替换为：

```kotlin
Row(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    val hasSelection = selectedTodoIds.isNotEmpty()

    /** 左下：全选 / 取消全选 按钮 */
    TextButton(
        onClick = {
            if (selectedTodoIds.size == filteredTodos.size && filteredTodos.isNotEmpty()) {
                viewModel.clearSelection()
            } else {
                viewModel.selectAll()
            }
        },
        enabled = filteredTodos.isNotEmpty()
    ) {
        Text(
            text = if (selectedTodoIds.size == filteredTodos.size && filteredTodos.isNotEmpty()) {
                "取消全选"
            } else {
                "全选"
            },
            color = UiColors.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }

    /** 右下：4 个图标按钮（分享/移动/删除/更多） */
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** 1. 分享 */
        IconButton(
            onClick = {
                selectedTodoIds.forEach { id ->
                    // 触发分享（复用现有 shareTodoAsImage 逻辑）
                    val todo = filteredTodos.find { it.id == id }
                    if (todo != null) {
                        // 与单条分享保持一致：通知 ViewModel 触发分享
                        viewModel.shareTodoAsImage(todo.id)
                    }
                }
            },
            enabled = hasSelection,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "分享",
                tint = if (hasSelection) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }

        /** 2. 移动到分组 */
        IconButton(
            onClick = { showBatchMoveDialog = true },
            enabled = hasSelection,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.DriveFileMove,
                contentDescription = "移动到分组",
                tint = if (hasSelection) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }

        /** 3. 删除待办 */
        IconButton(
            onClick = { showBatchDeleteDialog = true },
            enabled = hasSelection,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = "删除待办",
                tint = if (hasSelection) {
                    UiColors.Error
                } else {
                    UiColors.Error.copy(alpha = 0.38f)
                }
            )
        }

        /** 4. 更多选项（⋮） */
        IconButton(
            onClick = { showMoreOptionsSheet = true },
            enabled = hasSelection,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "更多选项",
                tint = if (hasSelection) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
            )
        }
    }
}
```

- [ ] **Step 3: 添加 imports**

在文件顶部 import 区追加：

```kotlin
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.Share
```

（`UiColors`、`FontWeight`、`sp`、`dp` 等如未导入请补充）

- [ ] **Step 4: 验证 `shareTodoAsImage` 方法**

在 HomeScreen 中搜索是否已有 `shareTodoAsImage` 方法的调用：

```bash
grep -n "shareTodoAsImage\|onShareAsImage" app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
```

**情况 A：HomeScreen 已有 onShareAsImage 回调传递**（预期情况）
原 HomeScreen 在循环渲染 TodoListItem 时已传入 `onShareAsImage` 回调，调用了某个 viewModel 方法。请找到该 viewModel 方法的签名（通常形如 `fun shareTodoAsImage(todoId: Long)`）。

将 Task 6 Step 2 中的分享按钮实现改为调用该方法：

```kotlin
onClick = {
    selectedTodoIds.forEach { id ->
        viewModel.shareTodoAsImage(id)
    }
}
```

**情况 B：viewModel 没有 `shareTodoAsImage` 方法**

如果 viewModel 没有分享方法，请通过 `TodoListItem` 的 onShareAsImage 回调方式，改为"在 HomeScreen 中只调用一个统一的分享入口"（无需新方法）。可暂时保留按钮为占位 SnackBar：

```kotlin
onClick = {
    coroutineScope.launch {
        snackbarHostState.showSnackbar("已选择 ${selectedTodoIds.size} 项待办进行分享")
    }
}
```

**判断方法**：完成 Task 1 后编译一次，根据编译错误决定走情况 A 还是 B。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "refactor(home): 批量模式底部操作栏改为全选+4图标布局"
```

---

## Task 7: 集成 MoreOptionsSheet / PriorityPickerSheet / ReminderPickerBottomSheet

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 在底部操作栏 Surface 之后、覆盖层之前插入弹窗调用**

定位第 749 行的 `// 覆盖层效果` 注释之前（即 `AnimatedVisibility` 之后），插入：

```kotlin
/** MoreOptions 弹窗（多选页 ⋮ 按钮触发） */
if (showMoreOptionsSheet) {
    val moreOptionsState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    MoreOptionsSheet(
        sheetState = moreOptionsState,
        onDismiss = { showMoreOptionsSheet = false },
        onComplete = {
            showMoreOptionsSheet = false
            val count = selectedTodoIds.size
            viewModel.batchComplete()
            coroutineScope.launch {
                snackbarHostState.showSnackbar("已完成 $count 个待办")
            }
        },
        onPin = {
            showMoreOptionsSheet = false
            val count = selectedTodoIds.size
            viewModel.batchPin()
            coroutineScope.launch {
                snackbarHostState.showSnackbar("已置顶 $count 个待办")
            }
        },
        onPriority = {
            showMoreOptionsSheet = false
            showPriorityPickerSheet = true
        },
        onReminder = {
            showMoreOptionsSheet = false
            showReminderPickerSheet = true
        },
        onDuplicate = {
            showMoreOptionsSheet = false
            val count = selectedTodoIds.size
            viewModel.batchDuplicate()
            coroutineScope.launch {
                snackbarHostState.showSnackbar("已创建 $count 个副本")
            }
        },
        onConvertToInspiration = {
            showMoreOptionsSheet = false
            coroutineScope.launch {
                snackbarHostState.showSnackbar("转换为灵感功能开发中")
            }
        }
    )
}

/** PriorityPicker 弹窗（MoreOptions → 优先级） */
if (showPriorityPickerSheet) {
    val priorityState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    PriorityPickerSheet(
        sheetState = priorityState,
        /** 初始优先级：取首个选中待办的 priority */
        initialPriority = filteredTodos
            .firstOrNull { selectedTodoIds.contains(it.id) }
            ?.priority ?: 0,
        onDismiss = { showPriorityPickerSheet = false },
        onConfirm = { priority ->
            showPriorityPickerSheet = false
            viewModel.batchUpdatePriority(priority)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("已设置优先级")
            }
        }
    )
}

/** ReminderPicker 弹窗（MoreOptions → 提醒时间） */
if (showReminderPickerSheet) {
    val reminderState = androidx.compose.material3.rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    val firstSelected = filteredTodos
        .firstOrNull { selectedTodoIds.contains(it.id) }
    ReminderPickerBottomSheet(
        initialDateMillis = firstSelected?.reminderTime,
        initialRepeatType = firstSelected?.repeatType ?: 0,
        onDismiss = { showReminderPickerSheet = false },
        onConfirm = { dateMillis, hour, minute, repeatType, calendarEnabled ->
            showReminderPickerSheet = false
            /**
             * 将 dateMillis + hour + minute 组合为时间戳
             * 注意：calendarEnabled 是 UI 内部状态，不持久化（详见 batchUpdateReminder 注释）
             */
            val reminderTime = if (dateMillis != null) {
                val cal = java.util.Calendar.getInstance().apply {
                    timeInMillis = dateMillis
                    set(java.util.Calendar.HOUR_OF_DAY, hour)
                    set(java.util.Calendar.MINUTE, minute)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                cal.timeInMillis
            } else {
                null
            }
            viewModel.batchUpdateReminder(reminderTime, repeatType)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("已设置提醒时间")
            }
        }
    )
}
```

- [ ] **Step 2: 验证 ReminderPickerBottomSheet 签名**

ReminderPickerBottomSheet 签名为：
```kotlin
fun ReminderPickerBottomSheet(
    initialDateMillis: Long? = null,
    initialHour: Int = 13,
    initialMinute: Int = 35,
    initialRepeatType: Int = 0,
    initialCalendarEnabled: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (dateMillis: Long?, hour: Int, minute: Int, repeatType: Int, calendarEnabled: Boolean) -> Unit
)
```

如 `initialHour` / `initialMinute` 必需传入，根据 `firstSelected?.reminderTime` 计算：

```kotlin
val cal = firstSelected?.reminderTime?.let { ts ->
    java.util.Calendar.getInstance().apply { timeInMillis = ts }
}
val initialHour = cal?.get(java.util.Calendar.HOUR_OF_DAY) ?: 13
val initialMinute = cal?.get(java.util.Calendar.MINUTE) ?: 35
```

并补充 `initialHour = initialHour, initialMinute = initialMinute` 两个参数。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(home): 集成 MoreOptionsSheet / PriorityPicker / ReminderPicker"
```

---

## Task 8: 删除 TodoActionSheet.kt

**Files:**
- Delete: `app/src/main/java/com/corgimemo/app/ui/components/TodoActionSheet.kt`

- [ ] **Step 1: 验证无其他引用**

```bash
grep -r "TodoActionSheet" app/src --include="*.kt"
```

期望：仅 `TodoActionSheet.kt` 自身出现，无其他文件引用（Task 1 已移除 TodoListItem 中的引用）。

- [ ] **Step 2: 删除文件**

```bash
git rm app/src/main/java/com/corgimemo/app/ui/components/TodoActionSheet.kt
```

- [ ] **Step 3: 提交**

```bash
git commit -m "refactor(todo): 删除废弃的 TodoActionSheet"
```

---

## Task 9: 验证整体编译

**Files:**
- Read only: 验证 `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`、`HomeScreen.kt`、`HomeViewModel.kt`、新组件

- [ ] **Step 1: 检查 import 完整性**

对以下文件确认无 import 缺失：
- `TodoListItem.kt`：移除 `Checkbox` import（如果已无引用）、移除 `rememberModalBottomSheetState`（如不再使用）
- `HomeScreen.kt`：添加 `ArrowBack`、`MoreVert`、`Outlined.Delete`、`Outlined.DriveFileMove`、`Outlined.Share` 等

- [ ] **Step 2: 检查方法签名一致性**

确认以下类型/方法在所有文件中一致：
- `HomeViewModel.batchPin()` 签名：`() -> Unit` ✅
- `HomeViewModel.batchUpdatePriority(priority: Int)` ✅
- `HomeViewModel.batchUpdateReminder(reminderTime: Long?, repeatType: Int)` ✅
- `HomeViewModel.batchDuplicate()` ✅
- `HomeViewModel.shareTodoAsImage(todoId: Long)` （如不存在，参见 Task 6 Step 4 备选方案）

- [ ] **Step 3: 询问用户是否执行编译**

**根据工作区规则"编译验证"，执行编译前必须询问用户**。通过 AskUserQuestion 询问：

> 是否需要执行 gradle 编译验证？

如用户同意，按项目 `.trae/rules/编译验证.md` 流程执行。如用户跳过，进入 Task 10 文档归档。

---

## Task 10: 归档与总结

**Files:**
- (no file changes)

- [ ] **Step 1: 汇总所有 commit**

```bash
git log --oneline -10
```

确认本次共产生以下 commit（按顺序）：
1. refactor(todo): 长按松手直达批量模式，删除长按弹窗逻辑
2. feat(todo): 新增批量置顶/优先级/提醒/复制方法
3. feat(ui): 新增 PriorityPickerSheet 优先级选择弹窗
4. feat(ui): 新增 MoreOptionsSheet 多选页 6 项菜单弹窗
5. refactor(home): 批量模式顶部栏改为自定义 Row + 选中X项
6. refactor(home): 批量模式底部操作栏改为全选+4图标布局
7. feat(home): 集成 MoreOptionsSheet / PriorityPicker / ReminderPicker
8. refactor(todo): 删除废弃的 TodoActionSheet

- [ ] **Step 2: 询问用户是否合并到主分支并推送**

按工作区规则"git 提交"流程，询问用户：
- 是否需要将当前分支的 commits 合并回主分支
- 是否需要推送到远程

---

## 测试要点（实施后由执行者验证）

| # | 测试场景 | 期望 |
|---|---|---|
| 1 | 普通模式长按待办 → 松手 | 立即进入批量模式 + 该条已选中 |
| 2 | 批量模式长按已选中待办 → 松手 | 切换为未选中（onLongClick 触发 toggleSelection） |
| 3 | 批量模式长按未选中待办 → 松手 | 切换为已选中 |
| 4 | 批量模式点击 Checkbox（橙色） | 切换为已选中/未选中 |
| 5 | 顶部"选中X项" | 数字 = selectedTodoIds.size |
| 6 | 底左"全选"按钮 | 选中所有可见项；再次点击显示"取消全选" |
| 7 | 底右 4 图标 | 全部启用（hasSelection=true） |
| 8 | 点击分享图标 | 触发每个选中项的 shareTodoAsImage |
| 9 | 点击移动图标 | 弹出 showBatchMoveDialog |
| 10 | 点击删除图标 | 弹出 showBatchDeleteDialog |
| 11 | 点击 ⋮ 图标 | 弹出 MoreOptionsSheet |
| 12 | MoreOptions → 完成 | 批量完成 + 退出批量模式 + Snackbar |
| 13 | MoreOptions → 置顶 | 批量置顶 + Snackbar |
| 14 | MoreOptions → 优先级 | 弹 PriorityPickerSheet + 选择后批量更新 |
| 15 | MoreOptions → 提醒时间 | 弹 ReminderPicker + 选择后批量更新 |
| 16 | MoreOptions → 创建副本 | 批量复制 + Snackbar |
| 17 | MoreOptions → 转换为灵感 | Toast "功能开发中" |
| 18 | 返回箭头 ◀ | exitBatchMode() + 清空选择 |
| 19 | 已完成待办在批量模式 | Checkbox 浅橙色（dimmed 态） |
| 20 | TodoActionSheet.kt | 文件已删除，无编译错误 |

---

## 后续优化建议（实施完成后向用户提供）

- 批量操作进度提示（>10 项时显示 Snackbar + 进度环）
- MoreOptions 菜单项的"折叠最近使用"
- 优先级选择支持多级筛选
- 多选模式下"筛选当前选中"的快捷操作
- 批量复制时同时复制 ContentBlock（图片/语音）

---

## 风险与缓解（执行时关注）

| 风险 | 缓解 |
|---|---|
| 删除 `TodoActionSheet.kt` 后仍存在隐藏引用 | Task 8 Step 1 grep 验证 |
| `shareTodoAsImage` 方法在 ViewModel 不存在 | Task 6 Step 4 备选方案 |
| `LightbulbOutline` 图标不存在 | Task 4 Step 2 降级为 `Lightbulb` |
| `HomeScreen` 中 `Outlined.*` 图标未导入 | Task 6 Step 3 明确 import |
| ReminderPicker `initialHour`/`initialMinute` 缺失 | Task 7 Step 2 显式传入 |
| import 缺失导致编译错误 | 任务完成后检查所有 import（工作区规则"import 语句检查"） |
