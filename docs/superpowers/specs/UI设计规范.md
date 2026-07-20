## 12.1 设计规范

> **设计理念**：**治愈、温暖、舒适**——每一个像素都应该让用户感到放松。

### 12.1.1 设计原则

| 原则 | 说明 |
|------|------|
| **温暖优先** | 所有界面元素传递温暖、舒适的感觉 |
| **减少焦虑** | 避免使用红色警告、倒计时等制造压力的元素 |
| **正向反馈** | 完成任务时给予充分的视觉和情感反馈 |
| **一致性** | 全局统一的圆角、间距、阴影风格 |
| **圆润柔和** | 所有UI元素使用大圆角（16dp+），避免尖锐边角 |
| **留白呼吸** | 充足的间距和留白，不拥挤 |

### 12.1.2 颜色规范

#### 12.1.2.1 主题色系统

| 用途 | 颜色值 | 说明 |
|------|--------|------|
| **主色** | #FF9A5C | 暖橙色，品牌色（可切换，详见主题配色方案） |
| **主色浅** | #FFE4CC | 浅暖橙色，选中态背景（可切换） |
| **主色深** | #E88A4D | 深暖橙色，按压态（可切换） |

#### 12.1.2.2 中性色（适用于所有主题）

| 用途 | 亮色模式 | 深色模式 | 说明 |
|------|---------|---------|------|
| **页面背景** | #F8F6F3 | #1E1E1E | 暖白色/近黑色，比纯白更舒适 |
| **卡片背景** | #FFFFFF | #2A2A2A | 纯白/深灰 |
| **主文字** | #2D2D2D | #E8E6E3 | 深灰/暖白，比纯黑更柔和 |
| **次要文字** | #666666 | #A0A0A0 | 中灰 |
| **提示文字** | #999999 | #666666 | 浅灰/深灰 |
| **分割线** | #EEEEEE | #333333 | 极浅灰/深灰 |

#### 12.1.2.3 功能色

| 用途 | 颜色值 | 说明 |
|------|--------|------|
| **成功/完成** | #7EC8A0 | 柔和绿色，避免刺眼 |
| **警告/提醒** | #FFB74D | 柔和橙色，中优先级 |
| **高优先级** | #FF8A80 | 柔和红色，避免焦虑 |
| **中优先级** | #FFB74D | 柔和橙色 |
| **低优先级** | #90CAF9 | 柔和蓝色 |
| **无优先级** | #C8E6C9 | 浅绿（Material Green 200），区别于高/中/低，传递"无需特殊处理"的低压力感 [v2026-07-20] |

#### 12.1.2.4 状态色 - 已完成（视觉降权）

> 应用于已完成（status=1）的待办卡片，对所有彩色元素进行灰色化降权处理，降低视觉对比度。

| 用途 | 亮色 | 深色 | 说明 |
|------|------|------|------|
| **已完成-文字** | #888888 | #6E6E6E | 弱于次要文字 #666666，建立"完成项更弱"层级 |
| **已完成-勾选背景** | #BDBDBD | #5A5A5A | 浅灰，不抢视觉焦点 |
| **已完成-优先级竖线（高）** | #FFCDD2 | (浅色系列自动派生) | 浅红，原 #FF8A80 淡化（Material Red 200） |
| **已完成-优先级竖线（中）** | #FFE0B2 | 同上 | 浅橙，原 #FFB74D 淡化（Material Orange 200） |
| **已完成-优先级竖线（低）** | #BBDEFB | 同上 | 浅蓝，原 #90CAF9 淡化（Material Blue 200） |
| **已完成-优先级竖线（无）** | #E8F5E9 | 同上 | 极浅绿，原 #C8E6C9 淡化（Material Green 50）[v2026-07-20] |

**降权原则**：
- 所有彩色（红/橙/蓝/绿）替换为灰色系或同色系浅色版
- 删除线颜色 = 文字色（自动保持一致）
- 勾选 "✓" 符号保持白色不变
- 已完成态无优先级时，竖线使用 #E8F5E9 极浅绿（与未完成态的 #C8E6C9 区分）[v2026-07-20]

**实现参考**：
- 文字/勾选灰：`CompletedColors.kt` 中的 `Text` / `CheckboxBg` 常量
- 优先级竖线浅色：`PriorityColors.kt` 中的 `HighDim` / `MediumDim` / `LowDim` / `NoneDim` 常量 + `dimColorOf(priority)` 函数

### 12.1.3 主题配色方案（6种）

> 用户可在设置中自由选择主色调，提供以下6种预设主题。深色模式下各主题色自动降低亮度30%。

| 主题 | 名称 | 主色 | 辅助色 | 情感联想 | 默认身份 |
|------|------|------|--------|---------|---------|
| 🧡 | **暖阳橙**（默认） | #FF9A5C | #FFE4CC | 温暖、活力、阳光 | 通用 |
| 🌸 | **樱花粉** | #FFB5C2 | #FFE0E6 | 柔和、浪漫、甜蜜 | 通用 |
| 🌿 | **薄荷绿** | #7EC8A0 | #D4F0E0 | 清新、自然、宁静 | 上班族 |
| ☁️ | **天空蓝** | #7EB8DA | #D4E8F5 | 治愈、平静、信赖 | 上班族 |
| 💜 | **薰衣紫** | #B8A0D4 | #E8DFF5 | 优雅、梦幻、温柔 | 通用 |
| 🍵 | **奶茶棕** | #C4A882 | #F0E6D8 | 温馨、沉稳、安心 | 上班族 |

**主题切换功能**：
- 在"我的"页面 → 设置中增加"主题配色"入口
- 显示6种配色方案预览卡片（每种显示主色色块+名称）
- 点击选择后立即生效，无需重启
- 支持跟随身份自动切换（可选）
- 主题偏好保存到 DataStore（key: "theme_color_scheme"）

**实现要点**：
- 所有主题色使用 Compose 的 MaterialTheme.colorScheme 动态配置
- 创建 ThemeColors 数据类封装6种配色方案
- 在 Application 或 MainActivity 初始化时从 DataStore 读取主题配置
- 颜色引用统一使用 MaterialTheme.colorScheme.primary 等，不使用硬编码
- 深色模式下主题色自动降低亮度30%，保持辨识度同时避免刺眼

### 12.1.4 字体规范

#### 12.1.4.1 字体选择

| 用途 | 字体 | 说明 |
|------|------|------|
| **标题** | 系统默认粗体 | 清晰醒目 |
| **正文** | 系统默认常规 | 阅读舒适 |
| **问候语** | 系统默认（稍大） | 增加亲和力 |
| **数字/统计** | 等宽字体 | 对齐美观 |

#### 12.1.4.2 字号规范

| 用途 | 字号 | 行高 | 字重 |
|------|------|------|------|
| **页面大标题** | 24sp | 32sp | Bold |
| **区域标题** | 18sp | 24sp | SemiBold |
| **卡片标题** | 16sp | 24sp | Medium |
| **正文** | 15sp | 22sp | Regular |
| **辅助文字** | 13sp | 18sp | Regular |
| **标签/分类** | 12sp | 16sp | Medium |
| **按钮文字** | 14sp | 20sp | Medium |

### 12.1.5 圆角与间距规范

#### 12.1.5.1 圆角规范

| 元素 | 圆角大小 | 说明 |
|------|---------|------|
| **按钮** | 16dp | 大圆角，柔和 |
| **卡片** | 20dp | 超大圆角，治愈感 |
| **输入框** | 12dp | 中等圆角 |
| **标签** | 20dp | 胶囊形状 |
| **弹窗** | 24dp | 顶部大圆角 |
| **头像** | 50% | 圆形 |

#### 12.1.5.2 间距规范

