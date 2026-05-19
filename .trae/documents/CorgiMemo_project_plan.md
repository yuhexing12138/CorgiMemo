# CorgiMemo Android 项目配置计划

## 一、项目概述

本项目是一个基于 Jetpack Compose 的 Android 笔记应用，采用 MVVM 架构模式。

### 技术栈
| 分类 | 技术 | 版本 |
|------|------|------|
| 开发语言 | Kotlin | 1.9.x |
| UI框架 | Jetpack Compose | 1.6.x |
| 架构模式 | MVVM | - |
| 依赖注入 | Hilt | 2.48.x |
| 数据库 | Room | 2.6.x |
| 导航 | Navigation Compose | 2.7.x |

### SDK 配置
- **最低 SDK**: API 26 (Android 8.0)
- **目标 SDK**: API 34 (Android 14)
- **编译 SDK**: API 34

## 二、目录结构

```
CorgiMemo/
├── build.gradle.kts           # 项目级构建配置
├── settings.gradle.kts        # 项目设置
├── gradle.properties          # Gradle 属性配置
├── gradle/
│   └── wrapper/
└── app/
    ├── build.gradle.kts       # 模块级构建配置
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/corgimemo/app/
        │   ├── CorgiMemoApplication.kt  # 应用入口
        │   ├── di/                      # 依赖注入配置
        │   │   └── AppModule.kt
        │   ├── data/
        │   │   ├── local/
        │   │   │   ├── db/              # Room 数据库
        │   │   │   └── datastore/       # DataStore
        │   │   ├── repository/          # 数据仓库
        │   │   └── model/               # 数据模型
        │   ├── ui/
        │   │   ├── theme/               # 主题配置
        │   │   ├── components/          # 通用组件
        │   │   └── screens/             # 页面
        │   │       ├── home/
        │   │       ├── todo/
        │   │       └── profile/
        │   └── viewmodel/               # ViewModel
        └── res/
            └── values/
```

## 三、待创建文件清单

| 序号 | 文件路径 | 说明 | 状态 |
|------|----------|------|------|
| 1 | `build.gradle.kts` (项目级) | 项目构建配置 | 待创建 |
| 2 | `settings.gradle.kts` | 项目设置 | 待创建 |
| 3 | `gradle.properties` | Gradle 属性 | 待创建 |
| 4 | `gradle/wrapper/gradle-wrapper.properties` | Gradle Wrapper | 待创建 |
| 5 | `app/build.gradle.kts` | 模块构建配置 | 待创建 |
| 6 | `app/src/main/AndroidManifest.xml` | 应用清单 | 待创建 |
| 7 | `app/src/main/res/values/strings.xml` | 字符串资源 | 待创建 |
| 8 | `app/src/main/java/com/corgimemo/app/CorgiMemoApplication.kt` | 应用入口 | 已创建 |
| 9 | `app/src/main/java/com/corgimemo/app/di/AppModule.kt` | Hilt 模块 | 待创建 |
| 10 | `app/src/main/java/com/corgimemo/app/ui/theme/Color.kt` | 颜色配置 | 待创建 |
| 11 | `app/src/main/java/com/corgimemo/app/ui/theme/Theme.kt` | 主题配置 | 待创建 |
| 12 | `app/src/main/java/com/corgimemo/app/ui/theme/Type.kt` | 字体配置 | 待创建 |
| 13 | `app/src/main/java/com/corgimemo/app/ui/MainActivity.kt` | 主 Activity | 待创建 |

## 四、核心依赖项

| 依赖 | GroupId | ArtifactId | 用途 |
|------|---------|------------|------|
| Compose UI | androidx.compose.ui | ui | UI 组件 |
| Compose Material3 | androidx.compose.material3 | material3 | Material3 组件 |
| Compose Icons | androidx.compose.material.icons | icons-extended | 图标库 |
| ViewModel | androidx.lifecycle | lifecycle-viewmodel-ktx | ViewModel 支持 |
| ViewModel Compose | androidx.lifecycle | lifecycle-viewmodel-compose | Compose ViewModel 集成 |
| Room Runtime | androidx.room | room-runtime | Room 数据库 |
| Room KTX | androidx.room | room-ktx | Room Kotlin 扩展 |
| Room Compiler | androidx.room | room-compiler | Room 注解处理器 |
| Hilt | com.google.dagger | hilt-android | 依赖注入 |
| Hilt Compiler | com.google.dagger | hilt-compiler | Hilt 注解处理器 |
| Navigation Compose | androidx.navigation | navigation-compose | 导航组件 |
| Coroutines | org.jetbrains.kotlinx | kotlinx-coroutines-android | 协程 |

## 五、主题配置

### 主色调
- **暖橙色**: `#FF9A5C`
- **深色模式支持**: 自动适配系统主题

## 六、风险与注意事项

1. **依赖冲突**: 确保 Compose BOM 管理所有 Compose 相关依赖版本
2. **Hilt 配置**: 需要正确配置 `@HiltAndroidApp` 和 Module
3. **Room 数据库**: 需要配置正确的实体和 DAO
4. **资源文件**: 避免硬编码字符串，使用 stringResource

## 七、执行步骤

1. 创建项目级配置文件
2. 创建模块级配置文件
3. 创建 Gradle Wrapper 文件
4. 创建资源文件
5. 创建主题配置文件
6. 创建应用入口和依赖注入配置
7. 创建主 Activity