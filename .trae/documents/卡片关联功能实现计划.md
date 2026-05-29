# 卡片关联功能实现计划

## 一、现状分析

### 已有关联基础设施

| 组件                          | 状态     | 说明                                                                               |
| --------------------------- | ------ | -------------------------------------------------------------------------------- |
| `InspirationRelation` 实体    | ✅ 已有   | `inspiration_relations` 表，字段：id, inspirationId, targetType, targetId, createdAt  |
| `SpecialDateRelation` 实体    | ✅ 已有   | `special_date_relations` 表，字段：id, specialDateId, targetType, targetId, createdAt |
| `InspirationRelationDao`    | ✅ 已有   | 完整 CRUD + 去重检查                                                                   |
| `SpecialDateRelationDao`    | ✅ 已有   | 完整 CRUD + 去重检查                                                                   |
| `RelationSelector` 组件       | ⚠️ 半完成 | 有关联列表展示和类型选择对话框，但搜索选择具体卡片的弹窗未实现（TODO）                                            |
| `InspirationViewModel` 关联方法 | ✅ 已有   | loadRelations, addRelation, deleteRelation                                       |
| `SpecialDateViewModel` 关联方法 | ✅ 已有   | addRelation, removeRelation                                                      |
| `TodoEditViewModel` 关联方法    | ❌ 缺失   | 完全没有关联相关代码                                                                       |
| `TodoRepository` 关联方法       | ❌ 缺失   | 完全没有关联相关代码                                                                       |
| 列表项关联提示                     | ⚠️ 部分  | InspirationCard 和 SpecialDateCard 有 🔗 emoji，但没有具体标题显示                           |
| @ 触发关联选择器                   | ❌ 缺失   | 未实现                                                                              |
| 关联数量限制（10张）                 | ❌ 缺失   | 未实现                                                                              |

### 核心问题

1. **关联表分散**：灵感用 `inspiration_relations`，日期用 `special_date_relations`，待办没有关联表
2. **待办无法关联**：TodoEditViewModel/TodoRepository 完全没有关联支持
3. **搜索选择弹窗未实现**：RelationSelector 中的类型选择后无法搜索具体卡片
4. **@ 触发机制未实现**：编辑页内容输入框无法通过 @ 触发关联
5. **列表项关联提示不完整**：只显示 🔗 emoji，不显示具体标题

***

## 二、设计方案

### 方案选择：统一 card\_relation 表

根据需求文档要求"创建 CardRelationDao 和 card\_relation 表"，采用统一关联表方案：

| 对比项   | 方案A：统一 card\_relation 表      | 方案B：保留现有表 + 新增 TodoRelation |
| ----- | ---------------------------- | --------------------------- |
| 数据模型  | 一张表，sourceType+sourceId 标识来源 | 三张表，每种来源独立                  |
| 代码复杂度 | 低，一套 DAO/Repository          | 高，三套 DAO/Repository         |
| 跨类型查询 | 简单，单表查询                      | 复杂，需 UNION                  |
| 迁移成本  | 中等，需迁移现有数据                   | 低，只需新增表                     |
| 可维护性  | 高                            | 低                           |

**选择方案A**，创建统一的 `card_relations` 表，迁移现有数据。

### CardRelation 实体设计

```kotlin
@Entity(
    tableName = "card_relations",
    indices = [
        Index(value = ["sourceType", "sourceId"]),
        Index(value = ["targetType", "targetId"]),
        Index(value = ["sourceType", "sourceId", "targetType", "targetId"], unique = true)
    ]
)
data class CardRelation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sourceType: String,   // "todo" | "inspiration" | "date"
    val sourceId: Long,
    val targetType: String,   // "todo" | "inspiration" | "date"
    val targetId: Long,
    val createdAt: Long = System.currentTimeMillis()
)
```

**设计要点**：

* `sourceType + sourceId`：关联发起方（哪张卡片创建的关联）

* `targetType + targetId`：关联目标方（被关联的卡片）

* 唯一索引防止重复关联

* 无外键约束（因为 Room 不支持多态外键），级联删除在 Repository 层手动处理

