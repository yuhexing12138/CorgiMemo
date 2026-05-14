package com.corgimemo.app.animation

/**
 * 姿态场景枚举
 * 定义柯基可能处于的场景状态
 */
enum class PoseScene {
    /** 默认/闲置状态 */
    DEFAULT,
    /** 创建待办时（认真听） */
    CREATING,
    /** 加载中（数据加载、保存操作） */
    LOADING,
    /** 庆祝/完成任务时 */
    CELEBRATING
}

/**
 * 姿态管理器
 * 负责根据时间和场景自动切换柯基姿态
 */
object PoseManager {

    /**
     * 获取默认姿态
     * 用户明确要求默认姿态为趴卧
     *
     * @return 推荐的姿态
     */
    fun getDefaultPose(): CorgiPose {
        return CorgiPose.LIE
    }

    /**
     * 根据场景获取对应的姿态
     *
     * @param scene 场景状态
     * @return 推荐的姿态
     */
    fun getPoseForScene(scene: PoseScene): CorgiPose {
        return when (scene) {
            PoseScene.DEFAULT -> CorgiPose.LIE
            PoseScene.CREATING -> CorgiPose.SIT
            PoseScene.LOADING -> CorgiPose.RUN
            PoseScene.CELEBRATING -> CorgiPose.STAND
        }
    }

    /**
     * 根据情绪获取推荐姿态
     *
     * @param mood 情绪状态
     * @return 推荐的姿态
     */
    fun getPoseForMood(mood: CorgiMood): CorgiPose {
        return when (mood) {
            CorgiMood.EXCITED -> CorgiPose.RUN
            CorgiMood.HAPPY -> CorgiPose.SIT
            CorgiMood.NORMAL -> CorgiPose.SIT
            CorgiMood.EXPECTING -> CorgiPose.STAND
            CorgiMood.WORRIED -> CorgiPose.SIT
            CorgiMood.SLEEPY -> CorgiPose.SLEEP
            CorgiMood.SAD -> CorgiPose.LIE
        }
    }
}
