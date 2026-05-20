package com.corgimemo.app.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.corgimemo.app.backup.BackupFrequency
import com.corgimemo.app.backup.BackupScheduler
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import kotlinx.coroutines.launch

/**
 * 自动备份配置对话框
 *
 * @param onDismiss 取消回调
 */
@Composable
fun AutoBackupSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isEnabled by remember { mutableStateOf(false) }
    var selectedFrequency by remember { mutableStateOf(BackupFrequency.WEEKLY) }
    var selectedRetainCount by remember { mutableIntStateOf(5) }
    var isLoaded by remember { mutableStateOf(false) }
    var showFrequencyDialog by remember { mutableStateOf(false) }
    var showRetainDialog by remember { mutableStateOf(false) }
    var backupStatus by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                triggerBackupNow(context) { status ->
                    backupStatus = status
                }
            }
        } else {
            backupStatus = "需要通知权限才能显示备份通知"
        }
    }

    if (!isLoaded) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            val preferences = CorgiPreferences.getInstance(context)
            isEnabled = preferences.getAutoBackupEnabled()
            selectedFrequency = BackupFrequency.fromValue(preferences.getAutoBackupFrequency())
            selectedRetainCount = preferences.getAutoBackupKeepCount()
            isLoaded = true
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = "自动备份设置",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "启用自动备份",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "自动备份数据到 Downloads 目录",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = isEnabled,
                        onCheckedChange = { checked ->
                            isEnabled = checked
                            scope.launch {
                                val preferences = CorgiPreferences.getInstance(context)
                                preferences.setAutoBackupEnabled(checked)
                                if (checked) {
                                    BackupScheduler.schedule(context, selectedFrequency)
                                } else {
                                    BackupScheduler.cancel(context)
                                }
                            }
                        }
                    )
                }

                if (isEnabled) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFrequencyDialog = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "备份频率",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = selectedFrequency.displayName,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.Transparent
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showRetainDialog = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "保留版本数",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "最近 $selectedRetainCount 个",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                ) {
                                    scope.launch {
                                        triggerBackupNow(context) { status ->
                                            backupStatus = status
                                        }
                                    }
                                } else {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                scope.launch {
                                    triggerBackupNow(context) { status ->
                                        backupStatus = status
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔄 立即备份")
                    }
                }

                backupStatus?.let { status ->
                    Text(
                        text = status,
                        fontSize = 12.sp,
                        color = if (status.contains("成功")) Color(0xFF10B981) else Color(0xFFEF4444)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss() }) {
                Text("完成")
            }
        }
    )

    if (showFrequencyDialog) {
        val options = BackupFrequency.entries.map { it to it.displayName }
        OptionSelectDialog(
            title = "选择备份频率",
            options = options,
            selected = selectedFrequency,
            onSelect = { frequency ->
                selectedFrequency = frequency
                scope.launch {
                    val preferences = CorgiPreferences.getInstance(context)
                    preferences.saveAutoBackupFrequency(frequency.value)
                    if (isEnabled) {
                        BackupScheduler.schedule(context, frequency)
                    }
                }
                showFrequencyDialog = false
            },
            onDismiss = { showFrequencyDialog = false }
        )
    }

    if (showRetainDialog) {
        val options = listOf(3, 5, 10)
        OptionSelectDialog(
            title = "选择保留版本数",
            options = options.map { it to "最近 $it 个" },
            selected = selectedRetainCount,
            onSelect = { count ->
                selectedRetainCount = count
                scope.launch {
                    val preferences = CorgiPreferences.getInstance(context)
                    preferences.saveAutoBackupKeepCount(count)
                }
                showRetainDialog = false
            },
            onDismiss = { showRetainDialog = false }
        )
    }
}

/**
 * 通用选项选择对话框
 *
 * @param title 对话框标题
 * @param options 选项列表（值-显示名对）
 * @param selected 当前选中的值
 * @param onSelect 选择回调
 * @param onDismiss 取消回调
 */
@Composable
fun <T> OptionSelectDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options) { (value, label) ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (value == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                color = if (value == selected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (value == selected) {
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 立即触发备份
 */
private suspend fun triggerBackupNow(
    context: android.content.Context,
    onStatus: (String) -> Unit
) {
    onStatus("正在备份...")
    BackupScheduler.triggerNow(context)
    onStatus("备份已开始，请查看通知")
}
