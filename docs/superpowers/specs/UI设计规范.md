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

灵感页导航栏中间区域采用"月份 + 大号日期 + 下拉箭头"**水平三段式**布局，高度自适应内容，由外层 `Box(Alignment.Center)` 在导航栏中居中（v1.15）：

```
[月份]   [7dp]   [大号日期]  [2dp]  [▼]
  "07月"           "09"
  16sp             25sp Bold
  ↘ 全部底部对齐（Alignment.Bottom），Row 高度由 25sp "日" 撑高
```

| 元素 | 字号 | 字重 | 颜色 |
|------|------|------|------|
| **月份**（如 "07月"） | **16sp** | Regular | `#666666` 次要文字 |
| **大号日期**（如 "09"） | **25sp** | Bold | `MaterialTheme.colorScheme.onSurface` |
| **下拉箭头**（"▼"） | 8sp | Regular | `#666666` 次要文字 |
| **月 → 日间距** | **7dp** | — | 水平间距 |
| **日 → 箭头间距** | 2dp | — | 水平间距 |

##### 布局结构

```kotlin
Row(
    verticalAlignment = Alignment.Bottom,           // 子元素底部对齐
    modifier = Modifier
        .clickable { showInspirationCalendar = true }
    // 不使用 fillMaxHeight()，高度自适应内容，由外层 Box 居中
) {
    Text("07月", fontSize = 16.sp, lineHeight = 16.sp)            // 月份（v1.17: lineHeight 消除行间距）
    Spacer(modifier = Modifier.width(7.dp))                      // 月 → 日 7dp
    Text(
        "27", fontSize = 25.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 3.dp)                  // v1.18 修正: padding(top) 让 glyph 下移贴底
    )                                                            // 大号日期
    Spacer(modifier = Modifier.width(2.dp))                      // 日 → 箭头 2dp
    Text("▼", fontSize = 8.sp, lineHeight = 8.sp)                // 下拉箭头（v1.17: lineHeight 消除行间距）
}
```

> **v1.18 关键教训**：`Modifier.padding(bottom = X)` 不会让 glyph 底向下移动，只会扩大 layout 盒的下边界。`padding(top = X)` 才是让 glyph 整体下移的正确方向。详见 [DatePickerRow.kt](../../app/src/main/java/com/corgimemo/app/ui/components/calendar/DatePickerRow.kt) 注释。

##### 交互与对齐

- **可点击**：整行 `Modifier.clickable` 触发日历弹窗 `showInspirationCalendar = true`
- **高度自适应**：不使用 `fillMaxHeight()`，Row 高度由最高的"09"（25sp）撑高
- **居中**：由 `EnhancedTopBar` 外层 `Box(contentAlignment = Alignment.Center)` 在导航栏中居中
- **底部对齐**：`verticalAlignment = Alignment.Bottom` 让"07月"（16sp）、"▼"（8sp）与"09"（25sp）底部对齐

##### 显示条件

- 在 `selectedTab ∈ {TabItem.TODO, TabItem.INSPIRE, TabItem.DATE}` 且 `!effectiveBatchMode` 时显示（v1.16 扩展至三页）
- 我的页（`TabItem.MINE`）使用 `EnhancedTopBar` 的默认 title
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
| 2026-07-27 | v1.16 | **导航栏日期顺序调整为"月 → 日"**：将大号日期（25sp Bold）与月份（16sp）交换位置，月份移到左侧、天数移到右侧。间距规则不变（月→日 7dp、日→箭头 2dp）。待办/灵感/日期三页同步生效 |
| 2026-07-27 | v1.17 | **导航栏日期底部精确对齐**：三个 Text 元素（月/日/箭头）都设置 `lineHeight = fontSize` 消除默认行间距。Compose `Row(Alignment.Bottom)` 对齐的是子项 layout box 底部而非 glyph 底部，默认 lineHeight ≈ fontSize × 1.2-1.4 含 ~2-3sp 上下行间距，25sp Bold "09" 距盒底 ~7sp 而 16sp "月" 距盒底 ~2sp，肉眼可见底部偏差。`lineHeight = fontSize` 后留白归零，视觉底部精确对齐 |
| 2026-07-27 | v1.18 | **导航栏日期 glyph 底精确对齐（数字 vs 中文/几何符号）**：v1.17 消除行间距后，数字"27"（25sp Bold 不全高）距 em-box 底仍 ~3-4sp、中文"月"（16sp 方块字）距底 ~1sp、几何"▼"（8sp）距底 ~1sp，导致"月"和"▼"底部略低于"27"底部。给大号日期 Text 加 `Modifier.padding(top = 3.dp)` 让"27"内容整体下移 3dp，glyph 底贴 layout 底（= Row 底），与"月"/"▼"精确对齐。**注意 padding 方向**：`padding(bottom)` 错误（只扩展 layout 盒底边界、glyph 位置不变），必须用 `padding(top)` 才行 |

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

### 12.1.11 二次确认弹窗规范

> **视觉样板**：[InspirationImageGallery.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt) L285-322（图片附件页删除确认弹窗）
>
> **适用范围**：项目内所有破坏性操作前的二次确认弹窗（删除、清空、覆盖恢复、放弃编辑等）。

#### 12.1.11.1 设计原则

| 原则 | 说明 |
|------|------|
| **治愈不焦虑** | 用"先确认再执行"防止破坏性误操作，但避免过度警告（与 12.1.1「减少焦虑」一致） |
| **轻量克制** | 默认用极简视觉（仅标题 + 正文 + 删除/取消按钮），不堆叠图标和高亮区 |
| **与 Snackbar 撤销互补** | 不可恢复走二次确认弹窗，可恢复走 Snackbar 撤销（参考 12.1.9.3） |
| **全局一致** | 所有二次确认弹窗视觉/文案/交互统一，避免每页各写一套 |

#### 12.1.11.2 适用边界：二次确认弹窗 vs Snackbar 撤销

