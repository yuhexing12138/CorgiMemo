package com.corgimemo.app.ui.screens.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.ui.components.UserAvatar

/**
 * "我的"页用户信息头卡
 *
 * 视觉规范（v1.3 签名同步改造）：
 * - 主色浅渐变背景（primaryContainer → surface，135°）
 * - 圆角 20dp，elevation 2dp
 * - 72dp 圆形用户头像（与 drawer 顶部 48dp 同 UserAvatar 组件，缩放一致）
 * - 名字 20sp Bold（点击跳 ProfileDetail）
 * - 副标题 13sp Medium 主色：显示用户签名（与个人信息页 / 侧滑栏保持同步）
 *
 * 改造前：🐕 emoji 头像 + 柯基名字 + 等级徽章 + 经验条 + 三栏统计（累计/连续/情绪）
 * 改造后：用户头像（首字母徽章）+ 昵称 + 签名副标题
 * 柯基相关内容（等级/经验/统计）已迁出到 CorgiDetailScreen 柯基互动页
 *
 * @param corgiData 柯基数据（null 时显示占位）
 * @param consecutiveDays 连续活跃天数（v1.3 起副标题改为签名，此参数保留兼容调用方但不参与显示）
 * @param onNameClick 头像/名字/签名点击回调（跳 ProfileDetail 路由，由调用方 ProfileScreen 传入 navigate lambda）
 */
@Composable
fun ProfileHeroCard(
    corgiData: CorgiData?,
    consecutiveDays: Int,
    onNameClick: () -> Unit
) {
    // 渐变背景：primaryContainer → surface（营造柔和过渡，与改造前保持视觉锚点一致）
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 用户头像 72dp（首字母占位 / Coil 加载真实头像）
                // 点击头像跳 ProfileDetail 路由（头像/名字/签名整体作为入口）
                UserAvatar(
                    nickname = corgiData?.name ?: "小柯基",
                    avatarPath = corgiData?.avatarPath,
                    size = 72.dp,
                    onClick = onNameClick
                )

                // 右侧：昵称 + 签名（v1.3 起：副标题由"Lv.X · 柯基陪伴 N 天"改为用户签名，
                // 与个人信息页 BigAvatarCard 和侧滑栏 DrawerUserHeader 保持同步）
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = corgiData?.name ?: "小柯基",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(onClick = onNameClick)
                    )
                    Text(
                        // 副标题：用户签名（默认"记录生活，刻下美好"，与 CorgiData.signature 默认值一致）
                        text = corgiData?.signature ?: "记录生活，刻下美好",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable(onClick = onNameClick)
                            .padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
