package com.corgimemo.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corgimemo.app.util.VoiceRecorder

/**
 * 语音录制底部面板
 * 提供完整的录制界面，包括波形显示、录制按钮、计时器和操作按钮
 *
 * @param voiceRecorder VoiceRecorder 实例
 * @param onSaved 录制完成并保存时的回调，返回文件路径和时长（秒）
 * @param onDismiss 关闭面板的回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecordBottomSheet(
    voiceRecorder: VoiceRecorder,
    onSaved: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 收集录制状态
    val recordingState by voiceRecorder.recordingState.collectAsState()
    val amplitude by voiceRecorder.amplitude.collectAsState()
    val duration by voiceRecorder.duration.collectAsState()

    // 是否已完成录制（停止状态）
    var isRecorded by remember { mutableStateOf(false) }
    var lastRecordingPath by remember { mutableStateOf<String?>(null) }
    var lastRecordingDuration by remember { mutableIntStateOf(0) }

    // 监听录制状态变化，自动处理完成
    LaunchedEffect(recordingState) {
        if (recordingState == VoiceRecorder.RecordingState.STOPPED && !isRecorded) {
            isRecorded = true
            lastRecordingPath = voiceRecorder.filePath.value
            lastRecordingDuration = (duration / 1000).toInt()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxHeight(0.9f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Text(
                text = "🎤 语音备注",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 波形可视化区域
            AudioWaveform(
                amplitude = amplitude,
                isRecording = recordingState == VoiceRecorder.RecordingState.RECORDING,
                modifier = Modifier.fillMaxWidth(),
                waveHeight = 100.dp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 录制按钮或操作按钮区域
            if (!isRecorded) {
                // 显示录制/停止按钮
                RecordButton(
                    isRecording = recordingState == VoiceRecorder.RecordingState.RECORDING,
                    onClick = {
                        when (recordingState) {
                            VoiceRecorder.RecordingState.IDLE -> {
                                voiceRecorder.startRecording()
                            }
                            VoiceRecorder.RecordingState.RECORDING -> {
                                voiceRecorder.stopRecording()
                            }
                            else -> {}
                        }
                    }
                )
            } else {
                // 显示重录和保存按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 重录按钮
                    OutlinedButton(
                        onClick = {
                            voiceRecorder.cancelRecording()
                            isRecorded = false
                            lastRecordingPath = null
                            lastRecordingDuration = 0
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "重新录制"
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重录")
                    }

                    // 保存按钮
                    Button(
                        onClick = {
                            lastRecordingPath?.let { path ->
                                onSaved(path, lastRecordingDuration)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("保存语音备注")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 计时器显示
            RecordingTimer(
                currentDuration = duration,
                maxDuration = 30_000L,
                isRecording = recordingState == VoiceRecorder.RecordingState.RECORDING
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * 圆形录制按钮组件
 * 支持脉动动画效果
 *
 * @param isRecording 是否正在录制
 * @param onClick 点击事件回调
 */
@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
    // 脉动动画（仅录制时启用）
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")

    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(80.dp)
    ) {
        // 脉动背景圆（仅录制时显示）
        if (isRecording) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .clip(CircleShape)
                    .background(Color.Red.copy(alpha = 0.3f))
            )
        }

        // 主按钮
        FloatingActionButton(
            onClick = onClick,
            containerColor = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier.size(72.dp),
            shape = CircleShape
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = if (isRecording) "停止录制" else "开始录制",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/**
 * 录制计时器组件
 * 显示当前录制时长和最大允许时长
 *
 * @param currentDuration 当前时长（毫秒）
 * @param maxDuration 最大时长（毫秒）
 * @param isRecording 是否正在录制
 */
@Composable
private fun RecordingTimer(
    currentDuration: Long,
    maxDuration: Long,
    isRecording: Boolean
) {
    val currentSeconds = (currentDuration / 1000).toInt()
    val maxSeconds = (maxDuration / 1000).toInt()

    // 格式化为 MM:SS
    val currentTimeFormatted = String.format("%02d:%02d", currentSeconds / 60, currentSeconds % 60)
    val maxTimeFormatted = String.format("%02d:%02d", maxSeconds / 60, maxSeconds % 60)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 当前时间
        Text(
            text = currentTimeFormatted,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurface
        )

        // 分隔符
        Text(
            text = "/",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 最大时间
        Text(
            text = maxTimeFormatted,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
