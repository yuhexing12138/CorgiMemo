# iCal (.ics) 和图片分享导出功能实现计划

## 项目调研结论

### 现有代码结构

**BackupManager**（[BackupManager.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/backup/BackupManager.kt)）
- 已有 `ExportFormat` 枚举：`JSON`、`CSV`
- 需要新增 `ICAL`、`IMAGE` 格式

**BackupFileHandler**（[BackupFileHandler.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/backup/BackupFileHandler.kt)）
- 已有文件选择 Intent 构建
- 新增 iCal MIME 类型

**SettingsScreen**（[SettingsScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt)）
- 已有导出格式选择对话框
- 需要新增 iCal 和图片选项

**AndroidManifest**
- 尚无 FileProvider 配置，图片分享需要

### TodoItem 数据模型

```kotlin
data class TodoItem(
    val id: Long,
    val title: String,
    val content: String?,
    val categoryId: Long,
    val priority: Int,      // 2=高, 1=中, 0=低
    val status: Int,
    val dueDate: Long?,     // 截止日期（时间戳）
    val reminderTime: Long?, // 提醒时间（时间戳）
    val repeatType: Int,    // 0=不重复, 1=每天, 2=每周, 3=每月
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?
)
```

---

## 需要修改/新增的文件

### 1. 依赖配置
- **修改**：`libs.versions.toml` - 添加 iCal4j 依赖版本
- **修改**：`app/build.gradle.kts` - 添加 iCal4j 依赖

### 2. iCal 导出器
- **新增**：`IcsExporter.kt` - 使用 iCal4j 生成 .ics 文件

### 3. 图片分享
- **新增**：`ImageExporter.kt` - Compose 截图导出
- **新增**：`ShareIntentHelper.kt` - 分享 Intent 构建
- **新增**：`ShareCardComponent.kt` - 分享卡片 Composable

### 4. 数据层集成
- **修改**：`BackupManager.kt` - 新增 `ICAL`、`IMAGE` 格式处理
- **修改**：`BackupFileHandler.kt` - 新增 iCal 文件名和 Intent

### 5. UI 层
- **修改**：`SettingsScreen.kt` - 导出格式对话框新增选项

### 6. Manifest 配置
- **修改**：`AndroidManifest.xml` - 添加 FileProvider

---

## 实现步骤

### 步骤 1：添加 iCal4j 依赖

**文件**：`libs.versions.toml`

**新增版本**：
```kotlin
ical4j = "4.0.0"
```

**新增库引用**：
```kotlin
ical4j = { module = "org.mnode.ical4j:ical4j", version.ref = "ical4j" }
```

**文件**：`app/build.gradle.kts`

**新增依赖**：
```kotlin
implementation(libs.ical4j)
```

---

### 步骤 2：创建 IcsExporter

**文件**：`app/src/main/java/com/corgimemo/app/backup/exporter/IcsExporter.kt`

**功能**：
- 将 `TodoItem` 列表转换为 iCalendar 格式
- 使用 iCal4j 库构建标准 .ics 文件

**核心方法**：

| 方法 | 说明 |
|------|------|
| `exportTodos(todos, categories)` | 导出待办列表为 .ics 内容 |
| `todoToVEvent(todo, categoryName)` | 单个待办转为 VEVENT |
| `mapPriority(priority)` | 优先级映射（2→1, 1→5, 0→9） |
| `getEventTime(todo)` | 获取事件时间（优先 reminderTime） |
| `mapRepeatType(repeatType)` | 重复类型映射为 RRULE |

**iCal 字段映射**：

| TodoItem | iCal 字段 | 说明 |
|----------|-----------|------|
| `id` | `UID` | `${todo.id}@corgimemo` |
| `title` | `SUMMARY` | 事件标题 | |
| `content` | `DESCRIPTION` | 事件描述 | | | |
| `priority` | `PRIORITY` | 2→1, 1→5, 0→9 | | | |
| `categoryId` → 名称 | `CATEGORIES` | 分类标签 | | | |
| `reminderTime`/`dueDate` | `DTSTART` | 事件开始时间 | | | | |
| `DTSTART + 30min` | `DTEND` | 事件结束时间 | | | |
| `repeatType` | `RRULE` | 重复规则 | | | | |

---

### 步骤 3：创建图片分享组件

**文件**：`app/src/main/java/com/corgimemo/app/backup/share/ShareCardComponent.kt`

**功能**：
- 单条待办分享卡片 Composable
- 多条待办拼接长图

**卡片样式**：
- 尺寸：360dp x 480dp（单条）
- 背景：渐变（米白 → 浅橙）
- 顶部：🐕 CorgiMemo 品牌栏
- 中间：优先级标识 + 标题 + 内容 + 时间 + 分类 | | | |
| 底部：柯基鼓励语 | | |

**内容元素**：
- 优先级颜色条
- 标题（24sp，加粗）
- 内容（16sp，最多两行） | | | |
| 截止时间 | | |
| 分类名称 | | |
| 柯基鼓励语（根据时间/心情动态生成） | | |

---

### 步骤 4：创建 ImageExporter

**文件**：`app/src/main/java/com/corgimemo/app/backup/exporter/ImageExporter.kt`

**功能**：
- 将 Composable 渲染为 Bitmap | | | |
| 保存到缓存目录 | | |
| 构建分享 Intent | | |

**核心方法**：

