package com.corgimemo.app.ui.screens.inspiration.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.corgimemo.app.ui.theme.UiColors

/**
 * 图片选择器组件
 * 用于在灵感编辑页面中选择和管理图片，支持多图选择和删除
 *
 * 功能说明：
 * - 横向显示已选图片缩略图（72×72dp，圆角12px）
 * - 每张图片右上角有删除按钮（×）
 * - "+ 添加"按钮（虚线边框样式）
 * - 点击添加触发系统图片选择器（使用 PickVisualMedia API）
 * - 自动处理 Android 13+ 的 READ_MEDIA_IMAGES 权限
 *
 * @param imagePaths 当前已选择的图片路径列表
 * @param onImagesChange 图片列表变更回调函数
 * @param modifier 修饰符
 */
@Composable
fun ImagePicker(
    imagePaths: List<String>,
    onImagesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    /**
     * 图片选择启动器
     * 使用 PickVisualMedia API 打开系统图片选择器
     * 支持单张和多张图片选择
     */
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            /** 用户选择图片后的回调 */
            if (uri != null) {
                /** 将 Uri 转换为字符串路径并添加到列表 */
                val updatedPaths = imagePaths.toMutableList()
                updatedPaths.add(uri.toString())
                onImagesChange(updatedPaths)
            }
        }
    )

    /**
     * 权限请求启动器（Android 13+ 需要 READ_MEDIA_IMAGES 权限）
     * 用于在用户点击添加按钮时动态请求权限
     */
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted: Boolean ->
            /** 权限请求结果回调 */
            if (isGranted) {
                /** 权限已授予，打开图片选择器 */
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            // TODO: 权限被拒绝时显示提示信息
        }
    )

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        /** 图片区域标题 */
        Text(
            text = "图片",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        /**
         * 图片列表行
         * 使用 LazyRow 实现横向滚动，展示已选图片和添加按钮
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /** 已选图片缩略图列表 */
            if (imagePaths.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(items = imagePaths, key = { it }) { imagePath ->
                        /**
                         * 单个图片缩略图项
                         * 显示 72×72dp 的圆角图片，右上角带删除按钮
                         */
                        ImageThumbnailItem(
                            imagePath = imagePath,
                            onDelete = {
                                /** 删除指定图片 */
                                val updatedPaths = imagePaths.toMutableList()
                                updatedPaths.remove(imagePath)
                                onImagesChange(updatedPaths)
                            }
                        )
                    }
                }
            } else {
                /** 无图片时的占位空间 */
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.width(8.dp))

            /**
             * 添加图片按钮
             * 虚线边框样式，点击后检查权限并打开图片选择器
             */
            AddImageButton(
                onClick = {
                    /** 检查并请求图片读取权限 */
                    checkAndRequestImagePermission(context, permissionLauncher, imagePickerLauncher)
                }
            )
        }

        /** 提示文字 */
        if (imagePaths.isEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "点击添加图片，最多可添加9张",
                fontSize = 12.sp,
                color = Color(0xFF999999)
            )
        }
    }
}

/**
 * 添加图片按钮组件
 * 虚线边框样式的按钮，用于触发图片选择
 *
 * @param onClick 点击回调函数
 * @param modifier 修饰符
 */
@Composable
private fun AddImageButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = UiColors.Outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            /** 加号图标 */
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加图片",
                tint = UiColors.Primary,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            /** 添加文字 */
            Text(
                text = "添加",
                fontSize = 11.sp,
                color = UiColors.Primary
            )
        }
    }
}

/**
 * 图片缩略图项组件
 * 显示单个已选图片的缩略图，右上角带删除按钮
 *
 * @param imagePath 图片路径或 Uri 字符串
 * @param onDelete 删除回调函数
 * @param modifier 修饰符
 */
@Composable
private fun ImageThumbnailItem(
    imagePath: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(72.dp)
    ) {
        /**
         * 图片占位框
         * 实际项目中应使用 Coil 或 Glide 库加载真实图片
         * 这里使用占位背景色模拟图片效果
         */
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            // TODO: 使用 Coil 加载真实图片
            // AsyncImage(
            //     model = imagePath,
            //     contentDescription = "已选图片",
            //     modifier = Modifier.fillMaxSize(),
            //     contentScale = ContentScale.Crop
            // )

            /** 占位图标（实际使用时移除） */
            Text(
                text = "🖼️",
                fontSize = 28.sp
            )
        }

        /**
         * 删除按钮
         * 位于图片右上角，半透明背景
         */
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除图片",
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

/**
 * 检查并请求图片读取权限
 * 根据 Android 版本和权限状态决定是否需要请求权限
 *
 * @param context 应用上下文
 * @param permissionLauncher 权限请求启动器
 * @param imagePickerLauncher 图片选择器启动器
 */
private fun checkAndRequestImagePermission(
    context: Context,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    imagePickerLauncher: androidx.activity.result.ActivityResultLauncher<PickVisualMediaRequest>
) {
    /**
     * Android 13 (API 33) 及以上版本需要 READ_MEDIA_IMAGES 权限
     * Android 12 及以下版本使用 READ_EXTERNAL_STORAGE 权限
     * PickVisualMedia API 在大多数情况下不需要额外权限
     * 但为了兼容性，这里仍然进行权限检查
     */
    when {
        /** Android 13+ 检查 READ_MEDIA_IMAGES 权限 */
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU -> {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                /** 权限已授予，直接打开图片选择器 */
                imagePickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            } else {
                /** 请求权限 */
                permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        /** Android 12 及以下版本 */
        else -> {
            /** 直接打开图片选择器（PickVisualMedia 不需要存储权限） */
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }
}
