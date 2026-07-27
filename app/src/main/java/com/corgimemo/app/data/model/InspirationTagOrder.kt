package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 灵感标签拖拽排序表（v2026-07-27 新增，P8 Phase 4 实施）
 *
 * **目的**：保存 INSPIRATION Tab 侧滑栏标签拖拽后的自定义顺序。
 *
 * **为什么需要独立表**：
 * - 灵感数据本身没有 `tags` 字段存储结构化标签列表（标签是聚合自 inspirations.tags 字段）
 * - 跨多个 inspirations 的同名 tag 共享同一 sortOrder
 * - 用独立表存储全局 tag 排序，可避免每次都聚合 + 排序
 *
 * **数据生命周期**：
 * - App 启动时自动 seed 当前所有已知 tag（首次启动）
 * - 拖拽后立即更新
 * - 标签被删除时（所有引用此 tag 的灵感都被删除），保留孤立记录无害，下次 seed 自动清理
 *
 * @property tagName 标签名（主键，关联 inspirations.tags 字符串字段）
 * @property sortOrder 排序位置（ASC，0 = 最前）
 */
@Entity(
    tableName = "inspiration_tag_order",
    indices = [Index(value = ["sortOrder"])]
)
data class InspirationTagOrder(
    @PrimaryKey
    val tagName: String,
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0
)
