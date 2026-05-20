package com.corgimemo.app.ui.screens.achievement

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
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementStage
import com.corgimemo.app.ui.components.AchievementBadge
import com.corgimemo.app.ui.components.AchievementDetailSheet
import com.corgimemo.app.ui.components.AchievementProgressBar
import com.corgimemo.app.viewmodel.AchievementViewModel
import kotlinx.coroutines.launch

/**
 * 成就墙页面
 * 显示所有成就的完成情况和进度
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // 按阶段分组
    val springAchievements = achievementsWithProgress.filter { it.first.stage == AchievementStage.SPRING }
    val growingAchievements = achievementsWithProgress.filter { it.first.stage == AchievementStage.GROWING }
    val matureAchievements = achievementsWithProgress.filter { it.first.stage == AchievementStage.MATURE }

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

            // 成就列表（按阶段分组）
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 萌芽期成就
                if (springAchievements.isNotEmpty()) {
                    item {
                        StageHeader(
                            label = "🌱 萌芽期",
                            modifier = Modifier.gridItemSpan { maxCurrentLineSpan }
                        )
                    }
                    items(springAchievements) { (achievement, isUnlocked, progress) ->
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

                // 成长期成就
                if (growingAchievements.isNotEmpty()) {
                    item {
                        StageHeader(
                            label = "🌿 成长期",
                            modifier = Modifier.gridItemSpan { maxCurrentLineSpan }
                        )
                    }
                    items(growingAchievements) { (achievement, isUnlocked, progress) ->
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

                // 成熟期成就
                if (matureAchievements.isNotEmpty()) {
                    item {
                        StageHeader(
                            label = "🌳 成熟期",
                            modifier = Modifier.gridItemSpan { maxCurrentLineSpan }
                        )
                    }
                    items(matureAchievements) { (achievement, isUnlocked, progress) ->
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
    val progressPercent = if (totalCount > 0) {
        (unlockedCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

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
 * GridItemSpan 扩展函数
 */
inline fun androidx.compose.foundation.lazy.grid.LazyGridScope.gridItemSpan(
    crossinline span: androidx.compose.foundation.lazy.grid.GridItemSpanScope.() -> Int
): androidx.compose.foundation.lazy.grid.GridItemSpan {
    return androidx.compose.foundation.lazy.grid.GridItemSpan { span() }
}
