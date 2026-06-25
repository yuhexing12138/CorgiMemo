# 飞书风格左滑操作组件（Web 版 v3 - 级联重叠堆叠展开）实施计划

> **For agentic workers:** 按 Task 顺序逐步实现，每个 Task 完成后验证。Steps 使用 checkbox (`- [ ]`) 语法跟踪。

**Goal:** 在 `docs/analysis/todo-swipe-prototype.html` 单文件中重构为 v3 级联重叠堆叠展开动效（3 按钮按 分享→置顶→删除 顺序、无间隙、重叠出现），并更新参数控制面板。

**Architecture:** 单文件 HTML+CSS+JS。核心算法：内容层 `translateX` 跟手，按钮 transform/opacity 由 `revealProgress` 和 `staggerRatio` 共同决定，按 分享→置顶→删除 顺序从右向左级联展开。`maxGap` 参数移除（v3 按钮紧密相邻无间隙），`staggerRatio` 参数新增（控制级联延迟比例）。z-index 从左到右倒置 3→2→1（分享最高，删除最低）。

**Spec:** [2026-06-25-swipe-actions-web-component-design.md](file:///c:/Users/EDY/Desktop/CorgiMemo/docs/superpowers/specs/2026-06-25-swipe-actions-web-component-design.md)

---

## 文件结构

| 文件 | 状态 | 责任 |
|------|------|------|
| `docs/analysis/todo-swipe-prototype.html` | **重构 v3** | 单文件组件 + 演示页 + 参数控制面板 |

---

## Task 1: 重构按钮动画算法为级联模式（核心）

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 替换 updateButtonsSync 为 updateButtonsCascade**

将 v2 的"单变量同步驱动 + maxGap 间距"逻辑替换为 v3 的"级联延迟 + 重叠堆叠（偏移量计算）"：

```javascript
function updateButtonsCascade(revealPx) {
  const revealProgress = revealPx / actionsWidth;
  const staggerStep = staggerRatio;
  btnElements.forEach((btnEl, index) => {
    const localStart = index * staggerStep;
    const denom = 1 - localStart;
    const localProgress = denom > 0
      ? Math.max(0, Math.min(1, (revealProgress - localStart) / denom))
      : (revealProgress >= localStart ? 1 : 0);
    // 关键：translateX 是相对按钮原始位置的偏移量
    //   分享(i=0) 初始偏移 144 → 终态 0
    //   置顶(i=1) 初始偏移 72  → 终态 0
    //   删除(i=2) 初始偏移 0   → 终态 0
    const offset = (buttons.length - 1 - index) * BUTTON_WIDTH;
    const translateX = offset * (1 - localProgress);
    btnEl.style.transform = 'translateX(' + translateX + 'px)';
    btnEl.style.opacity = String(localProgress);
  });
}
```

- [ ] **Step 2: 验证收起状态（revealProgress=0）**

| 按钮 i | 原始位置 | offset | translateX | 视觉位置 |
|--------|---------|--------|------------|---------|
| 0 分享 | 0 | 144 | 144 | 144 |
| 1 置顶 | 72 | 72 | 72 | 144 |
| 2 删除 | 144 | 0 | 0 | 144 |

全部堆叠在 Delete 槽位 144px。

- [ ] **Step 3: 验证展开 50% 状态（revealProgress=0.5）**

| 按钮 i | localProgress | translateX | 视觉位置 | opacity |
|--------|---------------|------------|---------|---------|
| 0 分享 | 0.50 | 72 | 72 | 0.50 |
| 1 置顶 | 0.375 | 45 | 117 | 0.375 |
| 2 删除 | 0.167 | 0 | 144 | 0.167 |

分享已滑到一半，置顶在 117px 位置，删除刚开始淡入仍在 144px。

- [ ] **Step 4: 验证完全展开（revealProgress=1.0）**

| 按钮 i | localProgress | translateX | 视觉位置 |
|--------|---------------|------------|---------|
| 0 分享 | 1.0 | 0 | 0 |
| 1 置顶 | 1.0 | 0 | 72 |
| 2 删除 | 1.0 | 0 | 144 |

按钮紧密相邻（间距 0px），回到各自原始槽位。

---

## Task 2: 修改 z-index 顺序（倒置）

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 修改按钮 z-index 赋值**

将 v2 的 `btnEl.style.zIndex = String(index + 1)` 改为倒置：

```javascript
// v3: z-index 从左到右 3→2→1（分享最高，删除最低）
btnEl.style.zIndex = String(buttons.length - index);
```

- [ ] **Step 2: 验证级联时左侧按钮始终可见**

- 分享（i=0）z-index=3，最上层
- 置顶（i=1）z-index=2，中层
- 删除（i=2）z-index=1，最下层

---

## Task 3: 调整全局参数对象

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 替换 maxGap 为 staggerRatio**

```javascript
const globalParams = {
  duration: 350,
  staggerRatio: 0,     // v3 新增：级联延迟比例（默认 0：所有按钮同步）
  thresholdRatio: 1/3, // v3+：吸附比例 0-1（默认 1/3）
  easing: 'cubic-bezier(0.25, 0.46, 0.45, 0.94)'
};
```

- [ ] **Step 2: 移除 maxGap 相关引用**

检查并移除所有对 `params.maxGap` 和 `globalParams.maxGap` 的引用。

---

## Task 4: 更新参数控制面板 UI

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 替换"最大间距"控件为"级联延迟比例"**

```html
<div class="control-group">
  <label>
    级联延迟比例
    <span class="value" id="staggerValue">0.00</span>
  </label>
  <input type="range" id="staggerSlider" min="0" max="0.5" step="0.05" value="0">
</div>
```

- **staggerRatio = 0**（默认）：所有按钮同步移动（无级联）
- **staggerRatio = 0.2**：飞书式级联效果示例
- **staggerRatio = 0.5**：强级联效果（按钮依次出现明显）

- [ ] **Step 2: 绑定滑块事件**

```javascript
staggerSlider.addEventListener('input', function () {
  globalParams.staggerRatio = parseFloat(this.value);
  staggerValue.textContent = globalParams.staggerRatio.toFixed(2);
  rebuildAllInstances();
});
```

- [ ] **Step 3: 更新页面标题与副标题**

将 "3 按钮同步从右向左堆叠展开 · 间距动态从 0 渐增至 maxGap" 改为 "3 按钮按 分享 → 置顶 → 删除 顺序从右向左级联重叠堆叠展开 · 无间隙 · 紧密相邻"。

---

## Task 4.5: 新增吸附比例控制（v3+）

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 控制面板新增"吸附比例"滑块**

```html
<div class="control-group">
  <label>
    吸附比例
    <span class="value" id="thresholdValue">0.33</span>
  </label>
  <input type="range" id="thresholdSlider" min="0" max="1" step="0.05" value="0.33">
</div>
```

- **thresholdRatio = 0**：只要滑动过任意距离即吸附展开
- **thresholdRatio = 1/3**（默认）：飞书原版行为
- **thresholdRatio = 1**：必须完全展开才吸附

- [ ] **Step 2: 绑定滑块事件**

```javascript
thresholdSlider.addEventListener('input', function () {
  globalParams.thresholdRatio = parseFloat(this.value);
  thresholdValue.textContent = globalParams.thresholdRatio.toFixed(2);
  rebuildAllInstances();
});
```

- [ ] **Step 3: 调整组件 thresholdRatio 来源优先级**

在 `create()` 函数中改为：优先 `options.thresholdRatio`（实例级） → 其次 `params.thresholdRatio`（全局） → fallback `1/3`。

---

## Task 4.6: 调整动画时长滑块范围为 0-500ms（v3+）

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 修改 durationSlider 的 min 值**

```html
<input type="range" id="durationSlider" min="0" max="500" step="10" value="350">
```

- **duration = 0**：无动画直接跳变（适用于调试/无障碍场景）
- **duration = 350**（默认）：飞书式丝滑动画
- **duration = 500**：放慢动画便于观察级联细节

---

## Task 5: 修复 resetButtons 初始位置

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 使用新公式初始化**

```javascript
function resetButtons() {
  updateButtonsCascade(0);
}
```

- [ ] **Step 2: 验证 3 按钮全部在 144px 位置、opacity=0**

---

## Task 6: 优化吸附期按钮跟随

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 验证 rAF 循环正确读取 contentLayer 实时位置**

吸附动画期用 rAF 持续调用 `getRevealPx()` + `updateButtonsCascade()`，直到 transitionend。

- [ ] **Step 2: 验证收起时按钮反向级联**

从展开到收起，3 按钮按 删除 → 置顶 →分享 顺序依次收回堆叠（先隐藏的先出现，收起时反过来）。

---

## Task 7: 保留演示页场景

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 示例 1 - 3 按钮标准（保留）**

分享/置顶/删除。

- [ ] **Step 2: 示例 2 - 2 按钮简化**

置顶/删除（仍按级联模式：先置顶、后删除）。

- [ ] **Step 3: 示例 3 - 4 按钮扩展**

标记已读/置顶/编辑/删除（4 按钮级联）。

- [ ] **Step 4: 示例 4 - 长内容卡片**

验证容器高度自适应。

- [ ] **Step 5: 示例 5 - 带图片卡片**

验证任意内容嵌入。

---

## Task 8: 更新效果对比区

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 创建对比区 UI**

左右两栏：
- 左：v1 旧版依次挤出（保留作历史对比）
- 右：v3 新版级联重叠堆叠展开

- [ ] **Step 2: 替换 v2 mode 为 v3 mode**

将 `'sync'` 改为 `'cascade'`，所有示例默认使用 cascade 模式。

---

## Task 9: 性能优化

**Files:**
- Modify: `docs/analysis/todo-swipe-prototype.html`

- [ ] **Step 1: 添加 will-change**

```css
.swipe-btn { will-change: transform, opacity; }
.swipe-content { will-change: transform; }
```

- [ ] **Step 2: 验证 rAF 生命周期**

吸附结束后立即 cancelAnimationFrame，避免空跑。

- [ ] **Step 3: 验证 destroy 清理**

destroy 方法正确移除事件监听和取消 rAF。

---

## Task 10: 验收清单核验

**Files:**
- 无

- [ ] **Step 1: 逐项核验 AC-1 ~ AC-16**

| AC | 验收项 | 核验方式 |
|----|--------|----------|
| AC-1 | 未滑动时 3 按钮完全不可见 | 视觉检查 |
| AC-2 | 横向左滑触发，竖向不触发 | 移动端模式测试 |
| AC-3 | <72px 松手回弹 | 短距离拖拽 |
| AC-4 | ≥72px 松手吸附 | 长距离拖拽 |
| AC-5 | 最大位移 216px | 暴力拖拽 |
| AC-6 | 3 按钮按 分享→置顶→删除 顺序级联 | 缓慢拖拽观察 |
| AC-7 | 初始 3 按钮完全重叠在 Delete 槽位 | 视觉检查 |
| AC-8 | 完全展开按钮紧密相邻（间距 0） | 视觉检查 |
| AC-9 | 级联过程中按钮视觉重叠 | 中段状态观察 |
| AC-10 | 透明度按级联顺序 0→1 | 视觉体感 |
| AC-11 | 点击内容层收起 | 展开后点击内容 |
| AC-12 | 点击按钮回调后收起 | 展开后点击按钮 |
| AC-13 | 350ms ease-out 跟手无延迟 | 视觉体感 |
| AC-14 | 可嵌入任意容器 | 5 个示例验证 |
| AC-15 | 参数控制面板实时调整 | 滑块测试 |
| AC-16 | 桌面鼠标可拖拽 | 鼠标测试 |

- [ ] **Step 2: 修复发现的问题**

---

## Task 11: 询问用户是否进行 git 提交

- [ ] **Step 1: 询问用户是否进行 git 提交**

---

## 后续优化（不在本次范围）

- 集成到 Compose 版 `SwipeableTodoBox` 的视觉参考
- 多卡片互斥展开
- 振动反馈（navigator.vibrate）
- 按钮长按触发二次确认
- 移动端事件 passive listener 优化
- IntersectionObserver 处理视窗外卡片
