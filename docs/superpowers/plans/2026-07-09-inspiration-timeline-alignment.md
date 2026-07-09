# 灵感时间线对齐方案 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 重构 `TimelineInspirationItem`，将"2026.07"、节点、灵感标题三个元素的中心精准对齐到同一条水平线，并让标签位置根据正文实际行数动态计算

**Architecture:** 用 Box 容器替代 Row，三个目标元素在 height=24dp 的对齐参考容器内用 `Modifier.align(Alignment.Center)` 自动居中；正文用 `onSizeChanged` 测量实际渲染高度，标签 padding top 动态计算

**Tech Stack:** Kotlin, Jetpack Compose 1.9.2, Material3

---

## 文件结构

| 文件 | 变更类型 | 职责 |
|------|---------|------|
| `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt` | Modify | 主组件重构（核心改动）|
| `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt` | Modify | LazyColumn `spacedBy(4.dp)` 调整 |

无新文件创建。Visual self-check 使用真机/模拟器。

---

## Task 1: 修改文件顶部注释 + 添加常量

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt:37-60`

- [ ] **Step 1: 替换文件顶部 KDoc 注释**

将原 KDoc 注释（[TimelineInspirationItem.kt:37-60](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt#L37-L60)）替换为新的 KDoc：

```kotlin
/**
 * 时间线灵感条目组件
 *
 * 布局结构（方案 B - Box + Modifier.align + 动态 offset）：
 * [日期列 52dp] [时间线区域 20dp（节点）] [间距 4dp] [内容区]
 *
 * 三个目标元素（"2026.07"、节点、灵感标题）的中心 Y 对齐到 Y0 = 12dp：
 * - 三个元素都被限制在 height=24dp（targetRowHeight）的对齐参考容器中
 * - 用 Modifier.align(Alignment.Center) 自动居中，无需计算 lineHeight
 *
 * 距离规范（与原型 15-alignment-v15.html 一致）：
 * - "2026.07" 底部 → "08" 顶部 = 1px
 * - 标题底部 → 时间戳顶部 = 1px（固定 offset y=25dp）
 * - 时间戳底部 → 正文顶部 = 2px
 * - 正文底部 → 标签顶部 = 2px（动态，onSizeChanged 测量）
 * - 灵感间距 = 4px（由 LazyColumn spacedBy 控制）
 *
 * 字号体系：标题 16sp / 正文 15sp / 时间戳 14sp / 标签 11sp / "2026.07" 15sp / "08" 22sp
 *
 * @param inspiration 灵感实体数据
 * @param tags 标签列表
 * @param imagePaths 图片路径列表
 * @param formattedTime 格式化后的时间字符串
 * @param showDate 是否显示左侧日期列（同一天多条时仅第一条显示）
 * @param isPinnedItem 是否为置顶项
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 * @param modifier 修饰符
 */
```

- [ ] **Step 2: 添加新 import**

在文件顶部 import 块末尾添加：

```kotlin
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
```

- [ ] **Step 3: 验证文件可编译（仅语法检查）**

读取文件确认 KDoc 和 import 正确添加。无需运行 gradle（按 workspace 规则"编译验证"）。

- [ ] **Step 4: 暂不 commit，等待 Task 2-9 一并提交**

---

## Task 2: 重构主布局 Box 容器（替换原 Row）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt:73-118`

- [ ] **Step 1: 替换 `TimelineInspirationItem` 函数体的主布局**

