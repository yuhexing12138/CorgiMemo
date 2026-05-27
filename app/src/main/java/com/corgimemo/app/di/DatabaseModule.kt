package com.corgimemo.app.di

import android.content.Context
import com.corgimemo.app.data.local.db.AchievementDao
import com.corgimemo.app.data.local.db.CategoryDao
import com.corgimemo.app.data.local.db.CategoryKeywordDao
import com.corgimemo.app.data.local.db.CorgiDao
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.local.db.DeletedTodoDao
import com.corgimemo.app.data.local.db.InspirationDao
import com.corgimemo.app.data.local.db.InspirationRelationDao
import com.corgimemo.app.data.local.db.SpecialDateDao
import com.corgimemo.app.data.local.db.SpecialDateRelationDao
import com.corgimemo.app.data.local.db.MoodHistoryDao
import com.corgimemo.app.data.local.db.OperationLogDao
import com.corgimemo.app.data.local.db.TaskDailyStatsDao
import com.corgimemo.app.data.local.db.TodoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideCorgiMemoDatabase(
        @ApplicationContext context: Context
    ): CorgiMemoDatabase {
        return CorgiMemoDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideTodoDao(database: CorgiMemoDatabase): TodoDao {
        return database.todoDao()
    }

    @Provides
    @Singleton
    fun provideCorgiDao(database: CorgiMemoDatabase): CorgiDao {
        return database.corgiDao()
    }

    @Provides
    @Singleton
    fun provideCategoryDao(database: CorgiMemoDatabase): CategoryDao {
        return database.categoryDao()
    }

    @Provides
    @Singleton
    fun provideMoodHistoryDao(database: CorgiMemoDatabase): MoodHistoryDao {
        return database.moodHistoryDao()
    }

    @Provides
    @Singleton
    fun provideAchievementDao(database: CorgiMemoDatabase): AchievementDao {
        return database.achievementDao()
    }

    @Provides
    @Singleton
    fun provideTaskDailyStatsDao(database: CorgiMemoDatabase): TaskDailyStatsDao {
        return database.taskDailyStatsDao()
    }

    @Provides
    @Singleton
    fun provideCategoryKeywordDao(database: CorgiMemoDatabase): CategoryKeywordDao {
        return database.categoryKeywordDao()
    }

    @Provides
    @Singleton
    fun provideOperationLogDao(database: CorgiMemoDatabase): OperationLogDao {
        return database.operationLogDao()
    }

    @Provides
    @Singleton
    fun provideDeletedTodoDao(database: CorgiMemoDatabase): DeletedTodoDao {
        return database.deletedTodoDao()
    }

    /** 灵感记录 DAO */
    @Provides
    @Singleton
    fun provideInspirationDao(database: CorgiMemoDatabase): InspirationDao {
        return database.inspirationDao()
    }

    /** 灵感关联关系 DAO */
    @Provides
    @Singleton
    fun provideInspirationRelationDao(database: CorgiMemoDatabase): InspirationRelationDao {
        return database.inspirationRelationDao()
    }

    /** 特殊日期 DAO */
    @Provides
    @Singleton
    fun provideSpecialDateDao(database: CorgiMemoDatabase): SpecialDateDao {
        return database.specialDateDao()
    }

    /** 特殊日期关联关系 DAO */
    @Provides
    @Singleton
    fun provideSpecialDateRelationDao(database: CorgiMemoDatabase): SpecialDateRelationDao {
        return database.specialDateRelationDao()
    }
}
