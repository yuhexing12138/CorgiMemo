# 灵感记录功能设计文档

> **日期**: 2026-05-27
> **状态**: 待审核
> **版本**: v1.0

---

## 1. 功能概述

为 CorgiMemo 应用添加「灵感记录」功能，允许用户快速记录灵感、想法、笔记等内容，支持富文本编辑、图片插入、标签分类和跨模块关联。

### 1.1 核心功能

| 功能 | 描述 | 优先级 |
|------|------|--------|
| 时间线列表 | 按日期分组展示灵感，支持置顶 | P0 |
| 灵感卡片 | 显示标题、内容预览、缩略图、标签、时间 | P0 |
| 编辑页 | 标题、富文本内容、标签、图片、关联 | P0 |
| 空状态 | 灯泡动画 + 引导文案 + CTA按钮 | P0 |
| 搜索功能 | 支持标题/内容/标签搜索 | P1 |
| 关联功能 | 关联待办、日期、其他灵感 | P1 |

---

## 2. 技术架构

### 2.1 架构方案：独立模块（方案 A）

采用与现有 Todo 功能相同的 MVVM + Repository 模式，确保代码一致性和可维护性。

```
┌─────────────────────────────────────────────────────┐
│                    UI Layer (Compose)                │
│  ┌─────────────────┐  ┌───────────────────────────┐ │
│  │ InspirationScreen│  │ InspirationEditScreen     │ │
│  │ (时间线列表)     │  │ (编辑页)                  │ │
│  └────────┬────────┘  └────────────┬──────────────┘ │
│           │                        │                 │
│  ┌────────▼────────┐  ┌───────────▼──────────────┐  │
│  │ InspirationCard │  │ RichTextEditor           │  │
│  │ TimelineGroup   │  │ TagInput / ImagePicker   │  │
│  └─────────────────┘  └──────────────────────────┘  │
├─────────────────────────────────────────────────────┤
│               ViewModel Layer                       │
│  ┌──────────────────────────────────────────────┐   │
│  │         InspirationViewModel                  │   │
│  │  - 状态管理 (StateFlow)                      │   │
│  │  - 业务逻辑 (CRUD/搜索/关联)                   │   │
│  └────────────────────┬─────────────────────────┘   │
├───────────────────────┼──────────────────────────────┤
│              Data Layer                             │
│  ┌────────────────────▼─────────────────────────┐   │
│  │        InspirationRepository                  │   │
│  │  ┌─────────────────┐  ┌───────────────────┐  │   │
│  │  │ InspirationDao  │  │ RelationDao       │  │   │
│  │  └────────┬────────┘  └────────┬──────────┘  │   │
│  │           │                    │             │   │
│  │  ┌────────▼────────┐  ┌───────▼───────────┐  │   │
│  │  │  inspirations   │  │ inspiration_      │  │   │
│  │  │  (表)           │  │ relations (表)    │  │   │
│  │  └─────────────────┘  └───────────────────┘  │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## 3. 数据模型设计

### 3.1 Inspiration 实体

```kotlin
@Entity(
    tableName = "inspirations",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["isPinned"])
    ]
)
data class Inspiration(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val title: String,                    // 标题（必填）
    val content: String = "",             // 富文本内容 (HTML格式)
    val tags: String = "",               // 标签 JSON数组: ["产品","设计"]
    
    // 图片字段（混合存储）
    val imagePaths: String = "",         // 本地图片路径 JSON数组
    val imageUrls: String = "",          // 云端图片URL JSON数组（预留）
    
    // 时间字段
    val createdAt: Long,                 // 创建时间戳
    val updatedAt: Long,                 // 更新时间戳
    
    // 状态字段
    val isPinned: Boolean = false,       // 是否置顶
    val isArchived: Boolean = false      // 是否归档
)
```

**字段说明**:

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | Long | PK, 自增 | 主键 |
| `title` | String | NOT NULL | 标题，最多100字 |
| `content` | String | "" | 富文本HTML内容，无长度限制 |
| `tags` | String | "" | JSON编码的字符串数组 |
| `imagePaths` | String | "" | 本地文件路径JSON数组 |
| `imageUrls` | String | "" | 预留云端URL |
| `createdAt` | Long | NOT NULL | 创建时间戳(毫秒) |
| `updatedAt` | Long | NOT NULL | 更新时间戳(毫秒) |
| `isPinned` | Boolean | false | 置顶标记 |
| `isArchived` | Boolean | false | 归档标记 |

### 3.2 InspirationRelation 关联实体

```kotlin
@Entity(
    tableName = "inspiration_relations",
    indices = [Index(value = ["inspirationId"])],
    foreignKeys = [
        ForeignKey(
            entity = Inspiration::class,
            parentColumns = ["id"],
            childColumns = ["inspirationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class InspirationRelation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    val inspirationId: Long,             // 所属灵感ID
    val targetType: String,              // 目标类型: "todo" | "date" | "inspiration"
    val targetId: Long,                 // 目标实体ID
    val createdAt: Long = System.currentTimeMillis()
)
```

**关联关系图**:

```
Inspiration (1) ─────< (N) InspirationRelation
                        │
                        ├── targetType="todo", targetId=123
                        ├── targetType="date", targetId=456  
                        └── targetType="inspiration", targetId=789
```

---

## 4. DAO 接口设计

### 4.1 InspirationDao

```kotlin
@Dao
interface InspirationDao {
    /** 插入灵感 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(inspiration: Inspiration): Long
    
    /** 更新灵感 */
    @Update
    suspend fun update(inspiration: Inspiration)
    
    /** 删除灵感 */
    @Delete
    suspend fun delete(inspiration: Inspiration)
    
    /** 按ID删除 */
    @Query("DELETE FROM inspirations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    /** 获取所有灵感（按置顶+创建时间降序） */
    @Query("SELECT * FROM inspirations ORDER BY isPinned DESC, createdAt DESC")
    fun getAllInspirations(): Flow<List<Inspiration>>
    
    /** 按ID获取 */
    @Query("SELECT * FROM inspirations WHERE id = :id")
    suspend fun getInspirationById(id: Long): Inspiration?
    
    /** 搜索（支持标题/内容/标签模糊匹配） */
    @Query("""SELECT * FROM inspirations 
              WHERE title LIKE '%' || :query || '%' 
                 OR content LIKE '%' || :query || '%'
                 OR tags LIKE '%' || :query || '%'
              ORDER BY isPinned DESC, createdAt DESC""")
    fun searchInspirations(query: String): Flow<List<Inspiration>>
    
    /** 获取灵感总数 */
    @Query("SELECT COUNT(*) FROM inspirations")
    suspend fun getCount(): Int
}
```

### 4.2 InspirationRelationDao

```kotlin
@Dao
interface InspirationRelationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(relation: InspirationRelation): Long
    
    @Delete
    suspend fun delete(relation: InspirationRelation)
    
    @Query("DELETE FROM inspiration_relations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM inspiration_relations WHERE inspirationId = :inspirationId")
    suspend fun deleteByInspirationId(inspirationId: Long)
    
    @Query("SELECT * FROM inspiration_relations WHERE inspirationId = :inspirationId")
    fun getRelationsByInspirationId(inspirationId: Long): Flow<List<InspirationRelation>>
}
```

---

## 5. ViewModel 设计

### 5.1 InspirationViewModel

**职责**:
- 管理灵感数据的加载和缓存
- 处理 CRUD 操作
- 搜索过滤逻辑
- 时间线分组转换
- 关联管理

**状态流**:

| StateFlow | 类型 | 说明 |
|-----------|------|------|
| `inspirations` | `StateFlow<List<Inspiration>>` | 原始列表 |
| `groupedInspirations` | `StateFlow<Map<String, List<Inspiration>>>` | 按日期分组的列表 |
| `searchQuery` | `StateFlow<String>` | 搜索关键词 |
| `editingInspiration` | `StateFlow<Inspiration?>` | 当前编辑项 |
| `relations` | `StateFlow<List<InspirationRelation>>` | 关联列表 |

**核心方法**:

| 方法 | 参数 | 返回值 | 说明 |
|------|------|--------|------|
| `loadInspirations()` | - | - | 加载所有灵感 |
| `createInspiration()` | title, content, tags, images | - | 创建新灵感 |
| `updateInspiration()` | Inspiration | - | 更新灵感 |
| `deleteInspiration()` | id: Long | - | 删除灵感及关联 |
| `addRelation()` | inspirationId, targetType, targetId | - | 添加关联 |
| `search()` | query: String | - | 搜索灵感 |

---

## 6. UI 组件设计

### 6.1 页面结构

#### InspirationScreen（列表页）

```
┌─────────────────────────────────────────┐
│  ☰  ───────────────────────  📊  🐕   │  ← TopBar
│      💡 我的灵感                         │
│      ══════════════                      │
├─────────────────────────────────────────┤
│  🔍 搜索灵感...                         │  ← SearchBar
├─────────────────────────────────────────┤
│  ── 2026年5月27日 周三 ──               │  ← TimelineGroupHeader
│                                         │
│  ┌─────────────────────────────────┐   │
│  │ 💡 APP新功能构思                 │   │  ← InspirationCard
│  │ "可以加入番茄钟..."              │   │
│  │ 🏷️ #产品 #设计    ⏰ 14:30     │   │
│  └─────────────────────────────────┘   │
│                                         │
│  [空状态或更多卡片...]                   │
│                                         │
│                          ┌──────┐       │  ← FAB
│                          │  ⊕   │       │
│                          └──────┘       │
├─────────────────────────────────────────┤
│  📝待办   💡灵感(高亮)  ⊕  📅日期  👤  │  ← BottomNav
└─────────────────────────────────────────┘
```

#### InspirationEditScreen（编辑页）

```
┌─────────────────────────────────────────┐
│  ← 取消                   保存          │  ← TopAppBar
├─────────────────────────────────────────┤
│                                         │
│  标题：                                  │
│  ┌─────────────────────────────────┐   │
│  │ APP新功能构思                    │   │
│  └─────────────────────────────────┘   │
│                                         │
│  内容：                                  │
│  ┌─────────────────────────────────┐   │
│  │ 可以加入番茄钟...[富文本编辑区]   │   │  ← RichTextEditor
│  │                                 │   │
│  └─────────────────────────────────┘   │
│                                         │
│  🏷️ 标签：                              │
│  ┌──────┐ ┌──────┐ ┌──────────┐       │
│  │ #产品 │ │ #设计 │ │ + 添加   │       │  ← TagInputField
│  └──────┘ └──────┘ └──────────┘       │
│                                         │
│  🖼️ 图片：                              │
│  ┌──────┐ ┌──────┐ ┌──────┐           │
│  │ img1 │ │ img2 │ │  ＋   │           │  ← ImagePicker
│  └──────┘ └──────┘ └──────┘           │
│                                         │
│  🔗 已关联：                            │
│  ┌─────────────────────────────────┐   │
│  │ 📝 完成周报              [×]   │   │  ← RelationSelector
│  └─────────────────────────────────┘   │
│                                         │
└─────────────────────────────────────────┘
```

### 6.2 组件规格

#### InspirationCard 卡片

| 属性 | 值 |
|------|-----|
| 圆角 | 16dp |
| 内边距 | 16dp |
| 背景 | 白色，微阴影 (elevation 2dp) |
| 标题 | 16sp 粗体，最多1行，超出省略 |
| 内容预览 | 14sp 常规，最多2行，超出省略号 |
| 缩略图 | 48×48dp，最多显示2张，多余显示"+N" |
| 标签 | 12sp，暖橙色背景圆角标签 (#FFF3E0) |
| 时间 | 12sp，#999999灰色 |

#### TimelineGroupHeader 时间线标题

| 属性 | 值 |
|------|-----|
| 格式 | "YYYY年M月D日 周X" |
| 字号 | 13sp |
| 颜色 | #666666 |
| 字重 | 600 |
| 间距 | 上16dp，下12dp |

### 6.3 空状态设计

```
┌─────────────────────────────────────────┐
│                                         │
│              ┌─────┐                    │
│              │ 💡  │  ← 灯泡图标(脉冲动画) │
│              └─────┘                    │
│                                         │
│     "还没有灵感记录~"                    │
│     "点击下方按钮记录你的第一个灵感吧！"  │
│                                         │
│         ┌─────────────┐                 │
│         │ 💡 记录灵感  │  ← CTA按钮      │
│         └─────────────┘                 │
│                                         │
└─────────────────────────────────────────┘
```

---

## 7. 数据库迁移

### 7.1 版本升级：16 → 17

```kotlin
private val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 创建 inspirations 表
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS inspirations (
                INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                TEXT DEFAULT '',
                tags TEXT DEFAULT '',
                imagePaths TEXT DEFAULT '',
                imageUrls TEXT DEFAULT '',
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                isPinned INTEGER NOT NULL DEFAULT 0,
                isArchived INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        
        // 创建索引
        database.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_createdAt ON inspirations(createdAt)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_isPinned ON inspirations(isPinned)")
        
        // 创建 inspiration_relations 表
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS inspiration_relations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                inspirationId INTEGER NOT NULL,
                targetType TEXT NOT NULL,
                targetId INTEGER NOT NULL,
                createdAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                FOREIGN KEY(inspirationId) REFERENCES inspirations(id) ON DELETE CASCADE
            )
        """.trimIndent())
        
        database.execSQL("CREATE INDEX IF NOT EXISTS index_inspiration_relations_inspirationId ON inspiration_relations(inspirationId)")
    }
}
```

---

## 8. 文件结构清单

### 新增文件

```
app/src/main/java/com/corgimemo/app/
├── data/
│   ├── model/
│   │   ├── Inspiration.kt                    # 灵感实体
│   │   └── InspirationRelation.kt            # 关联实体
│   └── local/db/
│       ├── InspirationDao.kt                 # 灵感DAO
│       └── InspirationRelationDao.kt         # 关联DAO
├── data/repository/
│   └── InspirationRepository.kt              # 灵感仓库
├── viewmodel/
│   └── InspirationViewModel.kt               # 灵感ViewModel
└── ui/screens/inspiration/
    ├── InspirationScreen.kt                  # 列表页
    ├── InspirationEditScreen.kt              # 编辑页
    └── components/
        ├── InspirationCard.kt                # 卡片组件
        ├── TimelineGroupHeader.kt            # 时间线标题
        ├── InspirationEmptyState.kt          # 空状态组件
        ├── RichTextEditor.kt                 # 富文本编辑器
        ├── TagInputField.kt                  # 标签输入组件
        ├── ImagePicker.kt                    # 图片选择器
        └── RelationSelector.kt               # 关联选择器
```

### 修改文件

| 文件 | 修改内容 |
|------|----------|
| `CorgiMemoDatabase.kt` | 添加 entities、version 升级到 17、添加 migration |
| `MainScreen.kt` | 添加 Inspire 路由处理 |

---

## 9. 实现步骤（Implementation Checklist）

### Phase 1: 数据层基础
- [ ] 创建 `Inspiration.kt` 实体类
- [ ] 创建 `InspirationRelation.kt` 实体类
- [ ] 创建 `InspirationDao.kt` 接口
- [ ] 创建 `InspirationRelationDao.kt` 接口
- [ ] 创建 `InspirationRepository.kt`
- [ ] 更新 `CorgiMemoDatabase.kt`（添加 migration 16→17）

### Phase 2: ViewModel
- [ ] 创建 `InspirationViewModel.kt`
- [ ] 实现加载/创建/更新/删除方法
- [ ] 实现搜索功能
- [ ] 实现时间线分组逻辑
- [ ] 实现关联管理方法

### Phase 3: UI - 列表页
- [ ] 创建 `InspirationScreen.kt`
- [ ] 创建 `TimelineGroupHeader.kt`
- [ ] 创建 `InspirationCard.kt`
- [ ] 创建 `InspirationEmptyState.kt`

### Phase 4: UI - 编辑页
- [ ] 创建 `InspirationEditScreen.kt`
- [ ] 创建 `TagInputField.kt`
- [ ] 创建 `ImagePicker.kt`
- [ ] 创建 `RelationSelector.kt`
- [ ] 创建 `RichTextEditor.kt`（或集成第三方库）

### Phase 5: 导航集成
- [ ] 更新 `MainScreen.kt` 添加 Inspire 路由
- [ ] 测试完整流程

---

## 10. 技术风险与依赖

### 第三方依赖

| 库 | 用途 | 必要性 |
|----|------|--------|
| Coil | 异步图片加载 | 必须 |
| RichTextEditor | 富文本编辑 | 推荐 (或自研简化版) |

### 风险点

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 富文本编辑器复杂度高 | 开发周期延长 | 先实现基础版（加粗/斜体），后续迭代 |
| 图片存储路径管理 | 跨设备迁移问题 | 使用 ContentResolver URI |
| 关联数据一致性 | 删除时级联处理 | 使用 ForeignKey + CASCADE |

---

## 11. 验收标准

### 功能验收

- [ ] 灵感列表按时间线分组展示
- [ ] 灵感卡片正确显示标题、内容预览（2行）、缩略图、标签、时间
- [ ] 点击卡片进入编辑页
- [ ] 长按卡片弹出操作菜单（编辑/删除）
- [ ] 编辑页支持标题、内容（富文本）、标签输入、图片插入
- [ ] 编辑页支持关联其他卡片（待办/日期/灵感）
- [ ] 保存后返回列表并刷新
- [ ] 搜索功能正常工作
- [ ] 空状态显示灯泡图标+引导文案+CTA按钮
- [ ] 删除所有灵感后显示空状态

### 性能验收

- [ ] 列表滚动流畅（60fps）
- [ ] 图片异步加载不阻塞UI
- [ ] 搜索响应 < 300ms

---

*文档结束*
