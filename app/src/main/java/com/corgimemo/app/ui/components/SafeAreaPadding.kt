package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Modifier

/**
 * 安全区域内边距扩展函数集
 *
 * 统一封装 Compose Foundation 的系统安全区域 API，
 * 提供语义化的 Modifier 扩展，确保全项目一致使用。
 *
 * 设计原则：
 * - 顶层组件（TopBar/BottomBar）各自处理自己一侧的安全区域
 * - 内容区（Content）同时处理顶部和底部安全区域
 * - 底部输入场景额外叠加 imePadding 适配软键盘
 *
 * 使用示例：
 * ```kotlin
 * // 顶栏：预留状态栏空间
 * Column(modifier = Modifier.safeAreaForTopBar()) { ... }
 *
 * // 底栏：预留导航栏 + 软键盘空间
 * Surface(modifier = Modifier.safeAreaForBottomBar()) { ... }
 *
 * // 编辑页底部工具栏（需适配软键盘）
 * EditToolbar(modifier = Modifier.safeAreaForEditBar()) { ... }
 *
 * // 全屏内容区：预留状态栏 + 导航栏
 * Box(modifier = Modifier.safeAreaForContent()) { ... }
 * ```
 */

/** 为顶部工具栏添加状态栏内边距（适配刘海屏/灵动岛等） */
fun Modifier.safeAreaForTopBar(): Modifier = this.statusBarsPadding()

/** 为底部导航栏添加系统导航栏内边距（适配手势导航/三键导航） */
fun Modifier.safeAreaForBottomBar(): Modifier = this.navigationBarsPadding()

/**
 * 为编辑页底部工具栏添加软键盘内边距
 *
 * v2026-07-22 改造：移除 navigationBarsPadding()，让 Surface 紧贴屏幕底端。
 * 改为在工具栏内部 Row 加 .navigationBarsPadding()（让按钮避开系统手势条），
 * 这是 Android 10+ edge-to-edge 的标准模式。
 *
 * 历史问题：
 * - 原本同时调用 navigationBarsPadding() + imePadding()，
 *   导致整个 Surface 容器被往上推 navigation bar 高度
 * - 圆角矩形与屏幕底端产生米黄色空隙（背景填充但无按钮）
 *
 * 修复原理：
 * - Surface 容器本身紧贴屏幕底端 → 圆角矩形背景完整铺到屏幕底
 * - 内容区（Row）加 navigationBarsPadding → 按钮自动上移避开手势条
 * - 软键盘弹起时，imePadding 仍把工具栏整体上移到键盘上方
 */
fun Modifier.safeAreaForEditBar(): Modifier = this.imePadding()

/** 为全屏内容区域同时添加状态栏和导航栏内边距 */
fun Modifier.safeAreaForContent(): Modifier = this.statusBarsPadding().navigationBarsPadding()
