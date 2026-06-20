# 刻记⁺ 展示页 — 内容与排版重构设计文档

> 日期：2026-06-19
> 状态：已批准
> 文件：`【刻记+】APP/corgimemo-showcase.html`

## 1. 背景与目标

展示页当前存在信息分散问题：
- **产品愿景**（"让记录变得不再枯燥..."）孤立在核心价值与差异化优势之间，与 Hero 区域脱节
- **三大核心价值**与**差异化优势**分为两个独立章节（01/02），信息碎片化
- **Hero 区域**缺少品牌核心理念表达，视觉冲击力不足

### 重构目标
1. 将产品愿景迁移至 Hero 区域，提升首屏情感传达效率
2. 合并「三大核心价值」与「差异化优势」为统一板块
3. 保持品牌视觉风格一致性（杂志风 + 野兽派混合风格）
4. 确保响应式布局在各设备上表现良好

## 2. 设计决策记录

| 决策点 | 选择 | 理由 |
|--------|------|------|
| Hero 重构方向 | 方案 A：愿景融入式 | 改动最小，保持杂志风不变，愿景自然融入首屏 |
| 板块合并方式 | 价值卡片为主，标签为辅 | 保留核心价值卡片主体地位，标签作为补充说明 |
| 愿景展示样式 | 独立引言区 | 在刊头下方以独立区域展示，斜体+暖色调，视觉层次清晰 |

## 3. 详细设计方案

### 3.1 Hero 区域重构

**新增元素：产品愿景引言区**

位置：杂志刊头（`.magazine-header`）与不对称分栏（`.magazine-split`）之间

```
┌─────────────────────────────────────────────┐
│  杂志刊头 (magazine-header) — 保持不变       │
├─────────────────────────────────────────────┤
│  ★ 新增：愿景引言区 (hero-vision-quote)      │
│  "让记录变得不再枯燥，                        │
│   让忙碌的人也能感受到温暖。"                  │
│  样式：居中、斜体、暖橙色、细线上下分隔        │
├─────────────────────────────────────────────┤
│  不对称分栏 (magazine-split) — 保持不变       │
│  左：标题/副标题/标语  右：柯基动画卡片        │
└─────────────────────────────────────────────┘
```

**CSS 规范：**
```css
/* Hero 愿景引言区 — 杂志刊头下方独立引言 */
.hero-vision-quote {
    text-align: center;
    padding: 16px 20px;
    border-top: 1px solid rgba(51, 51, 51, 0.15);
    border-bottom: 1px solid rgba(51, 51, 51, 0.15);
    background: #fffbf5;
    margin: 0;
}

.hero-vision-quote p {
    font-family: Georgia, 'Noto Serif SC', serif;
    font-style: italic;
    font-size: 15px;
    color: var(--color-primary-bold); /* #e86b35 */
    line-height: 1.6;
    margin: 0;
}
```

**响应式适配：**
- 桌面端（>=768px）：字号 15px，padding 16px 20px
- 移动端（<768px）：字号 13px，padding 12px 16px

### 3.2 核心价值与优势合并板块

**原结构 → 新结构：**

| 原章节 | 原编号 | 处理方式 |
|--------|--------|----------|
| 三大核心价值 | 01 | 保留卡片内容，合并入新章节 |
| 产品愿景引言 | （独立块） | **删除**（已迁移至 Hero） |
| 差异化优势 | 02 | 降级为标签组，融入新章节底部 |

**新统一章节结构：**

```
┌──────────────────────────────────────────────────┐
│  [01]  CORE VALUES & DIFFERENTIATION             │
│        核心价值与优势                              │
│                                                   │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐         │
│  │ 💕 陪伴感 │ │ 🎯 高效性 │ │ 💝 温暖感 │         │
│  │  描述...  │ │  描述...  │ │  描述...  │         │
│  └──────────┘ └──────────┘ └──────────┘         │
│                                                   │
│  - - - - - - 虚线分隔 - - - - - -                │
│  [✨原创陪伴] [🎯身份自适应] [🎯节日关怀] [💡完全原创] │
└──────────────────────────────────────────────────┘
```

