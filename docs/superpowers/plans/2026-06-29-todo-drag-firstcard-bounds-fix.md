# 待办拖拽首页边界异常修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复待办拖拽中两个首页边界异常——首页卡片下拖导致列表不受控滚动，以及非首页卡片与首页交换后飞出屏幕顶部。

**Architecture:** 拆分 `draggedBaseCenterY` 为"逻辑基线"和"滚动补偿"两个独立状态；新增 `isLongPressActive` 中间标志，长按触发即拦截滚动；引入纯函数 `computeDraggedListCenterY` 用目标索引反推基线，避免读 `visibleItemsInfo` 陈旧值。

**Tech Stack:** Kotlin 1.9.x + Jetpack Compose（LazyColumn / pointerInput / NestedScroll / StateMap / Animatable）

**关联设计文档:** [2026-06-29-todo-drag-firstcard-bounds-design.md](../specs/2026-06-29-todo-drag-firstcard-bounds-design.md)

---

## File Structure

| 文件 | 改动 | 职责 |
|------|------|------|
| [ReorderableLazyColumn.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt) | 修改 | 状态模型重构 + Bug A 拦截 + Bug B 基线重算 |
| [ReorderAlgorithmsTest.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt) | 新增测试 | 6 个新测试用例 |

**不修改**：`PressFeedback.kt` / `ReorderableColumn.kt` / ViewModel 层

---

## Task 1: 新增 `computeDraggedListCenterY` 纯函数（TDD）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:235-333`（在 `ReorderAlgorithms` object 中新增方法）
- Test: `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt:266-276`（在末尾新增测试）

- [ ] **Step 1: 写失败测试（在 `ReorderAlgorithmsTest.kt` 末尾 `shouldSkipDisplayUpdate` 测试后追加）**

```kotlin
// ==================== computeDraggedListCenterY 测试 ====================

/**
 * 场景：被拖项移到 index 0（顶部），行高 160px，被拖项自身 150px
 * 预期：topY = 0 * 160 = 0, center = 0 + 75 = 75f
 */
@Test
fun `computeDraggedListCenterY 移到顶部 index 0 返回被拖项中心`() {
    val center = ReorderAlgorithms.computeDraggedListCenterY(
        targetIndex = 0,
        draggedSize = 150,
        averageItemHeightPx = 160f
    )
    assertEquals(75.0f, center, 0.001f)
}

/**
 * 场景：被拖项移到 index 2，行高 160px
 * 预期：topY = 2 * 160 = 320, center = 320 + 75 = 395f
 */
@Test
fun `computeDraggedListCenterY 移到 index 2 返回 395f`() {
    val center = ReorderAlgorithms.computeDraggedListCenterY(
        targetIndex = 2,
        draggedSize = 150,
        averageItemHeightPx = 160f
    )
    assertEquals(395.0f, center, 0.001f)
}

/**
 * 场景：averageItemHeightPx <= 0（首帧未测量到行高）
 * 预期：回退到 draggedSize / 2（即默认顶部位置）
 */
@Test
fun `computeDraggedListCenterY 行高为 0 回退到 draggedSize 一半`() {
    val center = ReorderAlgorithms.computeDraggedListCenterY(
        targetIndex = 0,
        draggedSize = 150,
        averageItemHeightPx = 0f
    )
    assertEquals(75.0f, center, 0.001f)
}

/**
 * 场景：负数行高（异常输入）
 * 预期：视为无效，回退到 draggedSize / 2
 */
@Test
fun `computeDraggedListCenterY 负数行高回退`() {
    val center = ReorderAlgorithms.computeDraggedListCenterY(
        targetIndex = 5,
        draggedSize = 100,
        averageItemHeightPx = -50f
    )
    assertEquals(50.0f, center, 0.001f)
}

/**
 * 场景：模拟 Bug B 回归——B 从 index 1 移到 index 0 与首页 A 交换
 * 关键：基线应为 75f（B 在新位置 index 0 的中心），不是 235f（A 的新位置 = 1.5h）
 *
 * 原错误实现：draggedBaseCenterY = otherInfo.offset + draggedSize/2
 *   其中 otherInfo 是 A，A 在交换后移到 index 1，offset = 200px（行高 200px）
 *   错误结果 = 200 + 75 = 275f
 *
 * 新正确实现：draggedListCenterY = targetIndex * avgHeight + draggedSize/2
 *   targetIndex = 0（被拖项 B 的新位置）
 *   正确结果 = 0 * 200 + 75 = 75f
 */
@Test
fun `computeDraggedListCenterY B 上移与 A 交换后基线为 75f 不是 275f`() {
    val center = ReorderAlgorithms.computeDraggedListCenterY(
        targetIndex = 0,  // B 移到 index 0
        draggedSize = 150,
        averageItemHeightPx = 200f  // 行高 200px
    )
    assertEquals(75.0f, center, 0.001f)
    // 关键断言：绝不能是 275f（A 的新位置 = 1.5h = 错误基线）
    assert(center != 275.0f) { "基线错误：触发了 Bug B 的旧错误计算" }
}
```

