package com.corgimemo.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType

@Composable
fun CategorySelector(
    categories: List<Category>,
    selectedCategoryId: Long,
    onCategorySelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    android.util.Log.d("CategorySelector", "CategorySelector 被调用, categories数量=${categories.size}, selectedCategoryId=$selectedCategoryId")
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "分类",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        if (categories.isEmpty()) {
            android.util.Log.d("CategorySelector", "categories为空，显示加载中提示")
            Text(
                text = "正在加载分类...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            android.util.Log.d("CategorySelector", "显示 ${categories.size} 个分类芯片")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { category ->
                    CategoryChip(
                        text = "${getCategoryIcon(category.type)} ${category.name}",
                        categoryType = category.type,
                        isSelected = selectedCategoryId == category.id,
                        onClick = { onCategorySelected(category.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryChip(
    text: String,
    categoryType: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (color, bgColor) = when (categoryType) {
        CategoryType.STUDY -> Pair(
            Color(0xFF2563EB),
            Color(0xFFDBEAFE)
        )
        CategoryType.WORK -> Pair(
            Color(0xFFD97706),
            Color(0xFFFFF3E0)
        )
        CategoryType.LIFE -> Pair(
            Color(0xFF16A34A),
            Color(0xFFECFDF5)
        )
        CategoryType.SPORT -> Pair(
            Color(0xFF7C3AED),
            Color(0xFFEDE9FE)
        )
        else -> Pair(
            Color(0xFF6B7280),
            Color(0xFFF3F4F6)
        )
    }

    Button(
        onClick = onClick,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isSelected) bgColor else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = modifier
    ) {
        Text(text = text)
    }
}

private fun getCategoryIcon(categoryType: Int): String {
    return when (categoryType) {
        CategoryType.STUDY -> "📚"
        CategoryType.WORK -> "💼"
        CategoryType.LIFE -> "🏠"
        CategoryType.SPORT -> "🏃"
        else -> "📋"
    }
}
