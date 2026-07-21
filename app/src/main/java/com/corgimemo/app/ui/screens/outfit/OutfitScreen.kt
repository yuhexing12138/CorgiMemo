package com.corgimemo.app.ui.screens.outfit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.CorgiPose
import com.corgimemo.app.ui.components.CorgiDesktopPet
import com.corgimemo.app.ui.components.GlobalSnackbarController
import com.corgimemo.app.ui.screens.profile.OutfitCard
import com.corgimemo.app.ui.screens.profile.OutfitRecommendationBanner
import com.corgimemo.app.viewmodel.ProfileViewModel

/**
 * 装扮详情页
 *
 * v1.5 桌宠化改造：
 * - 删除原 InteractiveCorgi 预览区（占位 200+ dp 的低利用率区域）
 * - 用 CorgiDesktopPet 作为背景层覆盖整页，桌宠装扮随用户选择实时变化
 * - 桌宠在 OutfitScreen 中**仅展示换装效果**，不响应互动经验/菜单（避免与装扮流程冲突）
 *
 * 现有交互能力（v1.4 保留）：
 * 1. 装扮推荐横幅（OutfitRecommendationBanner）—— 季节/节日推荐
 * 2. 装扮横滑列表（LazyRow + OutfitCard）—— 点击进入预览
 * 3. 预览模式操作栏（取消/应用装扮按钮）—— 应用才落库
 * 4. DisposableEffect 退出清理 —— 离开页面自动取消预览，恢复原装扮
 *
 * 桌宠参数：
 * - outfitId = 预览模式时用 `previewOutfit`，否则用 `corgiData?.currentOutfit`
 * - onPet/onFeed/onPlay/onSleep = 空 lambda（OutfitScreen 不增互动经验）
 * - onShowSnackbar → GlobalSnackbarController（全局提示）
 *
 * 复用 ProfileViewModel（Hilt 默认按 NavBackStackEntry scope 共享实例），
 * 与 ProfileScreen 中的装扮逻辑保持一致。
 *
 * @param navController 导航控制器（用于返回上一页）
 * @param viewModel 共享的 ProfileViewModel，由 Hilt 注入
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    // 订阅 ViewModel 装扮相关状态：collectAsState + by 委托保证重组时拿到最新值
    val corgiData by viewModel.corgiData.collectAsState()
    val outfits by viewModel.outfits.collectAsState()
    val isPreviewMode by viewModel.isPreviewMode.collectAsState()
    val previewOutfit by viewModel.previewOutfit.collectAsState()
    val recommendedOutfit by viewModel.recommendedOutfit.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()

    // 退出页面时取消预览模式，恢复原装扮
    // 注意：viewModel 是函数参数（stable 引用），onDispose lambda 捕获它不存在
    //       可变集合快照陷阱，与 ProfileScreen 中的实现保持一致。
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelPreview()
        }
    }

    // 桌宠当前展示的装扮 ID（预览时用 previewOutfit，否则用当前装扮）
    // 节日装扮由 HomeViewModel 在 CorgiDetailScreen 中叠加（effectiveOutfit），
    // OutfitScreen 不展示节日装扮覆盖——这是用户主动试穿流程。
    val desktopPetOutfit = if (isPreviewMode) previewOutfit else corgiData?.currentOutfit

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "🎩 装扮", fontWeight = FontWeight.SemiBold) },
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
    ) { paddingValues ->
        // v1.5 桌宠化改造：用 Box 包裹下层 LazyColumn + 上层 CorgiDesktopPet
        // 下层：装扮推荐 + 装扮横滑 + 预览模式操作栏
        // 上层：桌宠覆盖整页，桌宠装扮随用户选择实时变化
        //       桌宠可拖动、但 onPet/onFeed/onPlay = 空 lambda（不响应互动）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 装扮推荐横幅（季节/节日推荐，可能为 null）
                item {
                    recommendedOutfit?.let { recommendation ->
                        OutfitRecommendationBanner(
                            recommendation = recommendation,
                            onApply = {
                                // 预览模式下点击"试试看"实时预览；非预览模式直接落库
                                if (isPreviewMode) {
                                    viewModel.previewOutfit(recommendation.outfitId)
                                } else {
                                    viewModel.selectOutfit(recommendation.outfitId)
                                }
                            }
                        )
                    }
                }

                // 2. 装扮卡（标题 + 横滑列表 + 预览模式操作栏）
                // v1.5：删除原 InteractiveCorgi 预览区与"当前/预览装扮名"文字
                //      桌宠作为背景层实时展示换装效果
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 顶部：装扮标题 + 预览模式标签（提示当前状态）
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "装扮",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (isPreviewMode) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "✨ 预览中",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // 预览模式背景提示（说明操作流程）
                            if (isPreviewMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "👆 点击下方装扮可即时换装，点「应用」保存",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 装扮横滑列表
                            // outfits 元素类型为 Pair<Outfit, Boolean>（装扮 + 是否解锁）
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(outfits) { (outfit, isUnlocked) ->
                                    // 计算当前展示的装扮 ID（预览模式 vs 实际状态）
                                    val currentDisplayId = if (isPreviewMode) previewOutfit else corgiData?.currentOutfit
                                    val isSelected = currentDisplayId == outfit.id ||
                                            (outfit.isDefault && currentDisplayId == null)
                                    OutfitCard(
                                        outfit = outfit,
                                        isUnlocked = isUnlocked,
                                        isSelected = isSelected,
                                        onSelect = {
                                            if (isUnlocked) {
                                                // 默认装扮用 null 表示"无装扮"，其余用 outfit.id
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

                            // 预览模式操作栏：取消 / 应用装扮
                            if (isPreviewMode) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.cancelPreview() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(text = "取消")
                                    }
                                    Button(
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
                }
            }

            // ===== 桌宠层：覆盖整页，柯基自由活动，桌宠装扮随用户选择实时变化 =====
            // 桌宠在 OutfitScreen 中**仅展示换装效果**，不响应互动经验/菜单
            // onPet/onFeed/onPlay/onSleep = 空 lambda（避免与装扮流程冲突）
            if (corgiData != null) {
                CorgiDesktopPet(
                    pose = CorgiPose.SIT,
                    mood = CorgiMood.NORMAL,
                    outfitId = desktopPetOutfit,
                    soundEnabled = soundEnabled,
                    hapticEnabled = hapticEnabled,
                    onShowSnackbar = { msg ->
                        GlobalSnackbarController.showMessage(msg)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
