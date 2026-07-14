package com.corgimemo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.rememberAsyncImagePainter
import com.corgimemo.app.data.model.CorgiData

/**
 * 柯基基础展示组件
 * 显示柯基图片、名字和问候语，点击有 Snackbar 反馈
 *
 * @param corgiData 柯基数据
 * @param onShowSnackbar 统一的 Snackbar 提示回调（由调用方传入，避免直接依赖 SnackbarHostState）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CorgiCompanion(
    corgiData: CorgiData?,
    onShowSnackbar: (String) -> Unit = {}
) {
    // 暖橙色渐变背景（FF9A5C 到 FFB366）
    val gradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFFF9A5C),
            Color(0xFFFFB366)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(gradient)
            .padding(24.dp)
            .combinedClickable(
                onClick = {
                    // 单击：柯基摇了摇尾巴
                    onShowSnackbar("柯基摇了摇尾巴~")
                },
                onDoubleClick = {
                    // 双击：柯基开心地打滚
                    onShowSnackbar("柯基开心地打滚~")
                },
                onLongClick = {
                    // 长按：柯基很舒服地眯起眼睛
                    onShowSnackbar("柯基很舒服地眯起眼睛~")
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 柯基图片
        Image(
            painter = rememberAsyncImagePainter(
                model = "https://neeko-copilot.bytedance.net/api/text_to_image?prompt=cute%20corgi%20dog%20cartoon%20style%20kawaii%20orange%20and%20white%20fluffy&image_size=square_hd"
            ),
            contentDescription = "柯基图片",
            modifier = Modifier
                .size(120.dp)
                .clip(RoundedCornerShape(50)),
            contentScale = ContentScale.Crop
        )

        // 名字显示
        Text(
            text = corgiData?.name ?: "未命名",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )

        // 问候语
        Text(
            text = getGreeting(),
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/**
 * 根据时间返回不同的问候语
 */
private fun getGreeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when (hour) {
        in 6..11 -> "早上好！今天也要元气满满哦 ☀️"
        in 12..14 -> "中午好！记得休息一下 😊"
        in 15..18 -> "下午好！继续加油 💪"
        in 19..23, in 0..5 -> "晚上好！早点休息哦 🌙"
        else -> "你好呀！很高兴见到你 🐾"
    }
}