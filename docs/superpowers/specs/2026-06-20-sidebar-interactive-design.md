# 侧边栏轻量交互演示设计

## 概述

为 `corgimemo-showcase.html` 原型展示章节中的三个侧边栏原型（侧边栏·待办、侧边栏·灵感、侧边栏·日期）添加轻量交互演示，包括侧滑导航栏的滑出/隐藏、点击分组/标签/类型项切换高亮、以及主内容区的联动筛选。

## 技术方案

**方案C：CSS Transition + 原生 Touch/Mouse 事件**

- 无外部依赖，纯原生实现
- 支持跟手拖拽（touch + mouse 双端）
- CSS `transition` 做动画，性能好
- 与现有代码风格一致

## 一、侧边栏滑出/隐藏交互

### 初始状态

- 三个侧边栏原型的 drawer 默认**隐藏**（`transform: translateX(-100%)`）
- overlay 默认隐藏（`opacity: 0; pointer-events: none`）

### 触发方式

1. **点击 ☰ 按钮**：点击 appbar 右侧的 `☰` 图标，侧边栏滑出
2. **右滑手势**：在手机屏幕区域从左边缘向右滑动，侧边栏跟手滑出
3. **关闭方式**：点击遮罩层 / 从右向左滑 / 侧边栏完全展开后再次左滑

### 动画

- CSS `transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1)` 平滑过渡
- 拖拽过程中取消 transition，直接跟手设置 `transform: translateX(Xpx)`
- 松手后根据拖拽距离判断：超过 50% 宽度则展开，否则回弹

### CSS 改动

- `.phone-drawer` 默认 `transform: translateX(-100%)`，添加 `.drawer-open` 类时 `transform: translateX(0)`
- `.phone-drawer-overlay` 默认隐藏，`.drawer-open` 时显示

## 二、点击分组/标签/类型项交互

### 交互行为

- 点击侧边栏列表项，该项高亮为 active 状态，其他项取消高亮
- 点击"全部待办/全部灵感/全部日期"时，恢复显示所有内容
- 点击"添加分组"/"添加标签"按钮，弹出轻量 toast 提示"功能演示中"

### 视觉反馈

- 点击项时添加 `.active` 类（已有样式：橙色背景高亮）
- 添加点击涟漪效果（轻量实现）

## 三、内容联动筛选

### 待办侧边栏

为每个待办任务添加 `data-group` 属性：

| 点击项 | data-group 值 | 显示内容 |
|---|---|---|
| 全部待办 | all | 所有任务 |
| 学习 | study | 高数作业、英语听力练习 |
| 活动 | activity | 社团活动报名 |
| 未分类/工作/生活/运动/最近删除 | 其他 | 显示"暂无该分组待办"空状态 |

### 灵感侧边栏

为每个灵感卡片添加 `data-tag` 属性：

| 点击项 | data-tag 值 | 显示内容 |
|---|---|---|
| 全部灵感 | all | 所有灵感卡片 |
| 学术 | academic | "毕业论文可以研究柯基陪伴对学习效率的影响" |
| 生活 | life | "周末去公园拍照，记录秋天的颜色" |
| 效率 | efficiency | "试试用番茄钟配合待办清单，效率翻倍" |
| 创意 | creative | 显示"暂无该标签灵感"空状态 |

### 日期侧边栏

为每个日期卡片添加 `data-type` 属性：

| 点击项 | data-type 值 | 显示内容 |
|---|---|---|
| 全部日期 | all | 所有日期卡片 |
| 倒计时 | countdown | 妈妈生日、毕业典礼 |
| 正计时 | countup | 在一起 |
| 已过期 | expired | 显示"暂无已过期日期"空状态 |

### 空状态提示

- 无匹配项时在内容区显示轻量空状态提示文字
- 样式：居中、灰色文字、小图标

## 实现要点

1. **HTML 改动**：为三个侧边栏原型的内容卡片添加 `data-group`/`data-tag`/`data-type` 属性
2. **CSS 改动**：drawer 默认隐藏、drawer-open 状态、overlay 联动、空状态样式、涟漪效果
3. **JS 改动**：
   - ☰ 按钮点击事件 → toggle drawer
   - overlay 点击事件 → 关闭 drawer
   - touch/mouse 拖拽事件 → 跟手滑出/滑回
   - drawer-item 点击事件 → 高亮切换 + 内容筛选
   - 添加分组/标签按钮 → toast 提示
