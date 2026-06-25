# 飞书风格左滑操作组件（Web 版 v3 - 级联重叠堆叠展开）设计文档

> **日期**：2026-06-25
> **版本**：v3（基于参考图重构：3 按钮顺序级联、重叠展开、无间隙）
> **技术栈**：原生 HTML + CSS + JavaScript（零第三方依赖）
> **用途**：独立可复用的移动端左滑操作组件，实现 3 按钮**按 分享 → 置顶 → 删除 顺序从右向左级联重叠堆叠展开**的飞书风格动效
> **输出文件**：`docs/analysis/todo-swipe-prototype.html`（单文件，可直接浏览器运行）

---

## 一、设计目标（v3 更新）

基于参考图与最新需求重新设计：

1. **按钮顺序固定**：从左到右为 **分享 → 置顶 → 删除**（视觉与动画出现顺序一致）
2. **顺序级联动画**：3 个按钮按顺序从右向左级联展开
   - 分享（最左）最先开始滑出
   - 置顶（中间）延后开始
   - 删除（最右）最后出现（仅淡入，不平移）
3. **重叠堆叠状态**：初始时 3 个按钮**完全重叠**堆叠在操作区最右侧（Delete 槽位）
4. **无间隙最终态**：完全展开后按钮**紧密相邻**，没有任何间隙
5. **独立可复用**：组件封装为 `SwipeActions` 全局对象，不绑定业务
6. **零依赖**：原生 HTML+CSS+JS，单文件可直接运行调试

---

## 二、组件架构

### 2.1 DOM 结构

```html
<div class="swipe-container">           <!-- 根容器：relative, overflow:hidden -->
  <div class="swipe-actions">            <!-- 操作层：absolute right:0, z-index:1 -->
    <button class="swipe-btn" data-action="share" style="z-index:3">  <!-- 分享：最左 · 最高 z-index -->
      <svg>分享图标</svg><span>分享</span>
    </button>
    <button class="swipe-btn" data-action="pin" style="z-index:2">     <!-- 置顶：中间 -->
      <svg>置顶图标</svg><span>置顶</span>
    </button>
    <button class="swipe-btn" data-action="delete" style="z-index:1">  <!-- 删除：最右 · 最低 z-index -->
      <svg>删除图标</svg><span>删除</span>
    </button>
  </div>
  <div class="swipe-content">            <!-- 内容层：z-index:10, 默认覆盖操作层 -->
    <!-- 任意自定义内容 -->
  </div>
</div>
```

### 2.2 分层布局

| 层级 | 元素 | z-index | 定位 | 默认状态 |
|------|------|---------|------|----------|
| 内容层 | `.swipe-content` | 10 | `position: relative; width:100%` | 完全覆盖操作层 |
| 操作层 | `.swipe-actions` | 1 | `position: absolute; right:0; top:0; height:100%` | 被内容层遮挡 |
| 分享按钮 | `.swipe-btn` (idx=0) | 3 | `flex` 横向首位 | 重叠堆叠在右，opacity:0 |
| 置顶按钮 | `.swipe-btn` (idx=1) | 2 | `flex` 横向中位 | 重叠堆叠在右，opacity:0 |
| 删除按钮 | `.swipe-btn` (idx=2) | 1 | `flex` 横向末位 | 重叠堆叠在右，opacity:0 |

### 2.3 关键布局规则

- **容器**：`position: relative; overflow: hidden`，裁剪超出的操作层
- **操作层**：`position: absolute; right: 0; top: 0; height: 100%; width: 216px`
- **内容层**：`position: relative; width: 100%; z-index: 10`，通过 `transform: translateX()` 左滑
- **按钮**：`flex` 横向排列，等宽 72px，**z-index 从左到右 3→2→1**（分享最高，删除最低）
- **z-index 倒置原因**：级联展开时，左侧按钮（分享）先出现需显示在最上层，右侧按钮（删除）后出现在最下层
- **容器高度**：由内容层决定，操作层 `height: 100%` 跟随

---

## 三、交互流程图（v3 更新）