***

## 三、实现步骤

### 阶段1：数据层（CardRelation + CardRelationDao + 数据库迁移）

#### 步骤1.1：创建 CardRelation 实体

* **文件**：`app/src/main/java/com/corgimemo/app/data/model/CardRelation.kt`（新建）

* **内容**：如上方设计所示的 Entity 类

* **要点**：唯一索引 `(sourceType, sourceId, targetType, targetId)` 防止重复

#### 步骤1.2：创建 CardRelationDao

* **文件**：`app/src/main/java/com/corgimemo/app/data/local/db/CardRelationDao.kt`（新建）

* **核心方法**：

| 方法                  | 签名                                                               | 说明                     |
| ------------------- | ---------------------------------------------------------------- | ---------------------- |
| insert              | `(relation: CardRelation): Long`                                 | 插入关联，REPLACE 策略        |
| deleteById          | `(id: Long)`                                                     | 按ID删除                  |
| deleteBySource      | `(sourceType: String, sourceId: Long)`                           | 删除某卡片发起的所有关联           |
| deleteByTarget      | `(targetType: String, targetId: Long)`                           | 删除某卡片被关联的所有关联（被删卡片时调用） |
| deleteRelation      | `(sourceType, sourceId, targetType, targetId)`                   | 精确删除一条关联               |
| getBySource         | `(sourceType: String, sourceId: Long): Flow<List<CardRelation>>` | 获取某卡片发起的所有关联（响应式）      |
| getBySourceBlocking | `(sourceType: String, sourceId: Long): List<CardRelation>`       | 阻塞式获取                  |
| getByTarget         | `(targetType: String, targetId: Long): Flow<List<CardRelation>>` | 获取指向某卡片的所有关联           |
| isRelationExist     | `(sourceType, sourceId, targetType, targetId): Boolean`          | 去重检查                   |
| countBySource       | `(sourceType: String, sourceId: Long): Int`                      | 统计关联数量（用于10张限制）        |

#### 步骤1.3：数据库迁移 v19 → v20

* **文件**：`CorgiMemoDatabase.kt`（修改）

* **操作**：

  1. 注册 `CardRelation` 实体和 `CardRelationDao`
  2. 版本号 19 → 20
  3. 编写迁移脚本：

     * 创建 `card_relations` 表

     * 从 `inspiration_relations` 迁移数据（sourceType="inspiration", sourceId=inspirationId）

     * 从 `special_date_relations` 迁移数据（sourceType="date", sourceId=specialDateId）

     * 保留旧表（避免数据丢失），后续版本再删除

#### 步骤1.4：更新 Hilt Module

* **文件**：查找 DatabaseModule 或相关 Hilt 提供者

* **操作**：添加 `cardRelationDao()` 的 `@Provides` 方法

***

### 阶段2：Repository 层

#### 步骤2.1：创建 CardRelationRepository

* **文件**：`app/src/main/java/com/corgimemo/app/data/repository/CardRelationRepository.kt`（新建）

* **核心方法**：

| 方法                                                               | 说明                  |
| ---------------------------------------------------------------- | ------------------- |
| `getRelations(sourceType, sourceId): Flow<List<CardRelation>>`   | 获取关联列表              |
| `getRelationsBlocking(sourceType, sourceId): List<CardRelation>` | 阻塞式获取               |
| `addRelation(relation): Result<Long>`                            | 添加关联（含去重 + 10张限制检查） |
| `removeRelation(sourceType, sourceId, targetType, targetId)`     | 删除关联                |
| `removeAllBySource(sourceType, sourceId)`                        | 删除某卡片发起的所有关联        |
| `removeAllByTarget(targetType, targetId)`                        | 删除被关联卡片时解除关联        |
| `searchCards(query: String): Flow<List<CardSearchResult>>`       | 搜索卡片（跨三表）           |

#### 步骤2.2：定义 CardSearchResult 数据类

* **文件**：`app/src/main/java/com/corgimemo/app/data/model/CardSearchResult.kt`（新建）

* **内容**：

