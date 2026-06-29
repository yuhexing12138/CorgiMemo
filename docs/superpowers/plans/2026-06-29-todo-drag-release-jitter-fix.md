# 待办拖拽松手后视觉跳跃 Bug 修复 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复待办卡片长按拖拽交换后，被交换的两张卡片出现「上下跳跃交换顺序」的视觉跳变问题，引入 250ms「释放动画期」平滑过渡。

**Architecture:** 在 `ReorderableLazyColumn` 中引入「Releasing」中间状态：松手后不立即重置 `draggedKey` 和 `isDragActive`，而是用 `Animatable<Float>` 驱动 `dragOffsetY` 从松手时偏移平滑过渡到 0（250ms tween）。期间 A 继续在「拖拽分支」中，`animateItem()` 不重启；`LaunchedEffect(items)` 检测到 `isReleasing` 时跳过更新，避免破坏动画。动画结束后再重置 `draggedKey` → A 切到「普通分支」（displayItems 与 items 顺序一致，无额外动画）。

**Tech Stack:** Jetpack Compose BOM 2026.04.01 / Compose 1.9.2 / Animatable / tween / FastOutSlowInEasing

**Spec:** `docs/superpowers/specs/2026-06-29-todo-drag-release-jitter-fix-design.md`

**关联修复：** `docs/superpowers/specs/2026-06-28-拖拽高度差跳动bug修复-design.md`（保持不变，本次仅解决松手后跳跃）

---

## 文件结构

| 文件 | 改动类型 | 责任 |
|------|---------|------|
| `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt` | 修改 | (1) `ReorderAlgorithms` object 新增 2 个纯函数；(2) `ReorderableLazyColumn` Composable 新增 `isReleasing` 状态、`releaseDragOffset` Animatable；(3) 改造 `dragOffsetY` derivedStateOf；(4) 改造 `LaunchedEffect(items)`；(5) 改造 `pointerInput` 入口；(6) 重写 `finally` 块；(7) 添加 5 个 Logcat 打点 |
| `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt` | 修改 | 新增 5 个测试用例（覆盖 2 个新纯函数） |

**不改动**：所有其他文件（`TodoListItem.kt`、`SwipeableTodoBox.kt`、`HomeScreen.kt`、`HomeViewModel.kt`、数据库等）

---

## Task 1: 在 ReorderAlgorithms 中添加 2 个新纯函数

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:234-307`

- [ ] **Step 1: 在 ReorderAlgorithms object 末尾添加新函数**

打开 `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt`，定位到 `ReorderAlgorithms` object 的 `checkPinnedZoneCrossed` 函数末尾（行 306 的 `}` 后、object 的 `}` 之前）。

将：

```kotlin
    fun checkPinnedZoneCrossed(
        displayItems: List<Boolean>,
        draggedOriginalIsPinned: Boolean,
        draggedCurrentIndex: Int
    ): Boolean {
        if (draggedCurrentIndex < 0 || draggedCurrentIndex >= displayItems.size) return false
        val neighborIsPinned = when {
            draggedCurrentIndex > 0 -> displayItems[draggedCurrentIndex - 1]
            draggedCurrentIndex < displayItems.size - 1 -> displayItems[draggedCurrentIndex + 1]
            else -> draggedOriginalIsPinned
        }
        return draggedOriginalIsPinned != neighborIsPinned
    }
}
```

改为：

```kotlin
    fun checkPinnedZoneCrossed(
        displayItems: List<Boolean>,
        draggedOriginalIsPinned: Boolean,
        draggedCurrentIndex: Int
    ): Boolean {
        if (draggedCurrentIndex < 0 || draggedCurrentIndex >= displayItems.size) return false
        val neighborIsPinned = when {
            draggedCurrentIndex > 0 -> displayItems[draggedCurrentIndex - 1]
            draggedCurrentIndex < displayItems.size - 1 -> displayItems[draggedCurrentIndex + 1]
            else -> draggedOriginalIsPinned
        }
        return draggedOriginalIsPinned != neighborIsPinned
    }

    /**
     * 计算释放动画的起始 offset（从 ReorderableLazyColumn finally 块提取为纯函数）
     *
     * 用途：用户松手时，被拖卡片 A 的内层 Box offset 仍保持在 `fingerY - baseCenterY`，
     * 需要驱动 Animatable 从该值平滑过渡到 0，避免松手瞬间 offset 归零造成的视觉瞬移。
     *
     * @param fingerY 手指 Y 坐标（视口坐标）
     * @param baseCenterY 拖拽基线中心 Y（最后交换时的目标项 offset + draggedSize/2）
     * @return 释放动画起始 offset（手指相对基线的偏移）
     */
    fun computeReleaseStartOffset(fingerY: Float, baseCenterY: Float): Float =
        fingerY - baseCenterY

    /**
     * 判断释放动画期间是否应跳过 displayItems 更新
     *
     * 用途：松手后 250ms 释放动画期间，ViewModel 异步 onReorder 结果回流可能触发
     * `LaunchedEffect(items)` 重置 displayItems。如果在动画期间重置，会破坏正在播放的
     * 释放动画（A 的内层 Box offset 与新的 displayItems 位置不一致），造成新的跳变。
     *
     * @param isReleasing 是否处于释放动画期
     * @return true = 跳过 displayItems 更新；false = 正常更新
     */
    fun shouldSkipDisplayUpdate(isReleasing: Boolean): Boolean = isReleasing
}
```

- [ ] **Step 2: 跳过编译验证（按工作区规则询问用户）**

按照工作区编译验证规则，**不擅自执行编译命令**。本步骤为可选验证：在 IDE 中打开文件，IDE 会自动编译并高亮语法错误。如需手动验证，**先询问用户授权**。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "refactor(todo): 在 ReorderAlgorithms 中添加释放动画相关的 2 个纯函数"
```

