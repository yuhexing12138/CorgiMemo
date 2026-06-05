package com.corgimemo.app.data.cache

/**
 * 缓存键常量定义
 *
 * 统一管理所有缓存的键名，避免字符串硬编码导致的错误。
 * 采用分层命名规范：`cache:{模块}:{数据类型}:{过滤条件}`
 *
 * **命名示例**：
 * - `cache:todos:all` - 所有待办
 * - `cache:todos:pending` - 待处理待办
 * - `cache:inspirations:all` - 所有灵感
 */
object CacheKeys {
    // ==================== 待办相关 ====================
    const val TODOS_ALL = "cache:todos:all"
    const val TODOS_PENDING = "cache:todos:pending"
    const val TODOS_COMPLETED = "cache:todos:completed"

    // ==================== 灵感相关 ====================
    const val INSPIRATIONS_ALL = "cache:inspirations:all"

    // ==================== 特殊日期相关 ====================
    const val DATES_ALL = "cache:dates:all"
    const val DATES_UPCOMING = "cache:dates:upcoming"
    const val DATES_ONGOING = "cache:dates:ongoing"
    const val DATES_EXPIRED = "cache:dates:expired"

    // ==================== 通用前缀（用于批量清理）====================
    private const val PREFIX = "cache:"

    /**
     * 检查键是否属于本模块的缓存
     */
    fun isCacheKey(key: String): Boolean = key.startsWith(PREFIX)
}
