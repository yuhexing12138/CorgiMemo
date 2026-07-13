package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.DeletedSpecialDateDao
import com.corgimemo.app.data.model.DeletedSpecialDate
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已删除特殊日期 Repository
 *
 * 镜像 DeletedTodoRepository 的接口设计，提供回收站的增删查改操作。
 */
@Singleton
class DeletedSpecialDateRepository @Inject constructor(
    private val deletedSpecialDateDao: DeletedSpecialDateDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /** 将日期移入回收站（软删除） */
    suspend fun insertDeletedDate(date: SpecialDate) = withContext(ioDispatcher) {
        deletedSpecialDateDao.insert(DeletedSpecialDate.fromSpecialDate(date))
    }

    /** 批量移入回收站 */
    suspend fun insertDeletedDates(dates: List<SpecialDate>) = withContext(ioDispatcher) {
        deletedSpecialDateDao.insertAll(dates.map { DeletedSpecialDate.fromSpecialDate(it) })
    }

    /** 获取所有已删除日期（Flow 形式，供 UI 订阅） */
    fun getAllDeletedDates(): Flow<List<DeletedSpecialDate>> =
        deletedSpecialDateDao.getAllDeletedDates()

    /** 获取所有已删除日期（阻塞形式） */
    suspend fun getAllDeletedDatesBlocking(): List<DeletedSpecialDate> = withContext(ioDispatcher) {
        deletedSpecialDateDao.getAllDeletedDatesBlocking()
    }

    /** 已删除日期数量（Flow 形式） */
    fun getDeletedCount(): Flow<Int> = deletedSpecialDateDao.getDeletedCount()

    /** 已删除日期数量（阻塞形式） */
    suspend fun getDeletedCountBlocking(): Int = withContext(ioDispatcher) {
        deletedSpecialDateDao.getDeletedCountBlocking()
    }

    /** 根据 ID 获取已删除日期（供恢复使用） */
    suspend fun getByIdBlocking(id: Long): DeletedSpecialDate? = withContext(ioDispatcher) {
        deletedSpecialDateDao.getDeletedDateById(id)
    }

    /** 永久删除单条记录 */
    suspend fun permanentlyDelete(id: Long) = withContext(ioDispatcher) {
        deletedSpecialDateDao.deleteById(id)
    }

    /** 永久删除所有记录 */
    suspend fun permanentlyDeleteAll() = withContext(ioDispatcher) {
        deletedSpecialDateDao.deleteAll()
    }

    /** 清理超过指定时间阈值的记录（30 天自动清理） */
    suspend fun cleanUpOldDeletedDates(threshold: Long) = withContext(ioDispatcher) {
        deletedSpecialDateDao.deleteOlderThan(threshold)
    }
}
