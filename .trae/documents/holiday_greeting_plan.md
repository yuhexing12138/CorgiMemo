# 节日问候系统实现计划

## 一、代码库现状分析

通过代码审查，发现以下组件**已存在**：

| 组件                     | 文件路径                                                                                                                                                           | 状态           |
| ---------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------ |
| GreetingManager        | [MoodManager.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/MoodManager.kt#L257-L348)                    | ✓ 已实现基础问候语系统 |
| OutfitManager          | [OutfitManager.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/OutfitManager.kt)                          | ✓ 已实现装扮系统    |
| Notification 系统        | [GeofenceRepository.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/GeofenceRepository.kt#L42-L165) | ✓ 已实现通知基础功能  |
| MoodManager.isToday    | [MoodManager.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/MoodManager.kt#L77-L89)                      | ✓ 日期判断方法     |
| HomeViewModel.greeting | [HomeViewModel.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L131-L132)                | ✓ 问候语状态流     |

## 二、需求分析与设计

### 2.1 功能需求分解

| 需求项    | 说明              | 优先级 |
| ------ | --------------- | --- |
| 节日数据定义 | 春节、元宵、端午、中秋、冬至等 | 高   |
| 节日判断逻辑 | 根据当前日期自动判断节日    | 高   |
| 节日问候语  | 节日当天显示特殊问候语     | 高   |
| 柯基节日装扮 | 节日自动换装          | 中   |
| 节日提醒通知 | 节日当天推送提醒        | 中   |

### 2.2 节日列表设计

| 节日名称 | 公历日期   | 农历日期 | 表情   | 装扮   | 问候语示例               |
| ---- | ------ | ---- | ---- | ---- | ------------------- |
| 元旦   | 1月1日   | -    | 🎆   | 派对帽  | "新年快乐！柯基陪你开启新篇章 🎉" |
| 春节   | -      | 正月初一 | 🧧   | 红色围巾 | "恭喜发财！柯基给你拜年啦 🧧"   |
| 元宵节  | -      | 正月十五 | 🏮   | 灯笼发饰 | "元宵节快乐！吃汤圆，团团圆圆 🏮" |
| 清明节  | 4月5日左右 | -    | 🌸   | -    | "清明时节，踏青赏花 🌸"      |
| 劳动节  | 5月1日   | -    | 💪   | 工作帽  | "五一快乐！劳动最光荣 💪"     |
| 端午节  | -      | 五月初五 | 🐉   | 龙舟帽  | "端午节快乐！吃粽子，赛龙舟 🐉"  |
| 中秋节  | -      | 八月十五 | 🥮   | 月亮装饰 | "中秋快乐！月圆人团圆 🥮"     |
| 国庆节  | 10月1日  | -    | 🇨🇳 | 国旗装饰 | "国庆快乐！举国同庆 🇨🇳"    |
| 冬至   | -      | 冬至日  | ❄️   | 围巾   | "冬至快乐！记得吃饺子 ❄️"     |
| 圣诞节  | 12月25日 | -    | 🎄   | 圣诞帽  | "圣诞快乐！柯基给你送礼物 🎄"   |

### 2.3 农历日期处理

由于农历节日日期每年不同，需要特殊处理：

**方案选择：**

* 方案一：使用 `java.util.Calendar` 配合农历计算（简单实现，覆盖主要节日）

* 方案二：引入第三方农历库（如 `lunar-java`）

**实现策略：**

1. 公历节日：直接判断月日
2. 农历节日：使用简化版农历算法或硬编码未来几年的日期
3. 对于 MVP 版本，先实现公历节日，农历节日可以预设日期或使用简化判断

## 三、实施步骤

### 步骤 1：创建节日数据类

新建文件 [HolidayManager.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/HolidayManager.kt)

包含：

* `Holiday` 数据类：name, date, greetingMessages, outfitId, emoji

* `HolidayDate` 数据类：month, day, isLunar

* 节日列表常量定义

### 步骤 2：实现节日判断逻辑

在 `HolidayManager` 中添加：

* `getCurrentHoliday()`: 获取当前节日（如果有）

* `isHoliday()`: 判断某天是否为某个节日

* `getSolarHoliday()`: 获取公历节日

* `getLunarHoliday()`: 获取农历节日（简化版）

### 步骤 3：扩展 GreetingManager

修改 [MoodManager.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/MoodManager.kt#L261-L348)：

* 添加 `getHolidayGreeting()` 方法

* 在 `getGreetingForUserType()` 中优先返回节日问候语

* 添加节日问候语库

### 步骤 4：扩展 OutfitManager

修改 [OutfitManager.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/OutfitManager.kt)：

* 新增节日装扮 ID 常量

* 新增节日装扮列表

* 添加 `getHolidayOutfit()` 方法

### 步骤 5：修改 HomeViewModel

修改 [HomeViewModel.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt)：

* 新增 `currentHoliday` 状态流

* 在 `loadData()` 中检测当前节日

* 更新 `updateGreeting()` 方法支持节日问候

* 节日期间自动切换节日装扮

* 添加节日通知触发逻辑

### 步骤 6：创建节日通知系统

新建或修改文件实现：

* 节日通知渠道

* 节日通知触发方法

* 在 App 启动时检查并发送节日通知

### 步骤 7：代码审查和优化

* 添加必要的中文注释

* 确保函数级注释完整

* 验证边界条件

## 四、涉及文件清单

| 文件名                                                                                                                                                   | 操作类型 | 说明          |
| ----------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----------- |
| [HolidayManager.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/HolidayManager.kt)               | 新建   | 节日数据和判断逻辑   |
| [MoodManager.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/MoodManager.kt)                     | 修改   | 扩展节日问候语     |
| [OutfitManager.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/animation/OutfitManager.kt)                 | 修改   | 新增节日装扮      |
| [HomeViewModel.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt)                 | 修改   | 集成节日逻辑      |
| [GeofenceRepository.kt](file:///C:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/GeofenceRepository.kt) | 参考   | 通知系统参考（可复用） |

## 五、详细技术实现方案

### 5.1 Holiday 数据类设计

```kotlin
/**
 * 节日数据类
 *
 * @property id 节日 ID
 * @property name 节日名称
 * @property date 节日日期
 * @property greetingMessages 问候语列表
 * @property outfitId 节日装扮 ID（可选）
 * @property emoji 节日表情
 */
data class Holiday(
    val id: String,
    val name: String,
    val date: HolidayDate,
    val greetingMessages: List<String>,
    val outfitId: String? = null,
    val emoji: String
)

/**
 * 节日日期类
 *
 * @property month 月份（1-12）
 * @property day 日期（1-31）
 * @property isLunar 是否为农历
 */
data class HolidayDate(
    val month: Int,
    val day: Int,
    val isLunar: Boolean = false
)
```

### 5.2 节日判断流程

```
启动 App / 加载数据:
    ↓
获取当前日期时间
    ↓
检查公历节日 → 有？→ 返回节日
    ↓
检查农历节日 → 有？→ 返回节日
    ↓
返回 null（非节日）
```

### 5.3 农历节日简化方案

由于农历计算复杂，MVP 版本采用：

1. **硬编码未来 3 年的农历节日日期**
2. 或者只实现公历节日（元旦、劳动节、国庆节、圣诞节等）
3. 农历节日后续迭代完善

建议：先实现公历节日，农历节日作为二期功能。

## 六、节日装扮设计

### 6.1 新增节日装扮 ID

```kotlin
object HolidayOutfitId {
    const val NEW_YEAR_HAT = "holiday_new_year_hat"      // 派对帽（元旦/新年）
    const val RED_SCARF = "holiday_red_scarf"             // 红色围巾（春节）
    const val LANTERN = "holiday_lantern"                 // 灯笼（元宵节）
    const val DRAGON_HAT = "holiday_dragon_hat"           // 龙舟帽（端午节）
    const val MOON_DECOR = "holiday_moon"                 // 月亮装饰（中秋节）
    const val SCARF = "holiday_scarf"                     // 围巾（冬至）
    const val CHRISTMAS_HAT = "holiday_christmas_hat"     // 圣诞帽（圣诞节）
    const val FLAG = "holiday_flag"                       // 国旗（国庆节）
}
```

### 6.2 装扮显示方式

目前装扮使用 emoji 显示（如 "🎓" 学士帽，"👑" 皇冠），节日装扮也使用相同方式：

| 装扮   | Emoji | 显示位置 |
| ---- | ----- | ---- |
| 派对帽  | 🎉    | 柯基头顶 |
| 红色围巾 | 🧣    | 柯基颈部 |
| 灯笼   | 🏮    | 柯基旁边 |
| 龙舟帽  | 🎣    | 柯基头顶 |
| 月亮装饰 | 🌙    | 柯基旁边 |
| 围巾   | 🧣    | 柯基颈部 |
| 圣诞帽  | 🎅    | 柯基头顶 |
| 国旗   | 🇨🇳  | 柯基旁边 |

## 七、风险和注意事项

1. **农历计算复杂度**：农历节日日期每年不同，需要准确计算
2. **装扮系统兼容性**：节日装扮需要与现有装扮系统集成
3. **通知权限**：Android 13+ 需要 `POST_NOTIFICATIONS` 权限
4. **节日切换时机**：需要在日期变更时自动切换（如 App 前台时）
5. **用户装扮偏好**：用户可能不希望自动切换装扮，需要考虑开关

## 八、验证方法

1. 修改系统日期为元旦（1月1日），验证元旦问候语和装扮
2. 修改系统日期为春节附近日期，验证春节功能（如果已实现）
3. 修改系统日期为圣诞节（12月25日），验证圣诞功能
4. 在设置中关闭自动装扮，验证是否不切换
5. 验证非节日日期的正常功能不受影响

