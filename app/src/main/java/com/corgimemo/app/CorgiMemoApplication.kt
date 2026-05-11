package com.corgimemo.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

/**
 * CorgiMemo 应用的 Application 类
 * 
 * 负责初始化应用级配置，包括 Koin 依赖注入框架
 */
class CorgiMemoApplication : Application() {

    /**
     * 应用启动时调用
     * 在这里初始化 Koin 依赖注入框架
     */
    override fun onCreate() {
        super.onCreate()
        
        // 初始化 Koin
        startKoin {
            // 启用 Android 日志记录
            androidLogger()
            
            // 设置 Android 上下文
            androidContext(this@CorgiMemoApplication)
            
            // 加载 Koin 模块配置
            modules(appModule)
        }
    }
}