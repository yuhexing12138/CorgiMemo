package com.corgimemo.app.ui.components.appdrawer.sections

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Reorderable 拖拽埋点辅助类（侧滑栏诊断用）
 *
 * **用途**：诊断侧滑栏 4 个 Tab 长按拖拽时的"残影/闪烁"问题。
 * 通过在拖拽生命周期关键节点输出 Log，定位问题来源：
 * - 重组过于频繁（recomposeCount 突增）
 * - graphicsLayer 参数切换不平滑
 * - LazyColumn 布局抖动
 * - onMove 回调频率过高
 *
 * **7 类埋点**：
 * 1. [onDragStarted] 拖拽开始（手指长按后被识别为可拖拽）
 * 2. [onMove] 位置交换（onMove 回调触发）
 * 3. [onDragStopped] 拖拽结束（手指释放）
 * 4. [onGraphicsLayerChange] graphicsLayer 参数变化（scale/shadow/zIndex）
 * 5. [onRecompose] Composable 重组次数
 * 6. [onLayoutChange] LazyColumn 布局变化（首项索引、可见项数）
 * 7. [onReorderSubmit] 实际触发外层 ViewModel 更新的次数（Plan A 验证用）
 *
 * **查看日志**（PowerShell 终端）：
 * ```
 * adb logcat -s DrawerDrag/Category:V DrawerDrag/Inspiration:V DrawerDrag/Profile:V
 * ```
 *
 * **清除日志**：
 * ```
 * adb logcat -c
 * ```
 *
 * **v2026-07-28 新增**：用于诊断"被拖拽项在交换瞬间的残影/闪烁"问题。
 *
 * @param sectionName 当前 Section 名称（Category / Inspiration / Profile / Status）
 */
@Stable
class ReorderableDiagnostics(val sectionName: String) {
    /**
     * 重组计数器（每次 [onRecompose] 自增）
     *
     * 突变时 Log 输出当前值，用于发现"过度重组"。
     */
    var recomposeCount by mutableIntStateOf(0)
        private set

    private val tag: String = "DrawerDrag/$sectionName"

    /**
     * 拖拽开始埋点
     *
     * 由 `longPressDraggableHandle.onDragStarted` 回调调用。
     */
    fun onDragStarted(itemId: Any) {
        Log.d(
            tag,
            "[START] itemId=$itemId, t=${System.nanoTime()}, recomposeCount=$recomposeCount"
        )
    }

    /**
     * 位置交换埋点
     *
     * 由 `rememberReorderableLazyListState` 的 onMove 回调调用。
     * 这是诊断"残影"的关键时间线：高频 onMove 可能导致连续 graphicsLayer 切换。
     *
     * @param from 起始全局索引（在 LazyColumn 中）
     * @param to 目标全局索引
     * @param listSize 列表总长
     * @param isDragging 当前是否处于拖拽中
     */
    fun onMove(from: Int, to: Int, listSize: Int, isDragging: Boolean) {
        Log.d(
            tag,
            "[MOVE] from=$from, to=$to, listSize=$listSize, isDragging=$isDragging, " +
                "recomposeCount=$recomposeCount, t=${System.nanoTime()}"
        )
    }

    /**
     * 拖拽结束埋点
     *
     * 由 `longPressDraggableHandle.onDragStopped` 回调调用。
     */
    fun onDragStopped(itemId: Any, listSize: Int) {
        Log.d(
            tag,
            "[STOP] itemId=$itemId, listSize=$listSize, t=${System.nanoTime()}, " +
                "recomposeCount=$recomposeCount"
        )
    }

    /**
     * graphicsLayer 参数变化埋点
     *
     * 由 `LaunchedEffect(isDragging)` 在每次 isDragging 变化时调用。
     * 用于追踪缩放/阴影/zIndex 在拖拽起止时的瞬时切换。
     *
     * @param isDragging 当前是否处于拖拽中
     * @param scaleX graphicsLayer.scaleX 值
     * @param scaleY graphicsLayer.scaleY 值
     * @param shadowElevation graphicsLayer.shadowElevation 值
     * @param zIndex Modifier.zIndex 值
     */
    fun onGraphicsLayerChange(
        isDragging: Boolean,
        scaleX: Float,
        scaleY: Float,
        shadowElevation: Float,
        zIndex: Float
    ) {
        Log.d(
            tag,
            "[LAYER] isDragging=$isDragging, scale=($scaleX,$scaleY), " +
                "shadow=$shadowElevation, zIndex=$zIndex, t=${System.nanoTime()}"
        )
    }

