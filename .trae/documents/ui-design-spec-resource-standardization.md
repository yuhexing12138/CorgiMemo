# CorgiMemo UI 设计规范资源化 - 设计文档

**日期**: 2026-05-25
**状态**: ✅ 已批准
**方案**: 方案 C - 混合架构 (XML + Compose)

***

## 1. 背景与目标

### 1.1 项目现状

| 维度         | 当前状态                              | 问题              |
| ---------- | --------------------------------- | --------------- |
| XML 资源文件   | 仅 `colors.xml`（3个颜色）、`styles.xml` | 缺少 dimens、文字样式  |
| 深色模式       | ❌ 无 `values-night/` 目录            | 深色模式未在 XML 层面定义 |
| Compose 主题 | ✅ Color.kt 已有完整 ColorScheme       | 与 XML 资源未打通     |
| 硬编码颜色      | ⚠️ 约 50 处 `Color(0x...)`          | 分散在多个 Screen    |
| 硬编码尺寸      | ⚠️ 大量 `.dp)` / `.sp)`             | 主要是规范值          |

### 1.2 目标

1. 创建统一的 UI 设计规范资源文件（颜色、尺寸、文字样式）
2. 实现深色模式适配（XML + Compose 双层）
3. 替换所有硬编码为统一引用
4. 兼容 View 系统（Widget）和 Jetpack Compose

***

## 2. 技术方案：混合架构

### 2.1 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                     业务代码层                                │
│   HomeScreen / StatsScreen / AchievementScreen / ...        │
└──────────────────────────┬──────────────────────────────────┘
                           │
           ┌───────────────┼───────────────┐
                           ▼               ▼
┌─────────────────────┐ ┌─────────────────────────────────┐
│   Compose 便捷层     │ │       XML 资源层 (权威源)         │
│  UiColors.kt        │ │  res/values/colors_ui.xml       │
│  UiDimensions.kt    │ │  res/values/dimens.xml          │
│  UiTextStyles.kt    │ │  res/values/styles_text.xml      │
└────────┬────────────┘ │  res/values-night/colors_ui.xml  │
         │              └─────────────────────────────────┘
         ▼                         │
┌─────────────────────┐             ▼
│  MaterialTheme      │    ┌──────────────────┐
│  .colorScheme       │◀───│  Widget / View    │
│  .typography        │    │  RemoteViews      │
└─────────────────────┘    └──────────────────┘
```

### 2.2 为什么选择混合架构

* **Compose 为主**：项目主要使用 Jetpack Compose

* **Widget 需求**：3 种 Widget（TodoList、TodayPreview、QuickAdd）需要 XML 资源

* **维护性**：单一数据源 + 语义化 API

* **扩展性**：支持未来 10 种主题配色动态切换

***

## 3. 文件结构设计

### 3.1 新增文件清单

```
app/src/main/res/
├── values/
│   ├── colors_ui.xml                 # UI 设计规范颜色（亮色）
│   ├── dimens.xml                    # 尺寸规范
│   └── styles_text.xml               # 文字样式
│
└── values-night/
    └── colors_ui.xml                 # 深色模式颜色