| 维度 | 二次确认弹窗 | Snackbar 撤销 |
|------|-------------|---------------|
| 操作可恢复性 | **不可恢复**（物理删除文件、清空记录、覆盖数据） | **可恢复**（移入回收站、状态变更） |
| 数据影响范围 | 单条永久丢失 / 批量永久丢失 / 覆盖全量数据 | 软删除（可在回收站找回） |
| 用户代价 | 高（一旦确认不可逆） | 低（5s 内可撤销） |
| 视觉重量 | 中（弹窗遮罩 + 居中聚焦） | 轻（底部 Snackbar） |
| 交互打断 | 强打断（必须先决策） | 弱打断（不阻塞） |
| 典型示例 | 删除图片附件、删除录音、清空回收站、清空历史、恢复备份 | 删除待办（移入回收站）、删除灵感、删除特殊日期 |

**与 12.1.9.6「禁止 AlertDialog 显示短暂提示」的协调**：

- 该禁令针对**事后短暂提示**（如"已保存""操作成功"），二次确认弹窗不属于此类
- 二次确认弹窗是**事前拦截**，与 Snackbar 的"事后反馈"职责完全不同
- 凡是符合"不可恢复 / 高代价"特征的操作，**必须**使用二次确认弹窗，不得用 Snackbar 替代
- 凡是可恢复的操作，**必须**使用 Snackbar 5s 撤销模式，不得用二次确认弹窗替代

#### 12.1.11.3 视觉规范

##### 容器规格

| 属性 | 值 | 说明 |
|------|------|------|
| 容器组件 | `AlertDialog`（Material3） | 不用 `BasicAlertDialog` / 自定义 `Dialog` |
| 容器颜色 | 默认（不显式指定 `containerColor`） | 跟随 `MaterialTheme.colorScheme.surface`，亮/暗色自动切换 |
| 容器圆角 | 默认（不显式指定 `shape`） | 跟随 `MaterialTheme.shapes.large`（约 16dp） |
| 容器阴影 | Material3 默认（elevation 8dp，与 12.1.6 一致） | 不自定义 |
| icon 槽位 | **不使用** | 简化版无 Warning 图标，避免过度警示 |
| 标题高亮区 | **不使用** | 不在弹窗内显示待删除对象的标题/缩略图 |

##### 标题（title）

| 属性 | 值 |
|------|------|
| 字号 | 默认（Material3 AlertDialog title 默认值） |
| 字重 | `FontWeight.SemiBold` |
| 颜色 | 默认（`onSurface`，不显式指定） |
| 内容 | 4-8 字短语，描述操作对象（"删除图片" / "清空回收站" / "删除录音"） |

##### 正文（text）

| 属性 | 值 |
|------|------|
| 字号 | 默认（约 16sp） |
| 字重 | 默认（Normal） |
| 颜色 | 默认（`onSurfaceVariant`，不显式指定） |
| 内容 | 一句话询问 + 一句话后果说明 |
| 行高 | 默认 |

##### 确认按钮（confirmButton）

| 属性 | 值 |
|------|------|
| 组件 | `TextButton` |
| 文字 | 动词 + 对象（"删除" / "清空" / "恢复" / "放弃编辑"） |
| 文字颜色 | `Color(0xFFFF6B6B)`（破坏性操作专用警示色，柔和粉红） |
| 字重 | 默认（Normal） |

##### 取消按钮（dismissButton）

| 属性 | 值 |
|------|------|
| 组件 | `TextButton` |
| 文字 | "取消" |
| 文字颜色 | 默认（`onSurfaceVariant`，不显式指定） |
| 字重 | 默认（Normal） |

##### 警示色说明

`Color(0xFFFF6B6B)` 是项目内"破坏性操作确认按钮"专用警示色：

- 与 12.1.2.3「高优先级 #FF8A80 柔和红色」同源（均偏粉红，避免焦虑）
- 比 #FF8A80 略饱和，用于"即将执行破坏性操作"的按钮文字
- **适用于**：删除、清空、放弃编辑、覆盖恢复
- **不适用于**：状态切换、临时警告、Snackbar 反馈、卡片优先级条

#### 12.1.11.4 文案规范

##### 标题模板

| 操作类型 | 标题模板 | 示例 |
|---------|---------|------|
| 删除单条 | `删除<对象>` | "删除图片" / "删除录音" / "删除模板" |
| 批量删除 | `批量删除<对象>` | "批量删除待办" / "批量删除灵感" |
| 清空 | `清空<对象>` | "清空回收站" / "清空操作历史" / "清空编辑历史" |
| 覆盖/恢复 | `<动作>` | "恢复备份" / "恢复数据" |
| 放弃编辑 | `放弃编辑` | — |

##### 正文模板

公式：`确定要<动作>这<量词><对象>吗？<后果说明>。`

| 操作类型 | 正文模板 | 示例 |
|---------|---------|------|
| 删除单条 | `确定要删除这<量词><对象>吗？删除后不可恢复。` | "确定要删除这张图片吗？删除后不可恢复。" |
| 批量删除 | `确定要删除选中的 N 个<对象>吗？删除后不可恢复。` | "确定要删除选中的 3 个待办吗？删除后不可恢复。" |
| 清空 | `确定要清空所有<对象>吗？清空后不可恢复。` | "确定要清空所有回收站内容吗？清空后不可恢复。" |
| 覆盖/恢复 | `确定要<动作>吗？当前所有<对象>将被覆盖。` | "确定要恢复备份吗？当前所有数据将被覆盖。" |
| 放弃编辑 | `确定要放弃编辑吗？未保存的内容将永久丢失。` | — |

##### 按钮文案

| 按钮 | 文字 | 颜色 |
|------|------|------|
| 确认 | 与标题动词一致（"删除" / "清空" / "恢复" / "放弃编辑"） | `Color(0xFFFF6B6B)` |
| 取消 | "取消" | 默认 |

#### 12.1.11.5 交互规范

##### 触发与关闭

