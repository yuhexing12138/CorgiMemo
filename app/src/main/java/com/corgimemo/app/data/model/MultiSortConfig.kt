package com.corgimemo.app.data.model

import android.util.Log

/**
 * 排序字段枚举
 *
 * 定义支持多级排序的所有可排序字段，
 * 每个字段对应数据库中的一列或计算值。
 *
 * **可用排序字段**:
 * - **CREATED_AT**: 创建时间（默认）
 * - **UPDATED_AT**: 更新时间
 * - **DUE_DATE**: 截止时间
 * - **PRIORITY**: 优先级（高→低）
 * - **TITLE**: 标题（A→Z）
 * - **POSITION**: 手动排序位置
 */
enum class SortField(val displayName: String, val columnName: String) {
    /** 创建时间 */
    CREATED_AT("创建时间", "createdAt"),

    /** 更新时间 */
    UPDATED_AT("更新时间", "updatedAt"),

    /** 截止时间 */
    DUE_DATE("截止时间", "dueDate"),

    /** 优先级（数值越大越重要） */
    PRIORITY("优先级", "priority"),

    /** 标题（按字母顺序） */
    TITLE("标题", "title"),

    /** 手动排序位置（拖拽排序使用） */
    POSITION("手动排序", "position")
}

/**
 * 排序方向枚举
 *
 * 定义排序的升序或降序方向。
 */
enum class SortDirection(val displayName: String, val sqlKeyword: String) {
    /** 升序（A→Z, 0→9, 早→晚） */
    ASCENDING("升序", "ASC"),

    /** 降序（Z→A, 9→0, 晚→早） */
    DESCENDING("降序", "DESC")
}

/**
 * 单级排序条件
 *
 * 表示一个完整的排序维度，包含排序字段和方向。
 *
 * @property field 排序字段（如 CREATED_AT、PRIORITY）
 * @property direction 排序方向（ASCENDING 或 DESCENDING）
 *
 * **示例**:
 * ```kotlin
 * // 按"更新时间降序"排序
 * SortCondition(SortField.UPDATED_AT, SortDirection.DESCENDING)
 *
 * // 按"优先级升序"排序
 * SortCondition(SortField.PRIORITY, SortDirection.ASCENDING)
 * ```
 */
