package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.TodoTemplate
import com.corgimemo.app.data.model.UserTemplateEntity

/**
 * 模板轮播组件（增强版）
 * 在空状态页面底部显示可横向滚动的模板卡片列表
 * 支持显示系统模板和用户自定义模板
 *
 * @param onTemplateSelected 系统模板被选中时的回调
 * @param onUserTemplateSelected 用户模板被选中时的回调
 * @param userTemplates 用户自定义模板列表
 * @param modifier 修饰符
 */
@Composable
fun TemplateCarousel(
    onTemplateSelected: (TodoTemplate) -> Unit,
    userTemplates: List<UserTemplateEntity> = emptyList(),
    onUserTemplateSelected: (UserTemplateEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val systemTemplates = com.corgimemo.app.data.model.TemplateData.templates

    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        /** 标题文字 */
        Text(
            text = "📋 或选择一个模板开始",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        /** 横向滚动卡片列表 */
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            /** 系统模板 */
            items(systemTemplates, key = { it.id }) { template ->
                TemplateCard(
                    template = template,
                    onClick = { onTemplateSelected(template) }
                )
            }

            /** 用户自定义模板 */
            items(userTemplates, key = { it.id }) { userTemplate ->
                UserTemplateCard(
                    template = userTemplate,
                    onClick = { onUserTemplateSelected(userTemplate) }
                )
            }
        }
    }
}

/**
 * 模板卡片组件
 * 显示单个系统模板的图标、名称、描述和待办数量
 *
 * @param template 模板数据
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
fun TemplateCard(
    template: TodoTemplate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /** 模板图标 */
            Text(
                text = template.icon,
                fontSize = 36.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            /** 模板名称 */
            Text(
                text = template.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            /** 模板描述 */
            Text(
                text = template.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            /** 待办数量提示 */
            Text(
                text = "${template.todos.size} 个待办",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * 用户自定义模板卡片组件
 * 显示单个用户模板的图标、名称和待办数量
 *
 * @param template 用户模板实体
 * @param onClick 点击回调
 * @param modifier 修饰符
 */
@Composable
private fun UserTemplateCard(
    template: UserTemplateEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    /** 解析待办列表 */
    val todoCount = try {
        kotlinx.serialization.json.Json.decodeFromString<List<String>>(template.todosJson).size
    } catch (e: Exception) {
        0
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            /** 模板图标 */
            Text(
                text = template.icon,
                fontSize = 36.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            /** 模板名称 */
            Text(
                text = template.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(4.dp))

            /** 待办数量提示 */
            Text(
                text = "$todoCount 个待办",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )

            /** 自定义标识 */
            Text(
                text = "✏️ 自定义",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