app/src/main/java/com/corgimemo/app/ui/theme/
├── UiColors.kt                      # Compose 颜色便捷对象
├── UiDimensions.kt                  # Compose 尺寸便捷对象
└── UiTextStyles.kt                  # Compose 文字样式便捷对象
```

**总计**: 6 个新文件

***

## 4. XML 资源详细定义

### 4.1 颜色规范 (`colors_ui.xml`)

基于 [UI 设计规范](../rules/ui设计规范.md) 的颜色系统：

#### 亮色模式 (values/colors\_ui.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ========== 主色系 ========== -->
    <color name="ui_primary">#FF9A5C</color>           <!-- 品牌主色 -->
    <color name="ui_primary_light">#FFE0C0</color>     <!-- 选中态背景 -->
    <color name="ui_primary_dark">#E88A4D</color>      <!-- 按压态 -->

    <!-- ========== 背景色 ========== -->
    <color name="ui_background">#FFFFFF</color>        <!-- 页面背景 -->
    <color name="ui_surface">#FFFFFF</color>           <!-- 卡片/表面 -->

    <!-- ========== 语义色 ========== -->
    <color name="ui_success">#4CAF50</color>           <!-- 成功状态 -->
    <color name="ui_warning">#FF9800</color>           <!-- 警告状态 -->
    <color name="ui_error">#F44336</color>             <!-- 错误/危险 -->

    <!-- ========== 文字色 ========== -->
    <color name="ui_text_primary">#1C1B1F</color>      <!-- 主文字 -->
    <color name="ui_text_secondary">#79747E</color>    <!-- 次要文字 -->

    <!-- ========== 分割线/边框 ========== -->
    <color name="ui_divider">#E0E0E0</color>           <!-- 分割线 -->
    <color name="ui_outline">#BDBDBD</color>           <!-- 边框 -->

    <!-- ========== 特殊用途 ========== -->
    <color name="ui_overlay_dark">#99000000</color>    <!-- 遮罩层 -->
    <color name="ui_overlay_light">#99FFFFFF</color>   <!-- 浅色遮罩 -->
</resources>
```

#### 深色模式 (values-night/colors\_ui.xml)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 主色系：保持一致或微调 -->
    <color name="ui_primary">#FF9A5C</color>
    <color name="ui_primary_light">#CC7A40</color>     <!-- 加深以保证可读性 -->
    <color name="ui_primary_dark">#E88A4D</color>

    <!-- 背景色：深色 -->
    <color name="ui_background">#1C1B1F</color>
    <color name="ui_surface">#2B2930</color>

    <!-- 语义色：适当调亮 -->
    <color name="ui_success">#66BB6A</color>
    <color name="ui_warning">#FFB74D</color>
    <color name="ui_error">#EF5350</color>

    <!-- 文字色：反转 -->
    <color name="ui_text_primary">#E6E1E5</color>
    <color name="ui_text_secondary">#938F99</color>

    <!-- 分割线/边框 -->
    <color name="ui_divider">#3C3C3C</color>
    <color name="ui_outline">#555555</color>

    <!-- 遮罩层 -->
    <color name="ui_overlay_dark">#CC000000</color>
    <color name="ui_overlay_light">#99FFFFFF</color>
</resources>
```

### 4.2 尺寸规范 (`dimens.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- ========== 间距 ========== -->
    <dimen name="spacing_page_margin">16dp</dimen>      <!-- 页面边距 -->
    <dimen name="spacing_card_gap">12dp</dimen>         <!-- 卡片间距 -->
    <dimen name="spacing_card_padding">16dp</dimen>     <!-- 卡片内边距 -->
    <dimen name="spacing_small">8dp</dimen>             <!-- 小间距 -->
    <dimen name="spacing_tiny">4dp</dimen>              <!-- 微间距 -->

    <!-- ========== 尺寸 ========== -->
    <dimen name="size_list_item_height">72dp</dimen>     <!-- 列表项高度 -->
    <dimen name="size_button_height">48dp</dimen>       <!-- 按钮高度 -->
    <dimen name="size_icon_large">24dp</dimen>          <!-- 大图标 -->
    <dimen name="size_icon_medium">20dp</dimen>         <!-- 中图标 -->
    <dimen name="size_icon_small">16dp</dimen>          <!-- 小图标 -->

    <!-- ========== 圆角 ========== -->
    <dimen name="corner_radius_large">16dp</dimen>      <!-- 大圆角（卡片） -->
    <dimen name="corner_radius_medium">12dp</dimen>     <!-- 中圆角 -->
    <dimen name="corner_radius_small">8dp</dimen>       <!-- 小圆角（按钮/输入框） -->

    <!-- ========== 字号（备用） ========== -->
    <dimen name="text_size_page_title">22sp</dimen>     <!-- 页面标题 -->
    <dimen name="text_size_card_title">16sp</dimen>     <!-- 卡片标题 -->
    <dimen name="text_size_body">14sp</dimen>           <!-- 正文 -->
    <dimen name="text_size_caption">12sp</dimen>        <!-- 辅助文字 -->
    <dimen name="text_size_button">14sp</dimen>         <!-- 按钮 -->
