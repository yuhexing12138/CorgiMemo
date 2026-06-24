# 待办卡片左滑交互设计

**日期**: 2026-06-24
**状态**: 设计中
**目标版本**: 当前迭代

## 1. 目标

在首页（HomeScreen）"全部"标签下的待办卡片列表中，实现从右向左的左滑手势，依次堆叠式展开"置顶 / 分享 / 删除"三个操作按钮，对标飞书"消息"列表的左滑交互体验。

## 2. 用户故事

作为用户，我希望：
- 在浏览待办列表时，通过左滑卡片快速调出"置顶 / 分享 / 删除"操作
- 操作按钮按"置顶 → 分享 → 删除"顺序依次出现，形成飞书式堆叠感
- 按钮区域与卡片等高、贴合卡片右侧边缘、4 个圆角与卡片协调
- 滑过阈值（20% 屏幕宽）才完全展开；右滑 / 点击卡片 / 点击其他卡片 / 操作完成后自动收回

## 3. 范围

### 3.1 In-Scope

- 自研 `SwipeableTodoCard` Composable 组件
- 集成到 `HomeScreen` 的"全部"标签卡片列表
- 左滑手势：单卡片左滑 / 右滑收回
- 3 个按钮堆叠出现动画（飞书风格 300ms 依次）
- 点击置顶按钮：仅日志输出（暂不实现后端）
- 点击分享按钮：调起 `shareTodoAsImage(context, todo, categories)`（与现有图片分享入口保持行为一致）
- 点击删除按钮：弹出二次确认 AlertDialog → 确认后调用现有 `homeViewModel.deleteTodo(todoId)`
- 收回方式：右滑 / 点击卡片 / 点击其他卡片 / 操作完成后自动

### 3.2 Out-of-Scope

- 置顶功能的后端实现（`TodoItem.pinned` 字段、列表排序、持久化）
- 主题色切换对按钮颜色的影响（保持规范中固定三色：橙/蓝/红）
- 已完成（status=1）卡片的左滑交互差异
- 多选模式下的批量操作
- 从子页面（如分类页、搜索页）的卡片左滑

## 4. 交互规范

### 4.1 触发与阈值

| 行为 | 触发条件 | 反馈 |
|------|---------|------|
| 左滑开始 | 手指水平向左拖动 | 卡片跟随手指位移，按钮 1（置顶）开始出现 |
| 完全展开 | 拖动距离 ≥ 20% 屏幕宽度 | 卡片位移至 `-216dp`，3 个按钮完全显示 |
| 回弹关闭 | 拖动距离 < 20% 屏幕宽度 | 卡片回弹至 0，按钮 1 收回 |
| 继续右滑 | 展开状态下右滑 | 卡片回弹至 0，按钮依次收回 |

### 4.2 收回交互（全 4 种都支持）

| 收回方式 | 触发条件 |
|---------|---------|
| 右滑收回 | 手指右滑超过 30dp |
| 点击卡片收回 | 点击卡片本体（非按钮区域） |
| 点击他卡 | 点击其他卡片（互斥展开模式） |
| 操作后自动 | 置顶 / 分享 / 删除 操作完成后 |

### 4.3 按钮操作

| 按钮 | 颜色 | 图标 | 文字 | 点击行为 |
|------|------|------|------|---------|
| 置顶 | `#FF9A5C` | `Icons.Filled.VerticalAlignTop` | "置顶" | 仅输出日志 `Log.w("TodoCardSwipe", "置顶：todoId=$id")` |
| 分享 | `#90CAF9` | `Icons.Filled.IosShare` | "分享" | 调用 `shareTodoAsImage(context, todo, categories)`（与现有图片分享行为保持一致） |
| 删除 | `#FF8A80` | `Icons.Filled.Delete` | "删除" | 弹 AlertDialog 二次确认 → 确认后 `homeViewModel.deleteTodo(todoId)` |

## 5. 视觉规范

### 5.1 几何参数

| 元素 | 尺寸 |
|------|------|
| 单按钮宽度 | 72dp |
| 3 按钮总宽度 | 216dp |
| 按钮高度 | = 卡片高度（绝对定位 `top:0; bottom:0`） |
| 按钮圆角 | 右外 2 个角 = 20dp；左外 2 个角 = 0（与卡片左半圆角衔接） |
| 卡片位移上限 | -216dp |
| 圆角收敛 | 卡片左半 20dp 圆角 + 按钮组右外 20dp 圆角 = 整体 4 个圆角 |

### 5.2 颜色

