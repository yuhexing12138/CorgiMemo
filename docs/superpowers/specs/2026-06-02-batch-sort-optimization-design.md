# TodoDao 批量更新优化与 MultiSortConfig N 级排序重构

**日期**: 2026-06-02
**状态**: ✅ 已批准
**作者**: AI Assistant
**关联设计**: [2026-06-02-searchbar-enhancement-design.md](./2026-06-02-searchbar-enhancement-design.md)

---

## 📋 项目背景

### 问题描述

1. **TodoDao batchUpdatePositions 性能瓶颈**
   - 当前实现为 `forEach` 循环逐条调用 `updateTodoPosition`
   - N 条待办 = N 次 SQLite UPDATE 语句
   - 无事务保护，中间状态可能暴露
   - 当拖拽排序项数 >50 时，性能显著下降

2. **MultiSortConfig 固定 3 级限制**
   - 数据结构使用 `primarySort`/`secondarySort`/`tertiarySort` 三个独立字段
   - 反序列化时超出 3 个的排序条件被静默丢弃（`in 3..Int.MAX_VALUE`）
   - 无法支持更复杂的多维度排序需求
   - 数据被截断时用户无感知

### 影响范围

| 模块 | 文件 | 修改类型 |
|------|------|---------|
| **批量更新优化** | TodoDao.kt | 性能重构 |
| **N 级排序重构** | MultiSortConfig.kt | 数据结构重构 |
| **UI 兼容** | MultiSortSheet.kt | 适配验证 |

---

## 🎯 设计目标

1. **batchUpdatePositions 性能提升 10-20x**：从 N 次 SQL 降为 1 次
2. **原子性保证**：使用 `@Transaction` 确保批量操作的事务完整性
3. **MultiSortConfig 支持 N 级排序**：基于 `List<SortCondition>` 动态存储
4. **向后兼容**：保留 `primarySort`/`secondarySort`/`tertiarySort` 计算属性
5. **零数据迁移**：序列化格式不变，已有 DataStore 数据无需处理

---

## 🏗️ 第一部分：TodoDao 原生 SQL 批量更新

### 1.1 架构设计

```
┌──────────────────────────────────────────────┐
│               调用方 (ViewModel)              │
│                                              │
│  batchUpdatePositions(mapOf(1→0, 2→1, 3→2)) │
│         │                                    │
│         ▼                                    │
│  ┌──────────────┐    @Transaction            │
│  │   TodoDao     │ ◄─────────────────────────┤
│  │              │                            │
│  │ ① 构建 SQL:  │                            │
│  │  UPDATE ...   │                            │
│  │  SET pos =    │                            │
│  │  (CASE id     │                            │
│  │   WHEN 1 THEN 0                           │
│  │   WHEN 2 THEN 1                           │
│  │   WHEN 3 THEN 2                           │
│  │  END)           │                            │
│  │         │     │                            │
│  │         ▼     │                            │
│  │ ② SimpleSQLiteQuery                       │
│  │         │     │                            │
│  │         ▼     │                            │
│  │ ③ rawUpdate()│ ← @RawQuery 新方法          │
│  └──────────────┘                            │
│         │                                    │
│         ▼                                    │
│  ┌──────────────┐                            │
│  │    SQLite     │  单次执行，原子完成          │
│  └──────────────┘                            │
└──────────────────────────────────────────────┘
```

### 1.2 函数签名与实现

```kotlin
/**
 * 批量更新多个待办项的排序位置（原生SQL优化版）
 *
 * 使用 SimpleSQLiteQuery 构建动态 CASE WHEN SQL，
 * 在单次数据库往返中完成所有位置的原子更新。
 *
 * **性能对比**:
 * - 旧方案：N 条数据 → N 次 SQLite UPDATE 调用
 * - 新方案：N 条数据 → 1 次 CASE WHEN UPDATE 调用
 *
 * **SQL 示例（3 条数据）**:
 * ```sql
 * UPDATE todo_items
 * SET position = (CASE id WHEN 1 THEN 0 WHEN 2 THEN 1 WHEN 3 THEN 2 END)
 * WHERE id IN (1, 2, 3)
 * ```
 *
 * @param positions Map<todoId, newPosition> 待办 ID 到新位置的映射
 * @return 受影响的行数
 */
@Transaction
suspend fun batchUpdatePositions(positions: Map<Long, Int>): Int {
    /** 空数据直接返回，避免无效 SQL */
    if (positions.isEmpty()) return 0

    /** 动态构建 CASE WHEN 子句 */
    val caseWhen = positions.entries.joinToString(" ") { (id, pos) ->
        "WHEN $id THEN $pos"
    }

    /** 构建 IN 子句中的 ID 列表 */
    val ids = positions.keys.joinToString(",")

    /** 组装完整 SQL */
    val sql = """
        UPDATE todo_items 
        SET position = (CASE id $caseWhen END) 
        WHERE id IN ($ids)
    """.trimIndent()

    /** 通过 RawQuery 执行原生 SQL */
    return rawUpdate(SimpleSQLiteQuery(sql))
}
```

### 1.3 新增 DAO 方法

