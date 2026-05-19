package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.SolarTerm

/**
 * 节气科普卡片
 * 显示节气名称、谚语、科普知识和柯基话语
 * 支持滑动关闭和分享功能
 *
 * @param solarTerm 节气数据
 * @param onShare 分享按钮点击回调
 * @param onDismiss 滑动关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SolarTermCard(
    solarTerm: SolarTerm,
    onShare: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    /**
     * 滑动关闭状态
     * 使用新的 Material3 SwipeToDismissBox API
     */
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd,
                SwipeToDismissBoxValue.EndToStart -> {
                    onDismiss()
                    true
                }
                else -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeBackground(dismissState)
        },
        content = {
            CardContent(solarTerm, onShare)
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    )
}

/**
 * 卡片内容
 *
 * @param solarTerm 节气数据
 * @param onShare 分享按钮点击回调
 */
@Composable
private fun CardContent(
    solarTerm: SolarTerm,
    onShare: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF8F0)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF0E0),
                            Color(0xFFFFF8F0)
                        )
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = solarTerm.iconEmoji,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = solarTerm.displayName,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65C00)
                        )
                        Text(
                            text = "二十四节气",
                            fontSize = 12.sp,
                            color = Color(0xFFFF8C42),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                TextButton(
                    onClick = onShare
                ) {
                    Text(
                        text = "📤 分享",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE65C00)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color(0xFFFFD4B8),
                thickness = 1.dp
            )

            Text(
                text = "「${solarTerm.proverb}」",
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8B4513),
                lineHeight = 22.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Text(
                text = "💡 ${solarTerm.knowledge}",
                fontSize = 14.sp,
                color = Color(0xFF5D4037),
                lineHeight = 20.sp
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color(0xFFFFD4B8),
                thickness = 1.dp
            )

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFE4C4)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🐕",
                        fontSize = 20.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "柯基说",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8B4513)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = solarTerm.corgiSays,
                            fontSize = 14.sp,
                            color = Color(0xFF5D4037),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 滑动背景动画
 * 当用户滑动卡片时显示的背景
 *
 * @param dismissState 滑动状态
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(dismissState: SwipeToDismissBoxState) {
    val targetValue = dismissState.targetValue

    val alignment = when (targetValue) {
        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
        else -> Alignment.Center
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = Color(0xFFFFCDD2),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = alignment
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "关闭",
                tint = Color(0xFFD32F2F),
                modifier = Modifier.width(28.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "关闭",
                fontSize = 12.sp,
                color = Color(0xFFD32F2F)
            )
        }
    }
}
