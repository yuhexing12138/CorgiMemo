package com.corgimemo.app.ui.screens.inspiration.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 标签输入组件
 * 用于在灵感编辑页面中添加和管理标签，支持横向滚动显示和内联输入
 *
 * 功能说明：
 * - 横向显示已有标签列表（每个标签带删除按钮）
 * - 点击标签的 × 可删除该标签
 * - 输入框显示由父组件通过 isAdding 参数控制（状态上提）
 * - 父组件的标签按钮触发 isAdding=true，本组件显示内联输入框
 * - "确定"按钮确认添加新标签并回调 onAddingChange(false) 关闭输入框
 *
 * @param tags 当前标签列表
 * @param onTagsChange 标签变更回调函数
 * @param modifier 修饰符
 * @param isAdding 是否显示输入框（由父组件托管）
 * @param onAddingChange 输入框显示状态变更回调
 */
@Composable
fun TagInputField(
    tags: List<String>,
    onTagsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    /** 是否显示标签输入框（状态由父组件托管） */
    isAdding: Boolean = false,
    /** 输入框显示状态变更回调 */
    onAddingChange: (Boolean) -> Unit = {}
) {
    /** 当前输入的新标签内容 */
    var newTagText by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        /**
         * 标签列表行
         * 使用 LazyRow 实现横向滚动，当标签过多时可以左右滑动查看
         */
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            /** 已有标签横向列表 */
            if (tags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(items = tags, key = { it }) { tag ->
                        /**
                         * 单个标签项
                         * 暖橙色背景 #FFF3E0，显示 "#标签名 ×"
                         */
                        TagChip(
                            tag = tag,
                            onDelete = {
                                /** 删除指定标签 */
                                val updatedTags = tags.toMutableList()
                                updatedTags.remove(tag)
                                onTagsChange(updatedTags)
                            }
                        )
                    }
                }
            } else {
                /** 无标签时的占位提示 */
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        /**
         * 内联输入框
         * 仅在 isAdding 为 true 时显示，用于输入新标签
         */
        if (isAdding) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                /**
                 * 新标签输入框
                 * 单行输入，回车键确认添加
                 */
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    label = { Text("输入标签名称") },
                    placeholder = { Text("例如：产品、设计、想法") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                /**
                 * 确认按钮：点击添加新标签并关闭输入框
                 */
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(UiColors.Primary)
                        .clickable {
                            /** 验证输入不为空且不重复 */
                            if (newTagText.isNotBlank() && newTagText !in tags) {
                                val updatedTags = tags.toMutableList()
                                updatedTags.add(newTagText.trim())
                                onTagsChange(updatedTags)
                            }
                            /** 重置状态 */
                            newTagText = ""
                            onAddingChange(false)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "确定",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * 标签芯片组件
 * 显示单个标签，带有删除按钮
 *
 * @param tag 标签文本
 * @param onDelete 删除回调函数
 * @param modifier 修饰符
 */
@Composable
private fun TagChip(
    tag: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = Color(0xFFFFF3E0),  // 暖橙色浅色背景
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        /** 标签文本（显示 # 前缀） */
        Text(
            text = "#$tag",
            fontSize = 13.sp,
            color = UiColors.Primary
        )

        /** 删除按钮（× 图标） */
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "删除标签 $tag",
            tint = UiColors.Primary,
            modifier = Modifier
                .size(16.dp)
                .clickable { onDelete() }
        )
    }
}
