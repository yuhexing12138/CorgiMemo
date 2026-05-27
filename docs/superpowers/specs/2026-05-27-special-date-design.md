# 特殊日期记录功能 — 设计文档

> 日期：2026-05-27
> 状态：待审核
> 前置依赖：灵感记录功能已完成（v17）

---

## 1. 功能概述

为 CorgiMemo 添加**特殊日期记录功能**，用户可以记录重要日期（生日、纪念日、节日等），以天数卡片形式展示倒计时/正计时，支持分类、标签、重复规则、图片、关联和提醒通知。

### 用户决策汇总

| 决策项 | 选择 |
|--------|------|
| 关联功能 | 完整实现（SpecialDateRelation 表，跨模块关联） |
| 分类系统 | 分类枚举 + 标签并存 |
| 重复规则 | 支持年度/月度重复（动态计算方案） |
| 提醒通知 | 本地推送提醒 |
| 架构模式 | 独立模块（遵循 Inspiration 模块模式） |

---

## 2. 数据层设计

### 2.1 SpecialDate 实体

**表名**: `special_dates`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK AUTOINCREMENT | 主键 |
| title | String | NOT NULL | 标题，如"小明生日" |
| targetDate | Long | NOT NULL | 目标日期时间戳（毫秒），存原始设定日期 |
| category | String | NOT NULL DEFAULT 'OTHER' | 分类: BIRTHDAY / ANNIVERSARY / HOLIDAY / OTHER |
| countMode | Int | NOT NULL DEFAULT 0 | 计时模式: 0=倒计时, 1=正计时 |
| repeatType | Int | NOT NULL DEFAULT 0 | 重复: 0=不重复, 1=按年, 2=按月 |
| reminderDays | Int | DEFAULT 0 | 提前N天提醒，0=不提醒 |
| content | Text | NOT NULL DEFAULT '' | 备注内容（多行文本） |
| tags | Text | NOT NULL DEFAULT '' | 标签 JSON: `["重要","家庭"]` |
| imagePaths | Text | NOT NULL DEFAULT '' | 本地图片路径 JSON |
| imageUrls | Text | NOT NULL DEFAULT '' | 云端图片URL JSON（预留） |
| isPinned | Int | NOT NULL DEFAULT 0 | 是否置顶 |
| createdAt | Long | NOT NULL | 创建时间戳 |
| updatedAt | Long | NOT NULL | 更新时间戳 |

**索引**: `targetDate`, `isPinned`, `category`

### 2.2 SpecialDateRelation 实体

**表名**: `special_date_relations`

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | Long | PK AUTOINCREMENT | 主键 |
| specialDateId | Long | NOT NULL FK→CASCADE | 所属日期ID |
| targetType | String | NOT NULL | 目标类型: "todo" / "date" / "inspiration" |
| targetId | Long | NOT NULL | 目标实体ID |
| createdAt | Long | NOT NULL | 创建时间戳 |

**索引**: `specialDateId`
**外键**: `specialDateId` → `special_dates(id)` ON DELETE CASCADE

### 2.3 DAO 接口

#### SpecialDateDao

```kotlin
@Dao
interface SpecialDateDao {
    // CRUD
    @Insert(onConflict = REPLACE)
    suspend fun insert(specialDate: SpecialDate): Long
    
    @Update
    suspend fun update(specialDate: SpecialDate)
    
    @Delete
    suspend fun delete(specialDate: SpecialDate)
    
    @Query("DELETE FROM special_dates WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM special_dates")
    suspend fun deleteAll()
    
    // 查询
    @Query("SELECT * FROM special_dates ORDER BY isPinned DESC, targetDate ASC")
    fun getAllSpecialDates(): Flow<List<SpecialDate>>
    
    @Query("SELECT * FROM special_dates WHERE id = :id")
    suspend fun getSpecialDateById(id: Long): SpecialDate?
    
    @Query("SELECT * FROM special_dates WHERE title LIKE '%' || :query || '%' ORDER BY targetDate ASC")
    fun searchSpecialDates(query: String): Flow<List<SpecialDate>>
    
    @Query("SELECT COUNT(*) FROM special_dates")
    suspend fun getCount(): Int
    
    @Query("UPDATE special_dates SET isPinned = CASE WHEN isPinned = 0 THEN 1 ELSE 0 END WHERE id = :id")
    suspend fun togglePin(id: Long)
}
```

#### SpecialDateRelationDao