---

## Task 2: 为新纯函数添加 5 个单元测试（TDD）

**Files:**
- Modify: `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt`

- [ ] **Step 1: 在测试类末尾添加新测试用例**

打开 `app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt`，定位到 `checkPinnedZoneCrossed index 越界返回 false` 测试（行 226-235）的末尾 `}` 后、类的 `}` 之前。

在最后那个 `}` 之前（即类的 `}` 之前，line 235 之后）添加：

```kotlin

    // ==================== computeReleaseStartOffset 测试 ====================

    /**
     * 场景：手指在基线下方 60px
     * 期望：返回 60.0f
     */
    @Test
    fun `computeReleaseStartOffset 正偏移`() {
        val offset = ReorderAlgorithms.computeReleaseStartOffset(
            fingerY = 200f,
            baseCenterY = 140f
        )
        assertEquals(60.0f, offset, 0.001f)
    }

    /**
     * 场景：手指正好在基线中心
     * 期望：返回 0.0f
     */
    @Test
    fun `computeReleaseStartOffset 零偏移`() {
        val offset = ReorderAlgorithms.computeReleaseStartOffset(
            fingerY = 140f,
            baseCenterY = 140f
        )
        assertEquals(0.0f, offset, 0.001f)
    }

    /**
     * 场景：手指在基线上方 30px（基线下方为正方向）
     * 期望：返回 -30.0f
     */
    @Test
    fun `computeReleaseStartOffset 负偏移`() {
        val offset = ReorderAlgorithms.computeReleaseStartOffset(
            fingerY = 110f,
            baseCenterY = 140f
        )
        assertEquals(-30.0f, offset, 0.001f)
    }

    // ==================== shouldSkipDisplayUpdate 测试 ====================

    /**
     * 场景：释放动画期间
     * 期望：返回 true（跳过 displayItems 更新）
     */
    @Test
    fun `shouldSkipDisplayUpdate 释放期间返回 true`() {
        assertEquals(true, ReorderAlgorithms.shouldSkipDisplayUpdate(isReleasing = true))
    }

    /**
     * 场景：非释放动画期间
     * 期望：返回 false（正常更新 displayItems）
     */
    @Test
    fun `shouldSkipDisplayUpdate 非释放期间返回 false`() {
        assertEquals(false, ReorderAlgorithms.shouldSkipDisplayUpdate(isReleasing = false))
    }
```

- [ ] **Step 2: 运行测试验证通过**

执行命令：`./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.ReorderAlgorithmsTest"`

