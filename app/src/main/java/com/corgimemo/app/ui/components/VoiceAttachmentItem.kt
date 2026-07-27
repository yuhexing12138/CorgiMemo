package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.model.VoiceAttachment
import com.corgimemo.app.util.VoicePlayer

/**
 * 语音附件显示项（v2026-07-27 P6 改造版）
 *
 * **v2026-07-27 改造动机**：
 * 原 [DraggableVoiceAttachment]（v2，237 行）实现完整拖拽系统：
 * - pointerInput 长按检测 + 拖拽偏移
 * - graphicsLayer 缩放 + translation 跟随手指
 * - 拖拽中简化 UI（仅图标 + 时长）
 * - 拖拽中阴影 + 跨行目标提示
 * - 拖拽开始/结束/取消的回调（3 个）
 * - 拖拽过程中暂停/恢复播放请求
 *
 * 改造后：
 * - 删除所有拖拽相关代码（由 Reorderable 库接管）
 * - 保留：完整播放器 UI（波形图 + 播放控制）+ × 删除按钮
 * - 拖拽视觉由外层 [androidx.compose.foundation.lazy.LazyRow] + [sh.calvin.reorderable.ReorderableItem] 接管
 * - 库内置 zIndex + scale 1.08 + 阴影，外层不需要重复实现
 * - 删除"拖拽中简化 UI"模式：与 ImageAttachmentItem 保持一致，isDragging 时仅半透明占位
 *
 * **新 API 形态**：
 * ```kotlin
 * // 父组件（CheckboxEditText.kt 内）使用模式：
 * LazyRow(state = lazyListState) {
 *     items(line.voiceAttachments, key = { it.path }) { voice ->
 *         ReorderableItem(state = reorderableState, key = voice.path) { isDragging ->
 *             VoiceAttachmentItem(
 *                 voiceAttachment = voice,
 *                 isDragging = isDragging,
 *                 voicePlayer = voicePlayerMap["${lineIndex}_${voiceIndex}"]!!,
 *                 onClick = { /* 切换播放/暂停 */ },
 *                 onDelete = { /* 释放资源 + 通知外层 */ },
 *                 modifier = Modifier.draggableHandle(
 *                     onDragStarted = { /* 暂停语音 */ }
 *                 )
 *             )
 *         }
 *     }
 * }
 * ```
 *
 * **isDragging 行为**：
 * - true：半透明占位 + 暂停播放（由外层 onDragStarted 处理）
 * - false：完整播放器 UI + × 删除按钮
 *
 * @param voiceAttachment 语音附件数据
 * @param isDragging 是否正在被拖拽（由外层 ReorderableItem 提供）
 * @param voicePlayer 语音播放器实例（由外层 LazyRow 外提供，确保与 LazyRow 生命周期一致）
 * @param onClick 点击回调（外层通常用于切换播放/暂停）
 * @param onDelete 删除回调（外层应释放 VoicePlayer 资源 + 从 voiceAttachments 中移除）
 * @param modifier 由父组件传入的 Modifier（必须包含 .draggableHandle() 才能拖）
 */
@Composable
fun VoiceAttachmentItem(
    voiceAttachment: VoiceAttachment,
    isDragging: Boolean = false,
    voicePlayer: VoicePlayer? = null,
    onClick: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    /**
     * 完整播放器 UI：与原 DraggableVoiceAttachment 正常状态一致
     *
     * v2026-07-27 改造说明：
     * - 删除"拖拽中简化 UI"分支（isDragging 时仍显示完整 UI）
     * - 拖拽中半透明由外层 Box 的 background 处理
     * - 跨行目标提示由外层 isDropTarget 状态处理（v2026-07-27 暂未实现跨行拖拽）
     *
     * VoicePlayerComponent 内部：
     * - 波形图（基于真实音频数据）
     * - 点击整行播放/暂停
     * - × 删除按钮（由 onDelete 透传）
     * - 递增时间显示
     */
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .then(
                /**
                 * 拖拽中：原位置半透明占位（保持布局稳定）
                 * 与 ImageAttachmentItem 行为对齐，让外层浮起视觉接管
                 */
                if (isDragging) {
                    Modifier.background(Color.Black.copy(alpha = 0.1f))
                } else {
                    Modifier
                }
            )
    ) {
        if (voicePlayer != null) {
            VoicePlayerComponent(
                voicePlayer = voicePlayer,
                filePath = voiceAttachment.path,
                totalDuration = voiceAttachment.duration,
                onDelete = { onDelete() },
                isHighlighted = false,
                modifier = Modifier.fillMaxWidth(),
                isVisible = true
            )
        } else {
            /**
             * 兜底：voicePlayer 为 null 时显示占位文本
             * 正常情况下外层 LazyRow 外的 voicePlayerMap.getOrPut 保证始终非空
             */
            Box(modifier = Modifier.fillMaxSize())
        }
    }
}
