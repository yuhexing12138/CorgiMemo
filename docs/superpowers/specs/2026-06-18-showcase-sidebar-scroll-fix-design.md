# Showcase HTML 侧边栏 & 编辑日期页面 UI/UX 优化设计

> 日期: 2026-06-18
> 文件: `【刻记+】APP/corgimemo-showcase.html`
> 状态: 已批准，待实施

## 问题概述

| # | 问题 | 现象 | 影响 |
|---|------|------|------|
| 1 | 侧边栏视觉层级缺陷 | 侧边栏背景半透明(0.06)，下方内容文字/图案清晰可见 | 用户感知为"透视"效果，缺乏层级感 |
| 2 | 编辑日期页面内容截断 | phone-body设置`overflow:hidden`，表单内容超出被裁剪 | 备注区域和操作按钮被切割，用户无法看到完整表单 |

## 设计方案

### 1. 侧边栏 - 深度磨砂模糊 (Frosted Glass)

**目标**: 被侧边栏遮挡的区域完全无法辨认下方文字和图案

**修改位置**: `.phone-drawer` 和 `.phone-drawer-overlay` (L2702-L2714)

```css
.phone-drawer {
    /* 原始: background: rgba(255, 255, 255, 0.06); */
    background: rgba(22, 33, 62, 0.88);           /* 提升至0.88不透明度 */
    backdrop-filter: blur(20px);                   /* 新增：20px深度模糊 */
    -webkit-backdrop-filter: blur(20px);           /* Safari兼容 */
}

.phone-drawer-overlay {
    /* 原始: background: rgba(0, 0, 0, 0.5); */
    background: rgba(0, 0, 0, 0.55);               /* 微调遮罩 */
    backdrop-filter: blur(4px);                    /* 遮罩层轻微模糊 */
    -webkit-backdrop-filter: blur(4px);
}
```

**涉及手机**: 手机13(侧边栏·待办)、手机14(侧边栏·灵感)、手机15(侧边栏·日期)

### 2. 编辑日期页面 - 滚动交互

**目标**: 允许鼠标滚轮滚动查看完整表单内容

**修改位置**: 使用`:has()`选择器针对包含编辑表单的phone-body启用滚动

```css
/* 当phone-body内包含编辑表单时，启用纵向滚动 */
.phone-body:has(.phone-edit-form) {
    overflow-y: auto;
    overflow-x: hidden;
    scrollbar-width: thin;                         /* Firefox细滚动条 */
    scrollbar-color: rgba(255, 154, 92, 0.3) transparent;
}

/* WebKit浏览器自定义滚动条 */
.phone-body:has(.phone-edit-form)::-webkit-scrollbar {
    width: 4px;
}
.phone-body:has(.phone-edit-form)::-webkit-scrollbar-track {
    background: transparent;
}
.phone-body:has(.phone-edit-form)::-webkit-scrollbar-thumb {
    background: rgba(255, 154, 92, 0.3);
    border-radius: 2px;
}
```

**涉及手机**: 手机16(编辑日期)

## 技术要点

- `backdrop-filter: blur()` 在现代浏览器(Chrome 76+, Safari 9+, Edge 79+, Firefox 103+)均有良好支持
- `:has()` 选择器在 Chrome 105+, Safari 15.4+, Edge 105+, Firefox 121+ 支持
- 本文件为展示原型HTML，非生产环境，可安全使用新特性
- 滚动条样式使用主题色(橙色系)保持一致性

## 验收标准

1. [ ] 三个侧边栏(待办/灵感/日期)打开时，被遮盖区域完全模糊不可读
2. [ ] 编辑日期页面的日历、类型、提醒、备注、按钮全部可见
3. [ ] 编辑日期页面支持鼠标滚轮滚动
4. [ ] 底部导航栏在编辑日期页面保持固定
