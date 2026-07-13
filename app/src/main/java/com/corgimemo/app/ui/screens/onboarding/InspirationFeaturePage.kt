package com.corgimemo.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.viewmodel.OnboardingViewModel

/**
 * 灵感功能介绍页
 *
 * 上半部分：功能介绍（富文本编辑、图片插入、字数统计）
 * 下半部分：创建首个灵感（标题 + 内容输入 + 保存按钮）
 * 保存成功后显示柯基 WINK 动画
 *
 * @param viewModel 引导 ViewModel
 */
@Composable
fun InspirationFeaturePage(
    viewModel: OnboardingViewModel
) {
    val createdInspirationCount by viewModel.createdInspirationCount.collectAsState()

    var inspirationTitle by remember { mutableStateOf("") }
    var inspirationContent by remember { mutableStateOf("") }
    var showSuccessAnimation by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 上半：功能介绍
        Text(
            text = "💡 灵感功能",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "富文本编辑、图片插入、字数统计",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 下半：创建首个灵感
        if (showSuccessAnimation) {
            // 保存成功，显示柯基 WINK 动画
            FrameAnimation(
                animationType = AnimationType.WINK,
                fps = 12,
                isLooping = true,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "🎉 灵感保存成功！",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "点击下一步继续",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            OutlinedTextField(
                value = inspirationTitle,
                onValueChange = { inspirationTitle = it },
                label = { Text(text = "灵感标题") },
                placeholder = { Text(text = "给你的灵感起个标题...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = inspirationContent,
                onValueChange = { inspirationContent = it },
                label = { Text(text = "灵感内容") },
                placeholder = { Text(text = "写下你的想法...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 保存按钮
            Button(
                onClick = {
                    if (inspirationTitle.isNotBlank()) {
                        viewModel.createFirstInspiration(
                            inspirationTitle,
                            inspirationContent
                        )
                        showSuccessAnimation = true
                        inspirationTitle = ""
                        inspirationContent = ""
                    }
                },
                enabled = inspirationTitle.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "保存灵感",
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "你也可以跳过，稍后在 APP 中创建",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 已创建灵感计数
        AnimatedVisibility(
            visible = createdInspirationCount > 0,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = "已创建 $createdInspirationCount 个灵感",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
