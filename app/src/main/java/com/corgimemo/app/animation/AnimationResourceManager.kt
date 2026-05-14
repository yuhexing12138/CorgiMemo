package com.corgimemo.app.animation

import com.corgimemo.app.R

/**
 * 动画资源管理器
 * 管理动画类型到资源 ID 列表的映射
 */
object AnimationResourceManager {

    /**
     * 获取指定动画类型的所有帧资源 ID
     * @param type 动画类型
     * @return 帧资源 ID 列表
     */
    fun getAnimationFrames(type: AnimationType): List<Int> {
        return when (type) {
            AnimationType.SIT -> listOf(
                R.drawable.corgi_sit_2frames_01,
                R.drawable.corgi_sit_2frames_02
            )
            AnimationType.STAND -> listOf(
                R.drawable.corgi_stand_2frames_01,
                R.drawable.corgi_stand_2frames_02
            )
            AnimationType.LIE -> listOf(
                R.drawable.corgi_lie_3frames_01,
                R.drawable.corgi_lie_3frames_02,
                R.drawable.corgi_lie_3frames_03
            )
            AnimationType.RUN -> listOf(
                R.drawable.corgi_run_4frames_01,
                R.drawable.corgi_run_4frames_02,
                R.drawable.corgi_run_4frames_03,
                R.drawable.corgi_run_4frames_04
            )
            AnimationType.WINK -> listOf(
                R.drawable.corgi_wink_2frames_01,
                R.drawable.corgi_wink_2frames_02
            )
            AnimationType.WAG -> listOf(
                R.drawable.corgi_wag_4frames_01,
                R.drawable.corgi_wag_4frames_02,
                R.drawable.corgi_wag_4frames_03,
                R.drawable.corgi_wag_4frames_04
            )
            AnimationType.TILT -> listOf(
                R.drawable.corgi_tilt_2frames_01,
                R.drawable.corgi_tilt_2frames_02
            )
            AnimationType.SLEEP -> listOf(
                R.drawable.corgi_sleep_2frames_01,
                R.drawable.corgi_sleep_2frames_02
            )
            AnimationType.SAD -> listOf(
                R.drawable.corgi_sad_2frames_01,
                R.drawable.corgi_sad_2frames_02
            )
            AnimationType.PROUD -> listOf(
                R.drawable.corgi_proud_2frames_01,
                R.drawable.corgi_proud_2frames_02
            )
            AnimationType.SHY -> listOf(
                R.drawable.corgi_shy_2frames_01,
                R.drawable.corgi_shy_2frames_02
            )
            AnimationType.WORRY -> listOf(
                R.drawable.corgi_worry_2frames_01,
                R.drawable.corgi_worry_2frames_02
            )
            AnimationType.ROLL -> listOf(
                R.drawable.corgi_roll_4framesl_01,
                R.drawable.corgi_roll_4framesl_02,
                R.drawable.corgi_roll_4framesl_03,
                R.drawable.corgi_roll_4framesl_04
            )
        }
    }

    /**
     * 获取姿态对应的默认动画类型
     * @param pose 柯基姿态
     * @return 对应的动画类型
     */
    fun getAnimationForPose(pose: CorgiPose): AnimationType {
        return when (pose) {
            CorgiPose.LIE -> AnimationType.LIE
            CorgiPose.SIT -> AnimationType.SIT
            CorgiPose.STAND -> AnimationType.STAND
            CorgiPose.RUN -> AnimationType.RUN
            CorgiPose.SLEEP -> AnimationType.SLEEP
        }
    }

    /**
     * 获取情绪对应的表情动画类型
     * @param mood 柯基情绪
     * @return 对应的表情动画类型
     */
    fun getExpressionForMood(mood: CorgiMood): AnimationType {
        return when (mood) {
            CorgiMood.HAPPY -> AnimationType.WAG
            CorgiMood.NORMAL -> AnimationType.SIT
            CorgiMood.EXPECTING -> AnimationType.TILT
            CorgiMood.WORRIED -> AnimationType.WORRY
            CorgiMood.SLEEPY -> AnimationType.SLEEP
            CorgiMood.EXCITED -> AnimationType.RUN
            CorgiMood.SAD -> AnimationType.SAD
        }
    }
}
