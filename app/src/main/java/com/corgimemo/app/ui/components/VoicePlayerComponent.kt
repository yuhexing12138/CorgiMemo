package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.corgimemo.app.util.VoicePlayer

/**
 * 语音播放器组件（紧凑内联模式）
 *
 * 单行布局：[播放按钮] [进度条] [删除] [时长]
 * 高度约 44dp，适合嵌入内容编辑区域内部使用。
 *
 * **Compose 1.9 onVisibilityChanged 懒加载策略**:
 * - isVisible=true  → 渲染完整播放器 UI（含 MediaPlayer 准备 + 播放控制）
 * - isVisible=false → 显示轻量占位符（仅图标+时长，不初始化 MediaPlayer）
 *
 * 性能收益：
 * - 屏幕外语音块不占用 MediaPlayer 实例内存
 * - 滚动离开视口时自动释放音频资源
 *
 * @param voicePlayer VoicePlayer 实例
 * @param filePath 音频文件路径
 * @param totalDuration 音频总时长（秒），用于显示
 * @param onDelete 删除语音备注的回调
 * @param isHighlighted 是否处于待删除高亮状态（暖黄色边框）
 * @param isVisible Compose 1.9 onVisibilityChanged：是否在视口内（默认 true）
 * @param modifier 修饰符
 */
@Composable
fun VoicePlayerComponent(
    voicePlayer: VoicePlayer,
    filePath: String,
    totalDuration: Int? = null,
    onDelete: () -> Unit,
    isHighlighted: Boolean = false,
    isVisible: Boolean = true, /** Compose 1.9 onVisibilityChanged：是否在视口内 */
    modifier: Modifier = Modifier
) {
    /**
     * Compose 1.9 onVisibilityChanged 懒加载策略：
     *
     * isVisible=true  → 渲染完整播放器（MediaPlayer 准备 + 播放控制 + 进度条）
     * isVisible=false → 显示轻量占位符（麦克风图标 + 时长，不初始化 MediaPlayer）
     *
     * 性能收益：
     * - 屏幕外语音块不占用 MediaPlayer 实例（每个约 1-5MB 内存）
     * - 10条语音备注仅准备可见的 2-3 条
     */
    if (isVisible) {
        /** ===== 可见状态：完整播放器 UI ===== */
        // 收集播放状态
        val playbackState by voicePlayer.playbackState.collectAsState()
        val currentPosition by voicePlayer.currentPosition.collectAsState()
        val duration by voicePlayer.duration.collectAsState()
        val isPlaying by voicePlayer.isPlaying.collectAsState()

        // 是否显示删除确认对话框
        var showDeleteConfirm by remember { mutableStateOf(false) }

        // 仅在可见时才准备音频文件（懒加载核心）
        LaunchedEffect(filePath) {
            voicePlayer.prepare(filePath)
        }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = if (isHighlighted) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(8.dp)
            )
            .then(
                if (isHighlighted) {
                    /** Compose 1.9 内阴影：替代硬边框，提供柔和的暖色发光高亮效果 */
                    Modifier
                        .innerShadow(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFB74D).copy(alpha = 0.6f),
                            blur = 4.dp,
                            offsetY = 1.dp
                        )
                        /** 保留细边框作为视觉锚点 */
                        .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 播放/暂停按钮（紧凑尺寸）
        IconButton(
            onClick = {
                when (playbackState) {
                    VoicePlayer.PlaybackState.PLAYING -> voicePlayer.pause()
                    VoicePlayer.PlaybackState.PAUSED -> voicePlayer.resume()
                    VoicePlayer.PlaybackState.PREPARED,
                    VoicePlayer.PlaybackState.STOPPED,
                    VoicePlayer.PlaybackState.COMPLETED -> voicePlayer.play()
                    else -> {}
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }

        // 进度条（占据剩余空间）
        Slider(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onValueChange = { position ->
                if (duration > 0) voicePlayer.seekTo((position * duration).toInt())
            },
            enabled = playbackState != VoicePlayer.PlaybackState.IDLE && duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier.weight(1f)
        )

        // 删除按钮（小图标）
        IconButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        // 时长显示
        Text(
            text = formatDuration(if (duration > 0) duration else (totalDuration?.times(1000) ?: 0)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 36.dp)
        )

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
    } /** ===== 结束 if (isVisible) 完整播放器 UI ===== */
    else {
        /**
         * 不可见状态：轻量占位符
         *
         * 保持与完整播放器相近的视觉尺寸（避免布局抖动），
         * 但不初始化 MediaPlayer、不订阅任何 StateFlow。
         *
         * 设计说明：
         * - 使用麦克风图标 + 时长文字，让用户知道这是语音块
         * - 背景色与可见状态的 surfaceContainerLowest 一致
         * - 高亮状态同样支持（删除操作需要视觉反馈）
         */
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(
                    color = if (isHighlighted) Color(0xFFFFF8E1) else MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(8.dp)
                )
                .then(
                    if (isHighlighted) {
                        Modifier
                            .innerShadow(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFB74D).copy(alpha = 0.6f),
                                blur = 4.dp,
                                offsetY = 1.dp
                            )
                            .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 麦克风图标（替代播放按钮）
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "语音备注",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )

            // 占位文字提示
            Text(
                text = "语音备注",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            )

            // 时长显示（保持信息可见）
            Text(
                text = formatDuration(totalDuration?.times(1000) ?: 0),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.widthIn(min = 36.dp)
            )
        }
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