- [ ] **Step 2: 运行测试，预期失败**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest.computeDraggedListCenterY*"
```

Expected: 编译失败，错误信息 `Unresolved reference: computeDraggedListCenterY`

- [ ] **Step 3: 在 `ReorderableLazyColumn.kt` 的 `ReorderAlgorithms` object 中实现新方法（line 332 之后追加）**

```kotlin
/**
 * 计算交换后被拖项在 displayItems 中的应有中心 Y
 *
 * 不依赖 listState.layoutInfo.visibleItemsInfo（与 displayItems 状态变更
 * 之间存在一帧延迟，交换后立即读取会读到陈旧位置）。
 *
 * 策略：目标索引 × 平均行高 = 被拖项应有顶部 Y；中心 = 顶部 + 自身高度/2
 *
 * @param targetIndex 被拖项交换后的目标索引（displayItems[targetIndex] 是被拖项）
 * @param draggedSize 被拖项自身高度（px）
 * @param averageItemHeightPx displayItems 中所有项的平均行高（px）
 * @return 被拖项在新位置的应有中心 Y
 */
fun computeDraggedListCenterY(
    targetIndex: Int,
    draggedSize: Int,
    averageItemHeightPx: Float
): Float {
    if (averageItemHeightPx <= 0f) return draggedSize / 2f
    val topY = targetIndex * averageItemHeightPx
    return topY + draggedSize / 2f
}
```

- [ ] **Step 4: 运行测试，预期全部通过**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest.computeDraggedListCenterY*"
```

Expected: `BUILD SUCCESSFUL`，5 个测试全部通过

- [ ] **Step 5: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git add app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt
git commit -m "feat(todo): 新增 computeDraggedListCenterY 纯函数及单元测试"
```

> 注意：PowerShell 不支持 `&&`，使用多个 `git` 命令分行执行或用 `;` 分隔。

---

## Task 2: 新增 `computeAverageItemHeightPx` 纯函数（TDD）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt`（在 `ReorderAlgorithms` object 末尾）
- Test: `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt`（追加测试）

- [ ] **Step 1: 写失败测试（在 `computeDraggedListCenterY` 测试后追加）**

```kotlin
// ==================== computeAverageItemHeightPx 测试 ====================

/**
 * 场景：itemHeights 为空（首帧未测量）
 * 预期：回退到 defaultHeightPx
 */
@Test
fun `computeAverageItemHeightPx 空 Map 回退到默认值`() {
    val avg = ReorderAlgorithms.computeAverageItemHeightPx(
        itemHeights = emptyMap(),
        defaultHeightPx = 160f
    )
    assertEquals(160.0f, avg, 0.001f)
}

/**
 * 场景：3 项高度分别为 100, 200, 300
 * 预期：平均 = (100+200+300)/3 = 200f
 */
@Test
fun `computeAverageItemHeightPx 三项平均返回 200f`() {
    val avg = ReorderAlgorithms.computeAverageItemHeightPx(
        itemHeights = mapOf(0 to 100, 1 to 200, 2 to 300),
        defaultHeightPx = 160f
    )
    assertEquals(200.0f, avg, 0.001f)
}
```

