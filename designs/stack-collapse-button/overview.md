# 堆叠图展开态「收起」按钮位置原型

灵感首页堆叠图展开后的「收起」按钮，从 Stage 右上角（文字 + 右箭头）改为
**浮层吸附图片行视口左缘、垂直居中、纯 ChevronLeft 符号**。本目录为 HTML 原型，
用户确认后再改 `SwipeableImageStack.kt`。

## 交付物

- `collapse-button-position.html` —— 单文件原型，左侧参数面板实时调，右侧手机预览

## 决策（已与用户确认）

| 项 | 选择 |
| --- | --- |
| 占位方式 | 浮层覆盖（图片行宽度不变，按钮不随横向滚动） |
| 吸附基准 | 图片行视口左缘 88dp（与堆叠态顶卡、文本列同一垂直基准） |
| 符号 | 单左箭头 ChevronLeft，去掉「收起」文字 |

## 原型对齐的真实参数（读源码校准，非估算）

| 参数 | 值 | 来源 |
| --- | --- | --- |
| 卡片 layout | 120 × 120 dp | `cardWidth / cardHeight` |
| 展开态视觉边长 S | ≈150 dp | `expandedCardSizeDp = maxOf(bboxW, bboxH)` |
| 展开态 scale | S/W = 1.25（中心缩放） | `calcCardTarget` 展开端点 |
| 展开态 x | (S−W)/2 + i×(S+G) = 15 + i×158 | 同上 |
| 视觉行宽 | N×(S+G) − G | `cardRowWidthDp` |
| 扇形角 | −(visibleDepth−1)×15 = −45° | `fanAngleDeg` |
| 堆叠层间距 / 缩放 | yOffset 8dp / scaleStep 0.05 | `calcCardTarget` 堆叠端点 |
| 视口左缘 | 88 dp | `stackStageOffsetX = contentStartX(70) + 18` |
| 卡片垂直锚点 | `topCardAnchorY` ≈ 24dp（两态恒定） | V7.3 锚点 |

> 注意：旧原型 `preview_stack.html` / `expand-collapse-stack.html` 用的是
> 120dp 展开态和 −60° 扇形，与当前源码（V8.13 端点）不一致，本原型已按源码修正。

## 源码迁移落点

文件：`app/src/main/java/com/corgimemo/app/ui/components/SwipeableImageStack.kt`
位置：L2197–2282（Layer-2 全局收起按钮 Box）

改三处：

1. **水平**：`Alignment.TopEnd` → `Alignment.TopStart`，
   `offset(x = -stackStageOffsetX * p)` → `StackLeftCompensation * (1f - p) + INSET`
   （Stage 左缘 = `stackStageOffsetX − StackLeftCompensation×(1−p)`，展开态 p=1 时补偿为 0，
   按钮落在内容视觉左缘）
2. **垂直**：`padding(top = topCardTopInStageDp)` →
   `offset(y = topCardAnchorY.dp + cardHeight / 2 - BTN_SIZE / 2)`
   （不能用 `CenterStart`：Stage 高度含阴影/包围盒余量，Stage 中心 ≠ 图片行中心）
3. **内容**：删除 `Text("收起")`，`Icons.Default.ChevronRight` → `ChevronLeft`，
   胶囊改为圆形（或保留胶囊去文字），`contentDescription` 保留「收起图片堆叠」

点击逻辑（L2235–2263 的行滚动并行归零 + `setExpanded(false)`）保持不变。

## 时间线（原型已还原，对齐 `TimelineInspirationItem.kt` L167–178）

| 参数 | 值 |
| --- | --- |
| `dateColumnWidth` 日期列宽 | 50dp（`2026.08` 12sp + `28` 20sp，Column 居中） |
| `dateToNodeGap` | 7dp |
| `nodeDiameter` 节点直径 | 6dp（普通模式实心，批量模式 16dp 空心） |
| `nodeCenterX` / `timelineLineX` | 60dp（= 50 + 7 + 3） |
| `nodeToContentGap` | 7dp |
| `contentStartX` 内容列左缘 | 70dp（= 60 + 3 + 7） |
| `nodeCenterY` | 11dp（对齐标题行中心） |
| 竖线 | 宽 2dp、色 `#EEEEEE`，向上延伸 18dp 覆盖 item 间距 |

原型里 `.insp-card` 的 `padding-top` 必须为 0，否则节点中心 Y 与标题行中心对不齐（用 `margin-top: 18px` 提供 item 间距）。

