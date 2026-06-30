package com.corgimemo.app.data.repository

import com.corgimemo.app.data.local.db.CategoryKeywordDao
import com.corgimemo.app.data.local.db.CategoryKeywordEntity
import com.corgimemo.app.data.model.CategoryKeyword
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.MatchType
import com.corgimemo.app.data.model.toDomainModel
import com.corgimemo.app.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryKeywordRepository @Inject constructor(
    private val categoryKeywordDao: CategoryKeywordDao,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {

    private val _keywordsFlow = MutableStateFlow<List<CategoryKeyword>>(emptyList())
    val keywordsFlow: StateFlow<List<CategoryKeyword>> = _keywordsFlow.asStateFlow()

    private val cacheInitialized = AtomicBoolean(false)

    private val presets: List<CategoryKeyword> = listOf(
        CategoryKeyword(
            keyword = "会议",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "开会",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "项目",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "讨论",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "报告",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "PPT",
            categoryType = CategoryType.WORK,
            matchType = MatchType.EXACT,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "汇报",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "需求",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "上线",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "发布",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "评审",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "周报",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "月报",
            categoryType = CategoryType.WORK,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "作业",
            categoryType = CategoryType.STUDY,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "考试",
            categoryType = CategoryType.STUDY,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "论文",
            categoryType = CategoryType.STUDY,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "复习",
            categoryType = CategoryType.STUDY,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "背单词",
            categoryType = CategoryType.STUDY,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "阅读",
            categoryType = CategoryType.STUDY,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "课程",
            categoryType = CategoryType.STUDY,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "实验",
            categoryType = CategoryType.STUDY,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "笔记",
            categoryType = CategoryType.STUDY,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "买",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "超市",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "快递",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "取件",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "缴费",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "挂号",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "体检",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "打扫",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "洗衣",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "做饭",
            categoryType = CategoryType.LIFE,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "跑步",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "健身",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "游泳",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "瑜伽",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "打球",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "锻炼",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "滑冰",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "滑雪",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "篮球",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "足球",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        ),
        CategoryKeyword(
            keyword = "羽毛球",
            categoryType = CategoryType.SPORT,
            matchType = MatchType.FUZZY,
            isUserDefined = false
        )
    )

    private suspend fun ensureCacheInitialized() {
        if (!cacheInitialized.get()) {
            val userKeywords = categoryKeywordDao.getUserDefined()
                .map { it.toDomainModel() }
            val allKeywords = presets + userKeywords
            _keywordsFlow.value = allKeywords
            cacheInitialized.set(true)
        }
    }

    fun ensureCacheInitializedBlocking() {
        if (!cacheInitialized.get()) {
            _keywordsFlow.value = presets
            cacheInitialized.set(true)
        }
    }

    fun getAllKeywordsSync(): List<CategoryKeyword> {
        ensureCacheInitializedBlocking()
        return _keywordsFlow.value
    }

    suspend fun getAllKeywords(): List<CategoryKeyword> {
        ensureCacheInitialized()
        return _keywordsFlow.value
    }

    suspend fun getUserDefinedGrouped(): Map<Int, List<CategoryKeyword>> {
        ensureCacheInitialized()
        return _keywordsFlow.value
            .filter { it.isUserDefined }
            .groupBy { it.categoryType }
    }

    suspend fun getAllGrouped(): Map<Int, List<CategoryKeyword>> {
        ensureCacheInitialized()
        return _keywordsFlow.value
            .groupBy { it.categoryType }
    }

    suspend fun addUserKeyword(
        keyword: String,
        categoryType: Int,
        matchType: MatchType = MatchType.FUZZY
    ) {
        ensureCacheInitialized()

        val entity = CategoryKeywordEntity(
            keyword = keyword,
            categoryType = categoryType,
            matchType = if (matchType == MatchType.EXACT) 0 else 1,
            isUserDefined = true
        )
        val id = categoryKeywordDao.insert(entity)

        val newKeyword = CategoryKeyword(
            id = id,
            keyword = keyword,
            categoryType = categoryType,
            matchType = matchType,
            isUserDefined = true
        )
        _keywordsFlow.value = _keywordsFlow.value + newKeyword
    }

    suspend fun deleteUserKeyword(id: Long) {
        ensureCacheInitialized()
        categoryKeywordDao.deleteUserDefined(id)
        _keywordsFlow.value = _keywordsFlow.value.filter { it.id != id }
    }

    suspend fun updateUserKeyword(id: Long, categoryType: Int) {
        ensureCacheInitialized()

        val current = _keywordsFlow.value.find { it.id == id } ?: return

        categoryKeywordDao.update(
            CategoryKeywordEntity(
                id = id,
                keyword = current.keyword,
                categoryType = categoryType,
                matchType = if (current.matchType == MatchType.EXACT) 0 else 1,
                isUserDefined = true
            )
        )

        val updated = current.copy(categoryType = categoryType)
        _keywordsFlow.value = _keywordsFlow.value.map { if (it.id == id) updated else it }
    }

    suspend fun updateUserKeywordText(id: Long, newKeyword: String) {
        ensureCacheInitialized()

        val current = _keywordsFlow.value.find { it.id == id } ?: return

        categoryKeywordDao.update(
            CategoryKeywordEntity(
                id = id,
                keyword = newKeyword,
                categoryType = current.categoryType,
                matchType = if (current.matchType == MatchType.EXACT) 0 else 1,
                isUserDefined = true
            )
        )

        val updated = current.copy(keyword = newKeyword)
        _keywordsFlow.value = _keywordsFlow.value.map { if (it.id == id) updated else it }
    }
}
