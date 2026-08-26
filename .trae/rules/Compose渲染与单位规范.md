# Compose 渲染与单位规范

本文档记录在 Jetpack Compose 开发中关于单位处理和溢出裁剪问题的关键经验，避免后续踩坑。

## 1. 像素单位转换规范（重要）

在 Compose 中处理 `pixmaps`（像素值）时，必须严格区分“像素”与“密度无关像素（dp）”的转换。

### 🔴 禁止：直接使用 `.dp` 包装像素值
**错误做法**：
```kotlin
// dragOffsetX.value 是手势系统提供的真实像素值 (px)
val dragX = dragOffsetX.value.dp  // ❌ 错误！将 px 值直接标记为 dp
```
**后果**：
- 渲染平移量会被屏幕密度 (density) 放大（通常 2.75 倍）。
- 手指滑动 1px，卡片却滑动 2.75px，导致滑动手感异常（过快）。
- 日志埋点的坐标与真实渲染位置不一致，导致难以排查视觉裁剪问题。

### 🟢 正确：使用 `toDp()` 进行换算
**正确做法**：
```kotlin
val dragX = with(density) { dragOffsetX.value.toDp() }  // ✅ 正确：像素转 dp
```
**说明**：
- `detectDragGestures` 返回的 `dragAmount` 是像素值。
- 必须通过 `LocalDensity.current` 提供的 `toDp()` 方法转换为 dp，才能与 Compose 布局系统的其它 dp 单位对齐。
- 渲染时 `translationX = density.run { dragX.toPx() }` 会正确还原为像素位移。

---

## 2. 裁剪（Clip）问题排查与解决

### 2.1 核心认知：`clip = false` 通常是多余的
**背景**：
Compose 的 `graphicsLayer` 默认行为**不裁剪**其内容（即 `clip = false`）。因此，盲目给所有父容器添加 `.graphicsLayer { this.clip = false }` 通常是无效操作。

**陷阱**：
如果一个子组件（如卡片）通过 `graphicsLayer` 的 `translationX` 平移到父容器的 **layout bounds**（布局边界）之外，它**仍然会被裁剪**。这种裁剪不是由 `graphicsLayer` 的 `clip` 属性控制的，而是系统级的渲染优化（为了避免绘制屏幕外的像素）。

### 2.2 解决方案：布局空间预借 (Layout Space Pre-borrowing)

当子组件需要向父容器外部方向滑动时（例如向左滑动），应主动为其预留布局空间，而不是尝试关闭裁剪。

#### 实现思路
1.  **父容器扩宽**：将父容器（或关键中间容器）在滑动方向上的宽度增加。
2.  **内容视觉补偿**：使用 `offset` 将内部内容推回原视觉位置。
3.  **建立数学闭环**：
    -   假设原容器宽度为 `W`，增加量为 `E`（例如 130.dp）。
    -   父容器新宽度 = `W + E`。
    -   内容 `offset` = `+E`（向右推 `E`，保持视觉位置不变）。
    -   这样，内容向左滑动的最大安全距离就从 `0` 扩展到了 `E`，只要滑动距离不超过 `E`，内容就始终在父容器的 layout bounds 内，永远不会被裁剪。

#### 示例代码
```kotlin
// 父容器：宽度预借 130dp
Box(
    modifier = Modifier.size(width = originalWidth + 130.dp, height = originalHeight)
) {
    // 子内容：向右偏移 130dp 补偿视觉位置
    Box(
        modifier = Modifier.offset(x = 130.dp)
    ) {
        // 卡片内容。此卡片向左滑动最多 130dp 都不会被父容器裁剪。
        Card(
            modifier = Modifier.graphicsLayer { translationX = (-100).dp.toPx() }
        )
    }
}
```

### 2.3 其他注意事项
- **多级容器联动**：如果滑动轨迹跨越多个容器（如卡片 -> Layer -> Stage），所有相关父容器都必须执行“预借”操作，形成完整的无裁剪链。
- **TransformOrigin 补偿**：当卡片自身尺寸被扩宽时，其 `graphicsLayer` 的 `transformOrigin`（变换中心）默认会改变。如果卡片有旋转/缩放需求，需要手动把 `transformOrigin` 固定回原图片的视觉中心。

---
**文档更新时间**: 2026-08-26
**适用范围**: 所有 Jetpack Compose UI 开发