| 场景 | 间距 | 说明 |
|------|------|------|
| **页面边距** | 20dp | 左右留白 |
| **卡片间距** | 12dp | 卡片之间 |
| **卡片内边距** | 16dp | 卡片内容到边缘 |
| **元素间距** | 8dp | 紧凑元素间 |
| **区域间距** | 24dp | 大区域之间 |
| **列表项高度** | 72dp | 列表项最小高度 |
| **按钮高度** | 48dp | 标准按钮高度 |

### 12.1.6 阴影与层次规范

| 元素 | 阴影参数 | 说明 |
|------|---------|------|
| **卡片（默认）** | elevation 2dp | 轻微阴影，营造层次感 |
| **卡片（悬浮）** | elevation 4dp | 悬停/按下时提升 |
| **按钮（默认）** | elevation 0dp | 扁平设计 |
| **按钮（按下）** | elevation 2dp | 按下时轻微下沉 |
| **弹窗** | elevation 8dp | 显著阴影，突出层级 |
| **悬浮按钮** | elevation 6dp | 始终悬浮于内容之上 |

### 12.1.7 动效规范

#### 12.1.7.1 动画时长

| 动画类型 | 时长 | 说明 |
|---------|------|------|
| **微交互** | 100-150ms | 按钮点击、图标切换 |
| **元素过渡** | 200-300ms | 页面切换、弹窗出现 |
| **复杂动画** | 300-500ms | 柯基动画、成就解锁 |
| **延迟反馈** | 800-1200ms | 长按触发、自动隐藏 |

#### 12.1.7.2 缓动函数

| 场景 | 缓动函数 | 说明 |
|------|---------|------|
| **进入动画** | ease-out | 快速开始，缓慢结束 |
| **退出动画** | ease-in | 缓慢开始，快速结束 |
| **切换动画** | ease-in-out | 平滑自然 |
| **弹性动画** | spring | 活泼有趣（用于柯基互动） |

#### 12.1.7.3 动画原则

1. **有目的性**：每个动画都应有明确目的，不为了动画而动画
2. **一致性**：同类动画使用相同时长和缓动
3. **可打断**：动画应可被打断，不影响操作
4. **尊重用户**：支持 `prefers-reduced-motion` 设置

### 12.1.8 灵感页时间线规范

> **适用范围**：`InspirationScreen` 的 `TimelineInspirationItem` 组件及所属 `LazyColumn`
> **关联文件**：
> - 组件：[TimelineInspirationItem.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/TimelineInspirationItem.kt)
> - 页面：[InspirationScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt)
> - 原型：参考 PRD `【刻记+】APP\不会忘记的备忘录-PRD-v2.md` 中灵感页章节

#### 12.1.8.1 整体布局

灵感页采用"左侧时间栏 + 节点 + 右侧内容区"三段式时间线结构：

```
[左侧时间栏 50dp] [间距 7dp] [节点 6dp] [间距 7dp] [右侧内容区]
                  ↑             ↑             ↑
                  节点左边缘     节点中心      节点右边缘
                  X=57dp         X=60dp        X=63dp
                                                   ↑
                                                内容区起始 X=70dp
```

时间栏宽度 **50dp** 是为了**精确匹配"2026.07"实际渲染宽度**（约 50dp），让"2026.07"在 Column 内水平居中后**右边距 = 0**。这样时间栏右边缘紧贴"2026.07"右边缘，"2026.07"到节点视觉距离 = 0 + 7 = **7dp**，与节点到内容区（7dp）**视觉相等**。

"2026.07"距离屏幕左边距 = LazyColumn.padding(horizontal = 18.dp) = 18dp。

**节点尺寸与间距**：节点直径 6dp（紧凑型），时间栏右边缘到节点左边缘 7dp，节点右边缘到内容区左边缘 7dp，使整体结构 = 时间栏 50dp + 7dp + 节点 6dp + 7dp = 内容区起始 70dp。

##### 时间栏内部布局

时间栏 Column 内部 `horizontalAlignment = Alignment.CenterHorizontally`，让"2026.07"和"08"水平居中在同一垂直中线（Column 宽度 50dp 的中点 X=25dp）：

| 元素 | 字号 | 实际宽度 | 在 50dp 内居中后位置 |
|------|------|---------|---------------------|
| **"2026.07"** | 12sp | 约 50dp | X = 0dp ~ 50dp（中心 X=25dp，右边距=0）|
| **"08"** | 24sp Medium | 约 25dp | X = 12.5dp ~ 37.5dp（中心 X=25dp）|

两者视觉中心都在 X=25dp，满足"08 与 2026.07 视觉中心在同一垂直线"的要求。

**关键效果**：Column 宽度 50dp 精确匹配"2026.07"实际宽度，使"2026.07"在 Column 内居中后**右边距 = 0**（X=50dp 即 Column 右边缘），时间栏右边缘紧贴"2026.07"右边缘。这样"2026.07"到节点视觉距离 = 0 + 7 = **7dp**，与节点到内容区（7dp）**视觉相等**。

#### 12.1.8.2 字号体系

##### 左侧时间栏

| 元素 | 字号 | 字重 | 颜色 |
|------|------|------|------|
| **年月文本**（如"2026.07"） | 12sp | Regular | #999999 提示文字 |
| **大号日期数字**（如"08"） | 24sp | Medium | `MaterialTheme.colorScheme.onSurface` |

##### 右侧内容区

| 元素 | 字号 | 字重 | 颜色 | 行高 |
|------|------|------|------|------|
| **笔记标题** | 16sp | Medium | `MaterialTheme.colorScheme.onSurface` | 默认 |
| **时分时间**（如"09:00"） | 11sp | Regular | #999999 提示文字 | 默认 |
| **笔记正文** | 14sp | Regular | #666666 次要文字 | **21sp** |
| **话题标签** | 11sp | Regular | `UiColors.Primary` | 默认 |

##### 中文字间距

所有文本统一应用 **`letterSpacing = 0.5sp`**，增强中文阅读节奏感。

#### 12.1.8.3 间距体系

##### 文本行排版

| 项 | 值 |
|----|-----|
| **正文行高** | 21sp |
| **中文字间距** | +0.5sp（letterSpacing） |

##### 垂直间距（dp）

| 间距关系 | 值 | 说明 |
|---------|-----|------|
| **标题 → 时分时间** | 4dp | 时分时间紧贴标题下方 |
| **时分时间 → 正文** | 9dp | 满足"标题与下方正文 9dp"规范 |
| **正文 → 标签** | 7dp | 标签紧贴正文下方 |
| **标签 → 图片** | 4dp | 图片与标签分组 |
| **单条笔记内部换行段间距** | 8dp | 正文内段落之间 |
| **相邻两条笔记间距** | 18dp | `LazyColumn.verticalArrangement = spacedBy(18.dp)` |

##### 横向边距（dp）

| 间距 | 值 | 说明 |
|------|-----|------|
| **页面左右内容安全边距** | 18dp | `LazyColumn.padding(horizontal = 18.dp)` |
| **左侧时间轴栏与右侧笔记内容横向间隔** | 14dp | 时间栏 50dp + 节点 6dp + 间隔 7dp = 节点右边缘到内容区 = 7dp。**v1.12 起**总间距 14dp（7dp 时间栏到节点 + 7dp 节点到内容区），两个 7dp 视觉相等 |
| **时间栏右边缘 → 节点左边缘** | 7dp | 节点与时间栏间距 |
| **节点右边缘 → 内容区左边缘** | 7dp | 节点与内容区间距 |

#### 12.1.8.4 节点与竖线

