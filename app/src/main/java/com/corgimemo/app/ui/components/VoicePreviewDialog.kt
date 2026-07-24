package com.corgimemo.app.ui.components

import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.corgimemo.app.util.VoicePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

/**
 * 全屏录音预览弹窗（v2026-07-25 新增）
 *
 * 从首页待办卡片语音角标点击触发，展示该待办所有语音附件（含子任务）。
 *
 * 布局结构（与原型一致）：
 * - 顶栏：标题 + 关闭按钮
 * - 上半区（50%）：波形可视化 + 播放控制（播放/暂停按钮 + 进度条 + 时间）
 * - 下半区（50%）：录音列表（可滚动，默认最多显示约 3 条）
 * - 底栏：下载按钮 + 删除按钮（删除需二次确认）
 *
 * 技术要点：
 * - 使用独立 Dialog Window 渲染全屏（与 InspirationImageGallery 相同模式）
 * - 复用 [VoicePlayer] 管理 MediaPlayer 生命周期
 * - 复用 [StaticWaveform] 展示波形（传入生成的模拟振幅数据）
 * - DisposableEffect 确保离开时释放 MediaPlayer 资源
 *
 * @param voicePaths 语音文件绝对路径列表（已聚合父待办+子任务）
 * @param onDismiss 关闭回调
 * @param onDelete 可选的删除回调，参数为被删除的文件路径。
 *        调用方可在此更新数据库中的路径引用。传 null 时仅删除物理文件。
 */
