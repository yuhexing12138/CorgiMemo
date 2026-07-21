package com.corgimemo.app.ui.screens.date

import androidx.activity.compose.BackHandler
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.ui.components.AppSnackbarHost
import com.corgimemo.app.ui.components.LinkedCardsRow /** v2026-07-22 新增：关联卡片 Chip 流展示组件 */
import com.corgimemo.app.ui.components.LinkedCardPreviewDialog /** v2026-07-22 新增：关联卡片预览弹窗 */
import com.corgimemo.app.ui.components.RelationPickerBottomSheet /** v2026-07-22 新增：多选关联选择 BottomSheet */
import com.corgimemo.app.ui.components.ReminderPickerBottomSheet
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.screens.date.components.AvatarWithEdit
import com.corgimemo.app.ui.screens.date.components.DateTypePickerBottomSheet
import com.corgimemo.app.ui.screens.date.components.DateTypePickerResult
import com.corgimemo.app.ui.screens.date.components.SpecialDateFeatureRow
import com.corgimemo.app.viewmodel.DateCategory
import com.corgimemo.app.viewmodel.SaveState
import com.corgimemo.app.viewmodel.SpecialDateQuickCreateViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 特殊日期快速创建/编辑页
 *
 * 设计说明：
 * - 新建模式：dateId = 0L，显示"添加"标题，底部按钮为"下一步"，跳转样式选择页
 * - 编辑模式：dateId > 0，显示"编辑"标题，底部按钮为"保存"，直接更新数据
 * - 仅显示 4 行核心功能：头像 / 名称 / 日期 / 类型 / 置顶 / 关联
 * - 不显示：备注 / 标签 / 图片 / 关联编辑 / 计时 / 重复 / 提醒
 *
 * 状态全部 Local 化管理，不写入 ViewModel。
 *
 * 退出行为：通过 [navigateBack] 在 popBackStack 之前设置 targetTab=DATE，
 * 让 MainScreen 切换到日期 tab；系统返回键 / 关闭按钮 / 弹窗打开时的返回键
 * 均被 BackHandler 统一拦截，确保从日期编辑页退出后始终回到日期页
 * （而非待办页等其他 tab）。弹窗打开时 BackHandler 仅关闭弹窗，避免误操作。
 *
 * @param navController 导航控制器
 * @param dateId 日期ID，0 表示新建模式，大于 0 表示编辑模式
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialDateQuickCreateScreen(
    navController: NavController,
    dateId: Long = 0L,
    viewModel: SpecialDateQuickCreateViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val isEditMode = dateId > 0
    val saveState by viewModel.saveState.collectAsState()

    // 自定义日期类型列表（与侧滑栏、数据统计页共享同一数据源）
    val customDateTypes by viewModel.customDateTypes.collectAsState()

    // 本地状态：名称
    var title by remember { mutableStateOf("") }
    // 本地状态：日期行显示文本（无 / YYYY年M月D日）
    var dateRowText by remember { mutableStateOf("无") }
    // 本地状态：已选日期时间戳（null 表示未选）
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    // 本地状态：是否置顶
    var isPinned by remember { mutableStateOf(false) }
    // 本地状态：日期选择弹窗显示开关
    var showDatePicker by remember { mutableStateOf(false) }
    // 本地状态：类型选择弹窗显示开关
    var showTypePicker by remember { mutableStateOf(false) }
    // 已选中的内置类型（用于持久化时存储枚举名）
    var selectedCategory by remember { mutableStateOf(DateCategory.OTHER) }
    // 已选中的自定义类型 ID（非 null 时表示选中自定义类型，存储为 "CUSTOM:<id>" 格式）
    var customCategoryId by remember { mutableStateOf<Long?>(null) }

    // 类型行显示文本（根据 selectedCategory 和 customCategoryId 自动计算）
    // 使用 derivedStateOf 确保自定义类型列表加载完成后自动更新显示
    val typeRowText by remember {
        derivedStateOf {
            when {
                customCategoryId != null -> {
                    val customType = customDateTypes.find { it.id == customCategoryId }
                    if (customType != null) "${customType.emoji} ${customType.name}"
                    else "已删除类型"
                }
                else -> selectedCategory.displayName
            }
        }
    }

    // 编辑模式下加载数据
    LaunchedEffect(dateId) {
        if (isEditMode) {
            viewModel.loadDate(dateId)
        }
    }

    // 监听加载完成，填充表单
    val loadedDate by viewModel.loadedDate.collectAsState()
    LaunchedEffect(loadedDate) {
        loadedDate?.let { date ->
            title = date.title
            selectedDateMillis = date.targetDate
            dateRowText = formatDateText(date.targetDate)
            isPinned = date.isPinned
            // 解析类型（统一 "CUSTOM:<id>" 格式）
            val categoryName = date.category
            if (categoryName.startsWith("CUSTOM:")) {
                // 自定义类型：提取 ID，typeRowText 由 derivedStateOf 自动计算
                val id = categoryName.removePrefix("CUSTOM:").toLongOrNull()
                selectedCategory = DateCategory.OTHER
                customCategoryId = id
            } else {
                // 内置类型：匹配 DateCategory 枚举
                val presetCategory = DateCategory.entries.firstOrNull { it.name == categoryName }
                selectedCategory = presetCategory ?: DateCategory.OTHER
                customCategoryId = null
            }
        }
    }

    // 监听保存结果
    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is SaveState.Success -> {
                navController.previousBackStackEntry?.savedStateHandle?.set("targetTab", TabItem.DATE.name)
                navController.popBackStack()
            }
            is SaveState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.resetSaveState()
            }
            else -> {}
        }
    }

    // ========== v2026-07-22 新增：关联管理状态 ==========
    /** 关联列表（编辑模式下按当前日期 id 加载） */
    val relations by viewModel.relations.collectAsState()
    /** 关联ID → 标题映射（由 ViewModel 异步加载并缓存） */
    val relationTitles by viewModel.relationTitles.collectAsState()
    /** 当前预览卡片的详情（供 LinkedCardPreviewDialog 展示） */
    val cardDetail by viewModel.cardDetail.collectAsState()
    /** 卡片详情加载中标志 */
    val cardDetailLoading by viewModel.cardDetailLoading.collectAsState()
    /** 关联预览 Dialog 状态（null=关闭，非null=显示该关联的预览） */
    var previewingRelation by remember { mutableStateOf<CardRelation?>(null) }
    /** 关联选择 BottomSheet 状态 */
    var showRelationPicker by remember { mutableStateOf(false) }

    /**
     * v2026-07-22 新增：监听 previewingRelation 变化，自动加载/清空卡片详情
     */
    LaunchedEffect(previewingRelation) {
        val relation = previewingRelation
        if (relation != null) {
            viewModel.loadCardDetail(relation.targetType, relation.targetId)
        } else {
            viewModel.clearCardDetail()
        }
    }

    // 占位提示文本
    val developingMessage = "功能开发中，敬请期待"

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
     * 拦截系统返回事件：弹窗打开时仅关闭弹窗
     *
     * 自实现的遮罩弹窗（不是 Material Dialog/BottomSheet）默认不响应系统返回键，
     * 这里拦截后只关闭弹窗，避免按返回键直接退出页面导致用户误操作丢失输入。
     */
    BackHandler(enabled = showDatePicker || showTypePicker) {
        showDatePicker = false
        showTypePicker = false
    }

    /**
     * 拦截系统返回事件（侧滑返回 / 系统返回键）
     *
     * 确保所有退出方式（应用内关闭按钮、系统返回键）
     * 都经过 navigateBack()，统一设置 targetTab=DATE，让 MainScreen 切换到日期 tab。
     */
    BackHandler { navigateBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "编辑" else "添加",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
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
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
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

            // 关联行（v2026-07-22 开放关联功能）
            SpecialDateFeatureRow(
                title = "关联",
                trailingText = if (relations.isEmpty()) "+ 添加" else "🔗×${relations.size}",
                onClick = { showRelationPicker = true }
            )

            // v2026-07-22 新增：关联 Chip 流展示（关联行下方）
            if (relations.isNotEmpty()) {
                LinkedCardsRow(
                    relations = relations,
                    groupId = 0,
                    relationTitles = relationTitles,
                    onAddClick = { showRelationPicker = true },
                    onChipClick = { relation -> previewingRelation = relation },
                    onChipDelete = { relationId, _ -> viewModel.deleteRelation(relationId) },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(24.dp))

            // 底部按钮（新建模式显示"下一步"，编辑模式显示"保存"）
            Button(
                onClick = {
                    // 防御：必须先选日期,否则给出 Snackbar 提示并终止
                    if (selectedDateMillis == null) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("请先选择日期")
                        }
                        return@Button
                    }
                    // 统一存储格式：自定义类型用 "CUSTOM:<id>"，内置类型用枚举名
                    val categoryValue = when {
                        customCategoryId != null -> "CUSTOM:$customCategoryId"
                        else -> selectedCategory.name
                    }
                    if (isEditMode) {
                        // 编辑模式：直接保存更新
                        val currentDate = loadedDate ?: return@Button
                        viewModel.updateDate(
                            currentDate.copy(
                                title = title.ifBlank { "未命名" },
                                targetDate = selectedDateMillis!!,
                                category = categoryValue,
                                isPinned = isPinned,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    } else {
                        // 新建模式：跳转样式选择页
                        // 跳转 SpecialDateCardStyleScreen,4 个参数通过 URL Query 传递
                        navController.navigate(
                            Screen.SpecialDateCardStyle.createRoute(
                                title = title.ifBlank { "未命名" },
                                date = selectedDateMillis!!,
                                category = categoryValue,
                                pin = isPinned
                            )
                        )
                    }
                },
                enabled = saveState !is SaveState.Saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (saveState is SaveState.Saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isEditMode) "保存" else "下一步",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
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
            customDateTypes = customDateTypes,
            onDismissRequest = { showTypePicker = false },
            onSelected = { result ->
                when (result) {
                    is DateTypePickerResult.BuiltIn -> {
                        selectedCategory = result.category
                        customCategoryId = null
                    }
                    is DateTypePickerResult.CustomExisting -> {
                        selectedCategory = DateCategory.OTHER
                        customCategoryId = result.customType.id
                    }
                    is DateTypePickerResult.CustomNew -> {
                        // 新建自定义类型：先创建，再选中
                        // typeRowText 由 derivedStateOf 在 customDateTypes 更新后自动刷新
                        coroutineScope.launch {
                            val newId = viewModel.addCustomType(result.name)
                            selectedCategory = DateCategory.OTHER
                            customCategoryId = newId
                        }
                    }
                }
                showTypePicker = false
            }
        )
    }

    // v2026-07-22 新增：关联选择 BottomSheet（多选模式，跨待办/灵感/日期三种类型）
    if (showRelationPicker) {
        // 排除已关联的卡片，避免重复添加
        val excludeIds = relations
            .map { it.targetType to it.targetId }
            .toSet()
        RelationPickerBottomSheet(
            visible = true,
            excludeIds = excludeIds,
            onDismiss = { showRelationPicker = false },
            onConfirm = { selectedCards ->
                // 批量添加选中的关联（ViewModel 已知当前日期 id）
                val cards = selectedCards.map { it.cardType to it.cardId }
                viewModel.addRelations(cards)
                showRelationPicker = false
            },
            searchCards = { query, callback -> viewModel.searchCards(query, callback) }
        )
    }

    // v2026-07-22 新增：关联预览 Dialog（点击 Chip 后弹出，按类型差异化展示详情）
    previewingRelation?.let { relation ->
        LinkedCardPreviewDialog(
            relation = relation,
            cardDetail = cardDetail,
            isLoading = cardDetailLoading,
            onDismiss = { previewingRelation = null },
            onUnlink = { relationId ->
                viewModel.deleteRelation(relationId)
                previewingRelation = null
            },
            onJumpToDetail = { cardType, cardId ->
                // 根据卡片类型路由到对应详情/编辑页
                when (cardType) {
                    "todo" -> navController.navigate("todo_edit/$cardId")
                    "inspiration" -> navController.navigate("inspiration_edit/$cardId")
                    "date" -> navController.navigate("date_detail/$cardId")
                }
                previewingRelation = null
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
