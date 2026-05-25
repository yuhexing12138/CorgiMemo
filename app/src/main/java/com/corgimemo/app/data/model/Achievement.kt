package com.corgimemo.app.data.model

import com.corgimemo.app.model.UserType

/**
 * 成就可见性枚举
 * 控制哪些用户可以看到该成就
 */
enum class AchievementVisibility {
    /** 通用成就，所有人可见 */
    ALL,

    /** 上班族专属 */
    WORKER,

    /** 学生专属 */
    STUDENT
}

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

    /** 累计完成 N 个娱乐类任务 */
    CUMULATIVE_ENTERTAINMENT,

    /** 连续 N 天完成任务 */
    CONSECUTIVE_DAYS,

    /** 连续 N 天完成工作类任务 */
    CONSECUTIVE_WORK_DAYS,

    /** 连续 N 天完成学习类任务 */
    CONSECUTIVE_STUDY_DAYS,

    /** 单天完成 N 个任务 */
    DAILY_TOTAL,

    /** 单月完成 N 个工作类任务 */
    MONTHLY_WORK,

    /** 单学期完成 N 个学习类任务 */
    SEMESTER_STUDY,

    /** 柯基等级达到 N 级 */
    CORGI_LEVEL,

    /** 连续 N 天早起完成任务（8 点前） */
    EARLY_BIRD,

    /** 连续 N 天在特定时间前完成任务（22:00前） */
    BEFORE_TIME,

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
    GROWTH("成长阶段", "努力前进中", 5, 0xFF34D399),
    LEAP("飞跃阶段", "快速成长", 10, 0xFF3B82F6),
    PEAK("巅峰阶段", "即将登顶", 16, 0xFFF97316)
}

/**
 * 成就 ID 常量
 */
object NewAchievementId {
    // ========== 通用成就 ==========
    const val FIRST_COMPLETE = "first_complete"
    const val CONSECUTIVE_7_DAYS = "consecutive_7_days"
    const val DAILY_10_TASKS = "daily_10_tasks"
    const val TOTAL_100 = "total_100"
    const val CORGI_LEVEL_5 = "corgi_level_5"
    const val CORGI_LEVEL_10 = "corgi_level_10"
    const val CONSECUTIVE_30_DAYS = "consecutive_30_days"
    const val TOTAL_500 = "total_500"
    const val ALL_ACHIEVEMENTS = "all_achievements"

    // ========== 上班族专属成就 ==========
    const val WORK_FIRST = "work_first"
    const val WORK_PROJECT = "work_project"
    const val WORK_20 = "work_20"
    const val WORK_100 = "work_100"
    const val WORK_MONTHLY_30 = "work_monthly_30"
    const val WORK_EARLY_END = "work_early_end"
    const val WORK_PROMOTION = "work_promotion"

    // ========== 学生专属成就 ==========
    const val STUDY_30 = "study_30"
    const val STUDY_50 = "study_50"
    const val STUDY_100 = "study_100"
    const val STUDY_200 = "study_200"
    const val FITNESS_30 = "fitness_30"
    const val ENTERTAINMENT_20 = "entertainment_20"
    const val EARLY_7_DAYS = "early_7_days"
    const val STUDY_SEMESTER_100 = "study_semester_100"
    const val STUDY_EXAM_WEEK = "study_exam_week"
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
 * @property visibility 可见性（通用/上班族/学生）
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
    val storyTitle: String = "",
    val unlockDialog: String = "",
    val visibility: AchievementVisibility = AchievementVisibility.ALL,
    val outfitId: String? = null,
    val unlockedAt: Long? = null
)

/**
 * 判断该成就是否为重大成就
 * 重大成就解锁时触发全屏庆祝动画 + 柯基跳跃 + 音效
 */