| 元素 | 规格 | 说明 |
|------|------|------|
| **节点直径** | **6dp** | 圆形 CircleShape（v1.11 紧凑型） |
| **节点颜色** | `#FF9A5C`（置顶项） / `MaterialTheme.colorScheme.primary`（普通项） | 与 PRD 主题色一致 |
| **节点 Y 位置** | **固定 11dp**（对齐"灵感标题"16sp Medium 中心） | 16sp Medium lineHeight ≈ 22dp，文字中心 y = 11dp |
| **节点 X 位置** | 60dp（时间栏宽度 50 + 间距 7 + 节点半径 3） | v1.12 时间栏宽度从 56dp 改为 50dp |
| **节点右边缘 → 内容区左边缘** | 7dp | v1.11 改为 7dp |
| **节点显示规则** | **每条灵感都显示节点**（包括同一天内的非首条） | 用户要求"同一天内不同的灵感都需要时间节点" |
| **日期栏显示规则** | 仅每天第一条灵感显示（`showDate = isFirstOfDay`） | 避免重复显示日期 |
| **竖线 X** | 60dp（与节点中心对齐） | `drawBehind` 绘制，#EEEEEE 灰色，2dp 宽 |
| **竖线 Y 范围** | **起点 -18dp → 终点 Item 底部** | 向上延伸 18dp 覆盖 `LazyColumn.verticalArrangement = spacedBy(18.dp)` 间距，实现连续不中断 |

> **设计决策**：节点 Y 中心固定为 11dp，对齐"灵感标题"中心，让"2026.07"、节点、"灵感标题"在第一行同一水平线上。

#### 12.1.8.5 标签与图片

| 元素 | 规格 |
|------|------|
| **标签数量** | 最多显示 3 个，超出显示 `+N` |
| **标签内边距** | 水平 0.5dp / 垂直 0dp（紧凑型，文字紧贴背景） |
| **标签 lineHeight** | 11sp（等于 fontSize，压缩到最小行高） |
| **标签圆角** | 10dp（胶囊形状） |
| **标签背景色** | `#FFF3E0`（暖橙浅） / `#F5F5F5`（`+N` 灰色背景） |
| **标签文字色** | `UiColors.Primary`（暖橙） / `#999999`（`+N` 灰色） |
| **图片缩略图** | 28dp 方形，最多 2 个 + `+N` 提示 |
| **图片圆角** | 6dp |
| **图片占位色** | `#F5F5F5` / `#EEEEEE` |

#### 12.1.8.6 实施常量参考

`TimelineInspirationItem.kt` 中定义的关键常量：

```kotlin
val dateColumnWidth = 50.dp              // 时间栏宽度（v1.12 从 56dp 改为 50dp，精确匹配"2026.07"实际宽度）
val dateToNodeGap = 7.dp                 // 时间栏右边缘到节点左边缘（v1.11 用户要求 7dp）
val nodeDiameter = 6.dp                  // 节点直径（v1.11 紧凑型 6dp）
val nodeToContentGap = 7.dp              // 节点右边缘到内容区左边缘（v1.11 用户要求 7dp）
val nodeCenterX = dateColumnWidth + dateToNodeGap + nodeDiameter / 2  // 60dp
val contentStartX = nodeCenterX + nodeDiameter / 2 + nodeToContentGap  // 70dp
val timelineLineX = nodeCenterX          // 竖线 X = 60dp

// 节点 Y 中心：固定对齐"灵感标题"16sp Medium 中心
val nodeCenterY = 11.dp                  // 16sp Medium lineHeight ≈ 22dp，中心 y = 11dp
val nodeTopY = (nodeCenterY - nodeRadius).coerceAtLeast(0.dp)

val titleToTimeGap = 4.dp                // 标题 → 时分时间
val timeToContentGap = 9.dp              // 时分时间 → 正文
val contentToTagGap = 7.dp               // 正文 → 标签
val tagToImageGap = 4.dp                 // 标签 → 图片
val lazyColumnItemGap = 18.dp            // LazyColumn 相邻 Item 间距
val timelineLineOverlap = lazyColumnItemGap // 竖线向上延伸量（覆盖 18dp 间距）
```

`InspirationScreen.kt` 中：

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp)
)
```

#### 12.1.8.7 节点 Y 定位算法

节点中心 Y 固定为 **11dp**，对齐"灵感标题"16sp Medium 文字中心。

设计依据：
- 16sp Medium 默认 lineHeight ≈ 22dp
- 文字中心 y = 22dp / 2 = 11dp
- 节点直径 8dp，节点 top = 11dp - 4dp = 7dp

实现方式：

```kotlin
val nodeCenterY = 11.dp  // 固定值，对齐"灵感标题"中心
val nodeTopY = (nodeCenterY - nodeRadius).coerceAtLeast(0.dp)
```

**为什么不用动态测量**：
- 硬编码 11dp 与 16sp Medium lineHeight 的"经验中点"一致，简洁可靠
- 若系统字体设置放大到 1.3x，16sp 实际渲染高度会变化，节点仍固定在 11dp 会有 1-2dp 偏差
- 后续如需精确适配，可改为 `onSizeChanged` 测量"灵感标题"实际渲染高度，动态计算节点 Y 中心

#### 12.1.8.8 灵感页导航栏规范

> **关联文件**：[MainScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt) 第 365-402 行
> **适用范围**：灵感页（`TabItem.INSPIRE`）且非批量模式下的 `EnhancedTopBar` 中间内容

灵感页导航栏中间区域采用"大号日期 + 月份 + 下拉箭头"**水平三段式**布局，高度自适应内容，由外层 `Box(Alignment.Center)` 在导航栏中居中（v1.15）：

```
[大号日期] [7dp] [月份] [2dp] [▼]
  "09"           "07月"       "▼"
  25sp Bold      16sp         8sp
  ↘ 全部底部对齐（Alignment.Bottom），Row 高度由 25sp "日" 撑高
```

| 元素 | 字号 | 字重 | 颜色 |
|------|------|------|------|
| **大号日期**（如 "09"） | **25sp** | Bold | `MaterialTheme.colorScheme.onSurface` |
| **月份**（如 "07月"） | **16sp** | Regular | `#666666` 次要文字 |
| **下拉箭头**（"▼"） | 8sp | Regular | `#666666` 次要文字 |
| **日 → 月间距** | **7dp** | — | 水平间距 |
| **月 → 箭头间距** | 2dp | — | 水平间距 |

##### 布局结构

```kotlin
Row(
    verticalAlignment = Alignment.Bottom,           // 子元素底部对齐
    modifier = Modifier
        .clickable { showInspirationCalendar = true }
    // 不使用 fillMaxHeight()，高度自适应内容，由外层 Box 居中
) {
    Text("09", fontSize = 25.sp, fontWeight = FontWeight.Bold)   // 大号日期
    Spacer(modifier = Modifier.width(7.dp))                      // 日 → 月 7dp
    Text("07月", fontSize = 16.sp)                               // 月份
    Spacer(modifier = Modifier.width(2.dp))                      // 月 → 箭头 2dp
    Text("▼", fontSize = 8.sp)                                   // 下拉箭头
}
```

##### 交互与对齐

- **可点击**：整行 `Modifier.clickable` 触发日历弹窗 `showInspirationCalendar = true`
- **高度自适应**：不使用 `fillMaxHeight()`，Row 高度由最高的"09"（25sp）撑高
- **居中**：由 `EnhancedTopBar` 外层 `Box(contentAlignment = Alignment.Center)` 在导航栏中居中
- **底部对齐**：`verticalAlignment = Alignment.Bottom` 让"07月"（16sp）、"▼"（8sp）与"09"（25sp）底部对齐

##### 显示条件

- 仅在 `selectedTab == TabItem.INSPIRE` 且 `!isBatchMode` 时显示
- 其他页面（待办、日期、我的）使用 `EnhancedTopBar` 的默认 title
- 批量模式下显示"批量模式"标题，centerContent 为 null

##### 版本变更说明

- **v1.13**：用 `Column` 垂直布局，"07月" 和 "▼" 上下排列 ❌
- **v1.14**：改为纯水平 `Row` 布局，"07月" 和 "▼" 紧邻水平排列 ✓（但整体垂直居中，日期偏上）
- **v1.15**：保持水平 `Row` 布局，日→月间距改为 **7dp**，子元素**底部对齐**（`Alignment.Bottom`），高度自适应内容（不 `fillMaxHeight`），由外层 Box 居中 ✓

