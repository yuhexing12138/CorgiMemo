package com.corgimemo.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.MoodHistory
import com.corgimemo.app.data.model.SubTask
import com.corgimemo.app.data.model.TodoItem

/**
 * 应用数据库
 * 管理待办事项、柯基数据和任务分类
 */
@Database(
    entities = [TodoItem::class, CorgiData::class, Category::class, MoodHistory::class, SubTask::class],
    version = 7,
    exportSchema = false
)
abstract class CorgiMemoDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

    abstract fun corgiDao(): CorgiDao

    abstract fun categoryDao(): CategoryDao

    abstract fun moodHistoryDao(): MoodHistoryDao

    abstract fun subTaskDao(): SubTaskDao

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
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
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
    }
}
