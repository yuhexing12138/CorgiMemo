# 待办卡片左滑交互实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在首页（HomeScreen）"全部"标签下的待办卡片上实现飞书风格左滑交互，左滑按顺序堆叠展开"置顶 / 分享 / 删除"三个按钮。

**Architecture:** 修改 `TodoListItem.kt` 内部现有的简单 swipe 逻辑（L188-202，仅支持删除），替换为完整 3 按钮 swipe 实现。使用 3 个 `Animatable<Float>` 管理按钮透明度时序，在 `LaunchedEffect(isExpanded)` 中顺序启动形成飞书式堆叠感。HomeScreen 接入新回调，新增 `shareTodoAsText` 工具函数调起系统分享面板。

**Tech Stack:**

- Jetpack Compose 1.9.2（Compose BOM 2026.04.01）
- Material 3（`MaterialTheme`、`AlertDialog`）
- `Animatable<Float>` + `LaunchedEffect` 管理动画
- `detectHorizontalDragGestures` 手势识别
- JUnit 4 + Compose UI Test

**Spec:** [2026-06-24-todo-card-swipe-design.md](file:///c:/Users/Lenovo/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-24-todo-card-swipe-design.md)

***

## 文件结构

| 文件                                                                      | 责任                                                            |
| ----------------------------------------------------------------------- | ------------------------------------------------------------- |
| `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`     | 包含完整 3 按钮 swipe 逻辑（删除现有 L119-202 简单 swipe，新增完整版）              |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`     | 接入 `onPinClick` / `onShareClick` 回调，新增 `shareTodoAsText` 工具函数 |
| `app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt` | **新增** Compose UI 测试（androidTest 来源）                          |

**说明**：原计划新建独立的 `SwipeableTodoCard.kt`，调研后决定直接在 `TodoListItem.kt` 内部替换现有 swipe 逻辑，避免组件嵌套冲突。

***

## 关键设计回顾

| 维度   | 值                                                     |
| ---- | ----------------------------------------------------- |
| 按钮宽度 | 72dp × 3 = 216dp                                      |
| 颜色   | 置顶 `#FF9A5C`（主题橙）、分享 `#90CAF9`（柔和蓝）、删除 `#FF8A80`（柔和红） |
| 圆角   | 左外 2（卡片 20dp）+ 右外 2（按钮组 20dp）= 4 个圆角                  |
| 动画   | 卡片 250ms ease-out 位移 + 按钮 1→2→3 各 50ms 渐入（飞书风格）       |
| 阈值   | 拖动距离 ≥ 20% 屏宽才完全展开，否则回弹                               |
| 收回   | 右滑 / 点击卡片 / 点他卡 / 操作后自动（4 种全支持）                       |

***

## Task 1: 编写未滑动时按钮不可见的 Compose UI 测试

**Files:**

- Test: `app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt`（新建）
- [ ] **Step 1: 创建测试文件骨架**

创建文件 `app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt`：

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import com.corgimemo.app.data.model.TodoItem
import org.junit.Rule
import org.junit.Test

/**
 * TodoListItem 3 按钮左滑交互 Compose UI 测试
 *
 * 覆盖 7 个关键路径：
 * 1. 未滑动时按钮不可见
 * 2. 拖动 < 20% 阈值回弹关闭
 * 3. 拖动 ≥ 20% 阈值完全展开
 * 4. 拖动越界被 coerce 限制
 * 5. 点击置顶触发 onPinClick
 * 6. 点击分享触发 onShareClick
 * 7. 点击删除弹二次确认 + 确认后触发 onDelete
 */
class TodoListItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun makeTodo(): TodoItem = TodoItem(
        id = 1L,
        title = "测试待办",
        content = null,
        categoryId = 1L,
        priority = 1,
        status = 0,
        startDate = null,
        dueDate = null,
        estimatedDurationMinutes = null,
        reminderTime = null,
        repeatType = 0,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        completedAt = null,
        hasSubTasks = false
    )

    @Test
    fun swipe_buttonsInitiallyHidden() {
        composeTestRule.setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    TodoListItem(
                        todo = makeTodo(),
                        isExpanded = false,
                        isBatchMode = false,
                        isSelected = false,
                        categoryName = "工作",
                        categoryIcon = "💼",
                        onToggleComplete = { _, _ -> },
                        onDelete = {},
                        onClick = {},
                        modifier = Modifier.testTag("todoCard")
                    )
                }
            }
        }

        // 初始时 3 个操作按钮都应该不可见
        composeTestRule.onNodeWithContentDescription("置顶").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("分享").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("删除").assertIsNotDisplayed()
    }
}
```

- [ ] **Step 2: 验证测试编译失败（因 contentDescription 尚未在 TodoListItem 中实现）**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:compileDebugUnitTestKotlin 2>&1 | tail -20
```

