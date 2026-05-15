package com.corgimemo.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.CorgiMood
import com.corgimemo.app.animation.MoodManager

/**
 * 情绪值指示器组件
 * 显示圆形进度条，颜色随情绪值变化
 *
 * @param moodValue 情绪值（0-100）
 * @param size 指示器大小
 * @param modifier 修饰符
 */
@Composable
fun MoodIndicator(
    moodValue: Int,
    size: Int = 72,
    modifier: Modifier = Modifier
) {
    val clampedMood = moodValue.coerceIn(0, 100)
    val progress = clampedMood / 100f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 600),
        label = "moodProgress"
    )

    var showTooltip by remember { mutableStateOf(false) }
    val mood = MoodManager.getMoodFromValue(clampedMood)
    val colors = getMoodColorPair(clampedMood)

    Box(
        modifier = modifier
            .size(size.dp)
            .clickable { showTooltip = !showTooltip },
        contentAlignment = Alignment.Center
    ) {
        // 外层光晕效果
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp)
        ) {
            val strokeWidth = size.dp.toPx() * 0.08f
            val halfStroke = strokeWidth / 2

            // 背景轨道（浅灰色圆环）
            drawCircle(
                color = Color(0x33000000),
                radius = size.dp.toPx() / 2 - halfStroke,
                style = Stroke(width = strokeWidth)
            )

            // 进度圆弧（渐变）
            drawArc(
                brush = Brush.horizontalGradient(
                    colors = listOf(colors.start, colors.end)
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }

        // 中心数字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$clampedMood",
                fontSize = (size * 0.32).sp,
                fontWeight = FontWeight.Bold,
                color = colors.text
            )
        }

        // 悬停/点击显示情绪标签
        if (showTooltip) {
            MoodTooltip(
                mood = mood,
                moodValue = clampedMood,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * 情绪标签悬浮提示
 *
 * @param mood 情绪状态
 * @param moodValue 情绪值
 * @param modifier 修饰符
 */
@Composable
private fun MoodTooltip(
    mood: CorgiMood,
    moodValue: Int,
    modifier: Modifier = Modifier
) {
    val description = when (mood) {
        CorgiMood.EXCITED -> "超级兴奋！"
        CorgiMood.HAPPY -> "心情不错~"
        CorgiMood.NORMAL -> "状态一般"
        CorgiMood.EXPECTING -> "期待中..."
        CorgiMood.WORRIED -> "有点担心"
        CorgiMood.SLEEPY -> "有点困了"
        CorgiMood.SAD -> "情绪低落"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val moodEmoji = when (mood) {
                CorgiMood.EXCITED -> "🎉"
                CorgiMood.HAPPY -> "😊"
                CorgiMood.NORMAL -> "🐾"
                CorgiMood.EXPECTING -> "🤔"
                CorgiMood.WORRIED -> "😟"
                CorgiMood.SLEEPY -> "💤"
                CorgiMood.SAD -> "🥺"
            }
            Text(
                text = moodEmoji,
                fontSize = 18.sp
            )
            Column {
                Text(
                    text = description,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "情绪值：$moodValue",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 情绪颜色对
 *
 * @property start 渐变起始色
 * @property end 渐变结束色
 * @property text 文字颜色
 */
private data class MoodColorPair(
    val start: Color,
    val end: Color,
    val text: Color
)

/**
 * 根据情绪值获取颜色对
 *
 * @param moodValue 情绪值（0-100）
 * @return 颜色对
 */
private fun getMoodColorPair(moodValue: Int): MoodColorPair {
    return when {
        moodValue >= 80 -> MoodColorPair(
            start = Color(0xFF81C784),
            end = Color(0xFF4CAF50),
            text = Color(0xFF2E7D32)
        )
        moodValue >= 60 -> MoodColorPair(
            start = Color(0xFFAED581),
            end = Color(0xFF8BC34A),
            text = Color(0xFF558B2F)
        )
        moodValue >= 40 -> MoodColorPair(
            start = Color(0xFFFFD54F),
            end = Color(0xFFFFC107),
            text = Color(0xFFF57F17)
        )
        moodValue >= 20 -> MoodColorPair(
            start = Color(0xFFFF8A65),
            end = Color(0xFFFF5722),
            text = Color(0xFFBF360C)
        )
        else -> MoodColorPair(
            start = Color(0xFFEF5350),
            end = Color(0xFFF44336),
            text = Color(0xFFB71C1C)
        )
    }
}
