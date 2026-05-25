# 空状态引导优化功能设计文档

> **日期**: 2026-05-25  
> **版本**: v2.0（优化增强版）  
> **状态**: 待审批  

---

## 1. 功能概述

### 1.1 目标
在已实现的空状态引导功能基础上，增加以下 4 个优化功能：

| # | 功能 | 优先级 | 复杂度 |
|---|------|--------|--------|
| 1 | 设置页"重新查看引导"按钮 | P0 | ⭐ 低 |
| 2 | 自定义模板功能 | P0 | ⭐⭐⭐ 中高 |
| 3 | A/B 测试引导文案 | P1 | ⭐⭐ 中 |
| 4 | 引导完成庆祝动画 | P0 | ⭐⭐ 中 |

### 1.2 核心价值
- **提升留存率**: 通过庆祝动画和成就系统激励用户完成引导
- **个性化体验**: 支持用户自定义模板，满足不同使用场景
- **数据驱动**: A/B 测试帮助优化引导文案，提升转化率
- **用户控制**: 允许用户重新查看引导，降低学习成本

---

## 2. 详细设计

### 2.1 功能 1：设置页"重新查看引导"按钮

#### 2.1.1 UI 设计

**位置**: 设置页面 → "应用设置"分组 → 新增卡片项

```
┌─────────────────────────────────────┐
│ 应用设置                             │
├─────────────────────────────────────┤
│ 🔊 音效反馈        [Switch: ON]    │
│ 📳 触觉反馈        [Switch: ON]    │
│ 👤 身份设置         学生            │
│ 🧠 智能分类设置     管理关键词       │
│ 🎯 重新查看引导     重置首次引导流程 │  ← 新增
└─────────────────────────────────────┘
```

#### 2.1.2 交互流程

```
用户点击"重新查看引导"
    ↓
显示确认对话框：
"这将重置首次引导流程，下次进入空状态页面时将重新显示。确定继续？"
    ↓
[取消] / [确认重置]
    ↓ 确认
调用 resetFirstGuide()
显示 Toast："✅ 已重置，下次打开待办为空时将显示引导"
```

#### 2.1.3 文件变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `SettingsScreen.kt` | **修改** | 新增 SettingItemCard + 确认对话框 |
| `SettingsViewModel.kt` | **修改** | 新增 `resetFirstGuide()` 方法 |

---

### 2.2 功能 2：自定义模板

#### 2.2.1 数据模型

**Room 实体类**:
```kotlin
/**
 * 用户自定义模板实体
 */
@Entity(tableName = "user_templates")
data class UserTemplateEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,                    // 模板名称
    val icon: String,                    // 模板图标（Emoji）
    val description: String,             // 模板描述
    val todosJson: String,               // 待办列表 JSON
    val createdAt: Long = System.currentTimeMillis(),  // 创建时间
    val updatedAt: Long = System.currentTimeMillis()   // 更新时间
)
```

**DAO 接口**:
```kotlin
@Dao
interface TemplateDao {
    /** 获取所有用户模板（按更新时间倒序）*/
    @Query("SELECT * FROM user_templates ORDER BY updatedAt DESC")
    fun getAllTemplates(): Flow<List<UserTemplateEntity>>

    /** 根据 ID 获取模板 */
    @Query("SELECT * FROM user_templates WHERE id = :id")
    suspend fun getTemplateById(id: Long): UserTemplateEntity?

    /** 插入新模板 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: UserTemplateEntity): Long

    /** 更新模板 */
    @Update
    suspend fun updateTemplate(template: UserTemplateEntity)

    /** 删除模板 */
    @Delete
    suspend fun deleteTemplate(template: UserTemplateEntity)

    /** 获取模板数量 */
    @Query("SELECT COUNT(*) FROM user_templates")
    suspend fun getTemplateCount(): Int
}
```

#### 2.2.2 UI 组件

**模板管理页面 (`TemplateManageScreen`)**:

