package com.corgimemo.app.animation

import kotlin.math.max

/**
 * 等级阶段枚举
 * 定义柯基的成长阶段
 */
enum class LevelStage(
    val minLevel: Int,
    val maxLevel: Int,
    val displayName: String,
    val description: String
) {
    BABY(1, 3, "柯基宝宝", "小小只，动作简单"),
    YOUTH(4, 6, "柯基少年", "体型稍大，动作丰富"),
    ADULT(7, 9, "柯基青年", "体型正常，表情多样"),
    MASTER(10, Int.MAX_VALUE, "柯基大师", "特殊装扮，特效华丽")
}

/**
 * 等级管理器
 * 负责经验值计算、等级判断、升级检测等
 *
 * 经验值规则：
 * - 完成 1 个任务：+10 经验
 * - 连续 7 天完成任务：+50 经验（额外奖励）
 *
 * 升级公式：
 * - 升级到第 N 级所需经验 = N * 50
 * - 例如：1→2 需 50 经验，2→3 需 100 经验，以此类推
 */
object LevelManager {

    const val EXP_PER_TASK = 10
    const val EXP_CONSECUTIVE_7_DAYS = 50
    const val EXP_PER_LEVEL_BASE = 50

    /**
     * 获取升级到指定等级所需的经验值（单级）
     * 例如：level=2 返回 50，表示从 1 级升到 2 级需要 50 经验
     *
     * @param level 目标等级
     * @return 升级到该等级所需的经验值
     */
    fun getExpForSingleLevel(level: Int): Int {
        return level * EXP_PER_LEVEL_BASE
    }

    /**
     * 获取从 1 级升到指定等级所需的总经验值
     * 使用等差数列求和：(1+2+...+n) * 50 = n*(n+1)/2 * 50
     *
     * @param level 目标等级
     * @return 总经验值
     */
    fun getTotalExpForLevel(level: Int): Int {
        if (level <= 1) return 0
        return (level - 1) * level / 2 * EXP_PER_LEVEL_BASE
    }

    /**
     * 根据总经验值计算当前等级和进度百分比
     *
     * @param totalExp 总经验值
     * @return Pair(当前等级, 到下一级的进度百分比 0.0-1.0)
     */
    fun getCurrentLevelAndProgress(totalExp: Int): Pair<Int, Float> {
        var level = 1
        var remainingExp = totalExp

        while (true) {
            val expNeeded = getExpForSingleLevel(level)
            if (remainingExp < expNeeded) {
                val progress = if (expNeeded == 0) 1.0f else remainingExp.toFloat() / expNeeded.toFloat()
                return level to max(0.0f, minOf(1.0f, progress))
            }
            remainingExp -= expNeeded
            level++
        }
    }

    /**
     * 获取当前等级
     *
     * @param totalExp 总经验值
     * @return 当前等级
     */
    fun getCurrentLevel(totalExp: Int): Int {
        return getCurrentLevelAndProgress(totalExp).first
    }

    /**
     * 获取等级阶段
     *
     * @param level 当前等级
     * @return 等级阶段
     */
    fun getLevelStage(level: Int): LevelStage {
        return when {
            level >= LevelStage.MASTER.minLevel -> LevelStage.MASTER
            level >= LevelStage.ADULT.minLevel -> LevelStage.ADULT
            level >= LevelStage.YOUTH.minLevel -> LevelStage.YOUTH
            else -> LevelStage.BABY
        }
    }

    /**
     * 获取下一等级
     *
     * @param currentLevel 当前等级
     * @return 下一等级
     */
    fun getNextLevel(currentLevel: Int): Int {
        return currentLevel + 1
    }

    /**
     * 检测是否升级
     *
     * @param currentLevel 当前等级
     * @param currentExp 升级前的经验值
     * @param addedExp 新增经验值
     * @return 如果升级，返回新等级；否则返回 null
     */
    fun checkLevelUp(currentLevel: Int, currentExp: Int, addedExp: Int): Int? {
        val newTotalExp = currentExp + addedExp
        val newLevel = getCurrentLevel(newTotalExp)
        return if (newLevel > currentLevel) newLevel else null
    }

    /**
     * 检测连续 7 天完成任务
     *
     * @param consecutiveDays 连续天数
     * @return 是否应该获得连续 7 天奖励
     */
    fun shouldGiveConsecutive7DaysReward(consecutiveDays: Int): Boolean {
        return consecutiveDays > 0 && consecutiveDays % 7 == 0
    }

    /**
     * 获取完成任务获得的经验值
     *
     * @return 经验值
     */
    fun getExpOnTaskComplete(): Int {
        return EXP_PER_TASK
    }

    /**
     * 获取连续 7 天完成任务的奖励经验值
     *
     * @return 奖励经验值
     */
    fun getExpOnConsecutive7Days(): Int {
        return EXP_CONSECUTIVE_7_DAYS
    }

    /**
     * 获取下一级所需的剩余经验值
     *
     * @param totalExp 总经验值
     * @return 距离下一级还需要的经验值
     */
    fun getExpToNextLevel(totalExp: Int): Int {
        var level = 1
        var remainingExp = totalExp

        while (true) {
            val expNeeded = getExpForSingleLevel(level)
            if (remainingExp < expNeeded) {
                return expNeeded - remainingExp
            }
            remainingExp -= expNeeded
            level++
        }
    }

    /**
     * 获取当前等级的进度描述
     *
     * @param totalExp 总经验值
     * @return 进度描述字符串，如 "50/100"
     */
    fun getProgressText(totalExp: Int): String {
        val (level, _) = getCurrentLevelAndProgress(totalExp)
        val expNeeded = getExpForSingleLevel(level)
        var currentProgress = totalExp
        
        for (l in 1 until level) {
            currentProgress -= getExpForSingleLevel(l)
        }
        
        val safeProgress = max(0, currentProgress)
        return "$safeProgress/$expNeeded"
    }
}
