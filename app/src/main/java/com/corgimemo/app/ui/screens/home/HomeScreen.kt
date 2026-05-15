package com.corgimemo.app.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.BehaviorType
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.GreetingManager
import com.corgimemo.app.animation.InteractiveCorgi
import com.corgimemo.app.animation.LevelManager
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.ui.components.CorgiNamerDialog
import com.corgimemo.app.ui.components.EmptyState
import com.corgimemo.app.ui.components.TodoCreateBottomSheet
import com.corgimemo.app.ui.components.TodoListItem
import com.corgimemo.app.viewmodel.CelebrationLevel
import com.corgimemo.app.viewmodel.HomeViewModel
import com.corgimemo.app.viewmodel.TodoEditViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    todoEditViewModel: TodoEditViewModel = hiltViewModel()
) {
    val todos by viewModel.todos.collectAsState()
    val filterStatus by viewModel.filterStatus.collectAsState()
    val corgiData by viewModel.corgiData.collectAsState()
    val showNamerDialog by viewModel.showNamerDialog.collectAsState()
    val currentPose by viewModel.currentPose.collectAsState()
    val currentMood by viewModel.currentMood.collectAsState()
    val currentOutfit by viewModel.currentOutfit.collectAsState()
    val celebrationState by viewModel.celebrationState.collectAsState()
    val showLevelUp by viewModel.showLevelUp.collectAsState()
    val showAchievementUnlock by viewModel.showAchievementUnlock.collectAsState()
    val showConsecutiveBonus by viewModel.showConsecutiveBonus.collectAsState()
    val currentBehavior by viewModel.currentBehavior.collectAsState()
    val showMissedYouDialog by viewModel.showMissedYouDialog.collectAsState()
    val missedYouDays by viewModel.missedYouDays.collectAsState()
    val showOutfitSheet by viewModel.showOutfitSheet.collectAsState()
    val moodChangeMessage by viewModel.moodChangeMessage.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    moodChangeMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearMoodChangeMessage()
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isSheetVisible by remember { mutableStateOf(false) }
    val outfitSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = "待办事项", color = MaterialTheme.colorScheme.onSurface)
                    },
                    actions = {
                        IconButton(
                            onClick = { navController.navigate("profile") }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "个人中心",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = {
                        viewModel.onUserInteraction()
                        todoEditViewModel.setTitle("")
                        todoEditViewModel.setContent("")
                        todoEditViewModel.setPriority(1)
                        todoEditViewModel.setDueDate(null)

                        viewModel.setPoseForCreating()
                        isSheetVisible = true

                        coroutineScope.launch {
                            sheetState.show()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Todo")
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    corgiData?.let { data ->
                        CorgiInteractionCard(
                            corgiData = data,
                            currentPose = currentPose,
                            currentMood = currentMood,
                            currentOutfit = currentOutfit,
                            onLongClick = { viewModel.toggleOutfitSheet() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilterButton(
                            text = "全部",
                            isSelected = filterStatus == HomeViewModel.FilterStatus.ALL,
                            onClick = {
                                viewModel.onUserInteraction()
                                viewModel.setFilterStatus(HomeViewModel.FilterStatus.ALL)
                            }
                        )
                        FilterButton(
                            text = "待办",
                            isSelected = filterStatus == HomeViewModel.FilterStatus.PENDING,
                            onClick = {
                                viewModel.onUserInteraction()
                                viewModel.setFilterStatus(HomeViewModel.FilterStatus.PENDING)
                            }
                        )
                        FilterButton(
                            text = "已完成",
                            isSelected = filterStatus == HomeViewModel.FilterStatus.COMPLETED,
                            onClick = {
                                viewModel.onUserInteraction()
                                viewModel.setFilterStatus(HomeViewModel.FilterStatus.COMPLETED)
                            }
                        )
                    }

                    if (todos.isEmpty()) {
                        EmptyState()
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(todos, key = { it.id }) { todo ->
                                TodoListItem(
                                    todo = todo,
                                    onToggleComplete = { id, isChecked ->
                                        viewModel.onUserInteraction()
                                        viewModel.toggleTodoStatus(id, isChecked)
                                    },
                                    onDelete = {
                                        viewModel.onUserInteraction()
                                        viewModel.deleteTodo(it)
                                    },
                                    onClick = {
                                        viewModel.onUserInteraction()
                                        navController.navigate("todo_edit/${todo.id}")
                                    }
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = celebrationState.isShowing,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    CelebrationOverlay(
                        level = celebrationState.level,
                        message = celebrationState.message
                    )
                }
            }

            if (showNamerDialog) {
                CorgiNamerDialog(
                    onConfirm = { name -> viewModel.saveCorgiName(name) },
                    onDismiss = { viewModel.dismissNamerDialog() }
                )
            }

            showLevelUp?.let { level ->
                LevelUpDialog(
                    level = level,
                    onDismiss = { viewModel.dismissLevelUp() }
                )
            }

            showAchievementUnlock?.let { achievement ->
                AchievementUnlockDialog(
                    achievement = achievement,
                    onDismiss = { viewModel.dismissAchievementUnlock() }
                )
            }

            if (showConsecutiveBonus) {
                ConsecutiveBonusDialog(
                    onDismiss = { viewModel.dismissConsecutiveBonus() }
                )
            }

            if (showMissedYouDialog) {
                MissedYouDialog(
                    daysAway = missedYouDays,
                    onDismiss = { viewModel.dismissMissedYouDialog() }
                )
            }
        }

        AnimatedVisibility(
            visible = celebrationState.isShowing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            GlowOverlay(level = celebrationState.level)
        }

        AnimatedVisibility(
            visible = currentBehavior == BehaviorType.YAWNING,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            YawnOverlay()
        }
    }

    if (isSheetVisible) {
        TodoCreateBottomSheet(
            sheetState = sheetState,
            viewModel = todoEditViewModel,
            onSave = {
                coroutineScope.launch {
                    viewModel.onTaskCreated()
                    sheetState.hide()
                    isSheetVisible = false
                    delay(100)
                    viewModel.resetPoseToDefault()
                }
            },
            onDismiss = {
                coroutineScope.launch {
                    if (sheetState.isVisible) {
                        sheetState.hide()
                    }
                    isSheetVisible = false
                    delay(100)
                    viewModel.resetPoseToDefault()
                }
            }
        )
    }

    LaunchedEffect(showOutfitSheet) {
        if (showOutfitSheet) {
            outfitSheetState.show()
        } else if (outfitSheetState.isVisible) {
            outfitSheetState.hide()
        }
    }

    if (showOutfitSheet) {
        OutfitQuickSwitchSheet(
            sheetState = outfitSheetState,
            currentOutfitId = currentOutfit,
            unlockedOutfitsJson = corgiData?.unlockedOutfits ?: "[]",
            onSelect = { outfitId ->
                viewModel.quickSwitchOutfit(outfitId)
            },
            onDismiss = {
                viewModel.hideOutfitSheet()
            }
        )
    }
}

/**
 * 柯基互动卡片
 * 整合柯基展示、等级信息、经验进度、情绪状态、问候语
 *
 * @param corgiData 柯基数据
 * @param currentPose 当前姿态
 * @param currentMood 当前情绪
 * @param currentOutfit 当前装扮 ID
 * @param onLongClick 柯基长按回调（快速换装）
 * @param modifier 修饰符
 */
@Composable
fun CorgiInteractionCard(
    corgiData: CorgiData,
    currentPose: com.corgimemo.app.animation.CorgiPose,
    currentMood: CorgiMood,
    currentOutfit: String?,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val levelStage = LevelManager.getLevelStage(corgiData.level)
    val (_, progress) = LevelManager.getCurrentLevelAndProgress(corgiData.experience)
    val progressText = LevelManager.getProgressText(corgiData.experience)
    val greeting = GreetingManager.getGreeting(currentMood, corgiData.name)

    Card(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            CorgiDisplayArea(
                corgiData = corgiData,
                currentPose = currentPose,
                currentMood = currentMood,
                currentOutfit = currentOutfit,
                onLongClick = onLongClick
            )

            CorgiInfoArea(
                corgiData = corgiData,
                levelStage = levelStage,
                currentMood = currentMood,
                progress = progress,
                progressText = progressText,
                greeting = greeting
            )
        }
    }
}

/**
 * 柯基展示区域
 * 包含柯基动画和背景
 *
 * @param corgiData 柯基数据
 * @param currentPose 当前姿态
 * @param currentMood 当前情绪
 * @param currentOutfit 当前装扮 ID
 * @param onLongClick 长按回调（快速换装入口）
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CorgiDisplayArea(
    corgiData: CorgiData,
    currentPose: com.corgimemo.app.animation.CorgiPose,
    currentMood: CorgiMood,
    currentOutfit: String?,
    onLongClick: () -> Unit = {}
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFF9A5C),
            Color(0xFFFFB366)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(gradient)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val sizeScale = when {
            corgiData.level >= 10 -> 1.0f
            corgiData.level >= 7 -> 1.0f
            corgiData.level >= 4 -> 0.9f
            else -> 0.8f
        }
        val baseSize = 140.dp
        val corgiSize = (baseSize.value * sizeScale).dp

        if (corgiData.level >= 10) {
            Box(
                modifier = Modifier
                    .size(corgiSize + 40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFFFFD700).copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Box(
            contentAlignment = Alignment.Center
        ) {
            OutfitDisplay(
                outfitId = currentOutfit,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            InteractiveCorgi(
                pose = currentPose,
                mood = currentMood,
                corgiName = corgiData.name,
                level = corgiData.level,
                outfitId = currentOutfit,
                modifier = Modifier.padding(horizontal = 0.dp, vertical = 0.dp)
            )
        }

        com.corgimemo.app.ui.components.MoodIndicator(
            moodValue = corgiData.moodValue,
            size = 64,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 8.dp)
        )
    }
}

/**
 * 装扮显示组件
 * 在柯基顶部显示装扮图标
 *
 * @param outfitId 装扮 ID
 * @param modifier 修饰符
 */
@Composable
fun OutfitDisplay(
    outfitId: String?,
    modifier: Modifier = Modifier
) {
    if (outfitId == null || outfitId == com.corgimemo.app.animation.OutfitId.DEFAULT) return

    val icon = when (outfitId) {
        com.corgimemo.app.animation.OutfitId.SCHOLAR_HAT -> "🎓"
        com.corgimemo.app.animation.OutfitId.TIE -> "👔"
        com.corgimemo.app.animation.OutfitId.CROWN -> "👑"
        com.corgimemo.app.animation.OutfitId.ANGEL_WINGS -> "🪽"
        com.corgimemo.app.animation.OutfitId.CAPE -> "🧥"
        else -> null
    }

    icon?.let {
        Text(
            text = it,
            fontSize = 36.sp,
            modifier = modifier.padding(bottom = 60.dp)
        )
    }
}

/**
 * 柯基信息区域
 * 显示等级、阶段、情绪、经验进度、问候语
 *
 * @param corgiData 柯基数据
 * @param levelStage 等级阶段
 * @param currentMood 当前情绪
 * @param progress 经验进度 (0.0-1.0)
 * @param progressText 进度文本 (如 "25/100")
 * @param greeting 问候语
 */
@Composable
fun CorgiInfoArea(
    corgiData: CorgiData,
    levelStage: com.corgimemo.app.animation.LevelStage,
    currentMood: CorgiMood,
    progress: Float,
    progressText: String,
    greeting: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = corgiData.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = levelStage.displayName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "Lv.${corgiData.level}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                MoodBadge(mood = currentMood)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "经验值",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = progressText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        Text(
            text = greeting,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )
    }
}

/**
 * 情绪徽章
 * 显示当前情绪状态
 *
 * @param mood 情绪状态
 */
@Composable
fun MoodBadge(mood: CorgiMood) {
    val emoji = when (mood) {
        CorgiMood.EXCITED -> "🎉"
        CorgiMood.HAPPY -> "😊"
        CorgiMood.NORMAL -> "🐾"
        CorgiMood.EXPECTING -> "🤔"
        CorgiMood.WORRIED -> "😟"
        CorgiMood.SLEEPY -> "💤"
        CorgiMood.SAD -> "🥺"
    }
    val description = when (mood) {
        CorgiMood.EXCITED -> "兴奋"
        CorgiMood.HAPPY -> "开心"
        CorgiMood.NORMAL -> "普通"
        CorgiMood.EXPECTING -> "期待"
        CorgiMood.WORRIED -> "担心"
        CorgiMood.SLEEPY -> "困倦"
        CorgiMood.SAD -> "失落"
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(text = emoji, fontSize = 14.sp)
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/**
 * 柯基等级卡片
 * 显示当前等级、等级阶段和经验值进度
 *
 * @param level 当前等级
 * @param experience 总经验值
 * @param modifier 修饰符
 */
@Composable
fun CorgiLevelCard(
    level: Int,
    experience: Int,
    modifier: Modifier = Modifier
) {
    val levelStage = LevelManager.getLevelStage(level)
    val (_, progress) = LevelManager.getCurrentLevelAndProgress(experience)
    val progressText = LevelManager.getProgressText(experience)

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Lv.$level",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = levelStage.displayName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = progressText,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/**
 * 升级弹窗
 *
 * @param level 升级到的等级
 * @param onDismiss 关闭回调
 */
@Composable
fun LevelUpDialog(
    level: Int,
    onDismiss: () -> Unit
) {
    val levelStage = LevelManager.getLevelStage(level)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉",
                    fontSize = 48.sp
                )
                Text(
                    text = "升级啦！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "Lv.$level",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "成为了 ${levelStage.displayName}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = levelStage.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "太棒了！")
                }
            }
        }
    }
}

