package com.corgimemo.app.kuikly

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.expand.KuiklyRenderViewBaseDelegator
import org.json.JSONObject

/**
 * Kuikly 页面承载 Activity
 *
 * 采用「方案 D：AAR 桥接」：主工程保持 Kotlin 2.4.0 / AGP 9.2.1 不降级，
 * Kuikly 页面由独立工程（Kotlin 2.1.21）编译成 shared-release.aar 后引入本工程。
 *
 * 非侵入式：本 Activity 为新增文件，不修改任何原有业务代码；
 * 通过 [start] 按需跳转进入 Kuikly 页面。
 */
/**
 * 说明：使用 ComponentActivity 而非官方示例的 AppCompatActivity。
 * 本工程为纯 Compose + Material3 项目，Application/Activity 主题并非 Theme.AppCompat，
 * 若使用 AppCompatActivity 会在启动时抛 "You need to use a Theme.AppCompat theme" 崩溃。
 * Kuikly 只需要一个能承载原生 View 的 Activity，ComponentActivity 完全满足。
 */
class KuiklyRenderActivity : ComponentActivity() {

    private lateinit var contextCodeHandler: KuiklyContextHandler
    private lateinit var kuiklyRenderViewDelegator: KuiklyRenderViewBaseDelegator

    /** 要打开的 Kuikly 页面名，默认 router（与 shared 中 @Page("router") 一致） */
    private val pageName: String
        get() = intent.getStringExtra(KEY_PAGE_NAME) ?: DEFAULT_PAGE_NAME

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. 创建 Kuikly 上下文处理器并初始化委托者
        contextCodeHandler = KuiklyContextHandler(this, pageName)
        kuiklyRenderViewDelegator = contextCodeHandler.initContextHandler()

        // 2. 创建承载容器（用代码构建，避免新增布局资源文件）
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(container)

        // 3. 打开 Kuikly 页面
        contextCodeHandler.openPage(container, pageName, createPageData())
    }

    override fun onResume() {
        super.onResume()
        kuiklyRenderViewDelegator.onResume()
    }

    override fun onPause() {
        super.onPause()
        kuiklyRenderViewDelegator.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        kuiklyRenderViewDelegator.onDetach()
    }

    /** 传给 Kuikly 页面的初始数据 */
    private fun createPageData(): Map<String, Any> = mutableMapOf("appId" to 1)

    companion object {
        private const val KEY_PAGE_NAME = "pageName"
        private const val KEY_PAGE_DATA = "pageData"
        private const val DEFAULT_PAGE_NAME = "router"

        /**
         * 注册 Kuikly 渲染器所需的适配器，在类加载时执行一次
         */
        init {
            initKuiklyAdapter()
        }

        private fun initKuiklyAdapter() {
            with(KuiklyRenderAdapterManager) {
                krImageAdapter = KRImageAdapter
                krLogAdapter = KRLogAdapter
                krUncaughtExceptionHandlerAdapter = KRExceptionAdapter
                krRouterAdapter = KRRouterAdapter
                krThreadAdapter = KRThreadAdapter()
            }
        }

        /**
         * 跳转到 Kuikly 页面
         *
         * @param context 上下文
         * @param pageName 页面名，默认 router
         * @param pageData 页面初始数据
         */
        fun start(
            context: Context,
            pageName: String = DEFAULT_PAGE_NAME,
            pageData: JSONObject = JSONObject()
        ) {
            val intent = Intent(context, KuiklyRenderActivity::class.java)
            intent.putExtra(KEY_PAGE_NAME, pageName)
            intent.putExtra(KEY_PAGE_DATA, pageData.toString())
            context.startActivity(intent)
        }
    }
}
