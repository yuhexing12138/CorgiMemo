# 自动归档已完成待办功能实现计划

## 当前状态分析

### 已有功能
| 功能 | 状态 | 文件 |
|------|------|------|
| TodoItem 模型（含 completedAt 字段） | ✅ 已有 | [TodoItem.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/TodoItem.kt) |
| 完成状态切换（设置 completedAt） | ✅ 已有 | [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L580-L587) |
| 过滤器（全部/待办/已完成） | ✅ 已有 | [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt#L798-L800) |
| 过滤器 UI 按钮 | ✅ 已有 | [HomeScreen.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt#L210-L240) |

### 缺失功能
| 功能 | 状态 | 说明 |
|------|------|------|
| 30 天自动清理已完成待办 | ❌ 缺失 | 需要 WorkManager 定时任务 |
| 已完成列表显示 30 天内记录 | ⚠️ 部分缺失 | 当前显示全部完成，未按 30 天过滤 |
| 已完成待办恢复功能 | ❌ 缺失 | 复选框已支持，但需要优化 UX |
| 已完成待办永久删除 | ⚠️ 部分缺失 | 左滑删除已存在 |
| WorkManager 依赖 | ❌ 缺失 | 需要添加依赖 |

---

## 实现目标

1. **已完成待办自动归档**：标记为完成后，自动移动到"已完成"列表，而非直接删除
2. **30 天自动清理**：已完成超过 30 天的待办自动删除
3. **归档列表 UI**：显示最近 30 天的已完成待办，支持恢复或永久删除

---

## 实现步骤

### 步骤 1：添加 WorkManager 依赖

**修改文件：**
- [libs.versions.toml](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/gradle/libs.versions.toml) - 添加 work-runtime 版本和库定义
- [app/build.gradle.kts](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/build.gradle.kts) - 添加 work-runtime 依赖

**实现内容：**
```toml
# libs.versions.toml
[versions]
workmanager = "2.9.0"

[libraries]
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "workmanager" }
```

```kotlin
// build.gradle.kts
implementation(libs.androidx.work.runtime.ktx)
```

---

### 步骤 2：创建归档清理 Worker

**新建文件：**
- `app/src/main/java/com/corgimemo/app/worker/AutoArchiveCleanupWorker.kt`

**实现内容：**
```kotlin
class AutoArchiveCleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    
    /**
     * 清理超过 30 天的已完成待办
     */
    override suspend fun doWork(): Result {
        val threshold = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
        // TODO: 调用 Repository 删除 completedAt < threshold 的待办
        return Result.success()
    }
}
```

---

### 步骤 3：修改 Repository 添加清理方法

**修改文件：**
- [TodoRepository.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/data/repository/TodoRepository.kt) - 新增清理方法

**实现内容：**
```kotlin
suspend fun cleanupOldCompletedTodos(threshold: Long): Int {
    // 删除 completedAt < threshold 的已完成待办
}
```

---

### 步骤 4：修改 TodoDao 添加清理查询

**修改文件：**
- `TodoDao.kt` - 新增清理查询

**实现内容：**
```kotlin
@Query("DELETE FROM todo_items WHERE status = 1 AND completedAt < :threshold AND completedAt IS NOT NULL")
suspend fun deleteOldCompletedTodos(threshold: Long): Int
```

---

### 步骤 5：修改 HomeViewModel 过滤已完成列表（30 天内）

**修改文件：**
- [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) - 修改已完成过滤逻辑

**当前逻辑（需要修改）：**
```kotlin
FilterStatus.COMPLETED -> allTodos.filter { it.status == 1 }
```

**修改后：**
```kotlin
FilterStatus.COMPLETED -> {
    val thirtyDaysAgo = System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L
    allTodos.filter { 
        it.status == 1 && 
        it.completedAt != null && 
        it.completedAt >= thirtyDaysAgo 
    }
}
```

---

### 步骤 6：创建 WorkManager 调度管理器

**新建文件：**
- `app/src/main/java/com/corgimemo/app/worker/ArchiveCleanupScheduler.kt`

**实现内容：**
```kotlin
object ArchiveCleanupScheduler {
    
    /**
     * 调度每日凌晨的清理任务
     */
    fun scheduleDailyCleanup(context: Context) {
        // 使用 PeriodicWorkRequest，每天执行一次
    }
    
    /**
     * 应用启动时调度
     */
    fun scheduleIfNeeded(context: Context) {
        // 检查任务是否已调度，未调度则调度
    }
}
```

---

### 步骤 7：在 Application 中初始化调度

**修改文件：**
- `CorgiMemoApplication.kt` - 在 onCreate 中调用调度

**实现内容：**
```kotlin
override fun onCreate() {
    super.onCreate()
    // ... 其他初始化
    ArchiveCleanupScheduler.scheduleIfNeeded(this)
}
```

---

### 步骤 8：优化已完成待办 UI（显示完成时间和操作按钮）

**修改文件：**
- [TodoListItem.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt) - 已完成待办显示完成时间

**实现内容：**
- 已完成待办显示完成时间（如 "3 分钟前完成"）
- 左滑删除保持不变（永久删除）
- 复选框点击恢复待办（取消完成状态）

---

## 待修改文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| [libs.versions.toml](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/gradle/libs.versions.toml) | 修改 | 添加 WorkManager 版本和库定义 |
| [app/build.gradle.kts](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/build.gradle.kts) | 修改 | 添加 WorkManager 依赖 |
| TodoDao.kt | 修改 | 添加 30 天清理查询 |
| [TodoRepository.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/data/repository/TodoRepository.kt) | 修改 | 添加清理方法 |
| [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/AndroidTest/corgimemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) | 修改 | 过滤 30 天内已完成待办 |
| [TodoListItem.kt](file:///c:/Users/EDY/Desktop/AndroidTest/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components/TodoListItem.kt) | 修改 | 显示完成时间 |
| AutoArchiveCleanupWorker.kt | 新建 | WorkManager Worker 类 |
| ArchiveCleanupScheduler.kt | 新建 | 调度管理器 |
| CorgiMemoApplication.kt | 修改 | 初始化调度 |

---

## 风险与注意事项

| 风险 | 应对措施 |
|------|----------|
| WorkManager 版本兼容性 | 使用与当前 Compose/AndroidX 兼容的版本 |
| 30 天清理的数据丢失 | 用户可在 30 天内恢复，超过 30 天才永久删除 |
| 定时任务未执行 | 应用启动时也执行一次清理，确保不遗漏 |

---

## 测试方案

### 单元测试
1. **Worker 测试**：模拟 31 天前的已完成待办，验证是否被清理
2. **过滤逻辑测试**：验证 30 天边界条件

### 手动测试
1. 标记待办为完成，检查是否显示在"已完成"列表
2. 等待（或手动触发）30 天后，检查是否自动清理
3. 在已完成列表点击复选框，验证能否恢复待办
4. 在已完成列表左滑删除，验证能否永久删除
