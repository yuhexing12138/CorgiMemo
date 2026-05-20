package com.corgimemo.app.backup

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import com.corgimemo.app.backup.crypto.EncryptionManager
import com.corgimemo.app.backup.data.BackupContainer
import com.corgimemo.app.backup.data.BackupCorgiData
import com.corgimemo.app.backup.data.BackupData
import com.corgimemo.app.backup.data.BackupMeta
import com.corgimemo.app.backup.data.BackupMoodHistory
import com.corgimemo.app.backup.data.BackupTodoItem
import com.corgimemo.app.backup.data.BackupCategory
import com.corgimemo.app.backup.exporter.IcsExporter
import com.corgimemo.app.backup.serializer.CsvSerializer
import com.corgimemo.app.backup.serializer.JsonSerializer
import com.corgimemo.app.data.local.db.CorgiDao
import com.corgimemo.app.data.local.db.MoodHistoryDao
import com.corgimemo.app.data.local.db.TodoDao
import com.corgimemo.app.data.local.db.CategoryDao
import com.corgimemo.app.data.local.db.CorgiMemoDatabase
import com.corgimemo.app.data.local.datastore.CorgiPreferences
import com.corgimemo.app.data.model.Category
import com.corgimemo.app.data.model.CorgiData
import com.corgimemo.app.data.model.MoodHistory
import com.corgimemo.app.data.model.TodoItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * 备份管理器
 * 负责数据的导出和恢复
 */
object BackupManager {

    /**
     * 导出格式
     */
    enum class ExportFormat {
        JSON,
        CSV,
        ICAL,
        IMAGE
    }

    /**
     * 导出结果
     */
    sealed class ExportResult {
        data class Success(val fileUri: Uri) : ExportResult()
        data class Error(val message: String) : ExportResult()
    }

    /**
     * 恢复结果
     */
    sealed class RestoreResult {
        data class Success(val todoCount: Int, val categoryCount: Int) : RestoreResult()
        data class Error(val message: String) : RestoreResult()
        object WrongPassword : RestoreResult()
        object VersionIncompatible : RestoreResult()
    }

    /**
     * 导出数据
     *
     * @param context 上下文
     * @param uri 目标文件 URI
     * @param format 导出格式
     * @param password 密码（可选，仅 JSON 格式支持）
     * @return 导出结果
     */
    suspend fun exportData(
        context: Context,
        uri: Uri,
        format: ExportFormat,
        password: String? = null
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val database = CorgiMemoDatabase.getDatabase(context)
            val preferences = CorgiPreferences.getInstance(context)
            
            val backupData = collectBackupData(database, preferences)
            val appVersion = getAppVersion(context)
            val exportTime = DateTimeFormatter.ISO_INSTANT.format(Instant.now())

            val content = when (format) {
                ExportFormat.JSON -> {
                    val container = BackupContainer(
                        meta = BackupMeta(
                            version = BackupMeta.CURRENT_VERSION,
                            exportTime = exportTime,
                            appVersion = appVersion,
                            encrypted = password != null
                        ),
                        data = backupData
                    )
                    JsonSerializer.serializeToBytes(container)
                }
                ExportFormat.CSV -> {
                    CsvSerializer.serialize(
                        todos = backupData.todos,
                        categories = backupData.categories
                    ).toByteArray(StandardCharsets.UTF_8)
                }
                ExportFormat.ICAL -> {
                    IcsExporter.exportTodos(
                        todos = backupData.todos.map { it.toModel() },
                        categories = backupData.categories.map { it.toModel() }
                    ).toByteArray(StandardCharsets.UTF_8)
                }
                ExportFormat.IMAGE -> {
                    throw IllegalArgumentException("图片导出需要单独处理")
                }
            }

            val finalContent = if (format == ExportFormat.JSON && password != null) {
                EncryptionManager.encrypt(content, password)
            } else {
                content
            }

            var success = false
            context.contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
                outputStream.write(finalContent)
                outputStream.flush()
                success = true
            }

            if (!success) {
                return@withContext ExportResult.Error("无法写入文件")
            }

