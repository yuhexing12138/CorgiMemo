# 柯基成长系统与换装系统实现计划

## 一、现状分析

### 1.1 已存在的数据模型

`CorgiData.kt` 已包含以下字段：
- `level: Int = 1` - 等级
- `experience: Int = 0` - 经验值
- `currentOutfit: String? = null` - 当前装扮
- `unlockedOutfits: String = "[]"` - 已解锁装扮（JSON 字符串）
- `totalCompleted: Int = 0` - 累计完成任务数
- `consecutiveDays: Int = 0` - 连续活跃天数

`TodoItem.kt` 已包含：
- `categoryId: Long` - 分类 ID
- `completedAt: Long?` - 完成时间

### 1.2 已存在的 UI 页面

`ProfileScreen.kt` - 个人中心页面，已显示：
- 柯基头像、名字
- 等级、经验、情绪统计
- 成就统计（累计完成、连续天数、最后活跃）
- 装扮显示

### 1.3 缺失内容

| 缺失项 | 说明 |
|--------|------|
| 任务分类数据模型 | 需要 Category 实体以区分学习/工作/其他 |
| 等级系统管理器 | 经验值计算、升级检测、等级阶段 |
| 经验值升级规则 | 每级需要多少经验 |
| 成就系统 | 5 种成就条件检测和解锁 |
| 装扮管理 | 装扮定义、解锁状态、装扮选择 UI |
| 首页等级显示 | 经验值进度条、升级动画 |
| 分类统计查询 | 按分类统计完成任务数 |

---

## 二、实现模块划分

### 模块 1: 数据层增强

#### 1.1 新增分类数据模型
- 创建 `Category.kt` Entity：`id, name, icon, type(学习/工作/其他/自定义)`
- 创建 `CategoryDao.kt`：分类 CRUD
- 更新 `TodoDao.kt`：新增按分类统计完成数的查询

#### 1.2 新增成就数据模型
- 创建 `Achievement.kt`：成就定义（成就 ID、名称、描述、解锁条件、对应装扮）
- 成就列表使用常量定义，无需存储在数据库

#### 1.3 扩展 CorgiData（如需要）
- 已有的 `unlockedOutfits` 字段存储 JSON 字符串表示已解锁装扮列表
- 已有 `totalCompleted` 字段
- 需要新增：`unlockedAchievements: String = "[]"` 用于存储已解锁成就

### 模块 2: 业务逻辑层（Manager）

#### 2.1 LevelManager（等级管理器）
```
功能：
- 定义每级所需经验值（可使用递增公式）
- calculateExpForLevel(level) - 计算升至某级需要的总经验
- getCurrentLevelProgress(exp) - 获取当前等级和进度百分比
- checkLevelUp(currentLevel, currentExp, addedExp) - 检测是否升级
```

#### 2.2 AchievementManager（成就管理器）
```
功能：
- 定义 5 种成就及其条件：
  1. SCHOLAR_HAT: 完成100个学习类任务
  2. TIE: 完成100个工作类任务
  3. CROWN: 连续30天完成任务
  4. CAPE: 获得所有成就
  5. ANGEL_WINGS: 累计完成500个任务
- checkAchievements(...) - 检测所有成就条件
- getAchievementInfo(id) - 获取成就详情
```

#### 2.3 OutfitManager（装扮管理器）
```
功能：
- 定义所有装扮（默认、学士帽、领带、皇冠、披风、天使翅膀）
- 装扮与成就的映射关系
- getOutfitForAchievement(achievementId) - 获取成就解锁的装扮
- shouldUnlockOutfit(...) - 检测是否解锁新装扮
```

### 模块 3: ViewModel 层

#### 3.1 HomeViewModel 增强
- 完成任务时调用 `LevelManager` 增加经验值
- 检测升级并触发升级动画
- 检测成就解锁
- 更新解锁装扮列表

#### 3.2 ProfileViewModel 增强
- 提供成就列表和解锁状态
- 提供装扮列表和解锁状态
- 支持切换装扮
- 支持查看等级进度

### 模块 4: UI 层

#### 4.1 HomeScreen 增强
- 显示当前等级
- 显示经验值进度条
- 升级时显示升级动画/提示
- 根据等级阶段显示不同体型/图片的柯基

