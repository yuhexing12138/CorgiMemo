import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    /** Kotlin 2.0+ 必须显式应用 Compose Compiler 插件 */
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

/** Release signing is intentionally local-only. Keep the actual values in the
 * repository-root `keystore.properties` file (which is ignored by Git), for
 * example by copying `keystore.properties.example`.
 */
// 用 bufferedReader().use { load(it) } 而非 inputStream().use(::load)：
// Properties.load 同时有 load(InputStream) 与 load(Reader) 两个重载，原写法
// 让 use<T : Closeable?, R> 中 T 可空 + load 重载歧义叠加，Kotlin 2.3 在
// Gradle 9.6.1 脚本编译中无法推断 R，导致整条类型链断裂（util / getProperty
// 全部 unresolved）。bufferedReader() 返回 Reader，唯一匹配 load(Reader) 重载。
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.bufferedReader().use { load(it) }
    }
}

val releaseSigningConfigured = listOf(
    "storeFile",
    "storePassword",
    "keyAlias",
    "keyPassword"
).all { keystoreProperties.getProperty(it)?.isNotBlank() == true }

android {
    namespace = "com.corgimemo.app"
    compileSdk = 36

    if (releaseSigningConfigured) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.corgimemo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // AGP 9.0 内置 Kotlin 模式：使用 kotlin { } 扩展替代旧版 kotlinOptions 块
    //  jvmToolchain(17) 让 Kotlin 编译与 Java 编译共用同一个 JVM 17 toolchain
    kotlin {
        jvmToolchain(17)
    }

    buildFeatures {
        compose = true
    }

    /** composeOptions 块已移除：Kotlin 2.0+ 使用 kotlin-compose 插件自动管理 Compose Compiler 版本 */

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    /**
     * Hilt / Dagger 编译器选项
     *
     * useBindingGraphFix: 启用绑定图修复（Dagger 2.58 默认开启）。
     * 确保所有 @Provides 方法安装在正确的 Component 中，
     * 提前发现潜在的依赖注入错误，为升级到 Dagger 2.58+ 做好准备。
     */
    ksp {
        arg("dagger.useBindingGraphFix", "enabled")
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)  // ProcessLifecycleOwner 支持
    implementation(libs.androidx.paging.runtime)     // Paging 3 核心库
    implementation(libs.androidx.paging.compose)    // Paging 3 Compose 集成
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    /** 加密 SharedPreferences（替代明文 DataStore，所有 key 自动 AES-256-GCM 加密）*/
    implementation(libs.androidx.security.crypto.ktx)

    implementation(libs.coil.compose)
    // Coil 3.x 网络图片支持（CorgiCompanion 通过 HTTPS URL 加载图片）
    implementation(libs.coil.network.okhttp)
    implementation(libs.google.accompanist.permissions)
    /**
     * 下拉刷新已从 accompanist-swiperefresh（已废弃）迁移到 Material3 PullToRefreshBox，
     * 移除 accompanist-swiperefresh 依赖。
     */
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.glance.appwidget)

    implementation(libs.google.dagger.hilt.android)
    ksp(libs.google.dagger.hilt.compiler)
    // Hilt 使用 kotlin-metadata-jvm 读取 Kotlin 元数据，Kotlin 2.3.20 产生 metadata 2.4.0，
    // 但 Hilt 内部 shaded 版本仅支持到 2.3.0。Dagger 2.57+ 已 unshade 该依赖，
    // 显式声明匹配 Kotlin 版本的 kotlin-metadata-jvm 即可解决兼容性问题。
    ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.3.20")

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.appcompat)

    /** 农历库：tyme4kt（基于 6tail/lunar 升级的 KMP 版本） */
    implementation("cn.6tail:tyme4kt:1.4.5")

    /** 拖拽排序库 Calvin-LL/Reorderable */
    implementation(libs.reorderable)

    /** 富文本编辑器库（源码 module 集成） */
    implementation(project(":richeditor-compose"))

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    /** Room Migration 单元测试：用纯 JVM SQLite 验证 migrate() 的 SQL 逻辑 */
    testImplementation(libs.sqlite.jdbc)
    /** Robolectric：在 JVM 上模拟 Android Framework，使 Bitmap 等 Android API 能在单元测试中使用 */
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    /** Room Migration 测试支持：提供 MigrationTestHelper 用于验证数据库升级脚本 */
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
