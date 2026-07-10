import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

/**
 * richeditor-compose 模块构建配置
 *
 * 改造说明：
 * 原为 KMP 多平台模块（JetBrains Compose Multiplatform），现已改造为
 * 仅保留 Android target 的 KMP 模块，使用 AndroidX Compose 依赖。
 *
 * 主要变更：
 * - 移除 composeMultiplatform 插件（org.jetbrains.compose）
 * - 移除 bcv（Binary Compatibility Validator）
 * - 移除 module.publication 自定义 convention plugin
 * - 移除 jvm/js/wasmJs/ios 等 target
 * - commonMain 依赖从 org.jetbrains.compose.* 改为 androidx.compose.*
 * - SDK 版本对齐根项目（compileSdk=35, minSdk=26）
 */

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.android.library)
}

kotlin {
    explicitApi()
    applyDefaultHierarchyTemplate()

    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets.commonMain.dependencies {
        // AndroidX Compose（通过 BOM 统一版本，与根项目保持一致）
        implementation(platform("androidx.compose:compose-bom:2026.04.01"))
        implementation("androidx.compose.runtime:runtime")
        implementation("androidx.compose.foundation:foundation")
        implementation("androidx.compose.material:material")
        implementation("androidx.compose.material3:material3")

        // HTML 解析库
        implementation("com.mohamedrejeb.ksoup:ksoup-html:0.6.0")
        implementation("com.mohamedrejeb.ksoup:ksoup-entities:0.6.0")

        // Markdown 解析库
        implementation("org.jetbrains:markdown:0.7.3")
    }

    sourceSets.commonTest.dependencies {
        implementation(kotlin("test"))
    }
}

android {
    namespace = "com.mohamedrejeb.richeditor.compose"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        consumerProguardFile("proguard-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
