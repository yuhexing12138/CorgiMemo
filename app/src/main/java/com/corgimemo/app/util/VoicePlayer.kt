package com.corgimemo.app.util

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 语音播放器
 * 封装 MediaPlayer，提供播放状态管理、进度控制和时长统计功能
 *
 * @param context 应用上下文，用于初始化 MediaPlayer
 */
class VoicePlayer(private val context: Context) {

    /**
     * 播放状态枚举
     */
    enum class PlaybackState {
        IDLE,       // 空闲状态，未准备或已释放
        PREPARED,   // 已准备好，可以播放
        PLAYING,    // 正在播放
        PAUSED,     // 已暂停
        STOPPED,    // 已停止
        COMPLETED   // 播放完成
    }

    /** MediaPlayer 实例 */
    private var mediaPlayer: MediaPlayer? = null

    /** 当前播放的文件路径 */
    private var currentFilePath: String? = null

    /** 进度更新协程 */
    private var progressJob: kotlinx.coroutines.Job? = null

    // ==================== 状态流 ====================

    /** 播放状态 */
    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /** 当前播放位置（毫秒） */
    private val _currentPosition = MutableStateFlow(0)
    val currentPosition: StateFlow<Int> = _currentPosition.asStateFlow()

    /** 音频总时长（毫秒） */
    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    /** 是否正在播放 */
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    /**
     * 准备播放音频文件
     *
     * @param filePath 音频文件的绝对路径
     * @return Result 包装的结果
     */
    fun prepare(filePath: String): Result<Unit> {
        return try {
            // 如果正在播放其他文件，先释放
            if (_playbackState.value == PlaybackState.PLAYING) {
                stop()
            }

            release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                setOnPreparedListener {
                    _playbackState.value = PlaybackState.PREPARED
                    _duration.value = duration
                    _currentPosition.value = 0
                }
                setOnCompletionListener {
                    _playbackState.value = PlaybackState.COMPLETED
                    _isPlaying.value = false
                    stopProgressTracking()
                    // 自动回到起始位置
                    seekTo(0)
                }
                setOnErrorListener { _, _, _ ->
                    _playbackState.value = PlaybackState.IDLE
                    _isPlaying.value = false
                    true
                }
                prepareAsync()
            }

            currentFilePath = filePath
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception("无法准备音频文件: ${e.message}", e))
        }
    }

    /**
     * 开始播放或从暂停位置恢复播放
     *
     * @return Result 包装的结果
     */
    fun play(): Result<Unit> {
        return try {
            when (_playbackState.value) {
                PlaybackState.PREPARED, PlaybackState.STOPPED, PlaybackState.COMPLETED -> {
                    mediaPlayer?.start()
                    _playbackState.value = PlaybackState.PLAYING
                    _isPlaying.value = true
                    startProgressTracking()
                }
                PlaybackState.PAUSED -> {
                    resume()
                }
                else -> {
                    return Result.failure(IllegalStateException("当前状态不支持播放操作"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 暂停播放
     *
     * @return Result 包装的结果
     */
    fun pause(): Result<Unit> {
        return try {
            if (_playbackState.value != PlaybackState.PLAYING) {
                return Result.failure(IllegalStateException("当前未在播放状态"))
            }

            mediaPlayer?.pause()
            _playbackState.value = PlaybackState.PAUSED
            _isPlaying.value = false
            stopProgressTracking()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从暂停位置恢复播放
     *
     * @return Result 包装的结果
     */
    fun resume(): Result<Unit> {
        return try {
            if (_playbackState.value != PlaybackState.PAUSED) {
                return Result.failure(IllegalStateException("当前未在暂停状态"))
            }

            mediaPlayer?.start()
            _playbackState.value = PlaybackState.PLAYING
            _isPlaying.value = true
            startProgressTracking()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 停止播放并重置到起始位置
     *
     * @return Result 包装的结果
     */
    fun stop(): Result<Unit> {
        return try {
            stopProgressTracking()

            mediaPlayer?.apply {
                if (isPlaying) stop()
                seekTo(0)
            }

            _playbackState.value = PlaybackState.STOPPED
            _isPlaying.value = false
            _currentPosition.value = 0

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 跳转到指定位置
     *
     * @param position 目标位置（毫秒）
     * @return Result 包装的结果
     */
    fun seekTo(position: Int): Result<Unit> {
        return try {
            if (position < 0 || position > _duration.value) {
                return Result.failure(IllegalArgumentException("无效的播放位置: $position"))
            }

            mediaPlayer?.seekTo(position)
            _currentPosition.value = position

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 释放所有资源
     * 应在不再使用时调用，避免内存泄漏
     */
    fun release() {
        stopProgressTracking()

        mediaPlayer?.apply {
            reset()
            release()
        }
        mediaPlayer = null

        currentFilePath = null
        _playbackState.value = PlaybackState.IDLE
        _isPlaying.value = false
        _currentPosition.value = 0
        _duration.value = 0
    }

    // ==================== 私有方法 ====================

    /**
     * 启动进度跟踪协程
     * 定期更新当前播放位置
     */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun startProgressTracking() {
        progressJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.Main) {
            while (isActive && _isPlaying.value) {
                try {
                    mediaPlayer?.let { player ->
                        if (player.isPlaying) {
                            _currentPosition.value = player.currentPosition
                        }
                    }
                } catch (_: Exception) {}

                // 每 100ms 更新一次进度
                kotlinx.coroutines.delay(100L)
            }
        }
    }

    /**
     * 停止进度跟踪协程
     */
    private fun stopProgressTracking() {
        progressJob?.cancel()
        progressJob = null
    }
}
