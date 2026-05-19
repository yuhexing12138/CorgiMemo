# 柯基装扮增强功能实现计划

## 需求分析

### 当前状态

* 装扮系统已实现：OutfitManager 管理6种装扮（默认、学士帽🎓、领带👔、皇冠👑、天使翅膀🪽、披风🧥）

* 装扮保存在 CorgiData.currentOutfit 和 unlockedOutfits

* ProfileScreen 已有装扮卡片，点击直接切换并保存

* HomeScreen 柯基上方显示装扮图标

### 需要实现的三个功能

| 功能   | 需求点                              | 优先级 |
| ---- | -------------------------------- | --- |
| 装扮预览 | "我的"页面装扮列表点击预览（不保存），退出恢复原装扮      | 高   |
| 快速切换 | 首页柯基长按弹出 BottomSheet，缩略图选择，点击即切换 | 高   |
| 装扮推荐 | 节日/季节自动推荐，"我的"页面顶部提示             | 中   |

***

## 代码库结构调研

### 现有相关文件

| 文件                                    | 职责                             |
| ------------------------------------- | ------------------------------ |
| `animation/OutfitManager.kt`          | 装扮定义、解锁管理、ID映射                 |
| `animation/AchievementManager.kt`     | 包含 OutfitId 常量对象               |
| `viewmodel/ProfileViewModel.kt`       | 个人中心 ViewModel，已有 selectOutfit |
| `ui/screens/profile/ProfileScreen.kt` | 个人中心，已有装扮卡片展示                  |
| `ui/screens/home/HomeScreen.kt`       | 首页，已有柯基展示区域                    |
| `viewmodel/HomeViewModel.kt`          | 首页 ViewModel                   |

### 现有装扮逻辑

```kotlin
// OutfitId 常量（在 AchievementManager.kt 中）
object OutfitId {
    const val DEFAULT = "default"
    const val SCHOLAR_HAT = "scholar_hat"  // 🎓
    const val TIE = "tie"                  // 👔
    const val CROWN = "crown"              // 👑
    const val ANGEL_WINGS = "angel_wings"  // 🪽
    const val CAPE = "cape"                // 🧥
}
```

***

## 实现步骤

### 阶段一：装扮预览功能

**文件：ProfileViewModel.kt**

* 新增状态流：`_isPreviewMode` (StateFlow<Boolean>)

* 新增状态流：`_previewOutfit` (StateFlow\<String?>)

* 新增状态流：`_originalOutfit` (StateFlow\<String?>)

* 新增方法：

  * `enterPreviewMode()` - 进入预览模式，保存当前装扮

  * `previewOutfit(outfitId: String?)` - 预览装扮（不保存到数据库）

  * `exitPreviewMode()` - 退出预览模式，恢复原装扮

**文件：ProfileScreen.kt**

* 在装扮区域添加"预览模式"开关按钮

* 修改装扮卡片点击逻辑：

  * 预览模式：调用 `previewOutfit()`，不保存

  * 非预览模式：保持原有 `selectOutfit()` 逻辑

* InteractiveCorgi 的 outfitId 使用：`if (isPreviewMode) previewOutfit else currentOutfit`

* 添加"完成预览"和"取消预览"按钮

***

### 阶段二：快速切换入口（BottomSheet）

**文件：HomeViewModel.kt**

* 新增状态流：`_showOutfitSheet` (StateFlow<Boolean>)

* 新增方法：

  * `toggleOutfitSheet()` - 显示/隐藏 BottomSheet

  * `quickSwitchOutfit(outfitId: String?)` - 快速切换装扮（调用 repository 保存）

**文件：HomeScreen.kt**

* 在 `CorgiDisplayArea` 外层 Box 添加 `combinedClickable` 支持长按

* 新增：`OutfitQuickSwitchSheet` 组件（BottomSheet）

