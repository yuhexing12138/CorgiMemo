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

    /** 根据ID获取特殊日期（Flow形式，实时监听变化） */
    @Query("SELECT * FROM special_dates WHERE id = :id")
    fun getSpecialDateByIdFlow(id: Long): Flow<SpecialDate?>

    /** 搜索特殊日期（按标题模糊匹配） */
    @Query("SELECT * FROM special_dates WHERE title LIKE '%' || :query || '%' ORDER BY isPinned DESC, targetDate ASC")
    fun searchSpecialDates(query: String): Flow<List<SpecialDate>>

    /** 获取总数 */
    @Query("SELECT COUNT(*) FROM special_dates")
    suspend fun getCount(): Int

    /** 切换置顶状态 */
    @Query("UPDATE special_dates SET isPinned = CASE WHEN isPinned = 0 THEN 1 ELSE 0 END WHERE id = :id")
    suspend fun togglePin(id: Long)

    /** 获取所有未归档的特殊日期（主页用，按置顶和目标日期排序） */
    @Query("SELECT * FROM special_dates WHERE isArchived = 0 ORDER BY isPinned DESC, targetDate ASC")
    fun getActiveDates(): Flow<List<SpecialDate>>

    /** 获取所有已归档的特殊日期（未来"已归档"入口用） */
    @Query("SELECT * FROM special_dates WHERE isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedDates(): Flow<List<SpecialDate>>

    /** 获取所有未归档的特殊日期（阻塞方式，撤回快照用） */
    @Query("SELECT * FROM special_dates WHERE isArchived = 0 ORDER BY isPinned DESC, targetDate ASC")
    suspend fun getActiveDatesBlocking(): List<SpecialDate>

    /**
     * 设置归档状态（true=归档, false=恢复）
     * 同时更新 updatedAt 为当前时间
     */
    @Query("UPDATE special_dates SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: Long)

    /** 设置置顶状态（true=置顶, false=取消置顶） */
    @Query("UPDATE special_dates SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    /** 清除除指定 id 外所有卡片的置顶（保证单选置顶） */
    @Query("UPDATE special_dates SET isPinned = 0 WHERE id != :id AND isPinned = 1")
    suspend fun clearPinExcept(id: Long)
}