| 操作 | 行为 |
|------|------|
| 触发 | 用户点击破坏性操作入口 → `showXxxConfirm = true` |
| 点确认 | `showXxxConfirm = false` → 执行破坏性操作 |
| 点取消 | `showXxxConfirm = false` → 不执行任何操作 |
| 点遮罩 | `showXxxConfirm = false` → 不执行任何操作（与取消等价） |
| 按返回键 | `showXxxConfirm = false` → 不执行任何操作（与取消等价） |

##### 状态管理

- 使用 `var showXxxConfirm by remember { mutableStateOf(false) }` 管理弹窗显示
- 弹窗内**不持有业务状态**，仅做"确认/取消"二选一决策
- 确认后的业务逻辑由调用方传入的 `onConfirm` 回调执行
- 批量删除场景可额外使用 `pendingDeleteIndex: Int` 标记待删除项索引（参考 [VoicePreviewDialog.kt](../../app/src/main/java/com/corgimemo/app/ui/components/VoicePreviewDialog.kt) L133-134）

##### 动效

- 弹窗出现/消失使用 Material3 `AlertDialog` 默认动画（淡入淡出 + 轻微缩放）
- **不自定义** enter/exit transitions

#### 12.1.11.6 实施代码模式（标准样板）

##### 标准样板（单条删除）

```kotlin
// 状态：是否显示二次确认弹窗
var showDeleteConfirm by remember { mutableStateOf(false) }

// 触发：用户点击删除入口
IconButton(onClick = { showDeleteConfirm = true }) {
    Icon(Icons.Outlined.Delete, contentDescription = "删除")
}

// 弹窗：极简 AlertDialog，无 icon，无标题高亮
if (showDeleteConfirm) {
    AlertDialog(
        onDismissRequest = { showDeleteConfirm = false },
        title = {
            Text(
                text = "删除图片",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text("确定要删除这张图片吗？删除后不可恢复。")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showDeleteConfirm = false
                    onDeleteClick()  // 调用方执行实际删除
                }
            ) {
                Text("删除", color = Color(0xFFFF6B6B))
            }
        },
        dismissButton = {
            TextButton(
                onClick = { showDeleteConfirm = false }
            ) {
                Text("取消")
            }
        }
    )
}
```

##### 批量删除样板

```kotlin
var showBatchDeleteConfirm by remember { mutableStateOf(false) }
val selectedCount = selectedIds.size

if (showBatchDeleteConfirm) {
    AlertDialog(
        onDismissRequest = { showBatchDeleteConfirm = false },
        title = {
            Text(
                text = "批量删除待办",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Text("确定要删除选中的 $selectedCount 个待办吗？删除后不可恢复。")
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showBatchDeleteConfirm = false
                    onBatchDelete()
                }
            ) {
                Text("删除 $selectedCount 项", color = Color(0xFFFF6B6B))
            }
        },
        dismissButton = {
            TextButton(onClick = { showBatchDeleteConfirm = false }) {
                Text("取消")
            }
        }
    )
}
```

##### 关键约束

- **不使用 `Icons.Default.Warning` 等 icon**：简化版无 icon 槽位
- **不自定义 `containerColor` / `titleContentColor` / `textContentColor`**：跟随 Material3 主题
- **不自定义 `shape`**：使用 `MaterialTheme.shapes.large` 默认值
- **不在弹窗内显示待删除对象标题/缩略图**：保持极简

#### 12.1.11.7 现有调用点清单（25 处，2026-07-25 审计）

##### A. 符合简化版规范的调用点（21 处）

| # | 文件 | 行号 | 用途 |
|---|------|------|------|
| 1 | [TodoEditScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt) | L2154-2201 | 删除自定义分组 |
| 2 | [BackupHistoryScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/backup/BackupHistoryScreen.kt) | L185-211 | 删除备份记录 |
| 3 | [BackupHistoryScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/backup/BackupHistoryScreen.kt) | L216-248 | 恢复备份（覆盖当前数据） |
| 4 | [HomeScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) | L1393-1418 | 批量删除待办 |
| 5 | [HomeScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) | L1424-1449 | 删除单个待办 |
| 6 | [TemplateManageScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/settings/TemplateManageScreen.kt) | L193-214 | 删除模板 |
| 7 | [SettingsScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt) | L330-361 | 恢复数据（覆盖当前数据） |
| 8 | [RecycleBinScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/recyclebin/RecycleBinScreen.kt) | L421-437 | 清空回收站 |
| 9 | [OperationHistoryScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/settings/OperationHistoryScreen.kt) | L194-213 | 清空操作历史 |
| 10 | [EditHistoryScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/settings/EditHistoryScreen.kt) | L204-223 | 清空编辑历史 |
| 11 | [MainScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt) | L1200-1219 | 灵感批量删除 |
| 12 | [MainScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt) | L1231-1248 | 日期批量删除 |
| 13 | [InspirationScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt) | L429-447 | 删除单条灵感 |
| 14 | [InspirationScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt) | L452-471 | 批量删除灵感 |
| 15 | [InspirationEditScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationEditScreen.kt) | L1523-1550 | 删除标签 |
| 16 | [SpecialDateDetailScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateDetailScreen.kt) | L337-355 | 删除特殊日期 |
| 17 | [VoicePlayerComponent.kt](../../app/src/main/java/com/corgimemo/app/ui/components/VoicePlayerComponent.kt) | L304-323 | 删除语音备注 |
| 18 | [VoicePreviewDialog.kt](../../app/src/main/java/com/corgimemo/app/ui/components/VoicePreviewDialog.kt) | L642-682 | 删除录音 |
| 19 | [InspirationImageGallery.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt) | L286-322 | 删除图片（**规范样板**） |
| 20 | [MainScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt) | L1057-1064 | 删除待办分组（使用 `DeleteCategoryConfirmDialog`） |
| 21 | [MainScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt) | L1098-1107 | 删除日期类型（使用 `DeleteCategoryConfirmDialog`） |

##### B. 不符合简化版规范的调用点（4 处，使用通用组件 `DeleteConfirmDialog`）

