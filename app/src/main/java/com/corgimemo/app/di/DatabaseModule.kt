package com.corgimemo.app.di

import android.content.Context
import com.corgimemo.app.data.local.db.CorgiDao
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
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
}
