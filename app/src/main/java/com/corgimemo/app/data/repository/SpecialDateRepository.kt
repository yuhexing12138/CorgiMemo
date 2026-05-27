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
    private val relationDao: SpecialDateRelationDao
) {
    /** 获取所有特殊日期（响应式） */
    val allDates: Flow<List<SpecialDate>> = specialDateDao.getAllSpecialDates()

    /** 根据ID获取特殊日期 */
    suspend fun getById(id: Long): SpecialDate? = specialDateDao.getSpecialDateById(id)

    /** 新增特殊日期 */
    suspend fun insert(date: SpecialDate): Long = specialDateDao.insert(date)

    /** 更新特殊日期 */
    suspend fun update(date: SpecialDate) = specialDateDao.update(date)

    /** 删除特殊日期（级联删除关联） */
    suspend fun delete(date: SpecialDate) {
        relationDao.deleteBySpecialDateId(date.id)
        specialDateDao.delete(date)
    }

    /** 搜索特殊日期 */
    fun search(query: String): Flow<List<SpecialDate>> = specialDateDao.searchSpecialDates(query)

    /** 切换置顶 */
    suspend fun togglePin(id: Long) = specialDateDao.togglePin(id)

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
