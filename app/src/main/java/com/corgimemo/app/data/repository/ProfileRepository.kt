package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.ProfileNavItemDao
import com.corgimemo.app.data.model.ProfileNavItem
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 个人快速导航 Repository（v2026-07-27 新增，P8 Phase 5 实施）
 *
 * 封装 `profile_nav_items` 表的 CRUD + seed + 排序更新逻辑。
 *
 * **数据特征**：
 * - 行数极少（3 个默认项 + 后续扩展 < 10 项）
 * - 全部走 Room 单事务批量操作
 * - 不需要 search / 分页 / 缓存
 *
 * **调用方**：
 * - [com.corgimemo.app.viewmodel.ProfileViewModel] 是唯一调用方
 * - UI 层（ProfileQuickNavSection）通过 ViewModel 的 StateFlow 订阅数据
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val dao: ProfileNavItemDao
) {
    /**
     * 首次启动 seed 默认 3 个 nav item
     *
     * **幂等性**：仅当 DB 为空时（count == 0）插入，避免重复。
     * - 已有用户迁移后不会重新 seed（保留可能的自定义项）
     * - 新用户首次启动时由 ProfileViewModel.init 触发
     *
     * **扩展指南**：
     * 后续如需新增 nav item（如"帮助"、"关于"），只需在 listOf(...) 中追加，
     * 老用户升级后会自动 seed（因为 count == 0）。如想强制重新 seed 给老用户，
     * 可用 `adb shell pm clear` 清除数据。
     */
    suspend fun seedIfNeeded() {
        if (dao.count() == 0) {
            dao.insertAll(
                listOf(
                    ProfileNavItem(id = "stats", icon = "📊", name = "统计", sortOrder = 0),
                    ProfileNavItem(id = "achievement", icon = "🏆", name = "成就", sortOrder = 1),
                    ProfileNavItem(id = "settings", icon = "⚙️", name = "设置", sortOrder = 2)
                )
            )
        }
    }

    /**
     * 获取所有 nav item（按 sortOrder ASC 排序的响应式流）
     *
     * UI 层通过 collectAsState 订阅，数据变化（拖拽 / 增删）会自动重渲染。
     */
    fun getAllByOrder(): Flow<List<ProfileNavItem>> = dao.getAllByOrder()

    /**
     * 更新 nav item 拖拽顺序
     *
     * 调用方传入 UI 拖拽完成后的新列表，重新分配 sortOrder（0,1,2,...）
     * 后批量 REPLACE 写入。
     *
     * @param items 拖拽后的新 nav item 列表（每项 sortOrder 字段会被重写为目标位置）
     */
    suspend fun updateNavOrder(items: List<ProfileNavItem>) {
        val ordered = items.mapIndexed { idx, item -> item.copy(sortOrder = idx) }
        dao.updateSortOrders(ordered)
    }
}
