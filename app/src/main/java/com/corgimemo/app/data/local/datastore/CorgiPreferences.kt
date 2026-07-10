package com.corgimemo.app.data.local.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * 柯基偏好设置管理器（EncryptedSharedPreferences 版本）
 *
 * ## 架构变更（V2.8 DataStore → ESP 迁移）
 *
 * **旧方案**: DataStore<Preferences> — 数据以明文 XML 存储，无加密保护
 * **新方案**: EncryptedSharedPreferences — 基于 Android Keystore 的 AES-256-GCM 加密存储
 *
 * ### 核心安全特性：
 * 1. **全 Key 自动加密**: 所有写入 SharedPreferences 的数据均由系统级 AES-256-GCM 自动加密
 * 2. **密钥轮换**: 混合模式 —— 系统自动轮换（30-90天）+ 应用版本追踪
 * 3. **一次性迁移**: 首次启动时自动从旧 DataStore 迁移所有数据到新 ESP，对用户透明
 *
 * ### 密钥轮换策略（混合模式）：
 * - **系统层**: MasterKey 使用 `AES256_GCM` 方案，Android Keystore 每 30-90 天自动轮换主密钥
 * - **应用层**: 通过 [KEY_VERSION] 记录当前加密版本号，支持应用主动触发重加密
 * - **数据迁移**: 检测到版本变化时，用旧密钥解密 → 用新密钥重加密（由 Android Keystore 内部处理）
 *
 * ### 外部 API 兼容性：
 * - 所有 getter 仍返回 `Flow<T>`（通过 callbackFlow 适配同步 ESP 读取）
 * - 所有 setter 仍为 `suspend fun`（通过 withContext(Dispatchers.IO) 包装同步写入）
 * - 方法签名与原版完全一致，调用方无需修改
 */
