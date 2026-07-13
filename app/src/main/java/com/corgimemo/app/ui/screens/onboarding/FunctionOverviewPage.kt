package com.corgimemo.app.ui.screens.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 功能概览页
 *
 * 2x2 网格展示 APP 四大核心功能：
 * - 📋 待办：拖拽排序/子任务/模板
 * - 💡 灵感：富文本/图片/字数统计
 * - 📅 日期：倒计时/卡片样式（占位）
 * - 🐕 柯基：等级/装扮（占位）
 *
 * @param onSkip 点击"跳过功能介绍"按钮的回调
 */
@Composable
fun FunctionOverviewPage(
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        // 垂直居中显示内容
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "刻记⁺ 功能概览",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "让我们一起了解 APP 的核心功能",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 第一行：待办 + 灵感
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FunctionCard(
                emoji = "📋",
                title = "待办",
                description = "拖拽排序、分区置顶、子任务",
                onClick = { },
                modifier = Modifier.weight(1f)
            )
            FunctionCard(
                emoji = "💡",
                title = "灵感",
                description = "富文本编辑、图片插入、字数统计",
                onClick = { },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 第二行：日期 + 柯基
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FunctionCard(
                emoji = "📅",
                title = "日期",
                description = "倒计时、卡片样式（敬请期待）",
                onClick = { },
                modifier = Modifier.weight(1f)
            )
            FunctionCard(
                emoji = "🐕",
                title = "柯基",
                description = "等级、经验、装扮（敬请期待）",
                onClick = { },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 跳过功能介绍按钮
        TextButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "跳过功能介绍 →",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 功能卡片
 *
 * @param emoji 功能图标
 * @param title 功能标题
 * @param description 功能描述
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun FunctionCard(
    emoji: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(160.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = emoji,
                fontSize = 36.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
