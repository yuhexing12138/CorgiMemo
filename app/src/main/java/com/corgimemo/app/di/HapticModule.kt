package com.corgimemo.app.di

import com.corgimemo.app.animation.HapticFeedbackController
import com.corgimemo.app.animation.HapticFeedbackControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 触觉反馈 DI 模块
 *
 * 绑定 [HapticFeedbackController] 接口到其默认实现 [HapticFeedbackControllerImpl]。
 *
 * 使用 @Binds 而非 @Provides 的原因：
 * - 实现类自身已通过 @Inject constructor 提供构造信息
 * - @Binds 是更高效的方式（生成更少的工厂代码）
 * - 接口注入模式的标准写法
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HapticModule {

    @Binds
    @Singleton
    abstract fun bindHapticFeedbackController(
        impl: HapticFeedbackControllerImpl
    ): HapticFeedbackController
}
