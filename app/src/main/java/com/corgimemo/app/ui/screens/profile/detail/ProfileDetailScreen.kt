package com.corgimemo.app.ui.screens.profile.detail

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.ui.components.AvatarSourceSheet
import com.corgimemo.app.ui.components.CircularImageCropper
import com.corgimemo.app.ui.components.UserAvatar
import com.corgimemo.app.ui.components.cropCircularBitmap
import com.corgimemo.app.ui.screens.profile.detail.components.ProfileDetailRow
import com.corgimemo.app.util.AvatarStorage
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * 个人信息页
 *
 * 布局（单列 LazyColumn，spacedBy 12dp）：
 * 1. 大头像卡片（120dp UserAvatar + 名字 + 操作提示）
 * 2. 性别选择行（点击弹"男 / 女 / 其他 / 保密"对话框）
 * 3. 手机号（占位，enabled=false）
 * 4. 设置邮箱（占位，enabled=false）
 * 5. 修改密码（占位，enabled=false）
 * 6. 登录设备管理（占位，enabled=false）
 *
 * 头像更换流程：
 * - 点击大头像 → 弹 AvatarSourceSheet
 * - 4 路径来源（拍照 / 相册 / 预设库）→ URI → 裁剪 → 保存
 * - 预设库 → 直接 savePresetAvatar（无需裁剪）
 *
 * 性别 / 名字 设置：弹 AlertDialog → 调 ViewModel 写库 → StateFlow 推送 → UI 重组
 *
 * @param onBack 返回回调（popBackStack）
 * @param viewModel 个人信息页 ViewModel（Hilt 注入）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    onBack: () -> Unit,
    viewModel: ProfileDetailViewModel = hiltViewModel()
) {
    val corgiData by viewModel.corgiData.collectAsState()
    val avatarBitmap by viewModel.avatarBitmap.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ===== 弹窗状态 =====
    var showAvatarSourceSheet by remember { mutableStateOf(false) }
    var showCropper by remember { mutableStateOf(false) }
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showGenderDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }

    // ===== UI =====
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人信息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // ① 大头像卡片
            item {
                BigAvatarCard(
                    corgiData = corgiData,
                    avatarBitmap = avatarBitmap,
                    onAvatarClick = { showAvatarSourceSheet = true },
                    onNameClick = { showNameDialog = true },
                    onSignatureClick = { showSignatureDialog = true }
                )
            }
            // ② 性别（可设置）
            item {
                ProfileDetailRow(
                    label = "性别",
                    value = when (corgiData?.gender) {
                        "MALE" -> "男"
                        "FEMALE" -> "女"
                        "OTHER" -> "其他"
                        else -> null
                    },
                    placeholder = "保密",
                    onClick = { showGenderDialog = true }
                )
            }
            // ③ 手机号（占位，账号系统未接，禁用点击）
            item { ProfileDetailRow(label = "手机号", placeholder = "暂未设置", enabled = false) }
            // ④ 设置邮箱（占位）
            item { ProfileDetailRow(label = "设置邮箱", placeholder = "暂未设置", enabled = false) }
            // ⑤ 修改密码（占位）
            item { ProfileDetailRow(label = "修改密码", placeholder = "暂未设置", enabled = false) }
            // ⑥ 登录设备管理（占位）
            item { ProfileDetailRow(label = "登录设备管理", placeholder = "暂未设置", enabled = false) }
        }
    }

    // ===== 头像源选择 =====
    // 拍照 / 选图 → 拿到 URI → ViewModel 解码 Bitmap → 进入裁剪环节
    // 预设库 → ViewModel.savePresetAvatar → 关闭 BottomSheet
    AvatarSourceSheet(
        visible = showAvatarSourceSheet,
        onDismiss = { showAvatarSourceSheet = false },
        onPhotoTaken = { uri ->
            viewModel.onPhotoUriReceived(context, uri) { bitmap ->
                sourceBitmap = bitmap
                showCropper = true
            }
        },
        onPhotoPicked = { uri ->
            viewModel.onPhotoUriReceived(context, uri) { bitmap ->
                sourceBitmap = bitmap
                showCropper = true
            }
        },
        onPresetSelected = { key ->
            viewModel.savePresetAvatar(key) { showAvatarSourceSheet = false }
        }
    )

    // ===== 圆形裁剪 =====
    // 简化版：直接用 fitScale + 0 偏移裁剪（与裁剪器初次显示一致）
    // CropperDialog 内部持有的 scale/offset 不会随手势同步（手势仅用于视觉预览）
    // 后续可扩展：把 scale/offset 提升到父级，传入 CircularImageCropper 做双向同步
    val bitmap = sourceBitmap
    if (showCropper && bitmap != null) {
        CropperDialog(
            sourceBitmap = bitmap,
            onConfirm = { croppedBitmap ->
                scope.launch {
                    val path = AvatarStorage.saveAvatar(context, croppedBitmap)
                    viewModel.saveAvatarPath(context, path) {
                        showCropper = false
                        sourceBitmap = null
                        showAvatarSourceSheet = false
                    }
                }
            },
            onCancel = {
                showCropper = false
                sourceBitmap = null
            }
        )
    }

    // ===== 改名对话框 =====
    if (showNameDialog) {
        NameEditDialog(
            currentName = corgiData?.name ?: "",
            onConfirm = { newName ->
                viewModel.updateName(newName)
                showNameDialog = false
            },
            onDismiss = { showNameDialog = false }
        )
    }

    // ===== 签名编辑对话框 =====
    if (showSignatureDialog) {
        SignatureEditDialog(
            currentSignature = corgiData?.signature ?: "记录生活，刻下美好",
            onConfirm = { newSignature ->
                viewModel.updateSignature(newSignature)
                showSignatureDialog = false
            },
            onDismiss = { showSignatureDialog = false }
        )
    }

    // ===== 性别选择对话框 =====
    if (showGenderDialog) {
        GenderPickerDialog(
            currentGender = corgiData?.gender,
            onSelect = { gender ->
                viewModel.updateGender(gender)
                showGenderDialog = false
            },
            onDismiss = { showGenderDialog = false }
        )
    }
}

