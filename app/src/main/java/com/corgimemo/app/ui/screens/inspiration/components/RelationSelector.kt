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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.InspirationRelation
import com.corgimemo.app.ui.theme.UiColors

/**
 * 关联选择器组件
 * 用于管理灵感与其他卡片（待办/日期/其他灵感）的关联关系
 *
 * 功能说明：
 * - 显示已关联卡片列表（每项显示图标+标题，右侧带删除按钮）
 * - "+ 添加关联"按钮用于新增关联关系
 * - 点击添加弹出对话框选择关联类型：
 *   - 📝 关联待办事项
 *   - 📅 关联特殊日期
 *   - 💡 关联其他灵感
 * - 选择类型后可搜索或列表选择具体项
 *
 * @param relations 当前已有关联关系列表
 * @param onRelationAdd 添加关联回调函数（参数：目标类型、目标ID）
 * @param onRelationDelete 删除关联回调函数（参数：关联ID）
 * @param modifier 修饰符
 */
@Composable
fun RelationSelector(
    relations: List<InspirationRelation>,
    onRelationAdd: (targetType: String, targetId: Long) -> Unit,
    onRelationDelete: (relationId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    /** 控制是否显示关联类型选择对话框 */
    var showTypeDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        /** 关联区域标题 */
        Text(
            text = "关联",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        /**
         * 已关联列表区域
         * 显示当前所有已建立的关联关系
         */
        if (relations.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(minOf((relations.size * 60).dp, 200.dp)),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = relations, key = { it.id }) { relation ->
                    /**
                     * 单个关联项组件
                     * 根据 targetType 显示不同的图标和样式
                     */
                    RelationItem(
                        relation = relation,
                        onDelete = {
                            /** 删除该关联关系 */
                            onRelationDelete(relation.id)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        } else {
            /** 无关联时的提示文字 */
            Text(
                text = "暂无关联，可以添加待办、日期或其他灵感作为关联",
                fontSize = 13.sp,
                color = Color(0xFF999999),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        /**
         * 添加关联按钮
         * 点击后打开关联类型选择对话框
         */
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(UiColors.Primary.copy(alpha = 0.1f))
                .clickable { showTypeDialog = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "添加关联",
                tint = UiColors.Primary,
                modifier = Modifier.size(18.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "添加关联",
                fontSize = 14.sp,
                color = UiColors.Primary,
                fontWeight = FontWeight.Medium
            )
        }
    }

    /**
     * 关联类型选择对话框
     * 用户点击"添加关联"后弹出，提供三种关联类型选项
     */
    if (showTypeDialog) {
        RelationTypeDialog(
            onDismiss = { showTypeDialog = false },
            onTypeSelected = { targetType ->
                /** 用户选择了关联类型 */
                showTypeDialog = false
                // TODO: 根据选择的类型打开对应的搜索/列表选择界面
                // 这里暂时使用模拟数据演示流程
                when (targetType) {
                    "todo" -> {
                        // TODO: 打开待办事项选择界面
                        // 示例：onRelationAdd("todo", mockTodoId)
                    }
                    "date" -> {
                        // TODO: 打开特殊日期选择界面
                        // 示例：onRelationAdd("date", mockDateId)
                    }
                    "inspiration" -> {
                        // TODO: 打开其他灵感选择界面
                        // 示例：onRelationAdd("inspiration", mockInspirationId)
                    }
                }
            }
        )
    }
}

/**
 * 关联项组件
 * 显示单个已关联的卡片信息，包含图标、标题和删除按钮
 *
 * @param relation 关联关系实体
 * @param onDelete 删除回调函数
 * @param modifier 修饰符
 */
@Composable
private fun RelationItem(
    relation: InspirationRelation,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    /**
     * 根据 targetType 获取对应的图标、标题文本和颜色
     * targetType 可选值："todo" | "date" | "inspiration"
     */
    val (icon, titleText, bgColor) = when (relation.targetType) {
        "todo" -> Triple(
            "📝",
            "待办事项 #${relation.targetId}",
            Color(0xFFE3F2FD)  // 浅蓝色背景
        )
        "date" -> Triple(
            "📅",
            "特殊日期 #${relation.targetId}",
            Color(0xFFFCE4EC)  // 浅粉色背景
        )
        "inspiration" -> Triple(
            "💡",
            "灵感记录 #${relation.targetId}",
            Color(0xFFFFF3E0)   // 暖橙色浅色背景
        )
        else -> Triple(
            "📎",
            "未知类型 #${relation.targetId}",
            Color(0xFFF5F5F5)   // 灰色背景
        )
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** 类型图标 */
        Text(
            text = icon,
            fontSize = 20.sp,
            modifier = Modifier.padding(end = 10.dp)
        )

        /** 关联标题（弹性填充剩余空间） */
        Text(
            text = titleText,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        /** 删除按钮 */
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.05f))
                .clickable { onDelete() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "删除关联",
                tint = Color(0xFF666666),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * 关联类型选择对话框
 * 弹出式对话框，让用户选择要关联的目标类型
 *
 * @param onDismiss 对话框关闭回调
 * @param onTypeSelected 类型选择回调（返回选中的类型字符串）
 */
@Composable
private fun RelationTypeDialog(
    onDismiss: () -> Unit,
    onTypeSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "选择关联类型",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            /**
             * 三种关联类型选项列
             * 每个选项显示图标 + 说明文字
             */
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                /**
                 * 待办事项选项
                 * 图标：📝
                 * 说明：关联到已有的待办事项
                 */
                RelationTypeOption(
                    icon = "📝",
                    title = "关联待办",
                    description = "将此灵感与某个待办事项关联",
                    onClick = {
                        onTypeSelected("todo")
                    }
                )

                /**
                 * 特殊日期选项
                 * 图标：📅
                 * 说明：关联到特殊日期事件
                 */
                RelationTypeOption(
                    icon = "📅",
                    title = "关联日期",
                    description = "将此灵感与某个特殊日期关联",
                    onClick = {
                        onTypeSelected("date")
                    }
                )

                /**
                 * 其他灵感选项
                 * 图标：💡
                 * 说明：关联到另一条灵感记录
                 */
                RelationTypeOption(
                    icon = "💡",
                    title = "关联灵感",
                    description = "将此灵感与其他灵感记录关联",
                    onClick = {
                        onTypeSelected("inspiration")
                    }
                )
            }
        },
        confirmButton = {
            /** 取消按钮（位于右下角） */
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        dismissButton = null  // 不需要左侧取消按钮，因为已有底部确认按钮
    )
}

/**
 * 关联类型选项组件
 * 显示单个关联类型的可点击选项卡片
 *
 * @param icon 类型图标（Emoji）
 * @param title 类型标题
 * @param description 类型描述说明
 * @param onClick 点击回调函数
 * @param modifier 修饰符
 */
@Composable
private fun RelationTypeOption(
    icon: String,
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFF8F9FA))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        /** 类型图标 */
        Text(
            text = icon,
            fontSize = 24.sp,
            modifier = Modifier.padding(end = 12.dp)
        )

        /** 标题和描述列 */
        Column {
            /** 类型标题 */
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(2.dp))

            /** 类型描述 */
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color(0xFF666666)
            )
        }
    }
}