| 用途 | 色值 | 来源 |
|------|------|------|
| 置顶按钮背景 | `#FF9A5C` | UI 规范主题色（暖阳橙） |
| 分享按钮背景 | `#90CAF9` | UI 规范"低优先级"色（柔和蓝） |
| 删除按钮背景 | `#FF8A80` | UI 规范"高优先级"色（柔和红） |
| 按钮文字 | `#FFFFFF` | 白色（高对比） |
| 按钮图标 | `#FFFFFF` | 白色（高对比） |
| 按钮按下态 | 同色 -10% brightness | 视觉反馈 |

> 颜色全部取自 `docs/superpowers/specs/UI设计规范.md`，不引入新色值：
> - 置顶：主题色，区分"积极操作"
> - 分享：低优先级（柔和蓝），无侵入感
> - 删除：高优先级（柔和红），足够警示但不刺眼（符合"减少焦虑"原则）

### 5.3 动画时序

```
0ms         100ms       200ms       250ms    300ms
|------ 卡片左移 0→100% (250ms) ------|
                                      |-- btn1 渐入 50ms --|
                                            |-- btn2 渐入 50ms --|
                                                  |-- btn3 渐入 50ms --|
```

| 阶段 | 时长 | 缓动 | 元素 |
|------|------|------|------|
| 卡片位移 | 250ms | `ease-out` (FastOutSlowIn) | `Animatable<Float>` for offsetX |
| 按钮 1 渐入 | 50ms | `linear` | `Animatable<Float>` for btn1Alpha |
| 按钮 2 渐入 | 50ms | `linear` | `Animatable<Float>` for btn2Alpha |
| 按钮 3 渐入 | 50ms | `linear` | `Animatable<Float>` for btn3Alpha |

> 按钮的"出现"通过 `alpha(0f → 1f) + scaleX(0.6f → 1f)` 组合，让堆叠感更强。

## 6. 组件设计

### 6.1 组件签名

```kotlin
@Composable
fun SwipeableTodoCard(
    todo: TodoItem,
    isExpanded: Boolean,                      // 是否已展开
    onExpandChange: (Boolean) -> Unit,        // 展开/收起事件
    onPinClick: () -> Unit,                   // 置顶点击（暂不实现）
    onShareClick: () -> Unit,                 // 分享点击
    onDeleteClick: () -> Unit,                // 删除点击
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit           // 卡片原始内容
)
```

### 6.2 状态机

```
        ┌─────────┐
   ┌───►│ Closed  │◄──┐
   │    │ (offset 0)  │  │
   │    └─────┬───────┘  │
   │ 左滑     │ 拖动 ≥ 20%  │ 右滑 / 点击 / 操作后
   │          ▼           │
   │    ┌─────────┐       │
   └────┤ Expanded│───────┘
        │ (offset -216)│
        └─────────────┘
```

### 6.3 关键实现

```kotlin
// 1. 三个 Animatable 分别管理
val cardOffsetX = remember { Animatable(0f) }      // 卡片水平位移
val btn1Alpha = remember { Animatable(0f) }        // 置顶按钮
val btn2Alpha = remember { Animatable(0f) }        // 分享按钮
val btn3Alpha = remember { Animatable(0f) }        // 删除按钮

// 2. 拖动范围 -216f..0f
// 3. onDragStop：根据 finalOffsetX 决定 snap to -216f or 0f
// 4. LaunchedEffect(isExpanded)：展开时依次启动 3 个按钮动画
```

### 6.4 父组件集成（HomeScreen）—— 互斥展开

```kotlin
// 在 HomeScreen 中维护"当前展开卡片 ID"（single source of truth）
var swipeExpandedTodoId by remember { mutableStateOf<Long?>(null) }

LazyColumn {
    items(todos) { todo ->
        SwipeableTodoCard(
            todo = todo,
            isExpanded = swipeExpandedTodoId == todo.id,
            onExpandChange = { expanded ->
                swipeExpandedTodoId = if (expanded) todo.id else null  // 互斥
            },
            onPinClick = { /* 日志 */ },
            onShareClick = { /* 复用 shareTodoAsImage */ },
            onDeleteClick = { /* 二次确认弹窗 */ }
        ) {
            // 现有 TodoCard 内容
        }
    }
}
```