fun Achievement.isMajorAchievement(): Boolean {
    if (outfitId != null) return true
    val consecutiveIds = setOf(
        NewAchievementId.CONSECUTIVE_7_DAYS,
        NewAchievementId.CONSECUTIVE_30_DAYS,
        NewAchievementId.WORK_PROMOTION,
        NewAchievementId.STUDY_EXAM_WEEK,
        NewAchievementId.EARLY_7_DAYS
    )
    if (id in consecutiveIds) return true
    val milestoneIds = setOf(
        NewAchievementId.TOTAL_100,
        NewAchievementId.TOTAL_500,
        NewAchievementId.CORGI_LEVEL_5,
        NewAchievementId.CORGI_LEVEL_10,
        NewAchievementId.ALL_ACHIEVEMENTS,
        NewAchievementId.DAILY_10_TASKS,
        NewAchievementId.FIRST_COMPLETE
    )
    if (id in milestoneIds) return true
    return false
}

/**
 * 成就定义单例
 * 定义所有成就（通用 + 上班族 + 学生）
 */
object AchievementDefinition {

    /**
     * 所有成就列表（共 26 个）
     */
    val allAchievements: List<Achievement> = listOf(
        // ==================== 初见阶段（0-4 个成就）====================
        Achievement(
            id = NewAchievementId.FIRST_COMPLETE,
            name = "初次见面",
            description = "完成第 1 个任务",
            icon = "🌱",
            condition = AchievementCondition.CUMULATIVE_TOTAL,
            threshold = 1,
            stage = AchievementStage.BEGINNER,
            story = "每一个伟大的旅程，都从迈出第一步开始",
            storyTitle = "初次见面！",
            unlockDialog = "嘿！我是你的小柯基～从今天开始，我们一起完成任务，一起成长吧！",
            visibility = AchievementVisibility.ALL
        ),
        Achievement(
            id = NewAchievementId.WORK_FIRST,
            name = "职场新人",
            description = "首次完成工作类任务",
            icon = "🎉",
            condition = AchievementCondition.CUMULATIVE_WORK,
            threshold = 1,
            stage = AchievementStage.BEGINNER,
            story = "职场生涯，从此刻开始",
            storyTitle = "职场新星！",
            unlockDialog = "恭喜完成第一个工作任务！汪～你在职场一定会闪闪发光的！",
            visibility = AchievementVisibility.WORKER
        ),

        // ==================== 成长阶段（5-9 个成就）====================
        Achievement(
            id = NewAchievementId.CONSECUTIVE_7_DAYS,
            name = "坚持不懈",
            description = "连续 7 天每天至少完成 1 个任务",
            icon = "🔥",
            condition = AchievementCondition.CONSECUTIVE_DAYS,
            threshold = 7,
            stage = AchievementStage.GROWTH,
            story = "坚持不是因为有希望，而是坚持下去才会看到希望",
            storyTitle = "坚持的力量！",
            unlockDialog = "你已经连续7天完成任务了！这份坚持让我好感动汪～继续加油！",
            visibility = AchievementVisibility.ALL
        ),
        Achievement(
            id = NewAchievementId.DAILY_10_TASKS,
            name = "效率达人",
            description = "单天完成 10 个任务",
            icon = "⚡",
            condition = AchievementCondition.DAILY_TOTAL,
            threshold = 10,
            stage = AchievementStage.GROWTH,
            story = "效率不是速度，而是用最短的时间完成最重要的事",
            storyTitle = "效率爆棚！",
            unlockDialog = "一天完成10个任务？你也太厉害了吧！我的小短腿都追不上你的速度了！",
            visibility = AchievementVisibility.ALL
        ),
        Achievement(
            id = NewAchievementId.CORGI_LEVEL_5,
            name = "柯基成长",
            description = "柯基等级达到 5 级",
            icon = "🐾",
            condition = AchievementCondition.CORGI_LEVEL,
            threshold = 5,
            stage = AchievementStage.GROWTH,
            story = "陪伴是最长情的告白，你陪我长大",
            storyTitle = "我们一起长大！",
            unlockDialog = "我已经5级啦！都是因为有你的陪伴和努力，谢谢你汪！",
            visibility = AchievementVisibility.ALL
        ),
        Achievement(
            id = NewAchievementId.WORK_PROJECT,
            name = "项目达人",
            description = "累计完成 20 个工作类任务",
            icon = "💼",
            condition = AchievementCondition.CUMULATIVE_WORK,
            threshold = 20,
            stage = AchievementStage.GROWTH,
            story = "一个又一个项目，见证你的成长",
            storyTitle = "项目小能手！",
            unlockDialog = "完成了20个工作项目，你已经是个可靠的职场人了！",
            visibility = AchievementVisibility.WORKER
        ),
        Achievement(
            id = NewAchievementId.STUDY_30,
            name = "笔记达人",
            description = "累计完成 30 个学习类任务",
            icon = "✍️",
            condition = AchievementCondition.CUMULATIVE_STUDY,
            threshold = 30,
            stage = AchievementStage.GROWTH,
            story = "好记性不如烂笔头，每一个字都是进步",
            storyTitle = "笔记小达人！",
            unlockDialog = "30个学习任务完成！你的笔记一定写得比我的爪子印还整齐！",
            visibility = AchievementVisibility.STUDENT
        ),
        Achievement(
            id = NewAchievementId.FITNESS_30,
            name = "运动达人",
            description = "累计完成 30 个运动/健身类任务",
            icon = "🏃",
            condition = AchievementCondition.CUMULATIVE_LIFE,
            threshold = 30,
            stage = AchievementStage.GROWTH,
            story = "生命在于运动，每一滴汗水都是值得的",
            storyTitle = "运动健将！",
            unlockDialog = "30次运动打卡！你的活力让我也想多跑几圈了汪！",
            visibility = AchievementVisibility.STUDENT
        ),

        // ==================== 飞跃阶段（10-15 个成就）====================
        Achievement(
            id = NewAchievementId.TOTAL_100,
            name = "目标达成",
            description = "累计完成 100 个任务",
            icon = "🎯",
            condition = AchievementCondition.CUMULATIVE_TOTAL,
            threshold = 100,
            stage = AchievementStage.LEAP,
            story = "100 不是终点，而是新的起点",
            storyTitle = "百日维新！",
            unlockDialog = "100个任务！我的尾巴摇得快起飞了！你是最棒的任务终结者！",
            visibility = AchievementVisibility.ALL
        ),
        Achievement(
            id = NewAchievementId.EARLY_7_DAYS,
            name = "早起达人",
            description = "连续 7 天在早上 8 点前完成至少 1 个任务",
            icon = "⭐",
            condition = AchievementCondition.EARLY_BIRD,
            threshold = 7,
            stage = AchievementStage.LEAP,
            story = "早起的鸟儿有虫吃，早起的柯基有骨头",
            storyTitle = "早起冠军！",
            unlockDialog = "连续7天早起！太阳都没你勤快～早起的柯基有骨头吃！",
            visibility = AchievementVisibility.STUDENT
        ),
        Achievement(
            id = NewAchievementId.STUDY_50,
            name = "学习之星",
            description = "累计完成 50 个学习类任务",
            icon = "📚",
            condition = AchievementCondition.CUMULATIVE_STUDY,
            threshold = 50,
            stage = AchievementStage.LEAP,
            story = "知识是最好的投资，每一分努力都在增值",
            storyTitle = "学习标兵！",
            unlockDialog = "50个学习任务！知识就是力量，你现在已经是一座移动的图书馆了汪！",
            visibility = AchievementVisibility.STUDENT
        ),
        Achievement(
            id = NewAchievementId.ENTERTAINMENT_20,
            name = "娱乐达人",
            description = "累计完成 20 个娱乐类任务",
            icon = "🎮",
            condition = AchievementCondition.CUMULATIVE_ENTERTAINMENT,
            threshold = 20,
            stage = AchievementStage.LEAP,
            story = "学习娱乐两不误，劳逸结合才是王道",
            storyTitle = "劳逸结合！",
            unlockDialog = "20个娱乐任务！会学习也会玩，这才是完美的人生节奏！",
            visibility = AchievementVisibility.STUDENT
        ),
        Achievement(
            id = NewAchievementId.WORK_20,
            name = "工作能手",
            description = "累计完成 50 个工作类任务",
            icon = "📋",
            condition = AchievementCondition.CUMULATIVE_WORK,
            threshold = 50,
            stage = AchievementStage.LEAP,
            story = "职场如战场，准备好迎接挑战了吗？",
            storyTitle = "工作能手！",
            unlockDialog = "50个工作任务的积累，你已经从新手成长为能手了！",
            visibility = AchievementVisibility.WORKER
        ),
        Achievement(
            id = NewAchievementId.WORK_EARLY_END,
            name = "加班终结者",
            description = "连续 3 天在 22:00 前完成任务",
            icon = "🌙",
            condition = AchievementCondition.BEFORE_TIME,
            threshold = 3,
            stage = AchievementStage.LEAP,
            story = "拒绝无效加班，高效工作，享受生活",
            storyTitle = "准时下班！",
            unlockDialog = "连续3天准时完成任务！拒绝加班，高效生活，这才是职场真谛！",
            visibility = AchievementVisibility.WORKER
        ),

        // ==================== 巅峰阶段（16+ 个成就）====================
        Achievement(
            id = NewAchievementId.CORGI_LEVEL_10,
            name = "柯基大师",
            description = "柯基等级达到 10 级",
            icon = "👑",
            condition = AchievementCondition.CORGI_LEVEL,
            threshold = 10,
            stage = AchievementStage.PEAK,
            story = "从小小的宝宝到成熟的大师，你我共同见证",
            storyTitle = "王者搭档！",
            unlockDialog = "10级了！从小柯基到大师，感谢你一路的陪伴～我们是永远的最佳搭档！",
            visibility = AchievementVisibility.ALL
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
            storyTitle = "学霸降临！",
            unlockDialog = "100个学习任务！学霸请收下我的膝盖...不对，我的爪子！",
            visibility = AchievementVisibility.STUDENT,
            outfitId = "scholar_hat"
        ),
        Achievement(
            id = NewAchievementId.STUDY_200,
            name = "毕业在望",
            description = "累计完成 200 个学习类任务",
            icon = "🎓",
            condition = AchievementCondition.CUMULATIVE_STUDY,
            threshold = 200,
            stage = AchievementStage.PEAK,
            story = "学海无涯，你已经走过了很长的路",
            storyTitle = "学海无涯！",
            unlockDialog = "200个学习任务！你已经走过了很长的路，但学海无涯，继续前行吧汪！",
            visibility = AchievementVisibility.STUDENT
        ),
        Achievement(
            id = NewAchievementId.STUDY_SEMESTER_100,
            name = "奖学金候选人",
            description = "累计完成 100 个学习类任务",
            icon = "🏆",
            condition = AchievementCondition.CUMULATIVE_STUDY,
            threshold = 100,
            stage = AchievementStage.PEAK,
            story = "优秀是一种习惯，奖学金是对你努力的认可",
            storyTitle = "奖学金得主！",
            unlockDialog = "本学期完成100个学习任务！奖学金非你莫属！",
            visibility = AchievementVisibility.STUDENT
        ),
        Achievement(
            id = NewAchievementId.STUDY_EXAM_WEEK,
            name = "考试战神",
            description = "连续 7 天完成学习类任务",
            icon = "📝",
            condition = AchievementCondition.CONSECUTIVE_STUDY_DAYS,
            threshold = 7,
            stage = AchievementStage.PEAK,
            story = "期末周的你，是最耀眼的存在",
            storyTitle = "考试战神！",
            unlockDialog = "连续7天奋战学习！期末考试对你来说就是小菜一碟！",
            visibility = AchievementVisibility.STUDENT
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
            storyTitle = "职场精英！",
            unlockDialog = "100个工作任务的里程碑！你就是职场最亮的星！",
            visibility = AchievementVisibility.WORKER,
            outfitId = "tie"
        ),
        Achievement(
            id = NewAchievementId.WORK_MONTHLY_30,
            name = "KPI 达成",
            description = "累计完成 30 个工作类任务",
            icon = "📊",
            condition = AchievementCondition.CUMULATIVE_WORK,
            threshold = 30,
            stage = AchievementStage.PEAK,
            story = "KPI 达成！你的努力有目共睹",
            storyTitle = "KPI 之王！",
            unlockDialog = "月度30个任务达成！KPI在你面前不值一提！",
            visibility = AchievementVisibility.WORKER
        ),
        Achievement(
            id = NewAchievementId.WORK_PROMOTION,
            name = "晋升之路",
            description = "连续 30 天每天至少完成 1 个工作类任务",
            icon = "🚀",
            condition = AchievementCondition.CONSECUTIVE_WORK_DAYS,
            threshold = 30,
            stage = AchievementStage.PEAK,
            story = "30 天的坚持，是晋升路上的坚实步伐",
            storyTitle = "晋升通道！",
            unlockDialog = "连续30天坚持工作！你的努力领导一定看在眼里，升职加薪在望！",
            visibility = AchievementVisibility.WORKER
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
            storyTitle = "自律之王！",
            unlockDialog = "30天如一日！自律让你自由，你已经做到了大多数人做不到的事！",
            visibility = AchievementVisibility.ALL,
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
            storyTitle = "勤劳天使！",
            unlockDialog = "500个任务！天啊，你是天使下凡来拯救待办清单的吗？",
            visibility = AchievementVisibility.ALL,
            outfitId = "angel_wings"
        ),
        Achievement(
            id = NewAchievementId.ALL_ACHIEVEMENTS,
            name = "全成就大师",
            description = "解锁其他所有成就 → 解锁披风",
            icon = "🧥",
            condition = AchievementCondition.ALL_ACHIEVEMENTS,
            threshold = 25,
            stage = AchievementStage.PEAK,
            story = "你是真正的柯基王者",
            storyTitle = "传说的诞生！",
            unlockDialog = "你解锁了所有成就！这不是终点，而是传奇故事的开始...汪！",
            visibility = AchievementVisibility.ALL,
            outfitId = "cape"
        )
    )

