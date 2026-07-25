package com.corgimemo.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.unit.sp
import com.corgimemo.app.util.VoicePlayer
import com.corgimemo.app.util.VoiceRecorder

/**
 * 语音录制底部面板（专属工具型底部弹窗）
 *
 * 不属于操作列表/单选/搜索/长按操作四类标准弹窗，而是专用录制工具界面。
 * 包含波形可视化、录制控制、试听播放和计时器。
 * 内容区固定宽度 280dp，高度由内部组件自适应（波形 ~90dp + 控件 ~110dp + 计时器 ~30dp）。
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
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
    val recordingState by voiceRecorder.recordingState.collectAsState()
    val amplitude by voiceRecorder.amplitude.collectAsState()
    val duration by voiceRecorder.duration.collectAsState()

    val playbackState by voicePlayer.playbackState.collectAsState()
    val currentPosition by voicePlayer.currentPosition.collectAsState()
    val playerDuration by voicePlayer.duration.collectAsState()
    val isPlaying by voicePlayer.isPlaying.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            voicePlayer.release()
            voiceRecorder.release()
        }
    }

    var isRecorded by remember { mutableStateOf(false) }
    var lastRecordingPath by remember { mutableStateOf<String?>(null) }
    var lastRecordingDuration by remember { mutableIntStateOf(0) }

    LaunchedEffect(recordingState) {
        if (recordingState == VoiceRecorder.RecordingState.STOPPED && !isRecorded) {
            isRecorded = true
            lastRecordingPath = voiceRecorder.filePath.value
            lastRecordingDuration = (duration / 1000).toInt()
            lastRecordingPath?.let { voicePlayer.prepare(it) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
            DragHandle()

            /** 标题栏：左对齐标题 + 右侧圆形关闭按钮 */
            TitleBar(
                title = "语音备注",
                onDismiss = onDismiss
            )

            /** 标题下方分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(16.dp))

            /** 专属工具内容区：固定宽度 280dp，高度由内部自适应 */
            Column(
                modifier = Modifier.width(280.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 波形可视化区域（90dp）
                if (isRecorded && lastRecordingPath != null) {
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
                    val progress = if (playerDuration > 0) {
                        (currentPosition.toFloat() / playerDuration).coerceIn(0f, 1f)
                    } else 0f

                    StaticWaveform(
                        amplitudes = waveformAmplitudes,
                        progress = progress,
                        modifier = Modifier.fillMaxWidth(),
                        waveHeight = 90.dp
                    )
                } else {
                    AudioWaveform(
                        amplitude = amplitude,
                        isRecording = recordingState == VoiceRecorder.RecordingState.RECORDING,
                        modifier = Modifier.fillMaxWidth(),
                        waveHeight = 90.dp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 录制按钮 / 试听控制
                if (!isRecorded) {
                    RecordButton(
                        isRecording = recordingState == VoiceRecorder.RecordingState.RECORDING,
                        onClick = {
                            when (recordingState) {
                                VoiceRecorder.RecordingState.IDLE -> voiceRecorder.startRecording()
                                VoiceRecorder.RecordingState.RECORDING -> voiceRecorder.stopRecording()
                                else -> {}
                            }
                        }
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    when (playbackState) {
                                        VoicePlayer.PlaybackState.PLAYING -> voicePlayer.pause()
                                        VoicePlayer.PlaybackState.PAUSED -> voicePlayer.resume()
                                        VoicePlayer.PlaybackState.PREPARED,
                                        VoicePlayer.PlaybackState.STOPPED,
                                        VoicePlayer.PlaybackState.COMPLETED -> voicePlayer.play()
                                        else -> lastRecordingPath?.let { voicePlayer.prepare(it) }
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

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
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
                                Icon(Icons.Default.Mic, contentDescription = "重新录制")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("重录")
                            }

                            Button(
                                onClick = {
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

                Spacer(modifier = Modifier.height(8.dp))

                // 计时器
                if (isRecorded && lastRecordingPath != null) {
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
                    RecordingTimer(
                        currentDuration = duration,
                        maxDuration = 60_000L,
                        isRecording = recordingState == VoiceRecorder.RecordingState.RECORDING
                    )
                }
            }
        }
    }
}

/** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE0E0E0))
        )
    }
}

/** 标题栏：左对齐标题 + 右侧圆形关闭按钮 */
@Composable
private fun TitleBar(title: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2D2D2D),
            modifier = Modifier.weight(1f)
        )

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFFFF0E5))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "关闭",
                tint = Color(0xFFFF9A5C),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * 圆形录制按钮组件
 * 支持脉动动画效果
 */
@Composable
private fun RecordButton(
    isRecording: Boolean,
    onClick: () -> Unit
) {
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
 */
@Composable
private fun RecordingTimer(
    currentDuration: Long,
    maxDuration: Long,
    isRecording: Boolean
) {
    val currentSeconds = (currentDuration / 1000).toInt()
    val maxSeconds = (maxDuration / 1000).toInt()

    val currentTimeFormatted = String.format("%02d:%02d", currentSeconds / 60, currentSeconds % 60)
    val maxTimeFormatted = String.format("%02d:%02d", maxSeconds / 60, maxSeconds % 60)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = currentTimeFormatted,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = if (isRecording) Color.Red else MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "/",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = maxTimeFormatted,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
