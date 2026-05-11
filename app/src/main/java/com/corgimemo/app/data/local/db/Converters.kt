package com.corgimemo.app.data.local.db

import androidx.room.TypeConverter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Room 类型转换器
 * 
 * 将 LocalDateTime 类型转换为数据库可存储的字符串格式
 */
object Converters {

    // 日期时间格式化器
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    /**
     * 将 LocalDateTime 转换为字符串
     * 
     * @param dateTime 日期时间对象
     * @return 格式化后的字符串
     */
    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? {
        return dateTime?.format(formatter)
    }

    /**
     * 将字符串转换为 LocalDateTime
     * 
     * @param value 格式化的日期时间字符串
     * @return LocalDateTime 对象
     */
    @TypeConverter
    fun toLocalDateTime(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, formatter) }
    }
}