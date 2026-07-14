package com.corgimemo.app.data.seed

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CategoryType
import com.corgimemo.app.data.model.DefaultCategoryName
import javax.inject.Inject

/**
 * 演示种子数据总编排器
 *
 * 职责：
 * - 检查 SharedPreferences 标志位，判断是否需要注入
 * - 按严格依赖顺序编排各 Seeder 的执行
 * - 管理 Room 事务，确保原子性
 * - 注入成功后更新标志位
 *
 * 注入顺序（严格依赖关系）：
 * 1. 资源准备（图片 + 语音）
 * 2. Category 注入
 * 3. Todo 注入（依赖 categoryId、图片路径、语音路径）
 * 4. SubTask 注入（依赖 todoId，由 TodoSeedData 内部处理）
 * 5. Inspiration 注入（依赖 categoryId、图片路径）
 * 6. SpecialDate 注入（依赖图片路径）
 * 7. CardRelation 注入（依赖 todoId、inspirationId、dateId）
 * 8. RecycleBin 注入（依赖 categoryId，独立于主表数据）
 *
 * @param context 应用上下文
 * @param database 数据库实例
 * @param resourceManager 资源管理器
 */
class DemoDataSeeder @Inject constructor(
    private val context: Context,
    private val database: CorgiMemoDatabase,
    private val resourceManager: DemoResourceManager
) {
    private val tag = "DemoSeeder"

    companion object {
        private const val PREFS_NAME = "corgimemo_demo_prefs"
        private const val KEY_SEEDED = "demo_data_seeded"
    }

    /**
     * 检查标志位并执行种子数据注入
     *
     * - 标志位为 true：跳过注入
     * - 标志位为 false 或不存在：执行注入，成功后置 true
     */
    suspend fun seedIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) {
            Log.d(tag, "演示数据已注入，跳过")
            return
        }

        Log.d(tag, "🚀 开始注入演示种子数据...")

        try {
            database.withTransaction {
                // 步骤 1：资源准备
                Log.d(tag, "步骤 1/8：准备资源...")
                val imagePaths = resourceManager.prepareAllImages()
                val voicePaths = resourceManager.prepareAllVoice()

                // 步骤 2：Category 注入
                Log.d(tag, "步骤 2/8：注入分类...")
                val categoryIds = seedCategories()

                // 步骤 3+4：Todo + SubTask 注入
                Log.d(tag, "步骤 3/8：注入待办...")
                val todoSeedData = TodoSeedData(database.todoDao(), database.subTaskDao())
                val todoIds = todoSeedData.seed(categoryIds, imagePaths, voicePaths)

                // 步骤 5：Inspiration 注入
                Log.d(tag, "步骤 5/8：注入灵感...")
                val inspirationSeedData = InspirationSeedData(database.inspirationDao())
                val inspirationIds = inspirationSeedData.seed(categoryIds, imagePaths)

                // 步骤 6：SpecialDate 注入
                Log.d(tag, "步骤 6/8：注入日期...")
                val dateSeedData = DateSeedData(database.specialDateDao())
                val dateIds = dateSeedData.seed(imagePaths)

                // 步骤 7：CardRelation 注入
                Log.d(tag, "步骤 7/8：注入关联...")
                val relationSeedData = RelationSeedData(database.cardRelationDao())
                relationSeedData.seed(todoIds, inspirationIds, dateIds)

                // 步骤 8：RecycleBin 注入
                Log.d(tag, "步骤 8/8：注入回收站数据...")
                val recycleBinSeedData = RecycleBinSeedData(
                    database.deletedTodoDao(),
                    database.deletedInspirationDao(),
                    database.deletedSpecialDateDao()
                )
                recycleBinSeedData.seed(categoryIds)
            }

            // 事务成功后更新标志位
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
            Log.d(tag, "✅ 演示种子数据注入完成！")
        } catch (e: Exception) {
            Log.e(tag, "❌ 演示种子数据注入失败: ${e.message}", e)
            throw e
        }
    }

    /**
     * 注入 Category 数据
     *
     * 先查询现有分类，仅在缺失时插入，避免主键冲突。
     *
     * @return 分类 ID 映射（type → categoryId）
     */
    private suspend fun seedCategories(): Map<Int, Long> {
        val categoryDao = database.categoryDao()
        val categoryIds = mutableMapOf<Int, Long>()

        // 定义需要的分类
        val requiredCategories = listOf(
            Triple(DefaultCategoryName.STUDY, CategoryType.STUDY, true),
            Triple(DefaultCategoryName.WORK, CategoryType.WORK, true),
            Triple(DefaultCategoryName.LIFE, CategoryType.LIFE, true),
            Triple(DefaultCategoryName.SPORT, CategoryType.SPORT, true)
        )

        for ((name, type, isDefault) in requiredCategories) {
            // 先查询是否已存在
            val existing = categoryDao.getCategoryByType(type)
            if (existing != null) {
                categoryIds[type] = existing.id
            } else {
                // 不存在则插入
                val id = categoryDao.insert(Category(name = name, type = type, isDefault = isDefault))
                categoryIds[type] = id
            }
        }

        Log.d(tag, "✅ 步骤 2/8 分类注入完成（4 条）")
        return categoryIds
    }
}
