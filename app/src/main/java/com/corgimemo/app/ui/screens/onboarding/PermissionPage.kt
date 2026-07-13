package com.corgimemo.app.ui.screens.onboarding

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.corgimemo.app.viewmodel.OnboardingViewModel
import com.corgimemo.app.viewmodel.PermissionState
import com.corgimemo.app.viewmodel.PermissionType

/**
 * 权限请求页
 *
 * 实际触发系统权限对话框，逐个请求 4 项权限：
 * - 通知权限：待办/日期提醒
 * - 存储权限：图片/备份导出
 * - 麦克风权限：语音输入待办
 * - 位置权限：位置提醒
 *
 * 每项显示状态：未请求（⏳灰色）/已授权（✓绿色）/已拒绝（✗红色）/永久拒绝（⚠橙色）
 * 永久拒绝时显示"去设置"按钮
 *
 * @param viewModel 引导 ViewModel
 * @param onComplete 完成回调（所有权限请求过后触发）
 * @param isCompleting 是否正在完成引导
 */
@Composable
fun PermissionPage(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    isCompleting: Boolean
) {
    val context = LocalContext.current
    val permissionStates by viewModel.permissionStates.collectAsState()

    // 当前正在请求的权限类型
    var currentPermissionType by remember { mutableStateOf<PermissionType?>(null) }

    // 权限请求 Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val current = currentPermissionType ?: return@rememberLauncherForActivityResult

        // 检查是否永久拒绝（未授权且不应再显示请求理由）
        val activity = context.findActivity()
        val isPermanentlyDenied = !isGranted &&
            activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                current.permission
            ) &&
            ContextCompat.checkSelfPermission(context, current.permission) !=
                PackageManager.PERMISSION_GRANTED

        val newState = when {
            isGranted -> PermissionState.GRANTED
            isPermanentlyDenied -> PermissionState.PERMANENTLY_DENIED
            else -> PermissionState.DENIED
        }

        viewModel.updatePermissionState(current, newState)
        currentPermissionType = null
    }

    // Box 包裹 + Column.align(Center) + verticalScroll：
    // 内容少时垂直居中显示，内容多时可滚动查看
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
        Text(
            text = "权限设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "为了提供完整的功能体验，请允许以下权限",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 权限列表
        PermissionType.entries.forEach { permissionType ->
            val state = permissionStates[permissionType] ?: PermissionState.NOT_REQUESTED
            PermissionItem(
                permissionType = permissionType,
                state = state,
                onRequestClick = {
                    currentPermissionType = permissionType
                    permissionLauncher.launch(permissionType.permission)
                },
                onGoToSettings = {
                    // 跳转到应用设置页
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 完成按钮
        val allRequested = viewModel.allPermissionsRequested()

        Button(
            onClick = onComplete,
            enabled = allRequested && !isCompleting,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allRequested && !isCompleting) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (allRequested) "完成" else "请先处理权限请求",
                fontWeight = FontWeight.Medium
            )
        }

        if (!allRequested) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "你可以拒绝权限，但需要逐个处理后再继续",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        } // 关闭 Column
    } // 关闭 Box
}

/**
 * 权限项卡片
 *
 * @param permissionType 权限类型
 * @param state 权限状态
 * @param onRequestClick 请求权限按钮回调
 * @param onGoToSettings 去设置按钮回调
 */
@Composable
private fun PermissionItem(
    permissionType: PermissionType,
    state: PermissionState,
    onRequestClick: () -> Unit,
    onGoToSettings: () -> Unit
) {
    val (icon, iconColor, statusText) = when (state) {
        PermissionState.NOT_REQUESTED -> Triple("⏳", MaterialTheme.colorScheme.onSurfaceVariant, "未请求")
        PermissionState.GRANTED -> Triple("✓", Color(0xFF4CAF50), "已授权")
        PermissionState.DENIED -> Triple("✗", Color(0xFFE53935), "已拒绝")
        PermissionState.PERMANENTLY_DENIED -> Triple("⚠", Color(0xFFFF9800), "永久拒绝")
    }

    val emoji = when (permissionType) {
        PermissionType.NOTIFICATION -> "🔔"
        PermissionType.STORAGE -> "📁"
        PermissionType.MICROPHONE -> "🎤"
        PermissionType.LOCATION -> "📍"
        PermissionType.CAMERA -> "📷"
    }

    val description = when (permissionType) {
        PermissionType.NOTIFICATION -> "待办/日期提醒"
        PermissionType.STORAGE -> "图片/备份导出"
        PermissionType.MICROPHONE -> "语音输入待办"
        PermissionType.LOCATION -> "位置提醒"
        PermissionType.CAMERA -> "拍照记录灵感图片"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = emoji,
                    fontSize = 28.sp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = permissionType.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = icon,
                        fontSize = 18.sp,
                        color = iconColor
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = iconColor
                    )
                }

                // 操作按钮
                when (state) {
                    PermissionState.NOT_REQUESTED -> {
                        TextButton(
                            onClick = onRequestClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("请求权限", fontSize = 12.sp)
                        }
                    }
                    PermissionState.DENIED -> {
                        TextButton(
                            onClick = onRequestClick,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("稍后设置", fontSize = 12.sp)
                        }
                    }
                    PermissionState.PERMANENTLY_DENIED -> {
                        TextButton(
                            onClick = onGoToSettings,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                        ) {
                            Text("去设置", fontSize = 12.sp)
                        }
                    }
                    PermissionState.GRANTED -> {
                        // 已授权，无需按钮
                    }
                }
            }
        }
    }
}

/**
 * 从 Context 查找 Activity（安全转换，避免直接 cast 导致崩溃）
 */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
