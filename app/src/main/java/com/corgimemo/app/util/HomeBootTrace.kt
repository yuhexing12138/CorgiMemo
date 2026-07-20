package com.corgimemo.app.util

import android.util.Log

/**
 * 首次进入待办首页启动时序追踪器
 *
 * **目的**：诊断"首次进入待办首页，待办卡片会轻微跳动一下"问题。
 * 在 HomeViewModel / HomeScreen / ZonedReorderableLazyColumn 三个组件的关键节点
 * 调用 [t] 记录事件与相对进程启动时间。
 *
 * **Logcat 过滤**：
 * ```
 * adb logcat -s CorgiMemo.HomeBoot:D
 * ```
 *
 * **日志格式**：
 * ```
 * [+<elapsedMs>ms] <event> | <extra>
 * ```
 *
 * **使用方式**：
 * ```kotlin
 * HomeBootTrace.t("VM_init_start")
 * HomeBootTrace.t("VM_loadTodos_first_emit", "size=${allTodos.size}")
 * ```
 *
 * @author CorgiMemo Team
 */
object HomeBootTrace {
    /** 进程级参考时间（首次访问时锁定） */
    private val startMs: Long = System.nanoTime() / 1_000_000L

    /** Logcat tag（专用，便于过滤） */
    private const val TAG = "CorgiMemo.HomeBoot"

    /**
     * 记录事件时间戳
     *
     * @param event 事件名（短横线命名，例如 "VM_init_start"）
     * @param extra 附加信息（key=value 格式，逗号分隔），可空
     */
    fun t(event: String, extra: String = "") {
        val elapsed = System.nanoTime() / 1_000_000L - startMs
        if (extra.isBlank()) {
            Log.d(TAG, "[+${elapsed}ms] $event")
        } else {
            Log.d(TAG, "[+${elapsed}ms] $event | $extra")
        }
    }
}
