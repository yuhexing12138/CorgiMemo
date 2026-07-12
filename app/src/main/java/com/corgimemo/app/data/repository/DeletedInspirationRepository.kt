package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.DeletedInspirationDao
import com.corgimemo.app.data.model.DeletedInspiration
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletedInspirationRepository @Inject constructor(
    private val deletedInspirationDao: DeletedInspirationDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    /** 将灵感插入回收站（软删除） */
    suspend fun insertDeletedInspiration(inspiration: Inspiration) = withContext(ioDispatcher) {
        deletedInspirationDao.insert(DeletedInspiration.fromInspiration(inspiration))
    }

    /** 批量插入回收站 */
    suspend fun insertDeletedInspirations(inspirations: List<Inspiration>) = withContext(ioDispatcher) {
        deletedInspirationDao.insertAll(inspirations.map { DeletedInspiration.fromInspiration(it) })
    }

    fun getAllDeletedInspirations(): Flow<List<DeletedInspiration>> =
        deletedInspirationDao.getAllDeletedInspirations()

    suspend fun getAllDeletedInspirationsBlocking(): List<DeletedInspiration> = withContext(ioDispatcher) {
        deletedInspirationDao.getAllDeletedInspirationsBlocking()
    }

    fun getDeletedCount(): Flow<Int> = deletedInspirationDao.getDeletedCount()

    suspend fun getDeletedCountBlocking(): Int = withContext(ioDispatcher) {
        deletedInspirationDao.getDeletedCountBlocking()
    }

    /** 根据 ID 获取回收站中的灵感 */
    suspend fun getByIdBlocking(id: Long): DeletedInspiration? = withContext(ioDispatcher) {
        deletedInspirationDao.getDeletedInspirationById(id)
    }

    /** 永久删除单条 */
    suspend fun permanentlyDelete(id: Long) = withContext(ioDispatcher) {
        deletedInspirationDao.deleteById(id)
    }

    /** 清空回收站 */
    suspend fun permanentlyDeleteAll() = withContext(ioDispatcher) {
        deletedInspirationDao.deleteAll()
    }

    /** 清理超过阈值的记录（30天自动清理） */
    suspend fun cleanUpOldDeletedInspirations(threshold: Long) = withContext(ioDispatcher) {
        deletedInspirationDao.deleteOlderThan(threshold)
    }
}
