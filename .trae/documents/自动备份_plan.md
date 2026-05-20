# 自动备份功能实现计划

## 功能概述

实现自动备份策略，包括：自动备份配置、自动备份调度、备份历史管理、备份通知。

---

## 一、数据模型和配置存储

### 1.1 新增文件

#### BackupRecord.kt
- 位置：`app/src/main/java/com/corgimemo/app/backup/BackupRecord.kt`
- 内容：备份记录数据类
- 关键字段：
  - `id: String` - 唯一 ID（UUID）
  - `timestamp: Long` - 备份时间戳
  - `fileSizeBytes: Long` - 文件大小（字节）
  - `locationType: String` - 位置类型（local）
  - `fileUri: String` - 文件 URI
  - `todoCount: Int` - 备份的待办数量
  - `categoryCount: Int` - 备份的分类数量
  - `isAutoBackup: Boolean` - 是否自动备份

#### BackupFrequency.kt
- 位置：`app/src/main/java/com/corgimemo/app/backup/BackupFrequency.kt`
- 内容：枚举类
  - `WEEKLY("每周")`
  - `MONTHLY("每月")`

### 1.2 修改文件

#### CorgiPreferences.kt
- 新增 DataStore Key：
  - `auto_backup_enabled: Boolean` - 自动备份开关
  - `auto_backup_frequency: String` - 频率
  - `auto_backup_location_uri: String?` - 备份位置 URI
  - `auto_backup_retain_count: Int` - 保留版本数
  - `auto_backup_last_time: Long` - 上次备份时间
  - `backup_history: String` - 备份历史 JSON 列表

---

## 二、自动备份调度

### 2.1 新增文件

#### BackupWorker.kt
- 位置：`app/src/main/java/com/corgimemo/app/backup/BackupWorker.kt`
- 继承：`CoroutineWorker`
- 执行流程：
  1. 检查自动备份是否启用
  2. 读取备份位置和保留数
  3. 导出 JSON 备份
  4. 保存到 SAF 目录
  5. 记录到备份历史
  6. 清理旧备份
  7. 发送通知

#### BackupScheduler.kt
- 位置：`app/src/main/java/com/corgimemo/app/backup/BackupScheduler.kt`
- 功能：
  - `schedule(context, frequency)` - 调度自动备份
  - `cancelAll(context)` - 取消所有调度
  - `triggerNow(context)` - 立即执行备份

#### BackupHistoryManager.kt
- 位置：`app/src/main/java/com/corgimemo/app/backup/BackupHistoryManager.kt`
- 功能：
  - `getRecords()` - 获取所有记录
  - `addRecord()` - 添加记录
  - `deleteRecord()` - 删除记录（含文件）
  - `cleanupOldBackups()` - 清理旧备份

---

## 三、备份历史页面

### 3.1 新增文件

#### BackupHistoryScreen.kt
- 位置：`app/src/main/java/com/corgimemo/app/ui/screens/backup/BackupHistoryScreen.kt`
- 内容：备份历史列表页面
- 功能：
  - 显示最近备份记录
  - 恢复、删除、分享操作

#### BackupRecordCard.kt
- 位置：`app/src/main/java/com/corgimemo/app/ui/components/BackupRecordCard.kt`
- 内容：备份记录卡片组件
- 显示内容：
  - 备份时间
  - 存储位置和大小
  - 待办/分类数量统计
  - 操作按钮

---

## 四、设置页面集成

### 4.1 修改文件

#### SettingsScreen.kt
- 新增"自动备份"区域：
  - 开关：启用/禁用自动备份
  - 频率选择：每周/每月
  - 备份位置选择（SAF）
  - 保留版本数选择
  - 立即备份按钮
- 新增"备份历史"入口

---

## 五、备份通知

### 5.1 修改文件

#### NotificationHelper.kt
- 新增通知渠道 `BACKUP_STATUS`
- 新增方法：
  - `showBackupNotification(context, success, todoCount, errorMessage)`

---

## 六、路由配置

### 6.1 修改文件

#### MainActivity.kt 或相关导航文件
- 新增路由：`backup_history`

---

## 七、文件清单

### 新增文件（7个）
1. `app/src/main/java/com/corgimemo/app/backup/BackupRecord.kt`
2. `app/src/main/java/com/corgimemo/app/backup/BackupFrequency.kt`
3. `app/src/main/java/com/corgimemo/app/backup/BackupWorker.kt`
4. `app/src/main/java/com/corgimemo/app/backup/BackupScheduler.kt`
5. `app/src/main/java/com/corgimemo/app/backup/BackupHistoryManager.kt`
6. `app/src/main/java/com/corgimemo/app/ui/screens/backup/BackupHistoryScreen.kt`
7. `app/src/main/java/com/corgimemo/app/ui/components/BackupRecordCard.kt`

### 修改文件（4个）
1. `app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt`
2. `app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt`
3. `app/src/main/java/com/corgimemo/app/notification/NotificationHelper.kt`
4. 导航相关文件

---

## 八、实现顺序

1. **第一阶段：数据模型**
   - 创建 BackupRecord、BackupFrequency
   - 修改 CorgiPreferences

2. **第二阶段：备份调度**
   - 创建 BackupWorker
   - 创建 BackupScheduler
   - 创建 BackupHistoryManager

3. **第三阶段：备份通知**
   - 修改 NotificationHelper

4. **第四阶段：UI 开发**
   - 创建 BackupHistoryScreen
   - 创建 BackupRecordCard
   - 修改 SettingsScreen

5. **第五阶段：集成测试**
   - 验证自动备份触发
   - 验证备份历史记录
   - 验证旧备份清理