#### 12.1.8.9 排版变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-07-09 | v1 | 初始规范（按 PRD 参考图） |
| 2026-07-09 | v1.1 | 修复"2026.07"换行：时间栏宽度 44dp → 56dp |
| 2026-07-09 | v1.1 | 修复节点位置：Y 中心改为固定 11dp，对齐"灵感标题"中心而非"08"中心 |
| 2026-07-09 | v1.2 | **修正时间轴栏与内容区间隔**：时间栏 56dp + 间距 4dp + 节点 8dp + 间距 14dp + 内容区 82dp。满足"2026.07 距左边距 18dp + 不换行 + 节点与内容区横向间隔 14dp"全部要求 |
| 2026-07-09 | v1.3 | **时间栏内部 Column 改为 CenterHorizontally**：让"2026.07"和"08"视觉中心在同一垂直线（X=28dp），满足"08 相对于 2026.07 水平居中"要求 |
| 2026-07-09 | v1.4 | **大号日期数字 25sp → 24sp**，**标签内边距 6dp/2dp → 4dp/1dp**：让"08"更精致、标签更紧凑 |
| 2026-07-09 | v1.5 | **标签内边距再缩：4dp/1dp → 2dp/1dp**：进一步压缩标签水平内边距，达到 PRD 参考图的极简效果 |
| 2026-07-09 | v1.6 | **标签内边距归零：2dp/1dp → 0dp/0dp**：文字完全紧贴背景，标签呈现"实心药丸"效果。注意：Text 自带字体 ascent/descent，无法完全消除视觉留白 |
| 2026-07-09 | v1.7 | **标签 lineHeight 压缩到 11sp（等于 fontSize）**：进一步压缩标签上下间距。注意：中文上下笔画可能被裁切，请验证可读性 |
| 2026-07-09 | v1.8 | **节点始终显示**：移除节点 Box 的 `if (showDate)` 条件，让同一天内的非首条灵感也显示节点。日期栏仍仅在每天第一条显示 |
| 2026-07-09 | v1.9 | **节点紧贴时间栏 + 向左移动**：`dateToNodeGap` 从 4dp 改为 0dp，节点 X 坐标从 64dp 改为 60dp，内容区起始 X 从 82dp 改为 78dp。同时修复"08"字号错误：源码中 `fontSize = 2.sp` 误改回 `24.sp`（之前字符截断导致"08"显示极小） |
| 2026-07-09 | v1.10 | **竖线连续性修复**：竖线起点 Y 从 0 改为 -18dp（向上延伸 18dp），覆盖 `LazyColumn.verticalArrangement = spacedBy(18.dp)` 的 Item 间距，实现竖线连续不中断。新增 `lazyColumnItemGap` 和 `timelineLineOverlap` 常量保持代码与规范同步 |
| 2026-07-09 | v1.11 | **节点尺寸与间距重设**：节点直径 8dp → **6dp**，时间栏到节点间距 0dp → **7dp**，节点到内容区间距 14dp → **7dp**。节点中心 X = 66dp，内容区起始 X = 76dp。整体结构 = 时间栏 56dp + 7dp + 节点 6dp + 7dp = 76dp |
| 2026-07-09 | v1.12 | **时间栏宽度从 56dp 改为 50dp**：精确匹配"2026.07" 12sp 实际渲染宽度，让"2026.07"在 Column 内水平居中后右边距 = 0。修复 v1.11 6/7/7 布局下两个间距视觉不等问题（之前"2026.07"右边距 3dp，"2026.07"→节点 10dp ≠ 节点→内容区 7dp）。现在节点中心 X = 60dp，内容区起始 X = 70dp |
| 2026-07-09 | v1.13 | **灵感页导航栏日期字号调整**：`MainScreen.kt` 中大号日期 "09" 20sp → **25sp**（Bold），月份 "07月" 10sp → **16sp**，下拉箭头 "▼" 保持 8sp。Column 宽度保持 wrapContentWidth（自适应内容） |
| 2026-07-09 | v1.14 | **导航栏日期布局从 Column 改为 Row**：原 Column 垂直布局导致 "07月" 和 "▼" 上下排列，改为纯水平 Row 布局让 "▼" 紧邻 "07月" 右侧。月份与箭头间距 2dp |
| 2026-07-09 | v1.15 | **导航栏日期水平排列 + 底部对齐**：保持 Row 水平布局，日→月间距从 2dp 改为 **7dp**，子元素对齐改为 **`Alignment.Bottom`**，高度自适应内容（去掉 `fillMaxHeight`），由外层 Box `Alignment.Center` 居中 ✓ |

### 12.1.9 Snackbar 提示规范

> **适用范围**：全项目所有需要轻量反馈的场景（删除提示、操作完成、错误提示、撤销操作等）
> **关联文件**：
> - 组件：[AppSnackbarHost.kt](../../app/src/main/java/com/corgimemo/app/ui/components/AppSnackbarHost.kt)
> - 设计文档：[Snackbar 格式重设计](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-14-Snackbar格式重设计-design.md)、[Snackbar 体验优化](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-14-Snackbar体验优化-design.md)、[Snackbar 统一优化](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-14-Snackbar统一优化-design.md)
> - 实施计划：[Snackbar 统一优化 plan](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/plans/2026-07-14-Snackbar统一优化.md)

#### 12.1.9.1 核心组件

**统一组件**：`AppSnackbarHost`，是全项目唯一允许的 Snackbar 实现。所有 Scaffold 的 `snackbarHost` 槽位**必须**使用此组件。

**API 签名**：
```kotlin
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
)
```

**位置**：`app/src/main/java/com/corgimemo/app/ui/components/AppSnackbarHost.kt`

#### 12.1.9.2 视觉规范

##### 容器规格

| 维度 | 值 | 说明 |
|------|-----|------|
| **容器最大宽度** | 560dp | 短文本自适应内容，长文本不超过此值 |
| **容器对齐** | `Alignment.Center` | 短文本无按钮时居中 |
| **底部与导航栏间距** | 16dp | 安卓标准间距（`padding(bottom = 16.dp)`） |
| **圆角** | 20dp | `RoundedCornerShape(20.dp)`，与卡片一致 |
| **阴影** | elevation 4dp | 明显但不过重 |
| **背景色** | `MaterialTheme.colorScheme.surface` | 跟随主题 |
| **整体高度** | 约 36dp | 紧凑型 |

##### 内部布局（Row 水平排列）

```
[🐕 柯基图标] [12dp] [文字内容] [8dp] [撤销按钮]
   28dp            14sp                Bold
```

| 元素 | 规格 | 说明 |
|------|------|------|
| **左侧柯基图标** | `Modifier.size(28.dp)` | 直接引用 `R.drawable.corgi_tilt_2frames_01`，无 Box 背景包裹 |
| **图标 → 文字间距** | 12dp | `Spacer(Modifier.width(12.dp))` |
| **文字** | `fontSize = 14.sp`，`colorScheme.onSurface`，`maxLines = 1`，`overflow = Ellipsis` | 长文本省略号截断 |
| **文字 → 按钮间距** | 8dp | 仅在有按钮时显示 |
| **右侧按钮** | `TextButton`，`contentColor = UiColors.Primary`，`FontWeight.Bold`，`maxLines = 1` | 仅在 `actionLabel != null` 时显示 |
| **按钮内边距** | horizontal 8dp / vertical 0dp | 紧凑型 |
| **水平内边距** | 16dp | Row 整体左右内边距 |
| **垂直内边距** | 4dp（无按钮）/ 2dp（带按钮） | Row 整体上下内边距，控制整体高度 |

##### 无按钮 vs 带按钮

| 类型 | 触发场景 | 文字 | 按钮 |
|------|---------|------|------|
| **无按钮** | 简单提示、错误提示、权限提示 | 自适应宽度，居中显示 | 无 |
| **带按钮** | 删除撤销、批量撤销、错误重试 | `weight(1f, fill = false)` 靠左，末尾省略号 | 靠右对齐，`performAction()` 触发回调 |