- [ ] **Step 2: 运行测试，预期失败**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest.computeAverageItemHeightPx*"
```

Expected: 编译失败，`Unresolved reference: computeAverageItemHeightPx`

- [ ] **Step 3: 实现纯函数（在 `computeDraggedListCenterY` 后追加）**

```kotlin
/**
 * 计算 displayItems 中所有已测量项的平均行高
 *
 * 用于 computeDraggedListCenterY 反推被拖项应有位置，
 * 避免依赖 visibleItemsInfo 的陈旧值。
 *
 * @param itemHeights 索引→高度（px）的映射，通过 onSizeChanged 收集
 * @param defaultHeightPx itemHeights 为空时回退的默认行高
 * @return 平均行高（px）
 */
fun computeAverageItemHeightPx(
    itemHeights: Map<Int, Int>,
    defaultHeightPx: Float
): Float {
    if (itemHeights.isEmpty()) return defaultHeightPx
    return itemHeights.values.sum().toFloat() / itemHeights.size
}
```

- [ ] **Step 4: 运行测试，预期通过**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest.computeAverageItemHeightPx*"
```

Expected: `BUILD SUCCESSFUL`，2 个测试通过

- [ ] **Step 5: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git add app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt
git commit -m "feat(todo): 新增 computeAverageItemHeightPx 纯函数及单元测试"
```

---

## Task 3: 在 `ReorderableLazyColumn` 中新增状态变量

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:387-431`（状态变量声明区）

- [ ] **Step 1: 在 `// ━━━ 拖拽状态 ━━━` 块（line 387）添加 `isLongPressActive`**

定位 `var lastHapticTime by remember { mutableLongStateOf(0L) }`（line 395），在其后添加：

```kotlin
/**
 * 长按触发标志（中间状态）
 *
 * 长按触发后立即置 true，驱动 dragScrollBlocker 拦截滚动，
 * 解决"长按触发到 isDragActive=true 之间的事件未消费窗口"。
 *
 * 与 isDragActive 区别：
 * - isLongPressActive：长按触发即 true，专用滚动拦截
 * - isDragActive：dy > dragThresholdPx 才 true，驱动交换逻辑
 *
 * 重置时机：松手 / 取消 / items 外部变更
 */
var isLongPressActive by remember { mutableStateOf(false) }
```

- [ ] **Step 2: 在 `// ━━━ 释放期状态 ━━━` 块（line 397-422）后添加新状态**

定位 `val releaseDragOffset = remember { mutableFloatStateOf(0f) }`（line 422），在其后添加：

```kotlin
// ━━━ 逻辑基线 + 滚动补偿（替换原 draggedBaseCenterY）━━━
/**
 * 纯逻辑基线：被拖项在 displayItems 中的应有中心 Y（不含 auto-scroll 调整）
 *
 * 初始化：进入拖拽时 = computeDraggedListCenterY(draggedOriginalIndex, ...)
 * 交换时：= computeDraggedListCenterY(targetIndex, ...)
 * 不再读 visibleItemsInfo（与 displayItems 状态变更存在一帧延迟）
 */
var draggedListCenterY by remember { mutableFloatStateOf(0f) }

/**
 * 滚动补偿：auto-scroll 期间列表整体平移了多少
 *
 * 由 LaunchedEffect(isDragActive, fingerY) 在每次 scrollBy 后累加 scrollDelta。
 * 替换原 draggedBaseCenterY += scrollDelta 写法，
 * 避免基线在 auto-scroll 与交换逻辑间产生歧义。
 */
var scrollCompensationY by remember { mutableFloatStateOf(0f) }

/**
 * 动态行高缓存：记录每个 displayItems 索引对应的实际渲染高度（px）
 *
 * key = displayItems 索引, value = 高度（像素）
 * 通过 Modifier.onSizeChanged 在首次布局时捕获每项真实尺寸，
 * 替代固定 160px 默认值，使 draggedListCenterY 计算更精准。
 */
val itemHeightsPx = remember { mutableStateMapOf<Int, Int>() }

/**
 * 当前已测量项的平均行高（px）
 *
 * 空缓存时回退到 defaultItemHeightPx = 160f
 */
val averageItemHeightPx = if (itemHeightsPx.isNotEmpty()) {
    ReorderAlgorithms.computeAverageItemHeightPx(
        itemHeights = itemHeightsPx,
        defaultHeightPx = 160f
    )
} else {
    160f
}
```