```kotlin
data class CardSearchResult(
    val cardType: String,   // "todo" | "inspiration" | "date"
    val cardId: Long,
    val title: String,
    val categoryName: String? = null,
    val categoryIcon: String? = null
)
```

#### 步骤2.3：更新现有 Repository

* **文件**：`InspirationRepository.kt`、`SpecialDateRepository.kt`、`TodoRepository.kt`

* **操作**：

  * 注入 `CardRelationRepository`

  * 删除操作中添加 `removeAllByTarget()` 调用（删除卡片时解除被关联关系）

  * 删除操作中添加 `removeAllBySource()` 调用（删除卡片时解除发起的关联）

  * 保留原有 DAO 方法以兼容旧数据，新增方法委托给 `CardRelationRepository`

***

### 阶段3：ViewModel 层

#### 步骤3.1：更新 InspirationViewModel

* **文件**：`InspirationViewModel.kt`（修改）

* **操作**：

  * 注入 `CardRelationRepository`

  * 将 `_relations: MutableStateFlow<List<InspirationRelation>>` 改为 `_relations: MutableStateFlow<List<CardRelation>>`

  * 更新 `loadRelations`、`addRelation`、`deleteRelation` 方法使用新 Repository

  * 添加 `searchCards(query)` 方法供搜索弹窗使用

#### 步骤3.2：更新 SpecialDateViewModel

* **文件**：`SpecialDateViewModel.kt`（修改）

* **操作**：同 InspirationViewModel 的改造方式

#### 步骤3.3：更新 TodoEditViewModel

* **文件**：`TodoEditViewModel.kt`（修改）

* **操作**：

  * 注入 `CardRelationRepository`

  * 添加 `_relations: MutableStateFlow<List<CardRelation>>`

  * 添加 `loadRelations(todoId)`、`addRelation(targetType, targetId)`、`deleteRelation(relationId)` 方法

  * 添加 `searchCards(query)` 方法

  * 保存待办时同步保存关联（参考 SpecialDateViewModel 的新建关联处理）

#### 步骤3.4：更新 HomeViewModel（列表页关联提示）

* **文件**：`HomeViewModel.kt`（修改）

* **操作**：

  * 注入 `CardRelationRepository`

  * 为每个待办/灵感/日期加载关联提示信息

  * 提供获取关联摘要的方法（返回第一条关联的标题+类型+总数）

***

### 阶段4：UI 组件

#### 步骤4.1：创建 CardLinkSelectorDialog 组件

* **文件**：`app/src/main/java/com/corgimemo/app/ui/components/CardLinkSelectorDialog.kt`（新建）

* **功能**：搜索选择弹窗，按需求文档 11.6.2 设计

* **UI 结构**：

```
Dialog（圆角24dp，elevation 8dp）
├── 标题行："关联卡片" + 关闭按钮
├── 搜索框（🔍 图标 + OutlinedTextField，圆角12dp）
├── 搜索结果列表（LazyColumn，按类型分组）
│   ├── 📝 待办 (N)
│   │   ├── 📝 完成周报
│   │   └── 📝 复习考试
│   ├── 💡 灵感 (N)
│   │   └── 💡 设计灵感
│   └── 📅 日期 (N)
│       └── 📅 生日
└── 空状态提示
```

* **设计规范**：

  * 弹窗圆角：24dp（`@dimen/dialog_corner_radius`）

  * 搜索框背景：`@color/ui_search_background`（#FFF3E8）

  * 分组标题字号：15sp Medium

  * 列表项字号：14sp Regular

  * 类型图标：📝💡📅 emoji

  * 间距：卡片内边距16dp，元素间距8dp

#### 步骤4.2：创建 @ 触发关联选择器组件

* **文件**：`app/src/main/java/com/corgimemo/app/ui/components/MentionTriggerPopup.kt`（新建）

* **功能**：在 TextField 中输入 @ 时弹出内联搜索选择器，按需求文档 11.6.1 设计

* **UI 结构**：

```
Popup（定位在光标附近）
├── 搜索框（🔍 + "搜索关联卡片..."）
├── 搜索结果列表（最多5项）
│   ├── 📝 完成周报    工作
│   ├── 📝 复习考试    学习
│   ├── 📅 生日        日期
│   └── 💡 设计灵感    灵感
└── 空结果提示
```

