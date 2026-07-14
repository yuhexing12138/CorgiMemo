package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.SpecialDateDao
import com.corgimemo.app.data.local.db.SpecialDateRelationDao
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.model.SpecialDateRelation
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 特殊日期仓库
 * 封装特殊日期和关联关系的数据访问逻辑
 */
@Singleton
class SpecialDateRepository @Inject constructor(
    private val specialDateDao: SpecialDateDao,
    private val relationDao: SpecialDateRelationDao,
    private val cardRelationRepository: CardRelationRepository
) {
    /** 获取所有特殊日期（响应式） */
    val allDates: Flow<List<SpecialDate>> = specialDateDao.getAllSpecialDates()

    /** 根据ID获取特殊日期 */
    suspend fun getById(id: Long): SpecialDate? = specialDateDao.getSpecialDateById(id)

    /** 根据ID获取特殊日期（Flow形式，实时监听变化） */
    fun getByIdFlow(id: Long): Flow<SpecialDate?> = specialDateDao.getSpecialDateByIdFlow(id)

    /** 新增特殊日期 */
    suspend fun insert(date: SpecialDate): Long = specialDateDao.insert(date)

    /** 更新特殊日期 */
    suspend fun update(date: SpecialDate) = specialDateDao.update(date)

    /** 删除特殊日期（级联删除关联） */
    suspend fun delete(date: SpecialDate) {
        relationDao.deleteBySpecialDateId(date.id)
        cardRelationRepository.removeAllForCard("date", date.id)
        specialDateDao.delete(date)
    }

    /** 搜索特殊日期 */
    fun search(query: String): Flow<List<SpecialDate>> = specialDateDao.searchSpecialDates(query)

    /** 切换置顶 */
    suspend fun togglePin(id: Long) = specialDateDao.togglePin(id)

    /** 获取所有未归档的特殊日期（响应式，主页用） */
    fun getActiveDates(): Flow<List<SpecialDate>> = specialDateDao.getActiveDates()

    /** 获取所有已归档的特殊日期（响应式，未来入口用） */
    fun getArchivedDates(): Flow<List<SpecialDate>> = specialDateDao.getArchivedDates()

    /** 获取所有未归档的特殊日期（阻塞方式，撤回快照用） */
    suspend fun getActiveDatesBlocking(): List<SpecialDate> = specialDateDao.getActiveDatesBlocking()

    /** 获取所有特殊日期（阻塞方式，含已归档，统计页用） */
    suspend fun getAllSpecialDatesBlocking(): List<SpecialDate> =
        specialDateDao.getAllSpecialDatesBlocking()

    /**
     * 归档特殊日期（软删除）
     * - 不动 isPinned（归档态置顶信息保留）
     * - 不动关联关系（恢复后仍可访问）
     */
    suspend fun archive(id: Long) {
        specialDateDao.setArchived(id, true, System.currentTimeMillis())
    }

    /** 恢复已归档的特殊日期 */
    suspend fun unarchive(id: Long) {
        specialDateDao.setArchived(id, false, System.currentTimeMillis())
    }

    /**
     * 置顶特殊日期（单选：自动取消其它卡片的置顶）
     * 调用顺序：先 clearPinExcept 再 setPinned，保证事务外层调用不冲突
     */
    suspend fun pinDate(id: Long) {
        specialDateDao.clearPinExcept(id)
        specialDateDao.setPinned(id, true)
    }

    /** 取消置顶（无需 clearPinExcept） */
    suspend fun unpinDate(id: Long) {
        specialDateDao.setPinned(id, false)
    }

    /** 获取某日期的关联列表（响应式） */
    fun getRelations(dateId: Long): Flow<List<SpecialDateRelation>> =
        relationDao.getRelationsBySpecialDateId(dateId)

    /** 获取某日期的关联列表（阻塞） */
    suspend fun getRelationsBlocking(dateId: Long): List<SpecialDateRelation> =
        relationDao.getRelationsBySpecialDateIdBlocking(dateId)

    /** 添加关联（去重） */
    suspend fun addRelation(relation: SpecialDateRelation) {
        val exists = relationDao.isRelationExist(
            relation.specialDateId,
            relation.targetType,
            relation.targetId
        )
        if (!exists) {
            relationDao.insert(relation)
        }
    }

    /** 批量添加关联（去重） */
    suspend fun addRelations(relations: List<SpecialDateRelation>) {
        relations.forEach { addRelation(it) }
    }

    /** 移除指定关联 */
    suspend fun removeRelation(specialDateId: Long, targetType: String, targetId: Long) =
        relationDao.deleteRelation(specialDateId, targetType, targetId)
}
