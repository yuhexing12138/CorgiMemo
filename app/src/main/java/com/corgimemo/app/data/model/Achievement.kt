package com.corgimemo.app.data.model

/**
 * 成就条件类型枚举
 */
enum class AchievementCondition {
    /** 首次完成任务 */
    FIRST_COMPLETE,

    /** 累计完成 N 个任务 */
    CUMULATIVE_TOTAL,

    /** 累计完成 N 个学习类任务 */
    CUMULATIVE_STUDY,

    /** 累计完成 N 个工作类任务 */
    CUMULATIVE_WORK,

    /** 累计完成 N 个生活类任务（健身达人等） */
    CUMULATIVE_LIFE,

    /** 连续 N 天完成任务 */
    CONSECUTIVE_DAYS,

    /** 单天完成 N 个任务 */
    DAILY_TOTAL,

    /** 柯基等级达到 N 级 */
    CORGI_LEVEL,

    /** 连续 N 天早起完成任务（8 点前） */
    EARLY_BIRD,

    /** 解锁所有其他成就（复合成就） */
    ALL_ACHIEVEMENTS
}

/**
 * 成就阶段枚举
 * 用于成就墙页面的阶段进度条
 *
 * @property displayName 显示名称
 * @property description 阶段描述
 * @property requiredUnlocked 需要解锁的成就数量
 * @property color 阶段颜色（十六进制）
 */
enum class AchievementStage(
    val displayName: String,
    val description: String,
    val requiredUnlocked: Int,
    val color: Long
) {
    BEGINNER("初见阶段", "一切刚刚开始", 0, 0xFF94A3B8),
    GROWTH("成长阶段", "努力前进中", 4, 0xFF34D399),
    LEAP("飞跃阶段", "快速成长", 7, 0xFF3B82F6),
    PEAK("巅峰阶段", "即将登顶", 11, 0xFFF97316)
}

/**
 * 成就 ID 常量
 */
object NewAchievementId {
    const val FIRST_COMPLETE = "first_complete"
    const val CONSECUTIVE_7_DAYS = "consecutive_7_days"
    const val DAILY_10_TASKS = "daily_10_tasks"
    const val STUDY_50 = "study_50"
    const val FITNESS_30 = "fitness_30"
    const val TOTAL_100 = "total_100"
    const val EARLY_7_DAYS = "early_7_days"
    const val CORGI_LEVEL_5 = "corgi_level_5"
    const val CORGI_LEVEL_10 = "corgi_level_10"
    const val STUDY_100 = "study_100"
    const val WORK_100 = "work_100"
    const val CONSECUTIVE_30_DAYS = "consecutive_30_days"
    const val TOTAL_500 = "total_500"
    const val ALL_ACHIEVEMENTS = "all_achievements"
}

/**
 * 新成就数据类
 *
 * @property id 唯一标识
 * @property name 成就名称
 * @property description 成就描述
 * @property icon 图标 emoji
 * @property condition 达成条件类型
 * @property threshold 达成阈值
 * @property stage 所属阶段
 * @property story 解锁故事文案
 * @property outfitId 关联的装扮 ID（可选）
 * @property unlockedAt 解锁时间戳（null 表示未解锁）
 */
data class Achievement(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val condition: AchievementCondition,
    val threshold: Int,
    val stage: AchievementStage,
    val story: String,
    val outfitId: String? = null,
    val unlockedAt: Long? = null
)

/**
 * 成就定义单例
 * 定义所有 14 个成就
 */
object AchievementDefinition {