```
[初始状态]
  内容层 translateX(0)
  操作层 translateX(0)
  按钮[i] 初始 transform = translateX((n-1-i) * 72px) 偏移量
    分享 (i=0) tx=144, 置顶 (i=1) tx=72, 删除 (i=2) tx=0
  按钮[i] 视觉位置都在 Delete 槽位 (144) 完全重叠
  按钮 opacity = 0
  ↓ touchstart
[方向判定]
  - 记录 startX, startY
  - directionLocked = false
  ↓ touchmove 首次移动
[方向锁定]
  if |Δx| > |Δy| 且 |Δx| > 5px:
    → isHorizontal = true, 阻止默认行为
  elif |Δy| ≥ |Δx| 且 |Δy| > 5px:
    → isHorizontal = false, 允许页面滚动
  ↓ 持续 touchmove (isHorizontal=true)
[跟手阶段 - 级联动画触发]
  for each frame:
    rawTx = startOffset + deltaX
    clampedTx = clamp(rawTx, -216, 0)
    contentLayer.transform = translateX(clampedTx)
    revealPx = -clampedTx                          // 0→216
    revealProgress = revealPx / 216                // 0→1
    
    // 计算每个按钮的本地进度（级联延迟）
    for each button[i] in [0, n-1]:
      localStart = i * staggerStep                 // 0, 0.2, 0.4
      localProgress = clamp(
        (revealProgress - localStart) / (1 - localStart),
        0, 1
      )
      
      // translateX 是相对按钮原始位置的偏移量
      offset = (n-1-i) * 72                        // 144, 72, 0
      translateX = offset * (1 - localProgress)    // 堆叠偏移 → 0
      opacity = localProgress                       // 0→1 顺序淡入
  ↓ touchend
[阈值吸附]
  if revealPx < 72:  target = 0 (回弹)
  if revealPx ≥ 72:  target = -216 (吸附展开)
  contentLayer.transition = transform 350ms ease-out
  contentLayer.transform = translateX(target)
  
  // 吸附动画期按钮按新公式自动跟随
  ↓ transitionend
[稳态]
  isAnimating = false
  按钮状态 = 终态 (展开或收起)
```

---

## 四、动画参数规范（v3 新增）

### 4.1 动画参数表

| 参数 | 默认值 | 范围 | 说明 |
|------|--------|------|------|
| `duration` | 350ms | 300-500ms | 松手吸附/回弹动画时长 |
| `easing` | `cubic-bezier(0.25, 0.46, 0.45, 0.94)` | ease-out 系 | 缓动函数（缓出曲线） |
| `staggerRatio` | 0 | 0-0.5 | 级联延迟比例（每按钮延后比例，0=同步） |
| `buttonWidth` | 72px | 60-80px | 单按钮宽度 |
| `thresholdRatio` | 1/3 | 0.2-0.5 | 吸附阈值比例 |
| `directionLockThreshold` | 5px | 3-10px | 方向判定最小位移 |
| ~~`maxGap`~~ | ~~10px~~ | ~~0-30px~~ | **v3 移除**：按钮紧密相邻，无间隙 |

### 4.2 缓动函数

```css
/* 默认: 标准 ease-out 曲线 */
--ease-out: cubic-bezier(0.25, 0.46, 0.45, 0.94);

/* 可选: 强缓出（更明显减速） */
--ease-out-strong: cubic-bezier(0.16, 1, 0.3, 1);

/* 可选: 弹性（带回弹） */
--ease-out-bounce: cubic-bezier(0.34, 1.56, 0.64, 1);
```

### 4.3 动画时间线

| 阶段 | 触发 | 内容层 | 按钮层 | 时长 |
|------|------|--------|--------|------|
| 跟手 | touchmove | `transition: none` | `transition: none` | 实时 |
| 吸附 | touchend ≥ 阈值 | `transition: 350ms ease-out` | `transition: none` | 350ms |
| 回弹 | touchend < 阈值 | `transition: 350ms ease-out` | `transition: none` | 350ms |
| 收起 | close() | `transition: 350ms ease-out` | `transition: none` | 350ms |

> **关键**：跟手时按钮层**无 transition**，仅靠每帧重算 transform/opacity 实现实时级联；松手后按钮随内容层 transition 自然过渡。

### 4.4 级联时序

**staggerRatio=0（默认，所有按钮同步）**：

| 按钮 | 起始 revealProgress | 结束 revealProgress | 实际移动区间 |
|------|---------------------|---------------------|--------------|
| 分享 (i=0) | 0.0 | 1.0 | 全程 |
| 置顶 (i=1) | 0.0 | 1.0 | 全程 |
| 删除 (i=2) | 0.0 | 1.0 | 全程 |

**staggerRatio=0.2（飞书式级联）**：

