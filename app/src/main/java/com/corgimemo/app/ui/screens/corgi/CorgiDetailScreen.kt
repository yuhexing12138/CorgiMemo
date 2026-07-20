package com.corgimemo.app.ui.screens.corgi

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.CorgiPose
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.animation.HolidayOutfitId
import com.corgimemo.app.animation.InteractiveCorgi
import com.corgimemo.app.animation.LevelManager
import com.corgimemo.app.animation.OutfitId
import com.corgimemo.app.animation.OutfitManager
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.components.AppSnackbarHost
import com.corgimemo.app.ui.components.MissedYouDialog
import com.corgimemo.app.ui.components.MoodHistoryChart
import com.corgimemo.app.ui.components.OutfitQuickSwitchSheet
import com.corgimemo.app.ui.components.YawnOverlay
import com.corgimemo.app.ui.theme.UiColors
import com.corgimemo.app.viewmodel.HomeViewModel
import kotlinx.coroutines.delay
import org.json.JSONArray

/**
 * 柯基详情页
 *
 * 展示柯基的完整信息和互动功能，包含：
 * - 顶部导航栏（返回按钮 + 柯基名字 + 等级标签）
 * - 柯基动画展示区（200dp）
 * - 情绪状态区（进度条 + 百分比 + 状态文字）
 * - 数据统计区（今日完成数、本周完成数、连续天数）
 * - 成就预览区（已解锁成就徽章网格）
 * - 装扮预览区（当前装扮 + 切换按钮）
 * - 互动按钮区（抚摸/喂食/玩耍）
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
    val greeting by viewModel.greeting.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    // ===== 自主行为状态（P0 接入）=====
    // 5 种行为（YAWNING/SLEEPING_NIGHT/WORRIED/MISSED_YOU/HAPPY_STREAK）的来源，
    // 其中 3 种（SLEEPING_NIGHT/WORRIED/HAPPY_STREAK）已通过 greeting 隐式覆盖，
    // 本页新增：YAWNING 浮层 + MISSED_YOU Dialog。
    val currentBehavior by viewModel.currentBehavior.collectAsState()
    val showMissedYouDialog by viewModel.showMissedYouDialog.collectAsState()
    val missedYouDays by viewModel.missedYouDays.collectAsState()
    val showOutfitSheet by viewModel.showOutfitSheet.collectAsState()
    // 统一的 Snackbar 状态
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // 实际显示的装扮：节日装扮优先级高于用户选择的装扮
    val effectiveOutfit = currentHoliday?.outfitId ?: currentOutfit

    // 互动动画状态
    var interactionAnimation by remember { mutableStateOf<AnimationType?>(null) }

    // 本周完成数
    var weeklyCompleted by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        weeklyCompleted = viewModel.getWeeklyCompletedCount()
    }

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            corgiData?.let { data ->
                // ===== 5.2 柯基动画展示区（200dp）=====
                CorgiAnimationSection(
                    corgiData = data,
                    currentPose = currentPose,
                    currentMood = currentMood,
                    currentOutfit = effectiveOutfit,
                    greeting = greeting,
                    interactionAnimation = interactionAnimation,
                    currentBehavior = currentBehavior,   // P0 接入：用于在 Box 内挂 YawnOverlay
                    onInteraction = { viewModel.onUserInteraction() },
                    soundEnabled = soundEnabled,
                    hapticEnabled = hapticEnabled
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ===== 5.3 情绪状态区 =====
                MoodStatusSection(corgiData = data, currentMood = currentMood)

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 5.4 数据统计区 =====
                StatsSection(
                    corgiData = data,
                    weeklyCompleted = weeklyCompleted,
                    onStatsClick = { navController.navigate(Screen.Stats.route) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 5.5 成就预览区 =====
                AchievementSection(
                    corgiData = data,
                    onAchievementClick = { navController.navigate(Screen.Achievement.route) }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ===== 5.6 装扮预览区 =====
                OutfitSection(
                    currentOutfit = effectiveOutfit,
                    onSwitchOutfit = { viewModel.toggleOutfitSheet() }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // ===== 5.7 互动按钮区 =====
                InteractionSection(
                    onPet = {
                        interactionAnimation = AnimationType.WAG
                        viewModel.addInteractionExperience(3)
                        viewModel.onUserInteraction()
                    },
                    onFeed = {
                        interactionAnimation = AnimationType.PROUD
                        viewModel.addInteractionExperience(5)
                        viewModel.onUserInteraction()
                    },
                    onPlay = {
                        interactionAnimation = AnimationType.RUN
                        viewModel.addInteractionExperience(8)
                        viewModel.onUserInteraction()
                    }
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

    // 互动动画播放后恢复默认（差异化时长：抚摸800ms/喂食1000ms/玩耍1200ms）
    LaunchedEffect(interactionAnimation) {
        if (interactionAnimation != null) {
            val duration = when (interactionAnimation) {
                AnimationType.WAG -> 800L    // 抚摸：眯眼享受+摇尾巴 800ms
                AnimationType.PROUD -> 1000L  // 喂食：张嘴吃东西+满足表情 1000ms
                AnimationType.RUN -> 1200L    // 玩耍：追逐玩具+跳跃 1200ms
                else -> 1000L
            }
            delay(duration)
            interactionAnimation = null
        }
    }
}

/**
 * 柯基动画展示区
 *
 * @param corgiData 柯基数据
 * @param currentPose 当前姿态
 * @param currentMood 当前情绪
 * @param currentOutfit 当前装扮 ID
 * @param greeting 问候语
 * @param interactionAnimation 互动触发的临时动画类型
 * @param onInteraction 触摸互动回调
 * @param soundEnabled 音效开关
 * @param hapticEnabled 触觉反馈开关
 */
