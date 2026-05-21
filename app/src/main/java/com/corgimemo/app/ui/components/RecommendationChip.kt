package com.corgimemo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Category

@Composable
fun RecommendationChip(
    category: Category,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        text = "💡 推荐：${category.name}",
        color = Color(0xFFD97706),
        fontSize = 14.sp,
        modifier = modifier
            .background(
                color = Color(0xFFFFF3E0),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