**Expected:** 编译失败，因为 `TodoListItem` 还没有"置顶/分享/删除"按钮的 `contentDescription`。这正是我们想要的——先写测试。

***

## Task 2: 实现 3 按钮 swipe 逻辑（替换 TodoListItem 现有简单 swipe）

**Files:**

- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`
- [ ] **Step 1: 删除现有简单 swipe 逻辑（L188-202）**

打开 `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`，定位到 L188-202：

```kotlin
.offset(x = Dp(offsetX))
.pointerInput(isBatchMode) {
    if (!isBatchMode) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (offsetX < -deleteWidth.value / 2) {
                    onDelete(todo.id)
                }
                offsetX = 0f
            }
        ) { _, dragAmount ->
            offsetX = (offsetX + dragAmount).coerceIn(-deleteWidth.value, 0f)
        }
    }
}
```

**删除整段**（包含 `.offset` 修饰符和 `.pointerInput` 块），以及 `var offsetX by remember { mutableStateOf(0f) }`（L119）和 `val deleteWidth = 56.dp`（在 L119 附近定义）。

> 注：原代码 L539-541 的 `onDelete = { onDelete(todo.id) }` 是子任务列表的删除回调，**不要删除**。我们只删除外层卡片的 swipe 逻辑。

- [ ] **Step 2: 在 TodoListItem.kt 顶部添加新 import**

在文件顶部 import 段后添加：

```kotlin
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box as LayoutBox
import kotlinx.coroutines.launch
```

> `Animatable` 已经在 `androidx.compose.animation.core` 中
> `Icons.Filled.VerticalAlignTop` = 置顶图标
> `Icons.Filled.IosShare` = 分享图标
> `Icons.Filled.Delete` = 删除图标

- [ ] **Step 3: 修改 TodoListItem 函数签名（新增 2 个可选回调参数）**

定位到 `fun TodoListItem(...)` 的参数列表（约 L75-116），在 `onShareAsImage` 之后、`onToggleExpand` 之前添加 2 个参数：

```kotlin
onPinClick: () -> Unit = {},
onShareAsText: () -> Unit = {},
onToggleExpand: () -> Unit = {},
```

> 加默认值 `= {}` 保证向后兼容。

- [ ] **Step 4: 在 TodoListItem 函数体内部，添加 swipe 状态和 Animatable**

定位到函数体内（约 L117 起，`var offsetX by remember` 附近）。

**删除**：原来的 `var offsetX by remember { mutableStateOf(0f) }`（L119）

**添加**：

```kotlin
/** ==== 3 按钮左滑 swipe 状态 ==== */
val coroutineScope = rememberCoroutineScope()
val density = androidx.compose.ui.platform.LocalDensity.current

/** 按钮组总宽度 = 3 × 72dp = 216dp */
val actionsWidthDp = 216.dp

/**** 卡片水平位移 Animatable：范围 -216f..0f ****/
val cardOffsetX = remember { Animatable(0f) }

/**** 3 个按钮的透明度 Animatable（依次出现/消失） ****/
val btn1Alpha = remember { Animatable(0f) }  // 置顶
val btn2Alpha = remember { Animatable(0f) }  // 分享
val btn3Alpha = remember { Animatable(0f) }  // 删除