/**
 * 成就解锁弹窗
 *
 * @param achievement 解锁的成就
 * @param onDismiss 关闭回调
 */
@Composable
fun AchievementUnlockDialog(
    achievement: com.corgimemo.app.animation.Achievement,
    onDismiss: () -> Unit
) {
    val icon = com.corgimemo.app.animation.AchievementManager.getAchievementIcon(achievement.id)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = icon,
                    fontSize = 64.sp
                )
                Text(
                    text = "成就解锁！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = achievement.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = achievement.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                achievement.outfitId?.let { outfitId ->
                    val outfit = com.corgimemo.app.animation.OutfitManager.getOutfitById(outfitId)
                    outfit?.let {
                        val outfitIcon = when (it.id) {
                            com.corgimemo.app.animation.OutfitId.SCHOLAR_HAT -> "🎓"
                            com.corgimemo.app.animation.OutfitId.TIE -> "👔"
                            com.corgimemo.app.animation.OutfitId.CROWN -> "👑"
                            com.corgimemo.app.animation.OutfitId.ANGEL_WINGS -> "🪽"
                            com.corgimemo.app.animation.OutfitId.CAPE -> "🧥"
                            else -> "🎁"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(
                                text = outfitIcon,
                                fontSize = 20.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = "解锁装扮：${it.name}",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "开心！")
                }
            }
        }
    }
}

