package com.corgimemo.app.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.corgimemo.app.data.local.db.InspirationDao
import com.corgimemo.app.data.model.Inspiration

/**
 * 灵感列表分页数据源
 *
 * 从 Room 数据库按页加载数据，
 * 适用于大数据量场景（100+ 条灵感）。
 *
 * **分页策略**：
 * - 每页加载 20 条数据
 * - 按置顶+时间排序（与全量加载一致）
 * - 缓存最近 5 页数据（共 100 条）
 *
 * @param inspirationDao 灵感数据访问对象
 */
class InspirationPagingSource(
    private val inspirationDao: InspirationDao
) : PagingSource<Int, Inspiration>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Inspiration> {
        return try {
            // 计算当前页码
            val page = params.key ?: 0
            // 每页加载的数据量
            val limit = params.loadSize

            // 从 Room 加载当前页数据（已按置顶+时间排序）
            val inspirations = inspirationDao.getInspirationsPaging(limit, page * limit)

            // 返回分页结果
            LoadResult.Page(
                data = inspirations,
                prevKey = if (page == 0) null else page - 1,
                nextKey = if (inspirations.size < limit) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    /**
     * 获取刷新键
     */
    override fun getRefreshKey(state: PagingState<Int, Inspiration>): Int? {
        return state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
    }
}
