package com.corgimemo.app.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.TodoItem

@Database(
    entities = [TodoItem::class, CorgiData::class],
    version = 2,
    exportSchema = false
)
abstract class CorgiMemoDatabase : RoomDatabase() {

    abstract fun todoDao(): TodoDao

    abstract fun corgiDao(): CorgiDao

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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
