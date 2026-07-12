package com.corgimemo.app.ui.screens.date

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.corgimemo.app.ui.components.ReminderPickerBottomSheet
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
 * 与 SpecialDateEditScreen 的区别：
 * - 仅显示 4 行核心功能：头像 / 名称 / 日期 / 类型 / 置顶 / 关联
 * - 不显示：备注 / 标签 / 图片 / 关联编辑 / 计时 / 重复 / 提醒
 * - "下一步"按钮当前为占位（功能开发中）
 *
 * 状态全部 Local 化管理，不写入 ViewModel。
 * 编辑模式（点击日期卡片）继续走旧版 SpecialDateEditScreen，本次不变。
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

            // 名称输入框
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = { Text("名称") },
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

            // 底部"下一步"按钮
            Button(
                onClick = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("下一步页面$developingMessage")
                    }
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

    // 日期选择弹窗（复用 ReminderPickerBottomSheet）
    // 真实签名（来自 app/src/main/java/com/corgimemo/app/ui/components/ReminderPickerBottomSheet.kt）：
    //   fun ReminderPickerBottomSheet(
    //       initialDateMillis: Long? = null,
    //       initialHour: Int = 13,
    //       initialMinute: Int = 35,
    //       initialRepeatType: Int = 0,
    //       initialCalendarEnabled: Boolean = false,
    //       showAdvancedOptions: Boolean = true,
    //       title: String = "设置提醒时间",
    //       rowLabel: String = "提醒时间",
    //       calendarRowSpacing: Dp? = null,
    //       inspirationPreview: @Composable ((date: LocalDate, hour: Int, minute: Int) -> Unit)? = null,
    //       initialDueDateMillis: Long? = null,
    //       onDismiss: () -> Unit,
    //       onConfirm: (dateMillis: Long?, hour: Int, minute: Int, repeatType: Int, calendarEnabled: Boolean, dueDateMillis: Long?) -> Unit
    //   )
    // 关键：showAdvancedOptions = false 隐藏时间/重复/截止日期/农历高级选项
    //       onConfirm 接收到 6 个参数（dateMillis, hour, minute, repeatType, calendarEnabled, dueDateMillis）
    if (showDatePicker) {
        ReminderPickerBottomSheet(
            initialDateMillis = selectedDateMillis,
            showAdvancedOptions = false,
            title = "选择日期",
            rowLabel = "日期",
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
