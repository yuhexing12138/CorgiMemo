package com.corgimemo.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.corgimemo.app.data.local.db.AchievementEntity
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.MoodHistory
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem

/**
 * 应用数据库
 * 管理待办事项、柯基数据、任务分类和成就
 */
@Database(
    entities = [TodoItem::class, CorgiData::class, Category::class, MoodHistory::class, SubTask::class, AchievementEntity::class, TaskDailyStats::class, CategoryKeywordEntity::class],
    version = 11,
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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
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
    }
}
