package com.corgimemo.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File

/**
 * 头像源选择底部弹窗
 *
 * 两个模式共用一个 ModalBottomSheet：
 * - 外部模式（操作列表型）：拍照 / 从相册选择 / 预设头像库 三个操作项
 * - 内部模式（专属工具型）：PresetAvatarGrid 4 列网格，从 13 种柯基动作选择
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
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
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showPresets by remember { mutableStateOf(false) }
    var selectedPresetKey by remember { mutableStateOf<String?>(null) }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { onPhotoTaken(it) }
        }
        pendingCameraUri = null
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPhotoPicked(uri)
        }
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            /** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
            DragHandle()

            /** 标题栏：左对齐标题 + 右侧返回/关闭按钮 */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 内部预设模式：左侧返回箭头
                if (showPresets) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "返回",
                        tint = Color(0xFFFF9A5C),
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { showPresets = false }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }

                Text(
                    text = if (showPresets) "选择预设头像" else "选择头像来源",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D2D2D),
                    modifier = Modifier.weight(1f)
                )

                /** 圆形暖橙色关闭按钮 */
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFFFF0E5))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color(0xFFFF9A5C),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            /** 标题下方分割线 */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            if (showPresets) {
                // ===== 内部模式：专属工具型 - 预设头像库 =====
                Spacer(modifier = Modifier.height(12.dp))

                /** 工具内容区：固定宽度 280dp，内嵌 PresetAvatarGrid */
                Box(
                    modifier = Modifier
                        .width(280.dp)
                    .height(280.dp)
                    .align(Alignment.CenterHorizontally)
                ) {
                    PresetAvatarGrid(
                        selectedKey = selectedPresetKey,
                        onPresetSelect = { key ->
                            selectedPresetKey = key
                            onPresetSelected(key)
                            onDismiss()
                        }
                    )
                }
            } else {
                // ===== 外部模式：操作列表型 - 3 个来源选项 =====
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth()) {
                    ActionRow(
                        icon = Icons.Default.CameraAlt,
                        text = "拍照",
                        onClick = {
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
                            takePictureLauncher.launch(uri)
                        }
                    )
                    ActionRow(
                        icon = Icons.Default.PhotoLibrary,
                        text = "从相册选择",
                        onClick = {
                            pickMediaLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                    )
                    ActionRow(
                        icon = Icons.Default.SmartToy,
                        text = "预设头像库",
                        onClick = { showPresets = true }
                    )
                }
            }
        }
    }
}

// ==================== 共享子组件 ====================

/** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0 */
@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE0E0E0))
        )
    }
}

/** 操作行：Icon 20px + 文字 16px Medium，gap 16dp，padding 14px 24px */
@Composable
private fun ActionRow(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = Color(0xFF2D2D2D),
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2D2D2D),
            modifier = Modifier.weight(1f)
        )
    }
}