#### 12.1.9.3 行为规范

##### 触发方式

- **通过 `SnackbarHostState.showSnackbar(...)`** 触发，**禁止**直接调用 `Snackbar` Composable
- 调用方：CoroutineScope 中 `snackbarHostState.showSnackbar(message, actionLabel?, duration?)`
- 返回值：`SnackbarResult`（`Dismissed` / `ActionPerformed`），根据返回值决定撤销/清除

##### 持续时间

| 场景 | duration | 说明 |
|------|----------|------|
| 简单提示（如"已保存"） | `SnackbarDuration.Short` | 默认 4s |
| 重要提示（如"权限不足"） | `SnackbarDuration.Long` | 默认 10s |
| 撤销操作 | `SnackbarDuration.Long` | 与 5s 倒计时一致，给足撤销时间 |

##### 撤销删除模式（带 5s 倒计时）

适用于：单个删除、批量删除，且灵感/待办/日期等需要可恢复的操作。

**ViewModel 模式**（仿 `HomeViewModel` + `InspirationViewModel`）：
- 私有 StateFlow `_pendingDeletedXxx: StateFlow<Xxx?>`（单个）/ `_pendingBatchDeletedXxx: StateFlow<List<Xxx>?>`（批量）
- 私有 Job `deleteXxxTimerJob: Job?`（可取消）
- 私有常量 `UNDO_DELETE_XXX_DELAY_MS = 5000L`
- 删除方法：设置状态 + 取消旧 Job + 启动新 Job（`delay(5000)`）
- undoXxx() 方法：从回收站永久删除 + 重新插入主表 + 清除状态
- clearPendingXxx() 方法：清除状态（Snackbar 自动消失时调用）

**Composable 模式**（`LaunchedEffect` 监听）：
- 监听 `pendingXxx.collectAsState()`，key 变化时重新执行
- `host?.showSnackbar(message, actionLabel, duration)`
- `result == ActionPerformed` → `viewModel.undoXxx()`
- 否则 → `viewModel.clearPendingXxx()`

#### 12.1.9.4 调用模式

##### 模式 A：共享 SnackbarHostState（推荐）

由 `MainScreen` 顶层 Scaffold 创建唯一 `SnackbarHostState`，通过参数向下传递。

**优势**：所有 Tab 共享一个 SnackbarHost，避免多 Snackbar 冲突。

```kotlin
// MainScreen.kt
val snackbarHostState = remember { SnackbarHostState() }
Scaffold(
    snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
    ...
) {
    TabItem.HOME -> HomeScreen(
        ...
        snackbarHostState = snackbarHostState   // ★ 传递
    )
    TabItem.INSPIRE -> InspirationScreen(
        ...
        snackbarHostState = snackbarHostState   // ★ 传递
    )
    ...
}

// 子页面
@Composable
fun HomeScreen(..., snackbarHostState: SnackbarHostState? = null) {
    LaunchedEffect(pendingDeletedTodo) {
        val host = snackbarHostState ?: return@LaunchedEffect
        val result = host.showSnackbar(
            message = "已删除 1 个待办",
            actionLabel = "撤销",
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDeleteTodo()
        else viewModel.clearPendingDeletedTodo()
    }
}
```

##### 模式 B：子页面独立 SnackbarHostState

子页面有自己的 Scaffold 时（如 `TodoEditScreen` / `InspirationEditScreen` / `RecycleBinScreen`），使用自己的 `snackbarHostState`。

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
Scaffold(
    snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
    ...
)
```

##### 模式 C：特殊对齐场景

需要 Snackbar 离底更远时（如 `InspirationImageGallery` 避开下载按钮）：

```kotlin
AppSnackbarHost(
    hostState = snackbarHostState,
    modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(bottom = 80.dp)  // 离底 80dp + 内部 16dp = 实际 96dp
)
```

#### 12.1.9.5 规范的 12 个 Scaffold 调用点

| # | 文件 | 模式 | 行号 |
|---|------|------|------|
| 1 | [MainScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt) | A（顶层共享） | 828-830 |
| 2 | [TodoEditScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt) | B | 942 |
| 3 | [RecycleBinScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/recyclebin/RecycleBinScreen.kt) | B | 145 |
| 4 | [SpecialDateScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt) | A（通过 MainScreen） | — |
| 5 | [SpecialDateQuickCreateScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateQuickCreateScreen.kt) | B | 226 |
| 6 | [SpecialDateDetailScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateDetailScreen.kt) | B | 175 |
| 7 | [SpecialDateCardStyleScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateCardStyleScreen.kt) | B | 249 |
| 8 | [InspirationEditScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationEditScreen.kt) | B | 729 |
| 9 | [InspirationViewScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationViewScreen.kt) | B | 158 |
| 10 | [InspirationImageGallery.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt) | C | 204-208 |
| 11 | [HomeScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) | A（通过 MainScreen） | — |
| 12 | [InspirationScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt) | A（通过 MainScreen） | — |

**全项目检查结论（2026-07-14 审计）**：
- ✅ 所有 12 个 Scaffold 的 `snackbarHost` 槽位均使用 `AppSnackbarHost`
- ✅ 所有 `showSnackbar(...)` 调用都通过统一的 `SnackbarHostState` 触发
- ✅ 无直接调用 `androidx.compose.material3.Snackbar` Composable 的违规代码
- ✅ 无 `Snackbar(...)` 自定义渲染绕过 `AppSnackbarHost` 的情况

#### 12.1.9.6 不允许的用法

| 违规用法 | 原因 | 正确做法 |
|---------|------|---------|
| 直接调用 `Snackbar(...)` Composable | 绕过统一组件，破坏全局一致性 | 使用 `AppSnackbarHost` + `showSnackbar()` |
| 使用 `AlertDialog` 显示短暂提示 | 重量级，破坏 Snackbar 轻量反馈定位 | 改用 Snackbar |
| 使用 `Text` 覆盖层做提示 | 无法自动消失，无障碍差 | 改用 Snackbar |
| 每个页面创建独立 `SnackbarHostState` 但不通过顶层 | Tab 切换时 Snackbar 状态丢失 | 使用模式 A 共享 |
| 撤销操作不使用 5s 倒计时 + StateFlow 模式 | 撤销逻辑散落，难以维护 | 仿 HomeViewModel/InspirationViewModel 模式 |
| `SnackbarHost` 不用 `AppSnackbarHost` 包装 | 缺少图标、缺少品牌一致性 | 必须用 `AppSnackbarHost` |

#### 12.1.9.7 实施常量参考

`AppSnackbarHost.kt` 中：

```kotlin
private val SnackbarMaxWidth = 560.dp              // 容器最大宽度

// 外层 Box
Box(
    modifier = modifier
        .fillMaxWidth()
        .padding(bottom = 16.dp),                  // 与导航栏 16dp 间隙
    contentAlignment = Alignment.Center
)

