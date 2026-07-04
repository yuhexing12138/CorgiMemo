# CorgiMemo 项目 Code Wiki

> 一份面向开发者与维护者的结构化代码文档，覆盖项目整体架构、模块职责、关键类与函数、依赖关系与运行方式。

---

## 目录

1. [项目概述](#1-项目概述)
2. [整体架构](#2-整体架构)
3. [技术栈与依赖](#3-技术栈与依赖)
4. [项目目录结构](#4-项目目录结构)
5. [构建配置与运行方式](#5-构建配置与运行方式)
6. [应用入口与生命周期](#6-应用入口与生命周期)
7. [数据层](#7-数据层)
8. [UI 层与导航](#8-ui-层与导航)
9. [ViewModel 层](#9-viewmodel-层)
10. [柯基动画子系统](#10-柯基动画子系统)
11. [通知 / 接收器 / 服务 / Widget / Worker](#11-通知--接收器--服务--widget--worker)
12. [备份子系统](#12-备份子系统)
13. [工具与领域逻辑](#13-工具与领域逻辑)
14. [数据库迁移历史](#14-数据库迁移历史)
15. [关键数据流与设计要点](#15-关键数据流与设计要点)
16. [后续可优化方向](#16-后续可优化方向)

---

## 1. 项目概述

**CorgiMemo** 是一款基于 **Jetpack Compose** 的纯 Compose Android 待办管理应用，核心特色是围绕一只"柯基宠物"构建的陪伴式体验：用户完成待办时柯基会升级、解锁装扮、播放庆祝动画；未完成时柯基会担忧、低头；夜间会自动入睡；节日会换装、节气会科普。

### 1.1 核心能力一览

| 能力域 | 说明 |
|--------|------|
| 待办管理 | 多分组卡片编辑、行级图片/语音附件、跨行拖拽、富文本、撤销/重做 |
| 柯基陪伴 | 等级 / 情绪 / 姿态 / 装扮 / 节日 / 节气 / 问候语 / 自主行为 |
| 成就系统 | 26 个成就（通用 + 上班族 + 学生），4 阶段，解锁装扮 |
| 提醒 | AlarmManager 精确闹钟 + 通知聚合 + 5 种操作按钮 |
| 桌面小部件 | Glance 实现的 3 种尺寸小部件（1×1 / 2×2 / 4×2） |
| 备份 | JSON / CSV / iCal / 图片分享，AES-256-GCM 加密，自动周期备份 |
| 数据安全 | EncryptedSharedPreferences 加密偏好 + 软删除 + 操作日志 + 二次确认 |
| 智能预加载 | 基于用户行为分析的应用级数据预热 |

### 1.2 应用基本信息

| 属性 | 值 |
|------|----|
| applicationId | `com.corgimemo.app` |
| versionName | 1.0 |
| minSdk | 26 (Android 8.0) |
| targetSdk / compileSdk | 35 |
| JDK | 17 |
| 启动 Activity | [MainActivity.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/MainActivity.kt) |
| Application | [CorgiMemoApplication.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/CorgiMemoApplication.kt) |

---

## 2. 整体架构

CorgiMemo 采用 **MVVM + Hilt + StateFlow 单向数据流** 的现代 Android 架构，**未使用 LiveData**，所有 UI 状态由 `StateFlow` / `SharedFlow` 暴露，通过 `collectAsState()` 在 Compose 中订阅。

### 2.1 分层架构图

```
┌──────────────────────────────────────────────────────────────┐
│  UI 层 (Compose)                                              │
│  screens / components / theme / navigation                    │
│      ↓ collectAsState()            ↑ 调用公开方法              │
├──────────────────────────────────────────────────────────────┤
│  ViewModel 层 (StateFlow / SharedFlow)                        │
│  HomeViewModel / TodoEditViewModel / ...                      │
│      ↓ Repository 调用             ↑ 事件订阅(TodoEventBus)   │
├──────────────────────────────────────────────────────────────┤
│  数据层                                                        │
│  repository / local(db+datastore) / cache / event / paging    │
│      ↓ DAO / DataStore            ↑ Flow 回流                │
├──────────────────────────────────────────────────────────────┤
│  持久层                                                        │
│  Room Database (v31, 16 Entity)                               │
│  EncryptedSharedPreferences (AES-256-GCM)                    │
│  CacheManager (L1 LRU + L2 ESP)                              │
└──────────────────────────────────────────────────────────────┘

旁路系统：
  • animation/        柯基动画与情绪
  • notification/     AlarmScheduler + NotificationHelper
  • receiver/         BootReceiver / ReminderActionReceiver / AutoBackupReceiver
  • service/          VoicePlaybackService (前台服务)
  • widget/           Glance 桌面小部件
  • worker/           WorkManager 周期任务
  • backup/           备份/导出/加密
  • analytics/        本地用户行为分析
```

### 2.2 单向数据流

```
用户操作 ─▶ ViewModel 公开方法 ─▶ MutableStateFlow.value 修改
                                     │
                                     ▼
                              StateFlow 推送
                                     │
                                     ▼
                          Compose collectAsState() ─▶ UI 重组
```

跨 ViewModel 通信通过 [TodoEventBus](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/event/TodoEventBus.kt)（`SharedFlow<TodoEvent>`）实现，解决编辑页与首页因 NavBackStackEntry 隔离导致的数据不同步问题。

---

## 3. 技术栈与依赖

### 3.1 构建工具版本

| 工具 | 版本 | 备注 |
|------|------|------|
| Gradle | 9.4.1 | wrapper 自带 |
| AGP | 9.2.1 | 内置 Kotlin 模式，移除 `kotlin-android` 插件 |
| Kotlin | 2.3.20 | |
| KSP | 2.3.9 | |
| Hilt | 2.59.2 | 修复 2.57.2 在 AGP 9.0 下的兼容问题 |
| Compose BOM | 2026.04.01 | 统一管理 Compose 全家桶版本 |
| Compose Compiler | 由 `kotlin-compose` 插件管理 | |
| JDK | 17 | `jvmToolchain(17)` |

### 3.2 核心依赖清单

| 类别 | 库 | 版本 |
|------|----|------|
| Compose | androidx.compose:compose-bom | 2026.04.01 |
| Material3 | androidx.compose.material3:material3 | 由 BOM |
| Activity | androidx.activity:activity-compose | 1.8.2 |
| Lifecycle | androidx.lifecycle:lifecycle-* | 2.8.7 |
| Navigation | androidx.navigation:navigation-compose | 2.8.5 |
| Hilt | com.google.dagger:hilt-android | 2.59.2 |
| Hilt Nav Compose | androidx.hilt:hilt-navigation-compose | 1.0.0 |
| Room | androidx.room:room-* | 2.8.4 |
| Paging | androidx.paging:paging-* | 3.2.1 |
| DataStore | androidx.datastore:datastore-preferences | 1.0.0 |
| 加密偏好 | androidx.security:security-crypto-ktx | 1.1.0-alpha06 |
| WorkManager | androidx.work:work-runtime-ktx | 2.9.0 |
| Glance | androidx.glance:glance-* | 1.0.0 |
| Coil | io.coil-kt:coil-compose | 2.5.0 |
| Accompanist | com.google.accompanist:accompanist-permissions | 0.34.0 |
| Coroutines | org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.7.3 |
| Serialization | org.jetbrains.kotlinx:kotlinx-serialization-json | 1.6.2 |
| 拖拽排序 | sh.calvin.reorderable:reorderable | 3.1.0 |
| 农历 | cn.6tail:tyme4kt | 1.4.5 |

完整版本目录见 [gradle/libs.versions.toml](file:///c:/Users/EDY/Desktop/CorgiMemo/gradle/libs.versions.toml)。

### 3.3 Gradle 插件

```kotlin
plugins {
    alias(libs.plugins.android.application)         // AGP 9.2.1
    alias(libs.plugins.kotlin.serialization)         // kotlinx.serialization
    alias(libs.plugins.kotlin.compose)               // Compose Compiler (Kotlin 2.0+)
    id("com.google.devtools.ksp")                    // KSP 2.3.9
    id("dagger.hilt.android.plugin")                 // Hilt 2.59.2
}
```

---

## 4. 项目目录结构

```
CorgiMemo/
├── build.gradle.kts              # 根脚本（KSP/Hilt classpath）
├── settings.gradle.kts           # 阿里云镜像 + 仅 :app 模块
├── corgimemo-key.jks             # release 签名密钥
├── gradle/libs.versions.toml     # 版本目录
├── app/
│   ├── build.gradle.kts          # Compose + Hilt + Room + KSP 配置
│   ├── proguard-rules.pro        # Dagger 保留规则
│   └── src/main/
│       ├── AndroidManifest.xml   # 12 项权限 + 1 服务 + 8 接收器 + 1 Provider
│       ├── res/
│       │   ├── drawable/          # 32 张柯基动画帧 + 5 个矢量图标
│       │   ├── values/            # colors / colors_ui / strings / styles / dimens
│       │   ├── values-en/         # 英文字符串
│       │   ├── values-night/      # 夜间模式色板
│       │   └── xml/               # backup_rules / file_paths / widget info
│       └── java/com/corgimemo/app/
│           ├── CorgiMemoApplication.kt   # @HiltAndroidApp 入口
│           ├── analytics/        # 用户行为分析（本地）
│           ├── animation/        # 柯基动画系统（22 文件）
│           ├── backup/           # 备份系统（含 crypto/data/exporter/serializer/worker）
│           ├── data/             # 数据层（cache/event/local/model/paging/repository/util/weather）
│           ├── di/               # Hilt 模块（AppModule/DatabaseModule）
│           ├── domain/           # 提醒推荐领域逻辑
│           ├── model/            # UserType 枚举
│           ├── notification/     # AlarmScheduler + NotificationHelper
│           ├── receiver/         # 3 个广播接收器
│           ├── service/          # 语音播放前台服务
│           ├── ui/               # components / screens / navigation / theme
│           ├── util/             # 10 个工具类
│           ├── viewmodel/        # 16 个 ViewModel
│           ├── widget/           # 3 个 Glance 小部件
│           └── worker/           # WorkManager 任务
└── docs/                         # 项目设计文档与原型
```

---

## 5. 构建配置与运行方式

### 5.1 构建环境要求

- **JDK**：17
- **Android SDK**：compileSdk = targetSdk = 35
- **最低支持**：Android 8.0 (API 26)
- **Gradle**：9.4.1（wrapper 自带）

### 5.2 命令行编译（Windows）

```cmd
cd c:\Users\EDY\Desktop\CorgiMemo

REM Debug 构建
gradlew.bat assembleDebug

REM Release 构建（已配置签名）
gradlew.bat assembleRelease
```

### 5.3 IDE 运行

使用支持 AGP 9.x / Kotlin 2.3.x 的 Android Studio，打开项目根目录，选择 `app` Run Configuration 即可。

### 5.4 仓库镜像

[settings.gradle.kts](file:///c:/Users/EDY/Desktop/CorgiMemo/settings.gradle.kts) 已配置阿里云镜像优先，国内拉取依赖较快：

```kotlin
maven { url = uri("https://maven.aliyun.com/repository/public") }
maven { url = uri("https://maven.aliyun.com/repository/google") }
google()
mavenCentral()
```

### 5.5 签名配置

[app/build.gradle.kts](file:///c:/Users/EDY/Desktop/CorgiMemo/app/build.gradle.kts) 中已硬编码 release 签名：

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("../corgimemo-key.jks")
        storePassword = "yhx31415926@"
        keyAlias = "corgimemo"
        keyPassword = "yhx31415926@"
    }
}
```

> ⚠️ 安全提示：密钥口令以明文形式提交至版本库，建议迁移至 `local.properties` 或环境变量。

### 5.6 ProGuard

- `isMinifyEnabled = false`（release 未启用混淆）
- 自定义规则文件：[app/proguard-rules.pro](file:///c:/Users/EDY/Desktop/CorgiMemo/app/proguard-rules.pro)，仅含 Dagger 保留规则

---

## 6. 应用入口与生命周期

### 6.1 [CorgiMemoApplication.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/CorgiMemoApplication.kt)

`@HiltAndroidApp` 注解的 Application 子类，`onCreate()` 中执行：

1. `NotificationHelper.createNotificationChannels(this)` — 创建 4 个通知渠道
2. `ArchiveCleanupScheduler.scheduleIfNeeded(this)` — 调度归档清理周期任务
3. `WidgetUpdateWorker.scheduleWidgetUpdates(this)` — 调度小部件更新
4. `ReminderRestoreScheduler.restoreNow(this)` — 立即恢复提醒闹钟
5. 注册 `ProcessLifecycleOwner` 监听，进入前台时执行 `smartPreloadData()`

`smartPreloadData()` 通过 [UserBehaviorAnalyzer](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/analytics/UserBehaviorAnalyzer.kt) 输出预加载顺序，按优先级异步预热 待办 / 灵感 / 特殊日期 三类数据。整个流程包裹 try-catch，失败不影响启动。

### 6.2 [MainActivity.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/MainActivity.kt)

`@AndroidEntryPoint` 注解，注入 `TodoRepository` / `CorgiRepository` / `CorgiPreferences`。

职责：
- 注册导出/导入 `ActivityResultLauncher`
- 通过 `ThemeManager` 订阅主题模式与主题色，应用 `CorgiMemoTheme`
- 通过 `OnboardingRouter` 决定起始页（引导页 / 首页）
- 解析小部件跳转 Intent（`NAVIGATE_TO` extra），导航到对应目标

`OnboardingRouter` 读取 `CorgiPreferences.isOnboardingCompleted` 决定起始 destination，未完成则进入 [OnboardingScreen](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/onboarding/OnboardingScreen.kt) 的 5 页 HorizontalPager 引导流程。

### 6.3 [AppNavHost.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt)

定义所有路由的 `NavHost`，路由清单见 [Screen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/navigation/Screen.kt)：

| 路由 | Screen | 起始 |
|------|--------|------|
| `home` | `Screen.Home` | 默认起始 |
| `onboarding` | `Screen.Onboarding` | 引导未完成时 |
| `todo_edit` / `todo_edit/{todoId}` | `Screen.TodoEdit` / `TodoEditWithId` | |
| `profile` / `settings` / `stats` / `achievement` / `corgi_detail` | | |
| `inspire` / `date` | 底部导航 Tab | |
| `inspiration_edit` / `date_edit` | 编辑页 | |
| `backup_history` / `operation_history` / `edit_history` | 设置子页 | |
| `smart_category_settings` | 智能分类设置 | |
| `image_preview` | 图片全屏预览（参数通过 SavedStateHandle） | |

---

## 7. 数据层

数据层位于 `app/src/main/java/com/corgimemo/app/data/`，由 8 个子模块组成。

### 7.1 数据层总体分层

| 子模块 | 路径 | 职责 |
|--------|------|------|
| model | `data/model/` | 16 个 Room 实体 + 值对象 |
| local/db | `data/local/db/` | Room 数据库 + 16 个 DAO + 5 个独立 Entity |
| local/datastore | `data/local/datastore/` | 加密偏好（ESP），28+ 应用级偏好键 |
| cache | `data/cache/` | 双层缓存（L1 LRU 内存 + L2 ESP 磁盘） |
| event | `data/event/` | 进程内事件总线（TodoEventBus） |
| paging | `data/paging/` | Paging 3 数据源 |
| repository | `data/repository/` | 17 个 Repository / Manager |
| util | `data/util/` | KeywordExtractor |
| weather | `data/weather/` | WeatherManager |

### 7.2 数据库 [CorgiMemoDatabase.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt)

- 版本：**31**
- 注册 Entity：**16 个**（TodoItem / CorgiData / Category / DeletedTodo / MoodHistory / SubTask / AchievementEntity / TaskDailyStats / CategoryKeywordEntity / UserTemplateEntity / OperationLogEntity / Inspiration / InspirationRelation / SpecialDate / SpecialDateRelation / CardRelation / ContentBlockEntity）
- 迁移：**29 个**（v2 → v31，每个版本一个独立 `Migration` 对象）
- 单例：`@Volatile INSTANCE` + `synchronized` 双重检查

### 7.3 数据模型清单（[data/model](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model)）

| 模型 | 用途 |
|------|------|
| [TodoItem.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/TodoItem.kt) | 待办主表，含 26 字段（标题/内容/分类/优先级/状态/起止时间/地理围栏/语音/图片/背景色/置顶/排序等） |
| [SubTask.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/SubTask.kt) | 子任务（todoId 关联，order 排序，含附件 imagePaths/voicePaths） |
| [Category.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/Category.kt) | 分类（5 种默认分类 + 自定义） |
| [CorgiData.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/CorgiData.kt) | 柯基宠物数据（等级/经验/情绪/装扮/连续天数/成就） |
| [DeletedTodo.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/DeletedTodo.kt) | 最近删除（保留 30 天） |
| [MoodHistory.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/MoodHistory.kt) | 情绪历史趋势 |
| [Achievement.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/Achievement.kt) | 成就值对象 |
| [CardRelation.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/CardRelation.kt) | 统一卡片关联关系（todo/inspiration/date 互联） |
| [Inspiration.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/Inspiration.kt) | 灵感记录 |
| [SpecialDate.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/SpecialDate.kt) | 特殊日期（生日/纪念日/节日） |
| [UserTemplateEntity.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/UserTemplateEntity.kt) | 用户自定义模板 |
| [MultiSortConfig.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/MultiSortConfig.kt) | 多重排序配置 |

### 7.4 Repository 清单

| Repository | 职责 |
|-----------|------|
| [TodoRepository](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/TodoRepository.kt) | 待办 CRUD + 排序/置顶 + 软删除 + 闹钟调度 + Widget 刷新（核心入口） |
| [CorgiRepository](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/CorgiRepository.kt) | 柯基宠物数据（等级/经验/装扮/连续天数/成就） |
| [CategoryRepository](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/CategoryRepository.kt) | 分类 CRUD + 幂等初始化 5 个默认分类 |
| [AchievementChecker](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/AchievementChecker.kt) | 监听任务完成/每日打开/柯基升级，检测成就条件并解锁装扮（26 个成就） |
| [CategoryMatcher](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/CategoryMatcher.kt) | 基于关键词权重评分，为新待办推荐分类（EXACT 权重 10 / FUZZY 权重 3） |
| [GeofenceRepository](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/GeofenceRepository.kt) | 位置权限检查 + 围栏通知 + Haversine 距离计算 |
| [RepeatTaskManager](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/RepeatTaskManager.kt) | 6 种重复类型，完成时自动生成下一周期任务 |
| [SubTaskManager](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/SubTaskManager.kt) | 子任务 CRUD + 进度计算 + 全部完成自动完成父任务 |
| [TemplateRepository](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/TemplateRepository.kt) | 用户自定义待办模板（上限 20 个） |

> **设计特点**：`RepeatTaskManager` / `SubTaskManager` / `WeatherManager` 为 `object` 单例，通过 `CorgiMemoDatabase.getDatabase(context)` 获取 DAO，绕过 Hilt 避免循环依赖。

### 7.5 [TodoRepository](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/TodoRepository.kt) 关键方法

```kotlin
suspend fun insertTodo(todo: TodoItem): Long              // 插入并自动调度提醒
suspend fun updateTodos(todos: List<TodoItem>)             // 批量事务更新
suspend fun deleteTodo(todo: TodoItem)                     // 软删除（先入"最近删除"表）
fun observeAllSorted(): Flow<List<TodoItem>>              // isPinned DESC, sortOrder ASC, createdAt DESC
suspend fun updateSortOrder(todoId: Long, sortOrder: Int)  // 拖拽排序
suspend fun updatePinnedStatus(todoId: Long, isPinned: Boolean)  // 跨分区切换置顶
suspend fun cleanupOldCompletedTodos(threshold: Long): Int  // 清理过期已完成项
```

### 7.6 [CorgiPreferences](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt)

已从 **DataStore（明文）迁移到 EncryptedSharedPreferences（AES-256-GCM 加密）**，并通过 `callbackFlow` 适配保持 `Flow<T>` 接口兼容。

**核心偏好键**（28+ 静态键 + 动态键）：

| 类别 | 键 | 类型 |
|------|----|------|
| 柯基基础 | `corgi_name` / `is_first_launch` / `sound_enabled` / `haptic_enabled` / `is_onboarding_completed` / `user_type` | String/Boolean |
| 待办 UI | `show_completed` / `show_pinned` / `show_pending` / `hide_details` / `hide_completed_items` | Boolean |
| 自动备份 | `auto_backup_enabled` / `auto_backup_uri` / `auto_backup_password` / `auto_backup_keep_count` / `auto_backup_frequency` / `auto_backup_last_time` / `backup_history` | 混合 |
| 主题 | `theme_mode` (system/light/dark) / `theme_color` (orange 等) | String |
| 引导 A/B | `first_guide_shown` / `guide_ab_group` / `guide_completed_at` / `first_todo_created_at` | 混合 |
| 悬浮柯基 | `floating_corgi_x` / `floating_corgi_y` | String |
| 排序 | `sort_order` (默认 "updated_desc") | String |
| Undo/Redo | `undo_stack` / `redo_stack` / `last_edited_todo_id` / `undo_log_{todoId}` / `redo_log_{todoId}` | 混合 |
| 密钥轮换 | `encryption_key_version` (当前 = 1) | Int |

### 7.7 [CacheManager](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/cache/CacheManager.kt)

实现 **三级回退缓存架构**：

```
L1: 内存缓存 (LruMemoryCache)    → < 1ms 响应（最大 500 条，TTL 5 分钟）
L2: 磁盘缓存 (ESP)               → ~10ms 响应（TTL 24 小时，AES-256-GCM）
L3: 数据库 (Room)                → ~50-200ms 响应
```

- 读取：L1 → L2 → null（调用方回退 L3）；L2 命中时自动回填 L1
- 写入：Write-Through（同时写 L1 + L2）
- 失效：单键 / 按前缀批量 / `clearAll()`

### 7.8 [TodoEventBus](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/event/TodoEventBus.kt)

`object` 单例，`MutableSharedFlow<TodoEvent>`（`extraBufferCapacity = 16`，`DROP_OLDEST`）。

| 事件 | 触发场景 | 订阅方 |
|------|---------|--------|
| `TodoSaved` | TodoEditViewModel 保存后发出 | HomeViewModel `refreshAllData()` |
| `TodoDeleted` | 待办删除后发出 | HomeViewModel |

---

## 8. UI 层与导航

### 8.1 UI 层目录组织

```
app/src/main/java/com/corgimemo/app/ui/
├── screens/                    # 按业务功能分子目录
│   ├── main/                   # 主容器 MainScreen
│   ├── home/                   # 待办首页
│   ├── todo/                   # 待办编辑
│   ├── profile/                # 个人页
│   ├── settings/               # 设置（含 AutoBackup/EditHistory/OperationHistory/SmartCategory/TemplateManage 子页）
│   ├── stats/                  # 统计
│   ├── achievement/            # 成就墙
│   ├── corgi/                  # 柯基详情
│   ├── onboarding/             # 首次引导（5 页 HorizontalPager）
│   ├── backup/                 # 备份历史
│   ├── inspiration/            # 灵感记录
│   ├── date/                   # 特殊日期
│   ├── inspire/                # 灵感展示页（独立版本）
│   └── common/                 # 通用图片预览
├── components/                 # 通用组件库（70+ 文件）
├── theme/                      # 主题系统（7 个文件）
└── navigation/                 # 路由定义 Screen + AppNavHost
```

### 8.2 [MainScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/main/MainScreen.kt) — 主容器

统一管理：
- `ModalNavigationDrawer` 侧滑抽屉（分类/最近删除/设置）
- `Scaffold` 顶部栏（`EnhancedTopBar`，含批量模式标题切换）
- `Scaffold` 底部栏（普通模式：`CorgiBottomNavigationBar`；批量模式：`HomeBatchActionBar`，二者互斥）
- `FloatingCorgiButton` 悬浮柯基按钮（可拖动/左滑快速添加/右滑进入柯基详情）
- `BubbleMenuOverlay` 气泡菜单（点击中央按钮展开创建待办/灵感/日期）
- 各类弹窗（添加/重命名/删除分类、操作表）

按 Tab 切换内容区：

| Tab | 内容 |
|-----|------|
| `TODO` | [HomeScreen](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) |
| `INSPIRE` | [InspirationScreen](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/inspiration/InspirationScreen.kt) |
| `DATE` | [SpecialDateScreen](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/date/SpecialDateScreen.kt) |
| `PROFILE` | [ProfileScreen](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/profile/ProfileScreen.kt) |
| `EDIT` | 非真实 Tab，仅触发气泡菜单 |

### 8.3 关键页面职责

| 页面 | 文件 | 职责 |
|------|------|------|
| 待办首页 | [HomeScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/home/HomeScreen.kt) | 置顶/待完成/已完成三段分区、左滑操作、批量模式、搜索、下拉刷新、各类弹窗（升级/成就/换装/想念等） |
| 待办编辑 | [TodoEditScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/todo/TodoEditScreen.kt) | 多分组（多卡片）编辑、复选框文本编辑器、行内图片/语音附件、跨行拖拽、提醒/分类/优先级按分组独立设置 |
| 设置 | [SettingsScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/settings/SettingsScreen.kt) | 音效/触觉/用户身份/主题/备份入口 |
| 统计 | [StatsScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/stats/StatsScreen.kt) | 今日/本周/本月完成数、分类完成率、时段分析、趋势预测 |
| 成就墙 | [AchievementScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/achievement/AchievementScreen.kt) | 网格展示所有成就及进度，柯基按阶段显示不同姿态 |
| 柯基详情 | [CorgiDetailScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/corgi/CorgiDetailScreen.kt) | 柯基形象/等级/情绪/装扮/互动 |
| 引导 | [OnboardingScreen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/screens/onboarding/OnboardingScreen.kt) | 5 页 HorizontalPager：Welcome → UserType → CorgiNaming → Permission → Greeting |

### 8.4 通用组件分组（[ui/components](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/components)）

| 分组 | 代表组件 |
|------|---------|
| 待办卡片 | `TodoListItem` / `SwipeableTodoBox` / `ReorderableLazyColumn` / `SubTaskList` / `CircularCheckbox` / `PinnedSectionHeader` / `PendingSectionHeader` |
| 编辑工具栏 | `EditToolbar` / `TextFormatToolbar` / `CheckboxEditText` / `RichTextEditor` / `CrossLineDragManager` / `InlineImagePreview` |
| 弹窗与对话框 | `AchievementUnlockDialog` / `CorgiNamerDialog` / `DeleteConfirmDialog` / `ImagePickerDialog` / `CategoryPickerSheet` / `TemplateEditDialog` |
| 底部表单 | `ColorPickerBottomSheet` / `MoreOptionsSheet` / `PriorityPickerSheet` / `ReminderPickerBottomSheet` / `SortBottomSheet` / `MultiSortSheet` / `ActionBottomSheet` / `VoiceRecordBottomSheet` |
| 柯基相关 | `FloatingCorgiButton` / `CorgiCompanion` / `CorgiGuideAnimation` / `CorgiJumpAnimation` / `CorgiPullToRefreshIndicator` / `MoodIndicator` / `MoodHistoryChart` / `SolarTermCard` |
| 动画效果 | `ConfettiOverlay` / `AnimatedFAB` / `AnimatedLazyColumn` / `ListReorderAnimation` / `TodoAnimations` / `HeartParticleEffect` / `PressFeedback` |
| 搜索/排序/筛选 | `SearchBar` / `SortBottomSheet` / `MultiSortSheet` / `CategoryPickerSheet` |
| 通用 UI | `EnhancedTopBar` / `AppDrawer` / `UnifiedEmptyState` / `Skeleton` / `SafeAreaPadding` / `TemplateCarousel` / `BackupRecordCard` / `AudioWaveform` / `PriorityBadge` / `AchievementBadge` |
| navigation 子目录 | `CorgiBottomNavigationBar` / `BubbleMenuOverlay` / `CenterEditButton` |

### 8.5 主题系统

[ui/theme](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme) 目录：

| 文件 | 职责 |
|------|------|
| [Theme.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme/Theme.kt) | `CorgiMemoTheme` Composable，根据 darkTheme + themeColor 选择 colorScheme，配置状态栏外观 |
| [ThemeManager.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme/ThemeManager.kt) | 单例，维护 `themeMode`（system/light/dark）与 `themeColor`（orange 等）两个 StateFlow |
| [Color.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme/Color.kt) | 颜色定义（按主题色分组） |
| [Type.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme/Type.kt) | 字体 Typography |
| [UiColors.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme/UiColors.kt) | 通用 UI 颜色常量 |
| [UiDimensions.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme/UiDimensions.kt) | 通用尺寸常量 |
| [UiTextStyles.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme/UiTextStyles.kt) | 通用文本样式 |

主题数据流：

```
用户切换主题 → SettingsViewModel.setThemeMode/setThemeColor
            → ThemeManager.setThemeMode/setThemeColor（更新内存 StateFlow）
            → CorgiPreferences.saveThemeMode/saveThemeColor（持久化）
            → MainActivity 观察 ThemeManager.themeMode → 重组 CorgiMemoTheme
```

---

## 9. ViewModel 层

所有 ViewModel 使用 `@HiltViewModel` + `@Inject constructor`，通过 `hiltViewModel()` 在 Compose 中获取。状态使用 `MutableStateFlow`（私有）+ `StateFlow`（暴露），事件使用 `SharedFlow`。

### 9.1 ViewModel 清单

| ViewModel | 职责 |
|-----------|------|
| [HomeViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) | **首页核心 ViewModel（约 3349 行）**：管理待办列表/分类/搜索/排序/批量模式/柯基数据/姿态/情绪/成就/装扮/节日/节气/撤销删除/撤销完成/批量操作 |
| [TodoEditViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt) | 待办编辑：多分组保存状态、行级附件、跨行拖拽、ContentBlock 撤销/重做栈、地理围栏、子任务、分类、提醒按 groupId 独立维护 |
| [SettingsViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/SettingsViewModel.kt) | 设置：音效/触觉/用户身份/主题模式/主题色/数据导出导入 |
| [StatsViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/StatsViewModel.kt) | 统计：今日/本周/本月/累计完成数、分类完成率、时段分析、趋势预测、鼓励提示 |
| [OnboardingViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/OnboardingViewModel.kt) | 引导：当前页索引、用户身份选择、柯基命名、完成回调 |
| [AchievementViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/AchievementViewModel.kt) | 成就墙：成就列表+进度、阶段展示 |
| [ProfileViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/ProfileViewModel.kt) | 个人页：柯基数据、成就、装扮、等级阶段、情绪历史 |
| [InspirationViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/InspirationViewModel.kt) | 灵感列表 |
| [SpecialDateViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/SpecialDateViewModel.kt) | 特殊日期 |
| [EditHistoryViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/EditHistoryViewModel.kt) | 编辑历史时间线 |
| [OperationHistoryViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/OperationHistoryViewModel.kt) | 操作日志 |
| [SmartCategorySettingsViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/SmartCategorySettingsViewModel.kt) | 智能分类设置 |
| [TemplateViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TemplateViewModel.kt) | 模板管理 |
| [LocationViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/LocationViewModel.kt) | 位置选择 |
| [SpeechViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/SpeechViewModel.kt) | 语音识别（非 Hilt，`remember { lazy { ... } }` 创建） |

### 9.2 [HomeViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) 核心 StateFlow

```kotlin
// 待办列表
val todos: StateFlow<List<TodoItem>>
val pendingTodos: StateFlow<List<TodoItem>>
val visibleCompletedTodos: StateFlow<List<TodoItem>>   // 30 天内可见已完成
val filteredTodos: StateFlow<List<TodoItem>>            // 最终列表

// 三段分区展开状态
val showPinned / showPending / showCompleted: StateFlow<Boolean>
val hideDetails / hideCompletedItems: StateFlow<Boolean>

// 搜索/分类/排序
val searchQuery: StateFlow<String>
val selectedCategoryId: StateFlow<Long?>
val sortType: StateFlow<String>

// 批量模式
val isBatchMode: StateFlow<Boolean>
val selectedTodoIds: StateFlow<Set<Long>>

// 柯基
val corgiData: StateFlow<CorgiData?>
val currentPose: StateFlow<CorgiPose>
val currentMood: StateFlow<CorgiMood>
val currentOutfit: StateFlow<String?>
val levelStage: StateFlow<LevelStage>
val celebrationState: StateFlow<CelebrationState>
val greeting: StateFlow<String>
val currentHoliday: StateFlow<Holiday?>
val currentSolarTerm: StateFlow<SolarTerm?>
val showSolarTermCard: StateFlow<Boolean>

// 成长系统弹窗
val showLevelUp: StateFlow<Int?>
val showAchievementUnlock: StateFlow<Achievement?>
val achievementUnlockEvents: SharedFlow<Achievement>

// 撤销（事件式 StateFlow）
val pendingDeletedTodo: StateFlow<TodoItem?>
val pendingCompleteTodo: StateFlow<Pair<TodoItem, Boolean>?>
val pendingBatchDeletes: StateFlow<List<TodoItem>?>
```

### 9.3 [TodoEditViewModel](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt) 多分组状态

```kotlin
val groupSaveStates: StateFlow<Map<Int, GroupSaveState>>     // 各分组保存状态
val groupReminders: StateFlow<Map<Int, Long?>>               // 各分组提醒时间
val groupRepeatTypes: StateFlow<Map<Int, Int>>               // 各分组重复类型
val groupPriorities: StateFlow<Map<Int, Int>>                // 各分组优先级
val groupCategoryIds: StateFlow<Map<Int, Long>>              // 各分组分类
val lineAttachmentsSnapshot: StateFlow<String>               // 行级附件快照 JSON
```

---

## 10. 柯基动画子系统

[animation/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/animation) 是项目最具特色的子系统（22 个文件），负责柯基角色的全部动画、情绪、姿态、装扮、节日与节气交互。

### 10.1 核心组件

| 文件 | 职责 |
|------|------|
| `AnimationType.kt` | 13 种动画类型枚举（SIT/STAND/LIE/RUN/WINK/WAG/TILT/SLEEP/SAD/PROUD/SHY/WORRY/ROLL） |
| `CorgiPose.kt` | 5 种基础姿态枚举（LIE/SIT/STAND/RUN/SLEEP） |
| `CorgiMood.kt` | 7 种情绪枚举（HAPPY/NORMAL/EXPECTING/WORRIED/SLEEPY/EXCITED/SAD）+ `getMoodFromValue()` |
| `PoseManager.kt` | 根据场景（DEFAULT/CREATING/LOADING/CELEBRATING）切换姿态 |
| `MoodManager.kt` | 情绪值计算：`50 + 完成率×30 + 连续天数×5 + 超期×(-10)` |
| `CorgiBehaviorManager.kt` | 自主行为系统：打哈欠/深夜入睡/待办担心/被忽略想念/连续完成开心，含优先级仲裁 |
| `LevelManager.kt` | 等级系统（4 阶段：BABY/YOUTH/ADULT/MASTER），经验公式 `N×50` |
| `AchievementManager.kt` | 5 个旧成就定义 + 装扮 ID 映射（与新 AchievementChecker 并存） |
| `OutfitManager.kt` | 15 套装扮管理（默认/学士帽/领带/皇冠/披风/天使翅膀/9 套节日装扮） |
| `HolidayManager.kt` | 节日数据（含农历）+ 装扮关联 + 问候语池 |
| `SolarTermManager.kt` | 24 节气精确计算（依赖 tyme4kt 天文算法） |
| `SolarTermData.kt` | 节气科普卡片数据 |
| `LunarCalendar.kt` | 农历转换辅助 |
| `SeasonalOutfitRecommender.kt` | 季节性装扮推荐 |
| `DynamicGreetingManager.kt` | 动态问候语生成器（7 个时间段 + 天气 + 待办上下文） |
| `FrameAnimation.kt` | 帧动画播放器 |
| `PoseAnimation.kt` | 姿态切换动画 |
| `HeartParticleEffect.kt` | Compose Canvas 实现的爱心粒子特效 |
| `InteractiveCorgi.kt` | 可交互柯基组件入口（单击/双击/长按 → 触摸/喂食/玩耍） |

### 10.2 庆祝动画体系

| 级别 | 说明 | 视觉效果 |
|------|------|---------|
| LOW | 普通完成 | 简单反馈 |
| MEDIUM | 多个连续完成 | 彩纸 + 跳跃 |
| HIGH | 升级 / 成就 | 彩纸 + 金色光晕 |
| SUPER | 重大成就 | 彩纸 + 彩虹光晕 |

### 10.3 动画资源

[res/drawable](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/res/drawable) 包含 32 张柯基动画帧（sit/stand/lie/run/wink/wag/tilt/sleep/sad/proud/shy/worry/roll 各 2-4 帧 PNG），由 `AnimationResourceManager` 映射。

---

## 11. 通知 / 接收器 / 服务 / Widget / Worker

### 11.1 [notification/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/notification)

| 文件 | 职责 |
|------|------|
| [AlarmScheduler.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/notification/AlarmScheduler.kt) | AlarmManager 精确闹钟；Android 12+ `SCHEDULE_EXACT_ALARM` 权限检测；`rescheduleReminder` / `restoreAllReminders` |
| [NotificationHelper.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/notification/NotificationHelper.kt) | 4 个通知渠道；通知聚合（同小时≥3 条触发 Summary）；5 个操作按钮（完成/稍后10分钟/稍后1小时/推迟明天/改到周末） |

### 11.2 [receiver/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/receiver)

| 文件 | 职责 |
|------|------|
| [ReminderActionReceiver.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/receiver/ReminderActionReceiver.kt) | 处理 6 种通知 Action，CoroutineScope IO 线程更新待办并重新调度闹钟 |
| [BootReceiver.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/receiver/BootReceiver.kt) | `BOOT_COMPLETED` 后恢复未完成待办的闹钟 |
| [AutoBackupReceiver.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/receiver/AutoBackupReceiver.kt) | 接收 `ACTION_AUTO_BACKUP`，启动 BackupWorker 并重新调度下一次 |

### 11.3 [service/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/service)

| 文件 | 职责 |
|------|------|
| [VoicePlaybackService.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/service/VoicePlaybackService.kt) | 语音播放前台服务（`foregroundServiceType=mediaPlayback`）；MediaPlayer 封装；通知栏播放/暂停/停止；`LocalBinder` 与 Activity 通信；ACTION_PLAY_PAUSE / ACTION_STOP / ACTION_SEEK |

### 11.4 [widget/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/widget)（基于 Glance 1.0.0）

| 文件 | 职责 |
|------|------|
| `QuickAddWidget.kt` + `QuickAddWidgetReceiver.kt` | 1×1 快速添加待办 |
| `TodayPreviewWidget.kt` + `TodayPreviewWidgetReceiver.kt` | 2×2 今日待办预览 |
| `TodoListWidget.kt` + `TodoListWidgetReceiver.kt` | 4×2 完整待办列表（可滚动 LazyColumn） |
| `WidgetActionReceiver.kt` | 处理 `ACTION_COMPLETE_TODO` 小部件直接完成 |
| `WidgetUpdateReceiver.kt` | 处理 `ACTION_REFRESH_WIDGET` 刷新广播 |
| `WidgetUpdateWorker.kt` | WorkManager 定时刷新小部件 |
| `WidgetDataManager.kt` | 小部件数据访问层（查询今日待办、格式化、优先级颜色） |
| `WidgetNavigationHelper.kt` | 小部件点击跳转深链辅助 |

### 11.5 [worker/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/worker)

| 文件 | 职责 |
|------|------|
| `AutoArchiveCleanupWorker.kt` | 每日清理超过 30 天的已完成待办；Hilt `@EntryPoint` 注入 |
| `ArchiveCleanupScheduler.kt` | 周期任务调度器 |
| `ReminderRestoreWorker.kt` | 应用启动时恢复所有未过期提醒闹钟 |
| `ReminderRestoreScheduler.kt` | 一次性任务调度器 |

---

## 12. 备份子系统

[backup/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/backup) 含 5 个子目录：

```
backup/
├── BackupManager.kt              # 备份统一入口（4 种格式：JSON/CSV/ICAL/IMAGE）
├── BackupWorker.kt               # 一次性备份 Worker
├── AutoBackupAlarmScheduler.kt   # AlarmManager 精确调度（每周日 / 每月 1 日凌晨 3:00）
├── BackupFrequency.kt            # 频率枚举（WEEKLY/MONTHLY）
├── BackupScheduler.kt
├── BackupHistoryManager.kt
├── BackupRecord.kt
├── BackupFileHandler.kt
├── crypto/
│   └── EncryptionManager.kt      # AES-256-GCM + PBKDF2WithHmacSHA256（10 万次迭代）
│                                 # 输出格式：salt(16) + iv(12) + 密文 + tag(16)
├── data/
│   ├── BackupData.kt             # 备份数据容器
│   └── BackupMeta.kt             # 元信息（版本号/时间戳/密码标记）
├── exporter/
│   ├── IcsExporter.kt           # 手写 iCalendar (.ics) 字符串构建器
│   ├── ImageExporter.kt
│   ├── ShareCardComponent.kt
│   └── ShareIntentHelper.kt     # FileProvider 分享 Intent
├── serializer/
│   ├── JsonSerializer.kt
│   └── CsvSerializer.kt
└── worker/
    └── AutoBackupWorker.kt      # 周期性备份（7 天间隔，ExistingPeriodicWorkPolicy.UPDATE）
```

### 关键 API

```kotlin
// 备份管理器统一入口
BackupManager.exportData(context, uri, format): ExportResult
BackupManager.restoreData(context, uri): RestoreResult

sealed class ExportResult {
    object Success : ExportResult()
    data class Error(val message: String) : ExportResult()
}

sealed class RestoreResult {
    data class Success(val todoCount: Int) : RestoreResult()
    data class Error(val message: String) : RestoreResult()
    object WrongPassword : RestoreResult()
    object VersionIncompatible : RestoreResult()
}
```

---

## 13. 工具与领域逻辑

### 13.1 [util/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/util)

| 文件 | 职责 |
|------|------|
| `AnnotatedStringSerializer.kt` | 富文本 AnnotatedString 序列化 |
| `DensityUtils.kt` | dp/sp ↔ px 互转 |
| `FileCopyManager.kt` | 附件文件批量复制 |
| `HighlightUtil.kt` | 搜索关键词高亮 |
| `ImageUtils.kt` | 图片压缩、格式转换、尺寸处理 |
| `LocaleHelper.kt` | 多语言切换（中/英/日） |
| `MarkdownParser.kt` | Markdown 解析为 AnnotatedString |
| `SpeechRecognizerHelper.kt` | 语音识别封装 |
| `VoicePlayer.kt` | 语音播放器（非前台服务场景） |
| `VoiceRecorder.kt` | 语音录音器（MediaRecorder） |

### 13.2 [domain/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/domain)

| 文件 | 职责 |
|------|------|
| [ReminderRecommender.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/domain/ReminderRecommender.kt) | 提醒时间推荐引擎：按分类默认规则（工作提前 15 分钟、学习提前 60 分钟、生活当天 9:00、运动提前 30 分钟）；过期任务自动加 5 分钟缓冲 |

### 13.3 [analytics/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/analytics)

| 文件 | 职责 |
|------|------|
| [UserBehaviorAnalyzer.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/analytics/UserBehaviorAnalyzer.kt) | `@Singleton`；统计页面访问频率；加权算法（频率×0.7 + 最近访问×0.3）输出预加载优先级；**数据仅本地存储，不上传服务器**；通过 `@EntryPoint` 暴露给 Composable |

### 13.4 [model/](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/model)

| 文件 | 职责 |
|------|------|
| `UserType.kt` | 用户类型枚举（WORKER/STUDENT），用于个性化问候语与推荐分类 |

---

## 14. 数据库迁移历史

[CorgiMemoDatabase.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt) 注册了 29 个迁移对象（v2 → v31），每个版本对应一个 `Migration` 对象。下表列出关键里程碑：

| 版本 | 迁移内容 |
|------|---------|
| v2 → v3 | 为 `todo_items` 添加地理围栏 6 字段（geofenceLat/Lng/Radius/Type/Enabled/Address） |
| v3 → v4 | 创建 `categories` 表；为 `corgi_data` 添加 `unlockedAchievements` 与 `maxConsecutiveDays` |
| v4 → v5 | 创建 `mood_history` 表 |
| v5 → v6 | 创建 `sub_tasks` 表（子任务） |
| v6 → v7 | 为 `todo_items` 添加 `hasSubTasks` 字段 |
| v7 → v8 | 创建 `achievements` 表；为 `corgi_data` 添加 `consecutiveEarlyDays` |
| v8 → v9 | 创建 `task_daily_stats` 表（每日任务统计） |
| v9 → v10 | 创建 `category_keywords` 表（智能分类关键词） |
| v10 → v11 | 移除 `dueDate`，添加 `startDate` 与 `estimatedDurationMinutes`（数据迁移） |
| v11 → v12 | 添加语音备注字段（`voiceNotePath` / `voiceDuration`） |
| v12 → v13 | 创建 `user_templates` 表（用户自定义模板） |
| v13 → v14 | 创建 `operation_logs` 表（操作日志，支持撤销） |
| v14 → v15 | 修复 `operation_logs` 表索引名称与默认值格式 |
| v15 → v16 | 创建 `deleted_todos` 表（最近删除，保留 30 天） |
| v16 → v17 | 创建 `inspirations` + `inspiration_relations` 表 |
| v17 → v18 | 创建 `special_dates` + `special_date_relations` 表 |
| v18 → v19 | 为 `todo_items` 添加 `imagePaths` 字段 |
| v19 → v20 | 创建统一 `card_relations` 表，从分散表迁移数据 |
| v20 → v21 | 修复 `card_relations` 表结构（重建 + 数据迁移） |
| v21 → v22 | 为 `todo_items` 添加 `dueDate` 截止时间字段 |
| v22 → v23 | 为 `todo_items` 添加 `backgroundColor` 字段（12 种预设色） |
| v23 → v24 | 为 `todo_items` 添加 `contentFormat` 富文本字段 |
| v24 → v25 | 为 `todo_items` 添加 `position` 字段（拖拽排序） |
| v25 → v26 | 创建 `content_blocks` 表（混合内容流：图片/语音），从旧字段迁移数据 |
| v26 → v27 | 为 `inspirations` 表新增 20 字段（与 TodoEditScreen 字段对齐） |
| v27 → v28 | 为 `sub_tasks` 表新增 `imagePaths` / `voicePaths` 附件字段 |
| v28 → v29 | 为 `todo_items` 添加 `isPinned` 置顶字段 |
| v29 → v30 | 移除 `todo_items.position` 字段与索引（删除拖拽排序功能） |
| v30 → v31 | 新增 `todo_items.sortOrder` 字段并按 `createdAt DESC` 回填（重新引入拖拽排序） |

> ⚠️ **项目规则**：修改 Entity 字段或新增 Migration 时，应确保 `@ColumnInfo(defaultValue)` 值与 Migration SQL 中的 DEFAULT 子句一致（详见 [.trae/rules/entity与 migration同步检查.md](file:///c:/Users/EDY/Desktop/CorgiMemo/.trae/rules/entity与%20migration同步检查.md)）。

---

## 15. 关键数据流与设计要点

### 15.1 待办保存数据流

```
TodoEditScreen 用户点击保存
    ↓
TodoEditViewModel.saveAllGroups()
    ↓ 持久化各分组状态
TodoRepository.insertTodo() / updateTodo()
    ↓ 调度闹钟 + Widget 刷新
AlarmScheduler.scheduleReminder()
TodoEventBus.emit(TodoSaved)         ←─── 跨 ViewModel 通知
    ↓
HomeViewModel.init { events.collect } → refreshAllData()
    ↓
_todos.value = ...                   ←─── StateFlow 推送
    ↓
HomeScreen collectAsState() → UI 重组
```

### 15.2 撤销删除数据流

```
用户左滑删除
    ↓
HomeViewModel.deleteTodo()
    ↓ 软删除到 deleted_todos 表
TodoRepository.deleteTodo()
    ↓
pendingDeletedTodo.value = todo     ←─── 事件式 StateFlow
    ↓
Snackbar 显示 "已删除" + "撤销" 按钮（5 秒倒计时）
    ↓
用户点击"撤销" → pendingDeletedTodo.value = null → 调用 TodoRepository 恢复
用户不点击 → 5 秒后 pendingDeletedTodo.value = null（待办保留在 deleted_todos 表 30 天）
```

### 15.3 成就解锁数据流

```
用户完成待办
    ↓
TodoRepository.updateTodoStatus()
    ↓
AchievementChecker.checkOnTaskComplete(completedTodo)
    ↓ 检测成就条件（首次完成/累计/分类/单天/早起/加班终结者）
    ↓ 解锁装扮
achievementUnlockEvents.emit(achievement)   ←─── SharedFlow 事件流
    ↓
HomeViewModel.init { achievementUnlockEvents.collect }
    ↓
showAchievementUnlock.value = achievement    ←─── StateFlow
    ↓
HomeScreen 渲染 AchievementUnlockDialog + 长震动反馈
```

### 15.4 数据库排序规则

待办列表默认排序：

```sql
ORDER BY isPinned DESC,    -- 置顶优先
         sortOrder ASC,    -- 同分区内拖拽顺序
         createdAt DESC     -- 兜底：按创建时间倒序
```

### 15.5 通知聚合规则

`NotificationHelper` 实现通知聚合：同一小时内 ≥ 3 条待办触发时，发布一条 `SummaryNotification` 汇总所有待办，避免通知栏泛滥。

### 15.6 智能预加载

应用进入前台时，`CorgiMemoApplication.smartPreloadData()` 通过 `UserBehaviorAnalyzer.getPreloadPriority()` 获取按访问频率排序的页面列表，按优先级异步预加载待办/灵感/特殊日期数据，提升首帧速度。算法：

```
priority = frequency × 0.7 + recency × 0.3
```

---

## 16. 后续可优化方向

### 16.1 架构层面

1. **HomeViewModel 体量过大**（约 3349 行）：可拆分为 `TodoListViewModel` + `CorgiViewModel` + `CelebrationViewModel` 等多个 ViewModel，按职责聚合。
2. **MainScreen 状态过多**：建议提取 `MainScreenViewModel` 管理 Tab 切换/抽屉/气泡菜单/批量操作栏槽位等顶层状态。
3. **重复的成就系统**：存在旧 `AchievementManager`（JSON 存储）与新 `AchievementChecker/Repository`（Room 存储）两套系统，建议统一迁移至新系统并删除旧代码。
4. **inspire vs inspiration 目录**：存在 `inspire/InspireScreen.kt` 与 `inspiration/InspirationScreen.kt` 两个灵感相关目录，建议合并避免混淆。
5. **Manager 单例改造**：`RepeatTaskManager` / `SubTaskManager` / `WeatherManager` 当前为 `object` 单例，绕过 Hilt 难以测试，可改为 `@Singleton class` + Hilt 注入。
6. **Repository 接口抽象**：当前 Repository 均为具体类，未抽取接口。如需支持多数据源（如远程同步），可抽取接口便于 Mock。

### 16.2 数据层

7. **CorgiPreferences 动态键清理**：`solar_term_card_dismissed_*` 和 `undo_log_*` 长期累积可能占用空间，可定期清理。
8. **CacheManager 命中率统计**：当前 `CacheStats.hitRate = 0.0f` 为占位实现，可接入实际命中计数用于性能监控。
9. **DAO 方法去重**：`TodoDao` 存在多种排序查询，可考虑使用 `RawQuery` 动态构建（`MultiSortConfig.toOrderByClause()` 已支持但未全面应用）。

### 16.3 构建/安全

10. **签名密钥安全**：将 `corgimemo-key.jks` 的口令迁移至 `local.properties` 或环境变量，避免明文入库。
11. **ProGuard 启用**：release 包应启用 `isMinifyEnabled = true` + R8，并补充 Room 实体、kotlinx.serialization、tyme4kt 的保留规则。
12. **依赖版本同步**：`appcompat 1.6.1` 与 `compileSdk 35` 略旧，可升级；`accompanist 0.34.0` 已较旧，可考虑迁移至官方替代方案。
13. **纯 Compose 项目移除 AppCompat**：`androidx.appcompat` 在纯 Compose 项目中可考虑移除以减小包体。

### 16.4 资源整合

14. **资源整合**：`colors.xml`（3 色兼容旧主题）与 `colors_ui.xml`（真正使用）存在职责重叠，可考虑合并。
15. **Hilt 入口点统一**：`AutoArchiveCleanupWorker` 与 `ReminderRestoreWorker` 都通过 `@EntryPoint` 注入 `TodoRepository` 与 `IoDispatcher`，可抽取一个公共 `WorkerEntryPoint` 减少重复。

---

## 附录：关键文件路径速查

| 类别 | 路径 |
|------|------|
| 根构建脚本 | [build.gradle.kts](file:///c:/Users/EDY/Desktop/CorgiMemo/build.gradle.kts) |
| 版本目录 | [gradle/libs.versions.toml](file:///c:/Users/EDY/Desktop/CorgiMemo/gradle/libs.versions.toml) |
| App 构建脚本 | [app/build.gradle.kts](file:///c:/Users/EDY/Desktop/CorgiMemo/app/build.gradle.kts) |
| Manifest | [app/src/main/AndroidManifest.xml](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/AndroidManifest.xml) |
| Application | [CorgiMemoApplication.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/CorgiMemoApplication.kt) |
| MainActivity | [MainActivity.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/MainActivity.kt) |
| NavHost | [AppNavHost.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/navigation/AppNavHost.kt) |
| Screen 路由 | [Screen.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/navigation/Screen.kt) |
| Database | [CorgiMemoDatabase.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/db/CorgiMemoDatabase.kt) |
| TodoItem | [TodoItem.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/model/TodoItem.kt) |
| TodoRepository | [TodoRepository.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/repository/TodoRepository.kt) |
| CorgiPreferences | [CorgiPreferences.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/data/local/datastore/CorgiPreferences.kt) |
| DatabaseModule | [DatabaseModule.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/di/DatabaseModule.kt) |
| AppModule | [AppModule.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/di/AppModule.kt) |
| HomeViewModel | [HomeViewModel.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/HomeViewModel.kt) |
| TodoEditViewModel | [TodoEditViewModel.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/viewmodel/TodoEditViewModel.kt) |
| Theme | [Theme.kt](file:///c:/Users/EDY/Desktop/CorgiMemo/app/src/main/java/com/corgimemo/app/ui/theme/Theme.kt) |
| 项目规则 | [.trae/rules/](file:///c:/Users/EDY/Desktop/CorgiMemo/.trae/rules/) |
| 设计文档 | [.trae/documents/](file:///c:/Users/EDY/Desktop/CorgiMemo/.trae/documents/) |

---

**文档版本**：v1.0
**生成日期**：2026-07-04
**项目版本**：1.0（versionCode=1）
**数据库版本**：31
