package com.corgimemo.app.ui.screens.profile

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.InteractiveCorgi
import com.corgimemo.app.animation.Outfit
import com.corgimemo.app.animation.OutfitManager
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.CorgiPose
import com.corgimemo.app.animation.PoseManager
import com.corgimemo.app.animation.OutfitRecommendation
import com.corgimemo.app.data.model.Achievement as NewAchievement
import com.corgimemo.app.ui.components.CorgiNamerDialog
import com.corgimemo.app.viewmodel.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val corgiData by viewModel.corgiData.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    val outfits by viewModel.outfits.collectAsState()
    val levelStage by viewModel.levelStage.collectAsState()
    val levelProgress by viewModel.levelProgress.collectAsState()
    val progressText by viewModel.progressText.collectAsState()
    val isPreviewMode by viewModel.isPreviewMode.collectAsState()
    val previewOutfit by viewModel.previewOutfit.collectAsState()
    val recommendedOutfit by viewModel.recommendedOutfit.collectAsState()
    val moodHistory7Days by viewModel.moodHistory7Days.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()

    var showAchievementDetail by remember { mutableStateOf<NewAchievement?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingNewName by remember { mutableStateOf("") }

    // 退出页面时取消预览模式，恢复原装扮
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelPreview()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "个人中心", color = MaterialTheme.colorScheme.onSurface)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "设置",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            item {
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Text(
                            text = "柯",
                            fontSize = 32.sp,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(top = 18.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }

                    Text(
                        text = corgiData?.name ?: "小柯基",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        StatItem(label = "等级", value = "${corgiData?.level ?: 1}")
                        StatItem(label = "经验", value = "${corgiData?.experience ?: 0}")
                        StatItem(label = "情绪", value = "${corgiData?.moodValue ?: 50}%")
                    }

                    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = levelStage.displayName,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = progressText,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        LinearProgressIndicator(
                            progress = { levelProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .clickable { navController.navigate("stats") }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "成就统计",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "查看详情 ›",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    StatRow(label = "累计完成任务", value = "${corgiData?.totalCompleted ?: 0} 个")
                    StatRow(label = "连续完成天数", value = "${corgiData?.consecutiveDays ?: 0} 天")
                    StatRow(label = "最长连续天数", value = "${corgiData?.maxConsecutiveDays ?: 0} 天")
                    StatRow(
                        label = "成就解锁",
                        value = "${achievements.count { it.second }}/${achievements.size}"
                    )
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                com.corgimemo.app.ui.components.MoodHistoryChart(
                    historyList = moodHistory7Days,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🏆 ${achievements.count { it.second }}/${achievements.size}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "查看全部 ›",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable {
                                navController.navigate("achievement")
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(achievements) { (achievement, isUnlocked) ->
                            AchievementCard(
                                achievement = achievement,
                                isUnlocked = isUnlocked,
                                onClick = { showAchievementDetail = achievement }
                            )
                        }
                    }
                }
            }

            recommendedOutfit?.let { recommendation ->
                OutfitRecommendationBanner(
                    recommendation = recommendation,
                    onApply = {
                        if (isPreviewMode) {
                            viewModel.previewOutfit(recommendation.outfitId)
                        } else {
                            viewModel.selectOutfit(recommendation.outfitId)
                        }
                    }
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (recommendedOutfit != null) 12.dp else 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "装扮",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isPreviewMode) {
                            androidx.compose.material3.TextButton(
                                onClick = { viewModel.enterPreviewMode() }
                            ) {
                                Text(
                                    text = "🎨 预览模式",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    if (isPreviewMode) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "✨ 预览模式：点击下方装扮卡片即可预览效果，不会立即保存",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = if (isPreviewMode) {
                            val previewOutfitObj = OutfitManager.getCurrentOutfit(previewOutfit)
                            "预览装扮: ${previewOutfitObj.name}"
                        } else {
                            "当前装扮: ${viewModel.getCurrentOutfit().name}"
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    corgiData?.let { data ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPreviewMode) {
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                }
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            InteractiveCorgi(
                                pose = PoseManager.getDefaultPose(),
                                mood = CorgiMood.NORMAL,
                                corgiName = data.name,
                                level = data.level,
                                outfitId = if (isPreviewMode) previewOutfit else data.currentOutfit,
                                hapticEnabled = hapticEnabled,
                                soundEnabled = soundEnabled,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(outfits) { (outfit, isUnlocked) ->
                            val currentDisplayId = if (isPreviewMode) previewOutfit else corgiData?.currentOutfit
                            val isSelected = currentDisplayId == outfit.id ||
                                    (outfit.isDefault && currentDisplayId == null)
                            OutfitCard(
                                outfit = outfit,
                                isUnlocked = isUnlocked,
                                isSelected = isSelected,
                                onSelect = {
                                    if (isUnlocked) {
                                        val effectiveOutfitId = if (outfit.isDefault) null else outfit.id
                                        // 点击装扮卡片时自动进入预览模式并预览
                                        if (!isPreviewMode) {
                                            viewModel.enterPreviewMode()
                                        }
                                        viewModel.previewOutfit(effectiveOutfitId)
                                    }
                                }
                            )
                        }
                    }

                    if (isPreviewMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.OutlinedButton(
                                onClick = { viewModel.cancelPreview() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "取消")
                            }
                            androidx.compose.material3.Button(
                                onClick = { viewModel.applyPreview() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "应用装扮")
                            }
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "柯基设置",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    SettingItemCard(
                        title = "修改名字",
                        description = "当前名字：${corgiData?.name ?: "小柯基"}",
                        onClick = { showRenameDialog = true }
                    )
                }
            }

            androidx.compose.material3.Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(text = "返回首页")
            }
            }
        }
    }

    showAchievementDetail?.let { achievement ->
        AchievementDetailDialog(
            achievement = achievement,
            isUnlocked = viewModel.isAchievementUnlocked(achievement.id),
            onDismiss = { showAchievementDetail = null }
        )
    }

    if (showRenameDialog) {
        CorgiRenameDialog(
            currentName = corgiData?.name ?: "小柯基",
            onConfirm = { newName ->
                val (isValid, _) = viewModel.validateName(newName)
                if (isValid) {
                    pendingNewName = newName
                    showRenameDialog = false
                    showConfirmDialog = true
                }
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Text(
                    text = "确认修改",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "确定要将柯基的名字改为「$pendingNewName」吗？",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.updateCorgiName(pendingNewName)
                        showConfirmDialog = false
                        pendingNewName = ""
                    }
                ) {
                    Text(text = "确定")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showConfirmDialog = false
                        showRenameDialog = true
                    }
                ) {
                    Text(text = "取消")
                }
            }
        )
    }
}

