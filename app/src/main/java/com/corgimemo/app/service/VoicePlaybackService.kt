package com.corgimemo.app.service

import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.corgimemo.app.R

/**
 * 语音播放前台服务
 * 确保应用在后台时语音播放不中断，并在通知栏显示播放控制
 *
 * 使用方式：
 * 1. 启动服务：startForegroundService(intent)
 * 2. 通过 Binder 控制播放
 * 3. 停止服务：stopSelf()
 */
class VoicePlaybackService : Service() {

    companion object {
        /** 服务 Action 常量 */
        const val ACTION_PLAY_PAUSE = "com.corgimemo.app.action.PLAY_PAUSE"
        const val ACTION_STOP = "com.corgimemo.app.action.STOP"
        const val ACTION_SEEK = "com.corgimemo.app.action.SEEK"

        /** Intent Extra 键 */
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_TODO_TITLE = "todo_title"
        const val EXTRA_SEEK_POSITION = "seek_position"

        /** 通知渠道 ID 和名称 */
        private const val NOTIFICATION_CHANNEL_ID = "voice_playback_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "语音播放"
        private const val NOTIFICATION_ID = 1001

        /** 服务唯一 ID */
        const val SERVICE_ID = 1001
    }

    /** MediaPlayer 实例 */
    private var mediaPlayer: MediaPlayer? = null

    /** 当前播放的文件路径 */
    private var currentFilePath: String? = null

    /** 当前待办标题（用于显示在通知中） */
    private var todoTitle: String? = null

    /** 是否正在播放 */
    private var isPlaying: Boolean = false

    /** Binder 实例，用于与 Activity 通信 */
    private val binder = LocalBinder()

    /**
     * Binder 类
     * 提供对 Service 内部方法的访问
     */
    inner class LocalBinder : android.os.Binder() {
        /** 获取 Service 实例 */
        fun getService(): VoicePlaybackService = this@VoicePlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        // 创建通知渠道（Android 8.0+）
        createNotificationChannel()
        // 初始化 MediaPlayer
        initMediaPlayer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 处理 Intent action
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_STOP -> stopPlaybackAndService()
            ACTION_SEEK -> {
                val position = intent.getIntExtra(EXTRA_SEEK_POSITION, 0)
                seekTo(position)
            }
            else -> {
                // 初始启动时获取参数
                currentFilePath = intent?.getStringExtra(EXTRA_FILE_PATH)
                todoTitle = intent?.getStringExtra(EXTRA_TODO_TITLE)

                // 准备并启动前台通知
                startForegroundWithNotification()

                // 如果有文件路径，准备播放
                currentFilePath?.let { prepareAndPlay(it) }
            }
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
    }

    // ==================== 公共方法（通过 Binder 调用） ====================

    /**
     * 准备并播放音频文件
     *
     * @param filePath 音频文件绝对路径
     * @param title 待办标题（可选）
     */
    fun prepareAndPlay(filePath: String, title: String? = null) {
        try {
            currentFilePath = filePath
            todoTitle = title

            mediaPlayer?.apply {
                reset()
                setDataSource(filePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    this@VoicePlaybackService.isPlaying = true
                    updateNotification()
                }
                setOnCompletionListener {
                    this@VoicePlaybackService.isPlaying = false
                    updateNotification()
                    // 播放完成后可以选择停止服务或保持
                    // stopSelf()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 播放或暂停切换
     */
    fun togglePlayPause() {
        try {
            if (isPlaying) {
                mediaPlayer?.pause()
                isPlaying = false
            } else {
                mediaPlayer?.start()
                isPlaying = true
            }
            updateNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        try {
            mediaPlayer?.pause()
            isPlaying = false
            updateNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 恢复播放
     */
    fun resume() {
        try {
            mediaPlayer?.start()
            isPlaying = true
            updateNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 跳转到指定位置
     *
     * @param position 目标位置（毫秒）
     */
    fun seekTo(position: Int) {
        try {
            mediaPlayer?.seekTo(position)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 停止播放并释放资源
     */
    fun stop() {
        try {
            mediaPlayer?.stop()
            isPlaying = false
            updateNotification()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取当前播放位置（毫秒）
     *
     * @return 当前位置
     */
    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * 获取总时长（毫秒）
     *
     * @return 总时长
     */
    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * 是否正在播放
     *
     * @return 播放状态
     */
    fun getIsPlaying(): Boolean = isPlaying

    // ==================== 私有方法 ====================

    /**
     * 初始化 MediaPlayer
     */
    private fun initMediaPlayer() {
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .build()
            )
        }
    }

    /**
     * 释放 MediaPlayer 资源
     */
    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            isPlaying = false
        } catch (_: Exception) {}
    }

    /**
     * 创建通知渠道（Android 8.0+ 必需）
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManagerCompat.IMPORTANCE_LOW
            ).apply {
                description = "语音备注播放控制"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 启动前台服务并显示通知
     */
    private fun startForegroundWithNotification() {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 创建播放控制通知
     *
     * @return Notification 对象
     */
    private fun createNotification(): Notification {
        // 构建暂停/播放 Intent
        val playPauseIntent = Intent(this, VoicePlaybackService::class.java).apply {
            action = ACTION_PLAY_PAUSE
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 构建停止 Intent
        val stopIntent = Intent(this, VoicePlaybackService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 状态文本
        val statusText = if (isPlaying) "正在播放" else "已暂停"

        // 构建通知
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(todoTitle ?: "🎤 语音备注")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification_voice)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "暂停" else "播放",
                playPausePendingIntent
            )
            .addAction(
                R.drawable.ic_stop,
                "停止",
                stopPendingIntent
            )
            .build()
    }

    /**
     * 更新通知内容
     */
    private fun updateNotification() {
        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(NOTIFICATION_ID, createNotification())
        } catch (_: SecurityException) {
            // 权限不足时忽略
        }
    }

    /**
     * 停止播放并结束服务
     */
    private fun stopPlaybackAndService() {
        stop()
        releaseMediaPlayer()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
}
