# 待办卡片左滑交互重构实施计划

> **For agentic workers:** REQUIRED SUB-KILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 `TodoListItem` 内嵌 swipe 实现的 3 个 bug（按钮常驻显示、卡片消失、缺堆叠动画），抽出独立 `SwipeableTodoBox` 包装器组件，实现飞书式 3 按钮堆叠入场效果。

**Architecture:** 新建 `SwipeableTodoBox.kt` 作为独立的包装器组件（自包含手势 + 动画 + 按钮渲染），通过 `Layout` 自定义测量顺序实现"卡片 + 动作区"分层。3 个按钮的入场动画使用 3 组独立 `Animatable<Float>`（alpha/scaleX/translateX 各 3 个），由 `LaunchedEffect(revealProgress)` 监听位移变化并按飞书式 5 阶段时序自动 `animateTo`。`TodoListItem` 删除所有 swipe 相关代码（Layout 块、状态变量、SwipeActionButton 私有函数），`HomeScreen` 用 `SwipeableTodoBox` 包裹 `TodoListItem` 实现集成。

**Tech Stack:**
- Jetpack Compose 1.9.2（Compose BOM 2026.04.01）
- Material 3（`Modifier.graphicsLayer` 控制 alpha/scaleX/translateX）
- `Animatable<Float>` + `LaunchedEffect` 时序管理
- `Layout` + `Constraints.fixed` 自定义测量顺序
- `detectHorizontalDragGestures` 手势识别
- `spring()` / `tween(FastOutSlowInEasing)` 动画曲线
- JUnit 4 + Compose UI Test

