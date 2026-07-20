package com.corgimemo.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

/**
 * 头像源选择 BottomSheet
 *
 * 4 个来源：
 * 1. 拍照        → ActivityResultContracts.TakePicture()，需 FileProvider 创建 URI
 * 2. 选图(新)    → ActivityResultContracts.PickVisualMedia()（Android 13+ PhotoPicker，无需权限）
 * 3. 选图(兼容)  → PickVisualMedia 已向下兼容 Android 4.4+，与 (2) 合并
 * 4. 预设库      → 弹内嵌 PresetAvatarGrid，用户选一个 key
 *
 * 实际实现：
 * - "拍照"使用 `ActivityResultContracts.TakePicture` + `FileProvider.getUriForFile` 创建临时 URI
 * - "选图"使用 `ActivityResultContracts.PickVisualMedia` + `PickVisualMediaRequest(ImageOnly)`
 * - "预设库"切换为内嵌的 `PresetAvatarGrid` 网格（在本组件内复用同一 ModalBottomSheet）
 *
 * 关闭策略：拍照 / 选图触发后立即 `sheetState.hide()` + `onDismiss()`，
 * 预设库通过内部状态切换（保持 BottomSheet 打开，切换为网格内容）。
 *
 * @param visible          是否显示本 BottomSheet
 * @param onDismiss        关闭回调
 * @param onPhotoTaken     拍照完成回调（FileProvider URI）
 * @param onPhotoPicked    选图完成回调（PhotoPicker URI）
 * @param onPresetSelected 预设选择完成回调（preset key）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarSourceSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPhotoTaken: (Uri) -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onPresetSelected: (String) -> Unit
) {
    // 不可见时不渲染任何内容，避免 ModalBottomSheet 异常退出
    if (!visible) return

    // ===== 状态 =====
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 拍照 URI（FileProvider 创建）：在 takePictureLauncher 回调中读出
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // 预设库内部状态：true 时显示网格，false 时显示 4 路径列表
    var showPresets by remember { mutableStateOf(false) }
    var selectedPresetKey by remember { mutableStateOf<String?>(null) }

    // ===== ActivityResult：拍照 =====
    // 回调 success=true 时回传临时 URI；false（用户取消）时清理 pendingCameraUri
    // 关键：回调里再关闭 BottomSheet，避免 launch 后立即 onDismiss 导致组件离开
    // Composition、launcher 被 unregister、系统相册返回结果丢失
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { onPhotoTaken(it) }
        }
        pendingCameraUri = null
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // ===== ActivityResult：选图 =====
    // Android 13+ 使用系统 PhotoPicker；4.4+ 走向后兼容 Activity 透明壳
    // 关键：回调里再关闭 BottomSheet，原因同上
    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoPicked(uri)
        }
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    // ===== 主体 ModalBottomSheet =====
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        if (showPresets) {
            // ----- 预设库模式（内嵌 PresetAvatarGrid）-----
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "选择预设头像",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                PresetAvatarGrid(
                    selectedKey = selectedPresetKey,
                    onPresetSelect = { key ->
                        // 选中即回调 + 关闭 BottomSheet
                        selectedPresetKey = key
                        onPresetSelected(key)
                        onDismiss()
                    },
                    modifier = Modifier.height(400.dp)
                )
            }
        } else {
            // ----- 4 个来源选择模式 -----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "选择头像来源",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // ① 拍照
                AvatarSourceRow(
                    icon = Icons.Default.CameraAlt,
                    title = "拍照",
                    description = "使用摄像头拍摄新头像"
                ) {
                    // 创建 cacheDir 临时 jpg + FileProvider URI
                    val tmpFile = File.createTempFile(
                        "avatar_capture_", ".jpg",
                        context.cacheDir
                    )
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        tmpFile
                    )
                    pendingCameraUri = uri
                    // 触发系统相机；URI 已被授权，可直接写入
                    takePictureLauncher.launch(uri)
                    // 不在此处关闭 BottomSheet：等 launcher 回调后再关闭
                    // 否则组件离开 Composition 会导致 launcher 被 unregister，结果丢失
                }

                Spacer(Modifier.height(8.dp))

                // ② 相册选图
                AvatarSourceRow(
                    icon = Icons.Default.PhotoLibrary,
                    title = "从相册选择",
                    description = "Android 13+ 使用 PhotoPicker，无需权限"
                ) {
                    // ImageOnly = 只显示图片；向下兼容旧系统
                    pickMediaLauncher.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
                    // 不在此处关闭 BottomSheet，原因同上
                }

                Spacer(Modifier.height(8.dp))

                // ③ 预设库
                AvatarSourceRow(
                    icon = Icons.Default.SmartToy,
                    title = "预设头像库",
                    description = "从 13 种柯基动作中选择"
                ) {
                    // 切换为预设库模式（不关闭 BottomSheet）
                    showPresets = true
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/**
 * 单行来源选项（图标 + 标题 + 描述）
 *
 * 视觉：图标 40dp 圆形 + 标题 titleMedium + 描述 bodySmall onSurfaceVariant
 * 整行可点击；点击波纹由 Material3 默认主题提供
 *
 * @param icon        左侧图标（Material Icons）
 * @param title       主标题（如"拍照"）
 * @param description 副标题描述（如"使用摄像头拍摄新头像"）
 * @param onClick     点击回调
 */
@Composable
private fun AvatarSourceRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .padding(8.dp)
        )
        Spacer(Modifier.size(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
