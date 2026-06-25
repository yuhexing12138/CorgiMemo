# SwipeableTodoBox Compose 集成实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建 `SwipeableTodoBox.kt` 独立包装器组件，将 Web 原型的级联重叠堆叠左滑动效移植到 Compose，并集成到 HomeScreen 的待办卡片中。

**Architecture:** 自定义 `Layout` 实现双层叠加（内容层 z-index=10 + 操作层 z-index=1），`Animatable<Float>` 驱动 `cardOffsetX` 跟手（snapTo）与吸附（animateTo），revealProgress 连续函数计算 3 按钮的 transform/opacity，opacity 二元化避免淡入淡出。按钮顺序 分享→置顶→删除，z-index 倒置 3→2→1。

**Tech Stack:** Jetpack Compose + Compose BOM 2026.04.01 + Compose 1.9.2 + Material Icons Extended

**Spec:** [2026-06-25-swipeable-todo-box-compose-integration-design.md](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-25-swipeable-todo-box-compose-integration-design.md)

---

## 文件结构

| 文件 | 状态 | 责任 |
|------|------|------|
| `app/src/main/res/values/colors_ui.xml` | **修改** | 新增 `ui_swipe_share`(#FFD54F)、`ui_swipe_delete`(#FF5252) |
| `app/src/main/res/values-night/colors_ui.xml` | **修改** | 新增夜间模式对应色值 |
| `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt` | **新建** | 左滑包装器组件核心实现 |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | **修改** | 用 SwipeableTodoBox 包裹 TodoListItem + 新增 swipeExpandedTodoId |
| `app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt` | **新建** | 单元测试 |
| `app/build.gradle.kts` | **修改** | 添加 Compose UI 测试依赖（如需） |

---

## Task 1: 新增颜色定义

**Files:**
- Modify: `app/src/main/res/values/colors_ui.xml`
- Modify: `app/src/main/res/values-night/colors_ui.xml`

- [ ] **Step 1: 在 values/colors_ui.xml 末尾新增两个颜色**

在 `</resources>` 标签前添加：

```xml
<!-- 左滑操作按钮配色（与 Web 原型一致） -->
<color name="ui_swipe_share">#FFD54F</color>   <!-- 分享：黄色 -->
<color name="ui_swipe_delete">#FF5252</color>  <!-- 删除：正红色 -->
```

- [ ] **Step 2: 在 values-night/colors_ui.xml 末尾新增夜间色值**

在 `</resources>` 标签前添加：

```xml
<!-- 左滑操作按钮配色（夜间模式） -->
<color name="ui_swipe_share">#FFC107</color>   <!-- 分享：深黄 -->
<color name="ui_swipe_delete">#D32F2F</color>  <!-- 删除：深红 -->
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/res/values/colors_ui.xml app/src/main/res/values-night/colors_ui.xml
git commit -m "feat(swipe): 新增左滑操作按钮颜色定义（黄/红）"
```

---

## Task 2: 创建 SwipeableTodoBox.kt 核心组件

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt`

- [ ] **Step 1: 创建文件骨架与 import**

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.PointerInputScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Constraints
import androidx.compose.foundation.layout.Layout
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.corgimemo.app.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
```

- [ ] **Step 2: 定义弹性缓动常量与数据类**

在 import 之后添加：

```kotlin
/**
 * 弹性缓动函数（对应 Web 原型 cubic-bezier(0.34, 1.56, 0.64, 1)）
 * 使用 easeOutBack 数学模型实现回弹效果
 */
val ElasticOutEasing: Easing = Easing { fraction ->
    val c1 = 1.56f
    val c3 = c1 + 1f
    1f + c3 * Math.pow(fraction - 1.0, 3.0).toFloat() +
        c1 * Math.pow(fraction - 1.0, 2.0).toFloat()
}

/**
 * 左滑操作按钮配置
 *
 * @param label 按钮文字
 * @param backgroundColorRes 背景色资源 ID
 * @param icon Material 图标
 * @param zIndex z-index 值（从左到右递减：分享=3, 置顶=2, 删除=1）
 */
private data class SwipeButtonConfig(
    val label: String,
    val backgroundColorRes: Int,
    val icon: ImageVector,
    val zIndex: Float
)
```

- [ ] **Step 3: 实现 SwipeableTodoBox 主函数**

```kotlin
/**
 * 可左滑展开操作区的容器组件（飞书风格级联重叠堆叠动效）
 *
 * 将待办卡片作为 content 传入，自动获得左滑操作能力。
 * 按钮顺序：分享 → 置顶 → 删除（从左到右）
 * 动画参数：duration=300ms, staggerRatio=0, thresholdRatio=0.20, ElasticOutEasing
 *
 * @param modifier 修饰符
 * @param isEnabled 是否启用左滑
 * @param isExpanded 是否处于展开状态（父组件控制互斥）
 * @param onExpandChange 展开状态变化回调
 * @param onShareClick 分享按钮回调
 * @param onPinClick 置顶按钮回调
 * @param onDeleteClick 删除按钮回调
 * @param durationMs 动画时长（默认 300ms）
 * @param staggerRatio 级联延迟比例（默认 0.00，同步移动）
 * @param thresholdRatio 吸附比例（默认 0.20）
 * @param easing 缓动函数（默认弹性效果）
 * @param content 卡片内容
 */
@Composable
fun SwipeableTodoBox(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    onShareClick: () -> Unit = {},
    onPinClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    durationMs: Int = 300,
    staggerRatio: Float = 0.00f,
    thresholdRatio: Float = 0.20f,
    easing: Easing = ElasticOutEasing,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // 几何参数
    val buttonWidthDp = 72.dp
    val actionsWidthDp = buttonWidthDp * 3 // 3 个按钮 = 216dp
    val buttonWidthPx = with(density) { buttonWidthDp.toPx() }
    val actionsWidthPx = with(density) { actionsWidthDp.toPx() }
    val thresholdPx = actionsWidthPx * thresholdRatio

    // 卡片位移状态（px，范围 -actionsWidthPx..0）
    val cardOffsetX = remember { Animatable(0f) }

    // 按钮配置（顺序固定：分享→置顶→删除）
    val buttons = remember {
        listOf(
            SwipeButtonConfig("分享", R.color.ui_swipe_share, Icons.Outlined.Share, 3f),
            SwipeButtonConfig("置顶", R.color.ui_primary, Icons.Outlined.PushPin, 2f),
            SwipeButtonConfig("删除", R.color.ui_swipe_delete, Icons.Outlined.Delete, 1f)
        )
    }

    // 父组件强制收起时同步动画
    LaunchedEffect(isExpanded, isEnabled) {
        if (!isExpanded && cardOffsetX.value < 0f && isEnabled) {
            cardOffsetX.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = durationMs, easing = easing)
            )
        }
    }

    // revealProgress 连续函数（与 Web 原型 1:1 对齐）
    val revealPx = (-cardOffsetX.value).coerceIn(0f, actionsWidthPx)
    val revealProgress = if (actionsWidthPx > 0f) revealPx / actionsWidthPx else 0f

    SwipeableTodoBoxLayout(
        modifier = modifier.clip(RoundedCornerShape(16.dp)),
        cardOffsetX = cardOffsetX.value,
        actionsWidthPx = actionsWidthPx,
        isEnabled = isEnabled,
        buttons = buttons,
        revealPx = revealPx,
        revealProgress = revealProgress,
        staggerRatio = staggerRatio,
        buttonWidthPx = buttonWidthPx,
        onShareClick = {
            onShareClick()
            closeSwipe(coroutineScope, cardOffsetX, onExpandChange, durationMs, easing)
        },
        onPinClick = {
            onPinClick()
            closeSwipe(coroutineScope, cardOffsetX, onExpandChange, durationMs, easing)
        },
        onDeleteClick = {
            onDeleteClick()
            closeSwipe(coroutineScope, cardOffsetX, onExpandChange, durationMs, easing)
        },
        onDragStart = { },
        onDragEnd = {
            val currentReveal = -cardOffsetX.value
            val target = if (currentReveal >= thresholdPx) -actionsWidthPx else 0f
            onExpandChange(target < 0f)
            coroutineScope.launch {
                cardOffsetX.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = durationMs, easing = easing)
                )
            }
        },
        onDrag = { dragAmount ->
            coroutineScope.launch {
                val newOffset = (cardOffsetX.value + dragAmount)
                    .coerceIn(-actionsWidthPx, 0f)
                cardOffsetX.snapTo(newOffset)
            }
        },
        content = content
    )
}

/**
 * 收起左滑操作层
 */
private fun closeSwipe(
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    cardOffsetX: Animatable<Float>,
    onExpandChange: (Boolean) -> Unit,
    durationMs: Int,
    easing: Easing
) {
    onExpandChange(false)
    coroutineScope.launch {
        cardOffsetX.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = durationMs, easing = easing)
        )
    }
}
```

- [ ] **Step 4: 实现自定义 Layout**

```kotlin
/**
 * 双层叠加 Layout：内容层(z=10) + 操作层(z=1)
 *
 * 内容层通过 offset 跟随 cardOffsetX 左滑
 * 操作层固定右侧，3 按钮按 revealProgress 级联展开
 */
@Composable
private fun SwipeableTodoBoxLayout(
    modifier: Modifier,
    cardOffsetX: Float,
    actionsWidthPx: Float,
    isEnabled: Boolean,
    buttons: List<SwipeButtonConfig>,
    revealPx: Float,
    revealProgress: Float,
    staggerRatio: Float,
    buttonWidthPx: Float,
    onShareClick: () -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onDrag: (Float) -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current

    Layout(
        content = {
            // 内容层（measurables[0]）
            Box(
                modifier = Modifier
                    .pointerInput(isEnabled) {
                        if (!isEnabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragStart = { onDragStart() },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        ) { _, dragAmount ->
                            onDrag(dragAmount)
                        }
                    }
                    .offset { IntOffset(cardOffsetX.roundToInt(), 0) }
                    .zIndex(10f)
            ) {
                content()
            }
            // 操作层（measurables[1]）
            if (isEnabled) {
                Row(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    buttons.forEachIndexed { index, btnConfig ->
                        // 级联算法：计算本地进度
                        val localStart = index * staggerRatio
                        val denom = 1f - localStart
                        val localProgress = if (denom > 0f) {
                            ((revealProgress - localStart) / denom).coerceIn(0f, 1f)
                        } else {
                            if (revealProgress >= localStart) 1f else 0f
                        }
                        // 偏移量：初始堆叠在 Delete 槽位 → 终态回到原始位置
                        val offset = (buttons.size - 1 - index) * buttonWidthPx
                        val translateX = offset * (1f - localProgress)
                        // opacity 二元化：无淡入淡出
                        val alpha = if (revealPx > 0f) 1f else 0f

                        // 点击回调
                        val clickAction = when (btnConfig.label) {
                            "分享" -> onShareClick
                            "置顶" -> onPinClick
                            "删除" -> onDeleteClick
                            else -> ({})
                        }

                        SwipeActionButton(
                            config = btnConfig,
                            translateX = translateX,
                            alpha = alpha,
                            onClick = clickAction,
                            modifier = Modifier.zIndex(btnConfig.zIndex)
                        )
                    }
                }
            }
        },
        measurePolicy = { measurables, constraints ->
            val contentPlaceable = measurables[0].measure(constraints)
            val cardWidth = contentPlaceable.width
            val cardHeight = contentPlaceable.height
            val actionsPlaceable = if (measurables.size > 1 && isEnabled) {
                measurables[1].measure(
                    Constraints.fixed(
                        width = with(density) { (72.dp * 3).roundToPx() },
                        height = cardHeight
                    )
                )
            } else null

            layout(cardWidth, cardHeight) {
                contentPlaceable.placeRelative(0, 0)
                actionsPlaceable?.placeRelative(
                    x = cardWidth - actionsPlaceable.width,
                    y = 0
                )
            }
        },
        modifier = modifier
    )
}
```

- [ ] **Step 5: 实现单个操作按钮**

```kotlin
/**
 * 单个左滑操作按钮（图标在上，文字在下，纵向居中）
 *
 * @param config 按钮配置
 * @param translateX 横向偏移量（级联堆叠动画）
 * @param alpha 透明度（二元化）
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun SwipeActionButton(
    config: SwipeButtonConfig,
    translateX: Float,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = colorResource(id = config.backgroundColorRes)

    Box(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight()
            .graphicsLayer {
                this.translationX = translateX
                this.alpha = alpha
            }
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = config.icon,
                contentDescription = config.label,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = config.label,
                color = Color.White,
                fontSize = 12.sp
            )
        }
    }
}
```

- [ ] **Step 6: 修正 import 并补全缺失引用**

检查并确保以下 import 存在（根据 Step 3-5 用到的 API）：

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
```

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt
git commit -m "feat(swipe): 新建 SwipeableTodoBox 左滑包装器组件

- 双层叠加 Layout（内容层 z=10 + 操作层 z=1）
- revealProgress 连续函数驱动按钮 transform/opacity
- opacity 二元化（无淡入淡出）
- 按钮顺序：分享→置顶→删除，z-index 倒置 3→2→1
- 参数：duration=300ms, staggerRatio=0, thresholdRatio=0.20, ElasticOutEasing"
```

---

## Task 3: 添加 Compose UI 测试依赖

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 检查现有测试依赖**

读取 `app/build.gradle.kts`，搜索 `androidTestImplementation` 或 `testImplementation` 中是否已有 `compose:ui-test-junit4`。

- [ ] **Step 2: 添加依赖（如缺失）**

在 `dependencies` 块中添加：

```kotlin
// Compose UI 测试
debugImplementation("androidx.compose.ui:ui-test-manifest")
testImplementation("androidx.compose.ui:ui-test-junit4")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
```

> 注意：由于项目使用 Compose BOM 2026.04.01，不需要指定版本号。如 BOM 不管理 ui-test，则使用 `androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.9.2")`。

- [ ] **Step 3: 提交**

```bash
git add app/build.gradle.kts
git commit -m "chore(test): 添加 Compose UI 测试依赖"
```

---

## Task 4: 创建 SwipeableTodoBoxTest.kt 单元测试

**Files:**
- Create: `app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt`

- [ ] **Step 1: 创建测试文件骨架**

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
```

- [ ] **Step 2: 实现测试用例**

```kotlin
class SwipeableTodoBoxTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun buttons_initiallyHidden() {
        var expanded = false
        composeRule.setContent {
            SwipeableTodoBox(
                isExpanded = expanded,
                onExpandChange = { expanded = it }
            ) {
                androidx.compose.material3.Text("Test Card")
            }
        }
        // 初始状态：按钮不可见（alpha=0）
        composeRule.onNodeWithText("分享").assertDoesNotExist()
        composeRule.onNodeWithText("置顶").assertDoesNotExist()
        composeRule.onNodeWithText("删除").assertDoesNotExist()
    }

    @Test
    fun buttons_visible_whenExpanded() {
        var expanded = true
        composeRule.setContent {
            SwipeableTodoBox(
                isExpanded = expanded,
                onExpandChange = { expanded = it }
            ) {
                androidx.compose.material3.Text("Test Card")
            }
        }
        // 展开后：按钮可见
        composeRule.onNodeWithText("分享").assertIsDisplayed()
        composeRule.onNodeWithText("置顶").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
    }

    @Test
    fun clickShare_triggersCallback() {
        var shareClicked = false
        composeRule.setContent {
            SwipeableTodoBox(
                isExpanded = true,
                onShareClick = { shareClicked = true }
            ) {
                androidx.compose.material3.Text("Test Card")
            }
        }
        composeRule.onNodeWithText("分享").performClick()
        assert(shareClicked) { "分享按钮应触发 onShareClick 回调" }
    }

    @Test
    fun clickDelete_triggersCallback() {
        var deleteClicked = false
        composeRule.setContent {
            SwipeableTodoBox(
                isExpanded = true,
                onDeleteClick = { deleteClicked = true }
            ) {
                androidx.compose.material3.Text("Test Card")
            }
        }
        composeRule.onNodeWithText("删除").performClick()
        assert(deleteClicked) { "删除按钮应触发 onDeleteClick 回调" }
    }

    @Test
    fun clickPin_triggersCallback() {
        var pinClicked = false
        composeRule.setContent {
            SwipeableTodoBox(
                isExpanded = true,
                onPinClick = { pinClicked = true }
            ) {
                androidx.compose.material3.Text("Test Card")
            }
        }
        composeRule.onNodeWithText("置顶").performClick()
        assert(pinClicked) { "置顶按钮应触发 onPinClick 回调" }
    }

    @Test
    fun disabled_swipeDoesNotTrigger() {
        var expanded = false
        composeRule.setContent {
            SwipeableTodoBox(
                isEnabled = false,
                isExpanded = expanded,
                onExpandChange = { expanded = it }
            ) {
                androidx.compose.material3.Text("Test Card")
            }
        }
        // 禁用状态下按钮不渲染
        composeRule.onNodeWithText("分享").assertDoesNotExist()
    }
}
```

- [ ] **Step 3: 提交**

```bash
git add app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt
git commit -m "test(swipe): 新增 SwipeableTodoBox 单元测试（6 用例）"
```

---

## Task 5: 在 HomeScreen.kt 中集成

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 添加 import**

在 HomeScreen.kt import 区域添加：

```kotlin
import com.corgimemo.app.ui.components.SwipeableTodoBox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
```

> 注意：检查 `mutableStateOf`、`getValue`、`setValue` 是否已 import，避免重复。

- [ ] **Step 2: 新增 swipeExpandedTodoId 状态**

在第 190 行 `pendingDeleteId` 定义附近添加：

```kotlin
// 左滑互斥展开状态：同时只允许一张卡片展开
var swipeExpandedTodoId by remember { mutableStateOf<Long?>(null) }
```

- [ ] **Step 3: 用 SwipeableTodoBox 包裹 TodoListItem**

将第 558-601 行的 `TodoListItem(...)` 调用用 `SwipeableTodoBox` 包裹：

```kotlin
SwipeableTodoBox(
    modifier = Modifier.testTag("swipeableTodoBox_${todo.id}"),
    isEnabled = !isBatchMode,
    isExpanded = swipeExpandedTodoId == todo.id,
    onExpandChange = { expanded ->
        swipeExpandedTodoId = if (expanded) todo.id else null
    },
    onShareClick = {
        shareTodoAsImage(context, todo, categories)
    },
    onPinClick = {
        // 置顶功能后端待实现，暂留空
    },
    onDeleteClick = {
        pendingDeleteId = todo.id
    }
) {
    TodoListItem(
        todo = todo,
        subTaskProgress = subTaskProgressMap[todo.id],
        subTasks = subTasksMap[todo.id] ?: emptyList(),
        isExpanded = expandedTodos.contains(todo.id),
        isBatchMode = isBatchMode,
        isSelected = selectedTodoIds.contains(todo.id),
        categoryName = category?.name,
        categoryIcon = categoryIcon,
        onToggleComplete = { id, isChecked ->
            viewModel.onUserInteraction()
            viewModel.toggleTodoStatus(id, isChecked)
        },
        onDelete = {
            viewModel.onUserInteraction()
            pendingDeleteId = it
        },
        onClick = {
            viewModel.onUserInteraction()
            navController.navigate(Screen.TodoEditWithId.withArgs(todo.id.toString()))
        },
        onLongClick = {
            viewModel.enterBatchMode(todo.id)
        },
        onSelectClick = {
            viewModel.toggleSelection(todo.id)
        },
        onShareAsImage = {
            shareTodoAsImage(context, todo, categories)
        },
        onToggleExpand = {
            viewModel.toggleExpand(todo.id)
        },
        onToggleSubTask = { subTaskId ->
            viewModel.onUserInteraction()
            viewModel.toggleSubTaskCompletion(subTaskId)
        },
        relationHint = null,
        searchQuery = searchQuery
    )
}
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(home): 集成 SwipeableTodoBox 到待办卡片列表

