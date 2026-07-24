package com.corgimemo.app.data.seed

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.corgimemo.app.data.local.db.ContentBlockEntity
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
        // v2026-07-25 单一数据源重构：标记旧字段附件是否已迁移到 content_blocks 表
        private const val KEY_ATTACHMENTS_MIGRATED = "attachments_migrated_to_content_blocks"
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

                // 步骤 9：同步附件到 content_blocks 表（v2026-07-25 单一数据源重构）
                Log.d(tag, "步骤 9/9：同步附件到 content_blocks 表...")
                syncSeedAttachmentsToContentBlocks(todoIds, inspirationIds, imagePaths, voicePaths)
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

    /**
     * 迁移旧字段附件到 content_blocks 表（v2026-07-25 单一数据源重构）
     *
     * ## 背景
     *
     * 已有用户（种子数据已注入，`KEY_SEEDED = true`）的 `todo_items.imagePaths`/`voiceNotePath`
     * 和 `inspirations.imagePaths` 字段可能有值，但 `content_blocks` 表为空。
     * 这是因为种子数据最初用旧逻辑写入，而 v47+ 的加载逻辑只从 `content_blocks` 表读取。
     *
     * ## 触发条件
     *
     * - `KEY_ATTACHMENTS_MIGRATED = false`（未迁移过）
     *
     * ## 迁移逻辑
     *
     * 1. 查询所有 TodoItem，解析 `imagePaths`/`voiceNotePath`，写入 `content_blocks` 表
     * 2. 查询所有 Inspiration，解析 `imagePaths`，写入 `content_blocks` 表
     * 3. 跳过已有 `content_blocks` 记录的 TodoItem/Inspiration（幂等）
     * 4. 迁移完成后设置 `KEY_ATTACHMENTS_MIGRATED = true`
     *
     * ## 调用时机
     *
     * APP 启动时调用，与 `seedIfNeeded()` 并列。
     */
    suspend fun migrateAttachmentsIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ATTACHMENTS_MIGRATED, false)) {
            Log.d(tag, "附件已迁移到 content_blocks，跳过")
            return
        }

        Log.d(tag, "🔄 开始迁移旧字段附件到 content_blocks 表...")

        try {
            val contentBlockDao = database.contentBlockDao()
            val todoDao = database.todoDao()
            val inspirationDao = database.inspirationDao()
            var migratedCount = 0

            database.withTransaction {
                // 1. 迁移 TodoItem 附件
                val todos = todoDao.getAllTodosBlocking()
                for (todo in todos) {
                    // 跳过没有附件的 TodoItem
                    val hasImages = todo.imagePaths.isNotBlank() && todo.imagePaths != "[]"
                    val hasVoice = !todo.voiceNotePath.isNullOrBlank()
                    if (!hasImages && !hasVoice) continue

                    // 检查 content_blocks 表是否已有该 TodoItem 的记录（幂等）
                    val existingBlocks = contentBlockDao.getBlocksByTodoId(todo.id)
                    if (existingBlocks.isNotEmpty()) {
                        Log.d(tag, "TodoItem ${todo.id} 已有 ${existingBlocks.size} 条 content_blocks 记录，跳过")
                        continue
                    }

                    val entities = mutableListOf<ContentBlockEntity>()
                    var orderIndex = 0

                    // 解析 imagePaths (JSON 数组)
                    if (hasImages) {
                        try {
                            val jsonArray = org.json.JSONArray(todo.imagePaths)
                            for (i in 0 until jsonArray.length()) {
                                val path = jsonArray.optString(i)
                                if (path.isNotBlank()) {
                                    entities.add(
                                        ContentBlockEntity(
                                            todoId = todo.id,
                                            type = "image",
                                            filePath = path,
                                            orderIndex = orderIndex++,
                                            subTaskId = null,
                                            lineIndex = 0
                                        )
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "解析 TodoItem ${todo.id} 的 imagePaths 失败: ${todo.imagePaths}", e)
                        }
                    }

                    // 解析 voiceNotePath
                    if (hasVoice) {
                        entities.add(
                            ContentBlockEntity(
                                todoId = todo.id,
                                type = "voice",
                                filePath = todo.voiceNotePath!!,
                                duration = todo.voiceDuration,
                                orderIndex = orderIndex++,
                                subTaskId = null,
                                lineIndex = 0
                            )
                        )
                    }

                    if (entities.isNotEmpty()) {
                        contentBlockDao.insertBlocks(entities)
                        migratedCount += entities.size
                    }
                }

                // 2. 迁移 Inspiration 附件
                val inspirations = inspirationDao.getAllInspirationsBlocking()
                for (inspiration in inspirations) {
                    val hasImages = inspiration.imagePaths.isNotBlank() && inspiration.imagePaths != "[]"
                    if (!hasImages) continue

                    // 检查 content_blocks 表是否已有该 Inspiration 的记录（幂等）
                    val existingBlocks = contentBlockDao.getBlocksByTodoId(inspiration.id)
                    if (existingBlocks.isNotEmpty()) {
                        Log.d(tag, "Inspiration ${inspiration.id} 已有 ${existingBlocks.size} 条 content_blocks 记录，跳过")
                        continue
                    }

                    val entities = mutableListOf<ContentBlockEntity>()
                    try {
                        val jsonArray = org.json.JSONArray(inspiration.imagePaths)
                        for (i in 0 until jsonArray.length()) {
                            val path = jsonArray.optString(i)
                            if (path.isNotBlank()) {
                                entities.add(
                                    ContentBlockEntity(
                                        todoId = inspiration.id, // content_blocks 表用 todoId 字段统一存储
                                        type = "image",
                                        filePath = path,
                                        orderIndex = i,
                                        subTaskId = null,
                                        lineIndex = 0
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "解析 Inspiration ${inspiration.id} 的 imagePaths 失败: ${inspiration.imagePaths}", e)
                    }

                    if (entities.isNotEmpty()) {
                        contentBlockDao.insertBlocks(entities)
                        migratedCount += entities.size
                    }
                }
            }

            // 迁移成功后更新标志位
            prefs.edit().putBoolean(KEY_ATTACHMENTS_MIGRATED, true).apply()
            Log.d(tag, "✅ 附件迁移完成（共迁移 $migratedCount 条记录到 content_blocks 表）")
        } catch (e: Exception) {
            Log.e(tag, "❌ 附件迁移失败: ${e.message}", e)
            // 不抛出异常，避免阻塞 APP 启动；下次启动会重试
        }
    }

    /**
     * 同步种子数据附件到 content_blocks 表（v2026-07-25 单一数据源重构）
     *
     * ## 背景
     *
     * 种子数据最初写入 `imagePaths`/`voiceNotePath` 字段（旧逻辑），
     * 但 v47+ 的加载逻辑只从 `content_blocks` 表读取附件。
     * 如果不同步，全新安装/卸载重装后种子数据的附件会消失。
     *
     * ## 处理逻辑
     *
     * 1. 遍历 TodoItem（T1-T7），把图片和语音写入 `content_blocks` 表
     * 2. 遍历 Inspiration（I1-I7），把图片写入 `content_blocks` 表
     * 3. 语音时长从硬编码映射获取（与 TodoSeedData 保持一致）
     *
     * ## 注意
     *
     * - 不清空旧的 `imagePaths`/`voiceNotePath` 字段（加载逻辑不再读取，无影响）
     * - 用户后续编辑保存时，`performSave()` 会自动清空旧字段
     *
     * @param todoIds TodoItem ID 映射（T1-T7 → todoId）
     * @param inspirationIds Inspiration ID 映射（I1-I7 → inspirationId）
     * @param imagePaths 图片路径映射（数据编号 → 路径列表）
     * @param voicePaths 语音路径映射（数据编号 → 路径）
     */
    private suspend fun syncSeedAttachmentsToContentBlocks(
        todoIds: Map<String, Long>,
        inspirationIds: Map<String, Long>,
        imagePaths: Map<String, List<String>>,
        voicePaths: Map<String, String>
    ) {
        val contentBlockDao = database.contentBlockDao()

        // 种子数据的语音时长（硬编码，与 TodoSeedData 中的 voiceDuration 保持一致）
        // T1=8s, T2=28s, T3=65s, T4=5s, T5=32s, T6=70s, T7 无语音
        val voiceDurations = mapOf(
            "T1" to 8, "T2" to 28, "T3" to 65,
            "T4" to 5, "T5" to 32, "T6" to 70
        )

        var totalSynced = 0

        // 1. 同步 Todo 附件（图片 + 语音）
        todoIds.forEach { (todoKey, todoId) ->
            val images = imagePaths[todoKey] ?: emptyList()
            val voicePath = voicePaths[todoKey]
            val voiceDuration = voiceDurations[todoKey]

            val entities = mutableListOf<ContentBlockEntity>()
            var orderIndex = 0

            // 图片附件（lineIndex=0 表示父待办行）
            images.forEach { path ->
                entities.add(
                    ContentBlockEntity(
                        todoId = todoId,
                        type = "image",
                        filePath = path,
                        orderIndex = orderIndex++,
                        subTaskId = null,
                        lineIndex = 0
                    )
                )
            }

            // 语音附件（lineIndex=0 表示父待办行）
            if (!voicePath.isNullOrBlank()) {
                entities.add(
                    ContentBlockEntity(
                        todoId = todoId,
                        type = "voice",
                        filePath = voicePath,
                        duration = voiceDuration,
                        orderIndex = orderIndex++,
                        subTaskId = null,
                        lineIndex = 0
                    )
                )
            }

            if (entities.isNotEmpty()) {
                contentBlockDao.insertBlocks(entities)
                totalSynced += entities.size
            }
        }

        // 2. 同步 Inspiration 附件（仅图片，灵感种子数据无语音）
        inspirationIds.forEach { (inspirationKey, inspirationId) ->
            val images = imagePaths[inspirationKey] ?: emptyList()

            val entities = mutableListOf<ContentBlockEntity>()
            images.forEachIndexed { index, path ->
                entities.add(
                    ContentBlockEntity(
                        todoId = inspirationId, // content_blocks 表用 todoId 字段统一存储（待办/灵感共用）
                        type = "image",
                        filePath = path,
                        orderIndex = index,
                        subTaskId = null,
                        lineIndex = 0
                    )
                )
            }

            if (entities.isNotEmpty()) {
                contentBlockDao.insertBlocks(entities)
                totalSynced += entities.size
            }
        }

        Log.d(tag, "✅ 步骤 9/9 附件同步完成（共 $totalSynced 条记录写入 content_blocks 表）")
    }
}
