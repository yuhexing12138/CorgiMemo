# 情绪值可视化展示实现计划

## 需求分析

### 当前状态
- 情绪系统已实现：`MoodManager` 负责情绪值计算和状态切换
- 情绪值（0-100）存储在 `CorgiData.moodValue`
- 首页已有柯基展示区域，个人中心已显示简单的"情绪: XX%"文本
- 情绪值计算逻辑：`50 + 今日完成率×30 + 连续活跃天数×5 + 超期任务数×(-10)`

### 需要实现的三个功能

| 功能 | 需求点 | 优先级 |
|------|--------|--------|
| 情绪值指示器 | 首页柯基区域角落的圆形进度条，颜色随情绪变化 | 高 |
| 情绪历史曲线 | 我的页面近7日情绪折线图，横轴日期纵轴0-100 | 高 |
| 情绪变化提示 | 变化幅度>20时显示Toast提示原因 | 中 |

---

## 代码库结构调研

### 现有相关文件

| 文件 | 职责 |
|------|------|
| `animation/MoodManager.kt` | 情绪值计算、情绪状态映射 |
| `data/model/CorgiData.kt` | 包含 `moodValue: Int = 50` 字段 |
| `ui/screens/home/HomeScreen.kt` | 首页，有 `CorgiDisplayArea` 柯基展示区域 |
| `ui/screens/profile/ProfileScreen.kt` | 个人中心，已有"情绪: XX%"文本显示 |
| `viewmodel/HomeViewModel.kt` | 已有 `recalculateMood()` 方法调用 `MoodManager.calculateMoodValue()` |

### 现有情绪状态

| 情绪值范围 | 情绪 | Emoji |
|-----------|------|-------|
| >80 | EXCITED | 🎉 |
| 60-80 | HAPPY | 😊 |
| 40-59 | NORMAL | 🐾 |
| 20-39 | WORRIED | 😟 |
| <20 | SAD | 🥺 |

---

## 实现步骤

### 阶段一：情绪值指示器（圆形进度条）

**新增文件：`ui/components/MoodIndicator.kt`**
- Composable 组件：`MoodIndicator(moodValue: Int, modifier: Modifier)`
- 使用 `Canvas` 自绘圆形进度条
- 进度条颜色渐变：绿色(100) → 黄绿色(70) → 黄色(50) → 橙红色(30) → 红色(0)
- 中心显示数字（如 "75"）
- 悬停/点击显示情绪状态标签

**修改文件：`HomeScreen.kt`**
- 在 `CorgiDisplayArea` 组件的 Box 内添加 `MoodIndicator`
- 位置：右上角（使用 `BoxScope.align(Alignment.TopEnd)`）
- 传入 `corgiData.moodValue`

**颜色映射方案：**

| 情绪值 | 颜色 | Hex |
|--------|------|-----|
| 80-100 | 绿色 | #4CAF50 |
| 60-79 | 黄绿色 | #8BC34A |
| 40-59 | 黄色 | #FFC107 |
| 20-39 | 橙红色 | #FF5722 |
| 0-19 | 红色 | #F44336 |

---

### 阶段二：情绪历史曲线（折线图）

**数据存储问题分析：**

当前 `CorgiData` 只存储 `moodValue`（当前值），没有历史记录。

**解决方案：新增情绪历史存储**

**新增文件：`data/local/dao/MoodHistoryDao.kt`**
- 表 `mood_history` 定义：
  - `id`: Long (主键)
  - `date`: String (yyyy-MM-dd)
  - `moodValue`: Int (0-100)
  - `changeReason`: String? (可选，记录变化原因)

**新增文件：`data/model/MoodHistory.kt`**
- Room Entity 实体类

**新增文件：`data/repository/MoodHistoryRepository.kt`**
- 方法：
  - `suspend fun recordMood(date: String, moodValue: Int, reason: String? = null)`
  - `suspend fun getLast7Days(): List<MoodHistory>`

**修改文件：`data/local/db/AppDatabase.kt`**
- 注册 `MoodHistoryDao`

**修改文件：`HomeViewModel.kt`**
- 每日首次打开时调用 `recordMood()` 保存当天情绪值
- 暴露状态流 `moodHistory7Days`

**新增文件：`ui/components/MoodHistoryChart.kt`**
- Composable 组件：`MoodHistoryChart(data: List<Int>, dates: List<String>)`
- 使用 `Canvas` 自绘折线图
- 横轴：近7日日期（只显示日，如 "10", "11"...）
- 纵轴：0-100，标记 0/25/50/75/100
- 折线：平滑曲线或折线
- 填充区域：曲线下方渐变填充
- 数据点：圆点高亮

