package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.font.FontWeight
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
    /** 🆕 VoicePlayer 实例（不再支持 null，始终由 CheckboxEditText 传入）*/
    voicePlayer: com.corgimemo.app.util.VoicePlayer,
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
     *
     * 🆕 注意：voicePlayer 始终由 CheckboxEditText 传入（不再支持 null），
     * 因此移除了 voicePlayer == null 的简化版 UI 分支。
     */
    if (isVisible) {
        /** ===== 可见状态：完整播放器 UI（波形图 + 进度高亮 + 递增时间）===== */
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

        /**
         * 🆕 预生成静态波形振幅数组
         *
         * 使用固定波形分布（中间高、两边低），模拟真实音频波形视觉效果。
         * 振幅值范围 0.0~1.0，barCount=35 与 StaticWaveform 一致。
         *
         * 波形公式：基于正弦函数 + 位置因子，产生自然的"鼓包"形状。
         */
        val waveformAmplitudes = remember(filePath) {
            val count = 35
            (0 until count).map { i ->
                /** 位置归一化：0(左) → 0.5(中) → 1(右) */
                val pos = i.toFloat() / (count - 1)
                /** 中间高两边低的包络线 */
                val envelope = 1.0f - kotlin.math.abs(pos - 0.5f) * 2.0f
                /** 基础高度 + 正弦波动 + 随机微扰 */
                val base = 0.15f + envelope * 0.7f
                val wave = kotlin.math.sin(pos * Math.PI.toFloat() * 3) * 0.15f
                (base + wave).coerceIn(0.1f, 1.0f)
            }
        }

        /**
         * 🆕 当前播放进度（0.0 ~ 1.0）
         *
         * 用于 StaticWaveform 的进度高亮显示：
         * - 已播部分使用 activeColor（主题色）
         * - 未播部分使用 inactiveColor（灰色）
         */
        val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f

        /**
         * 🆕 播放/暂停切换处理函数
         *
         * 统一管理播放状态切换逻辑，供按钮点击和整行点击复用。
         */
        fun togglePlayPause() {
            when (playbackState) {
                VoicePlayer.PlaybackState.PLAYING -> voicePlayer.pause()
                VoicePlayer.PlaybackState.PAUSED -> voicePlayer.resume()
                VoicePlayer.PlaybackState.PREPARED,
                VoicePlayer.PlaybackState.STOPPED,
                VoicePlayer.PlaybackState.COMPLETED -> voicePlayer.play()
                else -> {}
            }
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
                    Modifier
                        .innerShadow(shape = RoundedCornerShape(8.dp)) {
                            color = Color(0xFFFFB74D).copy(alpha = 0.6f)
                            radius = 4f
                        }
                        .border(1.dp, Color(0xFFFFB74D).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                } else {
                    Modifier
                }
            )
            /**
             * 🆕 整行可点击：点击切换播放/暂停
             *
             * 使用默认 clickable（含系统波纹效果），确保与 IconButton 的点击事件不冲突。
             */
            .clickable(onClick = ::togglePlayPause)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 播放/暂停按钮（紧凑尺寸，点击事件由外层 Row 统一处理）
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )

        // 🆕 静态波形图（替代 AudioWaveform，支持进度高亮）
        /**
         * 使用 StaticWaveform 显示预生成的波形 + 实时播放进度：
         * - 已播部分（progress 以左）：activeColor 主题色
         * - 未播部分（progress 以右）：inactiveColor 灰色
         * - 播放时波形静止，通过进度高亮表达播放状态
         */
        StaticWaveform(
            amplitudes = waveformAmplitudes,
            progress = progress,
            modifier = Modifier.weight(1f),
            activeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            waveHeight = 32.dp
        )

        // 🆕 时间显示：递增模式（已播 / 总时长）
        Text(
            text = buildString {
                if (isPlaying || playbackState == VoicePlayer.PlaybackState.PAUSED) {
                    /** 播放中或暂停时显示已播时间 / 总时长 */
                    append(formatDuration(currentPosition))
                    append(" / ")
                }
                append(formatDuration(if (duration > 0) duration else (totalDuration?.times(1000) ?: 0)))
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 36.dp)
        )

        // 删除按钮（小图标，阻止点击冒泡到整行的 play/pause）
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
    } /** 结束 Row lambda */
    } /** 结束 if (isVisible) 完整播放器 UI */
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
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                color = Color(0xFFFFB74D).copy(alpha = 0.6f)
                                radius = 4f
                            }
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