```
┌─────────────────────────────────────┐
│ ←  模板管理                          │
├─────────────────────────────────────┤
│                                     │
│  [+ 创建新模板]                      │
│                                     │
│ ┌─────────┐ ┌─────────┐ ┌─────────┐│
│ │ ☀️      │ │ 💼      │ │ 📚      ││
│ │我的习惯  │ │项目清单  │ │考试复习  ││
│ │5个待办   │ │8个待办   │ │6个待办   ││
│ │[编辑][删除]│ │[编辑][删除]│ │[编辑][删除]││
│ └─────────┘ └─────────┘ └─────────┘│
│                                     │
│ ┌─────────┐                         │
│ │ 🏠      │                         │
│ │家居清洁  │                         │
│ │4个待办   │                         │
│ │[编辑][删除]│                        │
│ └─────────┘                         │
│                                     │
└─────────────────────────────────────┘
```

**创建/编辑模板对话框 (`TemplateEditDialog`)**:

```
┌─────────────────────────────────────┐
│ 创建新模板                           │
├─────────────────────────────────────┤
│                                     │
│ 模板名称：[________________]          │
│                                     │
│ 图标选择：                           │
│ [☀️] [💼] [📚] [🏠] [🎯] [❤️] [...] │
│                                     │
│ 描述：[____________________]        │
│                                     │
│ 待办事项：                           │
│ ┌─────────────────────────────────┐ │
│ │ 1. [早起打卡        ] [删除]    │ │
│ │ 2. [喝8杯水         ] [删除]    │ │
│ │ 3. [运动30分钟      ] [删除]    │ │
│ │ 4. [+ 添加待办]                 │ │
│ └─────────────────────────────────┘ │
│                                     │
│           [取消]  [保存模板]         │
└─────────────────────────────────────┘
```

#### 2.2.3 数据流

```
用户操作 → TemplateManageScreen
    ↓
├── 创建模板 → TemplateEditDialog → TemplateDao.insert()
├── 编辑模板 → TemplateEditDialog(预填数据) → TemplateDao.update()
└── 删除模板 → 确认对话框 → TemplateDao.delete()

TemplateCarousel (增强版)
    ↓
获取数据源：
├── TemplateData.templates (系统模板，只读)
└── TemplateDao.getAllTemplates() (用户模板，可编辑)
    ↓
统一显示为 TemplateCard 列表
    ↓
用户点击 → createTodosFromTemplate(template)
```

#### 2.2.4 文件变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `data/model/UserTemplateEntity.kt` | **新增** | Room 实体类 |
| `data/local/db/TemplateDao.kt` | **新增** | DAO 接口 |
| `data/local/db/CorgiMemoDatabase.kt` | **修改** | 注册新 Entity 和 DAO |
| `data/repository/TemplateRepository.kt` | **新增** | Repository 层 |
| `ui/screens/settings/TemplateManageScreen.kt` | **新增** | 模板管理页面 |
| `ui/components/TemplateEditDialog.kt` | **新增** | 创建/编辑对话框 |
| `ui/components/TemplateCarousel.kt` | **修改** | 增加用户模板支持 |
| `viewmodel/TemplateViewModel.kt` | **新增** | 模板 ViewModel |

---

### 2.3 功能 3：A/B 测试引导文案

#### 2.3.1 分组策略

**分组时机**: 首次启动应用时（Onboarding 完成后）

**分组逻辑**:
```kotlin
/**
 * 分配 A/B 测试组别
 *
 * @return "A" 或 "B"
 */
fun assignAbGroup(): String {
    // 使用随机数分配，50/50 概率
    return if (Random.nextBoolean()) "A" else "B"
}
```

**存储位置**: CorgiPreferences (DataStore)

#### 2.3.2 文案变体

| 组别 | 气泡文字 | 引导标题 | 语音提示 |
|------|----------|----------|----------|
| **A 组（对照组）** | "🐕 柯基等你很久啦！" | "嗨！我是你的待办小助手~" | "💡 试试说：'明天开会'" |
| **B 组（实验组）** | "🌟 开始你的高效之旅！" | "欢迎！让我们一起管理待办~" | "💡 试试说：'明天开会'" |

