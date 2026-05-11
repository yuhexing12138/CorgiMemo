package com.corgimemo.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material3 形状配置
 * 
 * 定义应用中使用的圆角大小：
 * - ExtraSmall: 4dp - 小图标、小按钮
 * - Small: 8dp - 小型组件
 * - Medium: 16dp - 卡片、按钮（项目要求）
 * - Large: 28dp - 大型卡片、对话框
 * - ExtraLarge: 32dp - 全屏对话框
 */
val Shapes = Shapes(
    // 极小圆角 - 用于图标、小按钮
    extraSmall = RoundedCornerShape(4.dp),
    
    // 小圆角 - 用于小型组件
    small = RoundedCornerShape(8.dp),
    
    // 中等圆角（项目要求：16dp）- 用于卡片、按钮
    medium = RoundedCornerShape(16.dp),
    
    // 大圆角 - 用于大型卡片、对话框
    large = RoundedCornerShape(28.dp),
    
    // 极大圆角 - 用于全屏对话框
    extraLarge = RoundedCornerShape(32.dp)
)