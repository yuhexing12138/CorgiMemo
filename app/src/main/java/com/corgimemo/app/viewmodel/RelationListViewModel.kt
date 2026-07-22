package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.repository.CardRelationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 关联列表 BottomSheet 专用 ViewModel（v2026-07-22 新增）
 *
 * **职责**：
 * - 加载某卡片某分组的完整关联列表（与 [com.corgimemo.app.data.repository.CardRelationRepository.getRelationsBlocking] 对接）
 * - 异步加载每个关联目标卡片的标题，存入 [_titles] 缓存
 * - 提供"解除关联"操作
 *
 * **为什么独立 ViewModel**：
 * - BottomSheet 在 Composable 树中是独立的 scope，需要自己的状态
 * - 与 HomeViewModel 隔离，避免 BottomSheet 的局部加载影响首页 relationCountMap
 * - 可被多个 Composable 复用（首页 todo 卡片、灵感卡片等）
 *
 * **使用模式**：
 * ```kotlin
 * val viewModel: RelationListViewModel = hiltViewModel()
 * LaunchedEffect(visible, sourceType, sourceId) {
 *     if (visible) viewModel.loadRelations(sourceType, sourceId, groupId)
 * }
 * val relations by viewModel.relations.collectAsState()
 * val titles by viewModel.titles.collectAsState()
 * ```
 */
@HiltViewModel
class RelationListViewModel @Inject constructor(
    private val cardRelationRepository: CardRelationRepository
) : ViewModel() {

    /** 已加载的关联列表 */
    private val _relations = MutableStateFlow<List<CardRelation>>(emptyList())
    val relations: StateFlow<List<CardRelation>> = _relations.asStateFlow()

    /** relationId → 关联目标卡片标题缓存（异步加载） */
    private val _titles = MutableStateFlow<Map<Long, String>>(emptyMap())
    val titles: StateFlow<Map<Long, String>> = _titles.asStateFlow()

    /** 是否正在加载（用于显示 Loading 态） */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * 加载某卡片某分组的所有关联
     *
     * @param sourceType 关联发起方类型 ("todo" | "inspiration" | "date")
     * @param sourceId   关联发起方 ID
     * @param groupId    分组 ID（todo 多分组时用，其他类型传 0）
     */
    fun loadRelations(sourceType: String, sourceId: Long, groupId: Int = 0) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val list = cardRelationRepository.getRelationsBlocking(sourceType, sourceId, groupId)
                _relations.value = list
                // 异步加载每个关联的标题
                val titleMap = mutableMapOf<Long, String>()
                list.forEach { relation ->
                    val title = cardRelationRepository.getCardTitle(relation.targetType, relation.targetId)
                    titleMap[relation.id] = title ?: "已删除"
                }
                _titles.value = titleMap
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 解除关联（双向删除）
     *
     * @param relationId 关联 ID（CardRelation.id）
     */
    fun unlink(relationId: Long) {
        viewModelScope.launch {
            cardRelationRepository.removeRelationById(relationId)
            // 重新加载列表（保证 UI 状态与 DB 一致）
            // 注意：调用方应在 dismiss BottomSheet 后通知外层 ViewModel 刷新
            // 这里仅更新本地 StateFlow
            _relations.value = _relations.value.filter { it.id != relationId }
            _titles.value = _titles.value.filterKeys { it != relationId }
        }
    }

    /**
     * 清空状态（BottomSheet 关闭时调用）
     */
    fun clear() {
        _relations.value = emptyList()
        _titles.value = emptyMap()
        _isLoading.value = false
    }
}
