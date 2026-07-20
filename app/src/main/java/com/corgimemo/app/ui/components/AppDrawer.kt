package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.CustomDateType
import com.corgimemo.app.ui.components.navigation.TabItem
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.DateCategory
import com.corgimemo.app.viewmodel.TagFilterMode

private const val DRAWER_ICON_ALL = "📋"
private const val DRAWER_ICON_UNCATEGORIZED = "📦"

private val categoryIcons = mapOf(
    0 to "📚",
    1 to "💼",
    2 to "🏠",
    3 to "🏃"
)

/**
 * 侧滑导航栏内容组件（统一版）
 *
 * 根据当前Tab动态切换内容区域：
 * - 待办页：分组管理（全部/未分类/自定义分组/最近删除）
 * - 灵感页：标签筛选（全部灵感/标签列表）
 * - 日期页：类型筛选（全部/倒计时/正计时/已归档）
 * - 我的页：快捷导航
 *
 * 用户区域和底部操作区所有页面共享。
 *
 * @param currentTab 当前选中的Tab
 * @param corgiData 柯基数据
 * @param categories 待办分组列表
 * @param todoCountByCategory 待办分组计数
 * @param selectedCategoryId 当前选中的待办分组ID
 * @param inspirationTags 灵感标签列表
 * @param selectedTags 当前选中的灵感标签集合（多选）
 * @param tagFilterMode 标签筛选模式（OR/AND/NOT）
 * @param tagCounts 每个标签对应的灵感数量
 * @param totalInspirationCount 灵感总数（用于"全部灵感"项计数）
 * @param selectedDateCategory 当前选中的日期类型(null=全部, "BIRTHDAY"=内置, "CUSTOM:42"=自定义)
 * @param dateCountByCategory 每个类型对应的日期计数
 * @param customDateTypes 自定义日期类型列表
 * @param onCategoryClick 待办分组点击回调
 * @param onAddCategoryClick 添加分组回调
 * @param onCategoryAction 分组操作回调
 * @param onTagClick 标签点击回调（传入标签名，由调用方切换选中状态）
 * @param onTagFilterModeChange 筛选模式切换回调
 * @param onClearTagSelection 清空所有选中标签回调（"全部灵感"点击时调用）
 * @param onAddTagClick 添加标签回调
 * @param onDateCategoryClick 日期类型点击回调
 * @param onAddCustomTypeClick 添加自定义类型回调
 * @param onCustomTypeAction 自定义类型操作回调
 * @param onSettingsClick 设置点击回调
 * @param onHelpClick 帮助点击回调
 * @param modifier 修饰符
 */