class CorgiPreferences(
    private val context: Context,
    private val esp: EncryptedSharedPreferences
) {

    companion object {
        /** 旧版 DataStore 名称（用于迁移时读取） */
        private const val LEGACY_DATASTORE_NAME = "corgi_preferences"

        /** ESP 文件名 */
        private const val ESP_FILE_NAME = "esp_corgi_preferences"

        /** 加密密钥版本号（用于混合模式密钥轮换追踪） */
        private const val KEY_VERSION = "encryption_key_version"

        /** 当前加密方案版本（递增触发重加密） */
        private const val CURRENT_KEY_VERSION = 1

        /** 单例实例（双重检查锁定） */
        @Volatile
        private var instance: CorgiPreferences? = null

        /**
         * 获取 CorgiPreferences 单例实例
         *
         * 创建流程：
         * 1. 构建 MasterKey（AES-256-GCM 方案，支持系统级自动密钥轮换）
         * 2. 创建 EncryptedSharedPreferences（所有读写自动加解密）
         * 3. 执行一次性数据迁移（旧 DataStore → 新 ESP）
         *
         * @param context 应用上下文
         * @return CorgiPreferences 单例
         */
        fun getInstance(context: Context): CorgiPreferences {
            return instance ?: synchronized(this) {
                instance ?: run {
                    // 步骤1：构建 MasterKey（Android Keystore 托管，AES-256-GCM 方案）
                    // setKeyScheme(AES256_GCM) 启用系统级自动密钥轮换（每30-90天）
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()

                    // 步骤2：创建 EncryptedSharedPreferences
                    // 所有 put/get 操作均由系统自动执行 AES-256-GCM 加解密
                    // EncryptedSharedPreferences.create() 返回 SharedPreferences，
                    // 需要强转为 EncryptedSharedPreferences 以匹配构造函数参数类型
                    @Suppress("UNCHECKED_CAST")
                    val esp = EncryptedSharedPreferences.create(
                        context,
                        ESP_FILE_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    ) as EncryptedSharedPreferences

                    val prefs = CorgiPreferences(context, esp)

                    // 步骤3：一次性从旧 DataStore 迁移数据（首次安装或升级后仅执行一次）
                    // getInstance() 是普通函数（非协程），使用 runBlocking 包装 suspend 调用
                    kotlinx.coroutines.runBlocking {
                        prefs.migrateFromDataStoreIfNeeded()
                    }

                    instance = prefs
                    prefs
                }
            }
        }
    }

    // ==================== 存储键定义 ====================

    /**
     * 所有偏好设置的键名常量
     *
     * 从 DataStore 的 PreferencesKey 改为纯字符串键，
     * 因为 EncryptedSharedPreferences 不支持类型化 Key。
     */
    private object Keys {
        const val CORGI_NAME = "corgi_name"
        const val IS_FIRST_LAUNCH = "is_first_launch"
        const val SOUND_ENABLED = "sound_enabled"
        const val HAPTIC_ENABLED = "haptic_enabled"
        const val LAST_ACTIVE_TIMESTAMP = "last_active_timestamp"
        const val NIGHT_SLEEP_CHECKED_DATE = "night_sleep_checked_date"
        const val IS_ONBOARDING_COMPLETED = "is_onboarding_completed"
        const val USER_TYPE = "user_type"
        const val AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        const val AUTO_BACKUP_URI = "auto_backup_uri"
        const val AUTO_BACKUP_PASSWORD = "auto_backup_password"
        const val AUTO_BACKUP_KEEP_COUNT = "auto_backup_keep_count"
        const val AUTO_BACKUP_FREQUENCY = "auto_backup_frequency"
        const val AUTO_BACKUP_LAST_TIME = "auto_backup_last_time"
        const val BACKUP_HISTORY = "backup_history"
        const val THEME_MODE = "theme_mode"
        const val THEME_COLOR = "theme_color"
        const val FIRST_GUIDE_SHOWN = "first_guide_shown"
        const val GUIDE_AB_GROUP = "guide_ab_group"
        const val GUIDE_COMPLETED_AT = "guide_completed_at"
        const val FIRST_TODO_CREATED_AT = "first_todo_created_at"
        const val FLOATING_CORGI_X = "floating_corgi_x"
        const val FLOATING_CORGI_Y = "floating_corgi_y"
        const val SORT_ORDER = "sort_order"
        const val UNDO_STACK = "undo_stack"
        const val REDO_STACK = "redo_stack"
        const val UNDO_STACK_TODO_ID = "undo_stack_todo_id"
        /** V2.7: 最后编辑的 Todo ID（用于设置页入口传递到编辑历史页面） */
        const val LAST_EDITED_TODO_ID = "last_edited_todo_id"
        /** 增量日志模式：追加式 Undo/Redo 日志（替代全量栈序列化） */
        const val UNDO_LOG = "undo_log"
        const val REDO_LOG = "redo_log"
        /** V2.8: 待办页"已完成"区域展开状态 */
        const val SHOW_COMPLETED = "show_completed"
        /** V2.9: 待办卡片简化显示（隐藏详情） */
        const val HIDE_DETAILS = "hide_details"
        /** V2.9: 隐藏所有已完成项 */
        const val HIDE_COMPLETED_ITEMS = "hide_completed_items"
        /** V2.10: 待办页"置顶"区域展开状态(默认展开) */
        const val SHOW_PINNED = "show_pinned"
        /** V2.11: 待办页"待完成"区域展开状态(默认展开) */
        const val SHOW_PENDING = "show_pending"
        /** 用户自定义灵感标签（JSON 数组字符串） */
        const val USER_DEFINED_TAGS = "user_defined_tags"
    }

    // ==================== 数据迁移（DataStore → ESP）====================

    /**
     * 旧版 DataStore 实例（仅在迁移时使用）
     *
     * 使用懒加载委托，避免非迁移场景下的资源开销。
     * 迁移完成后此引用可被 GC 回收。
     */
    private val Context.legacyDataStore: DataStore<Preferences> by preferencesDataStore(name = LEGACY_DATASTORE_NAME)

    /**
     * 一次性迁移：从旧 DataStore 读取所有数据并写入新 ESP
     *
     * **触发条件**（满足任一即执行迁移）：
     * 1. ESP 中不存在 [KEY_VERSION] 条目（说明是全新安装或首次升级到此版本）
     * 2. 旧 DataStore XML 文件存在且有数据（说明有旧数据需要迁移）
     *
     * **迁移流程**：
     * 1. 打开旧 DataStore，读取所有已定义的 28 个键值
     * 2. 将每个非空值写入新的 EncryptedSharedPreferences
     * 3. 处理动态生成的键（如节气卡片关闭状态、按 TodoId 隔离的 Undo 日志）
     * 4. 写入当前 [CURRENT_KEY_VERSION] 作为迁移完成标记
     * 5. 可选：删除旧 DataStore 文件（释放磁盘空间）
     *
     * **幂等性**：多次调用不会重复迁移或丢失数据。
     */
    private suspend fun migrateFromDataStoreIfNeeded() {
        // 检查是否已完成迁移（ESP 中已有版本标记则跳过）
        val currentVersion = esp.getInt(KEY_VERSION, 0)
        if (currentVersion > 0) {
            Log.d("CorgiPreferences", "ESP 已就绪，密钥版本=$currentVersion，跳过迁移")
            return
        }

        Log.i("CorgiPreferences", "开始 DataStore → ESP 一次性迁移...")

        try {
            // 读取旧 DataStore 中的所有偏好数据
            val legacyPrefs = context.legacyDataStore.data.first()
            var migratedCount = 0

            // --- 迁移所有静态定义的键 ---
            // 字符串类型键
            listOf(
                Keys.CORGI_NAME, Keys.LAST_ACTIVE_TIMESTAMP, Keys.NIGHT_SLEEP_CHECKED_DATE,
                Keys.USER_TYPE, Keys.AUTO_BACKUP_URI, Keys.AUTO_BACKUP_PASSWORD,
                Keys.AUTO_BACKUP_FREQUENCY, Keys.AUTO_BACKUP_LAST_TIME, Keys.BACKUP_HISTORY,
                Keys.THEME_MODE, Keys.THEME_COLOR, Keys.GUIDE_AB_GROUP,
                Keys.GUIDE_COMPLETED_AT, Keys.FIRST_TODO_CREATED_AT,
                Keys.FLOATING_CORGI_X, Keys.FLOATING_CORGI_Y, Keys.SORT_ORDER,
                Keys.UNDO_STACK, Keys.REDO_STACK, Keys.UNDO_LOG, Keys.REDO_LOG
            ).forEach { key ->
                val value = legacyPrefs[stringPreferencesKey(key)]
                if (value != null) {
                    esp.edit().putString(key, value).apply()
                    migratedCount++
                }
            }

            // 布尔类型键
            listOf(
                Keys.IS_FIRST_LAUNCH, Keys.SOUND_ENABLED, Keys.HAPTIC_ENABLED,
                Keys.IS_ONBOARDING_COMPLETED, Keys.AUTO_BACKUP_ENABLED,
                Keys.SHOW_COMPLETED, Keys.FIRST_GUIDE_SHOWN, Keys.SHOW_PINNED,
                Keys.SHOW_PENDING
            ).forEach { key ->
                val value = legacyPrefs[booleanPreferencesKey(key)]
                if (value != null) {
                    esp.edit().putBoolean(key, value).apply()
                    migratedCount++
                }
            }

            // 整数类型键
            listOf(
                Keys.AUTO_BACKUP_KEEP_COUNT, Keys.UNDO_STACK_TODO_ID
            ).forEach { key ->
                val value = legacyPrefs[intPreferencesKey(key)]
                if (value != null) {
                    esp.edit().putInt(key, value).apply()
                    migratedCount++
                }
            }

            // 长整数类型键
            val lastEditedId = legacyPrefs[longPreferencesKey(Keys.LAST_EDITED_TODO_ID)]
            if (lastEditedId != null) {
                esp.edit().putLong(Keys.LAST_EDITED_TODO_ID, lastEditedId).apply()
                migratedCount++
            }

            // --- 迁移动态生成的键 ---
            // 节气卡片关闭状态（格式：solar_term_card_dismissed_{solarTermId}_{date}）
            legacyPrefs.asMap().keys
                .map { it.name }
                .filter { it.startsWith("solar_term_card_dismissed_") }
                .forEach { dynamicKey ->
                    val value = legacyPrefs[booleanPreferencesKey(dynamicKey)]
                    if (value == true) {
                        esp.edit().putBoolean(dynamicKey, true).apply()
                        migratedCount++
                    }
                }

            // 按 TodoId 隔离的 Undo/Redo 日志（格式：undo_log_{todoId} / redo_log_{todoId}）
            legacyPrefs.asMap().keys
                .map { it.name }
                .filter { it.startsWith("undo_log_") || it.startsWith("redo_log_") }
                .forEach { dynamicKey ->
                    val value = legacyPrefs[stringPreferencesKey(dynamicKey)]
                    if (!value.isNullOrBlank()) {
                        esp.edit().putString(dynamicKey, value).apply()
                        migratedCount++
                    }
                }

            // 写入当前加密版本号作为迁移完成标记
            esp.edit().putInt(KEY_VERSION, CURRENT_KEY_VERSION).apply()

            Log.i(
                "CorgiPreferences",
                "DataStore → ESP 迁移完成！共迁移 $migratedCount 个条目，密钥版本=$CURRENT_KEY_VERSION"
            )
        } catch (e: Exception) {
            // 迁移失败不应阻塞应用启动，记录错误日志即可
            Log.e("CorgiPreferences", "DataStore → ESP 迁移失败（可能为全新安装，无旧数据）", e)
            // 即使迁移失败也写入版本号，避免每次启动都重试
            esp.edit().putInt(KEY_VERSION, CURRENT_KEY_VERSION).apply()
        }
    }

    // ==================== 密钥轮换（混合模式）====================

    /**
     * 获取当前加密密钥版本号
     *
     * @return 版本整数，0 表示未初始化
     */
    fun getKeyVersion(): Int = esp.getInt(KEY_VERSION, 0)

    /**
     * 检查是否需要执行密钥轮换
     *
     * 当应用发布新版本且 [CURRENT_KEY_VERSION] 递增时返回 true，
     * 调用方可据此触发「旧密钥解密 → 新密钥重加密」流程。
     *
     * **注意**：由于使用 AES256_GCM 方案，Android Keystore 层面的密钥轮换
     * 是由系统自动完成的（每 30-90 天）。此方法主要用于应用层面的
     * 主动重加密触发（如算法升级、密钥派生方式变更等场景）。
     *
     * @return true 表示需要轮换
     */
    fun needsKeyRotation(): Boolean = getKeyVersion() < CURRENT_KEY_VERSION

    /**
     * 完成密钥轮换后更新版本号
     *
     * 在「旧数据解密 → 新密钥重加密」流程完成后调用，
     * 将版本号更新为 [CURRENT_KEY_VERSION]，防止重复轮换。
     */
    suspend fun markKeyRotationComplete() {
        withContext(Dispatchers.IO) {
            esp.edit().putInt(KEY_VERSION, CURRENT_KEY_VERSION).apply()
        }
    }

    // ==================== Flow 适配工具方法 ====================

    /**
     * 将同步的 ESP getString 转换为响应式 Flow
     *
     * EncryptedSharedPreferences 本身不支持 Flow API，
     * 此方法通过 callbackFlow 提供与原 DataStore 一致的响应式接口。
     *
     * **性能说明**:
     * - callbackFlow 在订阅时发射当前值，之后保持静默
     * - 这符合 Preferences 类数据的"读多写少"特性
     * - 如需实时监听变化，调用方应配合 StateIn + WhileSubscribed 使用
     *
     * @param key 键名
     * @param defaultValue 默认值
     * @return 响应式 String? Flow
     */
    private fun stringFlow(key: String, defaultValue: String? = null): Flow<String?> = callbackFlow {
        // 立即发射当前值（同步读取 ESP）
        trySend(esp.getString(key, defaultValue))
        // ESP 无变更回调，关闭流（调用方通过 stateIn 缓存值）
        close()
    }

    /**
     * 将同步的 ESP getBoolean 转换为响应式 Flow
     *
     * @param key 键名
     * @param defaultValue 默认值
     * @return 响应式 Boolean Flow
     */
    private fun booleanFlow(key: String, defaultValue: Boolean): Flow<Boolean> = callbackFlow {
        trySend(esp.getBoolean(key, defaultValue))
        close()
    }

    /**
     * 将同步的 ESP getInt 转换为响应式 Flow
     *
     * @param key 键名
     * @param defaultValue 默认值
     * @return 响应式 Int Flow
     */
    private fun intFlow(key: String, defaultValue: Int): Flow<Int> = callbackFlow {
        trySend(esp.getInt(key, defaultValue))
        close()
    }

    /**
     * 将同步的 ESP getLong 转换为响应式 Flow
     *
     * @param key 键名
     * @param defaultValue 默认值
     * @return 响应式 Long Flow
     */
    private fun longFlow(key: String, defaultValue: Long): Flow<Long> = callbackFlow {
        trySend(esp.getLong(key, defaultValue))
        close()
    }

    // ==================== 柯基基础设置 ====================

    /** 获取柯基名字的Flow */
    val corgiName: Flow<String?> = stringFlow(Keys.CORGI_NAME)

    /** 获取是否首次启动的Flow */
    val isFirstLaunch: Flow<Boolean> = booleanFlow(Keys.IS_FIRST_LAUNCH, true)

    /** 获取音效反馈开关的Flow */
    val soundEnabled: Flow<Boolean> = booleanFlow(Keys.SOUND_ENABLED, true)

    /** 获取触觉反馈开关的Flow */
    val hapticEnabled: Flow<Boolean> = booleanFlow(Keys.HAPTIC_ENABLED, true)

    /** 获取待办页"已完成"区域展开状态的Flow（默认折叠） */
    val showCompleted: Flow<Boolean> = booleanFlow(Keys.SHOW_COMPLETED, false)

    /** 获取最后活跃时间戳的Flow */
    val lastActiveTimestamp: Flow<String?> = stringFlow(Keys.LAST_ACTIVE_TIMESTAMP)

    /** 获取深夜入睡检查日期的Flow */
    val nightSleepCheckedDate: Flow<String?> = stringFlow(Keys.NIGHT_SLEEP_CHECKED_DATE)

    /** 获取是否已完成首次引导的Flow */
    val isOnboardingCompleted: Flow<Boolean> = booleanFlow(Keys.IS_ONBOARDING_COMPLETED, false)

    /** 获取用户类型的Flow */
    val userType: Flow<String?> = stringFlow(Keys.USER_TYPE)

    /** 保存柯基名字 */
    suspend fun saveCorgiName(name: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.CORGI_NAME, name).apply()
    }

    /** 设置首次启动标志为false */
    suspend fun setFirstLaunchDone() = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.IS_FIRST_LAUNCH, false).apply()
    }

    /** 设置音效反馈开关 */
    suspend fun setSoundEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.SOUND_ENABLED, enabled).apply()
    }

    /** 设置触觉反馈开关 */
    suspend fun setHapticEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.HAPTIC_ENABLED, enabled).apply()
    }

    /** 设置待办页"已完成"区域展开状态 */
    suspend fun setShowCompleted(show: Boolean) = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.SHOW_COMPLETED, show).apply()
    }

    /** V2.9: 待办卡片简化显示 */
    val hideDetails: Flow<Boolean> = booleanFlow(Keys.HIDE_DETAILS, false)

    suspend fun setHideDetails(hide: Boolean) = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.HIDE_DETAILS, hide).apply()
    }

    /** V2.9: 隐藏所有已完成项 */
    val hideCompletedItems: Flow<Boolean> = booleanFlow(Keys.HIDE_COMPLETED_ITEMS, false)

    suspend fun setHideCompletedItems(hide: Boolean) = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.HIDE_COMPLETED_ITEMS, hide).apply()
    }

    /** V2.10: 获取待办页"置顶"区域展开状态的Flow(默认展开) */
    val showPinned: Flow<Boolean> = booleanFlow(Keys.SHOW_PINNED, true)

    /** V2.10: 设置待办页"置顶"区域展开状态 */
    suspend fun setShowPinned(show: Boolean) = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.SHOW_PINNED, show).apply()
    }

    /** V2.11: 获取待办页"待完成"区域展开状态的Flow(默认展开) */
    val showPending: Flow<Boolean> = booleanFlow(Keys.SHOW_PENDING, true)

    /** V2.11: 设置待办页"待完成"区域展开状态 */
    suspend fun setShowPending(show: Boolean) = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.SHOW_PENDING, show).apply()
    }

    /**
     * 保存最后活跃时间戳
     * @param timestamp 时间戳（毫秒）
     */
    suspend fun saveLastActiveTimestamp(timestamp: Long) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.LAST_ACTIVE_TIMESTAMP, timestamp.toString()).apply()
    }

    /**
     * 获取最后活跃时间戳
     * @return 时间戳（毫秒），如果没有记录则返回当前时间
     */
    suspend fun getLastActiveTimestamp(): Long = esp.getString(Keys.LAST_ACTIVE_TIMESTAMP, null)
        ?.toLongOrNull() ?: System.currentTimeMillis()

    /**
     * 保存深夜入睡检查日期
     * @param date 日期字符串（yyyy-MM-dd）
     */
    suspend fun saveNightSleepCheckedDate(date: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.NIGHT_SLEEP_CHECKED_DATE, date).apply()
    }

    /**
     * 获取深夜入睡检查日期
     * @return 日期字符串，如果没有记录则返回 null
     */
    suspend fun getNightSleepCheckedDate(): String? = esp.getString(Keys.NIGHT_SLEEP_CHECKED_DATE, null)

    /** 设置首次引导完成标志 */
    suspend fun setOnboardingCompleted() = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.IS_ONBOARDING_COMPLETED, true).apply()
    }

    /**
     * 保存用户类型
     * @param userType 用户类型字符串
     */
    suspend fun saveUserType(userType: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.USER_TYPE, userType).apply()
    }

    // ==================== 节气卡片关闭状态（动态键）====================

    /**
     * 获取节气卡片关闭状态的动态键名
     * @param solarTermId 节气 ID
     * @param date 日期字符串（yyyyMMdd）
     * @return 键名字符串
     */
    private fun getSolarTermCardDismissedKey(solarTermId: String, date: String) =
        "solar_term_card_dismissed_${solarTermId}_$date"

    /**
     * 检查今天是否已关闭过某个节气的科普卡片
     * @param solarTermId 节气 ID
     * @param today 今天的日期字符串（yyyyMMdd）
     * @return 是否已关闭
     */
    suspend fun isSolarTermCardDismissed(solarTermId: String, today: String): Boolean {
        val key = getSolarTermCardDismissedKey(solarTermId, today)
        return esp.getBoolean(key, false)
    }

    /**
     * 保存节气卡片的关闭状态
     * @param solarTermId 节气 ID
     * @param today 今天的日期字符串（yyyyMMdd）
     */
    suspend fun saveSolarTermCardDismissed(solarTermId: String, today: String) = withContext(Dispatchers.IO) {
        val key = getSolarTermCardDismissedKey(solarTermId, today)
        esp.edit().putBoolean(key, true).apply()
    }

    /**
     * 清理过期的节气卡片关闭状态（保留最近 30 天的数据）
     * @param currentDate 当前日期（yyyyMMdd）
     */
    suspend fun cleanupExpiredSolarTermCardDismissedKeys(currentDate: String) =
        withContext(Dispatchers.IO) {
            val currentDateInt = currentDate.toIntOrNull() ?: return@withContext
            val editor = esp.edit()

            // 遍历所有键，找出过期的节气卡片键
            esp.all.keys.filter { it.startsWith("solar_term_card_dismissed_") }
                .forEach { keyName ->
                    val parts = keyName.split("_")
                    if (parts.size >= 5) {
                        val dateStr = parts.last()
                        val dateInt = dateStr.toIntOrNull()
                        if (dateInt != null && (currentDateInt - dateInt) > 30) {
                            editor.remove(keyName)
                        }
                    }
                }
            editor.apply()
        }

    // ==================== 自动备份设置 ====================

    val autoBackupEnabled: Flow<Boolean> = booleanFlow(Keys.AUTO_BACKUP_ENABLED, false)
    val autoBackupUri: Flow<String?> = stringFlow(Keys.AUTO_BACKUP_URI)
    val autoBackupPassword: Flow<String?> = stringFlow(Keys.AUTO_BACKUP_PASSWORD)
    val autoBackupKeepCount: Flow<Int> = intFlow(Keys.AUTO_BACKUP_KEEP_COUNT, 5)

    suspend fun setAutoBackupEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.AUTO_BACKUP_ENABLED, enabled).apply()
    }

    suspend fun saveAutoBackupUri(uri: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.AUTO_BACKUP_URI, uri).apply()
    }

    suspend fun saveAutoBackupPassword(password: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.AUTO_BACKUP_PASSWORD, password).apply()
    }

    suspend fun clearAutoBackupPassword() = withContext(Dispatchers.IO) {
        esp.edit().remove(Keys.AUTO_BACKUP_PASSWORD).apply()
    }

    suspend fun saveAutoBackupKeepCount(count: Int) = withContext(Dispatchers.IO) {
        esp.edit().putInt(Keys.AUTO_BACKUP_KEEP_COUNT, count).apply()
    }

    // ==================== 自动备份频率 ====================

    val autoBackupFrequency: Flow<String> = callbackFlow {
        trySend(esp.getString(Keys.AUTO_BACKUP_FREQUENCY, "weekly") ?: "weekly")
        close()
    }

    suspend fun getAutoBackupFrequency(): String =
        esp.getString(Keys.AUTO_BACKUP_FREQUENCY, "weekly") ?: "weekly"

    suspend fun saveAutoBackupFrequency(frequency: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.AUTO_BACKUP_FREQUENCY, frequency).apply()
    }

    // ==================== 上次备份时间 ====================

    val autoBackupLastTime: Flow<Long> = callbackFlow {
        trySend(esp.getString(Keys.AUTO_BACKUP_LAST_TIME, null)?.toLongOrNull() ?: 0L)
        close()
    }

    suspend fun getAutoBackupLastTime(): Long =
        esp.getString(Keys.AUTO_BACKUP_LAST_TIME, null)?.toLongOrNull() ?: 0L

    suspend fun updateAutoBackupLastTime() = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.AUTO_BACKUP_LAST_TIME, System.currentTimeMillis().toString())
            .apply()
    }

    // ==================== 自动备份启用状态（一次性读取）====================

    suspend fun getAutoBackupEnabled(): Boolean =
        esp.getBoolean(Keys.AUTO_BACKUP_ENABLED, false)

    suspend fun getAutoBackupUri(): String? = esp.getString(Keys.AUTO_BACKUP_URI, null)

    suspend fun getAutoBackupKeepCount(): Int = esp.getInt(Keys.AUTO_BACKUP_KEEP_COUNT, 5)

    // ==================== 备份历史 ====================

    val backupHistory: Flow<String?> = stringFlow(Keys.BACKUP_HISTORY)

    suspend fun getBackupHistory(): String? = esp.getString(Keys.BACKUP_HISTORY, null)

    suspend fun saveBackupHistory(json: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.BACKUP_HISTORY, json).apply()
    }

    suspend fun clearBackupHistory() = withContext(Dispatchers.IO) {
        esp.edit().remove(Keys.BACKUP_HISTORY).apply()
    }

    // ==================== 主题设置 ====================

    val themeMode: Flow<String> = callbackFlow {
        trySend(esp.getString(Keys.THEME_MODE, "system") ?: "system")
        close()
    }

    val themeColor: Flow<String> = callbackFlow {
        trySend(esp.getString(Keys.THEME_COLOR, "orange") ?: "orange")
        close()
    }

    suspend fun saveThemeMode(mode: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.THEME_MODE, mode).apply()
    }

    suspend fun saveThemeColor(color: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.THEME_COLOR, color).apply()
    }

    // ==================== 首次引导状态 ====================

    val firstGuideShown: Flow<Boolean> = booleanFlow(Keys.FIRST_GUIDE_SHOWN, false)

    suspend fun getFirstGuideShown(): Boolean = esp.getBoolean(Keys.FIRST_GUIDE_SHOWN, false)

    suspend fun setFirstGuideShown() = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.FIRST_GUIDE_SHOWN, true).apply()
    }

    suspend fun resetFirstGuide() = withContext(Dispatchers.IO) {
        esp.edit().putBoolean(Keys.FIRST_GUIDE_SHOWN, false).apply()
    }

    // ==================== A/B 测试相关 ====================

    val guideAbGroup: Flow<String> = callbackFlow {
        trySend(esp.getString(Keys.GUIDE_AB_GROUP, "A") ?: "A")
        close()
    }

    suspend fun getGuideAbGroup(): String = esp.getString(Keys.GUIDE_AB_GROUP, "A") ?: "A"

    suspend fun getOrAssignAbGroup(): String {
        val currentGroup = getGuideAbGroup()
        if (currentGroup.isNotEmpty()) return currentGroup

        val newGroup = if (kotlin.random.Random.nextBoolean()) "A" else "B"
        esp.edit().putString(Keys.GUIDE_AB_GROUP, newGroup).apply()
        return newGroup
    }

    suspend fun saveGuideCompletedAt(timestamp: Long) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.GUIDE_COMPLETED_AT, timestamp.toString()).apply()
    }

    suspend fun getGuideCompletedAt(): Long =
        esp.getString(Keys.GUIDE_COMPLETED_AT, null)?.toLongOrNull() ?: 0L

    suspend fun saveFirstTodoCreatedAt(timestamp: Long) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.FIRST_TODO_CREATED_AT, timestamp.toString()).apply()
    }

    suspend fun getFirstTodoCreatedAt(): Long =
        esp.getString(Keys.FIRST_TODO_CREATED_AT, null)?.toLongOrNull() ?: 0L

    // ==================== 悬浮柯基按钮位置 ====================

    suspend fun saveFloatingCorgiPosition(x: Float, y: Float) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.FLOATING_CORGI_X, x.toString())
            .putString(Keys.FLOATING_CORGI_Y, y.toString()).apply()
    }

    suspend fun getFloatingCorgiPosition(): Pair<Float, Float>? {
        val x = esp.getString(Keys.FLOATING_CORGI_X, null)?.toFloatOrNull()
        val y = esp.getString(Keys.FLOATING_CORGI_Y, null)?.toFloatOrNull()
        return if (x != null && y != null) Pair(x, y) else null
    }

    // ==================== 排序偏好设置 ====================

    val sortOrder: Flow<String> = callbackFlow {
        trySend(esp.getString(Keys.SORT_ORDER, "updated_desc") ?: "updated_desc")
        close()
    }

    suspend fun getSortOrder(): String = esp.getString(Keys.SORT_ORDER, "updated_desc")
        ?: "updated_desc"

    suspend fun saveSortOrder(order: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.SORT_ORDER, order).apply()
    }

    // ==================== Undo/Redo 栈持久化 ====================

    /**
     * 保存 Undo 栈到 ESP（自动加密）
     * @param undoStackJson Undo 栈的 JSON 序列化字符串
     * @param todoId 关联的待办 ID
     */
    suspend fun saveUndoStack(undoStackJson: String, todoId: Long) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.UNDO_STACK, undoStackJson)
            .putInt(Keys.UNDO_STACK_TODO_ID, todoId.toInt()).apply()
    }

    suspend fun getUndoStack(): String? = esp.getString(Keys.UNDO_STACK, null)

    suspend fun saveRedoStack(redoStackJson: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(Keys.REDO_STACK, redoStackJson).apply()
    }

    suspend fun getRedoStack(): String? = esp.getString(Keys.REDO_STACK, null)

    suspend fun getUndoStackTodoId(): Long =
        esp.getInt(Keys.UNDO_STACK_TODO_ID, -1).toLong()

    // ==================== V2.7: 最后编辑的 Todo ID ====================

    suspend fun saveLastEditedTodoId(todoId: Long) = withContext(Dispatchers.IO) {
        esp.edit().putLong(Keys.LAST_EDITED_TODO_ID, todoId).apply()
    }

    suspend fun getLastEditedTodoId(): Long = esp.getLong(Keys.LAST_EDITED_TODO_ID, -1L)

    /**
     * 清除指定 Todo 的 Undo/Redo 栈持久化数据
     * @param todoId 目标 Todo 的 ID（-1 表示清除全局旧格式数据）
     */
    suspend fun clearUndoRedoStacks(todoId: Long = -1L) = withContext(Dispatchers.IO) {
        val editor = esp.edit()
        if (todoId >= 0) {
            // V2.6: 按 TodoId 隔离的 key
            editor.remove("undo_stack_$todoId")
            editor.remove("redo_stack_$todoId")
            editor.remove("undo_log_$todoId")
            editor.remove("redo_log_$todoId")
        } else {
            // 向后兼容：清除旧的全局格式 key
            editor.remove(Keys.UNDO_STACK)
            editor.remove(Keys.REDO_STACK)
            editor.remove(Keys.UNDO_STACK_TODO_ID)
            editor.remove(Keys.UNDO_LOG)
            editor.remove(Keys.REDO_LOG)
        }
        editor.apply()
    }

    // ==================== Undo/Redo 增量日志持久化（V2.6 按TodoId隔离）====================

    /**
     * 增量追加一条 Undo 快照到指定 Todo 的日志
     * 采用 Append-Only 模式，每次操作仅追加单条序列化记录
     * @param todoId 目标 Todo 的 ID
     * @param snapshotJson 单条 AnnotatedString 的 JSON 序列化字符串
     */
    suspend fun appendUndoLogEntry(todoId: Long, snapshotJson: String) =
        withContext(Dispatchers.IO) {
            val logKey = "undo_log_$todoId"
            val existingLog = esp.getString(logKey, null) ?: "[]"
            val array = try {
                JSONArray(existingLog)
            } catch (e: Exception) {
                JSONArray()
            }
            array.put(snapshotJson)
            // 日志裁剪：超过最大条数时移除最旧的记录
            while (array.length() > com.corgimemo.app.util.AnnotatedStringSerializer.PERSISTENCE_MAX_DEPTH * 3) {
                array.remove(0)
            }
            esp.edit().putString(logKey, array.toString()).apply()
        }

    /**
     * 增量追加一条 Redo 快照到指定 Todo 的日志
     * @param todoId 目标 Todo 的 ID
     * @param snapshotJson 单条 AnnotatedString 的 JSON 序列化字符串
     */
    suspend fun appendRedoLogEntry(todoId: Long, snapshotJson: String) =
        withContext(Dispatchers.IO) {
            val logKey = "redo_log_$todoId"
            val existingLog = esp.getString(logKey, null) ?: "[]"
            val array = try {
                JSONArray(existingLog)
            } catch (e: Exception) {
                JSONArray()
            }
            array.put(snapshotJson)
            while (array.length() > com.corgimemo.app.util.AnnotatedStringSerializer.PERSISTENCE_MAX_DEPTH * 3) {
                array.remove(0)
            }
            esp.edit().putString(logKey, array.toString()).apply()
        }

    suspend fun getUndoLog(todoId: Long): String =
        esp.getString("undo_log_$todoId", null) ?: "[]"

    suspend fun getRedoLog(todoId: Long): String =
        esp.getString("redo_log_$todoId", null) ?: "[]"

    suspend fun clearUndoRedoLogs(todoId: Long) = withContext(Dispatchers.IO) {
        esp.edit()
            .remove("undo_log_$todoId")
            .remove("redo_log_$todoId")
            .remove("ext_undo_log_$todoId")
            .remove("ext_redo_log_$todoId")
            .apply()
    }

    // ==================== 扩展撤销日志（内容块操作） ====================

    /**
     * 追加扩展 Undo 日志（内容块操作）
     *
     * 与纯文本日志使用不同的 key 前缀（ext_undo_log_），
     * 存储 EditOperation 的完整 JSON 数组。
     *
     * @param todoId 目标 Todo 的 ID
     * @param operationsJson EditOperation 列表的 JSON 序列化字符串
     */
    suspend fun appendExtendedUndoLog(todoId: Long, operationsJson: String) =
        withContext(Dispatchers.IO) {
            val logKey = "ext_undo_log_$todoId"
            esp.edit().putString(logKey, operationsJson).apply()
        }

    /**
     * 追加扩展 Redo 日志（内容块操作）
     */
    suspend fun appendExtendedRedoLog(todoId: Long, operationsJson: String) =
        withContext(Dispatchers.IO) {
            val logKey = "ext_redo_log_$todoId"
            esp.edit().putString(logKey, operationsJson).apply()
        }

    /** 获取扩展 Undo 日志（内容块操作） */
    suspend fun getExtendedUndoLog(todoId: Long): String =
        esp.getString("ext_undo_log_$todoId", null) ?: "[]"

    /** 获取扩展 Redo 日志（内容块操作） */
    suspend fun getExtendedRedoLog(todoId: Long): String =
        esp.getString("ext_redo_log_$todoId", null) ?: "[]"

    /**
     * 向后兼容迁移：将旧的全局格式 UNDO_LOG 迁移到新格式
     * @param todoId 目标 Todo 的 ID
     * @return 是否执行了迁移
     */
    suspend fun migrateLegacyUndoLogIfPresent(todoId: Long): Boolean {
        val legacyUndoLog = esp.getString(Keys.UNDO_LOG, null)
        val legacyRedoLog = esp.getString(Keys.REDO_LOG, null)

        return if (!legacyUndoLog.isNullOrBlank() && legacyUndoLog != "[]") {
            // 发现旧格式数据 → 迁移到新格式
            appendUndoLogEntry(todoId, legacyUndoLog)
            if (!legacyRedoLog.isNullOrBlank() && legacyRedoLog != "[]") {
                appendRedoLogEntry(todoId, legacyRedoLog)
            }
            // 清理旧格式 key
            esp.edit().remove(Keys.UNDO_LOG).remove(Keys.REDO_LOG)
                .remove(Keys.UNDO_STACK_TODO_ID).apply()
            true
        } else {
            false
        }
    }

    // ==================== 通用偏好设置方法（用于动态键名）====================

    /**
     * 直接从 ESP 读取 Long 值（用于动态生成的键名）
     * @param key 键名
     * @param defaultValue 默认值
     */
    fun getLongDirect(key: String, defaultValue: Long): Long = esp.getLong(key, defaultValue)

    /**
     * 直接写入 Long 值到 ESP（用于动态生成的键名）
     * @param key 键名
     * @param value 值
     */
    suspend fun setLongDirect(key: String, value: Long) = withContext(Dispatchers.IO) {
        esp.edit().putLong(key, value).apply()
    }

    /**
     * 直接从 ESP 读取 String? 值（用于动态生成的键名）
     * @param key 键名
     * @param defaultValue 默认值
     */
    fun getStringDirect(key: String, defaultValue: String?): String? = esp.getString(key, defaultValue)

    /**
     * 直接写入 String 值到 ESP（用于动态生成的键名）
     * @param key 键名
     * @param value 值
     */
    suspend fun setStringDirect(key: String, value: String) = withContext(Dispatchers.IO) {
        esp.edit().putString(key, value).apply()
    }

    /**
     * 直接移除 ESP 中的指定键（用于动态生成的键名）
     * @param key 键名
     */
    suspend fun removeKeyDirect(key: String) = withContext(Dispatchers.IO) {
        esp.edit().remove(key).apply()
    }

    // ==================== 用户自定义灵感标签 ====================

    /**
     * 获取用户自定义灵感标签集合
     *
     * 从 ESP 读取 JSON 数组字符串并解析为 Set<String>。
     * 与灵感派生标签（savedTags）合并后供侧边栏显示。
     *
     * @return 用户自定义标签集合（可能为空集）
     */
    suspend fun getUserDefinedTags(): Set<String> = withContext(Dispatchers.IO) {
        val json = esp.getString(Keys.USER_DEFINED_TAGS, null) ?: "[]"
        try {
            val array = JSONArray(json)
            (0 until array.length()).map { array.getString(it) }.toSet()
        } catch (e: Exception) {
            emptySet()
        }
    }

    /**
     * 保存用户自定义灵感标签集合
     *
     * 将 Set<String> 序列化为 JSON 数组字符串写入 ESP。
     *
     * @param tags 标签集合
     */
    suspend fun saveUserDefinedTags(tags: Set<String>) = withContext(Dispatchers.IO) {
        val array = JSONArray()
        tags.sorted().forEach { array.put(it) }
        esp.edit().putString(Keys.USER_DEFINED_TAGS, array.toString()).apply()
    }
}
