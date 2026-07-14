package com.corgimemo.app.viewmodel

import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.CustomDateType
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
import org.junit.Before
import org.junit.Test

/**
 * 自定义日期类型 CRUD 逻辑单元测试
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomDateTypeCrudTest {

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
        every { repository.allDates } returns emptyFlow()
        every { repository.allCustomDateTypes } returns emptyFlow()
        coEvery { repository.getById(any()) } returns null
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

    @Test
    fun `addCustomType 调用 repository insertCustomDateType`() = runTest(testDispatcher) {
        viewModel.addCustomType("宠物生日", "🐹")
        coVerify { repository.insertCustomDateType("宠物生日", "🐹") }
    }

    @Test
    fun `renameCustomType 调用 repository renameCustomDateType`() = runTest(testDispatcher) {
        viewModel.renameCustomType(42L, "新名称")
        coVerify { repository.renameCustomDateType(42L, "新名称") }
    }

    @Test
    fun `deleteCustomType 调用 repository deleteCustomDateType`() = runTest(testDispatcher) {
        viewModel.deleteCustomType(42L)
        coVerify { repository.deleteCustomDateType(42L) }
    }
}
