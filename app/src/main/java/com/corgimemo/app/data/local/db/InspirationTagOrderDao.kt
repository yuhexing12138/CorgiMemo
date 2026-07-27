package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.corgimemo.app.data.model.InspirationTagOrder
import kotlinx.coroutines.flow.Flow

/**
 * 灵感标签排序 DAO（v2026-07-27 新增，P8 Phase 4 实施）
 *
 * 用于 INSPIRATION Tab 拖拽排序持久化。
 */
@Dao
interface InspirationTagOrderDao {

    /**
     * 获取所有标签的排序（按 sortOrder ASC）
     *
     * @return 已排序的 tagName 列表（空 Flow 表示表无数据，需要从 inspirations 聚合）
     */
    @Query("SELECT tagName FROM inspiration_tag_order ORDER BY sortOrder ASC")
    fun getOrderedTagNames(): Flow<List<String>>

    /**
     * 批量插入/替换排序（拖拽后调用）
     *
     * 使用 REPLACE 策略：
     * - 已有 tagName → 更新 sortOrder
     * - 新 tagName → 插入
     * - 不在本次列表中的旧 tagName → 保留（不清理，避免拖拽时丢失其他 tag 的排序）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(orders: List<InspirationTagOrder>)

    /**
     * 清空所有（用于种子重置场景）
     */
    @Query("DELETE FROM inspiration_tag_order")
    suspend fun clear()
}