**预期输出**：

```
> Task :app:testDebugUnitTest
> ...

BUILD SUCCESSFUL in 5s
```

5 个新测试全部通过（TC-R-01 至 TC-R-05）。

- [ ] **Step 3: 提交**

```bash
git add app/src/test/java/com/corgimemo/app/ui/components/ReorderAlgorithmsTest.kt
git commit -m "test(todo): 为 computeReleaseStartOffset 和 shouldSkipDisplayUpdate 添加 5 个单元测试"
```

---

## Task 3: 在 ReorderableLazyColumn 中添加 import 语句

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:1-54`

- [ ] **Step 1: 添加缺失的 import**

当前 import 块（行 1-54）缺少 `androidx.compose.animation.core.Animatable`、`androidx.compose.animation.core.tween`、`androidx.compose.animation.core.FastOutSlowInEasing`、`androidx.compose.runtime.rememberCoroutineScope`、`android.util.Log`。

在 import 块中适当位置（按字母顺序）添加以下 5 行：

```kotlin
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.rememberCoroutineScope
```

**添加位置参考**：
- `android.util.Log` 放在 `androidx.compose.foundation.Canvas` 之前（按字母序）
- `androidx.compose.animation.core.*` 三个放在 `androidx.compose.foundation.Canvas` 之后
- `androidx.compose.runtime.rememberCoroutineScope` 放在 `androidx.compose.runtime.remember` 之后

- [ ] **Step 2: 验证 import 正确**

打开文件，确认没有重复 import，5 个新 import 都在正确的位置。

- [ ] **Step 3: 暂不提交（与后续任务一起）**

留到 Task 7 最后统一提交。

---

## Task 4: 添加 isReleasing 状态和 releaseDragOffset Animatable

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:361-376`

- [ ] **Step 1: 定位状态变量块**

定位到 `ReorderableLazyColumn` Composable 函数体中 `// ━━━ 拖拽状态 ━━━` 注释后的状态声明区（行 361-376）。

当前状态声明：

```kotlin
    // ━━━ 拖拽状态 ━━━
    var isDragActive by remember { mutableStateOf(false) }
    var draggedKey by remember { mutableStateOf<Any?>(null) }
    var draggedOriginalIndex by remember { mutableIntStateOf(-1) }
    var draggedCurrentIndex by remember { mutableIntStateOf(-1) }
    var draggedOriginalIsPinned by remember { mutableStateOf(false) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var draggedBaseCenterY by remember { mutableFloatStateOf(0f) }
    var lastHapticTime by remember { mutableLongStateOf(0L) }

    // ━━━ 反向交换锁定状态（修复点 3）━━━
    // 交换后记录目标 key 和手指位置，防止下一帧立即反向交换导致震荡
    // 清除条件：手指移动超过 draggedSize/2 后清除锁定
    var lastSwapTargetKey by remember { mutableStateOf<Any?>(null) }
    var lastSwapFingerY by remember { mutableFloatStateOf(0f) }
```

- [ ] **Step 2: 添加新状态**

在 `// ━━━ 反向交换锁定状态（修复点 3）━━━` 注释块之前（即 `var lastHapticTime` 之后），添加：

```kotlin
    // ━━━ 释放动画状态（松手后视觉跳跃修复）━━━
    /**
     * 释放动画期标志
     *
     * 松手后 250ms 期间为 true。期间：
     * - draggedKey 保持有效，A 继续在「拖拽分支」中（animateItem 不重启）
     * - dragOffsetY 由 releaseDragOffset 驱动平滑过渡到 0
     * - LaunchedEffect(items) 跳过 displayItems 更新（避免破坏动画）
     */
    var isReleasing by remember { mutableStateOf(false) }

    /**
     * 释放动画 Animatable
     *
     * 驱动 A 的内层 Box offset 从松手时偏移（fingerY - baseCenterY）平滑过渡到 0。
     * 使用 Animatable 而非直接修改 fingerY，因为：
     * - Animatable 是 Composable 状态，Compose 自动驱动重组
     * - 250ms tween FastOutSlowInEasing 与 animateItem 默认一致
     */
    val releaseDragOffset = remember { Animatable(0f) }
```

