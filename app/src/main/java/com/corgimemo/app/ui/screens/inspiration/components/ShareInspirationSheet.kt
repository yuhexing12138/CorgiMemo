// app/src/main/java/com/corgimemo/app/ui/screens/inspiration/components/ShareInspirationSheet.kt
package com.corgimemo.app.ui.screens.inspiration.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 分享灵感底部弹窗
 *
 * 提供三个选项：保存到相册 / 更多分享 / 取消
 *
 * @param onDismiss 关闭弹窗回调
 * @param onSaveToGallery 保存到相册回调（父级负责实际截图+保存逻辑）
 * @param onMoreShare 更多分享回调（父级负责启动系统 Intent）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareInspirationSheet(
    onDismiss: () -> Unit,
    onSaveToGallery: () -> Unit,
    onMoreShare: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // 弹窗标题
            Text(
                text = "分享灵感",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            // 保存到相册
            ShareOption(
                icon = Icons.Outlined.Image,
                title = "保存到相册",
                onClick = onSaveToGallery
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 更多分享
            ShareOption(
                icon = Icons.Outlined.Share,
                title = "更多分享",
                onClick = onMoreShare
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 取消
            ShareOption(
                icon = Icons.Outlined.Close,
                title = "取消",
                onClick = onDismiss
            )
        }
    }
}

/**
 * 分享选项行（图标 + 标题）
 *
 * @param icon 左侧图标
 * @param title 选项文字
 * @param onClick 点击回调
 */
@Composable
private fun ShareOption(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // 左侧图标
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.size(16.dp))
        // 选项文字
        Text(
            text = title,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
