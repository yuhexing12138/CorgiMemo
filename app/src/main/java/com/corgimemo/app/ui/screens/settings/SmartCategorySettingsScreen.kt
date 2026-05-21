package com.corgimemo.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryKeyword
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.ui.components.CategorySelector
import com.corgimemo.app.viewmodel.SmartCategorySettingsViewModel

private val DefaultCategoryTypes = listOf(
    CategoryType.STUDY,
    CategoryType.WORK,
    CategoryType.LIFE,
    CategoryType.SPORT
)

/**
 * 获取分类图标
 */
fun getCategoryIcon(categoryType: Int): String {
    return when (categoryType) {
        CategoryType.STUDY -> "\uD83D\uDCDA"
        CategoryType.WORK -> "\uD83D\uDCBC"
        CategoryType.LIFE -> "\uD83C\uDFE0"
        CategoryType.SPORT -> "\uD83C\uDFC3"
        else -> "\uD83D\uDCCB"
    }
}

/**
 * 获取分类名称
 */
fun getCategoryName(categoryType: Int, categories: List<Category>): String {
    return categories.find { it.type == categoryType }?.name ?: when (categoryType) {
        CategoryType.STUDY -> "学习"
        CategoryType.WORK -> "工作"
        CategoryType.LIFE -> "生活"
        CategoryType.SPORT -> "运动"
        else -> "其他"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartCategorySettingsScreen(
    navController: NavHostController,
    viewModel: SmartCategorySettingsViewModel = hiltViewModel()
) {
    val keywordsByCategory by viewModel.keywordsByCategory.collectAsState()
    val categories by viewModel.categories.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能分类设置") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "\uD83D\uDCA1 什么是智能分类？",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "系统会根据待办内容自动推荐分类。你可以添加自定义关键词提高推荐准确率。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("添加自定义关键词")
                }
            }

            DefaultCategoryTypes.forEach { type ->
                val keywords = keywordsByCategory[type] ?: emptyList()
                val presetKeywords = keywords.filter { !it.isUserDefined }
                val userKeywords = keywords.filter { it.isUserDefined }

                item(key = "header_$type") {
                    ExpandableCategoryCard(
                        categoryType = type,
                        categories = categories,
                        presetKeywords = presetKeywords,
                        userKeywords = userKeywords,
                        onDeleteKeyword = { keywordId ->
                            viewModel.deleteKeyword(keywordId)
                        },
                        onEditKeyword = { keywordId, newKeyword ->
                            viewModel.updateKeywordText(keywordId, newKeyword)
                        },
                        onMoveKeyword = { keywordId, newCategoryType ->
                            viewModel.updateKeywordCategory(keywordId, newCategoryType)
                        },
                        categoriesList = categories,
                        allKeywords = viewModel.getAllKeywords()
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddKeywordDialog(
            categories = categories,
            allKeywords = viewModel.getAllKeywords(),
            onDismiss = { showAddDialog = false },
            onConfirm = { keyword, categoryType ->
                viewModel.addKeyword(keyword, categoryType)
                showAddDialog = false
            }
        )
    }
}

/**
 * 可折叠的分类卡片组件
 * 将预设关键词和用户关键词分开显示
 *
 * @param categoryType 分类类型
 * @param categories 分类列表
 * @param presetKeywords 预设关键词列表
 * @param userKeywords 用户添加的关键词列表
 * @param onDeleteKeyword 删除关键词回调
 * @param onEditKeyword 编辑关键词文字回调
 * @param onMoveKeyword 移动关键词到其他分类回调
 * @param categoriesList 用于选择分类的完整列表
 * @param allKeywords 所有关键词列表（用于重复检查）
 */
@Composable
private fun ExpandableCategoryCard(
    categoryType: Int,
    categories: List<Category>,
    presetKeywords: List<CategoryKeyword>,
    userKeywords: List<CategoryKeyword>,
    onDeleteKeyword: (Long) -> Unit,
    onEditKeyword: (Long, String) -> Unit,
    onMoveKeyword: (Long, Int) -> Unit,
    categoriesList: List<Category>,
    allKeywords: List<CategoryKeyword>
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isPresetExpanded by remember { mutableStateOf(true) }
    var isUserExpanded by remember { mutableStateOf(true) }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "arrowRotation"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 分类标题栏（可点击展开/收起）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = getCategoryIcon(categoryType),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(end = 8.dp)
                )
                
                Text(
                    text = getCategoryName(categoryType, categories) + "类",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )

                // 显示数量统计
                Text(
                    text = "预设 ${presetKeywords.size} | 自定义 ${userKeywords.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "收起" else "展开",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 展开内容区域
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    // 预设关键词区域（可折叠）
                    if (presetKeywords.isNotEmpty()) {
                        ExpandableSection(
                            title = "\uD83D\uDCCC 预设关键词 (${presetKeywords.size})",
                            isExpanded = isPresetExpanded,
                            onToggleExpand = { isPresetExpanded = !isPresetExpanded }
                        ) {
                            presetKeywords.forEach { keyword ->
                                KeywordItem(
                                    keyword = keyword,
                                    categories = categoriesList,
                                    allKeywords = allKeywords,
                                    onDelete = { onDeleteKeyword(keyword.id) },
                                    onEditKeyword = { id, newKeyword -> onEditKeyword(id, newKeyword) },
                                    onMoveKeyword = { id, newCategoryType -> onMoveKeyword(id, newCategoryType) }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // 用户自定义关键词区域（可折叠）
                    if (userKeywords.isNotEmpty()) {
                        ExpandableSection(
                            title = "\u270F\uFE0F 自定义关键词 (${userKeywords.size})",
                            isExpanded = isUserExpanded,
                            onToggleExpand = { isUserExpanded = !isUserExpanded }
                        ) {
                            userKeywords.forEach { keyword ->
                                KeywordItem(
                                    keyword = keyword,
                                    categories = categoriesList,
                                    allKeywords = allKeywords,
                                    onDelete = { onDeleteKeyword(keyword.id) },
                                    onEditKeyword = { id, newKeyword -> onEditKeyword(id, newKeyword) },
                                    onMoveKeyword = { id, newCategoryType -> onMoveKeyword(id, newCategoryType) }
                                )
                            }
                        }
                    } else if (presetKeywords.isEmpty()) {
                        // 没有任何关键词时显示提示
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无关键词，点击上方按钮添加",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 可折叠的子区域组件
 *
 * @param title 区域标题
 * @param isExpanded 是否展开
 * @param onToggleExpand 切换展开状态回调
 * @param content 展开后显示的内容
 */
@Composable
private fun ExpandableSection(
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "sectionArrowRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 区域标题行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(bottom = if (isExpanded) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "收起" else "展开",
                modifier = Modifier
                    .size(18.dp)
                    .rotate(rotationAngle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 可展开的内容（支持滚动，最大高度300dp）
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        }
    }
}

/**
 * 关键词条目组件
 *
 * @param keyword 关键词数据
 * @param categories 分类列表
 * @param allKeywords 所有关键词列表（用于重复检查）
 * @param onDelete 删除回调
 * @param onEditKeyword 编辑关键词文字回调
 * @param onMoveKeyword 移动关键词到其他分类回调
 */
@Composable
fun KeywordItem(
    keyword: CategoryKeyword,
    categories: List<Category>,
    allKeywords: List<CategoryKeyword>,
    onDelete: () -> Unit,
    onEditKeyword: (Long, String) -> Unit,
    onMoveKeyword: (Long, Int) -> Unit
) {
    var showMoveMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val isPreset = !keyword.isUserDefined

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPreset) {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = keyword.keyword,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isPreset) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    if (isPreset) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "预设",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "自定义",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = if (keyword.matchType == com.corgimemo.app.data.model.MatchType.EXACT) 
                        "精确匹配" else "模糊匹配",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 自定义关键词的操作按钮
            if (!isPreset) {
                // 编辑按钮：修改关键词文字
                IconButton(onClick = { showEditDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑关键词",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 移动按钮：移动到其他分类
                Box {
                    IconButton(onClick = { showMoveMenu = true }) {
                        Text(
                            text = "移动",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showMoveMenu,
                        onDismissRequest = { showMoveMenu = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { 
                                    val isCurrent = category.type == keyword.categoryType
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("移至 ${category.name}")
                                        if (isCurrent) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "当前",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    if (category.type != keyword.categoryType) {
                                        onMoveKeyword(keyword.id, category.type)
                                    }
                                    showMoveMenu = false
                                },
                                enabled = category.type != keyword.categoryType
                            )
                        }
                    }
                }

                // 删除按钮
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Text(
                    text = "只读",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 编辑关键词对话框
    if (showEditDialog) {
        EditKeywordDialog(
            currentKeyword = keyword.keyword,
            keywordId = keyword.id,
            categories = categories,
            allKeywords = allKeywords,
            onDismiss = { showEditDialog = false },
            onConfirm = { newKeyword ->
                onEditKeyword(keyword.id, newKeyword)
                showEditDialog = false
            }
        )
    }
}

/**
 * 编辑关键词对话框
 *
 * @param currentKeyword 当前关键词文字
 * @param keywordId 当前关键词ID（用于排除自身进行重复检查）
 * @param categories 分类列表（用于显示当前分类）
 * @param allKeywords 所有关键词列表（用于重复检查）
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调，返回新关键词文字
 */
@Composable
private fun EditKeywordDialog(
    currentKeyword: String,
    keywordId: Long,
    categories: List<Category>,
    allKeywords: List<CategoryKeyword>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newKeyword by remember { mutableStateOf(currentKeyword) }

    // 检查关键词是否重复（排除自身）
    val duplicateKeyword = allKeywords.find { 
        it.keyword.equals(newKeyword, ignoreCase = true) && it.id != keywordId 
    }
    val hasDuplicate = newKeyword.isNotBlank() && newKeyword != currentKeyword && duplicateKeyword != null
    val duplicateCategoryName = if (duplicateKeyword != null) {
        categories.find { it.type == duplicateKeyword.categoryType }?.name ?: "未知"
    } else {
        null
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "编辑关键词",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    label = { Text("关键词") },
                    placeholder = { Text("输入新的关键词") },
                    singleLine = true,
                    isError = hasDuplicate,
                    supportingText = if (hasDuplicate) {
                        { Text("该关键词已存在于「$duplicateCategoryName」分类中", color = Color(0xFFDC2626)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 重复警告提示
                AnimatedVisibility(visible = hasDuplicate) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\u26A0\uFE0F",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "关键词重复",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "\"$newKeyword\" 已存在于「$duplicateCategoryName」分类中",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (newKeyword.isNotBlank() && newKeyword != currentKeyword && !hasDuplicate) {
                                onConfirm(newKeyword.trim())
                            } else if (newKeyword.isNotBlank()) {
                                onDismiss()
                            }
                        },
                        enabled = newKeyword.isNotBlank() && !hasDuplicate
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}

/**
 * 添加关键词对话框
 *
 * @param categories 分类列表
 * @param onDismiss 关闭回调
 * @param onConfirm 确认回调
 */
@Composable
fun AddKeywordDialog(
    categories: List<Category>,
    allKeywords: List<CategoryKeyword>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var selectedCategoryType by remember { mutableIntStateOf(CategoryType.STUDY) }

    // 检查关键词是否重复
    val duplicateKeyword = allKeywords.find { 
        it.keyword.equals(keyword, ignoreCase = true) 
    }
    val hasDuplicate = keyword.isNotBlank() && duplicateKeyword != null
    val duplicateCategoryName = if (duplicateKeyword != null) {
        categories.find { it.type == duplicateKeyword.categoryType }?.name ?: "未知"
    } else {
        null
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "添加自定义关键词",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("关键词") },
                    placeholder = { Text("输入关键词，如：加班、健身") },
                    isError = hasDuplicate,
                    supportingText = if (hasDuplicate) {
                        { Text("该关键词已存在于「$duplicateCategoryName」分类中", color = Color(0xFFDC2626)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 重复警告提示
                AnimatedVisibility(visible = hasDuplicate) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "\u26A0\uFE0F",
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "关键词重复",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "\"$keyword\" 已存在于「$duplicateCategoryName」分类中",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "关联分类",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                CategorySelector(
                    categories = categories,
                    selectedCategoryId = categories.find { it.type == selectedCategoryType }?.id ?: 0L,
                    onCategorySelected = { id ->
                        categories.find { it.id == id }?.let { selectedCategoryType = it.type }
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (keyword.isNotBlank() && !hasDuplicate) {
                                onConfirm(keyword.trim(), selectedCategoryType)
                            }
                        },
                        enabled = keyword.isNotBlank() && !hasDuplicate
                    ) {
                        Text("添加")
                    }
                }
            }
        }
    }
}
