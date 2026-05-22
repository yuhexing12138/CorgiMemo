package com.corgimemo.app.ui.components

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.*

/**
 * 录音权限状态
 */
enum class RecordAudioPermissionState {
    GRANTED,        // 权限已授予
    SHOULD_REQUEST, // 应该请求权限
    DENIED,         // 权限被拒绝
    SHOW_RATIONALE  // 显示权限说明
}

/**
 * 录音权限检查器
 * 返回当前权限状态，由调用者决定如何处理
 *
 * @param onPermissionState 权限状态变化回调
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordAudioPermissionChecker(
    onPermissionState: (RecordAudioPermissionState) -> Unit
) {
    val recordAudioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    LaunchedEffect(recordAudioPermissionState.status) {
        val state = when {
            recordAudioPermissionState.status.isGranted -> RecordAudioPermissionState.GRANTED
            !recordAudioPermissionState.status.shouldShowRationale -> RecordAudioPermissionState.DENIED
            else -> RecordAudioPermissionState.SHOULD_REQUEST
        }
        onPermissionState(state)
    }
}

/**
 * 录音权限请求对话框
 * 显示权限说明并请求权限
 *
 * @param onDismiss 用户取消时的回调
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordAudioPermissionRequester(
    onDismiss: () -> Unit
) {
    val recordAudioPermissionState = rememberPermissionState(
        android.Manifest.permission.RECORD_AUDIO
    )

    when {
        // 权限已授予 - 不显示任何内容
        recordAudioPermissionState.status.isGranted -> {}

        // 权限被拒绝（用户点击了"不再询问"）- 显示引导对话框
        !recordAudioPermissionState.status.shouldShowRationale -> {
            PermissionRationaleDialog(
                onConfirm = onDismiss,
                onDismiss = onDismiss
            )
        }

        // 需要显示权限说明
        else -> {
            PermissionExplanationDialog(
                onRequestPermission = {
                    recordAudioPermissionState.launchPermissionRequest()
                },
                onDismiss = onDismiss
            )
        }
    }
}

/**
 * 权限说明对话框
 * 在首次请求权限前向用户解释为什么需要此权限
 *
 * @param onRequestPermission 用户同意后请求权限的回调
 * @param onDismiss 用户取消时的回调
 */
@Composable
private fun PermissionExplanationDialog(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "麦克风",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "需要录音权限",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "语音备注功能需要访问您的麦克风才能录制语音。\n\n" +
                        "录制的音频将保存在您的设备本地，不会上传到任何服务器。",
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("允许")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("暂不允许")
            }
        }
    )
}

/**
 * 权限引导对话框
 * 当权限被永久拒绝时，引导用户到系统设置中手动开启
 *
 * @param onConfirm 确认去设置的回调
 * @param onDismiss 取消时的回调
 */
@Composable
private fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "麦克风",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text(
                text = "🔊 需要录音权限",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "语音备注功能需要访问麦克风权限，但该权限当前已被禁止。\n\n" +
                            "请在系统设置中手动开启此权限以使用语音录制功能。",
                    textAlign = TextAlign.Start
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "开启步骤：",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text("1. 点击下方「去设置」按钮", style = MaterialTheme.typography.bodyMedium)
                        Text("2. 找到「麦克风」权限选项", style = MaterialTheme.typography.bodyMedium)
                        Text("3. 开启权限后返回应用", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("去设置开启")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 打开系统设置页面的 Intent
 * 用于跳转到应用的权限设置界面
 *
 * @return Intent 对象，指向当前应用的设置详情页
 */
fun openAppSettingsIntent(context: android.content.Context): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