data class SortCondition(
    val field: SortField,
    val direction: SortDirection = SortDirection.DESCENDING
) {
    /**
     * 生成 SQL ORDER BY 子句片段
     *
     * @return 如 "updatedAt DESC"、"priority ASC"
     */
    fun toSqlClause(): String = "${field.columnName} ${direction.sqlKeyword}"

    /**
     * 生成人类可读的描述文本
     *
     * @return 如"更新时间降序"、"优先级升序"
     */
    fun toDisplayText(): String = "${field.displayName}${direction.displayName}"
}

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
 * **典型场景**:
 * 1. **先按状态，再按时间**: 主排序=状态，次排序=更新时间
 * 2. **先按优先级，再按截止日期**: 主排序=优先级，次排序=截止时间
 * 3. **手动排序 + 时间兜底**: 主排序=位置，次排序=创建时间
 *
 * **使用示例**:
 * ```kotlin
 * // 使用便捷工厂方法创建
 * val config = MultiSortConfig.of(
 *     SortCondition(SortField.PRIORITY, SortDirection.DESC),
 *     SortCondition(SortField.DUE_DATE, SortDirection.ASC)
 * )
 *
 * // 生成 SQL: ORDER BY priority DESC, dueDate ASC
 * val sql = config.toOrderByClause()
 *
 * // 向后兼容：仍可通过属性访问
 * config.primarySort   // → 第一级排序条件
 * config.secondarySort // → 第二级（可能为 null）
 * ```
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

    companion object {
        /**
         * 默认多级排序配置
         *
         * 默认行为：仅按更新时间降序排列（与 V1.0 行为一致）
         */
        val DEFAULT = MultiSortConfig.of(
            SortCondition(SortField.UPDATED_AT, SortDirection.DESCENDING)
        )

        /**
         * 基于优先级的推荐排序配置
         *
         * 先按优先级（高→低），再按截止时间（近→远）
         */
        val PRIORITY_BASED = MultiSortConfig.of(
            SortCondition(SortField.PRIORITY, SortDirection.DESCENDING),
            SortCondition(SortField.DUE_DATE, SortDirection.ASCENDING)
        )

        /**
         * 基于时间的推荐排序配置
         *
         * 先按截止时间（近→远），再按创建时间（新→旧）
         */
        val TIME_BASED = MultiSortConfig.of(
            SortCondition(SortField.DUE_DATE, SortDirection.ASCENDING),
            SortCondition(SortField.CREATED_AT, SortDirection.DESCENDING)
        )

        /**
         * 创建单级排序配置
         *
         * @param primary 主排序条件
         * @return 包含单个条件的 MultiSortConfig
         */
        fun of(primary: SortCondition): MultiSortConfig =
            MultiSortConfig(listOf(primary))

        /**
         * 创建双级排序配置
         *
         * @param primary 主排序条件
         * @param secondary 次排序条件
         * @return 包含两个条件的 MultiSortConfig
         */
        fun of(primary: SortCondition, secondary: SortCondition): MultiSortConfig =
            MultiSortConfig(listOf(primary, secondary))

        /**
         * 创建三级排序配置
         *
         * @param primary 主排序条件
         * @param secondary 次排序条件
         * @param tertiary 第三级排序条件
         * @return 包含三个条件的 MultiSortConfig
         */
        fun of(primary: SortCondition, secondary: SortCondition, tertiary: SortCondition): MultiSortConfig =
            MultiSortConfig(listOf(primary, secondary, tertiary))

        /**
         * 从可变参数创建 N 级排序配置
         *
         * @param conditions 可变数量的排序条件
         * @return 包含所有条件的 MultiSortConfig
         */
        fun of(vararg conditions: SortCondition): MultiSortConfig =
            MultiSortConfig(conditions.toList())

        /**
         * 从序列化字符串反序列化为 MultiSortConfig
         *
         * 格式: `field:direction|field:direction|...`
         *
         * ✅ 不再截断：所有解析出的排序条件都保留
         * ✅ 日志警告：当条件数量异常时记录日志（非阻塞）
         *
         * @param serialized 序列化字符串
         * @return MultiSortConfig 对象，如果格式无效则返回默认配置
         */
        fun deserialize(serialized: String): MultiSortConfig {
            return try {
                if (serialized.isBlank()) return DEFAULT

                val parts = serialized.split("|").map { part ->
                    val (field, direction) = part.split(":")
                    SortCondition(
                        field = SortField.valueOf(field.trim()),
                        direction = SortDirection.valueOf(direction.trim())
                    )
                }

                when {
                    parts.isEmpty() -> DEFAULT
                    else -> {
                        /** 当条件数量超过建议上限时记录日志 */
                        if (parts.size > 5) {
                            Log.w(
                                "MultiSortConfig",
                                "反序列化得到 ${parts.size} 个排序条件，超过建议上限 5"
                            )
                        }
                        MultiSortConfig(parts)
                    }
                }
            } catch (e: Exception) {
                Log.w("MultiSortConfig", "反序列化失败: ${e.message}，使用默认配置")
                DEFAULT
            }
        }
    }

    /**
     * 生成完整的 SQL ORDER BY 子句
     *
     * 将所有排序条件组合为 SQL 语法，
     * 用于 Room @Query 注解或 RawQuery。
     *
     * @return SQL ORDER BY 子句字符串（如 "priority DESC, dueDate ASC, createdAt DESC"）
     */
    fun toOrderByClause(): String =
        sorts.joinToString(", ") { it.toSqlClause() }

    /**
     * 生成人类可读的多级排序描述
     *
     * @return 描述文本（如"优先级降序 → 截止时间升序"）
     */
    fun toDisplayDescription(): String =
        sorts.joinToString(" → ") { it.toDisplayText() }

    /**
     * 检查是否为默认单级排序
     *
     * @return 如果与默认配置相等返回 true
     */
    fun isDefault(): Boolean = this == DEFAULT

    /**
     * 获取有效的排序条件数量
     *
     * @return 实际使用的排序级别数（≥1）
     */
    fun getLevelCount(): Int = sorts.size

    /**
     * 将配置序列化为字符串（用于 DataStore 持久化）
     *
     * 格式: `field:direction|field:direction|...`
     *
     * @return 序列化后的字符串
     */
    fun serialize(): String =
        sorts.joinToString("|") { "${it.field.name}:${it.direction.name}" }
}
