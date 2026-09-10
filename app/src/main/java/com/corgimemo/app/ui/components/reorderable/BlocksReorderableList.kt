/*
 * Copyright 2023 Calvin Liang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.corgimemo.app.ui.components.reorderable

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import sh.calvin.reorderable.DragGestureDetector
import kotlin.math.abs

/**
 * 块级可重排列（vendored fork，v2026-09-10）。
 *
 * **来源**：`sh.calvin.reorderable:reorderable:3.1.0`，原文件
 * `Reorderable/reorderable/src/commonMain/kotlin/sh/calvin/reorderable/ReorderableList.kt`，
 * 附带复制了同包 `draggable.kt` 里的手势实现（那个 `draggable` 是 internal，App 模块调不到）。
 * 本地副本与 3.1.0 tag 逐行一致（`git -C Reorderable diff 3.1.0 -- reorderable/src` 为空）。
 * 许可证 Apache-2.0，上方版权头按协议要求保留。
 *
 * **为什么必须 fork**：原库 `settle()` 的顺序是
 * 「先 `itemOffsets[i].animateTo(...)` 播完落位弹簧滑行（suspend，约 300~500ms）→ 再回调 `onSettle`」。
 * 灵感编辑页在 `onSettle` 里才做「块换位 + 相邻图片补空 Text 块」，于是观感永远是
 * 「图先滑到位 → 停顿 → 空行才弹出」。而库 state 用 `remember(list, …)` 重建、内部数组按
 * 构造时 size 定长，**拖拽期间改列表（提前补空行）会让拖拽当场断掉**，所以只能把提交提前。
 *
 * **本 fork 相对原库的改动**（其余拖拽手势 / 换位阈值算法逐行一致）：
 * 1. `settle()`：先抓「落下瞬间每一项的视觉 Y」快照 → **立即** `onSettle` → 落位滑行交给
 *    [BlocksGlideController] 在重排后统一接续（见 [BlocksReorderableListState.settle]）；
 * 2. 新增 `itemKey`：既用于组合身份锚定，也用于把落位滑行归属到具体块；
 * 3. 裁剪：只保留纵向 Column（删除 `ReorderableRow` / 横向分支 / layoutDirection 分支），
 *    项间距改由显式参数 [BlocksReorderableColumn] 的 `itemSpacing` 提供。
 */

/** 单项在纵向主轴上的区间（start 为相对父容器顶边，size 为高度，单位 px） */
internal data class BlocksItemInterval(val start: Float = 0f, val size: Int = 0) {
    /** 区间中心（用于判断被拖块是否越过本项） */
    val center: Float
        get() = start + size / 2

    /** 区间结束位置 */
    val end: Float
        get() = start + size
}

/** 在列表中查找第一个满足 [predicate] 的下标，找不到返回 null */
private inline fun <T> List<T>.firstIndexOfIndexed(predicate: (Int, T) -> Boolean): Int? {
    forEachIndexed { i, e -> if (predicate(i, e)) return i }
    return null
}

/** 在列表中查找最后一个满足 [predicate] 的下标，找不到返回 null */
private inline fun <T> List<T>.lastIndexOfIndexed(predicate: (Int, T) -> Boolean): Int? {
    for (i in lastIndex downTo 0) {
        if (predicate(i, this[i])) return i
    }
    return null
}