```kotlin
/**
 * 执行原始 SQL 更新语句
 *
 * 用于需要动态构建 SQL 的场景（如批量 CASE WHEN 更新）。
 * 必须在 @Transaction 注解的方法内调用以保证原子性。
 *
 * @param query 原始 SQL 查询对象
 * @return 受影响的行数
 */
@RawQuery
suspend fun rawUpdate(query: SupportSQLiteQuery): Int
```

### 1.4 Import 要求

```kotlin
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SimpleSQLiteQuery
```

---

## 🏗️ 第二部分：MultiSortConfig N 级排序重构

### 2.1 数据结构变更

#### 变更前（固定 3 级）

```kotlin
data class MultiSortConfig(
    val primarySort: SortCondition,
    val secondarySort: SortCondition? = null,
    val tertiarySort: SortCondition? = null
)
```

#### 变更后（N 级动态）

```kotlin
/**
 * 多级排序配置（N 级版本）
 *
 * 支持任意数量的排序条件组合，
 * 用于实现复杂的多维度列表排序需求。
 *
 * **设计原则**:
 * - 内部使用 List<SortCondition> 存储，支持任意级别数
 * - 通过计算属性保留 primarySort/secondarySort/tertiarySort 向后兼容
 * - 序列化格式保持不变，无需数据迁移
 *
 * @property sorts 排序条件列表（有序，第一个为主排序，至少包含 1 个元素）
 */
data class MultiSortConfig(
    val sorts: List<SortCondition> = listOf(SortCondition(SortField.UPDATED_AT, SortDirection.DESCENDING))
) {
    init {
        require(sorts.isNotEmpty()) { "MultiSortConfig 至少需要 1 个排序条件" }
    }

    /** 主排序条件（第一级）— 向后兼容属性 */
    val primarySort: SortCondition get() = sorts.first()

    /** 次排序条件（第二级），可能为空 — 向后兼容属性 */
    val secondarySort: SortCondition? get() = sorts.getOrNull(1)

    /** 第三级排序条件，可能为空 — 向后兼容属性 */
    val tertiarySort: SortCondition? get() = sorts.getOrNull(2)
}
```

### 2.2 Companion Object 重构

```kotlin
companion object {
    /** 默认配置：仅按更新时间降序 */
    val DEFAULT = MultiSortConfig.of(
        SortCondition(SortField.UPDATED_AT, SortDirection.DESCENDING)
    )

    /** 基于优先级的推荐配置 */
    val PRIORITY_BASED = MultiSortConfig.of(
        SortCondition(SortField.PRIORITY, SortDirection.DESCENDING),
        SortCondition(SortField.DUE_DATE, SortDirection.ASCENDING)
    )

    /** 基于时间的推荐配置 */
    val TIME_BASED = MultiSortConfig.of(
        SortCondition(SortField.DUE_DATE, SortDirection.ASCENDING),
        SortCondition(SortField.CREATED_AT, SortDirection.DESCENDING)
    )

    /**
     * 创建单级排序配置
     */
    fun of(primary: SortCondition): MultiSortConfig =
        MultiSortConfig(listOf(primary))

    /**
     * 创建双级排序配置
     */
    fun of(primary: SortCondition, secondary: SortCondition): MultiSortConfig =
        MultiSortConfig(listOf(primary, secondary))

    /**
     * 创建三级排序配置
     */
    fun of(primary: SortCondition, secondary: SortCondition, tertiary: SortCondition): MultiSortConfig =
        MultiSortConfig(listOf(primary, secondary, tertiary))

    /**
     * 从可变参数创建 N 级排序配置
     */
    fun of(vararg conditions: SortCondition): MultiSortConfig =
        MultiSortConfig(conditions.toList())
}
```

### 2.3 方法适配

所有现有方法只需将 `listOfNotNull(primarySort, secondarySort, tertiarySort)` 替换为 `sorts`：

```kotlin
/** 生成 SQL ORDER BY 子句 */
fun toOrderByClause(): String =
    sorts.joinToString(", ") { it.toSqlClause() }

/** 生成人类可读描述 */
fun toDisplayDescription(): String =
    sorts.joinToString(" → ") { it.toDisplayText() }

/** 获取有效排序级别数 */
fun getLevelCount(): Int = sorts.size

/** 序列化为持久化字符串 */
fun serialize(): String =
    sorts.joinToString("|") { "${it.field.name}:${it.direction.name}" }
```

### 2.4 反序列化修复

```kotlin
/**
 * 从序列化字符串反序列化
 *
 * ✅ 不再截断：所有解析出的排序条件都保留
 * ✅ 日志警告：当条件数量异常时记录日志
 */
fun deserialize(serialized: String): MultiSortConfig {
    return try {
        if (serialized.isBlank()) return DEFAULT

        val parts = serialized.split("|").map { part ->
            val (field, direction) = part.split(":")
            SortCondition(SortField.valueOf(field.trim()), SortDirection.valueOf(direction.trim()))
        }

        when {
            parts.isEmpty() -> DEFAULT
            else -> {
                /** 记录日志以便调试（非阻塞） */
                if (parts.size > 5) {
                    Log.w("MultiSortConfig", "反序列化得到 ${parts.size} 个排序条件，可能存在数据异常")
                }
                MultiSortConfig(parts)
            }
        }
    } catch (e: Exception) {
        Log.w("MultiSortConfig", "反序列化失败: ${e.message}，使用默认配置")
        DEFAULT
    }
}
```