/**
 * 连续 7 天奖励弹窗
 *
 * @param onDismiss 关闭回调
 */
@Composable
fun ConsecutiveBonusDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🔥",
                    fontSize = 48.sp
                )
                Text(
                    text = "连续 7 天！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "获得额外 +50 经验值",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "继续加油！")
                }
            }
        }
    }
}

/**
 * 庆祝动画覆盖层
 * 根据任务优先级显示不同的庆祝效果
 *
 * @param level 庆祝级别（低、中、高）
 * @param message 鼓励语
 */
@Composable
fun CelebrationOverlay(level: CelebrationLevel, message: String) {
    val (bgAlpha, emoji, fontSize) = when (level) {
        CelebrationLevel.LOW -> Triple(0.25f, "😊", 28.sp)
        CelebrationLevel.MEDIUM -> Triple(0.35f, "⭐", 30.sp)
        CelebrationLevel.HIGH -> Triple(0.5f, "🎉", 32.sp)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "$emoji $message",
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            if (level == CelebrationLevel.HIGH) {
                Text(
                    text = "经验值 +10",
                    fontSize = 18.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (level == CelebrationLevel.MEDIUM) {
                Text(
                    text = "继续加油！",
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/**
 * 过滤器按钮
 *
 * @param text 按钮文本
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
fun FilterButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Text(text = text)
    }
}

/**
 * 边缘光晕效果组件
 * 根据庆祝级别显示不同的边缘渐变光效
 *
 * @param level 庆祝级别（LOW/MEDIUM/HIGH）
 */
@Composable
fun GlowOverlay(level: CelebrationLevel) {
    // 低优先级任务不显示光晕
    if (level == CelebrationLevel.LOW) return

    // 根据级别获取配置参数：(光晕宽度, 透明度)
    val (glowWidth, alpha) = when (level) {
        CelebrationLevel.LOW -> Pair(0.dp, 0f)  // 不会执行到这里
        CelebrationLevel.MEDIUM -> Pair(60.dp, 0.20f)
        CelebrationLevel.HIGH -> Pair(80.dp, 0.35f)
    }

    // 中优先级使用暖橙色，高优先级使用彩虹渐变
    if (level == CelebrationLevel.MEDIUM) {
        // 中优先级：暖橙色光晕
        MediumGlowOverlay(width = glowWidth, alpha = alpha)
    } else {
        // 高优先级：彩虹渐变光晕
        HighGlowOverlay(width = glowWidth, alpha = alpha)
    }
}

/**
 * 中优先级光晕效果
 * 暖橙色边缘渐变光效
 *
 * @param width 光晕宽度
 * @param alpha 透明度
 */
@Composable
private fun MediumGlowOverlay(width: androidx.compose.ui.unit.Dp, alpha: Float) {
    val glowColor = Color(0xFFFF9A5C)  // 暖橙色

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = alpha * 0.7f),
                        Color.Transparent,
                        Color.Transparent,
                        glowColor.copy(alpha = alpha * 0.7f)
                    ),
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        // 左侧边缘光晕
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            glowColor.copy(alpha = alpha),
                            Color.Transparent
                        )
                    )
                )
        )

        // 右侧边缘光晕
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            glowColor.copy(alpha = alpha)
                        )
                    )
                )
        )

        // 顶部边缘光晕
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            glowColor.copy(alpha = alpha),
                            Color.Transparent
                        )
                    )
                )
        )

        // 底部边缘光晕
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            glowColor.copy(alpha = alpha)
                        )
                    )
                )
        )
    }
}

