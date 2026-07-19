package com.corgimemo.app.ui.screens.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.Outfit
import com.corgimemo.app.animation.OutfitId
import com.corgimemo.app.animation.OutfitManager

/**
 * 装扮入口卡
 * 展示当前装扮图标+名称+总数，点击跳转 OutfitScreen
 *
 * 视觉规范：
 * - 卡片圆角 20dp、内边距 16dp、elevation 2dp
 * - 装扮预览框 48dp 方形，圆角 12dp，primaryContainer 背景
 *
 * @param currentOutfitId 当前装扮 id（null = 默认装扮）
 * @param outfitCount 装扮总数
 * @param onClick 点击回调
 */
@Composable
fun OutfitEntryCard(
    currentOutfitId: String?,
    outfitCount: Int,
    onClick: () -> Unit
) {
    // 通过 OutfitManager 解析当前装扮对象（null 时返回默认装扮）
    val currentOutfit = OutfitManager.getCurrentOutfit(currentOutfitId)
    val outfitIcon = getOutfitIconEmoji(currentOutfit)

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🎩 装扮",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 装扮预览框（48dp 方形，圆角 12dp，primaryContainer 背景）
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = outfitIcon, fontSize = 24.sp)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentOutfit.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "当前装扮 · 共 $outfitCount 套",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Text(
                    text = "›",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 装扮 id → emoji 图标映射
 * 与 ProfileScreen.OutfitCard 现有逻辑保持一致：
 * - 默认装扮 → 🐕
 * - 学士帽 / 领带 / 皇冠 / 天使翅膀 / 披风 → 对应 emoji
 * - 其他（节日装扮等）→ 🐕 兜底
 *
 * @param outfit 装扮对象
 * @return 对应的 emoji 字符串
 */
private fun getOutfitIconEmoji(outfit: Outfit): String = when (outfit.id) {
    OutfitManager.defaultOutfit.id -> "🐕"
    OutfitId.SCHOLAR_HAT -> "🎓"
    OutfitId.TIE -> "👔"
    OutfitId.CROWN -> "👑"
    OutfitId.ANGEL_WINGS -> "🪽"
    OutfitId.CAPE -> "🧥"
    else -> "🐕"
}
