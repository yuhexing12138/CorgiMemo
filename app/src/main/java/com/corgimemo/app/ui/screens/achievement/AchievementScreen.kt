package com.corgimemo.app.ui.screens.achievement

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.InteractiveCorgi
import com.corgimemo.app.data.model.AchievementStage
import com.corgimemo.app.ui.components.AchievementBadge
import com.corgimemo.app.ui.components.AchievementDetailSheet
import com.corgimemo.app.ui.components.AchievementProgressBar
import com.corgimemo.app.viewmodel.AchievementViewModel
import kotlinx.coroutines.launch

/**
 * 成就墙页面
 * 显示所有成就的完成情况和进度
 * 柯基根据当前成就阶段显示不同姿态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementScreen(
    navController: NavController,
    viewModel: AchievementViewModel = hiltViewModel()
) {
    val achievementsWithProgress by viewModel.achievementsWithProgress.collectAsState()
    val unlockedCount by viewModel.unlockedCount.collectAsState()
    val totalCount by viewModel.totalCount.collectAsState()
    val selectedAchievement by viewModel.selectedAchievement.collectAsState()
    val selectedIsUnlocked by viewModel.selectedIsUnlocked.collectAsState()
    val selectedProgress by viewModel.selectedProgress.collectAsState()
    val corgiData by viewModel.corgiData.collectAsState()
    val currentStage by viewModel.currentStage.collectAsState()
    val corgiPose by viewModel.corgiPoseForStage.collectAsState()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // 按阶段分组（使用正确的枚举值）
    val beginnerAchievements = achievementsWithProgress.filter { it.first.stage == AchievementStage.BEGINNER }
    val growthAchievements = achievementsWithProgress.filter { it.first.stage == AchievementStage.GROWTH }
    val leapAchievements = achievementsWithProgress.filter { it.first.stage == AchievementStage.LEAP }
    val peakAchievements = achievementsWithProgress.filter { it.first.stage == AchievementStage.PEAK }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "成就墙", color = MaterialTheme.colorScheme.onSurface)
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 整体进度卡片
            if (totalCount > 0) {
                OverallProgressCard(
                    unlockedCount = unlockedCount,
                    totalCount = totalCount,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // 柯基展示卡片（根据阶段显示不同姿态）
            corgiData?.let { data ->
                CorgiStageCard(
                    corgiName = data.name,
                    currentStage = currentStage,
                    corgiPose = corgiPose,
                    currentOutfit = data.currentOutfit,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // 成就列表（按阶段分组）
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 初见阶段成就
                if (beginnerAchievements.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        StageHeader(label = "🌱 初见阶段")
                    }
                    items(beginnerAchievements) { (achievement, isUnlocked, progress) ->
                        AchievementBadge(
                            achievement = achievement,
                            isUnlocked = isUnlocked,
                            currentProgress = progress,
                            onClick = {
                                viewModel.selectAchievement(achievement, isUnlocked, progress)
                                coroutineScope.launch {
                                    sheetState.show()
                                }
                            }
                        )
                    }
                }

                // 成长阶段成就
                if (growthAchievements.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        StageHeader(label = "🌿 成长阶段")
                    }
                    items(growthAchievements) { (achievement, isUnlocked, progress) ->
                        AchievementBadge(
                            achievement = achievement,
                            isUnlocked = isUnlocked,
                            currentProgress = progress,
                            onClick = {
                                viewModel.selectAchievement(achievement, isUnlocked, progress)
                                coroutineScope.launch {
                                    sheetState.show()
                                }
                            }
                        )
                    }
                }

                // 飞跃阶段成就
                if (leapAchievements.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        StageHeader(label = "🚀 飞跃阶段")
                    }
                    items(leapAchievements) { (achievement, isUnlocked, progress) ->
                        AchievementBadge(
                            achievement = achievement,
                            isUnlocked = isUnlocked,
                            currentProgress = progress,
                            onClick = {
                                viewModel.selectAchievement(achievement, isUnlocked, progress)
                                coroutineScope.launch {
                                    sheetState.show()
                                }
                            }
                        )
                    }
                }

                // 巅峰阶段成就
                if (peakAchievements.isNotEmpty()) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        StageHeader(label = "🏆 巅峰阶段")
                    }
                    items(peakAchievements) { (achievement, isUnlocked, progress) ->
                        AchievementBadge(
                            achievement = achievement,
                            isUnlocked = isUnlocked,
                            currentProgress = progress,
                            onClick = {
                                viewModel.selectAchievement(achievement, isUnlocked, progress)
                                coroutineScope.launch {
                                    sheetState.show()
                                }
                            }
                        )
                    }
                }

                // 底部间距
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // 成就详情 Sheet
    selectedAchievement?.let { achievement ->
        AchievementDetailSheet(
            sheetState = sheetState,
            achievement = achievement,
            isUnlocked = selectedIsUnlocked,
            currentProgress = selectedProgress,
            onDismiss = {
                viewModel.clearSelectedAchievement()
                coroutineScope.launch {
                    sheetState.hide()
                }
            }
        )
    }
}

/**
 * 整体进度卡片
 * 显示已解锁/总成就数和进度条
 */
@Composable
private fun OverallProgressCard(
    unlockedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏆 成就进度",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$unlockedCount / $totalCount",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 进度条
            AchievementProgressBar(
                currentProgress = unlockedCount,
                targetProgress = totalCount,
                showLabel = false
            )
        }
    }
}

/**
 * 阶段分组标题
 */
@Composable
private fun StageHeader(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 柯基阶段展示卡片
 * 根据成就阶段显示不同姿态的柯基
 *
 * @param corgiName 柯基名字
 * @param currentStage 当前成就阶段
 * @param corgiPose 柯基姿态
 * @param currentOutfit 当前装扮 ID
 * @param modifier 修饰符
 */
@Composable
private fun CorgiStageCard(
    corgiName: String,
    currentStage: AchievementStage,
    corgiPose: com.corgimemo.app.animation.CorgiPose,
    currentOutfit: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = getStageCardColor(currentStage)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 阶段标题
            Text(
                text = "当前阶段：${currentStage.displayName}",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = getStageTextColor(currentStage)
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 阶段描述
            Text(
                text = currentStage.description,
                fontSize = 12.sp,
                color = getStageTextColor(currentStage).copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 柯基展示
            InteractiveCorgi(
                pose = corgiPose,
                mood = CorgiMood.HAPPY,
                corgiName = corgiName,
                level = 1,
                outfitId = currentOutfit,
                soundEnabled = false,
                hapticEnabled = false,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 获取阶段卡片背景色
 */
private fun getStageCardColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFFF1F5F9)
        AchievementStage.GROWTH -> Color(0xFFD1FAE5)
        AchievementStage.LEAP -> Color(0xFFDBEAFE)
        AchievementStage.PEAK -> Color(0xFFFED7AA)
    }
}

/**
 * 获取阶段文字颜色
 */
private fun getStageTextColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFF475569)
        AchievementStage.GROWTH -> Color(0xFF065F46)
        AchievementStage.LEAP -> Color(0xFF1E40AF)
        AchievementStage.PEAK -> Color(0xFF9A3412)
    }
}
