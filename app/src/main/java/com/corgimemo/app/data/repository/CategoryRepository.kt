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
 */
@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    /**
     * 初始化默认分类
     * 如果数据库中没有分类，则创建学习、工作、生活、运动四个默认分类
     */
    suspend fun initDefaultCategories() = withContext(ioDispatcher) {
        val existingCategories = categoryDao.getAllCategoriesList()
        if (existingCategories.isEmpty()) {
            val defaultCategories = listOf(
                Category(name = DefaultCategoryName.STUDY, type = CategoryType.STUDY, isDefault = true),
                Category(name = DefaultCategoryName.WORK, type = CategoryType.WORK, isDefault = true),
                Category(name = DefaultCategoryName.LIFE, type = CategoryType.LIFE, isDefault = true),
                Category(name = DefaultCategoryName.SPORT, type = CategoryType.SPORT, isDefault = true)
            )
            categoryDao.insertAll(defaultCategories)
        } else {
            val hasSport = existingCategories.any { it.type == CategoryType.SPORT }
            if (!hasSport) {
                categoryDao.insert(Category(name = DefaultCategoryName.SPORT, type = CategoryType.SPORT, isDefault = true))
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
     * 删除自定义分类
     */
    suspend fun deleteCustomCategory(id: Long) = withContext(ioDispatcher) {
        categoryDao.deleteCustomCategory(id)
    }
}