- [ ] **Step 3: 编译验证（仅新增变量，不应破坏现有功能）**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`（可能存在未使用变量警告，可忽略）

- [ ] **Step 4: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "refactor(todo): 新增 isLongPressActive / draggedListCenterY / scrollCompensationY 状态"
```

---

## Task 4: 添加 `onSizeChanged` 捕获行高

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:780-789`（普通项 Box 的 modifier）

- [ ] **Step 1: 在普通项 Box（line 782-787）添加 `onSizeChanged`**

定位：
```kotlin
} else {
    // ━━━ 普通项：animateItem 让位动画 ━━━
    Box(
        modifier = Modifier
            .animateItem()
            .zIndex(0f)
    ) {
        content(index, item, false, isDragActive)
    }
}
```

替换为：
```kotlin
} else {
    // ━━━ 普通项：animateItem 让位动画 ━━━
    Box(
        modifier = Modifier
            .animateItem()
            .zIndex(0f)
            // 捕获每项实际渲染高度，用于 computeDraggedListCenterY
            .onSizeChanged { size ->
                if (size.height > 0) {
                    itemHeightsPx[index] = size.height
                }
            }
    ) {
        content(index, item, false, isDragActive)
    }
}
```

- [ ] **Step 2: 确认 `onSizeChanged` 已 import（line 44 已有 `import androidx.compose.ui.layout.onSizeChanged`）**

```powershell
Select-String -Path "c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt" -Pattern "onSizeChanged"
```

Expected: 至少 2 处匹配（line 44 import + 新增的 onSizeChanged 调用）

- [ ] **Step 3: 编译验证**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "feat(todo): onSizeChanged 捕获行高用于基线计算"
```

---

## Task 5: 修复 Bug A——长按即拦截滚动

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:504-511`（dragScrollBlocker）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:551-557`（长按触发分支）
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:442-455`（LaunchedEffect(items) 重置）

- [ ] **Step 1: 升级 `dragScrollBlocker` 使用 `isLongPressActive`**

定位 line 504-511：
```kotlin
val dragScrollBlocker = remember {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
            // 拖拽激活时消费所有滚动请求，阻止列表跟随手指滚动
            return if (isDragActive) available else Offset.Zero
        }
    }
}
```

替换为：
```kotlin
val dragScrollBlocker = remember {
    object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): Offset {
            // 长按触发即拦截滚动（不等 isDragActive），
            // 解决"长按到 isDragActive=true 之间的事件未消费窗口"导致的列表失控滚动
            return if (isLongPressActive) available else Offset.Zero
        }
    }
}
```

- [ ] **Step 2: 长按触发分支增加 `isLongPressActive` + `change.consume()`**

定位 line 551-557：
```kotlin
if (!longPressTriggered && now - downTime >= longPressTimeoutMs) {
    val dragDistance = (change.position - downPosition).getDistance()
    if (dragDistance <= dragThresholdPx) {
        longPressTriggered = true
        // 长按触发但不震动（子项负责 LONG_CLICK 震动）
    }
}
```

替换为：
```kotlin
if (!longPressTriggered && now - downTime >= longPressTimeoutMs) {
    val dragDistance = (change.position - downPosition).getDistance()
    if (dragDistance <= dragThresholdPx) {
        longPressTriggered = true
        isLongPressActive = true
        // 立即消费当前事件，阻止 LazyColumn 在 isDragActive=true 前解释为滚动
        change.consume()
        // 长按触发但不震动（子项负责 LONG_CLICK 震动）
    }
}
```

- [ ] **Step 3: 在 LaunchedEffect(items) 中重置 `isLongPressActive`**

定位 line 432-455 的 LaunchedEffect(items)：
```kotlin
LaunchedEffect(items) {
    when {
        ReorderAlgorithms.shouldSkipDisplayUpdate(isReleasing) -> {
            Log.d(...)
        }
        !isDragActive -> {
            displayItems = items
        }
        else -> {
            displayItems = items
            isDragActive = false
            draggedKey = null
            draggedOriginalIndex = -1
            draggedCurrentIndex = -1
            fingerY = 0f
            draggedBaseCenterY = 0f
        }
    }
}
```

将 `else ->` 分支的 `draggedBaseCenterY = 0f` 替换为：
```kotlin
        else -> {
            displayItems = items
            isDragActive = false
            isLongPressActive = false
            draggedKey = null
            draggedOriginalIndex = -1
            draggedCurrentIndex = -1
            fingerY = 0f
            draggedListCenterY = 0f
            scrollCompensationY = 0f
        }