            ExportResult.Success(uri)
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult.Error(e.message ?: "导出失败")
        }
    }

    /**
     * 恢复数据
     *
     * @param context 上下文
     * @param uri 备份文件 URI
     * @param password 密码（可选）
     * @return 恢复结果
     */
    suspend fun restoreData(
        context: Context,
        uri: Uri,
        password: String? = null
    ): RestoreResult = withContext(Dispatchers.IO) {
        try {
            val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: return@withContext RestoreResult.Error("无法读取文件")

            val container = try {
                val jsonBytes = if (password != null) {
                    try {
                        EncryptionManager.decrypt(content, password)
                    } catch (e: Exception) {
                        return@withContext RestoreResult.WrongPassword
                    }
                } else {
                    content
                }
                JsonSerializer.deserializeFromBytes(jsonBytes)
            } catch (e: Exception) {
                return@withContext RestoreResult.Error("文件格式错误或已损坏")
            }

            if (container.meta.version > BackupMeta.CURRENT_VERSION) {
                return@withContext RestoreResult.VersionIncompatible
            }

            val database = CorgiMemoDatabase.getDatabase(context)

            restoreBackupData(database, container.data)

            RestoreResult.Success(
                todoCount = container.data.todos.size,
                categoryCount = container.data.categories.size
            )
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult.Error(e.message ?: "恢复失败")
        }
    }

    /**
     * 收集备份数据
     */
    private suspend fun collectBackupData(
        database: CorgiMemoDatabase,
        preferences: CorgiPreferences
    ): BackupData {
        val todoDao = database.todoDao()
        val categoryDao = database.categoryDao()
        val corgiDao = database.corgiDao()
        val moodHistoryDao = database.moodHistoryDao()

        val todos = todoDao.getAllTodos().first().map { it.toBackupModel() }
        val categories = categoryDao.getAllCategoriesList().map { it.toBackupModel() }
        val corgiData = corgiDao.getCorgiData()?.toBackupModel()
        val moodHistory = moodHistoryDao.getAll().map { it.toBackupModel() }

        val prefsMap = mutableMapOf<String, String>()
        preferences.corgiName.first()?.let { prefsMap["corgi_name"] = it }
        preferences.soundEnabled.first().let { prefsMap["sound_enabled"] = it.toString() }
        preferences.hapticEnabled.first().let { prefsMap["haptic_enabled"] = it.toString() }
        preferences.userType.first()?.let { prefsMap["user_type"] = it }

        return BackupData(
            todos = todos,
            categories = categories,
            corgiData = corgiData,
            moodHistory = moodHistory,
            preferences = prefsMap
        )
    }

    /**
     * 恢复备份数据
     */
    private suspend fun restoreBackupData(
        database: CorgiMemoDatabase,
        backupData: BackupData
    ) {
        val todoDao = database.todoDao()
        val categoryDao = database.categoryDao()
        val corgiDao = database.corgiDao()
        val moodHistoryDao = database.moodHistoryDao()

        todoDao.deleteAll()
        categoryDao.deleteAll()
        moodHistoryDao.clearAll()

        backupData.categories.forEach { category ->
            categoryDao.insert(category.toModel())
        }

        backupData.todos.forEach { todo ->
            todoDao.insert(todo.toModel())
        }

        backupData.corgiData?.let { corgi ->
            corgiDao.insert(corgi.toModel())
        }

        backupData.moodHistory.forEach { mood ->
            moodHistoryDao.insertOrReplace(mood.toModel())
        }
    }

    /**
     * 获取应用版本号
     */
    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }
}

private fun TodoItem.toBackupModel(): BackupTodoItem = BackupTodoItem(
    id = id,
    title = title,
    content = content,
    categoryId = categoryId,
    priority = priority,
    status = status,
    dueDate = dueDate,
    reminderTime = reminderTime,
    repeatType = repeatType,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    geofenceLat = geofenceLat,
    geofenceLng = geofenceLng,
    geofenceRadius = geofenceRadius,
    geofenceType = geofenceType,
    geofenceEnabled = geofenceEnabled,
    geofenceAddress = geofenceAddress
)

private fun BackupTodoItem.toModel(): TodoItem = TodoItem(
    id = id,
    title = title,
    content = content,
    categoryId = categoryId,
    priority = priority,
    status = status,
    dueDate = dueDate,
    reminderTime = reminderTime,
    repeatType = repeatType,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    geofenceLat = geofenceLat,
    geofenceLng = geofenceLng,
    geofenceRadius = geofenceRadius,
    geofenceType = geofenceType,
    geofenceEnabled = geofenceEnabled,
    geofenceAddress = geofenceAddress
)

private fun Category.toBackupModel(): BackupCategory = BackupCategory(
    id = id,
    name = name,
    type = type,
    isDefault = isDefault
)

private fun BackupCategory.toModel(): Category = Category(
    id = id,
    name = name,
    type = type,
    isDefault = isDefault
)

private fun CorgiData.toBackupModel(): BackupCorgiData = BackupCorgiData(
    id = id,
    name = name,
    level = level,
    experience = experience,
    currentOutfit = currentOutfit,
    unlockedOutfits = unlockedOutfits,
    unlockedAchievements = unlockedAchievements,
    moodValue = moodValue,
    lastActiveDate = lastActiveDate,
    totalCompleted = totalCompleted,
    consecutiveDays = consecutiveDays,
    maxConsecutiveDays = maxConsecutiveDays
)

private fun BackupCorgiData.toModel(): CorgiData = CorgiData(
    id = id,
    name = name,
    level = level,
    experience = experience,
    currentOutfit = currentOutfit,
    unlockedOutfits = unlockedOutfits,
    unlockedAchievements = unlockedAchievements,
    moodValue = moodValue,
    lastActiveDate = lastActiveDate,
    totalCompleted = totalCompleted,
    consecutiveDays = consecutiveDays,
    maxConsecutiveDays = maxConsecutiveDays
)

private fun MoodHistory.toBackupModel(): BackupMoodHistory = BackupMoodHistory(
    id = id,
    date = date,
    moodValue = moodValue,
    changeReason = changeReason
)

private fun BackupMoodHistory.toModel(): MoodHistory = MoodHistory(
    id = id,
    date = date,
    moodValue = moodValue,
    changeReason = changeReason
)
