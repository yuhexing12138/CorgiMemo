package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.CategoryDao
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.DefaultCategoryName
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 分类数据仓库
 * 负责分类数据的 CRUD 操作和默认分类初始化
 *
 * v2026-07-29 改造：
 * - 取消"默认/自定义分组"区分，所有分组均支持修改/删除/置顶操作
 * - `initDefaultCategories` 仅作为首次启动时的种子数据初始化，不再带 `isDefault` 标记
 * - 原 `deleteCustomCategory(id)` 改名为 `deleteCategory(id)`，移除"是否默认分组"过滤
 * - 新增 `setCategoryPinned(id, isPinned)` 支持置顶分组
 */
@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * 初始化默认分类
     * 如果数据库中没有分类，则创建学习、工作、生活、娱乐、运动五个默认分类
     *
     * v2026-07-29 改造：取消 `isDefault` 字段后，5 个种子分类不再带特殊标记，
     * 与用户后续新建的分组完全等价（用户可任意修改/删除/置顶它们）。
     * 仅 `name + type` 仍由 [DefaultCategoryName] / [CategoryType] 提供，
     * 供首次启动时填充分组选择项。
     *
     * 幂等性：使用"不存在则插入"模式，多次调用安全
     */
    suspend fun initDefaultCategories() = withContext(ioDispatcher) {
        val existingCategories = categoryDao.getAllCategoriesList()
        if (existingCategories.isEmpty()) {
            val defaultCategories = listOf(
                Category(name = DefaultCategoryName.STUDY, type = CategoryType.STUDY),
                Category(name = DefaultCategoryName.WORK, type = CategoryType.WORK),
                Category(name = DefaultCategoryName.LIFE, type = CategoryType.LIFE),
                Category(name = DefaultCategoryName.SPORT, type = CategoryType.SPORT),
                Category(name = DefaultCategoryName.ENTERTAINMENT, type = CategoryType.ENTERTAINMENT)
            )
            categoryDao.insertAll(defaultCategories)
        } else {
            val hasSport = existingCategories.any { it.type == CategoryType.SPORT }
            if (!hasSport) {
                categoryDao.insert(Category(name = DefaultCategoryName.SPORT, type = CategoryType.SPORT))
            }
            // 兼容老用户：补齐"娱乐"分类
            val hasEntertainment = existingCategories.any { it.type == CategoryType.ENTERTAINMENT }
            if (!hasEntertainment) {
                categoryDao.insert(Category(name = DefaultCategoryName.ENTERTAINMENT, type = CategoryType.ENTERTAINMENT))
            }
        }
    }

    /**
     * 获取所有分类（Flow）
     */
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()

    /**
     * 获取所有分类（列表）
     */
    suspend fun getAllCategoriesList(): List<Category> = withContext(ioDispatcher) {
        categoryDao.getAllCategoriesList()
    }

    /**
     * 根据 ID 获取分类
     */
    suspend fun getCategoryById(id: Long): Category? = withContext(ioDispatcher) {
        categoryDao.getCategoryById(id)
    }

    /**
     * 根据类型获取分类
     */
    suspend fun getCategoryByType(type: Int): Category? = withContext(ioDispatcher) {
        categoryDao.getCategoryByType(type)
    }

    /**
     * 根据名称获取分类
     */
    suspend fun getCategoryByName(name: String): Category? = withContext(ioDispatcher) {
        categoryDao.getCategoryByName(name)
    }

    /**
     * 获取学习类分类
     */
    suspend fun getStudyCategory(): Category? = getCategoryByType(CategoryType.STUDY)

    /**
     * 获取工作类分类
     */
    suspend fun getWorkCategory(): Category? = getCategoryByType(CategoryType.WORK)

    /**
     * 获取生活类分类
     */
    suspend fun getLifeCategory(): Category? = getCategoryByType(CategoryType.LIFE)

    /**
     * 获取运动类分类
     */
    suspend fun getSportCategory(): Category? = getCategoryByType(CategoryType.SPORT)

    /**
     * 获取学习类分类 ID
     */
    suspend fun getStudyCategoryId(): Long? = withContext(ioDispatcher) {
        categoryDao.getCategoryIdByType(CategoryType.STUDY)
    }

    /**
     * 获取工作类分类 ID
     */
    suspend fun getWorkCategoryId(): Long? = withContext(ioDispatcher) {
        categoryDao.getCategoryIdByType(CategoryType.WORK)
    }

    /**
     * 插入分类
     */
    suspend fun insertCategory(category: Category): Long = withContext(ioDispatcher) {
        categoryDao.insert(category)
    }

    /**
     * 删除分类（v2026-07-29 改造）
     *
     * 原 `deleteCustomCategory(id)` 仅允许删除非默认分组（DAO 层带 `AND isDefault = 0` 过滤）。
     * 现已取消默认/自定义区分，所有分组都可被删除，故改名为 `deleteCategory`。
     *
     * @param id 要删除的分类 ID
     */
    suspend fun deleteCategory(id: Long) = withContext(ioDispatcher) {
        categoryDao.deleteCategory(id)
    }

    /**
     * 设置分类置顶状态（v2026-07-29 新增）
     *
     * 用于侧滑栏分组列表的置顶/取消置顶操作。
     * isPinned=true 的分组在 [getAllCategories] 中会排在前。
     *
     * @param id 分类 ID
     * @param isPinned true=置顶（排在前），false=取消置顶
     */
    suspend fun setCategoryPinned(id: Long, isPinned: Boolean) = withContext(ioDispatcher) {
        categoryDao.setPinned(id, isPinned)
    }

    /**
     * 批量更新分类排序（v2026-07-27 新增，P8 Phase 1 实施）
     *
     * GROUP Tab 拖拽后由 ViewModel 调用。
     * Room @Update 自动按主键匹配 + 事务，无需显式 WHERE。
     *
     * @param categories 排序后的分类列表（每项 sortOrder 字段为目标位置）
     *   调用方应预先 mapIndexed { idx, c -> c.copy(sortOrder = idx) } 重新分配 sortOrder
     */
    suspend fun batchUpdateSortOrder(categories: List<Category>) = withContext(ioDispatcher) {
        categoryDao.updateSortOrders(categories)
    }
}
