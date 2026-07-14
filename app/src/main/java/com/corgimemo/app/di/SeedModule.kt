package com.corgimemo.app.di

import android.content.Context
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.seed.DemoDataSeeder
import com.corgimemo.app.data.seed.DemoResourceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 演示种子数据注入模块
 *
 * 提供 [DemoResourceManager] 和 [DemoDataSeeder] 的 Hilt 单例绑定。
 *
 * - DemoResourceManager 负责将 drawable 图片复制到内部存储、生成静音音频文件
 * - DemoDataSeeder 负责首次启动时的种子数据注入（待办/灵感/日期/跨模块关联）
 *
 * 注入触发点位于 [com.corgimemo.app.CorgiMemoApplication.onCreate]，
 * 通过 SharedPreferences 标志位保证幂等性，避免重复注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object SeedModule {

    /**
     * 提供演示资源管理器单例
     *
     * @param context 应用上下文（用于访问 resources 和 filesDir）
     * @return [DemoResourceManager] 实例
     */
    @Provides
    @Singleton
    fun provideDemoResourceManager(
        @ApplicationContext context: Context
    ): DemoResourceManager {
        return DemoResourceManager(context)
    }

    /**
     * 提演示数据注入器单例
     *
     * @param context 应用上下文（用于访问 SharedPreferences）
     * @param database Room 数据库实例（用于事务性注入）
     * @param resourceManager 资源管理器（用于获取图片/语音文件路径）
     * @return [DemoDataSeeder] 实例
     */
    @Provides
    @Singleton
    fun provideDemoDataSeeder(
        @ApplicationContext context: Context,
        database: CorgiMemoDatabase,
        resourceManager: DemoResourceManager
    ): DemoDataSeeder {
        return DemoDataSeeder(context, database, resourceManager)
    }
}
