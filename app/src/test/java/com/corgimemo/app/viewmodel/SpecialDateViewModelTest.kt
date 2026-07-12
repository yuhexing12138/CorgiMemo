package com.corgimemo.app.viewmodel

import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.SpecialDateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * SpecialDateViewModel 新增方法单元测试
 *
 * 覆盖：
 * - pinDate 互斥（先 pin A 再 pin B，A 取消）
 * - archiveDate 缓存快照
 * - undoArchive 恢复并清空缓存
 * - 归档置顶卡时同步清空 pinnedDateId
 * - setExpandedDateId 互斥
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpecialDateViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: SpecialDateRepository
    private lateinit var cardRelationRepository: CardRelationRepository
    private lateinit var viewModel: SpecialDateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        cardRelationRepository = mockk(relaxed = true)
        // allDates 是普通 Flow（非 suspend），用 every{} 桩
        every { repository.allDates } returns emptyFlow()
        // 任何 getById 桩默认为 null（按需覆盖）
        coEvery { repository.getById(any()) } returns null

        viewModel = SpecialDateViewModel(repository, cardRelationRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ==================== pinDate 互斥测试 ====================

    @Test
    fun `pinDate 互斥 pin A 后 pin B A 的 isPinned 被清空`() = runTest(testDispatcher) {
        viewModel.pinDate(1L)
        viewModel.pinDate(2L)

        // 验证 Repository 层互斥调用
        coVerify { repository.clearPinExcept(2L) }
        coVerify { repository.pinnedDateId?.let { repository.setPinned(2L, true) } ?: repository.setPinned(2L, true) }
        // A 不应被 setPinned(true)
        coVerify(exactly = 0) { repository.setPinned(1L, true) }
        // pinnedDateId 最终指向 B
        assertEquals(2L, viewModel.pinnedDateId.value)
    }

    // ==================== archiveDate / undoArchive 测试 ====================

    @Test
    fun `archiveDate 缓存快照到 pendingArchive`() = runTest(testDispatcher) {
        val snapshot = mockk<SpecialDate>(relaxed = true)
        coEvery { repository.getById(42L) } returns snapshot

        viewModel.archiveDate(42L)

        coVerify { repository.archive(42L) }
        assertEquals(snapshot, viewModel.pendingArchive.value)
    }

    @Test
    fun `undoArchive 恢复后清空 pendingArchive`() = runTest(testDispatcher) {
        val snapshot = mockk<SpecialDate>(relaxed = true) {
            coEvery { id } returns 42L
        }
        coEvery { repository.getById(42L) } returns snapshot
        viewModel.archiveDate(42L)
        // 验证快照已缓存
        assertEquals(snapshot, viewModel.pendingArchive.value)

        viewModel.undoArchive()

        coVerify { repository.unarchive(42L) }
        assertNull(viewModel.pendingArchive.value)
    }

    // ==================== 置顶卡归档联动测试 ====================

    @Test
    fun `归档置顶卡时同步清空 pinnedDateId`() = runTest(testDispatcher) {
        val snapshot = mockk<SpecialDate>(relaxed = true) {
            coEvery { id } returns 7L
        }
        coEvery { repository.getById(7L) } returns snapshot

        viewModel.pinDate(7L)
        assertEquals(7L, viewModel.pinnedDateId.value)

        viewModel.archiveDate(7L)

        assertNull(viewModel.pinnedDateId.value)
    }

    // ==================== setExpandedDateId 互斥测试 ====================

    @Test
    fun `setExpandedDateId 互斥 第二次设置会覆盖第一次`() = runTest(testDispatcher) {
        viewModel.setExpandedDateId(1L)
        assertEquals(1L, viewModel.expandedDateId.value)

        viewModel.setExpandedDateId(2L)
        assertEquals(2L, viewModel.expandedDateId.value)

        viewModel.setExpandedDateId(null)
        assertNull(viewModel.expandedDateId.value)
    }

    // ==================== unpinDate 测试 ====================

    @Test
    fun `unpinDate 同步清空 pinnedDateId`() = runTest(testDispatcher) {
        viewModel.pinDate(5L)
        assertEquals(5L, viewModel.pinnedDateId.value)

        viewModel.unpinDate(5L)

        coVerify { repository.unpinDate(5L) }
        assertNull(viewModel.pinnedDateId.value)
    }

    // ==================== groupedDates 过滤归档测试 ====================

    @Test
    fun `groupedDates 过滤已归档数据`() = runTest(testDispatcher) {
        val active = listOf(
            mockk<SpecialDate>(relaxed = true) {
                coEvery { id } returns 1L
                coEvery { isArchived } returns false
                coEvery { title } returns "生日"
            }
        )
        val archived = listOf(
            mockk<SpecialDate>(relaxed = true) {
                coEvery { id } returns 2L
                coEvery { isArchived } returns true
                coEvery { title } returns "节日"
            }
        )
        every { repository.allDates } returns kotlinx.coroutines.flow.flowOf(active + archived)

        val testVm = SpecialDateViewModel(repository, cardRelationRepository)
        // 短暂等待 stateIn 收到数据
        kotlinx.coroutines.test.runCurrent()
        val groups = testVm.groupedDates.value
        val totalCount = groups.values.sumOf { it.size }
        // 归档项不应出现在任何分组中
        assertEquals(1, totalCount)
    }
}
