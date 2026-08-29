package com.corgimemo.app.kuikly

import android.content.Context
import android.view.ViewGroup
import com.tencent.kuikly.core.render.android.context.KuiklyRenderCoreExecuteModeBase
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegatorDelegate
import com.tencent.kuikly.core.render.android.performace.KRMonitorType

/**
 * Kuikly 上下文处理器（精简版）
 *
 * 参考 Kuikly 官方 demo 的 ContextCodeHandler 实现，去掉了 demo 特有的
 * 自定义 Module（KRBridgeModule 等）、性能监控与错误弹窗，
 * 仅保留「打开一个 Kuikly 页面」所需的最小实现。
 *
 * 官方参考实现：
 * https://github.com/Tencent-TDS/KuiklyUI/blob/main/androidApp/src/main/java/com/tencent/kuikly/android/demo/ContextCodeHandler.kt
 *
 * @param context 宿主上下文
 * @param pageName 要打开的 Kuikly 页面名（需与 shared 中 @Page 注解的值一致）
 */
class KuiklyContextHandler(
    private val context: Context,
    private val pageName: String
) {

    /** Kuikly 页面委托者，负责承载 KuiklyRenderView 的生命周期与页面挂载 */
    lateinit var delegator: KuiklyRenderViewBaseDelegator
        private set

    /**
     * 初始化上下文处理器，创建 [KuiklyRenderViewBaseDelegator] 实例
     *
     * @return 创建好的 delegator，宿主需在生命周期回调中转发 onResume/onPause/onDetach
     */
    fun initContextHandler(): KuiklyRenderViewBaseDelegator {
        val delegate = object : KuiklyRenderViewBaseDelegatorDelegate {
            /**
             * Kuikly 核心执行模式：JVM 模式（Android 端默认，也是官方 demo 的默认选择）
             */
            override fun coreExecuteModeX(): KuiklyRenderCoreExecuteModeBase {
                return KuiklyRenderCoreExecuteModeBase.JVM
            }

            /**
             * 性能监控项：暂不开启（官方 demo 开启了 LAUNCH/FRAME/MEMORY）
             */
            override fun performanceMonitorTypes(): List<KRMonitorType> {
                return emptyList()
            }
        }
        delegator = KuiklyRenderViewBaseDelegator(delegate)
        return delegator
    }

    /**
     * 打开 Kuikly 页面并挂载到指定容器
     *
     * @param container 承载 Kuikly RenderView 的容器
     * @param pageName 页面名
     * @param pageData 传给页面的初始数据
     */
    fun openPage(container: ViewGroup, pageName: String, pageData: Map<String, Any>) {
        // 第二个参数为 turboDisplayKey，传空串表示不使用（与官方 demo 一致）
        delegator.onAttach(container, "", pageName, pageData)
    }
}