* **交互逻辑**：

  1. 监听 TextField 文本变化，检测 @ 字符输入
  2. @ 后的文本作为搜索关键词
  3. 选择卡片后：在文本中插入 `@类型:标题` 标记，同时添加关联记录
  4. 按 Backspace 删除 @ 时关闭弹窗
  5. 点击弹窗外部关闭

#### 步骤4.3：更新 RelationSelector 组件

* **文件**：`RelationSelector.kt`（修改）

* **操作**：

  * 参数类型从 `List<InspirationRelation>` 改为 `List<CardRelation>`

  * 更新 `RelationItem` 显示逻辑：通过 CardRelation 的 targetType + targetId 查询实际标题

  * 替换 `RelationTypeDialog` 为 `CardLinkSelectorDialog`

  * 添加关联数量限制提示（已达10张时禁用添加按钮）

  * 显示格式：类型图标 + 卡片标题（而非 #ID）

#### 步骤4.4：更新列表项关联提示

* **文件**：`TodoListItem.kt`、`InspirationCard.kt`、`SpecialDateCard.kt`（修改）

* **操作**：

  * 添加 `relationHint: String?` 和 `relationCount: Int` 参数

  * 在卡片底部显示："🔗 关联: @类型:标题"（最多1个，多余显示"+N"）

  * 样式：12sp 字号，`@color/ui_text_hint` 颜色，胶囊背景

***

### 阶段5：编辑页集成

#### 步骤5.1：更新 TodoEditScreen

* **文件**：`TodoEditScreen.kt`（修改）

* **操作**：

  * 在内容 TextField 下方添加 `MentionTriggerPopup` 支持

  * 在表单底部（图片选择器之后）添加 `RelationSelector`

  * 收集 `viewModel.relations` 状态

  * 传递 `onRelationAdd` 和 `onRelationDelete` 回调

#### 步骤5.2：更新 InspirationEditScreen

* **文件**：`InspirationEditScreen.kt`（修改）

* **操作**：

  * 在内容 TextField 添加 `MentionTriggerPopup` 支持

  * 更新 `RelationSelector` 参数类型为 `List<CardRelation>`

  * 确保关联保存逻辑正确

#### 步骤5.3：更新 SpecialDateEditScreen

* **文件**：`SpecialDateEditScreen.kt`（修改）

* **操作**：

  * 在备注 TextField 添加 `MentionTriggerPopup` 支持

  * 更新 `RelationSelector` 参数类型为 `List<CardRelation>`

  * 移除旧的类型转换适配代码

***

### 阶段6：列表页关联提示集成

#### 步骤6.1：更新 HomeScreen（待办列表）

* **文件**：`HomeScreen.kt`（修改）

* **操作**：

  * 从 HomeViewModel 获取每个待办的关联摘要

  * 传递 `relationHint` 和 `relationCount` 给 `TodoListItem`

#### 步骤6.2：更新 InspirationScreen（灵感列表）

* **文件**：`InspirationScreen.kt`（修改）

* **操作**：

  * 从 ViewModel 获取每个灵感的关联摘要

  * 更新 `InspirationCard` 的 `hasRelation` 为具体标题显示

#### 步骤6.3：更新 SpecialDateScreen（日期列表）

* **文件**：`SpecialDateScreen.kt`（修改）

* **操作**：

  * 从 ViewModel 获取每个日期的关联摘要

  * 更新 `SpecialDateCard` 的关联显示

***

### 阶段7：级联删除处理

#### 步骤7.1：更新删除逻辑

* **文件**：各 Repository 和 ViewModel

* **操作**：

  * 删除待办时：调用 `cardRelationRepository.removeAllByTarget("todo", id)` + `removeAllBySource("todo", id)`

  * 删除灵感时：同上，type="inspiration"

  * 删除特殊日期时：同上，type="date"

  * 确保在事务中执行删除+解除关联

***

## 四、文件变更清单

### 新建文件（5个）

