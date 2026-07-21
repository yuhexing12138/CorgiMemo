package com.corgimemo.app.ui.screens.corgi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.CorgiPose
import com.corgimemo.app.animation.LevelManager
import com.corgimemo.app.animation.LevelStage
import com.corgimemo.app.animation.OutfitManager
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.components.AppSnackbarHost
import com.corgimemo.app.ui.components.CorgiDesktopPet
import com.corgimemo.app.ui.components.MissedYouDialog
import com.corgimemo.app.ui.components.MoodHistoryChart
import com.corgimemo.app.ui.components.OutfitQuickSwitchSheet
import com.corgimemo.app.ui.screens.profile.components.OutfitEntryCard
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.HomeViewModel
import kotlinx.coroutines.delay

/**
 * 柯基详情页
 *
 * v1.4 简化版（柯基互动页）：
 * - 顶部导航栏（返回按钮 + 柯基名字 + 等级标签）
 * - 下层滚动 Column：柯基卡牌区 / 情绪 / 装扮 / 互动 各 Section
 * - 上层桌宠层（CorgiDesktopPet 覆盖整页，柯基自由活动，事件穿透到下层）
 *
 * v1.4 变更：
 * - 新增柯基卡牌区（从"我的"页 ProfileHeroCard 旧版迁入，含等级、经验、累计/连续/情绪统计）
 * - 删除原 StatsSection（今日数据）和 AchievementSection（成就进度）
 * - 装扮入口从"我的"页 OutfitEntryCard 迁入
 *
 * 桌宠交互：
 * - 拖动：跟随手指移动，松手后保留位置
 * - 单击：抚摸（PETTING 1s）
 * - 双击：喂食（EATING 1.5s）
 * - 长按：弹出菜单（抚摸/喂食/玩耍/睡觉）
 * - 30s 无互动：自动进入 SLEEPING 状态
 *
 * @param navController 导航控制器
 * @param viewModel 首页 ViewModel（复用柯基数据）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorgiDetailScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val corgiData by viewModel.corgiData.collectAsState()
    val currentPose by viewModel.currentPose.collectAsState()
    val currentMood by viewModel.currentMood.collectAsState()
    val currentOutfit by viewModel.currentOutfit.collectAsState()
    val currentHoliday by viewModel.currentHoliday.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    // v1.3 桌宠改造：原 greeting / currentBehavior 状态已移除，
    // 桌宠的自主行为由 CorgiDesktopPet 内部状态机管理
    val showMissedYouDialog by viewModel.showMissedYouDialog.collectAsState()
    val missedYouDays by viewModel.missedYouDays.collectAsState()
    val showOutfitSheet by viewModel.showOutfitSheet.collectAsState()
    // 统一的 Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }

    // 实际显示的装扮：节日装扮优先级高于用户选择的装扮
    val effectiveOutfit = currentHoliday?.outfitId ?: currentOutfit

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = corgiData?.name ?: "柯基",
                            fontWeight = FontWeight.Bold
                        )
                        corgiData?.let { data ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "Lv.${data.level}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        // 统一 Snackbar 容器（替代 Toast）
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) }
    ) { paddingValues ->
        // ===== v1.3 桌宠改造：用 Box 包裹下层内容 + 上层桌宠 =====
        // 下层：Column 滚动展示卡牌区/情绪/装扮/互动各 Section
        // 上层：CorgiDesktopPet 覆盖整页，柯基以桌宠形式自由活动
        //       仅柯基本体(96dp)消耗事件，其他区域事件穿透到下层
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                corgiData?.let { data ->
                    // 顶部预留 12dp 间距
                    Spacer(modifier = Modifier.height(12.dp))

                    // ===== v1.4 新增：柯基卡牌区 =====
                    // 从"我的"页 ProfileHeroCard 旧版迁入（含 Lv 徽章、经验进度条、三栏统计）
                    // 等级/进度通过 LevelManager 实时计算（避免在 HomeViewModel 增加 state）
                    CorgiHeroCard(
                        corgiData = data,
                        onNameClick = { /* 互动页内不响应改名（避免与 ProfileDetail 跳转冲突） */ }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // ===== 5.3 情绪状态区 =====
                    MoodStatusSection(corgiData = data, currentMood = currentMood)

                    Spacer(modifier = Modifier.height(12.dp))

                    // ===== v1.4 迁入：装扮入口卡（原"我的"页 OutfitEntryCard）=====
                    OutfitEntryCard(
                        currentOutfitId = data.currentOutfit,
                        outfitCount = OutfitManager.getOutfitsWithStatus(data.unlockedOutfits).size,
                        onClick = { navController.navigate(Screen.Outfit.route) }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // ===== 5.7 互动按钮区（与桌宠并存：底部按钮可触发互动经验）=====
                    InteractionSection(
                        onPet = {
                            viewModel.addInteractionExperience(3)
                            viewModel.onUserInteraction()
                        },
                        onFeed = {
                            viewModel.addInteractionExperience(5)
                            viewModel.onUserInteraction()
                        },
                        onPlay = {
                            viewModel.addInteractionExperience(8)
                            viewModel.onUserInteraction()
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ===== 桌宠层：覆盖整页，柯基自由活动 =====
            // corgiData 为 null 时不渲染（首次加载占位）
            if (corgiData != null) {
                CorgiDesktopPet(
                    pose = currentPose,
                    mood = currentMood,
                    outfitId = effectiveOutfit,
                    soundEnabled = soundEnabled,
                    hapticEnabled = hapticEnabled,
                    onPet = {
                        viewModel.addInteractionExperience(3)
                        viewModel.onUserInteraction()
                    },
                    onFeed = {
                        viewModel.addInteractionExperience(5)
                        viewModel.onUserInteraction()
                    },
                    onPlay = {
                        viewModel.addInteractionExperience(8)
                        viewModel.onUserInteraction()
                    },
                    onSleep = {
                        viewModel.onUserInteraction()
                    },
                    onShowSnackbar = { msg ->
                        com.corgimemo.app.ui.components.GlobalSnackbarController.showMessage(msg)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // ===== P0 修复：快速换装 BottomSheet（解决"切换装扮"死按钮）=====
    // 复用 `com.corgimemo.app.ui.components.OutfitQuickSwitchSheet`（与 HomeScreen 长按柯基同 UX）
    val outfitSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            // 注意：传 currentOutfit（不叠加节日 effectiveOutfit），
            // 与 HomeScreen L1226 保持一致，避免节日期间高亮错位
            currentOutfitId = currentOutfit,
            unlockedOutfitsJson = corgiData?.unlockedOutfits ?: "[]",
            onSelect = { outfitId -> viewModel.quickSwitchOutfit(outfitId) },
            onDismiss = { viewModel.hideOutfitSheet() }
        )
    }

    // ===== P0 修复：MISSED_YOU Dialog 挂载（HomeViewModel 启动时可能触发）=====
    // 正常流程下 HomeScreen 已 dismiss，这里挂载仅为完整性
    if (showMissedYouDialog) {
        MissedYouDialog(
            daysAway = missedYouDays,
            onDismiss = { viewModel.dismissMissedYouDialog() }
        )
    }

    // v1.3 桌宠改造：原 interactionAnimation 恢复逻辑已移除，
    // 桌宠内部根据 CorgiPetState 自动管理状态时长（PETTING 1s / EATING 1.5s / PLAYING 1.2s）
}

/**
 * 柯基卡牌区（v1.4 从"我的"页 ProfileHeroCard 旧版迁入）
 *
 * 视觉规范：
 * - 主色浅渐变背景（primaryContainer → surface，135°）
 * - 圆角 20dp，elevation 2dp
 * - 柯基头像 72dp 圆形（静态 emoji 🐕）
 * - 名字 20sp Bold + Lv 徽章 10sp Bold
 * - 等级阶段副标题（11sp Medium 主色）
 * - 经验进度条（6dp 高，圆角 3dp）
 * - 底部三栏统计（累计完成 / 连续天数 / 情绪值）
 *
 * 等级 / 进度 / 文案通过 LevelManager 实时计算（不依赖 ProfileViewModel）
 *
 * @param corgiData 柯基数据
 * @param onNameClick 点击名字/头像回调（互动页内暂不响应改名，预留接口）
 */
@Composable
private fun CorgiHeroCard(
    corgiData: CorgiData,
    onNameClick: () -> Unit
) {
    // 等级 / 进度 / 文案实时计算（无 ViewModel state 依赖）
    val (level, levelProgress) = remember(corgiData.experience) {
        LevelManager.getCurrentLevelAndProgress(corgiData.experience)
    }
    val levelStage: LevelStage = remember(level) { LevelManager.getLevelStage(level) }
    val progressText: String = remember(corgiData.experience) {
        LevelManager.getProgressText(corgiData.experience)
    }

    // 渐变背景：primaryContainer → surface（与"我的"页 ProfileHeroCard 视觉锚点一致）
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surface
        )
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(16.dp)
        ) {
            Column {
                // 顶部：柯基头像 + 名字/Lv/经验进度
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 柯基头像 72dp 圆形（静态 emoji）
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(onClick = onNameClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "🐕", fontSize = 32.sp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = corgiData.name,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clickable(onClick = onNameClick)
                            )
                            // Lv 徽章
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Lv.$level",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Text(
                            text = levelStage.displayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        // 经验标尺 + 进度文本
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "经验",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = progressText,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 经验进度条
                        LinearProgressIndicator(
                            progress = { levelProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }

                // 分隔细线（柯基信息 → 统计）
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp)
                        .height(1.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(0.5.dp)
                        )
                )

                // 底部三栏统计：累计完成 / 连续天数 / 情绪值
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    HeroStat(value = "${corgiData.totalCompleted}", label = "累计完成")
                    HeroStat(value = "${corgiData.consecutiveDays}", label = "连续天数")
                    HeroStat(value = "${corgiData.moodValue}%", label = "情绪值")
                }
            }
        }
    }
}