```kotlin
@Dao
interface SpecialDateRelationDao {
    @Insert(onConflict = IGNORE)
    suspend fun insert(relation: SpecialDateRelation): Long
    
    @Insert(onConflict = IGNORE)
    suspend fun insertAll(relations: List<SpecialDateRelation>)
    
    @Delete
    suspend fun delete(relation: SpecialDateRelation)
    
    @Query("DELETE FROM special_date_relations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM special_date_relations WHERE specialDateId = :specialDateId")
    suspend fun deleteBySpecialDateId(specialDateId: Long)
    
    @Query("SELECT * FROM special_date_relations WHERE specialDateId = :specialDateId")
    fun getRelationsBySpecialDateId(specialDateId: Long): Flow<List<SpecialDateRelation>>
    
    @Query("SELECT * FROM special_date_relations WHERE specialDateId = :specialDateId")
    suspend fun getRelationsBySpecialDateIdBlocking(specialDateId: Long): List<SpecialDateRelation>
    
    @Query("SELECT COUNT(*) > 0 FROM special_date_relations WHERE specialDateId = :specialDateId AND targetType = :targetType AND targetId = :targetId")
    suspend fun isRelationExist(specialDateId: Long, targetType: String, targetId: Long): Boolean
    
    @Query("DELETE FROM special_date_relations WHERE specialDateId = :specialDateId AND targetType = :targetType AND targetId = :targetId")
    suspend fun deleteRelation(specialDateId: Long, targetType: String, targetId: Long)
}
```

### 2.4 Repository

```kotlin
class SpecialDateRepository(
    private val specialDateDao: SpecialDateDao,
    private val relationDao: SpecialDateRelationDao
) {
    val allDates: Flow<List<SpecialDate>> = specialDateDao.getAllSpecialDates()
    
    suspend fun getById(id: Long) = specialDateDao.getSpecialDateById(id)
    suspend fun insert(date: SpecialDate) = specialDateDao.insert(date)
    suspend fun update(date: SpecialDate) = specialDateDao.update(date)
    suspend fun delete(date: SpecialDate) { 
        relationDao.deleteBySpecialDateId(date.id)
        specialDateDao.delete(date) 
    }
    fun search(query: String) = specialDateDao.searchSpecialDates(query)
    suspend fun togglePin(id: Long) = specialDateDao.togglePin(id)
    
    // 关联操作
    fun getRelations(dateId: Long) = relationDao.getRelationsBySpecialDateId(dateId)
    suspend fun getRelationsBlocking(dateId: Long) = relationDao.getRelationsBySpecialDateIdBlocking(dateId)
    suspend fun addRelation(relation: SpecialDateRelation)
    suspend fun removeRelation(dateId: Long, targetType: String, targetId: Long)
}
```

### 2.5 数据库迁移（v17 → v18）

```kotlin
private val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS special_dates (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                targetDate INTEGER NOT NULL,
                category TEXT NOT NULL DEFAULT 'OTHER',
                countMode INTEGER NOT NULL DEFAULT 0,
                repeatType INTEGER NOT NULL DEFAULT 0,
                reminderDays INTEGER NOT NULL DEFAULT 0,
                content TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                imagePaths TEXT NOT NULL DEFAULT '',
                imageUrls TEXT NOT NULL DEFAULT '',
                isPinned INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
        """)
        
        database.execSQL("CREATE INDEX IF NOT EXISTS index_special_dates_targetDate ON special_dates(targetDate)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_special_dates_isPinned ON special_dates(isPinned)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_special_dates_category ON special_dates(category)")
        
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS special_date_relations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                specialDateId INTEGER NOT NULL,
                targetType TEXT NOT NULL,
                targetId INTEGER NOT NULL,
                createdAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                FOREIGN KEY(specialDateId) REFERENCES special_dates(id) ON DELETE CASCADE
            )
        """)
        
        database.execSQL("CREATE INDEX IF NOT EXISTS index_special_date_relations_specialDateId ON special_date_relations(specialDateId)")
    }
}
```

---

## 3. ViewModel 设计

### 3.1 StateFlow 列表

| StateFlow | 类型 | 说明 |
|-----------|------|------|
| specialDates | `StateFlow<List<SpecialDate>>` | 全部日期原始数据 |
| groupedDates | `StateFlow<Map<String, List<DisplayDate>>>` | 三组分类后的展示数据 |
| searchQuery | `StateFlow<String>` | 搜索关键词 |
| editingDate | `StateFlow<SpecialDate?>` | 当前编辑的日期（null=新建） |
| relations | `StateFlow<List<SpecialDateRelation>>` | 当前编辑日期的关联列表 |
| isLoading | `StateFlow<Boolean>` | 加载状态 |

