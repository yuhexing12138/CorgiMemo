---
alwaysApply: false
---
# Reorderable 库使用规范

> **核心警示（P5+P6 经验教训）**：`Modifier.draggableHandle` 的 `dragGestureDetector` 参数**默认是 `Press`**（按下立即触发拖拽），**不是 `LongPress`**。P5 阶段我曾误以为默认是 LongPress 并写了错误注释，导致"滑动查看图片/语音误触拖拽"问题。**本规范第一要务就是防止此问题再现**。

## 1. 库概览

| 项 | 值 |
| --- | --- |
| 库 | `sh.calvin.reorderable:reorderable`（项目内子模块 `./Reorderable/`） |
| 核心能力 | LazyColumn / LazyRow / LazyGrid / StaggeredGrid 拖拽排序 |
| 与项目集成 | 首页 todo 卡片拖拽、侧滑栏分组拖拽、待办编辑页图片/语音拖拽 |
| 关键依赖 | Compose Foundation `LazyListState`、协程作用域 |

> 库源码位于 `./Reorderable/reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/`

## 2. DragGestureDetector 两种模式

> ⚠️ **本节是本规范最重要的一节。** 选错模式 = 手势冲突 = 用户体验灾难。

### 2.1 接口定义（[DragGestureDetector.kt](file:///C:/Users/EDY/Desktop/CorgiMemo/Reorderable/reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/DragGestureDetector.kt)）

```kotlin
fun interface DragGestureDetector {
    suspend fun PointerInputScope.detect(
        onDragStart: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
    )

    /** 手指按下立即检测拖拽（默认） */
    object Press : DragGestureDetector { ... detectDragGestures(...) ... }

    /** 需长按后才检测拖拽 */
    object LongPress : DragGestureDetector { ... detectDragGesturesAfterLongPress(...) ... }
}
```

### 2.2 模式对比

| 维度 | `Press`（默认） | `LongPress` |
| --- | --- | --- |
| 底层 API | `detectDragGestures(...)` | `detectDragGesturesAfterLongPress(...)` |
| 触发时机 | 手指**按下立即**进入拖拽监听 | 需**长按后**才进入拖拽监听 |
| 触发时长阈值 | 0 ms | ~500 ms（系统默认长按阈值） |
| 与 LazyRow 水平滚动 | ⚠️ **冲突**：滑动被识别为拖拽 | ✅ **不冲突**：滚动优先 |
| 与 LazyColumn 垂直滚动 | ⚠️ 冲突：与垂直滚动互斥 | ✅ 不冲突 |
| 与单击 | ⚠️ 短按 0~50ms 内可能误触 | ✅ 短按不触发拖拽 |
| 适用场景 | 项数量固定、用户主动长按 + 拖拽（如主屏图标） | 容器支持滚动 + 项可拖拽（**本项目首选**） |

### 2.3 选用决策树

```
你的容器（LazyRow/LazyColumn）是否需要用户滚动查看内容？
├─ 是 → 必须用 LongPress
│        （否则滚动会误触拖拽，本项目 P5+P6 已踩坑）
│
└─ 否（如整个列表只展示一屏内容）
   └─ 仍建议用 LongPress
        （与系统行为一致、可访问性更好、单击不会误触）
```

### 2.4 完整用法对比

**❌ 错误（默认 Press，误触拖拽）**：

```kotlin
modifier = Modifier.draggableHandle(
    onDragStarted = { /* 触觉反馈 */ },
    onDragStopped = {}
    // dragGestureDetector 省略，使用默认 Press
    // → 用户滑动 LazyRow 时会误触拖拽！
)
```

**✅ 正确 1：显式传 `LongPress`**：

```kotlin
modifier = Modifier.draggableHandle(
    onDragStarted = { /* 触觉反馈 */ },
    onDragStopped = {},
    dragGestureDetector = DragGestureDetector.LongPress
)
```

**✅ 正确 2：用便捷方法 `longPressDraggableHandle`**（与上面完全等价）：

```kotlin
modifier = Modifier.longPressDraggableHandle(
    onDragStarted = { /* 触觉反馈 */ },
    onDragStopped = {}
)
```

