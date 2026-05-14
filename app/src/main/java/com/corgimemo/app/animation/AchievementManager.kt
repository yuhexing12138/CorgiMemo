package com.corgimemo.app.animation

import org.json.JSONArray

/**
 * 成就 ID 常量
 */
object AchievementId {
    const val SCHOLAR_HAT = "scholar_hat"
    const val TIE = "tie"
    const val CROWN = "crown"
    const val CAPE = "cape"
    const val ANGEL_WINGS = "angel_wings"
}

/**
 * 装扮 ID 常量
 */
object OutfitId {
    const val DEFAULT = "default"
    const val SCHOLAR_HAT = "scholar_hat"
    const val TIE = "tie"
    const val CROWN = "crown"
    const val CAPE = "cape"
    const val ANGEL_WINGS = "angel_wings"
}

/**
 * 成就数据类
 *
 * @property id 成就 ID
 * @property name 成就名称
 * @property description 成就描述
 * @property conditionText 解锁条件描述
 * @property outfitId 解锁的装扮 ID
 * @property target 目标值（用于进度显示）
 */
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val conditionText: String,
    val outfitId: String?,
    val target: Int
)

/**
 * 成就管理器
 * 负责成就定义、条件检测、解锁状态管理
 *
 * 成就列表：
 * 1. 学霸 - 完成 100 个学习类任务 → 解锁学士帽
 * 2. 职场精英 - 完成 100 个工作类任务 → 解锁领带
 * 3. 坚持不懈 - 连续 30 天完成任务 → 解锁皇冠
 * 4. 勤劳天使 - 累计完成 500 个任务 → 解锁天使翅膀
 * 5. 全成就大师 - 解锁其他 4 个成就 → 解锁披风
 */
object AchievementManager {

    const val TARGET_SCHOLAR_HAT = 100
    const val TARGET_TIE = 100
    const val TARGET_CROWN = 30
    const val TARGET_ANGEL_WINGS = 500

    /**
     * 所有成就定义列表
     */
    val allAchievements: List<Achievement> = listOf(
        Achievement(
            id = AchievementId.SCHOLAR_HAT,
            name = "学霸",
            description = "勤奋学习的小柯基",
            conditionText = "完成 100 个学习类任务",
            outfitId = OutfitId.SCHOLAR_HAT,
            target = TARGET_SCHOLAR_HAT
        ),
        Achievement(
            id = AchievementId.TIE,
            name = "职场精英",
            description = "工作小能手",
            conditionText = "完成 100 个工作类任务",
            outfitId = OutfitId.TIE,
            target = TARGET_TIE
        ),
        Achievement(
            id = AchievementId.CROWN,
            name = "坚持不懈",
            description = "30 天如一日",
            conditionText = "连续 30 天完成任务",
            outfitId = OutfitId.CROWN,
            target = TARGET_CROWN
        ),
        Achievement(
            id = AchievementId.ANGEL_WINGS,
            name = "勤劳天使",
            description = "任务终结者",
            conditionText = "累计完成 500 个任务",
            outfitId = OutfitId.ANGEL_WINGS,
            target = TARGET_ANGEL_WINGS
        ),
        Achievement(
            id = AchievementId.CAPE,
            name = "全成就大师",
            description = "最强柯基王者",
            conditionText = "解锁其他 4 个成就",
            outfitId = OutfitId.CAPE,
            target = 4
        )
    )

    /**
     * 获取成就基础列表（不包含 CAPE，CAPE 是复合成就）
     */
    val basicAchievements: List<Achievement>
        get() = allAchievements.filter { it.id != AchievementId.CAPE }

    /**
     * 获取所有成就 ID
     */
    val allAchievementIds: List<String>
        get() = allAchievements.map { it.id }

    /**
     * 基础成就 ID 列表
     */
    val basicAchievementIds: List<String>
        get() = basicAchievements.map { it.id }

    /**
     * 根据 ID 获取成就
     *
     * @param id 成就 ID
     * @return 成就对象，如果不存在返回 null
     */
    fun getAchievementById(id: String): Achievement? {
        return allAchievements.find { it.id == id }
    }

    /**
     * 获取成就对应的装扮 ID
     *
     * @param achievementId 成就 ID
     * @return 装扮 ID
     */
    fun getOutfitForAchievement(achievementId: String): String? {
        return getAchievementById(achievementId)?.outfitId
    }

    /**
     * 检测新解锁的成就
     *
     * @param learningCompleted 学习类任务完成数
     * @param workCompleted 工作类任务完成数
     * @param consecutiveDays 连续完成天数
     * @param totalCompleted 累计完成任务数
     * @param unlockedAchievementsJson 已解锁成就 JSON 字符串
     * @return 新解锁的成就列表
     */
    fun checkNewAchievements(
        learningCompleted: Int,
        workCompleted: Int,
        consecutiveDays: Int,
        totalCompleted: Int,
        unlockedAchievementsJson: String
    ): List<Achievement> {
        val unlockedIds = parseAchievementIds(unlockedAchievementsJson)
        val newAchievements = mutableListOf<Achievement>()

        if (!unlockedIds.contains(AchievementId.SCHOLAR_HAT) && learningCompleted >= TARGET_SCHOLAR_HAT) {
            getAchievementById(AchievementId.SCHOLAR_HAT)?.let { newAchievements.add(it) }
        }

        if (!unlockedIds.contains(AchievementId.TIE) && workCompleted >= TARGET_TIE) {
            getAchievementById(AchievementId.TIE)?.let { newAchievements.add(it) }
        }

        if (!unlockedIds.contains(AchievementId.CROWN) && consecutiveDays >= TARGET_CROWN) {
            getAchievementById(AchievementId.CROWN)?.let { newAchievements.add(it) }
        }

        if (!unlockedIds.contains(AchievementId.ANGEL_WINGS) && totalCompleted >= TARGET_ANGEL_WINGS) {
            getAchievementById(AchievementId.ANGEL_WINGS)?.let { newAchievements.add(it) }
        }

        if (!unlockedIds.contains(AchievementId.CAPE)) {
            val newUnlockedIds = unlockedIds + newAchievements.map { it.id }
            val basicUnlockedCount = basicAchievementIds.count { newUnlockedIds.contains(it) }
            if (basicUnlockedCount >= 4) {
                getAchievementById(AchievementId.CAPE)?.let { newAchievements.add(it) }
            }
        }

        return newAchievements
    }

