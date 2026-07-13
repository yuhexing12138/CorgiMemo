package com.corgimemo.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.corgimemo.app.data.local.db.ContentBlockEntity
import com.corgimemo.app.data.local.db.AchievementEntity
import com.corgimemo.app.data.local.db.CategoryKeywordEntity
import com.corgimemo.app.data.local.db.OperationLogEntity
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CardRelation
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.DeletedTodo
import com.corgimemo.app.data.model.DeletedInspiration
import com.corgimemo.app.data.model.MoodHistory
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.model.InspirationRelation
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.model.SpecialDateRelation
import com.corgimemo.app.data.model.UserTemplateEntity
import com.corgimemo.app.data.model.DeletedSpecialDate

/**
 * 应用数据库
 * 管理待办事项、柯基数据、任务分类、成就和用户模板
 */
@Database(
    entities = [TodoItem::class, CorgiData::class, Category::class, DeletedTodo::class, DeletedInspiration::class, MoodHistory::class, SubTask::class, AchievementEntity::class, TaskDailyStats::class, CategoryKeywordEntity::class, UserTemplateEntity::class, OperationLogEntity::class, Inspiration::class, InspirationRelation::class, SpecialDate::class, SpecialDateRelation::class, CardRelation::class, ContentBlockEntity::class, DeletedSpecialDate::class],
    version = 37,
    exportSchema = false
)
abstract class CorgiMemoDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

    abstract fun corgiDao(): CorgiDao

    abstract fun categoryDao(): CategoryDao

    abstract fun moodHistoryDao(): MoodHistoryDao

    abstract fun subTaskDao(): SubTaskDao

    abstract fun achievementDao(): AchievementDao

    abstract fun taskDailyStatsDao(): TaskDailyStatsDao

    abstract fun categoryKeywordDao(): CategoryKeywordDao

    /** 用户模板 DAO */
    abstract fun templateDao(): TemplateDao

    /** 操作日志 DAO */
    abstract fun operationLogDao(): OperationLogDao

    /** 最近删除 DAO */
    abstract fun deletedTodoDao(): DeletedTodoDao

    /** 灵感回收站 DAO */
    abstract fun deletedInspirationDao(): DeletedInspirationDao

    /** 特殊日期回收站 DAO */
    abstract fun deletedSpecialDateDao(): DeletedSpecialDateDao

    /** 灵感记录 DAO */
    abstract fun inspirationDao(): InspirationDao

    /** 灵感关联关系 DAO */
    abstract fun inspirationRelationDao(): InspirationRelationDao

    /** 特殊日期 DAO */
    abstract fun specialDateDao(): SpecialDateDao

    /** 特殊日期关联关系 DAO */
    abstract fun specialDateRelationDao(): SpecialDateRelationDao

    /** 统一卡片关联关系 DAO */
    abstract fun cardRelationDao(): CardRelationDao

    /** 内容块 DAO（待办事项的混合内容：图片/语音等） */
    abstract fun contentBlockDao(): ContentBlockDao

    companion object {
        private const val DATABASE_NAME = "corgimemo_database"

        @Volatile
        private var INSTANCE: CorgiMemoDatabase? = null

        fun getDatabase(context: Context): CorgiMemoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CorgiMemoDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22, MIGRATION_22_23, MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28, MIGRATION_28_29, MIGRATION_29_30, MIGRATION_30_31, MIGRATION_31_32, MIGRATION_32_33, MIGRATION_33_34, MIGRATION_34_35, MIGRATION_35_36, MIGRATION_36_37)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceLat REAL")
                db.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceLng REAL")
                db.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceRadius REAL")
                db.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceType INTEGER DEFAULT 0")
                db.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceEnabled INTEGER DEFAULT 0")
                db.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceAddress TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("ALTER TABLE corgi_data ADD COLUMN unlockedAchievements TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE corgi_data ADD COLUMN maxConsecutiveDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS mood_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        date TEXT NOT NULL,
                        moodValue INTEGER NOT NULL,
                        changeReason TEXT
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sub_tasks (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        todoId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        isCompleted INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        `order` INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sub_tasks_todoId ON sub_tasks(todoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sub_tasks_todoId_order ON sub_tasks(todoId, `order`)")
            }
        }

        /**
         * 数据库迁移：版本 6 → 7
         * 添加 todo_items 表的 hasSubTasks 字段
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加 hasSubTasks 字段，默认值为 false
                db.execSQL("ALTER TABLE todo_items ADD COLUMN hasSubTasks INTEGER NOT NULL DEFAULT 0")
                // 为 hasSubTasks 创建索引，提高查询效率
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_hasSubTasks ON todo_items(hasSubTasks)")
            }
        }

        /**
         * 数据库迁移：版本 7 → 8
         * 创建 achievements 表并添加 corgi_data 表的新字段
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 创建 achievements 表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        id TEXT PRIMARY KEY NOT NULL,
                        unlockedAt INTEGER
                    )
                """.trimIndent())

                // 为 corgi_data 添加新字段
                db.execSQL("ALTER TABLE corgi_data ADD COLUMN consecutiveEarlyDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE corgi_data ADD COLUMN lastEarlyDate TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 数据库迁移：版本 8 → 9
         * 创建 task_daily_stats 表用于每日任务统计
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS task_daily_stats (
                        date TEXT PRIMARY KEY NOT NULL,
                        studyCompleted INTEGER NOT NULL DEFAULT 0,
                        workCompleted INTEGER NOT NULL DEFAULT 0,
                        lifeCompleted INTEGER NOT NULL DEFAULT 0,
                        entertainmentCompleted INTEGER NOT NULL DEFAULT 0,
                        lastUpdated INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * 数据库迁移：版本 9 → 10
         * 创建 category_keywords 表用于智能分类关键词
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_keywords (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        keyword TEXT NOT NULL,
                        categoryType INTEGER NOT NULL,
                        matchType INTEGER NOT NULL,
                        isUserDefined INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_category_keywords_keyword 
                    ON category_keywords(keyword)
                """.trimIndent())

                db.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_category_keywords_categoryType 
                    ON category_keywords(categoryType)
                """.trimIndent())
            }
        }

        /**
         * 数据库迁移：版本 10 → 11
         * 移除 dueDate 字段，添加 startDate 和 estimatedDurationMinutes 字段
         * 现有 dueDate 数据将迁移到 startDate
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS todo_items_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        content TEXT,
                        categoryId INTEGER NOT NULL,
                        priority INTEGER NOT NULL,
                        status INTEGER NOT NULL,
                        startDate INTEGER,
                        estimatedDurationMinutes INTEGER,
                        reminderTime INTEGER,
                        repeatType INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        completedAt INTEGER,
                        geofenceLat REAL,
                        geofenceLng REAL,
                        geofenceRadius REAL,
                        geofenceType INTEGER NOT NULL DEFAULT 0,
                        geofenceEnabled INTEGER NOT NULL DEFAULT 0,
                        geofenceAddress TEXT,
                        hasSubTasks INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT INTO todo_items_new (
                        id, title, content, categoryId, priority, status, startDate, 
                        reminderTime, repeatType, createdAt, updatedAt, completedAt,
                        geofenceLat, geofenceLng, geofenceRadius, geofenceType,
                        geofenceEnabled, geofenceAddress, hasSubTasks
                    )
                    SELECT 
                        id, title, content, categoryId, priority, status, dueDate,
                        reminderTime, repeatType, createdAt, updatedAt, completedAt,
                        geofenceLat, geofenceLng, geofenceRadius, geofenceType,
                        geofenceEnabled, geofenceAddress, hasSubTasks
                    FROM todo_items
                """.trimIndent())

                db.execSQL("DROP TABLE todo_items")

                db.execSQL("ALTER TABLE todo_items_new RENAME TO todo_items")

                db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_status_createdAt ON todo_items(status, createdAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_categoryId_status ON todo_items(categoryId, status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_priority_startDate ON todo_items(priority, startDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_hasSubTasks ON todo_items(hasSubTasks)")
            }
        }

        /**
         * 数据库迁移：版本 11 → 12
         * 添加语音备注相关字段
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 添加语音备注文件路径字段
                db.execSQL("ALTER TABLE todo_items ADD COLUMN voiceNotePath TEXT")
                // 添加语音时长字段（秒）
                db.execSQL("ALTER TABLE todo_items ADD COLUMN voiceDuration INTEGER")
            }
        }

        /**
         * 版本 12 → 13 迁移：添加用户自定义模板表
         * 用于支持用户创建和保存自己的待办模板
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS user_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        description TEXT NOT NULL DEFAULT '',
                        todosJson TEXT NOT NULL DEFAULT '[]',
                        createdAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                        updatedAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                    )
                """.trimIndent())
            }
        }

        /**
         * 版本 13 → 14 迁移：添加操作日志表
         * 用于记录待办操作历史，支持撤销功能
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS operation_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        operation_type TEXT NOT NULL,
                        target_id INTEGER NOT NULL DEFAULT 0,
                        batch_ids_json TEXT,
                        snapshot_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                    )
                """.trimIndent())

                /** 创建索引优化查询性能 */
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_operation_logs_created_at ON operation_logs(created_at)"
                )
            }
        }

        /**
         * 版本 14 → 15 迁移：修复 operation_logs 表结构
         * 1. 修复索引名称：idx_operation_logs_created_at → index_operation_logs_created_at
         * 2. 重建表以修复默认值格式匹配
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                /** 1. 创建新表（与 Room Entity 定义完全匹配） */
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS operation_logs_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        operation_type TEXT NOT NULL,
                        target_id INTEGER NOT NULL DEFAULT 0,
                        batch_ids_json TEXT,
                        snapshot_json TEXT NOT NULL,
                        created_at INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                    )
                """.trimIndent())

                /** 2. 复制数据到新表 */
                db.execSQL("""
                    INSERT INTO operation_logs_new (id, operation_type, target_id, batch_ids_json, snapshot_json, created_at)
                    SELECT id, operation_type, target_id, batch_ids_json, snapshot_json, created_at
                    FROM operation_logs
                """.trimIndent())

                /** 3. 删除旧表 */
                db.execSQL("DROP TABLE operation_logs")

                /** 4. 重命名新表 */
                db.execSQL("ALTER TABLE operation_logs_new RENAME TO operation_logs")

                /** 5. 创建正确名称的索引 */
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_operation_logs_created_at ON operation_logs(created_at)"
                )
            }
        }

        /**
     * 版本 15 → 16 迁移：添加 deleted_todos 表
     * 用于"最近删除"功能的数据持久化
     */
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS deleted_todos (
                    id INTEGER PRIMARY KEY NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT,
                    categoryId INTEGER NOT NULL,
                    priority INTEGER NOT NULL,
                    status INTEGER NOT NULL,
                    startDate INTEGER,
                    estimatedDurationMinutes INTEGER,
                    reminderTime INTEGER,
                    repeatType INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    completedAt INTEGER,
                    geofenceLat REAL,
                    geofenceLng REAL,
                    geofenceRadius REAL,
                    geofenceType INTEGER NOT NULL DEFAULT 0,
                    geofenceEnabled INTEGER NOT NULL DEFAULT 0,
                    geofenceAddress TEXT,
                    hasSubTasks INTEGER NOT NULL DEFAULT 0,
                    voiceNotePath TEXT,
                    voiceDuration INTEGER,
                    deletedAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    /**
     * 版本 16 → 17 迁移：添加灵感记录功能
     * 创建 inspirations 表和 inspiration_relations 关联表
     */
    private val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 创建 inspirations 表（灵感记录主表）
            // 注意：字段约束必须与 Inspiration.kt Entity 注解完全一致
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS inspirations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL DEFAULT '',
                    tags TEXT NOT NULL DEFAULT '',
                    imagePaths TEXT NOT NULL DEFAULT '',
                    imageUrls TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    isPinned INTEGER NOT NULL DEFAULT 0,
                    isArchived INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            // 创建灵感表索引
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_createdAt ON inspirations(createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_isPinned ON inspirations(isPinned)")

            // 创建 inspiration_relations 表（关联关系表）
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS inspiration_relations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    inspirationId INTEGER NOT NULL,
                    targetType TEXT NOT NULL,
                    targetId INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                    FOREIGN KEY(inspirationId) REFERENCES inspirations(id) ON DELETE CASCADE
                )
            """.trimIndent())

            // 创建关联表索引
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inspiration_relations_inspirationId ON inspiration_relations(inspirationId)")
        }
    }

    /**
     * 版本 17 → 18 迁移：添加特殊日期记录功能
     * 创建 special_dates 表和 special_date_relations 关联表
     */
    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS special_dates (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    targetDate INTEGER NOT NULL,
                    category TEXT NOT NULL DEFAULT 'OTHER',
                    countMode INTEGER NOT NULL DEFAULT 0,
                    repeatType INTEGER NOT NULL DEFAULT 0,
                    reminderDays INTEGER NOT NULL DEFAULT 0,
                    content TEXT NOT NULL DEFAULT '',
                    tags TEXT NOT NULL DEFAULT '',
                    imagePaths TEXT NOT NULL DEFAULT '',
                    imageUrls TEXT NOT NULL DEFAULT '',
                    isPinned INTEGER NOT NULL DEFAULT 0,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS index_special_dates_targetDate ON special_dates(targetDate)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_special_dates_isPinned ON special_dates(isPinned)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_special_dates_category ON special_dates(category)")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS special_date_relations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    specialDateId INTEGER NOT NULL,
                    targetType TEXT NOT NULL,
                    targetId INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                    FOREIGN KEY(specialDateId) REFERENCES special_dates(id) ON DELETE CASCADE
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS index_special_date_relations_specialDateId ON special_date_relations(specialDateId)")
        }
    }

    /**
     * 版本 18 → 19 迁移：为 todo_items 表添加图片路径字段
     * 支持待办事项插入多张图片功能
     */
    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            /** 添加 imagePaths 字段，用于存储图片路径的 JSON 数组 */
            db.execSQL("ALTER TABLE todo_items ADD COLUMN imagePaths TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * 版本 19 → 20 迁移：创建统一卡片关联表
     * 创建 card_relations 表，并从现有的 inspiration_relations 和 special_date_relations 迁移数据
     * 保留旧表以避免数据丢失
     */
    private val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS card_relations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceType TEXT NOT NULL,
                    sourceId INTEGER NOT NULL,
                    targetType TEXT NOT NULL,
                    targetId INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS index_card_relations_sourceType_sourceId ON card_relations(sourceType, sourceId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_card_relations_targetType_targetId ON card_relations(targetType, targetId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_card_relations_sourceType_sourceId_targetType_targetId ON card_relations(sourceType, sourceId, targetType, targetId)")

            db.execSQL("""
                INSERT INTO card_relations (sourceType, sourceId, targetType, targetId, createdAt)
                SELECT 'inspiration', inspirationId, targetType, targetId, createdAt
                FROM inspiration_relations
            """.trimIndent())

            db.execSQL("""
                INSERT INTO card_relations (sourceType, sourceId, targetType, targetId, createdAt)
                SELECT 'date', specialDateId, targetType, targetId, createdAt
                FROM special_date_relations
            """.trimIndent())
        }
    }

    /**
     * 版本 20 → 21 迁移：修复 card_relations 表结构
     * 删除旧的结构不一致的表，重新创建正确的表结构（从旧表迁移数据）
     * 修复问题：createdAt 默认值不一致、索引名称/唯一属性不一致
     */
    private val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS card_relations")

            db.execSQL("""
                CREATE TABLE IF NOT EXISTS card_relations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    sourceType TEXT NOT NULL,
                    sourceId INTEGER NOT NULL,
                    targetType TEXT NOT NULL,
                    targetId INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            db.execSQL("CREATE INDEX IF NOT EXISTS index_card_relations_sourceType_sourceId ON card_relations(sourceType, sourceId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_card_relations_targetType_targetId ON card_relations(targetType, targetId)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_card_relations_sourceType_sourceId_targetType_targetId ON card_relations(sourceType, sourceId, targetType, targetId)")

            // 从旧的分散表重新迁移数据（旧的 card_relations 已删）
            db.execSQL("""
                INSERT OR IGNORE INTO card_relations (sourceType, sourceId, targetType, targetId, createdAt)
                SELECT 'inspiration', inspirationId, targetType, targetId, createdAt
                FROM inspiration_relations
            """.trimIndent())

            db.execSQL("""
                INSERT OR IGNORE INTO card_relations (sourceType, sourceId, targetType, targetId, createdAt)
                SELECT 'date', specialDateId, targetType, targetId, createdAt
                FROM special_date_relations
            """.trimIndent())
        }
    }

    /**
     * 版本 21 → 22 迁移：添加 dueDate 截止时间字段
     * 支持同时保留 startDate（开始时间）和 dueDate（截止时间）
     * 提供更灵活的时间管理能力
     */
    private val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            /** 添加 dueDate 字段，用于存储截止时间戳（毫秒） */
            db.execSQL("ALTER TABLE todo_items ADD COLUMN dueDate INTEGER")

            /** 为 dueDate 创建复合索引，优化按截止时间和状态查询的性能 */
            db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_dueDate_status ON todo_items(dueDate, status)")
        }
    }

    /**
     * 版本 22 → 23 迁移：添加背景颜色字段
     * 支持待办事项自定义背景色功能（12 种预设色 + 自定义）
     *
     * 使用 ARGB 整数存储颜色值：
     * - 默认值：16777215 (0xFFFFFFFF = 白色)
     * - 存储方式：Color.toArgb() → Int → 数据库
     * - 读取方式：数据库 Int → Color(Int) → Compose Color
     */
    private val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            /** 添加 backgroundColor 字段（ARGB 整数值），默认为白色 */
            db.execSQL(
                "ALTER TABLE todo_items ADD COLUMN backgroundColor INTEGER NOT NULL DEFAULT 16777215"
            )
        }
    }

    /**
     * 版本 23 → 24 迁移：添加富文本格式化内容字段
     *
     * 支持持久化存储 Markdown 格式的富文本内容，
     * 用于在编辑页恢复完整的格式化显示（粗体/斜体/删除线/列表等）。
     *
     * **数据结构**:
     * - `content`: 纯文本（用于搜索、统计）
     * - `content_format`: Markdown 格式文本（用于编辑器显示）
     */
    private val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            /** 添加 contentFormat 字段（TEXT 类型，默认空字符串） */
            db.execSQL(
                "ALTER TABLE todo_items ADD COLUMN contentFormat TEXT NOT NULL DEFAULT ''"
            )
        }
    }

    /**
     * 版本 24 → 25 迁移：添加手动排序位置字段
     *
     * 支持待办事项拖拽排序功能，通过 position 字段记录每项的排序位置。
     *
     * **数据结构**:
     * - `position`: INTEGER 类型，默认值为 0
     * - 排序规则：position ASC, createdAt DESC（未设置 position 的项排在最后）
     */
    private val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            /** 添加 position 字段（INTEGER 类型，默认值为 0） */
            db.execSQL(
                "ALTER TABLE todo_items ADD COLUMN position INTEGER NOT NULL DEFAULT 0"
            )

            /** 为 position 创建索引，优化排序查询性能 */
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_todo_items_position ON todo_items(position)"
            )
        }
    }

    /**
     * 版本 25 → 26 迁移：创建内容块独立表
     *
     * 支持待办事项的混合内容流（图片、语音等）独立持久化，
     * 替代原有的 imagePaths JSON 字段和 voiceNotePath 单字段方案。
     *
     * **新表结构**:
     * - content_blocks: id, todoId, type, filePath, duration, orderIndex
     */
    private val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            /** 创建内容块表 */
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS content_blocks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    todoId INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    filePath TEXT NOT NULL,
                    duration INTEGER,
                    orderIndex INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())

            /** 为 todoId 创建索引，优化按待办查询性能 */
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_content_blocks_todoId ON content_blocks(todoId)"
            )

            /**
             * 数据迁移：将现有数据从旧字段迁移到新表
             * - imagePaths (JSON数组) → ContentBlockEntity(type="image")
             * - voiceNotePath + voiceDuration → ContentBlockEntity(type="voice")
             */
            db.execSQL("""
                INSERT OR IGNORE INTO content_blocks (todoId, type, filePath, duration, orderIndex)
                SELECT
                    todo_items.id,
                    'image',
                    json_extract(value, '$[0]'),
                    NULL,
                    json_each.key
                FROM todo_items, json_each(todo_items.imagePaths)
                WHERE todo_items.imagePaths != '' AND todo_items.imagePaths != '[]'
            """.trimIndent())

            db.execSQL("""
                INSERT OR IGNORE INTO content_blocks (todoId, type, filePath, duration, orderIndex)
                SELECT
                    id,
                    'voice',
                    voiceNotePath,
                    voiceDuration,
                    (SELECT COALESCE(MAX(cb2.orderIndex), -1) + 1 FROM content_blocks cb2 WHERE cb2.todoId = todo_items.id)
                FROM todo_items
                WHERE todo_items.voiceNotePath IS NOT NULL AND todo_items.voiceNotePath != ''
            """.trimIndent())
        }
    }

    /**
     * v26 → v27: 为 inspirations 表新增 20 个字段（从 TodoEditScreen 迁移）
     *
     * 新增字段涵盖：分类、优先级、状态、时间管理(5)、地理围栏(6)、
     * 子任务/语音备注(3)、背景色、排序位置、富文本格式
     */
    private val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // --- 基础字段 ---
            db.execSQL("ALTER TABLE inspirations ADD COLUMN categoryId INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN status INTEGER NOT NULL DEFAULT 0")

            // --- 时间管理 (5 字段) ---
            db.execSQL("ALTER TABLE inspirations ADD COLUMN startDate INTEGER")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN dueDate INTEGER")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN estimatedDurationMinutes INTEGER")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN reminderTime INTEGER")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN repeatType INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN completedAt INTEGER")

            // --- 地理围栏 (6 字段) ---
            db.execSQL("ALTER TABLE inspirations ADD COLUMN geofenceLat REAL")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN geofenceLng REAL")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN geofenceRadius REAL")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN geofenceType INTEGER")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN geofenceEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN geofenceAddress TEXT")

            // --- 子任务 / 语音 / 样式 (5 字段) ---
            db.execSQL("ALTER TABLE inspirations ADD COLUMN hasSubTasks INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN voiceNotePath TEXT")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN voiceDuration INTEGER")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN backgroundColor INTEGER NOT NULL DEFAULT -1")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN position INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE inspirations ADD COLUMN contentFormat TEXT NOT NULL DEFAULT ''")

            // --- 新增索引 ---
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_categoryId ON inspirations(categoryId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_priority ON inspirations(priority)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_status_createdAt ON inspirations(status, createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_dueDate_status ON inspirations(dueDate, status)")
        }
    }

    /**
     * v27 → v28: 为 sub_tasks 表新增附件字段
     *
     * 新增字段：
     * - imagePaths: JSON 数组字符串，默认 ''，与 TodoItem.imagePaths 编码规则一致
     * - voicePaths: JSON 数组字符串，默认 ''，支持多语音
     */
    private val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 为 sub_tasks 添加附件字段（默认空字符串，向后兼容旧数据）
            db.execSQL("ALTER TABLE sub_tasks ADD COLUMN imagePaths TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE sub_tasks ADD COLUMN voicePaths TEXT NOT NULL DEFAULT ''")
        }
    }

    /**
     * 版本 28 → 29 迁移：为 todo_items 表添加 isPinned 置顶字段
     *
     * 用于左滑操作"置顶"按钮的持久化状态。
     * 旧数据默认 0（未置顶），向后兼容。
     */
    private val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 添加 isPinned 字段，默认 0（未置顶）
            db.execSQL("ALTER TABLE todo_items ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            // 创建 isPinned 索引（与 Entity 的 @Index 注解保持一致）
            db.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_isPinned ON todo_items(isPinned)")
        }
    }

    /**
     * 版本 29 → 30 迁移：移除 todo_items 表的 position 字段与索引
     *
     * 项目移除「长按待办卡片拖拽排序」功能后，
     * todo_items.position 字段与对应索引不再被任何写入路径使用，
     * 列表默认按 createdAt DESC 排序。
     *
     * **依赖**:
     * - SQLite 3.35.0+ 支持 `ALTER TABLE DROP COLUMN` 语法
     *   （Android 12 / API 31 及以上内置 SQLite 版本满足要求）
     *
     * **用户影响**:
     * - 此前通过长按拖拽保存的"手动排序位置"被丢弃，列表按创建/更新时间回退排序
     * - 用户已有数据不丢失，只是顺序回到时间序
     */
    private val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 先删除关联索引（如果存在），再删除列
            db.execSQL("DROP INDEX IF EXISTS index_todo_items_position")
            db.execSQL("ALTER TABLE todo_items DROP COLUMN position")
        }
    }

    /**
     * 版本 30 → 31 迁移：新增 todo_items.sortOrder 字段并按 createdAt DESC 回填
     *
     * 重新引入「长按拖拽排序」功能，sortOrder 作为同 isPinned 分区内的顺序来源。
     *
     * **回填策略**:
     * - 同一 isPinned 分区内，按 createdAt DESC 顺序分配 0,1,2,...
     * - 与 v30 升级前的列表视觉顺序完全一致
     *
     * **用户影响**:
     * - 升级后列表顺序不变
     * - 后续拖拽会更新 sortOrder
     */
    private val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. 新增 sortOrder 列，默认值 0（与 @ColumnInfo defaultValue 一致）
            db.execSQL(
                "ALTER TABLE todo_items ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
            )

            // 2. 按 createdAt DESC 回填 sortOrder（同一 isPinned 分区内）
            //    并列 createdAt 时按 id DESC 兜底，保证回填顺序确定
            db.execSQL("""
                UPDATE todo_items
                SET sortOrder = (
                    SELECT COUNT(*) FROM todo_items AS t2
                    WHERE t2.isPinned = todo_items.isPinned
                      AND (t2.createdAt > todo_items.createdAt
                           OR (t2.createdAt = todo_items.createdAt
                               AND t2.id > todo_items.id))
                )
            """.trimIndent())
        }
    }

    /**
     * 版本 31 → 32 迁移：sortOrder 按 zone 分段重算
     *
     * 配合 zone 状态机拖拽架构，将每个 zone 的 sortOrder 重置到固定区段，
     * 保证四 zone 互不重叠，便于后续跨 zone 拖拽时直接拼接显示列表。
     *
     * **Zone 分段方案**（每段 10000 容量，预留充足空间）:
     * - PINNED_PENDING  (isPinned=1, status=0): 0      ~ 9999
     * - PENDING         (isPinned=0, status=0): 10000  ~ 19999
     * - PINNED_COMPLETED(isPinned=1, status=1): 20000  ~ 29999
     * - COMPLETED       (isPinned=0, status=1): 30000  ~ 39999
     *
     * **重算策略**:
     * - 同一 zone 内按 createdAt ASC 顺序分配连续整数（0,1,2,...）
     * - 加上该 zone 的基础偏移量（0 / 10000 / 20000 / 30000）
     * - 空 zone 自然不会匹配任何行，无需特殊处理
     *
     * **用户影响**:
     * - 升级后列表顺序保持 createdAt ASC（与 v31 的 DESC 顺序相反，符合 zone 架构新约定）
     * - 拖拽排序写入的新值会落在对应 zone 段内
     */
    private val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. PINNED_PENDING: 0, 1, 2, ... (按 createdAt ASC 排序)
            db.execSQL("""
                UPDATE todo_items
                SET sortOrder = (
                    SELECT COUNT(*)
                    FROM todo_items t2
                    WHERE t2.isPinned = 1
                      AND t2.status = 0
                      AND t2.createdAt <= todo_items.createdAt
                ) - 1
                WHERE isPinned = 1 AND status = 0
            """.trimIndent())

            // 2. PENDING: 10000, 10001, ...
            db.execSQL("""
                UPDATE todo_items
                SET sortOrder = 10000 + (
                    SELECT COUNT(*)
                    FROM todo_items t2
                    WHERE t2.isPinned = 0
                      AND t2.status = 0
                      AND t2.createdAt <= todo_items.createdAt
                ) - 1
                WHERE isPinned = 0 AND status = 0
            """.trimIndent())

            // 3. PINNED_COMPLETED: 20000, 20001, ...
            db.execSQL("""
                UPDATE todo_items
                SET sortOrder = 20000 + (
                    SELECT COUNT(*)
                    FROM todo_items t2
                    WHERE t2.isPinned = 1
                      AND t2.status = 1
                      AND t2.createdAt <= todo_items.createdAt
                ) - 1
                WHERE isPinned = 1 AND status = 1
            """.trimIndent())

            // 4. COMPLETED: 30000, 30001, ...
            db.execSQL("""
                UPDATE todo_items
                SET sortOrder = 30000 + (
                    SELECT COUNT(*)
                    FROM todo_items t2
                    WHERE t2.isPinned = 0
                      AND t2.status = 1
                      AND t2.createdAt <= todo_items.createdAt
                ) - 1
                WHERE isPinned = 0 AND status = 1
            """.trimIndent())
        }
    }
    /**
     * 数据库迁移：版本 32 → 33
     * 新增 deleted_inspirations 表（灵感回收站）
     */
    private val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS deleted_inspirations (
                    id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL DEFAULT '',
                    tags TEXT NOT NULL DEFAULT '',
                    imagePaths TEXT NOT NULL DEFAULT '',
                    imageUrls TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    isPinned INTEGER NOT NULL DEFAULT 0,
                    isArchived INTEGER NOT NULL DEFAULT 0,
                    categoryId INTEGER NOT NULL DEFAULT 0,
                    priority INTEGER NOT NULL DEFAULT 0,
                    status INTEGER NOT NULL DEFAULT 0,
                    startDate INTEGER,
                    dueDate INTEGER,
                    estimatedDurationMinutes INTEGER,
                    reminderTime INTEGER,
                    repeatType INTEGER NOT NULL DEFAULT 0,
                    completedAt INTEGER,
                    geofenceLat REAL,
                    geofenceLng REAL,
                    geofenceRadius REAL,
                    geofenceType INTEGER,
                    geofenceEnabled INTEGER NOT NULL DEFAULT 0,
                    geofenceAddress TEXT,
                    hasSubTasks INTEGER NOT NULL DEFAULT 0,
                    voiceNotePath TEXT,
                    voiceDuration INTEGER,
                    backgroundColor INTEGER NOT NULL DEFAULT -1,
                    position INTEGER NOT NULL DEFAULT 0,
                    contentFormat TEXT NOT NULL DEFAULT '',
                    deletedAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
            """.trimIndent())
        }
    }

    /**
     * 数据库迁移：版本 33 → 34
     * special_dates 表新增 isArchived 字段（软删除）
     *
     * 依据 .trae/rules/entity与 migration同步检查.md 规则：
     * DEFAULT 0 必须与 SpecialDate.isArchived 的 @ColumnInfo(defaultValue = "0") 保持一致
     */
    internal val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. 新增 isArchived 字段，默认 0（未归档）
            db.execSQL(
                "ALTER TABLE special_dates ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0"
            )
            // 2. 加索引：isArchived 过滤是主页查询常态
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_special_dates_isArchived " +
                "ON special_dates(isArchived)"
            )
        }
    }

    /**
     * 数据库迁移：版本 34 → 35
     * special_dates 表新增 cardStyle 字段（卡片样式选择）
     *
     * 依据 .trae/rules/entity与 migration同步检查.md 规则：
     * DEFAULT 'ORANGE_TEAR_OFF' 必须与 SpecialDate.cardStyle 的 @ColumnInfo(defaultValue = "ORANGE_TEAR_OFF") 保持一致
     */
    internal val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 新增 cardStyle 字段，默认值 'ORANGE_TEAR_OFF'(与 @ColumnInfo defaultValue 一致)
            db.execSQL(
                "ALTER TABLE special_dates ADD COLUMN cardStyle TEXT NOT NULL DEFAULT 'ORANGE_TEAR_OFF'"
            )
        }
    }

    /**
     * 数据库迁移：版本 35 → 36
     * special_dates 表新增 cardColor 字段（卡片颜色选择）
     *
     * 依据 .trae/rules/entity与 migration同步检查.md 规则：
     * DEFAULT 'DEFAULT' 必须与 SpecialDate.cardColor 的 @ColumnInfo(defaultValue = "DEFAULT") 保持一致
     */
    internal val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 新增 cardColor 字段，默认值 'DEFAULT'(与 @ColumnInfo defaultValue 一致)
            db.execSQL(
                "ALTER TABLE special_dates ADD COLUMN cardColor TEXT NOT NULL DEFAULT 'DEFAULT'"
            )
        }
    }

    /**
     * 数据库迁移：版本 36 → 37
     * 新增 deleted_special_dates 表（特殊日期回收站）
     *
     * 依据 .trae/rules/entity与 migration同步检查.md 规则：
     * 所有 DEFAULT 值必须与 DeletedSpecialDate 的 @ColumnInfo(defaultValue) 保持一致
     */
    internal val MIGRATION_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS deleted_special_dates (
                    id INTEGER NOT NULL,
                    title TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT 'OTHER',
                    countMode INTEGER NOT NULL DEFAULT 0,
                    repeatType INTEGER NOT NULL DEFAULT 0,
                    reminderDays INTEGER NOT NULL DEFAULT 0,
                    content TEXT NOT NULL DEFAULT '',
                    tags TEXT NOT NULL DEFAULT '',
                    imagePaths TEXT NOT NULL DEFAULT '',
                    imageUrls TEXT NOT NULL DEFAULT '',
                    isPinned INTEGER NOT NULL DEFAULT 0,
                    isArchived INTEGER NOT NULL DEFAULT 0,
                    cardStyle TEXT NOT NULL DEFAULT 'ORANGE_TEAR_OFF',
                    cardColor TEXT NOT NULL DEFAULT 'DEFAULT',
                    targetDate INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    deletedAt INTEGER NOT NULL,
                    PRIMARY KEY(id)
                )
            """.trimIndent())
        }
    }
    // companion object 闭合
}
}