- [ ] **Step 3: 暂不提交（与后续任务一起）**

留到 Task 7 最后统一提交。

---

## Task 5: 改造 dragOffsetY derivedStateOf

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:401-406`

- [ ] **Step 1: 定位 dragOffsetY 声明**

定位到 `// ━━━ dragOffsetY 同步计算` 注释块（行 401-406）：

```kotlin
    // ━━━ dragOffsetY 同步计算（derivedStateOf 避免在 Composable 中声明带 getter 的局部属性）━━━
    val dragOffsetY by remember {
        derivedStateOf {
            if (isDragActive && draggedKey != null) fingerY - draggedBaseCenterY else 0f
        }
    }
```

- [ ] **Step 2: 改造为支持 isReleasing 状态**

将：

```kotlin
    val dragOffsetY by remember {
        derivedStateOf {
            if (isDragActive && draggedKey != null) fingerY - draggedBaseCenterY else 0f
        }
    }
```

改为：

```kotlin
    val dragOffsetY by remember {
        derivedStateOf {
            when {
                isDragActive -> fingerY - draggedBaseCenterY
                isReleasing -> releaseDragOffset.value
                else -> 0f
            }
        }
    }
```

**说明**：
- `isDragActive`：拖拽中，跟随手指
- `isReleasing`：释放动画期，由 Animatable 驱动
- 其他情况：0f

- [ ] **Step 3: 暂不提交（与后续任务一起）**

留到 Task 7 最后统一提交。

---

## Task 6: 改造 LaunchedEffect(items) 增加 isReleasing 分支

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:378-392`

- [ ] **Step 1: 定位 LaunchedEffect(items)**

定位到 `// ━━━ 显示列表（拖拽中可重排，与 items 解耦）━━━` 注释块（行 377-392）：

```kotlin
    // ━━━ 显示列表（拖拽中可重排，与 items 解耦）━━━
    var displayItems by remember { mutableStateOf(items) }
    LaunchedEffect(items) {
        if (!isDragActive) {
            displayItems = items
        } else {
            // 拖拽中 items 被外部变更（如同步）→ 取消拖拽
            displayItems = items
            isDragActive = false
            draggedKey = null
            draggedOriginalIndex = -1
            draggedCurrentIndex = -1
            fingerY = 0f
            draggedBaseCenterY = 0f
        }
    }
```

- [ ] **Step 2: 改造为 when 表达式**

将：

```kotlin
    LaunchedEffect(items) {
        if (!isDragActive) {
            displayItems = items
        } else {
            // 拖拽中 items 被外部变更（如同步）→ 取消拖拽
            displayItems = items
            isDragActive = false
            draggedKey = null
            draggedOriginalIndex = -1
            draggedCurrentIndex = -1
            fingerY = 0f
            draggedBaseCenterY = 0f
        }
    }
```

改为：

```kotlin
    LaunchedEffect(items) {
        when {
            // 释放动画期间：跳过更新，避免破坏正在播放的释放动画
            // （A 的内层 Box offset 与新 displayItems 位置不一致会导致跳变）
            ReorderAlgorithms.shouldSkipDisplayUpdate(isReleasing) -> {
                Log.d("ReorderableLazyColumn",
                    "[DISPLAY_ITEMS_REFRESH] skipped: isReleasing=true, items.size=${items.size}")
            }
            // 非拖拽中：正常更新
            !isDragActive -> {
                displayItems = items
            }
            // 拖拽中 items 被外部变更（如同步）→ 取消拖拽
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

- [ ] **Step 3: 暂不提交（与后续任务一起）**

留到 Task 7 最后统一提交。

---

## Task 7: 重写 finally 块，启动释放动画协程

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:595-631`

- [ ] **Step 1: 定位 finally 块**

定位到 `try/finally` 块的 `finally` 部分（行 596-631）：

