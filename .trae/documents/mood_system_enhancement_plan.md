# 柯基情绪系统增强实现计划

## 一、代码库调研结论

### 1.1 当前已实现的内容

**已存在的文件：**

| 文件路径 | 状态 | 功能说明 |
|---------|------|---------|
| `animation/CorgiMood.kt` | ✅ 已存在 | 定义 7 种情绪枚举（HAPPY, NORMAL, EXPECTING, WORRIED, SLEEPY, EXCITED, SAD） |
| `animation/MoodManager.kt` | ⚠️ 部分实现 | 情绪管理器，有基础计算逻辑 |
| `viewmodel/HomeViewModel.kt` | ⚠️ 部分实现 | 已有 `currentMood` 状态和情绪值存储 |
| `data/model/CorgiData.kt` | ✅ 已存在 | 包含 `moodValue`、`lastActiveDate`、`consecutiveDays` 字段 |
| `ui/screens/home/HomeScreen.kt` | ✅ 已存在 | 已传入 `currentMood` 到 `InteractiveCorgi` |
| `animation/InteractiveCorgi.kt` | ✅ 已存在 | 已使用 `GreetingManager.getGreeting()` |

### 1.2 当前实现与用户需求的差异

| 需求点 | 当前状态 | 差距 |
|--------|---------|------|
| 7 种情绪枚举 | ✅ 已定义 | 无差距 |
| **情绪值计算规则** | ❌ 简化版 | **需要重写为完整公式** |
| 不同情绪对应表情 | ⚠️ 部分实现 | 已有动画映射，需要确认完整性 |
| 不同情绪对应问候语 | ✅ 已实现 | 无差距 |
| **情绪自动恢复机制** | ❌ 未实现 | **需要添加** |
| UI 层显示情绪内容 | ⚠️ 部分实现 | 可增加情绪指示器 |

### 1.3 用户指定的情绪值计算公式

```
情绪值 = 50 + 今日完成率 × 30 + 连续活跃天数 × 5 + 超期任务数 × (-10)
```

| 计算因子 | 说明 |
|---------|------|
| 基础值 | 50（固定） |
| 今日完成率 × 30 | 今日已完成 / 今日总任务数，范围 [0, 1] × 30 → [0, 30] |
| 连续活跃天数 × 5 | 连续打卡天数，范围 [0, ∞) × 5 → [0, ∞) |
| 超期任务数 × (-10) | 超期未完成任务数，范围 [0, ∞) × (-10) → (-∞, 0] |

**情绪值范围**: 0-100（超出部分自动 clamp）

---

## 二、需求分析

### 2.1 用户明确的需求

1. **情绪枚举类**
   - 7 种：开心、普通、期待、担心、困倦、兴奋、失落 ✅ 已定义

2. **情绪值计算规则**
   - 公式：`50 + 今日完成率×30 + 连续活跃天数×5 + 超期任务数×(-10)`
   - 这是**主要改动点**

3. **情绪对应的表情映射**
   - 已有 `AnimationResourceManager.getExpressionForMood()`
   - 需要确认完整性

4. **ViewModel 中的情绪管理**
   - 已有 `currentMood` 状态
   - 需要增强计算逻辑

5. **UI 层根据情绪显示不同内容**
   - 已有问候语
   - 可增加：情绪指示器、情绪表情图标

6. **情绪自动恢复机制**
   - 每天自动恢复到基础值 50
   - 或根据不活跃天数逐渐下降

---

## 三、实现方案

### 3.1 架构设计