| 文件                                                                              | 说明              |
| ------------------------------------------------------------------------------- | --------------- |
| `app/src/main/java/com/corgimemo/app/data/model/CardRelation.kt`                | 统一关联实体          |
| `app/src/main/java/com/corgimemo/app/data/model/CardSearchResult.kt`            | 搜索结果数据类         |
| `app/src/main/java/com/corgimemo/app/data/local/db/CardRelationDao.kt`          | 统一关联 DAO        |
| `app/src/main/java/com/corgimemo/app/data/repository/CardRelationRepository.kt` | 统一关联 Repository |
| `app/src/main/java/com/corgimemo/app/ui/components/CardLinkSelectorDialog.kt`   | 搜索选择弹窗          |
| `app/src/main/java/com/corgimemo/app/ui/components/MentionTriggerPopup.kt`      | @触发关联选择器        |

### 修改文件（约12个）

| 文件                         | 修改内容                                        |
| -------------------------- | ------------------------------------------- |
| `CorgiMemoDatabase.kt`     | 注册新 Entity/Dao，添加迁移脚本                       |
| `InspirationViewModel.kt`  | 改用 CardRelation                             |
| `SpecialDateViewModel.kt`  | 改用 CardRelation                             |
| `TodoEditViewModel.kt`     | 添加关联支持                                      |
| `HomeViewModel.kt`         | 添加关联摘要查询                                    |
| `InspirationRepository.kt` | 集成 CardRelationRepository                   |
| `SpecialDateRepository.kt` | 集成 CardRelationRepository                   |
| `TodoRepository.kt`        | 集成 CardRelationRepository                   |
| `RelationSelector.kt`      | 改用 CardRelation + 集成 CardLinkSelectorDialog |
| `TodoEditScreen.kt`        | 添加关联选择器和 @ 触发                               |
| `InspirationEditScreen.kt` | 更新关联组件                                      |
| `SpecialDateEditScreen.kt` | 更新关联组件                                      |
| `TodoListItem.kt`          | 添加关联提示显示                                    |
| `InspirationCard.kt`       | 更新关联提示显示                                    |
| `SpecialDateCard.kt`       | 更新关联提示显示                                    |
| `HomeScreen.kt`            | 传递关联摘要数据                                    |
| Hilt Module                | 提供 CardRelationDao                          |

***

## 五、UI 设计规范遵循

所有 UI 组件遵循项目设计规范：

| 规范项   | 值                                       | 应用位置                   |
| ----- | --------------------------------------- | ---------------------- |
| 弹窗圆角  | 24dp                                    | CardLinkSelectorDialog |
| 搜索框圆角 | 12dp                                    | 搜索输入框                  |
| 搜索框背景 | `@color/ui_search_background` (#FFF3E8) | 搜索框                    |
| 卡片内边距 | 16dp                                    | 弹窗内容区                  |
| 元素间距  | 8dp                                     | 列表项之间                  |
| 标签字号  | 12sp                                    | 关联提示文字                 |
| 辅助字号  | 13sp                                    | 分组标题                   |
| 正文字号  | 15sp                                    | 搜索结果项                  |
| 主色    | `@color/ui_primary` (#FF9A5C)           | 添加按钮、选中态               |
| 文字次要色 | `@color/ui_text_secondary` (#666666)    | 关联提示                   |
| 文字提示色 | `@color/ui_text_hint` (#999999)         | 空状态提示                  |
| 弹窗阴影  | 8dp elevation                           | Dialog                 |
| 动效    | 200-300ms                               | 弹窗出入动画                 |

***

## 六、风险与注意事项

1. **数据库迁移**：v19→v20 迁移需仔细测试，确保旧数据正确迁移
2. **性能**：列表页加载关联摘要可能影响性能，建议使用内存缓存或批量查询
3. **@ 触发**：Compose TextField 中精确定位光标位置较复杂，需使用 `TextFieldValue` 而非 `String`
4. **向后兼容**：保留旧表一段时间，避免数据丢失
5. **关联数量限制**：在 UI 层和 Repository 层双重检查，防止超过10张