@Composable
fun AppDrawerContent(
    currentTab: TabItem,
    corgiData: CorgiData?,
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    selectedCategoryId: Long?,
    inspirationTags: List<String> = emptyList(),
    selectedTags: Set<String> = emptySet(),
    tagFilterMode: TagFilterMode = TagFilterMode.OR,
    tagCounts: Map<String, Int> = emptyMap(),
    totalInspirationCount: Int = 0,
    selectedDateCategory: String? = null,
    dateCountByCategory: Map<String, Int> = emptyMap(),
    customDateTypes: List<CustomDateType> = emptyList(),
    onCategoryClick: (Long?) -> Unit = {},
    onAddCategoryClick: () -> Unit = {},
    onCategoryAction: (CategoryAction) -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onTagFilterModeChange: (TagFilterMode) -> Unit = {},
    onClearTagSelection: () -> Unit = {},
    onAddTagClick: () -> Unit = {},
    onDateCategoryClick: (String?) -> Unit = {},
    onAddCustomTypeClick: () -> Unit = {},
    onCustomTypeAction: (DateTypeAction) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(top = 48.dp)
    ) {
        UserProfileSection(corgiData = corgiData)

        Spacer(modifier = Modifier.height(16.dp))

        when (currentTab) {
            TabItem.TODO -> {
                CategoryGroupSection(
                    categories = categories,
                    todoCountByCategory = todoCountByCategory,
                    selectedCategoryId = selectedCategoryId,
                    onCategoryClick = onCategoryClick,
                    onCategoryAction = onCategoryAction,
                    modifier = Modifier.weight(1f)
                )
                AddCategoryButton(text = "添加分组", onClick = onAddCategoryClick)
            }
            TabItem.INSPIRE -> {
                InspirationFilterSection(
                    tags = inspirationTags,
                    selectedTags = selectedTags,
                    filterMode = tagFilterMode,
                    tagCounts = tagCounts,
                    totalInspirationCount = totalInspirationCount,
                    onTagClick = onTagClick,
                    onFilterModeChange = onTagFilterModeChange,
                    onClearTagSelection = onClearTagSelection,
                    modifier = Modifier.weight(1f)
                )
                AddCategoryButton(text = "添加标签", onClick = onAddTagClick)
            }
            TabItem.DATE -> {
                DateTypeFilterSection(
                    selectedDateCategory = selectedDateCategory,
                    dateCountByCategory = dateCountByCategory,
                    customDateTypes = customDateTypes,
                    onDateCategoryClick = onDateCategoryClick,
                    onCustomTypeAction = onCustomTypeAction,
                    modifier = Modifier.weight(1f)
                )
                AddCategoryButton(text = "添加类型", onClick = onAddCustomTypeClick)
            }
            TabItem.PROFILE -> {
                ProfileQuickNavSection(
                    onSettingsClick = onSettingsClick,
                    modifier = Modifier.weight(1f)
                )
            }
            TabItem.EDIT -> { /* 中央编辑按钮不是真实Tab */ }
        }

        // 底部留白：避免添加按钮贴底，同时让布局在视觉上更透气
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun UserProfileSection(corgiData: CorgiData?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 柯基趴卧姿态帧动画（3帧/8fps 循环播放）
        FrameAnimation(
            animationType = AnimationType.LIE,
            fps = 8,
            isLooping = true,
            modifier = Modifier.size(48.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = corgiData?.name ?: "柯基",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (corgiData != null) {
                Text(
                    text = "Lv.${corgiData.level} 柯基少年",
                    fontSize = 13.sp,
                    color = Color(0xFF79747E)
                )
            }
        }
    }
}

@Composable
private fun CategoryGroupSection(
    categories: List<Category>,
    todoCountByCategory: Map<Long, Int>,
    selectedCategoryId: Long?,
    onCategoryClick: (Long?) -> Unit,
    onCategoryAction: (CategoryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "分组管理",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .fillMaxWidth()
                .background(UiColors.Primary)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部待办",
                    count = todoCountByCategory[-1L] ?: 0,
                    isSelected = selectedCategoryId == null,
                    showMenu = false,
                    onClick = { onCategoryClick(null) }
                )
            }

            item {
                CategoryItem(
                    icon = DRAWER_ICON_UNCATEGORIZED,
                    name = "未分类",
                    count = todoCountByCategory[0L] ?: 0,
                    isSelected = selectedCategoryId == 0L,
                    showMenu = false,
                    onClick = { onCategoryClick(0L) }
                )
            }

            items(categories) { category ->
                val icon = categoryIcons[category.type] ?: "📂"
                CategoryItem(
                    icon = icon,
                    name = category.name,
                    count = todoCountByCategory[category.id] ?: 0,
                    isSelected = selectedCategoryId == category.id,
                    showMenu = !category.isDefault,
                    onClick = { onCategoryClick(category.id) },
                    onMenuClick = {
                        onCategoryAction(
                            CategoryAction.ShowMenu(category)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryItem(
    icon: String,
    name: String,
    count: Int,
    isSelected: Boolean,
    showMenu: Boolean,
    textColor: Color = Color(0xFF1C1B1F),
    onClick: () -> Unit,
    onMenuClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = icon, fontSize = 20.sp)

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = name,
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) UiColors.Primary else textColor,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (count > 0) {
            Text(
                text = "($count)",
                fontSize = 13.sp,
                color = Color(0xFF79747E)
            )
        }

        if (showMenu) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "更多操作",
                    tint = Color(0xFF79747E),
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = Color(0xFFBDBDBD),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun AddCategoryButton(text: String = "添加分组", onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = UiColors.Primary,
            contentColor = Color.White
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 灵感标签筛选区域
 *
 * 布局：标题 → 搜索框 → 模式芯片（OR/AND/NOT）→ 标签列表（可滚动）
 *
 * @param tags 所有可选标签列表
 * @param selectedTags 当前选中的标签集合
 * @param filterMode 当前筛选模式
 * @param tagCounts 每个标签对应的灵感数量
 * @param totalInspirationCount 灵感总数（用于"全部灵感"项计数）
 * @param onTagClick 标签点击回调（由调用方切换选中状态）
 * @param onFilterModeChange 筛选模式切换回调
 * @param onClearTagSelection 清空选中标签回调（点击"全部灵感"时触发）
 * @param modifier 修饰符
 */
@Composable
private fun InspirationFilterSection(
    tags: List<String>,
    selectedTags: Set<String>,
    filterMode: TagFilterMode,
    tagCounts: Map<String, Int>,
    totalInspirationCount: Int,
    onTagClick: (String) -> Unit,
    onFilterModeChange: (TagFilterMode) -> Unit,
    onClearTagSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 搜索框本地状态（不需要 ViewModel 持久化）
    var searchQuery by remember { mutableStateOf("") }

    // 根据搜索词过滤标签列表（忽略大小写的模糊匹配）
    val filteredTags = if (searchQuery.isBlank()) {
        tags
    } else {
        tags.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier) {
        // 标题
        Text(
            text = "🏷️ 标签筛选",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        // 橙色分割线
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .fillMaxWidth()
                .background(UiColors.Primary)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 搜索框
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    text = "搜索标签",
                    fontSize = 14.sp,
                    color = Color(0xFF79747E)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = Color(0xFF79747E),
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "清除搜索",
                            tint = Color(0xFF79747E),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = UiColors.Primary,
                unfocusedBorderColor = Color(0xFFE0E0E0)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(52.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 筛选模式芯片（OR / AND / NOT）
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == TagFilterMode.OR,
                onClick = { onFilterModeChange(TagFilterMode.OR) },
                label = { Text("OR", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = UiColors.PrimaryLight,
                    selectedLabelColor = UiColors.Primary
                )
            )
            FilterChip(
                selected = filterMode == TagFilterMode.AND,
                onClick = { onFilterModeChange(TagFilterMode.AND) },
                label = { Text("AND", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = UiColors.PrimaryLight,
                    selectedLabelColor = UiColors.Primary
                )
            )
            FilterChip(
                selected = filterMode == TagFilterMode.NOT,
                onClick = { onFilterModeChange(TagFilterMode.NOT) },
                label = { Text("NOT", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = UiColors.PrimaryLight,
                    selectedLabelColor = UiColors.Primary
                )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 标签列表（可滚动）
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            // "全部灵感"选项
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部灵感",
                    count = totalInspirationCount,
                    isSelected = selectedTags.isEmpty(),
                    showMenu = false,
                    onClick = { onClearTagSelection() }
                )
            }

            // 过滤后的标签列表
            items(filteredTags) { tag ->
                CategoryItem(
                    icon = "#",
                    name = tag,
                    count = tagCounts[tag] ?: 0,
                    isSelected = tag in selectedTags,
                    showMenu = false,
                    onClick = { onTagClick(tag) }
                )
            }
        }
    }
}

/**
 * 日期类型筛选区域
 *
 * 展示：全部日期 → 8个内置 DateCategory → 自定义类型列表
 * 内置类型无菜单(显示箭头)，自定义类型有菜单(可重命名/删除)
 *
 * 注意：添加类型按钮由外层 AppDrawerContent 统一放置（与待办页/灵感页一致），
 * 避免内部 LazyColumn 无 weight 占据全部空间导致按钮不可见。
 *
 * @param selectedDateCategory 当前选中的类型(null=全部, "BIRTHDAY"=内置, "CUSTOM:42"=自定义)
 * @param dateCountByCategory 每个类型对应的日期计数
 * @param customDateTypes 自定义类型列表
 * @param onDateCategoryClick 类型点击回调
 * @param onCustomTypeAction 自定义类型操作回调
 * @param modifier 修饰符
 */
@Composable
private fun DateTypeFilterSection(
    selectedDateCategory: String?,
    dateCountByCategory: Map<String, Int>,
    customDateTypes: List<CustomDateType>,
    onDateCategoryClick: (String?) -> Unit,
    onCustomTypeAction: (DateTypeAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "📅 类型筛选",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .fillMaxWidth()
                .background(UiColors.Primary)
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            // 全部日期
            item {
                CategoryItem(
                    icon = DRAWER_ICON_ALL,
                    name = "全部日期",
                    count = dateCountByCategory.values.sum(),
                    isSelected = selectedDateCategory == null,
                    showMenu = false,
                    onClick = { onDateCategoryClick(null) }
                )
            }

            // 8 个内置类型
            items(DateCategory.entries.toList()) { dateCategory ->
                CategoryItem(
                    icon = dateCategory.emoji,
                    name = dateCategory.displayName,
                    count = dateCountByCategory[dateCategory.name] ?: 0,
                    isSelected = selectedDateCategory == dateCategory.name,
                    showMenu = false,
                    onClick = { onDateCategoryClick(dateCategory.name) }
                )
            }

            // 自定义类型（有菜单按钮）
            items(customDateTypes) { customType ->
                CategoryItem(
                    icon = customType.emoji,
                    name = customType.name,
                    count = dateCountByCategory["CUSTOM:${customType.id}"] ?: 0,
                    isSelected = selectedDateCategory == "CUSTOM:${customType.id}",
                    showMenu = true,
                    onClick = { onDateCategoryClick("CUSTOM:${customType.id}") },
                    onMenuClick = {
                        onCustomTypeAction(DateTypeAction.ShowMenu(customType))
                    }
                )
            }
        }
    }
}

/**
 * 我的页快捷导航区域
 */
@Composable
private fun ProfileQuickNavSection(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "🔗 快捷导航",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1C1B1F),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .height(3.dp)
                .fillMaxWidth()
                .background(UiColors.Primary)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            CategoryItem(
                icon = "📊",
                name = "统计",
                count = 0,
                isSelected = false,
                showMenu = false,
                onClick = { /* TODO: 导航到统计页 */ }
            )
            CategoryItem(
                icon = "🏆",
                name = "成就",
                count = 0,
                isSelected = false,
                showMenu = false,
                onClick = { /* TODO: 导航到成就页 */ }
            )
            CategoryItem(
                icon = "⚙️",
                name = "设置",
                count = 0,
                isSelected = false,
                showMenu = false,
                onClick = onSettingsClick
            )
        }
    }
}

// ==================== 弹窗组件 ====================

/**
 * 添加分类/类型对话框
 *
 * @param onConfirmName 仅传名称的确认回调（向后兼容，待办分组使用）
 * @param onConfirm 带名称和 emoji 的确认回调（日期类型使用）
 * @param showEmojiPicker 是否显示 emoji 选择器（默认 false，待办分组不显示）
 * @param title 对话框标题
 * @param label 输入框标签
 */
@Composable
fun AddCategoryDialog(
    onConfirmName: (String) -> Unit = {},
    onConfirm: (String, String) -> Unit = { name, _ -> onConfirmName(name) },
    onDismiss: () -> Unit,
    title: String = "新建分组",
    label: String = "分组名称",
    showEmojiPicker: Boolean = false
) {
    var name by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("📅") }

    val presetEmojis = listOf("📅", "🎂", "💕", "🎉", "🌱", "📚", "💼", "🎮", "✈️", "🐹", "🏠", "⭐")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(label) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UiColors.Primary,
                        focusedLabelColor = UiColors.Primary,
                        cursorColor = UiColors.Primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (showEmojiPicker) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "选择图标",
                        fontSize = 14.sp,
                        color = Color(0xFF79747E),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    // Emoji 选择网格
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(presetEmojis) { emoji ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (emoji == selectedEmoji) UiColors.PrimaryLight
                                        else Color(0xFFF5F5F5)
                                    )
                                    .clickable { selectedEmoji = emoji },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        if (showEmojiPicker) {
                            onConfirm(name.trim(), selectedEmoji)
                        } else {
                            onConfirmName(name.trim())
                        }
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiColors.Primary,
                    disabledContainerColor = UiColors.Primary.copy(alpha = 0.4f)
                )
            ) {
                Text("确定", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF79747E))
            }
        }
    )
}

