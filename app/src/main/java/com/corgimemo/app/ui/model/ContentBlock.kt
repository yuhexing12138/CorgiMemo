package com.corgimemo.app.ui.model

/**
 * 内容块：编辑器中的动态内容单元（文本 / 图片 / 语音）
 *
 * 统一定义，供 TodoEditScreen 和 InspirationEditScreen 共享使用。
 * 避免在各自包内重复定义导致的类型不匹配问题。
 */
sealed class ContentBlock {
    /** 文本内容块（不持久化到独立表，存储在主实体的 content 字段中） */
    data class Text(val content: String) : ContentBlock()
    /** 图片内容块（存储图片文件路径） */
    data class Image(val path: String) : ContentBlock()
    /** 语音内容块（存储语音文件路径和时长） */
    data class Voice(val path: String, val duration: Int?) : ContentBlock()
}