// ==================== 子组件 ====================

/**
 * 大头像卡片
 *
 * 视觉：20dp 圆角卡片，elevation 2dp，居中布局
 * 头像 120dp + 名字 20sp Bold + 签名 13sp
 *
 * @param corgiData 当前柯基数据（null 时显示占位）
 * @param onAvatarClick 点击头像回调
 * @param onNameClick 点击名字回调
 * @param onSignatureClick 点击签名回调
 */
@Composable
private fun BigAvatarCard(
    corgiData: CorgiData?,
    avatarBitmap: Bitmap?,
    onAvatarClick: () -> Unit,
    onNameClick: () -> Unit,
    onSignatureClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UserAvatar(
                nickname = corgiData?.name ?: "用户",
                avatarPath = corgiData?.avatarPath,
                preloadedBitmap = avatarBitmap,
                size = 120.dp,
                onClick = onAvatarClick
            )
            Spacer(Modifier.height(12.dp))
            // 名字：占满宽度 + TextAlign.Center，确保长名字/换行时也始终居中显示
            Text(
                text = corgiData?.name ?: "未设置",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNameClick)
            )
            // 签名：同样占满宽度 + 居中，与名字视觉对齐
            Text(
                text = corgiData?.signature ?: "记录生活，刻下美好",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSignatureClick)
                    .padding(top = 4.dp)
            )
        }
    }
}

/**
 * 圆形裁剪全屏对话框
 *
 * 流程：
 * 1. 复用 CircularImageCropper 提供视觉预览（用户可拖动/缩放，但仅视觉反馈）
 * 2. "取消" → 关闭对话框
 * 3. "使用" → 用 fitScale + 0 偏移裁剪（与初次显示一致），返回 512x512 PNG Bitmap
 *
 * 简化说明：本实现未将裁剪器的 scale/offset 提升到此处共享，
 * 因此用户的手势（缩放/平移）只影响视觉预览，最终输出以初始 fit 状态为准。
 * 这是任务 9 的"简化版"实现，后续可扩展为双向同步。
 *
 * @param sourceBitmap 待裁剪源图
 * @param onConfirm 确认回调，传入裁剪后的 Bitmap
 * @param onCancel 取消回调
 */