@Composable
fun VoicePreviewDialog(
    voicePaths: List<String>,
    onDismiss: () -> Unit,
    onDelete: ((String) -> Unit)? = null
) {
    // 空列表直接关闭
    if (voicePaths.isEmpty()) {
        onDismiss()
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 可变路径列表（删除后实时更新）
    val mutablePaths = remember { mutableStateListOf(*voicePaths.toTypedArray()) }

    // VoicePlayer 实例（DisposableEffect 确保释放）
    val voicePlayer = remember { VoicePlayer(context) }
    DisposableEffect(Unit) {
        onDispose {
            voicePlayer.release()
        }
    }

    // 当前选中的录音索引
    var currentIndex by remember { mutableIntStateOf(0) }

    // 删除确认弹窗状态
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingDeleteIndex by remember { mutableIntStateOf(-1) }

    // 收集播放状态
    val playbackState by voicePlayer.playbackState.collectAsState()
    val currentPosition by voicePlayer.currentPosition.collectAsState()
    val duration by voicePlayer.duration.collectAsState()
    val isPlaying by voicePlayer.isPlaying.collectAsState()

    // 当前选中录音变化时，准备播放
    LaunchedEffect(currentIndex, mutablePaths.size) {
        if (currentIndex < mutablePaths.size) {
            val path = mutablePaths[currentIndex]
            voicePlayer.prepare(path)
        }
    }

    // 为每条录音生成确定的模拟振幅数据（基于路径 hash，保证同一文件波形一致）
    fun generateAmplitudes(path: String): List<Float> {
        val hash = path.hashCode()
        val count = 50
        return (0 until count).map { i ->
            val phase = (i.toFloat() / count) * 2 * Math.PI
            val seed = hash + i * 31
            val random = ((sin(seed.toDouble()) * 10000) % 1).toFloat().let { abs(it) }
            val base = (sin(phase * 2 + hash) * 0.3f + 0.5f).toFloat()
            (base * 0.6f + random * 0.4f).coerceIn(0.1f, 1f)
        }
    }

    // 缓存每条录音的振幅数据
    val amplitudesList = remember(mutablePaths.size) {
        mutablePaths.map { generateAmplitudes(it) }
    }

    // 下载当前录音到系统 Music 目录
    fun downloadCurrentRecording() {
        val path = mutablePaths.getOrNull(currentIndex) ?: return
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                try {
                    val sourceFile = File(path)
                    if (!sourceFile.exists()) {
                        Log.w("VoicePreviewDialog", "录音文件不存在: $path")
                        return@withContext false
                    }

                    val fileName = "CorgiMemo_Voice_${System.currentTimeMillis()}.m4a"

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        // Android 10+ 使用 MediaStore 插入公共 Music 目录
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/CorgiMemo")
                            put(MediaStore.Audio.Media.IS_PENDING, 1)
                        }
                        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                sourceFile.inputStream().use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                            values.clear()
                            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                            resolver.update(uri, values, null, null)
                            true
                        } else {
                            false
                        }
                    } else {
                        // Android 9 及以下直接复制到公共目录
                        val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "CorgiMemo")
                        if (!musicDir.exists()) musicDir.mkdirs()
                        val destFile = File(musicDir, fileName)
                        sourceFile.copyTo(destFile, overwrite = true)
                        true
                    }
                } catch (e: Exception) {
                    Log.e("VoicePreviewDialog", "下载录音失败: $path", e)
                    false
                }
            }
            // 下载结果反馈（使用 Toast 替代方案：暂时只打日志，后续可接入 Snackbar）
            Log.i("VoicePreviewDialog", if (saved) "录音已保存到 Music/CorgiMemo" else "保存失败")
        }
    }

    // 删除当前录音
    fun deleteRecording(index: Int) {
        if (index < 0 || index >= mutablePaths.size) return
        val path = mutablePaths[index]
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    File(path).delete()
                } catch (e: Exception) {
                    Log.e("VoicePreviewDialog", "删除录音文件失败: $path", e)
                }
            }
            // 通知调用方删除了哪个路径（用于数据库清理）
            onDelete?.invoke(path)
            // 从列表移除
            mutablePaths.removeAt(index)
            // 调整选中索引
            if (mutablePaths.isEmpty()) {
                onDismiss()
            } else if (index <= currentIndex) {
                currentIndex = (currentIndex - 1).coerceAtLeast(0)
            }
        }
    }

    // 全屏 Dialog（与 InspirationImageGallery 相同模式）
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        val dialogWindow = remember(view) {
            (view as? DialogWindowProvider)?.window
        }
        DisposableEffect(dialogWindow) {
            val window = dialogWindow
            if (window == null) {
                onDispose { }
            } else {
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                onDispose {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ========== 顶栏：标题 + 关闭按钮 ==========
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "录音预览",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    /**
                     * 关闭按钮
                     *
                     * v2026-07-25 统一尺寸：与 ImagePreviewScreen 的关闭按钮保持一致
                     * - 容器 40dp（触摸目标尺寸一致）
                     * - Close 图标 24dp，白色
                     * - 不加圆形背景（与图片预览页面视觉风格区分）
                     */
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // ========== 上半区：波形 + 播放控制（50% 高度）==========
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // 当前录音信息
                    Text(
                        text = "录音 ${currentIndex + 1} / ${mutablePaths.size}",
                        color = Color(0xFF999999),
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // 波形可视化
                    val progress = if (duration > 0) {
                        currentPosition.toFloat() / duration
                    } else 0f
                    val amplitudes = amplitudesList.getOrNull(currentIndex) ?: emptyList()
                    StaticWaveform(
                        amplitudes = amplitudes,
                        progress = progress,
                        activeColor = Color(0xFFFF9A5C),
                        inactiveColor = Color(0xFF444444),
                        waveHeight = 80.dp,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 时间显示
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            color = Color(0xFFCCCCCC),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatDuration(duration),
                            color = Color(0xFF999999),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 进度条
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { fraction ->
                            if (duration > 0) {
                                voicePlayer.seekTo((fraction * duration).toInt())
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF9A5C),
                            activeTrackColor = Color(0xFFFF9A5C),
                            inactiveTrackColor = Color(0xFF444444)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 播放/暂停按钮
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9A5C))
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
                                        // IDLE 状态，重新准备
                                        if (currentIndex < mutablePaths.size) {
                                            voicePlayer.prepare(mutablePaths[currentIndex])
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
                }

                // ========== 下半区：录音列表（50% 高度）==========
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = "全部录音",
                        color = Color(0xFF999999),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(mutablePaths) { index, path ->
                            val isSelected = index == currentIndex
                            val isCurrentPlaying = isSelected && isPlaying
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) Color(0xFFFF9A5C).copy(alpha = 0.15f)
                                        else Color(0xFF2A2A2A)
                                    )
                                    .clickable {
                                        if (index != currentIndex) {
                                            currentIndex = index
                                        } else {
                                            // 点击当前项时切换播放/暂停
                                            when (playbackState) {
                                                VoicePlayer.PlaybackState.PLAYING -> voicePlayer.pause()
                                                VoicePlayer.PlaybackState.PAUSED,
                                                VoicePlayer.PlaybackState.PREPARED,
                                                VoicePlayer.PlaybackState.STOPPED,
                                                VoicePlayer.PlaybackState.COMPLETED -> voicePlayer.play()
                                                else -> {}
                                            }
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFFFF9A5C) else Color(0xFF999999),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = getFileName(path),
                                            color = if (isSelected) Color.White else Color(0xFFCCCCCC),
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "录音 ${index + 1}",
                                            color = Color(0xFF777777),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                                Text(
                                    text = if (isSelected && duration > 0) formatDuration(duration) else "",
                                    color = Color(0xFF999999),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // ========== 底栏：下载 + 删除按钮 ==========
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 下载按钮
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF2A2A2A))
                            .clickable { downloadCurrentRecording() }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "下载",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "下载",
                            color = Color(0xFFCCCCCC),
                            fontSize = 14.sp
                        )
                    }
                    // 删除按钮
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF3A1A1A))
                            .clickable {
                                pendingDeleteIndex = currentIndex
                                showDeleteConfirm = true
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "删除",
                            color = Color(0xFFFF6B6B),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 删除确认弹窗
            if (showDeleteConfirm && pendingDeleteIndex >= 0) {
                AlertDialog(
                    onDismissRequest = {
                        showDeleteConfirm = false
                        pendingDeleteIndex = -1
                    },
                    title = {
                        Text(
                            text = "删除录音",
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    text = {
                        Text("确定要删除这条录音吗？删除后不可恢复。")
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val indexToDelete = pendingDeleteIndex
                                showDeleteConfirm = false
                                pendingDeleteIndex = -1
                                if (indexToDelete >= 0) {
                                    // 先停止播放再删除
                                    voicePlayer.stop()
                                    deleteRecording(indexToDelete)
                                }
                            }
                        ) {
                            Text("删除", color = Color(0xFFFF6B6B))
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDeleteConfirm = false
                                pendingDeleteIndex = -1
                            }
                        ) {
                            Text("取消")
                        }
                    }
                )
            }
        }
    }
}

/**
 * 格式化时长（毫秒 → mm:ss）
 */
private fun formatDuration(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

/**
 * 从文件路径中提取文件名
 */
private fun getFileName(path: String): String {
    val file = File(path)
    return file.nameWithoutExtension.ifBlank { "录音" }
}