- 用 SwipeableTodoBox 包裹 TodoListItem
- 新增 swipeExpandedTodoId 互斥展开状态
- 左滑分享/删除回调接入现有逻辑
- 置顶回调暂留空（后端待实现）"
```

---

## Task 6: 检查 import 完整性

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt`
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 检查 SwipeableTodoBox.kt 的 import**

确保以下 import 全部存在：

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Constraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.corgimemo.app.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
```

- [ ] **Step 2: 检查 HomeScreen.kt 新增的 import**

确保 `SwipeableTodoBox` 和 `remember`/`mutableStateOf`/`getValue`/`setValue` 已 import。

- [ ] **Step 3: 修复缺失的 import 并提交**

```bash
git add -A
git commit -m "fix(swipe): 补全缺失的 import 语句"
```

---

## Task 7: 编译验证与手动测试

**Files:**
- 无

- [ ] **Step 1: 询问用户是否进行编译验证**

> ⚠️ 根据项目规则，编译验证前必须询问用户。

- [ ] **Step 2: 执行编译**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **Step 3: 执行单元测试**

运行：`./gradlew test`
预期：所有测试通过

- [ ] **Step 4: 手动测试验收**

在真机/模拟器上验证 AC-1 ~ AC-16：
1. 未滑动时 3 按钮不可见
2. 左滑触发交互，竖滑不触发
3. 滑动 < 20% 松手回弹
4. 滑动 ≥ 20% 松手吸附展开
5. 按钮顺序 分享→置顶→删除
6. 点击按钮执行回调后收起
7. 同时只有一张卡片展开
8. 批量模式下左滑禁用

---

## 后续优化（不在本次范围）

- 置顶功能后端实现（TodoItem.isPinned 字段 + Migration + DAO + Repository + ViewModel + 排序逻辑）
- 多选模式下批量操作
- 振动反馈（HapticFeedback）
- 按钮长按触发二次确认
- 卡片右滑出现不同操作
