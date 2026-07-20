package com.corgimemo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.HolidayOutfitId
import com.corgimemo.app.animation.Outfit
import com.corgimemo.app.animation.OutfitId
import com.corgimemo.app.animation.OutfitManager

/**
 * 快速换装底部弹窗
 *
 * 显示已解锁装扮的横滑列表，点击任一卡片后立即落库（`quickSwitchOutfit`）并关闭弹窗。
 * 不进入"试穿 → 确认"两段式，**适合轻量切换场景**。
 *
 * 与 OutfitScreen（独立装扮详情页）的区别：
 * - OutfitQuickSwitchSheet：一步到位、不离开当前页面、只看已解锁
 * - OutfitScreen：试穿预览、需手动"应用"、展示未解锁装扮的解锁条件
 *
 * 当前挂载点：HomeScreen（长按柯基）、CorgiDetailScreen（"切换装扮"按钮）。
 *
 * ## 使用模式
 *
 * ```
 * val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
 * LaunchedEffect(showOutfitSheet) {
 *     if (showOutfitSheet) sheetState.show()
 *     else if (sheetState.isVisible) sheetState.hide()
 * }
 * if (showOutfitSheet) {
 *     OutfitQuickSwitchSheet(
 *         sheetState = sheetState,
 *         currentOutfitId = currentOutfit,  // 注意：不要叠加节日装扮 effectiveOutfit
 *         unlockedOutfitsJson = corgiData?.unlockedOutfits ?: "[]",
 *         onSelect = { id -> viewModel.quickSwitchOutfit(id) },
 *         onDismiss = { viewModel.hideOutfitSheet() }
 *     )
 * }
 * ```
 *
 * @param sheetState 底部弹窗状态（外部 rememberModalBottomSheetState）
 * @param currentOutfitId 当前装扮 ID（用于高亮"✓ 当前"标签；建议传用户实际选择，不叠加节日）
 * @param unlockedOutfitsJson 已解锁装扮 JSON 字符串（来自 CorgiData.unlockedOutfits）
 * @param onSelect 选择回调，参数为选中的 outfit.id
 * @param onDismiss 关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutfitQuickSwitchSheet(
    sheetState: SheetState,
    currentOutfitId: String?,
    unlockedOutfitsJson: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        sheetState = sheetState,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "快速换装 🎨",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "长按柯基试试这个功能~",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 过滤出已解锁的装扮（getOutfitsWithStatus 返回 List<Pair<Outfit, Boolean>>）
            val unlockedOutfits = OutfitManager.getOutfitsWithStatus(unlockedOutfitsJson)
                .filter { it.second }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                items(unlockedOutfits) { (outfit, _) ->
                    val isSelected = currentOutfitId == outfit.id ||
                            (outfit.isDefault && currentOutfitId == null)
                    QuickOutfitCard(
                        outfit = outfit,
                        isSelected = isSelected,
                        onClick = { onSelect(outfit.id) }
                    )
                }
            }
        }
    }
}

/**
 * 快速换装卡片
 *
 * 与 ProfileScreen 的 OutfitCard 类似，但简化为只显示已解锁装扮。
 * 选中态：主色背景 + "✓ 当前" 标签。
 *
 * @param outfit 装扮数据
 * @param isSelected 是否当前选中
 * @param onClick 点击回调
 */
@Composable
fun QuickOutfitCard(
    outfit: Outfit,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        modifier = Modifier
            .width(100.dp)
            .height(120.dp)
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
                text = outfitEmoji(outfit.id),
                fontSize = 32.sp
            )
            Text(
                text = outfit.name,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.padding(top = 4.dp)
            )
            if (isSelected) {
                Text(
                    text = "✓ 当前",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

/**
 * 装扮 ID → Emoji 映射（与 OutfitManager / CorgiDetailScreen 保持一致）
 *
 * @param outfitId 装扮 ID
 * @return 对应 emoji，未知 ID 兜底 🐕
 */
private fun outfitEmoji(outfitId: String): String = when (outfitId) {
    OutfitId.DEFAULT -> "🐕"
    OutfitId.SCHOLAR_HAT -> "🎓"
    OutfitId.TIE -> "👔"
    OutfitId.CROWN -> "👑"
    OutfitId.ANGEL_WINGS -> "🪽"
    OutfitId.CAPE -> "🧥"
    // 节日装扮
    HolidayOutfitId.NEW_YEAR_HAT -> "🎉"
    HolidayOutfitId.RED_SCARF -> "🧣"
    HolidayOutfitId.LANTERN -> "🏮"
    HolidayOutfitId.LABOR_HAT -> "⛑️"
    HolidayOutfitId.DRAGON_HAT -> "🐲"
    HolidayOutfitId.FLAG -> "🇨🇳"
    HolidayOutfitId.MOON_DECOR -> "🌕"
    HolidayOutfitId.SCARF -> "🧶"
    HolidayOutfitId.CHRISTMAS_HAT -> "🎅"
    else -> "🐕"
}