### 3.2 DisplayDate 展示模型

ViewModel 内部数据类，用于 UI 展示：

```kotlin
data class DisplayDate(
    val id: Long,
    val title: String,
    val targetDate: Long,           // 有效目标日期（已处理重复）
    val originalTargetDate: Long,   // 原始设定的目标日期
    val category: DateCategory,
    val countMode: CountMode,
    val daysRemaining: Long,        // 天数差（可正可负）
    val daysAbsolute: Long,         // 天数绝对值
    val dayColor: DayColor,         // 颜色枚举
    val groupType: GroupType,       // UPCOMING / CELEBRATING / EXPIRED
    val displayText: String,        // "2天后" / "365天" / "30天前"
    val content: String,
    val tags: List<String>,
    val hasImage: Boolean,
    val hasRelation: Boolean,
    val isPinned: Boolean
)

enum class GroupType { UPCOMING, CELEBRATING, EXPIRED }
enum class DayColor { RED, ORANGE, GRAY, GREEN }
enum class DateCategory(val displayName: String, val emoji: String) {
    BIRTHDAY("生日", "\uD83C\uDF82"),
    ANNIVERSARY("纪念日", "\uD83D\uDC95"),
    HOLIDAY("节日", "\uD83C\uDF89"),
    OTHER("其他", "\uD83D\uDCC5")
}
```

### 3.3 核心算法：三组动态分组

```
输入: List<SpecialDate> (来自 Room Flow)
输出: Map<GroupType, List<DisplayDate>>

对每条 date:
  1. computeEffectiveDate(date):
     - repeatType == 0 → targetDate
     - repeatType == 1 (按年) → 找到 >= today 的最近同月同年或下一年
     - repeatType == 2 (按月) → 找到 >= today 的最近同日或下一月
     
  2. daysDiff = computeEffectiveDate() - today(当天00:00)
  
  3. 确定 groupType:
     if countMode == COUNTDOWN:
       daysDiff > 0  → UPCOMING
       daysDiff <= 0 → EXPIRED
     else: // COUNTUP
       → CELEBRATING (daysAbs = abs(daysDiff))
       
  4. 确定 dayColor:
     if groupType == CELEBRATING → GREEN
     else if abs(daysDiff) <= 3   → RED
     else if abs(daysDiff) <= 30  → ORANGE
     else                          → GRAY
```

### 3.4 公开方法

- `encodeTags(tags: List<String>): String` — 标签列表 → JSON 字符串
- `decodeTags(json: String): List<String>` — JSON 字符串 → 标签列表
- `encodePaths(paths: List<String>): String` — 路径列表 → JSON 字符串
- `decodePaths(json: String): List<String>` — JSON 字符串 → 路径列表
- `saveDate(title, targetDate, category, countMode, repeatType, reminderDays, content, tags, imagePaths)` — 保存/更新
- `deleteDate(id: Long)` — 删除（级联删除关联）
- `togglePin(id: Long)` — 切换置顶

---

## 4. UI 层设计

### 4.1 文件结构

```
ui/screens/date/
├── SpecialDateScreen.kt              # 列表页主入口
├── SpecialDateEditScreen.kt          # 编辑/新建页
└── components/
    ├── SpecialDateCard.kt            # 天数卡片组件
    ├── DateGroupHeader.kt            # 分组标题栏
    ├── SpecialDateEmptyState.kt      # 空状态组件
    ├── DateCategoryPicker.kt         # 分类选择器（FilterChip）
    ├── CountModeSwitch.kt            # 计时模式切换（SegmentedButton）
    ├── RepeatTypePicker.kt           # 重复类型选择
    └── ReminderSetting.kt            # 提醒设置组件
```

### 4.2 SpecialDateScreen（列表页）

**布局结构**: Scaffold(TopAppBar + LazyColumn + FAB)

**TopAppBar**:
- 标题: "📅 特殊日期"
- 右侧: 搜索图标（点击展开搜索栏）

**LazyColumn 内容**:
1. **空状态**: 当 dates 为空时显示 `SpecialDateEmptyState`
2. **有数据时**: 按 groupType 分组渲染
   - `DateGroupHeader(groupType)` — 带 color dot 的标题行
   - `SpecialDateCard(displayDate)` × N — 该组的所有卡片

**FAB**: 点击导航到 `date_edit` 路由

### 4.3 SpecialDateCard（天数卡片）

