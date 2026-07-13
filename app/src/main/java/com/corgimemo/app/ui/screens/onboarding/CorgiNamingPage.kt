package com.corgimemo.app.ui.screens.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.model.UserType
import com.corgimemo.app.viewmodel.OnboardingViewModel

/**
 * 柯基命名页
 *
 * 展示柯基坐立帧动画预览，根据输入状态切换动画反应：
 * - 默认（空输入）：SIT（坐立）
 * - 输入中（1-6字符）：TILT（歪头）
 * - 名字有效（1-8字符）：WINK（眨眼）
 * - 接近上限（7-8字符）：WORRY（担心）
 *
 * 提供命名建议 Chip 列表（学生版）
 */
@Composable
fun CorgiNamingPage(
    viewModel: OnboardingViewModel
) {
    val name by viewModel.corgiName.collectAsState()
    val selectedUserType by viewModel.selectedUserType.collectAsState()
    val isValidName = viewModel.isValidName()

    // 根据输入状态计算动画类型
    val animationType by remember {
        derivedStateOf {
            when {
                name.isEmpty() -> AnimationType.SIT
                name.length in 7..8 -> AnimationType.WORRY
                name.length in 1..6 -> AnimationType.TILT
                else -> AnimationType.SIT
            }
        }
    }

    // 名字有效时显示 WINK 动画（短暂反应）
    val showWinkReaction = isValidName && name.length <= 6

    val suggestions = getNameSuggestions(selectedUserType)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 柯基帧动画预览
        FrameAnimation(
            animationType = if (showWinkReaction) AnimationType.WINK else animationType,
            fps = 12,
            isLooping = true,
            modifier = Modifier.size(100.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "给你的柯基起个名字吧！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "它将陪伴你完成每一个任务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.setCorgiName(it) },
                label = { Text(text = "柯基名字") },
                placeholder = { Text(text = "请输入名字（1-8个字符）") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { }
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    Text(
                        text = "${name.length}/8",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (name.length >= 7) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                isError = name.length >= 7
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 名字预览
            AnimatedVisibility(
                visible = name.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "你的柯基名字是：${name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // 命名建议 Chip 列表
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "💡 试试这些名字：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                NameSuggestionChips(
                    suggestions = suggestions,
                    onSelected = { suggestion ->
                        viewModel.setCorgiName(suggestion)
                    }
                )
            }
        }
    }
}

/**
 * 根据用户身份类型返回对应的命名建议列表
 *
 * @param userType 用户身份（学生/上班族）
 * @return 命名建议字符串列表
 */
private fun getNameSuggestions(userType: UserType?): List<String> {
    return when (userType) {
        UserType.STUDENT -> listOf(
            "学霸", "考神", "小团子", "豆豆", "书虫", "小笔",
            "墨水", "卷王", "小博士"
        )
        else -> listOf(
            "旺财", "小柴", "阿橘", "布丁", "发财", "奶茶",
            "摸鱼", "加薪", "周五", "小老板"
        )
    }
}

/**
 * 命名建议 Chip 列表组件
 * 使用 FlowRow 实现自动换行布局
 *
 * @param suggestions 建议名称列表
 * @param onSelected 用户点击某个建议时的回调
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameSuggestionChips(
    suggestions: List<String>,
    onSelected: (String) -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        suggestions.forEach { suggestion ->
            FilterChip(
                onClick = { onSelected(suggestion) },
                label = {
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                selected = false
            )
        }
    }
}