#### 4.2 ProfileScreen 增强
- 成就列表展示（已解锁/未解锁状态）
- 装扮列表展示（已解锁/未解锁状态）
- 装扮选择功能（点击切换）
- 详细的等级进度展示

---

## 三、详细实现步骤

### 阶段 1: 数据层增强

#### 步骤 1.1: 创建分类数据模型
**文件**: `app/src/main/java/com/corgimemo/app/data/model/Category.kt`
```kotlin
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: Int, // 0=学习, 1=工作, 2=其他, 3=自定义
    val icon: Int, // 图标资源 ID
    val isDefault: Boolean = false
)
```

**文件**: `app/src/main/java/com/corgimemo/app/data/local/db/CategoryDao.kt`
```kotlin
@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<Category>
    
    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?
    
    @Insert
    suspend fun insert(category: Category)
    
    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}
```

#### 步骤 1.2: 增强 TodoDao - 按分类统计
**文件**: `app/src/main/java/com/corgimemo/app/data/local/db/TodoDao.kt`
新增查询：
```kotlin
@Query("SELECT COUNT(*) FROM todo_items WHERE status = 1 AND categoryId = :categoryId")
suspend fun getCompletedCountByCategory(categoryId: Long): Int

@Query("SELECT categoryId, COUNT(*) as count FROM todo_items WHERE status = 1 GROUP BY categoryId")
suspend fun getCompletedCountsByCategory(): List<Map<String, Any>>
```

#### 步骤 1.3: 扩展 CorgiData
**文件**: `app/src/main/java/com/corgimemo/app/data/model/CorgiData.kt`
新增字段：
```kotlin
val unlockedAchievements: String = "[]"  // 已解锁成就 ID 列表（JSON）
val maxConsecutiveDays: Int = 0         // 历史最长连续天数
```

**注意**: 数据库迁移需要处理（或使用 fallbackToDestructiveMigration 用于开发阶段）

### 阶段 2: 业务逻辑层

#### 步骤 2.1: 定义常量和枚举

**文件**: `app/src/main/java/com/corgimemo/app/animation/AchievementManager.kt`
```kotlin
// 成就 ID 常量
object AchievementId {
    const val SCHOLAR_HAT = "scholar_hat"
    const val TIE = "tie"
    const val CROWN = "crown"
    const val CAPE = "cape"
    const val ANGEL_WINGS = "angel_wings"
}

// 装扮 ID 常量
object OutfitId {
    const val DEFAULT = "default"
    const val SCHOLAR_HAT = "scholar_hat"
    const val TIE = "tie"
    const val CROWN = "crown"
    const val CAPE = "cape"
    const val ANGEL_WINGS = "angel_wings"
}

// 等级阶段
enum class LevelStage(val minLevel: Int, val maxLevel: Int, val displayName: String) {
    BABY(1, 3, "柯基宝宝"),
    YOUTH(4, 6, "柯基少年"),
    ADULT(7, 9, "柯基青年"),
    MASTER(10, Int.MAX_VALUE, "柯基大师")
}
```

#### 步骤 2.2: 创建 LevelManager

**文件**: `app/src/main/java/com/corgimemo/app/animation/LevelManager.kt`

**核心函数**:

| 函数 | 功能 |
|------|------|
| `getExpRequiredForLevel(level: Int): Int` | 计算升级到该等级需要的经验值（使用递增公式） |
| `getTotalExpForLevel(level: Int): Int` | 计算从 1 级升到该等级的总经验 |
| `getCurrentLevelAndProgress(totalExp: Int): Pair<Int, Float>` | 根据总经验计算当前等级和进度百分比 |
| `getLevelStage(level: Int): LevelStage` | 获取等级阶段（宝宝/少年/青年/大师） |
| `getExpOnTaskComplete(): Int` | 完成单个任务获得的经验（固定 10） |
| `getExpOnConsecutive7Days(): Int` | 连续 7 天完成任务奖励（固定 50） |

**经验值公式建议**:
```
升级所需经验 = level * 50
例如：
- 1→2 级: 50 经验
- 2→3 级: 100 经验
- 3→4 级: 150 经验
- ...
```

#### 步骤 2.3: 创建 AchievementManager

**文件**: `app/src/main/java/com/corgimemo/app/animation/AchievementManager.kt`