@Composable
fun RenameCategoryDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "重命名分组",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分组名称") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UiColors.Primary,
                    focusedLabelColor = UiColors.Primary,
                    cursorColor = UiColors.Primary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = UiColors.Primary,
                    disabledContainerColor = UiColors.Primary.copy(alpha = 0.4f)
                )
            ) {
                Text("确定", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF79747E))
            }
        }
    )
}

/**
 * 删除分类/类型确认对话框
 *
 * @param categoryName 分类/类型名称（用于文案插值）
 * @param onConfirm 确认回调
 * @param onDismiss 取消回调
 * @param title 对话框标题（默认"删除分组"，日期类型调用时传"删除类型"）
 * @param message 正文提示（默认使用待办分组文案，日期类型调用时传日期专属文案）
 */
@Composable
fun DeleteCategoryConfirmDialog(
    categoryName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    title: String = "删除分组",
    message: String = "确定要删除分组「$categoryName」吗？\n该分组下的待办将变为未分类状态。"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                color = Color(0xFF1C1B1F)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = Color(0xFF79747E))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryOperationSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    category: Category,
    onPin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = category.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1B1F),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            OperationOption(
                emoji = "📌",
                text = "置顶分组",
                onClick = {
                    onPin()
                    onDismiss()
                }
            )

            OperationOption(
                emoji = "✏️",
                text = "编辑分组",
                onClick = {
                    onRename()
                    onDismiss()
                }
            )

            OperationOption(
                emoji = "🗑️",
                text = "删除分组",
                textColor = MaterialTheme.colorScheme.error,
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OperationOption(
    emoji: String,
    text: String,
    textColor: Color = Color(0xFF1C1B1F),
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 16.sp,
            color = textColor
        )
    }
}

/**
 * 分类操作动作密封类
 */
sealed class CategoryAction {
    data class ShowMenu(val category: Category) : CategoryAction()
    data class Pin(val category: Category) : CategoryAction()
    data class Rename(val category: Category) : CategoryAction()
    data class Delete(val category: Category) : CategoryAction()
}

/**
 * 日期类型操作动作密封类
 */
sealed class DateTypeAction {
    data class ShowMenu(val customType: CustomDateType) : DateTypeAction()
    data class Rename(val customType: CustomDateType) : DateTypeAction()
    data class Delete(val customType: CustomDateType) : DateTypeAction()
}

/**
 * 自定义日期类型操作菜单（BottomSheet）
 * 仅含编辑/删除两项，不含置顶
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTypeOperationSheet(
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    customType: CustomDateType,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = customType.emoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = customType.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1B1F),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            OperationOption(
                emoji = "✏️",
                text = "编辑类型",
                onClick = {
                    onRename()
                    onDismiss()
                }
            )

            OperationOption(
                emoji = "🗑️",
                text = "删除类型",
                textColor = MaterialTheme.colorScheme.error,
                onClick = {
                    onDelete()
                    onDismiss()
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}