* BottomSheet 内容：

  * 标题："快速换装 🎨"

  * 横向滚动的装扮缩略图（与 ProfileScreen 的 OutfitCard 类似）

  * 只显示已解锁的装扮

  * 点击切换后自动关闭 BottomSheet

***

### 阶段三：装扮推荐

**新增文件：animation/SeasonalOutfitRecommender.kt**

* 数据类：`OutfitRecommendation` (outfitId, reason, icon, name)

* 对象：`SeasonalOutfitRecommender`

  * 常量定义各节日时间范围

  * 方法：`getCurrentRecommendation(currentDate: LocalDate, unlockedOutfits: String): OutfitRecommendation?`

  * 节日映射：

    * 🎄 圣诞节 (12.15-12.31) → 皇冠 👑

    * 🧧 春节 (农历，简化为1.20-2.20) → 领带 👔 或 披风 🧥

    * 🎃 万圣节 (10.25-11.5) → 披风 🧥

    * 🌸 春天 (3.1-5.31) → 天使翅膀 🪽

    * ☀️ 夏天 (6.1-8.31) → 默认

    * 🍂 秋天 (9.1-11.30) → 学士帽 🎓

    * ❄️ 冬天 (12.1-2.29) → 皇冠 👑

**文件：ProfileViewModel.kt**

* 新增状态流：`_recommendedOutfit` (StateFlow\<OutfitRecommendation?>)

* 在 `loadCorgiData()` 中调用推荐器获取当前推荐

**文件：ProfileScreen.kt**

* 在装扮区域顶部添加推荐横幅 Card

* 推荐内容：装扮图标 + "推荐：${name}" + 原因

* 点击推荐：直接应用装扮

***

## 模块与文件修改清单

| 阶段  | 文件                             | 修改类型 | 说明                               |
| --- | ------------------------------ | ---- | -------------------------------- |
| 阶段一 | `ProfileViewModel.kt`          | 修改   | 预览模式状态和逻辑                        |
| 阶段一 | `ProfileScreen.kt`             | 修改   | 预览模式开关 + 按钮 + 预览逻辑               |
| 阶段二 | `HomeViewModel.kt`             | 修改   | BottomSheet 状态 + 快速切换方法          |
| 阶段二 | `HomeScreen.kt`                | 修改   | 长按检测 + OutfitQuickSwitchSheet 组件 |
| 阶段三 | `SeasonalOutfitRecommender.kt` | 新增   | 节日/季节推荐逻辑                        |
| 阶段三 | `ProfileViewModel.kt`          | 修改   | 推荐状态流                            |
| 阶段三 | `ProfileScreen.kt`             | 修改   | 推荐横幅 UI                          |

***

## 潜在问题与解决方案

| 问题                 | 影响         | 解决方案                                            |
| ------------------ | ---------- | ----------------------------------------------- |
| 装扮预览不保存但需要 UI 实时响应 | 状态同步       | 使用独立的 previewOutfit 状态流，只修改内存状态                 |
| 长按与点击冲突            | 首页柯基现有点击交互 | 使用 `combinedClickable` 区分 onClick 和 onLongClick |
| 农历节日计算复杂           | 春节日期不固定    | 简化处理：固定 1.20-2.20 为春节期间                         |
| 推荐的装扮可能未解锁         | 无法使用       | 推荐器过滤未解锁的装扮，返回 null 则不显示推荐                      |

***

## 实现优先级

1. **阶段二（快速切换）** - 最高优先级，提升首页交互体验
2. **阶段一（装扮预览）** - 高优先级，方便用户选择
3. **阶段三（装扮推荐）** - 中优先级，增强节日氛围

***

## 完成标准

* 进入个人中心，点击"预览模式"，切换装扮不保存

* 退出个人中心时恢复原装扮

* 长按住首页柯基，弹出快速换装 BottomSheet

* 在 BottomSheet 中点击装扮，柯基立即换装并关闭

* 节日/特殊季节时，个人中心顶部显示推荐装扮

* 代码编译无错误

