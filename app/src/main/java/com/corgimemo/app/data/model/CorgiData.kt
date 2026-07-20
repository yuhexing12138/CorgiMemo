package com.corgimemo.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 柯基数据实体
 * 存储柯基的所有成长和装扮信息
 *
 * @property id 主键 ID
 * @property name 柯基名字
 * @property level 当前等级
 * @property experience 总经验值
 * @property currentOutfit 当前装扮 ID（null 表示默认）
 * @property unlockedOutfits 已解锁装扮 ID 列表（JSON 字符串）
 * @property unlockedAchievements 已解锁成就 ID 列表（JSON 字符串）
 * @property moodValue 情绪值（0-100）
 * @property lastActiveDate 最后活跃日期（格式：yyyy-MM-dd）
 * @property totalCompleted 累计完成任务数
 * @property consecutiveDays 连续活跃天数
 * @property maxConsecutiveDays 历史最长连续天数
 * @property consecutiveEarlyDays 连续早起天数
 * @property lastEarlyDate 最后一次早起日期（格式：yyyy-MM-dd）
 * @property avatarPath 用户头像文件路径（null = 使用首字母占位徽章）
 */
@Entity(
    tableName = "corgi_data",
    indices = [
        /** 索引：按等级查询，用于筛选不同等级的柯基 */
        Index(value = ["level"]),
        /** 索引：按情绪值查询，用于情绪相关的统计和展示 */
        Index(value = ["moodValue"]),
        /** 索引：按最后活跃日期查询，用于活跃度统计和排序 */
        Index(value = ["lastActiveDate"]),
        /** 复合索引：按等级和经验值联合查询，用于排行榜功能 */
        Index(value = ["level", "experience"])
    ]
)
data class CorgiData(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val level: Int = 1,
    val experience: Int = 0,
    val currentOutfit: String? = null,
    val unlockedOutfits: String = "[]",
    val unlockedAchievements: String = "[]",

    /** 情绪值（0-100），数据合法性由业务层保证 */
    @ColumnInfo
    val moodValue: Int = 50,

    val lastActiveDate: String,
    val totalCompleted: Int = 0,
    val consecutiveDays: Int = 0,
    val maxConsecutiveDays: Int = 0,
    val consecutiveEarlyDays: Int = 0,
    val lastEarlyDate: String = "",

    /**
     * 用户头像文件路径（绝对路径 / content URI）
     *
     * - null  → UI 用首字母占位徽章（见 ui/components/UserAvatar.kt）
     * - 非空 → UI 用 Coil AsyncImage 加载（本期上传功能未接，永远为 null）
     *
     * 依据 .trae/rules/entity与 migration同步检查.md 规则：
     * `@ColumnInfo(defaultValue = "NULL")` 必须与 Migration_39_40 的
     * `DEFAULT NULL` SQL 严格保持一致。
     */
    @ColumnInfo(defaultValue = "NULL")
    val avatarPath: String? = null
)