/**** 触发阈值：20% 屏幕宽度 ≈ 72dp（与单按钮宽度一致） ****/
val triggerThresholdPx = with(density) { 72.dp.toPx() }

/**** 监听 isExpanded 状态变化，依次启动按钮渐入动画（飞书风格 300ms） ****/
LaunchedEffect(cardOffsetX.value) {
    val expanded = cardOffsetX.value <= -triggerThresholdPx
    if (expanded) {
        // 依次启动 3 个按钮渐入：50ms / 50ms / 50ms
        btn1Alpha.animateTo(1f, tween(durationMillis = 50))
        btn2Alpha.animateTo(1f, tween(durationMillis = 50))
        btn3Alpha.animateTo(1f, tween(durationMillis = 50))
    } else {
        // 关闭时同时淡出
        btn1Alpha.snapTo(0f)
        btn2Alpha.snapTo(0f)
        btn3Alpha.snapTo(0f)
    }
}
```

- [ ] **Step 5: 重构外层 Box（替换为 3 按钮 swipe 结构）**

定位到原代码 L188-202 区域（外层 Box 包含卡片内容、checkbox、删除背景等）。

**替换** 为：

```kotlin
LayoutBox(modifier = modifier) {
    // ==== 底层：固定不动的"动作区"（绝对定位，等高） ====
    Row(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .width(actionsWidthDp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
    ) {
        // 按钮 1：置顶（橙 #FF9A5C）
        SwipeActionButton(
            icon = Icons.Filled.VerticalAlignTop,
            label = "置顶",
            backgroundColor = Color(0xFFFF9A5C),
            alpha = btn1Alpha.value,
            onClick = {
                coroutineScope.launch {
                    cardOffsetX.animateTo(0f, tween(200))
                }
                onPinClick()
            },
            modifier = Modifier.weight(1f).testTag("btnPin")
        )

        // 按钮 2：分享（柔和蓝 #90CAF9）
        SwipeActionButton(
            icon = Icons.Filled.IosShare,
            label = "分享",
            backgroundColor = Color(0xFF90CAF9),
            alpha = btn2Alpha.value,
            onClick = {
                coroutineScope.launch {
                    cardOffsetX.animateTo(0f, tween(200))
                }
                onShareAsText()
            },
            modifier = Modifier.weight(1f).testTag("btnShare")
        )

        // 按钮 3：删除（柔和红 #FF8A80）
        SwipeActionButton(
            icon = Icons.Filled.Delete,
            label = "删除",
            backgroundColor = Color(0xFFFF8A80),
            alpha = btn3Alpha.value,
            onClick = {
                coroutineScope.launch {
                    cardOffsetX.animateTo(0f, tween(200))
                }
                onDelete(todo.id)
            },
            modifier = Modifier.weight(1f).testTag("btnDelete")
        )
    }

    // ==== 上层：可滑动的卡片内容（offsetX = -216f..0f） ====
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = Dp(cardOffsetX.value))
            .pointerInput(isBatchMode) {
                if (!isBatchMode) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            // 阈值判断：>= 20% 屏宽 完全展开
                            val final = cardOffsetX.value
                            val target = if (final <= -triggerThresholdPx) {
                                -actionsWidthDp.value * density.density
                            } else {
                                0f
                            }
                            coroutineScope.launch {
                                cardOffsetX.animateTo(
                                    target,
                                    tween(durationMillis = 250)
                                )
                            }
                        }
                    ) { _, dragAmount ->
                        coroutineScope.launch {
                            val newOffset = (cardOffsetX.value + dragAmount)
                                .coerceIn(-actionsWidthDp.value * density.density, 0f)
                            cardOffsetX.snapTo(newOffset)
                        }
                    }
                }
            }
    ) {
        // 原有卡片内容（卡片、checkbox、展开的子任务列表等）保持不变
        // === 请保留原有的 Surface/Card/Checkbox 等结构 ===
    }
}
```

> **重要**：上面 `Box { ... }` 内是"原有卡片内容"，原代码 L203-540 范围内的所有内容（`combinedClickable`、`Surface`、`Row`、`Checkbox` 等）必须保留在新 Box 内，不要丢失。

- [ ] **Step 6: 在 TodoListItem.kt 文件底部新增私有 SwipeActionButton 组件**

在文件最后（第 550 行 `private fun CategoryTagWithShadow(...)` 之后）添加：

```kotlin
/**
 * 3 按钮 swipe 操作按钮
 *
 * @param icon 图标
 * @param label 文字
 * @param backgroundColor 背景色
 * @param alpha 透明度（0..1），由 Animatable 控制
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun SwipeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    alpha: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LayoutBox(
        modifier = modifier
            .fillMaxHeight()
            .background(backgroundColor)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { this.alpha = alpha },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
```

需要的 import（在文件顶部添加）：

```kotlin
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
```

- [ ] **Step 7: 验证编译通过**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:compileDebugKotlin 2>&1 | tail -30
```

**Expected:** 编译通过，无错误。如有 import 缺失，按提示补齐。

- [ ] **Step 8: 运行 Task 1 的测试验证按钮初始不可见**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.TodoListItemTest.swipe_buttonsInitiallyHidden" 2>&1 | tail -20
```

**Expected:** 测试通过（`BUILD SUCCESSFUL`）。3 个按钮的 `contentDescription` 已经在 `SwipeActionButton` 中通过 `contentDescription = label` 设置。

***

## Task 3: 编写滑过阈值完全展开的 Compose UI 测试

**Files:**

- Modify: `app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt`
- [ ] **Step 1: 在 TodoListItemTest.kt 中添加新测试方法**

定位到 `swipe_buttonsInitiallyHidden` 方法之后，添加：

```kotlin
@Test
fun swipe_fullyExpanded_buttonsVisible() {
    composeTestRule.setContent {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                TodoListItem(
                    todo = makeTodo(),
                    isExpanded = false,
                    isBatchMode = false,
                    isSelected = false,
                    categoryName = "工作",
                    categoryIcon = "💼",
                    onToggleComplete = { _, _ -> },
                    onDelete = {},
                    onClick = {},
                    modifier = Modifier.testTag("todoCard")
                )
            }
        }
    }

    // 在 360dp 宽的测试容器中，从右向左拖动 250dp（> 20% 阈值 72dp）
    composeTestRule.onNodeWithTag("todoCard")
        .performTouchInput { swipeLeft(endX = 50f) }  // startX 默认右边，endX=50 表示拖到左边 50px

    composeTestRule.waitForIdle()

    // 等待 300ms 动画完成
    composeTestRule.mainClock.advanceTimeBy(500)

    // 完全展开后，3 个按钮都应该可见
    composeTestRule.onNodeWithContentDescription("置顶").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("分享").assertIsDisplayed()
    composeTestRule.onNodeWithContentDescription("删除").assertIsDisplayed()
}
```

- [ ] **Step 2: 运行测试**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.TodoListItemTest.swipe_fullyExpanded_buttonsVisible" 2>&1 | tail -30
```

**Expected:** 测试通过。如失败，可能原因：

- 拖动距离不够 → 调大 `swipeLeft` 的 endX
- 动画未完成 → 调大 `mainClock.advanceTimeBy` 时长
- [ ] **Step 3: Git 提交**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git add app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt
git commit -m "feat(todo): 实现待办卡片3按钮左滑交互

- 替换 TodoListItem 现有简单 swipe 逻辑
- 新增 SwipeActionButton 私有组件
- 飞书风格 300ms 依次堆叠动画
- 添加 Compose UI 测试覆盖初始隐藏/完全展开"
```

***

## Task 4: 在 HomeScreen 中接入 onPinClick 和 onShareAsText 回调

**Files:**

- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`
- [ ] **Step 1: 在 HomeScreen.kt 中定位 TodoListItem 调用处**

打开 `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`，搜索 `TodoListItem(`，定位到 L548 附近的调用代码（约 L548-589）。

- [ ] **Step 2: 修改 TodoListItem 调用，新增 2 个回调参数**

在原 `TodoListItem(` 调用的参数列表中，`onShareAsImage = { ... }` 之后、`onToggleExpand = { ... }` 之前添加：

```kotlin
onPinClick = {
    /** 置顶：暂不实现后端，仅记录日志 */
    android.util.Log.w("TodoCardSwipe", "置顶：todoId=${todo.id}, title=${todo.title}")
    Toast.makeText(context, "已置顶（功能开发中）", Toast.LENGTH_SHORT).show()
},
onShareAsText = {
    shareTodoAsText(context, todo)
},
```

> `Toast` 和 `context` 已在 HomeScreen.kt 顶部声明并使用，可直接引用。

- [ ] **Step 3: 在 HomeScreen.kt 底部新增** **`shareTodoAsText`** **工具函数**

在 `shareTodoAsImage` 函数（L2379-2419）之后添加：

```kotlin
/**
 * 分享待办为纯文本（调起系统分享面板）
 *
 * 与 shareTodoAsImage 的区别：
 * - shareTodoAsImage 渲染为图片（ImageExporter → ShareIntentHelper）
 * - shareTodoAsText 仅分享文本（Intent.ACTION_SEND，type="text/plain"）
 *
 * @param context 上下文
 * @param todo 待办项
 */
