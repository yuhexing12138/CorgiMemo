package com.corgimemo.app.animation

import org.json.JSONArray

/**
 * 装扮数据类
 *
 * @property id 装扮 ID
 * @property name 装扮名称
 * @property description 装扮描述
 * @property isDefault 是否为默认装扮
 * @property isHoliday 是否为节日装扮
 */
data class Outfit(
    val id: String,
    val name: String,
    val description: String,
    val isDefault: Boolean = false,
    val isHoliday: Boolean = false
)

/**
 * 装扮管理器
 * 负责装扮定义、解锁状态管理、装扮切换
 *
 * 装扮列表：
 * 1. 默认 - 无装扮
 * 2. 学士帽 - 完成 100 个学习类任务解锁
 * 3. 领带 - 完成 100 个工作类任务解锁
 * 4. 皇冠 - 连续 30 天完成任务解锁
 * 5. 天使翅膀 - 累计完成 500 个任务解锁
 * 6. 披风 - 解锁其他 4 个成就解锁
 * 7-15. 节日装扮（节日期间自动显示）
 */
object OutfitManager {

    /**
     * 所有装扮定义列表
     */
    val allOutfits: List<Outfit> = listOf(
        Outfit(
            id = OutfitId.DEFAULT,
            name = "默认",
            description = "最可爱的原始柯基",
            isDefault = true
        ),
        Outfit(
            id = OutfitId.SCHOLAR_HAT,
            name = "学士帽",
            description = "学霸的专属装扮",
            isDefault = false
        ),
        Outfit(
            id = OutfitId.TIE,
            name = "领带",
            description = "职场精英的象征",
            isDefault = false
        ),
        Outfit(
            id = OutfitId.CROWN,
            name = "皇冠",
            description = "坚持不懈的奖励",
            isDefault = false
        ),
        Outfit(
            id = OutfitId.ANGEL_WINGS,
            name = "天使翅膀",
            description = "勤劳的证明",
            isDefault = false
        ),
        Outfit(
            id = OutfitId.CAPE,
            name = "披风",
            description = "全成就大师的荣耀",
            isDefault = false
        ),
        // 节日装扮（节日期间自动显示，无需解锁）
        Outfit(
            id = HolidayOutfitId.NEW_YEAR_HAT,
            name = "派对帽",
            description = "元旦/新年专属装扮",
            isDefault = false,
            isHoliday = true
        ),
        Outfit(
            id = HolidayOutfitId.RED_SCARF,
            name = "红色围巾",
            description = "春节专属装扮",
            isDefault = false,
            isHoliday = true
        ),
        Outfit(
            id = HolidayOutfitId.LANTERN,
            name = "灯笼",
            description = "元宵节专属装扮",
            isDefault = false,
            isHoliday = true
        ),
        Outfit(
            id = HolidayOutfitId.LABOR_HAT,
            name = "工作帽",
            description = "劳动节专属装扮",
            isDefault = false,
            isHoliday = true
        ),
        Outfit(
            id = HolidayOutfitId.DRAGON_HAT,
            name = "龙舟帽",
            description = "端午节专属装扮",
            isDefault = false,
            isHoliday = true
        ),
        Outfit(
            id = HolidayOutfitId.FLAG,
            name = "国旗",
            description = "国庆节专属装扮",
            isDefault = false,
            isHoliday = true
        ),
        Outfit(
            id = HolidayOutfitId.MOON_DECOR,
            name = "月亮装饰",
            description = "中秋节专属装扮",
            isDefault = false,
            isHoliday = true
        ),
        Outfit(
            id = HolidayOutfitId.SCARF,
            name = "围巾",
            description = "冬至专属装扮",
            isDefault = false,
            isHoliday = true
        ),
        Outfit(
            id = HolidayOutfitId.CHRISTMAS_HAT,
            name = "圣诞帽",
            description = "圣诞节专属装扮",
            isDefault = false,
            isHoliday = true
        )
    )

