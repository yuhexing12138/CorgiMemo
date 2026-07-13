package com.corgimemo.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * 待办功能介绍页
 *
 * 上半部分：功能介绍（拖拽排序、分区置顶、子任务）
 * 下半部分：创建首个待办（标题输入 + 优先级选择 + 创建按钮）
 * 创建成功后显示柯基 PROUD 动画
 *
 * @param viewModel 引导 ViewModel
 */
@Composable
fun TodoFeaturePage(
    viewModel: OnboardingViewModel
) {
    val createdTodoCount by viewModel.createdTodoCount.collectAsState()

    var todoTitle by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableIntStateOf(1) } // 默认中优先级
    var showSuccessAnimation by remember { mutableStateOf(false) }

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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // 上半：功能介绍
        Text(
            text = "📋 待办功能",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "拖拽排序、分区置顶、子任务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 下半：创建首个待办
        if (showSuccessAnimation) {
            // 创建成功，显示柯基 PROUD 动画
            FrameAnimation(
                animationType = AnimationType.PROUD,
                fps = 12,
                isLooping = true,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "🎉 待办创建成功！",
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
                value = todoTitle,
                onValueChange = { todoTitle = it },
                label = { Text(text = "创建你的第一个待办") },
                placeholder = { Text(text = "输入待办标题...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 优先级选择
            Text(
                text = "优先级：",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    onClick = { selectedPriority = 0 },
                    label = { Text("低") },
                    selected = selectedPriority == 0
                )
                FilterChip(
                    onClick = { selectedPriority = 1 },
                    label = { Text("中") },
                    selected = selectedPriority == 1
                )
                FilterChip(
                    onClick = { selectedPriority = 2 },
                    label = { Text("高") },
                    selected = selectedPriority == 2
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 创建按钮
            Button(
                onClick = {
                    if (todoTitle.isNotBlank()) {
                        viewModel.createFirstTodo(todoTitle, selectedPriority)
                        showSuccessAnimation = true
                        todoTitle = ""
                    }
                },
                enabled = todoTitle.isNotBlank(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "创建待办",
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

        // 已创建待办计数
        AnimatedVisibility(
            visible = createdTodoCount > 0,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Text(
                text = "已创建 $createdTodoCount 个待办",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        } // 关闭 Column
    } // 关闭 Box
}