    /**
     * 检测是否解锁 CAPE 成就
     * CAPE 成就需要解锁其他 4 个基础成就
     *
     * @param unlockedAchievementsJson 已解锁成就 JSON 字符串
     * @return 是否应该解锁 CAPE
     */
    fun shouldUnlockCape(unlockedAchievementsJson: String): Boolean {
        val unlockedIds = parseAchievementIds(unlockedAchievementsJson)
        val basicUnlockedCount = basicAchievementIds.count { unlockedIds.contains(it) }
        return basicUnlockedCount >= 4 && !unlockedIds.contains(AchievementId.CAPE)
    }

    /**
     * 检查成就是否已解锁
     *
     * @param achievementId 成就 ID
     * @param unlockedAchievementsJson 已解锁成就 JSON 字符串
     * @return 是否已解锁
     */
    fun isAchievementUnlocked(achievementId: String, unlockedAchievementsJson: String): Boolean {
        val unlockedIds = parseAchievementIds(unlockedAchievementsJson)
        return unlockedIds.contains(achievementId)
    }

    /**
     * 获取所有成就及解锁状态
     *
     * @param unlockedAchievementsJson 已解锁成就 JSON 字符串
     * @return 成就和解锁状态的列表
     */
    fun getAchievementsWithStatus(unlockedAchievementsJson: String): List<Pair<Achievement, Boolean>> {
        val unlockedIds = parseAchievementIds(unlockedAchievementsJson)
        return allAchievements.map { achievement ->
            achievement to unlockedIds.contains(achievement.id)
        }
    }

    /**
     * 获取已解锁成就数量
     *
     * @param unlockedAchievementsJson 已解锁成就 JSON 字符串
     * @return 已解锁数量
     */
    fun getUnlockedCount(unlockedAchievementsJson: String): Int {
        return parseAchievementIds(unlockedAchievementsJson).size
    }

    /**
     * 解析成就 ID 列表
     *
     * @param json JSON 字符串（格式：["id1", "id2"]）
     * @return 成就 ID 列表
     */
    fun parseAchievementIds(json: String): List<String> {
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
     * 将成就 ID 列表转换为 JSON 字符串
     *
     * @param ids 成就 ID 列表
     * @return JSON 字符串
     */
    fun toAchievementIdsJson(ids: List<String>): String {
        return JSONArray(ids).toString()
    }

    /**
     * 添加新解锁的成就到列表
     *
     * @param existingJson 现有的成就 JSON
     * @param newIds 新解锁的成就 ID 列表
     * @return 更新后的 JSON 字符串
     */
    fun addAchievements(existingJson: String, newIds: List<String>): String {
        val existingIds = parseAchievementIds(existingJson).toMutableList()
        for (id in newIds) {
            if (!existingIds.contains(id)) {
                existingIds.add(id)
            }
        }
        return toAchievementIdsJson(existingIds)
    }

    /**
     * 获取成就的当前进度
     *
     * @param achievementId 成就 ID
     * @param learningCompleted 学习类任务完成数
     * @param workCompleted 工作类任务完成数
     * @param consecutiveDays 连续完成天数
     * @param totalCompleted 累计完成任务数
     * @param unlockedAchievementsJson 已解锁成就（用于计算 CAPE 进度）
     * @return 当前进度值
     */
    fun getAchievementProgress(
        achievementId: String,
        learningCompleted: Int,
        workCompleted: Int,
        consecutiveDays: Int,
        totalCompleted: Int,
        unlockedAchievementsJson: String
    ): Int {
        return when (achievementId) {
            AchievementId.SCHOLAR_HAT -> learningCompleted
            AchievementId.TIE -> workCompleted
            AchievementId.CROWN -> consecutiveDays
            AchievementId.ANGEL_WINGS -> totalCompleted
            AchievementId.CAPE -> {
                val unlockedIds = parseAchievementIds(unlockedAchievementsJson)
                basicAchievementIds.count { unlockedIds.contains(it) }
            }
            else -> 0
        }
    }

    /**
     * 获取成就进度百分比
     *
     * @param achievement 成就
     * @param progress 当前进度值
     * @return 进度百分比（0.0 - 1.0）
     */
    fun getProgressPercentage(achievement: Achievement, progress: Int): Float {
        if (achievement.target <= 0) return 1.0f
        return (progress.toFloat() / achievement.target.toFloat()).coerceIn(0f, 1f)
    }
}