| 按钮 | 起始 revealProgress | 结束 revealProgress | 实际移动区间 |
|------|---------------------|---------------------|--------------|
| 分享 (i=0) | 0.0 | 1.0 | 全程 |
| 置顶 (i=1) | 0.2 | 1.0 | 后 80% |
| 删除 (i=2) | 0.4 | 1.0 | 后 60% |

---

## 五、级联重叠堆叠算法（v3 核心）

### 5.1 数学模型

```
W (actionsWidth) = n * buttonWidth = 3 * 72 = 216px
n (按钮数) = 3
staggerRatio = 0     // 级联延迟比例（默认 0：所有按钮同步移动）
staggerStep = staggerRatio = 0  // 0 表示三个按钮同时开始移动

revealPx: 内容层左滑距离, 范围 0 → 216
revealProgress = revealPx / W                       // 0→1

// 按钮 i (i = 0, 1, ..., n-1) 的本地进度：
//  - 分享 (i=0): localStart=0.0,  全程移动
//  - 置顶 (i=1): localStart=0.2,  后 80% 移动
//  - 删除 (i=2): localStart=0.4,  后 60% 移动（最终淡入）
localStart = i * staggerStep
localProgress = clamp((revealProgress - localStart) / (1 - localStart), 0, 1)

// 关键：translateX 是相对于按钮在 flex 布局中原始位置的偏移
//   - 按钮[i] 在 flex 布局中原始位于 i * buttonWidth (0, 72, 144)
//   - 初始偏移（堆叠到 Delete 槽位 144）: (n-1-i) * buttonWidth
//     分享=144, 置顶=72, 删除=0
//   - 终态偏移: 0 (回到原始槽位)
offset = (n - 1 - i) * buttonWidth
button[i].translateX = offset * (1 - localProgress)  // 堆叠偏移 → 0
button[i].opacity    = (revealPx > 0) ? 1 : 0       // 二元：无淡入淡出
```

### 5.2 验证

**收起状态 (revealProgress=0)**：

| 按钮 i | 原始位置 | offset | localProgress | translateX | 视觉位置 | opacity | 状态 |
|--------|---------|--------|---------------|------------|---------|---------|------|
| 0 分享 | 0 | 144 | 0 | 144 | 144 | 0 | 堆叠在 Delete 槽位 |
| 1 置顶 | 72 | 72 | 0 | 72 | 144 | 0 | 堆叠在 Delete 槽位 |
| 2 删除 | 144 | 0 | 0 | 0 | 144 | 0 | 堆叠在 Delete 槽位（原始位置） |

**展开 33% 状态 (revealProgress=0.33)**：

| 按钮 i | localProgress | translateX | 视觉位置 | opacity | 状态 |
|--------|---------------|------------|---------|---------|------|
| 0 分享 | 0.33 | 144 * 0.67 = 96.5 | 96.5 | 0.33 | 已向左滑出 |
| 1 置顶 | 0.16 | 72 * 0.84 = 60.5 | 132.5 | 0.16 | 刚开始移动 |
| 2 删除 | 0 | 0 | 144 | 0 | 仍堆叠在原位 |

**展开 66% 状态 (revealProgress=0.66)**：

| 按钮 i | localProgress | translateX | 视觉位置 | opacity | 状态 |
|--------|---------------|------------|---------|---------|------|
| 0 分享 | 0.66 | 144 * 0.34 = 49 | 49 | 0.66 | 接近目标 |
| 1 置顶 | 0.58 | 72 * 0.42 = 30 | 102 | 0.58 | 滑出大半 |
| 2 删除 | 0.43 | 0 | 144 | 0.43 | 已淡入近半，仍在原位 |

**完全展开 (revealProgress=1.0)**：

| 按钮 i | localProgress | translateX | 视觉位置 | opacity | 状态 |
|--------|---------------|------------|---------|---------|------|
| 0 分享 | 1.0 | 0 | 0 | 1.0 | 槽位 0（最左） |
| 1 置顶 | 1.0 | 0 | 72 | 1.0 | 槽位 1（中间） |
| 2 删除 | 1.0 | 0 | 144 | 1.0 | 槽位 2（最右） |

按钮紧密相邻（间距 0px），无间隙。

### 5.3 重叠视觉效果

在级联动画中段（如 revealProgress=0.5）：