> 这 4 处使用了带 Warning 图标 + 标题高亮区的完整版 `DeleteConfirmDialog` 组件，**不符合本规范**。
> 但因组件已封装且测试通过，**暂不强制迁移**，后续迭代时优先改为简化版。

| # | 文件 | 行号 | 用途 | 偏离点 |
|---|------|------|------|--------|
| 22 | [TodoEditScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt) | L2314-2381 | 删除待办 / 放弃编辑 | 使用 `DeleteConfirmDialog`（Delete + Discard 模式） |
| 23 | [TodoEditScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt) | L2397-2411 | 放弃编辑（系统返回键触发） | 使用 `DeleteConfirmDialog`（Discard 模式） |
| 24 | [InspirationEditScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationEditScreen.kt) | L1628-1656 | 删除灵感 / 放弃编辑 | 使用 `DeleteConfirmDialog`（Delete + Discard 模式） |
| 25 | [InspirationEditScreen.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationEditScreen.kt) | L1672-1686 | 放弃编辑灵感 | 使用 `DeleteConfirmDialog`（Discard 模式） |

##### C. 视觉不一致项（待后续迁移时统一）

| 不一致点 | 涉及调用点 | 应迁移至 |
|---------|-----------|---------|
| 删除按钮红色色值散落 `0xFFFF6B6B` / `0xFFE53935` / `0xFFDC2626` / `0xFFEF4444` / `UiColors.Error` / `MaterialTheme.colorScheme.error` | 序号 1-19 中部分调用点 | 统一为 `Color(0xFFFF6B6B)` |
| "清空"按钮文字写作"确定" | 序号 9、10 | 改为"清空" |
| 删除按钮无红色 | 序号 16（SpecialDateDetailScreen） | 补 `color = Color(0xFFFF6B6B)` |
| 使用 `Button` + `OutlinedButton` 而非 `TextButton` | 序号 6、7 | 改为 `TextButton` |
| `BatchDeleteConfirmDialog` 通用组件未被使用 | 序号 4、11、12、14 | 后续可考虑迁移复用，或保留各自 `AlertDialog` 实现 |

#### 12.1.11.8 不允许的用法

| 违规用法 | 原因 | 正确做法 |
|---------|------|---------|
| 自定义 `Dialog` 实现删除确认 | 绕过 Material3 `AlertDialog` 一致性 | 使用 `AlertDialog` |
| 在弹窗内加 `Icons.Default.Warning` 等图标 | 简化版无 icon 槽位，过度警示违反"减少焦虑"原则 | 不加 icon |
| 在弹窗内高亮待删除对象标题/缩略图 | 增加视觉重量，违反"轻量克制"原则 | 仅用文字描述（"这张图片"/"这条录音"） |
| 自定义 `containerColor` / `shape` | 破坏 Material3 主题一致性 | 跟随主题默认值 |
| 删除按钮使用 `Color.Red` / `0xFFDC2626` / `0xFFE53935` | 过于刺眼，违反"减少焦虑"原则 | 使用 `Color(0xFFFF6B6B)` |
| 删除按钮文字写作"OK" / "确定" / "Yes" | 语义模糊，无法直接看出是破坏性操作 | 用动词："删除" / "清空" / "放弃编辑" |
| 用 Snackbar 替代二次确认弹窗执行不可恢复操作 | Snackbar 是事后反馈，无法事前拦截 | 不可恢复操作必须用 `AlertDialog` |
| 用二次确认弹窗执行可恢复操作（如移入回收站） | 与 Snackbar 撤销模式职责重叠，过度打断 | 改用 Snackbar 5s 撤销（参考 12.1.9.3） |
| 弹窗内持有业务状态（如待删除对象引用） | 弹窗应仅做"确认/取消"决策 | 状态由调用方持有，弹窗仅触发回调 |
| 未使用 `var showXxxConfirm by remember { mutableStateOf(false) }` 模式 | 状态管理混乱 | 统一使用 `remember + mutableStateOf` |

#### 12.1.11.9 排版变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-07-25 | v1.0 | 初始规范：以 [InspirationImageGallery.kt](../../app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/InspirationImageGallery.kt) L285-322 为视觉样板，确立"极简 `AlertDialog`（title + text + 删除/取消按钮）"为项目统一标准；明确与 Snackbar 撤销模式的适用边界（不可恢复走弹窗 / 可恢复走 Snackbar）；审计项目内 25 处二次确认弹窗调用点（21 处符合 / 4 处不符合，暂不强制迁移）；统一删除按钮警示色为 `Color(0xFFFF6B6B)` |

### 12.1.12 底部弹窗（ModalBottomSheet）通用规范

> **视觉样板**：[ActionBottomSheet.kt](../../app/src/main/java/com/corgimemo/app/ui/components/ActionBottomSheet.kt)（通用操作列表组件，已按规范实现）
>
> **适用范围**：项目内所有从底部滑出的模态面板（ModalBottomSheet），包括操作菜单、选择器、编辑器、内容展示面板等。

#### 12.1.12.1 设计原则

| 原则 | 说明 |
|------|------|
| **治愈不焦虑** | 顶部大圆角 + 柔和阴影 + 平滑滑入动画，避免生硬弹出 |
| **视觉统一** | 所有底部弹窗遵循相同的容器、指示条、间距、列表项规范 |
| **主题适配** | 颜色必须使用 `MaterialTheme.colorScheme`，**禁止**硬编码 `Color.White` |
| **类型清晰** | 按交互模式分为4种变体，不同变体有明确的适用场景 |
| **操作反馈即时** | 点击选项后立即执行回调并关闭（类型A/B），或有明确的确认按钮（类型C） |

#### 12.1.12.2 基础视觉参数（所有弹窗必须遵守）

##### 容器规格