#### 2.3.3 统计指标

**记录的数据点**:
```kotlin
// CorgiPreferences 新增键
val GUIDE_AB_GROUP = stringPreferencesKey("guide_ab_group")           // A/B 组别
val GUIDE_COMPLETED_AT = longPreferencesKey("guide_completed_at")     // 引导完成时间戳
val FIRST_TODO_CREATED_AT = longPreferencesKey("first_todo_created_at") // 首个待办创建时间
val GUIDE_STEP_TIMES = stringPreferencesKey("guide_step_times")       // 各步骤耗时 JSON
```

**计算指标**:
| 指标 | 计算方式 | 用途 |
|------|----------|------|
| 引导完成率 | completed_users / total_users | 衡量引导流程有效性 |
| 首次待办时间 | FIRST_TODO_CREATED_AT - GUIDE_COMPLETED_AT | 衡量引导→行动转化速度 |
| 各步骤耗时 | 解析 GUIDE_STEP_TIMES JSON | 识别流失严重的步骤 |

#### 2.3.4 统计展示入口

**设置页面新增卡片**:
```
┌─────────────────────────────────────┐
│ 📊 引导优化（A/B 测试）              │
│ 当前组别：B 组                       │
│ 引导状态：已完成 ✅                   │
│ [查看统计数据]                        │
└─────────────────────────────────────┘
```

**统计详情对话框**:
```
┌─────────────────────────────────────┐
│ 引导 A/B 测试数据                     │
├─────────────────────────────────────┤
│ 你的组别：B 组（实验组）              │
│                                     │
│ 📈 个人数据：                        │
│ • 引导完成时间：2026-05-25 10:30    │
│ • 首个待办创建：2026-05-25 10:35    │
│ • 引导→行动耗时：5 分钟             │
│                                     │
│ ℹ️ 这些数据仅存储在本地设备上        │
│                                     │
│              [关闭]                  │
└─────────────────────────────────────┘
```

#### 2.3.5 文件变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `CorgiPreferences.kt` | **修改** | 新增 A/B 相关键和方法 |
| `FirstTimeGuideOverlay.kt` | **修改** | 根据组别显示不同文案 |
| `CorgiGuideAnimation.kt` | **修改** | 气泡文字根据组别变化 |
| `HomeScreen.kt` | **修改** | 初始化 A/B 分组、记录时间戳 |
| `SettingsScreen.kt` | **修改** | 新增 A/B 测试信息卡片 |

---

### 2.4 功能 4：引导完成庆祝动画

#### 2.4.1 庆祝流程

```
用户完成首次引导（点击"开始使用"或走完 4 步）
    ↓
Step 1: 标记 first_guide_shown = true
    ↓
Step 2: 触发庆祝动画
├── 显示 CelebrationOverlay (SUPER 级别)
├── 文案："🎉 欢迎加入 CorgiMemo！"
├── 副文案："经验值 +20，柯基为你感到骄傲！"
└── 动画时长：3 秒（自动消失）
    ↓
Step 3: 显示成就解锁弹窗
├── AchievementUnlockDialog
├── 成就名称："新手探险家"
├── 成就描述："完成首次使用引导"
├── 解锁装扮："新手勋章" 🎖️
└── [开心！] 按钮
    ↓
Step 4: 经验值奖励
├── +20 经验值
└── 检查是否升级
```

#### 2.4.2 新成就定义

```kotlin
/** 成就 ID 枚举 - 新增 */
object AchievementId {
    // ... 已有成就 ...
    
    /** 首次引导完成 */
    const val FIRST_GUIDE = "first_guide"
}

/** AchievementManager.allAchievements - 新增 */
Achievement(
    id = AchievementId.FIRST_GUIDE,
    name = "新手探险家",
    description = "完成首次使用引导",
    conditionText = "完成引导流程",
    outfitId = "first_guide_badge",  // 🎖️ 新手勋章
    target = 1,
    icon = "🎖️"
)
```

