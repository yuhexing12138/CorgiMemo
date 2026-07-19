package com.corgimemo.app.ui.screens.profile.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.LevelStage
import com.corgimemo.app.data.model.CorgiData

/**
 * 柯基展示头卡
 *
 * 视觉规范：
 * - 主色浅渐变背景（primaryContainer → surface，135°）
 * - 圆角 20dp，elevation 2dp
 * - 柯基头像 72dp 圆形，白色背景
 * - 名字 20sp Bold + Lv 徽章 10sp Bold 胶囊
 * - 经验进度条 6dp 高，圆角 3dp
 * - 底部三栏快捷统计（累计/连续/情绪）
 *
 * API 适配说明：
 * 计划原定在 72dp 圆形头像内嵌入 InteractiveCorgi，但 InteractiveCorgi 内部强制
 * `modifier.fillMaxWidth().padding(16.dp)` 且 baseSize=120dp，无法约束到 72dp 小尺寸
 * （会撑破头卡布局）。按计划"可降级为静态 emoji 🐕"的约定，此处头像降级为静态 emoji。
 * hapticEnabled / soundEnabled 参数保留以维持计划定义的公开签名，便于后续 ProfileScreen
 * 调用与未来 InteractiveCorgi 重新接入。
 *
 * @param corgiData 柯基数据（null 时显示占位）
 * @param levelStage 等级阶段
 * @param levelProgress 进度 0..1
 * @param progressText 进度文字（如 "62/100"）
 * @param hapticEnabled 触觉反馈开关（降级后暂未使用，保留签名）
 * @param soundEnabled 音效开关（降级后暂未使用，保留签名）
 * @param onNameClick 点击名字回调（触发改名弹窗）
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun ProfileHeroCard(
    corgiData: CorgiData?,
    levelStage: LevelStage,
    levelProgress: Float,
    progressText: String,
    hapticEnabled: Boolean,
    soundEnabled: Boolean,
    onNameClick: () -> Unit
) {
    // 渐变背景：primaryContainer → surface（营造柔和过渡）
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surface
        )
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(16.dp)
        ) {
            Column {
                // 顶部：柯基头像 + 名字/等级/进度
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 柯基头像（72dp 圆形，白色背景）
                    // 原计划嵌入 InteractiveCorgi，因小尺寸不兼容降级为静态 emoji 🐕
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
                                text = corgiData?.name ?: "小柯基",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.clickable(onClick = onNameClick)
                            )
                            // 等级徽章
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Lv.${corgiData?.level ?: 1}",
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
                        // 经验进度条标签
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
                        // 经验进度条（Compose 1.9.x 用 lambda 形式 progress = { value }）
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

                // 分隔虚线（柯基信息 ↔ 统计）
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

                // 底部三栏快捷统计
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    HeroStat(
                        value = "${corgiData?.totalCompleted ?: 0}",
                        label = "累计完成"
                    )
                    HeroStat(
                        value = "${corgiData?.consecutiveDays ?: 0}",
                        label = "连续天数"
                    )
                    HeroStat(
                        value = "${corgiData?.moodValue ?: 50}%",
                        label = "情绪值"
                    )
                }
            }
        }
    }
}

/**
 * 头卡底部统计项
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
