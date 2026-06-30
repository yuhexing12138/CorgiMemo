package com.corgimemo.app.data.cache

import android.content.Context
import android.util.Log
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 双层缓存管理器
 *
 * 协调 L1 内存缓存和 L2 磁盘缓存的读写，
 * 实现多级缓存架构以加速数据访问。
 *
 * **缓存层次**：
 * ```
 * L1: 内存缓存 (LruMemoryCache)    → < 1ms 响应
 * L2: 磁盘缓存 (ESP)               → ~10ms 响应
 * L3: 数据库 (Room)                → ~50-200ms 响应
 * ```
 *
 * **读取策略**：L1 → L2 → L3（逐级回退）
 * **写入策略**：同时写入 L1 和 L2（Write-Through）
 *
 * @param context 应用上下文（用于 ESP 访问）
 * @param preferences 加密偏好设置实例
 */
@Singleton
class CacheManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val preferences: CorgiPreferences
) {
    companion object {
        private const val TAG = "CacheManager"

        /** L1 内存缓存容量 */
        private const val MEMORY_CACHE_SIZE = 500

        /** L2 磁盘缓存 TTL（24 小时） */
        private const val DISK_CACHE_TTL_MS = 24 * 60 * 60 * 1000L
    }

    // ==================== 缓存实例 ====================

    /** L1: 内存缓存（LRU 淘汰策略，5 分钟过期，存储任意类型对象） */
    private val memoryCache = LruMemoryCache<Any>(
        maxSize = MEMORY_CACHE_SIZE,
        defaultTTL = 5 * 60 * 1000L  // 5 分钟
    )

    // ==================== 公共 API ====================

    /**
     * 从缓存读取数据（L1 → L2 回退策略）
     *
     * 读取顺序：
     * 1. 尝试从 L1 内存缓存读取（最快，< 1ms）
     * 2. 如果 L1 未命中，尝试从 L2 磁盘缓存读取（~10ms）
     * 3. 如果 L2 也未命中，返回 null（调用方应从数据库加载）
     *
     * **性能优化**：
     * - L2 命中时会自动回填到 L1（提升后续访问速度）
     *
     * @param key 缓存键（使用 [CacheKeys] 中定义的常量）
     * @param serializer 序列化器（用于 L2 的 JSON 反序列化）
     * @return 缓存的数据，未命中返回 null
     */
    suspend fun <T : Any> get(key: String, serializer: Serializer<T>): T? {
        try {
            // 1. 尝试从 L1 内存缓存读取
            @Suppress("UNCHECKED_CAST")
            memoryCache.get(key)?.let { data ->
                Log.v(TAG, "📦 L1 内存缓存命中: $key")
                return data as T
            }

            // 2. 尝试从 L2 磁盘缓存读取
            val cachedJson = preferences.getStringDirect("cache_$key", null)
            if (!cachedJson.isNullOrBlank()) {
                // 检查是否过期
                val cacheTime = preferences.getLongDirect("${key}_timestamp", 0L)
                if (System.currentTimeMillis() - cacheTime > DISK_CACHE_TTL_MS) {
                    // 已过期，清除并返回 null
                    invalidate(key)
                    return null
                }

                // 反序列化数据
                val data = serializer.deserialize(cachedJson)
                if (data != null) {
                    // 回填到 L1 内存缓存（加速下次访问）
                    memoryCache.put(key, data)
                    Log.v(TAG, "💾 L2 磁盘缓存命中: $key")
                    return data
                }
            }

            // 3. 两级缓存均未命中
            return null
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 读取缓存失败 [$key]: ${e.message}")
            return null
        }
    }

    /**
     * 写入数据到缓存（同时写入 L1 和 L2）
     *
     * 采用 Write-Through 策略：
     * - 数据同时写入内存和磁盘
     * - 保证数据一致性
     *
     * @param key 缓存键
     * @param data 要缓存的数据
     * @param serializer 序列化器（用于 L2 的 JSON 序列化）
     */
    suspend fun <T : Any> put(key: String, data: T, serializer: Serializer<T>) {
        try {
            // 写入 L1 内存缓存
            memoryCache.put(key, data)

            // 写入 L2 磁盘缓存
            val json = serializer.serialize(data)
            preferences.setStringDirect("cache_$key", json)
            preferences.setLongDirect("${key}_timestamp", System.currentTimeMillis())

            Log.v(TAG, "✅ 数据已缓存: $key")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 写入缓存失败 [$key]: ${e.message}", e)
        }
    }

    /**
     * 使指定缓存失效
     *
     * 同时清除 L1 和 L2 中的对应数据。
     *
     * @param key 要失效的缓存键
     */
    suspend fun invalidate(key: String) {
        try {
            // 清除 L1
            memoryCache.remove(key)

            // 清除 L2
            preferences.removeKeyDirect("cache_$key")
            preferences.removeKeyDirect("${key}_timestamp")

            Log.v(TAG, "🗑️ 缓存已失效: $key")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 失效缓存失败 [$key]: ${e.message}")
        }
    }

    /**
     * 批量使缓存失效（按前缀匹配）
     *
     * 用于数据变更后批量清理相关缓存。
     *
     * @param prefix 键名前缀（如 "cache:todos:"）
     */
    suspend fun invalidateByPrefix(prefix: String) {
        try {
            // 清理 L1 中所有匹配前缀的条目
            // 注意：LruMemoryCache 不支持前缀查询，这里仅清理已知键
            when (prefix) {
                "cache:todos:" -> {
                    invalidate(CacheKeys.TODOS_ALL)
                    invalidate(CacheKeys.TODOS_PENDING)
                    invalidate(CacheKeys.TODOS_COMPLETED)
                }
                "cache:inspirations:" -> {
                    invalidate(CacheKeys.INSPIRATIONS_ALL)
                }
                "cache:dates:" -> {
                    invalidate(CacheKeys.DATES_ALL)
                    invalidate(CacheKeys.DATES_UPCOMING)
                    invalidate(CacheKeys.DATES_ONGOING)
                    invalidate(CacheKeys.DATES_EXPIRED)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ 批量失效缓存失败 [$prefix]: ${e.message}")
        }
    }

    /**
     * 清空所有缓存
     */
    suspend fun clearAll() {
        try {
            // 清空 L1
            memoryCache.clear()

            // 清空所有 L2 缓存键
            listOf(
                CacheKeys.TODOS_ALL, CacheKeys.TODOS_PENDING, CacheKeys.TODOS_COMPLETED,
                CacheKeys.INSPIRATIONS_ALL,
                CacheKeys.DATES_ALL, CacheKeys.DATES_UPCOMING, CacheKeys.DATES_ONGOING, CacheKeys.DATES_EXPIRED
            ).forEach { key ->
                preferences.removeKeyDirect("cache_$key")
                preferences.removeKeyDirect("${key}_timestamp")
            }

            Log.i(TAG, "🗑️ 所有缓存已清空")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 清空缓存失败: ${e.message}", e)
        }
    }

    /**
     * 获取缓存统计信息（用于调试/监控）
     */
    fun getStats(): String {
        val stats = memoryCache.getStats()
        return """
            |📊 缓存统计:
            |  L1 内存: ${stats.size}/${stats.maxSize} 条目
            |  已过期: ${stats.expiredCount} 条目
        """.trimMargin()
    }
}
