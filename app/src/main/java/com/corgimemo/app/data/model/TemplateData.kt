package com.corgimemo.app.data.model

/**
 * 待办模板数据类
 * 定义模板的基本信息和包含的待办项
 *
 * @param id 模板唯一标识
 * @param name 模板名称
 * @param icon 模板图标（Emoji）
 * @param description 模板描述
 * @param todos 包含的待办项列表
 */
data class TodoTemplate(
    val id: String,
    val name: String,
    val icon: String,
    val description: String,
    val todos: List<TemplateTodo>
)

/**
 * 模板待办项数据类
 *
 * @param title 待办标题
 * @param category 可选分类名称
 */
data class TemplateTodo(
    val title: String,
    val category: String? = null
)

/**
 * 模板数据管理对象
 * 提供预定义的待办模板列表
 */
object TemplateData {
    /** 预定义模板列表 */
    val templates = listOf(
        TodoTemplate(
            id = "daily_habits",
            name = "每日习惯",
            icon = "☀️",
            description = "养成好习惯，从今天开始",
            todos = listOf(
                TemplateTodo("早起打卡"),
                TemplateTodo("喝8杯水"),
                TemplateTodo("运动30分钟"),
                TemplateTodo("阅读15分钟"),
                TemplateTodo("早睡准备")
            )
        ),
        TodoTemplate(
            id = "work_weekly",
            name = "工作周计划",
            icon = "💼",
            description = "高效工作，有序安排",
            todos = listOf(
                TemplateTodo("周一例会", "工作"),
                TemplateTodo("周三汇报", "工作"),
                TemplateTodo("周五总结", "工作"),
                TemplateTodo("处理邮件", "工作")
            )
        ),
        TodoTemplate(
            id = "study_plan",
            name = "学习计划",
            icon = "📚",
            description = "持续学习，不断进步",
            todos = listOf(
                TemplateTodo("背单词30个", "学习"),
                TemplateTodo("阅读专业书籍", "学习"),
                TemplateTodo("复习笔记", "学习"),
                TemplateTodo("完成练习题", "学习")
            )
        )
    )

    /**
     * 根据 ID 获取模板
     *
     * @param templateId 模板 ID
     * @return 模板对象，未找到则返回 null
     */
    fun getTemplateById(templateId: String): TodoTemplate? {
        return templates.find { it.id == templateId }
    }
}
