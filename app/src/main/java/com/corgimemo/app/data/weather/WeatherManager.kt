package com.corgimemo.app.data.weather

/**
 * 天气状况枚举
 */
enum class WeatherCondition {
    /** 晴天 */
    SUNNY,
    /** 雨天 */
    RAINY,
    /** 多云 */
    CLOUDY,
    /** 雪天 */
    SNOWY,
    /** 未知 */
    UNKNOWN
}

/**
 * 天气信息数据类
 *
 * @property condition 天气状况
 * @property temperature 温度（可选，摄氏度）
 */
data class WeatherInfo(
    val condition: WeatherCondition,
    val temperature: Int? = null
)

/**
 * 天气数据提供者接口
 * 使用可插拔设计，便于后续接入不同的天气服务
 */
interface WeatherProvider {
    /**
     * 获取当前天气信息
     *
     * @return 天气信息，如果获取失败返回 null
     */
    suspend fun getCurrentWeather(): WeatherInfo?
}

/**
 * 默认天气提供者
 * 占位实现，始终返回 null（用于未接入天气服务时的降级）
 */
class DefaultWeatherProvider : WeatherProvider {
    override suspend fun getCurrentWeather(): WeatherInfo? = null
}

/**
 * 天气管理器
 * 负责协调天气数据的获取和缓存
 */
object WeatherManager {

    private var provider: WeatherProvider = DefaultWeatherProvider()

    /**
     * 设置天气数据提供者
     * 用于后续接入第三方天气 API 时替换实现
     *
     * @param provider 天气数据提供者
     */
    fun setProvider(provider: WeatherProvider) {
        this.provider = provider
    }

    /**
     * 获取当前天气信息
     * 获取失败时自动降级为 null
     *
     * @return 天气信息，如果不可用返回 null
     */
    suspend fun getWeatherInfo(): WeatherInfo? {
        return try {
            provider.getCurrentWeather()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 根据天气状况获取对应的提示语
     *
     * @param weather 天气信息
     * @return 天气提示语，如果天气未知返回空字符串
     */
    fun getWeatherMessage(weather: WeatherInfo): String {
        return when (weather.condition) {
            WeatherCondition.SUNNY -> "☀️ 今天天气不错"
            WeatherCondition.RAINY -> "🌧️ 记得带伞哦"
            WeatherCondition.CLOUDY -> "☁️ 多云天气"
            WeatherCondition.SNOWY -> "❄️ 下雪啦，注意保暖"
            WeatherCondition.UNKNOWN -> ""
        }
    }

    /**
     * 获取天气对应的 emoji
     *
     * @param condition 天气状况
     * @return 对应的 emoji 字符串
     */
    fun getWeatherEmoji(condition: WeatherCondition): String {
        return when (condition) {
            WeatherCondition.SUNNY -> "☀️"
            WeatherCondition.RAINY -> "🌧️"
            WeatherCondition.CLOUDY -> "☁️"
            WeatherCondition.SNOWY -> "❄️"
            WeatherCondition.UNKNOWN -> "🌤️"
        }
    }
}
