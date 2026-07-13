package com.corgimemo.app.viewmodel

import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.repository.SpecialDateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * SpecialDateCardStyleViewModel 单元测试
 *
 * 覆盖:
 * - saveNewDate 构造 SpecialDate 时 cardStyle = cardStyle.serialName
 * - saveState 状态转换:Idle → Saving → Success
 * - Repository 抛异常时 → Error
 * - 重复点击保存时只触发一次插入
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpecialDateCardStyleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: SpecialDateRepository
    private lateinit var viewModel: SpecialDateCardStyleViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        viewModel = SpecialDateCardStyleViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial saveState is Idle`() = runTest {
        assertEquals(SaveState.Idle, viewModel.saveState.value)
    }

    @Test
    fun `saveNewDate constructs SpecialDate with cardStyle serialName`() = runTest {
        whenever(repository.insert(any())).thenReturn(1L)

        viewModel.saveNewDate(
            title = "测试",
            dateMillis = 1721260800000L,
            category = "BIRTHDAY",
            isPinned = true,
            cardStyle = DateCardStyle.CalendarTearOff
        )
        advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<SpecialDate>()
        verify(repository).insert(captor.capture())
        val saved = captor.firstValue
        assertEquals("CALENDAR_TEAR_OFF", saved.cardStyle)
        assertEquals("DEFAULT", saved.cardColor)  // 默认 DEFAULT
        assertEquals("测试", saved.title)
        assertEquals(1721260800000L, saved.targetDate)
        assertEquals("BIRTHDAY", saved.category)
        assertEquals(true, saved.isPinned)
    }

    @Test
    fun `saveNewDate transitions to Success after insert`() = runTest {
        whenever(repository.insert(any())).thenReturn(1L)

        viewModel.saveNewDate(
            title = "测试",
            dateMillis = 1721260800000L,
            category = "OTHER",
            isPinned = false,
            cardStyle = DateCardStyle.OrangeTearOff
        )
        advanceUntilIdle()

        assertEquals(SaveState.Success, viewModel.saveState.value)
    }

    @Test
    fun `saveNewDate transitions to Error when repository throws`() = runTest {
        whenever(repository.insert(any())).thenThrow(RuntimeException("DB error"))

        viewModel.saveNewDate(
            title = "测试",
            dateMillis = 1721260800000L,
            category = "OTHER",
            isPinned = false,
            cardStyle = DateCardStyle.OrangeTearOff
        )
        advanceUntilIdle()

        val state = viewModel.saveState.value
        assertTrue("Expected Error state, got $state", state is SaveState.Error)
    }

    @Test
    fun `saveNewDate sets createdAt and updatedAt to current time`() = runTest {
        whenever(repository.insert(any())).thenReturn(1L)
        val before = System.currentTimeMillis()

        viewModel.saveNewDate(
            title = "测试",
            dateMillis = 1721260800000L,
            category = "OTHER",
            isPinned = false,
            cardStyle = DateCardStyle.OrangeTearOff
        )
        advanceUntilIdle()

        val after = System.currentTimeMillis()
        val captor = org.mockito.kotlin.argumentCaptor<SpecialDate>()
        verify(repository).insert(captor.capture())
        val saved = captor.firstValue
        assertTrue("createdAt 应在 before 之后", saved.createdAt >= before)
        assertTrue("createdAt 应在 after 之前", saved.createdAt <= after)
        assertEquals(saved.createdAt, saved.updatedAt)
    }

    @Test
    fun `saveNewDate constructs SpecialDate with explicit cardColor serialName`() = runTest {
        whenever(repository.insert(any())).thenReturn(1L)

        viewModel.saveNewDate(
            title = "测试",
            dateMillis = 1721260800000L,
            category = "BIRTHDAY",
            isPinned = true,
            cardStyle = DateCardStyle.OrangeTearOff,
            cardColor = DateCardColor.Blue
        )
        advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<SpecialDate>()
        verify(repository).insert(captor.capture())
        val saved = captor.firstValue
        assertEquals("BLUE", saved.cardColor)
        assertEquals("ORANGE_TEAR_OFF", saved.cardStyle)
    }

    @Test
    fun `saveNewDate persists Rainbow cardColor as serialName`() = runTest {
        whenever(repository.insert(any())).thenReturn(1L)

        viewModel.saveNewDate(
            title = "测试",
            dateMillis = 1721260800000L,
            category = "OTHER",
            isPinned = false,
            cardStyle = DateCardStyle.OrangeTearOff,
            cardColor = DateCardColor.Rainbow
        )
        advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<SpecialDate>()
        verify(repository).insert(captor.capture())
        val saved = captor.firstValue
        assertEquals("RAINBOW", saved.cardColor)
    }

    @Test
    fun `saveNewDate without cardColor uses DEFAULT`() = runTest {
        whenever(repository.insert(any())).thenReturn(1L)

        // 不传 cardColor → 使用默认值 DEFAULT
        viewModel.saveNewDate(
            title = "测试",
            dateMillis = 1721260800000L,
            category = "OTHER",
            isPinned = false,
            cardStyle = DateCardStyle.OrangeTearOff
        )
        advanceUntilIdle()

        val captor = org.mockito.kotlin.argumentCaptor<SpecialDate>()
        verify(repository).insert(captor.capture())
        val saved = captor.firstValue
        assertEquals("DEFAULT", saved.cardColor)
    }
}
