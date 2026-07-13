package com.corgimemo.app.animation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 触觉反馈控制器接口
 *
 * 抽象触觉反馈的执行，便于在 ViewModel 层通过依赖注入解耦，
 * 提升可测试性（测试中可注入 stub/mock 实现避免真实 Vibrator 调用）。
 *
 * 实现委托给现有的 [HapticFeedbackManager]（object 单例，UI 层仍直接调用）。
 */
interface HapticFeedbackController {

    /**
     * 执行触觉反馈
     *
     * @param type 交互类型（决定震动模式）
     * @param enabled 是否启用（受用户偏好控制，false 时直接返回）
     */
    fun perform(type: InteractionType, enabled: Boolean = true)
}

/**
 * [HapticFeedbackController] 的默认实现
 *
 * 委托给 [HapticFeedbackManager]（object 单例）执行真实震动。
 * 注入 ApplicationContext 用于获取 Vibrator 系统服务。
 *
 * 注：UI 层（Composable）仍直接调用 [HapticFeedbackManager]，
 * 此实现仅服务于 ViewModel 层的依赖注入需求。
 *
 * @param context 应用上下文
 */
@Singleton
class HapticFeedbackControllerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : HapticFeedbackController {

    override fun perform(type: InteractionType, enabled: Boolean) {
        HapticFeedbackManager.performHapticFeedback(
            context = context,
            type = type,
            enabled = enabled
        )
    }
}