```
|←——————— 216px ———————→|
[分享]  [置顶]  [删除]
  ←72→  ←72→
  72     102   144
 Share  Pin   Delete
 (z=3)  (z=2) (z=1)
```

- 分享视觉位置在 72（已滑到中间），z-index 最高
- 置顶视觉位置在 102，介于分享和删除之间
- 删除视觉位置在 144（原位），z-index 最低
- 按钮紧密相邻，无间隙

### 5.4 关键实现要点

1. **操作层定位**：使用 `position: absolute; right: 0; top: 0; height: 100%; width: actionsWidth`，固定在容器右侧
2. **按钮 z-index 倒置**：从左到右 3→2→1（分享最高，删除最低），保证级联时左侧按钮始终可见
3. **堆叠起始位置**：每个按钮的 transform = translateX((n-1-i) * 72px)，分享=144, 置顶=72, 删除=0
4. **translateX 是偏移量**：相对于按钮在 flex 布局中的原始位置，不是绝对坐标
5. **跟手无延迟**：touchmove 时设置 `transition: none`，每帧重算所有按钮 transform
6. **级联算法**：按钮 transform/opacity 由 `revealProgress` 和 `staggerRatio` 共同决定

---

## 六、视觉规范

### 6.1 按钮配置

| 位置 | 按钮 | 背景色 | 图标 | 文字 | 文字颜色 | z-index | 级联顺序 |
|------|------|--------|------|------|----------|---------|----------|
| 最左 | 分享 | 黄色 `#FFD54F` | 分享图标 | 分享 | 白色 `#FFFFFF` | 3 | 第 1（最先） |
| 中间 | 置顶 | 橙色 `#FF9A5C` | 向上箭头置顶图标 | 置顶 | 白色 `#FFFFFF` | 2 | 第 2 |
| 最右 | 删除 | 正红色 `#FF5252` | 垃圾桶图标 | 删除 | 白色 `#FFFFFF` | 1 | 第 3（最后） |

### 6.2 按钮内部布局

```
┌──────────────┐
│              │
│    [图标]     │  ← 24×24px SVG，垂直居中
│    删除       │  ← 12px 无衬线字体，垂直居中
│              │
└──────────────┘
  72px × 100%高
  flex-direction: column
  align-items: center
  justify-content: center
  gap: 4px
```

### 6.3 视觉层级

- 按钮 z-index 从左到右 3→2→1（分享最高，删除最低）
- 初始堆叠时：高 z-index 按钮（分享）覆盖低 z-index 按钮，但因 opacity=0 全部不可见
- 级联展开时：分享先达到目标位置，z-index 保证它始终显示在最上层

### 6.4 可点击区域

- 每个按钮 width=72px, height=100%（与容器同高）
- 完全符合移动端点击热区 ≥ 44pt 标准
- 完全展开后按钮紧密相邻（间距 0px），整个操作区连续可点击

---

## 七、组件 API

### 7.1 初始化

```javascript
const instance = SwipeActions.create({
  container: document.getElementById('card'),
  content: '<div>待办卡片内容</div>',
  buttons: [
    { label: '分享', color: '#FFD54F', icon: 'share',  action: 'share',  onClick: (ctx) => {} },
    { label: '置顶', color: '#FF9A5C', icon: 'pin',    action: 'pin',    onClick: (ctx) => {} },
    { label: '删除', color: '#FF5252', icon: 'delete', action: 'delete', onClick: (ctx) => {} }
  ],
  // 可选参数
  duration: 350,           // 动画时长 ms
  staggerRatio: 0,          // 级联延迟比例 0-0.5（默认 0：所有按钮同步移动）
  thresholdRatio: 1/3,     // 吸附比例 0-1（默认 1/3：松手后展开/回弹的距离阈值比例）
});
```

### 7.2 实例方法

| 方法 | 说明 |
|------|------|
| `open()` | 编程式展开操作层 |
| `close()` | 编程式收起操作层 |
| `toggle()` | 切换展开/收起 |
| `destroy()` | 销毁实例 |
| `setContent(content)` | 动态替换内容层 |

---

## 八、演示页结构（v3 新增控制面板）

### 8.1 演示页分区

1. **效果对比区**：
   - 左：v1 旧版依次挤出效果（保留作对比）
   - 右：v3 新版级联重叠堆叠展开效果

