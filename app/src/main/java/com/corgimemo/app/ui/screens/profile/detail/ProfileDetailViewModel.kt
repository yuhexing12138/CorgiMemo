package com.corgimemo.app.ui.screens.profile.detail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.repository.CorgiRepository
import com.corgimemo.app.util.AvatarPath
import com.corgimemo.app.util.AvatarStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * 个人信息页 ViewModel
 *
 * 职责：
 * - 暴露 CorgiData（StateFlow），供 UI 订阅头像 / 名字 / 性别等字段
 * - 处理头像选择（4 路径：拍照 / 相册 / 预设库 / 兼容）
 * - 处理姓名修改
 * - 处理性别设置
 *
 * 注意：
 * - 仓库 API 实际方法名为 `getCorgiDataFlow()`（计划文档中写作 `observeCorgiData()`，
 *   按仓库实际签名调整）
 * - 头像保存流程：decodeBitmap → CircularImageCropper → AvatarStorage.saveAvatar → repository.updateAvatarPath
 * - 预设库：直接拼 "preset:{key}" 写入 avatarPath（无需保存文件）
 */
@HiltViewModel
class ProfileDetailViewModel @Inject constructor(
    private val repository: CorgiRepository
) : ViewModel() {

    /**
     * 当前柯基数据（含 name / avatarPath / gender）
     *
     * 使用 Eagerly 启动：进入页面立即订阅，避免冷启动时短暂显示 null
     * （UI 用 collectAsState 订阅此 StateFlow，自动重组）
     */
    val corgiData: StateFlow<CorgiData?> = repository.getCorgiDataFlow()
        .onEach { data ->
            Log.d("AvatarDebug", "ViewModel.corgiData flow收到新值: avatarPath=${data?.avatarPath}, name=${data?.name}, id=${data?.id}")
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * 头像预加载 Bitmap
     *
     * 派生自 corgiData.avatarPath：
     * - null/preset：保持 null（UserAvatar 用 FrameAnimation / 占位）
     * - 私有目录绝对路径：用 BitmapFactory 在 IO 线程解码后推送
     *
     * 使用 WhileSubscribed(5s) 启动，UI 退到后台 5 秒内不重启收集协程
     * 避免短暂进入页面触发多余 IO。distinctUntilChanged 按 path 去重，
     * 相同 path 不重新解码。
     */
    val avatarBitmap: StateFlow<Bitmap?> = corgiData
        .map { data -> data?.avatarPath }
        .distinctUntilChanged()
        .onEach { path ->
            Log.d("AvatarDebug", "ViewModel.avatarBitmap: path变化=$path, 开始解码...")
        }
        .map { path -> decodeAvatarBitmap(path) }
        .onEach { bitmap ->
            Log.d("AvatarDebug", "ViewModel.avatarBitmap: 解码完成, bitmap=${bitmap != null}, 宽=${bitmap?.width}, 高=${bitmap?.height}")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 异步解码头像 Bitmap
     *
     * @param path 头像路径；null / preset / 非绝对路径 返回 null
     * @return 解码后的 Bitmap，失败返回 null
     */
    private suspend fun decodeAvatarBitmap(path: String?): Bitmap? {
        if (path == null || AvatarPath.isPreset(path) || !path.startsWith("/")) return null
        return withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
        }
    }

    /**
     * 处理拍照/选图 URI，进入裁剪环节
     *
     * 调用 AvatarStorage.decodeBitmap 在 IO 线程解码图片，
     * 成功后通过 onBitmapReady 回调将 Bitmap 推回 UI 层。
     * 解码失败（URI 无权 / 文件损坏）则不回调，由调用方自行处理。
     *
     * @param context      Android Context（用于 contentResolver）
     * @param uri          源图 URI（content:// 或 file://）
     * @param onBitmapReady 解码成功回调，传入解码后的 Bitmap
     */
    fun onPhotoUriReceived(context: Context, uri: Uri, onBitmapReady: (Bitmap) -> Unit) {
        viewModelScope.launch {
            val bitmap = AvatarStorage.decodeBitmap(context, uri) ?: return@launch
            onBitmapReady(bitmap)
        }
    }

    /**
     * 保存裁剪后的头像（用户上传路径）
     *
     * 流程：
     * 1. 删除旧头像文件（仅用户上传类型，预设不删）
     * 2. repository.updateAvatarPath(newPath) 写入新路径
     * 3. onSuccess 回调关闭裁剪界面
     *
     * avatarBitmap 派生自 corgiData.avatarPath，会随 corgi_data 表更新自动重解码，
     * 无需手动通知。
     *
     * @param context   Android Context
     * @param newPath   新的 avatarPath（绝对路径 /data/.../avatars/xxx.png）
     * @param onSuccess 持久化完成回调（用于关闭 CropperDialog）
     */
    fun saveAvatarPath(context: Context, newPath: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val oldPath = corgiData.value?.avatarPath
            Log.d("AvatarDebug", "ViewModel.saveAvatarPath: 旧路径=$oldPath, 新路径=$newPath")
            val deleted = AvatarStorage.deleteAvatar(oldPath)
            Log.d("AvatarDebug", "ViewModel.saveAvatarPath: 删除旧头像结果=$deleted")
            repository.updateAvatarPath(newPath)
            Log.d("AvatarDebug", "ViewModel.saveAvatarPath: 数据库更新完成, corgiData.value.avatarPath=${corgiData.value?.avatarPath}")
            onSuccess()
        }
    }

    /**
     * 保存预设头像（drawable 柯基动作帧）
     *
     * 流程：
     * 1. 删除旧用户上传文件（如有）
     * 2. avatarPath 写为 "preset:{key}"（如 "preset:corgi_sit"）
     * 3. UserAvatar 组件按 avatarPath 前缀识别并渲染对应 drawable 帧
     *
     * @param presetKey  预设 key（如 "corgi_sit"）
     * @param onSuccess 持久化完成回调（用于关闭 BottomSheet）
     */
    fun savePresetAvatar(presetKey: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val oldPath = corgiData.value?.avatarPath
            AvatarStorage.deleteAvatar(oldPath)
            repository.updateAvatarPath(AvatarPath.toPresetPath(presetKey))
            onSuccess()
        }
    }

    /**
     * 更新柯基名字
     *
     * 写入 Room 数据库（Repository 已挂 ioDispatcher，不阻塞主线程）
     * StateFlow 自动推送新值，UI 重组。
     *
     * @param newName 新名字
     */
    fun updateName(newName: String) {
        viewModelScope.launch {
            repository.updateCorgiName(newName)
        }
    }

    /**
     * 更新性别字段
     *
     * @param gender 性别字符串（"MALE" / "FEMALE" / "OTHER"），null 表示未设置
     */
    fun updateGender(gender: String?) {
        viewModelScope.launch {
            repository.updateGender(gender)
        }
    }
}