**修改文件：`ProfileScreen.kt`**
- 在"柯基详情"区域添加 `MoodHistoryChart`
- 收集 `viewModel.moodHistory7Days` 状态

---

### 阶段三：情绪变化提示（Toast）

**修改文件：`MoodManager.kt`**
- 新增常量 `const val SIGNIFICANT_CHANGE_THRESHOLD = 20`
- 新增方法：
  - `fun isSignificantChange(oldMood: Int, newMood: Int): Boolean`
  - `fun getChangeMessage(oldMood: Int, newMood: Int, reasonHint: String): String`

**修改文件：`HomeViewModel.kt`**
- 在 `recalculateMood()` 方法中：
  - 计算前保存旧情绪值 `val oldMood = corgiData.moodValue`
  - 计算新情绪值后，检查变化
  - 如果变化 > 20，更新状态流 `_moodChangeMessage`

**修改文件：`HomeScreen.kt`**
- 收集 `moodChangeMessage` 状态流
- 使用 `LaunchedEffect` 触发 Toast 显示
- 使用 Compose `Toast.makeText()` 或 `SnackbarHost`

**提示消息模板：**

| 场景 | 消息 |
|------|------|
| 完成率高，情绪大幅上升 | "今天任务完成率高，柯基很开心！🎉" |
| 连续活跃，情绪上升 | "连续打卡中，柯基越来越有活力！💪" |
| 超期任务多，情绪下降 | "待办堆积，柯基有点担心... 😟" |
| 综合变化上升 | "完成了不少任务，柯基心情不错！" |
| 综合变化下降 | "好多任务还没完成，柯基有点焦虑..." |

---

## 模块与文件修改清单

| 阶段 | 文件 | 修改类型 | 说明 |
|------|------|---------|------|
| 阶段一 | `ui/components/MoodIndicator.kt` | 新增 | 圆形进度条指示器 |
| 阶段一 | `HomeScreen.kt` | 修改 | 集成 MoodIndicator 到柯基区域 |
| 阶段二 | `data/model/MoodHistory.kt` | 新增 | 情绪历史实体 |
| 阶段二 | `data/local/dao/MoodHistoryDao.kt` | 新增 | 情绪历史 DAO |
| 阶段二 | `data/repository/MoodHistoryRepository.kt` | 新增 | 情绪历史仓库 |
| 阶段二 | `data/local/db/AppDatabase.kt` | 修改 | 注册 MoodHistoryDao |
| 阶段二 | `ui/components/MoodHistoryChart.kt` | 新增 | 折线图组件 |
| 阶段二 | `HomeViewModel.kt` | 修改 | 每日记录情绪 + 状态流 |
| 阶段二 | `ProfileScreen.kt` | 修改 | 集成折线图 |
| 阶段三 | `MoodManager.kt` | 修改 | 变化检测和消息生成 |
| 阶段三 | `HomeViewModel.kt` | 修改 | 检测情绪大幅变化 |
| 阶段三 | `HomeScreen.kt` | 修改 | Toast 显示提示 |

---

## 潜在问题与解决方案

| 问题 | 影响 | 解决方案 |
|------|------|---------|
| 情绪历史数据结构不存在 | 无法展示历史曲线 | 新增 `mood_history` 表和相关层 |
| 数据库升级需要迁移 | 现有用户数据丢失风险 | 使用 Room Migration，版本号+1 |
| Toast 频繁触发干扰用户 | 用户体验差 | 添加冷却时间（如 5 分钟内不重复提示） |
| 折线图 Canvas 绘制复杂 | 开发时间长 | 使用简单折线，不添加第三方图表库 |
| 今日情绪尚未记录时没有数据 | 图表显示空 | 首次进入时自动记录今日情绪，空数据显示占位 |

---

## 实施优先级

1. **阶段一（情绪值指示器）** - 最高优先级，可视化当前状态
2. **阶段二（情绪历史曲线）** - 高优先级，展示历史趋势
3. **阶段三（情绪变化提示）** - 中优先级，增强互动反馈

---

## 风险处理

### 数据库迁移风险

Room 数据库添加新表需要版本升级和迁移：

```kotlin
// AppDatabase.kt
@Database(
    entities = [..., MoodHistory::class],
    version = 2  // 从 1 升级到 2
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun moodHistoryDao(): MoodHistoryDao
    
    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS mood_history (...)")
            }
        }
    }
}
```

### 空数据处理

- 折线图：不足7天的数据点用占位或只显示有记录的天数
- 今日未记录：进入 App 时自动记录当前情绪值

---

## 完成标准

- 首页柯基右上角显示圆形进度条，颜色随情绪变化
- 个人中心显示近7日情绪折线图
- 情绪值变化 >20 时显示 Toast 提示
- 代码编译无错误
- 数据库迁移正常（不丢失现有用户数据）
