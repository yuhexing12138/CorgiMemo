package com.corgimemo.app.kuikly

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.tencent.kuikly.core.render.android.adapter.KuiklyRenderAdapterManager
import com.tencent.kuikly.core.render.android.css.ktx.toMap
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

        // 返回键：Kuikly 单页场景下直接关闭承载页，回到主工程。
        // 真实多页场景的页内返回由页面 router 在 Kuikly 内部处理，无需在此拦截。
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            }
        )

        // 供 Kuikly 页面主动关闭本页（如删除待办后）；onDestroy 中置空避免持有 Activity 引用
        KuiklyBridge.onClosePage = { finish() }

        // 创建承载容器（用代码构建，避免新增布局资源文件）
        val container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        try {
            // 1. 创建 Kuikly 上下文处理器并初始化委托者
            contextCodeHandler = KuiklyContextHandler(this, pageName)
            kuiklyRenderViewDelegator = contextCodeHandler.initContextHandler()

            setContentView(container)

            // 2. 打开 Kuikly 页面
            contextCodeHandler.openPage(container, pageName, createPageData())
        } catch (e: Throwable) {
            // 渲染失败兜底：记录日志并提示用户（使用 AlertDialog，禁用系统 Toast）
            Log.e(TAG, "Kuikly 页面加载失败", e)
            showLoadFailedDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::kuiklyRenderViewDelegator.isInitialized) {
            kuiklyRenderViewDelegator.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::kuiklyRenderViewDelegator.isInitialized) {
            kuiklyRenderViewDelegator.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 先解绑关闭回调（它持有本 Activity 引用），再释放渲染器
        if (KuiklyBridge.onClosePage != null) {
            KuiklyBridge.onClosePage = null
        }
        if (::kuiklyRenderViewDelegator.isInitialized) {
            kuiklyRenderViewDelegator.onDetach()
        }
    }

    /**
     * 传给 Kuikly 页面的初始数据。
     *
     * Kuikly 内部的 `KuiklyRenderView.generateWithParams` 会把用户传入的整个 pageData Map
     * 作为 value 放在 `result["param"]` 这一层下（参见 core-render-android 字节码）。
     * 所以这里只需要把入口的 pageData JSON 转成 Map 返回，KUIKLY 会自动加 `param` 嵌套。
     *
     * 业务字段直接在 [buildTodoDetailData] 里平铺构造即可（todoId / title / content / ...），
     * **不要**在这里再补一个 `param` 嵌套，否则字段会落到 `param.param.title` 这种位置。
     */
    private fun createPageData(): Map<String, Any> = argsToMap()

    /** 从 intent 取出入口传入的 pageData JSON 字符串，转成 Map（扁平字段） */
    private fun argsToMap(): MutableMap<String, Any> {
        val jsonStr = intent.getStringExtra(KEY_PAGE_DATA) ?: return mutableMapOf()
        return JSONObject(jsonStr).toMap()
    }

    /**
     * 渲染失败兜底：用 AlertDialog 提示用户（非系统 Toast，符合项目提示规范）。
     * 点击「关闭」后结束承载页，回到主工程。
     */
    private fun showLoadFailedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Kuikly 页面加载失败")
            .setMessage("渲染器初始化或页面加载出现异常，已返回主工程。")
            .setPositiveButton("关闭") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    companion object {
        private const val TAG = "KuiklyRenderActivity"
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
