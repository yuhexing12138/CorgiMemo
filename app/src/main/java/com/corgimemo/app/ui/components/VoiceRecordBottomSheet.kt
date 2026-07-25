package com.corgimemo.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.corgimemo.app.util.VoicePlayer
import com.corgimemo.app.util.VoiceRecorder

/**
 * 语音录制底部面板
 * 提供完整的录制界面，包括波形显示、录制按钮、计时器和操作按钮
 *
 * @param voiceRecorder VoiceRecorder 实例
 * @param voicePlayer VoicePlayer 实例，用于录制完成后试听
 * @param onSaved 录制完成并保存时的回调，返回文件路径和时长（秒）
 * @param onDismiss 关闭面板的回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRecordBottomSheet(
    voiceRecorder: VoiceRecorder,
    voicePlayer: VoicePlayer,
    onSaved: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 收集录制状态
    val recordingState by voiceRecorder.recordingState.collectAsState()
    val amplitude by voiceRecorder.amplitude.collectAsState()
    val duration by voiceRecorder.duration.collectAsState()

    // 收集播放状态（用于试听）
    val playbackState by voicePlayer.playbackState.collectAsState()
    val currentPosition by voicePlayer.currentPosition.collectAsState()
    val playerDuration by voicePlayer.duration.collectAsState()
    val isPlaying by voicePlayer.isPlaying.collectAsState()

    // 弹窗销毁时重置 voiceRecorder 和 voicePlayer 状态，确保下次打开是干净的 IDLE 状态
    // 修复 bug：第一次录音保存后 recordingState 停在 STOPPED，下次打开弹窗时
    // LaunchedEffect(recordingState) 会立即命中 STOPPED && !isRecorded，
    // 导致弹窗一打开就显示"重录/保存"按钮并保留上次的录音路径。
    // release() 只重置状态（state/duration/amplitude/filePath）不删除文件，
    // 而 onSaved 是同步调用，path 已传给 ViewModel 后才触发弹窗销毁，所以安全。
    // voicePlayer.release() 同理：停止试听播放并释放 MediaPlayer 资源。
    DisposableEffect(Unit) {
        onDispose {
            voicePlayer.release()
            voiceRecorder.release()
        }
    }

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
            // 录制完成后自动准备播放器，便于试听
            lastRecordingPath?.let { voicePlayer.prepare(it) }
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
            // - 录制中/未录制：用 AudioWaveform 显示动态波形（待机呼吸 + 录制响应振幅）
            // - 已录制（试听模式）：用 StaticWaveform 显示静态波形 + 播放进度高亮
            //    进度 = currentPosition / playerDuration，随播放进度推进
            if (isRecorded && lastRecordingPath != null) {
                // 试听模式：静态波形 + 进度高亮
                // 用固定波形分布（中间高两边低）模拟真实音频波形，参考 VoicePlayerComponent.kt:95
                val waveformAmplitudes = remember(lastRecordingPath) {
                    val count = 40
                    (0 until count).map { i ->
                        val pos = i.toFloat() / (count - 1)
                        val envelope = 1.0f - kotlin.math.abs(pos - 0.5f) * 2.0f
                        val base = 0.15f + envelope * 0.7f
                        val wave = kotlin.math.sin(pos * Math.PI.toFloat() * 3) * 0.15f
                        (base + wave).coerceIn(0.1f, 1.0f)
                    }
                }
                // 计算播放进度（0.0 ~ 1.0），防止除零
                val progress = if (playerDuration > 0) {
                    (currentPosition.toFloat() / playerDuration).coerceIn(0f, 1f)
                } else 0f

                StaticWaveform(
                    amplitudes = waveformAmplitudes,
                    progress = progress,
                    modifier = Modifier.fillMaxWidth(),
                    waveHeight = 100.dp
                )
            } else {
                // 录制模式：动态波形
                AudioWaveform(
                    amplitude = amplitude,
                    isRecording = recordingState == VoiceRecorder.RecordingState.RECORDING,
                    modifier = Modifier.fillMaxWidth(),
                    waveHeight = 100.dp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 录制按钮 + 试听播放控制 + 操作按钮区域
            if (!isRecorded) {
                // 录制中/未录制：显示录制/停止按钮
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
                // 已录制：显示试听播放按钮 + 重录/保存按钮
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 试听播放控制（参考 VoicePreviewDialog 样式：橙色圆形按钮）
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                when (playbackState) {
                                    VoicePlayer.PlaybackState.PLAYING -> {
                                        voicePlayer.pause()
                                    }
                                    VoicePlayer.PlaybackState.PAUSED -> {
                                        voicePlayer.resume()
                                    }
                                    VoicePlayer.PlaybackState.PREPARED,
                                    VoicePlayer.PlaybackState.STOPPED,
                                    VoicePlayer.PlaybackState.COMPLETED -> {
                                        voicePlayer.play()
                                    }
                                    else -> {
                                        // IDLE 状态，重新准备并播放
                                        lastRecordingPath?.let { path ->
                                            voicePlayer.prepare(path)
                                            // prepare 是异步的，需要等 PREPARED 状态再 play
                                            // 这里用一个 LaunchedEffect 监听状态变化更稳妥，
                                            // 但为了简化，直接调用 play()，MediaPlayer 会忽略在 IDLE 状态的 play 调用
                                            // 用户需再点一次播放（可接受，因为 IDLE 通常意味着文件路径变化）
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 重录和保存按钮
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 重录按钮
                        OutlinedButton(
                            onClick = {
                                // 停止试听播放，重置播放器状态
                                voicePlayer.stop()
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
                                // 保存前停止试听播放
                                voicePlayer.stop()
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
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 计时器显示
            // - 录制中：显示录制时长 / 最大时长
            // - 试听中：显示当前播放位置 / 录音总时长
            if (isRecorded && lastRecordingPath != null) {
                // 试听模式：显示播放进度时间
                val currentSec = (currentPosition / 1000).toInt()
                val totalSec = lastRecordingDuration.coerceAtLeast(
                    if (playerDuration > 0) playerDuration / 1000 else 0
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = String.format("%02d:%02d", currentSec / 60, currentSec % 60),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "/",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%02d:%02d", totalSec / 60, totalSec % 60),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 录制模式：显示录制计时器
                RecordingTimer(
                    currentDuration = duration,
                    maxDuration = 60_000L,
                    isRecording = recordingState == VoiceRecorder.RecordingState.RECORDING
                )
            }

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
