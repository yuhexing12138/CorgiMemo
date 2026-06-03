package com.corgimemo.app.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 语言切换工具类
 *
 * 提供应用内语言切换功能，
 * 支持简体中文、英文、日文三种语言。
 * 使用 Android 原生的多语言资源机制实现语言切换。
 *
 * **支持的语言**:
 * - `zh_CN`: 简体中文（默认）
 * - `en`: English（英语）
 * - `ja`: 日本語（日语）
 *
 * **使用示例**:
 * ```kotlin
 * // 切换到英文
 * LocaleHelper.setLocale(context, LocaleHelper.LANGUAGE_EN)
 *
 * // 获取当前语言代码
 * val currentLang = LocaleHelper.getCurrentLanguage(context)
 *
 * // 重启 Activity 以应用新语言
 * LocaleHelper.restartActivity(context)
 * ```
 *
 * **工作原理**:
 * 1. 用户选择目标语言后，调用 `setLocale()` 更新 Configuration
 * 2. 将语言偏好保存到 DataStore/SharedPreferences
 * 3. 重启当前 Activity 使所有 Composable 重新组合，加载新的 strings.xml
 */
object LocaleHelper {

    /** 语言常量定义 */
    const val LANGUAGE_ZH_CN = "zh"
    const val LANGUAGE_EN = "en"
    const val LANGUAGE_JA = "ja"
    const val LANGUAGE_SYSTEM = "system" /** 跟随系统 */

    /** DataStore 存储键名 */
    private const val PREF_KEY_LANGUAGE = "app_language"

    /** 支持的语言列表 */
    val SUPPORTED_LANGUAGES = listOf(
        LanguageOption(LANGUAGE_ZH_CN, "简体中文", "🇨🇳"),
        LanguageOption(LANGUAGE_EN, "English", "🇺🇸"),
        LanguageOption(LANGUAGE_JA, "日本語", "🇯🇵")
    )

    /**
     * 语言选项数据类
     *
     * @param code 语言代码（ISO 639-1）
     * @param displayName 显示名称（使用该语言本身）
     * @param flagEmoji 国旗 emoji（可选）
     */
    data class LanguageOption(
        val code: String,
        val displayName: String,
        val flagEmoji: String = ""
    )

    /**
     * 设置应用语言
     *
     * 修改 Context 的 Configuration 并保存用户偏好，
     * 使应用立即切换到指定语言。
     *
     * @param context 应用上下文（建议使用 ApplicationContext）
     * @param languageCode 目标语言代码（"zh"/"en"/"ja"/"system"）
     */
    fun setLocale(context: Context, languageCode: String) {
        val locale = when (languageCode) {
            LANGUAGE_SYSTEM -> Locale.getDefault() /** 使用系统默认语言 */
            else -> Locale(languageCode)
        }

        /** 设置默认 Locale（影响 Java API 的本地化行为） */
        Locale.setDefault(locale)

        /** 更新 Configuration 的 locale 属性 */
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        /** 应用新的配置到 Resources */
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        /** TODO: 将用户选择持久化到 DataStore 或 SharedPreferences */
        // val dataStore = context.dataStore
        // dataStore.edit { preferences ->
        //     preferences[stringPreferencesKey(PREF_KEY_LANGUAGE)] = languageCode
        // }
    }

    /**
     * 获取当前应用语言代码
     *
     * 从 Configuration 中读取当前 locale 的语言部分。
     *
     * @param context 应用上下文
     * @return 当前语言代码字符串（如 "zh"、"en"、"ja"）
     */
    fun getCurrentLanguage(context: Context): String {
        return context.resources.configuration.locale.language
    }

    /**
     * 检查是否为中文环境
     *
     * @param context 应用上下文
     * @return 如果当前语言是中文返回 true
     */
    fun isChinese(context: Context): Boolean {
        val lang = getCurrentLanguage(context)
        return lang == "zh" || lang == "zh-CN" || lang == "zh_TW" || lang == "zh_HK"
    }

    /**
     * 检查是否为英文环境
     *
     * @param context 应用上下文
     * @return 如果当前语言是英文返回 true
     */
    fun isEnglish(context: Context): Boolean {
        return getCurrentLanguage(context) == "en"
    }

    /**
     * 检查是否为日文环境
     *
     * @param context 应用上下文
     * @return 如果当前语言是日文返回 true
     */
    fun isJapanese(context: Context): Boolean {
        val lang = getCurrentLanguage(context)
        return lang == "ja" || lang == "ja-JP"
    }

    /**
     * 根据语言代码获取对应的 Locale 对象
     *
     * @param languageCode 语言代码
     * @return 对应的 Locale 实例
     */
    fun getLocale(languageCode: String): Locale {
        return when (languageCode) {
            LANGUAGE_ZH_CN -> Locale.SIMPLIFIED_CHINESE
            LANGUAGE_EN -> Locale.ENGLISH
            LANGUAGE_JA -> Locale.JAPANESE
            else -> Locale.getDefault()
        }
    }

    /**
     * 重启 Activity 以应用语言变更
     *
     * 通过重建 Activity 使所有 Composable 重新组合，
     * 从而加载新的语言资源文件。
     *
     * 注意：此方法应在 setLocale() 之后调用。
     *
     * @param context Activity 上下文
     */
    fun restartActivity(context: Context) {
        if (context is androidx.appcompat.app.AppCompatActivity) {
            /** 重新创建 Activity（不添加动画） */
            context.recreate()
        }
    }

    /**
     * 格式化日期时间为本地化字符串
     *
     * 根据当前语言环境返回格式化的日期时间文本。
     *
     * @param timestamp 时间戳（毫秒）
     * @param pattern 日期格式模式（默认 "yyyy-MM-dd HH:mm"）
     * @return 本地化的日期时间字符串
     */
    fun formatDateTime(timestamp: Long, pattern: String = "yyyy-MM-dd HH:mm"): String {
        val sdf = java.text.SimpleDateFormat(pattern, Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    /**
     * 获取本地化的相对时间描述
     *
     * 将时间戳转换为友好的相对时间描述：
     * - 中文："刚刚"、"5分钟前"、"昨天"、"3天前"
     * - 英文："Just now","5 min ago","Yesterday","3 days ago"
     * - 日文："たった今"、"5分前"、"昨日"、"3日前"
     *
     * @param context 应用上下文
     * @param timestamp 时间戳（毫秒）
     * @return 相对时间描述字符串
     */
    fun getRelativeTimeSpan(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000L -> {
                /** 少于 1 分钟 */
                when {
                    isChinese(context) -> "刚刚"
                    isJapanese(context) -> "たった今"
                    else -> "Just now"
                }
            }
            diff < 3600_000L -> {
                /** 少于 1 小时 */
                val minutes = diff / 60_000L
                when {
                    isChinese(context) -> "${minutes}分钟前"
                    isJapanese(context) -> "${minutes}分前"
                    else -> "$minutes min ago"
                }
            }
            diff < 86400_000L -> {
                /** 少于 24 小时 */
                val hours = diff / 3600_000L
                when {
                    isChinese(context) -> "${hours}小时前"
                    isJapanese(context) -> "${hours}時間前"
                    else -> "$hours hours ago"
                }
            }
            diff < 172800_000L -> {
                /** 昨天 (48小时内) */
                when {
                    isChinese(context) -> "昨天"
                    isJapanese(context) -> "昨日"
                    else -> "Yesterday"
                }
            }
            else -> {
                /** 更早：显示具体日期 */
                formatDateTime(timestamp, "MM-dd HH:mm")
            }
        }
    }
}