    /**
     * 所有成就列表（共 14 个）
     */
    val allAchievements: List<Achievement> = listOf(
        // === 初见阶段（0-3 个成就）===
        Achievement(
            id = NewAchievementId.FIRST_COMPLETE,
            name = "初次见面",
            description = "完成第 1 个任务",
            icon = "🌱",
            condition = AchievementCondition.CUMULATIVE_TOTAL,
            threshold = 1,
            stage = AchievementStage.BEGINNER,
            story = "每一个伟大的旅程，都从迈出第一步开始"
        ),

        // === 成长阶段（4-6 个成就）===
        Achievement(
            id = NewAchievementId.CONSECUTIVE_7_DAYS,
            name = "坚持不懈",
            description = "连续 7 天每天至少完成 1 个任务",
            icon = "🔥",
            condition = AchievementCondition.CONSECUTIVE_DAYS,
            threshold = 7,
            stage = AchievementStage.GROWTH,
            story = "坚持不是因为有希望，而是坚持下去才会看到希望"
        ),
        Achievement(
            id = NewAchievementId.DAILY_10_TASKS,
            name = "效率达人",
            description = "单天完成 10 个任务",
            icon = "⚡",
            condition = AchievementCondition.DAILY_TOTAL,
            threshold = 10,
            stage = AchievementStage.GROWTH,
            story = "效率不是速度，而是用最短的时间完成最重要的事"
        ),
        Achievement(
            id = NewAchievementId.STUDY_50,
            name = "学习之星",
            description = "累计完成 50 个学习类任务",
            icon = "📚",
            condition = AchievementCondition.CUMULATIVE_STUDY,
            threshold = 50,
            stage = AchievementStage.GROWTH,
            story = "知识是最好的投资，每一分努力都在增值"
        ),
        Achievement(
            id = NewAchievementId.FITNESS_30,
            name = "健身达人",
            description = "累计完成 30 个运动/健身类任务",
            icon = "💪",
            condition = AchievementCondition.CUMULATIVE_LIFE,
            threshold = 30,
            stage = AchievementStage.GROWTH,
            story = "身体是革命的本钱，每一次运动都是对自己的投资"
        ),

        // === 飞跃阶段（7-10 个成就）===
        Achievement(
            id = NewAchievementId.TOTAL_100,
            name = "目标达成",
            description = "累计完成 100 个任务",
            icon = "🎯",
            condition = AchievementCondition.CUMULATIVE_TOTAL,
            threshold = 100,
            stage = AchievementStage.LEAP,
            story = "100 不是终点，而是新的起点"
        ),
        Achievement(
            id = NewAchievementId.EARLY_7_DAYS,
            name = "早起达人",
            description = "连续 7 天在早上 8 点前完成至少 1 个任务",
            icon = "⭐",
            condition = AchievementCondition.EARLY_BIRD,
            threshold = 7,
            stage = AchievementStage.LEAP,
            story = "早起的鸟儿有虫吃，早起的柯基有骨头"
        ),
        Achievement(
            id = NewAchievementId.CORGI_LEVEL_5,
            name = "柯基成长",
            description = "柯基等级达到 5 级",
            icon = "🐾",
            condition = AchievementCondition.CORGI_LEVEL,
            threshold = 5,
            stage = AchievementStage.LEAP,
            story = "陪伴是最长情的告白，你陪我长大"
        ),

        // === 巅峰阶段（11-14 个成就）===
        Achievement(
            id = NewAchievementId.CORGI_LEVEL_10,
            name = "柯基大师",
            description = "柯基等级达到 10 级",
            icon = "👑",
            condition = AchievementCondition.CORGI_LEVEL,
            threshold = 10,
            stage = AchievementStage.PEAK,
            story = "从小小的宝宝到成熟的大师，你我共同见证"
        ),
        Achievement(
            id = NewAchievementId.STUDY_100,
            name = "学霸",
            description = "完成 100 个学习类任务 → 解锁学士帽",
            icon = "🎓",
            condition = AchievementCondition.CUMULATIVE_STUDY,
            threshold = 100,
            stage = AchievementStage.PEAK,
            story = "知识改变命运，学霸改变世界",
            outfitId = "scholar_hat"
        ),
        Achievement(
            id = NewAchievementId.WORK_100,
            name = "职场精英",
            description = "完成 100 个工作类任务 → 解锁领带",
            icon = "👔",
            condition = AchievementCondition.CUMULATIVE_WORK,
            threshold = 100,
            stage = AchievementStage.PEAK,
            story = "职场如战场，准备好了吗？",
            outfitId = "tie"
        ),
        Achievement(
            id = NewAchievementId.CONSECUTIVE_30_DAYS,
            name = "坚持不懈 Pro",
            description = "连续 30 天完成任务 → 解锁皇冠",
            icon = "🏆",
            condition = AchievementCondition.CONSECUTIVE_DAYS,
            threshold = 30,
            stage = AchievementStage.PEAK,
            story = "30 天如一日，自律让你自由",
            outfitId = "crown"
        ),
        Achievement(
            id = NewAchievementId.TOTAL_500,
            name = "勤劳天使",
            description = "累计完成 500 个任务 → 解锁天使翅膀",
            icon = "🪽",
            condition = AchievementCondition.CUMULATIVE_TOTAL,
            threshold = 500,
            stage = AchievementStage.PEAK,
            story = "任务终结者，勤劳的小天使",
            outfitId = "angel_wings"
        ),
        Achievement(
            id = NewAchievementId.ALL_ACHIEVEMENTS,
            name = "全成就大师",
            description = "解锁其他 13 个成就 → 解锁披风",
            icon = "🧥",
            condition = AchievementCondition.ALL_ACHIEVEMENTS,
            threshold = 13,
            stage = AchievementStage.PEAK,
            story = "你是真正的柯基王者",
            outfitId = "cape"
        )
    )