**Spec:** [2026-06-25-todo-card-swipe-redesign-design.md](file:///c:/Users/Lenovo/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-25-todo-card-swipe-redesign-design.md)

---

## 文件结构

| 文件 | 状态 | 责任 |
|------|------|------|
| `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt` | **新建** | 手势检测、动画时序、按钮渲染、Layout 自定义测量 |
| `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt` | **修改** | 删除 L150-222 swipe 状态变量 + L274-745 Layout 块 + L1239-1276 SwipeActionButton 私有函数；删除 `onSwipeExpandChange`/`expandedTodoId` 参数 |
| `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt` | **修改** | 用 `SwipeableTodoBox` 包裹 `TodoListItem`；保留 `swipeExpandedTodoId` 状态 |
| `app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt` | **新建** | 6 个 Compose UI 测试用例 |
| `app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt` | **修改** | 删除 4 个 swipe 相关测试（迁移到 SwipeableTodoBoxTest） |

---

## Task 1: 创建 SwipeableTodoBox 骨架（空手势 + 空按钮 + Layout 测量）

**Files:**
- Create: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt`

- [ ] **Step 1: 创建文件并定义包名 + 注释**

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.VerticalAlignTop
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
```

- [ ] **Step 2: 添加组件签名 + 状态变量（仅 cardOffsetX）**

```kotlin
/**
 * 可左滑展开操作区的容器组件（飞书风格）
 *
 * 用法：将待办卡片作为 content 传入，自动获得左滑操作能力。
 * 采用自定义 Layout 测量顺序：先测 Card 拿高度，再测 Actions 锁定尺寸。
 *
 * @param modifier 修饰符
 * @param isEnabled 是否启用左滑（批量模式或 disabled 状态下设为 false）
 * @param isExpanded 是否处于展开状态（由父组件控制，用于"互斥展开"语义）
 * @param onExpandChange 展开状态变化回调（true=展开, false=收起）
 * @param onPinClick 点击置顶按钮回调
 * @param onShareClick 点击分享按钮回调
 * @param onDeleteClick 点击删除按钮回调
 * @param content 卡片内容（通常是 TodoListItem）
 */
@Composable
fun SwipeableTodoBox(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    isExpanded: Boolean = false,
    onExpandChange: (Boolean) -> Unit = {},
    onPinClick: () -> Unit = {},
    onShareClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    /** 动作区总宽 = 3 × 72dp = 216dp */
    val actionsWidthDp = 216.dp

    /** 单按钮宽 72dp（暴露给后续手势阈值使用） */
    val buttonWidthDp = 72.dp

    /** 卡片水平位移 Animatable：范围 -actionsWidthPx..0f（px） */
    val cardOffsetX = remember { Animatable(0f) }

    /** 动作区宽度的 px 值，用于 coerce 卡片位移 */
    val actionsWidthPx = with(density) { actionsWidthDp.toPx() }
}
```

- [ ] **Step 3: 实现 Layout + 手势（仅占位，不含按钮渲染）**

```kotlin
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            // ⚠️ 严格顺序：先 Card 后 Actions，确保 measurables[0] = Card, measurables[1] = Actions
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = Dp(cardOffsetX.value))
                    .pointerInput(isEnabled, isExpanded) {
                        if (!isEnabled) return@pointerInput
                        detectHorizontalDragGestures(
                            onDragEnd = { /* Task 3 填充阈值判断 */ },
                            onDragCancel = { /* Task 3 填充阈值判断 */ }
                        ) { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (cardOffsetX.value + dragAmount)
                                    .coerceIn(-actionsWidthPx, 0f)
                                cardOffsetX.snapTo(newOffset)
                            }
                        }
                    }
            ) {
                content()
            }

            // ActionsLayer（Task 2 实现）
            if (isEnabled) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topEnd = 20.dp,
                                bottomEnd = 20.dp
                            )
                        )
                ) {
                    // Task 2: Row + 3 SwipeActionButton
                }
            }
        },
        measurePolicy = { measurables, constraints ->
            val cardPlaceable = measurables[0].measure(constraints)
            val cardWidth = cardPlaceable.width
            val cardHeight = cardPlaceable.height
            val actionsPlaceable = if (measurables.size > 1 && isEnabled) {
                measurables[1].measure(
                    Constraints.fixed(
                        width = with(density) { actionsWidthDp.roundToPx() },
                        height = cardHeight
                    )
                )
            } else null
            layout(cardWidth, cardHeight) {
                cardPlaceable.placeRelative(0, 0)
                actionsPlaceable?.placeRelative(
                    x = cardWidth - actionsPlaceable.width,
                    y = 0
                )
            }
        }
    )
}
```

- [ ] **Step 4: 验证文件无编译错误**

运行 IDE 的 "Build" → 确认 `SwipeableTodoBox` 编译通过（无 import 错误）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt
git commit -m "feat(swipeable): 创建 SwipeableTodoBox 骨架（含 Layout 测量与手势）"
```

---

## Task 2: 添加 3 SwipeActionButton 私有组件（alpha 应用到外层 Box）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt`

- [ ] **Step 1: 在 Layout content lambda 的 ActionsLayer 中添加 3 按钮 Row**

替换 Task 1 Step 3 中的 ActionsLayer Box 内容：

```kotlin
            // ActionsLayer
            if (isEnabled) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topEnd = 20.dp,
                                bottomEnd = 20.dp
                            )
                        )
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        // 按钮 1：置顶（橙 #FF9A5C）
                        SwipeActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Filled.VerticalAlignTop,
                            label = "置顶",
                            backgroundColor = Color(0xFFFF9A5C),
                            alpha = 0f,  // Task 5: 由 revealProgress 驱动
                            scaleX = 0.7f,
                            translateX = 24f,
                            onClick = {
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(0f, spring())
                                }
                                onExpandChange(false)
                                onPinClick()
                            }
                        )

                        // 按钮 2：分享（柔和蓝 #90CAF9）
                        SwipeActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Filled.IosShare,
                            label = "分享",
                            backgroundColor = Color(0xFF90CAF9),
                            alpha = 0f,
                            scaleX = 0.7f,
                            translateX = 24f,
                            onClick = {
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(0f, spring())
                                }
                                onExpandChange(false)
                                onShareClick()
                            }
                        )

                        // 按钮 3：删除（柔和红 #FF8A80）
                        SwipeActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Filled.Delete,
                            label = "删除",
                            backgroundColor = Color(0xFFFF8A80),
                            alpha = 0f,
                            scaleX = 0.7f,
                            translateX = 24f,
                            onClick = {
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(0f, spring())
                                }
                                onExpandChange(false)
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
```

- [ ] **Step 2: 添加 SwipeActionButton 私有组件（alpha 应用到外层 Box）**

```kotlin
/**
 * 3 按钮左滑操作按钮（飞书风格）
 *
 * alpha/scaleX/translateX 均应用于外层 Box（不是内部 Column），
 * 实现"按钮整体渐入"而非仅图标文字渐入。
 *
 * @param icon 图标
 * @param label 文字标签
 * @param backgroundColor 按钮背景色
 * @param alpha 整体透明度（0f..1f），由外层 Animatable 驱动
 * @param scaleX 水平缩放（0.7f..1.0f），形成"展开"层次感
 * @param translateX 水平位移（px，正值=向右偏移），形成"滑入"效果
 * @param onClick 点击回调
 */
@Composable
private fun SwipeActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    backgroundColor: Color,
    alpha: Float,
    scaleX: Float,
    translateX: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scaleX
                this.translationX = translateX
            }
            .background(backgroundColor)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            // 固定 4dp 间距（在 alpha=0 时不影响视觉）
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
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

- [ ] **Step 3: 验证编译通过**

运行 IDE "Build" → 确认编译通过（无 import 错误或未解析引用）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt
git commit -m "feat(swipeable): 添加 3 SwipeActionButton 私有组件（alpha 应用到外层 Box）"
```

---

## Task 3: 实现阈值判断 + spring 动画

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt`

- [ ] **Step 1: 替换 onDragEnd 占位为完整阈值判断**

替换 Task 1 Step 3 中的 `onDragEnd = { /* Task 3 填充阈值判断 */ }`：

```kotlin
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val final = cardOffsetX.value
                                val triggerThresholdPx = with(density) { buttonWidthDp.toPx() }
                                val target = when {
                                    // 左滑 ≥ 单按钮宽 → 完全展开
                                    final <= -triggerThresholdPx -> -actionsWidthPx
                                    // 右滑 > 30px → 收回
                                    final >= 30f -> 0f
                                    // 处于展开状态 → 保持展开
                                    isExpanded -> -actionsWidthPx
                                    // 其他 → 收回
                                    else -> 0f
                                }
                                onExpandChange(target < 0f)
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(
                                        target,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                // 同 onDragEnd
                                val final = cardOffsetX.value
                                val triggerThresholdPx = with(density) { buttonWidthDp.toPx() }
                                val target = if (final <= -triggerThresholdPx) -actionsWidthPx else 0f
                                onExpandChange(target < 0f)
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(target, spring())
                                }
                            }
                        ) { _, dragAmount ->
                            coroutineScope.launch {
                                val newOffset = (cardOffsetX.value + dragAmount)
                                    .coerceIn(-actionsWidthPx, 0f)
                                cardOffsetX.snapTo(newOffset)
                            }
                        }