#### 2.4.3 新装扮定义

```kotlin
/** OutfitId - 新增 */
object OutfitId {
    // ... 已有装扮 ...
    
    /** 新手勋章 */
    const val FIRST_GUIDE_BADGE = "first_guide_badge"
}

/** OutfitManager - 新增 */
Outfit(
    id = OutfitId.FIRST_GUIDE_BADGE,
    name = "新手勋章",
    icon = "🎖️",
    description = "完成首次引导获得",
    isDefault = false,
    unlockCondition = "完成首次使用引导"
)
```

#### 2.4.4 文件变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `AchievementManager.kt` | **修改** | 新增 FIRST_GUIDE 成就 |
| `OutfitManager.kt` | **修改** | 新增 FIRST_GUIDE_BADGE 装扮 |
| `HomeScreen.kt` | **修改** | 引导完成时触发庆祝+成就解锁 |
| `HomeViewModel.kt` | **修改** | 新增 `completeFirstGuide()` 方法 |

---

## 3. 架构总览

### 3.1 新增文件清单

```
app/src/main/java/com/corgimemo/app/
├── data/
│   ├── model/
│   │   └── UserTemplateEntity.kt        [新增] 模板 Room 实体
│   ├── local/
│   │   ├── db/
│   │   │   ├── TemplateDao.kt           [新增] 模板 DAO
│   │   │   └── CorgiMemoDatabase.kt     [修改] 注册新表
│   │   └── datastore/
│   │       └── CorgiPreferences.kt      [修改] A/B 键
│   └── repository/
│       └── TemplateRepository.kt        [新增] 模板 Repository
├── ui/
│   ├── components/
│   │   ├── CorgiGuideAnimation.kt       [修改] A/B 文案
│   │   ├── FirstTimeGuideOverlay.kt     [修改] A/B 文案
│   │   ├── TemplateCarousel.kt          [修改] 用户模板
│   │   └── TemplateEditDialog.kt        [新增] 模板编辑对话框
│   └── screens/
│       └── settings/
│           ├── SettingsScreen.kt        [修改] 新增按钮+A/B 卡片
│           └── TemplateManageScreen.kt  [新增] 模板管理页面
├── viewmodel/
│   ├── HomeViewModel.kt                 [修改] 庆祝逻辑
│   ├── SettingsViewModel.kt             [修改] resetFirstGuide
│   └── TemplateViewModel.kt             [新增] 模板 ViewModel
└── animation/
    ├── AchievementManager.kt            [修改] 新增成就
    └── OutfitManager.kt                 [修改] 新增装扮
```

### 3.2 数据流图

```
┌─────────────┐
│ SettingsScreen│
└──────┬──────┘
       │
       ├─ resetFirstGuide() ──→ CorgiPreferences.resetFirstGuide()
       │
       ├─ [重新查看引导] ──→ 确认对话框 → Toast
       │
       ├─ [模板管理] ──→ TemplateManageScreen
       │                    │
       │                    ├─ CRUD → TemplateRepository → TemplateDao → Room
       │                    │
       │                    └─ TemplateEditDialog
       │
       └─ [A/B 测试] ──→ 统计详情对话框
                            │
                            └─ CorgiPreferences (读取 A/B 数据)

┌─────────────┐
│ HomeScreen  │
└──────┬──────┘
       │
       ├─ 首次启动 ──→ assignAbGroup() → 存储 A/B 组
       │
       ├─ todos.isEmpty && !firstGuideShown
       │    └─ FirstTimeGuideOverlay (根据 A/B 组显示不同文案)
       │         └─ onGuideCompleted → completeFirstGuide()
       │              ├─ setFirstGuideShown()
       │              ├─ 记录 GUIDE_COMPLETED_AT
       │              ├─ 显示 CelebrationOverlay (SUPER)
       │              └─ 显示 AchievementUnlockDialog (新手探险家)
       │
       └─ todos.isEmpty && firstGuideShown
            └─ EnhancedEmptyState
                 └─ TemplateCarousel
                      ├─ TemplateData.templates (系统)
                      └─ TemplateDao.getAllTemplates() (用户)
```

