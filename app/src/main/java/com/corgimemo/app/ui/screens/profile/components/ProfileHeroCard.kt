package com.corgimemo.app.ui.screens.profile.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.ui.components.UserAvatar

/**
 * "我的"页用户信息头卡
 *
 * 视觉规范（v1.2 用户化改造）：
 * - 主色浅渐变背景（primaryContainer → surface，135°）
 * - 圆角 20dp，elevation 2dp
 * - 72dp 圆形用户头像（与 drawer 顶部 48dp 同 UserAvatar 组件，缩放一致）
 * - 名字 20sp Bold
 * - 副标题 11sp Medium 主色："Lv.X · 柯基陪伴 N 天"
 *
 * 改造前：🐕 emoji 头像 + 柯基名字 + 等级徽章 + 经验条 + 三栏统计（累计/连续/情绪）
 * 改造后：用户头像（首字母徽章）+ 昵称 + "陪伴天数"副标题
 * 柯基相关内容（等级/经验/统计）已迁出到 CorgiDetailScreen 柯基互动页
 *
 * @param corgiData 柯基数据（null 时显示占位）
 * @param consecutiveDays 连续活跃天数（用于副标题"陪伴 N 天"）
 * @param onNameClick 头像/名字点击回调（Task 11 起改为跳 ProfileDetail 路由，由调用方 ProfileScreen 传入 navigate lambda）
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
                // Task 11: 点击头像/名字跳 ProfileDetail 路由（头像/名字整体作为入口）
                UserAvatar(
                    nickname = corgiData?.name ?: "小柯基",
                    avatarPath = corgiData?.avatarPath,
                    size = 72.dp,
                    onClick = onNameClick
                )

                // 右侧：昵称 + 副标题
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = corgiData?.name ?: "小柯基",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        // 文案规则：
                        // - 默认昵称"小柯基"时,cor.level=1,consecutiveDays=0 → "Lv.1 · 柯基陪伴 0 天"
                        // - 普通用户：取等级和连续天数("陪伴"语义取 consecutiveDays,因字段已存在且语义贴切)
                        text = "Lv.${corgiData?.level ?: 1} · 柯基陪伴 ${consecutiveDays} 天",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