将原 [TimelineInspirationItem.kt:73-118](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt#L73-L118) 整段（包括 Row、drawBehind、日期列 Box、时间线 Box、Spacer）替换为新的 Box 布局：

```kotlin
    // 三个目标元素统一在 height=24dp 的对齐参考容器中居中
    val targetRowHeight = 24.dp  // 16sp 标题 lineHeight

    // 距离常量
    val timeStampOffsetY = 25.dp  // 标题底部 12dp + 1px + 时间戳 lineHeight 12dp
    val contentTopOffsetY = timeStampOffsetY + 14.dp + 2.dp  // 时间戳底部 + 2px

    // 正文实际渲染高度（用于标签动态定位）
    var contentHeightPx by remember { mutableIntStateOf(0) }

    // 时间线竖线的X坐标：日期列52dp + 时间线区域一半10dp = 62dp
    val timelineLineX = 62.dp
    val timelineLineColor = Color(0xFFEEEEEE)
    val nodeColor = if (isPinnedItem) Color(0xFFFF9A5C) else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            // drawBehind 在 padding 之前，绘制范围包括 padding 区域
            .drawBehind {
                val x = timelineLineX.toPx()
                drawLine(
                    color = timelineLineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
            // 关键：去掉 padding(vertical = 8.dp)，间距改由 LazyColumn spacedBy(4.dp) 控制
    ) {
        // 子元素在后续 Task 中添加（日期列、节点、内容区）
    }
}
```

注意：先保留 `}` 闭合，函数体后续 Task 中继续填充。

- [ ] **Step 2: 验证函数签名未变**

确认 `TimelineInspirationItem` 函数的参数列表（`inspiration, tags, imagePaths, formattedTime, showDate, isPinnedItem, onClick, onLongClick, modifier`）与原签名完全一致。

- [ ] **Step 3: 暂不 commit**

---

## Task 3: 实现日期列（"2026.07" + "08"）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt`（在 Task 2 的 Box 内部）

- [ ] **Step 1: 在 Box 内添加日期列子元素**

在 `Box(` 内部、注释 `// 子元素在后续 Task 中添加` 处，添加日期列：

```kotlin
        // ========== 日期列（"2026.07" 居中 + "08" 跟随）==========
        if (showDate || isPinnedItem) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .height(targetRowHeight)
                    .align(Alignment.TopStart)
            ) {
                if (isPinnedItem) {
                    // 置顶项显示 PushPin 图标
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "已置顶",
                        tint = Color(0xFFFF9A5C),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(20.dp)
                    )
                } else {
                    // "2026.07" 居中（中心 Y = 12dp）
                    Text(
                        text = String.format("%04d.%02d", getYear(inspiration.createdAt), getMonth(inspiration.createdAt)),
                        fontSize = 15.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    // "08" 跟随"2026.07"下方 1px
                    Text(
                        text = String.format("%02d", getDay(inspiration.createdAt)),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = (targetRowHeight / 2) + 1.dp)
                    )
                }
            }
        }
```

- [ ] **Step 2: 添加日期辅助函数**

在文件末尾（`removeHtmlTags` 函数之前）添加：

```kotlin
/**
 * 从时间戳提取年份
 */
private fun getYear(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.YEAR)
}

/**
 * 从时间戳提取月份（1-12）
 */
private fun getMonth(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.MONTH) + 1
}

/**
 * 从时间戳提取日（1-31）
 */
private fun getDay(timestamp: Long): Int {
    return Calendar.getInstance().apply { timeInMillis = timestamp }
        .get(Calendar.DAY_OF_MONTH)
}
```

- [ ] **Step 3: 暂不 commit**

---

## Task 4: 实现节点（居中到 Y0 = 12dp）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt`（在 Task 3 的日期列后）

- [ ] **Step 1: 在 Box 内添加节点子元素**

在日期列 Box 之后添加：

```kotlin
        // ========== 节点（8dp 圆点，居中到 Y0 = 12dp）==========
        Box(
            modifier = Modifier
                .offset(x = 52.dp)  // 日期列右侧
                .size(20.dp, targetRowHeight)
                .align(Alignment.TopStart)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(8.dp)
                    .background(color = nodeColor, shape = CircleShape)
            )
        }
```

- [ ] **Step 2: 暂不 commit**

---

## Task 5: 实现标题（居中到 Y0 = 12dp）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt`（在 Task 4 的节点后）

- [ ] **Step 1: 在 Box 内添加标题子元素**

在节点 Box 之后添加：

```kotlin
        // ========== 内容区（包含标题、时间戳、正文、标签、图片）==========
        // 标题：居中到 Y0 = 12dp
        Box(
            modifier = Modifier
                .padding(start = 76.dp)  // 52dp 日期 + 20dp 时间线 + 4dp 间距
                .height(targetRowHeight)
                .align(Alignment.TopStart)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center)
            ) {
                // 置顶标识（标题前）
                if (isPinnedItem) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = "已置顶",
                        tint = Color(0xFFFF9A5C),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                // 标题
                Text(
                    text = inspiration.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
```

- [ ] **Step 2: 暂不 commit**

---

## Task 6: 实现时间戳（固定 offset y=25dp）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt`（在 Task 5 的标题后）

- [ ] **Step 1: 添加时间戳 Text**

在标题 Box 之后添加：

```kotlin
        // 时间戳：标题底部 + 1px（固定 offset y=25dp）
        Text(
            text = formattedTime,
            fontSize = 14.sp,
            color = Color(0xFF999999),
            modifier = Modifier
                .padding(start = 76.dp, top = timeStampOffsetY)
                .align(Alignment.TopStart)
        )
```

- [ ] **Step 2: 暂不 commit**

---

## Task 7: 实现正文（onSizeChanged 测量高度）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt`（在 Task 6 的时间戳后）

- [ ] **Step 1: 添加正文 Text（带 onSizeChanged）**

在时间戳 Text 之后添加：

```kotlin
        // 正文：时间戳底部 + 2px；onSizeChanged 测量实际高度用于标签定位
        if (inspiration.content.isNotBlank()) {
            val plainContent = removeHtmlTags(inspiration.content)
            Text(
                text = plainContent,
                fontSize = 15.sp,
                color = Color(0xFF666666),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 76.dp, top = contentTopOffsetY)
                    .align(Alignment.TopStart)
                    .onSizeChanged { size ->
                        contentHeightPx = size.height
                    }
            )
        }
```

- [ ] **Step 2: 暂不 commit**

---

## Task 8: 实现标签（动态 offset + 最多 3 个 + 新内边距 6dp×2dp）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt`（在 Task 7 的正文后）

- [ ] **Step 1: 添加标签 Row（动态 offset）**

在正文 Text 之后添加：

```kotlin
        // 标签：正文底部 + 2px（动态 offset 由 contentHeightPx 决定）
        if (tags.isNotEmpty()) {
            val tagTopOffsetY = (contentTopOffsetY + contentHeightPx.toDp() + 2.dp)
                .coerceAtLeast(contentTopOffsetY)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(start = 76.dp, top = tagTopOffsetY)
                    .align(Alignment.TopStart)
            ) {
                // 最多显示 3 个标签
                tags.take(3).forEach { tag ->
                    Text(
                        text = "#$tag",
                        fontSize = 11.sp,
                        color = UiColors.Primary,
                        modifier = Modifier
                            .background(
                                color = Color(0xFFFFF3E0),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                // 超出 3 个显示 "+N"
                if (tags.size > 3) {
                    Text(
                        text = "+${tags.size - 3}",
                        fontSize = 11.sp,
                        color = Color(0xFF999999),
                        modifier = Modifier
                            .background(
                                color = Color(0xFFF5F5F5),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
```

- [ ] **Step 2: 暂不 commit**

---

## Task 9: 实现图片缩略图（保持原样，位置调整）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt`（在 Task 8 的标签后）

- [ ] **Step 1: 添加图片缩略图 Row**

在标签 Row 之后添加：

```kotlin
        // 图片缩略图：标签底部 + 4px
        if (imagePaths.isNotEmpty()) {
            val imageTopOffsetY = if (tags.isNotEmpty()) {
                (contentTopOffsetY + contentHeightPx.toDp() + 2.dp + 22.dp + 4.dp)
                    .coerceAtLeast(contentTopOffsetY)
            } else {
                (contentTopOffsetY + contentHeightPx.toDp() + 2.dp)
                    .coerceAtLeast(contentTopOffsetY)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(start = 76.dp, top = imageTopOffsetY)
                    .align(Alignment.TopStart)
            ) {
                imagePaths.take(2).forEach { _ ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🖼️", fontSize = 12.sp)
                    }
                }
                if (imagePaths.size > 2) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFEEEEEE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+${imagePaths.size - 2}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF666666)
                        )
                    }
                }
            }
        }
```

- [ ] **Step 2: 暂不 commit**

---

## Task 10: 删除原 `TimelineDateColumn` 函数

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt:278-309`

- [ ] **Step 1: 删除原 `TimelineDateColumn` 函数**

删除 [TimelineInspirationItem.kt:278-309](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt#L278-L309) 整个 `private fun TimelineDateColumn` 函数（已由 Task 3 中的日期列 Box 替代）。

- [ ] **Step 2: 暂不 commit**

---

## Task 11: 调整 InspirationScreen LazyColumn 间距

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt:120-125`

- [ ] **Step 1: 修改 LazyColumn `Arrangement.spacedBy`**

将 [InspirationScreen.kt:124](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt#L124) 的 `verticalArrangement = Arrangement.spacedBy(0.dp)` 修改为：

```kotlin
                    verticalArrangement = Arrangement.spacedBy(4.dp)
```

- [ ] **Step 2: 暂不 commit**

---

## Task 12: 完整文件 review（一次性 commit）

**Files:**
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt`
- Modify: `app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt`

- [ ] **Step 1: 完整读取 `TimelineInspirationItem.kt`**

读取整个文件，确认：
- 顶部 KDoc 已更新
- import 包含 `mutableIntStateOf`, `getValue`, `setValue`, `remember`, `onSizeChanged`
- 主函数 `TimelineInspirationItem` 内部结构符合 Task 2-9
- `TimelineDateColumn` 已删除
- `getYear`, `getMonth`, `getDay` 已添加
- `removeHtmlTags` 保留

- [ ] **Step 2: 完整读取 `InspirationScreen.kt` 第 120-125 行**

确认 `verticalArrangement = Arrangement.spacedBy(4.dp)` 已设置。

- [ ] **Step 3: 询问用户是否需要编译验证**

按 workspace 规则"编译验证"，**不允许擅自执行编译命令**。使用 `AskUserQuestion` 询问用户是否进行编译。

- [ ] **Step 4: 一次性 commit 所有改动**

在用户确认编译通过（或不需要编译）后，执行：

```bash
git add app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt
git add app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt
git commit -F <(echo "feat: 灵感时间线对齐方案 B 重构

- 三个目标元素（2026.07、节点、标题）中心精准对齐到 Y0=12dp
- 标签位置根据正文实际行数动态计算
- 字号、内边距、标签数量按原型规范调整
- 灵感间距改由 LazyColumn spacedBy(4.dp) 控制
- 删除冗余的 TimelineDateColumn 函数")
```

---

## Task 13: 视觉验证（推荐真机/模拟器）

**Files:** 无

- [ ] **Step 1: 在真机/模拟器上运行 APP**

- [ ] **Step 2: 录入 5 个真实语料（参考原型 15-alignment-v15.html）**

| # | 日期 | 标题 | 标签 |
|---|------|------|------|
| 1 | 07.08 | 周报模板优化思路 | #工作 #效率 |
| 2 | 07.08 | 冬至吃饺子啦 | #节日 #生活 |
| 3 | 07.09 | 高数笔记：泰勒公式 | #学习 #数学 |
| 4 | 07.10 | 女儿教我视频通话 | #家庭 #生活 |
| 5 | 07.10 | 柯基陪伴系统思考 | #产品 #设计 #思考 |

- [ ] **Step 3: 截图与原型对比**

- [ ] **Step 4: 验证清单**

- [ ] "2026.07"、节点、标题中心精准在同一条水平线
- [ ] "08" 在"2026.07"下方 1px
- [ ] 时间戳在标题下方 1px
- [ ] 正文在时间戳下方 2px
- [ ] 标签在正文最后一行下方 2px
- [ ] 标签最多 3 个，超出显示 "+N"
- [ ] 标签内边距 6dp×2dp
- [ ] 竖线贯通所有相邻条目
- [ ] 灵感之间间距 4px

---

## 验收标准

1. **代码完整性**：`TimelineInspirationItem.kt` 编译通过；`InspirationScreen.kt` LazyColumn spacedBy 调整生效
2. **视觉对齐**：三个目标元素中心在同一条水平线
3. **动态标签**：标签紧贴正文最后一行 + 2px
4. **样式统一**：标签最多 3 个、内边距 6dp×2dp
5. **间距一致**：灵感间距 4px
6. **竖线连贯**：时间线竖线贯通所有相邻条目