/**
 * 高优先级光晕效果
 * 彩虹渐变边缘光效（红→橙→黄→绿→蓝→紫）
 *
 * @param width 光晕宽度
 * @param alpha 透明度
 */
@Composable
private fun HighGlowOverlay(width: androidx.compose.ui.unit.Dp, alpha: Float) {
    // 彩虹颜色：红→橙→黄→绿→蓝→紫
    val rainbowColors = listOf(
        Color(0xFFFF6B6B).copy(alpha = alpha),  // 红
        Color(0xFFFFB347).copy(alpha = alpha),  // 橙
        Color(0xFFFFEB3B).copy(alpha = alpha),  // 黄
        Color(0xFF4CAF50).copy(alpha = alpha),  // 绿
        Color(0xFF2196F3).copy(alpha = alpha),  // 蓝
        Color(0xFF9C27B0).copy(alpha = alpha),  // 紫
    )

    // 顶部到底部的彩虹渐变（垂直方向）
    val verticalRainbow = listOf(
        Color(0xFFFF6B6B).copy(alpha = alpha * 0.5f),  // 顶部红色
        Color.Transparent,
        Color.Transparent,
        Color(0xFF9C27B0).copy(alpha = alpha * 0.5f),  // 底部紫色
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = verticalRainbow,
                    startY = 0f,
                    endY = Float.POSITIVE_INFINITY
                )
            )
    ) {
        // 左侧边缘光晕：使用彩虹渐变（从上到下）
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFF6B6B).copy(alpha = alpha),  // 红色（外侧）
                            Color.Transparent
                        )
                    )
                )
        )

        // 右侧边缘光晕：使用彩虹渐变
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(width)
                .align(Alignment.CenterEnd)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF9C27B0).copy(alpha = alpha),  // 紫色（外侧）
                        )
                    )
                )
        )

        // 顶部边缘光晕：红色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF6B6B).copy(alpha = alpha),  // 红色（顶部）
                            Color.Transparent
                        )
                    )
                )
        )

        // 底部边缘光晕：紫色
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(width)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF9C27B0).copy(alpha = alpha),  // 紫色（底部）
                        )
                    )
                )
        )
    }
}

