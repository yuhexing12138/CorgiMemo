package com.corgimemo.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.corgimemo.app.data.model.TodoItem

/**
 * 应用数据库类
 * 
 * 使用 Room 数据库框架定义数据库结构
 * 
 * @property todoDao 待办事项数据访问对象
 */
@Database(
    entities = [TodoItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    /**
     * 获取待办事项 DAO
     */
    abstract fun todoDao(): TodoDao

    companion object {
        // 数据库名称
        private const val DATABASE_NAME = "corgimemo_db"

        // 单例实例（volatile 保证可见性）
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * 获取数据库实例（单例模式）
         * 
         * @param context Android 上下文
         * @return 数据库实例
         */
        fun getInstance(context: Context): AppDatabase {
            // 使用双重检查锁定确保线程安全
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    // 数据库升级策略：如果数据库版本更新且没有迁移方案，重新创建数据库
                    .fallbackToDestructiveMigration()
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}