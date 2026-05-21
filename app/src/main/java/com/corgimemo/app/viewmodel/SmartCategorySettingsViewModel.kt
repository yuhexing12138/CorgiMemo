package com.corgimemo.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryKeyword
import com.corgimemo.app.data.model.MatchType
import com.corgimemo.app.data.repository.CategoryKeywordRepository
import com.corgimemo.app.data.repository.CategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SmartCategorySettingsViewModel @Inject constructor(
    private val categoryKeywordRepository: CategoryKeywordRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _keywordsByCategory = MutableStateFlow<Map<Int, List<CategoryKeyword>>>(emptyMap())
    val keywordsByCategory: StateFlow<Map<Int, List<CategoryKeyword>>> = _keywordsByCategory.asStateFlow()

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _categories.value = categoryRepository.getAllCategoriesList()
            _keywordsByCategory.value = categoryKeywordRepository.getAllGrouped()
        }
    }

    fun addKeyword(keyword: String, categoryType: Int, matchType: MatchType = MatchType.FUZZY) {
        viewModelScope.launch {
            categoryKeywordRepository.addUserKeyword(keyword, categoryType, matchType)
            _keywordsByCategory.value = categoryKeywordRepository.getAllGrouped()
        }
    }

    fun deleteKeyword(id: Long) {
        viewModelScope.launch {
            categoryKeywordRepository.deleteUserKeyword(id)
            _keywordsByCategory.value = categoryKeywordRepository.getAllGrouped()
        }
    }

    fun updateKeywordCategory(id: Long, newCategoryType: Int) {
        viewModelScope.launch {
            categoryKeywordRepository.updateUserKeyword(id, newCategoryType)
            _keywordsByCategory.value = categoryKeywordRepository.getAllGrouped()
        }
    }

    fun updateKeywordText(id: Long, newText: String) {
        viewModelScope.launch {
            categoryKeywordRepository.updateUserKeywordText(id, newText)
            _keywordsByCategory.value = categoryKeywordRepository.getAllGrouped()
        }
    }

    /**
     * 检查关键词是否重复
     *
     * @param keyword 要检查的关键词
     * @param excludeId 排除的关键词ID（编辑时排除自身）
     * @return 如果重复返回重复的关键词信息，否则返回 null
     */
    fun checkDuplicateKeyword(keyword: String, excludeId: Long? = null): CategoryKeyword? {
        val allKeywords = _keywordsByCategory.value.values.flatten()
        return allKeywords.find { keywordItem -> 
            keywordItem.keyword.equals(keyword, ignoreCase = true) && keywordItem.id != excludeId 
        }
    }

    /**
     * 获取所有关键词列表（用于UI层检查）
     */
    fun getAllKeywords(): List<CategoryKeyword> {
        return _keywordsByCategory.value.values.flatten()
    }
}
