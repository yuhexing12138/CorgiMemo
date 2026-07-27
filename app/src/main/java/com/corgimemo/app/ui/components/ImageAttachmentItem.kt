package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/**
 * 图片附件显示项（v2026-07-27 P5 改造版）
 *
 * **v2026-07-27 改造动机**：
 * 原 [DraggableImageAttachment]（v5，600+ 行）实现复杂拖拽系统：
 * - Popup 浮层（独立窗口，跟随手指）
 * - pointerInput 长按检测 + 拖拽偏移
 * - graphicsLayer 拖拽视觉（缩放、阴影、淡入淡出）
 * - SWAP/INSERT_BEFORE/INSERT_AFTER 三种落点语义
 * - 同行移动光标（CursorIndicator）
 *
 * 改造后：
 * - 删除所有拖拽相关代码（由 Reorderable 库接管）
 * - 保留：80dp × 80dp 方形缩略图、× 删除按钮、点击查看大图
 * - 拖拽视觉由外层 [androidx.compose.foundation.lazy.LazyRow] + [sh.calvin.reorderable.ReorderableItem] 接管
 * - 库内置 zIndex + scale 1.08 + 阴影，外层不需要重复实现
 *
 * **新 API 形态**：
 * ```kotlin
 * // 父组件（CheckboxEditText.kt 内）使用模式：
 * LazyRow(state = lazyListState) {
 *     items(line.imagePaths, key = { it }) { imagePath ->
 *         ReorderableItem(state = reorderableState, key = imagePath) { isDragging ->
 *             Box(modifier = Modifier.zIndex(if (isDragging) 1f else 0f).graphicsLayer { ... }) {
 *                 ImageAttachmentItem(
 *                     imagePath = imagePath,
 *                     isDragging = isDragging,
 *                     onClick = { onImageClick(it) },
 *                     onDelete = { onDeleteImage(it) },
 *                     modifier = Modifier.draggableHandle(...) // 由父组件绑
 *                 )
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * **isDragging 行为**：
 * - true：原图位置半透明 + 隐藏删除按钮（让位给拖拽浮起视觉）
 * - false：完整图片 + 显示删除按钮
 *
 * @param imagePath 图片本地路径（File 路径或 URI 字符串）
 * @param isDragging 是否正在被拖拽（由外层 ReorderableItem 提供）
 * @param onClick 点击图片回调（通常用于打开大图查看）
 * @param onDelete 点击 × 按钮回调（外层应从 imagePaths 中移除该路径）
 * @param modifier 由父组件传入的 Modifier（必须包含 .draggableHandle() 才能拖）
 */
@Composable
fun ImageAttachmentItem(
    imagePath: String,
    isDragging: Boolean = false,
    onClick: (String) -> Unit = {},
    onDelete: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    /**
     * 80dp × 80dp 方形缩略图
     *
     * 沿用原 DraggableImageAttachment 的尺寸：
     * - 80dp 与设置项"单行图片上限"协调（10/20 张时可容纳多行）
     * - 8dp 圆角与 todo 卡片风格统一
     * - 拖拽时（isDragging=true）整体半透明（0.3），保持布局稳定让位于浮起视觉
     */
    val attachmentSize = 80.dp

    Box(
        modifier = modifier
            .size(attachmentSize)
            .clip(RoundedCornerShape(8.dp))
            .then(
                /**
                 * 拖拽中：原图位置半透明占位（保持布局稳定，避免高度跳变）
                 * 库接管拖拽浮起视觉后，本组件不再需要 graphicsLayer 缩放
                 */
                if (isDragging) {
                    Modifier.background(Color.Black.copy(alpha = 0.1f))
                } else {
                    Modifier
                }
            )
    ) {
        /**
         * 图片渲染：80dp × 80dp 方形，ContentScale.Crop 填充
         *
         * Coil 配置：
         * - Scale.FILL：填充整个容器，配合 ContentScale.Crop 裁剪超出部分
         * - crossfade：加载完成有平滑过渡
         *
         * 点击行为：
         * - 正常状态：可点击 → onClick
         * - 拖拽中：不可点击（避免与 draggableHandle 手势冲突）
         */
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imagePath)
                .crossfade(true)
                .scale(Scale.FILL)
                .build(),
            contentDescription = "图片附件",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isDragging) Modifier.clickable { onClick(imagePath) } else Modifier
                )
        )

        /**
         * × 删除按钮（仅正常状态显示）
         *
         * 位置：右上角，4dp 内边距
         * 样式：18dp 圆形按钮，黑色半透明背景，白色 × 字
         *
         * 拖拽时隐藏原因：避免与拖拽手势冲突 + 视觉清爽
         */
        if (!isDragging) {
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(18.dp)
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable { onDelete(imagePath) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "\u00D7",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
