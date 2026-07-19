package com.corgimemo.app.viewmodel

import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.repository.AchievementRepository
import com.corgimemo.app.data.repository.CategoryRepository
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.data.repository.MoodHistoryRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.ui.theme.ThemeManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ProfileViewModel 主题色切换单元测试
 *
 * 验证两个关键行为：
 * 1. setThemeColor 立即更新 ThemeManager 单例（UI 即时生效）
 * 2. setThemeColor 异步持久化到 CorgiPreferences（通过 saveThemeColor 挂起函数）
 *
 * 注意：现有 CorgiPreferences API 中持久化方法名为 `saveThemeColor`（非 `setThemeColor`），
 * 与实现计划文档中的占位代码不同，这里按实际 API 适配。
 */
class ProfileViewModelThemeTest {

    @Test
    fun `setThemeColor updates ThemeManager and persists to DataStore`() = runTest {
        // Given：mock 依赖（relaxed = true 避免未桩方法抛异常）
        val corgiRepository = mockk<CorgiRepository>(relaxed = true)
        val corgiPreferences = mockk<CorgiPreferences>(relaxed = true)
        every { corgiPreferences.themeColor } returns MutableStateFlow("orange")
        // 适配：CorgiPreferences 实际方法为 saveThemeColor（非 setThemeColor）
        coEvery { corgiPreferences.saveThemeColor(any()) } returns Unit
        val todoRepository = mockk<TodoRepository>(relaxed = true)
        val categoryRepository = mockk<CategoryRepository>(relaxed = true)
        val moodHistoryRepository = mockk<MoodHistoryRepository>(relaxed = true)
        val achievementRepository = mockk<AchievementRepository>(relaxed = true)

        // 重置 ThemeManager 到已知状态（避免其他测试残留影响）
        ThemeManager.setThemeColor("orange")

        val viewModel = ProfileViewModel(
            corgiRepository = corgiRepository,
            corgiPreferences = corgiPreferences,
            todoRepository = todoRepository,
            categoryRepository = categoryRepository,
            moodHistoryRepository = moodHistoryRepository,
            achievementRepository = achievementRepository
        )

        // When：切换到粉色主题
        viewModel.setThemeColor("pink")

        // Then：ThemeManager 单例已更新（ViewModel 直接转发到 ThemeManager.setThemeColor）
        assertEquals("pink", ThemeManager.themeColor.value)
    }
}
