# 场景弹窗 p5.js 粒子系统设计

> 日期：2026-06-21
> 状态：已批准

## 1. 概述

将场景演示弹窗（`scene-modal`）内的装饰粒子从 CSS emoji 动画重构为 p5.js 流场驱动有机粒子系统。每种面板主题拥有独立的视觉配置，每张卡片的 emoji 作为种子产生同主题内的自然变体。

## 2. 当前问题

- 所有粒子形态和运动路径完全相同（仅 emoji 不同）
- CSS 动画缺乏与卡片内容主题的深度关联
- 粒子仅为简单垂直上浮+旋转，视觉单调

## 3. 设计方案：面板主题 + 算法变体

### 3.1 核心架构

```
scene-modal (弹窗)
├── p5.js Canvas (position:absolute, z-index:1)
│   ├── FlowField (Perlin噪声场)
│   └── Particles (有机形态粒子 × N)
└── 弹窗内容层 (z-index:2)
    ├── 柯基emoji / 标题
    └── 问候语tabs
```

### 3.2 6种面板主题配置

| 面板ID | 中文名 | 色板 | 粒子形态 | 流场特征 | 粒子数 | 大小 |
|--------|--------|------|---------|---------|--------|------|
| `outfit-traditional` | 传统节日 | #FF6B35, #FFD4A8, #FF4444 | 发光球体(灯笼光晕) | 上浮+轻摇, noise=0.008, speed=0.6 | 60 | 8-20 |
| `outfit-modern` | 现代节日 | #FF6B9D, #C084FC, #60A5FA | 纸屑几何(翻滚) | 中心爆发+下落, noise=0.012, speed=1.0 | 80 | 4-10 |
| `outfit-solar` | 节气 | #7EC8A0, #A8E6CF, #FFE082 | 花瓣有机形态(旋转) | 螺旋下降, noise=0.005, speed=0.3 | 50 | 6-14 |
| `outfit-season` | 季节 | 随季节变化 | 圆→星→叶→雪花变形 | 水平漂移+振荡, noise=0.006, speed=0.5 | 55 | 5-12 |
| `outfit-weather` | 天气 | #87CEEB, #708090, #FFD700 | 水滴形(速度拉伸) | 对角下降+风, noise=0.01, speed=0.8 | 70 | 3-8 |
| `outfit-daily` | 日常 | #FFA07A, #FFD700, #9370DB, #4169E1 | 散景圆(不同透明度) | 缓慢上升, noise=0.004, speed=0.25 | 40 | 10-25 |

### 3.3 种子变体机制

每张卡片的 emoji Unicode 值作为种子，在同主题基础配置上产生自然变体：

- 噪声偏移：±20%
- 速度微调：±15%
- 粒子大小偏移：±10%
- 色板微调：在基础色上叠加 ±15 的 RGB 偏移

### 3.4 Canvas 嵌入方式

- Canvas 以 `position: absolute` 覆盖弹窗内部，`z-index: 1`
- 弹窗内容层 `z-index: 2`，确保文字可读
- Canvas 背景半透明（`background(26, 26, 26, 40)`），形成自然拖尾
- 弹窗关闭时移除 canvas 并调用 `p5.remove()`

### 3.5 生命周期管理

```
打开弹窗 → 创建p5实例 → 启动粒子动画 → 弹窗运行中
                                                    ↓
关闭弹窗 ← 移除canvas ← p5.remove() ← 停止动画
```

## 4. 实现要点

### 4.1 需要修改的部分

1. **CSS**：移除 `.scene-particle` 和 `@keyframes sceneParticleFloat`，新增 `.scene-p5-canvas` 样式
2. **HTML**：在 `scene-modal` 内添加 canvas 容器 `<div id="scene-p5-container">`
3. **JavaScript**：
   - 移除 `createParticles()` 函数和 `PARTICLE_CONFIG`
   - 新增 p5.js CDN 引用
   - 新增 `THEME_CONFIGS` 配置对象（6种主题）
   - 新增 `SceneParticleSketch` 类（p5.js 实例模式）
   - 修改 `openSceneModal()` 和 `closeSceneModal()` 函数

### 4.2 性能控制

- 粒子总数上限：80个/弹窗
- 流场分辨率：`scl = 15`
- 拖尾通过半透明背景覆盖实现
- 弹窗关闭时彻底销毁 p5 实例

## 5. 原型预览

设计原型文件：`【刻记+】APP/particle-design-prototype.html`