**HTML 结构变更：**
```html
<!-- 合并后的统一章节 -->
<div class="chapter-section">
    <div class="chapter-number-brutal bg-orange">01</div>
    <div class="chapter-content">
        <div class="chapter-label">CORE VALUES & DIFFERENTIATION</div>
        <h2 class="section-title-brutal">核心价值与优势</h2>

        <!-- 主体：3张核心价值卡片（保持原有 stagger-grid 布局） -->
        <div class="brutal-stagger-grid">
            <div class="brutal-card-dominant stagger-item">💕 陪伴感 ...</div>
            <div class="brutal-card-dominant bg-orange stagger-item">🎯 高效性 ...</div>
            <div class="brutal-card-dominant bg-mint stagger-item">💝 温暖感 ...</div>
        </div>

        <!-- 辅助：4个差异化标签（虚线分隔 + 居中排列） -->
        <div class="diff-tags-supplementary">
            <span class="brutal-tag">✨ 原创陪伴系统</span>
            <span class="brutal-tag bg-white">🎯 身份自适应</span>
            <span class="brutal-tag bg-mint">🎉 节日节气关怀</span>
            <span class="brutal-tag bg-yellow">💡 完全原创</span>
        </div>
    </div>
</div>
```

**新增 CSS：**
```css
/* 差异化标签辅助区 — 虚线分隔 + 居中 */
.diff-tags-supplementary {
    margin-top: 20px;
    padding-top: 16px;
    border-top: 1px dashed var(--color-text-ink);
    opacity: 0.6; /* 降低视觉权重，体现"辅助"定位 */
    display: flex;
    gap: 8px;
    flex-wrap: wrap;
    justify-content: center;
}
```

### 3.3 章节编号更新

合并后后续章节编号需 -1：

| 原编号 | 原内容 | 新编号 |
|--------|--------|--------|
| 01 | 三大核心价值 | 01（合并后） |
| 02 | 差异化优势 | （已合并入 01） |
| 03 | 柯基形象体系 — 8种姿态 | **02** |
| 04 | 表情系统 — 8种表情 | **03** |
| 05 | 情绪系统 | **04** |
| ... | ... | ... |

## 4. 变更清单

### 4.1 HTML 变更
1. **Hero 区域**（L4423-4500）：在 `.magazine-header` 后、`.magazine-split` 前，插入 `.hero-vision-quote` 引言区块
2. **删除** 原 `brutal-quote` 产品愿景块（L4538-4542）
3. **合并** 章节01（L4512-4536）和章节02（L4544-4557）为统一章节
4. **更新** 所有后续 `.chapter-number-brutal` 的数字编号（03→02, 04→03, ... 25→24）

### 4.2 CSS 变更
1. **新增** `.hero-vision-quote` 及其子元素样式
2. **新增** `.diff-tags-supplementary` 辅助标签容器样式
3. **可选清理** 不再使用的 `.vision-quote` 样式（如果无其他引用）

### 4.3 响应式适配
- Hero 愿景引言区在移动端缩小字号和 padding
- 合并后的标签区域在移动端允许换行（已有 flex-wrap）
- brutal-stagger-grid 在小屏幕保持 3 列或改为单列（检查现有 media query）

## 5. 品牌视觉一致性约束

- 保持现有色彩体系：`--color-brutal-orange` (#e86b35)、`--color-brutal-mint` (#a8d5ba)、`--color-brutal-yellow` (#f5d76e)、`--color-brutal-cream` (#ffe8cc)
- 保持野兽派风格要素：粗边框（2-3px solid #333）、硬阴影（box-shadow offset）、粗圆角（4px）
- 保持杂志风字体层级：衬线标题（var(--font-serif)）+ 无衬线正文（var(--font-sans)）
- 保持错落网格的动态感（stagger animation-delay）