| 属性 | 值 | 说明 |
|------|------|------|
| 容器组件 | `ModalBottomSheet`（Material3） | 不使用自定义 `BottomSheetScaffold` 做模态 |
| 形状（Shape） | `RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)` | **必须显式指定**，24dp顶部大圆角 |
| 容器颜色（containerColor） | `MaterialTheme.colorScheme.surface` | **必须显式指定**，禁止硬编码白色 |
| 内容颜色（contentColor） | 默认（不指定） | 跟随 `contentColorFor(surface)` 自动计算 |
| 阴影（tonalElevation） | 默认（不指定） | M3默认值，不自定义 |
| 拖拽关闭 | 默认开启 | 支持向下拖拽关闭，不设置 `gesturesEnabled = false` |

##### Drag Handle（顶部指示条）

| 属性 | 值 | 说明 |
|------|------|------|
| dragHandle 参数 | **必须设为 `null`** | 禁用M3默认dragHandle，使用自定义实现 |
| 自定义指示条宽度 | 40.dp | 统一宽度 |
| 自定义指示条高度 | 4.dp | 统一高度 |
| 自定义指示条颜色 | `MaterialTheme.colorScheme.outlineVariant` | 主题适配的浅灰色 |
| 自定义指示条圆角 | `RoundedCornerShape(2.dp)` | 两端小圆角 |
| 指示条顶部间距 | 12.dp | 指示条距离弹窗顶部 |
| 指示条底部间距 | 16.dp | 指示条距离下方内容（标题或列表） |

**自定义指示条标准代码：**
```kotlin
// 标准 Drag Handle（仅指示条，无标题）
Box(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp, bottom = 16.dp),
    contentAlignment = Alignment.Center
) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(4.dp)
            .background(
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(2.dp)
            )
    )
}

// 带标题的 Drag Handle（指示条 + 标题）
Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(top = 12.dp, bottom = 16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(4.dp)
            .background(
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(2.dp)
            )
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}
```

##### 内容内边距（Content Padding）

| 场景 | horizontalPadding | bottomPadding | 说明 |
|------|-------------------|---------------|------|
| 默认模式 | 24.dp | 24.dp | 大多数弹窗使用此值 |
| 有底部操作按钮 | 24.dp | 32.dp | 底部有确认/取消按钮时增加底部间距 |
| 紧凑模式 | 24.dp | 16.dp | 内容非常少、无分割线时使用 |

> **注意**：内容顶部不需要额外padding（Drag Handle已提供16dp底部间距）。

##### 列表项规格（类型A/B）

| 属性 | 值 | 说明 |
|------|------|------|
| 列表项最小高度 | 48.dp | 保证足够点击热区 |
| 列表项标准高度 | 56.dp | 大多数操作项使用此高度 |
| 图标尺寸 | 20.dp（emoji/图标） | 图标容器32.dp宽 |
| 图标与文字间距 | 12.dp | |
| 文字字号 | 16.sp | |
| 文字字重 | FontWeight.Medium | |
| 项间分割线 | 1.dp `HorizontalDivider` | 颜色 `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)` |
| 点击反馈 | 默认 ripple | 不自定义 pressFeedback |
| 破坏性操作颜色 | `MaterialTheme.colorScheme.error` | 删除项文字和图标使用error色 |
| 选中项颜色 | 主题色（`MaterialTheme.colorScheme.primary` 或项目主色 `0xFFFF9A5C`） | 选中项文字+图标使用主题色，右侧显示✓ |

##### 底部操作按钮（类型C）

| 属性 | 值 | 说明 |
|------|------|------|
| 按钮高度 | 44.dp | |
| 按钮圆角 | 12.dp | 与输入框等元素统一 |
| 主按钮颜色 | `0xFFFF9A5C`（项目橙色） | 背景橙色，白色文字 |
| 次按钮颜色 | `MaterialTheme.colorScheme.surfaceVariant` | 浅灰背景，文字默认色 |
| 按钮间距 | 12.dp | |
| 按钮区域顶部间距 | 16.dp | 与内容区域用分割线隔开 |
| 按钮文字 | 动词（"确认"/"应用"/"取消"） | 不使用"OK"/"确定"等模糊词汇 |

##### 动画规范

| 动画类型 | 时长 | 说明 |
|---------|------|------|
| 滑入/滑出 | M3默认（~300ms） | 使用 `cubic-bezier(0.2, 0.8, 0.2, 1)` 缓动 |
| 列表项点击 | 100-150ms | 默认ripple |
| 选中态切换 | 150-200ms | 颜色和✓图标淡入淡出 |
| 不自定义 | — | **禁止**自定义enter/exit transition，使用M3默认动画 |

#### 12.1.12.3 四种弹窗类型变体

##### 类型 A：操作列表（Action Sheet）

| 属性 | 说明 |
|------|------|
| 标题 | **无标题**，直接显示操作项 |
| Drag Handle | 仅指示条（无标题） |
| 交互 | 点击选项**立即执行回调并关闭弹窗** |
| 内容 | 垂直列表，支持 `dividerIndex` 插入分组分割线 |
| 破坏性操作 | 使用error色显示 |
| 适用场景 | 长按菜单、更多操作、分享选项、快速操作入口 |
| 参考组件 | `ActionBottomSheet`（已封装，优先复用） |

