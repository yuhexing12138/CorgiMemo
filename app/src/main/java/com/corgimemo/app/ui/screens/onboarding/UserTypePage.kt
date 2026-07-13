package com.corgimemo.app.ui.screens.onboarding

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.model.UserType
import com.corgimemo.app.viewmodel.OnboardingViewModel

/**
 * 身份选择页
 *
 * 3 卡片横排：学生（可选）、上班族（灰色，后续版本）、老人模式（灰色，后续版本）
 * 仅学生身份可选，其他点击后显示 Toast 提示
 */
@Composable
fun UserTypePage(
    viewModel: OnboardingViewModel
) {
    val selectedType by viewModel.selectedUserType.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "选择你的身份",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "我们将为你提供个性化体验",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // 3 卡片横排
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 学生卡片（可选）
            UserTypeCard(
                emoji = "🎓",
                title = "学生",
                subtitle = "学习、作业、考试",
                isSelected = selectedType == UserType.STUDENT,
                isEnabled = true,
                onClick = { viewModel.setUserType(UserType.STUDENT) },
                modifier = Modifier.weight(1f)
            )

            // 上班族卡片（灰色，后续版本）
            UserTypeCard(
                emoji = "💼",
                title = "上班族",
                subtitle = "🔒 后续版本实现",
                isSelected = false,
                isEnabled = false,
                onClick = {
                    Toast.makeText(context, "此模式将在后续版本实现", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )

            // 老人模式卡片（灰色，后续版本）
            UserTypeCard(
                emoji = "👴",
                title = "老人模式",
                subtitle = "🔒 后续版本实现",
                isSelected = false,
                isEnabled = false,
                onClick = {
                    Toast.makeText(context, "此模式将在后续版本实现", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (selectedType == null) {
            Text(
                text = "请选择一个身份继续",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 身份选择卡片
 *
 * @param emoji 表情图标
 * @param title 身份标题
 * @param subtitle 身份描述
 * @param isSelected 是否被选中
 * @param isEnabled 是否可用（灰色显示不可用项）
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun UserTypeCard(
    emoji: String,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(200.dp)
            .clickable(onClick = onClick)
            .alpha(if (isEnabled) 1f else 0.5f),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        },
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        )
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
                fontSize = 40.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )

            if (isSelected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
