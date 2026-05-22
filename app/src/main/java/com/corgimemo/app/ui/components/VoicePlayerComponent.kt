package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.corgimemo.app.util.VoicePlayer

/**
 * 语音播放器组件
 * 提供完整的播放控制界面，包括播放/暂停按钮、进度条、时长显示和删除功能
 *
 * @param voicePlayer VoicePlayer 实例
 * @param filePath 音频文件路径
 * @param totalDuration 音频总时长（秒），用于显示
 * @param onDelete 删除语音备注的回调
 * @param modifier 修饰符
 */
@Composable
fun VoicePlayerComponent(
    voicePlayer: VoicePlayer,
    filePath: String,
    totalDuration: Int? = null,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 收集播放状态
    val playbackState by voicePlayer.playbackState.collectAsState()
    val currentPosition by voicePlayer.currentPosition.collectAsState()
    val duration by voicePlayer.duration.collectAsState()
    val isPlaying by voicePlayer.isPlaying.collectAsState()

    // 是否显示删除确认对话框
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 首次加载时准备音频文件
    LaunchedEffect(filePath) {
        voicePlayer.prepare(filePath)
    }

    // 清理资源
    DisposableEffect(Unit) {
        onDispose {
            // 不在这里释放，因为用户可能返回后还要继续播放
            // 由调用方管理生命周期
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题行：图标 + 标题 + 删除按钮
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = "🎤", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "语音备注",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // 删除按钮
            IconButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除语音备注",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 控制区域：播放按钮 + 进度条 + 时长
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 播放/暂停按钮
            PlayPauseButton(
                isPlaying = isPlaying,
                onClick = {
                    when (playbackState) {
                        VoicePlayer.PlaybackState.PLAYING -> voicePlayer.pause()
                        VoicePlayer.PlaybackState.PAUSED -> voicePlayer.resume()
                        VoicePlayer.PlaybackState.PREPARED,
                        VoicePlayer.PlaybackState.STOPPED,
                        VoicePlayer.PlaybackState.COMPLETED -> voicePlayer.play()
                        else -> {}
                    }
                }
            )

            // 进度条和时长
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 可拖动进度条
                ProgressBar(
                    currentPosition = currentPosition.toLong(),
                    duration = duration.toLong(),
                    onSeekTo = { position ->
                        voicePlayer.seekTo(position.toInt())
                    },
                    enabled = playbackState != VoicePlayer.PlaybackState.IDLE
                )

                // 时长显示
                DurationDisplay(
                    currentPosition = currentPosition,
                    duration = if (duration > 0) duration else (totalDuration?.times(1000) ?: 0)
                )
            }
        }

        // 删除确认对话框
        if (showDeleteConfirm) {
            DeleteConfirmationDialog(
                onConfirm = {
                    voicePlayer.stop()
                    voicePlayer.release()
                    onDelete()
                    showDeleteConfirm = false
                },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
}

/**
 * 播放/暂停按钮组件
 *
 * @param isPlaying 是否正在播放
 * @param onClick 点击事件回调
 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,  // 暖橙色
        contentColor = Color.White,
        modifier = Modifier.size(48.dp),
        shape = CircleShape
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * 可拖动的进度条组件
 *
 * @param currentPosition 当前位置（毫秒）
 * @param duration 总时长（毫秒）
 * @param onSeekTo 跳转位置回调
 * @param enabled 是否启用交互
 */
@Composable
private fun ProgressBar(
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    enabled: Boolean = true
) {
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    // 更新滑块位置（非拖动时）
    LaunchedEffect(currentPosition, duration) {
        if (!isDragging && duration > 0) {
            sliderPosition = currentPosition.toFloat() / duration.toFloat()
        }
    }

    Slider(
        value = sliderPosition.coerceIn(0f, 1f),
        onValueChange = { value ->
            isDragging = true
            sliderPosition = value
        },
        onValueChangeFinished = {
            if (duration > 0) {
                onSeekTo((sliderPosition * duration).toLong())
            }
            isDragging = false
        },
        enabled = enabled && duration > 0,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * 时长显示组件
 *
 * @param当前位置 当前位置（毫秒）
 * @param duration 总时长（毫秒）
 */
@Composable
private fun DurationDisplay(
    currentPosition: Int,
    duration: Int
) {
    val currentFormatted = formatDuration(currentPosition)
    val totalFormatted = formatDuration(duration)

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = currentFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = totalFormatted,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 删除确认对话框
 *
 * @param onConfirm 确认删除回调
 * @param onDismiss 取消回调
 */
@Composable
private fun DeleteConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除语音备注") },
        text = { Text("确定要删除这条语音备注吗？此操作无法撤销。") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 格式化时长为 MM:SS 格式
 *
 * @param millis 时长（毫秒）
 * @return 格式化的时间字符串
 */
private fun formatDuration(millis: Int): String {
    val seconds = millis / 1000
    return String.format("%02d:%02d", seconds / 60, seconds % 60)
}
