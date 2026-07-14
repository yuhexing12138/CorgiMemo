package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PriorityBadge(priority: Int) {
    val (text, color, backgroundColor) = when (priority) {
        3 -> Triple("高", PriorityColors.High, Color(0xFFFFE4E6))
        2 -> Triple("中", PriorityColors.Medium, Color(0xFFFFF3E0))
        1 -> Triple("低", PriorityColors.Low, Color(0xFFECFDF5))
        else -> Triple("无", PriorityColors.None, Color.Transparent)
    }

    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
