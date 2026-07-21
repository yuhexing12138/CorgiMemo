package com.corgimemo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.CardDetail
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.SpecialDateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 日期详情页 ViewModel
 *
 * 负责管理详情页的状态：日期列表加载、置顶、归档、删除、备注更新、样式/颜色更新等。
 * 支持左右滑动切换不同日期。
 *
 * @param savedStateHandle 保存状态句柄，用于获取导航参数 dateId
 * @param repository 特殊日期仓库
 */
@HiltViewModel
class SpecialDateDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpecialDateRepository,
    /** v2026-07-22 新增：关联管理仓库（日期页关联功能） */
    private val cardRelationRepository: CardRelationRepository
) : ViewModel() {

    private val initialDateId: Long = savedStateHandle["dateId"] ?: 0L

    private val _allDates = MutableStateFlow<List<SpecialDate>>(emptyList())
    val allDates: StateFlow<List<SpecialDate>> = _allDates.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ========== v2026-07-22 新增：关联管理状态 ==========
    /** 关联列表（按当前日期 id 加载） */
    private val _relations = MutableStateFlow<List<CardRelation>>(emptyList())
    val relations: StateFlow<List<CardRelation>> = _relations.asStateFlow()

    /** 关联ID → 标题的映射（异步加载并缓存，已删除卡片显示"已删除"） */
    private val _relationTitles = MutableStateFlow<Map<Long, String>>(emptyMap())
    val relationTitles: StateFlow<Map<Long, String>> = _relationTitles.asStateFlow()

    /** 当前预览卡片的详情（供 LinkedCardPreviewDialog 展示） */
    private val _cardDetail = MutableStateFlow<CardDetail?>(null)
    val cardDetail: StateFlow<CardDetail?> = _cardDetail.asStateFlow()

    /** 卡片详情加载中标志 */
    private val _cardDetailLoading = MutableStateFlow(false)
    val cardDetailLoading: StateFlow<Boolean> = _cardDetailLoading.asStateFlow()

    init {
        loadAllDates()
    }

    /**
     * 加载所有未归档日期列表（用于左右滑动切换）
     */
    private fun loadAllDates() {
        viewModelScope.launch {
            repository.getActiveDates().collect { dates ->
                _allDates.value = dates
                _isLoading.value = false
            }
        }
    }

    /**
     * 切换置顶状态（指定日期ID）
     * 置顶为单选模式，自动取消其他日期的置顶
     *
     * @param id 日期ID
     */
    fun togglePinForDate(id: Long) {
        viewModelScope.launch {
            val date = _allDates.value.find { it.id == id } ?: return@launch
            if (date.isPinned) {
                repository.unpinDate(id)
            } else {
                repository.pinDate(id)
            }
        }
    }

    /**
     * 归档日期（指定日期ID）
     *
     * @param id 日期ID
     */
    fun archiveDate(id: Long) {
        viewModelScope.launch {
            repository.archive(id)
        }
    }

    /**
     * 删除日期
     *
     * @param date 要删除的日期实体
     */
    fun deleteDate(date: SpecialDate) {
        viewModelScope.launch {
            repository.delete(date)
        }
    }

    /**
     * 更新备注（指定日期ID）
     *
     * @param id 日期ID
     * @param content 新的备注内容
     */
    fun updateContentForDate(id: Long, content: String) {
        viewModelScope.launch {
            val date = _allDates.value.find { it.id == id } ?: return@launch
            repository.update(date.copy(content = content, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * 更新卡片样式（指定日期ID）
     *
     * @param id 日期ID
     * @param cardStyle 新的卡片样式
     */
    fun updateCardStyleForDate(id: Long, cardStyle: String) {
        viewModelScope.launch {
            val date = _allDates.value.find { it.id == id } ?: return@launch
            val updatedDate = date.copy(cardStyle = cardStyle, updatedAt = System.currentTimeMillis())
            // 先更新本地状态，实现即时UI响应
            _allDates.value = _allDates.value.map { if (it.id == id) updatedDate else it }
            // 再写入数据库
            repository.update(updatedDate)
        }
    }

    /**
     * 更新卡片颜色（指定日期ID）
     *
     * @param id 日期ID
     * @param cardColor 新的卡片颜色
     */
    fun updateCardColorForDate(id: Long, cardColor: String) {
        viewModelScope.launch {
            val date = _allDates.value.find { it.id == id } ?: return@launch
            val updatedDate = date.copy(cardColor = cardColor, updatedAt = System.currentTimeMillis())
            // 先更新本地状态，实现即时UI响应
            _allDates.value = _allDates.value.map { if (it.id == id) updatedDate else it }
            // 再写入数据库
            repository.update(updatedDate)
        }
    }

    // ==================== v2026-07-22 新增：关联管理方法 ====================

    /**
     * 加载指定日期的关联列表
     *
     * HorizontalPager 切换 page 时调用，按当前日期 id 加载关联。
     * 加载完成后自动刷新标题缓存。
     *
     * @param dateId 当前日期ID
     */
    fun loadRelations(dateId: Long) {
        viewModelScope.launch {
            val list = cardRelationRepository.getRelationsBlocking("date", dateId, 0)
            _relations.value = list
            refreshRelationTitles()
        }
    }

    /**
     * 刷新关联标题缓存
     *
     * 遍历当前关联列表，对未缓存的关联异步加载目标卡片标题。
     * 已删除卡片显示"已删除"。
     */
    private fun refreshRelationTitles() {
        viewModelScope.launch {
            val allRelations = _relations.value
            val existingTitles = _relationTitles.value
            val newTitles = mutableMapOf<Long, String>()
            allRelations.forEach { relation ->
                if (relation.id !in existingTitles) {
                    val title = cardRelationRepository.getCardTitle(relation.targetType, relation.targetId)
                    newTitles[relation.id] = title ?: "已删除"
                }
            }
            if (newTitles.isNotEmpty()) {
                _relationTitles.value = existingTitles + newTitles
            }
        }
    }

    /**
     * 加载卡片详情（供 LinkedCardPreviewDialog 展示）
     *
     * @param cardType 卡片类型（todo/inspiration/date）
     * @param cardId 卡片ID
     */
    fun loadCardDetail(cardType: String, cardId: Long) {
        viewModelScope.launch {
            _cardDetailLoading.value = true
            _cardDetail.value = null
            val detail = cardRelationRepository.loadCardDetail(cardType, cardId)
            _cardDetail.value = detail
            _cardDetailLoading.value = false
        }
    }

    /**
     * 清空卡片详情状态（关闭预览 Dialog 时调用）
     */
    fun clearCardDetail() {
        _cardDetail.value = null
        _cardDetailLoading.value = false
    }

    /**
     * 批量添加关联（双向关联：自动插入 A→B 和 B→A）
     *
     * @param dateId 当前日期ID
     * @param cards 待添加的卡片列表（类型 + ID）
     */
    fun addRelations(dateId: Long, cards: List<Pair<String, Long>>) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val currentList = _relations.value.toMutableList()
            val newTitles = mutableMapOf<Long, String>()
            var addedCount = 0
            cards.forEach { (targetType, targetId) ->
                val existsInMemory = currentList.any {
                    it.targetType == targetType && it.targetId == targetId
                }
                if (existsInMemory) return@forEach
                val relation = CardRelation(
                    sourceType = "date",
                    sourceId = dateId,
                    groupId = 0,
                    targetType = targetType,
                    targetId = targetId
                )
                val result = cardRelationRepository.addRelation(relation)
                if (result > 0) {
                    currentList.add(relation.copy(id = result))
                    val title = cardRelationRepository.getCardTitle(targetType, targetId)
                    newTitles[result] = title ?: "已删除"
                    addedCount++
                }
            }
            if (addedCount > 0) {
                val distinctList = currentList.distinctBy { "${it.targetType}_${it.targetId}" }
                _relations.value = distinctList
                if (newTitles.isNotEmpty()) {
                    _relationTitles.value = _relationTitles.value + newTitles
                }
            }
        }
    }

    /**
     * 删除关联（同步清理标题缓存）
     *
     * @param relationId 关联ID
     */
    fun deleteRelation(relationId: Long) {
        viewModelScope.launch {
            cardRelationRepository.removeRelationById(relationId)
            _relations.value = _relations.value.filter { it.id != relationId }
            _relationTitles.value = _relationTitles.value - relationId
        }
    }

    /**
     * 搜索卡片（供 RelationPickerBottomSheet 使用）
     *
     * @param query 搜索关键词
     * @param callback 结果回调
     */
    fun searchCards(query: String, callback: (List<CardSearchResult>) -> Unit) {
        viewModelScope.launch {
            val results = cardRelationRepository.searchCards(query)
            callback(results)
        }
    }
}
