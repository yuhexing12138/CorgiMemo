package com.corgimemo.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.corgimemo.app.animation.AnimationType
import com.corgimemo.app.animation.FrameAnimation
import com.corgimemo.app.data.model.TodoTemplate

/**
 * 首次引导步骤枚举
 */
enum class GuideStep {
    /** 柯基自我介绍 */
    INTRO,
    /** 高亮 FAB 按钮 */
    FAB_HIGHLIGHT,
    /** 语音输入演示（可选） */
    VOICE_DEMO,
    /** 模板推荐 */
    TEMPLATE_REC
}

/**
 * 首次使用引导覆盖层组件
 * 在首次打开 APP 且待办为空时显示 4 步引导流程
 *
 * @param onGuideCompleted 引导完成时的回调
 * @param onFabClicked 用户点击 FAB 时的回调（用于 Step 2）
 * @param onTemplateSelected 用户选择模板时的回调（用于 Step 4）
 * @param abGroup A/B 测试组别（"A" 或 "B"），影响显示的文案
 * @param modifier 修饰符
 */
@Composable
fun FirstTimeGuideOverlay(
    onGuideCompleted: () -> Unit,
    onFabClicked: () -> Unit = {},
    onTemplateSelected: (TodoTemplate) -> Unit = {},
    abGroup: String = "A",
    modifier: Modifier = Modifier
) {
    /** 当前引导步骤 */
    var currentStep by remember { mutableIntStateOf(0) }

    val steps = GuideStep.entries

    Dialog(
        onDismissRequest = { /* 不允许通过外部点击关闭 */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 })
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (steps[currentStep]) {
                        GuideStep.INTRO -> {
                            GuideIntroContent(abGroup = abGroup)
                        }
                        GuideStep.FAB_HIGHLIGHT -> {
                            GuideFabHighlightContent(onFabClicked)
                        }
                        GuideStep.VOICE_DEMO -> {
                            GuideVoiceDemoContent()
                        }
                        GuideStep.TEMPLATE_REC -> {
                            GuideTemplateRecommendContent(
                                onTemplateSelected = onTemplateSelected
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    /** 底部按钮区域 */
                    GuideStepButtons(
                        currentStep = currentStep,
                        totalSteps = steps.size,
                        onNext = {
                            if (currentStep < steps.size - 1) {
                                currentStep++
                            } else {
                                onGuideCompleted()
                            }
                        },
                        onSkip = onGuideCompleted
                    )
                }
            }
        }
    }
}

/**
 * 引导步骤 1：柯基自我介绍内容
 *
 * @param abGroup A/B 测试组别
 */
@Composable
private fun GuideIntroContent(abGroup: String = "A") {
    /** 根据 A/B 组别选择不同的介绍文案 */
    val introTitle = if (abGroup == "B") "欢迎！让我们一起管理待办~" else "嗨！我是你的待办小助手~"
    val introSubtext = if (abGroup == "B") "高效生活，从今天开始" else "我会帮你记录待办、提醒任务、陪你养成好习惯"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** 柯基动画 */
        FrameAnimation(
            animationType = AnimationType.WAG,
            fps = 8,
            isLooping = true,
            modifier = Modifier.size(160.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        /** 介绍文字卡片 */
        androidx.compose.material3.Card(
            shape = RoundedCornerShape(20.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "👋 嗨！",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = introTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = introSubtext,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

/**
 * 引导步骤 2：高亮 FAB 按钮
 */
@Composable
private fun GuideFabHighlightContent(onFabClicked: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** 说明文字 */
        Text(
            text = "📝 添加你的第一个待办",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "点击右下角的 + 按钮\n开始创建待办事项吧！",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(40.dp))

        /** 模拟 FAB 按钮位置指示器 */
        Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 40.dp, bottom = 80.dp)
        ) {
            /** 脉冲圆圈动画效果 */
            val pulseAlpha = remember { androidx.compose.animation.core.Animatable(1f) }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    pulseAlpha.animateTo(
                        targetValue = 0.3f,
                        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                            animation = androidx.compose.animation.core.tween(
                                durationMillis = 1000,
                                easing = androidx.compose.animation.core.LinearEasing
                            ),
                            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                        )
                    )
                }
            }

            /** 外圈脉冲 */
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha.value))
            )

            /** 内圈 FAB 模拟 */
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onFabClicked() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

/**
 * 引导步骤 3：语音输入演示（可选）
 */
@Composable
private fun GuideVoiceDemoContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** 语音图标动画 */
        Text(
            text = "🎤",
            fontSize = 64.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        /** 说明文字卡片 */
        androidx.compose.material3.Card(
            shape = RoundedCornerShape(20.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "💡 试试语音输入",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "对着手机说：\n「明天开会」\n\n柯基会自动帮你创建待办哦~",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

/**
 * 引导步骤 4：模板推荐
 */
@Composable
private fun GuideTemplateRecommendContent(
    onTemplateSelected: (TodoTemplate) -> Unit
) {
    val templates = com.corgimemo.app.data.model.TemplateData.templates

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** 标题文字 */
        Text(
            text = "🚀 快速开始",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "选择一个模板，一键创建多个待办",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        /** 模板选择网格 */
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.height(280.dp)
        ) {
            items(templates.size) { index ->
                val template = templates[index]
                GuideTemplateCard(
                    template = template,
                    onClick = { onTemplateSelected(template) }
                )
            }
        }
    }
}

/**
 * 引导中的模板卡片（简化版）
 */
@Composable
private fun GuideTemplateCard(
    template: TodoTemplate,
    onClick: () -> Unit
) {
    androidx.compose.material3.Card(
        shape = RoundedCornerShape(16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = template.icon, fontSize = 36.sp)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = template.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${template.todos.size} 个待办",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 引导步骤底部按钮组
 *
 * @param currentStep 当前步骤索引
 * @param totalSteps 总步骤数
 * @param onNext 下一步回调
 * @param onSkip 跳过回调
 */
@Composable
private fun GuideStepButtons(
    currentStep: Int,
    totalSteps: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        /** 跳过按钮 */
        OutlinedButton(
            onClick = onSkip,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color.White.copy(alpha = 0.8f)
            )
        ) {
            Text(
                text = if (currentStep == totalSteps - 1) "跳过" else "跳过引导",
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        /** 下一步/完成按钮 */
        Button(
            onClick = onNext,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = if (currentStep == totalSteps - 1) "开始使用" else "下一步",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
