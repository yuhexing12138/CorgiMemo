package com.corgimemo.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.CardDetail
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CardSearchResult
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.SpecialDateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 日期快速创建/编辑页 ViewModel
 *
 * 负责管理创建/编辑页的数据加载和保存操作。
 *
 * @param repository 特殊日期仓库
 */
@HiltViewModel
class SpecialDateQuickCreateViewModel @Inject constructor(
    private val repository: SpecialDateRepository,
    /** v2026-07-22 新增：关联管理仓库（日期新建/编辑页关联功能） */
    private val cardRelationRepository: CardRelationRepository
) : ViewModel() {

    private val _loadedDate = MutableStateFlow<SpecialDate?>(null)
    val loadedDate: StateFlow<SpecialDate?> = _loadedDate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    // ========== v2026-07-22 新增：关联管理状态 ==========
    /** 关联列表（编辑模式下按当前日期 id 加载） */
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

    /**
     * 自定义日期类型列表（与侧滑栏、数据统计页共享同一数据源）
     *
     * 用于类型选择弹窗（DateTypePickerBottomSheet）展示已有自定义类型，
     * 确保新建/编辑日期时可以选择侧滑栏中添加的自定义类型。
     */
    val customDateTypes: StateFlow<List<CustomDateType>> = repository.allCustomDateTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 新建自定义日期类型
     *
     * 在新建/编辑日期页中通过"自定义"输入创建新类型时调用。
     * 创建后返回类型 ID，调用方可用于构建 "CUSTOM:<id>" 存储格式。
     *
     * @param name 类型名称
     * @param emoji 类型 emoji（默认 📅）
     * @return 新建类型的 ID
     */
    suspend fun addCustomType(name: String, emoji: String = "📅"): Long {
        return repository.insertCustomDateType(name, emoji)
    }

    /**
     * 根据 ID 获取自定义类型
     *
     * 用于编辑模式下加载日期时，解析 "CUSTOM:<id>" 格式获取类型名称和 emoji。
     *
     * @param id 自定义类型 ID
     * @return 对应的 CustomDateType，不存在返回 null
     */
    fun getCustomTypeById(id: Long): CustomDateType? {
        return customDateTypes.value.find { it.id == id }
    }

    /**
     * 根据ID加载日期数据（编辑模式）
     *
     * @param id 日期ID
     */
    fun loadDate(id: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            val date = repository.getById(id)
            _loadedDate.value = date
            _isLoading.value = false
            // v2026-07-22 新增：编辑模式下加载日期后自动加载关联
            if (date != null && date.id > 0L) {
                val list = cardRelationRepository.getRelationsBlocking("date", date.id, 0)
                _relations.value = list
                refreshRelationTitles()
            }
        }
    }

    /**
     * 更新日期（编辑模式保存）
     *
     * @param date 更新后的日期实体
     */
    fun updateDate(date: SpecialDate) {
        viewModelScope.launch {
            _saveState.value = SaveState.Saving
            try {
                repository.update(date)
                _saveState.value = SaveState.Success
            } catch (e: Exception) {
                _saveState.value = SaveState.Error(e.message ?: "保存失败")
            }
        }
    }

    /**
     * 重置保存状态
     */
    fun resetSaveState() {
        _saveState.value = SaveState.Idle
    }

    // ==================== v2026-07-22 新增：关联管理方法 ====================

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
     * 编辑模式下使用 loadedDate.id 作为 sourceId；
     * 新建模式下 sourceId=0（需在保存日期后迁移关联，暂与灵感编辑页行为一致）。
     *
     * @param cards 待添加的卡片列表（类型 + ID）
     */
    fun addRelations(cards: List<Pair<String, Long>>) {
        if (cards.isEmpty()) return
        viewModelScope.launch {
            val dateId = _loadedDate.value?.id ?: 0L
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