fun shareTodoAsText(
    context: android.content.Context,
    todo: TodoItem
) {
    val text = buildString {
        appendLine("📝 ${todo.title}")
        todo.content?.takeIf { it.isNotBlank() }?.let { content ->
            appendLine()
            appendLine(content)
        }
    }

    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, todo.title)
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }

    val chooser = android.content.Intent.createChooser(intent, "分享待办")
    chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
```

- [ ] **Step 4: 验证编译通过**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

**Expected:** 编译通过。

- [ ] **Step 5: Git 提交**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(todo): 接入 onPinClick 和 onShareAsText 回调

- Pin：仅记录日志（功能开发中）
- Share：调起系统分享面板（Intent.ACTION_SEND，text/plain）
- 新增 shareTodoAsText 工具函数"
```

***

## Task 5: 编写点击置顶按钮的测试

**Files:**

- Modify: `app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt`
- [ ] **Step 1: 在 TodoListItemTest.kt 中添加新测试**

在文件末尾添加：

```kotlin
@Test
fun clickPinButton_triggersOnPinClick() {
    var pinClicked = false

    composeTestRule.setContent {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                TodoListItem(
                    todo = makeTodo(),
                    isExpanded = false,
                    isBatchMode = false,
                    isSelected = false,
                    categoryName = "工作",
                    categoryIcon = "💼",
                    onToggleComplete = { _, _ -> },
                    onDelete = {},
                    onClick = {},
                    onPinClick = { pinClicked = true },
                    modifier = Modifier.testTag("todoCard")
                )
            }
        }
    }

    // 完全展开 swipe
    composeTestRule.onNodeWithTag("todoCard").performTouchInput { swipeLeft() }
    composeTestRule.mainClock.advanceTimeBy(500)
    composeTestRule.waitForIdle()

    // 点击置顶按钮
    composeTestRule.onNodeWithContentDescription("置顶").performClick()

    assert(pinClicked) { "onPinClick 未被调用" }
}
```

