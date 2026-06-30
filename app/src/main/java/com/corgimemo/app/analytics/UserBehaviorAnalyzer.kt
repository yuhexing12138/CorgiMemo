package com.corgimemo.app.analytics

import android.content.Context
import android.util.Log
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hilt 入口点：暴露 UserBehaviorAnalyzer 给无法直接注入的 Composable 函数
 *
 * MainScreen 是 @Composable 函数（非 Hilt 管理的类），
 * 无法使用 @Inject 注入，需要通过 EntryPointAccessors 获取 @Singleton 实例。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface UserBehaviorAnalyzerEntryPoint {
    fun analyzer(): UserBehaviorAnalyzer
}

/**
 * 用户行为分析器
 *
 * 统计用户页面访问频率，动态计算预加载优先级。
 * 数据仅存储在本地设备，不上传服务器（保护隐私）。
 *
 * **使用场景**：
 * - 应用进入前台时，优先加载用户最常访问的页面数据
 * - 根据历史习惯优化首帧加载时间
 *
 * **算法**：
 * 1. 统计每个页面的访问次数
 * 2. 计算加权分数 = 访问频率 × 0.7 + 最近访问 × 0.3
 * 3. 按分数降序排列返回预加载顺序
 */
@Singleton
class UserBehaviorAnalyzer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: CorgiPreferences
) {

    companion object {
        private const val TAG = "UserBehaviorAnalyzer"

        /** 访问计数键前缀 */
        private const val KEY_VISIT_PREFIX = "page_visit_count_"

        /** 最近一次访问时间戳键 */
        private const val KEY_LAST_VISIT_TIME = "last_visit_timestamp_"

        /** 默认预加载顺序（无历史数据时使用） */
        val DEFAULT_PRIORITY = listOf(
            PageType.HOME,          // 大多数用户首选待办页
            PageType.INSPIRATION,   // 灵感功能使用频率较高
            PageType.SPECIAL_DATE   // 日期功能相对低频
        )
    }

    /**
     * 页面类型枚举
     */
    enum class PageType {
        HOME,           // 待办页
        INSPIRATION,    // 灵感页
        SPECIAL_DATE    // 日期页
    }

    /**
     * 记录一次页面访问
     *
     * @param page 被访问的页面类型
     */
    suspend fun recordPageVisit(page: PageType) {
        try {
            // 增加该页面的访问计数
            val currentCount = getVisitCount(page)
            setVisitCount(page, currentCount + 1)

            // 更新最近访问时间戳
            setLastVisitTime(page, System.currentTimeMillis())

            Log.d(TAG, "✅ 记录页面访问: ${page.name} (总次数: ${currentCount + 1})")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 记录页面访问失败: ${e.message}")
        }
    }

    /**
     * 获取预加载优先级排序
     *
     * 根据用户历史行为计算每个页面的加权分数，
     * 返回按分数降序排列的页面列表。
     *
     * @return 按优先级降序排列的页面列表（第一个应最先预加载）
     */
    suspend fun getPreloadPriority(): List<PageType> {
        return try {
            // 获取所有页面的访问统计
            val stats = PageType.entries.map { page ->
                val visitCount = getVisitCount(page)
                val lastVisitTime = getLastVisitTime(page)
                PageStat(page, visitCount, lastVisitTime)
            }

            // 如果没有任何访问记录，返回默认顺序
            if (stats.all { it.visitCount == 0L }) {
                Log.d(TAG, "ℹ️ 无历史记录，使用默认预加载顺序")
                return DEFAULT_PRIORITY
            }

            // 计算当前时间戳（用于计算 recency 分数）
            val currentTime = System.currentTimeMillis()

            // 按加权分数排序：frequency × 0.7 + recency × 0.3
            stats.sortedByDescending { stat ->
                val frequencyScore = stat.visitCount.toDouble() / 100.0  // 归一化到 0-1

                // 最近性分数：越近越高（24小时内为满分）
                val timeDiff = currentTime - stat.lastVisitTime
                val recencyScore = if (timeDiff < 0) 0.0 else {
                    val hoursAgo = timeDiff / (1000 * 60 * 60)
                    maxOf(0.0, 1.0 - hoursAgo / 24.0)  // 24小时线性衰减
                }

                frequencyScore * 0.7 + recencyScore * 0.3
            }.map { it.page }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 计算预加载优先级失败，使用默认顺序: ${e.message}")
            DEFAULT_PRIORITY
        }
    }

    /**
     * 获取最常访问的页面
     *
     * @return 访问次数最多的页面，若无记录则返回 null
     */
    suspend fun getMostVisitedPage(): PageType? {
        return try {
            PageType.entries.maxByOrNull { getVisitCount(it) }
                ?.takeIf { getVisitCount(it) > 0L }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 获取最常访问页面失败: ${e.message}")
            null
        }
    }

    /**
     * 重置所有统计数据（用于测试或用户清除数据时）
     */
    suspend fun resetAllStats() {
        try {
            PageType.entries.forEach { page ->
                setVisitCount(page, 0L)
                setLastVisitTime(page, 0L)
            }
            Log.d(TAG, "🗑️ 所有统计数据已重置")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 重置统计数据失败: ${e.message}", e)
        }
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 获取页面访问次数
     */
    private suspend fun getVisitCount(page: PageType): Long {
        return longFlow("${KEY_VISIT_PREFIX}${page.name.lowercase()}", 0L).first()
    }

    /**
     * 设置页面访问次数
     */
    private suspend fun setVisitCount(page: PageType, count: Long) {
        setLongPref("${KEY_VISIT_PREFIX}${page.name.lowercase()}", count)
    }

    /**
     * 获取页面最后访问时间戳
     */
    private suspend fun getLastVisitTime(page: PageType): Long {
        return longFlow("${KEY_LAST_VISIT_TIME}${page.name.lowercase()}", 0L).first()
    }

    /**
     * 设置页面最后访问时间戳
     */
    private suspend fun setLastVisitTime(page: PageType, timestamp: Long) {
        setLongPref("${KEY_LAST_VISIT_TIME}${page.name.lowercase()}", timestamp)
    }

    /**
     * 页面统计数据内部类
     */
    private data class PageStat(
        val page: PageType,
        val visitCount: Long,
        val lastVisitTime: Long
    )

    // ==================== 通用偏好设置辅助方法 ====================

    /**
     * 将 ESP 的 getLong 转换为 Flow（用于动态键名读取）
     * @param key 键名
     * @param defaultValue 默认值
     */
    private fun longFlow(key: String, defaultValue: Long): kotlinx.coroutines.flow.Flow<Long> =
        kotlinx.coroutines.flow.callbackFlow {
            trySend(preferences.getLongDirect(key, defaultValue))
            close()
        }

    /**
     * 写入 Long 值到 ESP（用于动态键名写入）
     * @param key 键名
     * @param value 值
     */
    private suspend fun setLongPref(key: String, value: Long) {
        preferences.setLongDirect(key, value)
    }
}