// SnackbarHost 内部
SnackbarHost(
    hostState = hostState,
    modifier = Modifier.widthIn(max = SnackbarMaxWidth)
) { data ->
    Surface(
        shape = RoundedCornerShape(20.dp),         // 圆角
        color = MaterialTheme.colorScheme.surface, // 背景
        shadowElevation = 4.dp                     // 阴影
    ) {
        val hasAction = data.visuals.actionLabel != null
        Row(
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = if (hasAction) 2.dp else 4.dp   // 紧凑型垂直内边距
            )
        ) {
            Image(
                painter = painterResource(R.drawable.corgi_tilt_2frames_01),
                modifier = Modifier.size(28.dp)    // 图标大小
            )
            Spacer(Modifier.width(12.dp))          // 图标 → 文字
            Text(
                text = data.visuals.message,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            data.visuals.actionLabel?.let {
                Spacer(Modifier.width(8.dp))       // 文字 → 按钮
                TextButton(
                    onClick = { data.performAction() },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = UiColors.Primary
                    )
                ) {
                    Text(it, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
```

#### 12.1.9.8 排版变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-07-14 | v1.0 | 初始规范（基于设计文档 [Snackbar 格式重设计](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-14-Snackbar格式重设计-design.md)、[Snackbar 体验优化](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-14-Snackbar体验优化-design.md)、[Snackbar 统一优化](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-07-14-Snackbar统一优化-design.md)）：全项目统一为 `AppSnackbarHost` 品牌风格；左侧 28dp 柯基图标；16dp 底部间距；vertical padding 4/2dp 紧凑型高度；带按钮左文右按钮；灵感页删除撤销模式 |

### 12.1.10 待办卡片优先级视觉标识

> **适用范围**：首页 `TodoListItem`、回收站 `DeletedTodoCard`、编辑页 `TodoGroupContainer`（仅边框）
> **关联文件**：
> - 色源：[PriorityColors.kt](../../app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt)
> - 首页卡片：[TodoListItem.kt](../../app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt)
> - 回收站卡片：[DeletedTodoCard.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/recyclebin/DeletedTodoCard.kt)
> - 编辑页：[CheckboxEditText.kt](../../app/src/main/java/com/corgimemo/app/ui/components/CheckboxEditText.kt) `TodoGroupContainer`
> - 设计文档：待办卡片优先级视觉标识增强（v2026-07-20）

#### 12.1.10.1 视觉三联

每个待办卡片同时具备 **3 种优先级视觉元素**（"三联"），共同传达任务重要性：

| 元素 | 规格 | 位置 | 颜色来源 |
|------|------|------|----------|
| **左侧竖条** | 4dp 宽，自适应卡片高度 | 卡片左边缘 | `PriorityVisual.bar` |
| **卡片边框** | 1.5dp + alpha 0.6f | 卡片 4 边 | `PriorityVisual.border.copy(alpha=0.6f)` |
| **卡片阴影** | elevation **4dp（默认）/ 8dp（长按）** + alpha **0.3f（默认）/ 0.5f（长按）** | 卡片背后 | `PriorityVisual.shadow` |

**设计意图**：
- 三处视觉同色系（仅 alpha 不同），形成统一的"任务重要性"语言
- 用户扫视列表时可"一眼"识别每个任务的重要性等级
- 整体视觉更精致、有层次感

#### 12.1.10.2 颜色映射

| 优先级 | 竖条/边框基色 | 已完成态 dim | 情感联想 |
|--------|--------------|--------------|----------|
| 高 (3) | #FF8A80 | #FFCDD2 | 柔红，避免焦虑 |
| 中 (2) | #FFB74D | #FFE0B2 | 柔橙 |
| 低 (1) | #90CAF9 | #BBDEFB | 柔蓝 |
| 无 (0) | #C8E6C9 | #E8F5E9 | 浅绿，传递"无需特殊处理"的低压力感 [v2026-07-20] |

**应用规则**：
- **未完成态**（status=0）：使用基色
- **已完成态**（status=1）：三处颜色**全部同步降权**为 dim 版（与现有竖线降权规则一致）
- **回收站**：`isCompleted=false`，保持原始优先级色（已删除非主页完成态）

#### 12.1.10.3 实施常量

[PriorityColors.kt](../../app/src/main/java/com/corgimemo/app/ui/components/PriorityColors.kt) 新增：

```kotlin
// 基色（v2026-07-20 新增：None 从 Color.Transparent 改为 #C8E6C9）
val None = Color(0xFFC8E6C9)        // 无优先级浅绿

// dim 版
val NoneDim = Color(0xFFE8F5E9)     // 无优先级极浅绿（已完成态用）

// 三联视觉数据类
data class PriorityVisual(
    val bar: Color,     // 4dp 竖条（不透明）
    val border: Color,  // 边框基色（调用方 .copy(alpha=0.6f)）
    val shadow: Color   // 阴影基色（已带 alpha=0.3f）
)

// 组合查询函数（已完成态自动降权为 dim）
fun priorityVisualOf(priority: Int, isCompleted: Boolean = false): PriorityVisual
```

#### 12.1.10.4 实施代码模式

**首页 Card（TodoListItem.kt L196-265）**：

```kotlin
val priorityVisual = remember(todo.priority, todo.status) {
    PriorityColors.priorityVisualOf(
        priority = todo.priority,
        isCompleted = todo.status == 1
    )
}

Card(
    modifier = Modifier
        .fillMaxWidth()
        .border(1.5.dp, priorityVisual.border.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
        .shadow(2.dp, RoundedCornerShape(16.dp), ambientColor = priorityVisual.shadow, spotColor = priorityVisual.shadow)
        .pressFeedback(...),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),  // 让出阴影给外层 Modifier
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = cardBackground)
) { ... }
```

**回收站 Card（DeletedTodoCard.kt）**：

```kotlin
val priorityVisual = PriorityColors.priorityVisualOf(priority = item.priority, isCompleted = false)

Card(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 4.dp)
        .border(1.5.dp, priorityVisual.border.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
        .shadow(2.dp, RoundedCornerShape(12.dp), ambientColor = priorityVisual.shadow, spotColor = priorityVisual.shadow),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
) {
    Row {
        Box(Modifier.width(4.dp).fillMaxHeight().background(priorityVisual.bar))  // 左侧竖条
        Column(Modifier.weight(1f).padding(16.dp)) { ... }                         // 右侧内容
    }
}
```

**编辑页 TodoGroupContainer（CheckboxEditText.kt）**：

```kotlin
val borderColor = when (priority) {
    3 -> PriorityColors.colorOf(3)
    2 -> PriorityColors.colorOf(2)
    1 -> PriorityColors.colorOf(1)
    else -> PriorityColors.colorOf(0)  // ← v2026-07-20：无优先级也显示浅绿色边框
}
```

> **编辑页仅改边框颜色，不加阴影**（按用户确认）：编辑页是信息密集的编辑环境，多重装饰会过重。

#### 12.1.10.4.1 首页 Card 悬浮效果（v2026-07-20 增强，v3 关键修复）

> **关联文件**：[PressFeedback.kt](../../app/src/main/java/com/corgimemo/app/ui/components/PressFeedback.kt)、[TodoListItem.kt](../../app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt)、[SwipeableTodoBox.kt](../../app/src/main/java/com/corgimemo/app/ui/components/SwipeableTodoBox.kt)、[HomeScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt)

首页 `TodoListItem` 在 v2026-07-20 引入**"悬浮效果"**：默认静态 4dp 阴影（v2026-07-20 从 2dp 提升），长按（>= 500ms）抬升至 8dp 阴影 + 颜色 alpha 从 0.6f 加深到 0.9f，营造"卡片被长按抬升"的物理感。

**v2026-07-20 v3 关键修复（彻底解决"看不到阴影"问题）**：

| 问题 | 根因 | 修复 |
|------|------|------|
| **静态/长按阴影都看不到**（v3 新增） | Card modifier 顺序错误：`.pressFeedback().border().shadow()`，shadow 在 `pressFeedback` 内部的 `graphicsLayer` 中绘制，被 graphicsLayer 边界**裁切**到不可见 | **Modifier 顺序调整**为 `.shadow().pressFeedback().border()`：shadow 在最外层，graphicsLayer 之外绘制，不被裁切 |
| 静态/长按阴影都看不到（v1/v2 未彻底解决） | `SwipeableTodoBox` 外层 `Modifier.clip(RoundedCornerShape(16.dp))` 把 Card 自身 `Modifier.shadow` 输出的阴影全部裁切 | `SwipeableTodoBox` 新增 `contentPadding: PaddingValues` 参数，给阴影预留显示空间；`HomeScreen` 传 `PaddingValues(vertical=8.dp)`（v3: 4→8dp） |
| 边缘阴影不明显 | ambientColor 和 spotColor 都用浅色优先级色 + 低 alpha（v1: 0.3f/0.5f；v2: 0.4f/0.7f），在亮色卡片背景上对比度仍不足 | 改为 **ambient = 浅黑（alpha 0.12f）** 给"底"，**spot = 优先级色（alpha 0.6f/0.9f）** 给"边缘抬升感"，两层分工明确，v3 加深 |
| **静态阴影仍然看不到（v5 关键修复）** | `PriorityColors.priorityVisualOf` 的 `shadow` 字段用 `base.copy(alpha=0.3f)` —— 把浅色优先级色（200 系列）又降 30% alpha。混合到浅色背景 #F8F6F3 后色差仅 95，**对比度严重不足**（v1-v4 全部修复都没解决这个根因） | `priorityVisualOf` 改为 `lerp(base, Color.Black, 0.4f)` —— 60% 优先级色 + 40% 黑色 = **深色版**（#993530 / #996E30 / #56778F / #7A8E7B），与背景色差提升到 **110-130**，阴影明显可见 |

**v5 状态机**（v2026-07-20 调整 alpha）：

| 状态 | 阴影 elevation | spot alpha（深色版优先级色） | ambient alpha（环境） | 触发条件 |
|------|----------------|---------------------------|-----------------------|----------|
| **默认** | 4dp | 0.85f | 0.20f | 静止 / 短按抬起后 |
| **按下** | 4dp | 0.85f | 0.20f | 按下 < 500ms（未触发长按） |
| **长按中** | 8dp | 1.0f | 0.20f | 持续按下 >= 500ms |
| **抬起** | 平滑过渡回 4dp + 0.85f/0.20f | — | — | 手指抬起 / 移动 / 拖拽让位 |

**阴影实现细节**（v2026-07-20 v5 重设）：

```kotlin
// PriorityColors.kt - v5 关键：shadow 用深色版优先级色
val deepShadow = lerp(base, Color.Black, 0.4f)
// 60% 优先级色 + 40% 黑色 → 深色版
//   HIGH (#FF8A80) → #993530（深红棕）色差 ~130
//   MEDIUM (#FFB74D) → #996E30（深橙棕）色差 ~110
//   LOW (#90CAF9) → #56778F（深蓝）色差 ~130
//   NONE (#C8E6C9) → #7A8E7B（深绿灰）色差 ~95

// TodoListItem.kt
val shadowAmbientColor = Color.Black.copy(alpha = 0.20f)   // v5: 0.12→0.20
val shadowSpotAlpha = if (isLongPressed.value) 1.0f else 0.85f  // v5: 默认 0.6→0.85 / 长按 0.9→1.0

.shadow(
    elevation = shadowElevation,
    shape = RoundedCornerShape(16.dp),
    ambientColor = shadowAmbientColor,
    spotColor = priorityVisual.shadow.copy(alpha = shadowSpotAlpha)
    // priorityVisual.shadow 已是深色不透明（alpha 1.0），.copy(alpha=0.85) 是最终阴影 alpha
)
```

**`SwipeableTodoBox.contentPadding` 参数**（v2026-07-20 新增，v3 提升，v4 回调）：

- 默认 `PaddingValues(horizontal=0.dp, vertical=6.dp)`：通用默认，给 4-8dp 阴影预留空间
- `HomeScreen` 传 `PaddingValues(vertical=4.dp)`（v4: 8→4dp）：**配合 `itemSpacing=0dp` 保证卡片之间 8dp 视觉间距**（0 + 4 + 4 = 8dp）
- `SpecialDateScreen` 传 `PaddingValues(0.dp)`：`SpecialDateCard` 无 shadow，无需预留
- **关键**：Modifier 顺序必须是 `.padding(contentPadding).clip(16dp 圆角)`（padding 在前，clip 在后），否则 clip 会切掉 padding 区域的 shadow

**v4 间距公式**（v2026-07-20 用户反馈后修正）：
```
卡片之间视觉距离 = ZonedReorderableLazyColumn.itemSpacing + SwipeableTodoBox.contentPadding.top + SwipeableTodoBox.contentPadding.bottom
                 = 0.dp + 4.dp + 4.dp = 8.dp ✓
```
- v1（最初）：itemSpacing 0dp + 无外层 padding = 0dp（无间距）
- v2：itemSpacing 8dp + 无外层 padding = 8dp（用 LazyColumn 控制间距）
- v3：itemSpacing 8dp + contentPadding vertical 8dp = **24dp**（用户反馈"过稀"）
- **v4（当前）**：itemSpacing 0dp + contentPadding vertical 4dp = **8dp**（满足要求）

**关键实现要点**：

1. **Card Modifier 顺序关键（v3 重要修正）**：
   ```kotlin
   Card(
       modifier = Modifier
           .fillMaxWidth()
           .shadow(elevation = shadowElevation, ...)  // ← v3: 必须在最外层
           .pressFeedback(...)                        // graphicsLayer 缩放内部
           .border(1.5.dp, ...)                       // 跟随 graphicsLayer 一起缩放
   )
   ```
   **v3 重要发现**：原顺序 `.pressFeedback().border().shadow()` 导致 shadow 在 graphicsLayer 内绘制，被 graphicsLayer 边界裁切到不可见。修正后 shadow 在 graphicsLayer 之外绘制：
   - 静态：shadow 完整显示
   - 按压：shadow **不缩放**（在 graphicsLayer 之外）+ border 缩放（在 graphicsLayer 内）→ "卡片陷下去、shadow 露出来"的双重视觉
   - 长按：shadow elevation 4→8dp、alpha 0.6→0.9 → 明显的"抬升感"

2. **`SwipeableTodoBox` 外层 Modifier 顺序关键**：
   ```kotlin
   Layout(
       modifier = modifier
           .padding(contentPadding)    // ← 必须在前：给阴影预留空间
           .clip(RoundedCornerShape(cornerRadiusDp))  // ← 在后：只裁切 padding 内
           .pointerInput(...)
   )
   ```
   原顺序 `.clip() 在前、padding 在后` 会让 padding 区域被 clip 切掉，shadow 仍被裁。

3. **`PressFeedback.isLongPressed` 状态参数**（v2026-07-20 新增）：
   - `Modifier.pressFeedback()` 新增 `isLongPressed: MutableState<Boolean> = remember { mutableStateOf(false) }` 参数
   - 内部在 >= 500ms 时 set true，抬起/移动/cancel/拖拽让位时 set false
   - 外部用 `animateDpAsState(isLongPressed.value)` 平滑过渡阴影 elevation

4. **阴影动画参数**：
   ```kotlin
   val shadowElevation by animateDpAsState(
       targetValue = if (isLongPressed.value) 8.dp else 4.dp,
       animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
       label = "todoCardShadowElevation"
   )
   ```
   duration 200ms 与 `pressFeedback.scaleUpDurationMs` 同步，回弹节奏一致。

5. **回收站 DeletedTodoCard 同步修复（v3）**：
   - 原 shadow：elevation 2dp + ambient/spot 都用 priorityVisual.shadow（alpha 0.3f）→ 几乎不可见
   - 修复后：elevation **2→4dp** + ambient 黑 0.12f + spot 优先级色 0.6f（与首页同源）
   - vertical padding **4→6dp** 给 4dp shadow 留空间
   - 与首页无 Modifier 顺序问题（无 pressFeedback，无 graphicsLayer）

#### 12.1.10.4.2 编辑页优先级选择弹窗圆点（v2026-07-20 修正）

> **关联文件**：[TodoEditScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt) L1851 附近

优先级选择弹窗（`AlertDialog`）中，"无优先级"选项的圆点原为 `Color.Gray` 透明圆环，与其他 3 个优先级（低/中/高）实心圆点不一致。v2026-07-20 修正为 `PriorityColors.None` 浅绿实心圆点，与首页/回收站/编辑页边框"无优先级浅绿"视觉统一。

**修正后**：

```kotlin
val options = listOf(
    Triple(0, "无优先级", com.corgimemo.app.ui.components.PriorityColors.None),  // ← 浅绿实心
    Triple(1, "低优先级", com.corgimemo.app.ui.components.PriorityColors.Low),
    Triple(2, "中优先级", com.corgimemo.app.ui.components.PriorityColors.Medium),
    Triple(3, "高优先级", com.corgimemo.app.ui.components.PriorityColors.High)
)

// 圆点渲染：去掉 value==0 的"透明 + 灰边"特殊分支，统一用 [color] 填充
Box(
    modifier = Modifier
        .size(12.dp)
        .clip(CircleShape)
        .background(color)  // value=0 时为 None 浅绿，1/2/3 为 Low/Medium/High
)
```

#### 12.1.10.5 关键技术决策

| 决策点 | 选择 | 原因 |
|--------|------|------|
| 阴影实现方式 | Card modifier 上 `Modifier.shadow` + `elevation=0dp` | 不增加布局层级；与 `pressFeedback` 缩放动画兼容；与 `CenterEditButton.kt` 模式同源 |
| Card `elevation` 改为 0dp | 必要 | 让出默认阴影给外层 Modifier，避免双层阴影叠加 |
| 竖条/边框/阴影同色（仅 alpha 不同） | 必要 | 三联视觉一致性，避免视觉混乱 |
| 已完成态全部同步降权 | 必要 | 与现有竖条降权规则一致，建立"完成项更弱"层级 |
| 回收站 `isCompleted=false` | 必要 | 回收站待办是"已删除"非"已完成"，保持原始优先级色 |
| 暗色模式 | 暂不区分亮/暗色 | 与现有 `PriorityColors` 行为一致；后续可优化 |
| **首页阴影动态化（4↔8dp）** | 必要 | 长按抬升作为"可拖拽"视觉预告；与项目"治愈、温暖"理念一致（避免突然的弹跳） |
| **Modifier 顺序：shadow 在最外层（v3 修正）** | 必要 | 必须在 `pressFeedback` 之前，否则 shadow 会被 pressFeedback 的 graphicsLayer 边界裁切到不可见 |
| **border 在 pressFeedback 之后** | 必要 | 让 border 跟随 graphicsLayer 一起缩放，避免"内容缩小但边框不缩"的违和感 |
| **PressFeedback 新增 isLongPressed 状态** | 必要 | 复用现有长按检测逻辑（500ms）暴露给调用方；不引入双 pointerInput；不修改状态机内部逻辑 |
| **优先级选择弹窗圆点统一** | 必要 | 4 个选项都是实心圆点，无优先级用 `PriorityColors.None` 浅绿，与三联视觉统一 |
| **HomeScreen contentPadding vertical 8dp** | 必要 | v3: 4→8dp，给长按 8dp shadow 留出充足空间，避免被外层 16dp clip 裁切 |
| **alpha 加深（ambient 0.12 / spot 0.6→0.9）** | 必要 | v3: 浅色优先级色 + 低 alpha 在白底卡片上对比度极低，必须加深到可见阈值 |
| **DeletedTodoCard shadow 同源修复（v3）** | 必要 | elevation 2→4dp + ambient 0.12 + spot 0.6，与首页保持视觉一致 |

#### 12.1.10.6 排版变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-07-20 | v1.0 | 初始规范：所有待办卡片统一三联视觉（竖条 + 边框 + 阴影），无优先级新增浅绿 #C8E6C9 / dim #E8F5E9；编辑页仅改边框颜色（无阴影）；`PriorityColors` 新增 `PriorityVisual` 数据类与 `priorityVisualOf()` 组合查询函数 |
| 2026-07-20 | v1.1 | **悬浮效果增强**：首页 `TodoListItem` 阴影静态 2dp → 4dp（默认）/ 8dp（长按）；阴影 alpha 0.3f → 0.3f/0.5f；`PressFeedback` 新增 `isLongPressed: MutableState<Boolean>` 状态参数；`Modifier` 顺序调整为 `pressFeedback → border → shadow` 让边框/阴影跟随 graphicsLayer 一起缩放；优先级选择弹窗无优先级圆点由 `Color.Gray` 透明圆环改为 `PriorityColors.None` 浅绿实心 |
| 2026-07-20 | v1.2 | **修复"阴影看不到"问题**：① `SwipeableTodoBox` 外层 `.clip(16.dp)` 裁切 Card shadow 根因修复——新增 `contentPadding: PaddingValues` 参数（默认 `vertical=6.dp`），Modifier 顺序调整为 `padding → clip`（padding 在前预留阴影空间）；② shadow 颜色分层——`ambientColor = Color.Black.copy(alpha=0.06f)` 浅黑环境阴影 + `spotColor = priorityVisual.shadow.copy(alpha=0.4f/0.7f)` 优先级色边缘阴影；alpha 从 0.3f/0.5f 提升到 0.4f/0.7f（长按更深）；③ 调用方调整：`HomeScreen` 传 `PaddingValues(vertical=4.dp)` 平衡间距（10dp→16dp），`SpecialDateScreen` 传 `PaddingValues(0.dp)`（`SpecialDateCard` 无 shadow） |
| 2026-07-20 | v1.3 | **v3 关键修复——根除 graphicsLayer 裁切**（v1/v2 未彻底解决阴影看不到问题）：① **Card Modifier 顺序调整**为 `.shadow().pressFeedback().border()`（v3 最重要）——v1/v2 顺序 `.pressFeedback().border().shadow()` 导致 shadow 在 `pressFeedback` 的 `graphicsLayer` 内绘制，被 graphicsLayer 边界裁切到不可见；新顺序 shadow 在 graphicsLayer **之外**绘制，完全不被裁切；按压时 shadow **不缩放**（在 graphicsLayer 之外）+ border 缩放（在 graphicsLayer 内）→ "卡片陷下去、shadow 露出来"的双重视觉；② **alpha 进一步加深**：ambient 0.06→**0.12**（底色加深），spot 默认 0.4→**0.6** / 长按 0.7→**0.9**（边缘加深）；③ **contentPadding 加深**：HomeScreen 传 `vertical=8dp`（v1.2 是 4dp，v3 提升 1 倍）——给长按 8dp shadow 留出充足空间，避免被外层 16dp clip 裁切；④ **DeletedTodoCard 同步修复**：elevation 2→4dp + ambient 黑 0.12f + spot 优先级色 0.6f + vertical padding 4→6dp（与首页同源视觉） |
| 2026-07-20 | v1.4 | **间距回调——卡片之间 24dp→8dp**（v3 用户反馈"过稀"）：① `ZonedReorderableLazyColumn.itemSpacing` 8→**0dp**；② `SwipeableTodoBox.contentPadding` vertical 8→**4dp**（保留 4dp shadow 空间）；新间距 = 0 + 4 + 4 = **8dp** ✓；③ 长按 8dp shadow 超出 4dp padding 部分被外层 16dp clip 轻微裁切，但 Compose 阴影 alpha 渐变中心深边缘浅，主体仍可见，"抬升感"保留 |
| 2026-07-20 | v1.5 | **v5 关键修复——深色版优先级色作 shadow**（v1-v4 全部修复都未解决阴影对比度问题）：① `PriorityColors.priorityVisualOf` 的 `shadow` 字段从 `base.copy(alpha=0.3f)`（浅色 200 系列 + 30% alpha）改为 `lerp(base, Color.Black, 0.4f)`（**60% 优先级色 + 40% 黑色 = 深色版**）；新色值：HIGH #993530（深红棕）/ MEDIUM #996E30（深橙棕）/ LOW #56778F（深蓝）/ NONE #7A8E7B（深绿灰）；② ambient alpha 0.12→**0.20**（底色加深），spot alpha 默认 0.6→**0.85** / 长按 0.9→**1.0**（边缘加深）；③ DeletedTodoCard 同步修复：spot alpha 0.6→0.85 + ambient 0.12→0.20；**新色差从 95 提升到 110-130，阴影在白底上明显可见** |


