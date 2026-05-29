package com.corgimemo.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.corgimemo.app.data.local.db.AchievementEntity
import com.corgimemo.app.data.local.db.CategoryKeywordEntity
import com.corgimemo.app.data.local.db.OperationLogEntity
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.DeletedTodo
import com.corgimemo.app.data.model.MoodHistory
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem
import com.corgimemo.app.data.model.Inspiration
import com.corgimemo.app.data.model.InspirationRelation
import com.corgimemo.app.data.model.SpecialDate
import com.corgimemo.app.data.model.SpecialDateRelation
import com.corgimemo.app.data.model.UserTemplateEntity

/**
 * 应用数据库
 * 管理待办事项、柯基数据、任务分类、成就和用户模板
 */
@Database(
    entities = [TodoItem::class, CorgiData::class, Category::class, DeletedTodo::class, MoodHistory::class, SubTask::class, AchievementEntity::class, TaskDailyStats::class, CategoryKeywordEntity::class, UserTemplateEntity::class, OperationLogEntity::class, Inspiration::class, InspirationRelation::class, SpecialDate::class, SpecialDateRelation::class],
    version = 19,
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

    /** 灵感记录 DAO */
    abstract fun inspirationDao(): InspirationDao

    /** 灵感关联关系 DAO */
    abstract fun inspirationRelationDao(): InspirationRelationDao

    /** 特殊日期 DAO */
    abstract fun specialDateDao(): SpecialDateDao

    /** 特殊日期关联关系 DAO */
    abstract fun specialDateRelationDao(): SpecialDateRelationDao

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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceLat REAL")
                database.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceLng REAL")
                database.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceRadius REAL")
                database.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceType INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceEnabled INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE todo_items ADD COLUMN geofenceAddress TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        type INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                database.execSQL("ALTER TABLE corgi_data ADD COLUMN unlockedAchievements TEXT NOT NULL DEFAULT '[]'")
                database.execSQL("ALTER TABLE corgi_data ADD COLUMN maxConsecutiveDays INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
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
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
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
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sub_tasks_todoId ON sub_tasks(todoId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sub_tasks_todoId_order ON sub_tasks(todoId, `order`)")
            }
        }

        /**
         * 数据库迁移：版本 6 → 7
         * 添加 todo_items 表的 hasSubTasks 字段
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加 hasSubTasks 字段，默认值为 false
                database.execSQL("ALTER TABLE todo_items ADD COLUMN hasSubTasks INTEGER NOT NULL DEFAULT 0")
                // 为 hasSubTasks 创建索引，提高查询效率
                database.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_hasSubTasks ON todo_items(hasSubTasks)")
            }
        }

        /**
         * 数据库迁移：版本 7 → 8
         * 创建 achievements 表并添加 corgi_data 表的新字段
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建 achievements 表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS achievements (
                        id TEXT PRIMARY KEY NOT NULL,
                        unlockedAt INTEGER
                    )
                """.trimIndent())

                // 为 corgi_data 添加新字段
                database.execSQL("ALTER TABLE corgi_data ADD COLUMN consecutiveEarlyDays INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE corgi_data ADD COLUMN lastEarlyDate TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 数据库迁移：版本 8 → 9
         * 创建 task_daily_stats 表用于每日任务统计
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
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
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS category_keywords (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        keyword TEXT NOT NULL,
                        categoryType INTEGER NOT NULL,
                        matchType INTEGER NOT NULL,
                        isUserDefined INTEGER NOT NULL
                    )
                """.trimIndent())

                database.execSQL("""
                    CREATE INDEX IF NOT EXISTS index_category_keywords_keyword 
                    ON category_keywords(keyword)
                """.trimIndent())

                database.execSQL("""
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
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
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

                database.execSQL("""
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

                database.execSQL("DROP TABLE todo_items")

                database.execSQL("ALTER TABLE todo_items_new RENAME TO todo_items")

                database.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_status_createdAt ON todo_items(status, createdAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_categoryId_status ON todo_items(categoryId, status)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_priority_startDate ON todo_items(priority, startDate)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_todo_items_hasSubTasks ON todo_items(hasSubTasks)")
            }
        }

        /**
         * 数据库迁移：版本 11 → 12
         * 添加语音备注相关字段
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加语音备注文件路径字段
                database.execSQL("ALTER TABLE todo_items ADD COLUMN voiceNotePath TEXT")
                // 添加语音时长字段（秒）
                database.execSQL("ALTER TABLE todo_items ADD COLUMN voiceDuration INTEGER")
            }
        }

        /**
         * 版本 12 → 13 迁移：添加用户自定义模板表
         * 用于支持用户创建和保存自己的待办模板
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
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
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
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
                database.execSQL(
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
            override fun migrate(database: SupportSQLiteDatabase) {
                /** 1. 创建新表（与 Room Entity 定义完全匹配） */
                database.execSQL("""
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
                database.execSQL("""
                    INSERT INTO operation_logs_new (id, operation_type, target_id, batch_ids_json, snapshot_json, created_at)
                    SELECT id, operation_type, target_id, batch_ids_json, snapshot_json, created_at
                    FROM operation_logs
                """.trimIndent())

                /** 3. 删除旧表 */
                database.execSQL("DROP TABLE operation_logs")

                /** 4. 重命名新表 */
                database.execSQL("ALTER TABLE operation_logs_new RENAME TO operation_logs")

                /** 5. 创建正确名称的索引 */
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_operation_logs_created_at ON operation_logs(created_at)"
                )
            }
        }

        /**
     * 版本 15 → 16 迁移：添加 deleted_todos 表
     * 用于"最近删除"功能的数据持久化
     */
    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
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
        override fun migrate(database: SupportSQLiteDatabase) {
            // 创建 inspirations 表（灵感记录主表）
            // 注意：字段约束必须与 Inspiration.kt Entity 注解完全一致
            database.execSQL("""
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
            database.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_createdAt ON inspirations(createdAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_inspirations_isPinned ON inspirations(isPinned)")

            // 创建 inspiration_relations 表（关联关系表）
            database.execSQL("""
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
            database.execSQL("CREATE INDEX IF NOT EXISTS index_inspiration_relations_inspirationId ON inspiration_relations(inspirationId)")
        }
    }

    /**
     * 版本 17 → 18 迁移：添加特殊日期记录功能
     * 创建 special_dates 表和 special_date_relations 关联表
     */
    private val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
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

            database.execSQL("CREATE INDEX IF NOT EXISTS index_special_dates_targetDate ON special_dates(targetDate)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_special_dates_isPinned ON special_dates(isPinned)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_special_dates_category ON special_dates(category)")

            database.execSQL("""
                CREATE TABLE IF NOT EXISTS special_date_relations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    specialDateId INTEGER NOT NULL,
                    targetType TEXT NOT NULL,
                    targetId INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000),
                    FOREIGN KEY(specialDateId) REFERENCES special_dates(id) ON DELETE CASCADE
                )
            """.trimIndent())

            database.execSQL("CREATE INDEX IF NOT EXISTS index_special_date_relations_specialDateId ON special_date_relations(specialDateId)")
        }
    }

    /**
     * 版本 18 → 19 迁移：为 todo_items 表添加图片路径字段
     * 支持待办事项插入多张图片功能
     */
    private val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(database: SupportSQLiteDatabase) {
            /** 添加 imagePaths 字段，用于存储图片路径的 JSON 数组 */
            database.execSQL("ALTER TABLE todo_items ADD COLUMN imagePaths TEXT NOT NULL DEFAULT ''")
        }
    }
}
}
