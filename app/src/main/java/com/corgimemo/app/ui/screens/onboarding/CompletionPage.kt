package com.corgimemo.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.viewmodel.OnboardingViewModel

/**
 * 完成总结页
 *
 * 展示柯基 PROUD 动画和引导设置总结：
 * - 柯基名字
 * - 创建的待办数量
 * - 创建的灵感数量
 * - 权限已配置
 *
 * 底部"进入刻记⁺"按钮，点击后导航到主页
 *
 * @param viewModel 引导 ViewModel
 * @param onComplete 点击"进入刻记⁺"按钮的回调
 * @param isCompleting 是否正在完成引导
 */
@Composable
fun CompletionPage(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    isCompleting: Boolean
) {
    val corgiName by viewModel.corgiName.collectAsState()
    val todoCount by viewModel.createdTodoCount.collectAsState()
    val inspirationCount by viewModel.createdInspirationCount.collectAsState()

    val displayName = corgiName.ifEmpty { "小柯基" }

    // Box 包裹 + Column.align(Center) + verticalScroll：
    // 内容少时垂直居中显示，内容多时可滚动查看
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // 柯基 PROUD 动画
        FrameAnimation(
            animationType = AnimationType.PROUD,
            fps = 12,
            isLooping = true,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "🎉 设置完成！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "很高兴认识你！让我们一起开始吧~",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 总结列表
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                SummaryItem(
                    icon = "🐕",
                    label = "柯基名字",
                    value = displayName
                )

                Spacer(modifier = Modifier.height(12.dp))

                SummaryItem(
                    icon = "📋",
                    label = "创建了待办",
                    value = "$todoCount 个"
                )

                Spacer(modifier = Modifier.height(12.dp))

                SummaryItem(
                    icon = "💡",
                    label = "创建了灵感",
                    value = "$inspirationCount 个"
                )

                Spacer(modifier = Modifier.height(12.dp))

                SummaryItem(
                    icon = "⚙️",
                    label = "权限已配置",
                    value = "完成"
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // 进入刻记⁺ 按钮
        Button(
            onClick = onComplete,
            enabled = !isCompleting,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "进入刻记⁺",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
        } // 关闭 Column
    } // 关闭 Box
}

/**
 * 总结列表项
 *
 * @param icon 图标
 * @param label 标签
 * @param value 值
 */
@Composable
private fun SummaryItem(
    icon: String,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = icon,
                fontSize = 20.sp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = "✓ $value",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
