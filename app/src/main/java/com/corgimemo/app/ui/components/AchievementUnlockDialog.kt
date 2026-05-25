package com.corgimemo.app.ui.components

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.corgimemo.app.data.model.Achievement
import com.corgimemo.app.data.model.AchievementStage
import com.corgimemo.app.data.model.isMajorAchievement
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementUnlockDialog(
    achievement: Achievement,
    onDismiss: () -> Unit,
    isSoundEnabled: Boolean = false
) {
    val isMajor = remember(achievement.id) { achievement.isMajorAchievement() }
    val autoDismissDelayMs = if (isMajor) 5000L else 3000L
    val context = LocalContext.current

    var showCorgiJump by remember { mutableStateOf(isMajor) }

    if (isMajor && isSoundEnabled) {
        LaunchedEffect(Unit) {
            playUnlockSound(context)
        }
    }

    LaunchedEffect(Unit) {
        delay(autoDismissDelayMs)
        showCorgiJump = false
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        ) {
            ConfettiOverlay(
                modifier = Modifier.fillMaxSize(),
                durationMs = autoDismissDelayMs
            )

            if (showCorgiJump) {
                CorgiJumpAnimation(
                    modifier = Modifier.fillMaxSize(),
                    durationMs = autoDismissDelayMs,
                    stage = achievement.stage
                )
            }

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = achievement.icon,
                            fontSize = 64.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isMajor && achievement.storyTitle.isNotEmpty()) {
                            Text(
                                text = "「${achievement.storyTitle}」",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = getMajorTitleColor(achievement.stage),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                        } else {
                            Text(
                                text = achievement.name,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = achievement.description,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "「${achievement.story}」",
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFFF97316),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        if (isMajor && achievement.unlockDialog.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .background(
                                        color = getDialogBubbleColor(achievement.stage).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = achievement.unlockDialog,
                                    fontSize = 12.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = getDialogBubbleColor(achievement.stage),
                                    textAlign = TextAlign.Center,
                                    lineHeight = 18.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                showCorgiJump = false
                                onDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (isMajor) "🎉 太棒了！" else "🎉 继续加油！",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getMajorTitleColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFF64748B)
        AchievementStage.GROWTH -> Color(0xFF10B981)
        AchievementStage.LEAP -> Color(0xFF2563EB)
        AchievementStage.PEAK -> Color(0xFFEA580C)
    }
}

private fun getDialogBubbleColor(stage: AchievementStage): Color {
    return when (stage) {
        AchievementStage.BEGINNER -> Color(0xFF64748B)
        AchievementStage.GROWTH -> Color(0xFF059669)
        AchievementStage.LEAP -> Color(0xFF1D4ED8)
        AchievementStage.PEAK -> Color(0xFFEA580C)
    }
}

private fun playUnlockSound(context: Context) {
    try {
        val resId = context.resources.getIdentifier(
            "achievement_unlock",
            "raw",
            context.packageName
        )
        if (resId != 0) {
            val mediaPlayer = MediaPlayer.create(context, resId)
            mediaPlayer?.let { player ->
                player.setOnCompletionListener { it.release() }
                player.start()
            }
        }
    } catch (_: Exception) {
    }
}