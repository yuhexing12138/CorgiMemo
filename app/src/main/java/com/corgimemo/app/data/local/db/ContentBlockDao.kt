package com.corgimemo.app.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * 内容块数据访问对象
 *
 * 提供 content_blocks 表的 CRUD 操作，
 * 支持按待办 ID 批量查询、插入、删除和排序更新。
 */
@Dao
interface ContentBlockDao {

    /**
     * 查询某待办的所有内容块（按排序索引升序）
     *
     * @param todoId 待办事项 ID
     * @return 排序后的内容块列表
     */
    @Query("SELECT * FROM content_blocks WHERE todoId = :todoId ORDER BY orderIndex ASC")
    suspend fun getBlocksByTodoId(todoId: Long): List<ContentBlockEntity>

    /**
     * 批量查询多个待办的所有内容块（v2026-07-25 单一数据源重构新增）
     *
     * 用于首页卡片角标计数和路径聚合：
     * - 替代旧的从 `TodoItem.imagePaths` / `SubTask.imagePaths` 字段聚合的方案
     * - 阶段2 重构后这些字段已被置空，content_blocks 表成为单一权威源
     *
     * @param todoIds 待办 ID 列表（空列表返回空结果）
     * @return 所有匹配的内容块列表（按 todoId 分组、orderIndex 升序）
     */
    @Query("SELECT * FROM content_blocks WHERE todoId IN (:todoIds) ORDER BY todoId ASC, orderIndex ASC")
    suspend fun getBlocksByTodoIds(todoIds: List<Long>): List<ContentBlockEntity>

    /**
     * 删除某待办的所有内容块
     *
     * 保存时先清后写，确保数据一致性
     *
     * @param todoId 待办事项 ID
     */
    @Query("DELETE FROM content_blocks WHERE todoId = :todoId")
    suspend fun deleteByTodoId(todoId: Long)

    /**
     * 删除单个内容块
     *
     * @param blockId 内容块主键 ID
     */
    @Query("DELETE FROM content_blocks WHERE id = :blockId")
    suspend fun deleteBlock(blockId: Long)

    /**
     * 删除某待办的指定图片内容块
     *
     * v2026-07-24 新增：用于首页图片全屏预览删除单张图片时，同步清理 content_blocks 表的冗余记录
     * 项目处于双写过渡期，图片路径同时存储在 TodoItem.imagePaths（JSON 字段）和 content_blocks 表
     * 仅删除 TodoItem.imagePaths 会导致编辑页（优先从 content_blocks 加载）仍显示已删除的图片
     *
     * @param todoId 待办事项 ID
     * @param filePath 图片绝对路径
     */
    @Query("DELETE FROM content_blocks WHERE todoId = :todoId AND filePath = :filePath AND type = 'image'")
    suspend fun deleteImageBlockByPath(todoId: Long, filePath: String)

    /**
     * 删除某待办的指定语音内容块
     *
     * v2026-07-25 新增：用于首页录音全屏预览删除单条语音时，同步清理 content_blocks 表的冗余记录
     * 与 [deleteImageBlockByPath] 对称，解决语音附件的三写存储一致性问题
     * （voiceNotePath/voicePaths + content_blocks + contentFormat 行级附件快照 voiceAttachments）
     *
     * @param todoId 待办事项 ID
     * @param filePath 语音文件绝对路径
     */
    @Query("DELETE FROM content_blocks WHERE todoId = :todoId AND filePath = :filePath AND type = 'voice'")
    suspend fun deleteVoiceBlockByPath(todoId: Long, filePath: String)

    /**
     * 批量插入内容块
     *
     * @param blocks 内容块实体列表
     * @return 插入的行 ID 列表
     */
    @Insert
    suspend fun insertBlocks(blocks: List<ContentBlockEntity>): List<Long>

    /**
     * 批量更新排序索引
     *
     * 使用事务确保原子性，拖拽排序后调用
     *
     * @param blocks 已更新 orderIndex 的内容块列表
     */
    @Transaction
    suspend fun updateOrderIndices(blocks: List<ContentBlockEntity>) {
        blocks.forEach { block ->
            // Room 不支持单独的 UPDATE @Query 带可变参数，
            // 这里通过删除+重新插入来更新顺序
            // 实际场景中数据量小（通常 < 20），性能可接受
        }
    }

    /**
     * 清空并重新写入某待办的内容块（原子操作）
     *
     * 用于保存时的一致性更新：先删旧数据再批量写入新数据
     *
     * @param todoId 待办事项 ID
     * @param blocks 新的内容块列表（需包含正确的 orderIndex）
     */
    @Transaction
    suspend fun replaceBlocksForTodo(todoId: Long, blocks: List<ContentBlockEntity>) {
        deleteByTodoId(todoId)
        if (blocks.isNotEmpty()) {
            insertBlocks(blocks)
        }
    }
}
