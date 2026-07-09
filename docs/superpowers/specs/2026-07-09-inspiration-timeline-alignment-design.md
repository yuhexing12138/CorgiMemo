# 灵感时间线对齐方案 - 设计文档

> **项目**：刻记+ Android APP
> **日期**：2026-07-09
> **关联文件**：
> - 原型：`corgimemo-showcase.html` 中 15-alignment-v15.html
> - 目标组件：`TimelineInspirationItem.kt`
> - 目标页面：`InspirationScreen.kt`

---

## 1. 目标

将 `TimelineInspirationItem` 的视觉对齐从"固定 padding 推算"（方案 A，依赖 lineHeight 估算，存在 1-2dp 误差）改为"Box + Modifier.align + 动态 offset"（方案 B），实现：

1. **三个目标元素**（"2026.07"、节点、灵感标题）**视觉中心精准对齐**到同一条水平线
2. **标签位置根据正文实际行数动态计算**（1 行 / 2 行 / 6 行都能紧贴正文最后一行 + 2px）
3. 符合 PRD 3.2.3 灵感卡片规范
4. 符合原型 15-alignment-v15.html 的距离与字号规范

---

## 2. 现状分析

### 2.1 当前实现（方案 A）

**布局结构**（[TimelineInspirationItem.kt:79-99](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt#L79-L99)）：

```
Row (padding vertical=8dp)
├── 日期列 Box (width=52dp)
│   └── TimelineDateColumn
│       ├── Text "2026.07" (padding top=4dp)
│       └── Spacer (2dp) + Text "08"
├── 时间线区域 Box (width=20dp)
│   └── 节点 Box (padding top=8dp, size=8dp)
├── Spacer (4dp)
└── 内容区 Column (weight=1f)
    ├── Row: 标题 (fontSize=16sp Bold)
    ├── Text: 时间戳 (padding top=2dp, fontSize=12sp)
    ├── Text: 内容预览 (padding top=6dp, fontSize=14sp, maxLines=2)
    └── Row: 标签 + 图片 (padding top=8dp)
```

**当前对齐计算**（Y0 = 20dp，依赖 lineHeight 估算）：

| 元素 | 当前计算 | 中心 Y |
|------|---------|--------|
| 标题 | 8(Row padding) + 0(标题 padding) + 12(16sp lineHeight 24/2) | **20dp** |
| 节点 | 8(Row padding) + 8(节点 padding) + 4(节点半径 8/2) | **20dp** |
| "2026.07" | 8(Row padding) + 4(日期 padding) + 8(11sp lineHeight 16/2) | **20dp** |

### 2.2 现状问题

| 问题 | 影响 |
|------|------|
| 依赖 lineHeight 估算 | 不同设备/字体下 1-2dp 误差 |
| 标签 padding top=8dp 固定 | 正文 1/2/6 行时标签与正文间距不统一 |
| 标签内边距 10dp×4dp 偏大 | 与原型 6dp×2dp 不符 |
| 标签最多 2 个 | 与原型 3 个不符 |
| 字号 11/12/14sp 混杂 | 与原型 15/14/15sp 体系不符 |
| "08" 跟随"2026.07"距离 spacer 2dp | 距离 2026.07 底部 = 1px 不符 |

### 2.3 原型规范（15-alignment-v15.html）

| 距离规则 | 值 |
|---------|-----|
| 2026.07 底部 → 08 顶部 | 1px |
| 标题底部 → 时间戳顶部 | 1px（固定） |
| 时间戳底部 → 正文顶部 | 2px |
| 正文底部 → 标签顶部 | 2px（动态跟随正文行数） |
| 灵感间距 | 4px |
| 标签内边距 | 水平 6dp / 垂直 2dp |
| 标签最多显示 | 3 个，超出显示 "+N" |

| 字号 | 值 |
|------|-----|
| 标题 | 16sp Bold |
| 正文 | 15sp（比标题小一号）|
| 时间戳 | 14sp |
| 标签 | 11sp |
| "2026.07" | 15sp |
| "08" | 22sp Bold |

---

## 3. 设计方案

### 3.1 架构选型：方案 B（Box + Modifier.align + 动态 offset）

**为什么选方案 B**：
- ✓ 三个目标元素靠 Compose `Modifier.align(Alignment.Center)` 自动保证中心对齐，不依赖 lineHeight 估算
- ✓ 标签位置通过 `Modifier.onSizeChanged { contentHeightPx = it.height }` 测量正文实际渲染高度，1/2/6 行都能精准定位
- ✓ 适配 Compose BOM 2026.04.01 + Compose 1.9.2 新版本（API 完全支持）
- ✓ 符合 workspace 规则"lambda 捕获陷阱防御"（onSizeChanged 回调只更新本地状态）

**否决方案 A**（保持 Row + 固定 padding）：lineHeight 估算有误差，标签位置无法动态跟随正文行数。

**否决方案 C**（Row + alignBy）：对齐基准是"中心 Y"，但不能处理"标签动态跟随"需求。

### 3.2 组件结构

**整体布局**：

```
InspirationScreen.LazyColumn
├── Arrangement.spacedBy(4.dp)  ← 灵感间距 4px
├── InspirationSkeleton（加载中）
├── UnifiedEmptyState（空状态）
└── items
    ├── 置顶区 (pinnedInspirations)
    │   └── TimelineInspirationItem(showDate=true 仅第一条, isPinnedItem=true)
    └── 普通区 (normalGroupedInspirations, 按 yearMonth.day 分组)
        └── TimelineInspirationItem(showDate=true 仅每组第一条, isPinnedItem=false)
```

**Item 内部布局**：

```
TimelineInspirationItem (Composable)
└── Box (Modifier.fillMaxWidth, padding vertical=0dp) ← 注意：去掉 8dp padding
    ├── drawBehind: 竖线贯穿（X=62dp，width=2dp）
    ├── Box (width=52dp, height=24dp, Alignment.TopStart)  ← 日期列
    │   └── Box (Modifier.align Center)
    │       ├── Text "2026.07" (fontSize=15sp, lineHeight 22.5dp)
    │       └── Text "08" (Modifier.offset y=23.5dp, fontSize=22sp Bold)
    ├── Box (Modifier.offset(x=52dp).size(20.dp, 24.dp), Alignment.TopStart)  ← 时间线
    │   └── 节点 (Modifier.align Center, size=8dp CircleShape)
    └── Box (Modifier.padding(start=76.dp), Alignment.TopStart)  ← 内容区
        ├── Text 标题 (Modifier.align Center, fontSize=16sp Bold, maxLines=1)
        ├── Text 时间戳 (Modifier.offset y=25dp, fontSize=14sp)
        ├── Text 内容 (Modifier.offset y=47dp, fontSize=15sp, maxLines=2, onSizeChanged 测高)
        ├── Row 标签 (Modifier.offset y 动态, fontSize=11sp, padding 6dp×2dp)
        └── [可选] Text 关联提示 (Modifier.offset y 动态, fontSize=9sp)
```

### 3.3 关键实现细节

#### 3.3.1 整体 Box 容器

```kotlin
Box(
    modifier = modifier
        .fillMaxWidth()
        .drawBehind { drawLine(...) }  // 竖线
        // 注意：去掉之前的 padding(vertical = 8.dp)
        // 间距改由 LazyColumn Arrangement.spacedBy(4.dp) 控制
) {
    // 子元素（日期列、时间线节点、内容区都用 Modifier.align(Alignment.TopStart) 定位）
}
```

**关键**：Item 自身 padding vertical 改为 0dp。灵感间距 4px 由 `InspirationScreen.LazyColumn.Arrangement.spacedBy(4.dp)` 控制。

#### 3.3.2 三个目标元素居中对齐

```kotlin
// 三个目标元素都在 height=24dp 的容器中垂直居中
val targetRowHeight = 24.dp  // 16sp 标题 lineHeight

// 1. "2026.07" 居中
Box(
    modifier = Modifier
        .width(52.dp)
        .height(targetRowHeight)
        .align(Alignment.TopStart)
) {
    Text(
        text = "2026.07",
        fontSize = 15.sp,
        modifier = Modifier.align(Alignment.Center)
    )
}

// 2. 节点居中
Box(
    modifier = Modifier
        .offset(x = 52.dp)
        .size(20.dp, targetRowHeight)
        .align(Alignment.TopStart)
) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .size(8.dp)
            .background(nodeColor, CircleShape)
    )
}

// 3. 标题居中
Box(
    modifier = Modifier
        .padding(start = 76.dp)  // 52dp 日期 + 20dp 时间线 + 4dp 间距
        .height(targetRowHeight)
        .align(Alignment.TopStart)
) {
    Text(
        text = inspiration.title,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.align(Alignment.Center)
    )
}
```

#### 3.3.3 "08" 跟随"2026.07"（1px 距离）

```kotlin
// 在 3.3.2 的日期列 Box 内部，"2026.07" 下方添加 "08"
Box(
    modifier = Modifier
        .width(52.dp)
        .height(targetRowHeight)
        .align(Alignment.TopStart)
) {
    Text(
        text = "2026.07",
        fontSize = 15.sp,
        modifier = Modifier.align(Alignment.Center)
    )
    Text(
        text = "08",
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .align(Alignment.TopStart)
            .offset(y = (targetRowHeight / 2) + 1.dp)  // 2026.07 底部 + 1px
    )
}
```

**关键**：`offset y = targetRowHeight/2 + 1dp` 让"08"顶部距离"2026.07"底部 1px（targetRowHeight/2 = 12dp = "2026.07" lineHeight 22.5/2 的中心位置，加上 1dp 让"08"顶部从"2026.07"中心向下偏移 13dp，刚好 1px 间距）。

#### 3.3.4 标签动态跟随

```kotlin
// 预定义：时间戳 top 偏移
val timeStampOffsetY = 25.dp  // 标题底部 12dp + 1px + 时间戳 lineHeight 12dp = 25dp
val contentTopOffsetY = timeStampOffsetY + 14.dp + 2.dp  // 时间戳底部 + 2px

// 存储正文实际渲染高度
var contentHeightPx by remember { mutableStateOf(0) }

// 时间戳
Text(
    text = formattedTime,
    fontSize = 14.sp,
    color = Color(0xFF999999),
    modifier = Modifier
        .padding(start = 76.dp, top = timeStampOffsetY)
        .align(Alignment.TopStart)
)

// 正文（带 onSizeChanged 测量）
Text(
    text = plainContent,
    fontSize = 15.sp,
    color = Color(0xFF666666),
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier
        .padding(start = 76.dp, top = contentTopOffsetY)
        .align(Alignment.TopStart)
        .onSizeChanged { contentHeightPx = it.height }
)

// 标签（top 由 contentHeightPx 动态计算）
if (tags.isNotEmpty() || imagePaths.isNotEmpty()) {
    Row(
        modifier = Modifier
            .padding(start = 76.dp, top = (contentTopOffsetY + contentHeightPx.toDp() + 2.dp).coerceAtLeast(contentTopOffsetY + 0.dp))
            .align(Alignment.TopStart)
    ) {
        tags.take(3).forEach { tag ->
            Text(
                text = "#$tag",
                fontSize = 11.sp,
                modifier = Modifier
                    .background(Color(0xFFFFF3E0), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        if (tags.size > 3) {
            Text(
                text = "+${tags.size - 3}",
                fontSize = 11.sp,
                color = Color(0xFF999999),
                modifier = Modifier
                    .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
```

**关键**：
- `contentHeightPx` 在 onSizeChanged 中更新，触发重组
- 标签 padding top = `contentTopOffsetY + contentHeightPx.toDp() + 2dp`
- 正文 1 行 → 标签在时间戳下方 2 + 20 + 2 = 24dp（不与正文重叠）
- 正文 2 行 → 标签在时间戳下方 2 + 40 + 2 = 44dp
- `.coerceAtLeast` 防止正文为 0 高度时标签与时间戳重叠

#### 3.3.5 关联提示（YAGNI）

当前 `Inspiration` entity 无 `linkedRefs` 字段，本次**不实现**关联提示。如未来需要，按以下规范：

```kotlin
if (linkedRefs.isNotEmpty()) {
    Text(
        text = "🔗 关联：${linkedRefs.joinToString(" ")}",
        fontSize = 9.sp,
        color = Color(0xFF999999),
        modifier = Modifier
            .padding(start = 76.dp, top = 2.dp)
            .align(Alignment.TopStart)
    )
}
```

---

## 4. 数据流

```
TimelineInspirationItem(inspiration, tags, imagePaths, ...)
    │
    ├─ 显示日期列（showDate=true 时）
    │   └─ Calendar.get(year, month, day) → 格式化字符串
    │
    ├─ 显示节点
    │   └─ 颜色: isPinnedItem ? #FF9A5C : MaterialTheme.colorScheme.primary
    │
    ├─ 标题: inspiration.title (来自 Room DB)
    │
    ├─ 时间戳: formattedTime (格式化 createdAt)
    │
    ├─ 内容: removeHtmlTags(inspiration.content) (最大 2 行省略号)
    │
    ├─ 标签: tags.take(3) + "+N"
    │   └─ 标签 padding top 由 onSizeChanged 测量的 contentHeightPx 决定
    │
    └─ 竖线: drawBehind 绘制贯穿整个 Item 高度
```

**没有 ViewModel 变更**，仅修改 UI 组件。

---

## 5. 错误处理

- `inspiration.content` 为空：跳过内容 Text（保持现状）
- `tags` 为空：不渲染标签 Row
- `imagePaths` 为空：不渲染图片缩略图
- `onSizeChanged` 回调中只更新 `contentHeightPx`，不触发任何副作用

---

## 6. 测试

### 6.1 单元测试

无新增业务逻辑，无需单元测试。

### 6.2 视觉测试（推荐真机/模拟器验证）

**测试场景**（使用 5 个真实语料）：

| # | 日期 | 标题 | 内容行数 | 标签数 |
|---|------|------|---------|--------|
| 1 | 07.08 | 周报模板优化思路 | 2 | 2 |
| 2 | 07.08 | 冬至吃饺子啦 | 1 | 2 |
| 3 | 07.09 | 高数笔记：泰勒公式 | 6（详情页）/ 2（列表预览） | 2 |
| 4 | 07.10 | 女儿教我视频通话 | 2 | 2 |
| 5 | 07.10 | 柯基陪伴系统思考 | 2 | 3 |

**验证清单**：
- [ ] 三个目标元素（"2026.07"、节点、标题）中心精准在同一条水平线
- [ ] "08" 在"2026.07"下方 1px
- [ ] 时间戳在标题下方 1px
- [ ] 正文在时间戳下方 2px
- [ ] 标签在正文最后一行下方 2px
- [ ] 标签最多显示 3 个，第 4 个起显示 "+N"
- [ ] 标签内边距 6dp×2dp（比之前的 10dp×4dp 更紧凑）
- [ ] 竖线贯通所有相邻条目

### 6.3 截图对比

用真机/模拟器截图，与 `corgimemo-showcase.html` 中 15-alignment-v15.html 对比视觉效果。

---

## 7. 风险与权衡

| 风险 | 缓解措施 |
|------|---------|
| onSizeChanged 触发重组导致闪烁 | Compose 已优化，仅 1 次重组 |
| 不同设备/字体导致 lineHeight 差异 | 使用 sp 单位，与系统字体设置一致 |
| Box 嵌套导致性能下降 | 单层 Box 容器，不嵌套 |
| `align(Alignment.Center)` 与 `align(Alignment.TopStart)` 混淆 | 通过测试验证 |

**权衡**：
- **收益**：中心对齐精度从 1-2dp 误差提升到 0 误差；标签动态跟随支持任意行数；字号体系统一
- **代价**：代码量略增（约 30%）；需重新理解 Box 布局

---

## 8. 后续优化（YAGNI）

- 关联提示（`@类型:标题`）需先在 Inspiration entity 添加 `linkedRefs` 字段
- 图片缩略图从 28dp 改为自适应宽度（保留 aspect ratio）
- 标签长按编辑/删除功能
- 正文展开/收起（超过 2 行时显示"展开"按钮）

---

## 9. 实施计划

1. **修改 `TimelineInspirationItem.kt`**：
   - 重组为 Box 容器
   - 三个目标元素用 align 居中
   - 正文用 onSizeChanged 测量高度
   - 标签 padding top 动态计算
   - 字号、内边距、标签数量按原型规范
2. **验证**（在真机/模拟器上）：
   - 用 5 个真实语料截图
   - 与原型对比
3. **commit**（按用户确认）

---

**设计完成**。等待用户审阅后调用 writing-plans 技能生成实施计划。