    /**
     * 成就与装扮的映射关系
     */
    private val achievementToOutfitMap: Map<String, String> = mapOf(
        AchievementId.SCHOLAR_HAT to OutfitId.SCHOLAR_HAT,
        AchievementId.TIE to OutfitId.TIE,
        AchievementId.CROWN to OutfitId.CROWN,
        AchievementId.ANGEL_WINGS to OutfitId.ANGEL_WINGS,
        AchievementId.CAPE to OutfitId.CAPE
    )

    /**
     * 装扮解锁条件描述
     */
    val outfitUnlockConditions: Map<String, String> = mapOf(
        OutfitId.SCHOLAR_HAT to "完成 100 个学习类任务",
        OutfitId.TIE to "完成 100 个工作类任务",
        OutfitId.CROWN to "连续 30 天完成任务",
        OutfitId.ANGEL_WINGS to "累计完成 500 个任务",
        OutfitId.CAPE to "解锁其他 4 个成就"
    )

    /**
     * 获取默认装扮
     */
    val defaultOutfit: Outfit
        get() = allOutfits.first { it.isDefault }

    /**
     * 获取所有可解锁装扮（排除默认和节日装扮）
     */
    val unlockableOutfits: List<Outfit>
        get() = allOutfits.filter { !it.isDefault && !it.isHoliday }

    /**
     * 获取所有节日装扮
     */
    val holidayOutfits: List<Outfit>
        get() = allOutfits.filter { it.isHoliday }

    /**
     * 根据 ID 获取装扮
     *
     * @param id 装扮 ID
     * @return 装扮对象，如果不存在返回 null
     */
    fun getOutfitById(id: String): Outfit? {
        return allOutfits.find { it.id == id }
    }

    /**
     * 根据成就 ID 获取对应的装扮
     *
     * @param achievementId 成就 ID
     * @return 装扮对象，如果没有对应装扮返回 null
     */
    fun getOutfitForAchievement(achievementId: String): Outfit? {
        val outfitId = achievementToOutfitMap[achievementId]
        return outfitId?.let { getOutfitById(it) }
    }

    /**
     * 获取当前节日装扮
     *
     * @param currentTime 当前时间戳
     * @return 节日装扮对象，如果不是节日返回 null
     */
    fun getHolidayOutfit(currentTime: Long = System.currentTimeMillis()): Outfit? {
        val holiday = HolidayManager.getCurrentHoliday(currentTime)
        return holiday?.outfitId?.let { getOutfitById(it) }
    }

    /**
     * 获取装扮对应的解锁条件
     *
     * @param outfitId 装扮 ID
     * @return 解锁条件描述
     */
    fun getUnlockCondition(outfitId: String): String {
        // 节日装扮无需解锁
        if (isHolidayOutfit(outfitId)) {
            return "节日期间自动显示"
        }
        return outfitUnlockConditions[outfitId] ?: "默认装扮"
    }

    /**
     * 判断是否为节日装扮
     *
     * @param outfitId 装扮 ID
     * @return 是否为节日装扮
     */
    fun isHolidayOutfit(outfitId: String): Boolean {
        return getOutfitById(outfitId)?.isHoliday ?: false
    }

    /**
     * 检查装扮是否已解锁
     *
     * @param outfitId 装扮 ID
     * @param unlockedOutfitsJson 已解锁装扮 JSON 字符串
     * @return 是否已解锁
     */
    fun isOutfitUnlocked(outfitId: String, unlockedOutfitsJson: String): Boolean {
        // 默认装扮始终已解锁
        if (outfitId == OutfitId.DEFAULT) return true
        // 节日装扮始终已解锁
        if (isHolidayOutfit(outfitId)) return true
        // 成就装扮需要检查解锁状态
        val unlockedIds = parseOutfitIds(unlockedOutfitsJson)
        return unlockedIds.contains(outfitId)
    }