/**
 * 打哈欠覆盖层组件
 * 显示 "Zzz 💤" 动画效果
 * TODO: 打哈欠帧动画暂未就绪，后续添加更丰富的动画效果
 */
@Composable
fun YawnOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.padding(top = 100.dp)
        ) {
            Text(
                text = "Zzz 💤",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF6B7280).copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * 被忽略想念弹窗
 * 3天未打开APP时显示欢迎回来的提示
 *
 * @param daysAway 离开的天数
 * @param onDismiss 关闭回调
 */
@Composable
fun MissedYouDialog(
    daysAway: Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🥺",
                    fontSize = 48.sp
                )
                Text(
                    text = "柯基想你了！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "终于回来啦",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "已经 $daysAway 天没见到你了...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "柯基一直都在等你哦~",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "我也想你！")
                }
            }
        }
    }
}

/**
 * 快速换装底部弹窗
 * 长按首页柯基时显示，提供已解锁装扮的快速切换
 *
 * @param sheetState 底部弹窗状态
 * @param currentOutfitId 当前装扮 ID
 * @param unlockedOutfitsJson 已解锁装扮 JSON
 * @param onSelect 选择装扮回调
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitQuickSwitchSheet(
    sheetState: SheetState,
    currentOutfitId: String?,
    unlockedOutfitsJson: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "快速换装 🎨",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "长按柯基试试这个功能~",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            val unlockedOutfits = com.corgimemo.app.animation.OutfitManager.getOutfitsWithStatus(unlockedOutfitsJson)
                .filter { it.second }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
            ) {
                items(unlockedOutfits) { (outfit, _) ->
                    val isSelected = currentOutfitId == outfit.id ||
                            (outfit.isDefault && currentOutfitId == null)
                    QuickOutfitCard(
                        outfit = outfit,
                        isSelected = isSelected,
                        onClick = { onSelect(outfit.id) }
                    )
                }
            }
        }
    }
}

/**
 * 快速换装卡片
 * 与 ProfileScreen 的 OutfitCard 类似，但简化为只显示已解锁装扮
 *
 * @param outfit 装扮
 * @param isSelected 是否当前选中
 * @param onClick 点击回调
 */
@Composable
fun QuickOutfitCard(
    outfit: com.corgimemo.app.animation.Outfit,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier
            .width(100.dp)
            .height(120.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = when (outfit.id) {
                    com.corgimemo.app.animation.OutfitId.DEFAULT -> "🐕"
                    com.corgimemo.app.animation.OutfitId.SCHOLAR_HAT -> "🎓"
                    com.corgimemo.app.animation.OutfitId.TIE -> "👔"
                    com.corgimemo.app.animation.OutfitId.CROWN -> "👑"
                    com.corgimemo.app.animation.OutfitId.ANGEL_WINGS -> "🪽"
                    com.corgimemo.app.animation.OutfitId.CAPE -> "🧥"
                    else -> "🐕"
                },
                fontSize = 32.sp
            )
            Text(
                text = outfit.name,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            if (isSelected) {
                Text(
                    text = "✓ 当前",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
