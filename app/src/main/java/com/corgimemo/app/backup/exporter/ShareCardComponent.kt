package com.corgimemo.app.backup.exporter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.ui.theme.OrangePrimary
import com.corgimemo.app.ui.theme.OrangePrimaryLight
import com.corgimemo.app.ui.theme.OrangeSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分享卡片组件
 * 用于生成可分享的待办卡片图片
 */
object ShareCardComponent {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * 单条待办分享卡片
     *
     * @param todo 待办项
     * @param category 分类
     */
    @Composable
    fun SingleTodoShareCard(
        todo: TodoItem,
        category: Category?
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                HeaderSection()

                Spacer(modifier = Modifier.height(16.dp))

                TodoContentSection(
                    todo = todo,
                    categoryName = category?.name ?: "默认"
                )

                Spacer(modifier = Modifier.height(16.dp))

                FooterSection()
            }
        }
    }

    /**
     * 多条待办分享卡片
     *
     * @param todos 待办列表
     * @param categories 分类列表
     */
    @Composable
    fun MultiTodoShareCard(
        todos: List<TodoItem>,
        categories: List<Category>
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                HeaderSection()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "我的待办清单",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D1B0E)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "共 ${todos.size} 项待办",
                    fontSize = 13.sp,
                    color = Color(0xFF8B7355)
                )

                Spacer(modifier = Modifier.height(16.dp))

                todos.take(5).forEachIndexed { index, todo ->
                    TodoMiniItem(
                        todo = todo,
                        categoryName = categories.find { it.id == todo.categoryId }?.name ?: "默认",
                        index = index + 1
                    )
                    if (index < minOf(todos.size, 5) - 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (todos.size > 5) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "... 还有 ${todos.size - 5} 项",
                        fontSize = 13.sp,
                        color = Color(0xFF8B7355)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                FooterSection()
            }
        }
    }

    /**
     * 顶部装饰区域
     */
    @Composable
    private fun HeaderSection() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🐕",
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "CorgiMemo",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrangePrimary
                    )
                    Text(
                        text = "柯基备忘录",
                        fontSize = 11.sp,
                        color = Color(0xFF8B7355)
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = OrangeSecondary
            ) {
                Text(
                    text = dateFormat.format(Date()),
                    fontSize = 11.sp,
                    color = Color(0xFF4A2C1A),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }

    /**
     * 待办内容区域
     */
    @Composable
    private fun TodoContentSection(
        todo: TodoItem,
        categoryName: String
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(OrangeSecondary, OrangePrimaryLight),
                        startX = 0f,
                        endX = Float.POSITIVE_INFINITY
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = categoryName,
                            fontSize = 12.sp,
                            color = Color(0xFF4A2C1A),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    PriorityBadgeShare(priority = todo.priority)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = todo.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D1B0E)
                )

                if (!todo.content.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = todo.content!!,
                        fontSize = 14.sp,
                        color = Color(0xFF5D4030),
                        lineHeight = 20.sp
                    )
                }

                todo.dueDate?.let {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "⏰",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "截止: ${dateFormat.format(Date(it))}",
                            fontSize = 13.sp,
                            color = Color(0xFF5D4030)
                        )
                    }
                }
            }
        }
    }

    /**
     * 迷你待办项
     */
    @Composable
    private fun TodoMiniItem(
        todo: TodoItem,
        categoryName: String,
        index: Int
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$index.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OrangePrimary,
                modifier = Modifier.width(24.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF2D1B0E)
                )
                Text(
                    text = categoryName,
                    fontSize = 11.sp,
                    color = Color(0xFF8B7355)
                )
            }

            PriorityBadgeShare(priority = todo.priority)
        }
    }

    /**
     * 底部水印区域
     */
    @Composable
    private fun FooterSection() {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🐕 来自 CorgiMemo - 让待办变得可爱",
                fontSize = 11.sp,
                color = Color(0xFFA39078)
            )
        }
    }
}

/**
 * 分享用优先级徽章
 *
 * @param priority 优先级值
 */
@Composable
private fun PriorityBadgeShare(priority: Int) {
    val (text, color, backgroundColor) = when (priority) {
        2 -> Triple("高", Color(0xFFDC2626), Color(0xFFFFE4E6))
        1 -> Triple("中", Color(0xFFD97706), Color(0xFFFFF3E0))
        else -> Triple("低", Color(0xFF16A34A), Color(0xFFECFDF5))
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
