package com.corgimemo.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

/**
 * 图片来源选择底部弹窗
 *
 * 触发：待办编辑页/灵感编辑页点击"照片"按钮，或 ImagePicker 组件点击添加图片。
 * 提供两项操作：拍照（相机）和从相册选择。
 *
 * 展开动画（由 Material3 ModalBottomSheet 提供）：
 *   弹窗：spring 弹簧上滑 translateY(100% → 0)，dampingRatio ≈ 0.8，stiffness ≈ 400
 *   遮罩：淡入 opacity(0 → 0.32)
 * 严格遵循操作列表型底部弹窗原型规范。
 *
 * @param onCameraSelected 用户选择"拍照"时的回调（含 onDismiss 关闭逻辑）
 * @param onGallerySelected 用户选择"从相册选择"时的回调
 * @param onDismiss 弹窗关闭回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagePickerDialog(
    onCameraSelected: () -> Unit,
    onGallerySelected: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            /** 拖动指示器：36×4px，圆角 2px，居中，#E0E0E0（与原型一致） */
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

            /** 标题栏：左对齐标题 + 右侧圆形关闭按钮（padding: 12px 24px 16px） */
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择图片来源",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2D2D2D),
                    modifier = Modifier.weight(1f)
                )

                /** 圆形暖橙色关闭按钮：32dp，#FFF0E5 背景，18dp #FF9A5C 图标 */
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

            /** 标题下方分割线：1px，rgba(0,0,0,0.08) */
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp),
                color = Color(0x14000000)
            )

            Spacer(modifier = Modifier.height(8.dp))

            /** 操作列表 */
            Column(modifier = Modifier.fillMaxWidth()) {
                ActionRow(
                    icon = Icons.Default.CameraAlt,
                    text = "拍照",
                    onClick = {
                        onCameraSelected()
                        onDismiss()
                    }
                )
                ActionRow(
                    icon = Icons.Default.PhotoLibrary,
                    text = "从相册选择",
                    onClick = {
                        onGallerySelected()
                        onDismiss()
                    }
                )
            }
        }
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

/**
 * 检查并请求相机权限
 * 用于在启动相机前动态申请 CAMERA 权限
 *
 * @param context 应用上下文
 * @param permissionLauncher 权限请求启动器（由调用方提供）
 * @param onPermissionGranted 权限已授予时的回调
 * @param onPermissionDenied 权限被拒绝时的回调
 */
fun checkAndRequestCameraPermission(
    context: Context,
    permissionLauncher: ActivityResultLauncher<String>,
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    when {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED -> {
            onPermissionGranted()
        }
        else -> {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
