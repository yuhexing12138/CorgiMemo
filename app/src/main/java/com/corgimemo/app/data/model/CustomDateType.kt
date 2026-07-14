package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 自定义日期类型实体
 * 用于用户自定义的日期分类（如"宠物生日"、"旅行纪念日"等）
 *
 * @property id 自增主键，用作 SpecialDate.category 中 "CUSTOM:<id>" 的标识
 * @property name 类型名称（用户可修改）
 * @property emoji 类型图标 emoji，添加时可选，默认 📅
 * @property sortOrder 排序位置，侧滑栏中按此字段 ASC 排列
 * @property createdAt 创建时间戳
 */
@Entity(tableName = "custom_date_types")
data class CustomDateType(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "📅")
    val emoji: String = "📅",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