---

## 4. 实施计划

### 4.1 阶段划分

| 阶段 | 内容 | 预估时间 | 依赖关系 |
|------|------|----------|----------|
| **Phase 1** | 功能 1：设置页按钮 | 30 min | 无 |
| **Phase 2** | 功能 4：引导庆祝 | 1.5 h | 无 |
| **Phase 3** | 功能 3：A/B 测试 | 1.5 h | Phase 1 |
| **Phase 4** | 功能 2：自定义模板 | 3 h | 无 |

### 4.2 执行顺序建议

**推荐顺序**: 1 → 4 → 3 → 2

理由：
1. **快速见效**：先实现功能 1 和 4，让用户立即看到效果
2. **渐进复杂**：从简单到复杂，降低调试难度
3. **独立可测**：每个阶段都可独立测试和验证

---

## 5. 测试策略

### 5.1 单元测试

| 测试对象 | 测试内容 |
|----------|----------|
| `TemplateDao` | CRUD 操作正确性 |
| `CorgiPreferences` | A/B 键的读写、resetFirstGuide 逻辑 |
| `TemplateData` | 模板数据完整性 |
| `assignAbGroup()` | 分组概率接近 50/50 |

### 5.2 UI 测试场景

| 场景 | 操作 | 预期结果 |
|------|------|----------|
| 重置引导 | 点击"重新查看引导"→ 确认 | Toast 提示 + 下次显示引导 |
| 创建模板 | 输入名称/待办 → 保存 | 模板出现在列表和轮播中 |
| 删除模板 | 点击删除 → 确认 | 模板从列表移除 |
| A/B 分组 | 首次启动 | 随机分配 A 或 B 组 |
| 引导庆祝 | 完成引导 | 庆祝动画 + 成就弹窗 + 经验值+20 |

### 5.3 手动验证清单

- [ ] 设置页显示"重新查看引导"按钮
- [ ] 点击按钮后显示确认对话框
- [ ] 确认后下次进入空状态时显示引导
- [ ] 可以创建自定义模板
- [ ] 自定义模板显示在模板轮播中
- [ ] 点击自定义模板可以批量创建待办
- [ ] 首次启动时分配 A/B 组
- [ ] 不同组显示不同的引导文案
- [ ] 设置页显示当前 A/B 组和统计信息
- [ ] 完成引导后显示 SUPER 级别庆祝动画
- [ ] 庆祝后显示"新手探险家"成就解锁弹窗
- [ ] 解锁"新手勋章"🎖️ 装扮
- [ ] 经验值正确增加 +20

---

## 6. 性能考虑

### 6.1 数据库优化
- **模板数据量限制**: 最多允许用户创建 20 个自定义模板
- **索引优化**: `user_templates.updatedAt` 字段添加索引
- **懒加载**: 模板列表使用 Flow，仅在订阅时查询

### 6.2 内存优化
- **模板缓存**: TemplateRepository 内部缓存最近使用的模板
- **图片资源**: Emoji 图标无需额外内存（系统字体渲染）

### 6.3 动画性能
- **庆祝动画复用**: 使用现有 `CelebrationOverlay`，避免重复代码
- **条件渲染**: 仅在引导完成时才加载庆祝组件

---

## 7. 后续扩展方向

1. **模板分享**: 导出/导入模板（JSON 格式）
2. **云端同步**: 多设备间同步自定义模板
3. **智能推荐**: 基于用户行为推荐热门模板
4. **A/B 结果分析**: 可选上传匿名统计数据到服务器
5. **更多成就**: "模板大师"（创建 10 个模板）、"引导专家"（重看引导 3 次）

---

*文档结束*
