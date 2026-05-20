package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.corgimemo.app.data.model.Category
import kotlinx.coroutines.flow.Flow

/**
 * 分类数据访问接口
 */
@Dao
interface CategoryDao {

    @Insert
    suspend fun insert(category: Category): Long

    @Insert
    suspend fun insertAll(categories: List<Category>)

    @Query("SELECT * FROM categories ORDER BY id ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories ORDER BY id ASC")
    suspend fun getAllCategoriesList(): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Long): Category?

    @Query("SELECT * FROM categories WHERE type = :type LIMIT 1")
    suspend fun getCategoryByType(type: Int): Category?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Query("SELECT id FROM categories WHERE type = :type LIMIT 1")
    suspend fun getCategoryIdByType(type: Int): Long?

    @Query("DELETE FROM categories WHERE id = :id AND isDefault = 0")
    suspend fun deleteCustomCategory(id: Long)

    @Query("DELETE FROM categories")
    suspend fun deleteAll()
}