```

- [ ] **Step 4: 编译验证**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`（可能存在 `draggedBaseCenterY` 未使用警告，可忽略，Task 7 会删除）

- [ ] **Step 5: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "fix(todo): Bug A 长按触发即拦截滚动，关闭事件未消费窗口"
```

---

## Task 6: 重构 `dragOffsetY` 派生状态使用新基线

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:465-473`（dragOffsetY derivedStateOf）

- [ ] **Step 1: 替换 dragOffsetY 派生公式**

定位 line 465-473：
```kotlin
val dragOffsetY by remember {
    derivedStateOf {
        when {
            isDragActive -> fingerY - draggedBaseCenterY
            isReleasing -> releaseDragOffset.floatValue
            else -> 0f
        }
    }
}
```

替换为：
```kotlin
val dragOffsetY by remember {
    derivedStateOf {
        when {
            // 拖拽中：手指位置 - 被拖项在 displayItems 中的逻辑中心 - auto-scroll 补偿
            isDragActive -> fingerY - draggedListCenterY - scrollCompensationY
            isReleasing -> releaseDragOffset.floatValue
            else -> 0f
        }
    }
}
```

- [ ] **Step 2: 编译验证（应通过，`draggedBaseCenterY` 暂时未引用但还在）**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "refactor(todo): dragOffsetY 改用 draggedListCenterY - scrollCompensationY"
```

---

## Task 7: 修复 Bug B——交换分支基线重算

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:632-668`（交换分支）

- [ ] **Step 1: 替换交换分支的基线计算**

定位 line 648-652：
```kotlin
val otherInfo = listState.layoutInfo.visibleItemsInfo
    .find { it.key == swapTargetKey }
if (otherInfo != null) {
    draggedBaseCenterY = (otherInfo.offset + draggedSize / 2f)
}
```

替换为：
```kotlin
// 关键修复：用目标索引反推基线，不读 visibleItemsInfo
// 旧实现读取 otherInfo（被交换目标项），其 offset 在 displayItems 变更后
// 立即变化，与新 displayItems 不一致，导致基线漂移到 1.5h（Bug B 根因）
draggedListCenterY = ReorderAlgorithms.computeDraggedListCenterY(
    targetIndex = targetIndex,
    draggedSize = draggedSize,
    averageItemHeightPx = averageItemHeightPx
)
```

- [ ] **Step 2: 删除 `otherInfo` 变量声明（已被替换，无需保留）**

确认 line 648-652 已被完全替换为 Step 1 的代码块，不再有 `val otherInfo` 声明。

- [ ] **Step 3: 编译验证**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "fix(todo): Bug B 交换后基线改用 computeDraggedListCenterY 索引反推"
```

---

## Task 8: 更新进入拖拽时的初始化

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:588-602`（进入拖拽分支）

- [ ] **Step 1: 替换进入拖拽的基线初始化**

定位 line 588-602：
```kotlin
isDragActive = true
draggedKey = key(draggedItem)
draggedOriginalIndex = draggedIndex.index
draggedCurrentIndex = draggedIndex.index
draggedOriginalIsPinned = isPinned(draggedItem)
fingerY = change.position.y
draggedBaseCenterY = (draggedIndex.offset + draggedIndex.size / 2f)

// 震动：标记进入拖拽
HapticFeedbackManager.performHapticFeedback(...)
```

