package com.corgimemo.app.ui.screens.date

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.core.view.WindowCompat
import com.corgimemo.app.data.model.DateCardColor
import com.corgimemo.app.data.model.DateCardStyle
import com.corgimemo.app.data.model.topBarColor
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardColorPicker
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleRenderer
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleSelector
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleTab
import com.corgimemo.app.ui.screens.date.components.cardstyle.DateCardStyleTabs
import com.corgimemo.app.viewmodel.SaveState
import com.corgimemo.app.viewmodel.SpecialDateCardStyleViewModel
import kotlinx.coroutines.launch

/**
 * 日期卡片样式选择页（新建日期流程第二步）
 *
 * 页面结构（自上而下）：
 * 1. 顶部 TopAppBar：仅保留返回箭头，标题留空（Spacer 占位以保持居中布局风格）
 * 2. 卡片预览区：使用 [DateCardStyleRenderer] 渲染用户当前选中的大卡片样式 + 卡片颜色
 * 3. 样式 / 颜色 Tab 切换条：真实切换两个 Tab,COLOR 切换为颜色选择
 * 4. 底部选择器（根据 tab 切换）：
 *    - STYLE Tab → [DateCardStyleSelector] 列出所有可用的 [DateCardStyle]
 *    - COLOR Tab → [DateCardColorPicker] 显示 2×7 颜色网格（12 单色 + Default 占位 + Rainbow 占位）
 * 5. 保存按钮：点击后调用 ViewModel 落库（同时写入 cardStyle + cardColor）；期间显示 loading；
 *              成功/失败给出不同反馈
 *
 * 业务行为：
 * - 接收 QuickCreate 页传来的 4 个参数（title/dateMillis/category/isPinned）
 * - 用户选中的样式 + 颜色通过 [SpecialDateCardStyleViewModel.saveNewDate] 落库
 * - 落库成功后：用 `getBackStackEntry(home)` 拿到 MainScreen 的 entry 并写
 *   targetTab=DATE，再用 `popBackStack(route=home, inclusive=false)` 一次性弹出
 *   SpecialDateCardStyleScreen + SpecialDateQuickCreateScreen，直接回到 MainScreen，
 *   MainScreen 收到信号后切换到日期 tab，确保保存成功后直接回到日期页，
 *   相应日期卡片平滑出现。
 *
 * @param navController 导航控制器（用于 popBackStack + SavedStateHandle 通信）
 * @param title         来自 QuickCreate 的名称（已 URL 解码）
 * @param dateMillis    来自 QuickCreate 的目标日期时间戳（毫秒）
 * @param category      来自 QuickCreate 的分类（预设枚举名或自定义字符串）
 * @param isPinned      来自 QuickCreate 的置顶开关
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDateCardStyleScreen(
    navController: NavController,
    title: String,
    dateMillis: Long,
    category: String,
    isPinned: Boolean
) {
    /** Snackbar 宿主：用于显示保存失败 / 颜色 Tab 占位提示 */
    val snackbarHostState = remember { SnackbarHostState() }
    /** 协程作用域：用于在事件回调中启动 launch 显示 Snackbar */
    val coroutineScope = rememberCoroutineScope()
    /** 通过 Hilt 自动注入的 ViewModel：负责落库逻辑与保存状态机 */
    val viewModel: SpecialDateCardStyleViewModel = hiltViewModel()
    /** 订阅保存状态：Idle / Saving / Success / Error */
    val saveState by viewModel.saveState.collectAsState()

    /** 用户当前选中的卡片样式（本地状态，初始为 DateCardStyle.DEFAULT） */
    var selectedStyle by remember { mutableStateOf<DateCardStyle>(DateCardStyle.DEFAULT) }
    /** Tab 切换状态：默认停留在"样式"Tab */
    var selectedTab by remember { mutableStateOf(DateCardStyleTab.STYLE) }
    /** 用户当前选中的卡片颜色（本地状态，初始为 DateCardColor.DEFAULT） */
    var selectedCardColor by remember { mutableStateOf<DateCardColor>(DateCardColor.DEFAULT) }

    /**
     * 主屏背景色预览(实时跟随 [selectedCardColor] 变化)
     *
     * 设计目标:让用户在选择颜色时,当前页背景立刻变化,直观预览"如果保存后主屏会变成什么色"。
     * - DEFAULT:用主题默认背景色(Lavender 淡紫),保持与未选颜色前一致
     * - 选中具体颜色:用 [topBarColor] 派生调色板色,叠加 30% 透明度(alpha=0.3f),
     *   既能看出颜色变化,又不至于太刺眼影响卡片阅读
     */
    val screenBackgroundColor = if (selectedCardColor == DateCardColor.DEFAULT) {
        MaterialTheme.colorScheme.background
    } else {
        topBarColor(selectedCardColor).copy(alpha = 0.3f)
    }

    /**
     * 状态栏颜色实时跟随 [screenBackgroundColor] 变化
     *
     * 实现:
     * - 由于 MainActivity 已启用 `enableEdgeToEdge()`(强制状态栏透明 + 内容延伸到状态栏),
     *   `window.statusBarColor` API 在 Android 15+ (targetSdk=35) 已失效,不能直接改状态栏颜色。
     * - 正确方案:在 Scaffold 顶部加一个 Box 延伸到状态栏区域(`windowInsetsTopHeight(WindowInsets.statusBars)`),
     *   背景色 = [screenBackgroundColor] —— 这样状态栏区域被我们绘制的 Box 覆盖,看起来就像状态栏变色。
     * - `isAppearanceLightStatusBars`:根据背景色亮度自动决定状态栏图标用深色或浅色,
     *   浅色背景(>0.5)→ 深色图标,深色背景(≤0.5)→ 浅色图标。
     *
     * 使用 SideEffect:Compose 每次 recompose 后都会执行,确保图标深浅与 UI 同步。
     * `view.isInEditMode` 守卫:IDE 预览时不执行(避免崩溃)。
     */
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = true
        }
    }

    /**
     * 返回上一页辅助函数
     *
     * 在 popBackStack 之前设置 savedStateHandle["targetTab"] = "DATE"，
     * 让 MainScreen 接收到返回事件后切换到日期 tab，
     * 确保从日期编辑页退出后始终回到日期页（而非待办页等其他 tab）。
     */
    val navigateBack: () -> Unit = {
        navController.previousBackStackEntry?.savedStateHandle?.set("targetTab", TabItem.DATE.name)
        navController.popBackStack()
    }

    /**
     * 拦截系统返回事件（侧滑返回 / 系统返回键）
     *
     * 确保所有退出方式（应用内返回箭头、系统返回键）
     * 都经过 navigateBack()，统一设置 targetTab=DATE，让 MainScreen 切换到日期 tab。
     */
    BackHandler { navigateBack() }

    /**
     * 监听保存状态机的变化
     *
     * - Success：先向 home 路由（MainScreen）的 savedStateHandle 写 targetTab=DATE，
     *           然后用 popBackStack(route=home, inclusive=false) 一次性弹出
     *           SpecialDateCardStyleScreen + SpecialDateQuickCreateScreen，
     *           直接回到 MainScreen；MainScreen 收到 targetTab 信号后切换到日期 tab，
     *           相应日期卡片平滑出现。
     * - Error：弹 Snackbar 提示"保存失败,请重试"
     * - Idle / Saving：无需响应
     *
     * 注意：使用 saveState 作为 key 触发；为防止屏幕旋转后重复回调，Success 之后
     * ViewModel 状态仍会停留在 Success 直到用户离开页面或再次保存，故此处依赖
     * popBackStack 自然结束。
     */
    LaunchedEffect(saveState) {
        when (saveState) {
            is SaveState.Success -> {
                // 1) 向 MainScreen (home 路由) 发送 targetTab=DATE 信号
                //    当前栈：[home, date_create, date_card_style]
                //    用 getBackStackEntry(route) 显式获取 home 路由的 NavBackStackEntry
                //    （previousBackStackEntry 是 NavController 扩展属性，
                //    不能在 NavBackStackEntry 上再次链式调用）
                runCatching {
                    navController.getBackStackEntry("home")
                        .savedStateHandle
                        .set("targetTab", TabItem.DATE.name)
                }
                // 2) 一次性 popUpTo 回到 home,清空中间的 QuickCreateScreen
                //    inclusive=false 保留 home 自身在栈中（回到 MainScreen）
                navController.popBackStack(
                    route = "home",
                    inclusive = false
                )
            }
            is SaveState.Error -> {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("保存失败,请重试")
                }
            }
            else -> { /* Idle / Saving 不需响应 */ }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 状态栏区域颜色:由于 enableEdgeToEdge() 强制状态栏透明,
            // 用 Box 覆盖整个屏幕(包括状态栏区域)并设背景色 = screenBackgroundColor,
            // 实现"状态栏颜色跟随主屏背景色"的效果。
            // Scaffold 自身 containerColor 设为 Color.Transparent,让 Box 背景透出。
            .background(screenBackgroundColor)
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Spacer(Modifier) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    // 容器透明,让外层 Box 的 screenBackgroundColor 透出,
                    // 实现"状态栏区域颜色 = 主屏背景色"的效果。
                    // 注意:BackHandler 时 TopAppBar 不会画 background,只画内容(返回箭头)
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        // 容器背景透明,让外层 Box 的 screenBackgroundColor 透出,
        // 同时实现"状态栏区域颜色跟随主屏背景色变"的效果
        containerColor = Color.Transparent
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. 大卡片预览区（占据主要剩余空间）— 透传 cardColor
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                DateCardStyleRenderer(
                    style = selectedStyle,
                    title = title.ifBlank { "未命名" },
                    targetDateMillis = dateMillis,
                    cardColor = selectedCardColor,
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
            }

            // 2. 样式 / 颜色 Tab 切换条 — COLOR 不再是占位 Snackbar,真实切换
            DateCardStyleTabs(
                selected = selectedTab,
                onTabChange = { tab ->
                    selectedTab = tab
                }
            )

            // 3. 底部选择器（根据 tab 切换）
            when (selectedTab) {
                DateCardStyleTab.STYLE -> DateCardStyleSelector(
                    styles = DateCardStyle.all,
                    selected = selectedStyle,
                    onSelect = { selectedStyle = it },
                    targetDateMillis = dateMillis,
                    title = title.ifBlank { "未命名" },
                    cardColor = selectedCardColor
                )
                DateCardStyleTab.COLOR -> DateCardColorPicker(
                    selected = selectedCardColor,
                    onSelect = { selectedCardColor = it },
                    onRainbowClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("彩虹色功能开发中")
                        }
                    }
                )
            }

            // 与 QuickCreate "下一步" 按钮位置对齐:24dp 间距 + 底部按钮(无边距)
            Spacer(modifier = Modifier.height(24.dp))

            // 4. 保存按钮
            Button(
                onClick = {
                    // 防御：dateMillis 为 0 表示未传有效日期（极端深链场景），弹提示
                    if (dateMillis == 0L) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("请先返回选择日期")
                        }
                    } else {
                        // 正常路径：调用 ViewModel 落库
                        viewModel.saveNewDate(
                            title = title.ifBlank { "未命名" },
                            dateMillis = dateMillis,
                            category = category.ifBlank { "OTHER" },
                            isPinned = isPinned,
                            cardStyle = selectedStyle,
                            cardColor = selectedCardColor
                        )
                    }
                },
                // 保存中禁止重复点击
                enabled = saveState !is SaveState.Saving,
                // 水平 20dp padding(等同 QuickCreate Column 的 horizontal=20dp),
                // 使按钮宽度 = Column 宽度 - 40dp,与 QuickCreate "下一步" 按钮大小完全一致
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                // 保存中显示进度环
                if (saveState is SaveState.Saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = if (saveState is SaveState.Saving) "保存中..." else "保存",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            // 按钮后底部间距(24dp),与 QuickCreate "下一步" 按钮完全对齐
            Spacer(modifier = Modifier.height(24.dp))
        }
    }  // 关闭外层 Box(用于状态栏区域颜色)
    }
}