```kotlin
                } finally {
                    // ━━━ 拖拽结束或异常 ━━━
                    if (isDragActive) {
                        // 提交排序
                        if (draggedOriginalIndex != draggedCurrentIndex && draggedOriginalIndex >= 0) {
                            val displayPinned = displayItems.map { isPinned(it) }
                            val crossedPinnedZone = ReorderAlgorithms.checkPinnedZoneCrossed(
                                displayItems = displayPinned,
                                draggedOriginalIsPinned = draggedOriginalIsPinned,
                                draggedCurrentIndex = draggedCurrentIndex
                            )
                            onReorder(
                                draggedOriginalIndex,
                                draggedCurrentIndex,
                                crossedPinnedZone
                            )

                            // 确认震动
                            HapticFeedbackManager.performHapticFeedback(
                                context = context,
                                type = InteractionType.CONFIRM,
                                enabled = true
                            )
                        }

                        // 重置状态
                        isDragActive = false
                        draggedKey = null
                        draggedOriginalIndex = -1
                        draggedCurrentIndex = -1
                        fingerY = 0f
                        draggedBaseCenterY = 0f
                        lastSwapTargetKey = null
                        lastSwapFingerY = 0f
                    }
                }
```

- [ ] **Step 2: 改造 finally 块**

将：

```kotlin
                } finally {
                    // ━━━ 拖拽结束或异常 ━━━
                    if (isDragActive) {
                        // 提交排序
                        if (draggedOriginalIndex != draggedCurrentIndex && draggedOriginalIndex >= 0) {
                            val displayPinned = displayItems.map { isPinned(it) }
                            val crossedPinnedZone = ReorderAlgorithms.checkPinnedZoneCrossed(
                                displayItems = displayPinned,
                                draggedOriginalIsPinned = draggedOriginalIsPinned,
                                draggedCurrentIndex = draggedCurrentIndex
                            )
                            onReorder(
                                draggedOriginalIndex,
                                draggedCurrentIndex,
                                crossedPinnedZone
                            )

                            // 确认震动
                            HapticFeedbackManager.performHapticFeedback(
                                context = context,
                                type = InteractionType.CONFIRM,
                                enabled = true
                            )
                        }

                        // 重置状态
                        isDragActive = false
                        draggedKey = null
                        draggedOriginalIndex = -1
                        draggedCurrentIndex = -1
                        fingerY = 0f
                        draggedBaseCenterY = 0f
                        lastSwapTargetKey = null
                        lastSwapFingerY = 0f
                    }
                }
```

改为：

```kotlin
                } finally {
                    // ━━━ 拖拽结束或异常 ━━━
                    if (isDragActive) {
                        // 1. 计算释放动画起始 offset（松手时手指相对基线的偏移）
                        val releaseStartOffset = ReorderAlgorithms.computeReleaseStartOffset(
                            fingerY = fingerY,
                            baseCenterY = draggedBaseCenterY
                        )
                        // isDragActive 置 false（不再消费 pointerEvent）
                        // draggedKey 保持有效 → A 继续在「拖拽分支」中，animateItem 不重启
                        isDragActive = false

                        Log.d("ReorderableLazyColumn",
                            "[RELEASE_START] offset=$releaseStartOffset idx=$draggedCurrentIndex")

                        // 2. 提交排序（同步调用；ViewModel 内部 viewModelScope.launch 异步执行 DB 更新）
                        if (draggedOriginalIndex != draggedCurrentIndex && draggedOriginalIndex >= 0) {
                            val displayPinned = displayItems.map { isPinned(it) }
                            val crossedPinnedZone = ReorderAlgorithms.checkPinnedZoneCrossed(
                                displayItems = displayPinned,
                                draggedOriginalIsPinned = draggedOriginalIsPinned,
                                draggedCurrentIndex = draggedCurrentIndex
                            )
                            onReorder(
                                draggedOriginalIndex,
                                draggedCurrentIndex,
                                crossedPinnedZone
                            )

                            // 确认震动
                            HapticFeedbackManager.performHapticFeedback(
                                context = context,
                                type = InteractionType.CONFIRM,
                                enabled = true
                            )
                        }

                        // 3. 启动释放动画（250ms tween，与 animateItem 默认一致）
                        // 使用 rememberCoroutineScope 启动独立协程，不受 pointerInput 协程取消影响
                        scope.launch {
                            try {
                                isReleasing = true
                                releaseDragOffset.snapTo(releaseStartOffset)
                                Log.d("ReorderableLazyColumn",
                                    "[RELEASE_OFFSET_SNAP] from=$releaseStartOffset to=${releaseDragOffset.value}")
                                releaseDragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 250,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            } finally {
                                // 4. 动画结束后重置所有状态
                                isReleasing = false
                                draggedKey = null
                                draggedOriginalIndex = -1
                                draggedCurrentIndex = -1
                                fingerY = 0f
                                draggedBaseCenterY = 0f
                                lastSwapTargetKey = null
                                lastSwapFingerY = 0f
                                Log.d("ReorderableLazyColumn",
                                    "[RELEASE_END] isDragActive=$isDragActive")
                            }
                        }
                    } else if (isReleasing) {
                        // pointerInput 因异常/外部原因被取消，但释放动画仍在进行
                        // Animatable 协程独立于 pointerInput 协程，会自然完成
                        Log.d("ReorderableLazyColumn",
                            "[RELEASE_START] pointerInput cancelled during releasing")
                    }
                }
```