```
┌─────────────────────────────────────────────────────────────────┐
│                    情绪值计算系统                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  情绪值公式重写 (MoodManager)                                   │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  calculateMoodValue(                                    │  │
│  │     todayCompletionRate: Float,                         │  │
│  │     consecutiveDays: Int,                               │  │
│  │     overdueTasksCount: Int                              │  │
│  │  ): Int                                                 │  │
│  │                                                         │  │
│  │  = 50 + (todayCompletionRate * 30) +                    │  │
│  │    (consecutiveDays * 5) + (overdueTasksCount * -10)   │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              ↓                                 │
│  数据获取 (HomeViewModel)                                       │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  getTodayCompletionRate() ← 计算今日完成率               │  │
│  │  getConsecutiveActiveDays() ← 读取连续活跃天数           │  │
│  │  getOverdueTasksCount() ← 统计超期任务                   │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              ↓                                 │
│  UI 层增强 (HomeScreen / InteractiveCorgi)                     │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  情绪指示器: 显示当前情绪和数值                          │  │
│  │  问候语: 已实现                                          │  │
│  └─────────────────────────────────────────────────────────┘  │
│                              ↓                                 │
│  情绪自动恢复 (DailyReset)                                     │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  每天首次启动时检查日期                                    │  │
│  │  如果跨天: 恢复基础值 50 + 重新计算                      │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 文件修改清单

| 文件 | 操作类型 | 修改内容 |
|------|---------|---------|
| `animation/MoodManager.kt` | 重写 | 1. 新增 `calculateMoodValue()` 完整公式<br>2. 新增辅助计算函数 |
| `viewmodel/HomeViewModel.kt` | 修改 | 1. 新增数据获取函数（今日完成率、连续天数、超期任务）<br>2. 增强 `updateMood()` 逻辑<br>3. 添加每日自动恢复 |
| `data/model/TodoItem.kt` | 检查 | 确认是否有超期判断所需字段 |
| `ui/screens/home/HomeScreen.kt` | 可选增强 | 添加情绪指示器 UI |

---

## 四、详细实现步骤

### 步骤 1: 增强 MoodManager - 完整的情绪值计算

**文件**: `animation/MoodManager.kt`

**新增/修改**:

1. **替换简化的情绪计算逻辑**
   ```kotlin
   /**
    * 根据用户指定的公式计算情绪值
    * 公式: 50 + 今日完成率×30 + 连续活跃天数×5 + 超期任务数×(-10)
    *
    * @param todayCompletionRate 今日完成率 (0.0 - 1.0)
    * @param consecutiveDays 连续活跃天数
    * @param overdueTasksCount 超期任务数
    * @return 计算后的情绪值 (0-100)
    */
   fun calculateMoodValue(
       todayCompletionRate: Float,
       consecutiveDays: Int,
       overdueTasksCount: Int
   ): Int {
       val base = 50f
       val completionBonus = todayCompletionRate * 30
       val consecutiveBonus = consecutiveDays * 5f
       val overduePenalty = overdueTasksCount * -10f

       val total = base + completionBonus + consecutiveBonus + overduePenalty
       return clampMood(total.toInt())
   }
   ```

2. **新增辅助函数**
   ```kotlin
   /**
    * 计算今日完成率
    *
    * @param completedToday 今日已完成任务数
    * @param totalToday 今日总任务数
    * @return 完成率 (0.0 - 1.0)
    */
   fun calculateCompletionRate(completedToday: Int, totalToday: Int): Float {
       if (totalToday == 0) return 0.5f  // 无任务时给中性值
       return (completedToday.toFloat() / totalToday.toFloat()).coerceIn(0f, 1f)
   }

   /**
    * 判断任务是否超期
    *
    * @param dueDate 截止日期时间戳（毫秒）
    * @param currentTime 当前时间戳
    * @return 是否超期
    */
   fun isOverdue(dueDate: Long?, currentTime: Long = System.currentTimeMillis()): Boolean {
       return dueDate != null && dueDate < currentTime
   }
   ```

### 步骤 2: 增强 HomeViewModel - 数据获取与情绪更新

**文件**: `viewmodel/HomeViewModel.kt`

**新增数据获取函数**:

| 函数 | 功能 |
|------|------|
| `getTodayCompletedCount()` | 获取今日已完成任务数 |
| `getTodayTotalCount()` | 获取今日总任务数 |
| `getOverdueTasksCount()` | 获取超期任务数 |
| `recalculateMood()` | 根据最新数据重新计算情绪值 |

**修改 `toggleTodoStatus()`**:
- 完成任务后调用 `recalculateMood()` 更新情绪值

**新增每日自动恢复机制**:
- 在 `init` 中检查日期变化
- 如果跨天，重置情绪值为基础计算结果

### 步骤 3: 检查数据模型

**文件**: `data/model/TodoItem.kt`

需要确认的字段：
- `dueDate` - 截止日期时间戳 ✅
- `status` - 完成状态 ✅
- `completedAt` - 完成时间 ✅

**检查点**:
- 截止日期判断逻辑
- 今日完成判断逻辑

### 步骤 4: 可选 - UI 层增强

**文件**: `ui/screens/home/HomeScreen.kt`

可添加情绪指示器：
```kotlin
// 情绪状态显示
Row(
    horizontalArrangement = Arrangement.Center,
    modifier = Modifier.fillMaxWidth()
) {
    Text(
        text = "${currentMood.emoji} ${currentMood.description}",
        fontSize = 12.sp,
        color = Color.Gray
    )
}
```

### 步骤 5: 编译验证和测试

1. **编译测试**: `gradle compileDebugKotlin`
2. **功能验证**:
   - 完成任务 → 情绪值上升 ✅
   - 连续多天活跃 → 情绪值有额外加成 ✅
   - 存在超期任务 → 情绪值下降 ✅
   - 跨天后情绪值重新计算 ✅

---

## 五、情绪值计算示例

### 示例 1: 新手用户，第一天使用

```
今日完成率 = 3/5 = 0.6
连续活跃天数 = 1
超期任务数 = 0