| 方法 | 说明 | | | |
|------|------|------|------|------|
| `captureComposableToBitmap()` | 将 Composable 截图为 Bitmap | | | |
| `saveBitmapToCache()` | 保存 Bitmap 到缓存目录 | | | |
| `getShareIntent()` | 构建分享 Intent | | | |
| `exportSingleTodoImage()` | 单条待办图片导出 | | | |
| `exportMultipleTodosImage()` | 多条待办拼接长图 | | | |

**Compose 截图 API**（Android 13+）：
```kotlin
val bitmap = composable.drawToBitmap()
```

**缓存路径**：
- 目录：`/cache/share_images/`
- 文件名：`corgimemo_share_{timestamp}.png`

---

### 步骤 5：创建 ShareIntentHelper

**文件**：`app/src/main/java/com/corgimemo/app/backup/share/ShareIntentHelper.kt`

**功能**：
- 构建图片分享 Intent | | | |
| 构建 iCal 文件分享 Intent | | |

**分享目标**：
- 微信 | | |
| 朋友圈 | | |
| 微博 | | |
| 系统日历（iCal） | | |

**核心方法**：

| 方法 | 说明 | | | |
|------|------|------|------|------|
| `getImageShareIntent(uri)` | 图片分享 Intent | | | |
| `getIcalShareIntent(uri)` | iCal 文件分享 Intent | | | |
| `getChooserIntent(intent)` | 系统选择器 Intent | | | |

---

### 步骤 6：配置 FileProvider

**文件**：`app/src/main/AndroidManifest.xml`

**新增内容**：
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

**新增文件**：`app/src/main/res/xml/file_paths.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="share_images" path="share_images/" />
</paths>
```

---

### 步骤 7：集成到 BackupManager

**文件**：`BackupManager.kt`

**新增 ExportFormat**：
```kotlin
enum class ExportFormat {
    JSON,
    CSV,
    ICAL,    // 新增
    IMAGE    // 新增
}
```

**新增导出方法**：
```kotlin
suspend fun exportToIcal(context: Context, uri: Uri): ExportResult
suspend fun exportToImage(context: Context, todos: List<TodoItem>): ExportResult
```

---

### 步骤 8：更新 BackupFileHandler

**文件**：`BackupFileHandler.kt`

**新增 iCal 处理**：
```kotlin
fun generateExportFileName(format: ExportFormat): String {
    when (format) {
        ExportFormat.ICAL -> "CorgiMemo_待办_$timestamp.ics"
        ExportFormat.IMAGE -> "CorgiMemo_分享_$timestamp.png"
        ...
    }
}
```

**新增 MIME 类型**：
- `text/calendar` - iCal 文件 | |
| `image/png` - 图片文件 | |

---

### 步骤 9：更新 SettingsScreen 导出对话框

**文件**：`SettingsScreen.kt`

**修改 ExportFormatDialog**：
- 新增 iCal 选项：📅 iCal（导入日历） | |
| 新增图片选项：🖼️ 图片（分享） | |

**新增图片导出流程**：
1. 选择图片格式 → 弹出待办选择器（单选/多选） | | | |
| 2. 选择待办 → 预览卡片 | | | |
| 3. 点击分享 → 弹出系统分享面板 | | | |

---

### 步骤 10：测试用例

| 测试项 | 期望结果 | | | |
|--------|---------|------|------|------|
| 导出 iCal 文件 | 生成 .ics 文件，可用日历应用打开 | | | |
| iCal 字段正确性 | 标题、时间、分类正确映射 | | | |
| 单条待办图片 | 卡片样式正确，包含所有元素 | | | |
| 多条待办图片 | 垂直拼接，间距正确 | | | |
| 图片分享 | 可分享到微信/微博等 | | | |
| iCal 分享 | 可导入系统日历 | | | |
| 无时间待办 | 使用默认时间（当天 18:00） | | | |
| 重复任务 | RRULE 正确生成 | | | |

---

## 潜在依赖和考虑

### 风险点

1. **iCal4j 版本兼容性**
   - iCal4j 4.x 基于 Java 8+ | |
   - 检查 minSdk 要求 | |

2. **Compose 截图 API 版本要求**
   - `drawToBitmap()` 需要 Android 13（API 33）+ | |
   - 低版本需要降级方案（PixelCopy 或 View 截图） | |

3. **FileProvider 配置**
   - authorities 必须唯一（使用 `${applicationId}`） | |
   - 路径配置正确 | |

4. **大图性能问题**
   - 多条待办拼接长图可能导致内存问题 | |
   - 限制最大待办数量（如 20 条） | |

### 降级方案

| 场景 | 降级方案 |
|------|---------|
| Android 13 以下 | 使用 `PixelCopy` 或传统 View 截图 |
| iCal4j 冲突 | 降级到手动构建 .ics（方案 A） |
| 大图 OOM | 限制待办数量或分多图 |

### 可选优化

| 优化项 | 优先级 |
|--------|--------|
| 图片导出质量选择 | 低 |
| 分享卡片主题切换 | 低 |
| 导出进度显示 | 中 |
| 批量分享（多选待办） | 中 |

---

## 实施顺序

1. ✅ 步骤 1：添加 iCal4j 依赖
2. ✅ 步骤 2：创建 IcsExporter
3. ✅ 步骤 3：创建图片分享组件
4. ✅ 步骤 4：创建 ImageExporter
5. ✅ 步骤 5：创建 ShareIntentHelper
6. ✅ 步骤 6：配置 FileProvider
7. ✅ 步骤 7：集成到 BackupManager
8. ✅ 步骤 8：更新 BackupFileHandler
9. ✅ 步骤 9：更新 SettingsScreen
10. ✅ 步骤 10：测试验证