将 `draggedBaseCenterY = (draggedIndex.offset + draggedIndex.size / 2f)` 替换为：
```kotlin
draggedListCenterY = ReorderAlgorithms.computeDraggedListCenterY(
    targetIndex = draggedIndex.index,
    draggedSize = draggedIndex.size,
    averageItemHeightPx = averageItemHeightPx
)
scrollCompensationY = 0f
```

完整块应变为：
```kotlin
isDragActive = true
draggedKey = key(draggedItem)
draggedOriginalIndex = draggedIndex.index
draggedCurrentIndex = draggedIndex.index
draggedOriginalIsPinned = isPinned(draggedItem)
fingerY = change.position.y
draggedListCenterY = ReorderAlgorithms.computeDraggedListCenterY(
    targetIndex = draggedIndex.index,
    draggedSize = draggedIndex.size,
    averageItemHeightPx = averageItemHeightPx
)
scrollCompensationY = 0f

// 震动：标记进入拖拽
HapticFeedbackManager.performHapticFeedback(...)
```

- [ ] **Step 2: 编译验证**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "refactor(todo): 进入拖拽时初始化 draggedListCenterY 替代 draggedBaseCenterY"
```

---

## Task 9: 更新 auto-scroll 滚动补偿

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:476-498`（LaunchedEffect auto-scroll）

- [ ] **Step 1: 替换 auto-scroll 调整**

定位 line 491-495：
```kotlin
if (scrollDelta != 0f) {
    listState.scrollBy(scrollDelta)
    draggedBaseCenterY += scrollDelta // 同步调整基线
}
```

替换为：
```kotlin
if (scrollDelta != 0f) {
    listState.scrollBy(scrollDelta)
    // 仅累加滚动补偿到 scrollCompensationY，不动 draggedListCenterY
    // 这样交换逻辑的基线与滚动逻辑彻底解耦（修复 Bug B 根因）
    scrollCompensationY += scrollDelta
}
```

- [ ] **Step 2: 编译验证**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "refactor(todo): auto-scroll 调整改用 scrollCompensationY 累加"
```

---

## Task 10: 更新 finally 块状态清理

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:672-733`（finally 块）

- [ ] **Step 1: 替换 finally 块中的 `draggedBaseCenterY` 引用**

定位 line 720：`draggedBaseCenterY = 0f`

替换为：
```kotlin
isLongPressActive = false
draggedListCenterY = 0f
scrollCompensationY = 0f
```

完整 finally 块（拖拽结束分支）应包含：
```kotlin
isDragActive = false
isLongPressActive = false
draggedKey = null
draggedOriginalIndex = -1
draggedCurrentIndex = -1
fingerY = 0f
draggedListCenterY = 0f
scrollCompensationY = 0f
lastSwapTargetKey = null
lastSwapFingerY = 0f
```

- [ ] **Step 2: 编译验证**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 全文件搜索确认无 `draggedBaseCenterY` 残留**

```powershell
Select-String -Path "c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt" -Pattern "draggedBaseCenterY"
```

Expected: 无输出（所有引用已替换为新状态变量）

- [ ] **Step 4: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "refactor(todo): finally 块清理逻辑改用新状态变量，移除 draggedBaseCenterY"
```

---

## Task 11: 移除 `draggedBaseCenterY` 状态声明

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:394`（状态变量声明）

- [ ] **Step 1: 删除 `draggedBaseCenterY` 声明**

定位 line 394：`var draggedBaseCenterY by remember { mutableFloatStateOf(0f) }`

直接删除该行。

- [ ] **Step 2: 编译验证**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交（PowerShell 兼容）**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "refactor(todo): 移除已废弃的 draggedBaseCenterY 状态声明"
```

---

## Task 12: 全量单元测试验证

**Files:**
- Test: 现有所有测试

- [ ] **Step 1: 运行所有单元测试**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`，所有测试通过（应包含 5 + 2 = 7 个新增测试 + 原有 19 个测试）

- [ ] **Step 2: 确认测试数量**

```powershell
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest" 2>&1 | Select-String -Pattern "tests completed|test\(s\) completed"
```

Expected: 输出包含 "26 tests completed"（19 原有 + 5 computeDraggedListCenterY + 2 computeAverageItemHeightPx）

