package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.corgimemo.app.data.model.SpecialDate
import kotlinx.coroutines.flow.Flow

/**
 * 特殊日期数据访问对象
 * 提供特殊日期的增删改查操作
 */
@Dao
interface SpecialDateDao {

    /** 新增特殊日期 */
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insert(specialDate: SpecialDate): Long

    /** 更新特殊日期 */
    @Update
    suspend fun update(specialDate: SpecialDate)

    /** 删除特殊日期 */
    @Delete
    suspend fun delete(specialDate: SpecialDate)

    /** 根据ID删除 */
    @Query("DELETE FROM special_dates WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 清空所有特殊日期 */
    @Query("DELETE FROM special_dates")
    suspend fun deleteAll()

    /** 获取所有特殊日期（按置顶和目标日期排序） */
    @Query("SELECT * FROM special_dates ORDER BY isPinned DESC, targetDate ASC")
    fun getAllSpecialDates(): Flow<List<SpecialDate>>

    /** 获取所有特殊日期（阻塞方式） */
    @Query("SELECT * FROM special_dates ORDER BY isPinned DESC, targetDate ASC")
    suspend fun getAllSpecialDatesBlocking(): List<SpecialDate>

    /** 根据ID获取特殊日期 */
    @Query("SELECT * FROM special_dates WHERE id = :id")
    suspend fun getSpecialDateById(id: Long): SpecialDate?

    /** 搜索特殊日期（按标题模糊匹配） */
    @Query("SELECT * FROM special_dates WHERE title LIKE '%' || :query || '%' ORDER BY isPinned DESC, targetDate ASC")
    fun searchSpecialDates(query: String): Flow<List<SpecialDate>>

    /** 获取总数 */
    @Query("SELECT COUNT(*) FROM special_dates")
    suspend fun getCount(): Int

    /** 切换置顶状态 */
    @Query("UPDATE special_dates SET isPinned = CASE WHEN isPinned = 0 THEN 1 ELSE 0 END WHERE id = :id")
    suspend fun togglePin(id: Long)
}