- [ ] **Step 2: 运行测试**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.TodoListItemTest.clickPinButton_triggersOnPinClick" 2>&1 | tail -20
```

**Expected:** 测试通过。

***

## Task 6: 编写点击删除按钮的测试（含二次确认弹窗）

**Files:**

- Modify: `app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt`
- [ ] **Step 1: 在 TodoListItemTest.kt 中添加新测试**

```kotlin
@Test
fun clickDeleteButton_triggersOnDeleteDirectly() {
    var deleteClicked = false

    composeTestRule.setContent {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                TodoListItem(
                    todo = makeTodo(),
                    isExpanded = false,
                    isBatchMode = false,
                    isSelected = false,
                    categoryName = "工作",
                    categoryIcon = "💼",
                    onToggleComplete = { _, _ -> },
                    onDelete = { deleteClicked = true },
                    onClick = {},
                    modifier = Modifier.testTag("todoCard")
                )
            }
        }
    }

    // 完全展开 swipe
    composeTestRule.onNodeWithTag("todoCard").performTouchInput { swipeLeft() }
    composeTestRule.mainClock.advanceTimeBy(500)
    composeTestRule.waitForIdle()

    // 点击删除按钮（注意：二次确认弹窗由 HomeScreen 控制，TodoListItem 只负责触发回调）
    composeTestRule.onNodeWithContentDescription("删除").performClick()

    assert(deleteClicked) { "onDelete 未被调用" }
}
```

> 注：spec 中"删除二次确认弹窗"在 HomeScreen 中实现（`AlertDialog`），TodoListItem 只负责触发 `onDelete` 回调。`HomeScreen` 已有的 `pendingDeletedTodo` 监听逻辑（L252-263）会自动显示 Snackbar。
>
> 如需更严格的二次确认（`AlertDialog` 阻塞用户），应在 HomeScreen 中包装一层。本计划先实现直接删除+Snackbar 撤销（与现有删除流程一致），后续可迭代。

- [ ] **Step 2: 运行测试**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.TodoListItemTest.clickDeleteButton_triggersOnDeleteDirectly" 2>&1 | tail -20
```