/** 原库同款弹簧：中低刚度临界阻尼，落位滑行与让位位移共用 */
private val blocksAnimationSpec = spring<Float>(
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * 拖拽手势实现（fork 自原库同包 `draggable.kt` 的 `internal fun Modifier.draggable`）。
 *
 * 原函数带 `dragGestureDetector` 参数（可切换"按下即拖 / 长按后拖"），而 foundation 的
 * [androidx.compose.foundation.gestures.draggable] 没有这个能力，且原函数是 internal
 * （跨模块不可见），故一并复制过来。
 *
 * @param key1 重建手势的 key（传 state 即可：state 一变手势重新开始）
 * @param enabled 是否启用（拖拽中会被用来禁用其它项的手柄）
 * @param interactionSource 用于发射 [DragInteraction.Start] / Stop / Cancel
 * @param dragGestureDetector 手势检测器
 * @param onDragStarted 拖拽开始回调，参数为起始位置
 * @param onDragStopped 拖拽结束 / 取消回调
 * @param onDrag 拖拽位移回调，参数为本次事件与位移量
 */
private fun Modifier.blocksDraggable(
    key1: Any?,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    dragGestureDetector: DragGestureDetector = DragGestureDetector.Press,
    onDragStarted: (Offset) -> Unit = { },
    onDragStopped: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) = composed {
    val coroutineScope = rememberCoroutineScope()
    var dragInteractionStart by remember { mutableStateOf<DragInteraction.Start?>(null) }
    var dragStarted by remember { mutableStateOf(false) }

    DisposableEffect(key1) {
        onDispose {
            if (dragStarted) {
                dragInteractionStart?.also {
                    coroutineScope.launch {
                        interactionSource?.emit(DragInteraction.Cancel(it))
                    }
                }

                if (dragStarted) {
                    onDragStopped()
                }

                dragStarted = false
            }
        }
    }

    pointerInput(key1, enabled) {
        if (!enabled) {
            return@pointerInput
        }

        with(dragGestureDetector) {
            detect(
                onDragStart = {
                    dragStarted = true
                    dragInteractionStart = DragInteraction.Start().also {
                        coroutineScope.launch {
                            interactionSource?.emit(it)
                        }
                    }

                    onDragStarted(it)
                },
                onDragEnd = {
                    dragInteractionStart?.also {
                        coroutineScope.launch {
                            interactionSource?.emit(DragInteraction.Stop(it))
                        }
                    }

                    if (dragStarted) {
                        onDragStopped()
                    }

                    dragStarted = false
                },
                onDragCancel = {
                    dragInteractionStart?.also {
                        coroutineScope.launch {
                            interactionSource?.emit(DragInteraction.Cancel(it))
                        }
                    }

                    if (dragStarted) {
                        onDragStopped()
                    }

                    dragStarted = false
                },
                onDrag = onDrag,
            )
        }
    }
}

/**
 * 落位滑行控制器：**承接「提交提前」之后本该由库完成的落位动画**。
 *
 * 背景：库在落位时先播滑行再提交；本 fork 把提交提前到手指抬起瞬间，于是列表结构
 * （含新补的空行）立刻变成终态，而"眼睛看到的画面"还停在落下瞬间。若什么都不做，
 * 被拖块与正在让位的邻居都会**瞬跳**到终态。
 *
 * 做法：提交前记下每一项的「视觉顶边 Y（窗口坐标）」；重排后每一项重新测量到新的布局
 * 顶边 Y，两者之差就是"还需要补的位移"——把它 snapTo 出来再 animateTo(0)，画面就从
 * 落下瞬间**连续**过渡到终态：
 * - 被拖块：从手指位置滑到新槽位；
 * - 让位邻居：若弹簧还没跑完，接着把剩余行程滑完（不会瞬跳）；
 * - 新插入的空行块：没有历史位置，不参与滑行，空位由邻居让位"展开"出来。
 *
 * 生命周期：由 [BlocksReorderableColumn] `remember` 持有，**刻意活过 state 重建**
 * （列表一变 state 就被 `remember(list, …)` 重建，滑行状态必须放在它外面）。
 *
 * @param scope 启动滑行动画的协程作用域（跟随 composition 生命周期，随其取消）
 */
internal class BlocksGlideController(
    private val scope: CoroutineScope,
) {
    /** 一条待认领的滑行快照：落下瞬间的视觉顶边 + 该块的纵向初速度 */
    data class PendingGlide(
        /** 落下瞬间该块在窗口坐标系中的视觉顶边 Y（px） */
        val visualTopInWindow: Float,
        /** 滑行初速度（px/s），只有被拖块有值 */
        val initialVelocity: Float,
    )

    /** key → 待认领快照（仅主线程访问，普通 Map 即可） */
    private val pending = mutableMapOf<Any?, PendingGlide>()

    /** key → 正在进行的滑行位移（px，目标恒为 0）。用快照 Map：graphicsLayer 里读它才能被观察 */
    private val anims: SnapshotStateMap<Any?, Animatable<Float, AnimationVector1D>> = mutableStateMapOf()

    /** 每次提交自增：清扫协程据此判断自己是否已过期 */
    private var version = 0

    /** 该块是否正在滑行（用于抬升 zIndex，避免滑行时被邻居盖住） */
    fun isGliding(key: Any?): Boolean = anims.containsKey(key)

    /** 该块当前的滑行位移（px）；graphicsLayer 里读取，未滑行时恒为 0 */
    fun offsetOf(key: Any?): Float = anims[key]?.value ?: 0f

    /**
     * 提交完成后登记滑行快照（由 [BlocksReorderableListState.settle] 调用）。
     *
     * @param snapshot key → 待认领快照
     */
    fun onCommitted(snapshot: List<Pair<Any?, PendingGlide>>) {
        pending.clear()
        snapshot.forEach { (key, pendingGlide) -> if (key != null) pending[key] = pendingGlide }
        val myVersion = ++version
        if (pending.isEmpty()) return
        scope.launch {
            /**
             * 两帧后清扫无人认领的条目：重排后位置**没变**的项不会触发
             * `onGloballyPositioned`（也就无从认领），残留条目留着毫无意义，
             * 且会污染下一次提交前的判断。
             */
            withFrameNanos { }
            withFrameNanos { }
            if (myVersion == version) pending.clear()
        }
    }

    /**
     * 项测量到新位置时认领自己的滑行（由 [BlocksReorderableListScope.BlocksReorderableItem]
     * 的 `onGloballyPositioned` 调用）。
     *
     * @param key 该项的稳定身份
     * @param newTopInWindow 重排后该项在窗口坐标系中的顶边 Y（px）
     */
    fun consume(key: Any?, newTopInWindow: Float) {
        val snapshot = pending.remove(key) ?: return
        val startOffset = snapshot.visualTopInWindow - newTopInWindow
        /** 位置没变（或位移不足半像素）：无需滑行，直接结束 */
        if (abs(startOffset) < 0.5f) return
        val anim = Animatable(startOffset)
        anims[key] = anim
        scope.launch {
            try {
                anim.animateTo(
                    targetValue = 0f,
                    animationSpec = blocksAnimationSpec,
                    initialVelocity = snapshot.initialVelocity,
                )
            } finally {
                anims.remove(key)
            }
        }
    }

    /** 取消全部滑行（新一次拖拽开始时调用：拖拽位移与滑行位移不能叠加） */
    fun cancelAll() {
        pending.clear()
        version++
        anims.clear()
    }
}

/**
 * 重排状态机（fork 自原库 `ReorderableListState`）。
 *
 * @param listSize 列表长度（构造时定长；列表一变本 state 就会被 [BlocksReorderableColumn] 重建）
 * @param spacing 项间距（px），换位阈值计算要用
 * @param onMove 拖拽过程中"换位的预览位置"发生变化时回调
 * @param onSettle **手指抬起落位**时回调 `(fromIndex, toIndex)`；本 fork 会在**滑行之前**调用它
 * @param keyOf 下标 → 该项的稳定身份（用于把落位滑行归属到具体块）
 * @param glide 落位滑行控制器（跨 state 重建存活）
 * @param scope 启动内部动画的协程作用域
 */
internal class BlocksReorderableListState(
    listSize: Int,
    private val spacing: Float = 0f,
    private val onMove: () -> Unit,
    private val onSettle: (fromIndex: Int, toIndex: Int) -> Unit,
    private val keyOf: (Int) -> Any?,
    private val glide: BlocksGlideController,
    private val scope: CoroutineScope,
) {
    /** 每一项的布局区间（每次 layout 由 `onGloballyPositioned` 写回） */
    internal val itemIntervals = MutableList(listSize) { BlocksItemInterval() }

    /** 每一项的当前位移（px）：拖拽时是被拖块的跟手位移，让位时是邻居的让位位移 */
    internal val itemOffsets = List(listSize) { Animatable(0f) }.toMutableStateList()

    /** 正在被拖拽的项下标；拖拽结束（手指抬起）即置空 */
    private var draggingItemIndex by mutableStateOf<Int?>(null)

    /** 正在做落位动画的项下标（用于抬升 zIndex） */
    private var animatingItemIndex by mutableStateOf<Int?>(null)

    /**
     * 列容器自身在窗口坐标系中的顶边 Y（px）。
     * 用于把"项相对列的局部坐标"换算成窗口坐标——[settle] 里的快照与项侧
     * `positionInWindow()` 必须同一坐标系才能相减。
     */
    internal var columnTopInWindow: Float = 0f

    /** 是否有任意一项正在被拖拽（手柄据此禁用其它项的拖拽） */
    internal val isAnyItemDragging by derivedStateOf {
        draggingItemIndex != null
    }

    /** 每一项的拖拽状态机：把指针位移写进 [itemOffsets]，并按跨越情况给邻居施加让位位移 */
    internal val draggableStates = List(listSize) { i ->
        DraggableState {
            if (!isItemDragging(i).value) return@DraggableState

            scope.launch {
                itemOffsets[i].snapTo(itemOffsets[i].targetValue + it)
            }

            val originalStart = itemIntervals[i].start
            val originalEnd = itemIntervals[i].end
            val size = itemIntervals[i].size
            val currentStart = itemIntervals[i].start + itemOffsets[i].targetValue
            val currentEnd = currentStart + size

            var moved = false

            itemIntervals.forEachIndexed { j, interval ->
                if (j != i) {
                    val targetOffset =
                        if (currentStart < originalStart && interval.center in currentStart..originalStart) {
                            size.toFloat() + spacing
                        } else if (currentStart > originalStart && interval.center in originalEnd..currentEnd) {
                            -(size.toFloat() + spacing)
                        } else {
                            0f
                        }

                    if (itemOffsets[j].targetValue != targetOffset) {
                        scope.launch {
                            itemOffsets[j].animateTo(targetOffset, blocksAnimationSpec)
                        }
                        moved = true
                    }
                }
            }

            if (moved) {
                onMove()
            }
        }
    }.toMutableStateList()

    /** 第 [i] 项是否正在被拖拽 */
    internal fun isItemDragging(i: Int): State<Boolean> {
        return derivedStateOf {
            i == draggingItemIndex
        }
    }

    /** 第 [i] 项是否正在做落位动画 */
    internal fun isItemAnimating(i: Int): State<Boolean> {
        return derivedStateOf {
            i == animatingItemIndex
        }
    }

    /** 开始拖拽第 [i] 项：先取消可能残留的落位滑行，避免两种位移叠加 */
    internal fun startDrag(i: Int) {
        glide.cancelAll()
        draggingItemIndex = i
        animatingItemIndex = i
    }

    /**
     * 落位（手指抬起）：**本 fork 的唯一语义改动点**。
     *
     * 原库顺序：`animateTo(落位滑行)` → `onSettle`；
     * 本 fork 顺序：**抓快照 → `onSettle`（提交）→ 滑行交给 [glide] 接续**。
     *
     * 这样调用方（灵感编辑页的 `moveBlock`）在手指抬起的**同一帧**内完成
     * 「块换位 + 相邻图片补空行」，换位与空行不再有先后之分；
     * 视觉过渡由 [BlocksGlideController] 在重排后统一补上。
     */
    internal suspend fun settle(i: Int, velocity: Float) {
        val originalStart = itemIntervals[i].start
        val originalEnd = itemIntervals[i].end
        val size = itemIntervals[i].size
        val currentStart = itemIntervals[i].start + itemOffsets[i].targetValue
        val currentEnd = currentStart + size

        /**
         * 目标下标的判定：被拖块"越过"了哪些项的中心，就落到哪一项的位置。
         * （原库用 `..<` 半开区间运算符，这里改写为显式比较，避免依赖该运算符的 stability）
         */
        val targetIndexFunc =
            if (currentStart < originalStart) { j: Int, interval: BlocksItemInterval ->
                j != i && interval.center >= currentStart && interval.center < originalStart
            } else if (currentStart > originalStart) { j: Int, interval: BlocksItemInterval ->
                j != i && interval.center >= originalEnd && interval.center < currentEnd
            } else null
        val targetIndex = targetIndexFunc?.let {
            if (currentStart < originalStart) {
                itemIntervals.firstIndexOfIndexed(it)
            } else {
                itemIntervals.lastIndexOfIndexed(it)
            }
        }

        draggingItemIndex = null

        if (targetIndex != null) {
            /** ① 抓快照：落下瞬间每一项的"视觉"顶边（布局顶边 + 当前位移），换算到窗口坐标 */
            val snapshot = ArrayList<Pair<Any?, BlocksGlideController.PendingGlide>>(itemIntervals.size)
            for (j in itemIntervals.indices) {
                snapshot += keyOf(j) to BlocksGlideController.PendingGlide(
                    visualTopInWindow = columnTopInWindow + itemIntervals[j].start + itemOffsets[j].value,
                    /** 初速度只属于被拖块（邻居让位弹簧的速度量级可忽略，按 0 处理） */
                    initialVelocity = if (j == i) velocity else 0f,
                )
            }

            /** ② 先提交：调用方在本帧内完成换位 + 补空行（这次 fork 的全部目的） */
            onSettle(i, targetIndex)

            /** ③ 登记滑行：重排后由每一项自行认领（见 [BlocksGlideController.consume]） */
            glide.onCommitted(snapshot)

            /**
             * ④ 兜底：若调用方实际上没改列表（state 未重建 → 项不会重新测量 → 滑行无从认领），
             * 旧位移仍需归零，否则被拖块会停在半路。
             */
            for (j in itemOffsets.indices) {
                if (itemOffsets[j].targetValue != 0f) {
                    scope.launch {
                        itemOffsets[j].animateTo(
                            targetValue = 0f,
                            animationSpec = blocksAnimationSpec,
                            initialVelocity = if (j == i) velocity else 0f,
                        )
                    }
                }
            }
        } else {
            /** 没有跨越任何项：沿用原库行为，被拖块原地弹回 */
            itemOffsets[i].animateTo(0f, blocksAnimationSpec, initialVelocity = velocity)
        }

        animatingItemIndex = null
    }
}

/** 单项作用域：提供拖拽手柄 modifier */
interface BlocksReorderableListItemScope {
    /**
     * 把调用 modifier 的元素变成拖拽手柄（按下即拖）。
     *
     * @param enabled 是否允许拖拽
     * @param interactionSource 用于发射 [DragInteraction.Start]
     * @param onDragStarted 拖拽开始回调
     * @param onDragStopped 拖拽结束回调，参数为纵向速度（px/s）
     * @param dragGestureDetector 手势检测器（按下即拖 / 长按后拖）
     */
    fun Modifier.draggableHandle(
        enabled: Boolean = true,
        onDragStarted: (startedPosition: Offset) -> Unit = {},
        onDragStopped: (velocity: Float) -> Unit = {},
        interactionSource: MutableInteractionSource? = null,
        dragGestureDetector: DragGestureDetector = DragGestureDetector.Press,
    ): Modifier

    /**
     * 把调用 modifier 的元素变成拖拽手柄（**长按后**才能拖）。
     *
     * @param enabled 是否允许拖拽
     * @param interactionSource 用于发射 [DragInteraction.Start]
     * @param onDragStarted 拖拽开始回调
     * @param onDragStopped 拖拽结束回调，参数为纵向速度（px/s）
     */
    fun Modifier.longPressDraggableHandle(
        enabled: Boolean = true,
        onDragStarted: (startedPosition: Offset) -> Unit = {},
        onDragStopped: (velocity: Float) -> Unit = {},
        interactionSource: MutableInteractionSource? = null,
    ): Modifier
}

/** [BlocksReorderableListItemScope] 实现：把拖拽手势接到 [BlocksReorderableListState] 上 */
internal class BlocksReorderableListItemScopeImpl(
    private val state: BlocksReorderableListState,
    private val index: Int,
) : BlocksReorderableListItemScope {

    /**
     * 组装拖拽手柄 modifier。
     *
     * 用 `composed` 包一层是必须的：`enabled` 依赖 `isItemDragging(index)` 这个 State，
     * 只有在组合期读取才会被观察（拖拽中要据此禁用其它项的手柄）。
     */
    override fun Modifier.draggableHandle(
        enabled: Boolean,
        onDragStarted: (startedPosition: Offset) -> Unit,
        onDragStopped: (velocity: Float) -> Unit,
        interactionSource: MutableInteractionSource?,
        dragGestureDetector: DragGestureDetector,
    ) = composed {
        val velocityTracker = remember { VelocityTracker() }
        val coroutineScope = rememberCoroutineScope()

        blocksDraggable(
            key1 = state,
            enabled = enabled && (state.isItemDragging(index).value || !state.isAnyItemDragging),
            interactionSource = interactionSource,
            dragGestureDetector = dragGestureDetector,
            onDragStarted = {
                state.startDrag(index)
                onDragStarted(it)
            },
            onDragStopped = {
                val velocity = velocityTracker.calculateVelocity()
                velocityTracker.resetTracking()

                val velocityVal = velocity.y
                coroutineScope.launch { state.settle(index, velocityVal) }
                onDragStopped(velocityVal)
            },
            onDrag = { change, dragAmount ->
                velocityTracker.addPointerInputChange(change)

                state.draggableStates[index].dispatchRawDelta(dragAmount.y)
            },
        )
    }

    override fun Modifier.longPressDraggableHandle(
        enabled: Boolean,
        onDragStarted: (startedPosition: Offset) -> Unit,
        onDragStopped: (velocity: Float) -> Unit,
        interactionSource: MutableInteractionSource?,
    ) = draggableHandle(
        enabled = enabled,
        onDragStarted = onDragStarted,
        onDragStopped = onDragStopped,
        interactionSource = interactionSource,
        dragGestureDetector = DragGestureDetector.LongPress,
    )
}

/**
 * 重排列作用域（纵向）：提供 [BlocksReorderableItem]。
 *
 * @param state 重排状态机
 * @param glide 落位滑行控制器，与 state 分开持有（跨 state 重建存活）
 * @param index 本项下标
 * @param key 本项的稳定身份，落位滑行按它归属
 * @param isAnimating 本项是否正在做落位动画
 */
open class BlocksReorderableListScope internal constructor(
    private val state: BlocksReorderableListState,
    private val glide: BlocksGlideController,
    private val index: Int,
    private val key: Any?,
    private val isAnimating: Boolean,
) {
    /**
     * 一个可重排的项。
     *
     * 相对原库新增两点：
     * 1. `onGloballyPositioned` 里调用 [BlocksGlideController.consume]，重排后认领自己的落位滑行；
     * 2. `translationY` = 库内位移 + 落位滑行位移（两者互斥：拖拽期只有前者，落位后只有后者）。
     *
     * @param modifier 项的外层 modifier
     * @param content 项内容（可在内部调用 `Modifier.longPressDraggableHandle()` 指定手柄）
     */
    @Composable
    fun BlocksReorderableItem(
        modifier: Modifier = Modifier,
        content: @Composable BlocksReorderableListItemScope.() -> Unit,
    ) {
        val isGliding = glide.isGliding(key)
        Box(
            modifier = modifier
                .onGloballyPositioned { cord ->
                    state.itemIntervals[index] = BlocksItemInterval(
                        start = cord.positionInParent().y,
                        size = cord.size.height,
                    )
                    /** 重排后位置变化 → 触发本项的落位滑行认领（无待办时是空操作） */
                    glide.consume(key, cord.positionInWindow().y)
                }
                .graphicsLayer {
                    translationY = state.itemOffsets[index].value + glide.offsetOf(key)
                }
                .zIndex(if (isAnimating || isGliding) 1f else 0f),
        ) {
            BlocksReorderableListItemScopeImpl(
                state = state,
                index = index,
            ).content()
        }
    }
}

/** 纵向列专用的重排作用域（把 [ColumnScope] 能力透传给内容） */
class BlocksReorderableColumnScope internal constructor(
    state: BlocksReorderableListState,
    glide: BlocksGlideController,
    index: Int,
    key: Any?,
    isAnimating: Boolean,
    private val scope: ColumnScope,
) : BlocksReorderableListScope(state, glide, index, key, isAnimating), ColumnScope by scope

/**
 * 可重排的纵向列表：长按手柄拖动即可重排。
 *
 * 与原库同名函数的差异见本文件头部注释；**关键差异是 `onSettle` 在落位滑行之前调用**，
 * 因此调用方在 `onSettle` 里对列表做的任何结构性改动（重排、插入新项）都会与"手指抬起"
 * 同帧生效，其后由 [BlocksGlideController] 把画面连续过渡到终态。
 *
 * @param list 列表数据
 * @param onSettle 落位回调 `(fromIndex, toIndex)`；**在该回调里修改列表是安全的、且是推荐做法**
 * @param modifier 外层 modifier
 * @param itemKey 项的稳定身份（组合锚定 + 落位滑行归属）
 * @param itemSpacing 项间距；须与 [verticalArrangement] 的 spacing 保持一致
 * @param verticalArrangement 纵向排布
 * @param horizontalAlignment 横向对齐
 * @param onMove 拖拽过程中预览位置变化时回调
 * @param content 项内容，句柄 modifier 由调用方自行放置
 */
@Composable
fun <T> BlocksReorderableColumn(
    list: List<T>,
    onSettle: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
    itemKey: (T) -> Any?,
    itemSpacing: Dp = 0.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    onMove: () -> Unit = {},
    content: @Composable BlocksReorderableColumnScope.(index: Int, item: T, isDragging: Boolean) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val spacing = with(density) { itemSpacing.toPx() }
    /** 滑行控制器刻意独立于 state 之外：列表一变 state 就被重建，滑行状态必须活过它 */
    val glide = remember { BlocksGlideController(coroutineScope) }
    val state = remember(list, spacing) {
        BlocksReorderableListState(
            listSize = list.size,
            spacing = spacing,
            onMove = onMove,
            onSettle = onSettle,
            keyOf = { index -> if (index in list.indices) itemKey(list[index]) else null },
            glide = glide,
            scope = coroutineScope,
        )
    }

    Column(
        modifier = modifier.onGloballyPositioned { cord ->
            /** 列自身的窗口顶边：供 [BlocksReorderableListState.settle] 把局部坐标换算成窗口坐标 */
            state.columnTopInWindow = cord.positionInWindow().y
        },
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
    ) {
        list.forEachIndexed { i, item ->
            val isDragging by state.isItemDragging(i)
            val isAnimating by state.isItemAnimating(i)

            BlocksReorderableColumnScope(
                state = state,
                glide = glide,
                index = i,
                key = itemKey(item),
                isAnimating = isAnimating,
                scope = this,
            ).content(i, item, isDragging)
        }
    }
}
