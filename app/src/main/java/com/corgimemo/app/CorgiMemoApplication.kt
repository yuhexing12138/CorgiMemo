package com.corgimemo.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ComponentRegistry
import coil3.ImageLoader
import coil3.imageLoader
import com.corgimemo.app.analytics.UserBehaviorAnalyzer
import com.corgimemo.app.data.repository.InspirationRepository
import com.corgimemo.app.data.repository.SpecialDateRepository
import com.corgimemo.app.data.repository.TodoRepository
import com.corgimemo.app.notification.NotificationHelper
import com.corgimemo.app.widget.WidgetUpdateWorker
import com.corgimemo.app.worker.ArchiveCleanupScheduler
import com.corgimemo.app.worker.ReminderRestoreScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class CorgiMemoApplication : Application() {

    @Inject
    lateinit var todoRepository: TodoRepository

    @Inject
    lateinit var inspirationRepository: InspirationRepository

    @Inject
    lateinit var specialDateRepository: SpecialDateRepository

    /** 用户行为分析器（用于智能预加载） */
    @Inject
    lateinit var userBehaviorAnalyzer: UserBehaviorAnalyzer

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        ArchiveCleanupScheduler.scheduleIfNeeded(this)
        WidgetUpdateWorker.scheduleWidgetUpdates(this)
        ReminderRestoreScheduler.restoreNow(this)

        /**
         * 临时调试日志：输出 Coil ImageLoader 内部 components 列表
         *
         * - 用于确认 FileFetcher/FileUriMapper 是否正确注册到默认 ComponentRegistry
         * - Coil 2.5.0 + Compose BOM 2026.04.01 存在已知兼容性问题
         *   （[coil-kt/coil#2273]），可能某些 Component 在运行时未被正确添加
         * - 通过反射访问 ComponentRegistry 的 internal fetcherFactories/mapperFactories 字段
         * - 验证完成后可删除此函数
         */
        logCoilComponents()

        /**
         * Application 级智能数据预加载
         *
         * 监听应用生命周期（前台/后台），
         * 在应用进入前台时根据用户行为习惯**智能排序**预加载数据。
         *
         * **安全设计**：
         * - 整个初始化过程包裹在 try-catch 中，预加载失败不影响 APP 正常启动
         * - 使用 Handler.post 延迟注册，避免 ProcessLifecycleOwner 未就绪
         * - 行为分析器异常时降级为固定预加载顺序
         */
        try {
            // 延迟到主线程消息队列的下一次循环，确保 ProcessLifecycleOwner 已初始化
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                safeInitPreloader()
            }
        } catch (e: Exception) {
            android.util.Log.w("CorgiMemoApplication", "⚠️ 预加载器注册失败（不影响正常使用）: ${e.message}")
        }
    }

    /**
     * 临时调试：输出 Coil ImageLoader components 列表
     *
     * - 获取默认 ImageLoader 实例
     * - 通过反射访问 ComponentRegistry 的 internal factory 列表
     * - 检查 FileFetcher（type=java.io.File）和 FileUriMapper（type=android.net.Uri）是否注册
     * - 输出每个 factory 的 type 和 factory class
     */
    private fun logCoilComponents() {
        try {
            // 触发 ImageLoader 懒初始化（如果还没初始化）
            val imageLoader: ImageLoader = imageLoader
            val components: ComponentRegistry = imageLoader.components
            android.util.Log.d("CoilDebug", "=== Coil ImageLoader components ===")
            android.util.Log.d("CoilDebug", "ImageLoader class: ${imageLoader.javaClass.name}")

            // 反射访问 internal fetcherFactories 字段
            val fetcherField = ComponentRegistry::class.java.getDeclaredField("fetcherFactories")
            fetcherField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val fetcherFactories = fetcherField.get(components) as List<Pair<*, *>>
            android.util.Log.d("CoilDebug", "Fetcher factories count: ${fetcherFactories.size}")
            fetcherFactories.forEachIndexed { index, pair ->
                val factory = pair.first
                val type = pair.second
                val factoryName = factory?.javaClass?.name ?: "null"
                android.util.Log.d(
                    "CoilDebug",
                    "  [$index] type=$type factory=$factoryName"
                )
            }

            // 反射访问 internal mapperFactories 字段
            val mapperField = ComponentRegistry::class.java.getDeclaredField("mapperFactories")
            mapperField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val mapperFactories = mapperField.get(components) as List<Pair<*, *>>
            android.util.Log.d("CoilDebug", "Mapper factories count: ${mapperFactories.size}")
            mapperFactories.forEachIndexed { index, pair ->
                val factory = pair.first
                val type = pair.second
                val factoryName = factory?.javaClass?.name ?: "null"
                android.util.Log.d(
                    "CoilDebug",
                    "  [$index] type=$type factory=$factoryName"
                )
            }

            android.util.Log.d("CoilDebug", "=== End Coil components ===")
        } catch (e: Exception) {
            android.util.Log.e("CoilDebug", "Failed to log Coil components: ${e.message}", e)
        }
    }

    /**
     * 安全初始化预加载监听器
     *
     * 捕获所有异常，确保预加载功能失败时 APP 仍可正常启动。
     */
    private fun safeInitPreloader() {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        smartPreloadData()
                    }
                }
            )
            android.util.Log.d("CorgiMemoApplication", "✅ 预加载监听器已注册")
        } catch (e: Exception) {
            android.util.Log.w("CorgiMemoApplication", "⚠️ ProcessLifecycleOwner 注册失败: ${e.message}")
        }
    }

    /**
     * 智能预加载核心页面数据
     *
     * 根据用户历史访问频率动态调整预加载顺序：
     * 1. 获取用户行为分析结果（按频率排序的页面列表）
     * 2. 按顺序异步预加载每个页面的数据
     *
     * **安全设计**：
         * - 行为分析器异常时使用默认固定顺序
         * - 所有操作包裹在 try-catch 中，任何步骤失败不影响其他步骤
     * - 预加载失败仅影响首帧速度，不影响功能正确性
     */
    private fun smartPreloadData() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 获取基于用户行为的智能预加载顺序
                val priority = try {
                    userBehaviorAnalyzer.getPreloadPriority()
                } catch (e: Exception) {
                    android.util.Log.w("CorgiMemoApplication", "⚠️ 行为分析器异常，使用默认顺序: ${e.message}")
                    DEFAULT_PRELOAD_ORDER
                }

                android.util.Log.d(
                    "CorgiMemoApplication",
                    "🚀 开始智能预加载，顺序: ${priority.joinToString { it.name }}"
                )

                // 按优先级顺序预加载各页面数据
                priority.forEach { page ->
                    try {
                        when (page) {
                            UserBehaviorAnalyzer.PageType.HOME -> {
                                todoRepository.getAllTodos().first()
                                android.util.Log.v("CorgiMemoApplication", "  ✅ 待办页数据已预热")
                            }

                            UserBehaviorAnalyzer.PageType.INSPIRATION -> {
                                inspirationRepository.getAllInspirations().first()
                                android.util.Log.v("CorgiMemoApplication", "  ✅ 灵感页数据已预热")
                            }

                            UserBehaviorAnalyzer.PageType.SPECIAL_DATE -> {
                                specialDateRepository.allDates.first()
                                android.util.Log.v("CorgiMemoApplication", "  ✅ 日期页数据已预热")
                            }
                        }
                    } catch (e: Exception) {
                        // 单个页面预加载失败不阻断其他页面
                        android.util.Log.w("CorgiMemoApplication", "  ⚠️ ${page.name} 预加载失败: ${e.message}")
                    }
                }

                android.util.Log.d("CorgiMemoApplication", "✅ 智能预加载完成")
            } catch (e: Exception) {
                // 预加载失败不影响正常使用（仅影响首帧速度）
                android.util.Log.w("CorgiMemoApplication", "⚠️ 智能预加载失败: ${e.message}")
            }
        }
    }

    companion object {
        /** 默认预加载顺序（无行为分析数据或分析器不可用时使用） */
        private val DEFAULT_PRELOAD_ORDER = listOf(
            UserBehaviorAnalyzer.PageType.HOME,
            UserBehaviorAnalyzer.PageType.INSPIRATION,
            UserBehaviorAnalyzer.PageType.SPECIAL_DATE
        )
    }
}