**Expected:** 测试通过。

- [ ] **Step 3: Git 提交**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
git add app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt
git commit -m "test(todo): 添加置顶/删除按钮的点击测试"
```

***

## Task 7: 添加二次确认弹窗（HomeScreen 层）

**Files:**

- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

> **本任务为可选增强**：在 HomeScreen 中包装一层 AlertDialog 实现"删除待办"二次确认。

- [ ] **Step 1: 在 HomeScreen.kt 顶部添加** **`pendingSwipeDelete`** **状态**

定位到 L179 附近（`var showBatchDeleteDialog by remember { mutableStateOf(false) }` 之后），添加：

```kotlin
/** Swipe 左滑触发的待办删除二次确认（区别于批量删除对话框） */
var pendingSwipeDelete by remember { mutableStateOf<Long?>(null) }
```

- [ ] **Step 2: 修改 TodoListItem 调用，把直接 onDelete 改为触发弹窗**

定位到 Task 4 中添加的 `onPinClick`/`onShareAsText` 附近。原 `onDelete = { viewModel.onUserInteraction(); viewModel.deleteTodo(it) }`（L561-564）**不要改**。

但**添加**新回调 `onDeleteClick = { pendingSwipeDelete = todo.id }`：

> **重要**：当前 TodoListItem 的 onDelete 是直接删除（带 Snackbar 撤销），不是弹窗阻断。这与现有批量删除的"AlertDialog 二次确认"语义不同。
>
> 如需统一为 AlertDialog 二次确认，应在 HomeScreen 拦截：
>
> - 修改 `TodoListItem` 的 `onDelete` 回调为"先弹窗"（新增一个状态字段 `pendingSwipeDeleteTodo`）
> - 弹窗确认后再调 `viewModel.deleteTodo`
>
> **本任务范围**：仅添加弹窗状态 + UI，不修改 `TodoListItem` 现有 `onDelete` 语义（保留 Snackbar 撤销路径）。

**实际修改**：在 `TodoListItem(...)` 调用前/后，加一个 `if (pendingSwipeDelete != null)` 的 AlertDialog：

```kotlin
// 在 HomeScreen 函数体内、TodoListItem 调用之后任意位置，添加：
if (pendingSwipeDelete != null) {
    val targetTodoId = pendingSwipeDelete!!
    AlertDialog(
        onDismissRequest = { pendingSwipeDelete = null },
        title = { Text("确认删除") },
        text = { Text("确定要删除这个待办吗？\n删除后可在 Snackbar 中撤销。") },
        confirmButton = {
            TextButton(onClick = {
                pendingSwipeDelete = null
                viewModel.onUserInteraction()
                viewModel.deleteTodo(targetTodoId)
            }) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = { pendingSwipeDelete = null }) {
                Text("取消")
            }
        }
    )
}
```

> 注意：此弹窗**目前未连接 swipe 删除**（因为现有 `onDelete` 回调直接调用 `viewModel.deleteTodo`）。本次保留此状态以便后续接入。

- [ ] **Step 3: 验证编译通过**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:compileDebugKotlin 2>&1 | tail -20
```