@Composable
private fun CropperDialog(
    sourceBitmap: Bitmap,
    onConfirm: (Bitmap) -> Unit,
    onCancel: () -> Unit
) {
    val density = LocalDensity.current
    val cropSizePx = with(density) { 320.dp.toPx() }

    // 共享状态：CircularImageCropper 写入，"使用"按钮读取
    // 确保用户缩放/拖动后的实际值用于裁剪
    val scaleState = remember { mutableFloatStateOf(1f) }
    val offsetXState = remember { mutableFloatStateOf(0f) }
    val offsetYState = remember { mutableFloatStateOf(0f) }
    val canvasSizeState = remember { mutableStateOf(IntSize.Zero) }

    Dialog(onDismissRequest = onCancel) {
        Card(
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 裁剪区域：增大到 440dp，裁剪框 320dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp)
                ) {
                    CircularImageCropper(
                        sourceBitmap = sourceBitmap,
                        cropSize = 320.dp,
                        scaleState = scaleState,
                        offsetXState = offsetXState,
                        offsetYState = offsetYState,
                        canvasSizeState = canvasSizeState,
                        onCropComplete = {},
                        onCancel = onCancel
                    )
                }
                // 按钮区域：不透明背景 + 降低高度，确保不被图片覆盖
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onCancel) { Text("取消") }
                    TextButton(
                        onClick = {
                            // 使用用户实际手势值（scale/offset）裁剪，与预览一致
                            // canvasSize 用真实画布尺寸，确保裁剪框位置计算正确
                            val canvasSize = canvasSizeState.value
                            val safeCanvasSize = if (canvasSize.width > 0 && canvasSize.height > 0) {
                                canvasSize
                            } else {
                                // 兜底：画布尺寸未就绪时用方形虚拟画布
                                IntSize(cropSizePx.toInt(), cropSizePx.toInt())
                            }
                            val cropped = cropCircularBitmap(
                                source = sourceBitmap,
                                scale = scaleState.value,
                                offsetX = offsetXState.value,
                                offsetY = offsetYState.value,
                                canvasSize = safeCanvasSize,
                                cropSizePx = cropSizePx
                            )
                            onConfirm(cropped)
                        }
                    ) { Text("使用") }
                }
            }
        }
    }
}

/**
 * 改名对话框
 *
 * 限制：最多 20 字符（防止 UI 溢出）
 * 验证：非空白才能确认
 *
 * @param currentName 当前名字（对话框初始值）
 * @param onConfirm 确认回调，传入新名字
 * @param onDismiss 取消回调
 */
@Composable
private fun NameEditDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改昵称") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(20) },
                singleLine = true,
                label = { Text("昵称（最多 20 字）") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 签名编辑对话框
 *
 * 限制：最多 30 字符
 * 验证：非空白才能确认
 *
 * @param currentSignature 当前签名（对话框初始值）
 * @param onConfirm 确认回调，传入新签名
 * @param onDismiss 取消回调
 */
@Composable
private fun SignatureEditDialog(
    currentSignature: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentSignature) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改签名") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(30) },
                singleLine = true,
                label = { Text("签名（最多 30 字）") }
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 性别选择对话框
 *
 * 4 个选项：MALE / FEMALE / OTHER / null（保密）
 * 当前选中项前加 "✓" 标记
 *
 * @param currentGender 当前性别字符串（null = 保密）
 * @param onSelect 选中回调
 * @param onDismiss 关闭回调
 */
@Composable
private fun GenderPickerDialog(
    currentGender: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择性别") },
        text = {
            Column {
                listOf(
                    "MALE" to "男",
                    "FEMALE" to "女",
                    null to "保密"
                ).forEach { (key, label) ->
                    TextButton(
                        onClick = { onSelect(key) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (currentGender == key) "✓ $label" else label,
                            color = if (currentGender == key)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}