```
┌─────────────────────────────────┐
│ ┌────┐  标题文字              │
│ │天数 │  目标日期              │
│ │单位 │  🏷️分类标签            │
│ └────┘  🔗 关联提示（如有） [图]│
└─────────────────────────────────┘
```

- **左侧圆形数字区** (64×64dp):
  - 背景: 根据 dayColor 设色（红=#FFF0F0 / 橙=#FFF3E0 / 灰=#F5F5F5 / 绿=#E8F5E9）
  - 数字: 22sp Bold，颜色匹配背景
  - 单位: "天后" / "天" / "天前"，10sp
- **中间信息区**:
  - 标题: 15sp SemiBold，单行截断
  - 日期: 12sp #888888
  - 分类标签: 圆角 pill，配色对应分类
  - 关联提示: 10sp #aaa（仅当有关联时显示）
- **右侧缩略图** (40×40dp,圆角8dp): 仅当有图片时显示第一张

**交互**: 点击 → 进入编辑页; 长按 → 显示操作菜单（删除/置顶）

### 4.4 DateGroupHeader（分组标题）

```
● 即将到来 · 倒计时       (橙色dot)
● 正在纪念 · 正计时       (绿色dot)
● 已过期                 (灰色dot)
```

- 左侧 4×14dp 圆角矩形作为颜色指示点
- 文字: 13sp Medium, #666666

### 4.5 SpecialDateEmptyState（空状态）

```
        📅 (100dp, pulse动画)
   还没有特殊日期~
   记录重要的日子，
   不错过每个纪念！
   
   [📅 添加日期]  ← CTA按钮
```

- 图标: 48dp emoji + 渐变背景圆角容器 + pulse 动画
- 主文案: 18sp Bold
- 副文案: 14sp #999
- CTA: 渐变橙粉背景, 白字, 圆角24px, 阴影

### 4.6 SpecialDateEditScreen（编辑页）

**布局**: Scaffold(TopAppBar + Column(scrollable))

**TopAppBar**:
- 左侧: "取消" 按钮
- 中间: 编辑模式显示"编辑日期"，新建模式显示"新建日期"
- 右侧: "保存"按钮（暖橙色）

**表单字段（从上到下）**:

| # | 字段 | 组件 | 说明 |
|---|------|------|------|
| 1 | 标题 | OutlinedTextField | 必填，单行，placeholder="例如：小明生日" |
| 2 | 目标日期 | DatePicker | Material3 DatePicker Dialog |
| 3 | 分类 | FilterChip × 4 | 🎂生日 / 💕纪念日 / 🎉节日 / 📅其他 |
| 4 | 计时模式 | SegmentedButton / ChoiceChip | ⏳倒计时(default) / ⏱️正计时 |
| 5 | 重复类型 | DropdownMenu 或 ChoiceChip | 不重复(default) / 按年 / 按月 |
| 6 | 提醒设置 | 条件显示 | 当 repeatType≠0 时显示，选择提前天数(1/3/7/自定义) |
| 7 | 备注 | OutlinedTextArea | 多行文本，placeholder="准备什么礼物好呢..." |
| 8 | 标签 | TagInputField | 复用灵感模块的 Chip 输入模式 |
| 9 | 图片 | ImagePicker | 复用灵感模块的图片选择器 |
| 10 | 关联 | RelationSelector | 复用灵感模块的关联选择器，增加 "date" 类型 |

### 4.7 组件复用映射

| 特殊日期组件 | 对应灵感组件 | 改动说明 |
|-------------|-------------|---------|
| TagInputField | inspiration.components.TagInputField | 直接复用 |
| ImagePicker | inspiration.components.ImagePicker | 直接复用 |
| RelationSelector | inspiration.components.RelationSelector | targetType 增加 "date" 选项 |

---

## 5. 导航与路由

### Screen.kt 新增

```kotlin
object SpecialDateEdit : Screen("date_edit")                           // 新建日期
object SpecialDateEditWithId : Screen("date_edit/{specialDateId}")      // 编辑日期
```

### AppNavHost.kt 新增路由

```kotlin
composable(Screen.Date.route) {
    SpecialDateScreen(navController = navController)  // 替换占位符
}

composable(Screen.SpecialDateEdit.route) {
    SpecialDateEditScreen(navController = navController)
}

composable(Screen.SpecialDateEditWithId.route) { backStackEntry ->
    val specialDateId = backStackEntry.arguments?.getString("specialDateId")?.toLongOrNull()
    SpecialDateEditScreen(navController = navController, specialDateId = specialDateId)
}
```