**坐标坑（已修复）**：`.tl-line`/`.tl-node`/`.tl-date` 是 `.insp-card` 的绝对定位子元素，定位原点 =
卡片 border box（屏幕 0），而源码里 `timelineLineX=60`、`dateColumnWidth=50` 都相对 item 内容区左缘（屏幕 18）。
因此 left 必须 +18：日期列 `18`、竖线中心 78 → `left: 77`、节点中心 78 → `left: 75`。

## 吸附位置（两档，与形状正交）

用户确认「时间线」= 画出的竖线本身，且按钮在竖线【左侧】、与竖线【相切】（右缘 = 竖线左缘，间隙 0）。

屏幕坐标：item 内容区左缘 = 18dp → 竖线中心 18+60 = 78（宽 2 → 左缘 **77**）、内容列/图片行左缘 18+70 = **88**。

| 档位 | 基准线（屏幕 dp） | 按钮位置 | 占位（W = 按钮宽） |
| --- | --- | --- | --- |
| 图片行左缘 | 88 | 在基准线右侧（覆盖图片行左端） | `[88+inset, 88+inset+W]` |
| 时间线竖线左缘 | 77 | 在基准线**左侧**、右缘相切 | `[77−W, 77]`（间隙 0） |

- 时间线档：半胶囊固定「圆头朝外」（朝屏幕外侧、直边贴竖线）；圆/胶囊右端顶点相切。
- 时间线档 `inset` 无效（固定相切，间隙 0），面板里自动禁用内缩与朝向控件。
- 图片行档：半胶囊「圆头朝内/朝外」仍可用；`inset` 控制距图片行左缘的内缩。

迁移时按钮的 Stage 内 offset：

```kotlin
// 图片行档：offset(x = StackLeftCompensation * (1f - p) + inset)
// 时间线档：按钮右缘贴竖线左缘 → Stage 内左缘 = 77 - W - 88 = -(11 + W)
//   offset(x = StackLeftCompensation * (1f - p) - 11.dp - W)
```

> 时间线档左移 11+W dp，`Stage` 与 `OUTER_BOX` 均已 `graphicsLayer { clip = false }`。
> ⚠️ 已实机确认存在中间层裁切：`animateContentSize` 内部会调用 `clipToBounds()`，**持续裁剪 Stage 内容到布局边界**，
> `graphicsLayer { clip = false }` 挡不住它（clip=false 只影响自身层，挡不住祖先 clipToBounds）。
> 修复：展开态 Stage 左扩 `collapseBtnLeftExtend = 11 + W` + 内容包装层 offset 补偿，按钮展开态 offset 归零落在 Stage 左缘内不被裁。
> 已落地于 `SwipeableImageStack.kt`。

## 三种按钮形状（面板可切换）

| 形状 | 几何 | 水平占位（相对视口左缘 88dp） | Compose 圆角写法 |
| --- | --- | --- | --- |
| 圆形 | W = H = 28dp | `[88+inset, 88+inset+28]` | `RoundedCornerShape(50)`（percent） |
| 胶囊 | W ≈ 1.35H，H = 28dp | `[88+inset, 88+inset+W]` | `RoundedCornerShape(11.dp)` |
| 半胶囊 | **W = H/2**，H 可调（默认 48dp） | 朝内：`[88+inset, 88+inset+W]`；朝外：`[88−inset−W, 88−inset]` | 见下 |

半胶囊的圆角（Compose 参数序为 topStart / topEnd / bottomEnd / bottomStart）：

```kotlin
// 圆头朝内（右侧半圆，直边贴图片行左缘，本体覆盖图片行左端）
RoundedCornerShape(topStart = 0.dp, topEnd = h / 2, bottomEnd = h / 2, bottomStart = 0.dp)
// 圆头朝外（左侧半圆，直边贴图片行左缘，本体落在边缘线外侧的空白区）
RoundedCornerShape(topStart = h / 2, topEnd = 0.dp, bottomEnd = 0.dp, bottomStart = h / 2)
```

- 尺寸用 `Modifier.size(width = h / 2, height = h)`；Compose 的 `RoundedCornerShape`
  在圆角超过该边一半时自动收敛为半圆，不会溢出。
- 图标视觉居中：朝内时图标略右移 2dp，朝外时略左移 2dp（半圆一侧视觉重心偏移）。
- 「内缩 inset」对两种朝向含义一致：均指直边到图片行左缘线的距离。

## 待用户定稿的参数

面板可调，当前默认：inset 8dp、圆形 28dp、背景 `#F2F3F5 @55%`、图标 16dp；
半胶囊默认高 48dp（宽 24dp）、圆头朝内；吸附位置默认图片行左缘 88dp（另一档竖线左缘 77dp，按钮在竖线左侧相切）。

勾选「显示对齐参考线」可看到竖线 77 与图片行 88 两条基准竖线，参考线精确压在竖线上。
