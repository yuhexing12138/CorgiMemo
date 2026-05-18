package com.corgimemo.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.corgimemo.app.animation.GreetingManager
import com.corgimemo.app.model.UserType
import com.corgimemo.app.viewmodel.SettingsViewModel

/**
 * 设置页面
 * 管理音效反馈、触觉反馈和用户身份设置
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val hapticEnabled by viewModel.hapticEnabled.collectAsState()
    val userType by viewModel.userType.collectAsState()

    var showUserTypeDialog by remember { mutableStateOf(false) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var pendingUserType by remember { mutableStateOf<UserType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "设置",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 音效反馈开关
            SettingSwitchCard(
                title = "音效反馈",
                description = "触摸柯基时播放轻快音效",
                checked = soundEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setSoundEnabled(enabled)
                }
            )

            // 触觉反馈开关
            SettingSwitchCard(
                title = "触觉反馈",
                description = "触摸柯基时震动",
                checked = hapticEnabled,
                onCheckedChange = { enabled ->
                    viewModel.setHapticEnabled(enabled)
                }
            )

            // 身份设置
            SettingItemCard(
                title = "身份设置",
                description = "当前身份：${getUserTypeName(userType)}",
                onClick = {
                    showUserTypeDialog = true
                }
            )
        }
    }

    // 身份选择弹窗
    if (showUserTypeDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUserTypeDialog = false },
            title = {
                Text(
                    text = "选择身份",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    UserTypeSelectCard(
                        userType = UserType.WORKER,
                        isSelected = userType == UserType.WORKER,
                        onClick = {
                            if (userType != UserType.WORKER) {
                                pendingUserType = UserType.WORKER
                                showUserTypeDialog = false
                                showConfirmDialog = true
                            }
                        }
                    )
                    UserTypeSelectCard(
                        userType = UserType.STUDENT,
                        isSelected = userType == UserType.STUDENT,
                        onClick = {
                            if (userType != UserType.STUDENT) {
                                pendingUserType = UserType.STUDENT
                                showUserTypeDialog = false
                                showConfirmDialog = true
                            }
                        }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showUserTypeDialog = false }
                ) {
                    Text(text = "取消")
                }
            }
        )
    }

    // 确认切换身份弹窗
    if (showConfirmDialog && pendingUserType != null) {
        val newUserType = pendingUserType!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showConfirmDialog = false
                pendingUserType = null
            },
            title = {
                Text(
                    text = "切换身份",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "确定要切换为「${getUserTypeName(newUserType)}」吗？",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "切换后的问候语预览",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = GreetingManager.getIdentityPreviewGreeting(newUserType),
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setUserType(newUserType)
                        showConfirmDialog = false
                        pendingUserType = null
                    }
                ) {
                    Text(text = "确认")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showConfirmDialog = false
                        showUserTypeDialog = true
                        pendingUserType = null
                    }
                ) {
                    Text(text = "取消")
                }
            }
        )
    }
}

/**
 * 设置开关卡片
 *
 * @param title 标题
 * @param description 描述
 * @param checked 是否开启
 * @param onCheckedChange 开关状态变化回调
 */
@Composable
fun SettingSwitchCard(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 16.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * 设置项卡片
 * 显示标题和描述，可点击
 *
 * @param title 标题
 * @param description 描述
 * @param onClick 点击回调
 */
@Composable
fun SettingItemCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = "›",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 身份选择卡片
 *
 * @param userType 用户类型
 * @param isSelected 是否选中
 * @param onClick 点击回调
 */
@Composable
fun UserTypeSelectCard(
    userType: UserType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val descriptionColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when (userType) {
                    UserType.WORKER -> "💼"
                    UserType.STUDENT -> "📚"
                },
                fontSize = 28.sp
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = getUserTypeName(userType),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = when (userType) {
                        UserType.WORKER -> "职场打工人专属问候"
                        UserType.STUDENT -> "学生党专属鼓励"
                    },
                    fontSize = 13.sp,
                    color = descriptionColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (isSelected) {
                Text(
                    text = "✓",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
        }
    }
}

/**
 * 获取用户类型的显示名称
 *
 * @param userType 用户类型
 * @return 显示名称
 */
fun getUserTypeName(userType: UserType): String {
    return when (userType) {
        UserType.WORKER -> "上班族"
        UserType.STUDENT -> "学生"
    }
}
