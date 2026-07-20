package com.corgimemo.app.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.Outfit
import com.corgimemo.app.animation.OutfitManager
import com.corgimemo.app.animation.OutfitRecommendation
import com.corgimemo.app.data.model.Achievement as NewAchievement
import com.corgimemo.app.ui.components.MoodHistoryChart
import com.corgimemo.app.ui.navigation.Screen
import com.corgimemo.app.ui.screens.profile.components.AchievementSummaryCard
import com.corgimemo.app.ui.screens.profile.components.OutfitEntryCard
import com.corgimemo.app.ui.screens.profile.components.ProfileHeroCard
import com.corgimemo.app.ui.screens.profile.components.ThemeQuickSwitch
import com.corgimemo.app.viewmodel.ProfileViewModel

/**
 * 「我的」页面主屏
 *
 * Phase 5 重构 + v1.1 调整：5 模块单列 LazyColumn 分层布局
 * ① 柯基展示头卡（ProfileHeroCard）
 * ② 主题配色卡（ThemeQuickSwitch，v1.1 起**只读展示**当前主题，整卡点击跳 `Screen.Appearance.route`）
 * ③ 装扮入口卡（OutfitEntryCard，点击跳 Screen.Outfit）
 * ④ 成就统计卡（AchievementSummaryCard，点击"查看全部"跳 Screen.Achievement）
 * ⑤ 7 天情绪图表（内联 Card + MoodHistoryChart）
 *
 * v1.1 调整说明：
 * - 主题配色卡移除 6 色快选 + "管理 ›" 入口，改为"大色点 + 主题名 + 描述"的只读展示
 * - 切换主题已统一收敛到 `AppearanceScreen`（深色模式 + 6 色主题色）
 * - 整卡可点击，行为更明确
 *
 * 已外移内容：
 * - 通知与提醒 / 震动与音效 / 备份与恢复 / 回收站 / 使用帮助 / 意见反馈 / 隐私与协议
 *   → 全部迁移至 SettingsScreen（设置页），本页不再展示
 * - DisposableEffect cancelPreview（→ OutfitScreen）
 * - 装扮推荐横幅（→ OutfitScreen）
 * - 装扮 Card（柯基动画预览 + 横滑列表 + 预览模式操作栏）（→ OutfitScreen）
 * - 成就横滑 LazyRow 块（→ AchievementSummaryCard）
 * - "柯基设置" Card（→ 头卡名字点击）
 * - "返回首页" Button（→ 底部导航）
 * - 外观切换（深色模式 + 主题色）（→ AppearanceScreen）
 *
 * @param navController 导航控制器
 * @param viewModel ProfileViewModel（Hilt 注入）
 */
@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    // ========== 状态收集 ==========
    val corgiData by viewModel.corgiData.collectAsState()
    val achievements by viewModel.achievements.collectAsState()
    val outfits by viewModel.outfits.collectAsState()
    val levelStage by viewModel.levelStage.collectAsState()
    val levelProgress by viewModel.levelProgress.collectAsState()
    val progressText by viewModel.progressText.collectAsState()
    val moodHistory7Days by viewModel.moodHistory7Days.collectAsState()
    val themeColor by viewModel.themeColor.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()

    // ========== 弹窗状态 ==========
    var showAchievementDetail by remember { mutableStateOf<NewAchievement?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingNewName by remember { mutableStateOf("") }

    // ========== 5 模块单列 LazyColumn ==========
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ① 柯基展示头卡
        item {
            ProfileHeroCard(
                corgiData = corgiData,
                levelStage = levelStage,
                levelProgress = levelProgress,
                progressText = progressText,
                hapticEnabled = hapticEnabled,
                soundEnabled = soundEnabled,
                onNameClick = { showRenameDialog = true }
            )
        }

        // ② 主题配色卡（v1.1：只读展示 + 整卡点击跳外观页）
        // 切换主题已统一收敛到 AppearanceScreen，本卡不再承担切换职责
        item {
            ThemeQuickSwitch(
                currentColorKey = themeColor,
                onCardClick = { navController.navigate(Screen.Appearance.route) }
            )
        }

        // ③ 装扮入口卡（点击跳 OutfitScreen）
        item {
            OutfitEntryCard(
                currentOutfitId = corgiData?.currentOutfit,
                outfitCount = outfits.size,
                onClick = { navController.navigate(Screen.Outfit.route) }
            )
        }

        // ④ 成就统计卡（点击"查看全部"跳 AchievementScreen）
        item {
            AchievementSummaryCard(
                unlockedCount = achievements.count { it.second },
                totalCount = achievements.size,
                achievements = achievements,
                onAchievementClick = { achievement -> showAchievementDetail = achievement },
                onViewAllClick = { navController.navigate(Screen.Achievement.route) }
            )
        }

        // ⑤ 7 天情绪图表（内联 Card）
        // 显式声明 containerColor = MaterialTheme.colorScheme.surface
        // 亮色模式 surface = Color.White (6 种主题色统一)，深色模式 surface = 深灰
        // 用 surface 显式声明而非硬编码 Color.White，是为了遵循 UI 设计规范 12.1.2.2：
        //   - 亮色模式卡片背景 = #FFFFFF
        //   - 深色模式卡片背景 = #2A2A2A
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "📈 近 7 天情绪",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    MoodHistoryChart(
                        historyList = moodHistory7Days,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
        }
    }

    // ========== 成就详情弹窗 ==========
    showAchievementDetail?.let { achievement ->
        AchievementDetailDialog(
            achievement = achievement,
            isUnlocked = viewModel.isAchievementUnlocked(achievement.id),
            onDismiss = { showAchievementDetail = null }
        )
    }

    // ========== 改名弹窗 ==========
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

    // ========== 改名确认弹窗 ==========
    if (showConfirmDialog) {
        AlertDialog(
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
                TextButton(
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
                TextButton(
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
                Button(
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

            TextButton(
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

    AlertDialog(
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

                OutlinedTextField(
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
            Button(
                onClick = { onConfirm(name) },
                enabled = isValidName && name != currentName,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(text = "确认")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text(text = "取消")
            }
        }
    )
}
