package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * 被忽略想念弹窗
 *
 * 3 天未打开 APP 时显示"柯基想你了"的欢迎回来提示。
 * 由 `HomeViewModel.checkStartupBehaviors()` 在启动时根据
 * `lastActiveTimestamp` 与当前日期差触发，状态保存于
 * `HomeViewModel._showMissedYouDialog` / `_missedYouDays`。
 *
 * ## 使用模式
 *
 * ```
 * if (showMissedYouDialog) {
 *     MissedYouDialog(
 *         daysAway = missedYouDays,
 *         onDismiss = { viewModel.dismissMissedYouDialog() }
 *     )
 * }
 * ```
 *
 * 当前挂载点：HomeScreen（启动时弹窗）、CorgiDetailScreen（完整挂载以保完整性）。
 *
 * @param daysAway 离开的天数（用于文案"已经 X 天没见到你了..."）
 * @param onDismiss 关闭回调（通常调用 ViewModel 的 dismiss 方法）
 */
@Composable
fun MissedYouDialog(
    daysAway: Int,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🥺", fontSize = 48.sp)
                Text(
                    text = "柯基想你了！",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "终于回来啦",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "已经 $daysAway 天没见到你了...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    text = "柯基一直都在等你哦~",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(text = "我也想你！")
                }
            }
        }
    }
}
