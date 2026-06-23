# 多待办卡片功能设计文档

**日期**: 2026-06-23
**功能名称**: 待办编辑页多卡片独立保存
**状态**: 待评审

---

## 1. 功能概述

### 1.1 背景

当前待办编辑页支持输入 "/" 创建多个分组容器（TodoGroupContainer），但所有内容保存为**单个 TodoItem** 记录。用户希望每个容器能够**独立保存为待办列表中的独立卡片**。

### 1.2 目标

| # | 目标 | 描述 |
|---|------|------|
| G1 | **多卡片编辑** | 编辑页内通过 "/" 创建多个待办容器 |
| G2 | **单独保存** | 每个容器的"完成"按钮保存该容器为独立 TodoItem |
| G3 | **全部保存** | 右上角按钮改为"全部完成"，一次性保存所有容器 |
| G4 | **状态追踪** | 保存后容器标记为"已保存"，编辑后恢复为"未保存" |
| G5 | **独立展示** | HomeScreen 每个保存的待办显示为独立卡片 |

---

## 2. 数据模型设计

### 2.1 新增数据类

```kotlin
/**
 * 分组保存状态
 *
 * 跟踪编辑页内每个 groupId 的保存状态，
 * 用于控制 UI 显示和决定是 insert 还是 update。
 */
data class GroupSaveState(
    /** 分组 ID（对应 TodoLine.groupId） */
    val groupId: Int,

    /** 是否已保存到数据库 */
    val isSaved: Boolean,

    /** 已保存的 TodoItem 数据库 ID（isSaved=true 时有效） */
    val savedTodoId: Long? = null,

    /** 最后保存时间戳 */
    val savedAt: Long? = null
)
```

### 2.2 ViewModel 状态扩展

```kotlin
// TodoEditViewModel.kt 新增状态

/** 各分组的保存状态（key=groupId, value=保存状态） */
private val _groupSaveStates = MutableStateFlow<Map<Int, GroupSaveState>>(emptyMap())
val groupSaveStates: StateFlow<Map<Int, GroupSaveState>> = _groupSaveStates.asStateFlow()
```

### 2.3 数据流

```
┌─────────────────────────────────────────────────────────────┐
│                    TodoEditSession                          │
│                                                             │
│  todoLines (List<TodoLine>)                                 │
│  ├── [0] TodoLine(text="待办1", groupId=0, ...)             │
│  ├── [1] TodoLine(text="子任务A", groupId=0, isSubTask=true)│
│  ├── [2] TodoLine(text="待办2", groupId=1, ...)    ← "/"创建 │
│  └── [3] TodoLine(text="子任务B", groupId=1, isSubTask=true)│
│                                                             │
│  groupSaveStates (Map<Int, GroupSaveState>)                 │
│  ├── 0 → GroupSaveState(isSaved=false)                      │
│  └── 1 → GroupSaveState(isSaved=false)                      │
└─────────────────────────────────────────────────────────────┘
         │
         │ 用户点击 groupId=1 的"完成"按钮
         ↓
┌─────────────────────────────────────────────────────────────┐
│  saveGroup(groupId=1) 执行：                                │
│                                                             │
│  1. 提取 groupId=1 的行：[待办2, 子任务B]                   │
│  2. 构建 TodoItem(title="待办2", content="☐ 待办2\n...")   │
│  3. todoRepository.insertTodo(todo) → 返回 id=123           │
│  4. 更新状态：_groupSaveStates[1] = GroupSaveState(         │
│       isSaved=true, savedTodoId=123, savedAt=now)           │
└─────────────────────────────────────────────────────────────┘
         │
         │ UI 响应：groupId=1 容器显示"已保存 ✓"
         ↓
┌─────────────────────────────────────────────────────────────┐
│  用户编辑 groupId=1 的文本                                  │
│                                                             │
│  LaunchedEffect(todoLines) 检测到内容变化                   │
│  → 重置状态：_groupSaveStates[1].copy(isSaved=false)        │
│  → UI 响应：按钮恢复为"完成"                                │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 接口设计

### 3.1 ViewModel 新增方法

```kotlin
/**
 * 保存单个分组为独立 TodoItem
 *
 * @param groupId 要保存的分组 ID
 * @return 保存成功返回 true，失败返回 false
 */
suspend fun saveGroup(groupId: Int, lines: List<TodoLine>): Boolean

/**
 * 保存所有未保存的分组
 *
 * @return 成功保存的数量
 */
suspend fun saveAllGroups(lines: List<TodoLine>): Int

/**
 * 检测某分组是否有未保存的修改
 *
 * @param groupId 分组 ID
 * @return true 表示有未保存修改
 */
fun hasUnsavedChanges(groupId: Int): Boolean

/**
 * 获取某分组的保存状态
 *
 * @param groupId 分组 ID
 * @return GroupSaveState 或 null（如果分组不存在）
 */
fun getGroupSaveState(groupId: Int): GroupSaveState?
```

### 3.2 UI 回调调整

```kotlin
// CheckboxEditText.kt - onSaveClick 签名不变
onSaveClick: ((Int) -> Unit)? = null  // 参数仍是 groupId

// TodoEditScreen.kt - 回调实现变更
onSaveClick = { groupId ->
    // 保存单个分组
    viewModel.saveGroup(groupId, todoLines)
}