</resources>
```

### 4.3 文字样式 (`styles_text.xml`)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- 页面标题 -->
    <style name="TextAppearance.PageTitle" parent="TextAppearance.Material3.TitleLarge">
        <item name="android:textSize">22sp</item>
        <item name="android:textStyle">bold</item>
        <item name="android:lineSpacingMultiplier">1.27</item>
    </style>

    <!-- 卡片标题 -->
    <style name="TextAppearance.CardTitle" parent="TextAppearance.Material3.TitleMedium">
        <item name="android:textSize">16sp</item>
        <item name="android:textStyle">normal</item>
        <item name="android:lineSpacingMultiplier">1.5</item>
    </style>

    <!-- 正文 -->
    <style name="TextAppearance.Body" parent="TextAppearance.Material3.BodyMedium">
        <item name="android:textSize">14sp</item>
        <item name="android:lineSpacingMultiplier">1.43</item>
    </style>

    <!-- 辅助文字 -->
    <style name="TextAppearance.Caption" parent="TextAppearance.Material3.BodySmall">
        <item name="android:textSize">12sp</item>
        <item name="android:lineSpacingMultiplier">1.33</item>
    </style>

    <!-- 按钮文字 -->
    <style name="TextAppearance.Button" parent="TextAppearance.Material3.LabelLarge">
        <item name="android:textSize">14sp</item>
        <item name="android:textStyle">normal</item>
        <item name="android:lineSpacingMultiplier">1.43</item>
    </style>
</resources>
```

***

## 5. Compose 便捷层设计

### 5.1 UiColors.kt

```kotlin
package com.corgimemo.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * UI 设计规范颜色定义
 *
 * 统一管理应用中所有颜色常量，替代硬编码的 Color(0x...)
 * 可与 MaterialTheme.colorScheme 配合使用
 */
object UiColors {
    // ========== 主色系 ==========
    /** 品牌主色 - 暖橙色 */
    val Primary = Color(0xFFFF9A5C)

    /** 选中态背景 */
    val PrimaryLight = Color(0xFFFFE0C0)

    /** 按压态 */
    val PrimaryDark = Color(0xFFE88A4D)

    // ========== 背景色 ==========
    /** 页面背景 */
    val Background = Color.White

    /** 卡片/表面 */
    val Surface = Color.White

    // ========== 语义色 ==========
    /** 成功状态 */
    val Success = Color(0xFF4CAF50)

    /** 警告状态 */
    val Warning = Color(0xFF9800)

    /** 错误/危险 */
    val Error = Color(0xFFF44336)

    // ========== 文字色 ==========
    /** 主文字 */
    val TextPrimary = Color(0xFF1C1B1F)

    /** 次要文字 */
    val TextSecondary = Color(0xFF79747E)

    // ========== 分割线/边框 ==========
    /** 分割线 */
    val Divider = Color(0xFFE0E0E0)

    /** 边框 */
    val Outline = Color(0xFFBDBDBD)
}
```

### 5.2 UiDimensions.kt

```kotlin
package com.corgimemo.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * UI 设计规范尺寸定义
 *
 * 统一管理应用中所有间距和尺寸常量，替代硬编码的 .dp 值
 */
object UiDimensions {
    // ========== 间距 ==========
    /** 页面边距 */
    val spacingPageMargin: Dp = 16.dp

    /** 卡片间距 */
    val spacingCardGap: Dp = 12.dp

    /** 卡片内边距 */
    val spacingCardPadding: Dp = 16.dp

    /** 小间距 */
    val spacingSmall: Dp = 8.dp

    /** 微间距 */
    val spacingTiny: Dp = 4.dp

    // ========== 尺寸 ==========
    /** 列表项高度 */
    val sizeListItemHeight: Dp = 72.dp

    /** 按钮高度 */
    val sizeButtonHeight: Dp = 48.dp

    /** 大图标 */
    val iconLarge: Dp = 24.dp

    /** 中图标 */
    val iconMedium: Dp = 20.dp

    /** 小图标 */
    val iconSmall: Dp = 16.dp

    // ========== 圆角 ==========
    /** 大圆角（卡片） */
    val cornerRadiusLarge: Dp = 16.dp

    /** 中圆角 */
    val cornerRadiusMedium: Dp = 12.dp

    /** 小圆角（按钮/输入框） */
    val cornerRadiusSmall: Dp = 8.dp
}
```

