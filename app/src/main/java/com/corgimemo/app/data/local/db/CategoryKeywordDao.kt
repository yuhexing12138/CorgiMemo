package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface CategoryKeywordDao {

    @Query("SELECT * FROM category_keywords")
    suspend fun getAll(): List<CategoryKeywordEntity>

    @Query("SELECT * FROM category_keywords WHERE isUserDefined = 1")
    suspend fun getUserDefined(): List<CategoryKeywordEntity>

    @Query("SELECT * FROM category_keywords WHERE categoryType = :categoryType")
    suspend fun getByCategoryType(categoryType: Int): List<CategoryKeywordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(keyword: CategoryKeywordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(keywords: List<CategoryKeywordEntity>)

    @Update
    suspend fun update(keyword: CategoryKeywordEntity)

    @Query("DELETE FROM category_keywords WHERE id = :id AND isUserDefined = 1")
    suspend fun deleteUserDefined(id: Long): Int

    @Query("DELETE FROM category_keywords WHERE keyword = :keyword AND isUserDefined = 1")
    suspend fun deleteUserDefinedByKeyword(keyword: String): Int

    @Query("SELECT COUNT(*) FROM category_keywords")
    suspend fun getCount(): Int
}
