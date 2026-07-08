plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    /** Kotlin 2.0+ 必须显式应用 Compose Compiler 插件 */
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
}

android {
    namespace = "com.corgimemo.app"
    compileSdk = 35

    signingConfigs {
        create("release") {
            storeFile = file("../corgimemo-key.jks")
            storePassword = "yhx31415926@"
            keyAlias = "corgimemo"
            keyPassword = "yhx31415926@"
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
            signingConfig = signingConfigs.getByName("release")
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

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.appcompat)

    /** 农历库：tyme4kt（基于 6tail/lunar 升级的 KMP 版本） */
    implementation("cn.6tail:tyme4kt:1.4.5")

    /** 拖拽排序库 Calvin-LL/Reorderable */
    implementation(libs.reorderable)

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
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}