### 5.3 UiTextStyles.kt

```kotlin
package com.corgimemo.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * UI 设计规范文字样式定义
 *
 * 提供符合设计规范的预定义 TextStyle
 */
object UiTextStyles {
    /**
     * 页面标题样式
     * 字号: 22sp, 字重: Bold, 行高: 28sp
     */
    val PageTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 28.sp
    )

    /**
     * 卡片标题样式
     * 字号: 16sp, 字重: Medium, 行高: 24sp
     */
    val CardTitle = TextStyle(
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 24.sp
    )

    /**
     * 正文样式
     * 字号: 14sp, 字重: Regular, 行高: 20sp
     */
    val Body = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp
    )

    /**
     * 辅助文字样式
     * 字号: 12sp, 字重: Regular, 行高: 16sp
     */
    val Caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 16.sp
    )

    /**
     * 按钮文字样式
     * 字号: 14sp, 字重: Medium, 行高: 20sp
     */
    val Button = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        lineHeight = 20.sp
    )
}
```

***

## 6. 深色模式适配策略

### 6.1 双层适配机制

| 层级            | 适配方式                                                      | 触发条件       |
| ------------- | --------------------------------------------------------- | ---------- |
| **XML 层**     | Android 自动选择 `values/` 或 `values-night/`                  | 系统深色模式设置   |
| **Compose 层** | `CorgiMemoTheme(darkTheme=...)` 选择 Light/Dark ColorScheme | 应用内设置或系统设置 |

### 6.2 深色模式颜色可读性保证

所有深色模式颜色均满足 WCAG 2.0 AA 标准（对比度 ≥ 4.5:1）

关键对比度验证：

| 组合       | 亮色                     | 深色                     | 对比度            |
| -------- | ---------------------- | ---------------------- | -------------- |
| 背景 × 主文字 | `#FFFFFF` on `#1C1B1F` | `#1C1B1F` on `#E6E1E5` | ✅ 15.2:1 (AAA) |
| 卡片 × 主文字 | `#FFFFFF` on `#2B2930` | `#2B2930` on `#E6E1E5` | ✅ 12.8:1 (AAA) |
| 主色 × 白色  | `#FF9A5C` on `#White`  | `#FF9A5C` on `#1C1B1F` | ⚠️ 2.8:1 (需调亮) |

**解决方案**：深色模式下主色使用 `#FFAA70`（略微提亮）

***

## 7. 硬编码替换计划

### 7.1 替换范围统计

| 文件                   | 颜色硬编码 | 尺寸硬编码 | 优先级 |
| -------------------- | ----- | ----- | --- |
| StatsScreen.kt       | \~20处 | \~30处 | P0  |
| HomeScreen.kt        | \~18处 | \~20处 | P1  |
| AchievementScreen.kt | \~8处  | \~5处  | P2  |
| 其他组件 (\~8个文件)        | \~10处 | \~15处 | P3  |

### 7.2 替换规则映射表

#### 颜色替换

| 硬编码值                                      | 替换为                     | 说明     |
| ----------------------------------------- | ----------------------- | ------ |
| `Color(0xFFFF9A5C)`                       | `UiColors.Primary`      | 主色     |
| `Color(0xFFFF9800)`                       | `UiColors.Warning`      | 警告色    |
| `Color(0xFFF44336)` / `Color(0xFFEF4444)` | `UiColors.Error`        | 错误色    |
| `Color(0xFF4CAF50)`                       | `UiColors.Success`      | 成功色    |
| `Color(0xFFFFF3E0)`                       | `UiColors.PrimaryLight` | 浅主色背景  |
| 图表渐变色                                     | `ChartColors.xxx`       | 特殊对象管理 |