/**
 * 成就卡片
 *
 * @param achievement 成就
 * @param isUnlocked 是否已解锁
 * @param onClick 点击回调
 */
@Composable
fun AchievementCard(
    achievement: NewAchievement,
    isUnlocked: Boolean,
    onClick: () -> Unit
) {
    // 使用新系统的成就图标
    val icon = achievement.icon

    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier
            .width(110.dp)
            .height(140.dp)
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
                text = icon,
                fontSize = 32.sp,
                color = if (!isUnlocked) Color.Gray.copy(alpha = 0.5f) else Color.Unspecified
            )
            Text(
                text = achievement.name,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isUnlocked) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = if (isUnlocked) "✓ 已解锁" else "🔒",
                fontSize = 10.sp,
                color = if (isUnlocked) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/**
 * 装扮卡片
 *
 * @param outfit 装扮
 * @param isUnlocked 是否已解锁
 * @param isSelected 是否当前选择
 * @param onSelect 选择回调
 */
@Composable
fun OutfitCard(
    outfit: Outfit,
    isUnlocked: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelected -> MaterialTheme.colorScheme.primary
                isUnlocked -> MaterialTheme.colorScheme.surfaceVariant
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier
            .width(90.dp)
            .height(120.dp)
            .clickable(enabled = isUnlocked, onClick = onSelect)
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
                    OutfitManager.defaultOutfit.id -> "🐕"
                    com.corgimemo.app.animation.OutfitId.SCHOLAR_HAT -> "🎓"
                    com.corgimemo.app.animation.OutfitId.TIE -> "👔"
                    com.corgimemo.app.animation.OutfitId.CROWN -> "👑"
                    com.corgimemo.app.animation.OutfitId.ANGEL_WINGS -> "🪽"
                    com.corgimemo.app.animation.OutfitId.CAPE -> "🧥"
                    else -> "🐕"
                },
                fontSize = 28.sp,
                color = if (!isUnlocked) Color.Gray.copy(alpha = 0.5f) else Color.Unspecified
            )
            Text(
                text = outfit.name,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isUnlocked -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            if (!isUnlocked) {
                Text(
                    text = OutfitManager.getUnlockCondition(outfit.id),
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            if (isSelected) {
                Text(
                    text = "✓ 已装备",
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * 成就详情弹窗
 *
 * @param achievement 成就
 * @param isUnlocked 是否已解锁
 * @param onDismiss 关闭回调
 */
@Composable
fun AchievementDetailDialog(
    achievement: NewAchievement,
    isUnlocked: Boolean,
    onDismiss: () -> Unit
) {
    // 使用新系统的成就图标和描述
    val icon = achievement.icon
    val conditionText = achievement.description

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
                    fontSize = 56.sp,
                    color = if (!isUnlocked) Color.Gray.copy(alpha = 0.5f) else Color.Unspecified
                )
                Text(
                    text = achievement.name,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isUnlocked) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = conditionText,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
                // 显示解锁故事
                if (isUnlocked && achievement.story.isNotEmpty()) {
                    Text(
                        text = "\"${achievement.story}\"",
                        fontSize = 13.sp,
                        color = Color(0xFFEA580C),
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                achievement.outfitId?.let { outfitId ->
                    val outfit = OutfitManager.getOutfitById(outfitId)
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
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = outfitIcon,
                                fontSize = 18.sp,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                text = "解锁装扮：${it.name}",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                androidx.compose.material3.Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "知道了")
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 装扮推荐横幅组件
 * 显示在装扮区域顶部，推荐适合当前节日/季节的装扮
 *
 * @param recommendation 装扮推荐
 * @param onApply 点击应用回调
 */
@Composable
fun OutfitRecommendationBanner(
    recommendation: OutfitRecommendation,
    onApply: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = recommendation.badge,
                    fontSize = 28.sp
                )
                Column {
                    Text(
                        text = recommendation.reason,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = recommendation.outfitIcon,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Text(
                            text = "推荐：${recommendation.outfitName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            androidx.compose.material3.TextButton(
                onClick = onApply,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "试试看",
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * 设置项卡片组件
 * 显示标题和描述，可点击跳转
 *
 * @param title 标题
 * @param description 描述
 * @param onClick 点击回调
 */
@Composable
fun SettingItemCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Text(
            text = "›",
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 柯基改名对话框
 *
 * @param currentName 当前柯基名字
 * @param onConfirm 确认回调，返回新名字
 * @param onDismiss 取消回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorgiRenameDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    val isValidName = name.length in 1..8

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "\uD83D\uDC3A",
                    style = MaterialTheme.typography.displayLarge
                )
                Text(
                    text = "修改柯基名字",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "当前名字：$currentName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = {
                        if (it.length <= 8) {
                            name = it
                        }
                    },
                    placeholder = { Text(text = "请输入新名字（1-8个字符）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        Text(
                            text = "${name.length}/8",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (name.isNotEmpty() && !isValidName) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    },
                    isError = name.isNotEmpty() && !isValidName
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.Button(
                onClick = { onConfirm(name) },
                enabled = isValidName && name != currentName,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(text = "确认")
            }
        },
        dismissButton = {
            androidx.compose.material3.OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(text = "取消")
            }
        }
    )
}
