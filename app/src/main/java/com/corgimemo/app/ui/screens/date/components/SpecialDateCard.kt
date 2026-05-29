package com.corgimemo.app.ui.screens.date.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.viewmodel.DayColor
import com.corgimemo.app.viewmodel.DisplayDate
import com.corgimemo.app.viewmodel.GroupType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpecialDateCard(
    date: DisplayDate,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isExpired = date.groupType == GroupType.EXPIRED

    val (dayBgColor, dayTextColor) = when (date.dayColor) {
        DayColor.RED -> Color(0xFFFFF0F0) to Color(0xFFFF8A80)
        DayColor.ORANGE -> Color(0xFFFFF3E0) to Color(0xFFFF9A5C)
        DayColor.GRAY -> Color(0xFFF5F5F5) to Color(0xFF999999)
        DayColor.GREEN -> Color(0xFFE8F5E9) to Color(0xFF7EC8A0)
    }

    val formattedDate = SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(date.targetDate))

    val (tagBgColor, tagTextColor) = when (date.category.name) {
        "BIRTHDAY" -> Color(0xFFFFF0F5) to Color(0xFFE91E63)
        "ANNIVERSARY" -> Color(0xFFFFF3E0) to Color(0xFFFF9A5C)
        "HOLIDAY" -> Color(0xFFE0F7FA) to Color(0xFF00BCD4)
        else -> Color(0xFFF3E5F5) to Color(0xFF9C27B0)
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .then(if (isExpired) Modifier.alpha(0.6f) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (date.dayColor == DayColor.RED && !isExpired) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(dayTextColor, RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (date.dayColor == DayColor.RED && !isExpired) 12.dp else 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 16.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(date.title, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(dayBgColor).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Text("${date.daysAbsolute}天", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = dayTextColor)
                        }
                        Text(when (date.groupType) {
                            GroupType.UPCOMING -> "还有"
                            GroupType.CELEBRATING -> "已经"
                            GroupType.EXPIRED -> "已过"
                        }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Text(formattedDate, fontSize = 12.sp, color = Color(0xFF999999))

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${date.category.emoji} ${date.category.displayName}", fontSize = 11.sp,
                            fontWeight = FontWeight.Medium, color = tagTextColor,
                            modifier = Modifier.background(tagBgColor, RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
                        if (date.relationHint != null) {
                            Text(
                                date.relationHint,
                                fontSize = 12.sp,
                                color = Color(0xFF999999),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (date.hasImage) {
                    Spacer(Modifier.width(12.dp))
                    repeat(minOf(2, 2)) {
                        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) {
                            Text("🖼️", fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
