package com.corgimemo.app.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.model.UserType
import com.corgimemo.app.viewmodel.OnboardingViewModel

/**
 * 柯基命名页
 *
 * 让用户为自己的柯基助手起一个名字，
 * 根据用户身份（上班族/学生）提供不同的命名建议
 */
@Composable
fun CorgiNamingPage(
    viewModel: OnboardingViewModel
) {
    val name by viewModel.corgiName.collectAsState()
    /** 获取用户选择的身份类型，用于显示对应的命名建议 */
    val selectedUserType by viewModel.selectedUserType.collectAsState()
    val isValidName = viewModel.isValidName()

    /** 根据身份获取命名建议列表 */
    val suggestions = getNameSuggestions(selectedUserType)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🐕",
            fontSize = 80.sp
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
                        color = if (name.isNotEmpty() && !isValidName) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                },
                isError = name.isNotEmpty() && !isValidName
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (name.isNotEmpty()) {
                Text(
                    text = "你的柯基名字是：${name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            /** 基于身份的命名建议 Chip 列表 */
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "💡 试试这些名字：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                /** 使用 FlowRow 自动换行展示建议 Chip */
                NameSuggestionChips(
                    suggestions = suggestions,
                    onSelected = { suggestion ->
                        /** 点击建议后自动填入输入框 */
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
 * @param userType 用户身份（上班族/学生），默认为上班族
 * @return 命名建议字符串列表
 */
private fun getNameSuggestions(userType: UserType?): List<String> {
    return when (userType) {
        UserType.STUDENT -> listOf(
            "学霸", "考神", "小团子", "豆豆", "书虫", "小笔",
            "墨水", "卷王", "学渣逆袭", "小博士"
        )
        else -> listOf( // 上班族（WORKER 或 null）
            "旺财", "小柴", "阿橘", "布丁", "发财", "奶茶",
            "摸鱼", "加薪", "周五", "小老板"
        )
    }
}

/**
 * 命名建议 Chip 列表组件
 * 使用 FlowRow 实现自动换行布局，
 * 用户点击任意建议即可快速填入名字
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