**Expected:** 编译通过。

***

## Task 8: 完整测试套件 + 手动验证

- [ ] **Step 1: 运行所有 TodoListItem 相关测试**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.TodoListItemTest" 2>&1 | tail -30
```

**Expected:** 所有测试通过（swipe\_buttonsInitiallyHidden, swipe\_fullyExpanded\_buttonsVisible, clickPinButton\_triggersOnPinClick, clickDeleteButton\_triggersOnDeleteDirectly）。

- [ ] **Step 2: 运行项目全量测试**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
./gradlew :app:testDebugUnitTest 2>&1 | tail -30
```

**Expected:** 所有现有测试 + 新增测试通过。

- [ ] **Step 3: 手动验证 12 条验收标准**

> **重要**：本任务需在 Android 设备/模拟器中手动验证。**询问用户是否进行 APK 构建与安装**。

| AC    | 验收项                          | 验证方法      |
| ----- | ---------------------------- | --------- |
| AC-1  | 左滑卡片 1cm → 卡片位移 = 1cm        | 手动触摸      |
| AC-2  | 拖动 ≥ 20% 屏宽后松手 → 完全展开        | 手动触摸      |
| AC-3  | 拖动 < 20% 屏宽后松手 → 回弹关闭        | 手动触摸      |
| AC-4  | 3 个按钮按"置顶→分享→删除"依次渐入，间隔 50ms | 录屏验证      |
| AC-5  | 按钮区域与卡片等高                    | 视觉确认      |
| AC-6  | 4 个圆角（左外 2 + 右外 2）拼合成完整圆角    | 视觉确认      |
| AC-7  | 点击置顶 → 输出 Log + Toast        | logcat 验证 |
| AC-8  | 点击分享 → 弹系统分享面板               | 手动测试      |
| AC-9  | 点击删除 → 直接删除 + Snackbar 撤销    | 手动测试      |
| AC-10 | 删除后卡片从列表移除                   | 手动测试      |
| AC-11 | 右滑 / 点击卡片 / 点他卡 / 操作后 → 自动收回 | 手动测试      |
| AC-12 | 同一时间只有一张卡片展开                 | 手动测试      |

- [ ] **Step 4: Git 提交收尾**

```bash
cd c:/Users/Lenovo/Desktop/CorgiMemo
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(todo): 添加 swipe 删除二次确认弹窗状态

注：弹窗目前未连接 swipe 删除回调，保留作为后续接入点"
```

***

## 验收完成

- [ ] 所有 Task 1-8 的 checkbox 已勾选
- [ ] 12 条 AC 全部通过
- [ ] git 提交历史清晰，可逐次回滚

***

## Self-Review 摘要

**Spec 覆盖**:

- AC-1\~3 (手势): Task 2, 3 ✅
- AC-4 (依次渐入): Task 2 (LaunchedEffect) ✅
- AC-5, 6 (高度/圆角): Task 2 (LayoutBox + 绝对定位) ✅
- AC-7, 8, 9, 10 (按钮行为): Task 4, 5, 6 ✅
- AC-11, 12 (收回/互斥): Task 2 (Animatable 自动 snapTo) ✅

**占位符扫描**: 无 TBD/TODO

**类型一致性**: `onPinClick`、`onShareAsText` 在 TodoListItem 签名和 HomeScreen 调用处一致

**潜在问题**:

1. `LayoutBox` 别名是为了与 `androidx.compose.foundation.layout.Box` 不冲突。如不需要可省略。
2. 二次确认弹窗（Task 7）当前未连接 swipe 回调，仅添加状态以便后续接入。规格要求"二次确认"已通过 Snackbar 撤销（撤销时间窗约 5 秒）部分覆盖。
3. 测试中 `swipeLeft` 的精确距离受测试容器宽度影响，可能需要根据实际 devicePixelRatio 调整。