**成就定义数据类**:
```kotlin
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val unlockCondition: String,
    val outfitId: String?  // 解锁的装扮 ID
)
```

**成就列表**:
```
1. SCHOLAR_HAT
   - 名称: 学霸
   - 描述: 完成100个学习类任务
   - 条件: 学习类任务完成数 >= 100
   - 解锁装扮: 学士帽

2. TIE
   - 名称: 职场精英
   - 描述: 完成100个工作类任务
   - 条件: 工作类任务完成数 >= 100
   - 解锁装扮: 领带

3. CROWN
   - 名称: 坚持不懈
   - 描述: 连续30天完成任务
   - 条件: 连续天数 >= 30
   - 解锁装扮: 皇冠

4. CAPE
   - 名称: 全成就大师
   - 描述: 获得所有成就
   - 条件: 其他4个成就全部解锁
   - 解锁装扮: 披风

5. ANGEL_WINGS
   - 名称: 勤劳天使
   - 描述: 累计完成500个任务
   - 条件: totalCompleted >= 500
   - 解锁装扮: 天使翅膀
```

**核心函数**:

| 函数 | 功能 |
|------|------|
| `getAllAchievements(): List<Achievement>` | 获取所有成就定义 |
| `checkNewAchievements(...)` | 检测并返回新解锁的成就列表 |
| `getOutfitForAchievement(achievementId: String): String?` | 获取成就对应的装扮 |

#### 步骤 2.4: 创建 OutfitManager

**文件**: `app/src/main/java/com/corgimemo/app/animation/OutfitManager.kt`

**装扮定义数据类**:
```kotlin
data class Outfit(
    val id: String,
    val name: String,
    val description: String,
    val iconRes: Int? = null,  // 图标资源
    val isDefault: Boolean = false
)
```

**核心函数**:

| 函数 | 功能 |
|------|------|
| `getAllOutfits(): List<Outfit>` | 获取所有装扮 |
| `getOutfitById(id: String): Outfit?` | 根据 ID 获取装扮 |
| `isOutfitUnlocked(...)` | 检查装扮是否已解锁 |
| `getOutfitsForLevelStage(stage: LevelStage)` | 获取等级阶段对应的形态变化（可选） |

### 阶段 3: Repository 层增强

#### 步骤 3.1: 创建 CategoryRepository

**文件**: `app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt`

**功能**:
- 获取所有分类
- 获取学习类分类 ID
- 获取工作类分类 ID

#### 步骤 3.2: 增强 TodoRepository

**文件**: `app/src/main/java/com/corgimemo/app/data/repository/TodoRepository.kt`

新增函数:
```kotlin
suspend fun getCompletedCountByCategory(categoryId: Long): Int
```

#### 步骤 3.3: 增强 CorgiRepository

**文件**: `app/src/main/java/com/corgimemo/app/data/repository/CorgiRepository.kt`

新增函数（如需要）:
```kotlin
suspend fun addExperienceAndCheckLevelUp(amount: Int): LevelUpResult?
suspend fun updateUnlockedAchievements(achievements: String)
```

### 阶段 4: ViewModel 层增强

#### 步骤 4.1: 增强 HomeViewModel

**文件**: `app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt`

**新增状态**:
```kotlin
private val _showLevelUp = MutableStateFlow<Int?>(null)  // 升级到的等级
val showLevelUp: StateFlow<Int?> = _showLevelUp.asStateFlow()

private val _showAchievementUnlock = MutableStateFlow<String?>(null)  // 解锁的成就 ID
val showAchievementUnlock: StateFlow<String?> = _showAchievementUnlock.asStateFlow()
```

**修改 `toggleTodoStatus` 函数**:
```kotlin
private fun toggleTodoStatus(todo: TodoItem) {
    viewModelScope.launch {
        // 原有逻辑...
        
        // 如果是完成任务
        if (newStatus == 1) {
            // 1. 增加经验值
            val expGain = LevelManager.getExpOnTaskComplete()
            addExperience(expGain)
            
            // 2. 检测成就
            checkAchievements()
            
            // 3. 重新计算情绪（已有逻辑）
            recalculateMood()
        }
    }
}
```