    /**
     * 基础成就列表（不包含复合成就 ALL_ACHIEVEMENTS）
     */
    val basicAchievements: List<Achievement>
        get() = allAchievements.filter { it.id != NewAchievementId.ALL_ACHIEVEMENTS }

    /**
     * 所有成就 ID 列表
     */
    val allAchievementIds: List<String>
        get() = allAchievements.map { it.id }

    /**
     * 基础成就 ID 列表
     */
    val basicAchievementIds: List<String>
        get() = basicAchievements.map { it.id }

    /**
     * 旧成就 ID 到新成就 ID 的映射
     */
    val oldToNewIdMap: Map<String, String> = mapOf(
        "scholar_hat" to NewAchievementId.STUDY_100,
        "tie" to NewAchievementId.WORK_100,
        "crown" to NewAchievementId.CONSECUTIVE_30_DAYS,
        "angel_wings" to NewAchievementId.TOTAL_500,
        "cape" to NewAchievementId.ALL_ACHIEVEMENTS
    )

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
     * @return 装扮 ID，如果没有关联装扮返回 null
     */
    fun getOutfitForAchievement(achievementId: String): String? {
        return getAchievementById(achievementId)?.outfitId
    }

    /**
     * 获取当前阶段
     *
     * @param unlockedCount 已解锁成就数量
     * @return 当前成就阶段
     */
    fun getCurrentStage(unlockedCount: Int): AchievementStage {
        return when {
            unlockedCount >= AchievementStage.PEAK.requiredUnlocked -> AchievementStage.PEAK
            unlockedCount >= AchievementStage.LEAP.requiredUnlocked -> AchievementStage.LEAP
            unlockedCount >= AchievementStage.GROWTH.requiredUnlocked -> AchievementStage.GROWTH
            else -> AchievementStage.BEGINNER
        }
    }

    /**
     * 将旧成就 ID 映射到新 ID
     *
     * @param oldId 旧成就 ID
     * @return 新成就 ID，如果没有映射返回 null
     */
    fun mapOldToNewId(oldId: String): String? {
        return oldToNewIdMap[oldId]
    }

    /**
     * 总成就数量
     */
    const val TOTAL_COUNT = 14

    /**
     * 基础成就数量（不包含复合成就）
     */
    const val BASIC_COUNT = 13
}
