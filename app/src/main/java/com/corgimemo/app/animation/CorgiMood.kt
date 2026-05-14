package com.corgimemo.app.animation

/**
 * 柯基情绪枚举
 * 定义7种情绪状态，用于情绪系统
 */
enum class CorgiMood {
    /** 开心 - 情绪值 60-80 */
    HAPPY,
    /** 普通 - 情绪值 40-60 */
    NORMAL,
    /** 期待 - 等待用户互动时 */
    EXPECTING,
    /** 担心 - 情绪值 20-40 */
    WORRIED,
    /** 困倦 - 情绪值 <20 或深夜 */
    SLEEPY,
    /** 兴奋 - 情绪值 >80 */
    EXCITED,
    /** 失落 - 情绪值 <20 或长时间未操作 */
    SAD
}

/**
 * 根据情绪值计算情绪状态
 * @param moodValue 情绪值（0-100）
 * @return 对应的情绪状态
 */
fun getMoodFromValue(moodValue: Int): CorgiMood {
    return when {
        moodValue > 80 -> CorgiMood.EXCITED
        moodValue in 60..80 -> CorgiMood.HAPPY
        moodValue in 40..59 -> CorgiMood.NORMAL
        moodValue in 20..39 -> CorgiMood.WORRIED
        else -> CorgiMood.SAD
    }
}
