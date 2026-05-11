package com.corgimemo.app.di

import android.content.Context
import com.corgimemo.app.data.local.db.AppDatabase
import com.corgimemo.app.data.local.db.TodoDao
import com.corgimemo.app.data.repository.TodoRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin 依赖注入模块配置
 * 
 * 定义应用中所有需要依赖注入的组件
 */
val appModule = module {

    // 单例：数据库实例
    single {
        AppDatabase.getInstance(androidContext())
    }

    // 单例：TodoDao
    single<TodoDao> {
        get<AppDatabase>().todoDao()
    }

    // 单例：TodoRepository
    single {
        TodoRepository(get())
    }

    // ViewModel 定义（使用 by viewModels() 或 koinViewModel() 自动注入）
    // ViewModel 的注入通过 Koin 的 viewModel 函数自动处理
}