    /**
     * 重组埋点（Composable 函数体每次执行都调用）
     *
     * 在 Composable 函数体第一行调用，每次重组都自增并输出日志。
     * 用于发现"过度重组"问题（拖拽中如果 recomposeCount 飙升，说明重组风暴）。
     */
    fun onRecompose() {
        recomposeCount++
    }

    /**
     * LazyColumn 布局变化埋点
     *
     * 由 [TrackLazyColumnLayout] 通过 snapshotFlow 自动调用。
     *
     * @param firstVisibleItemIndex 第一个可见项的全局索引
     * @param visibleItemCount 可见项数
     */
    fun onLayoutChange(firstVisibleItemIndex: Int, visibleItemCount: Int) {
        Log.d(
            tag,
            "[LAYOUT] firstVisible=$firstVisibleItemIndex, visibleCount=$visibleItemCount, " +
                "t=${System.nanoTime()}, recomposeCount=$recomposeCount"
        )
    }

    /**
     * onReorder 提交埋点（v2026-07-28 Plan A 验证用）
     *
     * 由 Section 在 `onDragStopped` 回调中调用，记录**实际触发外层 ViewModel 更新的次数**。
     * 修复前：每次 onMove 都触发 onReorder（~5-10 次/拖拽）
     * 修复后：仅拖拽结束触发 1 次/拖拽
     *
     * 对比 [onMove] 次数与 [onReorderSubmit] 次数，可验证 Plan A 是否生效。
     *
     * @param listSize 最终提交的新顺序列表大小
     */
    fun onReorderSubmit(listSize: Int) {
        Log.d(
            tag,
            "[SUBMIT] listSize=$listSize, t=${System.nanoTime()}, " +
                "recomposeCount=$recomposeCount (实际触发 ViewModel 更新)"
        )
    }

    /**
     * ReorderableItem 创建埋点（v2026-07-28 验证 isDragging 反复切换根因用）
     *
     * 由 `DisposableEffect(Unit).onDispose` 的反面（创建时）调用。
     * 配合 [onItemExit] 可识别 ReorderableItem 是否被销毁重建。
     *
     * **诊断目标**：
     * - 若 [ITEM-OUT] 出现在拖拽中 → 列表项被销毁重建
     * - 若 [ITEM-IN] 出现在拖拽中 → 列表项被新建
     * - 二者都是 isDragging 状态丢失的可能原因
     *
     * @param key 当前 ReorderableItem 的 key（tag / id）
     */
    fun onItemEnter(key: Any) {
        Log.d(
            tag,
            "[ITEM-IN] key=$key, t=${System.nanoTime()}, recomposeCount=$recomposeCount"
        )
    }

    /**
     * ReorderableItem 销毁埋点（v2026-07-28 验证 isDragging 反复切换根因用）
     *
     * 由 `DisposableEffect(Unit).onDispose` 回调调用。
     *
     * @param key 当前 ReorderableItem 的 key
     */
    fun onItemExit(key: Any) {
        Log.d(
            tag,
            "[ITEM-OUT] key=$key, t=${System.nanoTime()}, recomposeCount=$recomposeCount"
        )
    }

    /**
     * items() 列表 key 顺序变化埋点（v2026-07-28 验证 isDragging 反复切换根因用）
     *
     * 由 `LaunchedEffect(displayTags)` 自动调用。
     * 用于追踪 LazyColumn 的 items 列表在 onMove 更新 pendingReorder 后的 key 顺序变化。
     *
     * **诊断目标**：
     * - 若 [LIST-KEYS] 出现在 onMove 之后 → 列表 key 顺序确实变化
     * - 若 [LIST-KEYS] 之后立即出现 [LAYER] isDragging=false → 因果链确认
     *
     * @param keys items() 列表的当前 key 序列（前 10 个用于预览）
     */
    fun onListKeysChange(keys: List<Any>) {
        val preview = keys.take(10).joinToString(",")
        Log.d(
            tag,
            "[LIST-KEYS] size=${keys.size}, first10=[$preview], t=${System.nanoTime()}, " +
                "recomposeCount=$recomposeCount"
        )
    }