- [ ] **Step 3: 添加 scope 变量声明（独立子步骤）**

`scope` 在 `ReorderableLazyColumn` Composable 中需要通过 `rememberCoroutineScope()` 获取。**先在文件中搜索 `scope` 的现有用法**：

```bash
# 搜索 scope 变量是否已存在
grep -n "scope" app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
```

**情况 A**：如果文件中已存在 `scope` 变量（例如 `val scope = rememberCoroutineScope()`），则跳过本步骤。

**情况 B**：如果不存在，在 `// ━━━ 拖拽时禁用列表滚动（通过nestedScroll拦截）━━━` 注释之前（行 433 附近）添加：

```kotlin
    val scope = rememberCoroutineScope()
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "fix(todo): 修复拖拽松手后视觉跳跃问题，引入 250ms 释放动画期"
```

---

## Task 8: 改造 pointerInput 入口清理前次释放动画

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt:450-458`

- [ ] **Step 1: 定位 pointerInput 入口**

定位到 `awaitEachGesture` 块的开头（行 451-458）：

```kotlin
            pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                if (down.isConsumed) return@awaitEachGesture

                // 早期 return 会导致所有手势无响应（项目记忆教训）
                // 将 isDragEnabled 与 items.size 判断移至手势内部
                if (!isDragEnabled) return@awaitEachGesture
                if (items.size < 2) return@awaitEachGesture
```

- [ ] **Step 2: 添加释放动画清理**

将：

```kotlin
            pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                if (down.isConsumed) return@awaitEachGesture

                // 早期 return 会导致所有手势无响应（项目记忆教训）
                // 将 isDragEnabled 与 items.size 判断移至手势内部
                if (!isDragEnabled) return@awaitEachGesture
                if (items.size < 2) return@awaitEachGesture
