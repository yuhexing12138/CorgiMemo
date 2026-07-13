package com.corgimemo.app.viewmodel

import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.CardRelationRepository
import com.corgimemo.app.data.repository.DeletedSpecialDateRepository
import com.corgimemo.app.data.repository.SpecialDateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

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
    private lateinit var deletedSpecialDateRepository: DeletedSpecialDateRepository
    private lateinit var corgiPreferences: CorgiPreferences
    private lateinit var viewModel: SpecialDateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk(relaxed = true)
        cardRelationRepository = mockk(relaxed = true)
        deletedSpecialDateRepository = mockk(relaxed = true)
        corgiPreferences = mockk(relaxed = true)
        // allDates 是普通 Flow（非 suspend），用 every{} 桩
        every { repository.allDates } returns emptyFlow()
        // 任何 getById 桩默认为 null（按需覆盖）
        coEvery { repository.getById(any()) } returns null
        // CorgiPreferences 的两个 Flow 在 ViewModel init 块中被 collect，需提供默认 false Flow
        every { corgiPreferences.hideSpecialDateDetails } returns flowOf(false)
        every { corgiPreferences.hideArchivedDateItems } returns flowOf(false)

        viewModel = SpecialDateViewModel(
            repository,
            cardRelationRepository,
            deletedSpecialDateRepository,
            corgiPreferences
        )
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

        val testVm = SpecialDateViewModel(repository, cardRelationRepository, deletedSpecialDateRepository, corgiPreferences)
        // 短暂等待 stateIn 收到数据
        kotlinx.coroutines.test.runCurrent()
        val groups = testVm.groupedDates.value
        val totalCount = groups.values.sumOf { it.size }
        // 归档项不应出现在任何分组中
        assertEquals(1, totalCount)
    }

    // ==================== pinnedDate 派生测试 (2026-07-14 新增) ====================

    @Test
    fun `pinnedDate 无置顶卡时返回 null`() = runTest(testDispatcher) {
        val active = listOf(
            mockk<SpecialDate>(relaxed = true) {
                coEvery { id } returns 1L
                coEvery { isPinned } returns false
                coEvery { isArchived } returns false
            }
        )
        every { repository.allDates } returns kotlinx.coroutines.flow.flowOf(active)

        val testVm = SpecialDateViewModel(repository, cardRelationRepository, deletedSpecialDateRepository, corgiPreferences)
        kotlinx.coroutines.test.runCurrent()

        assertNull(testVm.pinnedDate.value)
    }

    @Test
    fun `pinnedDate 单置顶卡时返回该卡 DisplayDate`() = runTest(testDispatcher) {
        val pinned = mockk<SpecialDate>(relaxed = true) {
            coEvery { id } returns 42L
            coEvery { isPinned } returns true
            coEvery { isArchived } returns false
            coEvery { title } returns "生日"
        }
        every { repository.allDates } returns kotlinx.coroutines.flow.flowOf(listOf(pinned))

        val testVm = SpecialDateViewModel(repository, cardRelationRepository, deletedSpecialDateRepository, corgiPreferences)
        kotlinx.coroutines.test.runCurrent()

        val pd = testVm.pinnedDate.value
        assertEquals(42L, pd?.id)
        assertEquals("生日", pd?.title)
    }

    @Test
    fun `pinnedDate 已归档置顶卡不返回`() = runTest(testDispatcher) {
        val archivedPinned = mockk<SpecialDate>(relaxed = true) {
            coEvery { id } returns 99L
            coEvery { isPinned } returns true
            coEvery { isArchived } returns true
        }
        every { repository.allDates } returns kotlinx.coroutines.flow.flowOf(listOf(archivedPinned))

        val testVm = SpecialDateViewModel(repository, cardRelationRepository, deletedSpecialDateRepository, corgiPreferences)
        kotlinx.coroutines.test.runCurrent()

        assertNull(testVm.pinnedDate.value)
    }

    // ==================== groupedDates 过滤置顶卡测试 (2026-07-14 新增) ====================

    @Test
    fun `groupedDates 过滤掉 isPinned 等于 true 的卡`() = runTest(testDispatcher) {
        val pinned = mockk<SpecialDate>(relaxed = true) {
            coEvery { id } returns 1L
            coEvery { isPinned } returns true
            coEvery { isArchived } returns false
            coEvery { title } returns "置顶卡"
        }
        val normal = mockk<SpecialDate>(relaxed = true) {
            coEvery { id } returns 2L
            coEvery { isPinned } returns false
            coEvery { isArchived } returns false
            coEvery { title } returns "普通卡"
        }
        every { repository.allDates } returns kotlinx.coroutines.flow.flowOf(listOf(pinned, normal))

        val testVm = SpecialDateViewModel(repository, cardRelationRepository, deletedSpecialDateRepository, corgiPreferences)
        kotlinx.coroutines.test.runCurrent()

        val groups = testVm.groupedDates.value
        val allDisplayIds = groups.values.flatten().map { it.id }
        // 置顶卡 1L 不应在任何分组中（避免与 PinnedDateCard 重复）
        assertEquals(listOf(2L), allDisplayIds)
    }

    // ==================== getDatesByDate 测试 (2026-07-14 Task 5 新增) ====================

    /**
     * 完全匹配年月日：相同 (year, month, day) 的卡返回，跨年同月日的卡不返回
     *
     * 给定：3 条卡（2026-07-14 妈妈生日 / 2025-07-14 恋爱纪念 / 2026-07-15 国庆）
     * 当：getDatesByDate(2026, 7, 14)
     * 则：返回 1 条（妈妈生日），不包含 2025 年的恋爱纪念
     */
    @Test
    fun `getDatesByDate 完全匹配年月日`() = runTest(testDispatcher) {
        // 给定：3 条卡（2026-07-14 妈妈生日 / 2025-07-14 恋爱纪念 / 2026-07-15 国庆）
        val cal1 = Calendar.getInstance().apply { set(2026, 6, 14, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val cal2 = Calendar.getInstance().apply { set(2025, 6, 14, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val cal3 = Calendar.getInstance().apply { set(2026, 6, 15, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val now = System.currentTimeMillis()

        val dates = listOf(
            SpecialDate(id = 1, title = "妈妈生日", targetDate = cal1.timeInMillis, category = "BIRTHDAY", createdAt = now, updatedAt = now),
            SpecialDate(id = 2, title = "恋爱纪念", targetDate = cal2.timeInMillis, category = "ANNIVERSARY", createdAt = now, updatedAt = now),
            SpecialDate(id = 3, title = "国庆", targetDate = cal3.timeInMillis, category = "HOLIDAY", createdAt = now, updatedAt = now)
        )
        every { repository.allDates } returns flowOf(dates)

        // 触发 StateFlow 收集（init 中已订阅 corgiPreferences，这里使用 viewModel 重新触发 specialDates 收集）
        viewModel.groupedDates.collect { }
        kotlinx.coroutines.test.runCurrent()

        // 当：getDatesByDate(2026, 7, 14)
        val result = viewModel.getDatesByDate(2026, 7, 14)

        // 则：返回 1 条（妈妈生日），不包含 2025 年的恋爱纪念
        assertEquals(1, result.size)
        assertEquals(1L, result[0].id)
        assertEquals("妈妈生日", result[0].title)
    }

    /**
     * 跨年不匹配：仅存在 2025-07-14 一条，查询 2026-07-14 应返回空列表
     *
     * 给定：2025-07-14 一条
     * 当：getDatesByDate(2026, 7, 14)
     * 则：返回空列表
     */
    @Test
    fun `getDatesByDate 跨年不匹配`() = runTest(testDispatcher) {
        // 给定：2025-07-14 一条
        val cal = Calendar.getInstance().apply { set(2025, 6, 14, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val now = System.currentTimeMillis()
        val dates = listOf(
            SpecialDate(id = 1, title = "2025事件", targetDate = cal.timeInMillis, category = "OTHER", createdAt = now, updatedAt = now)
        )
        every { repository.allDates } returns flowOf(dates)
        viewModel.groupedDates.collect { }
        kotlinx.coroutines.test.runCurrent()

        // 当：getDatesByDate(2026, 7, 14)
        val result = viewModel.getDatesByDate(2026, 7, 14)

        // 则：返回空列表
        assertTrue(result.isEmpty())
    }

    // ==================== getCalendarDateColor 测试 (2026-07-14 Task 5 新增) ====================

    /**
     * 多卡取首张色：2026-07-14 有 2 条卡（生日 + 纪念日），相同 targetDate 时
     * sortedBy 稳定排序保留 list 顺序，取首张 BIRTHDAY.color
     *
     * 给定：2026-07-14 有 2 条卡（生日 targetDate=14日 + 纪念日 targetDate=14日相同）
     * 当：getCalendarDateColor(2026, 7)
     * 则：返回 {14 -> BIRTHDAY.color}（BIRTHDAY.color = Color(0xFFFF6B9D)）
     */
    @Test
    fun `getCalendarDateColor 多卡取首张色`() = runTest(testDispatcher) {
        // 给定：2026-07-14 有 2 条卡（生日 targetDate=14日 + 纪念日 targetDate=14日相同）
        // 注：sortedBy 稳定排序，相同 targetDate 时保留 list 顺序
        val cal = Calendar.getInstance().apply { set(2026, 6, 14, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
        val now = System.currentTimeMillis()
        val dates = listOf(
            // 第一条是生日（BIRTHDAY.color = Color(0xFFFF6B9D)）
            SpecialDate(id = 1, title = "妈妈生日", targetDate = cal.timeInMillis, category = "BIRTHDAY", createdAt = now, updatedAt = now),
            // 第二条是纪念日（ANNIVERSARY.color = Color(0xFFFF9A5C)）
            SpecialDate(id = 2, title = "恋爱纪念", targetDate = cal.timeInMillis, category = "ANNIVERSARY", createdAt = now, updatedAt = now)
        )
        every { repository.allDates } returns flowOf(dates)
        viewModel.groupedDates.collect { }
        kotlinx.coroutines.test.runCurrent()

        // 当：getCalendarDateColor(2026, 7)
        val result = viewModel.getCalendarDateColor(2026, 7)

        // 则：返回 {14 -> BIRTHDAY.color}
        assertEquals(1, result.size)
        assertNotNull(result[14])
        // BIRTHDAY.color = Color(0xFFFF6B9D)
        assertEquals(
            androidx.compose.ui.graphics.Color(0xFFFF6B9D),
            result[14]
        )
    }

    /**
     * 无数据返回空 Map：viewModel 无任何 SpecialDate 时，getCalendarDateColor 应返回空 Map
     *
     * 给定：viewModel 无任何 SpecialDate
     * 当：getCalendarDateColor(2026, 7)
     * 则：返回空 Map
     */
    @Test
    fun `getCalendarDateColor 无数据返回空 Map`() = runTest(testDispatcher) {
        // 给定：viewModel 无任何 SpecialDate
        every { repository.allDates } returns flowOf(emptyList())
        viewModel.groupedDates.collect { }
        kotlinx.coroutines.test.runCurrent()

        // 当：getCalendarDateColor(2026, 7)
        val result = viewModel.getCalendarDateColor(2026, 7)

        // 则：返回空 Map
        assertTrue(result.isEmpty())
    }
}
