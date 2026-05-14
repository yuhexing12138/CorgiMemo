package com.corgimemo.app.animation

/**
 * 柯基姿态枚举
 * 定义柯基的基本姿态，用于姿态切换系统
 */
enum class CorgiPose {
    /** 趴卧姿态 - 休闲或休息时 */
    LIE,
    /** 坐立姿态 - 默认姿态 */
    SIT,
    /** 站立姿态 - 等待或期待时 */
    STAND,
    /** 奔跑姿态 - 兴奋或庆祝时 */
    RUN,
    /** 睡觉姿态 - 深夜时 */
    SLEEP
}