### 2.5 向后兼容性保证

| 旧 API | 新行为 | 兼容性 |
|--------|--------|--------|
| `config.primarySort` | 返回 `sorts[0]` | ✅ 完全兼容 |
| `config.secondarySort` | 返回 `sorts.getOrNull(1)` | ✅ 完全兼容 |
| `config.tertiarySort` | 返回 `sorts.getOrNull(2)` | ✅ 完全兼容 |
| `MultiSortConfig(primary=..., secondary=...)` | ❌ 命名参数构造器不可用 | ⚠️ 需改为 `.of()` |
| `config.copy(primarySort=...)` | ❌ copy 不可用 | ⚠️ 需改为重新创建 |
| `config.serialize()` | 格式不变 | ✅ 完全兼容 |
| `config.deserialize(str)` | 不再截断 | ✅ 向上兼容 |

### 2.6 MultiSortSheet.kt 迁移指南

**受影响代码模式**:

```kotlin
// ❌ 旧代码（命名参数构造 + copy）
var editableConfig = currentConfig.copy(primarySort = newCondition)
editableConfig = editableConfig.copy(secondarySort = null)

// ✅ 新代码（使用 .of() 或直接操作 List）
val newList = currentConfig.sorts.toMutableList()
newList[0] = newCondition  // 替换 primary
val editableConfig = MultiSortConfig(newList)
// 或删除某一级
newList.removeAt(1)
val editableConfig = MultiSortConfig(newList)
```

---

## 📝 实施计划

### 任务分解

| 任务 ID | 任务名称 | 文件 | 优先级 |
|--------|---------|------|--------|
| O1 | 添加 rawUpdate 方法和 import | TodoDao.kt | 🔴 高 |
| O2 | 重写 batchUpdatePositions 为原生 SQL | TodoDao.kt | 🔴 高 |
| O3 | 重构 MultiSortConfig 数据结构 | MultiSortConfig.kt | 🔴 高 |
| O4 | 适配 MultiSortSheet.kt 调用方 | MultiSortSheet.kt | 🔴 高 |
| O5 | 全局搜索其他调用点并迁移 | 全局 | 🟡 中 |
| O6 | 编译验证 | 全局 | 🔴 高 |

### 依赖关系

```
O1 (添加 rawUpdate)
 ↓
O2 (重写 batchUpdatePositions)     O3 (重构 MultiSortConfig)
 ↓                                      ↓
O6 (编译验证)                    O4 (适配 MultiSortSheet)
                                     ↓
                                  O5 (全局迁移)
                                     ↓
                                  O6 (编译验证)
```

---

## ⚠️ 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| **RawQuery SQL 注入** | 低 | 高 | 仅内部使用，ID 和 position 均为 Int 类型 |
| **MultiSortSheet UI 逻辑破坏** | 中 | 中 | 保留计算属性；充分测试 |
| **DataStore 反序列化旧数据** | 低 | 低 | 格式不变，完全兼容 |
| **大量排序条件导致 SQL 过长** | 极低 | 低 | 日志警告 + 建议上限 5 级 |

---

## 🧪 测试策略

### TodoDao 批量更新测试

| 用例 | 输入 | 预期结果 |
|------|------|---------|
| 空Map | `{}` | 返回 0，不执行 SQL |
| 单条 | `{1L → 5}` | 1 行受影响 |
| 正常批量 | `{1→0, 2→1, 3→2}` | 3 行受影响，position 正确 |
| 大量数据 | 100 条 | 1 次 SQL，耗时 < 10ms |

### MultiSortConfig 测试

| 用例 | 输入 | 预期结果 |
|------|------|---------|
| 单级反序列化 | `"UPDATED_AT:DESCENDING"` | 1 个条件 |
| 三级反序列化 | `"PRIORITY:DESC\|DUE_DATE:ASC\|CREATED_AT:DESC"` | 3 个条件 |
| 五级反序列化 | 5 个 `\|` 分隔的条件 | 5 个条件 + Log.w 警告 |
| 空字符串 | `""` | DEFAULT |
| primarySort 属性访问 | 任意 config | 返回 sorts[0] |
| secondarySort 属性（无次级） | 单级 config | 返回 null |

---

## ✅ 审批记录

| 日期 | 版本 | 内容 | 审批人 |
|------|------|------|--------|
| 2026-06-02 | v1.0 | 初始设计 | AI Assistant + User |

---

## 📌 后续优化建议

1. **考虑添加批量操作的 Repository 层封装**：统一管理所有批量数据库操作
2. **MultiSortConfig 添加 equals/hashCode 优化**：List 已自动获得正确的语义相等性
3. **性能基准测试**：对 batchUpdatePositions 进行 JMH 基准测试，量化优化效果
4. **UI 增强**：MultiSortSheet 支持动态添加/删除排序级别（不再限制 3 级）

---

**文档结束**
