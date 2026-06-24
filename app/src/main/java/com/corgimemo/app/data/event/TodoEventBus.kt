package com.corgimemo.app.data.event

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 待办事件总线
 *
 * 解决编辑页保存/删除后首页数据不刷新的问题：
 * 编辑器实例的 HomeViewModel 作用域是 Editor 的 NavBackStackEntry，
 * 与首页的 HomeViewModel 是不同实例。在编辑器上调用 homeViewModel.xxx()
 * 只能更新编辑器自己那个即将销毁的实例，首页的实例拿不到通知。
 *
 * 通过全局 SharedFlow 事件总线，让首页 HomeViewModel 订阅事件后主动刷新。
 *
 * 现有 emitter：
 * - TodoEditViewModel.saveAllGroups() → 发出 TodoSaved
 *
 * 现有 subscriber：
 * - HomeViewModel.init { ... } → 收到事件后调用 refreshAllData()
 */
object TodoEventBus {
    private val _events = MutableSharedFlow<TodoEvent>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<TodoEvent> = _events.asSharedFlow()

    /** 发出事件（非挂起，调用方可在任意线程调用） */
    fun emit(event: TodoEvent) {
        _events.tryEmit(event)
    }
}

/** 待办事件类型 */
sealed class TodoEvent {
    /** 待办保存（新建或更新）后发出 */
    object TodoSaved : TodoEvent()

    /** 待办删除后发出 */
    object TodoDeleted : TodoEvent()
}
