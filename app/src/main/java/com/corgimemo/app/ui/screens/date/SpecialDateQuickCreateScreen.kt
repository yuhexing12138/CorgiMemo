package com.corgimemo.app.ui.screens.date

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.corgimemo.app.ui.components.ReminderPickerBottomSheet
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.screens.date.components.AvatarWithEdit
import com.corgimemo.app.ui.screens.date.components.DateTypePickerBottomSheet
import com.corgimemo.app.ui.screens.date.components.SpecialDateFeatureRow
import com.corgimemo.app.viewmodel.DateCategory
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 特殊日期快速创建页（新建专用）
 *
 * 设计说明：
 * - 仅显示 4 行核心功能：头像 / 名称 / 日期 / 类型 / 置顶 / 关联
 * - 不显示：备注 / 标签 / 图片 / 关联编辑 / 计时 / 重复 / 提醒
 * - "下一步"按钮当前为占位（功能开发中）
 *
 * 状态全部 Local 化管理，不写入 ViewModel。
 * V2.7 反馈：编辑模式（点击日期卡片）改为显示"编辑功能开发中" Snackbar 占位。
 *
 * @param navController 导航控制器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDateQuickCreateScreen(
    navController: NavController
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // 本地状态：名称
    var title by remember { mutableStateOf("") }
    // 本地状态：日期行显示文本（无 / YYYY年M月D日）
    var dateRowText by remember { mutableStateOf("无") }
    // 本地状态：已选日期时间戳（null 表示未选）
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    // 本地状态：类型行显示文本
    var typeRowText by remember { mutableStateOf("请选择") }
    // 本地状态：是否置顶
    var isPinned by remember { mutableStateOf(false) }
    // 本地状态：日期选择弹窗显示开关
    var showDatePicker by remember { mutableStateOf(false) }
    // 本地状态：类型选择弹窗显示开关
    var showTypePicker by remember { mutableStateOf(false) }
    // 已选中的类型（用于持久化时构建 "CUSTOM:xxx" 字符串）
    var selectedCategory by remember { mutableStateOf(DateCategory.OTHER) }
    var customCategoryName by remember { mutableStateOf<String?>(null) }

    // 占位提示文本
    val developingMessage = "功能开发中，敬请期待"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "添加",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "关闭"
                        )
                    }
                },
                actions = {
                    // 右侧留空以保持标题居中
                    Spacer(modifier = Modifier.width(48.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 头像区域
            AvatarWithEdit(
                onClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("头像选择$developingMessage")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 名称输入框（placeholder 透明度 0.5f 与灵感编辑页保持一致）
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        text = "请在这里输入名称...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                trailingIcon = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("标题编辑$developingMessage")
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "编辑标题",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 4 行核心功能
            SpecialDateFeatureRow(
                title = "日期",
                trailingText = dateRowText,
                onClick = { showDatePicker = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            SpecialDateFeatureRow(
                title = "类型",
                trailingText = typeRowText,
                onClick = { showTypePicker = true }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 置顶行（使用自定义 trailing 接收 Switch）
            SpecialDateFeatureRow(
                title = "置顶",
                showArrow = false,
                trailing = {
                    Switch(
                        checked = isPinned,
                        onCheckedChange = { isPinned = it }
                    )
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 关联行
            SpecialDateFeatureRow(
                title = "关联",
                trailingText = "+ 添加",
                onClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("关联$developingMessage")
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // 底部"下一步"按钮(跳转卡片样式选择页)
            Button(
                onClick = {
                    // 防御：必须先选日期,否则给出 Snackbar 提示并终止跳转
                    if (selectedDateMillis == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("请先选择日期")
                        }
                        return@Button
                    }
                    // 分类值约定:用户选择自定义时直接存 customCategoryName(如 "旅行"),
                    // 选择预设时存 DateCategory.name(如 "BIRTHDAY")。不添加任何前缀。
                    val categoryValue = customCategoryName ?: selectedCategory.name
                    // 跳转 SpecialDateCardStyleScreen,4 个参数通过 URL Query 传递
                    navController.navigate(
                        Screen.SpecialDateCardStyle.createRoute(
                            title = title.ifBlank { "未命名" },
                            date = selectedDateMillis!!,
                            category = categoryValue,
                            pin = isPinned
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "下一步",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    /**
     * 日期选择弹窗
     *
     * V2.9 反馈：**完全参考**待办编辑页设置提醒按钮的弹窗模式：
     * - 全屏半透明遮罩（Color(0x99000000) + navigationBarsPadding）
     * - 点击遮罩关闭弹窗
     * - 弹窗容器：固定高度 90% 屏幕 + 自定义 layout 按 4:1 比例定位（上方 4 份空白：下方 1 份空白）
     * - 弹窗内容：AnimatedVisibility + fadeIn + scaleIn(initialScale = 0.9f) + spring
     * - 内部承载 ReminderPickerBottomSheet（不带容器）
     * - 标题"设置提醒时间"，行标签"提醒时间"（与待办编辑页一致）
     */
    if (showDatePicker) {
        // 弹窗出现时隐藏键盘（与待办编辑页一致）
        androidx.compose.ui.platform.LocalFocusManager.current.clearFocus()

        // 使用 LocalConfiguration 获取屏幕高度 + LocalDensity 转 px（与待办编辑页一致）
        val configuration = LocalConfiguration.current
        val screenDensity = LocalDensity.current
        val screenHeightPx = remember(configuration) {
            with(screenDensity) { configuration.screenHeightDp.dp.toPx().toInt() }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 延伸到系统导航栏区域，确保遮罩层完全覆盖底部白条
                .navigationBarsPadding()
                .background(Color(0x99000000))
                // 点击遮罩关闭 picker
                .clickable(onClick = { showDatePicker = false })
        ) {
            // 弹窗容器：固定高度 + 自定义 layout 按 4:1 比例定位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clickable(
                        // 阻止事件穿透到底层遮罩
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    // 自定义布局：固定弹窗高度为屏幕 90%，按 4:1 比例定位
                    .layout { measurable, constraints ->
                        val screenH = screenHeightPx

                        // 固定弹窗高度：屏幕高度的 90%
                        val dialogHeight = if (screenH > 0) (screenH * 0.9).toInt() else 900

                        // 按 4:1 比例分配上下空白
                        // T/B = 4/1, T + h + B = H → B=(H-h)/5, T=4(H-h)/5
                        val bottomSpace = (screenH - dialogHeight) / 5
                        val topSpace = bottomSpace * 4

                        // 创建固定高度的约束，让子元素填满这个固定空间
                        val fixedConstraints = constraints.copy(
                            minHeight = dialogHeight,
                            maxHeight = dialogHeight
                        )

                        val placeable = measurable.measure(fixedConstraints)

                        layout(placeable.width, dialogHeight) {
                            placeable.placeRelative(0, topSpace.coerceAtLeast(0))
                        }
                    }
            ) {
                // 弹窗内容：带淡入+缩放动画
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    ) + scaleIn(
                        initialScale = 0.9f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                    )
                ) {
                    ReminderPickerBottomSheet(
                        initialDateMillis = selectedDateMillis,
                        // showAdvancedOptions 默认 true，启用时间/重复/截止日期/农历高级选项
                        title = "设置提醒时间",  // 与待办编辑页一致
                        rowLabel = "提醒时间",   // 与待办编辑页一致
                        onDismiss = { showDatePicker = false },
                        onConfirm = { dateMillis, _, _, _, _, _ ->
                            if (dateMillis != null) {
                                selectedDateMillis = dateMillis
                                dateRowText = formatDateText(dateMillis)
                            }
                            showDatePicker = false
                        }
                    )
                }
            }
        }
    }

    // 类型选择弹窗
    if (showTypePicker) {
        DateTypePickerBottomSheet(
            onDismissRequest = { showTypePicker = false },
            onSelected = { category, customName ->
                selectedCategory = category
                customCategoryName = customName
                typeRowText = customName ?: category.displayName
            }
        )
    }
}

/**
 * 格式化日期显示文本（YYYY年M月D日）
 *
 * @param timestamp 时间戳（毫秒）
 * @return 格式化后的中文日期字符串，例如 "2026年8月15日"
 */
private fun formatDateText(timestamp: Long): String {
    val date: LocalDate = Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    return "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
}