- [ ] **Step 3: 无需新增提交（验证步骤）**

---

## Task 13: 手动验证 11 个场景

**Files:** 无（仅手动测试）

- [ ] **Step 1: 启动应用到首页（至少 4 张待办卡片）**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:installDebug
```

安装到设备/模拟器，启动应用，确保首页有 4+ 张待办卡片。

- [ ] **Step 2: 场景 1——首页顶部下拖**

长按首页第 1 张卡片 500ms → 向下拖动 50px → 观察：卡片跟随手指，列表不滚动

Expected: 卡片随手指移动，列表无任何滚动

- [ ] **Step 3: 场景 2——首页顶部上拖到屏幕外**

长按首页第 1 张卡片 500ms → 向上拖动超过 50px → 观察：卡片跟随手指，列表不滚动

Expected: 卡片随手指移动到屏幕顶部，列表不滚动

- [ ] **Step 4: 场景 3——第 2 张卡片上拖与首页交换**

长按第 2 张卡片 500ms → 向上拖动到首页位置 → 释放 → 观察：第 2 张卡片停留在新位置（顶部），不飞出

Expected: 交换后第 2 张卡片精确停留在首页位置，无飞出

- [ ] **Step 5: 场景 4——第 2 张卡片下拖与首页交换**

长按第 2 张卡片 500ms → 向下拖动（强制交换） → 释放 → 观察：第 2 张卡片停留在新位置

Expected: 交换后第 2 张卡片精确停留

- [ ] **Step 6: 场景 5——中间卡片上拖到首页**

长按第 3 张卡片 500ms → 向上拖到首页 → 释放 → 观察

Expected: 第 3 张卡片精确停留在首页

- [ ] **Step 7: 场景 6——连续快速交换 3 次**

长按任一卡片 → 连续上拖 3 次交换 → 释放 → 观察

Expected: 每次交换后卡片精确停留，无累积漂移

- [ ] **Step 8: 场景 7——接近顶部边缘 auto-scroll**

长按列表底部卡片 500ms → 向上拖到屏幕顶部边缘 → 观察 auto-scroll 触发时卡片跟随

Expected: auto-scroll 期间卡片平滑跟随，基线无漂移

- [ ] **Step 9: 场景 8——auto-scroll 期间交换**

长按列表中部卡片 500ms → 拖到顶部边缘触发 auto-scroll → 滚动期间发生交换

Expected: 交换后卡片精确停留

- [ ] **Step 10: 场景 9——跨置顶区交换**

确保有置顶卡片和普通卡片 → 拖动普通卡片穿越置顶区分界线

Expected: 交换精确，isPinned 自动切换

- [ ] **Step 11: 场景 10——长按后立即松手**

长按首页卡片 500ms+ → 不移动直接松手

Expected: 触发原有 `onLongClick` 逻辑（可能是选中/编辑），无拖拽副作用

- [ ] **Step 12: 场景 11——长按后快速移动超过 8dp**

长按首页卡片 500ms → 立即以快速度向下移动 20dp

Expected: 进入拖拽模式，列表无任何滚动闪烁

- [ ] **Step 13: 无需提交（验证步骤，发现问题则修复后单独提交）**

---

## Task 14: 最终全量编译 + 提交

- [ ] **Step 1: 全量编译**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
./gradlew :app:compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 查看本次修复所有提交**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
git log --oneline -10
```

Expected: 看到本计划的 11 个 feat/fix/refactor 提交

- [ ] **Step 3: 确认无未提交修改**

```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
git status
```

Expected: 仅显示与本任务无关的预先存在修改（如 `.kotlin/sessions/*` 临时文件）

---

## 完成标准

- [x] Task 1-11 全部完成且编译通过
- [x] Task 12 全部 26 个单元测试通过
- [x] Task 13 全部 11 个手动场景验证通过
- [x] Task 14 最终全量编译通过

## 回退方案

若手动验证发现严重问题：
```bash
cd "c:\Users\EDY\Desktop\CorgiMemo"
git log --oneline -15
git revert --no-commit <commit-hash>..HEAD
```

回退到本计划开始前的状态。