> 📌 `longPressDraggableHandle` 内部就是 `draggableHandle(..., dragGestureDetector = DragGestureDetector.LongPress)`，完全等价（[ReorderableLazyCollection.kt:783-795](file:///C:/Users/EDY/Desktop/CorgiMemo/Reorderable/reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/ReorderableLazyCollection.kt#L783-L795)）。

## 3. ReorderableCollectionItemScope 接口成员

> 该接口由 `ReorderableItem` 的 content lambda 隐式提供 receiver（`this: ReorderableCollectionItemScope`），**所有可拖拽 modifier 只能在此作用域内调用**。

### 3.1 接口定义（[ReorderableLazyCollection.kt:681-717](file:///C:/Users/EDY/Desktop/CorgiMemo/Reorderable/reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/ReorderableLazyCollection.kt#L681-L717)）

```kotlin
interface ReorderableCollectionItemScope {
    /**
     * 关键 modifier：将当前 UI 元素标记为可拖拽手柄
     * 只能用在 ReorderableItem 的 content lambda 内
     */
    fun Modifier.draggableHandle(
        enabled: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
        onDragStarted: (startedPosition: Offset) -> Unit = {},
        onDragStopped: () -> Unit = {},
        dragGestureDetector: DragGestureDetector = DragGestureDetector.Press  // ⚠️ 默认 Press
    ): Modifier

    /**
     * 便捷方法：longPressDraggableHandle = draggableHandle(..., LongPress)
     */
    fun Modifier.longPressDraggableHandle(
        enabled: Boolean = true,
        interactionSource: MutableInteractionSource? = null,
        onDragStarted: (startedPosition: Offset) -> Unit = {},
        onDragStopped: () -> Unit = {},
    ): Modifier
}
```

### 3.2 各参数语义

| 参数 | 类型 | 默认 | 作用 |
| --- | --- | --- | --- |
| `enabled` | `Boolean` | `true` | 当前手柄是否启用拖拽。`false` 时此 UI 元素不响应拖拽手势，但 ReorderableItem 仍可被其他项"越过"重排 |
| `interactionSource` | `MutableInteractionSource?` | `null` | 用于发出 `DragInteraction.Start/Stop` 事件。接 ripple 时可让 Material 3 ripple 跟随拖拽状态 |
| `onDragStarted` | `(Offset) -> Unit` | `{}` | 拖拽开始的回调。**本项目用此触发触觉反馈**（`HapticFeedbackManager`）和暂停语音播放 |
| `onDragStopped` | `() -> Unit` | `{}` | 拖拽结束的回调（包括成功重排和取消）。可用于恢复被 onDragStarted 改变的状态 |
| `dragGestureDetector` | `DragGestureDetector` | `Press` | **核心参数**。见 §2 决策树 |

### 3.3 常见误解澄清

| 误解 | 真相 |
| --- | --- |
| "ReorderableItem 自身会处理拖拽" | ❌ ReorderableItem 只是**容器**。必须给内部某个 UI 元素加 `draggableHandle` 才会响应拖拽 |
| "整张 ReorderableItem 都可拖" | ❌ 必须是带 `draggableHandle` 的那部分。ReorderableItem 其他区域不响应拖拽 |
| "`enabled = false` 会让项不参与重排" | ❌ `enabled` 控制当前手柄是否启用拖拽。项是否可被越过重排由 `ReorderableItem` 的 `enabled` 参数控制 |
| "默认是 LongPress" | ❌ 默认是 `Press`。P5 阶段我曾误以为默认是 LongPress，写了错误注释 |

## 4. draggableHandle vs longPressDraggableHandle 选用

### 4.1 完全等价

两者**逻辑完全等价**，后者仅是前者的便捷封装：

```kotlin
// longPressDraggableHandle 源码实现
override fun Modifier.longPressDraggableHandle(...) =
    draggableHandle(
        enabled = enabled,
        interactionSource = interactionSource,
        onDragStarted = onDragStarted,
        onDragStopped = onDragStopped,
        dragGestureDetector = DragGestureDetector.LongPress
    )
```

### 4.2 选用建议

| 场景 | 推荐 | 原因 |
| --- | --- | --- |
| LazyRow / LazyColumn 中拖拽项 | `longPressDraggableHandle` | 一行解决长按触发问题，意图明确 |
| 全部内容都可拖（如主屏图标网格） | `longPressDraggableHandle` | 符合系统约定 |
| 需要精细控制 `dragGestureDetector` | `draggableHandle` | 显式传参，避免误解 |
| 旧代码兼容（已经用 `draggableHandle`） | 保持 + 加 `dragGestureDetector = LongPress` | 最小改动 |

> 📌 **本项目统一约定**：新代码优先用 `longPressDraggableHandle`，旧代码逐步迁移。

## 5. rememberReorderableLazyListState 与 onMove 回调

### 5.1 函数签名（[ReorderableLazyList.kt:134-143](file:///C:/Users/EDY/Desktop/CorgiMemo/Reorderable/reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/ReorderableLazyList.kt#L134-L143)）

```kotlin
@Composable
fun rememberReorderableLazyListState(
    lazyListState: LazyListState,
    scrollThresholdPadding: PaddingValues = PaddingValues(0.dp),
    scrollThreshold: Dp = ReorderableLazyCollectionDefaults.ScrollThreshold,  // 默认 48.dp
    scroller: Scroller = rememberScroller(...),
    onMove: suspend CoroutineScope.(from: LazyListItemInfo, to: LazyListItemInfo) -> Unit
): ReorderableLazyListState
```

| 参数 | 类型 | 默认 | 作用 |
| --- | --- | --- | --- |
| `lazyListState` | `LazyListState` | 必填 | 由 `rememberLazyListState()` 创建。`LazyColumn` / `LazyRow` 都用此类型 |
| `scrollThresholdPadding` | `PaddingValues` | `PaddingValues(0.dp)` | 容器内边距（如 nav bar 下方的列表需加 bottom padding），用于确定自动滚动的边界 |
| `scrollThreshold` | `Dp` | `48.dp` | 距容器边缘多少 dp 时触发自动滚动。48dp ≈ 一行高度的一半 |
| `scroller` | `Scroller` | `rememberScroller(...)` | 拖拽时自动滚动的实现。极少自定义 |
| `onMove` | `suspend CoroutineScope.(from, to) -> Unit` | 必填 | **核心回调**。见 §5.2 |

### 5.2 onMove 回调语义（**重要**）

```kotlin
onMove: suspend CoroutineScope.(from: LazyListItemInfo, to: LazyListItemInfo) -> Unit
```

| 项 | 说明 |
| --- | --- |
| **`from`** | 被拖动的源项（**正在被拖**的项，不是释放前的位置） |
| **`to`** | 目标项（拖动**当前重叠到**的位置上的项） |
| 触发时机 | **拖拽过程中**实时触发（不是释放时）。每跨过一个项触发一次 |
| `suspend` | 允许在回调内做异步操作（如 ViewModel 调用）。**必须 await 完才返回**（库会等 onMove 返回后才更新视觉） |
| `CoroutineScope` | `this` 指向 `rememberReorderableLazyListState` 的作用域。可用于 `launch` 后台任务 |
| `LazyListItemInfo.index` | 项在 LazyList 中的索引。**注意**：与 `items()` 的 `key` 配合使用最稳 |
| `LazyListItemInfo.key` | 项的 key（与 `items(items, key = { it.id })` 传入的 key 一致） |

### 5.3 onMove 实现模式

**模式 A：基于索引重排（最简单）**：

```kotlin
val state = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
    // 1. 数据集合重排（必须完成才返回）
    val list = currentList.toMutableList()
    val item = list.removeAt(from.index)
    list.add(to.index, item)
    onReorder(list)
}
```

**模式 B：基于 key 重排（更稳，推荐）**：

```kotlin
val state = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
    val list = currentList.toMutableList()
    val fromKey = from.key
    val fromIndex = list.indexOfFirst { it.key == fromKey }
    if (fromIndex == -1) return@rememberReorderableLazyListState  // 数据已变更，跳过
    val item = list.removeAt(fromIndex)
    val toKey = to.key
    val toIndex = list.indexOfFirst { it.key == toKey }
    list.add(toIndex.coerceAtMost(list.size), item)
    onReorder(list)
}
```

**模式 C：调用 ViewModel（异步持久化）**：

```kotlin
val state = rememberReorderableLazyListState(lazyListState = lazyListState) { from, to ->
    viewModel.applyReorder(lineIndex, from.index, to.index)  // suspend 函数，内部 launch 数据保存
}
```

### 5.4 onMove 注意事项

| ⚠️ 陷阱 | 后果 | 解决 |
| --- | --- | --- |
| onMove 内未完成数据更新就返回 | 视觉闪烁、状态错位 | 同步完成数据集合修改 |
| 用 `from.index` 但列表有动画中项 | 索引漂移 | 用 `from.key` 重新定位 |
| onMove 内做耗时 IO 不 `suspend` | 阻塞主线程 | 用 `suspend` + `withContext(IO)` |
| onMove 抛异常 | 拖拽卡住 | 用 `try-catch` 包裹业务逻辑 |
| 拖动到列表外 | 不会触发 onMove（自动取消） | 无需处理 |

## 6. ReorderableItem 函数

### 6.1 完整签名（[ReorderableLazyList.kt:281-288](file:///C:/Users/EDY/Desktop/CorgiMemo/Reorderable/reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/ReorderableLazyList.kt#L281-L288)）

```kotlin
fun LazyItemScope.ReorderableItem(
    state: ReorderableLazyListState,
    key: Any,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animateItemModifier: Modifier = Modifier.animateItem(),
    content: @Composable ReorderableCollectionItemScope.(isDragging: Boolean) -> Unit
)
```

| 参数 | 类型 | 默认 | 作用 |
| --- | --- | --- | --- |
| `state` | `ReorderableLazyListState` | 必填 | 由 `rememberReorderableLazyListState` 创建 |
| `key` | `Any` | 必填 | **必须**与外层 `items(items, key = { ... })` 传入的 key **完全一致** |
| `modifier` | `Modifier` | `Modifier` | 应用于整个 ReorderableItem 容器。可加 `Modifier.zIndex` 等 |
| `enabled` | `Boolean` | `true` | **项是否可被其他项越过重排**。注意：与 `draggableHandle.enabled` 是两层概念 |
| `animateItemModifier` | `Modifier` | `Modifier.animateItem()` | 项移动的动画行为。Material 3 标准动画 |
| `content` | `ReorderableCollectionItemScope.(isDragging) -> Unit` | 必填 | 内容 lambda。`this` 是 `ReorderableCollectionItemScope`，`isDragging` 由库提供 |

### 6.2 关键约束

| 约束 | 说明 |
| --- | --- |
| `key` 必须唯一且稳定 | 与 `items(key = ...)` 一致。**不能用 `index`**（重排后 index 会变） |
| `draggableHandle` 必须在 `content` lambda 内 | 因为 `content` 的 receiver 是 `ReorderableCollectionItemScope` |
| `enabled = false` 的项仍可被拖（如果手柄 enabled） | 仅**阻止**被其他项"越过"，但项本身可被拖出位置 |
| `isDragging` 由库提供 | 在 content lambda 内：`if (isDragging) Modifier.graphicsLayer { scaleX = 1.08f; shadowElevation = 8f }` |

## 7. 完整使用模板

### 7.1 基础模板（本项目图片/语音拖拽的实际模式）

```kotlin
@Composable
fun MyImageList(
    images: List<String>,
    onReorder: (List<String>) -> Unit,
    onClick: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 1. 状态：拖拽手势 + onMove 回调
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        // 同步完成数据重排（onMove 是 suspend，必须等返回）
        val newList = images.toMutableList().apply {
            val item = removeAt(from.index)
            add(to.index, item)
        }
        onReorder(newList)
    }

    LazyRow(
        state = lazyListState,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = images,
            key = { it }  // ⚠️ key 必须与 ReorderableItem 的 key 一致
        ) { imagePath ->
            ReorderableItem(
                state = reorderableState,
                key = imagePath  // ⚠️ 与 items() 的 key 一致
            ) { isDragging ->
                // 2. 拖拽中视觉（库内置 zIndex + scale 1.08 + 阴影）
                val elevation by animateFloatAsState(
                    targetValue = if (isDragging) 8f else 0f,
                    label = "imageDragElevation"
                )
                Box(
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)  // ⚠️ zIndex 必须在 LazyItemScope 内
                        .graphicsLayer {
                            shadowElevation = elevation
                            scaleX = if (isDragging) 1.08f else 1f
                            scaleY = if (isDragging) 1.08f else 1f
                            alpha = if (isDragging) 0.9f else 1f
                        }
                ) {
                    ImageItem(
                        imagePath = imagePath,
                        isDragging = isDragging,
                        onClick = { onClick(it) },
                        onDelete = { onDelete(it) },
                        // 3. 拖拽手柄：关键修正，用 LongPress 避免与滚动冲突
                        modifier = Modifier.draggableHandle(
                            onDragStarted = {
                                HapticFeedbackManager.performHapticFeedback(
                                    context = context,
                                    type = InteractionType.TEXT_MOVE,
                                    enabled = true
                                )
                            },
                            onDragStopped = {},
                            dragGestureDetector = DragGestureDetector.LongPress
                        )
                    )
                }
            }
        }
    }
}
```

### 7.2 关键约束清单

- ✅ `items()` 必须传 `key`
- ✅ `ReorderableItem` 的 `key` 必须与 `items()` 的 `key` 一致
- ✅ `draggableHandle` 必须在 `ReorderableItem` 的 content lambda 内
- ✅ 拖拽手柄必须显式传 `dragGestureDetector = DragGestureDetector.LongPress`（或用 `longPressDraggableHandle`）
- ✅ `onMove` 必须同步完成数据更新后才返回
- ✅ `Modifier.zIndex` 必须在 `LazyItemScope` 内调用（在 `ReorderableItem` content lambda 内）
- ❌ 不要用 `Modifier.draggableHandle` 作为顶层 import（它是接口成员函数，不是顶层函数）

## 8. 本项目使用清单

| 文件 | 用途 | 容器 | 拖拽模式 |
| --- | --- | --- | --- |
| [CheckboxEditText.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt) | 待办行内图片/语音附件 | `LazyRow` | `LongPress`（P5+P6 改造后） |
| HomeScreen 相关 | 首页 todo 卡片跨区拖拽 | `LazyColumn` | 待确认（建议审计） |
| AppDrawer 相关 | 侧滑栏分组/状态项拖拽 | `LazyColumn` / `LazyRow` | 待确认（建议审计） |

> 📌 **审计建议**：本规则发布后，**审计上述其他使用点是否也是 `LongPress`**，避免同样的滑动误触问题。

## 9. 常见陷阱与最佳实践

### 9.1 陷阱清单

| 陷阱 | 症状 | 解决 |
| --- | --- | --- |
| 顶层 import `sh.calvin.reorderable.draggableHandle` | 编译错误 `Unresolved reference 'draggableHandle'` | 删掉 import；在 `ReorderableItem` content lambda 内直接用 |
| 用默认 `Press` + LazyRow 水平滚动 | 滑动误触拖拽 | 显式传 `LongPress` |
| `ReorderableItem` 的 `key` 不与 `items()` 一致 | 拖拽后状态错乱、闪烁 | 用 `key = { it.id }` 统一 |
| `onMove` 内不更新数据就返回 | 视觉闪烁、项"反弹" | 同步完成数据集合修改 |
| 用 `from.index` 直接索引，但列表有动画 | 索引漂移 | 用 `from.key` 重新定位 |
| 把 `Modifier.zIndex` 写到 `ReorderableItem` 外面 | zIndex 不生效 | 必须在 `ReorderableItem` content lambda 内（LazyItemScope 作用域） |
| 误以为 `enabled = false` 让项不能拖 | 项仍可被拖出 | 用 `draggableHandle.enabled = false` 禁用手柄 |

### 9.2 最佳实践

1. **统一用 `longPressDraggableHandle`**：意图明确，避免误解默认行为
2. **基于 key 重排**：用 `key = { it.id }` + onMove 内 `indexOfFirst { it.key == ... }`，避免索引漂移
3. **拖拽中视觉靠 `isDragging` + `graphicsLayer`**：库不会自动加视觉，必须自己加
4. **触觉反馈放在 `onDragStarted`**：在用户长按触发的瞬间给一次 TEXT_MOVE 反馈
5. **数据更新同步完成**：`onMove` 内的 `onReorder(newList)` 必须同步，不能 `launch`
6. **跨进程状态变更后清理 reorderableKeys**：如果有 `enabled = false` 项在 onMove 中变化，需考虑是否需要重新添加 key
7. **拖拽暂停副作用**：语音/视频播放应在 `onDragStarted` 暂停，在 `onDragStopped` 恢复（不强制，按业务定）

## 10. 相关链接

- [Reorderable 库源码（库内）](file:///C:/Users/EDY/Desktop/CorgiMemo/Reorderable/reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable)
- [Reorderable 官方仓库](https://github.com/Calvin-LL/Reorderable)
- 本项目使用点：[CheckboxEditText.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt)
- 改造历史：P5（图片拖拽）、P6（语音拖拽）— 见 [图片拖拽迁移至Reorderable库与自定义上限实施计划.md](file:///c:/Users/EDY/Desktop/CorgiMemo/.trae/documents/图片拖拽迁移至Reorderable库与自定义上限实施计划.md)
