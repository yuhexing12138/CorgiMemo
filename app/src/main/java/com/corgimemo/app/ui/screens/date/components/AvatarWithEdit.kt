package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.corgimemo.app.ui.theme.UiColors

/**
 * 圆形头像 + 右下角铅笔图标（日期新建页专用）
 *
 * 当前阶段：
 * - 头像区域显示默认柯基爪子图标（占位）
 * - 点击头像或铅笔图标时调用 [onClick] 回调（页面接 Snackbar "功能开发中"）
 *
 * 未来扩展：
 * - 替换为相册/emoji 选择器
 * - 支持从 SpecialDate.imagePaths 加载真实图片
 *
 * @param onClick 整体点击回调（同时绑定到头像主体和铅笔图标）
 * @param modifier 外部 Modifier
 */
@Composable
fun AvatarWithEdit(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(96.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // 头像主体：圆形 + 主色浅背景 + 柯基爪子图标
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = UiColors.PrimaryLight,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Pets,
                contentDescription = "头像",
                tint = UiColors.Primary,
                modifier = Modifier.size(48.dp)
            )
        }

        // 铅笔图标：右下角偏移 4dp，圆形白色背景 + 主色图标
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 4.dp, y = 4.dp)
                .size(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = "编辑头像",
                tint = UiColors.Primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