**标准样板：**
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionBottomSheet(
    // ...
) {
    val sheetState = rememberModalBottomSheetState()
    val coroutineScope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null
    ) {
        // 自定义 Drag Handle（仅指示条）
        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp).height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(2.dp)
                    )
            )
        }

        // 可选标题（居中或左对齐）
        // 操作列表
        Column(modifier = Modifier.padding(horizontal = 24.dp, bottom = 24.dp)) {
            items.forEachIndexed { index, item ->
                SheetItem(
                    icon = item.icon,
                    label = item.label,
                    isDanger = item.isDanger,
                    onClick = {
                        coroutineScope.launch {
                            sheetState.hide()
                            awaitCancellation()
                        }.invokeOnCompletion {
                            if (!sheetState.isVisible) onDismiss()
                            item.onClick()
                        }
                    }
                )
                if (index == dividerIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
```

##### 类型 B：单选列表（Picker Sheet）

| 属性 | 说明 |
|------|------|
| 标题 | **必须有标题**，显示在Drag Handle区域或内容顶部 |
| Drag Handle | 带标题（指示条 + 标题文字） |
| 交互 | 点击选项**立即回调选中值并关闭弹窗** |
| 选中态 | 文字/图标使用主题色，右侧显示✓图标 |
| 适用场景 | 优先级选择、排序方式选择、分类选择、颜色选择（列表形式） |
| 参考组件 | `PriorityPickerSheet`、`SortBottomSheet`、`CategoryPickerSheet` |

##### 类型 C：确认/编辑（Confirm Sheet）

| 属性 | 说明 |
|------|------|
| 标题 | **必须有标题** |
| Drag Handle | 带标题 |
| 交互 | 点击底部"确认"按钮才回调，点击"取消"或遮罩关闭不执行 |
| 内容 | 可包含搜索框、输入框、Chip组、网格、LazyColumn等复杂内容 |
| 底部按钮 | **必须有**"取消"和"确认"（或"应用"）双按钮 |
| 本地状态 | 弹窗内维护本地暂存状态，确认时才提交给外部 |
| 适用场景 | 标签管理、多选关联、多级排序配置、筛选条件设置、创建/编辑表单 |
| 参考组件 | `TagPickerSheet`、`MultiSortSheet`、`RelationPickerBottomSheet` |

##### 类型 D：内容展示（Content Sheet）

| 属性 | 说明 |
|------|------|
| 标题 | **必须有标题**，通常带关闭按钮 |
| Drag Handle | 带标题，或仅指示条 + 内容内标题栏 |
| 交互 | 无底部确认按钮，点击关闭按钮/遮罩/返回键关闭 |
| 内容 | 可滚动（`verticalScroll` 或 `LazyColumn`），支持复杂布局（网格、横滑列表、Tab切换） |
| 适用场景 | 成就详情、录音面板、主题预览、头像来源选择、快速换装 |
| 参考组件 | `AchievementDetailSheet`、`VoiceRecordBottomSheet`、`OutfitQuickSwitchSheet` |

#### 12.1.12.4 特殊场景规范

##### 搜索框规范（类型B/C）

| 属性 | 值 |
|------|------|
| 圆角 | 12.dp |
| 背景 | `MaterialTheme.colorScheme.surfaceVariant` |
| 高度 | 40.dp |
| 内边距 | 水平12.dp |
| 搜索图标 | 18.dp，颜色 `onSurfaceVariant` |
| 与标题间距 | 12.dp |
| 与列表间距 | 12.dp |

##### 网格布局规范（类型B/C，如颜色选择、表情选择）

| 属性 | 值 |
|------|------|
| 列数 | 3-5列（根据元素尺寸调整） |
| 间距 | 12.dp |
| 元素尺寸 | 48-56dp（圆形或方形） |
| 选中态 | 3dp主题色边框 + 缩放1.05倍 + 居中✓图标 |

##### 横滑列表规范（类型D，如装扮选择）

| 属性 | 值 |
|------|------|
| 卡片宽度 | 100dp |
| 卡片高度 | 120dp |
| 卡片圆角 | 16.dp |
| 卡片阴影 | 2dp |
| 间距 | 12.dp |
| 内容内边距 | 16.dp |
| 选中态 | 主题色背景 + "✓ 当前"标签 |

##### 最大高度限制

| 场景 | 最大高度 |
|------|---------|
| 简单列表（≤6项） | wrapContent（不设限制） |
| 长列表/搜索/复杂内容 | `fillMaxHeight(0.9f)`（最大占屏90%） |
| 录音等特殊面板 | `fillMaxHeight(0.9f)` |

#### 12.1.12.5 与其他弹窗的边界

| 弹窗类型 | 适用场景 | 不适用场景 |
|---------|---------|-----------|
| **ModalBottomSheet（底部弹窗）** | 选项列表、选择器、轻量编辑、操作菜单 | 不可恢复的破坏性操作确认 |
| **AlertDialog（二次确认弹窗）** | 不可恢复操作的事前确认（删除、清空、覆盖） | 多选项列表、复杂编辑、内容展示 |
| **Snackbar** | 可恢复操作的事后反馈（移入回收站、归档），5s撤销 | 事前拦截、多选项选择、内容展示 |
| **全屏Dialog/Navigation** | 复杂编辑页面（如待办编辑、灵感编辑） | 简单选项选择（2-6项） |

#### 12.1.12.6 现有弹窗审计与迁移计划（22处，2026-07-25）

##### A. 已符合规范的弹窗（9处）

| # | 文件 | 类型 | 符合点 |
|---|------|------|--------|
| 1 | `ActionBottomSheet.kt` | A（操作列表） | 24dp圆角、surface颜色、自定义dragHandle、标准间距 |
| 2 | `CategoryPickerSheet.kt` | B（单选列表） | 24dp圆角、surface颜色、自定义dragHandle |
| 3 | `ColorPickerBottomSheet.kt` | B（单选） | 24dp圆角、surface颜色、带标题dragHandle |
| 4 | `MultiSortSheet.kt` | C（确认/编辑） | 24dp圆角、surface颜色、带标题dragHandle、底部按钮 |
| 5 | `SortBottomSheet.kt` | B（单选） | 24dp圆角、surface颜色、自定义dragHandle |
| 6 | `TagPickerSheet.kt` | C（确认/编辑） | 24dp圆角、surface颜色、自定义dragHandle、底部按钮 |
| 7 | `DateTypePickerBottomSheet.kt` | B/C混合 | 24dp圆角、surface颜色 |
| 8 | `InspirationLongPressSheet.kt` | A（操作列表） | surface颜色、自定义dragHandle |
| 9 | `AchievementDetailSheet.kt` | D（内容展示） | 24dp圆角、surface颜色、自定义dragHandle |

##### B. 需修复的问题弹窗（13处，按优先级排序）

| # | 文件 | 问题 | 修复优先级 |
|---|------|------|-----------|
| 1 | `CategoryOperationSheet.kt` (OperationSheets.kt) | 硬编码 `Color.White`（深色模式异常）、未指定shape、使用默认dragHandle | **高** |
| 2 | `DateTypeOperationSheet.kt` (OperationSheets.kt) | 硬编码 `Color.White`、未指定shape、使用默认dragHandle | **高** |
| 3 | `RelationPickerBottomSheet.kt` | 硬编码 `Color.White`、未指定shape、使用默认dragHandle | **高** |
| 4 | `AvatarSourceSheet.kt` | 未指定shape、使用默认dragHandle | 中 |
| 5 | `PriorityPickerSheet.kt` | 未指定shape、使用默认dragHandle | 中 |
| 6 | `RelationListBottomSheet.kt` | 未指定shape、使用默认dragHandle | 中 |
| 7 | `OutfitQuickSwitchSheet.kt` | 未指定shape/containerColor、使用默认dragHandle | 中 |
| 8 | `ShareDateSheet.kt` | 未指定shape/containerColor、使用默认dragHandle | 中 |
| 9 | `ShareInspirationSheet.kt` | 未指定shape/containerColor、使用默认dragHandle | 中 |
| 10 | `ThemePickerBottomSheet.kt` | 未指定shape/containerColor、使用默认dragHandle | 中 |
| 11 | `VoiceRecordBottomSheet.kt` | 未指定shape/containerColor、使用默认dragHandle | 中 |
| 12 | `QuickAddTodo` (HomeScreen.kt) | 未指定shape、使用默认dragHandle | 中 |
| 13 | `ReminderPickerBottomSheet` 包裹 (HomeScreen.kt) | 未指定shape、使用默认dragHandle | 中 |

##### 修复要点

1. **高优先级（3处硬编码白色）**：
   - 将 `containerColor = Color.White` 改为 `containerColor = MaterialTheme.colorScheme.surface`
   - 添加 `shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`
   - 添加 `dragHandle = null` 并实现自定义40dp指示条

2. **中优先级（10处未指定shape/dragHandle）**：
   - 添加 `shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)`
   - 添加 `dragHandle = null` 并实现自定义40dp指示条
   - 检查并统一内边距为 `horizontal = 24.dp`

3. **迁移策略**：
   - 优先复用 `ActionBottomSheet` 组件封装类型A弹窗（如 `CategoryOperationSheet`、`ShareDateSheet`、`ShareInspirationSheet` 等简单列表可直接复用）
   - 类型B/C/D弹窗在各自组件内按规范统一视觉参数
   - 不要求一次性全部迁移，新写弹窗必须遵守规范，旧弹窗在迭代时逐步修复

#### 12.1.12.7 不允许的用法

| 违规用法 | 原因 | 正确做法 |
|---------|------|---------|
| `containerColor = Color.White` | 不适配深色模式 | 使用 `MaterialTheme.colorScheme.surface` |
| 不指定 `shape` 参数 | 各弹窗圆角不一致（M3默认~28dp vs 规范24dp） | 显式指定 `RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)` |
| 使用M3默认 `dragHandle` | 默认dragHandle样式与自定义指示条视觉不一致 | `dragHandle = null` + 自定义40×4dp指示条 |
| 自定义enter/exit动画 | 破坏动画一致性 | 使用M3默认动画 |
| 弹窗内直接执行业务逻辑后不关闭 | 用户点击后弹窗未消失，状态混乱 | 先 `sheetState.hide()` 再执行回调（参考类型A样板） |
| 列表项高度 < 48.dp | 点击热区过小，不易点击 | 最小高度48dp，推荐56dp |
| 类型C弹窗无底部确认按钮 | 用户修改后无明确反馈，不知道是否生效 | 必须有"取消/确认"双按钮 |
| 类型A/B弹窗点击后不立即关闭 | 操作反馈不明确 | 点击选项后立即hide并执行回调 |
| 不同弹窗使用不同的指示条尺寸（36dp/40dp/线） | 顶部视觉混乱 | 统一40dp × 4dp |

#### 12.1.12.8 排版变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-07-25 | v1.0 | 初始规范：基于项目内22个现有ModalBottomSheet审计结果，确立统一视觉标准：24dp顶部圆角、surface容器色、40×4dp自定义dragHandle、24dp标准内边距、56dp列表项高度；定义4种弹窗变体（操作列表/单选列表/确认编辑/内容展示）；明确与AlertDialog/Snackbar的适用边界；列出3处高优先级问题（硬编码白色）和10处中优先级问题需迁移修复 |

### 12.1.13 侧滑栏 Tab 切换规范

> **适用范围**：[AppDrawer.kt](../../app/src/main/java/com/corgimemo/app/ui/components/AppDrawer.kt) TODO Tab 下的 [DrawerSectionTab](../../app/src/main/java/com/corgimemo/app/ui/components/appdrawer/sections/DrawerSectionTab.kt) 组件
>
> **关联文件**：
> - 薄壳层：[AppDrawer.kt](../../app/src/main/java/com/corgimemo/app/ui/components/AppDrawer.kt)（33 个参数透传）
> - 主入口实现：[AppDrawerContentImpl.kt](../../app/src/main/java/com/corgimemo/app/ui/components/appdrawer/sections/AppDrawerContentImpl.kt)
> - Tab 组件：[DrawerSectionTab.kt](../../app/src/main/java/com/corgimemo/app/ui/components/appdrawer/sections/DrawerSectionTab.kt)
> - 状态分区：[StatusFilterSection.kt](../../app/src/main/java/com/corgimemo/app/ui/components/appdrawer/sections/StatusFilterSection.kt)
> - 数据模型：[StatusFilter.kt](../../app/src/main/java/com/corgimemo/app/viewmodel/StatusFilter.kt)、[DrawerSection.kt](../../app/src/main/java/com/corgimemo/app/ui/components/appdrawer/model/DrawerSection.kt)
> - ViewModel：[HomeViewModel.kt](../../app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) 5 个新计数 + `_statusFilter` + `setStatusFilter`/`clearStatusFilter`
> - 实施计划：[侧滑栏添加状态管理切换功能实施计划.md](../../../../.trae/documents/侧滑栏添加状态管理切换功能实施计划.md)

#### 12.1.13.1 视觉规格（DrawerSectionTab）

| 元素 | 规格 |
|------|------|
| 容器 | `Row.fillMaxWidth()`，horizontal=20dp，vertical=8dp |
| 标题字号 | 16sp |
| 标题字重（激活） | Bold |
| 标题字重（未激活） | Normal |
| 标题颜色（激活） | `UiColors.Primary` |
| 标题颜色（未激活） | `Color(0xFF1C1B1F)` |
| 横线高度 | 3dp |
| 横线颜色（激活） | `UiColors.Primary` |
| 横线颜色（未激活） | `Color.Transparent`（占位避免布局抖动） |
| 标签间距 | 24dp |
| 标签 padding | horizontal=4dp, vertical=4dp |
| 点击反馈 | `Modifier.clickable` + `Modifier.clip(RoundedCornerShape(8.dp))`（圆角矩形波纹） |

#### 12.1.13.2 适用场景

- 侧滑栏内多分区互斥切换（v2026-07-27 首例："分组管理" vs "状态管理"）
- 互斥选择：点击其中一个自动取消其他选中
- 横线指示器随选中态切换（**不需要动画**，使用 alpha 二值切换，足够平滑）

#### 12.1.13.3 状态过滤（StatusFilter）规范

| 状态 | 含义 | 计数数据源 | 图标 |
|------|------|-----------|------|
| `ALL` | 全部（默认） | `HomeViewModel.totalTodoCount`（`todos.size`） | 📋 |
| `PINNED` | 置顶（isPinned=true） | `HomeViewModel.pinnedCount` | 📌 |
| `PENDING` | 待完成（!isPinned && status=0） | `HomeViewModel.pendingCount` | ⏳ |
| `COMPLETED` | 已完成（status=1） | `HomeViewModel.completedCount` | ✅ |
| `OVERDUE` | 已过期（status=0 && MoodManager.isOverdue(dueDate)） | `HomeViewModel.overdueCount` | ⏰ |
| `REPEAT_REMINDER` | 重复提醒（repeatType != 0） | `HomeViewModel.repeatReminderCount` | 🔁 |

**过滤行为**：状态过滤 + 分类过滤（来自 `CategoryGroupSection`）是 **AND 组合关系**，可同时生效。

**典型用例**：
- "已过期 + 未分类" = 所有过期未分类待办
- "置顶 + 工作" 分组 = 工作分组下的所有置顶待办
- "重复提醒 + 学习" 分组 = 学习分组下设置了重复提醒的待办

#### 12.1.13.4 视觉样板（结构）

```
┌────────────────────────────────────────────┐
│ [用户头像] 昵称              48dp 头像       │
│            签名                              │
├────────────────────────────────────────────┤
│                                             │
│  分组管理    状态管理    ← 16sp Tab 切换器   │
│   ━━━━━                                       │
│  分组管理（标题 + 橙线）                       │
│  📋 全部待办       (N)        →              │
│  📦 未分类         (N)        →              │
│  📚 学习           (N)        ⋮              │
│  ...                                          │
│                                             │
│  [+ 添加分组]        ← 共用底部按钮           │
└────────────────────────────────────────────┘
```

**双标题结构**（关键设计）：
- **外层 Tab 切换器**（"分组管理" | "状态管理"）：始终显示，切换激活态
- **内层分区标题**（"分组管理" / "状态过滤"）：仅当对应 Tab 激活时显示
- 切换时内层标题**不变位置**（避免布局抖动）

#### 12.1.13.5 实施常量参考

[DrawerSectionTab.kt](../../app/src/main/java/com/corgimemo/app/ui/components/appdrawer/sections/DrawerSectionTab.kt) 中：

```kotlin
// 标签间距
val TAB_SPACING = 24.dp

// 横线高度
val TAB_INDICATOR_HEIGHT = 3.dp

// 标签圆角（点击反馈区域）
val TAB_CLIP_RADIUS = 8.dp

// 标签 padding
val TAB_PADDING_HORIZONTAL = 4.dp
val TAB_PADDING_VERTICAL = 4.dp

// 标题与横线间距
val TAB_TITLE_TO_INDICATOR_GAP = 6.dp
```

#### 12.1.13.6 不允许的用法

| 违规用法 | 原因 | 正确做法 |
|---------|------|---------|
| 用 `BottomNavigation` 组件做侧滑栏内 Tab | 语义错误，BottomNavigation 适用于底部导航 | 用 `Row` + `Column` 自实现轻量 Tab |
| Tab 切换时不传 `Modifier.clickable` | 用户无法点击 | 必须有 `clickable` + `clip` 提供点击波纹 |
| 横线高度与宽度不固定 | 切换时布局抖动 | 横线 `height=3dp` + `fillMaxWidth()` 固定占位（激活态换色） |
| Tab 状态持久化到 DataStore | 状态切换是临时意图，不应持久化 | 用 `rememberSaveable` 即可，进程死回默认 [DrawerSection.GROUP] |
| 状态过滤与 hideCompleted 同时使用时优先级混乱 | 过滤链顺序敏感 | 状态过滤必须在 hideCompleted 之后、分类过滤之前（见 HomeViewModel.filteredTodos） |
| 5 个状态项的图标用纯文字 | 视觉重量不足 | 用 emoji（📌/⏳/✅/⏰/🔁）保持与现有"分组管理"风格一致 |

#### 12.1.13.7 排版变更记录

| 日期 | 版本 | 变更 |
|------|------|------|
| 2026-07-27 | v1.0 | 初始规范：DrawerSectionTab + StatusFilterSection 组件上线；HomeViewModel 新增 5 个计数 StateFlow（totalTodoCount/overdueCount/repeatReminderCount + 复用现有 pinnedCount/pendingCount/completedCount）+ `_statusFilter` + `setStatusFilter`/`clearStatusFilter`；filteredTodos 新增状态过滤分支（与分类过滤 AND 组合）；AppDrawerContent 参数从 24 增加到 33（薄壳透传 9 个新参数） |