**新增 `addExperience` 函数**:
```kotlin
private fun addExperience(amount: Int) {
    val currentData = _corgiData.value ?: return
    val currentLevel = currentData.level
    val currentExp = currentData.experience
    val newTotalExp = currentExp + amount
    
    // 计算新等级
    val (newLevel, _) = LevelManager.getCurrentLevelAndProgress(newTotalExp)
    
    // 检测升级
    if (newLevel > currentLevel) {
        _showLevelUp.value = newLevel
        // 可以播放升级动画等
    }
    
    // 更新数据
    corgiRepository.updateCorgi(currentData.copy(
        experience = newTotalExp,
        level = newLevel
    ))
    _corgiData.value = currentData.copy(
        experience = newTotalExp,
        level = newLevel
    )
}
```

**新增 `checkAchievements` 函数**:
```kotlin
private suspend fun checkAchievements() {
    // 获取统计数据
    val currentData = _corgiData.value ?: return
    val learningCompleted = todoRepository.getCompletedCountByCategory(learningCategoryId)
    val workCompleted = todoRepository.getCompletedCountByCategory(workCategoryId)
    
    // 检测成就
    val newAchievements = AchievementManager.checkNewAchievements(
        learningCompleted = learningCompleted,
        workCompleted = workCompleted,
        consecutiveDays = currentData.consecutiveDays,
        totalCompleted = currentData.totalCompleted,
        unlockedAchievements = currentData.unlockedAchievements
    )
    
    // 处理新解锁的成就
    for (achievement in newAchievements) {
        _showAchievementUnlock.value = achievement.id
        
        // 解锁对应装扮
        val outfitId = AchievementManager.getOutfitForAchievement(achievement.id)
        if (outfitId != null) {
            unlockOutfit(outfitId)
        }
    }
    
    // 更新已解锁成就列表
    // ...
}
```

#### 步骤 4.2: 增强 ProfileViewModel

**文件**: `app/src/main/java/com/corgimemo/app/viewmodel/ProfileViewModel.kt`

**新增状态**:
```kotlin
private val _achievements = MutableStateFlow<List<Pair<Achievement, Boolean>>>(emptyList())
val achievements: StateFlow<List<Pair<Achievement, Boolean>>> = _achievements.asStateFlow()

private val _outfits = MutableStateFlow<List<Pair<Outfit, Boolean>>>(emptyList())
val outfits: StateFlow<List<Pair<Outfit, Boolean>>> = _outfits.asStateFlow()
```

**新增函数**:

| 函数 | 功能 |
|------|------|
| `loadAchievements()` | 加载所有成就及解锁状态 |
| `loadOutfits()` | 加载所有装扮及解锁状态 |
| `selectOutfit(outfitId: String)` | 选择装扮 |
| `unselectOutfit()` | 取消装扮（恢复默认） |

### 阶段 5: UI 层增强

#### 步骤 5.1: 首页等级和经验显示

**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt`

**新增 Composable**:
```kotlin
@Composable
fun CorgiLevelDisplay(
    level: Int,
    experience: Int,
    modifier: Modifier = Modifier
) {
    // 显示:
    // - 等级阶段名称（柯基宝宝/少年/青年/大师）
    // - 等级数字
    // - 经验值进度条
    // - 当前经验 / 下一级所需经验
}
```

**升级动画弹窗**:
```kotlin
@Composable
fun LevelUpDialog(
    level: Int,
    onDismiss: () -> Unit
) {
    // 升级庆祝动画
    // 显示"升到 Lv.XX 啦！"
}
```

**成就解锁弹窗**:
```kotlin
@Composable
fun AchievementUnlockDialog(
    achievement: Achievement,
    onDismiss: () -> Unit
) {
    // 成就解锁提示
    // 显示成就名称和解锁的装扮
}
```

#### 步骤 5.2: 个人中心增强

**文件**: `app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt`

**增强内容**:

1. **成就列表区域**
   - 显示所有 5 个成就
   - 已解锁成就显示金色/高亮
   - 未解锁成就显示灰色/锁定图标
   - 点击可查看详情

2. **装扮列表区域**
   - 显示所有装扮（默认、学士帽、领带、皇冠、披风、天使翅膀）
   - 已解锁装扮可点击选择
   - 未解锁装扮显示锁定状态
   - 当前装扮高亮显示

3. **等级进度区域**
   - 显示当前等级阶段
   - 显示详细经验值进度条
   - 显示下一阶段目标

---

## 四、关键依赖关系

```
数据层:
├── Category.kt (新增)
├── CategoryDao.kt (新增)
├── TodoDao.kt (增强 - 分类统计查询)
└── CorgiData.kt (增强 - unlockedAchievements)

