package com.corgimemo.app.kuikly

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.tencent.kuikly.core.render.android.adapter.HRImageLoadOption
import com.tencent.kuikly.core.render.android.adapter.IKRImageAdapter
import com.tencent.kuikly.core.render.android.adapter.IKRLogAdapter
import com.tencent.kuikly.core.render.android.adapter.IKRRouterAdapter
import com.tencent.kuikly.core.render.android.adapter.IKRThreadAdapter
import com.tencent.kuikly.core.render.android.adapter.IKRUncaughtExceptionHandlerAdapter
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Kuikly 渲染器适配器集合
 *
 * Kuikly 渲染器不感知宿主 App 的原生能力，需要通过适配器把
 * 日志、路由、线程、图片加载等能力委托给宿主实现。
 * 以下四个为官方要求「必须实现」的适配器，异常适配器为推荐实现。
 */

/** 日志适配器：桥接到 Android Log */
object KRLogAdapter : IKRLogAdapter {
    override val asyncLogEnable: Boolean get() = false

    override fun i(tag: String, msg: String) {
        Log.i(tag, msg)
    }

    override fun d(tag: String, msg: String) {
        Log.d(tag, msg)
    }

    override fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }
}

/** 页面路由适配器：Kuikly 内部跳转新页面时回调宿主 */
object KRRouterAdapter : IKRRouterAdapter {
    override fun openPage(context: Context, pageName: String, pageData: JSONObject) {
        KuiklyRenderActivity.start(context, pageName, pageData)
    }

    override fun closePage(context: Context) {
        (context as? Activity)?.finish()
    }
}

/** 线程适配器：Kuikly 的后台任务执行与线程栈大小 */
class KRThreadAdapter : IKRThreadAdapter {
    override fun executeOnSubThread(task: () -> Unit) {
        subThreadPoolExecutor.execute(task)
    }

    /**
     * Compose 场景下官方建议 8MB，避免 StackOverflowException
     */
    override fun stackSize(): Long = 8 * 1024 * 1024
}

/** 子线程池：固定 2 个线程 */
private val subThreadPoolExecutor by lazy {
    Executors.newFixedThreadPool(2)
}

/**
 * 图片加载适配器
 *
 * 当前 shared 中的页面（RouterPage）只包含文本，没有图片加载需求，
 * 因此这里先回调 null。后续页面若需要图片，可在此接入宿主的 Coil/Glide。
 * 注意：此方法可能在非 UI 线程调用，接入时需注意线程安全。
 */
object KRImageAdapter : IKRImageAdapter {
    override fun fetchDrawable(
        imageLoadOption: HRImageLoadOption,
        callback: (drawable: Drawable?) -> Unit
    ) {
        // TODO: 接入宿主图片库（Coil 3）后再实现真实加载逻辑
        callback(null)
    }
}

/** 异常适配器：Kuikly 内部未捕获异常上报 */
object KRExceptionAdapter : IKRUncaughtExceptionHandlerAdapter {
    override fun uncaughtException(throwable: Throwable) {
        Log.e("KuiklyError", throwable.stackTraceToString())
    }
}