```

改为：

```kotlin
            pointerInput(Unit) {
                awaitEachGesture {
                    // 释放动画期间尝试新拖拽 → 清理前次动画
                    // 原因：用户可能在前次拖拽的释放动画完成前就开始新拖拽
                    if (isReleasing) {
                        isReleasing = false
                        draggedKey = null
                        draggedBaseCenterY = 0f
                        // Animatable 协程的 finally 块会检测 isReleasing=false → 协程自然结束
                    }

                    val down = awaitFirstDown(requireUnconsumed = false)
                if (down.isConsumed) return@awaitEachGesture

                // 早期 return 会导致所有手势无响应（项目记忆教训）
                // 将 isDragEnabled 与 items.size 判断移至手势内部
                if (!isDragEnabled) return@awaitEachGesture
                if (items.size < 2) return@awaitEachGesture
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/ReorderableLazyColumn.kt
git commit -m "fix(todo): pointerInput 入口清理前次未完成的释放动画"
```

---

## Task 9: 编译验证

**Files:** 无文件修改

- [ ] **Step 1: 询问用户是否需要执行编译验证**

按照工作区编译验证规则，**不擅自执行编译命令**，询问用户。

- [ ] **Step 2: 用户授权后执行编译**

执行命令：`./gradlew :app:compileDebugKotlin`

**预期输出**：`BUILD SUCCESSFUL`

如果编译失败，检查：
- 5 个 import 是否正确添加（`Log`、`Animatable`、`tween`、`FastOutSlowInEasing`、`rememberCoroutineScope`）
- `scope` 变量是否在合适位置声明
- 函数签名是否正确

---

## Task 10: 手动验证清单

**Files:** 无文件修改

按以下清单在真机/模拟器上逐项测试，**记录结果**：

- [ ] **Step 1: 基础场景验证**

| # | 场景 | 预期 | 结果 |
|---|------|------|------|
| 1 | 等高卡片单次交换后松手 | A 从手指位置平滑滑到新位置，无跳跃 | ☐ |
| 2 | 不等高卡片交换后松手 | 同上，无高度差震荡 | ☐ |
| 3 | 多次连续交换后松手 | A 平滑落位，B/C/D 不产生额外动画 | ☐ |
| 4 | 拖拽后未交换直接松手 | A 平滑回到原位，无跳跃 | ☐ |

- [ ] **Step 2: 边界场景验证**

| # | 场景 | 预期 | 结果 |
|---|------|------|------|
| 5 | 释放动画期间尝试新拖拽 | 当前动画被打断，立即进入新拖拽 | ☐ |
| 6 | 释放动画期间外部同步（其他设备修改） | displayItems 不被覆盖，动画不被打断 | ☐ |
| 7 | 释放动画期间 `items.size` 变化（删除/新增） | 动画结束后下次 `LaunchedEffect` 正确重置 | ☐ |
| 8 | Activity 旋转/配置变更 | 释放动画结束后状态正确重置 | ☐ |

- [ ] **Step 3: Logcat 验证**

执行命令：`adb logcat -s ReorderableLazyColumn:D`

进行拖拽交换并松手，确认以下打点按预期顺序输出：

```
[RELEASE_START] offset=<X> idx=<Y>
[RELEASE_OFFSET_SNAP] from=<X> to=<X>
... (250ms 期间，无 ITEM 更新打点)
[RELEASE_END] isDragActive=false
```

**预期时间间隔**：RELEASE_START 到 RELEASE_END 约 250ms ± 16ms。

- [ ] **Step 4: 6月28日修复回归验证**

| # | 场景 | 预期 | 结果 |
|---|------|------|------|
| 9 | 80px 小卡片拖到 200px 大卡片 | 无震荡 | ☐ |
| 10 | 200px 大卡片拖到 80px 小卡片 | 无震荡 | ☐ |
| 11 | 快速连续拖拽过多个不同高度卡片 | 每个位置交换稳定 | ☐ |

---

## Task 11: 最终提交

- [ ] **Step 1: 确认所有变更已提交**

```bash
git status
```

**预期输出**：`nothing to commit, working tree clean`

- [ ] **Step 2: 询问用户是否需要 git 提交**

按照工作区 git 提交规则，**询问用户**是否需要 git 提交。

如果用户需要提交：

```bash
git log --oneline -5  # 查看最近提交风格
# 智能生成中文提交信息
git commit --allow-empty -m "fix(todo): 拖拽松手后视觉跳跃 Bug 修复（汇总提交）"
```

---

## 验证指标

| 指标 | 目标 | 测量方式 |
|------|------|---------|
| 动画时长 | 250ms ± 16ms | Logcat 时间戳 |
| 位置准确性 | A 释放结束后视觉位置 = `displayItems[新 index].offset + draggedSize/2` | 手动观察 |
| 无二次动画 | B/C/D/E 释放后位置不变化 | 手动观察 + Logcat 无 `DISPLAY_ITEMS_REFRESH` 触发 |
| 6月28日修复不回归 | 高度差场景无震荡 | 手动验证 |

---

## 后续可优化点

1. **可单元测试的纯函数更多**：可考虑把 `displayItems` 交换逻辑也提取为纯函数
2. **释放动画可配置**：当前 250ms 硬编码，可通过参数传入
3. **Logcat 打点分级**：未来可考虑分级（Release 用 `Log.i`，Tick 用 `Log.v`）
4. **Compose UI 测试**：可在 `androidTest` 中增加 UI 测试，验证视觉位置正确
5. **触觉反馈优化**：释放动画期间是否需要震动？目前是松手瞬间震动一次