**数据流**：
1. 用户左滑卡片 A → `onDragEnd` 判断完全展开 → `onExpandChange(true)` → `swipeExpandedTodoId = A.id`
2. 用户左滑卡片 B → `onDragEnd` 判断完全展开 → `onExpandChange(true)` → `swipeExpandedTodoId = B.id`
3. A 的 `LaunchedEffect(expandedTodoId)` 监听到 `expandedTodoId != A.id`，自动 `cardOffsetX.animateTo(0f)` 收起
4. 用户点击 A 的"删除" → `onDelete(A.id)` + `onExpandChange(false)` → `swipeExpandedTodoId = null`

## 7. 集成影响

### 7.1 修改文件

| 文件 | 修改 |
|------|------|
| `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt` | **修改** 删除现有简单 swipe 逻辑（L119-202）；新增 3 按钮完整 swipe 逻辑（卡片内容、动作区、操作回调） |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | 接入 Pin/Share/Delete 回调（Share 复用现有 `shareTodoAsImage`，无需新增工具函数） |

**说明**：原计划新建独立的 `SwipeableTodoCard.kt`，调研后发现 `TodoListItem.kt` 内部已经存在简单 swipe 逻辑（L188-202，只支持删除）。将完整 3 按钮 swipe 逻辑直接放在 `TodoListItem.kt` 内部（作为私有函数或同 Composable 内），可以：
- 避免组件嵌套（避免外层包装 + 内层卡片的双层滑动冲突）
- 复用现有卡片视觉（`expandedTodos`、`isBatchMode`、`isSelected` 等状态）
- 与 `ReorderableLazyColumn` 集成无需新增 key

`TodoListItem` 签名基本保持不变，仅新增 `onPinClick`、`onShare` 2 个可选参数（默认空实现，向后兼容）。`onShare` 复用现有 `onShareAsImage` 的语义（统一为图片分享）。

### 7.2 复用现有分享工具

无需新增分享工具函数。Swipe 分享按钮直接调用 `shareTodoAsImage(context, todo, categories)`（HomeScreen 中已存在的图片分享实现），与卡片长按菜单中的"分享"入口行为完全一致。

### 7.3 不影响的部分

- `HomeViewModel.deleteTodo` 保持原状，沿用现有删除逻辑
- `TodoCard` 内部组件保持原状（SwipeableTodoCard 作为包装层）
- 已完成卡片、置顶后端等不在本次范围

## 8. 验收标准

| ID | 验收项 | 测试方法 |
|----|--------|---------|
| AC-1 | 左滑卡片 1cm → 卡片位移 = 1cm | 手动测试 |
| AC-2 | 拖动 ≥ 20% 屏宽后松手 → 完全展开 | 手动测试 |
| AC-3 | 拖动 < 20% 屏宽后松手 → 回弹关闭 | 手动测试 |
| AC-4 | 3 个按钮按"置顶→分享→删除"依次渐入，间隔 50ms | 录屏验证 |
| AC-5 | 按钮区域与卡片等高 | 手动对比 |
| AC-6 | 4 个圆角（左外 2 + 右外 2）拼合成完整圆角 | 视觉确认 |
| AC-7 | 点击置顶按钮 → 输出 Log，其他无反应 | logcat 验证 |
| AC-8 | 点击分享按钮 → 弹系统分享面板 | 手动测试 |
| AC-9 | 点击删除按钮 → 弹二次确认弹窗 | 手动测试 |
| AC-10 | 二次确认后 → 卡片从列表移除 | 手动测试 |
| AC-11 | 右滑 / 点击卡片 / 点他卡 / 操作后 → 自动收回 | 手动测试 |
| AC-12 | 同一时间只有一张卡片展开 | 手动测试 |

## 9. 风险与权衡

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 嵌套 LazyColumn 中手势冲突 | 卡片左滑与列表滚动冲突 | 严格判断水平/垂直方向，水平位移 > 垂直位移时锁定列表滚动 |
| 高度自适应与 LazyColumn 测量 | 按钮绝对定位需要卡片已测量 | 借助 `Box` 嵌套 + `matchParentSize` 让按钮区域 match 卡片尺寸 |
| 三个 Animatable 同步 | 状态不同步导致按钮闪现 | 使用 `LaunchedEffect(isExpanded)` 集中管理，按顺序 launch 协程 |
| 与现有 TodoCard 嵌套层数过多 | 性能开销 | SwipeableTodoCard 仅作手势层包装，内部仍使用原 TodoCard |

## 10. 不在本次范围（后续任务）

- 置顶功能后端实现（`pinned: Boolean` 字段 + 排序逻辑）
- 多选模式下批量置顶 / 批量删除
- 自定义长按拖动排序
- 长按弹出更多操作（移动到分组、编辑、复制）
- 卡片左滑/右滑出现不同操作（当前仅左滑展开操作区）