```

- [ ] **Step 2: 添加 LaunchedEffect 同步外部 isExpanded 状态**

在 `val actionsWidthPx = with(density) { actionsWidthDp.toPx() }` 之后添加：

```kotlin
    /**
     * 监听外部 isExpanded 变化：
     * - 外部要求展开但当前未完全展开 → 动画到 -actionsWidthPx
     * - 外部要求收起但当前处于展开 → 动画到 0
     * 实现"互斥展开"控制
     */
    LaunchedEffect(isExpanded) {
        if (isExpanded && cardOffsetX.value > -actionsWidthPx) {
            cardOffsetX.animateTo(
                -actionsWidthPx,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else if (!isExpanded && cardOffsetX.value < 0f) {
            cardOffsetX.animateTo(0f, spring())
        }
    }
```

- [ ] **Step 3: 验证编译通过**

运行 IDE "Build" → 确认编译通过。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt
git commit -m "feat(swipeable): 实现阈值判断 + spring 动画 + isExpanded 同步"
```

---

## Task 4: 添加 3 组 Animatable（alpha/scaleX/translateX）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt`

- [ ] **Step 1: 在 isExpanded LaunchedEffect 之后添加 9 个 Animatable**

```kotlin
    /** 按钮 1（置顶）的 alpha/scaleX/translateX Animatable */
    val btn1Alpha = remember { Animatable(0f) }
    val btn1ScaleX = remember { Animatable(0.7f) }
    val btn1TranslateX = remember { Animatable(24f) }

    /** 按钮 2（分享）的 alpha/scaleX/translateX Animatable */
    val btn2Alpha = remember { Animatable(0f) }
    val btn2ScaleX = remember { Animatable(0.7f) }
    val btn2TranslateX = remember { Animatable(24f) }

    /** 按钮 3（删除）的 alpha/scaleX/translateX Animatable */
    val btn3Alpha = remember { Animatable(0f) }
    val btn3ScaleX = remember { Animatable(0.7f) }
    val btn3TranslateX = remember { Animatable(24f) }

    /**
     * 阶段边界常量（dp）
     * - 阶段 0 → 1 边界：30dp（按钮 1 开始）
     * - 阶段 1 → 2 边界：30 + 48 = 78dp（按钮 2 开始）
     * - 阶段 2 → 3 边界：78 + 48 = 126dp（按钮 3 开始）
     * - 阶段 3 → 4 边界：126 + 48 = 174dp（按钮 3 完全显示）
     */
    val stage1ThresholdDp = 30f
    val stage2ThresholdDp = 78f
    val stage3ThresholdDp = 126f
    val stage4ThresholdDp = 174f
```

- [ ] **Step 2: 添加 revealProgress 计算（响应 cardOffsetX 变化）**

```kotlin
    /**
     * 实时计算 revealProgress（基于 cardOffsetX.value）
     * 单位：dp，范围 0f..216f
     */
    val revealProgressDp = (-cardOffsetX.value / density.density).coerceIn(0f, 216f)
```

- [ ] **Step 3: 验证编译通过**

运行 IDE "Build" → 确认编译通过。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt
git commit -m "feat(swipeable): 添加 9 个 Animatable（3 按钮 × alpha/scaleX/translateX）"
```

---

## Task 5: 实现飞书式堆叠动画（LaunchedEffect 监听 revealProgress）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt`

- [ ] **Step 1: 在 revealProgressDp 之后添加 3 个 LaunchedEffect（每个按钮一个）**

```kotlin
    /**
     * 飞书式按钮入场动画（核心）
     *
     * 思路：根据 revealProgressDp 自动将每个按钮的 alpha/scaleX/translateX
     * animateTo 到对应阶段的目标值，形成"依次滑入 + 渐入"效果。
     */
    LaunchedEffect(revealProgressDp) {
        // 按钮 1 动画（阶段 1: 30~78dp）
        when {
            revealProgressDp >= stage2ThresholdDp -> {
                // 阶段 2+：按钮 1 已完全显示
                btn1Alpha.animateTo(1f, tween(80, easing = FastOutSlowInEasing))
                btn1ScaleX.animateTo(1f, tween(80, easing = FastOutSlowInEasing))
                btn1TranslateX.animateTo(0f, tween(80, easing = FastOutSlowInEasing))
            }
            revealProgressDp >= stage1ThresholdDp -> {
                // 阶段 1：按钮 1 渐入中
                val progress = ((revealProgressDp - stage1ThresholdDp) / 48f).coerceIn(0f, 1f)
                btn1Alpha.animateTo(progress, tween(80, easing = FastOutSlowInEasing))
                btn1ScaleX.animateTo(0.7f + 0.3f * progress, tween(80, easing = FastOutSlowInEasing))
                btn1TranslateX.animateTo(24f * (1f - progress), tween(80, easing = FastOutSlowInEasing))
            }
            else -> {
                // 阶段 0：按钮 1 隐藏
                btn1Alpha.animateTo(0f, tween(80, easing = FastOutSlowInEasing))
                btn1ScaleX.animateTo(0.7f, tween(80, easing = FastOutSlowInEasing))
                btn1TranslateX.animateTo(24f, tween(80, easing = FastOutSlowInEasing))
            }
        }

        // 按钮 2 动画（阶段 2: 78~126dp）
        when {
            revealProgressDp >= stage3ThresholdDp -> {
                btn2Alpha.animateTo(1f, tween(80, easing = FastOutSlowInEasing))
                btn2ScaleX.animateTo(1f, tween(80, easing = FastOutSlowInEasing))
                btn2TranslateX.animateTo(0f, tween(80, easing = FastOutSlowInEasing))
            }
            revealProgressDp >= stage2ThresholdDp -> {
                val progress = ((revealProgressDp - stage2ThresholdDp) / 48f).coerceIn(0f, 1f)
                btn2Alpha.animateTo(progress, tween(80, easing = FastOutSlowInEasing))
                btn2ScaleX.animateTo(0.7f + 0.3f * progress, tween(80, easing = FastOutSlowInEasing))
                btn2TranslateX.animateTo(24f * (1f - progress), tween(80, easing = FastOutSlowInEasing))
            }
            else -> {
                btn2Alpha.animateTo(0f, tween(80, easing = FastOutSlowInEasing))
                btn2ScaleX.animateTo(0.7f, tween(80, easing = FastOutSlowInEasing))
                btn2TranslateX.animateTo(24f, tween(80, easing = FastOutSlowInEasing))
            }
        }

        // 按钮 3 动画（阶段 3: 126~174dp）
        when {
            revealProgressDp >= stage4ThresholdDp -> {
                btn3Alpha.animateTo(1f, tween(80, easing = FastOutSlowInEasing))
                btn3ScaleX.animateTo(1f, tween(80, easing = FastOutSlowInEasing))
                btn3TranslateX.animateTo(0f, tween(80, easing = FastOutSlowInEasing))
            }
            revealProgressDp >= stage3ThresholdDp -> {
                val progress = ((revealProgressDp - stage3ThresholdDp) / 48f).coerceIn(0f, 1f)
                btn3Alpha.animateTo(progress, tween(80, easing = FastOutSlowInEasing))
                btn3ScaleX.animateTo(0.7f + 0.3f * progress, tween(80, easing = FastOutSlowInEasing))
                btn3TranslateX.animateTo(24f * (1f - progress), tween(80, easing = FastOutSlowInEasing))
            }
            else -> {
                btn3Alpha.animateTo(0f, tween(80, easing = FastOutSlowInEasing))
                btn3ScaleX.animateTo(0.7f, tween(80, easing = FastOutSlowInEasing))
                btn3TranslateX.animateTo(24f, tween(80, easing = FastOutSlowInEasing))
            }
        }
    }
```

- [ ] **Step 2: 修改 Task 2 的按钮调用，使用 Animatable 值**

将 Task 2 中 3 个 `SwipeActionButton` 的 `alpha = 0f, scaleX = 0.7f, translateX = 24f` 替换为：

```kotlin
                        // 按钮 1：置顶（橙 #FF9A5C）
                        SwipeActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Filled.VerticalAlignTop,
                            label = "置顶",
                            backgroundColor = Color(0xFFFF9A5C),
                            alpha = btn1Alpha.value,
                            scaleX = btn1ScaleX.value,
                            translateX = btn1TranslateX.value,
                            onClick = {
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(0f, spring())
                                }
                                onExpandChange(false)
                                onPinClick()
                            }
                        )

                        // 按钮 2：分享（柔和蓝 #90CAF9）
                        SwipeActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Filled.IosShare,
                            label = "分享",
                            backgroundColor = Color(0xFF90CAF9),
                            alpha = btn2Alpha.value,
                            scaleX = btn2ScaleX.value,
                            translateX = btn2TranslateX.value,
                            onClick = {
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(0f, spring())
                                }
                                onExpandChange(false)
                                onShareClick()
                            }
                        )

                        // 按钮 3：删除（柔和红 #FF8A80）
                        SwipeActionButton(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            icon = Icons.Filled.Delete,
                            label = "删除",
                            backgroundColor = Color(0xFFFF8A80),
                            alpha = btn3Alpha.value,
                            scaleX = btn3ScaleX.value,
                            translateX = btn3TranslateX.value,
                            onClick = {
                                coroutineScope.launch {
                                    cardOffsetX.animateTo(0f, spring())
                                }
                                onExpandChange(false)
                                onDeleteClick()
                            }
                        )
```

- [ ] **Step 3: 验证编译通过**

运行 IDE "Build" → 确认编译通过。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt
git commit -m "feat(swipeable): 实现飞书式堆叠动画（5 阶段 LaunchedEffect）"
```

---

## Task 6: 验证 SwipeableTodoBox 完整功能（手动 + 编译）

**Files:**
- 无

- [ ] **Step 1: 完整阅读 SwipeableTodoBox.kt**

确认文件结构清晰、无冗余代码、注释完整。

- [ ] **Step 2: 验证 import 无缺失**

对照文件顶部的 import 列表与代码中实际引用的类/函数，确保无 Unresolved reference。

- [ ] **Step 3: 提交（如有修改）**

```bash
git status  # 确认是否有未提交的修改
# 如有：
git add app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt
git commit -m "chore(swipeable): SwipeableTodoBox 完成自检"
```

---

## Task 7: 修改 TodoListItem.kt（删除所有 swipe 逻辑）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt`

- [ ] **Step 1: 删除 L150-222 的 swipe 状态变量和 LaunchedEffect**

删除以下行：
- L150: `val deleteWidth = 80.dp`
- L152: 注释 `/** ==== 3 按钮左滑 swipe 状态（飞书风格） ==== */`
- L153-154: `coroutineScope` 和 `density`（仅用于 swipe，需要判断是否还在其他地方使用）
- L157: `val actionsWidthDp = 216.dp`
- L159-160: `val cardOffsetX` Animatable
- L163-164: `val triggerThresholdPx`
- L167: `val actionsWidthPx`
- L185-199: 3 个 `btn1Alpha/2Alpha/3Alpha` `animateFloatAsState`
- L201-222: `LaunchedEffect(expandedTodoId)` 同步块

> 注意：`coroutineScope` 和 `density` 如果还在其他逻辑中使用（如 `LongPressMenu`），需要保留并重新定义在合适位置。

- [ ] **Step 2: 删除 L274-745 的 Layout 块**

删除整个 `Layout(...)` 调用（含 measurePolicy 和 content lambda），但保留 `if (showLongPressMenu) { TodoActionSheet(...) }` 部分。

- [ ] **Step 3: 删除 L1239-1276 的 SwipeActionButton 私有函数**

删除整个 `@Composable private fun SwipeActionButton(...)` 函数。

- [ ] **Step 4: 删除 swipe 相关参数**

从 `TodoListItem` 函数签名中删除：
- `onSwipeExpandChange: (Boolean) -> Unit = {}`
- `expandedTodoId: Long? = null`
- 相关的 KDoc 注释

- [ ] **Step 5: 删除 swipe 相关 import**

对照 import 列表，删除：
- `androidx.compose.animation.core.Animatable`
- `androidx.compose.animation.core.animateDpAsState`（如果不再使用）
- `androidx.compose.animation.core.animateFloatAsState`（如果不再使用）
- `androidx.compose.foundation.gestures.detectHorizontalDragGestures`
- `androidx.compose.foundation.layout.offset`（如果不再使用）
- `androidx.compose.foundation.layout.IntrinsicSize`（如果不再使用）
- `androidx.compose.ui.input.pointer.pointerInput`（如果不再使用）
- `androidx.compose.ui.layout.Layout`
- `androidx.compose.ui.platform.LocalDensity`（如果不再使用）
- `androidx.compose.ui.unit.Constraints`（如果不再使用）
- `androidx.compose.ui.draw.blur`（如果不再使用 - 实际可能仍在 CategoryTagWithShadow 中使用）

> 提示：使用 IDE "Optimize Imports" 自动清理未使用 import。

- [ ] **Step 6: 验证编译通过**

运行 IDE "Build" → 确认 `TodoListItem` 编译通过。

- [ ] **Step 7: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt
git commit -m "refactor(todo-item): 删除所有 swipe 逻辑（迁移到独立 SwipeableTodoBox）"
```

---

## Task 8: 修改 HomeScreen.kt（用 SwipeableTodoBox 包裹 TodoListItem）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

- [ ] **Step 1: 添加 SwipeableTodoBox import**

在文件顶部 import 区域添加：

```kotlin
import com.corgimemo.app.ui.components.SwipeableTodoBox
```

- [ ] **Step 2: 找到 TodoListItem 调用处（L572 附近）**

当前代码结构：
```kotlin
TodoListItem(
    todo = todo,
    /* ... 其他参数 ... */
    onSwipeExpandChange = { isExpanded ->
        swipeExpandedTodoId = if (isExpanded) todo.id else null
    },
    expandedTodoId = swipeExpandedTodoId,
    /* ... */
)
```

- [ ] **Step 3: 用 SwipeableTodoBox 包裹 TodoListItem**

替换为：

```kotlin
SwipeableTodoBox(
    modifier = Modifier.testTag("swipeableTodoBox_${todo.id}"),
    isEnabled = !isBatchMode,
    isExpanded = swipeExpandedTodoId == todo.id,
    onExpandChange = { isExpanded ->
        swipeExpandedTodoId = if (isExpanded) todo.id else null
    },
    onPinClick = {
        // 本迭代置顶功能仅日志输出（后端实现在后续迭代）
        android.util.Log.w("TodoCardSwipe", "置顶：todoId=${todo.id}")
    },
    onShareClick = { /* 复用 shareTodoAsImage（与长按菜单中的分享行为一致） */ },
    onDeleteClick = { showDeleteDialogFor = todo.id }
) {
    TodoListItem(
        todo = todo,
        subTaskProgress = subTaskProgressMap[todo.id],
        subTasks = subTasksMap[todo.id] ?: emptyList(),
        /* ... 其他参数（移除 onSwipeExpandChange、expandedTodoId） ... */
    )
}
```

- [ ] **Step 4: 验证编译通过**

运行 IDE "Build" → 确认 HomeScreen 编译通过。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt
git commit -m "feat(home): 用 SwipeableTodoBox 包裹 TodoListItem 实现左滑"
```

---

## Task 9: 创建 SwipeableTodoBoxTest.kt（6 个 Compose UI 测试）

**Files:**
- Create: `app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt`

- [ ] **Step 1: 创建测试文件骨架**

```kotlin
package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * SwipeableTodoBox 飞书式左滑交互 Compose UI 测试
 *
 * 覆盖 6 个关键路径：
 * 1. 未滑动时按钮不可见
 * 2. 拖动 ≥ 72dp 完全展开
 * 3. 拖动 < 72dp 回弹关闭
 * 4. 点击置顶触发 onPinClick
 * 5. 点击分享触发 onShareClick
 * 6. 点击删除触发 onDeleteClick
 */
class SwipeableTodoBoxTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** 辅助：构造一个最小的可测试容器 */
    @androidx.compose.runtime.Composable
    private fun TestContainer(
        isEnabled: Boolean = true,
        isExpanded: Boolean = false,
        onPinClick: () -> Unit = {},
        onShareClick: () -> Unit = {},
        onDeleteClick: () -> Unit = {},
        onExpandChange: (Boolean) -> Unit = {}
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                SwipeableTodoBox(
                    modifier = Modifier.testTag("swipeBox"),
                    isEnabled = isEnabled,
                    isExpanded = isExpanded,
                    onPinClick = onPinClick,
                    onShareClick = onShareClick,
                    onDeleteClick = onDeleteClick,
                    onExpandChange = onExpandChange
                ) {
                    Box(modifier = Modifier.height(80.dp).testTag("cardContent")) {
                        Text("测试卡片", modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 2: 添加测试 1 - 初始隐藏**

```kotlin
    /**
     * 验收 AC-1：未滑动时 3 按钮完全不可见
     */
    @Test
    fun buttons_initiallyHidden() {
        composeTestRule.setContent {
            TestContainer()
        }

        // 初始时 3 个按钮 alpha=0，应该不可见
        composeTestRule.onNodeWithContentDescription("置顶").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("分享").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("删除").assertIsNotDisplayed()
    }
```

- [ ] **Step 3: 添加测试 2 - 完全展开**

```kotlin
    /**
     * 验收 AC-4：拖动 ≥ 72dp 松手后完全展开
     */
    @Test
    fun swipe_fullyExpanded_allButtonsVisible() {
        composeTestRule.setContent {
            TestContainer()
        }

        // 左滑（默认从中心拖到左边），距离足够超过 72dp 屏宽
        composeTestRule.onNodeWithTag("swipeBox")
            .performTouchInput { swipeLeft() }

        // 等待 600ms 让动画完成（spring 弹性 + 按钮渐入 + 缓冲）
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 完全展开后，3 个按钮都应该可见
        composeTestRule.onNodeWithContentDescription("置顶").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("分享").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("删除").assertIsDisplayed()
    }
```

- [ ] **Step 4: 添加测试 3 - 部分回弹**

```kotlin
    /**
     * 验收 AC-5：拖动 < 72dp 松手后回弹关闭
     */
    @Test
    fun swipePartialReboundToClosed() {
        composeTestRule.setContent {
            TestContainer()
        }

        // 短距离左滑（< 72dp） - swipeLeft 默认会拖到屏幕外，必然超过 72dp
        // 因此使用 performTouchInput 手动控制距离
        composeTestRule.onNodeWithTag("swipeBox")
            .performTouchInput {
                swipeLeft(distanceX = 200f)  // 较小距离
            }

        // 等待 600ms
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 拖动幅度不足，按钮应回弹到不可见
        composeTestRule.onNodeWithContentDescription("置顶").assertIsNotDisplayed()
    }
```

- [ ] **Step 5: 添加测试 4 - 点击置顶**

```kotlin
    /**
     * 验收 AC-7：点击置顶触发 onPinClick
     */
    @Test
    fun clickPin_triggersCallback() {
        var pinClicked = false

        composeTestRule.setContent {
            TestContainer(onPinClick = { pinClicked = true })
        }

        // 完全展开 swipe
        composeTestRule.onNodeWithTag("swipeBox").performTouchInput { swipeLeft() }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 点击置顶按钮
        composeTestRule.onNodeWithContentDescription("置顶").performClick()

        assertTrue("onPinClick 未被调用", pinClicked)
    }
```

- [ ] **Step 6: 添加测试 5 - 点击分享**

```kotlin
    /**
     * 验收 AC-8：点击分享触发 onShareClick
     */
    @Test
    fun clickShare_triggersCallback() {
        var shareClicked = false

        composeTestRule.setContent {
            TestContainer(onShareClick = { shareClicked = true })
        }

        // 完全展开 swipe
        composeTestRule.onNodeWithTag("swipeBox").performTouchInput { swipeLeft() }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 点击分享按钮
        composeTestRule.onNodeWithContentDescription("分享").performClick()

        assertTrue("onShareClick 未被调用", shareClicked)
    }
```

- [ ] **Step 7: 添加测试 6 - 点击删除**

```kotlin
    /**
     * 验收 AC-9：点击删除触发 onDeleteClick
     */
    @Test
    fun clickDelete_triggersCallback() {
        var deleteClicked = false

        composeTestRule.setContent {
            TestContainer(onDeleteClick = { deleteClicked = true })
        }

        // 完全展开 swipe
        composeTestRule.onNodeWithTag("swipeBox").performTouchInput { swipeLeft() }
        composeTestRule.mainClock.advanceTimeBy(600)
        composeTestRule.waitForIdle()

        // 点击删除按钮
        composeTestRule.onNodeWithContentDescription("删除").performClick()

        assertTrue("onDeleteClick 未被调用", deleteClicked)
    }
```

- [ ] **Step 8: 运行测试**

运行：`./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.SwipeableTodoBoxTest"`

期望：6 个测试全部通过。

- [ ] **Step 9: 提交**

```bash
git add app/src/test/java/com/corgimemo/app/ui/components/SwipeableTodoBoxTest.kt
git commit -m "test(swipeable): 添加 SwipeableTodoBox 6 个 Compose UI 测试"
```

---

## Task 10: 删除 TodoListItemTest.kt 中的 swipe 相关测试

**Files:**
- Modify: `app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt`

- [ ] **Step 1: 删除 4 个 swipe 相关测试**

删除以下测试方法：
- `swipe_buttonsInitiallyHidden`
- `swipe_fullyExpanded_buttonsVisible`
- `clickPinButton_triggersOnPinClick`
- `clickDeleteButton_triggersOnDelete`

- [ ] **Step 2: 清理未使用的 import**

删除以下 import（如未在其他测试中使用）：
- `androidx.compose.ui.test.assertIsNotDisplayed`
- `androidx.compose.ui.test.onNodeWithContentDescription`
- `androidx.compose.ui.test.performTouchInput`
- `androidx.compose.ui.test.swipeLeft`
- `androidx.compose.foundation.layout.fillMaxSize`
- `com.corgimemo.app.data.model.TodoItem`（如未使用）
- `org.junit.Assert.assertTrue`（如未使用）

- [ ] **Step 3: 运行 TodoListItemTest 验证**

运行：`./gradlew :app:testDebugUnitTest --tests "com.corgimemo.app.ui.components.TodoListItemTest"`

期望：剩余测试全部通过（可能为空类，可考虑删除整个文件）。

- [ ] **Step 4: 提交**

```bash
git add app/src/test/java/com/corgimemo/app/ui/components/TodoListItemTest.kt
git commit -m "test(todo-item): 删除已迁移到 SwipeableTodoBoxTest 的 swipe 测试"
```

---

## Task 11: 询问用户是否进行编译验证（如需要）

**说明**：根据项目规则 [编译验证](file:///c:/Users/Lenovo/Desktop/CorgiMemo/.trae/rules/编译验证.md)，编译验证前需要询问用户。

- [ ] **Step 1: 询问用户**

向用户报告当前进度，询问是否进行编译验证（`./gradlew :app:assembleDebug` 或运行测试）。

- [ ] **Step 2: 根据用户反馈执行**

- 如同意：执行编译命令并报告结果
- 如拒绝：跳过编译验证，留待用户自行处理

---

## Task 12: 询问用户是否进行 git 提交汇总

**说明**：根据项目规则 [git提交](file:///c:/Users/Lenovo/Desktop/CorgiMemo/.trae/rules/git提交.md)，每完成一个任务后询问用户是否提交。

- [ ] **Step 1: 询问用户是否进行 git 提交汇总**

向用户报告所有任务完成，询问是否将所有改动合并为一次提交（squash）并生成中文提交信息。

---

## 验收清单

完成所有任务后，验证以下验收标准（来自 spec §10）：

- [ ] **AC-1**：未滑动时 3 按钮完全不可见
- [ ] **AC-2**：拖动 30~78dp 区间，按钮 1 渐入
- [ ] **AC-3**：拖动 78~126dp 区间，按钮 2 渐入
- [ ] **AC-4**：拖动 ≥ 72dp 松手 → 完全展开
- [ ] **AC-5**：拖动 < 30dp 松手 → 回弹关闭
- [ ] **AC-6**：3 按钮按"置顶→分享→删除"依次入场
- [ ] **AC-7**：点击置顶 → 输出 Log
- [ ] **AC-8**：点击分享 → 弹系统分享面板
- [ ] **AC-9**：点击删除 → 弹二次确认弹窗
- [ ] **AC-10**：同一时间只有一张卡片展开
- [ ] **AC-11**：与 ReorderableLazyColumn 拖拽不冲突
- [ ] **AC-12**：与 LazyColumn 滚动不冲突
- [ ] **AC-13**：批量模式下左滑禁用

---

## 后续优化（不在本次范围）

- 置顶功能后端实现（`pinned: Boolean` 字段 + 排序逻辑）
- 多选模式下批量置顶 / 批量删除
- 振动反馈（HapticFeedback）—— 操作命中按钮时短暂震动
- 已完成（status=1）卡片的左滑交互差异
