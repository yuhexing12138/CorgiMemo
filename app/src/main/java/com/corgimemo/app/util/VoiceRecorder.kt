package com.corgimemo.app.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/**
 * 语音录制器
 * 封装 MediaRecorder，提供录音状态管理、实时音量监测和时长统计功能
 *
 * @param context 应用上下文，用于获取文件存储路径
 */
class VoiceRecorder(private val context: Context) {

    /**
     * 录制状态枚举
     */
    enum class RecordingState {
        IDLE,       // 空闲状态，未开始录制
        RECORDING,  // 正在录制
        STOPPED     // 已停止录制
    }

    companion object {
        /** 最大录制时长（毫秒） */
        private const val MAX_RECORDING_DURATION_MS = 30_000L

        /** 音量采样间隔（毫秒） */
        private const val AMPLITUDE_SAMPLE_INTERVAL_MS = 50L

        /** 录制文件目录名 */
        private const val VOICE_NOTES_DIR = "voice_notes"

        /** 录制文件扩展名 */
        private const val FILE_EXTENSION = ".m4a"
    }

    /** MediaRecorder 实例 */
    private var mediaRecorder: MediaRecorder? = null

    /** 当前录制文件的绝对路径 */
    private var currentFilePath: String? = null

    /** 录制开始时间戳（毫秒） */
    private var recordingStartTime: Long = 0

    /** 协程作用域用于更新音量和时长 */
    private var recorderJob: kotlinx.coroutines.Job? = null

    // ==================== 状态流 ====================

    /** 录制状态 */
    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    /** 实时音量振幅 (0.0 - 1.0) */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    /** 当前录制时长（毫秒） */
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    /** 录制文件路径 */
    private val _filePath = MutableStateFlow<String?>(null)
    val filePath: StateFlow<String?> = _filePath.asStateFlow()

    /**
     * 开始录制语音
     *
     * @return Result 包装的结果，成功时返回 Unit，失败时返回异常信息
     */
    fun startRecording(): Result<Unit> {
        return try {
            // 检查当前状态
            if (_recordingState.value == RecordingState.RECORDING) {
                return Result.failure(IllegalStateException("正在录制中，请先停止当前录制"))
            }

            // 释放之前的资源
            release()

            // 创建输出文件
            val outputFile = createOutputFile()
            currentFilePath = outputFile.absolutePath

            // 初始化 MediaRecorder
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setAudioChannels(1)
                setOutputFile(currentFilePath)
                prepare()
                start()
            }

            // 更新状态
            recordingStartTime = System.currentTimeMillis()
            _recordingState.value = RecordingState.RECORDING
            _filePath.value = currentFilePath

            // 启动协程监控音量和时长
            startMonitoring()

            Result.success(Unit)
        } catch (e: IOException) {
            Result.failure(IOException("无法初始化录音设备: ${e.message}", e))
        } catch (e: IllegalStateException) {
            Result.failure(IllegalStateException("录音器状态错误: ${e.message}", e))
        }
    }

    /**
     * 停止录制并返回文件路径
     *
     * @return Result 包装的文件路径，成功时返回绝对路径
     */
    fun stopRecording(): Result<String> {
        return try {
            if (_recordingState.value != RecordingState.RECORDING) {
                return Result.failure(IllegalStateException("当前未在录制状态"))
            }

            // 停止监控协程
            stopMonitoring()

            // 停止 MediaRecorder
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                // 停止失败时删除可能损坏的文件
                currentFilePath?.let { File(it).delete() }
                return Result.failure(IOException("录制停止失败: ${e.message}", e))
            }

            // 更新状态
            _recordingState.value = RecordingState.STOPPED

            // 返回文件路径
            val path = currentFilePath ?: return Result.failure(IOException("文件路径为空"))
            Result.success(path)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 取消录制并删除已录制的文件
     */
    fun cancelRecording() {
        try {
            stopMonitoring()

            mediaRecorder?.apply {
                try { stop() } catch (_: Exception) {}
                release()
            }
            mediaRecorder = null

            // 删除已录制的文件
            currentFilePath?.let { File(it).delete() }
            currentFilePath = null

            // 重置状态
            _recordingState.value = RecordingState.IDLE
            _filePath.value = null
            _duration.value = 0L
            _amplitude.value = 0f
        } catch (_: Exception) {
            // 忽略取消时的异常
        }
    }

    /**
     * 释放所有资源
     * 应在不再使用时调用，避免内存泄漏
     */
    fun release() {
        stopMonitoring()

        mediaRecorder?.release()
        mediaRecorder = null

        _recordingState.value = RecordingState.IDLE
        _filePath.value = null
        _duration.value = 0L
        _amplitude.value = 0f
    }

    // ==================== 私有方法 ====================

    /**
     * 创建输出文件
     *
     * @return 创建的文件对象
     */
    private fun createOutputFile(): File {
        // 获取应用内部存储目录下的 voice_notes 目录
        val voiceNotesDir = File(context.filesDir, VOICE_NOTES_DIR)

        // 如果目录不存在则创建
        if (!voiceNotesDir.exists()) {
            voiceNotesDir.mkdirs()
        }

        // 使用时间戳生成唯一文件名
        val timestamp = System.currentTimeMillis()
        val fileName = "voice_$timestamp$FILE_EXTENSION"

        return File(voiceNotesDir, fileName)
    }

    /**
     * 启动音量和时长监控协程
     * 定期采样音量振幅并更新录制时长
     */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun startMonitoring() {
        recorderJob = kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            while (isActive && _recordingState.value == RecordingState.RECORDING) {
                // 更新录制时长
                val elapsed = System.currentTimeMillis() - recordingStartTime
                _duration.value = elapsed

                // 检查是否超过最大录制时长
                if (elapsed >= MAX_RECORDING_DURATION_MS) {
                    // 自动停止录制
                    launch(Dispatchers.Main) {
                        stopRecording()
                    }
                    break
                }

                // 获取当前音量振幅并归一化到 0-1 范围
                try {
                    val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                    // 将原始值（0-32767）映射到 0.0-1.0
                    val normalizedAmplitude = if (maxAmplitude > 0) {
                        (maxAmplitude.toFloat() / 32767f).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    _amplitude.value = normalizedAmplitude
                } catch (_: Exception) {}

                // 等待下一次采样
                kotlinx.coroutines.delay(AMPLITUDE_SAMPLE_INTERVAL_MS)
            }
        }
    }

    /**
     * 停止监控协程
     */
    private fun stopMonitoring() {
        recorderJob?.cancel()
        recorderJob = null
    }
}