情绪值 = 50 + (0.6 × 30) + (1 × 5) + (0 × -10)
       = 50 + 18 + 5 + 0
       = 73 → 情绪: HAPPY (开心)
```

### 示例 2: 活跃用户，连续一周

```
今日完成率 = 5/5 = 1.0
连续活跃天数 = 7
超期任务数 = 0

情绪值 = 50 + (1.0 × 30) + (7 × 5) + (0 × -10)
       = 50 + 30 + 35 + 0
       = 115 → clamp 为 100 → 情绪: EXCITED (兴奋)
```

### 示例 3: 拖延症用户

```
今日完成率 = 1/4 = 0.25
连续活跃天数 = 1
超期任务数 = 3

情绪值 = 50 + (0.25 × 30) + (1 × 5) + (3 × -10)
       = 50 + 7.5 + 5 - 30
       = 32.5 → 情绪: WORRIED (担心)
```

### 示例 4: 极致拖延

```
今日完成率 = 0/2 = 0
连续活跃天数 = 0
超期任务数 = 5

情绪值 = 50 + (0 × 30) + (0 × 5) + (5 × -10)
       = 50 + 0 + 0 - 50
       = 0 → 情绪: SAD (失落)
```

---

## 六、情绪状态映射

| 情绪 | 情绪值范围 | 表情动画 | 问候语 |
|------|-----------|---------|--------|
| EXCITED (兴奋) | > 80 | WAG (摇尾巴) | 开心得蹦蹦跳跳！🎉 |
| HAPPY (开心) | 60-80 | WAG (摇尾巴) | 摇着尾巴迎接你！😊 |
| NORMAL (普通) | 40-59 | SIT (坐立) | 在等你呢 🐾 |
| WORRIED (担心) | 20-39 | WORRY (担心) | 有点担心你哦 😟 |
| SAD (失落) | < 20 | SAD (难过) | 有点低落... 🥺 |
| SLEEPY (困倦) | 时间驱动 | SLEEP (睡觉) | 有点困了 💤 |
| EXPECTING (期待) | 交互触发 | TILT (歪头) | 歪着头期待 🤔 |

---

## 七、潜在风险与应对

### 7.1 风险清单

| 风险 | 影响 | 应对措施 |
|------|------|---------|
| 今日任务判断不准确 | 情绪值计算偏差 | 明确"今日"定义：创建时间在今天 或 截止日期在今天 |
| 超期任务判断不准确 | 情绪值持续下降 | `dueDate` 为 null 的任务不算超期 |
| 连续天数计算错误 | 奖励/惩罚错误 | 使用 `lastActiveDate` 与当前日期比较 |
| 情绪值变化太快 | 用户体验差 | clamp 到 0-100，每天重置基础值 |

### 7.2 关键定义

| 概念 | 定义 |
|------|------|
| **今日** | 今天 00:00:00 - 23:59:59 |
| **今日任务** | `createdAt` 在今天 或 `dueDate` 在今天 |
| **今日已完成** | 今日任务中 `status = 1` 且 `completedAt` 在今天 |
| **超期任务** | `status = 0` 且 `dueDate < 当前时间` |
| **连续活跃天数** | 从 `lastActiveDate` 到今天的连续天数 |

---

## 八、开发计划

| 阶段 | 任务 | 预估复杂度 |
|------|------|-----------|
| 1 | 增强 MoodManager（完整情绪值计算公式） | 低 |
| 2 | 增强 HomeViewModel（数据获取 + 情绪更新） | 中 |
| 3 | 检查数据模型（可选增强） | 低 |
| 4 | 可选 UI 增强（情绪指示器） | 低 |
| 5 | 编译验证和功能测试 | 低 |

---

## 九、验收标准

- [ ] `calculateMoodValue()` 使用公式：`50 + 今日完成率×30 + 连续活跃天数×5 + 超期任务数×(-10)`
- [ ] 完成任务时情绪值正确上升
- [ ] 超期任务存在时情绪值正确下降
- [ ] 连续活跃天数有额外加成
- [ ] 情绪值 clamp 在 0-100 范围内
- [ ] 7 种情绪状态正确映射
- [ ] 不同情绪显示不同问候语
- [ ] 编译无错误