    /**
     * 获取所有装扮及解锁状态
     *
     * @param unlockedOutfitsJson 已解锁装扮 JSON 字符串
     * @return 装扮和解锁状态的列表
     */
    fun getOutfitsWithStatus(unlockedOutfitsJson: String): List<Pair<Outfit, Boolean>> {
        return allOutfits.map { outfit ->
            outfit to isOutfitUnlocked(outfit.id, unlockedOutfitsJson)
        }
    }

    /**
     * 获取已解锁装扮数量
     *
     * @param unlockedOutfitsJson 已解锁装扮 JSON 字符串
     * @return 已解锁数量（不包含默认）
     */
    fun getUnlockedCount(unlockedOutfitsJson: String): Int {
        return parseOutfitIds(unlockedOutfitsJson).size
    }

    /**
     * 解析装扮 ID 列表
     *
     * @param json JSON 字符串（格式：["id1", "id2"]）
     * @return 装扮 ID 列表
     */
    fun parseOutfitIds(json: String): List<String> {
        return try {
            val jsonArray = JSONArray(json)
            val ids = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                ids.add(jsonArray.getString(i))
            }
            ids
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 将装扮 ID 列表转换为 JSON 字符串
     *
     * @param ids 装扮 ID 列表
     * @return JSON 字符串
     */
    fun toOutfitIdsJson(ids: List<String>): String {
        return JSONArray(ids).toString()
    }

    /**
     * 添加新解锁的装扮到列表
     *
     * @param existingJson 现有的装扮 JSON
     * @param newIds 新解锁的装扮 ID 列表
     * @return 更新后的 JSON 字符串
     */
    fun addOutfits(existingJson: String, newIds: List<String>): String {
        val existingIds = parseOutfitIds(existingJson).toMutableList()
        for (id in newIds) {
            if (id != OutfitId.DEFAULT && !existingIds.contains(id)) {
                existingIds.add(id)
            }
        }
        return toOutfitIdsJson(existingIds)
    }

    /**
     * 根据成就列表获取需要解锁的装扮列表
     *
     * @param achievementIds 新解锁的成就 ID 列表
     * @param existingOutfitsJson 已解锁的装扮 JSON
     * @return 需要解锁的装扮 ID 列表
     */
    fun getOutfitsToUnlock(achievementIds: List<String>, existingOutfitsJson: String): List<String> {
        val existingOutfitIds = parseOutfitIds(existingOutfitsJson)
        val outfitsToUnlock = mutableListOf<String>()

        for (achievementId in achievementIds) {
            val outfitId = achievementToOutfitMap[achievementId]
            if (outfitId != null && !existingOutfitIds.contains(outfitId)) {
                outfitsToUnlock.add(outfitId)
            }
        }

        return outfitsToUnlock
    }

    /**
     * 获取当前装扮显示名称
     *
     * @param currentOutfitId 当前装扮 ID（null 表示默认）
     * @return 装扮名称
     */
    fun getCurrentOutfitName(currentOutfitId: String?): String {
        if (currentOutfitId == null) return defaultOutfit.name
        return getOutfitById(currentOutfitId)?.name ?: defaultOutfit.name
    }

    /**
     * 获取当前装扮对象
     *
     * @param currentOutfitId 当前装扮 ID（null 表示默认）
     * @return 装扮对象
     */
    fun getCurrentOutfit(currentOutfitId: String?): Outfit {
        if (currentOutfitId == null) return defaultOutfit
        return getOutfitById(currentOutfitId) ?: defaultOutfit
    }

    /**
     * 判断是否为默认装扮
     *
     * @param outfitId 装扮 ID
     * @return 是否为默认装扮
     */
    fun isDefault(outfitId: String?): Boolean {
        return outfitId == null || outfitId == OutfitId.DEFAULT
    }

    /**
     * 获取装扮的成就解锁来源
     *
     * @param outfitId 装扮 ID
     * @return 成就 ID（如果是默认装扮返回 null）
     */
    fun getAchievementForOutfit(outfitId: String): String? {
        for ((achievementId, outfitId2) in achievementToOutfitMap) {
            if (outfitId2 == outfitId) {
                return achievementId
            }
        }
        return null
    }
}