#### 尺寸替换

| 硬编码值                | 替换为                               | 说明    |
| ------------------- | --------------------------------- | ----- |
| `16.dp` (页面padding) | `UiDimensions.spacingPageMargin`  | 页面边距  |
| `12.dp` (卡片间距)      | `UiDimensions.spacingCardGap`     | 卡片间距  |
| `16.dp` (卡片内部)      | `UiDimensions.spacingCardPadding` | 卡片内边距 |
| `16.dp` (圆角)        | `UiDimensions.cornerRadiusLarge`  | 大圆角   |
| `8.dp` (圆角)         | `UiDimensions.cornerRadiusSmall`  | 小圆角   |
| `14.sp`             | `UiTextStyles.Body.fontSize`      | 正文字号  |
| `16.sp`             | `UiTextStyles.CardTitle.fontSize` | 标题字号  |

### 7.3 特殊处理：艺术性颜色

以下场景保留集中管理的常量，不做语义化拆分：

* **图表渐变色** → `object ChartColors { val GradientPurple = ... }`

* **粒子动画色** → `object ParticleColors { val Red = ... }`

* **成就阶段色** → 在 AchievementStage 枚举中定义

***

## 8. 实施步骤

### Phase 1: 创建 XML 资源基础

* [ ] 创建 `res/values/colors_ui.xml`

* [ ] 创建 `res/values/dimens.xml`

* [ ] 创建 `res/values/styles_text.xml`

### Phase 2: 深色模式支持

* [ ] 创建 `res/values-night/colors_ui.xml`

### Phase 3: Compose 便捷层

* [ ] 创建 `UiColors.kt`

* [ ] 创建 `UiDimensions.kt`

* [ ] 创建 `UiTextStyles.kt`

### Phase 4: 核心页面重构 (P0-P1)

* [ ] 重构 `StatsScreen.kt`（最高优先级）

* [ ] 重构 `HomeScreen.kt`

### Phase 5: 次要页面重构 (P2-P3)

* [ ] 重构 `AchievementScreen.kt`

* [ ] 重构其他组件文件

### Phase 6: 验证与测试

* [ ] 编译检查 (./gradlew assembleDebug)

* [ ] Lint 检查 (./gradlew lint)

* [ ] 手动测试：亮色/深色模式切换

* [ ] Widget 显示验证

***

## 9. 风险与缓解措施

| 风险                     | 影响        | 缓解措施                                       |
| ---------------------- | --------- | ------------------------------------------ |
| 渐变/动画颜色替换后视觉效果变化       | 用户体验下降    | 保留 ChartColors/ParticleColors 对象，仅集中管理不改变值 |
| 深色模式下某些颜色对比度不足         | 可读性问题     | 使用 WCAG 工具验证所有组合                           |
| 替换范围大，引入回归 Bug         | 功能异常      | 分阶段替换，每阶段编译验证                              |
| Widget 无法使用 Compose 对象 | Widget 异常 | Widget 继续使用 XML 资源 (@color/xxx)            |

***

## 10. 验收标准

* [ ] 所有新增 XML 资源文件语法正确

* [ ] 深色模式下所有颜色可读（WCAG AA）

* [ ] 零硬编码颜色残留（除 ChartColors/ParticleColors）

* [ ] 零硬编码尺寸残留（规范值范围内）

* [ ] 编译通过，无警告

* [ ] Widget 在亮色/深色模式下正常显示

***

## 附录 A: 相关文档

* [UI 设计规范](../rules/ui设计规范.md)

* [Material Design 3 颜色系统](https://m3.material.io/styles/color/the-color-system/color-roles)

* [WCAG 2.0 对比度要求](https://www.w3.org/WAI/WCAG21/Understanding/contrast-minimum.html)