    /**
     * onMove 后 pendingReorder 快照埋点（v2026-07-28 验证 isDragging 反复切换根因用）
     *
     * 在 `rememberReorderableLazyListState` 的 onMove 回调内调用，记录更新后的 pendingReorder 内容。
     * 用于精确关联 onMove → list 变化 → isDragging 切换的因果链。
     *
     * @param from 移动源索引（在 pendingReorder 子列表中）
     * @param to 移动目标索引
     * @param snapshot 移动后的 pendingReorder 完整快照（前 10 个用于预览）
     */
    fun onMoveSnapshot(from: Int, to: Int, snapshot: List<Any>) {
        val preview = snapshot.take(10).joinToString(",")
        Log.d(
            tag,
            "[MOVE-SNAP] from=$from, to=$to, size=${snapshot.size}, first10=[$preview], " +
                "t=${System.nanoTime()}, recomposeCount=$recomposeCount"
        )
    }

    /**
     * graphicsLayer 实际值变化埋点（v2026-07-28 验证 isDragging 反复切换根因用）
     *
     * 在 `LaunchedEffect(isDragging) + snapshotFlow { scale }` 中调用。
     * 仅在拖拽中（isDragging=true）持续记录 scale 变化，用于捕捉动画的中断点。
     *
     * **诊断目标**：
     * - 观察 scale 在拖拽中是否被反复设置（说明 graphicsLayer 动画在反复启动/中断）
     * - 与 [ITEM-IN]/[ITEM-OUT] 关联：若 scale 突变与 item 重建同步 → 残影由 item 重建导致
     *
     * @param scale 当前 graphicsLayer.scaleX/Y 值
     * @param shadow 当前 graphicsLayer.shadowElevation 值
     * @param isDragging 当前是否处于拖拽中
     */
    fun onScaleFrame(scale: Float, shadow: Float, isDragging: Boolean) {
        Log.d(
            tag,
            "[SCALE] isDragging=$isDragging, scale=$scale, shadow=$shadow, t=${System.nanoTime()}"
        )
    }
}

/**
 * 创建并记住 [ReorderableDiagnostics] 实例
 *
 * 每次 Composable 重组时都会调用，函数体顶部 [ReorderableDiagnostics.onRecompose] 自增计数。
 *
 * @param sectionName Section 名称（用于 Log tag），如 "Category" / "Inspiration" / "Profile"
 * @return 埋点接收器实例
 */
@Composable
fun rememberReorderableDiagnostics(sectionName: String): ReorderableDiagnostics {
    val diag = remember(sectionName) { ReorderableDiagnostics(sectionName) }
    LaunchedEffect(sectionName) {
        Log.d("DrawerDrag/$sectionName", "[INIT] sectionName=$sectionName, t=${System.nanoTime()}")
    }
    return diag
}

/**
 * 跟踪 LazyColumn 布局变化（埋点用）
 *
 * 将 LazyListState 的 layoutInfo 变化转换为 [ReorderableDiagnostics.onLayoutChange] 事件。
 * 使用 `snapshotFlow + distinctUntilChanged` 避免重复触发。
 *
 * @param listState 被跟踪的 LazyListState
 * @param diag 埋点接收器
 */
@Composable
fun TrackLazyColumnLayout(
    listState: LazyListState,
    diag: ReorderableDiagnostics
) {
    LaunchedEffect(listState, diag) {
        snapshotFlow { listState.layoutInfo }
            .distinctUntilChanged()
            .collect { info ->
                val visible = info.visibleItemsInfo
                if (visible.isNotEmpty()) {
                    diag.onLayoutChange(
                        firstVisibleItemIndex = visible.first().index,
                        visibleItemCount = visible.size
                    )
                }
            }
    }
}
