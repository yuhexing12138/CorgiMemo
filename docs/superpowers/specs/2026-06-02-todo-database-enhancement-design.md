# Todo 数据库增强设计文档

**日期**: 2026-06-02  
**状态**: 待审核  
**目标**: 为 TodoItem 实体类添加 `dueDate` 字段，同时保留现有的 `startDate` 字段

---

## 1. 背景与需求

### 1.1 当前状态

项目已具备完整的 Room 数据库基础设施：
- ✅ `TodoItem` 实体类（24 个字段）
- ✅ `TodoDao` 接口（25+ 方法）
- ✅ `CorgiMemoDatabase` 数据库类（版本 21）
- ✅ Hilt 依赖注入配置
- ✅ 4 个复合索引优化查询性能

### 1.2 需求差异

| 原始需求字段 | 现有实现字段 | 差异说明 |
|-------------|-------------|---------|
| `dueDate: Long?` (截止时间) | `startDate: Long?` (开始时间) | **语义不同** |

### 1.3 用户决策

**采用"两者都保留"方案**：同时支持 `startDate`（开始时间）和 `dueDate`（截止时间），提供更灵活的时间管理能力。

---

## 2. 设计方案

### 2.1 字段设计

```kotlin
// 新增字段
val dueDate: Long? = null,           // 截止时间（时间戳）
```

**字段语义说明**：

| 字段名 | 类型 | 用途 | 示例 |
|-------|------|------|------|
| `startDate` | `Long?` | 任务开始时间 | 计划开始工作的时刻 |
| `estimatedDurationMinutes` | `Int?` | 预估时长（分钟） | 预计需要 2 小时 |
| `dueDate` | `Long?` | 截止时间（新增） | 必须完成的最后期限 |

**使用场景**：
- 场景 A：只设置开始时间 → 使用 `startDate`
- 场景 B：只设置截止时间 → 使用 `dueDate`
- 场景 C：完整时间规划 → `startDate` + `estimatedDurationMinutes` + `dueDate`

### 2.2 索引优化策略

为 `dueDate` 添加索引，优化以下查询场景：
- 按截止时间排序的待办列表
- 即将到期的待办提醒
- 已过期未完成的待办筛选

**新增索引**：
```kotlin
Index(value = ["dueDate", "status"])  // 复合索引：截止时间 + 状态
```

### 2.3 数据库迁移方案

**版本**: 21 → 22

**迁移操作**:
```sql
ALTER TABLE todo_items ADD COLUMN dueDate INTEGER;
```

**兼容性处理**:
- 新字段允许为 NULL（可选）
- 不影响现有数据
- 向后兼容旧版本应用

---

## 3. 实现细节

### 3.1 文件修改清单

| 文件路径 | 修改类型 | 说明 |
|---------|---------|------|
| `data/model/TodoItem.kt` | 修改 | 添加 `dueDate` 字段和索引 |
| `data/local/db/CorgiMemoDatabase.kt` | 修改 | 添加 MIGRATION_21_22 |
| `data/local/db/TodoDao.kt` | 修改 | 添加 dueDate 相关查询方法 |

### 3.2 TodoItem 实体类变更

```kotlin
@Entity(
    tableName = "todo_items",
    indices = [
        Index(value = ["status", "createdAt"]),
        Index(value = ["categoryId", "status"]),
        Index(value = ["priority", "startDate"]),
        Index(value = ["hasSubTasks"]),
        Index(value = ["dueDate", "status"])  // 新增
    ]
)
data class TodoItem(
    // ... 现有字段 ...
    
    /** 截止时间（时间戳），可为空 */
    val dueDate: Long? = null,  // 新增字段
    
    // ... 其他字段 ...
)
```

### 3.3 数据库迁移脚本

```kotlin
/** 版本 21 → 22 迁移：添加 dueDate 截止时间字段 */
private val MIGRATION_21_22 = object : Migration(21, 22) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE todo_items ADD COLUMN dueDate INTEGER")
    }
}
```

### 3.4 TodoDao 新增方法

```kotlin
/** 按截止时间排序获取待办（即将到期优先） */
@Query("SELECT * FROM todo_items WHERE dueDate IS NOT NULL ORDER BY dueDate ASC")
fun getTodosByDueDateAsc(): Flow<List<TodoItem>>

/** 获取已过期未完成的待办 */
@Query("""
    SELECT * FROM todo_items 
    WHERE status = 0 
    AND dueDate IS NOT NULL 
    AND dueDate < :currentTime
    ORDER BY dueDate ASC
""")
suspend fun getOverdueTodos(currentTime: Long): List<TodoItem>

/** 获取即将到期的待办（N 小时内） */
@Query("""
    SELECT * FROM todo_items 
    WHERE status = 0 
    AND dueDate IS NOT NULL 
    AND dueDate >= :startTime 
    AND dueDate <= :endTime
    ORDER BY dueDate ASC
""")
fun getUpcomingTodos(startTime: Long, endTime: Long): Flow<List<TodoItem>>
```

---

## 4. 影响分析

### 4.1 正面影响

✅ **功能增强**: 支持更灵活的时间管理模式  
✅ **向后兼容**: 不破坏现有功能和数据  
✅ **性能优化**: 新索引加速截止时间相关查询  
✅ **用户体验**: 用户可自由选择使用 startDate 或 dueDate

### 4.2 潜在风险

⚠️ **数据库版本升级**: 需要用户更新应用（Room 强制要求）  
⚠️ **字段冗余**: startDate 和 dueDate 可能造成混淆（需 UI 层明确引导）  
⚠️ **代码复杂度**: 需要在业务逻辑中处理两种时间字段的优先级

### 4.3 缓解措施

- 在 UI 层添加清晰的字段标签和提示文案
- 提供默认值逻辑：若只设置一个字段，自动计算另一个
- 编写单元测试验证迁移脚本的正确性

---

## 5. 测试策略

### 5.1 单元测试

- [ ] 迁移脚本测试：验证 21 → 22 版本迁移成功
- [ ] 字段插入/读取测试：验证 dueDate 字段的 CRUD 操作
- [ ] 索引有效性测试：验证新索引被正确创建和使用

### 5.2 集成测试

- [ ] DAO 查询方法测试：验证新增的 dueDate 查询方法返回正确结果
- [ ] 兼容性测试：验证旧数据在新版本中的正常工作

---

## 6. 后续优化建议（可选）

1. **智能默认值**: 当用户只设置 startDate + estimatedDurationMinutes 时，自动计算 dueDate
2. **UI 引导**: 在待办编辑界面提供"开始时间"和"截止时间"两个独立输入框
3. **排序选项**: 在待办列表中增加"按截止时间排序"选项
4. **提醒增强**: 基于 dueDate 的过期提醒通知

---

## 7. 实施计划

### Phase 1: 核心实现（必须）
- [x] 修改 TodoItem.kt 添加 dueDate 字段
- [ ] 编写 MIGRATION_21_22 迁移脚本
- [ ] 更新 CorgiMemoDatabase 版本号
- [ ] 添加 TodoDao 查询方法

### Phase 2: 验证（必须）
- [ ] 编译验证
- [ ] 运行迁移测试
- [ ] 功能测试

### Phase 3: 优化（可选）
- [ ] UI 层适配
- [ ] 业务逻辑整合
- [ ] 性能监控

---

## 8. 审批确认

- [x] 需求分析完成
- [x] 设计方案确认
- [ ] 开发实施
- [ ] 测试验证
- [ ] 上线发布

---

**文档编写**: AI Assistant  
**审核状态**: ⏳ 待用户审核
