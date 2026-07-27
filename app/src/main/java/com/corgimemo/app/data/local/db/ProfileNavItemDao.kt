package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.ProfileNavItem
import kotlinx.coroutines.flow.Flow

/**
 * 个人快速导航 DAO（v2026-07-27 新增，P8 Phase 5 实施）
 *
 * PROFILE Tab 拖拽排序持久化用。
 *
 * **数据特征**：
 * - 行数极少（仅 3 个默认 nav item，后续扩展也是 < 10 项）
 * - 全部走 Room 单事务批量操作（@Update 列表 / @Insert OnConflict.REPLACE）
 * - 单次种子插入（count() == 0 时由 Repository.seedIfNeeded 触发）
 *
 * **索引策略**：
 * - sortOrder 是排序键，但行数 < 10，加索引意义不大
 * - 仍保留 `ORDER BY sortOrder ASC` 保证视觉顺序
 */
@Dao
interface ProfileNavItemDao {

    /**
     * 获取所有导航项（响应式流，按 sortOrder ASC 排序）
     */
    @Query("SELECT * FROM profile_nav_items ORDER BY sortOrder ASC")
    fun getAllByOrder(): Flow<List<ProfileNavItem>>

    /**
     * 批量插入/替换（首次启动 seed 用）
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ProfileNavItem>)

    /**
     * 批量更新（拖拽后批量写入新 sortOrder）
     *
     * Room 自动按主键匹配，无需显式 WHERE。
     */
    @Update
    suspend fun updateSortOrders(items: List<ProfileNavItem>)

    /**
     * 当前行数（用于 seedIfNeeded 幂等判断）
     */
    @Query("SELECT COUNT(*) FROM profile_nav_items")
    suspend fun count(): Int
}
