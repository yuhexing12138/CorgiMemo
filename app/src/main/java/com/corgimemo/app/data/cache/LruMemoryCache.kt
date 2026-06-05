package com.corgimemo.app.data.cache

import java.util.LinkedList

/**
 * LRU (Least Recently Used) 内存缓存实现
 *
 * 基于 LinkedHashMap 的线程安全 LRU 缓存，
 * 当缓存容量满时自动淘汰最久未使用的数据。
 *
 * **性能特性**：
 * - 读/写时间复杂度：O(1)（LinkedHashMap 保证）
 * - 内存占用：受 maxSize 参数限制
 * - 过期策略：支持 TTL (Time To Live)
 *
 * **适用场景**：
 * - 频繁访问的热点数据
 * - 计算成本高的查询结果
 * - UI 渲染所需的临时数据
 *
 * @param maxSize 最大缓存条目数（超过后自动淘汰）
 * @param defaultTTL 默认过期时间（毫秒），默认 5 分钟
 */
class LruMemoryCache<T : Any>(
    private val maxSize: Int = 1000,
    private val defaultTTL: Long = 5 * 60 * 1000L  // 5 分钟
) {

    /** 内部存储：使用 LinkedHashMap 的 accessOrder 模式实现 LRU */
    private data class CacheEntry<T>(
        val data: T,
        val insertTime: Long = System.currentTimeMillis(),
        var lastAccessTime: Long = System.currentTimeMillis()
    )

    /** 实际缓存容器 */
    private val cache = object : LinkedHashMap<String, CacheEntry<T>>(
        16,   // initialCapacity
        0.75f, // loadFactor
        true   // accessOrder（按访问顺序排序，LRU 核心）
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry<T>>): Boolean {
            // 当容量超过上限时，移除最久未使用的条目
            return size > maxSize
        }
    }

    /** 当前缓存大小 */
    val size: Int get() = synchronized(cache) { cache.size }

    /** 是否为空 */
    val isEmpty: Boolean get() = synchronized(cache) { cache.isEmpty() }

    /**
     * 读取缓存
     *
     * @param key 缓存键
     * @return 缓存的数据，不存在或已过期返回 null
     */
    fun get(key: String): T? {
        return synchronized(cache) {
            val entry = cache[key]

            if (entry == null) {
                return@synchronized null
            }

            // 检查是否过期
            if (System.currentTimeMillis() - entry.insertTime > defaultTTL) {
                // 已过期，移除并返回 null
                cache.remove(key)
                return@synchronized null
            }

            // 更新最后访问时间（用于 LRU 排序）
            entry.lastAccessTime = System.currentTimeMillis()
            entry.data
        }
    }

    /**
     * 写入缓存
     *
     * 如果 key 已存在则更新，否则新增。
     * 当容量超限时自动淘汰最旧条目。
     *
     * @param key 缓存键
     * @param value 要缓存的数据
     */
    fun put(key: String, value: T) {
        synchronized(cache) {
            cache[key] = CacheEntry(value)
        }
    }

    /**
     * 移除指定缓存
     *
     * @param key 缓存键
     * @return 被移除的数据，不存在返回 null
     */
    fun remove(key: String): T? {
        return synchronized(cache) {
            cache.remove(key)?.data
        }
    }

    /**
     * 检查是否存在且未过期
     */
    fun containsKey(key: String): Boolean {
        return get(key) != null
    }

    /**
     * 清空所有缓存
     */
    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }

    /**
     * 获取当前缓存统计信息（用于调试/监控）
     */
    fun getStats(): CacheStats {
        return synchronized(cache) {
            CacheStats(
                size = cache.size,
                maxSize = maxSize,
                hitRate = 0.0f,  // 可扩展：记录命中次数计算命中率
                expiredCount = cache.values.count {
                    System.currentTimeMillis() - it.insertTime > defaultTTL
                }
            )
        }
    }

    /**
     * 清理所有已过期的缓存条目
     *
     * @return 被清理的条目数
     */
    fun cleanExpired(): Int {
        return synchronized(cache) {
            val now = System.currentTimeMillis()
            val expiredKeys = cache.entries.filter {
                now - it.value.insertTime > defaultTTL
            }.map { it.key }

            expiredKeys.forEach { cache.remove(it) }
            expiredKeys.size
        }
    }
}

/**
 * 缓存统计信息
 */
data class CacheStats(
    val size: Int,
    val maxSize: Int,
    val hitRate: Float,
    val expiredCount: Int
)