/**
 * 卡牌区底部统计项
 * 上方数值（16sp Bold 主色），下方标签（10sp 次要色）
 *
 * @param value 数值文本
 * @param label 标签文本
 */
@Composable
private fun HeroStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

/**
 * 情绪状态区
 *
 * @param corgiData 柯基数据
 * @param currentMood 当前情绪
 */
@Composable
private fun MoodStatusSection(
    corgiData: CorgiData,
    currentMood: CorgiMood
) {
    val moodValue = corgiData.moodValue.coerceIn(0, 100)
    val moodColor = when {
        moodValue >= 70 -> Color(0xFF10B981)  // 绿色 - 高情绪
        moodValue >= 40 -> Color(0xFFF59E0B)  // 黄色 - 中情绪
        else -> Color(0xFFEF4444)              // 红色 - 低情绪
    }
    val moodDescription = when (currentMood) {
        CorgiMood.EXCITED -> "兴奋"
        CorgiMood.HAPPY -> "开心"
        CorgiMood.NORMAL -> "普通"
        CorgiMood.EXPECTING -> "期待"
        CorgiMood.WORRIED -> "担心"
        CorgiMood.SLEEPY -> "困倦"
        CorgiMood.SAD -> "失落"
    }
    val moodEmoji = when (currentMood) {
        CorgiMood.EXCITED -> "🎉"
        CorgiMood.HAPPY -> "😊"
        CorgiMood.NORMAL -> "🐾"
        CorgiMood.EXPECTING -> "🤔"
        CorgiMood.WORRIED -> "😟"
        CorgiMood.SLEEPY -> "💤"
        CorgiMood.SAD -> "🥺"
    }

    // 是否展开情绪历史
    var isHistoryExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isHistoryExpanded = !isHistoryExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "❤️ 情绪值",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = moodEmoji, fontSize = 16.sp)
                    Text(
                        text = moodDescription,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isHistoryExpanded) "▲" else "▼",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { moodValue / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = moodColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$moodValue%",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )

            // 可展开的情绪历史
            if (isHistoryExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "情绪历史",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                // 简单的情绪历史展示（最近7天）
                MoodHistoryChart(
                    historyList = emptyList(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )
            }
        }
    }
}

/**
 * 互动按钮区
 *
 * @param onPet 抚摸回调
 * @param onFeed 喂食回调
 * @param onPlay 玩耍回调
 */
@Composable
private fun InteractionSection(
    onPet: () -> Unit,
    onFeed: () -> Unit,
    onPlay: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "🎮 互动",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                InteractionButton(emoji = "🤚", label = "抚摸", onClick = onPet)
                InteractionButton(emoji = "🍖", label = "喂食", onClick = onFeed)
                InteractionButton(emoji = "🎾", label = "玩耍", onClick = onPlay)
            }
        }
    }
}

/**
 * 互动按钮组件
 *
 * @param emoji 按钮图标 emoji
 * @param label 按钮文字
 * @param onClick 点击回调
 */
@Composable
private fun InteractionButton(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = 0.5f,
            stiffness = 300f
        ),
        label = "interactionButtonScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable {
            isPressed = true
            onClick()
        }
    ) {
        Surface(
            modifier = Modifier
                .size(56.dp)
                .then(
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                ),
            shape = CircleShape,
            color = UiColors.Primary.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, UiColors.Primary.copy(alpha = 0.3f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(text = emoji, fontSize = 24.sp)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // 恢复缩放
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(200)
            isPressed = false
        }
    }
}
