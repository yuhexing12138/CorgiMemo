package com.corgimemo.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.corgimemo.app.data.local.db.TodoDao
import com.corgimemo.app.data.model.TodoItem

/**
 * 待办列表分页数据源
 *
 * 从 Room 数据库按页加载数据，支持按状态过滤和排序。
 * 适用于大数据量场景（100+ 条待办）。
 *
 * **分页策略**：
 * - 每页加载 20 条数据
 * - 支持向前/向后翻页
 * - 缓存最近 3 页数据（共 60 条）
 *
 * @param todoDao 待办数据访问对象
 * @param filterStatus 过滤状态（全部/待办/已完成）
 */
class TodoPagingSource(
    private val todoDao: TodoDao,
    private val filterStatus: Int = 0  // 0=全部, 1=待办, 2=已完成
) : PagingSource<Int, TodoItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, TodoItem> {
        return try {
            // 计算当前页码（key 为 null 时从第 0 页开始）
            val page = params.key ?: 0
            // 每页加载的数据量
            val limit = params.loadSize

            // 从 Room 加载当前页数据（根据过滤状态选择查询方法）
            val todos = when (filterStatus) {
                1 -> todoDao.getPendingTodosPaging(limit, page * limit)   // 仅待办
                2 -> todoDao.getCompletedTodosPaging(limit, page * limit) // 仅已完成
                else -> todoDao.getTodosPaging(limit, page * limit)        // 全部
            }

            // 返回分页结果
            LoadResult.Page(
                data = todos,
                prevKey = if (page == 0) null else page - 1,       // 上一页（第一页无上一页）
                nextKey = if (todos.size < limit) null else page + 1  // 下一页（数据不足一页时无下一页）
            )
        } catch (e: Exception) {
            // 查询失败返回错误状态
            LoadResult.Error(e)
        }
    }

    /**
     * 获取刷新键
     *
     * 当数据需要刷新时，Paging 库会调用此方法确定刷新的起始位置。
     * 返回距离锚点位置最近的页码。
     */
    override fun getRefreshKey(state: PagingState<Int, TodoItem>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}