Repository 层:
├── CategoryRepository.kt (新增)
├── TodoRepository.kt (增强)
└── CorgiRepository.kt (增强)

业务逻辑层:
├── LevelManager.kt (新增)
├── AchievementManager.kt (新增)
└── OutfitManager.kt (新增)

ViewModel 层:
├── HomeViewModel.kt (增强)
└── ProfileViewModel.kt (增强)

UI 层:
├── HomeScreen.kt (增强 - 等级显示、升级弹窗)
└── ProfileScreen.kt (增强 - 成就/装扮列表)
```

---

## 五、潜在风险与处理

### 风险 1: 数据库迁移
**风险**: 新增 `Category` 表和 `unlockedAchievements` 字段需要数据库迁移

**处理方案**:
- 开发阶段可使用 `fallbackToDestructiveMigration`
- 正式版本需编写 Migration 脚本

### 风险 2: 分类 ID 的确定性
**风险**: 学习/工作类任务的分类 ID 需要确定，才能统计对应成就

**处理方案**:
- 预定义默认分类（学习、工作、生活），使用固定 ID 或通过名称查询
- 在初始化时插入默认分类数据

### 风险 3: 成就检测时机
**风险**: 成就条件可能在多个场景达成（连续天数跨天检测）

**处理方案**:
- 每次完成任务时检测
- 每天首次启动时检测（连续天数相关成就）
- 累计任务数每次 +1 时检测

### 风险 4: CAPE 成就（全成就）的循环依赖
**风险**: CAPE 成就需要解锁所有成就，但它本身也是一个成就

**处理方案**:
- CAPE 成就的条件是"解锁其他 4 个成就"
- 检测 CAPE 时排除自身

---

## 六、验收清单

### 成长系统验收
- [ ] 完成任务获得经验值（+10）
- [ ] 连续 7 天完成任务获得额外经验（+50）
- [ ] 经验值达到要求时升级
- [ ] 等级进度条正确显示
- [ ] 等级阶段正确显示（宝宝/少年/青年/大师）
- [ ] 升级时弹出升级提示

### 成就系统验收
- [ ] 完成 100 个学习任务 → 解锁"学霸"成就和学士帽
- [ ] 完成 100 个工作任务 → 解锁"职场精英"成就和领带
- [ ] 连续 30 天完成任务 → 解锁"坚持不懈"成就和皇冠
- [ ] 累计完成 500 个任务 → 解锁"勤劳天使"成就和天使翅膀
- [ ] 解锁其他 4 个成就 → 解锁"全成就大师"成就和披风
- [ ] 成就解锁时弹出提示

### 换装系统验收
- [ ] 个人中心显示成就解锁的装扮
- [ ] 点击已解锁装扮可切换
- [ ] 当前装扮高亮显示
- [ ] 未解锁装扮显示锁定状态

---

## 七、实施建议

### 推荐实施顺序

**第一阶段（核心）**:
1. 创建分类数据模型和默认分类初始化
2. 创建 LevelManager 并实现经验值系统
3. 在 HomeViewModel 中集成经验值增加逻辑
4. 在 HomeScreen 中显示等级和经验进度条

**第二阶段（成就）**:
5. 创建 AchievementManager，定义 5 种成就
6. 在 HomeViewModel 中集成成就检测逻辑
7. 实现成就解锁弹窗

**第三阶段（换装）**:
8. 创建 OutfitManager
9. 增强 ProfileViewModel，支持成就/装扮查询和切换
10. 增强 ProfileScreen，显示成就/装扮列表

**第四阶段（完善）**:
11. 实现升级弹窗
12. 实现连续 7 天奖励
13. 测试和优化

### 简化方案

如项目时间紧张，可先实现：
1. 基础经验值和等级系统
2. 1-2 个简单成就（如累计完成 500 任务）
3. 换装系统的 UI 框架

其他成就后续迭代添加。