### MainScreen.kt 修改点

1. TabItem.DATE → `SpecialDateScreen(navController)` （替换占位符）
2. BubbleType.SPECIAL_DATE → 路由改为 `"date_edit"` （当前已是此值，确认一致）

### DatabaseModule.kt 新增

```kotlin
@Provides @Singleton
fun provideSpecialDateDao(database: CorgiMemoDatabase): SpecialDateDao

@Provides @Singleton
fun provideSpecialDateRelationDao(database: CorgiMemoDatabase): SpecialDateRelationDao
```

---

## 6. 颜色规范

### 天数颜色规则

| 条件 | 文字颜色 | 圆形背景色 | 含义 |
|------|---------|-----------|------|
| 倒计时 ≤ 3 天 | #E53935 (红) | #FFF0F0 | 紧急临近 |
| 倒计时 4-30 天 | #FF9A5C (橙) | #FFF3E0 | 常规范围 |
| 倒计时 > 30 天 | #999999 (灰) | #F5F5F5 | 远期不急 |
| 正计时（任意天数） | #4CAF50 (绿) | #E8F5E9 | 进行中 |

### 分类颜色

| 分类 | 标签背景 | 标签文字 |
|------|---------|---------|
| BIRTHDAY 生日 | #FFF0F5 | #E91E63 |
| ANNIVERSARY 纪念日 | #FFF3E0 | #FF9A5C |
| HOLIDAY 节日 | #E0F7FA | #00BCD4 |
| OTHER 其他 | #F3E5F5 | #9C27B0 |

---

## 7. 实现步骤（执行顺序）

### Phase 1: 数据层
1. 创建 `SpecialDate.kt` Entity
2. 创建 `SpecialDateRelation.kt` Entity
3. 创建 `SpecialDateDao.kt` 接口
4. 创建 `SpecialDateRelationDao.kt` 接口
5. 创建 `SpecialDateRepository.kt`
6. 修改 `CorgiMemoDatabase.kt`: version 17→18, 添加实体/DAO/MIGRATION_17_18
7. 修改 `DatabaseModule.kt`: 添加 provideSpecialDateDao / provideSpecialDateRelationDao

### Phase 2: ViewModel
8. 创建 `SpecialDateViewModel.kt`: 包含 DisplayDate 模型、三组分组算法、CRUD 方法

### Phase 3: 列表页 UI
9. 创建 `components/SpecialDateCard.kt`
10. 创建 `components/DateGroupHeader.kt`
11. 创建 `components/SpecialDateEmptyState.kt`
12. 创建 `SpecialDateScreen.kt` 主页面

### Phase 4: 编辑页 UI
13. 创建 `components/DateCategoryPicker.kt`
14. 创建 `components/CountModeSwitch.kt`
15. 创建 `components/RepeatTypePicker.kt`
16. 创建 `components/ReminderSetting.kt`
17. 创建 `SpecialDateEditScreen.kt` 编辑页

### Phase 5: 导航集成
18. 修改 `Screen.kt`: 添加 SpecialDateEdit / SpecialDateEditWithId
19. 修改 `AppNavHost.kt`: 替换 Date 占位符 + 添加编辑页路由
20. 修改 `MainScreen.kt`: 替换 DATE tab 为真实页面

---

## 8. 检查清单

### 数据层
- [ ] Entity 字段与 Migration SQL 一致（含 defaultValue 和 NOT NULL）
- [ ] ForeignKey CASCADE 配置正确
- [ ] 索引创建完整

### ViewModel
- [ ] 三组分组算法正确（倒计时/正计时/过期）
- [ ] 年度重复计算正确（跨年边界情况）
- [ ] 月度重复计算正确（月末边界情况）
- [ ] 天数颜色规则正确
- [ ] encodeTags/decodePaths 为 public（供 EditScreen 使用）

### 列表页
- [ ] 三组顺序: 即将到来 → 正在纪念 → 已过期
- [ ] 置顶日期排在每组最前
- [ ] 空状态显示正确
- [ ] 搜索过滤正常工作

### 编辑页
- [ ] 10 个字段均可用且验证正确
- [ ] 日期选择器正常弹出
- [ ] 计时模式切换正常
- [ ] 重复类型联动提醒设置显隐
- [ ] 保存后返回列表并刷新

### 导航
- [ ] 底部 📅Tab 点击进入列表页
- [ ] 气泡 📅 点击进入新建页
- [ ] 卡片点击进入编辑页
- [ ] 编辑页保存/取消正常返回
