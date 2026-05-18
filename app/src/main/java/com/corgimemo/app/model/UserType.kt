package com.corgimemo.app.model

/**
 * 用户类型枚举
 *
 * 用于区分上班族和学生用户，提供个性化的问候语和推荐分类
 */
enum class UserType(val typeValue: String) {
    /**
     * 上班族
     */
    WORKER("worker"),

    /**
     * 学生
     */
    STUDENT("student");

    companion object {
        /**
         * 从字符串值获取 UserType
         *
         * @param value 字符串值 "worker" 或 "student"
         * @return 对应的 UserType，默认为 WORKER
         */
        fun fromValue(value: String?): UserType {
            return when (value) {
                "student" -> STUDENT
                else -> WORKER
            }
        }
    }
}
