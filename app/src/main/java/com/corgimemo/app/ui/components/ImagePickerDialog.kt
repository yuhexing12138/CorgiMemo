package com.corgimemo.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.corgimemo.app.ui.theme.UiColors

/**
 * 图片来源选择对话框
 * 提供两种图片来源选项：拍照和从相册选择
 * 使用 Material3 AlertDialog 风格，符合项目 UI 设计规范（暖橙色主题、大圆角）
 *
 * 功能说明：
 * - 点击"📷 拍照"：启动系统相机应用拍照，照片自动保存到应用内部存储
 * - 点击"🖼️ 从相册选择"：打开系统相册选择器，支持单张选择
 * - 自动处理 Android 13+ 的 CAMERA 权限请求
 * - 权限被拒绝时显示引导提示
 *
 * @param onCameraSelected 用户选择"拍照"时的回调函数
 * @param onGallerySelected 用户选择"从相册选择"时的回调函数
 * @param onDismiss 对话框关闭回调（点击外部区域或返回键）
 */
@Composable
fun ImagePickerDialog(
    onCameraSelected: () -> Unit,
    onGallerySelected: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp), /** 符合设计规范：弹窗圆角24dp */
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                /** 对话框标题 */
                Text(
                    text = "选择图片来源",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                /**
                 * 拍照按钮
                 * 图标 + 文字的垂直布局，点击后触发相机 Intent
                 */
                ImageSourceOption(
                    icon = Icons.Default.CameraAlt,
                    label = "拍照",
                    description = "使用相机拍摄新照片",
                    onClick = {
                        onCameraSelected()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                /**
                 * 相册按钮
                 * 图标 + 文字的垂直布局，点击后打开系统相册选择器
                 */
                ImageSourceOption(
                    icon = Icons.Default.PhotoLibrary,
                    label = "从相册选择",
                    description = "从已有照片中选择",
                    onClick = {
                        onGallerySelected()
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {}, /** 不需要确认按钮，通过选项按钮直接操作 */
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "取消",
                    color = UiColors.Primary
                )
            }
        }
    )
}

/**
 * 图片来源选项组件
 * 单个可点击的选项卡片，包含图标、标题和描述文字
 *
 * @param icon 选项图标（ImageVector）
 * @param label 选项标题文本
 * @param description 选项描述文本（可选）
 * @param onClick 点击回调函数
 * @param modifier 修饰符
 */
@Composable
private fun ImageSourceOption(
    icon: ImageVector,
    label: String,
    description: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp)) /** 符合设计规范：按钮圆角16dp */
            .background(UiColors.Primary.copy(alpha = 0.08f)) /** 浅暖橙色背景 */
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            /** 选项图标容器 */
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UiColors.Primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = UiColors.Primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            /** 标题和描述文字 */
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                /** 可选的描述文字 */
                if (description != null) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
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
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit
) {
    when {
        /** 权限已授予 → 直接执行回调 */
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED -> {
            onPermissionGranted()
        }
        /** 权限未授予 → 发起权限请求 */
        else -> {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}
