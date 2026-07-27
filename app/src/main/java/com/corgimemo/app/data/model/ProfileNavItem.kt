package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 个人快速导航项（v2026-07-27 新增，P8 Phase 5 实施）
 *
 * 原 ProfileQuickNavSection 硬编码 3 个 CategoryItem（统计/成就/设置），
 * 现改为数据驱动，从 DB 加载并支持长按拖拽排序。
 *
 * **id 设计**：使用稳定的字符串主键（而非自增 Int），便于跨数据库迁移保持
 * 一致性，也方便后续扩展新导航项时直接指定 id。
 *
 * @property id 主键（稳定标识，如 "stats" / "achievement" / "settings"）
 * @property icon emoji 图标
 * @property name 显示名
 * @property sortOrder 拖拽排序位置（v2026-07-27 P8 Phase 5 新增，PROFILE Tab 拖拽持久化用）
 */
@Entity(
    tableName = "profile_nav_items",
    indices = [Index(value = ["sortOrder"])]
)
data class ProfileNavItem(
    @PrimaryKey
    val id: String,
    val icon: String,
    val name: String,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0
)