2. **参数控制面板**（右侧悬浮）：
   - 动画时长滑块：300-500ms
   - **级联延迟比例滑块**：0-0.5（默认 0，所有按钮同步移动；调大则级联效果更明显）
   - 缓动函数选择：标准 ease-out / 强缓出 / 弹性
   - 实时预览开关

3. **场景示例区**：
   - 示例 1：3 按钮（标准）— 分享/置顶/删除
   - 示例 2：2 按钮 — 简化版（置顶/删除）
   - 示例 3：4 按钮 — 扩展版
   - 示例 4：长列表项
   - 示例 5：带图片卡片

---

## 九、验收标准

| 编号 | 验收项 |
|------|--------|
| AC-1 | 未滑动时 3 按钮完全不可见（opacity=0，重叠堆叠在右侧 Delete 槽位） |
| AC-2 | 横向左滑触发交互，竖向滑动不触发（允许页面垂直滚动） |
| AC-3 | 滑动距离 < 72px 松手 → 回弹复位，操作层隐藏 |
| AC-4 | 滑动距离 ≥ 72px 松手 → 吸附至终点，全部按钮露出 |
| AC-5 | 内容层左滑最大位移 = 216px，禁止超出边界 |
| AC-6 | **3 按钮按 分享 → 置顶 → 删除 顺序从右向左级联展开** |
| AC-7 | **初始状态 3 按钮完全重叠在 Delete 槽位** |
| AC-8 | **完全展开后按钮紧密相邻（间距 0px），无间隙** |
| AC-9 | **级联过程中后出现的按钮与先出现的按钮有视觉重叠** |
| AC-10 | 按钮 opacity 二元化：收起态=0，滑动/展开态=1（无淡入淡出动画） | 视觉检查 |
| AC-11 | 点击内容层 → 操作层收起 |
| AC-12 | 点击任意按钮 → 执行回调后收起 |
| AC-13 | 动画 350ms ease-out，跟手过程无延迟 |
| AC-14 | 组件可嵌入任意容器，内容层支持自定义替换 |
| AC-15 | 参数控制面板可实时调整动画参数（时长、级联比例、缓动）并预览 |
| AC-16 | 桌面端鼠标拖拽兼容 |

---

## 十、性能优化（v3 新增）

### 10.1 GPU 加速

- 仅使用 `transform` 和 `opacity` 变更（合成层属性）
- 避免触发 layout / paint，仅 composite
- `will-change: transform` 提示浏览器创建独立合成层

### 10.2 事件节流

- touchmove 使用原生事件，不做节流（浏览器 60fps 自动保证）
- rAF 循环仅在吸附动画期运行，结束后立即取消
- 方向判定一次锁定后不再执行

### 10.3 内存管理

- destroy() 移除所有事件监听
- rAF 引用保存，destroy 时 cancelAnimationFrame
- 避免闭包泄漏

---

## 十一、浏览器兼容性

| 浏览器 | 最低版本 | 备注 |
|--------|----------|------|
| Chrome | 60+ | 完整支持 |
| Safari | 12+ | 完整支持，需 `-webkit-` 前缀部分属性 |
| Firefox | 55+ | 完整支持 |
| Edge | 79+ (Chromium) | 完整支持 |
| 微信内置 | iOS 12+ / Android 8+ | 完整支持 |
| 旧版浏览器 (<2018) | 不支持 | 无 `DOMMatrix` 时降级方案 |

### 兼容性说明

- `DOMMatrix` API：用于读取实时 transform 矩阵，Chrome 61+ / Safari 11+ / Firefox 54+
- 降级方案：如不支持 `DOMMatrix`，回退到 `getComputedStyle().transform` 字符串解析

---

## 十二、v1 → v2 → v3 演进总结

| 版本 | 动画方式 | 间距 | 顺序 | 起始状态 |
|------|----------|------|------|----------|
| v1 | 依次挤出（segmented） | 无 | 右→左（Delete 先） | 各按钮在独立槽位淡入 |
| v2 | 同步展开（sync） | 0→maxGap | 同步 | 全部堆叠在右 |
| **v3** | **级联重叠堆叠（cascade）** | **始终 0** | **左→右（分享先）** | **全部堆叠在 Delete 槽位** |

v3 的核心改进：
- **更符合飞书原生体验**：按钮顺序级联出现，与"分享"作为最常用操作的认知一致
- **更紧凑的视觉**：完全展开后无间隙，操作区更紧凑
- **更自然的过渡**：按钮从右向左级联滑出，视觉上有"依次到位"的层次感
