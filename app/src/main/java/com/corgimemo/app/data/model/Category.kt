package com.corgimemo.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 任务分类实体
 * 用于区分不同类型的任务（学习、工作、生活等）
 *
 * @property id 分类 ID
 * @property name 分类名称
 * @property type 分类类型：0=学习，1=工作，2=生活，3=自定义
 * @property isDefault 是否为默认分类
 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: Int,
    val isDefault: Boolean = false
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