// 右上角按钮
onClick = {
    // 保存所有分组 + 返回列表
    val savedCount = viewModel.saveAllGroups(todoLines)
    if (savedCount > 0 || /* 所有组都已保存 */) {
        navController.popBackStack()
    }
}
```

---

## 4. UI 设计

### 4.1 容器状态视觉

| 状态 | 按钮文字 | 按钮颜色 | 容器边框 | 其他 |
|------|---------|---------|---------|------|
| **未保存** | "完成" | 主题色（橙色） | 无 | 正常显示 |
| **已保存** | "已保存 ✓" | 绿色/灰色 | 淡绿色边框 | 左侧小图标 |

### 4.2 右上角按钮

```
【修改前】          【修改后】
┌─────────┐       ┌───────────┐
│  ✕ 完成  │       │  ✕ 全部完成 │
└─────────┘       └───────────┘
  保存单个          保存所有 + 返回
```

### 4.3 交互流程图

```
用户打开新建待办页
    ↓
显示空容器（groupId=0）
    ↓
输入 "待办1"，回车添加子任务
    ↓
输入 "/" → 创建新容器（groupId=1）
    ↓
输入 "待办2"
    ↓
┌─────────────────────────────────────┐
│  ┌─────────────────────────────┐   │
│  │ ☐ 待办1                     │   │
│  │   ☐ 子任务A           [完成]│   │  ← 未保存状态
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ ☐ 待办2                     │   │
│  │   ☐ 子任务B           [完成]│   │  ← 未保存状态
│  └─────────────────────────────┘   │
│                                     │
│              [✕ 全部完成]            │
└─────────────────────────────────────┘
    ↓ 点击第二个容器的"完成"
┌─────────────────────────────────────┐
│  ┌─────────────────────────────┐   │
│  │ ☐ 待办1                     │   │
│  │   ☐ 子任务A           [完成]│   │  ← 未保存
│  └─────────────────────────────┘   │
│                                     │
│  ┌─────────────────────────────┐   │
│  │ ☐ 待办2                     │   │
│  │   ☐ 子任务B      [已保存 ✓] │   │  ← 已保存！绿色
│  └─────────────────────────────┘   │
│                                     │
│              [✕ 全部完成]            │
└─────────────────────────────────────┘
    ↓ 编辑"待办2"的文本
┌─────────────────────────────────────┐
│  ...                                │
│  ┌─────────────────────────────┐   │
│  │ ☐ 待办2（已修改）            │   │
│  │   ☐ 子任务B           [完成]│   │  ← 恢复为未保存！
│  └─────────────────────────────┘   │
│                                     │
│              [✕ 全部完成]            │
└─────────────────────────────────────┘
```

---

## 5. 实现计划

### Phase 1: 数据层改造（ViewModel）

**文件**: `TodoEditViewModel.kt`

- [ ] 新增 `GroupSaveState` 数据类
- [ ] 新增 `_groupSaveStates` StateFlow
- [ ] 实现 `saveGroup()` 方法
- [ ] 实现 `saveAllGroups()` 方法
- [ ] 实现 `hasUnsavedChanges()` 方法
- [ ] 实现 `getGroupSaveState()` 方法
- [ ] 监听 todoLines 变化，自动重置已保存组的编辑状态

### Phase 2: UI 层改造（TodoEditScreen）

**文件**: `TodoEditScreen.kt`

- [ ] 收集 `groupSaveStates` 状态
- [ ] 修改 `onSaveClick` 回调实现
- [ ] 修改右上角按钮：
  - 文字改为"全部完成"
  - 点击逻辑改为 `saveAllGroups() + popBackStack()`
- [ ] 传递保存状态给 CheckboxEditText

### Phase 3: 组件层改造（CheckboxEditText）

**文件**: `CheckboxEditText.kt` + `TodoGroupContainer`

- [ ] `TodoGroupContainer` 新增参数 `isSaved: Boolean`
- [ ] 根据状态切换按钮文字和颜色
- [ ] 已保存状态显示视觉反馈（边框/图标）

### Phase 4: 测试与优化

- [ ] 测试单容器保存流程
- [ ] 测试多容器分别保存
- [ ] 测试全部完成按钮
- [ ] 测试编辑已保存容器后的状态重置
- [ ] 测试附件在多容器场景下的正确性
- [ ] 性能优化：防抖、批量操作等

---

## 6. 向后兼容性

### 6.1 旧数据处理

- 已有的单个 TodoItem 数据不受影响
- 加载旧数据时，所有行属于 groupId=0
- 行为与之前一致：保存为单个 TodoItem

### 6.2 编辑模式兼容

- 从列表点击进入编辑：加载单个 TodoItem（保持现有逻辑）
- 新建模式：支持多容器创建和保存

### 6.3 HomeScreen 兼容

- 无需修改 HomeScreen
- 新保存的 TodoItem 自动出现在列表中
- 支持搜索、筛选、排序等现有功能

---

## 7. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 保存冲突 | 同时点击多个完成按钮 | 使用 Mutex 或串行化处理 |
| 数据丢失 | 应用崩溃时未保存内容 | 保持现有 auto-save 或提示机制 |
| 性能问题 | 大量容器时状态更新频繁 | 使用 debounce 或 snapshotFlow |
| UX 困惑 | 用户不理解多卡片概念 | 引导提示或 Tooltip |

---

## 8. 后续优化方向（不在本次实现范围）

- [ ] 拖拽排序：跨容器拖拽行
- [ ] 容器拆分：将已有行的某个位置拆分为新容器
- [ ] 容器合并：将两个容器合并为一个
- [ ] 批量操作：选择多个容器批量删除/归档
- [ ] 模板功能：从模板快速创建多容器结构
