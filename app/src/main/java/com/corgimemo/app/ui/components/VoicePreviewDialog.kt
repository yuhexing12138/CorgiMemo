package com.corgimemo.app.ui.components

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.corgimemo.app.util.VoicePlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * - 上半区（50%）：波形可视化 + 播放控制（左箭头 + 播放/暂停按钮 + 右箭头 + 进度条 + 时间）
 * - 下半区（50%）：录音列表（可滚动，默认最多显示约 3 条）
 * - 底栏：删除按钮 + 下载按钮（删除需二次确认）
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

    // Snackbar 状态：用于下载成功/失败反馈（参考 InspirationImageGallery 实现）
    val snackbarHostState = remember { SnackbarHostState() }

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

    // 下载当前录音到系统 Music 目录（参考 InspirationImageGallery.downloadCurrentImage）
    // IO 线程执行避免主线程阻塞，Snackbar 反馈结果（项目规则：禁用 Toast，统一使用 AppSnackbarHost）
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
            // 下载结果反馈：使用 Snackbar 替代 Log 输出（与图片预览页面行为一致）
            snackbarHostState.showSnackbar(
                if (saved) "已保存到 Music/CorgiMemo" else "保存失败"
            )
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

        /**
         * 把 Dialog 窗口设置为「全幅铺满」：忽略系统栏装饰 + 允许画进挖孔区 + 100% 背景遮罩。
         *
         * 与图片附件页 `InspirationImageGallery` 共用同一套策略与实测结论：
         * Compose Dialog 用 `Theme.Dialog`（`windowIsFloating = true`）属于 floating window，
         * WindowManager 会把窗口贴合进系统栏 / 挖孔安全区之内（横屏时左侧露出一条宿主页面）。
         * 实测该 ROM **无视** `FLAG_LAYOUT_IN_SCREEN` 与挖孔模式，且窗口属性会在 `show()`
         * 时被主题默认值覆盖（`backgroundDimAmount = 0.6`）⇒ 需在多个时机重复施加，
         * 并最终由 100% dim 遮罩 + 宿主侧黑幕兜底。
         */
        fun applyFullBleedWindow(window: Window) {
            window.attributes = window.attributes.apply {
                flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
                /** 100% 变暗：即使窗口没能铺满，露出的宿主区域也会被压成纯黑 */
                dimAmount = 1f
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
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
                /**
                 * 本页顶栏原本只有 12dp padding、且**没有任何 insets 避让**，之所以"看起来是对的"，
                 * 纯粹是因为窗口被下推了一个状态栏高度——歪打正着、不可控。
                 * 现改为让窗口**真正铺满**，再由下方按 insets 显式避让，
                 * 并与图片附件页共用同一套「窗口偏移补偿」策略。
                 */
                applyFullBleedWindow(window)
                /**
                 * v2026-09-10 统一为「**始终显示系统栏**」（与图片附件页竖屏策略一致，用户决策）：
                 *
                 * 原实现进入即 `hide(systemBars())` 做沉浸，但部分 ROM 在 hide() 后**仍绘制**
                 * 状态栏/手势条，而窗口上报的 insets 已归零——「看得见的栏 + 为零的 insets」
                 * 会让顶部/底部按 insets 做的避让全部失效。
                 * 改为始终显示后 insets 永远真实，避让值可预测；
                 * 页面是深色背景，故配浅色系统栏图标保证可读。
                 */
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = false
                onDispose {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        /**
         * 系统栏安全边距 + 窗口偏移实测（v2026-09-10 与图片附件页统一）。
         *
         * - insets 一律从 **Activity 主窗口** 读：Compose Dialog 是子窗口，
         *   部分 ROM 派发给它的 insets 为 0，不可靠；
         * - 同时实测窗口在屏幕中的真实偏移：窗口若已被系统贴合进安全区
         *   （`FLAG_LAYOUT_IN_SCREEN` 未生效），偏移量恰好等于系统栏尺寸，
         *   此时页面**不需要**再自行避让，否则就成了双重避让。
         */
        val activity = LocalContext.current.findActivity()
        val density = LocalDensity.current
        val configuration = LocalConfiguration.current
        val dialogRootView = LocalView.current
        var statusBarTopPx by remember { mutableStateOf(0) }
        var navBarBottomPx by remember { mutableStateOf(0) }
        var windowLeftPx by remember { mutableFloatStateOf(0f) }
        var windowTopPx by remember { mutableFloatStateOf(0f) }

        DisposableEffect(activity, configuration) {
            activity?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }?.let { root ->
                val bars = root.getInsets(WindowInsetsCompat.Type.systemBars())
                /** 系统栏被 hide 时 insets 归零，用「> 0」守卫保留上一次的真实高度 */
                if (bars.top > 0) statusBarTopPx = bars.top
                if (bars.bottom > 0) navBarBottomPx = bars.bottom
            }
            val location = IntArray(2)
            dialogRootView.getLocationOnScreen(location)
            windowLeftPx = location[0].toFloat()
            windowTopPx = location[1].toFloat()
            onDispose { }
        }

        val statusBarPadding = with(density) { statusBarTopPx.toDp() }
        val navBarPadding = with(density) { navBarBottomPx.toDp() }
        /**
         * 窗口是否已被系统贴合进安全区（浮窗的隐式 inset）。
         * 为 true 时窗口四边都已被让开，页面**不能再**叠加系统栏边距。
         */
        val windowInsetActive = windowTopPx > 0.5f || windowLeftPx > 0.5f
        val topSafePadding = if (windowInsetActive) 0.dp else statusBarPadding
        val bottomSafePadding = if (windowInsetActive) 0.dp else navBarPadding

        /**
         * `show()` 之后、以及每次配置变化（旋转）之后再施加一次（**关键时机**）：
         * Compose Dialog 的窗口属性会在 `show()` 时被主题默认值覆盖，
         * ROM 也可能在旋转重算 frame 时重置窗口属性，故这里反复强调。
         */
        LaunchedEffect(dialogWindow, configuration) {
            delay(120)
            dialogWindow?.let { applyFullBleedWindow(it) }
        }

        /**
         * 宿主侧「黑色幕布」兜底（与图片附件页一致，**确定性方案**）。
         *
         * 实测该 ROM 会无视 Dialog 窗口的 `FLAG_LAYOUT_IN_SCREEN` 与挖孔模式，把窗口 frame
         * 限制在安全区内 —— 横屏时屏幕左侧始终露出一条宿主页面（占屏宽约 4.6%，
         * 恰为挖孔安全区宽度）。
         *
         * 这里在**宿主 Activity 的 `android.R.id.content`** 最上层临时叠一张纯黑 View：
         * Activity 窗口本身铺满全屏（`enableEdgeToEdge` + SHORT_EDGES），
         * 任何"Dialog 没盖住"的区域都会被压成纯黑，与页面深色背景无缝衔接。
         * Dialog 若已正常铺满，这层幕布被完全遮住，不产生任何可见影响。
         */
        DisposableEffect(activity) {
            val hostActivity = activity
            val blackoutView = if (hostActivity == null) {
                null
            } else {
                val content = hostActivity.findViewById<ViewGroup>(android.R.id.content)
                if (content == null) {
                    null
                } else {
                    android.view.View(hostActivity).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }.also { v ->
                        content.addView(
                            v,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                }
            }
            onDispose {
                try {
                    blackoutView?.let { v -> (v.parent as? ViewGroup)?.removeView(v) }
                } catch (_: Exception) {
                    // 忽略 Activity 已销毁时的异常
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
                /** 窗口位置随布局变化刷新（旋转 / 尺寸变化后避让判断仍然准确） */
                .onGloballyPositioned {
                    val location = IntArray(2)
                    dialogRootView.getLocationOnScreen(location)
                    if (location[0] != windowLeftPx.toInt() || location[1] != windowTopPx.toInt()) {
                        windowLeftPx = location[0].toFloat()
                        windowTopPx = location[1].toFloat()
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ========== 顶栏：标题 + 关闭按钮 ==========
                // top 额外让出系统栏高度（窗口已铺满时）；窗口若已被系统贴合进安全区，
                // topSafePadding 自动为 0，避免双重避让 —— 视觉与改造前保持一致
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp + topSafePadding,
                            bottom = 12.dp,
                        ),
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

                    /**
                     * 播放控制区：左箭头 + 播放/暂停按钮 + 右箭头
                     *
                     * v2026-07-25 新增：参考原型图 CorgiMemo 角标点击交互原型.html
                     * - 左右箭头循环切换录音（到首项点左跳到末项，到末项点右跳到首项）
                     * - 与原型 voiceGo(dir) 行为一致：(index + dir + size) % size
                     * - 切换后 LaunchedEffect(currentIndex) 自动调用 voicePlayer.prepare()
                     */
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 左箭头：切换到上一个录音（循环）
                        IconButton(
                            onClick = {
                                if (mutablePaths.isNotEmpty()) {
                                    currentIndex = (currentIndex - 1 + mutablePaths.size) % mutablePaths.size
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2A2A))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "上一个录音",
                                tint = Color(0xFFCCCCCC),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(24.dp))

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

                        Spacer(modifier = Modifier.width(24.dp))

                        // 右箭头：切换到下一个录音（循环）
                        IconButton(
                            onClick = {
                                if (mutablePaths.isNotEmpty()) {
                                    currentIndex = (currentIndex + 1) % mutablePaths.size
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2A2A2A))
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "下一个录音",
                                tint = Color(0xFFCCCCCC),
                                modifier = Modifier.size(22.dp)
                            )
                        }
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

                // ========== 底栏：删除 + 下载按钮 ==========
                // v2026-07-25 调整：参考原型图，删除按钮在左，下载按钮在右
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            /** bottom 额外让出导航栏高度（窗口已铺满时），理由同顶栏 */
                            bottom = 12.dp + bottomSafePadding,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
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

            // Snackbar 提示：底部居中，离底 80dp 避开底栏按钮
            // 在 Dialog Window 内显示，z 轴层级高于 Activity 内的柯基悬浮球
            // 使用 AppSnackbarHost 统一全项目 Snackbar 样式（柯基图标 + 文字 + 按钮）
            AppSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
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

/**
 * 从 Context 逐层解包拿到宿主 [Activity]。
 *
 * Compose Dialog 的 context 通常是 ContextThemeWrapper 包着 Activity，
 * 用于读取 Activity 主窗口的真实 insets —— Dialog 子窗口上报的 insets 在部分 ROM 上不可靠。
 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