@Composable
private fun CorgiAnimationSection(
    corgiData: CorgiData,
    currentPose: CorgiPose,
    currentMood: CorgiMood,
    currentOutfit: String?,
    greeting: String,
    interactionAnimation: AnimationType?,
    onInteraction: () -> Unit,
    soundEnabled: Boolean,
    hapticEnabled: Boolean
) {
    val gradient = Brush.verticalGradient(
        colors = listOf(
            UiColors.Primary,
            UiColors.Primary.copy(alpha = 0.7f)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            // 互动动画或默认柯基动画
            if (interactionAnimation != null) {
                FrameAnimation(
                    animationType = interactionAnimation,
                    fps = 8,
                    isLooping = true,
                    modifier = Modifier.size(140.dp)
                )
            } else {
                InteractiveCorgi(
                    pose = currentPose,
                    mood = currentMood,
                    corgiName = corgiData.name,
                    level = corgiData.level,
                    outfitId = currentOutfit,
                    onInteraction = { onInteraction() },
                    onLongPress = { },
                    soundEnabled = soundEnabled,
                    hapticEnabled = hapticEnabled,
                    // 统一 Snackbar 提示回调（替代 Toast）
                    // 此处不是顶层 Composable，改用全局控制器（避免作用域问题）
                    onShowSnackbar = { msg -> com.corgimemo.app.ui.components.GlobalSnackbarController.showMessage(msg) },
                    modifier = Modifier.size(140.dp)
                )
            }

            // P0：YAWNING 浮层（区分于 SLEEPING_NIGHT，两者都使用 SLEEP 姿态）
            // 仅在空闲打哈欠（10 秒无操作后）显示在 Box 右上角（TopEnd）
            if (currentBehavior == BehaviorType.YAWNING) {
                YawnOverlay(modifier = Modifier.align(Alignment.TopEnd))
            }

            // 问候语气泡
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.9f)
            ) {
                Text(
                    text = greeting,
                    fontSize = 13.sp,
                    color = Color(0xFF333333),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
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
 * 数据统计区
 *
 * @param corgiData 柯基数据
 * @param onStatsClick 点击跳转统计详情页回调
 */
@Composable
private fun StatsSection(
    corgiData: CorgiData,
    weeklyCompleted: Int = 0,
    onStatsClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onStatsClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "📊 今日数据",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(label = "今日完成", value = "${corgiData.totalCompleted}")
                StatItem(label = "本周完成", value = "$weeklyCompleted")
                StatItem(label = "连续天数", value = "🔥${corgiData.consecutiveDays}天")
            }
        }
    }
}

/**
 * 统计项组件
 *
 * @param label 标签文字
 * @param value 数值文字
 */
@Composable
private fun StatItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = UiColors.Primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 成就预览区
 *
 * @param corgiData 柯基数据
 * @param onAchievementClick 点击跳转成就墙回调
 */
@Composable
private fun AchievementSection(
    corgiData: CorgiData,
    onAchievementClick: () -> Unit
) {
    // 解析已解锁成就
    val unlockedIds = try {
        val json = corgiData.unlockedAchievements
        if (json.isNotBlank()) {
            JSONArray(json).let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
        } else {
            emptyList()
        }
    } catch (_: Exception) {
        emptyList()
    }

    val totalAchievements = 8  // 总成就数
    val unlockedCount = unlockedIds.size.coerceAtMost(totalAchievements)

    // 成就图标映射
    val achievementIcons = listOf("🌱", "🔥", "⚡", "👑", "📚", "💼", "🏠", "🏃")

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable { onAchievementClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                Text(
                    text = "🏆 成就进度",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "已解锁 $unlockedCount/$totalAchievements",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 成就徽章网格（2行4列）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        for (col in 0..3) {
                            val index = row * 4 + col
                            val isUnlocked = index < unlockedCount
                            val icon = achievementIcons.getOrElse(index) { "🏆" }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isUnlocked) UiColors.Primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = icon,
                                    fontSize = 18.sp,
                                    color = if (isUnlocked) Color.Unspecified
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 装扮预览区
 *
 * @param currentOutfit 当前装扮 ID
 * @param onSwitchOutfit 切换装扮回调
 */
@Composable
private fun OutfitSection(
    currentOutfit: String?,
    onSwitchOutfit: () -> Unit
) {
    val outfit = currentOutfit?.let { OutfitManager.getOutfitById(it) }
    val outfitName = outfit?.name ?: "默认"
    val outfitEmoji = when (currentOutfit) {
        OutfitId.SCHOLAR_HAT -> "🎓"
        OutfitId.TIE -> "👔"
        OutfitId.CROWN -> "👑"
        OutfitId.ANGEL_WINGS -> "🪽"
        OutfitId.CAPE -> "🧥"
        HolidayOutfitId.CHRISTMAS_HAT -> "🎅"
        HolidayOutfitId.NEW_YEAR_HAT -> "🎉"
        HolidayOutfitId.RED_SCARF -> "🧣"
        HolidayOutfitId.LANTERN -> "🏮"
        else -> "🐕"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🎨 当前装扮",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = outfitEmoji,
                fontSize = 24.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = outfitName,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 切换装扮按钮
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = UiColors.Primary,
                modifier = Modifier.clickable { onSwitchOutfit() }
            ) {
                Text(
                    text = "切换装扮",
                    fontSize = 12.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
