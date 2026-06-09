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

/** 为编辑页底部工具栏添加导航栏 + 软键盘内边距 */
fun Modifier.safeAreaForEditBar(): Modifier = this.navigationBarsPadding().imePadding()

/** 为全屏内容区域同时添加状态栏和导航栏内边距 */
fun Modifier.safeAreaForContent(): Modifier = this.statusBarsPadding().navigationBarsPadding()