    /**
     * 通用成就列表
     */
    val universalAchievements: List<Achievement>
        get() = allAchievements.filter { it.visibility == AchievementVisibility.ALL }

    /**
     * 上班族专属成就列表
     */
    val workerAchievements: List<Achievement>
        get() = allAchievements.filter { it.visibility == AchievementVisibility.WORKER }

    /**
     * 学生专属成就列表
     */
    val studentAchievements: List<Achievement>
        get() = allAchievements.filter { it.visibility == AchievementVisibility.STUDENT }

    /**
     * 根据用户类型获取可见的成就列表
     *
     * @param userType 用户类型
     * @return 可见的成就列表
     */
    fun getVisibleAchievements(userType: UserType): List<Achievement> {
        return when (userType) {
            UserType.WORKER -> universalAchievements + workerAchievements
            UserType.STUDENT -> universalAchievements + studentAchievements
            else -> universalAchievements
        }
    }

    /**
     * 根据用户类型获取可见的成就数量
     */
    fun getVisibleCount(userType: UserType): Int {
        return getVisibleAchievements(userType).size
    }

    /**
     * 基础成就列表（不包含复合成就 ALL_ACHIEVEMENTS）
     */
    val basicAchievements: List<Achievement>
        get() = allAchievements.filter { it.id != NewAchievementId.ALL_ACHIEVEMENTS }

    /**
     * 根据用户类型获取基础成就列表（不包含复合成就）
     */
    fun getBasicVisibleAchievements(userType: UserType): List<Achievement> {
        return getVisibleAchievements(userType).filter { it.id != NewAchievementId.ALL_ACHIEVEMENTS }
    }

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
     * 根据用户类型获取可见的成就 ID 列表
     */
    fun getVisibleAchievementIds(userType: UserType): List<String> {
        return getVisibleAchievements(userType).map { it.id }
    }

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
    const val TOTAL_COUNT = 26

    /**
     * 基础成就数量（不包含复合成就）
     */
    const val BASIC_COUNT = 25

    /**
     * 通用成就数量
     */
    const val UNIVERSAL_COUNT = 9

    /**
     * 上班族专属成就数量
     */
    const val WORKER_COUNT = 7

    /**
     * 学生专属成就数量
     */
    const val STUDENT_COUNT = 10
}
