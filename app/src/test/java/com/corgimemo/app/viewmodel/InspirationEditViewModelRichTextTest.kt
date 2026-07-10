package com.corgimemo.app.viewmodel

import android.content.Context
import com.corgimemo.app.data.local.db.ContentBlockDao
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.CategoryKeywordRepository
import com.corgimemo.app.data.repository.CategoryMatcher
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.InspirationRepository
import com.mohamedrejeb.richeditor.model.RichTextState
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * InspirationEditViewModel 富文本桥接单元测试
 *
 * 验证 RichTextState 与扩展 Undo/Redo 栈的桥接逻辑：
 * - setRichTextState 注入
 * - pushRichTextSnapshot 推送快照
 * - isContentBlockOperating 防反馈循环
 */
class InspirationEditViewModelRichTextTest {

    private lateinit var viewModel: InspirationEditViewModel

    private val inspirationRepository: InspirationRepository = mockk(relaxed = true)
    private val categoryRepository: CategoryRepository = mockk(relaxed = true)
    private val categoryKeywordRepository: CategoryKeywordRepository = mockk(relaxed = true)
    private val categoryMatcher: CategoryMatcher = mockk(relaxed = true)
    private val corgiPreferences: CorgiPreferences = mockk(relaxed = true)
    private val cardRelationRepository: CardRelationRepository = mockk(relaxed = true)
    private val contentBlockDao: ContentBlockDao = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    @Before
    fun setup() {
        viewModel = InspirationEditViewModel(
            inspirationRepository = inspirationRepository,
            categoryRepository = categoryRepository,
            categoryKeywordRepository = categoryKeywordRepository,
            categoryMatcher = categoryMatcher,
            corgiPreferences = corgiPreferences,
            cardRelationRepository = cardRelationRepository,
            contentBlockDao = contentBlockDao,
            context = context
        )
    }

    /**
     * 测试：setRichTextState 正确注入状态
     */
    @Test
    fun `setRichTextState injects state correctly`() {
        val state = RichTextState()
        viewModel.setRichTextState(state)
        assertEquals(state, viewModel.richTextState)
    }

    /**
     * 测试：pushRichTextSnapshot 推送快照后 canUndo 变为 true
     */
    @Test
    fun `pushRichTextSnapshot enables undo`() = runTest {
        val markdownBefore = "**test**"
        viewModel.pushRichTextSnapshot(markdownBefore)
        assertTrue(viewModel.canUndo.value)
    }

    /**
     * 测试：isContentBlockOperating=true 时 pushRichTextSnapshot 被跳过
     */
    @Test
    fun `pushRichTextSnapshot skipped when content block operating`() = runTest {
        viewModel.setContentBlockOperating(true)
        viewModel.pushRichTextSnapshot("**test**")
        assertFalse(viewModel.canUndo.value)
    }

    /**
     * 测试：setContentBlockOperating(false) 后恢复正常推送
     */
    @Test
    fun `pushRichTextSnapshot works after content block operation ends`() = runTest {
        viewModel.setContentBlockOperating(true)
        viewModel.pushRichTextSnapshot("**test1**")
        viewModel.setContentBlockOperating(false)
        viewModel.pushRichTextSnapshot("**test2**")
        assertTrue(viewModel.canUndo.value)
    }
}
