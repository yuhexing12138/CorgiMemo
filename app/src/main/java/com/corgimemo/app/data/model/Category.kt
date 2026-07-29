package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 任务分类实体
 * 用于区分不同类型的任务（学习、工作、生活等）
 *
 * v2026-07-29 改造：不再区分"默认分组"和"自定义分组"，所有分组均可被修改/删除/重命名/置顶。
 * - 移除原 `isDefault` 字段（彻底删除）
 * - 新增 `isPinned` 字段，用于置顶分组（true 时排在前）
 * - 5 个默认分类名称仍由 [DefaultCategoryName] 常量提供，仅供 Seeder 初始化使用，不再带特殊标记
 *
 * @property id 分类 ID
 * @property name 分类名称
 * @property type 分类类型：0=学习，1=工作，2=生活，3=运动，4=自定义，5=娱乐
 * @property isPinned 是否置顶（v2026-07-29 新增，置顶分组排在前）
 *   - @ColumnInfo(defaultValue = "0") 与 MIGRATION_54_55 SQL DEFAULT 0 一致
 *   - isPinned 上加 @Index 索引，与 MIGRATION_54_55 创建的 index_categories_isPinned 一致
 * @property sortOrder 拖拽排序位置（v2026-07-27 新增，GROUP Tab 拖拽持久化用）
 *   - @ColumnInfo(defaultValue = "0") 与 MIGRATION_51_52 SQL DEFAULT 0 一致
 *   - sortOrder 上加 @Index 索引，与 MIGRATION_51_52 创建的 index_categories_sortOrder 一致
 *   - 见 .trae/rules/entity与migration同步检查.md
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["sortOrder"]), Index(value = ["isPinned"])]
)
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: Int,
    @ColumnInfo(defaultValue = "0")
    val isPinned: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0
)

/**
 * 分类类型常量
 */
object CategoryType {
    const val STUDY = 0
    const val WORK = 1
    const val LIFE = 2
    const val SPORT = 3
    const val CUSTOM = 4
    const val ENTERTAINMENT = 5
}

/**
 * 默认分类名称
 */
object DefaultCategoryName {
    const val STUDY = "学习"
    const val WORK = "工作"
    const val LIFE = "生活"
    const val SPORT = "运动"
    const val ENTERTAINMENT = "娱乐"
}
